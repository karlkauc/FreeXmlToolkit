package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The attribute names already written in the start tag the caret is in, so attribute
 * completion can leave them out.
 *
 * <p>The tag runs from the last {@code <} before the caret to the first {@code >} after it
 * that is not inside a quoted value (XPath expressions such as {@code test="a > b"} contain
 * {@code >}). The attribute name the caret is currently typing is not reported. Meant for
 * an attribute-name position, i.e. the caret is not inside a quoted value.</p>
 */
public final class StartTagAttributes {

    private StartTagAttributes() {
    }

    /**
     * Collects the attribute names of the start tag around the caret.
     *
     * @param textBeforeCaret the document text before the caret
     * @param textAfterCaret  the document text after the caret
     * @return the qualified attribute names in document order (never null)
     */
    public static Set<String> present(String textBeforeCaret, String textAfterCaret) {
        Set<String> names = new LinkedHashSet<>();
        if (textBeforeCaret == null) {
            return names;
        }
        int open = textBeforeCaret.lastIndexOf('<');
        if (open < 0) {
            return names;
        }
        String before = textBeforeCaret.substring(open + 1);
        collect(before, true, names);
        if (textAfterCaret != null && !textAfterCaret.isEmpty()) {
            // A partial name being typed at the caret continues after it; skip that remainder first.
            boolean typingName = !before.isEmpty() && isNameChar(before.charAt(before.length() - 1));
            int i = 0;
            while (typingName && i < textAfterCaret.length() && isNameChar(textAfterCaret.charAt(i))) {
                i++;
            }
            String after = textAfterCaret.substring(i, endOfTag(textAfterCaret, i));
            collect(after, false, names);
        }
        return names;
    }

    /**
     * Adds every {@code name=} found in {@code tagText}, skipping quoted values. With
     * {@code startsWithElementName}, the first token is the element name and is skipped.
     */
    private static void collect(String tagText, boolean startsWithElementName, Set<String> names) {
        int i = 0;
        int n = tagText.length();
        if (startsWithElementName) {
            while (i < n && !Character.isWhitespace(tagText.charAt(i))) {
                i++;
            }
        }
        while (i < n) {
            char c = tagText.charAt(i);
            if (c == '"' || c == '\'') {
                int close = tagText.indexOf(c, i + 1);
                if (close < 0) {
                    return;
                }
                i = close + 1;
            } else if (isNameChar(c)) {
                int start = i;
                while (i < n && isNameChar(tagText.charAt(i))) {
                    i++;
                }
                int j = i;
                while (j < n && Character.isWhitespace(tagText.charAt(j))) {
                    j++;
                }
                if (j < n && tagText.charAt(j) == '=') {
                    names.add(tagText.substring(start, i));
                    i = j + 1;
                }
            } else {
                i++;
            }
        }
    }

    /** @return the index of the closing {@code >} (or the next {@code <}, or the end) from {@code from} */
    private static int endOfTag(String text, int from) {
        char quote = 0;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>' || c == '<') {
                return c == '>' && i > from && text.charAt(i - 1) == '/' ? i - 1 : i;
            }
        }
        return text.length();
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ':' || c == '.';
    }
}
