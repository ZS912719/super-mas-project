# Step 11.6: The Desktop Mailbox Asks the Agents

## Outcome

With two settings, the teammate's mailbox gets its drafts from our Java agents instead of Ollama. A clear routing decision becomes the message's label. Review, version-specific approval and sending stay exactly as the teammate built them. Driven over HTTP on the demo workspace, the real mailbox server and the real agent service handled all 7 received demo messages: 7 drafts, 164-651 ms each. A send was refused before approval and simulated after it. Tests: Java **307, 0 failures** (7 new); mailbox **14, 0 failures** (the teammate's 11 unchanged, 3 new).

| Commit | What |
| --- | --- |
| `eae2c00` | `prototype/AgentService` and its tests (our code) |
| `3fb59ef` | `mailbox/lib/agents.js`, a 10-line opt-in branch in `mailbox/server.js`, `.env.example`, `mailbox/test/agents.test.js` (teammate's module) |
| (docs commit) | `docs/plans/agent-service-api.md`, `mailbox/README.md` section, this report, protocol updates |

## Decisions by the project owner (2026-11-06)

| Question | Decision |
| --- | --- |
| Routing as labels | Yes |
| Change the mailbox UI wording ("local model") | **No.** The UI is untouched |

From the second decision it followed that the planned `generator` field in `sessionView` was dropped too: nothing would read it.

## What was built

| Item | Behaviour |
| --- | --- |
| `prototype/AgentService` (Java, prototype wiring) | `POST /v1/reply` on `127.0.0.1:4318` with the JDK's built-in `HttpServer` (no new dependency). Refuses a wrong `Host` (403), a missing or wrong token (401, constant-time comparison), other paths, methods and types (404/405/415), over 256 KB (413), malformed or mixed-thread conversations, a `sent` message to answer, bad dates (400). Stores earlier received messages as history, runs the selected one through `LeasedCoordinator`, returns draft, routing, plan, notes and the classification. One request at a time. Same `--knowledge/--data/--project` options as `PrototypeApp`, plus `--port` |
| `PrototypeApp.Wiring` | Now also exposes the conversation recorder and the role catalogue |
| `mailbox/lib/agents.js` (Node, new) | `agentServiceEndpoint` (the same loopback-only rules as `localModelEndpoint`) and `requestAgentReply`: token check, only conversation fields sent, 256 KB cap, 60 s timeout, redirects refused, draft validated (non-empty, at most 16,000 characters), errors turned into messages safe to show |
| `mailbox/server.js` | In `/api/generate` only: if `AGENT_SERVICE_URL` is set, call the agents; a non-null classification goes through the teammate's own `normalizeClassification` and is stored like an imported one; return `{body, origin: "model", agent}`. Otherwise unchanged |
| `mailbox/.env.example`, `mailbox/README.md` | Two commented settings; a short usage section |
| `docs/plans/agent-service-api.md` | The contract, marked as a proposal |

## Choices and why

| Choice | Why | Alternative rejected |
| --- | --- | --- |
| **A token, as well as loopback and `Host` checks** | A web page in any local browser can send a request to `127.0.0.1`. The `Host` check stops DNS rebinding; the token stops a page from using the agents at all | Loopback only: open to any local page |
| **Opt-in by one environment variable** | The teammate's default behaviour, tests and Ollama path stay as they are; the change can be reviewed and removed in isolation | Replacing Ollama: removes their working feature |
| **Their classification adapter, not a new label path** | The adapter says "Replace this adapter when the agent output schema is agreed": it is their designed integration point, and it keeps raw JSON and unknown fields | A new endpoint or field: more of their code to change |
| **Labels only on a `CLEAR` match** | Found while running (see problems): a fallback means "no role fits", not a class. Labelling it overwrote meaningful labels with "Everything else". `null` leaves the mailbox's labels alone | Always label: destroys information |
| **The label is the role's `displayName` from its JSON file** | No label text in code; a new role brings its own label | Hard-coded names |
| **Sent mail is not stored as history** | `SENT` is recorded only after a version-specific approval on the agents' side (`ConversationRecorder.onSentConfirmed`). The mailbox's approval does not reach the agents, so storing it would break that invariant. The response says so in `notes` | Storing it as `SENT`: an unconfirmed send in memory |
| **Mailbox tests in a new file** | The teammate's test file is untouched | Adding to `mailbox.test.js` |
| **Dependencies installed with `npm ci --ignore-scripts`** | Tests and the web server need no Electron binary; nothing is downloaded or run at install time | A full install: downloads and runs Electron's installer |

## How it was verified

| Check | Result |
| --- | --- |
| Teammate's mailbox suite before any change | 11 of 11 pass (Node 22.22.1) |
| `AgentServiceTest` (7) | Student email: draft by `student`, label "Student requests", confidence in (0, 1]; lunch email: `NONE`, Generic draft, classification `null`; history: earlier mail stored, sent mail noted, repeat harmless; token, `Host` (raw socket), malformed input, 413 and 405 refused; a short token stops start-up |
| `agents.test.js` (3) | Loopback rules; token header, only conversation fields sent (no labels or unread flags), null classification kept null, refusal, unreachable and timeout messages; through the real mailbox server with a fake agent service: demo thread of 3 sent oldest first, draft filled, label applied, sent message refused, no send before approval, simulated send after it |
| **Live run**: real `AgentService` + real `node server.js`, driven by a script as the UI would (scratchpad `drive-mailbox.mjs`) | 7 drafts, HTTP 200, 164-651 ms; fixture labels kept (second run, after the fix); send refused before approval, `simulated` after |
| Electron window | **Not run.** Needs the Electron binary (skipped at install) and a display. The desktop entry reads the same `mailbox/.env` from source, so the configuration is shared; the window itself is untested |

## Problems encountered

1. **Every demo message went to the Generic fallback**, and in the first live run their labels ("Research", "Teaching"…) were replaced by "Everything else". Cause, from the routing scores: our roles assume a **staff mailbox**. The role hint describes the *sender* (a student writing to staff), and the keyword lists are written for that. The teammate's demo is the mailbox of a *student or researcher* receiving mail from an office, a colleague or a lab. No hint matched and almost no keywords (best score 0.27 against a 0.4 threshold). Fixed the overwrite (labels only on `CLEAR`). Did **not** tune keywords or thresholds: that would invalidate the step 9.8 benchmark and is a product decision.
2. **Stopping the background services with `pkill -f` killed the shell that ran it**, because the pattern was in its own command line. Services are now stopped by PID from a small script.

## Critical points

- **Whose mailbox is it?** The roles and the demo disagree. The team should decide whether the assistant serves staff (as the roles, prompts and knowledge assume) or any university member. Until then, real routing in the demo will mostly fall back.
- **The UI says "Generate reply · local model" and "Local draft generated"** even when the agents wrote the draft, by the project owner's choice. New labels show only after **Refresh**.
- **Two processes to start.** Electron does not launch the Java service; packaging is the teammate's call.
- **One request at a time, 60 s timeout.** A local model behind the agents may need a longer timeout or progress reporting.
- **Approvals and sends do not reach the agents:** no `SENT` history and no revision after rejection from the mailbox. That needs the team's review service and a second endpoint.
- **`npm audit` reports vulnerabilities** in the teammate's dependency tree. Not changed: their versions are their call.
- **The contract is a proposal** for the open mailbox-to-coordinator question, versioned `/v1/` so it can change without breaking.

## Addendum (same day): labels from contributions, not from routing

Project owner's decision, after discussing whether labels equal to the routing result would reduce the blackboard to a router: labels are now **derived from the board after the work**: one per non-fallback specialist that contributed a reply plan (`contributors` in the response). Today exactly one specialist contributes per email, so the output is the same as before; the change makes labels measure collaboration, so several labels appear once several specialists contribute (Phase 12). No specialist contribution gives `null`, as before. `AgentServiceTest` checks the `contributors` field. Commit: see the session protocol.
