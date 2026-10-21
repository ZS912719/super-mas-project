# Current Java API UML

These diagrams replace the former class, sequence and agent-organization proposal. They describe the current Java interfaces and selected data contracts, not a running email assistant.

## Diagrams

| View | Editable source | Vector preview | Raster preview |
| --- | --- | --- | --- |
| Java package dependencies and source inventory | [PlantUML](package-overview.puml) | [SVG](package-overview.svg) | [PNG](package-overview.png) |
| Blackboard and specialist interfaces | [PlantUML](class-diagram.puml) | [SVG](class-diagram.svg) | [PNG](class-diagram.png) |
| Coordination, context, model, storage and review interfaces | [PlantUML](service-contracts.puml) | [SVG](service-contracts.svg) | [PNG](service-contracts.png) |
| Core task, evidence and draft data contracts | [PlantUML](data-contracts.puml) | [SVG](data-contracts.svg) | [PNG](data-contracts.png) |

All 23 current interfaces are covered across the blackboard/specialist and service views. Method names, parameter types, return types and interface inheritance match `src/main/java/org/supermas`. Long parameter lists use display-only line breaks. The five specialist role interfaces currently declare no additional methods.

The data view shows 15 selected records nested in `org.supermas.model.Domain`, including all components of each displayed record. Supporting routing, context and model records/enums remain defined in `Domain.java` and appear by type name in signatures and fields. Field-reference multiplicities express intended API semantics, not runtime validation.

## Reading the diagrams

- Package arrows represent imports found in Java source files. Package inventories are source-file lists rather than extra Java classes.
- Hollow triangle arrows represent actual interface inheritance.
- Dotted arrows in the interface view represent parameter/return type dependencies, not runtime calls or stored fields.
- Data arrows represent record-component references. They do not assert lifecycle ownership or implementation-level aggregation.
- Service placement does not prescribe execution order or dependency injection. No implementation wiring exists yet.
- No sequence diagram is included: the code does not yet implement the scheduling, retrieval or feedback loops. Their intended contracts are described in [the architecture notes](../architecture.md).
- Interface declarations do not enforce access control, budgets, draft transitions or local-only inference. Those behaviors require later implementation and tests.

## Render locally

A JDK and PlantUML JAR are required. The existing local JAR at `tmp/tools/plantuml.jar` is ignored by Git and is not bundled with the project. With that file present, run from the repository root:

```powershell
./docs/uml/render.ps1
```

To select explicit tool locations:

```powershell
./docs/uml/render.ps1 -PlantUmlJar 'C:/tools/plantuml.jar' -JavaExecutable 'C:/Program Files/Java/jdk-17/bin/java.exe'
```

The renderer uses the supplied Java executable, otherwise `JAVA_HOME`, otherwise `java` from PATH. It discovers current `.puml` files, validates syntax, and produces SVG and PNG locally without uploading project data. All views use the Smetana layout engine, so Graphviz is not required.

When Java contracts change, update the PlantUML declarations and regenerate previews together. Java sources remain authoritative.
