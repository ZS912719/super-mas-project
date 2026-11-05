# SuperMAS Team Knowledge

This is the dedicated `team-knowledge-vault` branch. It stores shared development knowledge and maintenance instructions. Application code, build scripts and CI remain on their code branches; this branch contains no executable application.

## Dependencies and structure

Use Git to exchange committed notes. Obsidian is optional for editing and navigation; a Markdown editor is sufficient. No plugin, paid sync service, vector database or nested Git repository is required.

- `super-mas-team-dev-vault/`: English team notes, source index, decisions and workflows.
- `AGENTS.md`: agent entry point and maintenance instructions.
- `.gitignore`: excludes personal context, local settings, runtime data and caches.
- `LICENSE`: repository license.

## Usage

Open `super-mas-team-dev-vault` as an existing Obsidian vault. Start with [Home](super-mas-team-dev-vault/Home.md) and [branch-aware status](super-mas-team-dev-vault/Project-Status.md).

Agents on another branch can read knowledge directly:

```sh
git show team-knowledge-vault:super-mas-team-dev-vault/Home.md
```

See [cross-branch access](super-mas-team-dev-vault/Knowledge-Branch-Access.md) for consistent reads, change detection and optional hooks. A separate Git checkout helps when persistent Obsidian access is needed. Knowledge updates target this branch; do not merge its knowledge-only tree into code branches.

## Progress

Team notes include branch-attributed knowledge and maintenance workflows. Personal `super-mas-dev-vault` and application `runtime/super-mas-vault` remain distinct and local. Hooks and semantic RAG are documented proposals, not installed services.
