# Autonomous Session, 2026-11-03

What I built while you were away, how it works, what the measurements say, and what needs you. Everything is on the `docs/metadata-context-plan` branch, in 17 commits plus the one adding this protocol. Nothing was pushed or merged.

**State at the end:** `mvn package` passes with **256 tests, 0 failures, 0 skipped** (195 at the start of the session). Each step has its own report in `docs/reports/`.

---

## 1. Summary in five points

1. **Topics now build and regroup themselves**, as you asked. Nobody declares a topic: mail is clustered into topics automatically, topics are refreshed as new mail arrives, and identifiers stay stable across regroupings. On Enron, 214 topics formed with no configuration, and 97% of emails fit one.
2. **Memory exists.** Received mail, every draft version and (later, when a transport exists) confirmed sends are stored per project and thread, kept apart by kind, and survive a restart. Reference knowledge (FAQ, policies) is loaded from a folder, with readers per source.
3. **The specialized agents exist.** Student, Teacher, Research, Administration and Generic are configuration: one JSON file and one prompt file per role, with no code per role. Each one assesses fit for routing, searches what it may read, writes a structured **reply plan** to the blackboard, builds a budgeted prompt and asks the team's generation service for a draft.
4. **Everything runs end to end** with the team's parts played by marked stand-ins: email → metadata → memory → topics → context → missing-information check → routing → specialist → plan → draft awaiting review.
5. **Honest results:** discovered topics are far better than folder-name topics, but plain search plus thread history (C1 + thread) is still the best retrieval measured. Routing is safe (never the wrong specialist) but cautious (63% correct; with a perfect role hint it would be 100%). Injection suite: 0 violations in 72 cases. I also found and fixed one real defect (drafts leaking into search).

---

## 2. How to review and undo

| Goal | Command |
| --- | --- |
| See this session's commits | `git log --oneline before-autonomous-2026-11-03..HEAD` |
| Full diff of the session | `git diff before-autonomous-2026-11-03..HEAD` |
| Undo the whole session | `git reset --hard before-autonomous-2026-11-03` |
| Undo one step | `git revert <commit>` |
| Run all tests | `mvn test` |

| Commit | Step | What |
| --- | --- | --- |
| `4c1e256` | 8.0 | CLAUDE.md and plan updated; Phases 8-10 added |
| `02db7fa` | 8.1 | Conversation history in the project index |
| `7ef0b60` | 8.2 | Recorder: received, drafts, confirmed sends (P10) |
| `61eb390` | 8.3, 8.4 | Coordinator stand-in records history; restart test |
| `56fd007` | 8.5 | Knowledge loader with readers per source |
| `2348de5` | 10.1 | Topic discovery (k-means + class-based TF-IDF) |
| `589c8d4` | fix | Domain search never returns unsent drafts |
| `412de6a` | 10.2 | Automatic regrouping, stable topic identifiers |
| `e599b0f` | 10.3 | Discovered-topics context agent |
| `5c219f1` | 10.4 | Benchmark: discovered vs declared topics vs C1 |
| `082b228` | 9.1 | `assess` for reply agents, in writing |
| `61b9d57` | 9.2 | Roles and prompts as configuration |
| `c5b2bca` | 9.3-9.5 | `RoleAgent`: assess, plan, prompt, draft (P11) |
| `34b51af` | 9.6 | Optional LLM request extraction |
| `494008d` | 9.7 | Template generation stand-in, end-to-end test |
| `6a38179` | 9.8 | Benchmark: routing, grounding, injections |
| `648d703` | docs | Stand-ins list updated |

---

## 3. How the whole thing works

### 3.1 The flow of one email

