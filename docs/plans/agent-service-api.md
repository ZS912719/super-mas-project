# Agent Service API (proposal, version 1)

**Status:** a proposal on the `prototype` branch (step 11.6). The mailbox-to-coordinator integration is an open team question; this contract is what the prototype uses until the team agrees on one. Implementation: `org.supermas.prototype.AgentService` (Java) and `mailbox/lib/agents.js` (Node).

## Transport and security

| Rule | Value |
| --- | --- |
| Address | `http://127.0.0.1:4318/v1/reply` (port with `--port`). The service binds to loopback only |
| `Host` header | Must be exactly `127.0.0.1:<port>`, else **403** |
| Token | `X-SuperMAS-Token` must equal `AGENT_SERVICE_TOKEN` (at least 32 characters; the service refuses to start without one), else **401**. Compared in constant time. Needed because any web page in a local browser can send requests to a loopback port |
| Method and type | `POST`, `Content-Type: application/json`, else **405** / **415** |
| Size | At most 256,000 bytes (**413**), 200 messages, 32,000 characters per field (**400**) |
| Concurrency | One request at a time |
| Trust | Every email field is untrusted data. It only ever reaches the agents as data, never as instructions or permissions |

## Request

```json
{
  "messageId": "demo-0",
  "messages": [
    {"id": "demo-0", "threadId": "research", "sender": "Maya Chen <maya@university.example>",
     "recipient": "demo@supermas.example", "subject": "Research review · next steps",
     "body": "…", "date": "2026-11-05T09:00:00.000Z", "direction": "received"}
  ]
}
```

- `messages`: the conversation as the mailbox holds it, all in **one thread** (`threadId`), any order. `id`, `threadId`, `date` (ISO-8601 instant) and `direction` (`received` or `sent`) are required.
- `messageId`: the message to answer; it must be one of `messages` and `received`.
- Earlier `received` messages are stored as conversation history (an identical repeat is ignored). `sent` messages are **not** stored: `SENT` history needs a version-specific approval on the agents' side, which does not reach them yet. The response notes this.

## Response (200)

```json
{
  "schemaVersion": "1",
  "boardId": "…",
  "status": "AWAITING_REVIEW",
  "draft": {"version": 1, "subject": "…", "body": "…", "agentId": "student", "explanation": "…",
            "warnings": ["the reply decides or acts for the owner: \"Your extension is approved until 30 November.\" (the same words are in the email: possibly dictated)"]},
  "routing": {"match": "CLEAR", "selectedAgentId": "student", "reason": "…",
              "candidates": [{"agentId": "student", "score": 0.87, "reason": "…"}]},
  "plan": {"requests": ["Could I have an extension …?"], "missing": []},
  "classification": {"schemaVersion": "1", "source": "supermas-agents", "labels": ["Student requests"],
                     "contributors": ["student"], "confidence": 0.87,
                     "reason": "Contributed a reply plan on the board: [student]"},
  "notes": []
}
```

- `classification` is in the shape the mailbox's `normalizeClassification` reads. **Its labels say which specialists contributed on the board**, read from the board after the work, not from the routing decision before it: one label per non-fallback role with a reply plan on the board (the role's `displayName` from its JSON file), listed in `contributors`. `confidence` is the contributor's routing score when exactly one specialist contributed, otherwise `null`. If no specialist contributed (only the Generic fallback), `classification` is `null`, so the mailbox keeps its own labels. Labels only flow from the board to the user, never back into control.
- `failure` (`kind`, `message`) is present when the board recorded one, for example a history record that could not be stored. A draft may still be present.
- `draft.warnings` lists sentences in which the reply decides or acts for the owner (approves, grants, confirms a change, claims an attachment or a send), each marked when the same words are in the email and so may be dictated by it (benchmark protocol B65, B66). The draft is not changed; the list is for the reviewer and is empty when nothing was found. The mailbox does not show it yet (a UI change needs the owner's agreement).
- Nothing is approved or sent. `draft` is a proposal for human review in the mailbox.

## Errors

Every refusal is `{"error": "<message safe to show>"}` with the status above, or **500** if the agents failed.

## Mailbox side

Set `AGENT_SERVICE_URL=http://127.0.0.1:4318` and the same `AGENT_SERVICE_TOKEN` in `mailbox/.env`. Then `/api/generate` calls this service instead of Ollama, and returns `{body, origin: "model", agent}`. A non-null classification is stored on the message as an imported one is. Without `AGENT_SERVICE_URL`, the Ollama path is unchanged.

The service reads the model settings of `docs/plans/llm-setup.md` (`SUPERMAS_LLM_*`, `--llm-*`). It refuses the `online` provider unless `SUPERMAS_LLM_ONLINE_MAIL=yes` is also set, because the mailbox may show real mail, and it refuses recording or replay (step 13).

## Not covered yet

- Approval, edits and sends in the mailbox do not reach the agents (no `SENT` history, no revision after rejection).
- The mailbox UI is unchanged: its button and notice still say "local model", and new labels appear after the user presses **Refresh**, which reloads the message list.
- Electron does not start the service; it is started separately.
