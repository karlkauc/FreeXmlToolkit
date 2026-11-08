package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;

/**
 * Three-way merge of text documents (diff3): the changes from a common <b>base</b> to the
 * <b>left</b> and to the <b>right</b> version are combined; where both changed the same lines
 * differently, a conflict is written with Git's markers
 * ({@code <<<<<<<}, {@code |||||||}, {@code =======}, {@code >>>>>>>}). Changes that touch adjacent
 * lines count as overlapping, like in Git. With {@link #merge(String, String, String, boolean)
 * ignoreWhitespace}, two changes that differ only in whitespace (indentation of the same XML) are
 * not a conflict. UI-free; used by Tools → Merge Files and {@code fxt-cli merge}.
 */
public final class ThreeWayMerge {

    public static final String MARKER_LEFT = "<<<<<<<";
    public static final String MARKER_BASE = "|||||||";
    public static final String MARKER_SEPARATOR = "=======";
    public static final String MARKER_RIGHT = ">>>>>>>";

    /** A part of the merge result. */
    public sealed interface Segment permits Resolved, Conflict {
    }

    /** Text taken as is: unchanged, or changed on one side, or changed alike on both. */
    public record Resolved(String text) implements Segment {
    }

    /** Lines both sides changed differently. */
    public record Conflict(String base, String left, String right) implements Segment {
    }

    /**
     * The result.
     *
     * @param segments resolved text and conflicts in document order
     */
    public record Result(List<Segment> segments) {

        /** @return the number of conflicts */
        public int conflicts() {
            return (int) segments.stream().filter(Conflict.class::isInstance).count();
        }

        /**
         * @return the merged text; each conflict as a block with markers naming the sides
         * @param leftName  label after {@code <<<<<<<}
         * @param baseName  label after {@code |||||||} (the base part is written only with {@code withBase})
         * @param rightName label after {@code >>>>>>>}
         */
        public String text(String leftName, String baseName, String rightName, boolean withBase) {
            StringBuilder out = new StringBuilder();
            String newline = "\n";
            for (Segment segment : segments) {
                if (segment instanceof Resolved resolved) {
                    out.append(resolved.text());
                } else if (segment instanceof Conflict conflict) {
                    out.append(MARKER_LEFT).append(' ').append(leftName).append(newline);
                    out.append(endWithNewline(conflict.left()));
                    if (withBase) {
                        out.append(MARKER_BASE).append(' ').append(baseName).append(newline);
                        out.append(endWithNewline(conflict.base()));
                    }
                    out.append(MARKER_SEPARATOR).append(newline);
                    out.append(endWithNewline(conflict.right()));
                    out.append(MARKER_RIGHT).append(' ').append(rightName).append(newline);
                }
            }
            return out.toString();
        }
    }

    private ThreeWayMerge() {
    }

    /** One change from the base: lines {@code [start, end)} of the base become {@code lines}. */
    private record Change(boolean left, int start, int end, List<String> lines) {
    }

