package org.fxt.freexmltoolkit.service.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.QueryErrorHints;
import org.fxt.freexmltoolkit.service.standards.XPathVersion;

import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.trans.XPathException;

/**
 * Turns a failed XPath / XQuery run into a {@link QueryError} (code, position, cleaned message, hint,
 * corrections) and checks a query while it is typed ({@link #check}) without running it.
 *
 * <p>Saxon reports positions through the exception's locator. Its columns are 0-based for XPath
 * expressions and syntax errors ({@code NestedLocation}) and 1-based for XQuery run-time errors
 * ({@code Loc}); both are turned into 1-based columns here. Where the locator is missing or vague
 * (unknown variable, unknown function, unknown prefix) the name from the message is looked up in the
 * query instead.</p>
 */
public final class QueryErrors {

    private static final Processor PROCESSOR = new Processor(false);

    private static final Pattern VARIABLE = Pattern.compile("\\$([\\p{L}_][\\w.\\-]*(?::[\\p{L}_][\\w.\\-]*)?)");
    private static final Pattern PREFIX = Pattern.compile("[Pp]refix '?([\\p{L}_][\\w.\\-]*)'? (?:has not been|is not) declared");
    private static final Pattern CALL_START = Pattern.compile("^(?:fn:)?([\\p{L}_][\\w.\\-]*(?::[\\p{L}_][\\w.\\-]*)?)\\(");
    private static final Pattern SUPPLIED_CALL = Pattern.compile(
            "supplied expression \\{(?:fn:)?([\\p{L}_][\\w.\\-]*(?::[\\p{L}_][\\w.\\-]*)?)\\(");
    private static final Pattern OPERAND_OF = Pattern.compile("operand of '([^']+)'");
    private static final Pattern EQNAME = Pattern.compile("Q\\{([^}]*)\\}");
    private static final Pattern DECLARED_NS = Pattern.compile(
            "declare\\s+namespace\\s+([\\p{L}_][\\w.\\-]*)\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final List<String> WRAPPERS = List.of("Invalid XPath expression or XML: ",
            "XQuery execution failed: ", "Unexpected error: ", "ERROR: ");

    private QueryErrors() {
    }

    /** The outcome of {@link #check}: the first static error (or {@code null}) and the compiler's warnings. */
    public record Check(QueryError error, List<QueryWarning> warnings) {

        /** @return whether the query compiles without error or warning */
        public boolean clean() {
            return error == null && warnings.isEmpty();
        }
    }

    /**
     * Explains a failed run.
     *
     * @param failure      what the run threw (a Saxon exception anywhere in the chain gives code and position); nullable
     * @param fallbackCode the code when the failure carries none (e.g. from the telemetry classification); nullable
     * @param fallbackText the message when the failure has none; nullable
     * @param query        the query text
     * @param language     XPath or XQuery
     * @param version      the XPath version functions are looked up in ({@code null} = 3.1)
     * @param xml          the document the query ran on, for namespace suggestions; nullable
     */
    public static QueryError from(Throwable failure, String fallbackCode, String fallbackText, String query,
                                  QueryLanguage language, XPathVersion version, String xml) {
        String text = query == null ? "" : query;
        XPathException xpe = xpathException(failure);
        String code = fallbackCode;
        int line = -1;
        int column = -1;
        String message = fallbackText;
        String failing = null;
        if (xpe != null) {
            if (xpe.getFailingExpression() != null) {
                failing = xpe.getFailingExpression().toString();
            }
            if (xpe.getErrorCodeQName() != null) {
                code = xpe.getErrorCodeQName().getLocalPart();
            }
            message = xpe.getMessage();
            int[] pos = position(xpe.getLocator(), text);
            line = pos[0];
            column = pos[1];
        } else if (failure instanceof SaxonApiException sae && sae.getErrorCode() != null) {
            code = sae.getErrorCode().getLocalName();
            message = sae.getMessage();
        } else if (message == null && failure != null) {
            message = failure.getMessage();
        }
        return build(code, line, column, message, failing, text, language, version, xml);
    }

    /** As {@link #from} for an error known only by code and message (no position). */
    public static QueryError of(String code, String message, String query, QueryLanguage language) {
        return build(code, -1, -1, message, null, query == null ? "" : query, language, null, null);
    }

