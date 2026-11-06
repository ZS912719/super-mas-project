# Step 8.5: Loading Knowledge per Source

## Outcome

`KnowledgeLoader` loads trusted reference documents from a folder into the project indexes and returns the trusted source list the retriever needs. Rights are per source, written in one manifest. Five tests pass: an agent finds only sources it may read even when another source matches better, reloading drops deleted files, links leaving the folder are not followed, and a source identifier cannot escape the folder.

Step 8.6 (persistent facts about correspondents) is **cut**, as the plan allows: it is the first item to cut, it needs a verified-sender signal that does not exist, and Phase 10 was added in its place.

## How it works

```
knowledge/
  sources.json      [{"id": "student-faq", "project": "uni", "readers": ["student", "generic"]}]
  student-faq/      *.md and *.txt, any depth
```

| Rule | Behaviour |
| --- | --- |
| Document identifier | `kb:<source>:<relative path>` |
| Scope | The source's project, thread `kb:<source>`. Domain documents have no `MessageKind`, so they can never come back as thread history |
| Title | First `# ` heading, else the file name |
| Readers | From the manifest, both on each document and on the `ContextSource` |
| Reload | Deletes the source's documents, then adds the current files |
| Limits | Only `.md` and `.txt`; empty files and files over 200,000 characters are skipped and listed in the report |
| Paths | Source identifiers must match `[a-z0-9][a-z0-9-]*`. Links are not followed, and every file's real path must stay inside the folder |

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Rights in one manifest | One place to audit who reads what, as with every other source | Rights per file, which nobody would maintain |
| Whole-source reload | Simple and correct for deletions | Diffing files, unneeded at this size |
| A plain folder, not a database | Whoever maintains a policy edits a Markdown file | A storage format, which is open decision 7 for the team |

## Critical points

- **Large documents are not split into passages.** A long policy is one document, so retrieval returns all of it or nothing within the budget. Splitting by heading would be the next improvement.
- **Knowledge is trusted to load, not trusted as instructions.** It enters model input as untrusted data like any evidence. A staff member who writes "ignore your rules" into a policy is still stopped by the trust boundary, not by the loader.
- **The loader does not check that readers exist as agents.** A misspelled reader silently grants nothing, which fails closed.
