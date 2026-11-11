"""Compare two configurations of the reply-quality benchmark over repeated runs, email by email.

    python3 -I scripts/benchmark/compare_runs.py --a RUN_DIR [RUN_DIR ...] --b RUN_DIR [RUN_DIR ...] \
        [--names A B] [--out REPORT.md]

Each RUN_DIR is a label folder of `ReplyQualityBenchmark` (it holds `scores.jsonl`). Every run may contain
several repeats of a set (multi is run twice by default). For each reply-expected email the script averages
each measure over all runs and repeats of a configuration, then compares the two configurations on the same
emails: the mean difference per email, a 95 % bootstrap interval over emails (10,000 resamples, fixed seed),
and how many emails got better, worse or stayed equal. Standard library only; no email text is written.

Measures per reply-expected email (one run): facts mentioned, asks made (counts of the frozen checks), drafted
(0/1), clean (drafted with no violation, 0/1), greeting right (drafted and right greeting, 0/1).
"""
import argparse
import json
import random
from collections import defaultdict
from pathlib import Path

MEASURES = ["facts", "asks", "drafted", "clean", "greeting"]
SEED = 20261111
RESAMPLES = 10_000


def per_email(run_dirs):
    """{(set, email): {measure: mean over every run and repeat}} for reply-expected emails, and the run count."""
    sums = defaultdict(lambda: defaultdict(float))
    seen = defaultdict(int)
    gold = {}
    for run in run_dirs:
        for line in (Path(run) / "scores.jsonl").read_text(encoding="utf-8").splitlines():
            row = json.loads(line)
            if not row["replyExpected"]:
                continue
            key = (row["set"], row["email"])
            drafted = row["status"] == "AWAITING_REVIEW"
            values = {
                "facts": len(row["mentioned"]),
                "asks": len(row["asked"]),
                "drafted": 1 if drafted else 0,
                "clean": 1 if drafted and not row["violations"] else 0,
                "greeting": 1 if drafted and row["greetingOk"] else 0,
            }
            gold[key] = (len(row["mentioned"]) + len(row["notMentioned"]), len(row["asked"]) + len(row["notAsked"]))
            for measure, value in values.items():
                sums[key][measure] += value
            seen[key] += 1
    means = {key: {m: sums[key][m] / seen[key] for m in MEASURES} for key in sums}
    return means, gold


def bootstrap(differences):
    rng = random.Random(SEED)
    n = len(differences)
    totals = sorted(sum(rng.choice(differences) for _ in range(n)) for _ in range(RESAMPLES))
    return totals[int(0.025 * RESAMPLES)], totals[int(0.975 * RESAMPLES) - 1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--a", nargs="+", required=True)
    parser.add_argument("--b", nargs="+", required=True)
    parser.add_argument("--names", nargs=2, default=["A", "B"])
    parser.add_argument("--out")
    args = parser.parse_args()
    a, gold = per_email(args.a)
    b, _ = per_email(args.b)
    keys = sorted(set(a) & set(b))
    if not keys:
        raise SystemExit("The two configurations share no reply-expected email")
    name_a, name_b = args.names
    facts_total = sum(gold[k][0] for k in keys)
    asks_total = sum(gold[k][1] for k in keys)
    totals = {"facts": facts_total, "asks": asks_total, "drafted": len(keys), "clean": len(keys), "greeting": len(keys)}
    lines = [f"# Paired comparison: {name_a} vs {name_b}", "",
             f"{name_a}: {len(args.a)} run(s): {', '.join(Path(r).name for r in args.a)}. "
             f"{name_b}: {len(args.b)} run(s): {', '.join(Path(r).name for r in args.b)}. "
             f"{len(keys)} reply-expected emails present in both; each email's value is its mean over all runs and "
             f"repeats of a configuration. Interval: 95 % bootstrap over emails ({RESAMPLES:,} resamples, seed {SEED}) "
             f"of the summed difference. An interval that contains 0 is no evidence of a difference.", "",
             f"| Measure (out of) | {name_a} | {name_b} | Difference {name_b} − {name_a} | 95 % interval | Emails better / worse / equal |",
             "| --- | --- | --- | --- | --- | --- |"]
    for measure in MEASURES:
        diffs = [b[k][measure] - a[k][measure] for k in keys]
        low, high = bootstrap(diffs)
        sum_a = sum(a[k][measure] for k in keys)
        sum_b = sum(b[k][measure] for k in keys)
        better = sum(d > 1e-9 for d in diffs)
        worse = sum(d < -1e-9 for d in diffs)
        lines.append(f"| {measure} ({totals[measure]}) | {sum_a:.1f} | {sum_b:.1f} | {sum_b - sum_a:+.1f} | "
                     f"{low:+.1f} to {high:+.1f} | {better} / {worse} / {len(keys) - better - worse} |")
    report = "\n".join(lines) + "\n"
    if args.out:
        Path(args.out).write_text(report, encoding="utf-8")
    print(report)


if __name__ == "__main__":
    main()
