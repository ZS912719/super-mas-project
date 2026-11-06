# Step 0.6: Synthetic Development Emails

## What was built

`benchmark/data/synthetic-dev` holds 24 emails, 12 English and 12 French, each with gold labels. The French set mirrors the English one in structure.

The set covers:
- Student, teacher, administration and research mail.
- Replies with `In-Reply-To` and `References`, and one reply per language with neither header.
- Quoted replies in several client styles, forwarded mail and signatures.
- Encoded headers, quoted-printable bodies and `multipart/alternative`.
- Mail with no entities, no speech acts and no role.

All addresses use reserved example domains, and all people are invented. `.gitattributes` keeps the data byte-identical across platforms. The French text is test data, which is a necessary exception to the English-only project rule.

## Why

Work can start before the real dataset is chosen.

## Results

- Python's mail parser, separate from the Java code, confirmed senders, recipients, dates, thread references and decoded `currentText` for all 24 emails, with 0 problems. It also confirmed that entities appear in `currentText`.
- `SyntheticDevDataTest` confirms the balance between languages and that labels follow the schema.
- **Gate met:** the runner produced a baseline report at `docs/benchmark/results/baseline-empty.md`. Macro F1 is 0.000 on 24 emails with 0 failures. The dataset hash is `cbd11d761bafef1645ba5bc16360212930bee27341b46adbb821c45916ee34e3`.

The baseline's null accuracy of 1.000 is expected: predicting nothing is always correct when nothing is present. It shows why null accuracy must be read together with recall.

## Limitations

- **Do not report accuracy from this set.** I wrote both the emails and the labels, so it is easier and less varied than real mail. The checks prove labels are consistent, not that my judgment on speech acts and roles is right. There is no second annotator.
- **Too small for training or conclusions.** It is for development only, and is not the frozen test set.
- **Missing real-world mess:** no inline replies, bottom posting, mixed languages, attachments, typos or mailing list footers.
- **Mirrored French mail** is not independent of the English mail.
- **The generator was a one-off and is not committed.** The data files are now the source.

## Open

Real labeled mail must be chosen by day 3, because SetFit training in step 1.7 needs it.
