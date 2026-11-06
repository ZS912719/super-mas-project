# Specialized Agents, Prompts, Contributions and the Composer

**Packages:** `org.supermas.agent`, `org.supermas.agent.role`, `org.supermas.agent.collaboration`. **Resources:** `src/main/resources/agents/*.json`, `src/main/resources/prompts/*.txt`. **Reports:** steps 9.1-9.8, 12.

## Architecture

- **One class, many roles.** `RoleAgent` serves every role; a role is a JSON file (identifier, kind, prompt file, role hints, keywords, capabilities, readable sources, required fields, rules, fallback flag, verified-sender flag, budgets) plus a prompt file. `RoleCatalog` loads and validates them (bundled or from a folder) and builds each trusted system prompt: `common.txt` + role prompt + rules. `RoleAgents.create` gives each instance its role interface (`StudentAgent`, `TeacherAgent`, `ResearchProjectAgent`, `AdministrationAgent`, `GenericAgent`).
- **Estimate.** Fit = 0.6 × (role hint matches) + 0.4 × keyword score, 0 for the fallback and for unverified senders where required. For the blackboard: the same fit, if the role would answer at least one request on the board (a keyword in the request, or every request on a role-hint match), and 0 after it contributed until new feedback.
- **Contribution** (`contribution/v1`). For the requests it answers: its own search (thread + its sources), a budgeted selection, a `ReplyPlan` (quoted requests, facts, evidence references, missing fields, rules, notes) and short excerpts. Posted privately; only the composer gets a copy.
- **Request splitter.** Splits the email into quoted requests (`request/v1`), rules by default, an LLM extractor that may only quote as an option.
- **Composer.** Merges every contribution into one plan (requests in order, overlaps ordered by estimate, uncovered requests flagged for the reviewer), builds a trusted prompt from the catalogue (`common.txt` + `composer.txt` + contributors' rules) and the untrusted data (email, feedback, plan, excerpts), and asks the generation service for one draft.
- **Prompt boundary.** `RolePromptBuilder`: trusted system prompt only from configuration; untrusted labelled blocks; `<<`/`>>` in untrusted text broken; input budget enforced, email and feedback cut and marked if needed, never the plan.

## Choices and alternatives

| Choice | Alternative | Why |
| --- | --- | --- |
| **Roles as configuration** | One Java class per role | Statement §13: add or change agents without changing the application; a new role is two files |
| **Plan before draft** (structured reply plan) | Free text from the model | Traceable: every claim points to evidence on the board; the template and a model use the same plan |
| **Specialists contribute, the composer drafts** (Phase 12) | Each specialist drafts (router) | Mixed emails are answered completely; one reply per email |
| **Specialists never see each other's contributions** | Shared contributions | Excerpts come from role-restricted sources; the composer resolves overlaps |
| **Composer rules from the catalogue** | From the contributions | A contribution is agent output; trusted instructions must not travel through the board |
| **Prompts in files** | In code or a database | The user edits them without recompiling (statement §5.1); version = hash of the prompt, recorded with each plan and draft |

## Results

Router baseline on 24 synthetic emails: 15 correct, never the wrong specialist (errors go to the fallback); with the gold role hint 24 of 24, so the role-hint classifier is the bottleneck. Plans: 0 of 14 citations ungrounded. Injection suite: 0 violations in 72 cases. Blackboard on 12 multi-request emails: see [evaluation](evaluation.md).

## Why it complies

- **Statement §4.2, §5, §13, §14.6-7, §14.11:** the five illustrative agents exist, each with its own prompt and context, added by configuration; several suitable agents collaborate; no suitable agent leads to the fallback.
- **Targeted architecture:** specialists are independent knowledge sources with condition (estimate) and action (contribution) parts, posting partial solutions at one level that another source (the composer) merges, as in BB1.

## Open points

A SuperMASProject role (statement §6) is two files away; agent management through a UI is not built; specialists are rule-based and do not use a model or the feedback text themselves.
