# SuperMASProject

> STOP AI SLOP!!! Every line written shall be verified manually!!!

A blackboard-based multi-agent email reply assistant project with a local Java blackboard runtime and a separate desktop mailbox.

## Project progress

| Component | Current status |
| --- | --- |
| Java framework | SQLite blackboard storage, fenced task leases/recovery and Markdown observations are implemented. The real coordinator, routing and scheduling remain deferred. |
| Java agents | Metadata analysis, conversation memory, knowledge loading, discovered topics, context agents, a missing-information check and five configurable specialized agents (student, teacher, research, administration, generic) are implemented. |
| Prototype | `PrototypeApp` takes `.eml` files through all agents to drafts awaiting review on the SQLite blackboard, using marked stand-ins for coordinator, routing, scheduling and generation (`docs/plans/stand-ins.md`). Drafts come from a fixed template; no language model is called. |
| UML | Documents the current Java packages, interfaces and selected records. |
| Desktop mailbox | Electron opens an independent window and owns its local server; the UI provides demo mail, Gmail/Outlook adapters, history, labels and reviewed drafts. |
| Local generation | The mailbox calls local Ollama directly; it does not implement the Java generation or agent interfaces. |
| Integration | The Java agents run on the SQLite blackboard. The mailbox is not yet connected to them. |
| Persistence | Java blackboards use local SQLite with immutable revision history. The independent mailbox still uses session memory. |
| Observation | Markdown generations provide an Obsidian observation vault; Kripke models check task and projection protocols. |

## Dependencies

