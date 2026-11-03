# Step 8.2: Recording Conversation Events

## Outcome

`ConversationRecorder` turns three events into history: a received email, each draft version, and a confirmed send. Five tests pass, including every refusal path for `SENT`.

## What was built

| Item | Behaviour |
| --- | --- |
| `onReceived(email, analysis)` | Stores the email's new text (`currentText` from the MetadataAgent, else the body) under the email's own identifier, kind `RECEIVED`. Refuses empty identifiers and identifiers starting with `supermas:` |
| `onDraft(board, draft)` | One record per version: `GENERATED_DRAFT` for `MODEL_GENERATED`, `USER_EDITED_DRAFT` for `USER_EDITED` |
| `onSentConfirmed(board, draft, approval, confirmation)` | `SENT` only if the approval names this board and exactly this version, the draft is `APPROVED`, and a `SendConfirmation` from the mail system exists and is not earlier than the approval |
| `MessageTagger` | Chooses topic tags at storage time: `none()`, `subjects(TopicTagger)`, `correspondents(SenderVerifier)` (verified senders only), `all(...)` |
| Listeners | Told about each newly stored message. A failing listener is counted, never loses the record, and does not stop other listeners. Phase 10 uses this to regroup topics |

Internal record identifiers are `supermas:<thread>:<task>:v<version>:<KIND>`, so two boards never collide.

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| New text only for received mail | Quoted history would repeat every earlier message in each reply and inflate retrieval | Storing the full body |
| The email's own identifier for received mail | `TopicContextAgent` already skips the document whose identifier is the email being answered (step 4.2) | A generated identifier, which would make the email its own context |
| `SENT` requires a separate confirmation object | The architecture requires `SENT` only after an externally confirmed send; approval alone is not a send | Recording `SENT` on approval |
| Caller is marked P10 | Who calls the recorder is the coordinator's, review's and transport's business, all team areas | Calling it from our agents, which have no access to approvals or sends |

## Problems encountered

- **Equal timestamps have no defined order.** Two drafts recorded at the same instant came back in either order, because thread history sorts by time only. The test now uses distinct times. In real use, versions are created at different instants, but a tie-break is missing in `ProjectIndex.thread`.
- **The transport's message identifier is not kept in the index.** `IndexedDocument` has a source and no free provenance field, so a stored `SENT` message reads back with provenance source `mail` (the conversation source, needed for rights checks) and the confirmation time. The transport identifier is checked but not stored.

## Critical points

- **The checks stop mistakes, not forgery.** An `Approval` is a plain record, so a caller could construct one. Proving a real user action stays with the team's review service.
- **Received mail is trusted to have a unique identifier.** A conflicting reuse is refused (step 8.1), but a sender who reuses another email's Message-ID in the same project can block the later one from being stored. The refusal surfaces as an exception, not silent loss.
