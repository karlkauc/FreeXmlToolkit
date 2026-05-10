package org.fxt.freexmltoolkit.controls.diff;

import java.util.List;

/**
 * One run of contiguous lines representing a piece of the diff result.
 *
 * <p>Line indices are zero-based and half-open: {@code [start, end)}.
 * For an EQUAL chunk the left and right ranges always have the same length.
 * For DELETE the right range is empty; for INSERT the left range is empty.
 * For CHANGE both ranges are non-empty and may have different lengths.
 *
 * <p>{@link #wordPatches} carries optional intra-line word-level patches for
 * CHANGE chunks. The list is parallel to the line pairs of the chunk
 * (index {@code i} = left line {@code leftStart+i} vs right line
 * {@code rightStart+i}); when the two sides differ in line count the extra
 * lines have no entry. The list is {@code null} for non-CHANGE chunks.
 */
public final class DiffChunk {

    public enum Type { EQUAL, DELETE, INSERT, CHANGE }

    private final Type type;
    private final int leftStart;
    private final int leftEnd;
    private final int rightStart;
    private final int rightEnd;
    private final List<LineWordDiff> wordPatches;

    public DiffChunk(Type type,
                     int leftStart, int leftEnd,
                     int rightStart, int rightEnd,
                     List<LineWordDiff> wordPatches) {
        this.type = type;
        this.leftStart = leftStart;
        this.leftEnd = leftEnd;
        this.rightStart = rightStart;
        this.rightEnd = rightEnd;
        this.wordPatches = wordPatches;
    }

    public Type getType() { return type; }
    public int getLeftStart() { return leftStart; }
    public int getLeftEnd() { return leftEnd; }
    public int getRightStart() { return rightStart; }
    public int getRightEnd() { return rightEnd; }
    public int getLeftLength() { return leftEnd - leftStart; }
    public int getRightLength() { return rightEnd - rightStart; }
    public List<LineWordDiff> getWordPatches() { return wordPatches; }

    public boolean isEqual() { return type == Type.EQUAL; }

    @Override
    public String toString() {
        return type + "[L " + leftStart + "-" + leftEnd + ", R " + rightStart + "-" + rightEnd + "]";
    }

    /**
     * Word-level diff for a single pair of changed lines.
     * Each segment is either kept on both sides, removed from left, or added to right.
     */
    public static final class LineWordDiff {
        public enum SegmentKind { EQUAL, REMOVED, ADDED }

        private final List<Segment> leftSegments;
        private final List<Segment> rightSegments;

        public LineWordDiff(List<Segment> leftSegments, List<Segment> rightSegments) {
            this.leftSegments = leftSegments;
            this.rightSegments = rightSegments;
        }

        public List<Segment> getLeftSegments() { return leftSegments; }
        public List<Segment> getRightSegments() { return rightSegments; }

        public static final class Segment {
            private final SegmentKind kind;
            private final int startCol;
            private final int endCol;

            public Segment(SegmentKind kind, int startCol, int endCol) {
                this.kind = kind;
                this.startCol = startCol;
                this.endCol = endCol;
            }

            public SegmentKind getKind() { return kind; }
            public int getStartCol() { return startCol; }
            public int getEndCol() { return endCol; }
        }
    }
}
