# Step 2.4: Isolation Test, and the Phase 2 Gate

## Outcome

**The Phase 2 gate is met:** a fake agent completes a full loop and stops within budget. Boards also stay isolated when thread identifiers are equal across projects. This is verified against stand-ins only, not the team's real implementation.

## What was built

`IsolationTest` has 5 tests:

| Case | Expected result |
| --- | --- |
| Same thread identifier, different projects | Separate boards, with no shared evidence or drafts |
| Evidence from the other project | Rejected for both coordinator and agent |
| Task from the other project, with the same thread and task identifiers | Rejected for both |
| Two tasks in one thread | Separate boards |
| End to end through the coordinator | An agent sees only its own project's email, and a crossed `BoardId` is unknown |

## Do the tests catch broken rules?

Passing first time proves little (a weakness noted in step 2.1), so I disabled each key rule in turn and reran `IsolationTest` and `InMemoryBlackboardTest`. The code was restored afterwards, and `git diff` confirmed no change remained.

| Rule disabled | Tests failing |
| --- | --- |
| Evidence scope check | 1 |
| Task board check | 2 |
| Evidence read filter | 1 |
| Source and provenance check | 3 |
| Drafting capability check | 1 |

This covers these five rules only. It is not full mutation testing, and the coordinator's budget checks were not tested this way.

## Verification

The full suite has 89 tests, with 0 failures and 0 skipped, including the ONNX model tests.

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Test the whole `Scope`, using the same thread identifier | This is the exact collision the invariant names | Different identifiers: would pass even with no isolation |
| Add an end-to-end case | A board can be correct while the coordinator mixes boards up | Board tests only |
| Disable rules by hand once | Cheap direct evidence for the most important rules | Adding a mutation tool: a new dependency for little more value at this stage |
| Mark `P4`, `P5` and `P8` as implemented in `assumed-decisions.md` | The plan makes that list what goes to the team | Waiting until the end |

## Problems encountered

**The plan's "one proposal, one commit" rule was only partly followed.** P4, P5 and P8 each sit inside their step's commit, alongside the stand-in they belong to. Reverting a commit therefore removes more than the proposal. They live in stand-ins and are easy to remove by their markers, so I left it that way. The `If rejected` column is updated to say so.

## Critical points

- **These tests prove the stand-ins, not the system.** The real blackboard and coordinator must pass equivalent tests. The tests could be reused if they were written against the interfaces, but they currently construct stand-ins directly.
- **Retrieval isolation is still untested.** Phase 3 adds the path most likely to leak across projects, with the Lucene index, and its gate requires zero isolation violations.
- **Isolation holds only if `Scope` is correct.** Nothing checks that an email's project or thread are right, and the threader and importer set them. A wrong scope would place an email correctly inside the wrong project.
- **The audit is still outstanding.** Phase 1's gate remains open, as the project owner agreed. `MetadataAgent` uses rules for speech acts until then.
- **Team validation is outstanding** for P4, P5 and P8, and nothing has been sent to the team.
