# Email Analysis: the MetadataAgent

**Package:** `org.supermas.metadata`. **Reports:** steps 0.4-0.6, 1.1-1.10, `labeling-setup.md`, `data-import-enron.md`.

## Architecture

`MetadataAgent` implements the shared `Preprocessor` contract: the coordinator calls it once when an email arrives, before the board exists, and its `Analysis` sits on level 1 of the board. Stages, each replaceable and each optional where a model is involved:

| Stage | Class | How |
| --- | --- | --- |
| MIME and headers | `MimeEmailParser` | Apache James Mime4j (deliberately a different parser from the gold-label tool) |
| Threading | `EmailThreader` | `References`/`In-Reply-To`, subject fallback (Enron rarely has references) |
| New text only | `QuoteSignatureStripper` | Quote markers, forwards (incl. Lotus Notes), signature blocks → `currentText` |
| Language | `LanguageDetector` | English/French |
| Entities | `RuleEntityExtractor` + optional `GlinerEntityExtractor` (ONNX, in-process) | Hybrid per type: model for PERSON, COURSE, ID; rules for DATE, DEADLINE, ID |
| Speech acts | `RuleSpeechActClassifier` + optional `OnnxSpeechActClassifier` | request, propose, commit, deliver |
| Role hint | `RuleRoleHintClassifier` | From what the sender says about themselves ("I am a student"), not from the address |

Everything travels in `Analysis.metadata()` under fixed keys, so the shared record did not change. A failing stage is recorded in `failedStages` and the email goes on.

## Choices and alternatives

| Choice | Alternative | Why |
| --- | --- | --- |
| **No LLM in analysis by default** | LLM extraction or an LLM checker | Small local models are slow and can be steered by email text; rules and small encoders are fast, deterministic and testable. The LLM checker (Phase 5) was set aside, kept as a switch idea |
| **Rules first, then GLiNER and a SetFit-style classifier** | Models only | Measured: GLiNER wins PERSON (F1 0.83 vs 0.15), rules win DATE; for speech acts rules reached macro F1 0.54 against 0.26 for the model (provisional labels) |
| **Role hint from text** | From the sender's address | The statement: classification must not rely only on the address; one person writes in several roles |
| **Analysis is a hint** | Facts that gate decisions | Email is untrusted; nothing here grants permissions |

## Results (provisional)

Synthetic set: sender, recipients, date, `currentText`, language F1 1.000; threading 24/24. Enron dev (100 emails, labels drafted by an LLM, not yet reviewed by a person): `currentText` 0.780; hybrid entities as above. Recommended configuration: GLiNER entities + rule speech acts; the prototype runs rules only, because model files are not in Git.

## Why it complies

- **Statement §3, §4.1:** routing may use sender role, subject, content, thread and metadata, not only the address. The analysis supplies exactly these, as hints.
- **Targeted architecture:** analysis is the input level of the blackboard; the knowledge sources read it, none is triggered by the address alone.

## Open points

Human review of the Enron labels (25 hand-labelled test emails) is still pending, so all accuracy figures are provisional. French is deferred.
