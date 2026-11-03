package org.supermas.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Finds subject topics in a project's mail with no input from anyone: threads are clustered by their words,
 * and each cluster is named by the words that set it apart. Pure Java, deterministic for a given input and seed.
 *
 * <p>Method: TF-IDF vectors per thread, spherical k-means with k-means++ seeding, then clusters that are too
 * close are merged, too small ones are dropped, and threads far from every centre stay without a topic. Keywords
 * are ranked by class-based TF-IDF, the labelling idea of BERTopic (Grootendorst, 2022).
 *
 * <p>Keywords come from untrusted mail. They are only ever matched as whole words against text and used to
 * pick index tags. They never enter a prompt, a configuration or an identifier. Two rules limit how far one
 * sender can shape a topic: a topic needs several threads, and a keyword must occur in several of its threads.
 */
public final class TopicDiscovery {
    private static final Pattern WORD = Pattern.compile("\\p{L}{3,25}");
    private static final int RESTARTS = 5;

    /**
     * @param minThreads a topic needs at least this many threads
     * @param maxTopics an upper bound, whatever the mailbox size
     * @param keywordsPerTopic how many keywords name a topic
     * @param minKeywordShare a keyword must occur in at least this share of the topic's threads, and in two
     * @param maxDocumentShare words in more threads than this share are too common to separate topics
     * @param minSimilarity a thread less similar than this to its centre stays without a topic
     * @param mergeSimilarity centres at least this similar are one topic
     */
    public record Settings(int minThreads, int maxTopics, int keywordsPerTopic, double minKeywordShare,
                           double maxDocumentShare, double minSimilarity, double mergeSimilarity, int iterations,
                           long seed) {
        /** Fixed before any benchmark ran. */
        public static Settings defaults() {
            return new Settings(3, 50, 8, 0.2, 0.3, 0.1, 0.5, 25, 20261103L);
        }
    }

    /** One thread's words: its subject and its messages' new text. */
    public record ThreadText(String threadId, String text) { }

    /** A topic before it has a stable identifier. */
    public record Cluster(List<String> keywords, Set<String> threadIds) { }

    private final Settings settings;

    public TopicDiscovery(Settings settings) {
        this.settings = settings;
    }