    /**
     * Compiles {@code query} without running it: the first static error with its position and corrections,
     * and the compiler's warnings.
     *
     * @param query    the query text
     * @param language XPath or XQuery
     * @param version  the XPath version (XPath only; {@code null} = 3.1)
     * @param xml      the document, for namespace suggestions; nullable
     */
    public static Check check(String query, QueryLanguage language, XPathVersion version, String xml) {
        if (query == null || query.isBlank()) {
            return new Check(null, List.of());
        }
        List<XmlProcessingError> reported = new ArrayList<>();
        try {
            if (language == QueryLanguage.XQUERY) {
                XQueryCompiler compiler = PROCESSOR.newXQueryCompiler();
                compiler.setErrorReporter(reported::add);
                compiler.compile(query);
            } else {
                XPathCompiler compiler = PROCESSOR.newXPathCompiler();
                QueryCompilers.configureXPath(compiler, version);
                compiler.setWarningHandler(reported::add);
                compiler.compile(query);
            }
            return new Check(null, warnings(reported, query));
        } catch (SaxonApiException e) {
            return new Check(from(e, null, e.getMessage(), query, language, version, xml), warnings(reported, query));
        } catch (RuntimeException e) {
            // a check must never break typing; an unexpected compiler failure is reported by the run
            return new Check(null, List.of());
        }
    }

    /**
     * A hint for a query without result on a document whose root element is in a namespace while the query
     * uses unprefixed element names (which match only elements in no namespace).
     *
     * @return the hint, or {@code null} when that is not the case
     */
    public static String emptyResultHint(String query, String xml) {
        if (query == null || xml == null) {
            return null;
        }
        String uri = DocumentNamespaces.rootNamespace(xml);
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        String name = QuerySuggestions.firstUnprefixedName(query);
        return name == null ? null : org.fxt.freexmltoolkit.i18n.I18n.t("QueryError.namespace-empty", uri, name);
    }

    // ---------------------------------------------------------------------------------------------

    private static QueryError build(String code, int line, int column, String rawMessage, String failing,
                                    String query, QueryLanguage language, XPathVersion version, String xml) {
        String message = clean(rawMessage == null ? "" : rawMessage, query, xml);
        int length = 1;
        int[] named = locateNamed(code, message, query);
        if (named != null) {
            int[] pos = QueryText.position(query, named[0]);
            line = pos[0];
            column = pos[1];
            length = Math.max(1, named[1] - named[0]);
        } else if (line > 0 && column > 0) {
            int offset = QueryText.offsetOf(query, line, column);
            int[] token = refine(code, message, operandCall(failing, rawMessage), query, offset,
                    QueryText.tokenAt(query, offset));
            int[] pos = QueryText.position(query, token[0]);
            line = pos[0];
            column = pos[1];
            length = Math.max(1, token[1] - token[0]);
        }
        List<QueryError.Suggestion> suggestions = QuerySuggestions.forError(code, message, query, language,
                version, xml);
        String hint = QueryErrorHints.hintFor(code, message);
        String arityNote = QuerySuggestions.arityNote(code, message, version);
        if (arityNote != null) {
            hint = hint == null ? arityNote : arityNote + "\n" + hint;
        }
        return new QueryError(code, QueryError.Kind.of(code), line, column, length, message, hint, suggestions,
                query, language);
    }

    private static List<QueryWarning> warnings(List<XmlProcessingError> reported, String query) {
        List<QueryWarning> out = new ArrayList<>();
        for (XmlProcessingError e : reported) {
            if (!e.isWarning()) {
                continue;
            }
            int[] pos = position(e.getLocation(), query);
            int length = 1;
            String message = clean(e.getMessage(), query, null);
            String code = e.getErrorCode() == null ? null : e.getErrorCode().getLocalName();
            if (pos[0] > 0) {
                int offset = QueryText.offsetOf(query, pos[0], pos[1]);
                int[] token = refine(code, message, operandCall(null, e.getMessage()), query, offset,
                        QueryText.tokenAt(query, offset));
                pos = QueryText.position(query, token[0]);
                length = Math.max(1, token[1] - token[0]);
            }
            out.add(new QueryWarning(code, pos[0], pos[1], length, message));
        }
        return out;
    }

