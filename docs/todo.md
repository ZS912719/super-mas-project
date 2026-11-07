# To-Do: a Fully Compliant, Benchmarked and Tested Project

Derived from the [compliance check](compliance.md) and the [code analysis](code-analysis.md), state of 2026-11-07. Each item has an acceptance criterion. **Owner:** *T* = this member (Thomas), *S* = teammate, *Team* = all members. Priorities: **P1** needed for the deliverables and core requirements, **P2** strongly expected by the assessment criteria, **P3** quality and extensions.

## 1. Deliverables (statement §16-18)

| # | Item | Acceptance | Owner | Prio |
| --- | --- | --- | --- | --- |
| 1.1 | Final report with the 16 required sections | Every item of §16.2 present; the 14 design questions of §14 answered (draft answers in compliance §3); results with caveats | Team | P1 |
| 1.2 | "Contributions of group members" table | Names, student numbers, parts, tasks, reflecting Git history | Team | P1 |
| 1.3 | UML updated for steps 8-12 | Class diagrams for `agent.role`, `agent.collaboration`, `control`, `llm`; a sequence diagram of one email on the blackboard (requests → context → contributions → compose → review); an organization diagram of the agents and levels; `docs/uml/README.md` counts updated; SVG and PNG regenerated | T (+S for storage) | P1 |
| 1.4 | README check from a clean clone | Someone else compiles, runs `PrototypeApp`, runs the mailbox demo and configures a local model from the README alone | Team | P1 |

## 2. Local LLM (statement §8, §15, §19)

| # | Item | Acceptance | Owner | Prio |
| --- | --- | --- | --- | --- |
| 2.0 | First online run (step 13; connection verified on `en-01`, addressee bug fixed in the prompt, see step 13 §5b) | With an OpenRouter key and an open model that can also run locally: `synthetic-multi` recorded with `--llm-cache`; every email drafted or its failure explained; latency noted. Development evidence only | T | P1 |
| 2.1 | Install a local engine and choose a model (on a stronger computer; this laptop fits only 1.5-3 B) | Ollama (or llama.cpp) running with the same open model as 2.0 and one alternative; context window set for a 5,000-token prompt; documented in `docs/plans/llm-setup.md` | T | P1 |
| 2.2 | Run the prototype with the model | `--llm-endpoint/--llm-model` on the 24 + 12 synthetic emails: drafts produced, failure rate, latency p50/p95, timeouts recorded | T | P1 |
| 2.3 | Replace-the-model demonstration | Same run with the second model by configuration only; results compared | T | P2 |
| 2.4 | Lease heartbeat or longer leases for slow models | No task fenced during a slow generation (test with a slow fake engine) | T | P2 |
| 2.5 | Retry retryable generation failures (429, 5xx, timeout) | The coordinator retries within `ExecutionLimits.maxRetries`; test with a fake engine answering 429 then 200 | T | P2 |
| 2.6 | Delivered configuration is local | Final demo and every reported number use provider `local`; online results labelled with model and place where they appear | T | P1 |

## 3. Experiments (statement §15)

| # | Item | Acceptance | Owner | Prio |
| --- | --- | --- | --- | --- |
| 3.1 | Human review of the labels | 25 Enron test emails hand-labelled blind; the 12 multi-request gold labels reviewed; agreement reported | T | P1 |
| 3.2 | Relevance of responses | A rubric (answers every request, no invented facts, asks for missing data, tone) scored on the synthetic sets for router vs blackboard with the real model; two raters on a sample | Team | P1 |
| 3.3 | Influence of the system prompt | Same emails with the role prompts vs a neutral prompt; rubric scores and violations compared | T | P1 |
| 3.4 | Influence of context | With and without retrieval (and C1 vs topics) on reply quality, not only retrieval recall | T | P2 |
| 3.5 | Single-agent vs multi-agent | One model with one generic prompt vs the blackboard, same rubric (statement §20) | T | P2 |
| 3.6 | Contribution threshold on a fresh set | A new held-out multi-request set; choose the threshold there; report on the old set only as a check | T | P2 |
| 3.7 | Error analysis and LLM limits | Table of observed errors by type (missed request, wrong role, invented fact, disclosure, timeout) with examples | Team | P1 |
| 3.8 | Injection suite on the blackboard with the real model | The step 9.8 suite run end to end through the composer; 0 violations or each documented | T | P2 |

## 4. Interface (statement §10)

| # | Item | Acceptance | Owner | Prio |
| --- | --- | --- | --- | --- |
| 4.1 | Show which agents contributed and why | Mailbox shows contributors and the control trail for a draft (data already in the agent-service response) | S (with T) | P2 |
| 4.2 | Reject with feedback reaches the agents | A mailbox action calls a new `/v1/feedback` (wrapping `BoardReviewService.reject`); the next generation is version 2 | S + T | P2 |
| 4.3 | View, create and modify agents, prompts and context sources | A settings view listing roles; editing a prompt and the sources writes the role folder (`RoleCatalog.fromFolder`); validation errors shown | S + T | P2 |
| 4.4 | Electron starts and stops the agent service | One launch; token generated per launch | S | P3 |

