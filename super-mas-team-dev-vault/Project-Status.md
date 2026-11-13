---
id: team-project-status
type: status
scope: team-development
updated: 2026-11-08
status: active
---

# Branch-Aware Project Status

This is a dated local Git inspection, not a live remote-branch or hosted-CI report. Inspect `git status --short`, `git branch --show-current` and `git log -1` at task start.

| Baseline inspected | Available capabilities | Limits |
| --- | --- | --- |
| `main` at `d62e4fc` | Java 17 interfaces/data contracts, UML, CI and delivery infrastructure | No tracked mailbox, durable blackboard runtime, formal models or Java runtime tests |
| `team-knowledge-vault`, originally based on `d62e4fc` | Shared development knowledge and maintenance instructions only | Application code/build/CI files are removed from its current tree; historical commits and other branches retain them |
| `feat/mailbox-desktop` at `c039b37` | Independent Electron/Node mailbox | Review still required; not connected to Java agents |
| `codex/blackboard-storage` at `d58b69c` | Desktop mailbox plus SQLite blackboard, leases, projections, tests, models and runtime-vault rename | Complete orchestration, Java retrieval/model/review services and mailbox integration deferred |

Local branch references can change or be absent in another clone. Use `git show <ref>:<path>` to inspect another revision without changing your working tree; fetch through the team's normal Git workflow when needed. Commit identifiers above identify inspected snapshots, not permanently current versions.

## Documentation drift identified during initialization

The `main` README contains older mailbox instructions although that module is not tracked there. `docs/architecture.md` describes the contract baseline and has not incorporated feature-branch storage choices. `docs/blackboard-distribution.md` and its launcher use the older `runtime/vault` name; the feature branch uses `runtime/super-mas-vault`. Confirm paths when integrating these branches. Adding this vault does not merge those features or update their implementations.

## Integration milestones

Implementation documents cited above remain on their named code branches. This knowledge branch preserves its original ancestry for recoverability; it is not an orphan branch and does not erase earlier code history.

1. Review and integrate desktop mailbox behavior independently.
2. Review and integrate durable blackboard storage and observation, reconciling package paths, runtime-vault naming and CI packaging.
3. Agree orchestration, retrieval, model and human-review integration protocols before implementing a complete agent workflow.
4. Introduce development semantic RAG only as a separate capability with defined corpus, freshness and access controls.

Sources: local branch tips and tracked trees inspected on 2026-11-05; `docs/ci-cd.md`; branch-specific `docs/architecture.md` and `mailbox/README.md`. Related: [Validation-and-Limits](Validation-and-Limits.md).

## Prototype integration: 2026-11-07

The earlier table above is historical. Prototype now contains a runnable multi-agent pipeline and opt-in mailbox connection. Merge `5504b71e925bd25765ef7462e974e4b040aad0c3` adds the independent generation workspace from `feat/generation-ui` at `4563c2a`; it was pushed to origin/prototype and verified remotely. See [Generation-UI-Integration](Generation-UI-Integration.md) for capabilities and the current validation limits. Other protocol designs in the local knowledge workspace are outside this publication; no installed knowledge automation is claimed.

## Grafana observation published: 2026-11-08

Prototype revision `f9151ac97f0f0654f5c215030a1627e11e55f115` adds a separate authenticated read-only observation API and provisioned Grafana dashboard through five focused commits based on 5504b71. It was pushed and the remote tip was verified at this exact revision on 2026-11-08. Synthetic Docker/browser validation passed; native Windows Grafana and real Windows-database/mailbox integration remain unverified. See [Grafana-Blackboard-Observation](Grafana-Blackboard-Observation.md). The earlier generation-workspace merge remains part of its ancestry.

Documentation follow-up `2b8bba3dadc3097a651a9b315bbe7e60a1379cb8` was also pushed and remotely verified on 2026-11-08. It adds versioned English implementation prompts and moves README progress into `docs/project-progress.md`; runtime validation still applies to the unchanged feature code at f9151ac.

## Desktop drafting and review feature branch: 2026-11-13

[Mailbox-Draft-Review](Mailbox-Draft-Review.md) records the published feature branch, opt-in demo generation, mailbox draft review, and validation. The branch has not been merged into prototype.
