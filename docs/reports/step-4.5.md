# Step 4.5: Merge Overlapping Topic Results

## Outcome

`TopicContextSelector` implements `ContextSelector`. It folds duplicates, fuses rankings with Reciprocal Rank Fusion (RRF), reduces near-copies with Maximal Marginal Relevance (MMR), and fits the budget. Three topics finding one email now count as one document, which the plan names as a risk.

## What was built

| Item | Role |
| --- | --- |
| `TopicContextSelector` | The merge, in the order given by the plan |
| `TopicContextSelectorTest` | 9 tests |

## Architectural choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| RRF with k = 60 | Uses ranks only, so scores from different searches need no normalization. 60 is the value from the original paper | Summing BM25 scores: not comparable, and thread results have no score |
| MMR with Jaccard over word sets | Needs no embedding model and no extra memory on this laptop | Embedding similarity: catches paraphrases but adds a model. Near-copies in email are usually literal |
| Relevance weight 0.7 | A common starting point, left untuned on purpose | Tuning it on the proxy |
| Rights checked for the requester here too | Being on the board does not make evidence eligible (P6). A test passes a view that shows everything | Trusting the view |
| Only `RETRIEVED_EVIDENCE` | Inferences and facts stay distinct, as the provenance rule requires | Merging everything |
| Skip an item that does not fit, and continue | A smaller later document may still fit | Stopping at the first overflow |

## Critical points

- **`omittedSourceIds` is coarse.** It lists sources with something left out, not how many documents. The shared record allows nothing finer.
- **RRF favours agreement between topics, and agreement may not mean independence.** Overlapping topics searching one index with similar queries agree easily, so a document can be boosted by correlated searches. Merging stops the double counting of evidence but not this effect.
- **The interface asks to prioritize confirmed sent content, and this does not.** `ContextSelector`'s comment says so. Drafts are excluded at retrieval, but `SENT` has no boost over `RECEIVED`. Provenance holds the kind as text only, and I saw no measured reason to add a weight.
- **Selection is quadratic in the number of documents.** Each retrieval returns at most 10 or 30 per agent, so this stays small. Step 4.10 measures the cost.
- **Language:** word overlap does not see an English and a French version as similar.
