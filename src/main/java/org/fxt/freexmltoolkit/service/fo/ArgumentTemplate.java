package org.fxt.freexmltoolkit.service.fo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The argument template of an external XSL-FO processor, turned into the argument list of one run.
 *
 * <ul>
 *   <li>Arguments are separated by whitespace; double quotes keep spaces in one argument.
 *       The list goes to the program directly, never through a shell.</li>
 *   <li>Placeholders {@code ${name}} are replaced inside an argument; a path with spaces stays
 *       one argument. Names: {@value #FO}, {@value #PDF} (also {@value #OUT}), {@value #XML},
 *       {@value #XSL}, {@value #BASE_DIR}, {@value #TITLE}, {@value #AUTHOR}, {@value #SUBJECT},
 *       {@value #PDFA}.</li>
 *   <li>An optional group {@code [ … ]} is left out when one of its placeholders is empty;
 *       {@value #PDFA} is empty unless PDF/A was asked for and expands to nothing otherwise, so
 *       {@code [-pdfprofile PDF/A-1b ${pdfa}]} adds a flag only for PDF/A.</li>
 *   <li>An argument that expands to an empty text outside a group is dropped.</li>
 * </ul>
 */
public final class ArgumentTemplate {

    /** The XSL-FO document to render. */
    public static final String FO = "fo";
    /** The PDF to write. */
    public static final String PDF = "pdf";
    /** Same as {@value #PDF}. */
    public static final String OUT = "out";
    /** The XML input of the XSLT stage (empty when rendering a finished FO). */
    public static final String XML = "xml";
    /** The stylesheet of the XSLT stage (empty when rendering a finished FO). */
    public static final String XSL = "xsl";
    /** The folder relative references are resolved against. */
    public static final String BASE_DIR = "baseDir";
    /** PDF title metadata. */
    public static final String TITLE = "title";
    /** PDF author metadata. */
    public static final String AUTHOR = "author";
    /** PDF subject metadata. */
    public static final String SUBJECT = "subject";
    /** Non-empty (but expanding to nothing) when PDF/A was asked for. */
    public static final String PDFA = "pdfa";

    /** Every placeholder name, in the order the dialog lists them. */
    public static final List<String> NAMES = List.of(FO, PDF, OUT, XML, XSL, BASE_DIR, TITLE, AUTHOR, SUBJECT, PDFA);

    /** Marker value of {@value #PDFA} when PDF/A is asked for: present, but expands to nothing. */
    public static final String FLAG_ON = "\u0000on";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z]+)}");

    private ArgumentTemplate() {
    }

    /**
     * Expands a template.
     *
     * @param template the template text
     * @param values   placeholder values (missing or {@code null} = empty)
     * @return the arguments
     * @throws IllegalArgumentException for an unknown placeholder, an unclosed quote or bracket
     */
    public static List<String> expand(String template, Map<String, String> values) {
        List<String> result = new ArrayList<>();
        for (Token token : tokenize(template)) {
            if (token.group().isEmpty()) {
                String expanded = substitute(token.text(), values);
                if (!expanded.isEmpty()) {
                    result.add(expanded);
                }
                continue;
            }
            boolean complete = true;
            List<String> parts = new ArrayList<>();
            for (String part : token.group()) {
                Matcher m = PLACEHOLDER.matcher(part);
                while (m.find()) {
                    String value = values.get(m.group(1));
                    if (value == null || value.isEmpty()) {
                        complete = false;
                    }
                }
                String expanded = substitute(part, values);
                if (!expanded.isEmpty()) {
                    parts.add(expanded);
                }
            }
            if (complete) {
                result.addAll(parts);
            }
        }
        return result;
    }

    /**
     * @return the placeholder names used in the template
     * @throws IllegalArgumentException for an unknown placeholder or a syntax error
     */
    public static Set<String> placeholders(String template) {
        Set<String> names = new TreeSet<>();
        for (Token token : tokenize(template)) {
            List<String> texts = token.group().isEmpty() ? List.of(token.text()) : token.group();
            for (String text : texts) {
                Matcher m = PLACEHOLDER.matcher(text);
                while (m.find()) {
                    names.add(m.group(1));
                }
            }
        }
        return names;
    }

    /** @return {@code null} when the template is valid, else the problem in plain words */
    public static String validate(String template) {
        try {
            Set<String> used = placeholders(template);
            if (!used.contains(FO)) {
                return "The arguments must contain ${fo}, the XSL-FO document to render.";
            }
            if (!used.contains(PDF) && !used.contains(OUT)) {
                return "The arguments must contain ${pdf}, the PDF to write.";
            }
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static String substitute(String text, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            if (value == null || FLAG_ON.equals(value)) {
                value = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** One argument, or an optional group of arguments. */
    private record Token(String text, List<String> group) {
    }

    private static List<Token> tokenize(String template) {
        List<Token> tokens = new ArrayList<>();
        if (template == null) {
            return tokens;
        }
        List<String> group = null;
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                hasToken = true;
            } else if (Character.isWhitespace(c) || c == '[' || c == ']') {
                if (hasToken) {
                    addToken(tokens, group, current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
                if (c == '[') {
                    if (group != null) {
                        throw new IllegalArgumentException("Optional groups [ … ] cannot be nested.");
                    }
                    group = new ArrayList<>();
                } else if (c == ']') {
                    if (group == null) {
                        throw new IllegalArgumentException("A ] has no matching [.");
                    }
                    tokens.add(new Token("", List.copyOf(group)));
                    group = null;
                }
            } else {
                current.append(c);
                hasToken = true;
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("A double quote is not closed.");
        }
        if (hasToken) {
            addToken(tokens, group, current.toString());
        }
        if (group != null) {
            throw new IllegalArgumentException("A [ has no matching ].");
        }
        return tokens;
    }

    private static void addToken(List<Token> tokens, List<String> group, String text) {
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            if (!NAMES.contains(m.group(1))) {
                throw new IllegalArgumentException("Unknown placeholder ${" + m.group(1) + "}. Known: "
                        + String.join(", ", NAMES.stream().map(n -> "${" + n + "}").toList()) + ".");
            }
        }
        if (group != null) {
            group.add(text);
        } else {
            tokens.add(new Token(text, List.of()));
        }
    }
}
