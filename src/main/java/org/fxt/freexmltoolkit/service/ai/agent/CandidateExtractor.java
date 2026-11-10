package org.fxt.freexmltoolkit.service.ai.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes the artefact out of a model's answer: exactly one fenced code block of the expected language
 * ({@code ```xpath}, {@code ```xml} …). An answer with none, or with several, is a failed round; the message tells the
 * model what to fix.
 */
public final class CandidateExtractor {

    private static final Pattern FENCE = Pattern.compile("```([\\w+-]*)[ \\t]*\\R(.*?)\\R?```", Pattern.DOTALL);

    /** The candidate, or why there is none. */
    public record Extraction(String candidate, String problem) {
        public boolean ok() {
            return candidate != null;
        }
    }

    private final Set<String> languages;
    private final String expected;

    /** @param languages accepted fence languages (lower case); an untagged block counts when it is the only one */
    public CandidateExtractor(String expected, Set<String> languages) {
        this.expected = expected;
        this.languages = languages;
    }

    public static CandidateExtractor xpath() {
        return new CandidateExtractor("xpath", Set.of("xpath", "xpath3", "xquery"));
    }

    public static CandidateExtractor xquery() {
        return new CandidateExtractor("xquery", Set.of("xquery", "xq", "xpath"));
    }

    public static CandidateExtractor xslt() {
        return new CandidateExtractor("xml", Set.of("xml", "xslt", "xsl"));
    }

    public static CandidateExtractor json() {
        return new CandidateExtractor("json", Set.of("json"));
    }

    public Extraction extract(String answer) {
        if (answer == null || answer.isBlank()) {
            return new Extraction(null, "The answer was empty. Answer with one ```" + expected + " block.");
        }
        List<String> tagged = new ArrayList<>();
        List<String> untagged = new ArrayList<>();
        Matcher m = FENCE.matcher(answer);
        while (m.find()) {
            String language = m.group(1).toLowerCase(Locale.ROOT);
            String body = m.group(2).strip();
            if (languages.contains(language)) {
                tagged.add(body);
            } else if (language.isEmpty()) {
                untagged.add(body);
            }
        }
        if (tagged.size() == 1) {
            return new Extraction(tagged.getFirst(), null);
        }
        if (tagged.isEmpty() && untagged.size() == 1) {
            return new Extraction(untagged.getFirst(), null);
        }
        if (tagged.size() > 1 || untagged.size() > 1) {
            return new Extraction(null, "The answer contained several code blocks. Answer with exactly one ```"
                    + expected + " block.");
        }
        return new Extraction(null, "The answer contained no ```" + expected + " block. Answer with exactly one.");
    }
}
