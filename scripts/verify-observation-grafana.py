"""Verify the local synthetic Docker stack through real Grafana/Infinity queries."""
import argparse
import base64
import copy
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:3000"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fault-tests", action="store_true", help="Test synthetic age, empty discovery, and outage controls")
    args = parser.parse_args()
    for attempt in range(20):
        try:
            with urllib.request.urlopen(BASE + "/api/health", timeout=2) as response:
                if json.load(response).get("database") == "ok":
                    break
        except (OSError, ValueError):
            pass
        time.sleep(0.5)
    else:
        raise RuntimeError("Local Grafana did not become ready")
    values = dict(line.split("=", 1) for line in (ROOT / "runtime/observation-docker.env").read_text().splitlines() if "=" in line)
    authorization = "Basic " + base64.b64encode(("admin:" + values["GF_SECURITY_ADMIN_PASSWORD"]).encode()).decode()
    dashboard = json.loads((ROOT / "observability/grafana/dashboards/blackboard.json").read_text())

    def query(targets, board="", agent="*"):
        queries = copy.deepcopy(targets)
        now = str(int(time.time() * 1000))
        for item in queries:
            item["url"] = (item["url"].replace("${board:percentencode}", board).replace("${expert:percentencode}", agent)
                           .replace("${__from}", "0").replace("${__to}", now))
        request = urllib.request.Request(BASE + "/api/ds/query", json.dumps({"from": "0", "to": now, "queries": queries}).encode(),
                                         {"Authorization": authorization, "Content-Type": "application/json"})
        try:
            response = urllib.request.urlopen(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        return json.load(response)["results"]

    def records(result):
        assert not result.get("error"), result.get("error")
        frame = result["frames"][0]
        names = [field["name"] for field in frame["schema"]["fields"]]
        return [dict(zip(names, row)) for row in zip(*frame["data"]["values"])]

    boards = records(query([dashboard["templating"]["list"][0]["query"]["infinityQuery"]])["A"])
    assert len(boards) == 3, "The sealed synthetic fixture must have three boards"
    docker = str(Path(os.environ["LOCALAPPDATA"]) / "Programs/DockerDesktop/resources/bin/docker.exe")
    report = []
    for board in boards:
        key = board["__value"]
        assert len(key) == 64 and all(char in "0123456789abcdef" for char in key)
        command = 'wget -qO- --header="Authorization: Bearer $SUPERMAS_OBSERVATION_TOKEN" "http://127.0.0.1:8091/view?board=' + key + '"'
        view = json.loads(subprocess.check_output([docker, "exec", "supermas-observation-grafana-1", "sh", "-c", command], encoding="utf-8", timeout=15))
        for panel in dashboard["panels"]:
            if not panel.get("targets"):
                continue
            results = query(panel["targets"], key)
            for target in panel["targets"]:
                rows = records(results[target["refId"]])
                section = target["root_selector"]
                if section in ("overview", "experts", "events", "timeline", "nodes", "edges"):
                    assert len(rows) == len(view[section]), (panel["title"], len(rows), len(view[section]))
                    if rows and "sourceRevision" in rows[0]:
                        assert all(row["sourceRevision"] == view["sourceRevision"] for row in rows)
                    if section == "overview":
                        for field in ("status", "draftVersion", "taskCount", "failedTaskCount", "failedAttemptCount"):
                            assert rows[0][field] == view["overview"][0][field], field
                elif panel["id"] == 8:
                    assert rows[0]["count"] == 3
                elif panel["id"] == 2:
                    assert 0 <= rows[0]["age"] < 10
        expert_query = next(panel for panel in dashboard["panels"] if panel["id"] == 4)["targets"]
        assert all(row["agent"] == "alpha" for row in records(query(expert_query, key, "alpha")["A"]))
        report.append({"revision": view["sourceRevision"], "draftVersion": view["overview"][0]["draftVersion"],
                       "failedAttempts": view["overview"][0]["failedAttemptCount"], "nodes": len(view["nodes"]), "edges": len(view["edges"])})

    if args.fault_tests:
        controls = ROOT / "runtime/observation-controls"
        controls.mkdir(parents=True, exist_ok=True)
        age = controls / "age-seconds"
        empty = controls / "empty-discovery"
        outage = controls / "unavailable"
        assert not any(path.exists() for path in (age, empty, outage)), "Clear existing controls before fault tests"
        age_query = next(panel for panel in dashboard["panels"] if panel["id"] == 2)["targets"]
        count_query = next(panel for panel in dashboard["panels"] if panel["id"] == 8)["targets"]
        try:
            age.write_text("120\n")
            assert 119 <= records(query(age_query, boards[0]["__value"])["A"])[0]["age"] < 130
            age.unlink()
            empty.touch()
            assert records(query(count_query)["A"])[0]["count"] == 0
            empty.unlink()
            outage.touch()
            result = query(age_query, boards[0]["__value"])["A"]
            assert result.get("error") and result.get("status") != 200
            assert not any(frame["data"]["values"] for frame in result.get("frames", [])), "Outage returned successful stale rows"
        finally:
            for path in (age, empty, outage):
                path.unlink(missing_ok=True)
        assert 0 <= records(query(age_query, boards[0]["__value"])["A"])[0]["age"] < 10
    print(json.dumps({"boards": report, "faultTests": args.fault_tests, "result": "passed"}, indent=2))


if __name__ == "__main__":
    main()
