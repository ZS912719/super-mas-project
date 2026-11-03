# Fix: Domain Search Returned Unsent Drafts

## What was wrong

`ProjectIndex.search` filtered by project, source, reader and topic, but not by message kind. Until step 8.3 nothing indexed drafts, so this never showed. Since the recorder stores each draft in the project's mail source, a domain search (C1, subject topics) could return an unsent draft as evidence "from thread X". That breaks the architecture rule that unsent drafts never appear as sent facts.

Found while writing step 10.2, which reads every document of a project. It was never released, but steps 8.3 to 8.5 had the defect.

## Fix

`search` now excludes `GENERATED_DRAFT` and `USER_EDITED_DRAFT` inside the Lucene query (a `MUST_NOT` clause), so no caller can forget it. Drafts stay retrievable through `ConversationRepository.find` with an explicit kind, which is how history of what was proposed is meant to be read.

New test `IndexConversationRepositoryTest.domainSearchNeverReturnsAnUnsentDraft`: received, generated draft, user-edited draft and sent messages with the same words; search returns only received and sent. 218 tests pass.

Also added `ProjectIndex.all()`, which step 10.2 needs.

## Lesson

A new writer into a shared index must be checked against every reader of that index. The isolation tests checked scope and rights, not kinds, for domain search.