```
Coordinator.receive(email)                                    [team; stand-in ScriptedCoordinator]
 ├─ MetadataAgent.analyze            → currentText, language, entities, speech acts, role hint   (Phase 1)
 ├─ board created                                              [team; stand-in InMemoryBlackboard]
 └─ ConversationRecorder.onReceived  → stored as RECEIVED, tagged with its topics       (8.2, 10.2)
        └─ AutoTopicMaintainer: every N new mails → TopicRegrouper.refresh(project)       (10.2)

Coordinator.run: one action per round                         [stand-in ContextFirst → DraftFirst]
 ├─ RETRIEVE_CONTEXT  discussion agent        thread history (RECEIVED, SENT only)        (4.3)
 ├─ RETRIEVE_CONTEXT  declared topic agents   optional, if any are configured              (4.2)
 ├─ RETRIEVE_CONTEXT  DiscoveredTopicsAgent   best 2 discovered topics, tag-filtered search (10.3)
 ├─ RETRIEVE_CONTEXT  MissingInformationAgent what the email seems to lack → maybe 1 further retrieval (4.6-4.8)
 ├─ ROUTE             every RoleAgent.assess → RoutingStrategy → clear / ambiguous / none (9.3)
 ├─ GENERATE          chosen RoleAgent (or Generic fallback):                              (9.4, 9.5)
 │     own search (thread + its sources) → merge within budget → ReplyPlan on the board
 │     → ModelRequest (trusted role prompt | untrusted data) → GenerationService.generate → draft
 │     └─ ConversationRecorder.onDraft → stored as GENERATED_DRAFT
 └─ PRESENT_DRAFT     board → AWAITING_REVIEW. Nothing is approved or sent by our code.
```

### 3.2 Memory (package `org.supermas.memory`)

| Class | What it does |
| --- | --- |
| `IndexConversationRepository` | Implements the shared `ConversationRepository`. Stores each message in the project's Lucene index with its `MessageKind`. `find(scope, query)` returns exactly one project and thread, only the asked kinds, newest N in time order. Readers come from the project's mail source in trusted configuration; a project without one fails closed. An identical repeat is ignored; a conflicting reuse of an identifier is refused. Commits after each write |
| `ConversationRecorder` | `onReceived(email, analysis)` stores the new text under the email's identifier. `onDraft(board, draft)` stores each version as `GENERATED_DRAFT` or `USER_EDITED_DRAFT`. `onSentConfirmed(board, draft, approval, confirmation)` stores `SENT` only for the exact approved version with a confirmation from the mail system not earlier than the approval. Listeners hear about each new message; a failing listener never loses a record |
| `MessageTagger` | Topic tags at storage time: `discovered(catalog)`, `subjects(TopicTagger)`, `correspondents(verifier)`, `all(...)`, `none()`. Drafts never get discovered-topic tags |
| `AutoTopicMaintainer` | Listener that regroups a project after every N received or sent messages; `refreshNow(project)` for start-up |
| `KnowledgeLoader` | Loads `knowledge/sources.json` + one folder per source (`.md`, `.txt`) into the index, and returns the trusted source list. Readers per source, whole-source reload, no links followed, paths cannot escape |

**Rule kept everywhere:** a draft is never something said. Domain search excludes draft kinds inside the Lucene query (the fix), thread history asks for `RECEIVED` and `SENT` only, and drafts are not clustered into topics.

### 3.3 Automatic topics (package `org.supermas.context`)

| Class | What it does |
| --- | --- |
| `TopicDiscovery` | Threads → words (3-25 letters, stop list, in ≥ 2 threads and ≤ 30% of threads) → TF-IDF → spherical k-means (k = √threads, ≤ 50, 5 restarts, best kept) → merge centres with cosine ≥ 0.5 → drop topics under 3 threads and threads with similarity < 0.1 → keywords by class-based TF-IDF (as BERTopic), each in ≥ 2 and ≥ 20% of the topic's threads. Deterministic |
| `TopicCatalog` | Current topics per project, in memory or one JSON per project. Identifiers `t<n>` from a counter, tags `auto-topic-t<n>`; a new topic keeps an old identifier when their threads overlap with Jaccard ≥ 0.3 |
| `TopicRegrouper` | Reads the project, discovers topics, updates the catalogue, rewrites only documents whose `auto-topic-*` tags changed. Mail in a clustered thread gets its thread's topic; knowledge and other mail join a topic with ≥ 2 of its keywords |
| `DiscoveredTopicsAgent` | One context agent with a fixed identity and rights from configuration. `assess`: 1 if the email's thread is in a topic, else the best keyword score. `execute`: searches inside the best 2 topics, each as its own ranked list for the merge |

**Why one agent and not one per topic:** rights are checked by agent identifier. A new agent per discovered topic would either find nothing or need rights granted by discovery, which would let mail decide who reads what.

### 3.4 Specialized agents (package `org.supermas.agent.role`)

