package org.fxt.freexmltoolkit.service.sqf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

/**
 * Maps XML nodes to their exact character spans in the raw document text, so quick
 * fixes can be applied as minimal text edits that preserve the document's original
 * formatting. Elements are addressed by a positional path of (local name, index)
 * steps — derived from a Saxon node via {@link #pathOf} and resolved against the
 * text by a forward scan (the same tokenization approach as
 * {@code XmlElementAtCaret}: comments, CDATA, processing instructions and quoted
 * attribute values are never mistaken for tags). Saxon's column numbers are
 * deliberately not used — they are unreliable.
 */
public final class XmlNodeSpanLocator {

    private XmlNodeSpanLocator() {
    }

    /** A half-open character range {@code [start, end)} in the document text. */
    public record Span(int start, int end) {
    }

    /** One step of a positional path: the n-th child element with this local name. */
    public record Step(String localName, int position) {
    }

    /**
     * An element's text layout: its full span, its start tag end, its content range
     * and the spans of its child elements.
     *
     * @param span              the whole element including tags
     * @param startTagEnd       offset of the {@code '>'} closing the start tag
     * @param contentStart      first offset of the content, or {@code -1} if self-closing
     * @param contentEnd        offset of the closing tag's {@code '<'}, or {@code -1}
     * @param childElementSpans spans of the direct child elements, in order
     */
    public record ElementRegion(Span span, int startTagEnd, int contentStart, int contentEnd,
                                List<Span> childElementSpans) {

        /** @return {@code true} for {@code <name/>}-style elements without content */
        public boolean selfClosing() {
            return contentStart < 0;
        }
    }

    // ---- path derivation from Saxon nodes -----------------------------------

    /**
     * @param element an element node of a parsed document
     * @return the positional path from the document root to the element (local
     *         names, 1-based index among same-local-name siblings)
     */
    public static List<Step> pathOf(XdmNode element) {
        List<Step> steps = new ArrayList<>();
        XdmNode current = element;
        while (current != null && current.getNodeKind() == XdmNodeKind.ELEMENT) {
            String localName = current.getNodeName().getLocalName();
            int position = 1;
            for (XdmSequenceIterator<XdmNode> it = current.axisIterator(Axis.PRECEDING_SIBLING); it.hasNext(); ) {
                XdmNode sibling = it.next();
                if (sibling.getNodeKind() == XdmNodeKind.ELEMENT
                        && localName.equals(sibling.getNodeName().getLocalName())) {
                    position++;
                }
            }
            steps.add(0, new Step(localName, position));
            current = current.getParent();
        }
        return steps;
    }

    /** @return the 0-based index of {@code textNode} among its parent's text children */
    public static int textNodeIndex(XdmNode textNode) {
        int index = 0;
        for (XdmSequenceIterator<XdmNode> it = textNode.axisIterator(Axis.PRECEDING_SIBLING); it.hasNext(); ) {
            if (it.next().getNodeKind() == XdmNodeKind.TEXT) {
                index++;
            }
        }
        return index;
    }

    // ---- span resolution ----------------------------------------------------

    /** Resolves a positional path to the element's text layout. */
    public static Optional<ElementRegion> elementRegion(String xml, List<Step> path) {
        if (xml == null || path == null || path.isEmpty()) {
            return Optional.empty();
        }
        List<El> roots = scan(xml);
        List<El> level = roots;
        El current = null;
        for (Step step : path) {
            current = nth(level, step);
            if (current == null) {
                return Optional.empty();
            }
            level = current.children;
        }
        List<Span> childSpans = new ArrayList<>();
        for (El child : current.children) {
            childSpans.add(new Span(child.start, child.end));
        }
        return Optional.of(new ElementRegion(new Span(current.start, current.end),
                current.startTagEnd, current.contentStart, current.contentEnd, childSpans));
    }

    private static El nth(List<El> level, Step step) {
        int seen = 0;
        for (El el : level) {
            if (el.name.equals(step.localName()) && ++seen == step.position()) {
                return el;
            }
        }
        return null;
    }

    /**
     * @return the span of the attribute inside the element's start tag, including
     *         the whitespace before its name (so deleting the span removes the
     *         attribute cleanly), or empty when the attribute is not present
     */
    public static Optional<Span> attributeSpan(String xml, ElementRegion region, String attrName) {
        int tagStart = region.span().start();
        int tagEnd = region.startTagEnd();
        int i = tagStart + 1;
        // skip element name
        while (i < tagEnd && !isWs(xml.charAt(i)) && xml.charAt(i) != '>' && xml.charAt(i) != '/') {
            i++;
        }
        while (i < tagEnd) {
            int wsStart = i;
            while (i < tagEnd && isWs(xml.charAt(i))) {
                i++;
            }
            if (i >= tagEnd || xml.charAt(i) == '/' || xml.charAt(i) == '>') {
                return Optional.empty();
            }
            int nameStart = i;
            while (i < tagEnd && xml.charAt(i) != '=' && !isWs(xml.charAt(i))) {
                i++;
            }
            String name = xml.substring(nameStart, i);
            while (i < tagEnd && (isWs(xml.charAt(i)) || xml.charAt(i) == '=')) {
                i++;
            }
            if (i >= tagEnd) {
                return Optional.empty();
            }
            char quote = xml.charAt(i);
            if (quote != '"' && quote != '\'') {
                return Optional.empty(); // malformed
            }
            int valueEnd = xml.indexOf(quote, i + 1);
            if (valueEnd < 0 || valueEnd > tagEnd) {
                return Optional.empty();
            }
            i = valueEnd + 1;
            String local = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
            if (local.equals(attrName) || name.equals(attrName)) {
                return Optional.of(new Span(wsStart, i));
            }
        }
        return Optional.empty();
    }

