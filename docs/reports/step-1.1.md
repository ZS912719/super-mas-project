# Step 1.1: MIME and header parsing

## What was built

`org.supermas.metadata.MimeEmailParser` turns raw message bytes into the shared `Email` record, using Apache mime4j. `EmailParseException` wraps a hard parse failure. `pom.xml` adds `apache-mime4j-dom` 0.8.11 as a compile dependency.

The parser fills: `id` (Message-ID), `sender`, `recipients` (To then Cc), `subject` (RFC 2047 decoded), `body` (decoded plain text), `receivedAt` (Date header) and a small `metadata` map (In-Reply-To, References, Content-Type).

## Why these choices

- **A different parser from the importer.** The importer derives gold facts with Jakarta Mail; this agent parses with mime4j. Keeping them separate means the gold labels are not produced by the code under test.
- **Permissive parsing.** Real and Enron mail have malformed headers, so `MimeConfig.PERMISSIVE` keeps them usable.
- **Plain text preferred.** For `multipart/alternative`, the parser takes `text/plain`, and falls back to stripped HTML only when there is no plain part.
- **Two fields are provisional.** `projectId` is passed in, because a message does not name its project. `threadId` is the first referenced id, else the message's own id; step 1.3 computes the real thread root from all messages and may overwrite it. This is documented in the class.
- **Nothing is dropped.** A missing Message-ID becomes a synthetic id from the content hash.
- **The body keeps quotes and signatures.** Removing them is step 1.4, so `body` is the full decoded text for now.

## Results

Environment: Maven 3.9.12, OpenJDK 17.0.20.1. All 29 tests pass.

`MimeEmailParserTest` parses all 24 synthetic emails and checks each against the independently verified gold:

- **24 / 24** correct for `sender`, `recipients` and `date`.
- Every `subject` is non-empty and fully decoded (no `=?...?=` left).
- Every `body` contains the expected new text, after newline normalization.

Targeted tests also confirm: an RFC 2047 subject and a quoted-printable accented body decode correctly, `multipart/alternative` yields the plain part with no HTML tags, the provisional thread id comes from the first `References` id and otherwise falls back to the own id, and a missing Message-ID is synthesized.

## Limitations

- **Not run on real Enron mail yet.** The synthetic set is clean; real headers will be messier.
- **HTML fallback is crude.** It strips tags with a regex, which is enough for a last resort but not for HTML-only mail with real structure. No synthetic case exercises it yet.
- **`threadId` and `currentText` are not final here.** Accuracy for those belongs to steps 1.3 and 1.4, so there is no benchmark predictor yet; this step is verified by unit tests.
- **Documentation drift:** `README.md` and `docs/architecture.md` still say the project has no dependencies. This now includes mime4j (compile) and Jackson, JUnit, Jakarta Mail (test). Left for step 7.3.
