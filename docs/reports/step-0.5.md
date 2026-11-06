# Step 0.5: Benchmark Runner

## What was built

In `src/test/java/org/supermas/benchmark`, so that none of it enters the packaged jar:

- `Dataset` loads paired `.eml` and `.gold.json` files, rejects mismatches, and computes a SHA-256 hash of the data.
- `Predictor` is the interface for each variant. It receives only raw email bytes and never the labels.
- `Scorer` computes per-field precision, recall, F1, null accuracy and macro F1.
- `BenchmarkRunner` writes a Markdown report overall and per language, with latency and failure counts.

`pom.xml` adds `jackson-databind` 2.18.2 with test scope, and `exec-maven-plugin` 3.5.0 to run the benchmark:

```bash
mvn -q test-compile exec:java -Dexec.args="benchmark/data/synthetic-dev target/benchmark/baseline-empty.md empty"
```

## Why

- **The dataset hash** shows whether two reports used the same data.
- **Undefined values show as `n/a`**, such as precision with no predictions, so they cannot be mistaken for real scores.
- **A predictor failure counts as an empty prediction**, so failures lower scores and are also counted.
- **Later comparisons:** McNemar's test from the plan needs paired outcomes per email. The runner keeps predictions per email, but the test itself is not built yet.

## Results

`ScorerTest` and `BenchmarkRunnerTest` pass, covering a perfect prediction, an empty prediction, invented values, duplicates, wrong entity types, accent handling, predictor failure and mismatched files.

## Known limitations

- **Peak heap is not meaningful yet.** `exec:java` runs inside Maven's JVM, so the number includes Maven. It must run in a separate JVM before memory is compared in step 1.10.
- **Latency:** one run over a small dataset has no warm-up. Numbers are rough until real predictors run with warm-up.
