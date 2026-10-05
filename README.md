# SuperMASProject

A proposed Java multi-agent framework for drafting email replies with locally running language models.

## Project status

The repository currently contains architecture documentation and diagrams. The Java application has not been implemented yet.

## Language convention

Use English for all project-authored content, including documentation, diagram labels, identifiers, comments, prompts, interface text, logs, test descriptions, and commit messages.

## Planned workflow

1. Receive an email and store its conversation context.
2. Route it to an appropriate specialized agent using the sender, subject, content, and thread metadata.
3. Assemble the selected agent's system prompt, permitted knowledge sources, and relevant conversation history.
4. Generate a draft using a local language model.
5. Present the draft for user review, editing, approval, or rejection.
6. Regenerate rejected drafts using user feedback; send only explicitly approved text.

The proposed roles include student, teacher, administration, research project, and generic fallback agents. Routing strategies, context providers, memory mechanisms, and local inference adapters are designed to be replaceable.

## Architecture diagrams

| Diagram | Editable source | SVG | PNG |
| --- | --- | --- | --- |
| UML class diagram | [PlantUML](docs/uml/class-diagram.puml) | [SVG](docs/uml/class-diagram.svg) | [PNG](docs/uml/class-diagram.png) |
| Email processing sequence | [PlantUML](docs/uml/sequence-diagram.puml) | [SVG](docs/uml/sequence-diagram.svg) | [PNG](docs/uml/sequence-diagram.png) |
| Agent organization | [PlantUML](docs/uml/agent-organization.puml) | [SVG](docs/uml/agent-organization.svg) | [PNG](docs/uml/agent-organization.png) |

See [the design notes](docs/uml/README.md) for architectural decisions and their relationship to the course requirements.

## Rendering the diagrams

Install Java and obtain the PlantUML JAR from its official distribution. Then run the following command from the repository root:

```powershell
.\docs\uml\render.ps1 -PlantUmlJar 'C:\tools\plantuml.jar'
```

The script validates the diagram syntax and generates SVG and PNG files locally. It does not download dependencies. The class and organization diagrams use the Smetana layout engine, so Graphviz is not required.

Temporary files and rendering tools are excluded from version control.

## License

See [LICENSE](LICENSE).
