package org.supermas.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.supermas.blackboard.api.Preprocessor;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;

/**
 * Turns an email into its first analysis, with no LLM: strip quotes and signatures, detect language,
 * then extract entities and labels. Each model is a switch, and everything works without one. See
 * docs/reports/step-1.9.md.
 *
 * <p>All output is a hint computed from untrusted text. It must never override permissions. Results
 * travel in {@link Analysis#metadata()} under the {@code KEY_} names, as JSON where they are lists,
 * so that the shared record does not have to change.
 */
public final class MetadataAgent implements Preprocessor {
    public static final String KEY_CURRENT_TEXT = "currentText";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_ENTITIES = "entities";
    public static final String KEY_SPEECH_ACTS = "speechActs";
    public static final String KEY_ROLE_HINT = "roleHint";
    public static final String KEY_FAILED_STAGES = "failedStages";

    /** Which source was stronger per type on dev: see docs/reports/step-1.6.md. */
    private static final Set<String> MODEL_TYPES = Set.of("PERSON", "COURSE", "ID");
    private static final Set<String> RULE_TYPES = Set.of("DATE", "DEADLINE", "ID");
    private static final Set<String> STOP_WORDS = Set.of("re", "fw", "fwd", "tr", "the", "a", "an", "and", "or",
            "of", "to", "for", "in", "on", "at", "is", "your", "my", "our", "with", "about", "le", "la", "les",
            "de", "des", "du", "et", "un", "une", "pour");

    private final ObjectMapper json = new ObjectMapper();
    private final QuoteSignatureStripper stripper = new QuoteSignatureStripper();
    private final LanguageDetector language = new LanguageDetector();
    private final RuleEntityExtractor ruleEntities = new RuleEntityExtractor();
    private final RuleRoleHintClassifier roleHints = new RuleRoleHintClassifier();
    private final Optional<GlinerEntityExtractor> modelEntities;
    private final SpeechActClassifier ruleActs = new RuleSpeechActClassifier();
    private final Optional<SpeechActClassifier> modelActs;

    /**
     * @param modelEntities empty to use rules only for entities
     * @param modelActs empty to use rules only for speech acts
     */
    public MetadataAgent(Optional<GlinerEntityExtractor> modelEntities, Optional<SpeechActClassifier> modelActs) {
        this.modelEntities = modelEntities;
        this.modelActs = modelActs;
    }

    /** Rules only: needs no model file. */
    public static MetadataAgent rulesOnly() {
        return new MetadataAgent(Optional.empty(), Optional.empty());
    }

    @Override
    public Analysis analyze(Email email) {
        String currentText = stripper.currentText(email.body() == null ? "" : email.body());
        String detected = language.detect(currentText);
        List<String> failed = new ArrayList<>();
        List<EntityMention> entities = entities(currentText, failed);
        List<SpeechAct> acts = speechActs(currentText, failed);
        Optional<String> role = roleHints.classify(currentText);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(KEY_CURRENT_TEXT, currentText);
        metadata.put(KEY_LANGUAGE, detected);
        metadata.put(KEY_ENTITIES, toJson(entities));
        metadata.put(KEY_SPEECH_ACTS, toJson(acts));
        role.ifPresent(value -> metadata.put(KEY_ROLE_HINT, value));
        metadata.put(KEY_FAILED_STAGES, toJson(failed));

        List<String> tags = new ArrayList<>();
        tags.add("lang:" + detected);
        acts.forEach(act -> tags.add("act:" + act.act()));
        role.ifPresent(value -> tags.add("role:" + value));
        return new Analysis(keywords(email.subject(), entities), tags, metadata);
    }

    /** If the model fails, the email is still analyzed with every rule type, and the failure is reported. */
    private List<EntityMention> entities(String text, List<String> failed) {
        List<EntityMention> rules = ruleEntities.extract(text);
        if (modelEntities.isEmpty()) {
            return rules;
        }
        try {
            return hybrid(modelEntities.get().extract(text), rules);
        } catch (Exception e) {
            failed.add("entityModel");
            return rules;
        }
    }

    /** The model goes first, so it decides the type when both sources find the same text. */
    public static List<EntityMention> hybrid(List<EntityMention> model, List<EntityMention> rules) {
        Map<String, EntityMention> merged = new LinkedHashMap<>();
        model.stream().filter(m -> MODEL_TYPES.contains(m.type()))
                .forEach(m -> merged.putIfAbsent(m.text().toLowerCase(Locale.ROOT), m));
        rules.stream().filter(m -> RULE_TYPES.contains(m.type()))
                .forEach(m -> merged.putIfAbsent(m.text().toLowerCase(Locale.ROOT), m));
        return new ArrayList<>(merged.values());
    }

    private List<SpeechAct> speechActs(String text, List<String> failed) {
        try {
            return (modelActs.isPresent() ? modelActs.get() : ruleActs).classify(text);
        } catch (Exception e) {
            failed.add("speechActModel");
            try {
                return ruleActs.classify(text);
            } catch (Exception impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    /** Subject words and course names, as a starting point for routing and retrieval. */
    private static List<String> keywords(String subject, List<EntityMention> entities) {
        Set<String> keywords = new LinkedHashSet<>();
        if (subject != null) {
            for (String word : subject.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                if (word.length() > 1 && !STOP_WORDS.contains(word)) {
                    keywords.add(word);
                }
            }
        }
        entities.stream().filter(m -> m.type().equals("COURSE"))
                .forEach(m -> keywords.add(m.text().toLowerCase(Locale.ROOT)));
        return new ArrayList<>(keywords);
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Plain records and lists must serialize", e);
        }
    }
}
