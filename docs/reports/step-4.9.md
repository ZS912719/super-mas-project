# Step 4.9: Correspondent Topic Type

## Outcome

Correspondent topics are built and off by default. When on, an agent reads a verified sender's other threads in the same project. An unverified or forged sender gets nothing. **No real verifier exists,** so in the product this type stays inactive until the team supplies one: the only built-in verifier says no to everyone.

## What was built

| Item | Role |
| --- | --- |
| `TopicType.CORRESPONDENT` | The third type in `TopicContextAgent` |
| `CorrespondentSettings` | The switch, with `off()` as the default |
| `SenderVerifier` | The verification signal, with `none()` as the only implementation |
| `TopicTagger.correspondentTag` | Tags a message with its sender only if verified |
| `CorrespondentTopicTest` | 13 tests |

## Security checks

| Case | Result, each with a test |
| --- | --- |
| Switch off, or on with the default verifier | Nothing found, score 0 |
| Switch off with a verified sender | Nothing found, score 0 |
| Forged sender claiming a real person's address | No history, score 0 |
| Forged mail indexed in the past under a victim's address | Never joins the victim's history |
| Same sender in another project | Nothing found |
| Message the agent may not read | Stays hidden |
| Verifier throws an error | Treated as not verified |
| Blank sender | Never verified |

I also changed six security rules by hand across this type, thread retrieval and the merge, one at a time, and ran the tests:

| Removed rule | Caught? |
| --- | --- |
| The `enabled` switch | **No at first.** Added a test, and it is now caught |
| Sender verification at retrieval | Yes |
| Sender verification at indexing | Yes |
| Draft exclusion from thread history | Yes |
| Source and message rights on thread history | Yes |
| The requester's rights in the merge | Yes |

The first row was a real gap: `off()` also used a verifier that rejects everyone, so no test depended on the switch.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Verification is an interface, with "no" as the default | The plan requires a real signal, and this project has none | Reading `Authentication-Results`: a sender can write that header unless the receiving system removes it, and DMARC proves a domain, not a person |
| Tag at indexing only if verified | Forged history can never be found later, whatever the verifier says at reading time | Filtering at reading time: there would be no record of who was verified then |
| Check the switch in `execute` too | A scheduler may run an agent without assessing it | Relying on `assess` |
| A hashed tag | Addresses do not appear in identifiers or logs | The plain address |
| Normalize case and spaces | The same person should have one history | Exact text |

## Critical points

- **Verification is at most as good as the verifier.** Everything here assumes it is honest. A team member implementing it from message headers would reintroduce forgery.
- **A sender's other threads may hold what should not reach this reply.** This is the plan's main concern with this type. A verified sender and the same project limit the risk but do not remove it: a confidential thread with the same person would still be found. The switch, source rights and message rights are the only controls.
- **Addresses are not people.** One person can have two addresses, giving two histories, and a shared mailbox gives one history to several people.
- **`docs/architecture.md` still forbids this behaviour.** Step 7.3 updates its isolation rule. The exception is decision D1 in `docs/plans/assumed-decisions.md`.
- **A previously verified message cannot lose its tag automatically.** If a verifier is found to be wrong later, tags must be rebuilt.
- **Hashing is not anonymity.** Anyone holding a candidate address can compute its tag.
