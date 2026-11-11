# Threading (benchmark plan C02)

Run 2026-11-11T11:43Z on `benchmark/data/synthetic-dev` (24 emails, gold `threadId`).

| Variant | Thread right | Wrong |
| --- | --- | --- |
| Parser on receipt (reply headers only; the prototype) | 22 / 24 | en-03, fr-03 |
| EmailThreader, references only | 22 / 24 | en-03, fr-03 |
| EmailThreader with subject fallback | 24 / 24 |  |
