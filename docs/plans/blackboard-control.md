# Blackboard Control: Several Specialists, One Reply (Phase 12 design)

**Status:** approved by the project owner on 2026-11-07, implemented on the `prototype` branch (steps 12.1-12.8). The project owner allowed changing the teammate's blackboard code and the shared contracts on this branch for it.

## 1. Why

Until step 11, the prototype was a router: one routing decision picked one specialist, which extracted the requests, planned and drafted alone. The blackboard was a notepad on the way. The project statement asks what happens "when several agents appear to be suitable" (section 14, question 6), lists "collaboration between several agents on the same response" as an extension (section 20), and ends with "the main challenge is to consider how several specialized agents can organize themselves, communicate, share certain information, and use different knowledge sources in order to collectively solve a complex problem" (section 21).

The two papers in the project's bibliography describe the target:

- **Hayes-Roth (1985), *A Blackboard Architecture for Control* (BB1).** Solution elements live on a shared blackboard at levels of abstraction. Independent knowledge sources (KSs) never call each other; each blackboard change can trigger KS activations (KSARs). A scheduler chooses one KSAR per cycle. BB1 adds a **control blackboard**: Problem (what is being solved and when it is solved), Strategy (a sequential plan, e.g. successive levels), Focus (the current local goal), Policy (permanent criteria), To-Do-Set (eligible KSARs) and Chosen-Action. KSARs are rated against the operative heuristics, weighted by importance.
- **Corkill (2003), *Collaborating Software: Blackboard and Multi-Agent Systems & the Future*.** KSs are specialists using a functional modularization of expertise; control knowledge is split into generic control in the control shell and KS-specific estimates packaged with each KS. The shell asks each KS for an estimate ("if selected, I will contribute this, with this quality"), without the KS doing the work, and decides. KSs never make control decisions.

## 2. The domain blackboard: five levels

| Level | Entries (all `Evidence`, so permissions, provenance and promotion apply) | Written by |
| --- | --- | --- |
| 1. Email | `Email` and `Analysis` on the board | `MetadataAgent` (preprocessor) |
| 2. Requests | `request:<n>`, a `MODEL_INFERENCE` whose text is `request/v1` JSON: index and the sentence quoted from the email | `RequestSplitter` (new KS) |
| 3. Context | Retrieved evidence and the missing-information inference | Context KSs (existing) |
| 4. Contributions | `contribution:<agent>:<n>`, `contribution/v1` JSON: the requests it covers, and for them facts, evidence references with short excerpts, missing fields, the role's rules, notes | Specialist KSs (role agents, changed) |
| 5. Reply | `reply-plan:composer:<n>` (the merged plan) and the draft version | `Composer` (new KS) |

Every KS submits privately; the coordinator promotes (step 11.3) context findings, requests and contributions to the agents whose trusted configuration lists their source. The composer lists every role as a source, so it reads all contributions and nothing else directly.

## 3. The control blackboard (after BB1)

| BB1 level | Here |
| --- | --- |
| Problem | "Propose one reply that answers every request, for human review." Solved when a draft exists that is newer than every contribution and every feedback. |
| Strategy | Successive levels: Requests → Context → Contributions → Reply |
| Focus | The first level whose criterion is not yet met: no requests yet; context agents still triggered; requests not covered by a contribution; no current draft |
| Policy | (1) A KS runs again only if the board changed since its last run (event-driven triggering). (2) A KS with estimate 0 is not eligible. (3) Keep two rounds for composing and presenting. (4) Contributions need at least the configured minimum estimate (0.4). |
| To-Do-Set | Every KS with a non-zero estimate this cycle, rated `estimate × weight`, weight 1.0 at the focus level and 0.3 elsewhere, so a strong activation at another level can still win (opportunism) |
| Chosen-Action | The highest rating; ties by registration order |

Each cycle, the coordinator writes a control entry `control:<round>` on the board (readable by no agent, visible in the Markdown projection): the focus, the chosen activation and the whole rated To-Do-Set. The chosen action's reason also becomes the task's reason, so `ExecutionRecord.selectionReason` explains every step.

Estimates come from each KS's own `assess` (Corkill's split): the controller has no domain knowledge. A KS only learns that it is asked for an estimate, then, if chosen, to act.

| KS | Estimate |
| --- | --- |
| `RequestSplitter` | 1 while no request entry exists, else 0 |
| Context KSs | Their existing `assess`; a pending further-retrieval task triggers them again |
| Specialists | 0 without request entries. Otherwise the role's fit (role hint and keywords, as before) for the requests **not yet covered**: a keyword in the request, or the sender's role matching the role. 0 once nothing is left for it |
| `Composer` | 1 when there is at least one contribution and either every request is covered or no specialist is eligible; 0 when a current draft exists |
| Generic fallback | Contributes only when nothing else is eligible and nothing has contributed, covering every request |

Ending: `PRESENT_DRAFT` when the Problem is solved; `FAILED` on budget exhaustion or when no KS is eligible and nothing can be composed.

## 4. Revision (step 12.6)

`Feedback` is recorded on the board with the new `Blackboard.recordFeedback`, and the board returns to `PROCESSING`. A specialist's estimate rises again when feedback is newer than its contribution; it contributes again with the feedback visible (untrusted data), then the composer writes a **new draft version**. Approval stays a separate human act (`DraftReviewService`), never implied.

## 5. Changes to shared contracts and to the teammate's code

| Change | Why |
| --- | --- |
| `Action.CONTRIBUTE` added to `Domain.Action` | A contribution is neither retrieval nor generation; counting it as generation would use the generation budget |
| `Blackboard.viewFor(AgentConfig)`: a restricted **read-only** view without a lease | Estimates must be cheap (Corkill). Through leases, every estimate would be a task and a database write. Writing still needs a lease |
| `Blackboard.recordFeedback(Feedback)` | Revision after rejection needs feedback on the board; the interface had no way to record it |
| `BoardState.transition` keeps `Task.need` | Defect reported in step 11.2 |
| `BoardState.draft` requires the `draft-reply` or `compose-reply` capability | Proposal P5, reported in step 11.2 |
| Agent views show all feedback on the board | Feedback is about the reply, which several specialists build; before, only a draft's author saw it |

Not changed: `RoutingDecision` stays and records the first round of specialist estimates, for diagnostics and comparison with the router baseline.

## 6. What stays as before, and why

The router pipeline (`ScriptedCoordinator`, `DraftFirstCoordinationStrategy`, `ContextFirstCoordinationStrategy`, role agents drafting directly) stays for the in-memory unit tests and as the **baseline** of the step 12.7 comparison (single-specialist router vs blackboard), which the project statement suggests in section 20.

## 7. Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep routing, run the second-best specialist too | Still a router with a fixed number of agents; no shared partial solutions |
| Contract-net (agents bid, a manager awards tasks) | Close to this design, but a manager awarding work is direct delegation; the blackboard keeps agents anonymous and lets control be explicit and inspectable on the board |
| Each specialist drafts, a critic picks the best | Duplicated work, and a mixed email still gets a one-sided answer |
| Contributions as a new `Domain` record | More explicit, but every board, store and projection would change; evidence already carries permissions, provenance and promotion (owner's decision) |