    /**
     * Saxon's position is often the start of an operand or the end of a call. Where the message says which
     * operator or call failed, the span is moved onto it:
     * <ul>
     *   <li>XPTY0019: the {@code /} whose left side is the value (the first binary {@code /} from there)</li>
     *   <li>"operand of 'op'": the first {@code op} from there (or from the start when Saxon gave none)</li>
     *   <li>a closing parenthesis: the whole call {@code name(…)}</li>
     * </ul>
     */
    private static int[] refine(String code, String message, String call, String query, int offset, int[] token) {
        if ("XPTY0019".equals(code) || message.contains("operand of '/'")) {
            // Saxon may point into the left operand's argument (distinct-values(//item/@sku)/..): start after the
            // call that is the left operand when it encloses the position
            int from = offset;
            int[] enclosing = call == null ? null : enclosingCall(query, call, offset);
            if (enclosing != null) {
                from = enclosing[1];
            }
            int slash = binarySlashFrom(query, from);
            if (slash >= 0) {
                return new int[] {slash, slash + 1};
            }
        }
        Matcher op = OPERAND_OF.matcher(message);
        if (op.find() && !op.group(1).equals("/")) {
            int at = operatorFrom(query, op.group(1), offset);
            if (at < 0) {
                at = operatorFrom(query, op.group(1), 0);
            }
            if (at >= 0) {
                return new int[] {at, at + op.group(1).length()};
            }
        }
        if (token[1] - token[0] == 1 && token[0] < query.length() && query.charAt(token[0]) == ')') {
            int open = matchingOpen(query, token[0]);
            if (open >= 0) {
                int s = open;
                while (s > 0 && QueryText.isNameChar(query.charAt(s - 1))) {
                    s--;
                }
                if (s < open) {
                    return new int[] {s, token[1]};
                }
            }
        }
        return token;
    }

    /**
     * The function call that is the failing operand: from Saxon's failing expression
     * ({@code distinct-values(data(…))}) or from a warning's "supplied expression {distinct-values(...)}".
     *
     * @return the function name as it may appear in the query, or {@code null}
     */
    private static String operandCall(String failing, String message) {
        Matcher m = failing == null ? null : CALL_START.matcher(failing);
        if (m != null && m.find()) {
            return m.group(1);
        }
        Matcher w = message == null ? null : SUPPLIED_CALL.matcher(message);
        return w != null && w.find() ? w.group(1) : null;
    }

    /** {open, end} of the call {@code name(…)} in the query that contains {@code offset}, the innermost one. */
    private static int[] enclosingCall(String query, String name, int offset) {
        int[] best = null;
        int i = 0;
        while ((i = query.indexOf(name, i)) >= 0) {
            int nameEnd = i + name.length();
            boolean whole = (i == 0 || !QueryText.isNameChar(query.charAt(i - 1)) || query.charAt(i - 1) == ':')
                    && nameEnd < query.length() && query.charAt(nameEnd) == '(';
            if (whole && i <= offset) {
                int close = matchingClose(query, nameEnd);
                if (close >= offset) {
                    best = new int[] {i, close + 1};
                }
            }
            i = nameEnd;
        }
        return best;
    }

