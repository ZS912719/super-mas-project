# SuperMASProject

Java 17 foundations for a blackboard-based multi-agent email reply assistant using local language models.

## Current scope

The shared multi-agent infrastructure remains **interfaces and data contracts only**. Person 3 supplies an Ollama adapter, independent composition and an executable JavaFX demonstration. No authoritative Blackboard, coordinator, business agent, review store or email transport is implemented by this module. See [Person 3 usage and integration](docs/person3.md).

The demonstration uses visibly labeled, fixed presentation fixtures. Optional real inference calls an already installed local Ollama server; sample agents remain fixtures.

## Build

Requirements: JDK 17 and Maven 3.9 or later.

```powershell
mvn package
mvn javafx:run
```

This runs tests and creates a thin JAR. Launch with `mvn javafx:run`, not `java -jar`. Dependencies are Gson 2.13.2, JavaFX 17.0.18 and test-only JUnit 5.14.2. Maven may download dependencies/plugins on first use.

If necessary, select the JDK explicitly on Windows:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
mvn package
```

On the development Windows machine, Maven is installed outside PATH. Equivalent commands with a workspace-local cache are:

```powershell
$maven = 'C:/Program Files/Apache/Maven/apache-maven-3.9.16/bin/mvn.cmd'
& $maven '-Dmaven.repo.local=target/maven-cache' test
& $maven '-Dmaven.repo.local=target/maven-cache' package
& $maven '-Dmaven.repo.local=target/maven-cache' javafx:run
```

Tests use deterministic local HTTP responses, composition fixtures and JavaFX. They require no Ollama/model installation. UI tests require a graphical desktop and create `target/ui-smoke.png`. Test success does not validate real-model quality or unimplemented peer modules.

## Module contracts

| Package | Responsibility |
| --- | --- |
| `model` | Email, scope, provenance, tasks, budgets, drafts, feedback, history and model data contracts |
| `blackboard` | Read-only snapshots, restricted specialist contributions, coordinator access, creation/restoration and preprocessing |
| `agent` | Common specialist interface, registration and five role-specific extension interfaces |
| `coordinator` | Orchestration, routing and state-based next-action selection |
| `context` | Thread/domain retrieval and budgeted selection of permitted evidence |
| `llm` | Local inference, trusted instruction boundaries and outward draft generation |
| `persistence` | Storage contracts for blackboards, conversation messages and agent configurations |
| `review` | Versioned editing, rejection with feedback and explicit approval |

The role interfaces are `StudentAgent`, `TeacherAgent`, `AdministrationAgent`, `ResearchProjectAgent` and `GenericAgent`. They inherit `config`, `assess` and `execute` from `SpecializedAgent`; they are not implemented agents. Additional roles can implement the shared interface directly and register through `AgentRegistry`.

## Intended processing contract

1. `Coordinator.receive` preprocesses an email, creates an isolated blackboard and associates thread history.
2. `Coordinator.run` delegates next-action selection to `CoordinationStrategy` under explicit limits.
3. `RoutingStrategy` evaluates assessments and retains clear, ambiguous or unmatched routing decisions.
4. `ContextRetriever` retrieves thread context first, then permitted domain evidence as needed.
5. `ContextSelector` and `GenerationService.prepare` explicitly select budgeted model input.
6. A specialist submits evidence, suggested tasks and a draft through `AgentBlackboard`.
7. `DraftReviewService` handles user edits, rejection feedback and approval of one version. Rejection makes revision work available to a subsequent coordinator run.

This is an intended contract, not a fixed implemented pipeline. Reclassification, supplemental retrieval, revision and stopping are represented as actions. Scheduling algorithms remain open.

## Required implementation boundaries

- Scope includes both project and thread; a blackboard identifier additionally identifies a processing task.
- Specialist snapshots must exclude unauthorized evidence. Blackboard storage must not automatically include information in model input.
- Original facts, retrieved evidence, model inferences and drafts remain distinct and retain provenance.
- Trusted role instructions remain separate from untrusted emails, references, history, prior drafts and feedback. Data must not override permissions or approval rules.
- Model adapters must enforce local-only communication and explicitly handle connection failure, timeout and invalid output.
- Round, retry, generation, total-input and context budgets must be enforced.
- Generated drafts, user edits and confirmed sent messages are distinct history kinds. Approval does not imply sending.
- Editing creates a new unapproved version; approval covers only the current version.
- No interface in this revision sends email. Future transport must require explicit version-specific user approval.

Records define field shapes only. Input validation, defensive copying, access checks, error propagation and concurrency guarantees are deferred; interface declarations do not enforce them.

## Configuration and demonstration status

`AgentConfig`, `ContextSource`, `ModelConfig` and `ExecutionLimits` define shared configuration vocabulary. No shared persistent format or loader has been selected. Person 3's session-only settings and fixed demo examples do not replace these contracts.

See [the API design notes](docs/architecture.md) for collaboration boundaries, open decisions and future validation.

## Current UML

[The shared API UML](docs/uml/README.md) documents the unchanged infrastructure contracts and domain records. Editable PlantUML sources and SVG/PNG previews are in `docs/uml`. These baseline diagrams do not include Person 3 implementations or UI-local types.

## License

See [LICENSE](LICENSE).
