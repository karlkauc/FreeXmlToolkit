package org.fxt.freexmltoolkit.service.standards;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the JDK XPath 1.0 engine's messages into what Saxon reports for the same mistake: an error code
 * ({@code XPST0003} syntax, {@code XPST0017} unknown function, {@code XPST0008} unknown variable), a message in
 * Saxon's wording (so "Did you mean" and the arity note work unchanged) and, where the JDK names a token, the
 * offset of the error in the expression. The JDK reports no positions of its own.
 */
public final class Xpath10Messages {

    /**
     * @param code    the error code, or {@code null} for an evaluation error
     * @param message the message for display
     * @param offset  0-based offset of the error in the expression, or -1
     */
    public record Translated(String code, String message, int offset) {
    }

    private static final Pattern UNKNOWN_FUNCTION = Pattern.compile("Could not find function: ([\\w.:-]+)");
    private static final Pattern WRONG_ARITY = Pattern.compile("\\.Func(\\w+) only allows (.+) arguments?");
    private static final Pattern UNKNOWN_VARIABLE = Pattern.compile("resolve variable ([\\w.:-]+)");
    private static final Pattern TOKEN = Pattern.compile("following token was encountered:\\s*(\\S+)");
    private static final Pattern EXTRA = Pattern.compile("Extra illegal tokens: '([^']*)'");

    private Xpath10Messages() {
    }

    /**
     * @param jdkMessage the JDK's message (without "XPath 1.0: "); nullable
     * @param expression the expression; nullable
     */
    public static Translated translate(String jdkMessage, String expression) {
        String m = jdkMessage == null ? "" : jdkMessage.strip();
        String q = expression == null ? "" : expression;
        Matcher f = UNKNOWN_FUNCTION.matcher(m);
        if (f.find()) {
            String name = f.group(1);
            return new Translated("XPST0017", "Cannot find a " + arity(q, name) + "-argument function named "
                    + name + "()", -1);
        }
        Matcher a = WRONG_ARITY.matcher(m);
        if (a.find()) {
            String name = hyphenated(a.group(1));
            return new Translated("XPST0017", "Cannot find a " + arity(q, name) + "-argument function named "
                    + name + "()", -1);
        }
        Matcher v = UNKNOWN_VARIABLE.matcher(m);
        if (v.find()) {
            return new Translated("XPST0008", "Variable $" + v.group(1) + " has not been declared", -1);
        }
        if (m.contains("end of the XPath expression was found") || m.matches("(?s)Expected .*, but found:\\s*")) {
            return new Translated("XPST0003", m, lastNonBlank(q));
        }
        Matcher t = TOKEN.matcher(m);
        if (t.find()) {
            return new Translated("XPST0003", m, afterOperator(q, t.group(1)));
        }
        Matcher x = EXTRA.matcher(m);
        if (x.find()) {
            return new Translated("XPST0003", m, unmatchedCloser(q, x.group(1)));
        }
        if (m.startsWith("Expected") || m.contains("was expected") || m.contains("illegal")
                || m.contains("Unclosed") || m.contains("Prefix must resolve")) {
            return new Translated("XPST0003", m, -1);
        }
        return new Translated(null, m, -1);
    }

    /** {@code SubstringBefore} → {@code substring-before}. */
    static String hyphenated(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    /** The number of arguments of the first call of {@code name} in {@code query}, or 0. */
    static int arity(String query, String name) {
        Matcher call = Pattern.compile("(?<![\\w.:-])" + Pattern.quote(name) + "\\s*\\(").matcher(query);
        if (!call.find()) {
            return 0;
        }
        int depth = 0;
        int args = 0;
        boolean content = false;
        char quote = 0;
        for (int i = call.end(); i < query.length(); i++) {
            char c = query.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                content = true;
            } else if (c == '(' || c == '[') {
                depth++;
                content = true;
            } else if (c == ')' || c == ']') {
                if (depth == 0) {
                    return content ? args + 1 : args;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                args++;
            } else if (!Character.isWhitespace(c)) {
                content = true;
            }
        }
        return content ? args + 1 : args;
    }

    private static int lastNonBlank(String q) {
        for (int i = q.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(q.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /** The first {@code token} that follows an operator, {@code [}, {@code (} or {@code ,} (where a path was due). */
    private static int afterOperator(String q, String token) {
        int from = 0;
        while (true) {
            int at = q.indexOf(token, from);
            if (at < 0) {
                return -1;
            }
            int before = at - 1;
            while (before >= 0 && Character.isWhitespace(q.charAt(before))) {
                before--;
            }
            if (before < 0 || "=<>!+-*|[(,/".indexOf(q.charAt(before)) >= 0) {
                return at;
            }
            from = at + 1;
        }
    }

    /** The first {@code ]} / {@code )} that closes nothing, else the first occurrence of {@code token}. */
    private static int unmatchedCloser(String q, String token) {
        int square = 0;
        int round = 0;
        char quote = 0;
        for (int i = 0; i < q.length(); i++) {
            char c = q.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            switch (c) {
                case '"', '\'' -> quote = c;
                case '[' -> square++;
                case '(' -> round++;
                case ']' -> {
                    if (--square < 0) {
                        return i;
                    }
                }
                case ')' -> {
                    if (--round < 0) {
                        return i;
                    }
                }
                default -> {
                }
            }
        }
        return token.isEmpty() ? -1 : q.indexOf(token);
    }
}
