# Paired comparison: 3B before fixes (A) vs 3B after fixes (C, 3 runs)

3B before fixes (A): 1 run(s): A-3b-checks2. 3B after fixes (C, 3 runs): 3 run(s): C-3b, C-repeat-3b-46bed76, C-repeat2-3b-46bed76. 22 reply-expected emails present in both; each email's value is its mean over all runs and repeats of a configuration. Interval: 95 % bootstrap over emails (10,000 resamples, seed 20261111) of the summed difference. An interval that contains 0 is no evidence of a difference.

| Measure (out of) | 3B before fixes (A) | 3B after fixes (C, 3 runs) | Difference 3B after fixes (C, 3 runs) − 3B before fixes (A) | 95 % interval | Emails better / worse / equal |
| --- | --- | --- | --- | --- | --- |
| facts (21) | 4.0 | 5.2 | +1.2 | -3.2 to +5.8 | 4 / 4 / 14 |
| asks (6) | 0.5 | 2.7 | +2.2 | +0.0 to +5.2 | 3 / 0 / 19 |
| drafted (22) | 18.0 | 22.0 | +4.0 | +1.0 to +7.5 | 5 / 0 / 17 |
| clean (22) | 3.0 | 17.3 | +14.3 | +10.2 to +18.0 | 17 / 1 / 4 |
| greeting (22) | 15.0 | 18.5 | +3.5 | -0.7 to +7.7 | 7 / 2 / 13 |
