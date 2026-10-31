package org.supermas.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.supermas.metadata.MetadataAgent;
import org.supermas.metadata.RuleEntityExtractor;
import org.supermas.model.Domain.Analysis;
import org.supermas.model.Domain.Email;

/**
 * A table of what each label needs: a meeting request needs a date, for example. The table is a judgment,
 * not learned from data. English words only for now, with French to follow the deferred French work.
 */
public final class RuleMissingInformationCheck implements MissingInformationCheck {
    public static final String SOURCE = "rules";

    /**
     * @param mention a fixed name for what the email is about, safe to pass outside this class
     * @param acts speech acts this applies to
     * @param words any of these in the email triggers the rule: empty means always
     * @param satisfiedBy any of these present means nothing is missing
     * @param reported what to report otherwise
     */
    private record Requirement(String mention, Set<String> acts, List<String> words,
                               Set<InformationField> satisfiedBy, InformationField reported) { }

    private static final List<Requirement> TABLE = List.of(
            new Requirement("meeting", Set.of("request", "propose"),
                    List.of("meeting", "meet", "call", "appointment", "schedule", "conference call"),
                    EnumSet.of(InformationField.DATE, InformationField.DEADLINE), InformationField.DATE),
            new Requirement("commitment", Set.of("commit"), List.of(),
                    EnumSet.of(InformationField.DATE, InformationField.DEADLINE), InformationField.DEADLINE),
            new Requirement("submission", Set.of("request"), List.of("due", "submit", "extension", "deadline"),
                    EnumSet.of(InformationField.DATE, InformationField.DEADLINE), InformationField.DEADLINE),
            new Requirement("course", Set.of("request"), List.of("grade", "exam", "lecture", "assignment", "homework"),
                    EnumSet.of(InformationField.COURSE), InformationField.COURSE),
            new Requirement("reference", Set.of("request"), List.of("invoice", "contract", "ticket", "order number"),
                    EnumSet.of(InformationField.ID), InformationField.ID));

    private static final ObjectMapper JSON = new ObjectMapper();
    private final RuleEntityExtractor subjectEntities = new RuleEntityExtractor();

    @Override
    public MissingInformation check(Email email, Analysis analysis) {
        Set<String> acts = acts(analysis);
        String text = TopicText.normalize(email.subject() + " " + analysis.metadata().get(MetadataAgent.KEY_CURRENT_TEXT));
        Set<InformationField> present = present(email, analysis);
        Set<InformationField> missing = EnumSet.noneOf(InformationField.class);
        for (Requirement requirement : TABLE) {
            if (applies(requirement, acts, text) && requirement.satisfiedBy().stream().noneMatch(present::contains)) {
                missing.add(requirement.reported());
            }
        }
        return new MissingInformation(missing, SOURCE);
    }

    private static boolean applies(Requirement requirement, Set<String> acts, String text) {
        return requirement.acts().stream().anyMatch(acts::contains)
                && (requirement.words().isEmpty()
                || requirement.words().stream().anyMatch(word -> TopicText.contains(text, word)));
    }

    /** The fixed names of the table rows that this email triggers, to pass on without any email text. */
    static List<String> mentions(Email email, Analysis analysis) {
        Set<String> acts = acts(analysis);
        String text = TopicText.normalize(email.subject() + " " + analysis.metadata().get(MetadataAgent.KEY_CURRENT_TEXT));
        return TABLE.stream().filter(requirement -> applies(requirement, acts, text)).map(Requirement::mention).toList();
    }

    /** Entities of the new text, plus any date stated only in the subject. */
    Set<InformationField> present(Email email, Analysis analysis) {
        Set<InformationField> present = EnumSet.noneOf(InformationField.class);
        for (JsonNode entity : read(analysis, MetadataAgent.KEY_ENTITIES)) {
            addField(present, entity.path("type").asText());
        }
        if (email.subject() != null) {
            subjectEntities.extract(email.subject()).forEach(mention -> addField(present, mention.type()));
        }
        return present;
    }

    private static void addField(Set<InformationField> present, String type) {
        for (InformationField field : InformationField.values()) {
            if (field.name().equals(type)) {
                present.add(field);
            }
        }
    }

    static Set<String> acts(Analysis analysis) {
        Set<String> acts = new HashSet<>();
        for (JsonNode act : read(analysis, MetadataAgent.KEY_SPEECH_ACTS)) {
            acts.add(act.path("act").asText());
        }
        return acts;
    }

    private static JsonNode read(Analysis analysis, String key) {
        String value = analysis.metadata().get(key);
        try {
            return value == null ? JSON.createArrayNode() : JSON.readTree(value);
        } catch (JsonProcessingException e) {
            return JSON.createArrayNode();
        }
    }

    /** Field names and whether each is present, for callers that must not see the values. */
    Map<String, Boolean> presence(Email email, Analysis analysis) {
        Set<InformationField> present = present(email, analysis);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (InformationField field : InformationField.values()) {
            result.put(field.name(), present.contains(field));
        }
        return result;
    }
}
