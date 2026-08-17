package org.fxt.freexmltoolkit.service.xmledit;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.service.SaxonXPathHelper;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator;
import org.fxt.freexmltoolkit.service.sqf.XmlNodeSpanLocator.Span;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.NodeMatch;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathEditException;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathQuery;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmValue;

/**
 * Turns located XPath matches into a formatting-preserving {@link EditPlan}.
 * The per-node-kind span logic mirrors the SQF quick-fix engine's replace/delete
 * semantics (whole-line removal for elements, leading-whitespace-inclusive
 * attribute spans) without touching the SQF classes themselves.
 */
public final class XPathReplacePlanner {

    private XPathReplacePlanner() {
    }

    /** What to do with each matched node. */
    public enum ReplaceMode {
        /** Set the element text / attribute value to a literal string. */
        SET_VALUE,
        /** Set the value to the string result of an XPath evaluated per match. */
        COMPUTE_VALUE,
        /** Remove the matched node entirely. */
        DELETE,
        /** Replace the matched element with an XML fragment. */
        REPLACE_FRAGMENT
    }

    /** The plan plus how many selected matches were dropped as descendants of other matches. */
    public record PlanResult(EditPlan plan, int subsumed) {
    }

    /**
     * Plans the edits for {@code selected} matches against the {@code xml} snapshot
     * they were located in.
     *
     * @param query    the original query (namespace bindings are reused for
     *                 {@link ReplaceMode#COMPUTE_VALUE} expressions)
     * @param argument the literal value, compute-XPath, or XML fragment —
     *                 ignored for {@link ReplaceMode#DELETE}
     */
    public static PlanResult plan(String xml, XPathQuery query, ReplaceMode mode, String argument,
                                  List<NodeMatch> selected) throws XPathEditException {
        List<NodeMatch> located = selected.stream().filter(NodeMatch::located).toList();
        int subsumed = 0;
        if (mode == ReplaceMode.DELETE || mode == ReplaceMode.REPLACE_FRAGMENT) {
            // A match inside another selected match would produce overlapping edits;
            // the ancestor's edit covers it.
            List<NodeMatch> top = dropContained(located);
            subsumed = located.size() - top.size();
            located = top;
        }
        if (mode == ReplaceMode.REPLACE_FRAGMENT) {
            requireWellFormedFragment(argument, query.namespaces());
        }
        XPathSelector computeSelector = null;
        if (mode == ReplaceMode.COMPUTE_VALUE) {
            try {
                computeSelector = XPathMatchLocator.newCompiler(query.namespaces())
                        .compile(argument).load();
            } catch (SaxonApiException e) {
                throw new XPathEditException("Invalid value expression: " + e.getMessage(), e);
            }
        }
        List<TextEdit> edits = new ArrayList<>();
        for (NodeMatch match : located) {
            switch (mode) {
                case DELETE -> edits.add(deleteEdit(xml, match));
                case SET_VALUE -> edits.add(valueEdit(match, argument));
                case COMPUTE_VALUE -> edits.add(valueEdit(match,
                        computeValue(computeSelector, match)));
                case REPLACE_FRAGMENT -> edits.add(fragmentEdit(xml, match, argument));
            }
        }
        try {
            return new PlanResult(new EditPlan(edits), subsumed);
        } catch (IllegalArgumentException e) {
            throw new XPathEditException("Matches overlap — replace them one by one: "
                    + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------

    private static TextEdit deleteEdit(String xml, NodeMatch match) throws XPathEditException {
        Span span = match.span();
        if (match.kind() == XdmNodeKind.ELEMENT) {
            span = expandToWholeLine(xml, span);
        }
        return new TextEdit(span.start(), span.end(), "");
    }

    private static TextEdit valueEdit(NodeMatch match, String value)
            throws XPathEditException {
        switch (match.kind()) {
            case ELEMENT -> {
                Span span = match.span();
                String raw = match.matchedText();
                if (match.contentStart() < 0) {
                    // self-closing: <name attrs/> → <name attrs>value</name>
                    String rawName = tagName(raw);
                    String opened = raw.substring(0, raw.length() - 2).stripTrailing() + ">";
                    return new TextEdit(span.start(), span.end(),
                            opened + escapeText(value) + "</" + rawName + ">");
                }
                return new TextEdit(match.contentStart(), match.contentEnd(), escapeText(value));
            }
            case ATTRIBUTE -> {
                // span covers ` name="value"` — narrow to the quoted value
                String raw = match.matchedText();
                int eq = raw.indexOf('=');
                int quoteStart = -1;
                for (int i = eq + 1; i < raw.length(); i++) {
                    char c = raw.charAt(i);
                    if (c == '"' || c == '\'') {
                        quoteStart = i;
                        break;
                    }
                }
                if (eq < 0 || quoteStart < 0 || raw.length() - 1 <= quoteStart) {
                    throw new XPathEditException("Cannot locate the attribute value in " + preview(raw));
                }
                return new TextEdit(match.span().start() + quoteStart + 1,
                        match.span().end() - 1, escapeAttributeValue(value));
            }
            case TEXT -> {
                return new TextEdit(match.span().start(), match.span().end(), escapeText(value));
            }
            default -> throw new XPathEditException(
                    "Cannot set a value on " + match.kind() + " nodes");
        }
    }

    private static TextEdit fragmentEdit(String xml, NodeMatch match, String fragment)
            throws XPathEditException {
        if (match.kind() != XdmNodeKind.ELEMENT) {
            throw new XPathEditException(
                    "Replace-with-fragment works on elements only, not " + match.kind() + " nodes");
        }
        Span span = match.span();
        String indented = fragment.strip();
        if (indented.contains("\n")) {
            String indent = XmlNodeSpanLocator.indentationAt(xml, span.start());
            indented = indented.lines()
                    .map(String::strip)
                    .collect(Collectors.joining("\n" + indent));
        }
        return new TextEdit(span.start(), span.end(), indented);
    }

    private static String computeValue(XPathSelector selector, NodeMatch match)
            throws XPathEditException {
        try {
            selector.setContextItem(match.node());
            XdmValue result = selector.evaluate();
            StringBuilder sb = new StringBuilder();
            for (XdmItem item : result) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(item.getStringValue());
            }
            return sb.toString();
        } catch (SaxonApiException e) {
            throw new XPathEditException("Value expression failed at line " + match.line()
                    + ": " + e.getMessage(), e);
        }
    }

    /** Fragment must parse; user namespace prefixes are bound on the check wrapper. */
    private static void requireWellFormedFragment(String fragment, Map<String, String> namespaces)
            throws XPathEditException {
        if (fragment == null || fragment.isBlank()) {
            throw new XPathEditException("Enter the XML fragment to replace the matches with");
        }
        StringBuilder wrapper = new StringBuilder("<fxt-fragment-check");
        if (namespaces != null) {
            namespaces.forEach((prefix, uri) -> {
                if (uri == null || uri.isBlank()) {
                    return;
                }
                wrapper.append(prefix == null || prefix.isEmpty()
                        ? " xmlns=\"" + uri + "\""
                        : " xmlns:" + prefix + "=\"" + uri + "\"");
            });
        }
        wrapper.append('>').append(fragment).append("</fxt-fragment-check>");
        try {
            SaxonXPathHelper.getProcessor().newDocumentBuilder()
                    .build(new StreamSource(new StringReader(wrapper.toString())));
        } catch (SaxonApiException e) {
            throw new XPathEditException("The replacement fragment is not well-formed XML: "
                    + e.getMessage(), e);
        }
    }

    /** Drops matches whose span lies inside another selected match's span. */
    private static List<NodeMatch> dropContained(List<NodeMatch> matches) {
        List<NodeMatch> kept = new ArrayList<>();
        for (NodeMatch candidate : matches) {
            boolean contained = matches.stream().anyMatch(other -> other != candidate
                    && other.span().start() <= candidate.span().start()
                    && candidate.span().end() <= other.span().end()
                    && (other.span().start() != candidate.span().start()
                        || other.span().end() != candidate.span().end()));
            if (!contained) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    /** Whole-line expansion for element deletion (mirrors the SQF engine). */
    private static Span expandToWholeLine(String xml, Span span) {
        int lineStart = XmlNodeSpanLocator.lineStart(xml, span.start());
        if (!xml.substring(lineStart, span.start()).isBlank()) {
            return span;
        }
        int i = span.end();
        while (i < xml.length() && (xml.charAt(i) == ' ' || xml.charAt(i) == '\t')) {
            i++;
        }
        if (i < xml.length() && xml.charAt(i) == '\r') {
            i++;
        }
        if (i < xml.length() && xml.charAt(i) == '\n') {
            return new Span(lineStart, i + 1);
        }
        return span;
    }

    /** @return the raw (possibly prefixed) tag name of a start tag's text. */
    private static String tagName(String rawTag) {
        int i = 1;
        while (i < rawTag.length()) {
            char c = rawTag.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '/' || c == '>') {
                break;
            }
            i++;
        }
        return rawTag.substring(1, i);
    }

    private static String escapeText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;");
    }

    private static String escapeAttributeValue(String value) {
        return escapeText(value).replace("\"", "&quot;");
    }

    private static String preview(String raw) {
        return raw.length() <= 60 ? raw : raw.substring(0, 60) + "…";
    }
}
