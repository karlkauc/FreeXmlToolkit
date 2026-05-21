package org.fxt.freexmltoolkit.controls.diff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxt.freexmltoolkit.controls.shared.XmlSyntaxHighlighter;

/**
 * Builds {@link StyleSpans} that overlay the XML syntax highlighting with diff
 * coloring (line backgrounds for INSERT/DELETE/CHANGE plus optional
 * intra-line word emphasis).
 *
 * <p>Two methods produce the spans for the left and right side of a
 * {@link DiffView}; both compose the result with
 * {@link XmlSyntaxHighlighter#computeHighlighting(String)} so syntax colors
 * remain visible underneath the diff backgrounds.
 */
public final class DiffHighlighter {

    public static final String CLASS_DELETED      = "diff-deleted";
    public static final String CLASS_ADDED        = "diff-added";
    public static final String CLASS_MODIFIED     = "diff-modified";
    public static final String CLASS_WORD_REMOVED = "diff-word-removed";
    public static final String CLASS_WORD_ADDED   = "diff-word-added";

    public enum Side { LEFT, RIGHT }

    private DiffHighlighter() {
        // utility
    }

    /**
     * Builds a syntax + diff overlay for one side of the comparison.
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text, List<DiffChunk> chunks, Side side) {
        StyleSpans<Collection<String>> syntax = XmlSyntaxHighlighter.computeHighlighting(text);
        StyleSpans<Collection<String>> diff = computeDiffOverlay(text, chunks, side);
        return syntax.overlay(diff, DiffHighlighter::merge);
    }

    private static Collection<String> merge(Collection<String> a, Collection<String> b) {
        if (b.isEmpty()) return a;
        if (a.isEmpty()) return b;
        HashSet<String> out = new HashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static StyleSpans<Collection<String>> computeDiffOverlay(String text, List<DiffChunk> chunks, Side side) {
        int[] lineOffsets = computeLineOffsets(text);
        int totalLines = lineOffsets.length - 1;
        int textLen = text.length();

        StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
        int cursor = 0;

        for (DiffChunk c : chunks) {
            int startLine = side == Side.LEFT ? c.getLeftStart() : c.getRightStart();
            int endLine   = side == Side.LEFT ? c.getLeftEnd()   : c.getRightEnd();
            if (endLine <= startLine) continue;
            if (startLine >= totalLines) continue;

            int chunkStartChar = lineOffsets[Math.min(startLine, totalLines)];
            int chunkEndChar   = Math.min(textLen, lineOffsets[Math.min(endLine, totalLines)]);
            if (chunkEndChar <= chunkStartChar) continue;

            if (chunkStartChar > cursor) {
                b.add(Collections.emptyList(), chunkStartChar - cursor);
                cursor = chunkStartChar;
            }

            String lineClass = lineClassFor(c.getType(), side);
            if (c.getType() == DiffChunk.Type.CHANGE) {
                cursor = applyChangeChunk(b, cursor, c, side, lineOffsets, chunkEndChar, lineClass);
            } else {
                b.add(Collections.singleton(lineClass), chunkEndChar - cursor);
                cursor = chunkEndChar;
            }
        }
        if (cursor < textLen) {
            b.add(Collections.emptyList(), textLen - cursor);
        }
        return b.create();
    }

    private static int applyChangeChunk(StyleSpansBuilder<Collection<String>> b,
                                        int cursor,
                                        DiffChunk c,
                                        Side side,
                                        int[] lineOffsets,
                                        int chunkEndChar,
                                        String lineClass) {
        int startLine = side == Side.LEFT ? c.getLeftStart() : c.getRightStart();
        int endLine   = side == Side.LEFT ? c.getLeftEnd()   : c.getRightEnd();
        List<DiffChunk.LineWordDiff> wordPatches = c.getWordPatches();
        int totalLines = lineOffsets.length - 1;
        String wordClass = side == Side.LEFT ? CLASS_WORD_REMOVED : CLASS_WORD_ADDED;
        DiffChunk.LineWordDiff.SegmentKind interestingKind =
                side == Side.LEFT ? DiffChunk.LineWordDiff.SegmentKind.REMOVED
                                  : DiffChunk.LineWordDiff.SegmentKind.ADDED;

        for (int line = startLine; line < endLine && line < totalLines; line++) {
            int lineStart = lineOffsets[line];
            int lineEnd = (line + 1 < lineOffsets.length) ? lineOffsets[line + 1] : chunkEndChar;
            int relIndex = line - startLine;

            DiffChunk.LineWordDiff wp = (wordPatches != null && relIndex < wordPatches.size())
                    ? wordPatches.get(relIndex) : null;

            if (wp == null) {
                b.add(Collections.singleton(lineClass), lineEnd - cursor);
                cursor = lineEnd;
                continue;
            }

            List<DiffChunk.LineWordDiff.Segment> segs =
                    side == Side.LEFT ? wp.getLeftSegments() : wp.getRightSegments();

            int lineCursor = lineStart;
            List<DiffChunk.LineWordDiff.Segment> highlightSegs = new ArrayList<>();
            for (DiffChunk.LineWordDiff.Segment s : segs) {
                if (s.getKind() == interestingKind) highlightSegs.add(s);
            }
            for (DiffChunk.LineWordDiff.Segment s : highlightSegs) {
                int segStart = lineStart + s.getStartCol();
                int segEnd = Math.min(lineEnd, lineStart + s.getEndCol());
                if (segStart > lineCursor) {
                    b.add(Collections.singleton(lineClass), segStart - lineCursor);
                }
                if (segEnd > segStart) {
                    HashSet<String> classes = new HashSet<>();
                    classes.add(lineClass);
                    classes.add(wordClass);
                    b.add(classes, segEnd - segStart);
                }
                lineCursor = Math.max(lineCursor, segEnd);
            }
            if (lineCursor < lineEnd) {
                b.add(Collections.singleton(lineClass), lineEnd - lineCursor);
            }
            cursor = lineEnd;
        }
        return cursor;
    }

    private static String lineClassFor(DiffChunk.Type type, Side side) {
        return switch (type) {
            case INSERT -> side == Side.RIGHT ? CLASS_ADDED : CLASS_MODIFIED;
            case DELETE -> side == Side.LEFT ? CLASS_DELETED : CLASS_MODIFIED;
            case CHANGE -> CLASS_MODIFIED;
            case EQUAL -> "";
        };
    }

    /**
     * Returns an array {@code offsets} of length {@code lines+1} where
     * {@code offsets[i]} is the absolute character index where line {@code i}
     * starts and {@code offsets[lines]} == text.length().
     */
    static int[] computeLineOffsets(String text) {
        if (text == null) text = "";
        int n = text.length();
        int lines = 1;
        for (int i = 0; i < n; i++) if (text.charAt(i) == '\n') lines++;
        int[] offsets = new int[lines + 1];
        offsets[0] = 0;
        int idx = 1;
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) == '\n') {
                offsets[idx++] = i + 1;
            }
        }
        offsets[lines] = n;
        return offsets;
    }
}