| Module | Requirements | Dependency configuration |
| --- | --- | --- |
| Java runtime | JDK 17 and Maven 3.9 or later | [pom.xml](pom.xml); SQLite JDBC 3.54.1.0, Jackson 2.21.4, Lucene 9.12.3, Apache James Mime4j 0.8.11, ONNX Runtime 1.31.1, DJL tokenizers 0.40.0; JUnit 5.12.2 and Angus Mail 2.0.3 for tests |
| Optional metadata models | GLiNER and speech-act ONNX models, not in Git | Tests that need them skip when absent; the prototype uses rules only |
| Blackboard observation | Obsidian for viewing generated Markdown; nuXmv for formal checks | [Runtime usage](docs/blackboard-runtime.md), [model-checking setup](formal/README.md) |
| Desktop mailbox development | Node.js 22.12 or later and npm | [mailbox/package.json](mailbox/package.json) and [lockfile](mailbox/package-lock.json); Electron runs the window, esbuild bundles assets and electron-builder packages Windows builds |
| Reply generation | A running local Ollama service and an installed model | [mailbox/.env.example](mailbox/.env.example) |
| Live mail accounts | User-provided Gmail or Outlook OAuth credentials and an Internet connection | [Mailbox setup](mailbox/README.md#connect-a-real-mailbox) |
| UML rendering | A JDK and a locally supplied PlantUML JAR | [Rendering instructions](docs/uml/README.md#render-locally) |

## Code structure

```text
.
|-- pom.xml
|-- src/main/java/org/supermas/
|   |-- model/
|   |-- blackboard/
|   |   |-- api/
|   |   |-- impl/
|   |   |-- model/
|   |   |-- persistence/
|   |   |   |-- codec/
|   |   |   `-- sqlite/
|   |   |-- export/
|   |   `-- app/
|   |-- agent/
|   |   `-- role/
|   |-- coordinator/
|   |-- context/
|   |-- llm/
|   |-- memory/
|   |-- metadata/
|   |-- persistence/
|   |-- prototype/
|   `-- review/
|-- src/main/resources/
|   |-- agents/
|   `-- prompts/
|-- src/test/java/org/supermas/
|-- benchmark/
|-- formal/
|-- scripts/
|-- mailbox/
|   |-- desktop/
|   |-- server.js
|   |-- lib/
|   |-- web/
|   |-- scripts/
|   |-- test/
|   `-- vendor/
`-- docs/
    |-- architecture.md
    `-- uml/
```

| Java package | Responsibility |
| --- | --- |
| `model` | Scoped emails, evidence provenance, tasks, budgets, drafts, feedback, history and model data contracts |
| `blackboard.api` | Read-only snapshots, restricted specialist contributions, coordinator access and preprocessing contracts |
| `blackboard.impl` | Storage-independent blackboard and factory implementations |
| `blackboard.model` | Version/lease data and the encapsulated transaction-local state editor |
| `blackboard.persistence` | Snapshot/transactional storage contracts, identities and storage errors |
| `blackboard.persistence.sqlite` | SQLite implementation of the storage contract |
| `blackboard.persistence.codec` | JSON encoding and content digests shared by persistence and export |
| `blackboard.export` | Markdown generations, publication, read-back audit and retry worker |
| `blackboard.app` | Demo and observation-process entry points; selects concrete adapters |
| `agent` | Specialist interface, registry and student, teacher, administration, research-project and generic role interfaces |
| `agent.role` | Specialized agents defined by configuration: one JSON file in `resources/agents` and one prompt in `resources/prompts` per role; assessment, reply plan, budgeted prompt and draft request |
| `metadata` | `MetadataAgent`: MIME parsing, quote and signature removal, language, entities, speech acts and role hint, by rules with optional ONNX models |
| `memory` | Conversation history per project and thread, knowledge loading, automatic topic regrouping |
| `coordinator` | Orchestration, routing and next-action selection contracts |
| `context` | Retrieval and selection contracts; Lucene retrieval, topic discovery, topic and discovered-topics context agents, missing-information check |
| `llm` | Local inference and generation contracts |
| `persistence` | Conversation and agent-configuration storage contracts |
| `review` | Versioned editing, rejection feedback and explicit approval contracts |
| `prototype` | Marked stand-ins for parts not built yet (coordinator, routing, scheduling, generation, in-memory board) and the `PrototypeApp` entry point |

The desktop entry in `mailbox/desktop/main.js` starts `mailbox/server.js` and opens the UI from `mailbox/web` in an isolated application window. Its `lib` modules handle provider calls, local generation, draft review, classification imports and demo data. This application operates independently of the Java interfaces.

See [architecture details](docs/architecture.md), [current UML](docs/uml/README.md) and [mailbox documentation](mailbox/README.md) for module boundaries and API usage.

## Deployment and usage

GitHub Actions verifies the Java project on Linux and Windows. Mailbox and formal checks activate when their modules are present in the checked-out branch. Delivery workflows prepare component packages and draft releases when the required implementation is available. See [CI and delivery](docs/ci-cd.md) for setup and release commands.

### Desktop mailbox

Run from the repository root:

```powershell
cd mailbox
npm ci
npm run build
npm start
```

`npm start` opens the desktop application. Select **Explore the demo** to browse sample mail without credentials. Demo sending is simulated; **Generate reply** still requires a running local model. `npm run package:win` produces a portable executable in `mailbox/dist` with its runtime included.

For browser-only debugging, use `npm run start:web` after building the assets. Do not run it alongside the desktop application on the same port.

For real accounts or local generation, copy `mailbox/.env.example` to `mailbox/.env` and configure the required values before starting the server. See [OAuth setup](mailbox/README.md#connect-a-real-mailbox) and [local model configuration](mailbox/README.md#local-reply-generation). The mailbox uses **Save draft → Approve → Send** with version-specific approval.

Rebuild after editing browser source; restart the server after changing server code or environment configuration. Session data is held in memory and is lost on restart. This is a local application, not a public multi-user deployment.

### Java blackboard runtime

Create a demo board and export an observation vault:

```powershell
./scripts/run-blackboard.ps1 -Mode demo
./scripts/run-blackboard.ps1 -Mode audit
./scripts/run-blackboard.ps1 -Mode watch
```

Open `runtime/super-mas-vault` as an existing Obsidian vault and follow a board's `generated/<board-key>/current.md` link. `watch` recovers expired leases and refreshes observations; it does not execute agents. Stop it with Ctrl+C. Use `-DataDirectory` to choose another runtime directory. [Runtime documentation](docs/blackboard-runtime.md) describes API usage, structure and recovery behavior.

### Java prototype

Process emails from `.eml` files to drafts awaiting review:

```bash
mvn -q compile exec:java -Dexec.mainClass=org.supermas.prototype.PrototypeApp \
  -Dexec.args="benchmark/data/synthetic-dev"
```

The argument is one `.eml` file or a folder. Options: `--knowledge <dir>` (default: the sample university knowledge in `src/test/resources/university/knowledge`), `--data <dir>` (default `runtime`), `--project <id>` (default `uni`). State persists in the data folder: `blackboard.db`, the Obsidian vault `super-mas-vault`, the search index `index` and discovered topics `topics`. Nothing is approved or sent. See [the prototype report](docs/reports/step-11.4.md).

### Java package

Run from the repository root with JDK 17 selected in the environment:

```powershell
mvn package
```

The output is `target/supermas-1.0.0-SNAPSHOT.jar`, a library with demo/observer entry classes invoked by the script above and the `PrototypeApp` entry class, without an executable JAR manifest. Maven may download dependencies and build plugins on first use.

### Validation commands

Build and package the Java API from the repository root:

```powershell
mvn package
```

Run mailbox tests and rebuild browser assets:

```powershell
cd mailbox
npm test
npm run build
```

Java packaging runs blackboard concurrency, recovery and projection tests. Run `./scripts/check-formal.ps1` for the finite Kripke models and their negative controls. See [blackboard validation](docs/blackboard-validation.md). Mailbox tests exercise the separate application; see [the mailbox validation report](mailbox/VALIDATION.md) for its recorded checks and remaining live-service prerequisites.
