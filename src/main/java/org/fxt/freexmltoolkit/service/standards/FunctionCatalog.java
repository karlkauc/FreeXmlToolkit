package org.fxt.freexmltoolkit.service.standards;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;

/**
 * Which built-in functions ({@code fn:} namespace) exist in which XPath version, read from Saxon's own function
 * libraries per language level (20 = XPath 2.0 / XQuery 1.0, 30 = 3.0, 31 = 3.1), plus a scan that finds calls
 * of functions a query's target version does not have.
 */
public final class FunctionCatalog {

    private static final Configuration CONFIG = new Configuration();
    private static final Pattern CALL = Pattern.compile(
            "(?<![\\w.$:-])(?:(fn|math|map|array):)?([A-Za-z_][\\w.-]*)\\s*\\(");
    private static final java.util.Set<String> KEYWORDS = java.util.Set.of("if", "typeswitch", "switch",
            "element", "attribute", "node", "text", "comment", "processing-instruction", "document-node",
            "schema-element", "schema-attribute", "item", "empty-sequence", "function", "map", "array",
            "namespace-node", "return", "satisfies", "and", "or", "div", "idiv", "mod", "union", "intersect",
            "except", "to", "eq", "ne", "lt", "le", "gt", "ge", "is", "instance", "treat", "castable", "cast",
            "where", "in", "else", "then", "ordered", "unordered", "validate", "document");

    private FunctionCatalog() {
    }

    /** Saxon's language level of an XPath version (1.0 = the 2.0 library, the 1.0 engine is checked apart). */
    static int level(XPathVersion version) {
        return switch (version) {
            case V1_0, V2_0 -> 20;
            case V3_0 -> 30;
            case V3_1 -> 31;
        };
    }

    /** The XPath 1.0 core function library: name → {min, max} arguments (max -1 = any number). */
    private static final java.util.Map<String, int[]> XPATH10 = java.util.Map.ofEntries(
            java.util.Map.entry("last", new int[] {0, 0}), java.util.Map.entry("position", new int[] {0, 0}),
            java.util.Map.entry("count", new int[] {1, 1}), java.util.Map.entry("id", new int[] {1, 1}),
            java.util.Map.entry("local-name", new int[] {0, 1}), java.util.Map.entry("namespace-uri", new int[] {0, 1}),
            java.util.Map.entry("name", new int[] {0, 1}), java.util.Map.entry("string", new int[] {0, 1}),
            java.util.Map.entry("concat", new int[] {2, -1}), java.util.Map.entry("starts-with", new int[] {2, 2}),
            java.util.Map.entry("contains", new int[] {2, 2}), java.util.Map.entry("substring-before", new int[] {2, 2}),
            java.util.Map.entry("substring-after", new int[] {2, 2}), java.util.Map.entry("substring", new int[] {2, 3}),
            java.util.Map.entry("string-length", new int[] {0, 1}),
            java.util.Map.entry("normalize-space", new int[] {0, 1}), java.util.Map.entry("translate", new int[] {3, 3}),
            java.util.Map.entry("boolean", new int[] {1, 1}), java.util.Map.entry("not", new int[] {1, 1}),
            java.util.Map.entry("true", new int[] {0, 0}), java.util.Map.entry("false", new int[] {0, 0}),
            java.util.Map.entry("lang", new int[] {1, 1}), java.util.Map.entry("number", new int[] {0, 1}),
            java.util.Map.entry("sum", new int[] {1, 1}), java.util.Map.entry("floor", new int[] {1, 1}),
            java.util.Map.entry("ceiling", new int[] {1, 1}), java.util.Map.entry("round", new int[] {1, 1}));

    /** Whether {@code fn:localName#arity} exists in {@code version} (1.0: the XPath 1.0 core library). */
    public static boolean exists(XPathVersion version, String localName, int arity) {
        if (version == XPathVersion.V1_0) {
            int[] range = XPATH10.get(localName);
            return range != null && arity >= range[0] && (range[1] < 0 || arity <= range[1]);
        }
        BuiltInFunctionSet set = CONFIG.getXPathFunctionSet(level(version));
        return set.getFunctionDetails(localName, arity) != null;
    }

    /** The oldest version that has {@code fn:localName#arity}, or {@code null} for none. */
    public static XPathVersion since(String localName, int arity) {
        for (XPathVersion v : XPathVersion.values()) {
            if (exists(v, localName, arity)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Finds calls in {@code mask} of built-in functions that {@code target} does not have (unprefixed or
     * {@code fn:}), and any {@code math:} / {@code map:} / {@code array:} function before 3.0 / 3.1.
     */
    static List<CompatibilityIssue> findNewerCalls(SourceMask mask, XPathVersion target, String languageLabel) {
        List<CompatibilityIssue> issues = new ArrayList<>();
        String text = mask.masked();
        Matcher m = CALL.matcher(text);
        while (m.find()) {
            String prefix = m.group(1);
            String name = m.group(2);
            int open = m.end() - 1;
            if (prefix == null && KEYWORDS.contains(name)) {
                continue;
            }
            if ("math".equals(prefix) || "map".equals(prefix) || "array".equals(prefix)) {
                XPathVersion since = "math".equals(prefix) ? XPathVersion.V3_0 : XPathVersion.V3_1;
                if (target.compareTo(since) < 0) {
                    issues.add(issue(mask, m.start(), prefix + ":" + name + "()", since, languageLabel));
                }
                continue;
            }
            int arity = arity(text, open);
            if (arity < 0 || exists(target, name, arity)) {
                continue;
            }
            XPathVersion since = since(name, arity);
            if (since != null && since.compareTo(target) > 0) {
                issues.add(issue(mask, m.start(), (prefix == null ? "" : prefix + ":") + name + "#" + arity, since,
                        languageLabel));
            }
        }
        return issues;
    }

    private static CompatibilityIssue issue(SourceMask mask, int offset, String what, XPathVersion since,
                                            String languageLabel) {
        return new CompatibilityIssue(mask.line(offset), mask.column(offset), "FXT-COMPAT-FUNCTION",
                "The function " + what + " is not available in " + languageLabel + " (introduced in "
                        + since.label() + ").");
    }

    /** The number of top-level arguments of the call whose '(' is at {@code open}; -1 when unbalanced. */
    static int arity(String text, int open) {
        int depth = 0;
        int commas = 0;
        boolean any = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
                if (depth > 1) {
                    any = true;
                }
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return any ? commas + 1 : 0;
                }
            } else if (depth == 1) {
                if (c == ',') {
                    commas++;
                    any = true;
                } else if (!Character.isWhitespace(c)) {
                    any = true;
                }
            }
        }
        return -1;
    }
}