    /**
     * @return the pure-text gaps between the element's child elements (its text
     *         segments), in order; gaps containing markup (comments, CDATA, PIs)
     *         are excluded
     */
    public static List<Span> textSegmentSpans(String xml, ElementRegion region) {
        List<Span> segments = new ArrayList<>();
        if (region.selfClosing()) {
            return segments;
        }
        int cursor = region.contentStart();
        List<Span> children = new ArrayList<>(region.childElementSpans());
        children.add(new Span(region.contentEnd(), region.contentEnd())); // sentinel
        for (Span child : children) {
            if (child.start() > cursor) {
                String gap = xml.substring(cursor, child.start());
                if (!gap.isEmpty() && gap.indexOf('<') < 0) {
                    segments.add(new Span(cursor, child.start()));
                }
            }
            cursor = Math.max(cursor, child.end());
        }
        return segments;
    }

    // ---- line helpers -------------------------------------------------------

    /** @return the offset of the first character of the line containing {@code offset} */
    public static int lineStart(String xml, int offset) {
        int i = Math.min(offset, xml.length());
        while (i > 0 && xml.charAt(i - 1) != '\n') {
            i--;
        }
        return i;
    }

    /** @return the leading whitespace of the line containing {@code offset} */
    public static String indentationAt(String xml, int offset) {
        int start = lineStart(xml, offset);
        int i = start;
        while (i < xml.length() && (xml.charAt(i) == ' ' || xml.charAt(i) == '\t')) {
            i++;
        }
        return xml.substring(start, i);
    }

    private static boolean isWs(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    // ---- scanning (tokenization mirrors XmlElementAtCaret) ------------------

    private static final class El {
        final String name;
        final int start;
        int startTagEnd = -1;
        int end;
        int contentStart = -1;
        int contentEnd = -1;
        final List<El> children = new ArrayList<>();

        El(String name, int start) {
            this.name = name;
            this.start = start;
        }
    }

    private static List<El> scan(String text) {
        List<El> roots = new ArrayList<>();
        Deque<El> open = new ArrayDeque<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            int lt = text.indexOf('<', i);
            if (lt < 0) {
                break;
            }
            if (text.startsWith("<!--", lt)) {
                int e = text.indexOf("-->", lt + 4);
                i = e < 0 ? n : e + 3;
            } else if (text.startsWith("<![CDATA[", lt)) {
                int e = text.indexOf("]]>", lt + 9);
                i = e < 0 ? n : e + 3;
            } else if (text.startsWith("<?", lt)) {
                int e = text.indexOf("?>", lt + 2);
                i = e < 0 ? n : e + 2;
            } else if (text.startsWith("<!", lt)) {
                int e = text.indexOf('>', lt + 2);
                i = e < 0 ? n : e + 1;
            } else if (text.startsWith("</", lt)) {
                int gt = text.indexOf('>', lt + 2);
                if (gt < 0) {
                    break;
                }
                if (!open.isEmpty()) {
                    El closed = open.pop();
                    closed.contentEnd = lt;
                    closed.end = gt + 1;
                }
                i = gt + 1;
            } else {
                int gt = tagEnd(text, lt);
                if (gt < 0) {
                    break;
                }
                boolean selfClose = gt > lt + 1 && text.charAt(gt - 1) == '/';
                El el = new El(localName(text, lt + 1), lt);
                el.startTagEnd = gt;
                El parent = open.peek();
                if (parent != null) {
                    parent.children.add(el);
                } else {
                    roots.add(el);
                }
                if (selfClose) {
                    el.end = gt + 1;
                } else {
                    el.contentStart = gt + 1;
                    open.push(el);
                }
                i = gt + 1;
            }
        }
        for (El el : open) { // unclosed (malformed / mid-edit) extend to EOF
            el.end = n;
            el.contentEnd = n;
        }
        return roots;
    }

    private static int tagEnd(String text, int lt) {
        char quote = 0;
        for (int i = lt + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static String localName(String text, int from) {
        int nameEnd = from;
        int n = text.length();
        while (nameEnd < n) {
            char c = text.charAt(nameEnd);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') {
                break;
            }
            nameEnd++;
        }
        String raw = text.substring(from, nameEnd);
        int colon = raw.indexOf(':');
        return colon >= 0 ? raw.substring(colon + 1) : raw;
    }
}
