# Conversation Memory After a Restart (benchmark plan C14)

Run 2026-11-11T11:42Z. The 24 `synthetic-dev` emails, first all under a second project with the same thread identifiers, then each under the real project in a freshly created wiring on the same data folder (a restart before every email). Template generation.

| Measure | Value |
| --- | --- |
| Emails that continue a thread | 6 |
| Earlier thread messages found after the restart (recall) | 4 / 8 |
| Thread messages from other threads of the same project | 0 |
| Thread messages from the other project (leaks) | 0 |

| Email | Earlier messages of its thread | Found | Missed | From other threads | From the other project |
| --- | --- | --- | --- | --- | --- |
| en-02 | 1 | 1 |  |  | 0 |
| en-03 | 2 | 0 | <en-a1@students.example.edu>, <en-a2@example.edu> |  | 0 |
| en-06 | 1 | 1 |  |  | 0 |
| fr-02 | 1 | 1 |  |  | 0 |
| fr-03 | 2 | 0 | <fr-a1@etu.example.fr>, <fr-a2@example.fr> |  | 0 |
| fr-06 | 1 | 1 |  |  | 0 |
