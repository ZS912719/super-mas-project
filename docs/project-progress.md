# Project Progress

| Component | Current status |
| --- | --- |
| Java framework | SQLite blackboard storage, fenced task leases/recovery and Markdown observations are implemented. The real coordinator, routing and scheduling remain deferred. |
| Java agents | Metadata analysis, conversation memory, knowledge loading, discovered topics, context agents, a missing-information check and five configurable specialized agents (student, teacher, research, administration, generic) are implemented. |
| Prototype | `PrototypeApp` takes `.eml` files through all agents to drafts awaiting review on the SQLite blackboard. Several specialists contribute to one email and a composer writes one reply; a BB1-style controller chooses each step and records why (`plans/blackboard-control.md`). Drafts come from a fixed template by default, from a local model (OpenAI-compatible API), or during development from an online model (OpenRouter), all by configuration ([model setup](plans/llm-setup.md)). The step 11 router stays as a baseline (`--architecture router`). |
| Generation workspace | Independent JavaFX demonstration, native Ollama transport and structured composition validation; not wired into `PrototypeApp` or the Electron mailbox. |
| UML | Documents the current Java packages, interfaces and selected records. |
| Desktop mailbox | Electron opens an independent window and owns its local server; the UI provides demo mail, Gmail/Outlook adapters, history, labels and reviewed drafts. |
| Local generation | The mailbox calls local Ollama directly; it does not implement the Java generation or agent interfaces. |
| Integration | The Java agents run on the SQLite blackboard. With `AGENT_SERVICE_URL` set, the mailbox's **Generate reply** asks them through the loopback `AgentService` (a proposal, `plans/agent-service-api.md`); approval and sending stay in the mailbox. |
| Persistence | Java blackboards use local SQLite with immutable revision history. The independent mailbox still uses session memory. |
| Observation | Markdown generations provide an Obsidian observation vault; Kripke models check task and projection protocols. A separate read-only API and pinned Grafana/Infinity dashboard passed synthetic Docker and browser validation; see [observation deployment and limits](blackboard-observation.md). |
