# Compliance Check

**What this is:** the project, as on the `prototype` branch on 2026-11-07, checked against (1) the project statement *Design of a multi-agent system for automatic email responses using local LLMs* (`docs/references/multi_agent_project_en.pdf`), section by section, and (2) the targeted architecture in the bibliography: Hayes-Roth (1985), *A Blackboard Architecture for Control* (BB1), and Corkill (2003), *Collaborating Software: Blackboard and Multi-Agent Systems & the Future*. The PDFs of the two papers are kept locally in `docs/references/bibliography/` and ignored by Git for copyright reasons; see [`docs/references/README.md`](references/README.md).

**Status values:** **Met** (implemented and tested), **Partly** (implemented with a stated limit, or implemented but not measured), **Not met** (missing). Every claim names its evidence. What is missing is in [`docs/todo.md`](todo.md).

## 1. Summary

| Area | Status |
| --- | --- |
| Multi-agent organization, specialization, routing, collaboration | **Met** |
| Context, memory and conversation management | **Met**, except sent-mail history (Partly) |
| Local LLM | **Partly**: adapter built and tested against a fake engine; no real model measured |
| Assistance rather than sending | **Met** |
| Java, OOP, design patterns, extensibility | **Met** (see the code analysis for the weak spots) |
| Graphical interface | **Partly**: mailbox shows mail, drafts, approval; no agent management, no router view |
| Experiments | **Partly**: routing, retrieval, collaboration measured; answer relevance and prompt influence need a real model |
| Deliverables (README, report, UML, contributions) | **Partly**: README and many reports; the final report, updated UML and the contributions table are not written |
| BB1 / Corkill targeted architecture | **Met** for levels, KS independence, estimates, agenda control and explanation; **Partly** for control knowledge sources (the control plan is fixed in code, not itself built by KSs) |

## 2. Project statement, section by section

