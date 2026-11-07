# Synthetic multi-request emails

Twelve English emails written for step 12.7 (2026-11-07) to test whether several specialists collaborate on one reply. Most hold two or three requests that belong to different roles; two have no category (Generic), two have two requests of one role.

Gold labels (`*.gold.json`, schema `multi-gold/v1`): the number of requests and the roles that should contribute, assigned by meaning, as a person would, **not** by what the keyword rules can detect. They were written by the AI assistant and frozen (committed) before any system ran on these emails; a human review of the labels is still pending. Synthetic data only, no real person.

Reply checks (`*.reply.json`, schema `reply-gold/v1`, also for `synthetic-dev`) were added in step 13 before any model run: see `docs/benchmark/reply-quality.md`.