| Class or file | What it does |
| --- | --- |
| `src/main/resources/agents/*.json` | One role each: identifier, kind, prompt file, role hints, keywords, capabilities, sources, required fields, rules, fallback, `requiresVerifiedSender`, budgets |
| `src/main/resources/prompts/common.txt` | Trusted rules for every role: data section is untrusted, never send, never claim done, answer in the email's language, no promises, ask when information is missing |
| `src/main/resources/prompts/<role>.txt` | What the role handles and how |
| `RoleCatalog` | Loads and validates roles (bundled, or `fromFolder(path)` to override). System prompt = common + role + rules; version = hash of that text. Implements `AgentConfigRepository` |
| `RoleAgents.create(catalog, services)` | One `RoleAgent` per role, each with its role interface (`StudentAgent`…) |
| `RoleAgent.assess` | `0.6 × role hint + 0.4 × keyword score`; Generic always 0 (fallback); 0 for an unverified sender if the role requires a verified one; `sufficientContext` false if a required field is reported missing (`docs/plans/reply-agent-assess.md`) |
| `ReplyWork` (`execute`) | Own search (thread + its sources) → merged selection within budget → `ReplyPlan` on the board → `RolePromptBuilder` → `GenerationService.generate` → draft with an explanation naming plan, prompt version and context used |
| `ReplyPlan` | Quoted requests, facts from entities, evidence references, missing fields, the role's rules, prompt version, notes. On the board as `MODEL_INFERENCE` evidence `reply-plan:<agent>:<n>` (P11) |
| `RolePromptBuilder` | Trusted system prompt only from configuration; untrusted labelled blocks (subject, email, feedback, plan, context); `<<`/`>>` in untrusted text broken; total within `maxInputCharacters`, email and feedback cut and marked if needed, context added whole |
| `RuleRequestExtractor` / `LlmRequestExtractor` | Find what is asked. Rules by default. The LLM variant may only quote: every quote is checked against the email, or the rules are used |
| `RoleServices` | What an agent is given: retriever, selector, generation service and model configuration (team), request extractor, sender verifier |

**Adding a role:** add `agents/<id>.json` and `prompts/<id>.txt` (and a line in `agents/index.txt` for the bundled set, or just the two files in a folder catalogue). No Java, no coordinator change.

### 3.5 Wiring it (as `EndToEndTest` does)

```java
Map<String, ContextSource> sources = new KnowledgeLoader(indexes).load(knowledgeFolder).sources();   // + "mail" source
LuceneContextRetriever retriever = new LuceneContextRetriever(indexes, sources, configs::get);
TopicCatalog catalog = TopicCatalog.onDisk(dataFolder.resolve("topics"));
ConversationRecorder recorder = new ConversationRecorder(
        new IndexConversationRepository(indexes, project -> mailSourceOf(project)), MessageTagger.discovered(catalog));
recorder.addListener(new AutoTopicMaintainer(
        new TopicRegrouper(indexes, catalog, new TopicDiscovery(TopicDiscovery.Settings.defaults())), 25));
RoleServices services = RoleServices.planOnly().withRetriever(retriever).withGeneration(generationService, modelConfig);
RoleAgents.create(RoleCatalog.bundled(), services).forEach(registry::register);
// plus: discussion TopicContextAgent, DiscoveredTopicsAgent, MissingInformationAgent
```

The mail source's readers must include every agent that should read mail (role agents, discussion, discovered).

---

## 4. Results

### Retrieval (step 10.4, Enron, 7 mailboxes, 10,206 messages; C1 reproduces step 4.10 exactly)

| Folder proxy (405 queries) | Recall@10 | MRR | nDCG@10 |
| --- | --- | --- | --- |
| C1 (search) | **0.113** | **0.687** | **0.454** |
| Declared folder-name topics (4.10) | 0.020 | 0.121 | 0.068 |
| **Discovered topics** | 0.070 | 0.615 | 0.325 |

- Discovered topics are 3.5× better than declared ones on Recall@10, and cover 97% of emails instead of 3%, with no configuration.
- They still lose to C1, probably because they search only inside 2 topics (my reading, untested).
- **C1 + thread history** (the fair baseline step 4.10 said was missing) is the best measured design: thread-proxy Recall@10 0.988, MRR 1.000. The role agents' own search does exactly this.
- Discovery plus retagging for all 7 mailboxes took 9.6 s on this laptop. Isolation: 0 violations.

### Specialized agents (step 9.8, 24 synthetic emails)

| | Value |
| --- | --- |
| Routing correct, default | 15 / 24 (0.63), never to a wrong specialist; all errors go to Generic |
| Routing correct with the gold role hint | 24 / 24 |
| Plans grounded | 14 citations, 0 ungrounded |
| Injection suite | 72 cases, **0 violations** |

---

## 5. Decisions I made on my own (override any of them)

