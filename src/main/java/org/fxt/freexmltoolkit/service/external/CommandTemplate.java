package org.fxt.freexmltoolkit.service.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The argument template of an external program (XSL-FO or XSLT processor), turned into the argument
 * list of one run.
 *
 * <ul>
 *   <li>Arguments are separated by whitespace; double quotes keep spaces in one argument.
 *       The list goes to the program directly, never through a shell.</li>
 *   <li>Placeholders {@code ${name}} are replaced inside an argument; a path with spaces stays
 *       one argument. Only the names given to the template are allowed.</li>
 *   <li>A <em>list</em> placeholder standing alone as an argument ({@code ${params}}) becomes as many
 *       arguments as the list has (none for an empty list).</li>
 *   <li>An optional group {@code [ … ]} is left out when one of its placeholders is empty;
 *       {@link #FLAG_ON} counts as present but expands to nothing, so a group can add a flag.</li>
 *   <li>An argument that expands to an empty text outside a group is dropped.</li>
 * </ul>
 */
public final class CommandTemplate {

    /** Marker value of a flag placeholder that is on: present, but expands to nothing. */
    public static final String FLAG_ON = "\u0000on";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z]+)}");
    private static final Pattern PARAMETER = Pattern.compile("\\{(name|value)}");

    private final List<String> names;

    /** @param names the placeholder names the template may use, in the order a dialog lists them */
    public CommandTemplate(List<String> names) {
        this.names = List.copyOf(names);
    }

    /** @return the allowed placeholder names */
    public List<String> names() {
        return names;
    }

    /**
     * Expands a template with text placeholders only.
     *
     * @throws IllegalArgumentException for an unknown placeholder, an unclosed quote or bracket
     */
    public List<String> expand(String template, Map<String, String> values) {
        return expand(template, values, Map.of());
    }

    /**
     * Expands a template.
     *
     * @param values placeholder values (missing or {@code null} = empty)
     * @param lists  list placeholders: a token that is exactly {@code ${name}} becomes one argument per
     *               element; inside a longer argument the elements are joined with spaces
     * @return the arguments
     * @throws IllegalArgumentException for an unknown placeholder, an unclosed quote or bracket
     */
    public List<String> expand(String template, Map<String, String> values, Map<String, List<String>> lists) {
        List<String> result = new ArrayList<>();
        for (Token token : tokenize(template)) {
            if (token.group().isEmpty()) {
                List<String> list = listOf(token.text(), lists);
                if (list != null) {
                    result.addAll(list);
                    continue;
                }
                String expanded = substitute(token.text(), values, lists);
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
                    String name = m.group(1);
                    boolean empty = lists.containsKey(name) ? lists.get(name) == null || lists.get(name).isEmpty()
                            : values.get(name) == null || values.get(name).isEmpty();
                    if (empty) {
                        complete = false;
                    }
                }
                List<String> list = listOf(part, lists);
                if (list != null) {
                    parts.addAll(list);
                    continue;
                }
                String expanded = substitute(part, values, lists);
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

    /** @return the list when the token is exactly one list placeholder, else {@code null} */
    private static List<String> listOf(String token, Map<String, List<String>> lists) {
        Matcher m = PLACEHOLDER.matcher(token);
        if (m.matches() && lists.containsKey(m.group(1))) {
            List<String> list = lists.get(m.group(1));
            return list == null ? List.of() : list;
        }
        return null;
    }

    /**
     * @return the placeholder names used in the template
     * @throws IllegalArgumentException for an unknown placeholder or a syntax error
     */
    public Set<String> placeholders(String template) {
        Set<String> used = new TreeSet<>();
        for (Token token : tokenize(template)) {
            List<String> texts = token.group().isEmpty() ? List.of(token.text()) : token.group();
            for (String text : texts) {
                Matcher m = PLACEHOLDER.matcher(text);
                while (m.find()) {
                    used.add(m.group(1));
                }
            }
        }
        return used;
    }

    /**
     * Expands a parameter template once per parameter: {@code --stringparam {name} {value}} gives three
     * arguments per parameter, {@code {name}={value}} one. Quotes keep spaces, as in the argument template;
     * a value with spaces stays inside its argument.
     *
     * @param parameters the parameters in order (a {@code null} value counts as empty)
     * @throws IllegalArgumentException for an unclosed quote, a bracket or a placeholder other than
     *                                  {@code {name}} / {@code {value}}
     */
    public static List<String> expandParameters(String parameterTemplate, Map<String, ?> parameters) {
        List<String> tokens = new ArrayList<>();
        for (Token token : new CommandTemplate(List.of()).tokenize(parameterTemplate)) {
            if (!token.group().isEmpty()) {
                throw new IllegalArgumentException("A parameter template has no optional groups [ … ].");
            }
            tokens.add(token.text());
        }
        if (!tokens.isEmpty() && tokens.stream().noneMatch(t -> t.contains("{name}"))) {
            throw new IllegalArgumentException("The parameter template must contain {name}.");
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ?> parameter : parameters.entrySet()) {
            String value = parameter.getValue() == null ? "" : String.valueOf(parameter.getValue());
            for (String token : tokens) {
                Matcher m = PARAMETER.matcher(token);
                StringBuilder sb = new StringBuilder();
                while (m.find()) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1).equals("name") ? parameter.getKey() : value));
                }
                m.appendTail(sb);
                result.add(sb.toString());
            }
        }
        return result;
    }

    /** @return {@code null} when the parameter template is valid, else the problem in plain words */
    public static String validateParameters(String parameterTemplate) {
        try {
            expandParameters(parameterTemplate, Map.of("n", "v"));
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private static String substitute(String text, Map<String, String> values, Map<String, List<String>> lists) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = lists.containsKey(m.group(1))
                    ? String.join(" ", lists.get(m.group(1)) == null ? List.of() : lists.get(m.group(1)))
                    : values.get(m.group(1));
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

    private List<Token> tokenize(String template) {
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

    private void addToken(List<Token> tokens, List<String> group, String text) {
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            if (!names.contains(m.group(1))) {
                throw new IllegalArgumentException("Unknown placeholder ${" + m.group(1) + "}. Known: "
                        + String.join(", ", names.stream().map(n -> "${" + n + "}").toList()) + ".");
            }
        }
        if (group != null) {
            group.add(text);
        } else {
            tokens.add(new Token(text, List.of()));
        }
    }
}
