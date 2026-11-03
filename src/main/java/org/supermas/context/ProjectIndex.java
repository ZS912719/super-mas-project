package org.supermas.context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.supermas.model.Domain.MessageKind;
import org.supermas.model.Domain.Scope;

/**
 * One project's search index. Each document is searchable in the field for its language, with that
 * language's stemming, and a query searches both. Query terms are built directly from analyzed text,
 * with no query parser, so email text cannot inject query syntax.
 */
public final class ProjectIndex implements AutoCloseable {
    /** Lucene's limit is 1024 clauses. Terms are searched in two fields and two more clause groups are used. */
    private static final int MAX_QUERY_TERMS = 256;

    private static final String ID = "id";
    private static final String PROJECT = "project";
    private static final String THREAD = "thread";
    private static final String SOURCE = "source";
    private static final String KIND = "kind";
    private static final String PERMITTED = "permitted";
    private static final String TOPIC = "topic";
    private static final String LANGUAGE = "language";
    private static final String SUBJECT = "subject";
    private static final String TEXT = "text";
    private static final String RECORDED = "recorded";
    private static final String BODY_EN = "body_en";
    private static final String BODY_FR = "body_fr";

    /** One search hit, with the score Lucene gave it. */
    public record Hit(IndexedDocument document, double score) { }

    private final String projectId;
    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;
    private final SearcherManager searchers;

    public ProjectIndex(String projectId, Directory directory) {
        this.projectId = projectId;
        this.directory = directory;
        this.analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(),
                Map.of(BODY_EN, new EnglishAnalyzer(), BODY_FR, new FrenchAnalyzer()));
        try {
            this.writer = new IndexWriter(directory, new IndexWriterConfig(analyzer));
            this.searchers = new SearcherManager(writer, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open the index of project " + projectId, e);
        }
    }

