# Blackboard Kripke Models

Use nuXmv to check the finite transition systems in [tasks.smv](tasks.smv) and [projection.smv](projection.smv). Definitions follow `K = (S, S0, R, L)`: variable valuations are states, `init` defines initial states, `next` defines transitions, and named predicates label states. Events model nondeterministic interleavings. Terminal/bounded situations retain idle transitions; counters never wrap.

The models are design abstractions, not automatically extracted proofs of Java, SQLite, the filesystem or Obsidian. The first model has one contested task and two execution workers. Workers can represent different attempts of the same configured agent. It bounds execution epochs at two and business revisions at six. The second model bounds revisions at four and abstracts complete document payloads into three distinguishable values with historical payload storage. Splitting the concerns reduces symbolic state space; their composition is supported by integration tests, not a formal refinement proof.

## State transitions and implementation mapping

| Model action/state | Java implementation |
| --- | --- |
| Acquire/release `write_lock` | `SqliteBlackboardStore.transaction`, `BEGIN IMMEDIATE`, commit/rollback |
| Claim, owner, epoch, worker token | `claim`, active lease and unique UUID token |
| Expected revision | Lease revision validation in `contribute`, `heartbeat`, `complete` |
| Expire/recover | `recoverExpired` safe-retry branch; uncertain work is tested in Java |
| Crash/restart | Writer lock released, committed SQLite state survives |
| Render captured revision/payload | `MarkdownProjector.render` of one `VersionedSnapshot` |
| Publish captured revision/payload | Complete generation and atomic `current.md` replacement |
| Acknowledge | Monotonic projection watermark in SQLite |

`tasks.smv` checks three safety invariants and two reachability properties: stale attempts cannot complete, running tasks have one owner, owned tasks have an epoch, recovery can produce a second attempt, and an older worker token can coexist with a new active attempt. It models token/lease/revision checks and crash lock release, not SQL lock internals, source permissions, uncertain external side effects or idempotent receipts.

`projection.smv` checks four safety invariants, one reachability property and one LTL progress property. Documents only reference committed revisions, publication never regresses, acknowledgements never lead publication, and document payloads match history at their declared revision. Publication before acknowledgement is reachable. Eventually the published and acknowledged revision stays equal to the database revision.

The LTL progress property assumes the environment eventually stops business updates and crashes (`stable`), and render/publish/acknowledge events recur fairly. The eventual-stability assumption also excludes permanent disk failure and permanent downtime. There is no claimed time bound. All seven safety invariants are checked again without fairness, so progress assumptions cannot hide unsafe prefixes.

## Reproduce

Download the [official nuXmv Windows 2.1.0 archive and checksum](https://nuxmv.fbk.eu/previous-version.html), verify its SHA-256 and extract it into `tmp/tools/nuxmv`. The archive digest used for validation is `ad1f0f1df931eb274da21f1fbebee4ba1949ef5c1718650b84ba82cf2e579f1f`. The tool stays outside version control. Review the [tool license](https://nuxmv.fbk.eu/license.html) for your usage; no binaries are distributed by this repository.

```powershell
./scripts/check-formal.ps1
# Or select another compatible executable:
./scripts/check-formal.ps1 -NuXmv './tmp/tools/custom/nuXmv.exe'
```

The script checks result counts and the process exit code. Logs and generated variants are saved in `target/formal`. Expected results:

| Run | True | False |
| --- | ---: | ---: |
| Task model | 5 | 0 |
| Projection model | 6 | 0 |
| Task safety without fairness | 3 | 0 |
| Projection safety without fairness | 4 | 0 |
| Broken execution fencing | 2 | 1 |
| Broken payload capture | 3 | 1 |

The two negative controls deliberately bypass worker fencing and publish a live payload under a captured historical revision. Each must produce a counterexample. These false results are expected and make the validation script fail if the defect is no longer detected.

Runtime audits complement model checking: generated Markdown is independently read back and compared with its historical SQLite source. Obsidian's cached screen is outside both models. Semantic correctness of an agent's reasoning is also outside these storage guarantees.

Primary references: [SQLite isolation](https://www.sqlite.org/isolation.html), [transactions](https://www.sqlite.org/lang_transaction.html), [WAL](https://www.sqlite.org/wal.html), [nuXmv documentation](https://nuxmv.fbk.eu/documentation.html), [Obsidian storage and cache](https://obsidian.md/help/data-storage).
