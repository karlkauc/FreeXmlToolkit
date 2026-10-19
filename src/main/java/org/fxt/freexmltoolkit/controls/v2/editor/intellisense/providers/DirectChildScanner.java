package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textual scan of the direct child elements of the element the caret is inside — those before
 * the caret and those after it — by local name (namespace prefixes are stripped).
 */
final class DirectChildScanner {

    /** Direct children before and after the caret, in document order, local names only. */
    record Siblings(List<String> before, List<String> after) {
        static final Siblings NONE = new Siblings(List.of(), List.of());
    }

    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z_][\\w.:-]*)(?:\\s[^<>]*)?(/?)>");

    private DirectChildScanner() {
    }

    /**
     * @param textBeforeCaret text from the start of the document to the caret
     * @param textAfterCaret  text from the caret to the end of the document (may be null)
     * @param parentLocalName local name of the element the caret is inside
     */
    static Siblings scan(String textBeforeCaret, String textAfterCaret, String parentLocalName) {
        if (textBeforeCaret == null || textBeforeCaret.isEmpty() || parentLocalName == null || parentLocalName.isEmpty()) {
            return Siblings.NONE;
        }
        int contentStart = findParentContentStart(textBeforeCaret, parentLocalName);
        if (contentStart < 0) {
            return Siblings.NONE;
        }
        // Both scans walk the text in place, skipping comments/CDATA/PIs as they go. The text after the
        // caret is only read up to the parent's end tag: stripping all of it first (three regex passes
        // over the rest of the document) cost hundreds of ms per typed '<' in multi-MB files.
        List<String> before = directChildren(textBeforeCaret, contentStart, false);
        List<String> after = textAfterCaret == null || textAfterCaret.isEmpty()
                ? List.of()
                : directChildren(textAfterCaret, 0, true);
        return new Siblings(before, after);
    }

    /**
     * Index just after the {@code >} of the last opening tag of the parent (any prefix): the last
     * match of {@code <(?:[\w.-]+:)?NAME(?=[\s>/])}, found by searching backwards from the caret.
     */
    static int findParentContentStart(String text, String parentLocalName) {
        int from = text.length();
        while (from >= 0) {
            int at = text.lastIndexOf(parentLocalName, from);
            if (at < 0) {
                return -1;
            }
            from = at - 1;
            int afterName = at + parentLocalName.length();
            if (afterName >= text.length() || !isTagNameTerminator(text.charAt(afterName))) {
                continue;
            }
            int tagStart = -1;
            if (at > 0 && text.charAt(at - 1) == '<') {
                tagStart = at - 1;
            } else if (at > 1 && text.charAt(at - 1) == ':') {
                int k = at - 2;
                while (k >= 0 && isPrefixChar(text.charAt(k))) {
                    k--;
                }
                if (k < at - 2 && k >= 0 && text.charAt(k) == '<') {
                    tagStart = k;
                }
            }
            if (tagStart >= 0) {
                int tagEnd = text.indexOf('>', tagStart);
                return tagEnd < 0 ? -1 : tagEnd + 1;
            }
        }
        return -1;
    }

    /** {@code [\s>/]}: what may follow a tag name. */
    private static boolean isTagNameTerminator(char c) {
        return c == '>' || c == '/' || c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == 0x0B;
    }

    /** {@code [\w.-]}: a namespace prefix character. */
    private static boolean isPrefixChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '.' || c == '-';
    }

    /**
     * Walks the tags at depth 0 of {@code text} from {@code from}, skipping comments, CDATA sections
     * and processing instructions. When {@code stopAtParentClose} is set (text after the caret), the
     * first closing tag at depth 0 is the parent's end tag and terminates the scan.
     */
    private static List<String> directChildren(String text, int from, boolean stopAtParentClose) {
        List<String> names = new ArrayList<>();
        Matcher m = TAG.matcher(text);
        int depth = 0;
        int i = from;
        int n = text.length();
        while (i < n) {
            int lt = text.indexOf('<', i);
            if (lt < 0) {
                break;
            }
            int skipTo = skipNonMarkup(text, lt);
            if (skipTo > lt) {
                i = skipTo;
                continue;
            }
            m.region(lt, n);
            if (!m.lookingAt()) {
                i = lt + 1;
                continue;
            }
            i = m.end();
            boolean closing = !m.group(1).isEmpty();
            boolean selfClosing = !m.group(3).isEmpty();
            String name = localName(m.group(2));
            if (closing) {
                if (depth == 0) {
                    if (stopAtParentClose) {
                        break;
                    }
                    continue;
                }
                depth--;
            } else if (selfClosing) {
                if (depth == 0) {
                    names.add(name);
                }
            } else {
                if (depth == 0) {
                    names.add(name);
                }
                depth++;
            }
        }
        return names;
    }

    /**
     * If a terminated comment, CDATA section or processing instruction starts at {@code lt}, the index
     * after it; otherwise {@code lt} (an unterminated one is plain text, as for the former regexes).
     */
    private static int skipNonMarkup(String text, int lt) {
        String start;
        String end;
        if (text.startsWith("<!--", lt)) {
            start = "<!--";
            end = "-->";
        } else if (text.startsWith("<![CDATA[", lt)) {
            start = "<![CDATA[";
            end = "]]>";
        } else if (text.startsWith("<?", lt)) {
            start = "<?";
            end = "?>";
        } else {
            return lt;
        }
        // the terminator is searched after the opener, as by the former regexes ("<!-->" is no comment)
        int close = text.indexOf(end, lt + start.length());
        return close < 0 ? lt : close + end.length();
    }

    static String localName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int colon = qualifiedName.lastIndexOf(':');
        return colon >= 0 ? qualifiedName.substring(colon + 1) : qualifiedName;
    }
}
