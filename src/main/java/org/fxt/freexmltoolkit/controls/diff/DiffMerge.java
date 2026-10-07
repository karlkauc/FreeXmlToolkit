package org.fxt.freexmltoolkit.controls.diff;

import java.util.List;

/**
 * Pure-Java helpers for applying diff chunks back onto the underlying text.
 * Extracted from {@link DiffView} so the merge math can be unit-tested
 * without bringing up a JavaFX runtime.
 */
public final class DiffMerge {

    public enum Direction { LEFT_TO_RIGHT, RIGHT_TO_LEFT }

    /** Result of an apply operation: the updated left and right text. */
    public record Result(String left, String right) {}

    private DiffMerge() {
        // utility
    }

    /**
     * Apply a single chunk in the given direction. Returns the new {@code (left, right)}.
     * EQUAL chunks are no-ops.
     */
    public static Result applyChunk(String left, String right, DiffChunk c, Direction dir) {
        if (c.isEqual()) return new Result(left, right);

        int[] leftOffsets = DiffHighlighter.computeLineOffsets(left);
        int[] rightOffsets = DiffHighlighter.computeLineOffsets(right);

        if (dir == Direction.LEFT_TO_RIGHT) {
            String replacement = sliceLines(left, leftOffsets, c.getLeftStart(), c.getLeftEnd());
            String newRight = replaceRange(right, rightOffsets, c.getRightStart(), c.getRightEnd(), replacement);
            return new Result(left, newRight);
        } else {
            String replacement = sliceLines(right, rightOffsets, c.getRightStart(), c.getRightEnd());
            String newLeft = replaceRange(left, leftOffsets, c.getLeftStart(), c.getLeftEnd(), replacement);
            return new Result(newLeft, right);
        }
    }

    /**
     * Apply ALL non-equal chunks of the supplied diff in the given direction.
     * Builds the result in a single pass over the target text, so the cost does not grow
     * with the number of chunks.
     */
    public static Result applyAll(String left, String right, List<DiffChunk> chunks, Direction dir) {
        boolean toRight = dir == Direction.LEFT_TO_RIGHT;
        String source = toRight ? left : right;
        String target = toRight ? right : left;
        int[] sourceOffsets = DiffHighlighter.computeLineOffsets(source);
        int[] targetOffsets = DiffHighlighter.computeLineOffsets(target);

        StringBuilder sb = new StringBuilder(Math.max(source.length(), target.length()));
        int cursor = 0;
        for (DiffChunk c : chunks) {
            if (c.isEqual()) continue;
            int sourceStart = toRight ? c.getLeftStart() : c.getRightStart();
            int sourceEnd = toRight ? c.getLeftEnd() : c.getRightEnd();
            int targetStart = toRight ? c.getRightStart() : c.getLeftStart();
            int targetEnd = toRight ? c.getRightEnd() : c.getLeftEnd();

            int start = targetOffsets[Math.min(targetStart, targetOffsets.length - 1)];
            int end = Math.min(target.length(), targetOffsets[Math.min(targetEnd, targetOffsets.length - 1)]);
            if (start < cursor) continue; // overlapping chunk; cannot happen for a line diff
            sb.append(target, cursor, start);
            sb.append(sliceLines(source, sourceOffsets, sourceStart, sourceEnd));
            cursor = Math.max(start, end);
        }
        sb.append(target, cursor, target.length());
        return toRight ? new Result(left, sb.toString()) : new Result(sb.toString(), right);
    }

    private static String sliceLines(String text, int[] offsets, int startLine, int endLine) {
        if (startLine >= endLine) return "";
        int start = offsets[Math.min(startLine, offsets.length - 1)];
        int end   = offsets[Math.min(endLine,   offsets.length - 1)];
        end = Math.min(end, text.length());
        return text.substring(start, end);
    }

    private static String replaceRange(String text, int[] offsets, int startLine, int endLine, String replacement) {
        int start = offsets[Math.min(startLine, offsets.length - 1)];
        int end   = offsets[Math.min(endLine,   offsets.length - 1)];
        end = Math.min(end, text.length());
        StringBuilder sb = new StringBuilder(text.length() + replacement.length());
        sb.append(text, 0, start);
        sb.append(replacement);
        sb.append(text, end, text.length());
        return sb.toString();
    }
}
