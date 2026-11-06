# Steps 8.3 and 8.4: Memory in the Loop, and Across Restarts

## Outcome

The stand-in coordinator now records each received email and each new draft. A second email in the same thread receives the first email as context through the discussion agent, does not receive itself, and does not receive the unsent draft. History kept on disk survives closing and reopening the indexes. 208 tests pass in total.

Step 8.4 is so small once 8.3 exists (`ProjectIndexes.onDisk` and a commit per append already exist) that both share one commit and this report.

## What was built

| Item | Behaviour |
| --- | --- |
| `ScriptedCoordinator(…, Optional<ConversationRecorder>)` | New constructor; the old one passes `Optional.empty()` and behaves as before |
| On `receive` | After preprocessing and board creation, `onReceived(email, analysis)` |
| After each executed action | Each draft that appeared during the action goes to `onDraft` |
| Storage failure | Written as a failed `ExecutionRecord` with `FailureKind.STORAGE`; the email is still handled |
| `MemoryPipelineTest` | Three tests: second email sees the first; restart on disk; storage failure |

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Recording in the coordinator stand-in, marked P10 | The coordinator is the only component that sees every received email and every draft | Recording inside our agents, which see neither approvals nor sends |
| History failure does not fail the board | A reply can still be drafted without history; the failure is visible on the board | Failing the whole email over a history write |
| No `SENT` call anywhere | There is no transport. Approval is not a send | Recording `SENT` on approval |

## Problems encountered

None in this step. The draft identity was the only subtle point: the coordinator compares the draft list before and after each action, so each version is recorded once.

## Critical points

- **History execution records use task identifiers (`history-N`) that are not in the task list.** Nothing reads executions by task today, but a real coordinator should treat history writes as its own kind of record.
- **The email is recorded before it is processed.** If processing later fails, the email is still in history, which is correct for a received message.
- **Restart covers the index only.** Boards of the stand-in are in memory and are lost on restart; that is the team's blackboard storage.
