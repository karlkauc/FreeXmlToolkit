package org.fxt.freexmltoolkit.debugger;

/**
 * Where Saxon reports an instruction whose start tag spans several lines: at the line where the
 * start tag <em>ends</em>. A breakpoint (or Run to Cursor) on an earlier line of such a tag
 * would never be reached; {@link #map(String)} gives, per line, the line the debugger has to
 * watch instead, so the breakpoint can stay where the user put it.
 *
 * <p>Comments, CDATA sections, processing instructions and the DOCTYPE are skipped; quoted
 * attribute values may contain {@code >} and line breaks. Text that does not parse well still
 * yields a best-effort map.</p>
 */
public final class StartTagLines {

    private StartTagLines() {
    }

    /**
     * @return an array indexed by 1-based line: the line where the start tag covering that line
     *         ends, or 0 when the line is not inside a multi-line start tag (or is its last line)
     */
    public static int[] map(String text) {
        if (text == null || text.isEmpty()) {
            return new int[0];
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        int[] map = new int[lines + 1];
        int line = 1;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (c != '<') {
                i++;
                continue;
            }
            if (text.startsWith("<!--", i)) {
                int[] r = skipTo(text, i + 4, "-->", line);
                i = r[0];
                line = r[1];
            } else if (text.startsWith("<![CDATA[", i)) {
                int[] r = skipTo(text, i + 9, "]]>", line);
                i = r[0];
                line = r[1];
            } else if (text.startsWith("<?", i)) {
                int[] r = skipTo(text, i + 2, "?>", line);
                i = r[0];
                line = r[1];
            } else if (text.startsWith("<!", i) || text.startsWith("</", i)) {
                int[] r = skipTo(text, i + 2, ">", line);
                i = r[0];
                line = r[1];
            } else {
                int startLine = line;
                char quote = 0;
                int j = i + 1;
                for (; j < n; j++) {
                    char d = text.charAt(j);
                    if (d == '\n') {
                        line++;
                    } else if (quote != 0) {
                        if (d == quote) {
                            quote = 0;
                        }
                    } else if (d == '"' || d == '\'') {
                        quote = d;
                    } else if (d == '>') {
                        break;
                    }
                }
                for (int l = startLine; l < line && l < map.length; l++) {
                    map[l] = line;
                }
                i = j + 1;
            }
        }
        return map;
    }

    /** @return the line to watch for {@code line} (itself unless it lies inside a multi-line start tag). */
    public static int resolve(int[] map, int line) {
        return map != null && line > 0 && line < map.length && map[line] > 0 ? map[line] : line;
    }

    /** Skips to after {@code end}, counting line breaks; returns {index, line}. */
    private static int[] skipTo(String text, int from, String end, int line) {
        int at = text.indexOf(end, from);
        int stop = at < 0 ? text.length() : at + end.length();
        for (int k = from; k < stop; k++) {
            if (text.charAt(k) == '\n') {
                line++;
            }
        }
        return new int[]{stop, line};
    }
}
