package org.fxt.freexmltoolkit.service.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XPathFunctionLibrary;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.standards.FunctionCatalog;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;

/**
 * Corrections for the static errors users make most: an unknown function (XPST0017, "did you mean"), an
 * unknown variable (XPST0008) and an undeclared namespace prefix (XPST0081). Candidates are close in
 * edit distance (at most 2) and offered at most three at a time, closest first.
 */
final class QuerySuggestions {

    private static final int MAX = 3;
    private static final int MAX_DISTANCE = 2;
    private static final Pattern FUNCTION = Pattern.compile(
            "(\\d+)-argument function named (?:Q\\{([^}]*)\\})?([\\p{L}_][\\w.\\-]*(?::[\\p{L}_][\\w.\\-]*)?)\\(\\)");
    private static final Pattern VARIABLE = Pattern.compile("\\$([\\p{L}_][\\w.\\-]*(?::[\\p{L}_][\\w.\\-]*)?)");
    private static final Pattern PREFIX = Pattern.compile("[Pp]refix '?([\\p{L}_][\\w.\\-]*)'? (?:has not been|is not) declared");
    private static final Pattern DECLARED_FUNCTION = Pattern.compile(
            "declare\\s+function\\s+([\\p{L}_][\\w.\\-]*(?::[\\p{L}_][\\w.\\-]*)?)\\s*\\(");
    private static final String FN = "http://www.w3.org/2005/xpath-functions";
    private static final Set<String> NOT_NAMES = Set.of("and", "or", "div", "idiv", "mod", "eq", "ne", "lt", "le",
            "gt", "ge", "is", "to", "in", "return", "satisfies", "then", "else", "instance", "of", "cast", "as",
            "castable", "treat", "union", "intersect", "except", "node", "text", "comment", "element", "attribute");
    /** A name step after / or // that has no prefix, is no function call and no axis. */
    private static final Pattern UNPREFIXED_STEP = Pattern.compile(
            "/{1,2}([\\p{L}_][\\w.\\-]*)(?![\\w.\\-]*\\s*\\()(?![\\w.\\-]*::)(?![\\w.\\-]*:[\\p{L}*])");

    private QuerySuggestions() {
    }

    static List<QueryError.Suggestion> forError(String code, String message, String query, QueryLanguage language,
                                                XPathVersion version, String xml) {
        if (code == null || query == null || query.isEmpty()) {
            return List.of();
        }
        return switch (code) {
            case "XPST0017" -> functions(message, query, version);
            case "XPST0008" -> variables(message, query);
            case "XPST0081" -> prefixes(message, query, language, xml);
            default -> List.of();
        };
    }

    /** "substring takes 2 or 3 arguments." when the unknown function exists with another number of arguments. */
    static String arityNote(String code, String message, XPathVersion version) {
        if (!"XPST0017".equals(code) || message == null) {
            return null;
        }
        Matcher m = FUNCTION.matcher(message);
        if (!m.find() || (m.group(2) != null && !m.group(2).equals(FN)) || m.group(3).contains(":")) {
            return null;
        }
        String name = m.group(3);
        List<String> arities = new ArrayList<>();
        for (int a = 0; a <= 6; a++) {
            if (FunctionCatalog.exists(version(version), name, a)) {
                arities.add(String.valueOf(a));
            }
        }
        if (arities.isEmpty()) {
            // not in this version at all: name the version that has it (distinct-values() in XPath 1.0)
            XPathVersion since = FunctionCatalog.since(name, Integer.parseInt(m.group(1)));
            return since != null && since.compareTo(version(version)) > 0
                    ? I18n.t("QueryError.newer-function", name, since.label(), version(version).label()) : null;
        }
        String list = arities.size() == 1 ? arities.get(0)
                : String.join(", ", arities.subList(0, arities.size() - 1)) + " " + I18n.t("QueryError.or") + " "
                + arities.get(arities.size() - 1);
        return I18n.t("QueryError.arity", name, list);
    }

    /**
     * The unknown function of an XPST0017 message as it is written in the query, with its prefix
     * ({@code local:fo}); Saxon names it by namespace URI ({@code Q{uri}fo}).
     *
     * @return the name, or {@code null} when the message names none or the query does not contain it
     */
    static String functionNameInQuery(String message, String query) {
        Matcher m = FUNCTION.matcher(message == null ? "" : message);
        if (!m.find() || query == null) {
            return null;
        }
        String name = m.group(3);
        if (name.contains(":") || m.group(2) == null || m.group(2).equals(FN)) {
            return QueryErrors.find(query, name, true) != null ? name : null;
        }
        Matcher prefixed = Pattern.compile("(?<![\\w.:-])([\\p{L}_][\\w.\\-]*):" + Pattern.quote(name) + "\\s*\\(")
                .matcher(query);
        return prefixed.find() ? prefixed.group(1) + ":" + name : null;
    }

