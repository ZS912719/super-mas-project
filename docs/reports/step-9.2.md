# Step 9.2: Roles and Prompts as Configuration

## Outcome

The specialized agents are now configuration: one JSON file and one prompt file per role, plus one shared prompt. `RoleCatalog` loads the bundled roles from the class path, or a folder with the same layout that replaces them, validates them, composes each system prompt, versions it, and implements the shared `AgentConfigRepository`. Six tests pass.

## Layout

```
src/main/resources/agents/index.txt          list of bundled role files
src/main/resources/agents/<role>.json        one role (RoleDefinition)
src/main/resources/prompts/common.txt        rules shared by every role (trust boundary, language, no promises)
src/main/resources/prompts/<role>.txt        what the role handles and how
```

A deployment can point `RoleCatalog.fromFolder(path)` at `path/*.json` and `path/prompts/*.txt` instead.

| Field of a role | Use |
| --- | --- |
| `id`, `kind`, `displayName` | Identity; `kind` picks the role interface (step 9.3) |
| `prompt`, `rules` | Prompt file and extra one-line rules, both trusted |
| `roleHints`, `keywords` | What makes an email fit (`reply-agent-assess.md`) |
| `capabilities` | `draft-reply` for reply agents |
| `contextSources` | Sources the agent may read; the retriever still checks each one |
| `requiredFields` | Fields from the fixed `InformationField` list a reply needs |
| `fallback`, `requiresVerifiedSender` | One fallback at most; claim-proof routing for sensitive roles |
| `maxContextCharacters`, `maxInputCharacters` | Budgets (context must be below total input) |

**Bundled roles:** `student`, `teacher`, `research`, `administration`, `generic` (fallback). Their sources are `mail`, `general-info` and one role-specific source each (`student-faq`, `teaching-guide`, `research-office`, `admin-procedures`); a deployment defines these sources in its knowledge manifest (step 8.5), and an undefined source is simply denied.

**System prompt** = common prompt + role prompt + "Rules for this role" list. **Prompt version** = first 12 hex digits of SHA-256 of that text, so every edit changes the version with nothing to bump by hand.

**Validation:** identifier pattern, prompt file name pattern (no path), prompt present and not blank, context budget below input budget, no empty keywords, unique identifiers, at most one fallback.

**`save(AgentConfig)`:** for a folder catalogue, writes capabilities, keywords, sources and fallback back to the role's JSON. It refuses a changed system prompt, because prompts are edited in their files. The bundled catalogue is read-only.

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| JSON plus text files | Jackson is already a dependency; prompts as plain text are easy to review and diff | YAML (new dependency) or prompts inside JSON strings |
| One shared prompt for safety rules | The trust boundary and the "no promises, no sending" rules must not depend on each role author | Repeating them in every role prompt |
| Hash as prompt version | Never forgotten, and drafts can be traced to the exact prompt text | Manual version numbers |
| `RoleDefinition` in our package, `AgentConfig` unchanged | Extra fields would change a shared record, which needs the team | Adding fields to `AgentConfig` |

## Critical points

- **The prompts are untested against a real model.** They follow the architecture's trust-boundary rule, but field separation and prompt wording alone cannot guarantee resistance to injection (`docs/architecture.md`). Human review remains the safeguard.
- **Keyword lists were written by hand** and include some French words; they were not tuned on data.
- **Source names are conventions.** `mail`, `student-faq` and the others must match the deployment's knowledge manifest, or agents read less than intended (they never read more).
