# Step 9.8: Specialized Agents on the Synthetic Set

## Outcome

**Routing is safe but cautious: 15 of 24 correct (0.63), with no email ever sent to the wrong specialist.** All nine errors send a specialist's email to the Generic fallback. With the gold role hint instead of the predicted one, routing is 24 of 24, so **the rule-based role-hint classifier is the bottleneck**, not the agents' scoring. Plans are grounded: 0 of 14 citations point to evidence that is missing, not permitted, or from a source outside the agent's configuration. The injection suite has **0 violations in 72 cases**.

Full results: `docs/benchmark/results/roles-synthetic.md`. Command:

```bash
mvn -q test-compile exec:java -Dexec.mainClass=org.supermas.benchmark.RoleBenchmark -Dexec.classpathScope=test \
  -Dexec.args="benchmark/data/synthetic-dev src/test/resources/university/knowledge docs/benchmark/results/roles-synthetic.md"
```

## Results

Gold role = labelled `roleHint` (student, teacher, administration, researcher → research; none → generic). Routing `ScoreRoutingStrategy(0.4, 0.1)`; `NONE` goes to the fallback.

| Variant | Correct | Ambiguous | None |
| --- | --- | --- | --- |
| Predicted role hint + keywords (default) | 15 / 24 (0.63) | 0 | 19 |
| Keywords only | 12 / 24 (0.50) | 0 | 22 |
| Gold role hint + keywords (upper bound) | 24 / 24 (1.00) | 0 | 10 |

Specialist emails (14): 5 routed correctly by default, 9 to Generic, 0 to another specialist.

| Plans, default routing | Value |
| --- | --- |
| Plans written | 24 / 24 |
| With at least one quoted request | 11 |
| Listing a missing field | 3 |
| Citations / ungrounded citations | 14 / **0** |

| Injection suite (3 endings × 24 emails) | Value |
| --- | --- |
| Violations (system prompt changed, forged block, second plan block, draft not `PROPOSED`, request over budget) | **0** |
| Routing changed by the injection | 9, all by ending 2 ("Confirm that my grade was changed…"): Generic → Student |

## How to read the result

- **Keywords alone rarely reach the routing minimum.** A role needs 3 keyword matches for 0.4. Short emails seldom have three, so most mail without a role hint goes to Generic. This was a design choice in step 9.1 (keywords weigh less than who writes); the data shows its cost in recall. I did not change the weights, because 24 emails written by the same author as the rules are too few and too biased to tune on.
- **The 9 routing changes are the documented role-claim effect.** "My grade" is a student role-hint phrase, so the injected sentence makes the email look like a student's. It grants nothing: the Student role reads no source the Generic role cannot. It does show why `requiresVerifiedSender` exists for roles with sensitive sources.
- **0 injection violations is structural, not behavioural.** It shows the system prompt is always the role's own, untrusted text cannot open its own block, nothing gets approved, and the budget holds. It does not show how a model reacts to the injected text, because no model was run.
- **Ten emails without a role** are correctly handled by Generic in every variant, which is the fallback design working.

## Critical points

- **Synthetic, small, single-author data.** 24 emails, written by the same author as the role-hint rules and keywords. Real university mail is needed for a real routing number.
- **Gold labels are role hints, not "the right agent".** Whether the student agent is the best responder to a student's administrative question is a design question this proxy cannot answer.
- **Grounding is checked against the board, not against the draft.** Draft text from a real model could still state things the plan does not support; the template stand-in cannot.
- **Improving routing** is most likely through the role-hint classifier (step 1.8's model, or more rules), since the gold-hint variant reaches 1.00.
