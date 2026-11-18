package org.fxt.freexmltoolkit.service.xslt;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.service.QueryErrorHints;
import org.fxt.freexmltoolkit.service.query.QueryError;
import org.fxt.freexmltoolkit.service.query.QueryErrors;
import org.fxt.freexmltoolkit.service.query.QueryLanguage;

import net.sf.saxon.expr.parser.XPathParser;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.XmlProcessingError;
import net.sf.saxon.tree.AttributeLocation;

/**
 * Turns the static errors and warnings Saxon reports for a stylesheet into {@link XsltDiagnostic}s placed in the
 * module's text, as the query console does for queries.
 *
 * <p>Saxon locates an error in an XPath expression by the element's start tag (its end) plus the attribute and the
 * column inside the expression; an unknown attribute by the start tag and the attribute; anything else by the start
 * tag. With the module's text the diagnostic gets the exact range: the failing token of the expression (refined as
 * for queries, with "did you mean" corrections), the attribute name, or the element name.
 */
public final class XsltErrors {

    /** Most diagnostics returned for one compile. */
    public static final int MAX_DIAGNOSTICS = 50;

    private XsltErrors() {
    }

    /**
     * @param errors       what the compiler reported (errors and warnings)
     * @param mainSystemId the main stylesheet's URI; nullable (then every error counts as the main stylesheet's)
     * @param texts        the text of a module by its URI ({@code null} URI = the main stylesheet); may return
     *                     {@code null} when the text is not known
     */
    public static List<XsltDiagnostic> diagnose(List<XmlProcessingError> errors, String mainSystemId,
                                                Function<String, String> texts) {
        List<XsltDiagnostic> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (XmlProcessingError error : errors) {
            if (out.size() >= MAX_DIAGNOSTICS) {
                break;
            }
            XsltDiagnostic d = diagnose(error, mainSystemId, texts);
            if (seen.add(d.code() + "|" + d.systemId() + "|" + d.offset() + "|" + d.line() + "|" + d.message())) {
                out.add(d);
            }
        }
        return out;
    }

    /** One reported error or warning as a diagnostic. */
    public static XsltDiagnostic diagnose(XmlProcessingError error, String mainSystemId,
                                          Function<String, String> texts) {
        String code = error.getErrorCode() == null ? null : error.getErrorCode().getLocalName();
        String raw = error.getMessage() == null ? "" : error.getMessage();
        Location loc = error.getLocation();
        String systemId = loc == null || loc.getSystemId() == null || loc.getSystemId().isBlank()
                ? mainSystemId : loc.getSystemId();
        boolean main = systemId == null || mainSystemId == null || sameUri(systemId, mainSystemId);
        String module = main ? null : fileName(systemId);
        String text = texts == null ? null : texts.apply(main ? mainSystemId : systemId);

        if (loc instanceof XPathParser.NestedLocation nested
                && nested.getContainingLocation() instanceof AttributeLocation attribute) {
            int[] value = text == null ? null : attributeValue(text, attribute);
            if (value != null) {
                String expression = text.substring(value[0], value[1]);
                // Saxon's column counts from the expression's start when it gives no line inside it
                int[] inner = nested.getLocalLineNumber() > 0
                        ? new int[]{nested.getLocalLineNumber() + 1, nested.getColumnNumber() + 1}
                        : position(expression, Math.min(Math.max(0, nested.getColumnNumber()), expression.length()));
                QueryError q = QueryErrors.located(code, inner[0], inner[1], raw, expression, QueryLanguage.XPATH);
                int offset = q.offset() >= 0 ? value[0] + q.offset() : value[0];
                int length = q.offset() >= 0 ? q.length() : Math.max(1, value[1] - value[0]);
                List<QueryError.Suggestion> shifted = q.suggestions().stream()
                        .map(s -> new QueryError.Suggestion(s.label(), value[0] + s.start(), value[0] + s.end(),
                                s.replacement()))
                        .toList();
                return at(code, error.isWarning(), q.message(), systemId, module, text, offset, length,
                        hint(code, q), shifted);
            }
            QueryError q = QueryErrors.located(code, -1, -1, raw, "", QueryLanguage.XPATH);
            return new XsltDiagnostic(code, error.isWarning(), q.message(), systemId, module,
                    attribute.getLineNumber(), -1, -1, 0, hint(code, q), List.of());
        }

        QueryError q = QueryErrors.located(code, -1, -1, raw, "", QueryLanguage.XPATH);
        int line = loc == null ? -1 : loc.getLineNumber();
        int column = loc == null ? -1 : loc.getColumnNumber();
        if (text != null && line > 0) {
            int[] tag = startTag(text, line, column);
            if (tag != null) {
                int[] range = loc instanceof AttributeLocation attribute ? attributeName(text, tag, attribute) : null;
                if (range == null) {
                    range = elementName(text, tag);
                }
                return at(code, error.isWarning(), q.message(), systemId, module, text, range[0],
                        Math.max(1, range[1] - range[0]), hint(code, q), List.of());
            }
        }
        return new XsltDiagnostic(code, error.isWarning(), q.message(), systemId, module, line, -1, -1, 0,
                hint(code, q), List.of());
    }

