# Step 4.7: LLM Variant of the Missing-Information Check

## Outcome

`LlmMissingInformationCheck` exists behind the same interface as the rules and is used only if chosen. **It has never run against a real model.** All 7 tests use a fake `LocalLlm`, because the LLM adapter comes in step 5.1. So nothing here shows that it works better or worse than the rules.

## What the model receives

```json
{"present":{"DATE":false,"DEADLINE":false,"PERSON":true,"COURSE":false,"ID":false},
 "speechActs":["request"],"mentions":["meeting"]}
```

No email text, subject, name or value. A test plants secrets in each of those and confirms that none reach the request.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Fixed labels only | Follows the plan, and removes the injection route, because nothing an attacker writes reaches the model | Sending the email: more accurate perhaps, but it reopens injection and sends mail to a possibly online model |
| Added `mentions` beyond the plan's field names and presence | Without it, a meeting request and any other request look identical, so the model has no basis to decide. Values come from the table's row names | Strictly names and presence, which would be close to a guess |
| Speech acts filtered to the four known | Metadata is built from email text, so a bad value must not pass through | Trusting the stored labels |
| One unknown field rejects the whole answer | It suggests the model is off script, so its other names are not trustworthy either | Dropping only the unknown name |
| Fall back to the rules, and record the source | The reply flow must not depend on the LLM, and benchmarks must count fallbacks | Reporting nothing missing: hides the failure |

## Critical points

- **The online LLM rule still applies to any later benchmark.** Summaries hold no text, but the rule against sending personal mail online should still be read as covering this path until someone decides otherwise.
- **Its value is doubtful by design.** The model sees only what the rules already derived from the same table. It can differ only by judging importance, so it may add cost and nothing else. The plan's section 7 lists this step as one to cut if behind.
- **A crafted email still steers the input indirectly.** Words in the email can set `mentions` and `present`. This changes the answer within the fixed list, which at worst causes one extra bounded retrieval.
- **The prompt is untested on any model.** It may need changes, and the plan requires measuring again on the final local model.
- **Step 4.10 does not compare the two.** No gold data exists and no model is available, so the "rules vs LLM" comparison in section 6 of the plan remains open.
