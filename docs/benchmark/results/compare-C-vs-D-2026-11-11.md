# Paired comparison: 3B (C, 3 runs) vs 8B (D, 3 runs)

3B (C, 3 runs): 3 run(s): C-3b, C-repeat-3b-46bed76, C-repeat2-3b-46bed76. 8B (D, 3 runs): 3 run(s): D-8b, D-repeat-8b-46bed76, D-repeat2-8b-46bed76. 22 reply-expected emails present in both; each email's value is its mean over all runs and repeats of a configuration. Interval: 95 % bootstrap over emails (10,000 resamples, seed 20261111) of the summed difference. An interval that contains 0 is no evidence of a difference.

| Measure (out of) | 3B (C, 3 runs) | 8B (D, 3 runs) | Difference 8B (D, 3 runs) − 3B (C, 3 runs) | 95 % interval | Emails better / worse / equal |
| --- | --- | --- | --- | --- | --- |
| facts (21) | 5.2 | 6.5 | +1.3 | -3.5 to +6.5 | 4 / 3 / 15 |
| asks (6) | 2.7 | 3.7 | +1.0 | -0.2 to +2.7 | 3 / 1 / 18 |
| drafted (22) | 22.0 | 19.7 | -2.3 | -5.0 to +0.0 | 0 / 3 / 19 |
| clean (22) | 17.3 | 11.2 | -6.2 | -10.7 to -1.7 | 3 / 10 / 9 |
| greeting (22) | 18.5 | 11.8 | -6.7 | -10.3 to -3.2 | 1 / 10 / 11 |
