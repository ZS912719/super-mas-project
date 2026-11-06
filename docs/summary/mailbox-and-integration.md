# Desktop Mailbox (teammate) and the Agent Service

**Code:** `mailbox/` (Node 22, Electron; teammate), `org.supermas.prototype.AgentService`. **Docs:** `mailbox/README.md`, `docs/plans/agent-service-api.md`; reports 11.6, 12.

## Architecture

- **Mailbox (teammate):** an Electron window over a loopback Node server. Gmail and Outlook through OAuth with PKCE, a demo workspace, conversation view, labels, a classification-import adapter, drafts with **Save → Approve → Send** and version-specific approval; direct generation through local Ollama.
- **Agent service (this member):** `POST /v1/reply` on `127.0.0.1:4318`; exact `Host`, a shared token, JSON size limits; stores earlier received mail of the thread as history, runs the email on the blackboard and returns the draft, routing, plan and a classification of the contributing specialists.
- **Mailbox change (opt-in):** when `AGENT_SERVICE_URL` is set, `/api/generate` calls the agent service (`lib/agents.js`); the classification goes through the teammate's own adapter. Without it, nothing changes. The UI was left untouched at the owner's request.

## Choices and alternatives

| Choice | Alternative | Why |
| --- | --- | --- |
| **Loopback HTTP between Node and Java** | Embed Java in Electron; rewrite the mailbox in Java | Smallest change to the teammate's app; each side stays testable |
| **A token besides loopback** | Loopback only | Any local web page can send requests to a loopback port |
| **Labels = contributing specialists, set only when one contributed** | Labels = routing result; labels from topics | Labels report what happened on the board, never steer control; a fallback is not a class |
| **Approval and sending stay in the mailbox** | Approval on the Java board | The mailbox already does version-specific approval; the Java board refuses `APPROVED` until a review service exists |

## Why it complies

Statement §10-11: the user sees the email and the proposed reply, edits, approves and sends; nothing is sent automatically. Local only. The integration protocol is a documented proposal, since the team listed mailbox-to-coordinator integration as an open question.

## Open points

The UI shows neither the selected agents nor the control trail, offers no agent management, and its reject does not reach the agents (`BoardReviewService` exists on the Java side). Electron does not start the Java service. The Electron window itself has not been run here.
