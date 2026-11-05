---
id: team-branch-access
type: workflow
scope: team-development
updated: 2026-11-05
status: active
---

# Knowledge Branch Access and Change Detection

## Direct Git access is the default for agents

An agent can inspect committed notes without checking out the knowledge branch or maintaining a copy. Run from any checkout of this repository:

```powershell
git rev-parse team-knowledge-vault
git show team-knowledge-vault:super-mas-team-dev-vault/Home.md
git show team-knowledge-vault:super-mas-team-dev-vault/Project-Status.md
git ls-tree -r --name-only team-knowledge-vault -- super-mas-team-dev-vault
```

For a consistent retrieval batch, resolve the ref once and use that returned commit id in subsequent reads. These commands read committed objects, not uncommitted edits. Local refs can lag collaborators: an authorized `git fetch origin` updates remote-tracking refs, not automatically the local branch. Select either a reviewed local branch or `origin/team-knowledge-vault` explicitly; do not silently alternate. The remote ref exists only after publishing and fetching.

## Detect changes since actual reading

Keep the last knowledge commit actually read in a per-agent/task local checkpoint, outside tracked notes. Resolve the selected ref at task start. If it differs, compare old/current commit ids:

```text
git diff --name-status <previous-commit> <current-commit> -- super-mas-team-dev-vault AGENTS.md
git diff <previous-commit> <current-commit> -- super-mas-team-dev-vault
```

Read changed relevant notes, including removals/renames and status changes. Advance the checkpoint only after reading; otherwise preserve it or record deferred notes. On first use or a missing old commit, start from Home and Project-Status. Comparing two snapshots also works when history is not a simple fast-forward.

Initially the checkpoint can live in a task handoff, without a daemon. Long tasks should recheck at meaningful checkpoints. Neither Git nor a hook rewrites a running model's context: the agent must consume results explicitly. A code-branch instruction pointer or direct user guidance is needed to discover this protocol.

## Optional hook design, not installed

Git hooks run on specific Git events, not continuously on remote changes or unsaved edits. `post-commit`, `post-checkout` and `post-merge` can prompt a lightweight ref check for local operations, but do not cover every ref-update path. `reference-transaction` can observe ref transactions, including relevant fetched remote-tracking updates; filter the exact knowledge ref and committed phase and handle repeated events safely.

A future hook should only write a small local notification with old/new knowledge ids and changed paths. Avoid network access, model calls, automatic commits or tracked-note writes inside hooks. The agent still directly checks the current ref and maintains its reading checkpoint, so missed or disabled hooks cannot hide updates. Preserve existing hooks when installing; versioning a hook file does not install it for every clone. No hook, watcher, service or checkpoint file is installed by this documentation change.

## When a separate checkout helps

Obsidian reads actual files. For continuous access while editing code on another branch, use a separate Git worktree or clone of the knowledge branch. This is a Git-managed checkout, not a manually synchronized vault copy. Its files remain visible when the code checkout changes branches; updates still use Git.

Agents using Git object reads need no extra checkout. Uncommitted knowledge edits are visible only in the editing worktree until committed. Do not create a manually copied vault as a second authority. A managed worktree can be created later for persistent Obsidian editing; none is created by this change.

## Branch boundaries

This branch's current tree omits application source, build scripts and CI. Keep the maintenance README, AGENTS, ignore rules and license alongside notes. Original ancestry remains recoverable and main/feature refs remain unchanged. Do not merge this branch into code branches: that also proposes code deletions. Knowledge PRs target this branch; code and discovery pointers are reviewed on their own branches.

References: [Git show](https://git-scm.com/docs/git-show), [Git diff](https://git-scm.com/docs/git-diff), [Git hooks](https://git-scm.com/docs/githooks).