## 5. Requirements gaps (statement §3, §6, §9)

| # | Item | Acceptance | Owner | Prio |
| --- | --- | --- | --- | --- |
| 5.1 | SuperMASProject role (statement §6) | `agents/supermasproject.json` + prompt + a knowledge source with project documents; a test email is contributed to by it | T | P1 |
| 5.2 | `SENT` and `USER_EDITED_DRAFT` from real use | The mailbox reports approved edits and confirmed sends; Java records them through `ConversationRecorder.onSentConfirmed` with a version-specific approval | S + T | P2 |
| 5.3 | User preferences | A per-user preference file (tone, language, signature) read into the trusted prompt; test that it changes the plan's rules | T | P3 |

## 6. Security and confidentiality

| # | Item | Acceptance | Owner | Prio |
| --- | --- | --- | --- | --- |
| 6.1 | Disclosure by audience | Sources carry an audience; the composer receives only excerpts whose audience includes the email's sender; test: a student email never gets a staff-only excerpt in the composer's prompt | T | P1 |
| 6.2 | Bounded agent-service queue | 503 when full; test | T | P3 |

## 7. Code quality (from the code analysis)

| # | Item | Acceptance | Owner | Prio |
| --- | --- | --- | --- | --- |
| 7.1 | Split the entry points (SRP) | `PrototypeApp` → CLI, composition root, console report; `AgentService` → HTTP adapter, request mapper, service | T | P2 |
| 7.2 | One draft-writing path (Template Method) | `ReplyWork.run` and `ComposerAgent.execute` share validation; test of the exactly-one rule in one place | T | P2 |
| 7.3 | Capability registry (OCP) | No capability string literal outside one registry; controller levels from configuration | T | P2 |
| 7.4 | `Blackboard` contract split (LSP/ISP) | Coordinator control, agent access and legacy operations in separate interfaces; no implementation throws for an interface method it claims | Team (shared) | P2 |
| 7.5 | `replyCandidates()` on `AgentRegistry` (DIP) | Coordinators depend on interfaces only | T | P3 |
| 7.6 | Narrower exception handling | Broad catches only at agent boundaries; others narrowed | T | P3 |
| 7.7 | Builder for options; shared hashing helper; one coordinator loop | As in the analysis | T | P3 |
| 7.8 | Request extraction: adverbs in patterns ("I would also like") | Both synthetic sets re-measured before/after; no regression on single-intent | T | P2 |

## 8. Team and repository

| # | Item | Acceptance | Owner | Prio |
| --- | --- | --- | --- | --- |
| 8.1 | Teammate reviews the changes to their code | Every change listed in the step 11 and 12 reports accepted or reverted | S | P1 |
| 8.2 | Team validates shared-interface changes | P2 (`InformationNeed`), P5, P10, P11, `viewFor`, `recordFeedback`, `Action.CONTRIBUTE`, promotion rule, agent-service API | Team | P1 |
| 8.3 | Merge strategy to `main` | A reviewed PR from `prototype` (or split PRs); CI green on Linux and Windows | Team | P1 |
| 8.4 | Team knowledge vault updated | A knowledge commit on `team-knowledge-vault` describing the prototype branch, per its workflow | T | P2 |
| 8.5 | French | Analyzers, labels and keywords for French; per-language results | T | P3 |

## 9. Test and benchmark coverage per part (target state)

| Part | Unit / integration tests today | Benchmark today | Missing for "tested and benchmarked" |
| --- | --- | --- | --- |
| Metadata | Yes (parser, threader, stripper, entities, acts) | Facts (synthetic), metadata (Enron, provisional) | Human-reviewed labels (3.1) |
| Retrieval and topics | Yes, incl. isolation | C1, C3, discovered (Enron) | Effect on reply quality (3.4) |
| Memory | Yes, incl. restart | — | `SENT` from real use (5.2) |
| Specialists and prompts | Yes | Routing (synthetic), injections | Prompt influence (3.3) |
| Blackboard storage | Yes (teammate), formal models | — | Load test with many boards (P3) |
| Blackboard control | Yes (collaboration, revision, budget, fixes) | Router vs blackboard, threshold sweep | Fresh validation set (3.6), real model (2.2) |
| Generation / LLM | Yes against fake engines (local, online, record/replay) | — | Online run (2.0), real local engine (2.1-2.3) |
| Agent service + mailbox | Yes (Java 7, Node 3 + teammate's 11) | Live drive (manual) | Electron run, UI items (4.x) |
