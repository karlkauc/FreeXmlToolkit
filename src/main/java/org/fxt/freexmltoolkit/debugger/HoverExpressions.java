package org.fxt.freexmltoolkit.debugger;

import java.util.Optional;

/**
 * Finds the XPath to evaluate when the mouse rests on stylesheet text while the debugger is
 * paused: a variable reference {@code $name} / {@code $p:name} under the mouse. (A selection
 * that contains the mouse position is handled by the caller — the selected text is evaluated.)
 */
public final class HoverExpressions {

    private HoverExpressions() {
    }

    /**
     * @param text  the document text
     * @param index character index under the mouse
     * @return {@code $name} when {@code index} is on a variable reference (the {@code $} or the
     *         name), otherwise empty
     */
    public static Optional<String> variableAt(String text, int index) {
        if (text == null || index < 0 || index >= text.length()) {
            return Optional.empty();
        }
        int start = index;
        if (text.charAt(start) == '$') {
            start++;
        } else {
            if (!isNameChar(text.charAt(start))) {
                return Optional.empty();
            }
            while (start > 0 && isNameChar(text.charAt(start - 1))) {
                start--;
            }
            if (start == 0 || text.charAt(start - 1) != '$') {
                return Optional.empty();
            }
        }
        int end = start;
        while (end < text.length() && isNameChar(text.charAt(end))) {
            end++;
        }
        // A QName ends with a name character, not with ':', '.' or '-' (e.g. "$a:" in "$a:b" typos,
        // or "$total." at a sentence end).
        while (end > start && !isNameEnd(text.charAt(end - 1))) {
            end--;
        }
        if (end == start || !Character.isLetter(text.charAt(start)) && text.charAt(start) != '_') {
            return Optional.empty();
        }
        return Optional.of("$" + text.substring(start, end));
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':';
    }

    private static boolean isNameEnd(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
