package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.List;

/**
 * Word-wraps a string to a maximum pixel width using a {@link TextMeasurer}.
 *
 * <p>Rules: embedded newlines always break; otherwise a greedy word wrap on whitespace;
 * a single token wider than the line is hard-broken by characters (at least one character
 * per line, so wrapping always terminates). There is no upper limit on the number of
 * lines — the grid never truncates content.</p>
 */
public final class TextWrap {

    private TextWrap() {
    }

    /**
     * @param text     the text to wrap ({@code null} or empty yields one empty line)
     * @param font     the font to measure in
     * @param maxWidth the available width in unscaled pixels
     * @param m        the measurer
     * @return the wrapped lines, never empty
     */
    public static List<String> wrap(String text, GridFont font, double maxWidth, TextMeasurer m) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        if (text.indexOf('\n') < 0 && m.width(text, font) <= maxWidth) {
            return List.of(text);
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, font, maxWidth, m, lines);
        }
        return lines;
    }

    private static void wrapParagraph(String paragraph, GridFont font, double maxWidth,
                                      TextMeasurer m, List<String> out) {
        if (paragraph.isEmpty() || m.width(paragraph, font) <= maxWidth) {
            out.add(paragraph);
            return;
        }
        StringBuilder line = new StringBuilder();
        int i = 0;
        int n = paragraph.length();
        while (i < n) {
            // Next token = run of whitespace or run of non-whitespace.
            int start = i;
            boolean ws = Character.isWhitespace(paragraph.charAt(i));
            while (i < n && Character.isWhitespace(paragraph.charAt(i)) == ws) {
                i++;
            }
            String token = paragraph.substring(start, i);
            if (ws) {
                // Whitespace never starts a line; it only survives inside a line.
                if (line.length() > 0 && m.width(line + token, font) <= maxWidth) {
                    line.append(token);
                } else if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                continue;
            }
            if (m.width(line + token, font) <= maxWidth) {
                line.append(token);
                continue;
            }
            if (line.length() > 0) {
                out.add(rstrip(line));
                line.setLength(0);
                if (m.width(token, font) <= maxWidth) {
                    line.append(token);
                    continue;
                }
            }
            // Token alone is wider than the line: hard-break by characters.
            int t = 0;
            while (t < token.length()) {
                int end = t + 1;
                while (end < token.length() && m.width(token.substring(t, end + 1), font) <= maxWidth) {
                    end++;
                }
                if (end < token.length()) {
                    out.add(token.substring(t, end));
                } else {
                    line.append(token, t, end);
                }
                t = end;
            }
        }
        if (line.length() > 0) {
            out.add(rstrip(line));
        }
    }

    private static String rstrip(StringBuilder sb) {
        int end = sb.length();
        while (end > 0 && Character.isWhitespace(sb.charAt(end - 1))) {
            end--;
        }
        return sb.substring(0, end);
    }
}
