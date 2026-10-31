# Metadata Field Schema

This schema defines what the `MetadataAgent` extracts and how gold data is labeled. It belongs to the benchmark and does not change any shared interface.

## Fields

| Field | Kind | Values | Empty allowed |
| --- | --- | --- | --- |
| `sender` | Fact | Address from the `From` header, lowercase | No |
| `recipients` | Fact | Addresses from `To` and `Cc` | No |
| `date` | Fact | `Date` header as a UTC instant, such as `2026-11-03T08:12:00Z` | No |
| `threadId` | Fact | `Message-ID` of the thread's first message, with angle brackets | No |
| `currentText` | Fact | New text written by the sender, as defined below | No |
| `entities` / `PERSON` | Entity | Name without title | Yes |
| `entities` / `COURSE` | Entity | Course name or code | Yes |
| `entities` / `DATE` | Entity | A date mentioned that is not a deadline | Yes |
| `entities` / `DEADLINE` | Entity | A date by which something is due | Yes |
| `entities` / `ID` | Entity | Student, ticket, invoice or similar number | Yes |
| `speechActs` | Label, many | `request`, `propose`, `commit`, `deliver` | Yes |
| `roleHint` | Label, one | `student`, `teacher`, `administration`, `researcher` | Yes (`null`) |
| `language` | Label, one | `en`, `fr` | No |

**Facts** come from the email's structure and should be correct without a model. **Entities** and **labels** are model output and remain hints.

`sender` is the claimed address only and is not proof of identity. `roleHint` is what the content suggests about the sender, and it must never grant permissions.

## Annotation rules

### `currentText`
- Keep the greeting and the new message.
- Remove quoted replies, forwarded content, the sign-off and the signature. "Best regards," and "Cordialement," are part of the sign-off.
- Scoring ignores differences in whitespace and letter case.

### Entities
- Label only entities found in `currentText`, never in quoted text or signatures.
- Copy the text as written, without surrounding words: `Friday 14 November`, not `by Friday 14 November`.
- Leave out titles: `Martin`, not `Prof. Martin`.
- List an entity once per email, even when it appears several times.
- A date is either `DATE` or `DEADLINE`, never both.
- Course codes are `COURSE`, not `ID`.

### Speech acts
Label the whole email. Several acts may apply, and purely informational mail may have none. These follow the email act categories of Cohen, Carvalho & Mitchell (2004).

| Act | Meaning | Example |
| --- | --- | --- |
| `request` | Asks the recipient to do something or provide something | "Could you send me the form?" |
| `propose` | Suggests a joint action or time, open for acceptance | "Shall we meet on Tuesday?" |
| `commit` | The sender undertakes to do something | "I will review it tomorrow." |
| `deliver` | Provides the requested information or a file | "The grades are attached." |

### Role hint
Use only evidence in the current email, such as "my thesis supervisor" or "I teach this course". Use `null` when there is no clear evidence. An email address alone is not enough.

### Language
Use the main language of `currentText`.

## Scoring

- Text is compared after Unicode NFC normalization, lowercasing and whitespace collapsing.
- Each field is compared as a multiset, so a duplicated prediction counts as a false positive.
- Entities must match in both type and text. There is no partial credit.
- **Null accuracy:** among emails whose gold value for a field is empty, the share where the prediction is also empty.
- Macro F1 averages only over fields that have gold values in the evaluated data.
