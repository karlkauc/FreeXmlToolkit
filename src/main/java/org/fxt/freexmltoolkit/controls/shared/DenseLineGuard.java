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

    /**
     * Lines longer than this are not given to the highlighter at all: a minified document of several MB on one line
     * blocked the UI thread for seconds in the highlighter's regular expression alone (telemetry, 3.6.0), only for its
     * spans to be flattened afterwards.
     */
    public static final int MAX_HIGHLIGHTED_LINE = 20_000;

    private DenseLineGuard() {
        // utility
    }

    /**
     * Highlights {@code text} with {@code highlighter}, leaving every line longer than {@code maxLine} unstyled
     * without running the highlighter on it; then {@link #flattenDenseLines(String, StyleSpans)}. For a highlighter
     * whose tokens never span a line break (JSON: strings cannot hold a raw line break).
     */
    public static StyleSpans<Collection<String>> highlightSkippingLongLines(String text,
            java.util.function.Function<String, StyleSpans<Collection<String>>> highlighter, int maxLine) {
        if (text == null || text.isEmpty() || !hasLineLongerThan(text, maxLine)) {
            return flattenDenseLines(text, highlighter.apply(text));
        }
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int start = 0;
        int plain = 0; // unstyled characters waiting to be added as one span
        while (start < text.length()) {
            int newline = text.indexOf('\n', start);
            int end = newline < 0 ? text.length() : newline;
            if (end - start > maxLine) {
                plain += end - start;
            } else if (end > start) {
                StyleSpans<Collection<String>> line = flattenDenseLines(text.substring(start, end),
                        highlighter.apply(text.substring(start, end)));
                if (plain > 0) {
                    builder.add(Collections.emptyList(), plain);
                    plain = 0;
                }
                for (StyleSpan<Collection<String>> span : line) {
                    builder.add(span.getStyle(), span.getLength());
                }
            }
            if (newline < 0) {
                break;
            }
            plain++; // the line break
            start = newline + 1;
        }
        if (plain > 0) {
            builder.add(Collections.emptyList(), plain);
        }
        return builder.create();
    }

    /** @return whether a line of {@code text} is longer than {@code maxLine} characters */
    public static boolean hasLineLongerThan(String text, int maxLine) {
        int lineStart = 0;
        for (int i = 0, n = text.length(); i < n; i++) {
            if (text.charAt(i) == '\n') {
                if (i - lineStart > maxLine) {
                    return true;
                }
                lineStart = i + 1;
            }
        }
        return text.length() - lineStart > maxLine;
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