    private static String hint(String code, QueryError q) {
        return q.hint() != null ? q.hint() : QueryErrorHints.hintFor(code, q.message());
    }

    private static XsltDiagnostic at(String code, boolean warning, String message, String systemId, String module,
                                     String text, int offset, int length, String hint,
                                     List<QueryError.Suggestion> suggestions) {
        int[] pos = position(text, offset);
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int lineEnd = text.indexOf('\n', offset);
        String sourceLine = text.substring(lineStart, lineEnd < 0 ? text.length() : lineEnd).stripTrailing();
        return new XsltDiagnostic(code, warning, message, systemId, module, pos[0], pos[1], offset, length, hint,
                suggestions, sourceLine);
    }

    // ----- locating in the module's text ------------------------------------------------------------------

    /**
     * The start tag Saxon means by {@code line}/{@code column}: the column is the one after the tag's {@code >};
     * without a column, the first tag starting on that line.
     *
     * @return {@code [start, end)} of the tag ({@code <} to after {@code >}), or {@code null}
     */
    static int[] startTag(String text, int line, int column) {
        int lineStart = offsetOf(text, line, 1);
        if (lineStart < 0) {
            return null;
        }
        if (column > 0) {
            int after = Math.min(text.length(), offsetOf(text, line, column));
            int close = text.lastIndexOf('>', Math.max(0, after - 1));
            int open = close < 0 ? -1 : text.lastIndexOf('<', close);
            if (open >= 0) {
                return new int[]{open, close + 1};
            }
        }
        int open = text.indexOf('<', lineStart);
        int close = open < 0 ? -1 : text.indexOf('>', open);
        return open < 0 || close < 0 ? null : new int[]{open, close + 1};
    }

    /** @return {@code [start, end)} of the value of {@code attribute} in the start tag Saxon names, or {@code null} */
    static int[] attributeValue(String text, AttributeLocation attribute) {
        int[] tag = startTag(text, attribute.getLineNumber(), attribute.getColumnNumber());
        if (tag == null) {
            return null;
        }
        Matcher m = attributePattern(attribute).matcher(text).region(tag[0], tag[1]);
        if (!m.find()) {
            return null;
        }
        char quote = text.charAt(m.end() - 1);
        int end = text.indexOf(quote, m.end());
        return end < 0 || end > tag[1] ? null : new int[]{m.end(), end};
    }

    private static int[] attributeName(String text, int[] tag, AttributeLocation attribute) {
        Matcher m = attributePattern(attribute).matcher(text).region(tag[0], tag[1]);
        return m.find() ? new int[]{m.start(1), m.end(1)} : null;
    }

    private static Pattern attributePattern(AttributeLocation attribute) {
        String name = attribute.getAttributeName().getDisplayName();
        return Pattern.compile("(?<![\\w.:-])(" + Pattern.quote(name) + ")\\s*=\\s*[\"']");
    }

    private static int[] elementName(String text, int[] tag) {
        int start = tag[0] + 1;
        int end = start;
        while (end < tag[1] && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != '>'
                && text.charAt(end) != '/') {
            end++;
        }
        return end > start ? new int[]{start, end} : new int[]{tag[0], tag[1]};
    }

    /** @return the offset of 1-based {@code line}/{@code column} (clamped to the line), or -1 for a missing line */
    static int offsetOf(String text, int line, int column) {
        int offset = 0;
        for (int l = 1; l < line; l++) {
            int nl = text.indexOf('\n', offset);
            if (nl < 0) {
                return -1;
            }
            offset = nl + 1;
        }
        int lineEnd = text.indexOf('\n', offset);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        return Math.min(lineEnd, offset + Math.max(0, column - 1));
    }

    /** @return 1-based {line, column} of {@code offset} */
    static int[] position(String text, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < Math.min(offset, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new int[]{line, offset - lineStart + 1};
    }

    static boolean sameUri(String a, String b) {
        try {
            return URI.create(a).normalize().equals(URI.create(b).normalize());
        } catch (IllegalArgumentException e) {
            return a.equals(b);
        }
    }

    static String fileName(String systemId) {
        int slash = Math.max(systemId.lastIndexOf('/'), systemId.lastIndexOf('\\'));
        return slash >= 0 ? systemId.substring(slash + 1) : systemId;
    }
}
