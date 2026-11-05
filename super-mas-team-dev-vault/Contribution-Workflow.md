---
id: team-contribution-workflow
type: workflow
scope: team-development
updated: 2026-11-05
status: active
---

# Team Contribution Workflow

## Onboarding

Clone the project using its normal repository access. Open `super-mas-team-dev-vault/` as an existing vault in Obsidian; its local `.obsidian/` settings remain ignored. Read [[Home]], [[Project-Status]] and `AGENTS.md`. No private vault, paid sync service, community plugin or machine-specific path is required.

The parent project's Git history is the shared version source. Use it to exchange notes alongside code; do not run `git init` inside this vault. Avoid using a second synchronization mechanism on the same team folder. Members may use a Markdown editor instead of Obsidian.

Knowledge lives on `team-knowledge-vault`; application code lives on separate branches. Use direct Git reads for agents or a separate knowledge checkout for persistent Obsidian access; see [[Knowledge-Branch-Access]]. Do not merge this knowledge-only tree into application branches.

## Before editing

Inspect branch, HEAD and working-tree changes. Update from the team's accepted base through the ordinary Git workflow; do not discard others' edits. Prefer task branches such as `codex/<topic>` unless the user specifies otherwise. Read only the relevant knowledge and source files, checking their branch applicability.

## Editing and review

Keep one topic per note and stable filenames/ids. Shared notes must contain source paths and a date; implementation claims must identify branch/revision. Label proposals, historical evidence and superseded decisions explicitly. Pair each implementation change with a separate knowledge update targeting this branch, citing the code commit or PR. Do not paste complete source files, generated diagrams already in Git or whole conversations into notes.

Use English Conventional Commits when committing is authorized, for example `docs(knowledge): clarify blackboard recovery guarantees`. Knowledge PRs target `team-knowledge-vault`; implementation PRs target their code branches. Review correctness, source applicability, privacy, links and commands. Track the related knowledge update when reviewing code. A local note update does not imply a commit, push or PR has occurred.

## Conflict handling

Prefer edits to separate topic notes. When two contributors edit a shared note, reconcile both sets of source-backed facts and remove conflict markers; do not automatically accept one entire version. Preserve historical context only when useful and labeled. Resolve a disagreement through code evidence and an explicit team decision.

## Completion and maintenance

Update affected notes and dates, validation results and remaining questions. Add a short team handoff only when another contributor needs it; personal scratch work stays private. Update the ignored Chinese local progress log after code generation or rollback. Periodically reconcile [[Project-Status]] and [[Source-Index]] after branch integration, file moves and renamed entry points.

## Shareability checklist

Share architecture, contracts, design rationale, reproducible commands, synthetic examples and summarized evidence. Exclude credentials/tokens, real emails, personal identifiers, absolute workstation paths, runtime state, raw private chats, private stash/recovery details and unreviewed logs. Review content even when its source is an ignored personal note; Git ignore rules are not a content-sanitization mechanism.

The team vault's `.obsidian/`, `.trash/` and `.rag-cache/` are ignored. Build outputs and runtime databases remain outside the shared knowledge corpus. Do not ignore the team's Markdown notes.
