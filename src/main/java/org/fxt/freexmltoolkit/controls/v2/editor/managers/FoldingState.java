package org.fxt.freexmltoolkit.controls.v2.editor.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages folding state and stores original text for folded regions.
 * This allows proper fold/unfold operations with text restoration.
 * Supports nested folding by preserving fold states of inner regions.
 */
public class FoldingState {

    /**
     * Creates a new FoldingState instance.
     */
    public FoldingState() {
        // Default constructor
    }

    // Maps start line -> original folded text (lines between start and end)
    private final Map<Integer, String[]> foldedTextStorage = new HashMap<>();

    // Maps outer folded line -> map of inner folded lines (for nested folding)
    // This preserves which lines were folded inside a region that gets folded
    private final Map<Integer, Map<Integer, String[]>> nestedFoldedStorage = new HashMap<>();

    /**
     * Stores the folded text for a region.
     *
     * @param startLine the start line (0-based)
     * @param lines the original lines that were folded
     */
    public void storeFoldedText(int startLine, String[] lines) {
        foldedTextStorage.put(startLine, lines);
    }

    /**
     * Gets the stored folded text for a region.
     *
     * @param startLine the start line (0-based)
     * @return the original folded lines, or null if not found
     */
    public String[] getFoldedText(int startLine) {
        return foldedTextStorage.get(startLine);
    }

    /**
     * Removes stored folded text for a region.
     *
     * @param startLine the start line (0-based)
     */
    public void removeFoldedText(int startLine) {
        foldedTextStorage.remove(startLine);
    }

    /**
     * Clears all folded text storage.
     */
    public void clear() {
        foldedTextStorage.clear();
    }

    /**
     * Checks if text is stored for a region.
     *
     * @param startLine the start line (0-based)
     * @return true if text is stored
     */
    public boolean hasFoldedText(int startLine) {
        return foldedTextStorage.containsKey(startLine);
    }

    /**
     * Stores nested folded regions when folding an outer region.
     * This preserves which inner lines were already folded.
     *
     * @param outerStartLine the start line of the outer region being folded
     * @param innerFoldedLines map of inner folded lines (startLine -> lines)
     */
    public void storeNestedFolds(int outerStartLine, Map<Integer, String[]> innerFoldedLines) {
        if (!innerFoldedLines.isEmpty()) {
            nestedFoldedStorage.put(outerStartLine, new HashMap<>(innerFoldedLines));
        }
    }

    /**
     * Gets stored nested folds for an outer region.
     *
     * @param outerStartLine the start line of the outer region
     * @return map of inner folded lines, or null if none
     */
    public Map<Integer, String[]> getNestedFolds(int outerStartLine) {
        return nestedFoldedStorage.get(outerStartLine);
    }

    /**
     * Removes nested fold storage for an outer region.
     *
     * @param outerStartLine the start line of the outer region
     */
    public void removeNestedFolds(int outerStartLine) {
        nestedFoldedStorage.remove(outerStartLine);
    }

    /**
     * Gets all currently folded lines.
     *
     * @return set of all folded start lines
     */
    public Set<Integer> getAllFoldedLines() {
        return foldedTextStorage.keySet();
    }
}
