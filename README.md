# SuperMASProject

> STOP AI SLOP!!! Every line written shall be verified manually!!!

A local-first desktop email assistant that uses blackboard-based multi-agent drafting and requires human approval before sending.

## Introduction

We built Super MAS, a Java multi-agent framework that uses a locally running language model to draft email replies for human review. The framework analyzes an incoming email, asks the relevant specialists to find information, and combines their contributions into a single draft. We built it to meet the requirements of Université Côte d’Azur’s SMEIA305 – ECUE Multiagent systems course project, Multi-Agent Systems Project: Design of a multi-agent system for automatic email responses using local LLMs.

As email can contain private exchanges or unpublished material, we use local inference to limit the need to transfer that material to a model provider. This raises the question that guides the project: can a small local model write a more useful reply when specialized agents gather the relevant facts and instructions for it?

Consider a student who asks a lecturer for both an assignment extension and a signature on a registration form. The first request calls for the course’s extension policy, while the second may require an administrative procedure. If the system chooses a role from the sender’s address alone, it can easily overlook the second request.

Super MAS uses a shared blackboard so that the student and administration specialists can each record their findings. The coordinator decides which work to do next, and the composer uses those findings to prepare a draft. The experiments then examine whether the system involves the right specialists and whether the draft answers the email.

## Project status

### Implemented

- **Desktop mailbox.** Electron provides a demo inbox, conversation history, labels and a versioned draft editor. Gmail and Outlook adapters implement OAuth, mail retrieval and submission. A user must approve the current saved draft and explicitly send it.
- **Multi-agent drafting.** The Java prototype takes `.eml` files through metadata analysis, context retrieval, specialist contributions and reply composition. The mailbox can call the Java Agent Service over loopback. With live-mail drafting enabled, each request uses a fresh in-memory workspace and keeps approval in the mailbox.
- **Blackboard and observation.** SQLite stores durable board revisions and supports task leases and recovery. Markdown projections can be viewed in Obsidian, and a separate read-only API feeds the Grafana dashboard.
- **Windows packaging.** The portable build includes the desktop runtime. The release build can embed the project's public Google and Microsoft desktop OAuth client IDs. Packaging has been checked with fixture IDs.

### Next milestones

1. **Prepare a live-account test build.** Register the Google Desktop and Microsoft Entra clients, configure the release build with their public IDs, and build the Windows package. The demo and synthetic tests can be used while live-account testing is pending.
2. **Validate the end-to-end mailbox flow.** With dedicated Gmail and Outlook test accounts, verify authorization, token refresh, message and thread retrieval, local model and agent drafting, versioned approval, sending and reconnecting. Live provider behavior and real-mail inference still need validation.
3. **Prepare wider distribution.** Complete the applicable Gmail scope verification, validate organizational account consent where supported, and verify a signed Windows release. Desktop sessions currently hold tokens, drafts and labels in memory. Persistent sign-in and draft recovery are future work.

## Dependencies

