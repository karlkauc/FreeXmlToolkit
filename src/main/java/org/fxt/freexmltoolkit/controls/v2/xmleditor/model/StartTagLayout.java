package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * How a start tag was laid out in the source text: the whitespace before each attribute and before
 * the closing {@code >} / {@code />}. The serializer writes one space before every attribute; with a
 * layout it writes the source's whitespace instead, so attributes that were on lines of their own
 * stay there after an edit in the grid, the Tree or the Inspector.
 * <p>
 * Only start tags that differ from the serializer's form get a layout ({@link #capture}), so most
 * elements of a large document carry none. A layout is immutable and shared by copies.
 */
public final class StartTagLayout {

    private final String[] names;
    private final String[] before;
    private final String beforeClose;
    /** The whitespace that started the tag's line, or {@code null} when text preceded the tag on it. */
    private final String lineIndent;

    private StartTagLayout(String[] names, String[] before, String beforeClose, String lineIndent) {
        this.names = names;
        this.before = before;
        this.beforeClose = beforeClose;
        this.lineIndent = lineIndent;
    }

    /**
     * @param attribute   an attribute name
     * @param indentWidth the indentation the serializer writes for the tag's line, or -1 when it
     *                    writes none (mixed content: the source's layout is kept as it was)
     * @return the whitespace to write before the attribute
     */
    public String before(String attribute, int indentWidth) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(attribute)) {
                return reindent(before[i], indentWidth);
            }
        }
        return " "; // added after parsing
    }

    /** @return the whitespace to write before {@code >} or {@code />} (see {@link #before}) */
    public String beforeClose(int indentWidth) {
        return reindent(beforeClose, indentWidth);
    }

    /**
     * Whitespace with a line break keeps its indentation relative to the tag's line, so a tag that
     * moved to another depth carries its attributes along.
     */
    private String reindent(String whitespace, int indentWidth) {
        int lastBreak = whitespace.lastIndexOf('\n');
        if (lastBreak < 0 || indentWidth < 0 || lineIndent == null) {
            return whitespace;
        }
        String tail = whitespace.substring(lastBreak + 1);
        if (!tail.startsWith(lineIndent)) {
            return whitespace;
        }
        return whitespace.substring(0, lastBreak + 1) + " ".repeat(indentWidth) + tail.substring(lineIndent.length());
    }

    // ==================== Capture ====================

    /**
     * Gives the elements of {@code document} the layout of their start tags in {@code text}, the
     * text the document was just parsed from. Start tags and elements are paired in document order;
     * at the first tag whose name differs from its element's (an entity that expanded into
     * elements, say) the pairing stops and the remaining elements keep the plain layout.
     * <p>
     * The document type declaration is taken over as written too ({@link XmlDocument#setDoctype}).
     */
    public static void capture(XmlDocument document, CharSequence text) {
        if (document == null || text == null) {
            return;
        }
        Deque<XmlNode> pending = new ArrayDeque<>();
        pushReversed(pending, document.getChildren());
        Scanner scanner = new Scanner(text);
        boolean first = true;
        while (scanner.nextStartTag()) {
            if (first) {
                first = false;
                takeDoctype(document, scanner);
            }
            XmlElement element = nextElement(pending);
            if (element == null || !element.getQualifiedName().equals(scanner.tagName)) {
                return;
            }
            if (scanner.irregular) {
                element.setStartTagLayout(new StartTagLayout(scanner.attributeNames.toArray(String[]::new),
                        scanner.whitespace.toArray(String[]::new), scanner.beforeClose, scanner.lineIndent));
            }
        }
        if (first) {
            takeDoctype(document, scanner);
        }
    }

    /**
     * Gives {@code document} the document type declaration of {@code prolog}, the start of the text
     * it was parsed from (a stream's first bytes). Taken only when the prolog reaches the root's
     * start tag, so a cut-off declaration is never kept.
     */
    public static void captureDoctype(XmlDocument document, CharSequence prolog) {
        if (document == null || prolog == null) {
            return;
        }
        Scanner scanner = new Scanner(prolog);
        if (scanner.nextStartTag()) {
            takeDoctype(document, scanner);
        }
    }

    /** The doctype as the source text has it (the DOM parser's is rebuilt from its parts). */
    private static void takeDoctype(XmlDocument document, Scanner scanner) {
        if (scanner.doctype != null) {
            document.setDoctype(scanner.doctype, scanner.doctypePosition);
        }
    }

    private static XmlElement nextElement(Deque<XmlNode> pending) {
        while (!pending.isEmpty()) {
            XmlNode node = pending.pop();
            if (node instanceof XmlElement element) {
                pushReversed(pending, element.getChildren());
                return element;
            }
        }
        return null;
    }

    private static void pushReversed(Deque<XmlNode> pending, List<XmlNode> children) {
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i) instanceof XmlElement) {
                pending.push(children.get(i));
            }
        }
    }

    /** Finds the start tags of a text in order, skipping comments, CDATA, instructions and the DOCTYPE (kept as text). */
    private static final class Scanner {

        private final CharSequence text;
        private final int length;
        private int pos;

        String tagName;
        final List<String> attributeNames = new ArrayList<>();
        final List<String> whitespace = new ArrayList<>();
        String beforeClose;
        String lineIndent;
        boolean irregular;
        /** The {@code <!DOCTYPE …>} before the first start tag, as written, or null. */
        String doctype;
        /** How many comments and processing instructions came before {@link #doctype}. */
        int doctypePosition;
        private int prologNodes;
        private boolean inProlog = true;

        Scanner(CharSequence text) {
            this.text = text;
            this.length = text.length();
        }

        boolean nextStartTag() {
            while (pos < length) {
                if (text.charAt(pos) != '<') {
                    pos++;
                    continue;
                }
                if (startsWith("<!--")) {
                    prologNodes++;
                    skipPast("-->");
                } else if (startsWith("<![CDATA[")) {
                    skipPast("]]>");
                } else if (startsWith("<?")) {
                    if (!isXmlDeclaration()) {
                        prologNodes++;
                    }
                    skipPast("?>");
                } else if (startsWith("<!")) {
                    int start = pos;
                    skipDeclaration();
                    if (inProlog && doctype == null && startsWith(start, "<!DOCTYPE")) {
                        doctype = text.subSequence(start, pos).toString();
                        doctypePosition = prologNodes;
                    }
                } else if (startsWith("</")) {
                    skipPast(">");
                } else {
                    inProlog = false;
                    return readStartTag();
                }
            }
            return false;
        }

        private boolean readStartTag() {
            lineIndent = indentBefore(pos);
            pos++; // '<'
            int nameStart = pos;
            while (pos < length && !isWhitespace(text.charAt(pos)) && text.charAt(pos) != '>' && text.charAt(pos) != '/') {
                pos++;
            }
            tagName = text.subSequence(nameStart, pos).toString();
            attributeNames.clear();
            whitespace.clear();
            irregular = false;
            while (pos < length) {
                int wsStart = pos;
                while (pos < length && isWhitespace(text.charAt(pos))) {
                    pos++;
                }
                String ws = text.subSequence(wsStart, pos).toString();
                if (pos >= length) {
                    return false;
                }
                char c = text.charAt(pos);
                if (c == '>' || c == '/') {
                    beforeClose = ws;
                    irregular |= !ws.isEmpty();
                    skipPast(">");
                    return true;
                }
                int attrStart = pos;
                while (pos < length && text.charAt(pos) != '=' && !isWhitespace(text.charAt(pos))
                        && text.charAt(pos) != '>') {
                    pos++;
                }
                attributeNames.add(text.subSequence(attrStart, pos).toString());
                whitespace.add(ws);
                irregular |= !" ".equals(ws);
                while (pos < length && text.charAt(pos) != '"' && text.charAt(pos) != '\'' && text.charAt(pos) != '>') {
                    pos++; // '=' with optional whitespace around it
                }
                if (pos < length && text.charAt(pos) != '>') {
                    char quote = text.charAt(pos++);
                    while (pos < length && text.charAt(pos) != quote) {
                        pos++;
                    }
                    pos++;
                }
            }
            return false;
        }

        /** @return the whitespace between the line start and {@code at}, or null when other text is there */
        private String indentBefore(int at) {
            int i = at;
            while (i > 0 && (text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '\t')) {
                i--;
            }
            return i == 0 || text.charAt(i - 1) == '\n' ? text.subSequence(i, at).toString() : null;
        }

        /** A {@code <!DOCTYPE …>} with an internal subset holds '>' and quoted text of its own. */
        private void skipDeclaration() {
            int depth = 0;
            pos += 2;
            while (pos < length) {
                char c = text.charAt(pos++);
                if (c == '"' || c == '\'') {
                    while (pos < length && text.charAt(pos) != c) {
                        pos++;
                    }
                    pos++;
                } else if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                } else if (c == '>' && depth <= 0) {
                    return;
                }
            }
        }

        /** {@code <?xml version=…?>}, not an instruction such as {@code <?xml-stylesheet …?>} */
        private boolean isXmlDeclaration() {
            return startsWith("<?xml") && pos + 5 < length
                    && (isWhitespace(text.charAt(pos + 5)) || text.charAt(pos + 5) == '?');
        }

        private boolean startsWith(String prefix) {
            return startsWith(pos, prefix);
        }

        private boolean startsWith(int at, String prefix) {
            if (at + prefix.length() > length) {
                return false;
            }
            for (int i = 0; i < prefix.length(); i++) {
                if (text.charAt(at + i) != prefix.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        private void skipPast(String end) {
            while (pos < length && !startsWith(end)) {
                pos++;
            }
            pos = Math.min(length, pos + end.length());
        }

        private static boolean isWhitespace(char c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\r';
        }
    }
}
