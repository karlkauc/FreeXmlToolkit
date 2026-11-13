package org.fxmisc.flowless;

/**
 * FreeXmlToolkit patch (see {@code src/flowless/PATCHES.md}): items that take no space in the viewport,
 * e.g. the folded lines of a code editor.
 * <p>
 * Without it, a hidden item (a cell of length 0) covers nothing, so filling the viewport walks through
 * every hidden item and builds a cell for each - folding 24,000 lines built 24,000 cells and froze the
 * application. With it, the {@link Navigator} skips hidden items without creating cells, and the
 * {@link SizeTracker} leaves them out of the scroll estimates.
 * <p>
 * {@link #isHidden(int)} must be cheap and exact for the current items; the counting methods are used for
 * estimates only (scroll bar size and position) and may lag behind briefly. Call
 * {@link VirtualFlow#hiddenItemsChanged()} after the set of hidden items changed.
 */
public interface HiddenItems {

    /** No item is hidden (the original behaviour). */
    HiddenItems NONE = index -> false;

    /** @return whether the item takes no space and gets no cell */
    boolean isHidden(int itemIndex);

    /**
     * @return the last index of the run of hidden items that contains the hidden {@code itemIndex}, so a
     *         forward walk can jump over the whole run (default: the item itself)
     */
    default int hiddenRunEnd(int itemIndex) {
        return itemIndex;
    }

    /**
     * @return the first index of the run of hidden items that contains the hidden {@code itemIndex}, so a
     *         backward walk can jump over the whole run (default: the item itself)
     */
    default int hiddenRunStart(int itemIndex) {
        return itemIndex;
    }

    /** @return the number of hidden items before {@code itemIndex} (estimates only) */
    default int hiddenBefore(int itemIndex) {
        return 0;
    }

    /** @return the number of hidden items (estimates only) */
    default int hiddenCount() {
        return 0;
    }

    /**
     * @return the index of the item that is the {@code shownIndex}-th shown item (0-based), counting only
     *         items that are not hidden (estimates only: jumping to a scroll position)
     */
    default int itemIndexOfShown(int shownIndex) {
        return shownIndex;
    }
}