| Module | Requirements | Dependency configuration |
| --- | --- | --- |
| Java runtime | JDK 17 and Maven 3.9 or later | [pom.xml](pom.xml); SQLite JDBC 3.54.1.0, Jackson 2.21.4, Lucene 9.12.3, Apache James Mime4j 0.8.11, ONNX Runtime 1.31.1, DJL tokenizers 0.40.0; JUnit 5.12.2 and Angus Mail 2.0.3 for tests |
| Optional metadata models | GLiNER and speech-act ONNX models, not in Git | Tests that need them skip when absent; the prototype uses rules only |
| Blackboard observation | Obsidian for viewing generated Markdown; nuXmv for formal checks | [Runtime usage](docs/blackboard-runtime.md), [model-checking setup](formal/README.md) |
| Desktop mailbox development | Node.js 22.12 or later and npm | [mailbox/package.json](mailbox/package.json) and [lockfile](mailbox/package-lock.json); Electron runs the window, esbuild bundles assets and electron-builder packages Windows builds |
| Reply generation | A running local Ollama service and an installed model; during development optionally an OpenRouter key (online stand-in, synthetic mail only by default) | [mailbox/.env.example](mailbox/.env.example), [model setup](docs/plans/llm-setup.md) |
| Live mail accounts | Internet access, a system browser, and registered OAuth clients. Release builds embed public client IDs | [Mailbox setup](mailbox/README.md#connect-a-real-mailbox) |
| UML rendering | A JDK and a locally supplied PlantUML JAR | [Rendering instructions](docs/uml/README.md#render-locally) |

The independent JavaFX demonstration also uses Gson 2.13.2 and JavaFX Controls 17.0.18. Its UI tests require a graphical desktop, or Xvfb on Linux. See [generation workspace integration](docs/person3.md).

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
|   |-- composition/
|   |-- ui/
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

The desktop entry in `mailbox/desktop/main.js` starts `mailbox/server.js` and opens the UI from `mailbox/web` in an isolated application window. Its `lib` modules handle provider calls, local generation, draft review, classification imports and demo data. The optional Java integration uses the [loopback Agent Service API](docs/plans/agent-service-api.md).

See [project progress](docs/project-progress.md), [implementation prompts](docs/implementation-prompts.md), [architecture details](docs/architecture.md), [current UML](docs/uml/README.md) and [mailbox documentation](mailbox/README.md) for module boundaries and API usage.

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

`npm start` opens the desktop application. Select **Explore the demo** to browse sample mail without credentials. Demo sending is simulated. Generate replies with local Ollama or with the Java Agent Service configured to use its template provider. `npm run package:win` produces a portable executable in `mailbox/dist` with its runtime included.

For browser-only debugging, use `npm run start:web` after building the assets. Do not run it alongside the desktop application on the same port.

For a release with live sign-in, maintainers provide the public client IDs and run `npm run package:win:release`. Users connect through their system browser. For source development and optional local generation settings, use `mailbox/.env.example` as the configuration template. The mailbox uses **Save draft → Approve → Send** with version-specific approval. See [OAuth setup](mailbox/README.md#connect-a-real-mailbox), [local model configuration](mailbox/README.md#local-reply-generation) and [Agent Service distribution](docs/agent-service-distribution.md).

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

The argument is one `.eml` file or a folder. Options: `--knowledge <dir>` (default: the sample university knowledge in `src/test/resources/university/knowledge`), `--data <dir>` (default `runtime`), `--project <id>` (default `uni`), `--architecture blackboard|router` (default `blackboard`), `--min-contribution <0..1>` (default 0.4), `--llm-provider template|local|online|replay`, `--llm-model <name>`, `--llm-endpoint <url>`, `--llm-cache <dir>` (default: template; the same settings as `SUPERMAS_LLM_*` environment variables; `online` is a development stand-in, see [model setup](docs/plans/llm-setup.md)). State persists in the data folder: `blackboard.db`, the Obsidian vault `super-mas-vault`, the search index `index` and discovered topics `topics`. Nothing is approved or sent. See [the prototype report](docs/reports/step-11.4.md).

### Independent JavaFX generation workspace

```powershell
mvn javafx:run
```

This workspace uses labeled sample data and simulated replies by default. Select the local Ollama option to use an already installed model through the native `/api/chat` endpoint. It does not run the prototype agents, approve drafts or send mail. The prototype retains its existing generation path. See [usage and integration boundaries](docs/person3.md).

### Java package

Run from the repository root with JDK 17 selected in the environment:

```powershell
mvn package
```

The output is `target/supermas-1.0.0.jar`, a library with demo/observer entry classes invoked by the script above and the `PrototypeApp` entry class, without an executable JAR manifest. Maven may download dependencies and build plugins on first use.

### Validation commands

Build and package the Java API from the repository root:

```powershell
mvn package
```

On headless Linux, run Java tests with a virtual display:

```bash
xvfb-run -a mvn -B -ntp clean verify
```

Run mailbox tests and rebuild browser assets:

```powershell
cd mailbox
npm test
npm run build
```

Java packaging runs blackboard concurrency, recovery and projection tests. Run `./scripts/check-formal.ps1` for the finite Kripke models and their negative controls. See [blackboard validation](docs/blackboard-validation.md). Mailbox tests exercise the separate application; see [the mailbox validation report](mailbox/VALIDATION.md) for its recorded checks and remaining live-service prerequisites.

### Grafana blackboard observation

`src/main/java/org/supermas/observation/` provides a separate loopback-only, authenticated, read-only SQLite metadata API. Versioned Grafana provisioning and dashboard JSON live in `observability/grafana/`. Java 17 and Maven use the existing dependencies; the optional native Windows viewer pins Grafana 13.1.2 and Infinity 4.1.6.

Inject `SUPERMAS_OBSERVATION_TOKEN` and exact `SUPERMAS_OBSERVATION_BOARDS` keys, then run `./scripts/start-observation.ps1 -Database runtime/blackboard.db`. With an extracted Grafana distribution and the pinned plugin already available, inject `GF_SECURITY_ADMIN_PASSWORD` and run `./scripts/start-observation-grafana.ps1 -GrafanaHome ./tools/grafana` in a second terminal. See [deployment, API fields and validation progress](docs/blackboard-observation.md).

The first dashboard includes overview, expert assessments, activity intervals, coordinator/execution records and recorded relationships. A synthetic Docker Desktop stack is available through `./scripts/start-observation-docker.ps1`; supply environment credentials or its protected runtime environment file. `python ./scripts/verify-observation-grafana.py --fault-tests` checks actual Infinity queries and controlled failure cases. Docker deployment and browser validation are recorded in the observation guide; the native Windows Grafana distribution remains unverified and the JavaFX sample remains independent.
