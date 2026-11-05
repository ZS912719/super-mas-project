---
id: team-decisions
type: decisions
scope: team-development
updated: 2026-11-05
status: active
---

# Decisions and Open Questions

## Established decisions

| Decision | Reason and source | Scope |
| --- | --- | --- |
| Use Obsidian + parent-project Git for team knowledge | User decision, 2026-11-05; notes review alongside code | Team workflow |
| Preserve personal vault and separate application vault | User decision, 2026-11-05; prevent private/runtime data entering shared development retrieval | All branches adopting this workflow |
| Keep shared project content in English; personal communication and local-note languages follow each developer's preferences | User clarification on 2026-11-05 and `AGENTS.md` | International team conventions |
| Keep domain implementation in responsibility-named packages | Feature `docs/architecture.md`; avoid a broad runtime source package | Blackboard implementation |
| SQLite is authoritative; Markdown is rebuildable observation | Feature `docs/blackboard-runtime.md` | Blackboard branch; not yet main implementation |
| Model/tool calls occur outside short write transactions; use leases and fencing for execution | Same source; separate lock lifetime from task lifetime | Blackboard branch |
| Preserve mailbox/Java separation until an integration protocol is agreed | Feature `docs/architecture.md`, `mailbox/README.md` | Current implementation boundary |
| Keep approval version-specific and separate from sending | Main review contracts; feature mailbox draft implementation | Contract and mailbox behavior, different implementation coverage |
| Report evidence only within its tested/modelled scope | Branch validation reports and `formal/README.md` | All verification claims |

## Unresolved product and implementation choices

- Routing scores, confidence thresholds, tie-breaking and clarification fallback.
- Supplemental retrieval, reclassification and stopping rules.
- Specialist lifecycle, dependency injection, trusted role configuration and context permissions.
- Token/character accounting and cumulative execution/revision budgets.
- Java local inference protocol, simulator and output validation.
- Mailbox-to-coordinator integration, user identity and explicit human-review actions.
- Blackboard migrations/retention, dependency-specific conflict checks and uncertain external side-effect recovery.
- Public desktop authorization, durable mailbox storage and release signing.
- Development semantic retrieval: embedding model, index storage, freshness checks and service ownership.

These are open design topics, not implementation authorization. Main's older architecture document still lists storage as undecided; the feature branch resolves part of that question only within its own scope.

Source snapshots: [[Project-Status]]. Record new substantial decisions using [[Templates]] with context, chosen behavior, alternatives, consequences and evidence.
