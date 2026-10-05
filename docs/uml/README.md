# SuperMASProject Architecture Diagrams

These diagrams are based on the course project specification. The Java application has not been implemented yet, so the diagrams describe a proposed architecture rather than an existing implementation. Class names and labels are in English to support their use in Java code and the project report.

## Diagrams

| Diagram | Editable source | Vector preview | Raster preview |
| --- | --- | --- | --- |
| UML class diagram | [class-diagram.puml](class-diagram.puml) | [SVG](class-diagram.svg) | [PNG](class-diagram.png) |
| Email processing sequence | [sequence-diagram.puml](sequence-diagram.puml) | [SVG](sequence-diagram.svg) | [PNG](sequence-diagram.png) |
| Agent organization | [agent-organization.puml](agent-organization.puml) | [SVG](agent-organization.svg) | [PNG](agent-organization.png) |

SVG files scale without loss of quality and are suitable for report layout. PNG files provide convenient previews. All three diagrams use the same roles, responsibilities, and approval constraints.

## Design Decisions

- **Agents:** `Agent` defines a common message-handling entry point. `RoutingAgent` selects and coordinates agents, while `SpecializedAgent` assembles context and generates replies. Student, teacher, administration, research project, and generic roles are represented by separate configured instances, each with its own `AgentConfig`. A separate subclass is not required for every role.
- **Routing:** `RoutingStrategy` is replaceable, with `RuleBasedRouting` proposed for the first version. Selection considers the sender, identity, subject, body, and thread metadata. Multiple candidates are ranked using scores and configured priorities; low-confidence or unmatched requests are assigned to GenericAgent. Candidates, the selected agent, and the reason are recorded. Scoring rules and thresholds will be determined during implementation and experimentation.
- **Knowledge boundaries:** Requests to the shared `ContextProvider` carry an agent identifier and permitted source identifiers. History retrieval is also filtered by thread and agent permissions. `ContextBundle` contains the current email, relevant knowledge, permitted history, and user feedback, subject to a context budget.
- **Local models:** `LocalLlmClient` isolates the inference engine, with `OllamaClient` as a proposed adapter. All roles may share one local model. Replacing the inference engine requires an appropriate adapter; switching between compatible models requires a configuration change.
- **User review:** `ReviewService` manages draft revisions and states through `DraftRepository`. Rejection and feedback trigger regeneration, and the final text, including any user edits, requires explicit approval. `EmailGateway` sends only approved text. A mock adapter can be used during development and evaluation.
- **Conversation memory:** Received emails, generated drafts, user feedback, and actual sent messages are stored separately. A message is marked as sent only after the gateway confirms success. Generation and sending failures produce recoverable errors for the user. When the send result is unknown, such as after a timeout, a real mailbox integration must check message status and prevent duplicate sending before retrying.
- **Design patterns:** Strategy supports routing, Adapter supports local inference and mailbox integration, and Repository supports draft persistence. `RoutingAgent` coordinates the workflow, and the registry supports agent configuration and lookup. Additional patterns, such as Factory or Observer, should be introduced when an implementation requirement justifies them.

The class diagram omits supporting types such as `AgentRequest`, `AgentResult`, `DraftStatus`, and `SendResult`, as well as UI classes, to keep core dependencies readable. Concrete implementations of `ConversationMemory`, `ContextProvider`, `DraftRepository`, and `EmailGateway` can be added later, for example using SQLite persistence, file-based context, and a mock email gateway.

## Mapping to Course Requirements

| Course requirement | Diagram elements |
| --- | --- |
| Routing agent and multiple specialized agents | Agent framework in the class diagram; five roles in the organization diagram |
| Independent prompts and context | AgentConfig; source permissions for each role in the organization diagram |
| Local LLM and replaceable models | LocalLlmClient, OllamaClient, and the local inference node |
| Discussion threads and memory | ConversationMemory; history retrieval and actual sent-message recording in the sequence diagram |
| User acceptance, editing, rejection, and regeneration with feedback | ReviewService; review loop and approval branches in the sequence diagram |
| Ambiguous and unmatched emails | RoutingDecision; candidate ranking and GenericAgent fallback |
| Extensible Java framework | AgentRegistry and interfaces for routing, context, memory, and inference |

Configured agent instances, scoring and fallback rules, SQLite and Ollama choices, and error-handling details are design proposals rather than mandatory choices prescribed by the course. RAG, a critic agent, and collaborative generation are optional extensions outside this baseline.

## Local Rendering

Java and a PlantUML JAR are required. The tool used to create these diagrams is stored locally at `tmp/tools/plantuml.jar` and is excluded from version control. Rendering runs locally, without uploading emails or project materials to a remote diagram service.

If the JAR is available at that default path, run this command from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\docs\uml\render.ps1
```

Alternatively, specify the path to your own PlantUML JAR:

```powershell
.\docs\uml\render.ps1 -PlantUmlJar 'C:\tools\plantuml.jar'
```

The script regenerates diagram files without downloading tools. The class and organization diagrams use the Smetana layout engine, so a separate Graphviz installation is not required.
