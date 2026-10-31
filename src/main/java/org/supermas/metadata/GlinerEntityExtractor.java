package org.supermas.metadata;

import ai.onnxruntime.OrtException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Maps GLiNER spans onto the schema's entity types and applies its annotation rules, such as leaving
 * out titles. "organization" is requested only as a distractor: without it, company names tend to be
 * forced into the other labels. See docs/reports/step-1.6.md.
 */
public final class GlinerEntityExtractor {
    private static final String SOURCE = "gliner";
    private static final List<String> LABELS =
            List.of("person", "course", "date", "deadline", "identifier", "organization");
    private static final Pattern TITLE = Pattern.compile(
            "^(?:(?:Dear|Prof(?:essor)?|Dr|Mr|Mrs|Ms|Mx|Mme|M)\\.?\\s+)+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final Pattern DEADLINE_CUE = Pattern.compile(
            "\\b(deadline|due|by|before|no later than|until)\\b[^.?!\\n]{0,25}$", Pattern.CASE_INSENSITIVE);

    private final GlinerEntityModel model;

    public GlinerEntityExtractor(GlinerEntityModel model) {
        this.model = model;
    }

    public List<EntityMention> extract(String text) throws OrtException {
        Map<String, EntityMention> found = new LinkedHashMap<>();
        for (GlinerEntityModel.Span span : model.predict(text, LABELS)) {
            String type = schemaType(span, text);
            if (type == null) {
                continue;
            }
            String clean = type.equals("PERSON") ? TITLE.matcher(span.text()).replaceFirst("") : span.text();
            clean = clean.replaceAll("\\s+", " ").strip();
            if (!clean.isEmpty()) {
                found.putIfAbsent(clean.toLowerCase(Locale.ROOT),
                        new EntityMention(type, clean, span.score(), SOURCE));
            }
        }
        return new ArrayList<>(found.values());
    }

    private static String schemaType(GlinerEntityModel.Span span, String text) {
        switch (span.label()) {
            case "person":
                return "PERSON";
            case "course":
                return "COURSE";
            case "identifier":
                // "student number" names an identifier but is not one.
                return HAS_DIGIT.matcher(span.text()).find() ? "ID" : null;
            case "date":
            case "deadline":
                // The model rarely separates these, so the words before the date decide.
                String before = text.substring(Math.max(0, span.start() - 40), span.start());
                return DEADLINE_CUE.matcher(before).find() ? "DEADLINE" : "DATE";
            default:
                return null;
        }
    }
}
