package org.fxt.freexmltoolkit.controls.shared.utilities;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the XML element that encloses a caret position in raw XML text, without
 * needing a parsed model.
 *
 * <p>A single forward scan builds a lightweight element tree (skipping comments,
 * CDATA, processing instructions and declarations so markup-looking characters
 * inside them are never mistaken for tags, and honouring quoted attribute values
 * and self-closing tags). The innermost element whose source range contains the
 * caret yields both its verbatim XML fragment and a positional XPath
 * (e.g. {@code /root/items/item[2]/name}) computed the same way as
 * {@link org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath}.
 *
 * <p>This is the text-mode counterpart used by the editor's "Copy Node" / "Copy
 * XPath" actions; it deliberately does not touch the IntelliSense
 * {@code ContextAnalyzer}, whose name-based XPath is needed for schema matching.
 */
public final class XmlElementAtCaret {

    private XmlElementAtCaret() {
    }

    /** The element at the caret: its verbatim XML and its positional XPath. */
    public record Result(String xml, String xpath) {
    }

    /**
     * @param xml   the full XML document text
     * @param caret a caret offset into {@code xml}
     * @return the innermost element containing {@code caret}, or empty if the caret
     *         is not inside any element (e.g. empty text or only the prolog)
     */
    public static Optional<Result> at(String xml, int caret) {
        if (xml == null || xml.isEmpty()) {
            return Optional.empty();
        }
        int pos = Math.max(0, Math.min(caret, xml.length()));

        List<El> all = scan(xml);

        // Innermost containing element = the one with the latest start among those
        // whose [start, end] range contains the caret (sibling ranges never overlap).
        El best = null;
        for (El el : all) {
            if (el.start <= pos && pos <= el.end && (best == null || el.start > best.start)) {
                best = el;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(new Result(xml.substring(best.start, best.end), xpathOf(best)));
    }

    // ---- scanning -----------------------------------------------------------

    private static final class El {
        final String name;          // local name (prefix stripped), for XPath steps
        final int start;            // offset of '<'
        int end;                    // offset just past the element's last '>'
        El parent;
        final List<El> children = new ArrayList<>();

        El(String name, int start) {
            this.name = name;
            this.start = start;
        }
    }

    private static List<El> scan(String text) {
        List<El> all = new ArrayList<>();
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
                // DOCTYPE or other declaration — skip to its '>'.
                int e = text.indexOf('>', lt + 2);
                i = e < 0 ? n : e + 1;
            } else if (text.startsWith("</", lt)) {
                int gt = text.indexOf('>', lt + 2);
                if (gt < 0) {
                    break;
                }
                if (!open.isEmpty()) {
                    open.pop().end = gt + 1;
                }
                i = gt + 1;
            } else {
                int gt = tagEnd(text, lt);
                if (gt < 0) {
                    break;
                }
                boolean selfClose = gt > lt + 1 && text.charAt(gt - 1) == '/';
                El el = new El(localName(text, lt + 1), lt);
                el.parent = open.peek();
                if (el.parent != null) {
                    el.parent.children.add(el);
                }
                all.add(el);
                if (selfClose) {
                    el.end = gt + 1;
                } else {
                    open.push(el);
                }
                i = gt + 1;
            }
        }
        // Unclosed elements (malformed / mid-edit) extend to the end of the text.
        for (El el : open) {
            el.end = n;
        }
        return all;
    }

    /** Finds the index of the '>' that closes the tag starting at {@code lt}, skipping quoted values. */
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

    /** Reads the element's local name starting at {@code from} (just after '<'), stripping any prefix. */
    private static String localName(String text, int from) {
        int i = from;
        int n = text.length();
        int nameEnd = i;
        while (nameEnd < n) {
            char c = text.charAt(nameEnd);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '>' || c == '/') {
                break;
            }
            nameEnd++;
        }
        String raw = text.substring(i, nameEnd);
        int colon = raw.indexOf(':');
        return colon >= 0 ? raw.substring(colon + 1) : raw;
    }

    // ---- positional XPath (mirrors XmlNodeXPath) ----------------------------

    private static String xpathOf(El node) {
        StringBuilder xpath = new StringBuilder();
        for (El current = node; current != null; current = current.parent) {
            xpath.insert(0, step(current));
        }
        return xpath.length() > 0 ? xpath.toString() : "/";
    }

    private static String step(El element) {
        List<El> siblings = element.parent != null ? element.parent.children : null;
        if (siblings == null) {
            return "/" + element.name;
        }
        int position = 1;
        int sameNameCount = 0;
        for (El sibling : siblings) {
            if (sibling.name.equals(element.name)) {
                sameNameCount++;
                if (sibling == element) {
                    position = sameNameCount;
                }
            }
        }
        return sameNameCount > 1 ? "/" + element.name + "[" + position + "]" : "/" + element.name;
    }
}
