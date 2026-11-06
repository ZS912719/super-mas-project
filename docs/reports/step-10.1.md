# Step 10.1: Discovering Topics Without Anyone Declaring Them

## Outcome

`TopicDiscovery` groups a project's threads into subject topics and names each with keywords, with no input from the user. It is pure Java, needs no model download, and gives the same result for the same mail in any order. Four tests pass: three separable groups give exactly three topics with their own words, input order does not matter, a mailbox under six usable threads gives no topics, and a word repeated in one thread only cannot name a topic.

## How it works

1. **Words per thread.** Subject and new text of the thread's messages, lower case, words of 3-25 letters, without the stop list (`StopWords`: English and French function words, greetings, weekday and month names, mail phrasing).
2. **Vocabulary.** Words in at least 2 threads and at most 30% of threads.
3. **Vectors.** TF-IDF with sublinear term frequency, `(1 + ln tf) · ln(N / df)`, length-normalized.
4. **Clustering.** Spherical k-means (cosine), k-means++ seeding, `k = round(√threads)` capped at 50, at most 25 iterations, **5 restarts** with seeds `seed … seed+4`, keeping the run with the highest total similarity.
5. **Clean-up.** Centres with cosine ≥ 0.5 are merged (union-find). A thread with similarity < 0.1 to its centre stays without a topic. Topics with fewer than 3 threads are dropped.
6. **Naming.** Class-based TF-IDF as in BERTopic: `(word count in topic / words in topic) · ln(1 + A / word count in all topics)`, A the mean words per topic. A keyword must occur in at least 2 threads and 20% of the topic's threads. Top 8, ties by word.

All settings are in `TopicDiscovery.Settings.defaults()` and were fixed before any benchmark ran.

## Choices

| Choice | Why | Rejected alternative |
| --- | --- | --- |
| Java k-means instead of BERTopic | BERTopic is Python-only and needs an embedding model; the plan named that as the reason T3 was out of scope. This runs inside the existing build | A Python process, which adds a runtime the team must install |
| Threads, not messages, as the unit | A thread is already one conversation; replies would otherwise form clusters of their own thread | Clustering messages |
| More clusters than expected, then merging | k-means with a small k got stuck in a local optimum during testing (see below). Over-splitting and merging similar centres is more robust | A clever choice of k, such as silhouette search, which is slower and still local |
| Words, not stems | Keywords are matched as whole words by the existing `assess` and tagging code (step 4.2), so they must be real words | Stems, which would not match |
| Keywords must span several threads | One sender writing one thread cannot name a topic. This is the plan's steering risk for discovered topics | No rule, where repeated words in one email would dominate |

## Problems encountered

- **The first version found 2 topics in 3 clean groups.** With `k = √(n/2) = 3`, k-means++ put two seeds in one group and merged two other groups. Restarts and a larger `k` followed by merging fixed it. This is a property of k-means, so real mail can still give unlucky splits; the restarts reduce, not remove, the risk.
- **`Set.of` rejects duplicates.** The hand-written stop list had repeated words, which would have failed at class load. It now uses `Set.copyOf(List.of(...))`.

## Critical points

- **The steering defence is partial.** A sender who writes three threads with the same words can still create a topic, because the index stores no sender for a stored message. A topic gives nobody new rights (step 10.3); the effect is limited to which permitted mail is retrieved.
- **English and French words of one subject fall into different clusters** unless they share words such as course codes. The plan's language risk still applies.
- **No measured quality yet.** The tests use synthetic, cleanly separable groups. Step 10.4 measures retrieval with these topics on Enron.
- **Cost grows with threads × k × iterations × restarts.** With dense centres, about 7,000 Enron threads × 50 topics should take seconds; step 10.4 measures it.
