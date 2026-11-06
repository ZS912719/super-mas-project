"""Select a labelling sample across all Enron mailboxes, and split imported files into dev and test.

Two subcommands, run with the importer between them:

    python3 -I scripts/labeling/sample_enron.py select MAILDIR SELECTED_DIR COUNT SEED
    python3 -I scripts/labeling/sample_enron.py split IMPORTED_DIR OUT_DIR TEST_COUNT SEED

Prints counts only, never email content, so the test set stays unseen by whoever runs it.
Selection is deterministic for a given corpus and seed. See docs/reports/labeling-setup.md.
"""
import argparse
import email
import email.policy
import hashlib
import json
import random
import re
import shutil
from collections import Counter
from pathlib import Path

MIN_BODY_CHARS = 40
MAX_BODY_CHARS = 6000
SUBJECT_PREFIX = re.compile(r"^\s*((re|fw|fwd)\s*:\s*)+", re.IGNORECASE)
EXCLUDED_SENDERS = {"no.address@enron.com"}


def inspect(path):
    """Returns (subject key, body hash) if the message is eligible, otherwise a rejection reason."""
    try:
        message = email.message_from_bytes(path.read_bytes(), policy=email.policy.compat32)
        sender = (message.get("From") or "").strip().lower()
        if not sender:
            return "no_sender"
        if sender in EXCLUDED_SENDERS:
            return "excluded_sender"
        payload = message.get_payload(decode=True) or b""
    except Exception:  # A corpus file that will not parse is simply not eligible.
        return "unparseable"
    body = " ".join(payload.decode("latin-1").split())
    if not MIN_BODY_CHARS <= len(body) <= MAX_BODY_CHARS:
        return "body_length"
    subject = SUBJECT_PREFIX.sub("", str(message.get("Subject") or "")).strip().lower()
    return subject, hashlib.sha256(body.encode()).hexdigest()


def select(args):
    rng = random.Random(args.seed)
    target = Path(args.selected_dir)
    if target.exists():
        raise SystemExit(f"{target} already exists; refusing to mix samples")
    target.mkdir(parents=True)

    queues = {}
    for mailbox in sorted(p for p in Path(args.maildir).iterdir() if p.is_dir()):
        files = sorted(p for p in mailbox.rglob("*") if p.is_file())
        rng.shuffle(files)
        queues[mailbox.name] = iter(files)

    subjects, bodies, rejected = set(), set(), Counter()
    per_mailbox = Counter()
    picked = 0
    while picked < args.count and queues:
        # Round robin, so no mailbox can dominate the sample.
        for name in list(queues):
            if picked == args.count:
                break
            for path in queues[name]:
                result = inspect(path)
                if isinstance(result, str):
                    rejected[result] += 1
                    continue
                subject, body_hash = result
                # A repeated subject could be the same conversation, which would leak across a split.
                if body_hash in bodies or (subject and subject in subjects):
                    rejected["duplicate_or_same_subject"] += 1
                    continue
                bodies.add(body_hash)
                if subject:
                    subjects.add(subject)
                picked += 1
                per_mailbox[name] += 1
                shutil.copyfile(path, target / f"{picked:04d}-{name}.eml")
                break
            else:
                del queues[name]

    print(json.dumps({"selected": picked, "mailboxes": len(per_mailbox),
                      "maxPerMailbox": max(per_mailbox.values()),
                      "rejected": dict(rejected)}))


def split(args):
    imported, out = Path(args.imported_dir), Path(args.out_dir)
    manifest = json.loads((imported / "manifest.json").read_text())
    threads = {}
    for entry in manifest:
        threads.setdefault(entry["threadId"], []).append(entry)
    order = sorted(threads)
    random.Random(args.seed).shuffle(order)

    test_ids, test_messages = set(), 0
    for thread in order:  # Whole threads only, so a thread never spans the split.
        if test_messages >= args.test_count:
            break
        test_ids.add(thread)
        test_messages += len(threads[thread])

    counts = Counter()
    for name in ("dev", "test"):
        (out / name).mkdir(parents=True, exist_ok=True)
    for entry in manifest:
        name = "test" if entry["threadId"] in test_ids else "dev"
        stem = entry["file"].removesuffix(".eml")
        for suffix in (".eml", ".gold.todo.json"):
            shutil.move(str(imported / (stem + suffix)), str(out / name / (stem + suffix)))
        counts[name] += 1
    print(json.dumps({"threads": len(threads), **counts}))


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(required=True)
    p = commands.add_parser("select")
    p.add_argument("maildir")
    p.add_argument("selected_dir")
    p.add_argument("count", type=int)
    p.add_argument("seed", type=int)
    p.set_defaults(run=select)
    p = commands.add_parser("split")
    p.add_argument("imported_dir")
    p.add_argument("out_dir")
    p.add_argument("test_count", type=int)
    p.add_argument("seed", type=int)
    p.set_defaults(run=split)
    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
