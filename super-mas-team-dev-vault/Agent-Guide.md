---
id: team-agent-guide
type: workflow
scope: team-development
updated: 2026-11-05
status: active
---

# Agent Guide and Skill Proposal

## Follow-up workflow

`AGENTS.md` is the project entry point. Read [[Home]] and [[Project-Status]], then retrieve task-relevant notes and source. Keep source facts, intended contracts, proposals and historical tests distinct. Do not require the personal vault or local Chinese progress log for shared understanding.

At completion, update affected knowledge with the actual source scope and evidence. For unfinished work use the handoff format in [[Templates]]: objective, accepted decisions, relevant source, completed work, verification, blockers and next action. Remove private chat/recovery details before sharing.

## Should this become a skill?

The current baseline uses `AGENTS.md` plus ordinary Markdown workflows. This is enough to communicate repository conventions without requiring contributors to install a particular assistant configuration. No custom skill is created or installed by this task.

A future optional skill is useful when repeatable automation exists: checking branch applicability, validating internal links/frontmatter, locating stale source references, sanitizing publishable notes, or preparing a structured handoff. Candidate name: `supermas-team-knowledge`.

Keep such a skill thin: define triggers, discover the repository root, read the shared workflow using relative paths, invoke reviewed checking scripts and report evidence. Keep architecture facts in the vault, not copied into skill instructions. Mandatory behavior remains in `AGENTS.md` so assistants without the skill can follow the same process.

Skill packaging, discovery paths and installation differ by assistant. Confirm the target tool and read its current official skill guidance before creating an implementation. A repository skill file alone must not be described as automatically installed for every agent.

Related: [[Contribution-Workflow]], [[Retrieval-and-RAG]].
