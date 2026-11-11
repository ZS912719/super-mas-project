"""Summary of the human rubric scores of one real-mail run (plan W6/W9), written by the labelling page.

    python3 -I scripts/benchmark/rubric_stats.py RUN_DIR

Reads `RUN_DIR/rubric/<email>.<rater>.json` and prints, per rater and criterion, how many emails got each
level and the mean of the numeric levels (n/a left out). With two raters it also prints, per criterion, the
exact agreement and Cohen's kappa with quadratic weights on the emails both scored numerically. Prints
counts and scores only, no email or reply text. Standard library only.
"""
import json
import sys
from collections import defaultdict
from pathlib import Path

CRITERIA = ["polite", "answers", "inventsNothing", "admitsLimits"]
LEVELS = ["0", "1", "2"]


def weighted_kappa(pairs):
    """Cohen's kappa with quadratic weights for ordinal levels 0-2; None when undefined."""
    n = len(pairs)
    if n == 0:
        return None
    k = len(LEVELS)
    weight = lambda i, j: 1 - ((i - j) ** 2) / ((k - 1) ** 2)
    observed = sum(weight(int(a), int(b)) for a, b in pairs) / n
    left = [sum(1 for a, _ in pairs if a == level) / n for level in LEVELS]
    right = [sum(1 for _, b in pairs if b == level) / n for level in LEVELS]
    expected = sum(left[i] * right[j] * weight(i, j) for i in range(k) for j in range(k))
    return None if expected == 1 else (observed - expected) / (1 - expected)


def main():
    run = Path(sys.argv[1])
    scores = defaultdict(dict)  # rater -> email -> scores
    for path in sorted((run / "rubric").glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        scores[data["rater"]][data["email"]] = data["scores"]
    if not scores:
        raise SystemExit(f"No rubric scores in {run / 'rubric'}")
    print(f"Run {run.name}")
    for rater, emails in sorted(scores.items()):
        print(f"\nRater {rater}: {len(emails)} emails")
        print(f"{'criterion':16} {'0':>3} {'1':>3} {'2':>3} {'n/a':>4}  mean")
        for criterion in CRITERIA:
            values = [s.get(criterion) for s in emails.values()]
            counts = {level: values.count(level) for level in LEVELS + ["n/a"]}
            numeric = [int(v) for v in values if v in LEVELS]
            mean = f"{sum(numeric) / len(numeric):.2f}" if numeric else "-"
            print(f"{criterion:16} {counts['0']:>3} {counts['1']:>3} {counts['2']:>3} {counts['n/a']:>4}  {mean}")
    raters = sorted(scores)
    if len(raters) == 2:
        a, b = raters
        both = sorted(set(scores[a]) & set(scores[b]))
        print(f"\nAgreement {a} vs {b} on {len(both)} emails scored by both")
        for criterion in CRITERIA:
            pairs = [(scores[a][e].get(criterion), scores[b][e].get(criterion)) for e in both]
            numeric = [(x, y) for x, y in pairs if x in LEVELS and y in LEVELS]
            exact = sum(1 for x, y in pairs if x == y)
            kappa = weighted_kappa(numeric)
            print(f"{criterion:16} exact {exact}/{len(pairs)}  weighted kappa "
                  f"{'-' if kappa is None else f'{kappa:.2f}'} (n={len(numeric)})")
    elif len(raters) > 2:
        print("\nMore than two raters: agreement not computed")


if __name__ == "__main__":
    main()