    /** @return the first unprefixed element name step in the query, or {@code null} */
    static String firstUnprefixedName(String query) {
        Matcher m = UNPREFIXED_STEP.matcher(query);
        while (m.find()) {
            if (!NOT_NAMES.contains(m.group(1))) {
                return m.group(1);
            }
        }
        return null;
    }

    private static List<QueryError.Suggestion> functions(String message, String query, XPathVersion version) {
        Matcher m = FUNCTION.matcher(message == null ? "" : message);
        if (!m.find()) {
            return List.of();
        }
        int arity = Integer.parseInt(m.group(1));
        String name = functionNameInQuery(message, query);
        if (name == null) {
            return List.of();
        }
        String prefix = name.contains(":") ? name.substring(0, name.indexOf(':') + 1) : "";
        String local = name.substring(prefix.length());
        int[] span = QueryErrors.find(query, name, true);
        if (span == null) {
            return List.of();
        }
        Set<String> candidates = new LinkedHashSet<>();
        for (String known : XPathFunctionLibrary.functionNames()) {
            String knownPrefix = known.contains(":") ? known.substring(0, known.indexOf(':') + 1) : "";
            if (!knownPrefix.equals(prefix)) {
                continue;
            }
            if (prefix.isEmpty() && !FunctionCatalog.exists(version(version), known, arity)
                    && !existsAnyArity(known, version)) {
                continue;
            }
            candidates.add(known.substring(knownPrefix.length()));
        }
        Matcher declared = DECLARED_FUNCTION.matcher(query);
        while (declared.find()) {
            String fn = declared.group(1);
            String fnPrefix = fn.contains(":") ? fn.substring(0, fn.indexOf(':') + 1) : "";
            if (fnPrefix.equals(prefix)) {
                candidates.add(fn.substring(fnPrefix.length()));
            }
        }
        return closest(local, candidates).stream()
                .map(c -> new QueryError.Suggestion(prefix + c, span[0] + prefix.length(), span[1], c))
                .toList();
    }

    private static List<QueryError.Suggestion> variables(String message, String query) {
        Matcher m = VARIABLE.matcher(message == null ? "" : message);
        if (!m.find()) {
            return List.of();
        }
        String unknown = m.group(1);
        int[] span = QueryErrors.find(query, "$" + unknown, false);
        if (span == null) {
            return List.of();
        }
        Set<String> declared = new LinkedHashSet<>();
        Matcher v = VARIABLE.matcher(query);
        while (v.find()) {
            if (!v.group(1).equals(unknown)) {
                declared.add(v.group(1));
            }
        }
        return closest(unknown, declared).stream()
                .map(c -> new QueryError.Suggestion("$" + c, span[0] + 1, span[1], c))
                .toList();
    }

    private static List<QueryError.Suggestion> prefixes(String message, String query, QueryLanguage language,
                                                        String xml) {
        Matcher m = PREFIX.matcher(message == null ? "" : message);
        if (!m.find()) {
            return List.of();
        }
        String prefix = m.group(1);
        int[] span = QueryErrors.find(query, prefix + ":", false);
        List<QueryError.Suggestion> out = new ArrayList<>();
        Map<String, String> declared = xml == null ? Map.of() : DocumentNamespaces.declared(xml);
        String uri = declared.get(prefix);
        if (language == QueryLanguage.XQUERY && uri != null) {
            String declaration = "declare namespace " + prefix + " = \"" + uri + "\";\n";
            out.add(new QueryError.Suggestion(declaration.strip(), 0, 0, declaration));
        }
        if (span != null) {
            int end = QueryText.tokenAt(query, span[0])[1];
            String local = query.substring(span[1], end);
            out.add(new QueryError.Suggestion("*:" + local, span[0], span[1], "*:"));
        }
        return out;
    }

    private static boolean existsAnyArity(String name, XPathVersion version) {
        for (int a = 0; a <= 4; a++) {
            if (FunctionCatalog.exists(version(version), name, a)) {
                return true;
            }
        }
        return false;
    }

    private static XPathVersion version(XPathVersion version) {
        if (version == null) {
            return XPathVersion.V3_1;
        }
        return version; // FunctionCatalog knows the XPath 1.0 core library
    }

    private static List<String> closest(String wrong, Set<String> candidates) {
        String w = wrong.toLowerCase(java.util.Locale.ROOT);
        record Scored(String name, int distance) {
        }
        List<Scored> scored = new ArrayList<>();
        for (String c : candidates) {
            int d = distance(w, c.toLowerCase(java.util.Locale.ROOT));
            if (d > 0 && d <= Math.min(MAX_DISTANCE, Math.max(1, wrong.length() / 3))) {
                scored.add(new Scored(c, d));
            } else if (d == 0 && !c.equals(wrong)) {
                scored.add(new Scored(c, 0)); // differs only in case
            }
        }
        scored.sort(Comparator.comparingInt(Scored::distance).thenComparing(Scored::name));
        return scored.stream().limit(MAX).map(Scored::name).toList();
    }

    /** Levenshtein distance with adjacent transpositions counting as one edit. */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