    public List<Cluster> discover(List<ThreadText> threads) {
        List<ThreadText> sorted = new ArrayList<>(threads);
        sorted.sort(Comparator.comparing(ThreadText::threadId));
        List<Map<String, Integer>> counts = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (ThreadText thread : sorted) {
            Map<String, Integer> words = words(thread.text());
            counts.add(words);
            words.keySet().forEach(word -> documentFrequency.merge(word, 1, Integer::sum));
        }
        int n = sorted.size();
        if (n < 2 * settings.minThreads()) {
            return List.of();
        }
        Map<String, Integer> vocabulary = new TreeMap<>();
        documentFrequency.forEach((word, df) -> {
            if (df >= 2 && df <= settings.maxDocumentShare() * n) {
                vocabulary.put(word, 0);
            }
        });
        int position = 0;
        for (Map.Entry<String, Integer> entry : vocabulary.entrySet()) {
            entry.setValue(position++);
        }
        String[] terms = vocabulary.keySet().toArray(String[]::new);
        List<Integer> kept = new ArrayList<>();
        List<double[]> weights = new ArrayList<>();
        List<int[]> indices = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            Map<Integer, Double> vector = new TreeMap<>();
            for (Map.Entry<String, Integer> word : counts.get(t).entrySet()) {
                Integer index = vocabulary.get(word.getKey());
                if (index != null) {
                    double idf = Math.log((double) n / documentFrequency.get(word.getKey()));
                    vector.put(index, (1 + Math.log(word.getValue())) * idf);
                }
            }
            double norm = Math.sqrt(vector.values().stream().mapToDouble(v -> v * v).sum());
            if (norm > 0) {
                kept.add(t);
                indices.add(vector.keySet().stream().mapToInt(Integer::intValue).toArray());
                weights.add(vector.values().stream().mapToDouble(v -> v / norm).toArray());
            }
        }
        int m = kept.size();
        if (m < 2 * settings.minThreads()) {
            return List.of();
        }
        // Deliberately more clusters than expected topics: near-identical centres are merged afterwards.
        int k = Math.max(2, Math.min(settings.maxTopics(), (int) Math.round(Math.sqrt(m))));
        double[][] centres = null;
        int[] assignment = new int[m];
        double[] similarity = new double[m];
        double best = Double.NEGATIVE_INFINITY;
        for (int restart = 0; restart < RESTARTS; restart++) {
            double[][] candidate = cluster(indices, weights, terms.length, k, settings.seed() + restart);
            int[] candidateAssignment = new int[m];
            double[] candidateSimilarity = new double[m];
            assign(indices, weights, candidate, candidateAssignment, candidateSimilarity);
            double objective = Arrays.stream(candidateSimilarity).sum();
            if (objective > best) {
                best = objective;
                centres = candidate;
                assignment = candidateAssignment;
                similarity = candidateSimilarity;
            }
        }
        int[] group = merge(centres);
        Map<Integer, List<Integer>> members = new TreeMap<>();
        for (int i = 0; i < m; i++) {
            if (similarity[i] >= settings.minSimilarity()) {
                members.computeIfAbsent(group[assignment[i]], g -> new ArrayList<>()).add(i);
            }
        }
        members.values().removeIf(list -> list.size() < settings.minThreads());
        return name(members, indices, kept, counts, sorted, vocabulary.keySet());
    }

    /** One k-means run from one seed. The best of several runs is kept, since k-means finds local optima. */
    private double[][] cluster(List<int[]> indices, List<double[]> weights, int dimensions, int k, long seed) {
        int m = indices.size();
        double[][] centres = seed(indices, weights, dimensions, k, seed);
        int[] assignment = new int[m];
        double[] similarity = new double[m];
        for (int iteration = 0; iteration < settings.iterations(); iteration++) {
            boolean changed = assign(indices, weights, centres, assignment, similarity) || iteration == 0;
            centres = recompute(indices, weights, assignment, centres, dimensions);
            if (!changed) {
                break;
            }
        }
        return centres;
    }

    /** Lower-case words of three or more letters that are not common function or mail words. */
    static Map<String, Integer> words(String text) {
        Map<String, Integer> words = new HashMap<>();
        for (String token : TopicText.normalize(text).trim().split(" ")) {
            if (WORD.matcher(token).matches() && !StopWords.ALL.contains(token)) {
                words.merge(token, 1, Integer::sum);
            }
        }
        return words;
    }

    /** k-means++: each new centre is drawn with probability growing with its distance to the nearest centre. */
    private static double[][] seed(List<int[]> indices, List<double[]> weights, int dimensions, int k, long seed) {
        Random random = new Random(seed);
        int m = indices.size();
        double[][] centres = new double[k][];
        int first = random.nextInt(m);
        centres[0] = dense(indices.get(first), weights.get(first), dimensions);
        double[] distance = new double[m];
        Arrays.fill(distance, Double.MAX_VALUE);
        for (int c = 1; c < k; c++) {
            double total = 0;
            for (int i = 0; i < m; i++) {
                double d = 1 - dot(indices.get(i), weights.get(i), centres[c - 1]);
                distance[i] = Math.min(distance[i], d * d);
                total += distance[i];
            }
            int chosen = m - 1;
            double target = random.nextDouble() * total;
            for (int i = 0; i < m; i++) {
                target -= distance[i];
                if (target <= 0) {
                    chosen = i;
                    break;
                }
            }
            centres[c] = dense(indices.get(chosen), weights.get(chosen), dimensions);
        }
        return centres;
    }

    private static boolean assign(List<int[]> indices, List<double[]> weights, double[][] centres, int[] assignment,
                                  double[] similarity) {
        boolean changed = false;
        for (int i = 0; i < indices.size(); i++) {
            int best = 0;
            double bestSimilarity = -1;
            for (int c = 0; c < centres.length; c++) {
                double s = dot(indices.get(i), weights.get(i), centres[c]);
                if (s > bestSimilarity) {
                    bestSimilarity = s;
                    best = c;
                }
            }
            changed |= assignment[i] != best;
            assignment[i] = best;
            similarity[i] = bestSimilarity;
        }
        return changed;
    }

    /** The normalized mean of each cluster. An empty cluster keeps its old centre. */
    private static double[][] recompute(List<int[]> indices, List<double[]> weights, int[] assignment,
                                        double[][] previous, int dimensions) {
        int k = previous.length;
        double[][] centres = new double[k][dimensions];
        boolean[] used = new boolean[k];
        for (int i = 0; i < indices.size(); i++) {
            int[] index = indices.get(i);
            double[] weight = weights.get(i);
            used[assignment[i]] = true;
            for (int j = 0; j < index.length; j++) {
                centres[assignment[i]][index[j]] += weight[j];
            }
        }
        for (int c = 0; c < k; c++) {
            double norm = Math.sqrt(Arrays.stream(centres[c]).map(v -> v * v).sum());
            if (used[c] && norm > 0) {
                for (int d = 0; d < dimensions; d++) {
                    centres[c][d] /= norm;
                }
            } else {
                centres[c] = previous[c];
            }
        }
        return centres;
    }

    /** Joins centres whose similarity reaches the merge threshold, including chains, by union-find. */
    private int[] merge(double[][] centres) {
        int[] parent = new int[centres.length];
        for (int c = 0; c < parent.length; c++) {
            parent[c] = c;
        }
        for (int a = 0; a < centres.length; a++) {
            for (int b = a + 1; b < centres.length; b++) {
                double s = 0;
                for (int d = 0; d < centres[a].length; d++) {
                    s += centres[a][d] * centres[b][d];
                }
                if (s >= settings.mergeSimilarity()) {
                    parent[root(parent, b)] = root(parent, a);
                }
            }
        }
        for (int c = 0; c < parent.length; c++) {
            parent[c] = root(parent, c);
        }
        return parent;
    }

    private static int root(int[] parent, int c) {
        while (parent[c] != c) {
            c = parent[c];
        }
        return c;
    }

    /**
     * Class-based TF-IDF: a word's share of the words in a topic, times log(1 + A / f), where A is the mean
     * word count per topic and f the word's count over all topics. Only words in enough of the topic's threads
     * may name it.
     */
    private List<Cluster> name(Map<Integer, List<Integer>> members, List<int[]> indices, List<Integer> kept,
                               List<Map<String, Integer>> counts, List<ThreadText> threads, Set<String> vocabulary) {
        Map<Integer, Map<String, Integer>> wordCounts = new LinkedHashMap<>();
        Map<Integer, Map<String, Integer>> threadCounts = new LinkedHashMap<>();
        Map<String, Integer> overall = new HashMap<>();
        long totalWords = 0;
        for (Map.Entry<Integer, List<Integer>> topic : members.entrySet()) {
            Map<String, Integer> words = new HashMap<>();
            Map<String, Integer> inThreads = new HashMap<>();
            for (int i : topic.getValue()) {
                counts.get(kept.get(i)).forEach((word, count) -> {
                    if (vocabulary.contains(word)) {
                        words.merge(word, count, Integer::sum);
                        inThreads.merge(word, 1, Integer::sum);
                    }
                });
            }
            words.forEach((word, count) -> overall.merge(word, count, Integer::sum));
            totalWords += words.values().stream().mapToLong(Integer::longValue).sum();
            wordCounts.put(topic.getKey(), words);
            threadCounts.put(topic.getKey(), inThreads);
        }
        double average = members.isEmpty() ? 0 : (double) totalWords / members.size();
        List<Cluster> clusters = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> topic : members.entrySet()) {
            Map<String, Integer> words = wordCounts.get(topic.getKey());
            Map<String, Integer> inThreads = threadCounts.get(topic.getKey());
            int size = topic.getValue().size();
            double wordsInTopic = words.values().stream().mapToDouble(Integer::doubleValue).sum();
            int needed = Math.max(2, (int) Math.ceil(settings.minKeywordShare() * size));
            List<String> keywords = words.keySet().stream()
                    .filter(word -> inThreads.get(word) >= needed)
                    .sorted(Comparator.comparingDouble((String word) -> -(words.get(word) / wordsInTopic)
                            * Math.log(1 + average / overall.get(word))).thenComparing(word -> word))
                    .limit(settings.keywordsPerTopic())
                    .toList();
            if (keywords.isEmpty()) {
                continue;
            }
            Set<String> ids = new TreeSet<>();
            topic.getValue().forEach(i -> ids.add(threads.get(kept.get(i)).threadId()));
            clusters.add(new Cluster(keywords, ids));
        }
        return clusters;
    }

    private static double[] dense(int[] index, double[] weight, int dimensions) {
        double[] vector = new double[dimensions];
        for (int j = 0; j < index.length; j++) {
            vector[index[j]] = weight[j];
        }
        return vector;
    }

    private static double dot(int[] index, double[] weight, double[] centre) {
        double sum = 0;
        for (int j = 0; j < index.length; j++) {
            sum += weight[j] * centre[index[j]];
        }
        return sum;
    }
}
