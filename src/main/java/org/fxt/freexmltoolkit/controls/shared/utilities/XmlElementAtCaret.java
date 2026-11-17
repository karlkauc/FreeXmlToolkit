package org.fxt.freexmltoolkit.controls.shared.utilities;

import java.util.ArrayDeque;
import java.util.ArrayList;
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

    /**
     * The element at the caret: its verbatim XML, its positional XPath, and the offset of the
     * {@code >} that closes its start tag ({@code -1} when unknown).
     */
    public record Result(String xml, String xpath, int startTagEnd) {

        public Result(String xml, String xpath) {
            this(xml, xpath, -1);
        }
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
        return Optional.of(new Result(xml.substring(best.start, best.end), xpathOf(best), best.startTagEnd));
    }

    /** The source range of an element: {@code start} = its {@code <}, {@code end} just past its last {@code >}. */
    public record Range(int start, int end, int startTagEnd) {
    }

    /**
     * Finds an element by its child-element index path from the root element (the inverse of
     * mapping a caret to a model path): {@code path} counts child elements, leaving out those whose
     * local name {@code skip} accepts (e.g. {@code xs:annotation} for an XSD model without them).
     *
     * @param xml  the full XML document text
     * @param path child-element indexes from the root element (empty = the root element)
     * @param skip local names not counted, or {@code null}
     * @return the element's range, or empty when the path does not exist in the text
     */
    public static Optional<Range> rangeOf(String xml, List<Integer> path,
                                          java.util.function.Predicate<String> skip) {
        if (xml == null || xml.isEmpty() || path == null) {
            return Optional.empty();
        }
        El current = null;
        for (El el : scan(xml)) {
            if (el.parent == null) {
                current = el; // the root element: the first top-level one
                break;
            }
        }
        if (current == null) {
            return Optional.empty();
        }
        for (int index : path) {
            El next = null;
            int i = 0;
            for (El child : current.children) {
                if (skip != null && skip.test(child.name)) {
                    continue;
                }
                if (i++ == index) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return Optional.empty();
            }
            current = next;
        }
        return Optional.of(new Range(current.start, current.end, current.startTagEnd));
    }

    // ---- scanning -----------------------------------------------------------

    private static final class El {
        final String name;          // local name (prefix stripped), for XPath steps
        final int start;            // offset of '<'
        int end;                    // offset just past the element's last '>'
        int startTagEnd = -1;       // offset of the '>' closing the start tag
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
                el.startTagEnd = gt;
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

    // ---- local lookups (documents too large for a full scan) -----------------

    /**
     * The start of the start tag of an element named {@code qname} that ends at or before
     * {@code before} - the last {@code <qname} followed by whitespace, {@code >} or {@code /}.
     *
     * @param xml    the document text
     * @param before an offset (e.g. the end of the line a parser reported for the element)
     * @param qname  the element's qualified name
     * @param limit  how far back to look at most
     * @return the offset of the {@code <}, or -1
     */
    public static int startTagBefore(String xml, int before, String qname, int limit) {
        if (xml == null || qname == null || qname.isEmpty()) {
            return -1;
        }
        String needle = "<" + qname;
        int floor = Math.max(0, before - limit);
        int from = Math.min(before, xml.length() - 1);
        while (from >= floor) {
            int idx = xml.lastIndexOf(needle, from);
            if (idx < floor) {
                return -1;
            }
            int after = idx + needle.length();
            if (after < xml.length()) {
                char c = xml.charAt(after);
                if (Character.isWhitespace(c) || c == '>' || c == '/') {
                    return idx;
                }
            }
            from = idx - 1;
        }
        return -1;
    }

    /**
     * The end of the element whose start tag begins at {@code start}: just past its end tag (or its
     * {@code />}), skipping comments, CDATA sections, processing instructions and quoted attribute
     * values. Reads at most {@code limit} characters.
     *
     * @param xml   the document text
     * @param start the offset of the element's {@code <}
     * @param limit how many characters to read at most
     * @return the offset just past the element, or -1 when it does not end within the limit
     */
    public static int elementEndFrom(String xml, int start, int limit) {
        if (xml == null || start < 0 || start >= xml.length() || xml.charAt(start) != '<') {
            return -1;
        }
        int n = (int) Math.min(xml.length(), (long) start + limit);
        int depth = 0;
        int i = start;
        while (i < n) {
            int lt = xml.indexOf('<', i);
            if (lt < 0 || lt >= n) {
                return -1;
            }
            int skipTo = -1;
            if (xml.startsWith("<!--", lt)) {
                skipTo = endOf(xml, "-->", lt + 4);
            } else if (xml.startsWith("<![CDATA[", lt)) {
                skipTo = endOf(xml, "]]>", lt + 9);
            } else if (xml.startsWith("<?", lt)) {
                skipTo = endOf(xml, "?>", lt + 2);
            } else if (xml.startsWith("<!", lt)) {
                skipTo = endOf(xml, ">", lt + 2);
            }
            if (skipTo >= 0 || xml.startsWith("<!", lt) || xml.startsWith("<?", lt)) {
                if (skipTo < 0) {
                    return -1;
                }
                i = skipTo;
                continue;
            }
            int gt = tagEnd(xml, lt, n);
            if (gt < 0) {
                return -1;
            }
            if (lt + 1 < xml.length() && xml.charAt(lt + 1) == '/') {
                depth--;
                if (depth == 0) {
                    return gt + 1;
                }
            } else if (xml.charAt(gt - 1) == '/') {
                if (depth == 0) {
                    return gt + 1;
                }
            } else {
                depth++;
            }
            i = gt + 1;
        }
        return -1;
    }

    /** @return the offset just past {@code end} searched from {@code from}, or -1 */
    private static int endOf(String xml, String end, int from) {
        int idx = xml.indexOf(end, from);
        return idx < 0 ? -1 : idx + end.length();
    }

    /** @return the offset of the {@code >} closing the tag at {@code lt} (quoted values skipped), or -1 */
    private static int tagEnd(String xml, int lt, int n) {
        char quote = 0;
        for (int i = lt + 1; i < n; i++) {
            char c = xml.charAt(i);
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
}