| § | Requirement | Status | Evidence | Gap |
| --- | --- | --- | --- | --- |
| Principle | Java framework drafting replies with local models only | **Partly** | Java 17 throughout; `llm/OpenAiCompatibleLlm` (loopback only); template default | No real local model run yet |
| 1 | Data protection: local inference | **Met** | Loopback-only adapters in Java and Node (`OpenAiCompatibleLlm.chatEndpoint`, `mailbox/lib/model.js`, `lib/agents.js`); tests refuse remote origins and redirects | — |
| 2 | Roles, organization, interactions, routing/delegation, per-agent knowledge, local LLM, modular architecture, OOP, patterns, justification | **Met** / Partly (LLM) | `docs/plans/blackboard-control.md`, `docs/reports/step-12.md`, role JSON files | LLM as above |
| 3 | Determine which agent fits a message; use sender role, subject, thread, previous exchanges, context, documents, preferences | **Met** / Partly | Role hint, keywords, subject, thread history, discovered topics, knowledge sources (`RoleAgent.assess`, context agents). User preferences: only as rules in role JSON and prompts | No per-user preference store |
| 4.1 | Routing agent using address, identity, role, subject, content, thread, metadata; not only the address | **Met** | `MetadataAgent` (role hint from text, not address), `RoleAgent` estimates, `AgendaStrategy` + recorded `RoutingDecision`. Sender verification exists for roles that need it | The "router" is the BB1 controller: justified in the design note |
| 4.2 | Specialized agents (Student, Teacher, Administration, ResearchProject, Generic) | **Met** | `resources/agents/*.json`, role interfaces in `agent/`, `RoleAgents` | — |
| 5.1 | Own system prompt per agent (role, tone, must/never disclose, structure, preferences, rules), editable without code | **Met** | `resources/prompts/<role>.txt` + `common.txt` + role `rules`; `RoleCatalog.fromFolder(path)` loads an editable folder | Editing is by files, not UI |
| 5.2 | Own context per agent (files, project documents, user info, old emails, thread history); not all agents see the same | **Met** | `KnowledgeLoader` (`sources.json` with readers per source), `contextSources` per role, board filtering, promotion only to allowed readers; staff-only guide never reaches the student (tests) | — |
| 6 | Research-project example: dedicated agent with project files and rules; 10-step flow ending in validate or reject with comments | **Met** / Partly | A role is one JSON + one prompt (adding `supermasproject.json` needs no code); flow: receive → metadata → context → contributions → compose → present; reject with feedback → revision (`BoardReviewService`, step 12.6) | No SuperMASProject role shipped; the mailbox's reject does not reach the agents |
| 7 | Minimum scenario: email → routing → specialist → local LLM → routing → user | **Met** / Partly | `PrototypeApp`, `AgentService` + mailbox; the composer calls the LLM through `GenerationService` | Real LLM untested |
| 7 | Optional agents (validation, search, memory, checking, supervisor, several routing levels), justified | **Met** | Search (context agents), memory (`ConversationRecorder`, `IndexConversationRepository`), missing-information check, composer, controller; justified in the reports | No critic agent |
| 8 | Local LLM; model, engine, protocol, prompting, retrieval chosen by students; no strong dependency on one model | **Partly** | OpenAI-compatible protocol (Ollama, llama.cpp, LM Studio); model and endpoint are configuration; trusted/untrusted prompt split; Lucene retrieval | Model not chosen or measured |
| 9 | Conversation management: previous messages, generated responses, responses actually sent, conversation context; how much to send to the LLM | **Met** / Partly | `MessageKind` RECEIVED / GENERATED_DRAFT / USER_EDITED_DRAFT / SENT kept apart; thread history; budgets (`RolePromptBuilder`, `maxContextCharacters`, `maxInputCharacters`) | `SENT` needs a confirmed send; the mailbox's sends are not reported back |
| 10 | GUI (strongly encouraged): view/create/modify agents, prompts, context; view email, selected agent, proposed response; accept, modify, reject | **Partly** | Mailbox (teammate): view email, generate, edit, approve, send; labels show contributing specialists after Refresh | No agent management UI; no router view; reject-with-feedback not wired to agents |
| 11 | Propose, do not send; user keeps final control | **Met** | No send in Java; approval is version-specific in the mailbox; tests show no send before approval | — |
| 12 | Java; encapsulation, abstraction, low coupling, high cohesion, separation, extensibility, reusability; justified patterns | **Met** (with findings) | Interfaces per responsibility; patterns listed in §4 below; [`docs/code-analysis.md`](code-analysis.md) | See the analysis |
| 13 | Generic framework: add agents, change prompts, change context sources, replace LLM, add routing strategies, add memory mechanisms, evolve the organization | **Met** | JSON roles; prompt folder; `sources.json`; `LocalLlm` / `GenerationService`; `CoordinationStrategy` / `RoutingStrategy`; `ConversationRepository`; levels by capability in `AgendaStrategy` | — |
| 14 | Design questions answered in the report | **Partly** | Answers exist across reports and are collected in §3 below | The final report must contain them |
| 15 | Experiments: distinguish email types (student, lecturer, administration, project, ambiguous); routing quality, relevance, context, prompt, errors, LLM limits; compare configurations | **Partly** | Routing: `roles-synthetic.md`, `blackboard-vs-router.md`; context: C1/C3/discovered on Enron; ambiguous mail: multi-request set; configurations: router vs blackboard, threshold sweep, C1 vs C3 | Answer relevance, prompt influence, LLM limits need a real model; labels unreviewed by a person |
| 16.1 | Source code: organization, dependencies, compile, run, configure the local LLM; README | **Met** | `README.md`, `CLAUDE.md`, `docs/summary/` | — |
| 16.2 | Report (16 items) with UML class, sequence and organization diagrams | **Not met** | Material exists (`docs/summary/`, reports); UML in `docs/uml` predates steps 8-12 | Write the report; update UML |
| 17 | Contributions of group members (name, student number, parts, tasks) | **Not met** | Git history and reports identify this member's work | Team must write it |
| 19 | Assessment: relevance of modelling, organization, routing, specialization, context/memory, LLM, Java architecture, OOP, patterns, extensibility, interface, experiments, report, justification; simple and justified over complex | **Partly** | As above | Complexity is real (several stand-ins); the summary justifies each part |
| 20 | Extensions: RAG, local embeddings, short/long-term memory, preference learning, dynamic agents, collaboration on one response, critic, router confidence, logging decisions, explainability, single vs multi comparison, confidentiality | Several **Met** | RAG (Lucene retrieval into prompts), long-term memory (index), collaboration (Phase 12), router confidence (estimates, `confidence` in labels), decision logging and explainability (control entries), single-specialist router vs blackboard comparison, confidentiality (per-source readers, promotion, isolation tests) | No embeddings, no critic, no preference learning |
| 21 | Show how MAS concepts differ from sending every email to one model | **Met** | Blackboard levels, independent KSs, controller, composer; measured gain on mixed emails | — |

## 3. The 14 design questions (statement §14), with where the answer lives

