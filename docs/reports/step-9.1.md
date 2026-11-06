# Step 9.1: Defining `assess` for Reply Agents

## Outcome

`docs/plans/reply-agent-assess.md` fixes what our reply agents report to routing: `score = 0.6 × role hint + 0.4 × keyword score`, `sufficientContext` false only when a role-required field is reported missing, and a reason without email text. Generic is the fallback and scores 0. Code follows in step 9.3.

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Role hint weighs more than keywords | The hint describes who writes, which decides the procedure; keywords such as "exam" appear in mail from every role | Keywords only, which cannot tell a student's exam question from a teacher's |
| Keywords can still route alone (0.4) | The rule-based role hint is often empty (10 of 24 synthetic emails have none) | Hint only, which would send most mail to Generic |
| Fallback scores 0 | A fallback must never beat a fitting role; the registry already provides it when routing finds none | A small constant score, which competes with weak matches |
| `requiresVerifiedSender` | A role hint is a claim in untrusted text. Routing by claim must not widen what the reply may draw from | Trusting the claim |

## Critical points

- **Routing quality will largely be the role-hint classifier's quality.** The benchmark (step 9.8) must say so.
- The weights are a starting design, set before any measurement, and are not to be tuned on the synthetic set, which is too small and was written by the same author as the rules.
