# Input Budget (benchmark plan P2)

Run 2026-11-11. The system answered each email through its local-model adapter (provider `local`) talking to `scripts/benchmark/fake_llm_server.py` on 127.0.0.1, which returns one fixed reply and records the size of every request (characters of system and user messages). The adapter itself refuses a request over the budget before sending it, so over-budget requests appear as budget failures of the run. Budget: 12,000 characters (`maxInputCharacters` of every role). Counts only.

| Set | Emails | Requests sent | Median size | Largest | Over budget | Failed for the budget |
| --- | --- | --- | --- | --- | --- | --- |
| Enron frozen test (bodies 40-6,000 characters) | 100 | 100 | 4,168 | 9,692 | 0 | 0 |
| `synthetic-dev` | 24 | 24 | 4,084 | 5,386 | 0 | 0 |
| `synthetic-multi` | 12 | 12 | 4,706 | 6,577 | 0 | 0 |
| The 20 longest Enron messages (226-400 KB) | 20 | 17 | — | 12,000 | 0 | 3 |

System prompt: 3,113-3,528 characters (common prompt, composer prompt and the contributing roles' rules). The three failures on the longest messages are refusals by design: "The system prompt and the plan exceed the input budget of composer" (`RolePromptBuilder`: the plan is never cut; the email text is).