| # | Question | Short answer | Details |
| --- | --- | --- | --- |
| 1 | What is an agent? | A knowledge source: a `SpecializedAgent` with a trusted `AgentConfig` (capabilities, sources, prompt) that estimates its contribution (`assess`) and contributes through restricted board access (`execute`) | `docs/summary/blackboard-and-control.md` |
| 2 | Responsibilities | One level each: requests, context, contributions, reply; control is separate | Design note §2-3 |
| 3 | What information does it possess? | Only what its configured sources and the board's filter show it; contributions reach only the composer | Step 11.2-11.3, 12 |
| 4 | How do agents communicate? | Only through the blackboard (indirect, anonymous); no agent calls another | Corkill §2.3; `AgentBlackboard` |
| 5 | How is the agent chosen? | Each specialist estimates its fit per request; the controller rates estimates within the focus level | `AgendaStrategy` |
| 6 | Several suitable agents? | All above the threshold contribute; the composer merges, ordering overlaps by estimate | Step 12; multi-request benchmark |
| 7 | None suitable? | The Generic fallback contributes; the composer still writes; labels stay unset | `AgendaStrategy`, step 11.6 |
| 8 | How are contexts represented? | Evidence with provenance and readers, per project and thread; knowledge sources with readers; discovered topics as tags | `docs/summary/context-and-memory.md` |
| 9 | Conversation history? | Per project and thread in Lucene, kinds kept apart; drafts never as facts | Step 8.1-8.4 |
| 10 | Not sending too much context? | Per-role context and input budgets; excerpts capped; prompt builder cuts email and feedback, never the plan | `RolePromptBuilder`, step 9.5 |
| 11 | Adding an agent? | One JSON file and one prompt file; no code | Step 9.2 |
| 12 | Changing the LLM? | Configuration: `--llm-endpoint`, `--llm-model`; any OpenAI-compatible local engine | `OpenAiCompatibleLlm` |
| 13 | Persistent data? | Boards (SQLite, every revision), conversation history and knowledge (Lucene), topics (JSON); mailbox sessions are in memory | Teammate's runtime doc; step 8 |
| 14 | Advantages and limits | Collaboration on mixed emails, explainable control, isolation; costs ≈2× rounds, threshold-sensitive, rule-based specialists, no real LLM yet | Step 12 report |

## 4. Design patterns in use (statement §12)

| Pattern | Where | Real need |
| --- | --- | --- |
| Strategy | `CoordinationStrategy` (`AgendaStrategy`, router strategies), `RoutingStrategy`, `RequestExtractor`, `MissingInformationCheck`, `MessageTagger`, `SpeechActClassifier` | Compare router and blackboard, rules and models, without changing callers |
| Factory | `BlackboardFactory`, `RoleAgents.create`, `RoleCatalog.bundled/fromFolder` | One isolated board per task; agents built from configuration |
| Repository | `ConversationRepository`, `AgentConfigRepository`, `BlackboardRepository`/`BlackboardStore` | Storage hidden behind contracts (Lucene, SQLite) |
| Observer | `ConversationRecorder.Listener` → `AutoTopicMaintainer` | Topics regroup when mail is recorded, without the recorder knowing topics |
| Mediator | The blackboard and coordinator: agents never reference each other | The core MAS requirement |
| Adapter | `OpenAiCompatibleLlm`, `mailbox/lib/agents.js`, `MimeEmailParser` | External protocols behind internal contracts |
| Facade | `AgentContextAccess`, `AgentService`, `PrototypeApp.Wiring` | One entry point over many collaborators |
| Command | `Task` / `NextAction` executed by the coordinator, with leases and records | Decisions are data, recorded and replayable |

Not used, on purpose: Chain of Responsibility (the controller's rating replaces a fixed chain), Template Method (composition preferred).

## 5. Targeted architecture (bibliography)

| BB1 / Corkill feature | Status | Where |
| --- | --- | --- |
| Solution elements on a global blackboard, organized by levels of abstraction | **Met** | Levels: email, requests, context, contributions, reply (design note §2) |
| Independent, cooperative knowledge sources that never invoke each other | **Met** | Agents only see the board; promotion by the coordinator |
| Condition-action KSs; activation on blackboard events | **Met** | `assess` = condition/estimate, `execute` = action; relevant-event policy |
| One KS activation chosen per cycle by a scheduler | **Met** | `AgendaStrategy.next`, one action per round |
| Control blackboard: Problem, Strategy, Focus, Policy, To-Do-Set, Chosen-Action | **Met** (fixed plan) | `control/v1` entries; strategy and policy are code, not data |
| Control knowledge sources that build and change the control plan | **Not met** | The control plan is fixed in `AgendaStrategy`; a future step could make focus/policy decisions KSs |
| Ratings weighted by operative heuristics | **Partly** | Estimate × focus weight; one heuristic family |
| Corkill: KS-specific estimates, generic control in the shell | **Met** | `assess` through read-only views; the controller has no domain knowledge |
| Corkill: blackboard as community memory, contributions kept even if not used yet | **Met** | Every revision stored in SQLite; contributions, requests, control entries kept |
| Corkill: control can notice unused contributions | **Partly** | The composer flags uncovered requests; unused contributions are not analysed |
| Explanation of behaviour | **Met** | Control trail per board, Markdown projection, `selectionReason` |
