# Current Java API UML

These diagrams describe the Java interfaces, selected data contracts and the first local SQLite blackboard runtime. A complete email-processing assistant is not implemented.

## Diagrams

| View | Editable source | Vector preview | Raster preview |
| --- | --- | --- | --- |
| Java package dependencies and source inventory | [PlantUML](package-overview.puml) | [SVG](package-overview.svg) | [PNG](package-overview.png) |
| Blackboard and specialist interfaces | [PlantUML](class-diagram.puml) | [SVG](class-diagram.svg) | [PNG](class-diagram.png) |
| Coordination, context, model, storage and review interfaces | [PlantUML](service-contracts.puml) | [SVG](service-contracts.svg) | [PNG](service-contracts.png) |
| Core task, evidence and draft data contracts | [PlantUML](data-contracts.puml) | [SVG](data-contracts.svg) | [PNG](data-contracts.png) |
| SQLite blackboard runtime and Markdown observations | [PlantUML](runtime.puml) | [SVG](runtime.svg) | [PNG](runtime.png) |

All 24 current interfaces are covered across the blackboard/specialist and service views, including the new `blackboard.persistence.BlackboardStore`. Blackboard service contracts live in `blackboard.api`; snapshot and transactional storage contracts live in `blackboard.persistence`, separate from their implementations. Method names, parameter types, return types and interface inheritance match `src/main/java/org/supermas`. Long parameter lists use display-only line breaks. The five specialist role interfaces currently declare no additional methods.

The data view shows 15 selected records nested in `org.supermas.model.Domain`, including all components of each displayed record. Supporting routing, context and model records/enums remain defined in `Domain.java` and appear by type name in signatures and fields. Field-reference multiplicities express intended API semantics, not runtime validation.

## Reading the diagrams

- Package arrows represent imports found in Java source files. Package inventories are source-file lists rather than extra Java classes.
- Hollow triangle arrows represent actual interface inheritance.
- Dotted arrows in the interface view represent parameter/return type dependencies, not runtime calls or stored fields.
- Data arrows represent record-component references. They do not assert lifecycle ownership or implementation-level aggregation.
- Service placement in contract views does not prescribe execution order or dependency injection. The runtime view documents the implemented storage/observation relationships.
- No sequence diagram is included. Task/storage/publication transitions are described in [runtime notes](../blackboard-runtime.md) and [Kripke models](../../formal/README.md); scheduling, retrieval and feedback loops remain deferred.
- Interface declarations alone do not enforce behavior. The runtime implements an initial scope/source/lease boundary and monotonic budget counters; human review and local-only inference remain deferred.

## Render locally

A JDK and PlantUML JAR are required. The existing local JAR at `tmp/tools/plantuml.jar` is ignored by Git and is not bundled with the project. With that file present, run from the repository root:

```powershell
./docs/uml/render.ps1
```

To select explicit tool locations:

```powershell
./docs/uml/render.ps1 -PlantUmlJar './tmp/tools/plantuml.jar' -JavaExecutable './tmp/jdk-17/bin/java.exe'
```

The renderer uses the supplied Java executable, otherwise `JAVA_HOME`, otherwise `java` from PATH. It discovers current `.puml` files, validates syntax, and produces SVG and PNG locally without uploading project data. All views use the Smetana layout engine, so Graphviz is not required.

When Java contracts change, update the PlantUML declarations and regenerate previews together. Java sources remain authoritative.
