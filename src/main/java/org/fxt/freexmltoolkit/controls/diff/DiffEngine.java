package org.fxt.freexmltoolkit.controls.diff;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;

/**
 * Pure-Java diff engine used by the side-by-side compare view.
 *
 * <p>Wraps {@code java-diff-utils} to compute a line-level diff between two
 * strings. The result is an ordered list of {@link DiffChunk} that covers the
 * entire input on both sides (i.e. EQUAL chunks are included between non-equal
 * regions). For CHANGE chunks an additional word-level diff is computed
 * line-by-line so the highlighter can emphasize the actual modified words.
 *
 * <p>This class has no JavaFX dependencies and is fully unit-testable.
 */
public final class DiffEngine {

    private DiffEngine() {
        // utility
    }

    /**
     * Compute a line-level diff. Both inputs are split on \n; a single empty
     * string produces a single empty line. Trailing newlines are preserved as
     * empty trailing lines so that line indices map back to the source faithfully.
     */
    public static List<DiffChunk> compute(String left, String right) {
        List<String> leftLines = splitLines(left);
        List<String> rightLines = splitLines(right);

        Patch<String> patch = DiffUtils.diff(leftLines, rightLines);
        List<AbstractDelta<String>> deltas = patch.getDeltas();

        List<DiffChunk> result = new ArrayList<>();
        int leftCursor = 0;
        int rightCursor = 0;

        for (AbstractDelta<String> delta : deltas) {
            int leftPos = delta.getSource().getPosition();
            int rightPos = delta.getTarget().getPosition();

            if (leftPos > leftCursor || rightPos > rightCursor) {
                int equalLen = Math.min(leftPos - leftCursor, rightPos - rightCursor);
                if (equalLen > 0) {
                    result.add(new DiffChunk(DiffChunk.Type.EQUAL,
                            leftCursor, leftCursor + equalLen,
                            rightCursor, rightCursor + equalLen,
                            null));
                    leftCursor += equalLen;
                    rightCursor += equalLen;
                }
            }

            int leftLen = delta.getSource().size();
            int rightLen = delta.getTarget().size();
            DiffChunk.Type type = mapDeltaType(delta.getType());

            List<DiffChunk.LineWordDiff> wordPatches = null;
            if (type == DiffChunk.Type.CHANGE) {
                wordPatches = computeWordPatches(
                        delta.getSource().getLines(),
                        delta.getTarget().getLines());
            }

            result.add(new DiffChunk(type,
                    leftCursor, leftCursor + leftLen,
                    rightCursor, rightCursor + rightLen,
                    wordPatches));

            leftCursor += leftLen;
            rightCursor += rightLen;
        }

        int trailingLeft = leftLines.size() - leftCursor;
        int trailingRight = rightLines.size() - rightCursor;
        int trailingEqual = Math.min(trailingLeft, trailingRight);
        if (trailingEqual > 0) {
            result.add(new DiffChunk(DiffChunk.Type.EQUAL,
                    leftCursor, leftCursor + trailingEqual,
                    rightCursor, rightCursor + trailingEqual,
                    null));
        }

        return result;
    }

    private static DiffChunk.Type mapDeltaType(DeltaType deltaType) {
        return switch (deltaType) {
            case INSERT -> DiffChunk.Type.INSERT;
            case DELETE -> DiffChunk.Type.DELETE;
            case CHANGE -> DiffChunk.Type.CHANGE;
            case EQUAL -> DiffChunk.Type.EQUAL;
        };
    }

