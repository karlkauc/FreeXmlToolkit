package org.fxt.freexmltoolkit.service.xmledit;

import java.util.Comparator;
import java.util.List;

/**
 * An ordered set of non-overlapping {@link TextEdit}s against one document
 * snapshot. Edits are normalized to descending start order so applying them
 * never shifts the offsets of edits still to come (the same contract as the
 * SQF engine's edit plans).
 */
public record EditPlan(List<TextEdit> edits) {

    public EditPlan {
        List<TextEdit> sorted = edits.stream()
                .sorted(Comparator.comparingInt(TextEdit::start).reversed())
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            // sorted descending: the later-in-text edit is at i-1
            if (sorted.get(i).end() > sorted.get(i - 1).start()) {
                throw new IllegalArgumentException("Overlapping edits at offset "
                        + sorted.get(i - 1).start());
            }
        }
        edits = sorted;
    }

    public boolean isEmpty() {
        return edits.isEmpty();
    }

    /** @return the smallest start offset of any edit (undefined when empty). */
    public int minStart() {
        return edits.get(edits.size() - 1).start();
    }

    /** @return the largest end offset of any edit (undefined when empty). */
    public int maxEnd() {
        return edits.stream().mapToInt(TextEdit::end).max().orElse(0);
    }

    /** Applies all edits to {@code text} (descending order, offsets stay valid). */
    public String applyTo(String text) {
        StringBuilder sb = new StringBuilder(text);
        for (TextEdit edit : edits) {
            sb.replace(edit.start(), edit.end(), edit.replacement());
        }
        return sb.toString();
    }

    /**
     * The replacement for the contiguous region {@code [minStart, maxEnd)} with all
     * edits applied — apply it via a single native text replace so the editor
     * records exactly one undo step (the quick-fix engine's merge pattern).
     */
    public String mergedRegion(String text) {
        int base = minStart();
        StringBuilder region = new StringBuilder(text.substring(base, maxEnd()));
        for (TextEdit edit : edits) {
            region.replace(edit.start() - base, edit.end() - base, edit.replacement());
        }
        return region.toString();
    }
}
