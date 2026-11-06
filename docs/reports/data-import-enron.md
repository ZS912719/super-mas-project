# Data import: Enron

## What was built

`src/test/java/org/supermas/benchmark/dataset`:

- `ParsedMessage` parses one raw message with Jakarta Mail and extracts facts (sender, recipients, date, references, Message-ID).
- `EnronImporter` reads a folder of raw messages, deduplicates by Message-ID, groups messages into threads, samples a fixed number of threads with a seed, and writes the result.

`pom.xml` adds `org.eclipse.angus:angus-mail` 2.0.3 with test scope. `.gitignore` now excludes the folders that hold real mail.

## How to run it

The Enron corpus is not downloaded, and it must not be committed. Download it yourself:

```bash
mkdir -p benchmark/data/enron-raw && cd benchmark/data/enron-raw
curl -LO https://www.cs.cmu.edu/~enron/enron_mail_20150507.tar.gz
tar xzf enron_mail_20150507.tar.gz    # creates maildir/
```

Then import a sample:

```bash
mvn -q test-compile exec:java -Dexec.mainClass=org.supermas.benchmark.dataset.EnronImporter \
  -Dexec.classpathScope=test \
  -Dexec.args="benchmark/data/enron-raw/maildir benchmark/data/enron 120 20260101"
```

This writes about 120 threads of `.eml` files to `benchmark/data/enron` (an ignored folder).

## Design choices and why

- **An independent parser for gold facts.** The MetadataAgent (step 1.2) will parse with mime4j. If it also produced the gold labels, it would grade itself. Jakarta Mail is a different parser, so its facts are an independent reference. They still need a human spot-check.
- **The output is not a loadable dataset.** Each `.eml` gets a `.gold.todo.json`, not a `.gold.json`. `Dataset` only pairs `.eml` with `.gold.json`, so it refuses the folder until a person reviews each template and renames it. A test confirms this. This stops unlabeled data being scored by accident, which would otherwise show as meaningless entity numbers.
- **Threads kept whole.** Grouping follows Message-ID reference links (RFC 5256 stage one), using union-find. A whole thread is sampled together, so later a thread cannot be split across training and test. The thread id is the earliest message's id.
- **Deterministic sampling.** Thread order is sorted then shuffled with a seed, so the same seed always picks the same threads, whatever order the filesystem returns.
- **Nothing is dropped silently.** A message with no Message-ID gets a synthetic id from its content hash. A message that will not parse keeps its bytes and records a warning. All warnings go to `manifest.json`.

## What this does not do

- **No labels.** It fills only facts. `currentText`, entities, speech acts and role are left for a person, because they need quote and signature removal (step 1.4) and judgment.
- **No subject-based threading.** Enron messages often lack reference headers, so many threads will be single messages. Subject-based merging (RFC 5256 stage two) is error-prone and is not done; this undercounts true threads.
- **No filtering of automated mail.** Bulk and notification mail is kept. Add filtering during review if needed.
- **English only.** Enron is English, so the template sets `language` to `en` for review. All French benchmark data must come from another source.

## Status and open points

- Tested on invented fixtures only. I have not run it on the real corpus.
- **Personal mail is blocked.** The Microsoft 365 export did not work and the administrator has been asked. Until that is resolved, French real-mail data is missing. Decide whether to wait or choose a public French source.
- The data-protection question about personal mail still stands before any benchmarking on it.
