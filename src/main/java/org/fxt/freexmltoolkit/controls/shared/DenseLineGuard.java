package org.fxt.freexmltoolkit.controls.shared;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Keeps syntax highlighting from freezing the editor on minified content.
 *
 * <p>RichTextFX renders every style span of a paragraph as its own text node, and its line
 * layout queries cost grows quadratically with the number of nodes in one paragraph. A
 * minified document puts thousands of spans on a single line: 20 KB of minified XML already
 * blocks the UI thread for seconds, 100 KB for minutes, while the same line without styles
 * lays out in milliseconds. Lines with too many spans are therefore shown unstyled; all other
 * lines keep their highlighting.</p>
 */
public final class DenseLineGuard {

    /** Most style spans a single line may carry before it is shown unstyled. */
    public static final int MAX_SPANS_PER_LINE = 1000;

    private DenseLineGuard() {
        // utility
    }

    /**
     * Removes the styles of every line that carries more than {@link #MAX_SPANS_PER_LINE} spans.
     *
     * @param text  the highlighted text
     * @param spans the style spans computed for {@code text}
     * @return {@code spans} itself when no line is too dense, otherwise a copy with those lines unstyled
     */
    public static StyleSpans<Collection<String>> flattenDenseLines(String text, StyleSpans<Collection<String>> spans) {
        return flattenDenseLines(text, spans, MAX_SPANS_PER_LINE);
    }

    /**
     * As {@link #flattenDenseLines(String, StyleSpans)} with an explicit limit.
     *
     * @param maxSpansPerLine most spans a line may carry and stay styled
     */
    public static StyleSpans<Collection<String>> flattenDenseLines(String text, StyleSpans<Collection<String>> spans,
                                                                   int maxSpansPerLine) {
        if (text == null || spans == null || spans.getSpanCount() <= maxSpansPerLine) {
            return spans;
        }
        List<int[]> dense = denseLineRanges(text, spans, maxSpansPerLine);
        if (dense.isEmpty()) {
            return spans;
        }

        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int rangeIndex = 0;
        int position = 0;
        int pendingPlain = 0;
        for (StyleSpan<Collection<String>> span : spans) {
            int end = position + span.getLength();
            while (position < end) {
                while (rangeIndex < dense.size() && dense.get(rangeIndex)[1] <= position) {
                    rangeIndex++;
                }
                int[] range = rangeIndex < dense.size() ? dense.get(rangeIndex) : null;
                if (range != null && position >= range[0]) {
                    // Inside a dense line: collect into one unstyled span.
                    int pieceEnd = Math.min(end, range[1]);
                    pendingPlain += pieceEnd - position;
                    position = pieceEnd;
                } else {
                    int pieceEnd = range != null ? Math.min(end, range[0]) : end;
                    if (pendingPlain > 0) {
                        builder.add(Collections.emptyList(), pendingPlain);
                        pendingPlain = 0;
                    }
                    builder.add(span.getStyle(), pieceEnd - position);
                    position = pieceEnd;
                }
            }
        }
        if (pendingPlain > 0) {
            builder.add(Collections.emptyList(), pendingPlain);
        }
        return builder.create();
    }

    /** @return {@code [start, end)} offsets of the lines carrying more than {@code max} spans */
    private static List<int[]> denseLineRanges(String text, StyleSpans<Collection<String>> spans, int max) {
        List<int[]> dense = new ArrayList<>();
        int lineStart = 0;
        int lineEnd = lineEndAfter(text, 0);
        int count = 0;
        int position = 0;
        for (StyleSpan<Collection<String>> span : spans) {
            int end = position + span.getLength();
            if (span.getLength() == 0) {
                continue;
            }
            count++;
            // A span reaching past the line end also counts for each following line it touches.
            while (end > lineEnd) {
                if (count > max) {
                    dense.add(new int[] {lineStart, lineEnd});
                }
                lineStart = lineEnd;
                lineEnd = lineEndAfter(text, lineStart);
                count = 1;
            }
            position = end;
        }
        if (count > max) {
            dense.add(new int[] {lineStart, lineEnd});
        }
        return dense;
    }

    /** @return the offset just past the line break ending the line that starts at {@code from} */
    private static int lineEndAfter(String text, int from) {
        if (from >= text.length()) {
            return Integer.MAX_VALUE;
        }
        int newline = text.indexOf('\n', from);
        return newline < 0 ? Integer.MAX_VALUE : newline + 1;
    }
}
