package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finds the foldable regions of an XML text in one pass: every element whose start tag begins on one line
 * and whose end tag is at least {@code minLines} lines further down.
 * <p>
 * A character scanner instead of per-line patterns: a start tag spread over several lines (attributes on
 * their own lines, e.g. {@code <FundsXML4 xmlns:xsi="…"} ⏎ {@code xsi:noNamespaceSchemaLocation="…">}) counts
 * from the line of its {@code <}; comments, CDATA sections, processing instructions and the DOCTYPE are
 * skipped (tags inside them do not count); a {@code >} or {@code />} inside a quoted attribute value does not
 * end the tag; a self-closing element opens no region, also when other tags share its line.
 * <p>
 * Tolerant of documents being typed: an end tag without a matching start tag is ignored, start tags left
 * open by an end tag further out end without a region, and elements still open at the end run to the last
 * line. A {@code <} inside a quoted value (never valid XML) ends the broken tag there, so an unfinished
 * quote does not swallow the rest of the document.
 */
final class XmlFoldScanner {

    private XmlFoldScanner() {
    }

    /** An element whose start tag has been read and whose end tag is still to come. */
    private record Open(String name, int line, int lineStart) {
    }

    /**
     * @param text     the XML text
     * @param minLines minimum distance in lines between start and end tag
     * @return the regions by their start line (0-based), in line order
     */
    static Map<Integer, FoldingRegion> scan(String text, int minLines) {
        Map<Integer, FoldingRegion> regions = new TreeMap<>();
        Deque<Open> open = new ArrayDeque<>();
        int n = text.length();
        int line = 0;
        int lineStart = 0;
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                lineStart = ++i;
                continue;
            }
            if (c != '<') {
                i++;
                continue;
            }
            int end;
            if (text.startsWith("<!--", i)) {
                end = indexAfter(text, "-->", i + 4);
            } else if (text.startsWith("<![CDATA[", i)) {
                end = indexAfter(text, "]]>", i + 9);
            } else if (text.startsWith("<?", i)) {
                end = indexAfter(text, "?>", i + 2);
            } else if (text.startsWith("<!", i)) {
                end = doctypeEnd(text, i + 2);
            } else if (i + 1 < n && text.charAt(i + 1) == '/') {
                int nameEnd = nameEnd(text, i + 2);
                end = tagEnd(text, nameEnd);
                if (nameEnd > i + 2) {
                    close(regions, open, text.substring(i + 2, nameEnd), line, text, minLines);
                }
            } else if (i + 1 < n && isNameStart(text.charAt(i + 1))) {
                int nameEnd = nameEnd(text, i + 1);
                end = tagEnd(text, nameEnd);
                boolean selfClosing = end - 2 > i && text.charAt(end - 1) == '>' && text.charAt(end - 2) == '/';
                if (!selfClosing) {
                    open.push(new Open(text.substring(i + 1, nameEnd), line, lineStart));
                }
            } else {
                i++; // a lone '<' (a document being typed)
                continue;
            }
            // Lines inside the skipped construct (a multi-line tag, comment, CDATA section)
            for (int k = i + 1; k < end; k++) {
                if (text.charAt(k) == '\n') {
                    line++;
                    lineStart = k + 1;
                }
            }
            i = end;
        }
        int lastLine = line;
        while (!open.isEmpty()) {
            Open o = open.pop();
            if (lastLine - o.line >= minLines) {
                regions.put(o.line, new FoldingRegion(o.line, lastLine, o.name, indentLevel(text, o.lineStart)));
            }
        }
        return regions;
    }

    /** Ends the innermost open element of that name; elements opened inside it end without a region. */
    private static void close(Map<Integer, FoldingRegion> regions, Deque<Open> open, String name, int line,
                              String text, int minLines) {
        for (Iterator<Open> it = open.iterator(); it.hasNext(); ) {
            if (it.next().name.equals(name)) {
                Open o;
                do {
                    o = open.pop();
                } while (!o.name.equals(name));
                if (line - o.line >= minLines && !regions.containsKey(o.line)) {
                    regions.put(o.line, new FoldingRegion(o.line, line, o.name, indentLevel(text, o.lineStart)));
                }
                return;
            }
        }
        // an end tag without a start tag: ignored
    }

    /** @return the index after the end of a tag starting with a name ending at {@code from}: after its {@code >} */
    private static int tagEnd(String text, int from) {
        int n = text.length();
        char quote = 0;
        for (int k = from; k < n; k++) {
            char c = text.charAt(k);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else if (c == '<') {
                    return k; // an unfinished quote: the broken tag ends before the next tag
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return k + 1;
            } else if (c == '<') {
                return k; // a tag without '>': it ends where the next one begins
            }
        }
        return n;
    }

    /** @return the index after the DOCTYPE (or another {@code <!…>} declaration), internal subset included */
    private static int doctypeEnd(String text, int from) {
        int n = text.length();
        int depth = 0;
        char quote = 0;
        for (int k = from; k < n; k++) {
            char c = text.charAt(k);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth = Math.max(0, depth - 1);
            } else if (c == '>' && depth == 0) {
                return k + 1;
            }
        }
        return n;
    }

    private static int indexAfter(String text, String terminator, int from) {
        int at = text.indexOf(terminator, from);
        return at < 0 ? text.length() : at + terminator.length();
    }

    private static int nameEnd(String text, int from) {
        int k = from;
        while (k < text.length() && isNameChar(text.charAt(k))) {
            k++;
        }
        return k;
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c == ':';
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-' || c == '.';
    }

    /** @return the indent level of the line starting at {@code lineStart}: 4 spaces (or a tab) per level */
    static int indentLevel(String text, int lineStart) {
        int spaces = 0;
        for (int k = lineStart; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == ' ') {
                spaces++;
            } else if (c == '\t') {
                spaces += 4;
            } else {
                break;
            }
        }
        return spaces / 4;
    }
}