    /**
     * Merges.
     *
     * @param ignoreWhitespace two changes that differ only in whitespace are taken from the left
     *                         instead of being a conflict
     */
    public static Result merge(String base, String left, String right, boolean ignoreWhitespace) {
        List<String> baseLines = lines(base);
        List<Change> changes = new ArrayList<>();
        changes.addAll(changes(baseLines, lines(left), true));
        changes.addAll(changes(baseLines, lines(right), false));
        changes.sort(Comparator.comparingInt(Change::start).thenComparingInt(Change::end));

        List<Segment> segments = new ArrayList<>();
        StringBuilder resolved = new StringBuilder();
        int cursor = 0;
        int i = 0;
        while (i < changes.size()) {
            // a group: changes whose base ranges overlap or touch
            int lo = changes.get(i).start();
            int hi = changes.get(i).end();
            int j = i + 1;
            while (j < changes.size() && changes.get(j).start() <= hi) {
                hi = Math.max(hi, changes.get(j).end());
                j++;
            }
            List<Change> group = changes.subList(i, j);
            append(resolved, baseLines, cursor, lo);
            boolean fromLeft = group.stream().anyMatch(Change::left);
            boolean fromRight = group.stream().anyMatch(c -> !c.left());
            String leftText = apply(baseLines, lo, hi, group, true);
            String rightText = apply(baseLines, lo, hi, group, false);
            if (!fromRight) {
                resolved.append(leftText);
            } else if (!fromLeft) {
                resolved.append(rightText);
            } else if (leftText.equals(rightText) || ignoreWhitespace && normalize(leftText).equals(normalize(rightText))) {
                resolved.append(leftText);
            } else {
                if (!resolved.isEmpty()) {
                    segments.add(new Resolved(resolved.toString()));
                    resolved.setLength(0);
                }
                StringBuilder baseText = new StringBuilder();
                append(baseText, baseLines, lo, hi);
                segments.add(new Conflict(baseText.toString(), leftText, rightText));
            }
            cursor = hi;
            i = j;
        }
        append(resolved, baseLines, cursor, baseLines.size());
        if (!resolved.isEmpty()) {
            segments.add(new Resolved(resolved.toString()));
        }
        return new Result(List.copyOf(segments));
    }

    private static List<Change> changes(List<String> base, List<String> other, boolean left) {
        List<Change> changes = new ArrayList<>();
        for (AbstractDelta<String> delta : DiffUtils.diff(base, other).getDeltas()) {
            int start = delta.getSource().getPosition();
            changes.add(new Change(left, start, start + delta.getSource().size(), delta.getTarget().getLines()));
        }
        return changes;
    }

    /** The base lines {@code [lo, hi)} with one side's changes of the group applied. */
    private static String apply(List<String> base, int lo, int hi, List<Change> group, boolean left) {
        StringBuilder text = new StringBuilder();
        int cursor = lo;
        for (Change change : group) {
            if (change.left() != left) {
                continue;
            }
            append(text, base, cursor, change.start());
            change.lines().forEach(text::append);
            cursor = Math.max(cursor, change.end());
        }
        append(text, base, cursor, hi);
        return text.toString();
    }

    private static void append(StringBuilder out, List<String> lines, int from, int to) {
        for (int k = from; k < to; k++) {
            out.append(lines.get(k));
        }
    }

    /** Splits into lines that keep their line terminator, so the merge rebuilds the text exactly. */
    static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        int start = 0;
        for (int k = 0; k < text.length(); k++) {
            if (text.charAt(k) == '\n') {
                lines.add(text.substring(start, k + 1));
                start = k + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    private static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").replaceAll(" ?([<>/=]) ?", "$1");
    }

    private static String endWithNewline(String text) {
        return text.isEmpty() || text.endsWith("\n") ? text : text + "\n";
    }

    // ---- conflicts in an edited text ----------------------------------------------------------

    /**
     * A conflict block found in a text (as written by {@link Result#text}).
     *
     * @param start  offset of the {@code <<<<<<<} line
     * @param end    offset after the {@code >>>>>>>} line (its newline included)
     * @param left   the left part
     * @param base   the base part, or {@code null} when the block has none
     * @param right  the right part
     */
    public record Block(int start, int end, String left, String base, String right) {
    }

    private static final Pattern BLOCK = Pattern.compile(
            "(?m)^<<<<<<<[^\\n]*\\n(.*?)(?:^\\|\\|\\|\\|\\|\\|\\|[^\\n]*\\n(.*?))?^=======[^\\n]*\\n(.*?)^>>>>>>>[^\\n]*(?:\\n|\\z)",
            Pattern.DOTALL);

    /** @return the conflict blocks still in {@code text}, in order */
    public static List<Block> blocks(String text) {
        List<Block> blocks = new ArrayList<>();
        Matcher matcher = BLOCK.matcher(text);
        while (matcher.find()) {
            blocks.add(new Block(matcher.start(), matcher.end(), matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return blocks;
    }
}
