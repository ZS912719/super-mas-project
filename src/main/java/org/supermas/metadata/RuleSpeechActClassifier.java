package org.supermas.metadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Speech acts from English phrases, written from the schema's definitions without reading the dev
 * emails, so the comparison with the model is not tuned to them. A rule only says that a phrase was
 * present, so confidence is always 1.0. French is deferred along with French data.
 */
public final class RuleSpeechActClassifier implements SpeechActClassifier {
    private static final String SOURCE = "rule";
    /** "Please find attached" delivers something and asks for nothing. */
    private static final Pattern DELIVERY_COURTESY = Pattern.compile(
            "\\bplease\\s+(?:find|see)\\s+(?:the\\s+)?(?:attached|enclosed|below)\\b", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put("request", phrases("please", "kindly", "(?:could|can|would|will) you", "let me know",
                "send me", "i need you to", "you need to", "i would like you to", "do you (?:have|know)"));
        PATTERNS.put("propose", phrases("shall we", "let'?s", "how about", "why don'?t we", "we could",
                "we should", "i (?:suggest|propose|recommend)", "would you like to",
                "are you (?:free|available)"));
        PATTERNS.put("commit", phrases("i will", "i'?ll", "i shall", "we will", "we'?ll", "i am going to",
                "i'?m going to", "we are going to", "i promise"));
        PATTERNS.put("deliver", phrases("attached", "attachment", "enclosed", "here is", "here'?s", "here are",
                "see below", "fyi", "as requested", "please find"));
    }

    private static Pattern phrases(String... alternatives) {
        return Pattern.compile("\\b(?:" + String.join("|", alternatives) + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public List<SpeechAct> classify(String currentText) {
        List<SpeechAct> acts = new ArrayList<>();
        if (currentText == null) {
            return acts;
        }
        String withoutCourtesy = DELIVERY_COURTESY.matcher(currentText).replaceAll(" ");
        PATTERNS.forEach((act, pattern) -> {
            String text = act.equals("request") ? withoutCourtesy : currentText;
            if (pattern.matcher(text).find()) {
                acts.add(new SpeechAct(act, 1.0, SOURCE));
            }
        });
        return acts;
    }
}