    private static int matchingClose(String query, int open) {
        int depth = 0;
        for (int i = open; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The first "/" from {@code from} that has an operand on its left (not a leading "/" or "//"). */
    private static int binarySlashFrom(String query, int from) {
        for (int i = Math.max(0, from); i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '"' || c == '\'') {
                int close = query.indexOf(c, i + 1);
                if (close < 0) {
                    return -1;
                }
                i = close;
                continue;
            }
            if (c != '/') {
                continue;
            }
            int j = i - 1;
            while (j >= 0 && Character.isWhitespace(query.charAt(j))) {
                j--;
            }
            if (j >= 0) {
                char before = query.charAt(j);
                if (before == ')' || before == ']' || before == '.' || before == '"' || before == '\''
                        || (QueryText.isNameChar(before) && before != ':')) {
                    return i;
                }
            }
            if (i + 1 < query.length() && query.charAt(i + 1) == '/') {
                i++;
            }
        }
        return -1;
    }

    /** The first occurrence of an operator from {@code from}: a word as a whole word, a symbol on its own. */
    private static int operatorFrom(String query, String operator, int from) {
        boolean word = Character.isLetter(operator.charAt(0));
        int i = Math.max(0, from);
        while ((i = query.indexOf(operator, i)) >= 0) {
            int end = i + operator.length();
            char before = i == 0 ? ' ' : query.charAt(i - 1);
            char after = end >= query.length() ? ' ' : query.charAt(end);
            boolean ok = word ? !QueryText.isNameChar(before) && !QueryText.isNameChar(after)
                    : !"=!<>+-*/".contains(String.valueOf(after)) && !"=!<>+-*/".contains(String.valueOf(before))
                    && !(operator.equals("-") && QueryText.isNameChar(before) && QueryText.isNameChar(after));
            if (ok) {
                return i;
            }
            i = end;
        }
        return -1;
    }

    private static int matchingOpen(String query, int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            char c = query.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** @return {line, column}, both 1-based, or {-1, -1} */
    private static int[] position(Location location, String query) {
        if (location == null || location.getLineNumber() < 1 || query.isEmpty()) {
            return new int[] {-1, -1};
        }
        int line = location.getLineNumber();
        if (line > QueryText.lineCount(query)) {
            return new int[] {-1, -1};
        }
        int raw = location.getColumnNumber();
        if (raw < 0) {
            return new int[] {line, 1};
        }
        // XPath expressions and syntax errors count from 0, XQuery's run-time locations from 1
        boolean zeroBased = location.getClass().getSimpleName().contains("Nested");
        return new int[] {line, Math.max(1, zeroBased ? raw + 1 : raw)};
    }

    /** The span of the variable / function / prefix the message names, when it occurs in the query. */
    private static int[] locateNamed(String code, String message, String query) {
        if (code == null || query.isEmpty()) {
            return null;
        }
        switch (code) {
            case "XPST0008" -> {
                Matcher m = VARIABLE.matcher(message);
                if (m.find()) {
                    return find(query, "$" + m.group(1), false);
                }
            }
            case "XPST0017" -> {
                String name = QuerySuggestions.functionNameInQuery(message, query);
                if (name != null) {
                    return find(query, name, true);
                }
            }
            case "XPST0081" -> {
                Matcher m = PREFIX.matcher(message);
                if (m.find()) {
                    int[] span = find(query, m.group(1) + ":", false);
                    if (span != null) {
                        int[] token = QueryText.tokenAt(query, span[0]);
                        return new int[] {span[0], token[1]};
                    }
                }
            }
            default -> {
                return null;
            }
        }
        return null;
    }

    /** First occurrence of {@code name} as a whole name (optionally followed by "("). */
    static int[] find(String query, String name, boolean call) {
        int from = 0;
        while (true) {
            int i = query.indexOf(name, from);
            if (i < 0) {
                return null;
            }
            int end = i + name.length();
            boolean startOk = i == 0 || !QueryText.isNameChar(query.charAt(i - 1)) || name.startsWith("$");
            boolean endOk = name.endsWith(":") || end == query.length() || !QueryText.isNameChar(query.charAt(end));
            if (startOk && endOk && (!call || query.substring(end).stripLeading().startsWith("("))) {
                return new int[] {i, end};
            }
            from = i + 1;
        }
    }

    /** The processor's message without wrappers and Saxon notation. */
    static String clean(String message, String query, String xml) {
        String m = message == null ? "" : message.strip();
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String w : WRAPPERS) {
                if (m.startsWith(w)) {
                    m = m.substring(w.length()).strip();
                    stripped = true;
                }
            }
        }
        m = m.replaceAll("(?<![\\w\"])u\"", "\"");
        Map<String, String> prefixes = prefixesByUri(query, xml);
        Matcher eq = EQNAME.matcher(m);
        StringBuilder out = new StringBuilder();
        while (eq.find()) {
            String uri = eq.group(1);
            String replacement;
            if (uri.isEmpty() || uri.equals("http://www.w3.org/2005/xpath-functions")) {
                replacement = "";
            } else if (prefixes.containsKey(uri)) {
                replacement = prefixes.get(uri) + ":";
            } else {
                replacement = eq.group(0);
            }
            eq.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        eq.appendTail(out);
        return out.toString().replace("{fn:", "{");
    }

    private static Map<String, String> prefixesByUri(String query, String xml) {
        Map<String, String> byUri = new java.util.HashMap<>();
        byUri.put("http://www.w3.org/2001/XMLSchema", "xs");
        byUri.put("http://www.w3.org/2005/xpath-functions/map", "map");
        byUri.put("http://www.w3.org/2005/xpath-functions/array", "array");
        byUri.put("http://www.w3.org/2005/xpath-functions/math", "math");
        byUri.put("http://www.w3.org/2005/xquery-local-functions", "local");
        if (xml != null) {
            DocumentNamespaces.declared(xml).forEach((prefix, uri) -> {
                if (!prefix.isEmpty()) {
                    byUri.putIfAbsent(uri, prefix);
                }
            });
        }
        if (query != null) {
            Matcher m = DECLARED_NS.matcher(query);
            while (m.find()) {
                byUri.put(m.group(2), m.group(1));
            }
        }
        return byUri;
    }

    private static XPathException xpathException(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof XPathException x) {
                return x;
            }
        }
        return null;
    }
}