    /** Adds the document, or replaces the one with the same identifier. Visible to the next search. */
    public void add(IndexedDocument document) {
        if (!projectId.equals(document.scope().projectId())) {
            throw new SecurityException("Document " + document.id() + " belongs to another project");
        }
        try {
            writer.updateDocument(new Term(ID, document.id()), toLucene(document));
            searchers.maybeRefreshBlocking();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Adds many documents, refreshing once at the end, for bulk loads. */
    public void addAll(Iterable<IndexedDocument> documents) {
        try {
            for (IndexedDocument document : documents) {
                if (!projectId.equals(document.scope().projectId())) {
                    throw new SecurityException("Document " + document.id() + " belongs to another project");
                }
                writer.updateDocument(new Term(ID, document.id()), toLucene(document));
            }
            searchers.maybeRefreshBlocking();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Durable on disk once this returns. */
    public void commit() {
        try {
            writer.commit();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Messages of exactly this thread and these kinds, newest first. Returned in time order. */
    public List<IndexedDocument> thread(Scope scope, Set<MessageKind> kinds, int maxMessages) {
        if (kinds.isEmpty() || maxMessages <= 0 || !projectId.equals(scope.projectId())) {
            return List.of();
        }
        BooleanQuery.Builder query = projectFilter();
        query.add(new TermQuery(new Term(THREAD, scope.threadId())), BooleanClause.Occur.FILTER);
        query.add(anyOf(KIND, kinds.stream().map(Enum::name).toList()), BooleanClause.Occur.FILTER);
        List<IndexedDocument> newestFirst = new ArrayList<>();
        Sort sort = new Sort(new SortField(RECORDED, SortField.Type.LONG, true));
        withSearcher(searcher -> {
            for (ScoreDoc hit : searcher.search(query.build(), maxMessages, sort).scoreDocs) {
                newestFirst.add(fromLucene(searcher.storedFields().document(hit.doc)));
            }
        });
        java.util.Collections.reverse(newestFirst);
        return newestFirst;
    }

    /**
     * Best matches among the given sources, for one agent. All three filters apply in the index itself.
     *
     * @param sourceIds sources the caller has already authorized: empty finds nothing
     * @param topicTags when not empty, only documents carrying one of these tags
     */
    public List<Hit> search(String text, Set<String> sourceIds, String agentId, Set<String> topicTags, int limit) {
        Query terms = termsQuery(text);
        if (terms == null || sourceIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        BooleanQuery.Builder query = projectFilter();
        query.add(anyOf(SOURCE, sourceIds), BooleanClause.Occur.FILTER);
        query.add(new TermQuery(new Term(PERMITTED, agentId)), BooleanClause.Occur.FILTER);
        if (!topicTags.isEmpty()) {
            query.add(anyOf(TOPIC, topicTags), BooleanClause.Occur.FILTER);
        }
        query.add(terms, BooleanClause.Occur.MUST);
        List<Hit> hits = new ArrayList<>();
        withSearcher(searcher -> {
            for (ScoreDoc hit : searcher.search(query.build(), limit).scoreDocs) {
                hits.add(new Hit(fromLucene(searcher.storedFields().document(hit.doc)), hit.score));
            }
        });
        return hits;
    }

    /** Removes every document of one source, for example before reloading it. */
    public void deleteSource(String sourceId) {
        try {
            writer.deleteDocuments(new Term(SOURCE, sourceId));
            searchers.maybeRefreshBlocking();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The document with this identifier, if this project has it. */
    public Optional<IndexedDocument> find(String documentId) {
        List<IndexedDocument> found = new ArrayList<>();
        withSearcher(searcher -> {
            for (ScoreDoc hit : searcher.search(new TermQuery(new Term(ID, documentId)), 1).scoreDocs) {
                found.add(fromLucene(searcher.storedFields().document(hit.doc)));
            }
        });
        return found.stream().findFirst();
    }

    /** Replaces a document's topic tags and keeps everything else. False if the document is unknown. */
    public boolean retag(String documentId, Set<String> topicTags) {
        List<IndexedDocument> found = new ArrayList<>();
        withSearcher(searcher -> {
            for (ScoreDoc hit : searcher.search(new TermQuery(new Term(ID, documentId)), 1).scoreDocs) {
                found.add(fromLucene(searcher.storedFields().document(hit.doc)));
            }
        });
        if (found.isEmpty()) {
            return false;
        }
        IndexedDocument old = found.get(0);
        add(new IndexedDocument(old.id(), old.scope(), old.sourceId(), old.kind(), old.language(), old.subject(),
                old.text(), old.permittedAgentIds(), topicTags, old.recordedAt()));
        return true;
    }

    public int size() {
        int[] count = new int[1];
        withSearcher(searcher -> count[0] = searcher.count(new MatchAllDocsQuery()));
        return count[0];
    }

    private BooleanQuery.Builder projectFilter() {
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        // Redundant with one index per project, on purpose: a mix-up of indexes must still find nothing.
        query.add(new TermQuery(new Term(PROJECT, projectId)), BooleanClause.Occur.FILTER);
        return query;
    }

    private static Query anyOf(String field, Iterable<String> values) {
        List<BytesRef> terms = new ArrayList<>();
        values.forEach(value -> terms.add(new BytesRef(value)));
        return new TermInSetQuery(field, terms);
    }

    /** Null when the text has no searchable term, such as only stop words. */
    private Query termsQuery(String text) {
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        int added = addTerms(query, BODY_EN, text) + addTerms(query, BODY_FR, text);
        return added == 0 ? null : query.build();
    }

    private int addTerms(BooleanQuery.Builder query, String field, String text) {
        Set<String> terms = new HashSet<>();
        try (TokenStream tokens = analyzer.tokenStream(field, text == null ? "" : text)) {
            CharTermAttribute term = tokens.addAttribute(CharTermAttribute.class);
            tokens.reset();
            while (tokens.incrementToken() && terms.size() < MAX_QUERY_TERMS) {
                terms.add(term.toString());
            }
            tokens.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        terms.forEach(term -> query.add(new TermQuery(new Term(field, term)), BooleanClause.Occur.SHOULD));
        return terms.size();
    }

    private static Document toLucene(IndexedDocument source) {
        Document document = new Document();
        document.add(new StringField(ID, source.id(), Field.Store.YES));
        document.add(new StringField(PROJECT, source.scope().projectId(), Field.Store.NO));
        document.add(new StringField(THREAD, source.scope().threadId(), Field.Store.YES));
        document.add(new StringField(SOURCE, source.sourceId(), Field.Store.YES));
        source.kind().ifPresent(kind -> document.add(new StringField(KIND, kind.name(), Field.Store.YES)));
        source.permittedAgentIds().forEach(agent -> document.add(new StringField(PERMITTED, agent, Field.Store.YES)));
        source.topicTags().forEach(tag -> document.add(new StringField(TOPIC, tag, Field.Store.YES)));
        document.add(new StoredField(LANGUAGE, source.language()));
        document.add(new StoredField(SUBJECT, source.subject()));
        document.add(new StoredField(TEXT, source.text()));
        document.add(new StoredField(RECORDED, source.recordedAt().toString()));
        document.add(new NumericDocValuesField(RECORDED, source.recordedAt().toEpochMilli()));
        String searchable = source.subject() + "\n" + source.text();
        document.add(new TextField("fr".equals(source.language()) ? BODY_FR : BODY_EN, searchable, Field.Store.NO));
        return document;
    }

    private IndexedDocument fromLucene(Document document) {
        String kind = document.get(KIND);
        return new IndexedDocument(document.get(ID), new Scope(projectId, document.get(THREAD)), document.get(SOURCE),
                kind == null ? Optional.empty() : Optional.of(MessageKind.valueOf(kind)), document.get(LANGUAGE),
                document.get(SUBJECT), document.get(TEXT), Set.of(document.getValues(PERMITTED)),
                Set.of(document.getValues(TOPIC)), Instant.parse(document.get(RECORDED)));
    }

    private interface SearcherTask {
        void run(IndexSearcher searcher) throws IOException;
    }

    private void withSearcher(SearcherTask task) {
        try {
            IndexSearcher searcher = searchers.acquire();
            try {
                task.run(searcher);
            } finally {
                searchers.release(searcher);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        searchers.close();
        writer.close();
        analyzer.close();
        directory.close();
    }
}
