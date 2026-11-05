---
id: team-home
type: index
scope: team-development
updated: 2026-11-05
status: active
---

# SuperMAS Team Development Knowledge

This Git-versioned Obsidian vault holds shareable development knowledge. Open this directory as an existing vault, or read its Markdown files in an editor. Git in the parent project is the synchronization and review mechanism; there is no nested repository or required Obsidian plugin.

The dedicated `team-knowledge-vault` branch stores knowledge only. Application source remains on its respective branches; inspect it through Git or a separate code checkout. Do not merge this branch into a code branch. See [Knowledge-Branch-Access](Knowledge-Branch-Access.md) for reading notes while another branch is checked out.

Start with [Project-Status](Project-Status.md), then select a topic from [Source-Index](Source-Index.md). Verify claims against the appropriate revision before changing code. Repository paths in notes are relative to the project root, not this vault.

## Navigation

- [Architecture-and-Contracts](Architecture-and-Contracts.md) — domain vocabulary, responsibilities and planned execution flow.
- [Blackboard-Storage-and-Observation](Blackboard-Storage-and-Observation.md) — feature-branch persistence, leases and projections.
- [Mailbox](Mailbox.md) — independent desktop application and integration limits.
- [Build-CI-and-Delivery](Build-CI-and-Delivery.md) — branch-specific setup, validation and packaging.
- [Validation-and-Limits](Validation-and-Limits.md) — recorded evidence and what it does not prove.
- [Decisions-and-Open-Questions](Decisions-and-Open-Questions.md) — established boundaries and unresolved design choices.
- [Contribution-Workflow](Contribution-Workflow.md) — onboarding, Git review and knowledge maintenance.
- [Knowledge-Branch-Access](Knowledge-Branch-Access.md) — direct Git reads, change detection and optional hook design.
- [Retrieval-and-RAG](Retrieval-and-RAG.md) — context retrieval and future ingestion contract.
- [Agent-Guide](Agent-Guide.md) — agent handoff and optional skill design.
- [Templates](Templates.md) — reusable decision, evidence and handoff formats.

## Three distinct knowledge areas

| Directory | Role | Sharing |
| --- | --- | --- |
| `super-mas-team-dev-vault/` | Team development notes and source indexes | Git-reviewed Markdown |
| `super-mas-dev-vault/` | Personal context and private handoffs | Local, ignored |
| `runtime/super-mas-vault/` | Application knowledge and generated blackboard observations | Application data, ignored |

Do not require personal notes to understand shared work. Runtime SQLite is authoritative for application state; this vault describes development and never replaces that database. No semantic RAG service is installed by this documentation change.

Sources: `AGENTS.md`; user decision on 2026-11-05 to adopt Obsidian + Git and preserve the personal vault.
