# Step 0.4: Field Schema

## What was built

`docs/benchmark/field-schema.md` defines each metadata field as a fact, entity or label, along with annotation and scoring rules.

## Why

Without fixed rules, gold labels and scores would change from email to email.

- **Facts vs model output:** this follows the project's provenance rule. Facts should be correct without a model.
- **Speech acts:** these use the email act categories of [Cohen, Carvalho & Mitchell (2004)](https://aclanthology.org/W04-3240/).
- **Exact-match entities:** gold data holds text, not character positions, so labels survive changes in decoding and whitespace. Partial matches get no credit, which is strict and may understate model quality.

## Additions beyond the plan

- **`currentText`:** French segmentation has no public data that I found, so step 1.3 needs its own gold values.
- **`language`:** the plan requires results per language.
- **Role values:** the plan only said "role hint". I chose `student`, `teacher`, `administration` and `researcher`, based on the specialist roles.

## Results

There is nothing to measure in this step. No shared interface changed, because these types live in the benchmark only.
