package org.fxt.freexmltoolkit.service.standards;

/**
 * A query text with string literals and comments blanked out (same length, line breaks kept), so a syntax scan
 * over it finds only real tokens; plus offset → line/column.
 */
final class SourceMask {

    private final String original;
    private final String masked;

    private SourceMask(String original, String masked) {
        this.original = original;
        this.masked = masked;
    }

    /** Masks XQuery/XPath string literals ('…', "…" with doubled quotes) and comments (: … :), nested. */
    static SourceMask ofQuery(String text) {
        char[] out = text.toCharArray();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '(' && i + 1 < n && text.charAt(i + 1) == ':') {
                int depth = 0;
                int j = i;
                while (j < n) {
                    if (text.startsWith("(:", j)) {
                        depth++;
                        j += 2;
                    } else if (text.startsWith(":)", j)) {
                        depth--;
                        j += 2;
                        if (depth == 0) {
                            break;
                        }
                    } else {
                        j++;
                    }
                }
                blank(out, i, Math.min(j, n));
                i = j;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n) {
                    if (text.charAt(j) == c) {
                        if (j + 1 < n && text.charAt(j + 1) == c) {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                // keep the quotes, blank the content
                blank(out, i + 1, Math.min(j, n));
                i = j + 1;
            } else {
                i++;
            }
        }
        return new SourceMask(text, new String(out));
    }

    private static void blank(char[] out, int from, int to) {
        for (int k = from; k < to; k++) {
            if (out[k] != '\n' && out[k] != '\r') {
                out[k] = ' ';
            }
        }
    }

    String masked() {
        return masked;
    }

    String original() {
        return original;
    }

    /** 1-based line of {@code offset}. */
    int line(int offset) {
        int line = 1;
        for (int k = 0; k < offset && k < original.length(); k++) {
            if (original.charAt(k) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 1-based column of {@code offset}. */
    int column(int offset) {
        int start = original.lastIndexOf('\n', Math.max(0, offset - 1));
        return offset - start;
    }
}
