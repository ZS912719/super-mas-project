"""Turn LLM pre-labels into draft gold files, verifying each one against its email.

    python3 -I scripts/labeling/build_prelabels.py build DEV_DIR PRELABELS_JSON
    python3 -I scripts/labeling/build_prelabels.py promote DEV_DIR

`build` writes `.gold.draft.json`, which `Dataset` will not load. `currentText` is cut from the raw
body between two anchors, so it cannot contain invented text. Every entity and act quote must occur
in it, otherwise the build fails. See docs/reports/labeling-setup.md.

`promote` writes `.gold.json` for unflagged drafts, and for flagged drafts (non-empty `_attention`)
only once a person has marked them `"_reviewed": true`.

    python3 -I scripts/labeling/build_prelabels.py audit-compare AUDIT_DIR TEST_DIR
    python3 -I scripts/labeling/build_prelabels.py finalize-test AUDIT_DIR TEST_DIR

`audit-compare` reports agreement between hand labels and LLM labels on the audit emails.
`finalize-test` writes the test set's `.gold.json`, using the person's label where one exists.
"""
import argparse
import email
import json
import re
import sys
from collections import Counter
from pathlib import Path

ENTITY_TYPES = {"PERSON", "COURSE", "DATE", "DEADLINE", "ID"}
ACTS = {"request", "propose", "commit", "deliver"}
ROLES = {"student", "teacher", "administration", "researcher"}
DRAFT_SUFFIX = ".gold.draft.json"


def flexible(text):
    """A regex for `text` that ignores whitespace differences, such as line wrapping."""
    return r"\s+".join(re.escape(word) for word in text.split())


def normalized(text):
    return " ".join(text.split()).lower()


def current_text(body, label):
    if not label["s"]:
        return ""
    start = re.search(flexible(label["s"]), body)
    if not start:
        raise ValueError(f"start anchor not found: {label['s']!r}")
    # The anchor may be the whole text, so the end search begins at the start of the match.
    end = re.search(flexible(label["e"]), body[start.start():])
    if not end:
        raise ValueError(f"end anchor not found: {label['e']!r}")
    text = body[start.start():start.start() + end.end()]
    for phrase in label.get("drop", []):
        found = re.search(flexible(phrase), text)
        if not found:
            raise ValueError(f"drop phrase not found: {phrase!r}")
        text = text[:found.start()] + text[found.end():]
    return text.strip()