    private static List<String> splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.singletonList("");
        }
        return Arrays.asList(text.split("\n", -1));
    }

    private static List<DiffChunk.LineWordDiff> computeWordPatches(List<String> leftLines, List<String> rightLines) {
        int pairs = Math.min(leftLines.size(), rightLines.size());
        List<DiffChunk.LineWordDiff> out = new ArrayList<>(pairs);
        for (int i = 0; i < pairs; i++) {
            out.add(diffLineByWords(leftLines.get(i), rightLines.get(i)));
        }
        return out;
    }

    private static DiffChunk.LineWordDiff diffLineByWords(String leftLine, String rightLine) {
        List<Token> leftTokens = tokenize(leftLine);
        List<Token> rightTokens = tokenize(rightLine);

        List<String> leftStrings = leftTokens.stream().map(t -> t.text).toList();
        List<String> rightStrings = rightTokens.stream().map(t -> t.text).toList();

        Patch<String> patch = DiffUtils.diff(leftStrings, rightStrings);

        List<DiffChunk.LineWordDiff.Segment> leftSeg = new ArrayList<>();
        List<DiffChunk.LineWordDiff.Segment> rightSeg = new ArrayList<>();

        int leftIdx = 0;
        int rightIdx = 0;
        for (AbstractDelta<String> d : patch.getDeltas()) {
            int lp = d.getSource().getPosition();
            int rp = d.getTarget().getPosition();
            while (leftIdx < lp && rightIdx < rp) {
                addEqualSegment(leftSeg, leftTokens, leftIdx);
                addEqualSegment(rightSeg, rightTokens, rightIdx);
                leftIdx++;
                rightIdx++;
            }
            for (int i = 0; i < d.getSource().size(); i++) {
                addSegment(leftSeg, leftTokens, leftIdx + i, DiffChunk.LineWordDiff.SegmentKind.REMOVED);
            }
            for (int i = 0; i < d.getTarget().size(); i++) {
                addSegment(rightSeg, rightTokens, rightIdx + i, DiffChunk.LineWordDiff.SegmentKind.ADDED);
            }
            leftIdx += d.getSource().size();
            rightIdx += d.getTarget().size();
        }
        while (leftIdx < leftTokens.size() && rightIdx < rightTokens.size()) {
            addEqualSegment(leftSeg, leftTokens, leftIdx);
            addEqualSegment(rightSeg, rightTokens, rightIdx);
            leftIdx++;
            rightIdx++;
        }
        while (leftIdx < leftTokens.size()) {
            addSegment(leftSeg, leftTokens, leftIdx, DiffChunk.LineWordDiff.SegmentKind.REMOVED);
            leftIdx++;
        }
        while (rightIdx < rightTokens.size()) {
            addSegment(rightSeg, rightTokens, rightIdx, DiffChunk.LineWordDiff.SegmentKind.ADDED);
            rightIdx++;
        }
        return new DiffChunk.LineWordDiff(leftSeg, rightSeg);
    }

    private static void addEqualSegment(List<DiffChunk.LineWordDiff.Segment> dst, List<Token> tokens, int idx) {
        addSegment(dst, tokens, idx, DiffChunk.LineWordDiff.SegmentKind.EQUAL);
    }

    private static void addSegment(List<DiffChunk.LineWordDiff.Segment> dst,
                                   List<Token> tokens,
                                   int idx,
                                   DiffChunk.LineWordDiff.SegmentKind kind) {
        Token t = tokens.get(idx);
        dst.add(new DiffChunk.LineWordDiff.Segment(kind, t.start, t.end));
    }

    private record Token(String text, int start, int end) {}

    /**
     * Splits a line into alternating word and whitespace tokens so that the
     * word-level diff highlights only the words that actually changed (not the
     * spaces between them, which would otherwise look noisy).
     */
    private static List<Token> tokenize(String line) {
        List<Token> tokens = new ArrayList<>();
        if (line.isEmpty()) {
            return tokens;
        }
        int n = line.length();
        int i = 0;
        while (i < n) {
            int start = i;
            boolean isWord = !Character.isWhitespace(line.charAt(i));
            while (i < n && !Character.isWhitespace(line.charAt(i)) == isWord) {
                i++;
            }
            tokens.add(new Token(line.substring(start, i), start, i));
        }
        return tokens;
    }
}
