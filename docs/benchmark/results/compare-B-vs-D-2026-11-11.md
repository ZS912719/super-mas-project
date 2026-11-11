# Paired comparison: 8B before fixes (B) vs 8B after fixes (D, 3 runs)

8B before fixes (B): 1 run(s): B-8b-checks2. 8B after fixes (D, 3 runs): 3 run(s): D-8b, D-repeat-8b-46bed76, D-repeat2-8b-46bed76. 22 reply-expected emails present in both; each email's value is its mean over all runs and repeats of a configuration. Interval: 95 % bootstrap over emails (10,000 resamples, seed 20261111) of the summed difference. An interval that contains 0 is no evidence of a difference.

| Measure (out of) | 8B before fixes (B) | 8B after fixes (D, 3 runs) | Difference 8B after fixes (D, 3 runs) − 8B before fixes (B) | 95 % interval | Emails better / worse / equal |
| --- | --- | --- | --- | --- | --- |
| facts (21) | 9.0 | 6.5 | -2.5 | -6.3 to +0.5 | 3 / 4 / 15 |
| asks (6) | 3.5 | 3.7 | +0.2 | -1.2 to +1.8 | 1 / 2 / 19 |
| drafted (22) | 22.0 | 19.7 | -2.3 | -5.0 to +0.0 | 0 / 3 / 19 |
| clean (22) | 0.5 | 11.2 | +10.7 | +6.7 to +14.7 | 15 / 0 / 7 |
| greeting (22) | 16.5 | 11.8 | -4.7 | -8.7 to -1.0 | 3 / 8 / 11 |
