# Context, Retrieval, Topics, Missing Information and Memory

**Packages:** `org.supermas.context`, `org.supermas.memory`. **Reports:** steps 3.1-3.5, 4.1-4.10, 8.1-8.5, 10.1-10.4, `fix-drafts-in-search.md`, 11.2-11.3.

## Architecture

| Part | Classes | What it does |
| --- | --- | --- |
| Per-project index | `ProjectIndex`, `ProjectIndexes` | One Lucene index per project (English and French analyzers); a document carries source, thread, kind, readers, topic tags |
| Retrieval | `LuceneContextRetriever`, `AgentContextAccess` | Thread history (exact project and thread, only asked kinds), domain search authorized **before** reading (source must list the agent, agent must list the source), per-document readers ∩ current source readers |
| Knowledge | `KnowledgeLoader` | `knowledge/sources.json` + one folder per source (`.md`, `.txt`); readers per source; no links followed, paths cannot escape |
| Conversation memory | `IndexConversationRepository`, `ConversationRecorder` | `RECEIVED`, `GENERATED_DRAFT`, `USER_EDITED_DRAFT`, `SENT` kept apart; `SENT` only for the exact approved version after a confirmed send; drafts never appear in search |
| Discovered topics | `TopicDiscovery`, `TopicCatalog`, `TopicRegrouper`, `AutoTopicMaintainer` | TF-IDF + spherical k-means over threads, class-based TF-IDF keywords (as BERTopic), stable identifiers across regroupings, refreshed every N new messages (Observer on the recorder) |
| Context agents | `TopicContextAgent` (discussion, subject, correspondent), `DiscoveredTopicsAgent` | Knowledge sources at the context level; fixed identities, rights from configuration |
| Missing information | `MissingInformationAgent`, `RuleMissingInformationCheck`, optional `LlmMissingInformationCheck` | What the email lacks (date, deadline, course, ID, person); suggests one bounded further retrieval with an `InformationNeed` |
| Selection for prompts | `TopicContextSelector` | Reciprocal-rank fusion and MMR within a character budget, whole items only |
| Promotion (Phase 11) | `prototype.EvidencePromotion` | Agents submit privately; the coordinator copies findings to agents allowed by trusted config and the document's readers |

## Choices and alternatives

| Choice | Alternative | Why |
| --- | --- | --- |
| **Lucene (BM25), local** | Embeddings and a vector store | No model download, explainable, fast on a laptop; embeddings are an extension (to-do) |
| **Both C1 (plain search) and C3 (topic agents) built and measured** | Pick one by taste | The plan required evidence: C1 + thread history is the best measured retrieval; discovered topics beat declared ones 3.5× but still lose to C1 on the folder proxy |
| **Topics discovered automatically** (owner's decision, 2026-11-03) | Topics declared and approved by a person | No configuration, 97% of Enron emails fit a topic |
| **One discovered-topics agent, not one per topic** | An agent per topic | Rights are checked by agent identifier; agents created by discovery would let mail decide who reads what |
| **Authorization before reading, readers per document** | Filter after retrieval | Being on the board does not make evidence eligible; nothing unauthorized is ever read |
| **Memory in the same index, kinds separate** | A separate database | One store per project; a draft is never mistaken for something said |
| **Private contributions + promotion** (teammate's board rule) | Agents choose readers | Only trusted configuration widens access |

## Results

Enron, 7 mailboxes, 10,206 messages: folder proxy Recall@10 C1 0.113, discovered topics 0.070, declared topics 0.020; thread proxy C1 + thread history 0.988. **0 isolation violations** in 2,742 cross-project and 914 forged-sender probes. Drafts-in-search defect found and fixed.

## Why it complies

- **Statement §5.2, §9, §20:** each agent has its own context (sources with readers), conversation history covers received, generated and sent messages separately, budgets decide how much reaches the model, retrieval-augmented prompts (RAG) and long-term memory are in place, contexts are isolated.
- **Targeted architecture:** context agents are independent knowledge sources at their own level, triggered by events (a further-retrieval need from the missing-information check); the board is the community memory.

## Open points

Embeddings, user preference memory, and recording `SENT` from the mailbox's confirmed sends are on the to-do list.
