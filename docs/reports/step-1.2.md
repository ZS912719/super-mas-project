# Step 1.2: Threading

## What was built

`org.supermas.metadata.EmailThreader` assigns a thread id to each email in one project:

1. link messages by their `References` and `In-Reply-To` headers (union-find), then
2. a subject fallback: join messages that share a base subject when at least one is a reply or forward.

The thread id is the earliest message's id, matching the field schema and the importer. The subject fallback is a constructor flag so it can be turned off and measured.

`ThreadingStats` (test tool) reports threading counts for a folder with no message contents. `pom.xml` now takes the `exec:java` main class from the `exec.mainClass` property (default `BenchmarkRunner`), so other tools can be run with `-Dexec.mainClass=...`.

## Why a subject fallback is needed

The importer run on a real Enron user folder (`arora-h`, 654 messages) found **every thread was a single message**: real mail there carries almost no reference headers. Reference-only threading would therefore never group anything. `ThreadingStats` on the same folder:

| Method | Threads |
| --- | --- |
| References only | 654 (all singletons) |
| With subject fallback | 572 |

So the fallback merged 82 messages into existing threads. This is the main reason the fallback exists.

## Results

- **Synthetic set: 24 / 24** thread ids match the independently set gold, including the two headerless replies (`en-03`, `fr-03`) that only the subject links.
- A test shows the fallback's effect directly: with it, the headerless reply joins its thread; without it, the reply stands alone.
- A test records that two projects are threaded independently, so equal thread ids in different projects stay isolated.

## Risks and limits

- **The subject fallback can over-merge.** Two unrelated threads that share a generic base subject merge once one of them is a reply. On Enron this likely happens with subjects like "Re: meeting". Threading precision is not the product (context retrieval is), but this will add noise; the flag allows turning it off, and a future step could add participant overlap as a stronger signal.
- **Cross-folder threads are not reconstructed.** A real thread is split across senders' folders. This step threads whatever set it is given; combining folders is a data-preparation choice, not done here.
- **Measured on one Enron user only**, to stay within memory on this laptop, and on the self-authored synthetic set. These numbers are indicative, not a proper evaluation; a labelled threading test would be needed for that.
