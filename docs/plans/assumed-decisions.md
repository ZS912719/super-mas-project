# Assumed Team Decisions

This list is for team validation. Work on the Metadata and Context agents proceeds as if these proposals were accepted, but the other two members have not validated them yet.

To find every affected place in the code, search for `ASSUMED-TEAM-DECISION`.

**Status values:** `Not implemented`, `Implemented, pending validation`, `Validated`, `Rejected`.

## Needs team validation

| ID | Gap | Proposed choice | Where it applies | Status | Commit | Affected files | If rejected |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P1 | `Analysis` has no per-field confidence or source | Add a field record with value, confidence and source | `Domain.java` | Not implemented | - | - | Revert the commit; keep field details inside the Metadata package |
| P2 | `Task` has no field for the information need | Add a structured information need | `Domain.java` | Implemented, pending validation | 922b34a | `Domain`, `InMemoryBlackboard`, `ScriptedCoordinator` | Revert the commit; carry the need in a Context package task object |
| P3 | `ModelRequest` has no response schema | Add an optional response schema | `Domain.java` | Not implemented | - | - | Revert the commit; pass the schema through the stand-in adapter only |
| P4 | Agents in `AgentRegistry.all()` could become reply candidates | Filter candidates on `AgentConfig.capabilities` | Stand-in registry | Implemented, pending validation | d10fb48 | `Capabilities`, `InMemoryAgentRegistry`, `ScriptedCoordinator` | Follow the code markers, because the commit holds the whole registry. Apply the team's way of separating agent kinds |
| P5 | Every agent can call `submitDraft` | Reject it unless the agent has a drafting capability | Stand-in blackboard | Implemented, pending validation | 79577fe | `Capabilities`, `InMemoryBlackboard` | Follow the code markers, because the commit holds the whole blackboard. Remove the check, or apply the team's permission rule |
| P8 | Each volunteering agent uses `ExecutionLimits` rounds | Each context agent execution counts as one round | Stand-in coordinator | Implemented, pending validation | 8109a01 | `ScriptedCoordinator` | Follow the code marker, because the commit holds the whole coordinator. Change the counting in the stand-in and its tests |
| P9 | `Feedback` covers drafts, not metadata | Add a metadata correction record, stored in a local file | `Domain.java` | Not implemented | - | - | Revert the commit; keep corrections in a private format |
| P10 | Nothing records conversation history when an email arrives, a draft is made or a send is confirmed | The coordinator, the board or review service and the mail transport call `ConversationRecorder` (`onReceived`, `onDraft`, `onSentConfirmed`) | `memory/ConversationRecorder` | Implemented, pending validation | step 8.2 | `ConversationRecorder`, later the stand-in coordinator (step 8.3) | Call `IndexConversationRepository` from wherever the team records these events |

P8's counting rule is a starting assumption. The plan only says that the team should agree how context budget is counted.

## For information: decided by the project owner

These are inside the Metadata and Context area, so they need no validation. The team should still know about them because they affect shared behavior.

| ID | Decision | Why the team should know |
| --- | --- | --- |
| P6 | Evidence rights are source rights ∩ requester rights, and `ContextQuery.agentId` means the requester | Callers of `ContextQuery` must pass the requesting agent |
| P7 | `sufficientContext` gets a written definition for context agents | Routing code that reads assessments must not assume another meaning |
| D1 | Correspondent topics may read across threads only within the same project and for verified senders, with the switch off by default | This is an exception to the thread isolation rule in `docs/architecture.md` |
| D2 | An online LLM is used during development on synthetic and public data only | The real LLM integration must stay switchable to a local model through configuration |
