# SuperMASProject

Java API contracts for a blackboard-based multi-agent email reply assistant using local language models.

## Current scope

This revision contains **interfaces and data contracts only**. Concrete methods, scheduling algorithms, model adapters, persistence engines, English prompt text, configuration loading and user interfaces require further discussion. There is no executable application, mock model, model connection or email sending capability yet.

The API describes an extensible organization coordinated through a task blackboard. It does not implement or validate the proposed runtime behavior.

## Build

Requirements: JDK 17 and Maven 3.9 or later.

```powershell
mvn package
```

This compiles the interfaces and packages `target/supermas-1.0.0-SNAPSHOT.jar`, a library without a main class. Maven may download build plugins on first use. There are no application or test-library dependencies.

If necessary, select the JDK explicitly on Windows:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
mvn package
```

Direct compilation without Maven is also possible:

```powershell
New-Item -ItemType Directory -Force target/classes | Out-Null
$sources = Get-ChildItem src/main/java -Filter *.java -Recurse | ForEach-Object FullName
javac --release 17 -encoding UTF-8 -d target/classes $sources
```

There are no behavioral tests because no runtime behavior is implemented. Build success verifies compilation and packaging only.

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

`AgentConfig`, `ContextSource`, `ModelConfig` and `ExecutionLimits` define configuration vocabulary. No format or loader has been selected. Model adapters, a simulator, sample emails and an interactive demo are intentionally deferred. There are no placeholder business methods that throw unsupported-operation exceptions.

See [the API design notes](docs/architecture.md) for collaboration boundaries, open decisions and future validation.

## Current UML

[The current UML](docs/uml/README.md) documents package dependencies, blackboard and specialist interfaces, service contracts and core domain records. Editable PlantUML sources and rendered SVG/PNG previews are in `docs/uml`. The old proposal has been removed. Diagrams describe interfaces and data shapes, not implemented runtime behavior.

## License

See [LICENSE](LICENSE).