1. **Discovered topics need no approval.** You said the user must do nothing; the plan's section 4 had proposed approval. Recorded in the plan.
2. **One discovered-topics agent, not one per topic** (rights, see 3.3).
3. **Topic discovery in Java** (k-means + class-based TF-IDF), not BERTopic in Python: no new runtime, no model download, deterministic.
4. **Specialists write a plan, then call the team's `GenerationService.generate`.** Prompts per role are ours (assignment), the model call is the team's. This matches the architecture ("a specialist contributes evidence, suggested tasks, and a draft").
5. **The plan travels as evidence until `AgentContribution` exists** (P11, marked for team validation).
6. **History is recorded by the coordinator, review and transport** (P10, marked for team validation).
7. **Role agents do their own search (C1 + thread)**, because that was the best measured design.
8. **Routing weights 0.6 / 0.4, routing threshold 0.4 in tests**: set before measuring, not tuned on 24 emails.
9. **Step 8.6 (correspondent facts) cut**, as the plan's cut order allows.
10. **Steps 8.3-8.4 and 9.3-9.5 committed together**, because they were too small or too intertwined to split; each has a report saying so.

## 6. Problems I ran into

| Problem | What I did |
| --- | --- |
| **Unsent drafts could be returned by domain search** once the recorder started indexing drafts | Fixed in the index query, own commit and report (`fix-drafts-in-search.md`), regression test |
| k-means found 2 topics in 3 clean groups | 5 restarts, more initial clusters, then merging |
| Hand-written stop list had duplicates (`Set.of` would fail at start-up) | `Set.copyOf(List.of(...))` |
| The tagger could not tell drafts from sent mail | Taggers now receive the `MessageKind` |
| Very small mailboxes form no or few topics (30% word rule, k = √threads) | Not tuned after the benchmark; written down as a limit; harmless because agents still search |
| Equal timestamps have no defined order in thread history | Tests use distinct times; tie-break is a known gap |
| My own stray `cat` hung the shell for ten minutes | No code effect |

## 7. Critical points to know

- **No real model was used anywhere.** Prompts, LLM extraction and drafts are tested with fakes and a template stand-in. Prompt wording cannot guarantee injection resistance; human review stays the safeguard.
- **A role hint is a claim in the email.** "I teach…" makes the teacher role fit. Routing never grants rights, but a role whose sources are sensitive must set `requiresVerifiedSender` (no bundled role does yet, because no real verifier exists).
- **Discovered topics do not improve retrieval over C1 on current evidence.** I recommend C1 + thread history as the default context, and keeping discovered topics as the automatic grouping (useful for organizing and a UI) and as an optional agent.
- **All accuracy numbers are on proxies or synthetic data.** The 25-email hand audit from the last session is still not done (`benchmark/data/enron/labeling/audit/`), and routing needs real university mail.
- **Readers frozen at write time** (memory): removing a reader works on old mail, adding one needs re-indexing.
- **The transport identifier of a sent message is checked but not stored** in the index (no field for it).

## 8. What needs you

1. **Review P10 and P11** in `docs/plans/assumed-decisions.md` with your two teammates (who calls the recorder; plan as evidence until `AgentContribution`).
2. **Decide the default context design**: my recommendation is C1 + thread history by default, discovered topics on as grouping and optional agent.
3. **The 25-email audit** (unchanged from last time): it is still the only way to make the Phase 1 numbers final.
4. **Real university mail for routing**, or a decision to keep synthetic routing numbers as indicative only.
5. **The team's LLM adapter**: once a `GenerationService` and `LocalLlm` exist, plug them into `RoleServices` and re-run steps 9.6 and 9.8 with a real model.
6. **Phase 7** (final report, UML and architecture updates) is still open, as planned.

## 9. Where to read more

| Topic | File |
| --- | --- |
| Plan with Phases 8-10 | `docs/plans/metadata-context-agents.md` |
| Team validation list | `docs/plans/assumed-decisions.md` |
| Stand-ins and their gaps | `docs/plans/stand-ins.md` |
| `assess` for context and reply agents | `docs/plans/context-agent-assess.md`, `docs/plans/reply-agent-assess.md` |
| Step reports | `docs/reports/step-8.*.md`, `step-9.*.md`, `step-10.*.md`, `fix-drafts-in-search.md` |
| Benchmark results | `docs/benchmark/results/c3-discovered-enron.md`, `docs/benchmark/results/roles-synthetic.md` |
