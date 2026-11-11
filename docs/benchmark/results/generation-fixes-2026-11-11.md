# Generation fixes: retry, copied headers, greeting (commit 2427f0e)

Three runs per model in the system configuration on the current code, against the three runs of B59. Paired by email. Run folders `runtime/reply-benchmark/F-base-*` (Git-ignored).

# Paired comparison: B59 3B vs fixed 3B

B59 3B: 3 run(s): M-base-3b-r1, M-base-3b-r2, M-base-3b-r3. fixed 3B: 3 run(s): F-base-3b-r1, F-base-3b-r2, F-base-3b-r3. 22 reply-expected emails present in both; each email's value is its mean over all runs and repeats of a configuration. Interval: 95 % bootstrap over emails (10,000 resamples, seed 20261111) of the summed difference. An interval that contains 0 is no evidence of a difference.

| Measure (out of) | B59 3B | fixed 3B | Difference fixed 3B − B59 3B | 95 % interval | Emails better / worse / equal |
| --- | --- | --- | --- | --- | --- |
| facts (21) | 4.5 | 5.2 | +0.7 | -0.2 to +1.7 | 3 / 1 / 18 |
| asks (6) | 2.2 | 2.8 | +0.7 | +0.0 to +1.7 | 2 / 0 / 20 |
| drafted (22) | 22.0 | 22.0 | +0.0 | +0.0 to +0.0 | 0 / 0 / 22 |
| clean (22) | 16.5 | 17.2 | +0.7 | -1.5 to +2.7 | 6 / 3 / 13 |
| greeting (22) | 17.5 | 18.3 | +0.8 | -1.3 to +2.7 | 7 / 2 / 13 |


# Paired comparison: B59 8B vs fixed 8B

B59 8B: 3 run(s): M-base-8b-r1, M-base-8b-r2, M-base-8b-r3. fixed 8B: 3 run(s): F-base-8b-r1, F-base-8b-r2, F-base-8b-r3. 22 reply-expected emails present in both; each email's value is its mean over all runs and repeats of a configuration. Interval: 95 % bootstrap over emails (10,000 resamples, seed 20261111) of the summed difference. An interval that contains 0 is no evidence of a difference.

| Measure (out of) | B59 8B | fixed 8B | Difference fixed 8B − B59 8B | 95 % interval | Emails better / worse / equal |
| --- | --- | --- | --- | --- | --- |
| facts (21) | 6.3 | 8.8 | +2.5 | +0.3 to +4.8 | 6 / 1 / 15 |
| asks (6) | 3.7 | 4.5 | +0.8 | +0.0 to +2.3 | 2 / 0 / 20 |
| drafted (22) | 19.5 | 21.7 | +2.2 | +0.5 to +4.2 | 6 / 0 / 16 |
| clean (22) | 11.7 | 15.5 | +3.8 | +1.7 to +6.0 | 12 / 2 / 8 |
| greeting (22) | 12.8 | 15.7 | +2.8 | +0.7 to +5.0 | 9 / 2 / 11 |


# Paired comparison: fixed 3B vs fixed 8B

fixed 3B: 3 run(s): F-base-3b-r1, F-base-3b-r2, F-base-3b-r3. fixed 8B: 3 run(s): F-base-8b-r1, F-base-8b-r2, F-base-8b-r3. 22 reply-expected emails present in both; each email's value is its mean over all runs and repeats of a configuration. Interval: 95 % bootstrap over emails (10,000 resamples, seed 20261111) of the summed difference. An interval that contains 0 is no evidence of a difference.

| Measure (out of) | fixed 3B | fixed 8B | Difference fixed 8B − fixed 3B | 95 % interval | Emails better / worse / equal |
| --- | --- | --- | --- | --- | --- |
| facts (21) | 5.2 | 8.8 | +3.7 | -0.3 to +8.8 | 5 / 1 / 16 |
| asks (6) | 2.8 | 4.5 | +1.7 | +0.2 to +3.5 | 4 / 0 / 18 |
| drafted (22) | 22.0 | 21.7 | -0.3 | -0.8 to +0.0 | 0 / 2 / 20 |
| clean (22) | 17.2 | 15.5 | -1.7 | -5.3 to +2.2 | 5 / 9 / 8 |
| greeting (22) | 18.3 | 15.7 | -2.7 | -6.0 to +0.5 | 3 / 10 / 9 |

