package org.supermas.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.supermas.context.TopicDiscovery.Cluster;

/**
 * The current discovered topics of each project. When topics are found again, a new topic that mostly holds the
 * same threads as an old one keeps the old identifier and tag, so regrouping does not rename everything.
 * Identifiers and tags are generated here from a counter and never contain mail text.
 */
public final class TopicCatalog {
    /** Every tag of a discovered topic starts with this, which is how regrouping finds and replaces them. */
    public static final String TAG_PREFIX = "auto-topic-";
    /** Two topics with at least this share of threads in common are the same topic. */
    static final double SAME_TOPIC = 0.3;
    /** A message outside every clustered thread joins a topic only if it contains this many of its keywords. */
    static final int KEYWORDS_TO_JOIN = 2;

    /**
     * One discovered topic.
     *
     * @param keywords untrusted words from mail, used only for whole-word matching
     */
    public record DiscoveredTopic(String id, String tag, List<String> keywords, Set<String> threadIds) {
        public DiscoveredTopic {
            keywords = List.copyOf(keywords);
            threadIds = Set.copyOf(threadIds);
        }
    }

    /** What is stored for a project. */
    record State(int nextNumber, List<DiscoveredTopic> topics) { }

    private final Optional<Path> folder;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();

    private TopicCatalog(Optional<Path> folder) {
        this.folder = folder;
    }

    public static TopicCatalog inMemory() {
        return new TopicCatalog(Optional.empty());
    }

    /** Topics are kept in one JSON file per project in this folder, so identifiers survive a restart. */
    public static TopicCatalog onDisk(Path folder) {
        return new TopicCatalog(Optional.of(folder));
    }

    public List<DiscoveredTopic> topics(String projectId) {
        return state(projectId).topics();
    }

    /** Replaces the project's topics with these clusters, keeping identifiers where the threads match. */
    public synchronized List<DiscoveredTopic> update(String projectId, List<Cluster> clusters) {
        State old = state(projectId);
        int next = old.nextNumber();
        record Pair(int cluster, DiscoveredTopic topic, double overlap) { }
        List<Pair> pairs = new ArrayList<>();
        for (int c = 0; c < clusters.size(); c++) {
            for (DiscoveredTopic topic : old.topics()) {
                double overlap = jaccard(clusters.get(c).threadIds(), topic.threadIds());
                if (overlap >= SAME_TOPIC) {
                    pairs.add(new Pair(c, topic, overlap));
                }
            }
        }
        pairs.sort(Comparator.comparingDouble((Pair pair) -> -pair.overlap()).thenComparing(pair -> pair.topic().id())
                .thenComparingInt(Pair::cluster));
        Map<Integer, String> kept = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (Pair pair : pairs) {
            if (!kept.containsKey(pair.cluster()) && used.add(pair.topic().id())) {
                kept.put(pair.cluster(), pair.topic().id());
            }
        }
        List<DiscoveredTopic> topics = new ArrayList<>();
        for (int c = 0; c < clusters.size(); c++) {
            String id = kept.containsKey(c) ? kept.get(c) : "t" + next++;
            topics.add(new DiscoveredTopic(id, TAG_PREFIX + id, clusters.get(c).keywords(),
                    new TreeSet<>(clusters.get(c).threadIds())));
        }
        topics.sort(Comparator.comparing(DiscoveredTopic::id));
        State state = new State(next, List.copyOf(topics));
        states.put(projectId, state);
        save(projectId, state);
        return state.topics();
    }

    /**
     * The topic tags a message should carry: the topic of its thread if the thread was clustered, else each
     * topic of which it contains enough keywords.
     */
    public Set<String> tagsFor(String projectId, String threadId, String subject, String text) {
        Set<String> tags = new TreeSet<>();
        String normalized = TopicText.normalize(subject + " " + text);
        for (DiscoveredTopic topic : topics(projectId)) {
            if (threadId != null && topic.threadIds().contains(threadId)) {
                tags.add(topic.tag());
            } else if (matched(topic, normalized) >= KEYWORDS_TO_JOIN) {
                tags.add(topic.tag());
            }
        }
        return tags;
    }

    /** How many of the topic's keywords the normalized text contains. */
    static long matched(DiscoveredTopic topic, String normalizedText) {
        return topic.keywords().stream().filter(keyword -> TopicText.contains(normalizedText, keyword)).count();
    }

    private State state(String projectId) {
        return states.computeIfAbsent(projectId, this::load);
    }

    private State load(String projectId) {
        if (folder.isPresent()) {
            Path file = file(projectId);
            if (Files.exists(file)) {
                try {
                    return json.readValue(file.toFile(), new TypeReference<State>() { });
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not read the topics of project " + projectId, e);
                }
            }
        }
        return new State(1, List.of());
    }

    private void save(String projectId, State state) {
        if (folder.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(folder.get());
            Path temporary = Files.createTempFile(folder.get(), "topics", ".tmp");
            json.writeValue(temporary.toFile(), state);
            Files.move(temporary, file(projectId), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the topics of project " + projectId, e);
        }
    }

    /** Hashed like index folders, so a project identifier cannot choose the path. */
    private Path file(String projectId) {
        return folder.orElseThrow().resolve(ProjectIndexes.folderName(projectId) + ".json");
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        long shared = a.stream().filter(b::contains).count();
        long union = a.size() + b.size() - shared;
        return union == 0 ? 0 : (double) shared / union;
    }
}
