# Phase 12: The Blackboard Becomes Real (steps 12.0-12.8, and the local-model adapter)

## Outcome

The prototype is no longer a router. An email is split into its requests; every specialist that fits contributes a partial plan for the requests it knows; a composer merges all contributions into one reply; a BB1-style controller chooses each step from the board's state and writes every decision on the board; a rejected draft is revised by the same mechanism. On the 12 frozen multi-request emails, the blackboard matches the gold set of specialists on 5 emails against 4 for the router at the default threshold (10 at threshold 0.2), splits 11 of 12 into the right number of requests and answers every request in 11; on the 24 single-intent emails it does exactly as well as the router (15 of 24), at about twice the rounds and database writes. A local-model adapter for the OpenAI-compatible API is in place, tested against a fake engine; the template stays the default because no engine is installed here.

`mvn test`: **325 tests, 0 failures, 0 skipped** (307 before the phase). Mailbox tests unchanged (14 of 14).

| Commit | Step | What |
| --- | --- | --- |
| `894f12b` | 12.0 | Design note `docs/plans/blackboard-control.md` |
| `cae151d` | 12.1 | Teammate's board: read-only views, feedback, drafting rule, kept needs; `Action.CONTRIBUTE` |
| `0656dab` | 12.2-12.5 | Request splitter, contributions, composer, agenda controller, wiring |
| `d4ad4da` | 12.6 | Review service: reject with feedback, revision |
| `fb8c553` | 12.7 | Frozen multi-request emails |
| `91ee98c` | 12.7 | Benchmark router vs blackboard, threshold sweep |
| `ee6c0f0` | — | Local-model adapter (approved for the night) |

## Decisions by the project owner (2026-11-07, before the night)

| Question | Decision |
| --- | --- |
| Make the blackboard architecture meaningful, changing the teammate's code if needed | Yes, on the personal `prototype` branch |
| Contributions as board evidence with a JSON schema, not a new record type | Yes |
| Keep `RoutingDecision` unchanged; later control decisions in `ExecutionRecord.selectionReason` | Yes |
| More SQLite writes accepted, measured | Yes |
| Controller modelled on Hayes-Roth's BB1 (from the project bibliography) | Yes |
| Build a local-model adapter, keep the template as default | Yes |

## What was built

### 12.1 Changes to the teammate's blackboard (all tested in `BlackboardPrototypeChangesTest`)

| Change | File | Why |
| --- | --- | --- |
| `Blackboard.viewFor(AgentConfig)`: read-only restricted view, no lease | `blackboard/api/Blackboard.java`, `impl/DefaultBlackboard.java` | Corkill: the control shell asks every KS for a cheap estimate. Through leases each estimate was a task and a database write |
| The agent filter is one method used by both views | `DefaultBlackboard.restricted` | One rule for reading, so the two views cannot drift |
| `Blackboard.recordFeedback(Feedback)`; that version marked `REJECTED`, content unchanged | `Blackboard.java`, `BoardState.java` | Revision needs feedback on the board; drafts are never replaced in place |
| Agents see all feedback | `DefaultBlackboard.restricted` | Feedback is about the reply, which several specialists build; before, only a draft's author saw it |
| `BoardState.transition` keeps `Task.need` | `BoardState.java` | The defect reported in step 11.2 |
| Drafts need `draft-reply` or `compose-reply` | `BoardState.draft` | Proposal P5, reported in step 11.2. The teammate's fixture agent and demo agent gained `draft-reply` |
| `Action.CONTRIBUTE` | `model/Domain.java` | A contribution is neither retrieval nor generation; as generation it would use the generation budget |
| Same two methods on the in-memory stand-in | `prototype/InMemoryBlackboard.java` | It implements the same interface |

### 12.2-12.5 The levels, the knowledge sources and the controller

