# Steps 9.3 to 9.5: The Specialized Agents

## Outcome

`RoleAgent` is one configurable class for every specialized agent. It assesses fit for routing, writes a structured reply plan to the board, builds a budgeted model request with the role's trusted prompt, and turns the team's generation result into a versioned draft. Student, Teacher, Research, Administration and Generic are instances built from the JSON roles of step 9.2. Ten tests pass.

The three steps share one commit and report because they are one class and its helpers, and splitting the tests would have meant writing stubs only to replace them.

## How it works

**Building agents.** `RoleAgents.create(RoleCatalog.bundled(), services)` returns one agent per role. Each gets the matching role interface (`StudentAgent`, `TeacherAgent`, `ResearchProjectAgent`, `AdministrationAgent`, `GenericAgent`) through a tiny subclass; a role with another `kind` is a plain `RoleAgent`. Registering them with the team's registry is all the coordinator needs.

**`RoleServices`** (injected): own retriever (optional), context selector (default: the merge of step 4.5), generation service and model configuration (the team's, optional), request extractor (rules by default), sender verifier (nobody by default).

**`assess`** (as written in step 9.1): `0.6 × role hint + 0.4 × keyword score`; Generic scores 0; a role with `requiresVerifiedSender` scores 0 unless the verifier confirms the sender; `sufficientContext` false when the missing-information evidence this agent may read names one of its required fields; reasons contain counts, never email text.

**`execute`** (`ReplyWork`):

1. **Own search, if a retriever is given:** the thread's received and sent messages, then a search of the agent's own sources with the analysis keywords and new text. This is "C1 plus thread history", the best design measured in step 10.4. Each is a ranked list on the board (`<doc>#topic:<agent>.thread` and `.search`).
2. **Selection:** the merge selects evidence for this agent within its rights and `maxContextCharacters`.
3. **Plan** (`ReplyPlan`): sentences that ask something (copied, never rephrased), facts from the analysis' entities, references to the selected evidence by identifier, missing fields (required by the role and absent from the entities, or reported by the check), the role's rules, the prompt version, and notes such as context left out for budget.
4. **Plan to the board** as `MODEL_INFERENCE` evidence `reply-plan:<agent>:<n>`, readable by the agent itself (P11).
5. **Without a generation service**, the agent stops here and reports success.
6. **Request** (`RolePromptBuilder`): system prompt = the role's composed trusted prompt only. Untrusted data = labelled blocks: subject, email text, feedback on the previous draft (if any), the plan as JSON, then context items in the selector's order. `<<` and `>>` in untrusted text are broken, so text cannot open a block of its own. The total, system prompt included, stays within `maxInputCharacters`: email text and feedback are cut and marked `[truncated]` if needed; context items are added whole while they fit.
7. **Draft:** a result must hold exactly one of draft or failure, else `INVALID_OUTPUT`. A failure is returned as the agent's outcome, and the plan stays on the board. A draft is submitted under the agent's own identifier with subject `Re: …` if the service gave none, and an explanation naming the plan, the prompt version and how much context was used or cut.

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| One class, roles by configuration | "Added or modified without changing the orchestrator", and without code | One class per role |
| Plan before draft, on the board | A structured contribution is what the assignment asks of us; the draft text belongs to the team's generation service | Drafting directly from the email |
| The agent searches its own sources | The context agents may not have run, and each specialist's rights differ; the board check keeps it honest | Relying only on context agents |
| Our request builder, not `GenerationService.prepare` | Prompts per role are ours; `generate(request, config)` is the team's LLM side | Delegating prompt construction to the team |
| Requests copied verbatim | A rephrased request can drift; a copy can be checked against the email | Summarizing |

## Critical points

- **A role hint is a claim.** "I teach…" makes the teacher role fit. Bundled roles do not set `requiresVerifiedSender`, so a deployment must set it on any role whose sources a claimant must not see. A test shows the flag works.
- **The plan is readable only by its author.** A reviewer UI reading the coordinator's full snapshot sees it; other agents do not. Whether the team's writer should read it is part of P11.
- **Breaking `<<` is not a defence against injection.** It keeps block labels honest. Whether a model obeys text in the data section is untested here; only a real model and the injection suite can show it, and human review stays the safeguard.
- **No model has produced a draft yet.** Tests use a fake generation service, and step 9.7 adds a template stand-in. Draft quality is untested.
- **Missing fields are coarse.** "COURSE missing" means no COURSE entity was extracted, which inherits the entity extractor's errors (step 1.10).
