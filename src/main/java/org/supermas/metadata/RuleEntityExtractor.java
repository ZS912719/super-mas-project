package org.supermas.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regular-expression entity extraction: the floor that models must beat, and a source of precise
 * dates and identifiers. It finds people only where a greeting names them, so its PERSON recall is
 * deliberately low.
 */
public final class RuleEntityExtractor {
    private static final String SOURCE = "rules";
    private static final double CONFIDENCE = 0.9;
    private static final String WEEKDAY =
            "(?:Mon|Tues?|Wed(?:nes)?|Thu(?:rs?)?|Fri|Sat(?:ur)?|Sun)(?:day)?";
    private static final String MONTH = "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|June?|July?"
            + "|Aug(?:ust)?|Sept?(?:ember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\.?";
    private static final Pattern DATE = Pattern.compile(
            "(?:(?:next|this)\\s+)?(?:" + WEEKDAY + ",?\\s+)?" + MONTH + "\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s+\\d{4})?"
            + "|(?:" + WEEKDAY + ",?\\s+)?\\d{1,2}\\s+" + MONTH + "(?:\\s+\\d{4})?"
            + "|\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b"
            + "|\\b(?:(?:next|this)\\s+)?" + WEEKDAY + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEADLINE_CUE = Pattern.compile(
            "\\b(deadline|due|by|before|no later than|until|thru|through)\\b[^.?!\\n]{0,25}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COURSE = Pattern.compile("\\b[A-Z]{2,4}\\s?\\d{3,4}\\b");
    private static final Pattern NUMBER_AFTER_CUE = Pattern.compile(
            "(?i:\\b(?:order|ticket|invoice|account|deal|request id|student number|tag|case|reference)"
            + "\\b(?:\\s+(?:number|no\\.?|id))?(?:\\s+is)?\\s*[:#]?\\s*)#?([A-Za-z]{0,3}\\d[\\w.-]*\\d)");
    private static final Pattern HASH_NUMBER = Pattern.compile("#(\\d{3,})");
    private static final Pattern GREETING = Pattern.compile(
            "^(?:(?:Dear|Hi|Hello|Hey)\\s+)(?:(?:Prof|Dr|Mr|Mrs|Ms)\\.?\\s+)?"
            + "([A-Z][a-zA-Z'-]+(?:\\s+[A-Z][a-zA-Z'-]+)?)\\s*[,:-]",
            Pattern.MULTILINE);
    private static final Pattern BARE_GREETING = Pattern.compile("\\A\\s*([A-Z][a-z]+)\\s*[,:-]\\s*$", Pattern.MULTILINE);

    public List<EntityMention> extract(String text) {
        Map<String, EntityMention> found = new LinkedHashMap<>();
        Matcher date = DATE.matcher(text);
        while (date.find()) {
            String before = text.substring(Math.max(0, date.start() - 40), date.start());
            // A date at the end of a sentence must not keep the full stop an abbreviated month may take.
            String value = date.group().replaceAll("\\.$", "");
            add(found, DEADLINE_CUE.matcher(before).find() ? "DEADLINE" : "DATE", value);
        }
        Matcher course = COURSE.matcher(text);
        while (course.find()) {
            add(found, "COURSE", course.group());
        }
        for (Pattern pattern : List.of(NUMBER_AFTER_CUE, HASH_NUMBER)) {
            Matcher id = pattern.matcher(text);
            while (id.find()) {
                add(found, "ID", id.group(1));
            }
        }
        for (Pattern pattern : List.of(GREETING, BARE_GREETING)) {
            Matcher person = pattern.matcher(text);
            while (person.find()) {
                add(found, "PERSON", person.group(1));
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void add(Map<String, EntityMention> found, String type, String text) {
        String clean = text.replaceAll("\\s+", " ").strip();
        found.putIfAbsent(clean.toLowerCase(Locale.ROOT), new EntityMention(type, clean, CONFIDENCE, SOURCE));
    }
}
