# SuperMASProject

> STOP AI SLOP!!! Every line written shall be verified manually!!!

A blackboard-based multi-agent email reply assistant project with Java API contracts and a separate local web mailbox.

## Project progress

| Component | Current status |
| --- | --- |
| Java framework | Interfaces and data contracts are defined; coordinator, blackboard runtime and specialist implementations remain deferred. |
| UML | Documents the current Java packages, interfaces and selected records. |
| Web mailbox | Provides a browser UI, demo workspace, Gmail/Outlook adapters, conversation history, classification JSON import and reviewed drafts. |
| Local generation | The mailbox calls local Ollama directly; it does not implement the Java generation or agent interfaces. |
| Integration | The mailbox and Java contracts are not connected. Automatic multi-agent classification and scheduling remain deferred. |
| Persistence | The mailbox uses session memory; durable application storage remains deferred. |

## Dependencies

| Module | Requirements | Dependency configuration |
| --- | --- | --- |
| Java contracts | JDK 17 and Maven 3.9 or later | [pom.xml](pom.xml); no application or test-library dependencies |
| Web mailbox | Node.js 22 or later and npm | [mailbox/package.json](mailbox/package.json) and [lockfile](mailbox/package-lock.json); esbuild bundles browser assets |
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
|   |-- agent/
|   |-- coordinator/
|   |-- context/
|   |-- llm/
|   |-- persistence/
|   `-- review/
|-- mailbox/
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
| `blackboard` | Read-only snapshots, restricted specialist contributions, coordinator access and preprocessing contracts |
| `agent` | Specialist interface, registry and student, teacher, administration, research-project and generic role interfaces |
| `coordinator` | Orchestration, routing and next-action selection contracts |
| `context` | Thread/domain retrieval and budgeted evidence-selection contracts |
| `llm` | Local inference and generation contracts |
| `persistence` | Blackboard, conversation and agent-configuration storage contracts |
| `review` | Versioned editing, rejection feedback and explicit approval contracts |

The mailbox browser code is in `mailbox/web`; `mailbox/server.js` exposes the local HTTP application. Its `lib` modules handle provider calls, local generation, draft review, classification imports and demo data. This application operates independently of the Java interfaces.

See [architecture details](docs/architecture.md), [current UML](docs/uml/README.md) and [mailbox documentation](mailbox/README.md) for module boundaries and API usage.

## Deployment and usage

### Web mailbox

Run from the repository root:

```powershell
cd mailbox
npm ci
npm run build
npm start
```

Open [the local mailbox](http://127.0.0.1:4317) and select **Explore the demo** to browse sample mail without credentials. Demo sending is simulated; **Generate reply** still requires a running local model.

For real accounts or local generation, copy `mailbox/.env.example` to `mailbox/.env` and configure the required values before starting the server. See [OAuth setup](mailbox/README.md#connect-a-real-mailbox) and [local model configuration](mailbox/README.md#local-reply-generation). The mailbox uses **Save draft → Approve → Send** with version-specific approval.

Rebuild after editing browser source; restart the server after changing server code or environment configuration. Session data is held in memory and is lost on restart. This is a local application, not a public multi-user deployment.

### Java contracts

Run from the repository root with JDK 17 selected in the environment:

```powershell
mvn package
```

The output is `target/supermas-1.0.0-SNAPSHOT.jar`, a library without a main class or executable email-processing workflow. Maven may download build plugins on first use. Concrete Java method implementations require further discussion.

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

Java compilation verifies API signatures and packaging only. Mailbox tests exercise its separate application behavior; they do not validate an implemented Java multi-agent runtime. See [the mailbox validation report](mailbox/VALIDATION.md) for recorded checks and remaining live-service prerequisites.