def build_one(stem, directory, label):
    message = email.message_from_bytes((directory / f"{stem}.eml").read_bytes())
    body = (message.get_payload(decode=True) or b"").decode("latin-1")
    template = json.loads((directory / f"{stem}.gold.todo.json").read_text())
    text = current_text(body, label)
    haystack = normalized(text)

    entities = []
    for kind, values in label.get("ents", {}).items():
        if kind not in ENTITY_TYPES:
            raise ValueError(f"unknown entity type {kind}")
        for value in values:
            if normalized(value) not in haystack:
                raise ValueError(f"entity not in currentText: {value!r}")
            entities.append({"type": kind, "text": value})
    for act, quote in label.get("acts", {}).items():
        if act not in ACTS:
            raise ValueError(f"unknown act {act}")
        if normalized(quote) not in haystack:
            raise ValueError(f"quote for {act} not in currentText: {quote!r}")
    role = label.get("role")
    if role is not None:
        if role not in ROLES:
            raise ValueError(f"unknown role {role}")
        if normalized(label["roleEvidence"]) not in haystack:
            raise ValueError(f"role evidence not in currentText: {label['roleEvidence']!r}")

    attention = []
    if label.get("note"):
        attention.append(label["note"])
    if not text:
        attention.append("empty currentText: the field schema does not allow this yet")
    if template["sender"] is None:
        attention.append("sender is null: the parser could not read a single address")
    if not template["recipients"]:
        attention.append("recipients are empty: the field schema does not allow this")

    draft = {
        "_status": "LLM_DRAFT: set _reviewed to true after checking every field against the email",
        "_reviewed": False,
        "_attention": attention,
        "_evidence": {"acts": label.get("acts", {}), "role": label.get("roleEvidence")},
        "sender": template["sender"],
        "recipients": template["recipients"],
        "date": template["date"],
        "threadId": template["threadId"],
        "currentText": text,
        "entities": entities,
        "speechActs": sorted(label.get("acts", {})),
        "roleHint": role,
        "language": template["language"],
    }
    (directory / f"{stem}{DRAFT_SUFFIX}").write_text(
        json.dumps(draft, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return draft


def build(args):
    directory = Path(args.dev_dir)
    labels = json.loads(Path(args.prelabels).read_text(encoding="utf-8"))
    stems = sorted(p.name[:-4] for p in directory.glob("*.eml"))
    keys = {re.fullmatch(r"enron-t(\d{4})-m\d\d", stem).group(1): stem for stem in stems}
    errors = [f"unlabelled: {stem}" for key, stem in keys.items() if key not in labels]
    errors += [f"label for a file not in this folder: {key}" for key in labels if key not in keys]

    drafts = []
    for key, stem in keys.items():
        if key in labels:
            try:
                drafts.append(build_one(stem, directory, labels[key]))
            except ValueError as error:
                errors.append(f"{stem}: {error}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
        sys.exit(1)

    print(json.dumps({
        "drafts": len(drafts),
        "emptyCurrentText": sum(not d["currentText"] for d in drafts),
        "needAttention": sum(bool(d["_attention"]) for d in drafts),
        "acts": Counter(a for d in drafts for a in d["speechActs"]),
        "noActs": sum(not d["speechActs"] for d in drafts),
        "entities": Counter(e["type"] for d in drafts for e in d["entities"]),
        "roles": Counter(d["roleHint"] for d in drafts if d["roleHint"]),
    }))


def promote(args):
    directory = Path(args.dev_dir)
    promoted = pending = 0
    for path in sorted(directory.glob(f"*{DRAFT_SUFFIX}")):
        draft = json.loads(path.read_text(encoding="utf-8"))
        if draft["_attention"] and draft.get("_reviewed") is not True:
            pending += 1
            continue
        gold = {key: value for key, value in draft.items() if not key.startswith("_")}
        target = directory / path.name.replace(DRAFT_SUFFIX, ".gold.json")
        target.write_text(json.dumps(gold, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        promoted += 1
    print(json.dumps({"promoted": promoted, "stillPending": pending}))


def entity_set(gold):
    return {(e["type"], normalized(e["text"])) for e in gold["entities"]}


def f1(left, right):
    if not left and not right:
        return 1.0
    return 2 * len(left & right) / (len(left) + len(right))


def kappa(pairs):
    """Cohen's kappa over paired yes/no decisions, or None when chance agreement is total."""
    observed = sum(a == b for a, b in pairs) / len(pairs)
    a_yes = sum(a for a, _ in pairs) / len(pairs)
    b_yes = sum(b for _, b in pairs) / len(pairs)
    chance = a_yes * b_yes + (1 - a_yes) * (1 - b_yes)
    return None if chance == 1 else (observed - chance) / (1 - chance)


def load_audit_pairs(audit_dir, test_dir):
    ids = json.loads((audit_dir / "audit-ids.json").read_text())
    missing = [stem for stem in ids if not (audit_dir / f"{stem}.gold.json").exists()]
    if missing:
        raise SystemExit(f"{len(missing)} of {len(ids)} audit emails are not labelled yet")
    return ids, [(json.loads((audit_dir / f"{stem}.gold.json").read_text(encoding="utf-8")),
                  json.loads((test_dir / f"{stem}{DRAFT_SUFFIX}").read_text(encoding="utf-8")))
                 for stem in ids]


def audit_compare(args):
    _, pairs = load_audit_pairs(Path(args.audit_dir), Path(args.test_dir))
    report = {
        "emails": len(pairs),
        "currentTextExactAgreement": sum(
            normalized(h["currentText"]) == normalized(l["currentText"]) for h, l in pairs) / len(pairs),
        "entityMeanF1": sum(f1(entity_set(h), entity_set(l)) for h, l in pairs) / len(pairs),
        "roleHintAgreement": sum(h["roleHint"] == l["roleHint"] for h, l in pairs) / len(pairs),
        "speechActKappa": {},
    }
    for act in sorted(ACTS):
        decisions = [(act in h["speechActs"], act in l["speechActs"]) for h, l in pairs]
        report["speechActKappa"][act] = {
            "kappa": kappa(decisions),
            "humanYes": sum(a for a, _ in decisions),
            "llmYes": sum(b for _, b in decisions),
        }
    print(json.dumps(report, indent=2))


def finalize_test(args):
    audit_dir, test_dir = Path(args.audit_dir), Path(args.test_dir)
    ids, pairs = load_audit_pairs(audit_dir, test_dir)
    written = 0
    for path in sorted(test_dir.glob(f"*{DRAFT_SUFFIX}")):
        draft = json.loads(path.read_text(encoding="utf-8"))
        gold = {key: value for key, value in draft.items() if not key.startswith("_")}
        stem = path.name.replace(DRAFT_SUFFIX, "")
        if stem in ids:  # The person's label is final for audited emails.
            gold = json.loads((audit_dir / f"{stem}.gold.json").read_text(encoding="utf-8"))
        (test_dir / f"{stem}.gold.json").write_text(
            json.dumps(gold, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        written += 1
    print(json.dumps({"written": written, "humanLabels": len(ids), "llmLabels": written - len(ids)}))


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(required=True)
    for name, run in (("audit-compare", audit_compare), ("finalize-test", finalize_test)):
        p = commands.add_parser(name)
        p.add_argument("audit_dir")
        p.add_argument("test_dir")
        p.set_defaults(run=run)
    p = commands.add_parser("build")
    p.add_argument("dev_dir")
    p.add_argument("prelabels")
    p.set_defaults(run=build)
    p = commands.add_parser("promote")
    p.add_argument("dev_dir")
    p.set_defaults(run=promote)
    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
