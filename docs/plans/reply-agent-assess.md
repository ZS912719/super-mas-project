# What `assess` Means for a Reply Agent

A reply agent (Student, Teacher, Research, Administration, Generic) is a candidate to answer the email. Routing (the team's `RoutingStrategy`) turns the assessments of all reply candidates into one decision: clear, ambiguous or none. This document fixes what our reply agents report, so routing code can rely on it. The context-agent version is `context-agent-assess.md`.

## The fields

| Field | Meaning for a reply agent |
| --- | --- |
| `score` | How well the email fits this role, from 0 to 1. It is not the agent's confidence in a good answer |
| `sufficientContext` | False only when the missing-information check, visible to this agent, names a field this role requires. True otherwise, including when no check ran |
| `reason` | A fixed sentence with counts and the matched role hint. It never contains email text |

## How the score is computed

`score = 0.6 × hint + 0.4 × keywords`, capped at 1, where:

- **hint** is 1 when the MetadataAgent's `roleHint` is one of the role's `roleHints`, else 0;
- **keywords** is the number of distinct role keywords found as whole words in the subject and new text, divided by `min(3, number of keywords)`, capped at 1, as for subject topics.

A role hint alone gives 0.6, three keywords alone give 0.4, and both give 1. Routing thresholds are the team's; our tests and benchmark use the stand-in `ScoreRoutingStrategy(0.4, 0.1)`.

**Fallback.** The Generic agent has `fallback: true` and always scores 0. It answers when routing finds no clear candidate, through the registry's fallback, so it never wins over a role that fits.

**Verified senders.** A role marked `requiresVerifiedSender` scores 0 unless the configured `SenderVerifier` confirms the sender. Use it for any role whose sources must not reach a sender who merely claims the role (below).

## Rules

1. **Deterministic, no LLM, no index access.** Same email and board, same assessment.
2. **Another project is not a concern.** Reply agents are not configured per project; their rights come from their sources, which are per project.
3. **A score grants nothing.** Rights stay with the sources in the agent's configuration and the retriever's checks.
4. **No match is normal.** Score 0 and no failure.

## What readers must not assume

- **A role hint is a claim made in the email.** "I teach…" makes the teacher role fit. That is fine for choosing the tone and procedure of a reply, but routing must never be how rights are decided. A role whose sources hold anything a student must not see needs `requiresVerifiedSender`.
- **`sufficientContext` true does not mean the answer will be complete.** It only says no required field was reported missing.
