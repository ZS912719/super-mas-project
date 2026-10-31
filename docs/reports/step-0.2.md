# Step 0.2: JUnit 5

## What was built

- `pom.xml` now has `junit-jupiter` 5.11.4 with `test` scope. It does not enter the packaged jar.
- `src/test/java/org/supermas/model/ScopeTest.java` has two tests.

## Why

Every later step must end with passing tests, so the project needs a working test setup first.

The plan asked for one trivial test. I used two small tests of `Scope` equality, because they also record the isolation rule that equal thread IDs in different projects are different scopes. They only test Java record equality, not real isolation, which step 2.4 covers.

Version 5.11.4 is a stable release. Maven Central's search reported the milestone `5.13.0-M3` as latest, and I did not use a milestone for this project. I did not check for a newer stable release.

## Results

Environment: Maven 3.9.12, OpenJDK 17.0.20.1.

| Command | Result |
| --- | --- |
| `mvn -B test` | 2 tests run, 0 failures, 0 errors, 0 skipped |
| `mvn -B package` | Succeeded; the jar contains no test classes |

## Known issue

`README.md`, `docs/architecture.md` and `CLAUDE.md` still say that the project has no tests or test dependencies. I left them for the documentation update in step 7.3.
