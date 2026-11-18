package org.fxt.freexmltoolkit.service.query;

/** Line / column arithmetic on query texts (1-based lines and columns, {@code \n} line ends). */
final class QueryText {

    private QueryText() {
    }

    /** @return the offset of {@code line}/{@code column}, clamped to the line's end */
    static int offsetOf(String text, int line, int column) {
        int start = lineStart(text, line);
        if (start < 0) {
            return -1;
        }
        int end = lineEnd(text, start);
        return Math.min(start + Math.max(0, column - 1), end);
    }

    /** @return the text of a 1-based line without its line end, or {@code ""} */
    static String line(String text, int line) {
        int start = lineStart(text, line);
        return start < 0 ? "" : text.substring(start, lineEnd(text, start));
    }

    /** @return the number of lines */
    static int lineCount(String text) {
        int n = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /** @return {line, column} (both 1-based) of an offset */
    static int[] position(String text, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new int[] {line, offset - lineStart + 1};
    }

    static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == ':' || c == '$';
    }

    /**
     * The token at an offset: a name (with {@code $} and prefix) when the offset is on one, else the
     * non-blank character there; at a line end or on blanks the token before it.
     *
     * @return {start, end} offsets, end exclusive
     */
    static int[] tokenAt(String text, int offset) {
        if (text.isEmpty()) {
            return new int[] {0, 0};
        }
        int i = Math.max(0, Math.min(offset, text.length()));
        if (i == text.length() || Character.isWhitespace(text.charAt(i))) {
            int j = i - 1;
            while (j >= 0 && text.charAt(j) != '\n' && Character.isWhitespace(text.charAt(j))) {
                j--;
            }
            if (j < 0 || text.charAt(j) == '\n') {
                return new int[] {Math.min(i, text.length()), Math.min(i, text.length())};
            }
            i = j;
        }
        if (!isNameChar(text.charAt(i))) {
            return new int[] {i, i + 1};
        }
        int s = i;
        while (s > 0 && isNameChar(text.charAt(s - 1))) {
            s--;
        }
        int e = i + 1;
        while (e < text.length() && isNameChar(text.charAt(e))) {
            e++;
        }
        return new int[] {s, e};
    }

    private static int lineStart(String text, int line) {
        if (line < 1) {
            return -1;
        }
        int start = 0;
        for (int l = 1; l < line; l++) {
            int nl = text.indexOf('\n', start);
            if (nl < 0) {
                return -1;
            }
            start = nl + 1;
        }
        return start;
    }

    private static int lineEnd(String text, int start) {
        int nl = text.indexOf('\n', start);
        int end = nl < 0 ? text.length() : nl;
        return end > start && text.charAt(end - 1) == '\r' ? end - 1 : end;
    }
}
