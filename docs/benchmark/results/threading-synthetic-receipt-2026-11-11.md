# Threading (benchmark plan C02)

Run 2026-11-11T19:29Z on `benchmark/data/synthetic-dev` (24 emails, gold `threadId`).

| Variant | Thread right | Wrong |
| --- | --- | --- |
| Parser alone (reply headers only; the prototype before f45bff4) | 22 / 24 | en-03, fr-03 |
| EmailThreader, references only | 22 / 24 | en-03, fr-03 |
| EmailThreader with subject fallback | 24 / 24 |  |
| On receipt with the restricted subject fallback (the prototype since f45bff4) | 24 / 24 |  |
