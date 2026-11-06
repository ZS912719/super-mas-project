# Step 10.2: Regrouping Topics Automatically

## Outcome

Topics now appear and regroup with no action from the user. Recording mail is enough: after every `refreshEvery` new received or sent messages in a project, the topics are found again, identifiers are kept where topics persist, and the index tags are updated. New mail joins the current topics as it is stored. Nine tests pass (7 regrouping and catalogue, 2 automatic trigger).

## How it works

| Part | Behaviour |
| --- | --- |
| `TopicCatalog` | Current topics per project: `DiscoveredTopic(id, tag, keywords, threadIds)`. Identifier `t<n>` from a counter that never reuses numbers; tag `auto-topic-t<n>`. In memory or one JSON file per project (name hashed, atomic replace) |
| Stable identifiers | A new cluster takes an old topic's identifier when their thread sets have Jaccard ≥ 0.3, pairing greedily from the highest overlap. Other clusters get new numbers; vanished topics disappear |
| `TopicRegrouper.refresh(project)` | Reads every document of the project; clusters threads of `RECEIVED` and `SENT` messages (step 10.1); updates the catalogue; recomputes each document's `auto-topic-*` tags, keeping all other tags; writes only documents whose tags changed |
| Tag rule | A message in a clustered thread gets its thread's topic. A knowledge document, or a message in an unclustered thread, gets each topic with at least 2 of its keywords. Drafts get none |
| `MessageTagger.discovered(catalog)` | The same rule at storage time, so mail arriving between regroupings already joins topics. Taggers now receive the `MessageKind` |
| `AutoTopicMaintainer` | A `ConversationRecorder.Listener`. Counts new received and sent messages per project and regroups at the threshold. `refreshNow(project)` for start-up |

Wiring, with nothing else needed from the user:

```java
TopicCatalog catalog = TopicCatalog.onDisk(folder.resolve("topics"));
ConversationRecorder recorder = new ConversationRecorder(repository,
        MessageTagger.all(List.of(MessageTagger.discovered(catalog), MessageTagger.subjects(declaredTagger))));
recorder.addListener(new AutoTopicMaintainer(
        new TopicRegrouper(indexes, catalog, new TopicDiscovery(TopicDiscovery.Settings.defaults())), 25));
```

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Rebuild all topics, then match identifiers | Simple, deterministic, and a regrouping can split or merge topics freely | Updating clusters incrementally, which drifts and is harder to test |
| Jaccard of threads to keep identifiers | Threads are what a topic is; keywords can shift while the topic stays | Matching by keywords, which change when a few words gain weight |
| Two keywords to join a topic | One shared word such as "budget" is too weak a reason to join | One keyword, as declared subject tags use. Declared keywords are chosen by people; discovered ones are not |
| Drafts excluded from clustering and tagging | A draft is not something anyone said, and the system's own words must not shape the topics | Clustering all kinds |
| Synchronous regrouping | No thread management in our code; the coordinator's concurrency is the team's | A background executor |

## Problems encountered

- **The tagger could not tell drafts from sent mail.** Both arrive without a received email. I changed `MessageTagger` to take the `MessageKind`, which also makes the rule explicit.
- **A hung shell step** (my stray `cat` waiting for input) cost about ten minutes while running the suite. No code effect.

## Critical points

- **Regrouping reads the whole project.** It costs one full pass over the index plus clustering, every `refreshEvery` messages. Step 10.4 measures it on about 10,000 messages.
- **A retagged document is rewritten.** Lucene updates are delete plus add, so a regrouping rewrites every document whose tags changed. For large mailboxes this is the main cost.
- **The threshold is a count, not a schedule.** A quiet project is never regrouped after its last threshold. `refreshNow` covers start-up.
- **Identifiers can still change** when a topic's threads change by more than the overlap rule allows. Anything that stores a topic identifier, such as a benchmark, must treat it as a name for the current grouping only.
