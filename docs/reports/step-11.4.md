# Step 11.4: The First Runnable Prototype

## Outcome

`PrototypeApp` takes real `.eml` files through the whole system to drafts awaiting review on the durable blackboard, keeps all state between runs, and projects every board to Markdown that Obsidian can open. On the 24 synthetic emails: **24 of 24 drafts awaiting review, in about 6 seconds** (including Maven start-up) on the development laptop. `mvn test`: **300 tests, 0 failures**. Commit: `fbc414e`.

## How to run it

```bash
mvn -q compile exec:java -Dexec.mainClass=org.supermas.prototype.PrototypeApp \
  -Dexec.args="benchmark/data/synthetic-dev"
```

| Argument | Default | Meaning |
| --- | --- | --- |
| `EMAILS` (required) | — | One `.eml` file, or a folder whose `.eml` files are read in name order. Other files are skipped |
| `--knowledge DIR` | `src/test/resources/university/knowledge` | A knowledge folder with `sources.json` (step 8.5) |
| `--data DIR` | `runtime` (Git-ignored) | `blackboard.db` (SQLite), `super-mas-vault/` (Markdown), `index/` (Lucene: knowledge and history), `topics/` (discovered topics) |
| `--project ID` | `uni` | The project of the emails, as in the sample knowledge |

Open `<data>/super-mas-vault` as a vault in Obsidian; each board's `current.md` links to its latest snapshot. Running again on the same `--data` keeps history and topics.

## What one email goes through

```
.eml ─ MimeEmailParser ─▶ LeasedCoordinator.receive
        MetadataAgent (rules) ─▶ DefaultBlackboardFactory.create (SQLite)  ─▶ ConversationRecorder: RECEIVED
      LeasedCoordinator.run, one leased task per action
        RETRIEVE_CONTEXT  context-discussion     thread history            ─┐ private, then promoted
        RETRIEVE_CONTEXT  context-discovered     best 2 discovered topics  ─┤ by EvidencePromotion
        RETRIEVE_CONTEXT  missing-information    what the email lacks      ─┘
        ROUTE             5 leased assessments ─▶ ScoreRoutingStrategy(0.4, 0.1)
        GENERATE          chosen role: own search, ReplyPlan, prompt ─▶ TemplateGenerationService ─▶ draft v1
                          ─▶ ConversationRecorder: GENERATED_DRAFT
        PRESENT_DRAFT     AWAITING_REVIEW. Nothing is approved or sent.
      MarkdownProjector.reconcile ─▶ super-mas-vault/generated/<board>/current.md
```

## What was built

| Item | Behaviour |
| --- | --- |
| `prototype/PrototypeApp` (prototype wiring) | Parses options, wires every part as `EndToEndTest` does but on disk, processes the emails, prints per email the status, routing with non-zero scores, the plan (requests, missing fields, context size) and the draft, then reconciles the Markdown projection and prints each board's `current.md`. Regroups topics on start-up from stored history, then every 25 new messages |
| `PrototypeAppTest` | Three emails into a temporary folder: three drafts awaiting review, database and vault exist, a non-`.eml` file is skipped; a second run on the same data finds the first run's mail as history (more evidence). Bad arguments are refused |

## Results on `benchmark/data/synthetic-dev` (24 emails, 12 English, 12 French)

| Measure | First run (empty data) | Second run (same data) |
| --- | --- | --- |
| Drafts awaiting review | 24 of 24 | 24 of 24 |
| Discovered topics at start | 0 | 4 |
| Routing `CLEAR` / `NONE` | 5 / 19 | 5 / 19 (unchanged) |
| Context items in the plan of `en-01` | 3 | 9 |

Routing is **exactly the step 9.8 default result**: `CLEAR` to student 2, teacher 1, research 1, administration 1, and 19 emails to the Generic fallback (15 of 24 correct, because 10 emails are Generic in the gold labels). So the durable board and the leased coordinator reproduce the benchmarked behaviour. The low `CLEAR` rate is the known cautious routing of step 9.8 (rules-only role hint), not a new defect; with the gold role hint it was 24 of 24.

The second run had more context because the first run's 24 emails were stored as history. **This also means a replayed email can see later messages of its own thread**, which a real mailbox never offers. Measurements must use a fresh `--data` folder.

## Choices and why

| Choice | Why |
| --- | --- |
| A Java entry point, no new script | The teammate's scripts are PowerShell; this laptop runs Linux. `mvn exec:java` works on both. A script can follow once the team picks a launcher |
| `runtime/` as the default data folder | The teammate's convention, already Git-ignored. Our Lucene index and topic catalogue live beside their database |
| Rules-only metadata | GLiNER and SetFit models are Git-ignored and optional. Rules make the prototype run on any checkout |
| Template generation | Project owner's decision: no model for now, a local model as the perspective. Replacing it needs only another `GenerationService` in `RoleServices.withGeneration` |
| Sample knowledge from test resources by default | The only knowledge folder in the repository. Real knowledge is passed with `--knowledge` |

## Problems encountered

1. **No topics formed on the first run.** 24 emails is under the 25-message regrouping interval, and too few threads for discovery (step 9.7: about 15-20 threads or 4 subjects at least). On the second run, the start-up refresh formed 4 topics from stored history. Expected behaviour, not changed.

## Critical points

- **French emails go through, but French is deferred** (plan decision). Their routing relies on keywords only.
- **The draft is a template**, not a reply. It shows the plan's structure for the reviewer.
- **The prototype is single-user and sequential.** One coordinator, one email at a time.
- **No transport, no approval.** Drafts stay `PROPOSED`. The mailbox has approval and sending; connecting it is step 11.6.
