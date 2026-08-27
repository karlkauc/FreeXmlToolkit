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
        List<String> before = directChildren(stripNonMarkup(textBeforeCaret.substring(contentStart)), false);
        List<String> after = textAfterCaret == null || textAfterCaret.isEmpty()
                ? List.of()
                : directChildren(stripNonMarkup(textAfterCaret), true);
        return new Siblings(before, after);
    }

    /** Index just after the {@code >} of the last opening tag of the parent (any prefix). */
    private static int findParentContentStart(String text, String parentLocalName) {
        Pattern open = Pattern.compile("<(?:[\\w.-]+:)?" + Pattern.quote(parentLocalName) + "(?=[\\s>/])");
        Matcher m = open.matcher(text);
        int lastStart = -1;
        while (m.find()) {
            lastStart = m.start();
        }
        if (lastStart < 0) {
            return -1;
        }
        int tagEnd = text.indexOf('>', lastStart);
        return tagEnd < 0 ? -1 : tagEnd + 1;
    }

    /**
     * Walks the tags at depth 0. When {@code stopAtParentClose} is set (text after the caret),
     * the first closing tag at depth 0 is the parent's end tag and terminates the scan.
     */
    private static List<String> directChildren(String content, boolean stopAtParentClose) {
        List<String> names = new ArrayList<>();
        Matcher m = TAG.matcher(content);
        int depth = 0;
        while (m.find()) {
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

    static String localName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int colon = qualifiedName.lastIndexOf(':');
        return colon >= 0 ? qualifiedName.substring(colon + 1) : qualifiedName;
    }

    /** Removes comments, CDATA sections and processing instructions so their content is not scanned. */
    static String stripNonMarkup(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("<!--[\\s\\S]*?-->", "")
                .replaceAll("<!\\[CDATA\\[[\\s\\S]*?]]>", "")
                .replaceAll("<\\?[\\s\\S]*?\\?>", "");
    }
}