| Item | Behaviour |
| --- | --- |
| `agent/collaboration/RequestEntry` | `request/v1`: index, quote copied from the email, or a general entry when no sentence reads as a request |
| `agent/collaboration/RequestSplitter` | Rule extractor by default (the optional LLM extractor of step 9.6 plugs in). Estimate 1 until its entries exist |
| `agent/role/Contribution` | `contribution/v1`: covered request indices, the role's plan for them, excerpts (≤ 800 characters each, within the role's context budget), `feedbackSeen`, estimate. The JSON author must equal the evidence source |
| `RoleAgent.contributionEstimate` / `covers` | Estimate = the role's existing fit (hint 0.6 + keywords 0.4) if it would answer at least one request, 0 after contributing until new feedback. Covers a request when it quotes a role keyword, or all when the role hint matches or the role fits only through the subject |
| `ReplyWork.contribute` | Own search, selection, plan restricted to covered requests, contribution posted. No draft. The fallback role covers every request when the controller asks it |
| `agent/role/ComposerAgent` | Reads contributions, requests and its own plans only. Merges: requests in order, facts deduplicated, missing fields united, rules prefixed by role, a note per request (who answers it, or "answered by no specialist; the reviewer must answer it"). Trusted prompt from `RoleCatalog.composerSystemPrompt` (common rules + `prompts/composer.txt` + contributors' rules from the catalogue, never from the board). One draft per composition, explanation names contributors and estimates |
| `control/AgendaStrategy` | Problem, strategy, focus, policy, To-Do-Set, chosen action (see the design note). Estimates through `viewFor`; one `ROUTE` round records the first specialist estimates for diagnostics; fallback when nothing fits; budget forces the reply level |
| `control/ControlEntry`, `ControlRecording` | `control/v1` decision per round on the board: focus, chosen activation, rated To-Do-Set; readable by no agent, visible in Markdown |
| `LeasedCoordinator` | Writes control entries; routing assessments through views; promotes contributions (only) to the composer |
| `PrototypeApp`, `AgentService` | `--architecture blackboard` (default) or `router`; `--min-contribution`; the run prints its control trail |

### 12.6 Revision

`prototype/BoardReviewService` implements `DraftReviewService.reject`: only the current, proposed version; feedback recorded; board back to `PROCESSING`. The next run re-triggers context sources (new feedback is a relevant event), each specialist contributes again (`feedbackSeen` behind), the composer writes v2 with the feedback in its prompt. `edit` and `approve` are refused (they live in the mailbox).

### Local-model adapter

`llm/OpenAiCompatibleLlm` (Adapter for `LocalLlm`) and `llm/LlmGenerationService`. Literal loopback origins only (`127.0.0.1`, `[::1]`), redirects never followed, separate system and user messages, input budget, explicit `CONNECTION`, `TIMEOUT`, `INVALID_OUTPUT`, `BUDGET_EXHAUSTED`, `INTERRUPTED` failures, exactly one of output or failure, echoed prompt blocks refused. Switch with `--llm-endpoint http://127.0.0.1:11434 --llm-model <name>`.

## Choices and why

| Choice | Why | Alternative rejected |
| --- | --- | --- |
| **Specialists never read each other's contributions** | Excerpts come from role-restricted sources (the teaching guide is staff-only); cross-reading would leak them between roles. Overlaps are resolved by the composer, as competing partial solutions in BB1 | Roles reading others to avoid overlap: a leak path |
| **Strict successive levels; rating within the focus** | A first, fully opportunistic version let a strong specialist contribute before weak context sources ran, so it never used that context. BB1 allows a strategy to prescribe successive levels | Off-focus weight for every level |
| **Relevant events only re-trigger context** | The first version re-ran the discussion agent after every contribution (3 wasted rounds on one email): "the board changed" is too eager. Relevant events: new feedback, or a pending further-retrieval need | Any board change |
| **One `ROUTE` round kept** | `RoutingDecision` stays meaningful and comparable with the router baseline, at the cost of one round | Dropping routing |
| **Composer's trusted prompt from the catalogue** | Contributions are agent output on the board; rules travelling inside them would let a contribution inject instructions | Rules from the contribution JSON |
| **Composer drafts, specialists do not** | One reply per email; the board's P5 rule now enforces who may draft | Each specialist drafts, a critic picks |
| **Router kept** | Baseline for the comparison the statement suggests (section 20) | Deleting it |
| **Default threshold 0.4 kept despite the sweep** | 0.4 was fixed before the data existed; choosing 0.2 after seeing test results would be tuning on the test set | Adopting 0.2 tonight |

## How it was verified

| Check | Result |
| --- | --- |
| Teammate's own tests after their code changed | 22 of 22 pass |
| `BlackboardPrototypeChangesTest` (4) | Need kept through claim and completion; draft refused without capability; read-only view without lease writes nothing; feedback rejects that version and is visible |
| `BlackboardCollaborationTest` (9) | Two requests → student + administration, one composed draft, router would have been `AMBIGUOUS`; clear student email → student only, same as the router; no fit → fallback contributes; contributions readable only by the composer; one control entry per round and no context loop; tight budget composes; injection changes nothing; rejection → revised contributions (`:2`, `feedbackSeen` 1) and v2; local model drafts through a fake engine with both roles' rules in the trusted prompt |
| `OpenAiCompatibleLlmTest` (5) | Success with separate messages; loopback-only (6 bad origins); budget, refused connection, timeout; empty, oversize, invalid JSON, redirect not followed; echoed prompt refused |
| `BlackboardBenchmark` | See `docs/benchmark/results/blackboard-vs-router.md` and below |

### Results (rules-only metadata, template generation)

| | Router | Blackboard (0.4) |
| --- | --- | --- |
| Single-intent, answering agents = gold (24) | 15 | 15 |
| Multi-request, answering agents = gold set (12) | 4 | 5 |
| Multi-request role precision / recall | 0.83 / 0.48 | 0.92 / 0.57 |
| Request count right / every request answered (12) | — | 11 / 11 |
| Mean rounds, SQLite revisions, median ms per email (multi) | 4.0, 22, 67 | 6.1, 41, 149 |

Threshold sweep (blackboard): 0.2 gives single-intent 16 of 24 and multi-request 10 of 12 (precision 1.00, recall 0.90); 0.1 reaches recall 1.00 but single-intent falls to 10; 0.5 falls to 3 of 12. The single-intent mismatches are the same for both architectures: 7 French emails (French deferred) and 2 English ones with no keyword support.

## Problems encountered

1. **`Contribution.sequence` would have read promoted copies as version 0** (`…:1@shared`). Found while writing the composer, before any test; fixed to read the digits only.
2. **Context agents looped** (see choices). Found from the control trail printed by `PrototypeApp`.
3. **First edit of the policy failed silently** because a script aborted on a javadoc mismatch before writing; the rerun showed unchanged behaviour and the edit was redone.
4. **Four existing tests changed meaning**, all intended: assessments are no longer leased tasks (two tests), and on the blackboard the composer writes the draft (two tests). Updated to state the new behaviour, none loosened.

## Critical points

- **The threshold is the dominant parameter.** The second role in a mixed email often has one or two keywords; its email-level fit stays under 0.4. The sweep says 0.2 is much better on this set, but adopting it needs a fresh validation set (to-do).
- **Contributions are rule-based**: specialists use no model; only the composer would call one. Feedback text reaches the composer's prompt, not the specialists' rules.
- **A specialist does not revise when context arrives after it contributed** (only on feedback), by design to bound cost.
- **About 2× rounds, database writes and time** per email. Acceptable at this scale (≈150 ms median); a long thread with many specialists would grow.
- **Gold labels were written by the assistant** and are frozen but not yet reviewed by a person.
- **The real local model is untested**: only the fake engine. Quality, latency and output validation must be measured on the chosen engine and model.
- **The teammate's code was changed** (listed in 12.1): the teammate must review it before anything goes to `main`.

## Addendum: code-review fixes (same night)

The night's code analysis (`docs/code-analysis.md`) found three defects in this phase's code, fixed with a test each (each test fails without its fix):

1. **A further-retrieval need was taken by the discussion agent**, which cannot serve it ("A thread has nothing further to find"). The controller now asks a context source that already ran about the pending task it would take, and the discussion agent rates a need at 0. The need now stays for a source that can serve it.
2. **Without a generation service the composer re-composed until the round budget ran out.** It now rates 0 once the current contributions are composed.
3. **A budget failure left a control decision in the controller's map.** The coordinator now takes the decision right after `next`.

Benchmark rerun after the fixes: every quality figure unchanged; multi-request mean rounds 6.83 → 6.08 and SQLite revisions 44.7 → 40.9 (the wasted round is gone). The tables above show the rerun.
