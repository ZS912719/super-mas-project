# Step 1.9: MetadataAgent

## Outcome

`MetadataAgent implements Preprocessor` now combines every Phase 1 stage, with a separate switch for the entity model and the speech-act model. It uses no LLM. It works with no model files, and if a model fails during a call it falls back to rules and reports the failure.

## What was built

| Class | Role |
| --- | --- |
| `metadata/MetadataAgent` | Strip, detect language, extract entities, classify speech acts and role hint, return an `Analysis` |
| `MetadataAgentTest` | Rules-only use, quoted text, empty bodies, model failure, switches, entity merging |
| `benchmark/MetadataPredictor` | Scores the real agent from end to end |
| `MetadataAgent.hybrid` | The entity merge rule, moved from the benchmark into production so that only one copy exists |

`BenchmarkRunner` gains `metadata-rules`, `metadata-entity-model` and `metadata-all-models`, and loses `labels-model`, whose only result was in-sample.

## How output maps to `Analysis`

| Field | Content |
| --- | --- |
| `keywords` | Subject words without common stop words, plus course names |
| `initialTags` | `lang:en`, `act:request`, `role:student` |
| `metadata.currentText` | The sender's new text |
| `metadata.language` | `en` or `fr` |
| `metadata.entities` | JSON list with type, text, confidence and source |
| `metadata.speechActs` | JSON list with act, confidence and source |
| `metadata.roleHint` | Present only when there is evidence |
| `metadata.failedStages` | JSON list, such as `["entityModel"]` |

## Verification

- 58 tests, 0 failures, 0 skipped.
- The moved merge rule is unchanged: all five entity types match the step 1.6 hybrid report on every count.
- Quality, memory and latency are in `step-1.10.md`.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Keep the shared `Analysis` record unchanged and use JSON in `metadata` | Shared interfaces need team validation. Confidence and source survive this way | Applying proposal 1 now: not needed yet, and the team has not validated it |
| A model is switched on by passing it in as an `Optional` | No flags to misread, and "rules only" needs no model code to load | Boolean flags with model paths inside the agent: mixes configuration with analysis |
| Fall back to rules on model failure and report it | One failure should not lose the email's analysis, and silence would hide it | Throwing: the Preprocessor would give nothing. Silent fallback: the coordinator could not know |
| When the model fails, use every rule entity type | Otherwise PERSON and COURSE would vanish entirely | Keeping the hybrid filter |
| Parsing is outside the agent | `Preprocessor.analyze` receives a parsed `Email` | Changing the contract to raw bytes |
| Speech-act model replaces rules when on, with no merging | Simple and measurable | Merging: untested, and it would be a new design choice on provisional labels |
| Benchmark treats a failed stage as a predictor failure | Fallback must not hide failures in scores | Scoring the fallback silently |

## Problems encountered

1. `labels-model` had to be removed so that the runner stays within the ten entries of `Map.of`. It was also the variant most likely to be misread, because it reports in-sample scores.
2. A compiled Python cache folder appeared under `scripts/training`, from a syntax check of mine. I deleted it.

## Critical points

- **`metadata` is an untyped contract.** Readers depend on key names and JSON form. Proposal 1 would make this safer once the team validates it.
- **Not wired to a coordinator.** No coordinator exists yet, so `failedStages` has no consumer, and the agent's only callers are tests and the benchmark.
- **Thread-safety is untested.** Sharing one agent across threads depends on ONNX sessions, the tokenizer and the regex classes. Scheduling belongs to the team, so this must be checked before concurrent use.
- **`currentText` goes into `metadata` as untrusted text.** Any later prompt must treat it as `untrustedData`.
- **Keywords are simple.** The subject is attacker-controlled, so a crafted subject can influence routing candidates. Routing must not treat keywords as permission.
- **Hybrid type sets are tuned on dev** (see step 1.6).
- **Long emails are not capped.** GLiNER handles the whole of `currentText` in chunks, so very long mail takes proportionally longer. Budgets belong to the coordinator, but this should be raised with the team.
