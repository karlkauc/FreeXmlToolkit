package org.fxt.freexmltoolkit.controls.shell.editor;

/**
 * A thin diff layer (P6) for the model→text round-trip: instead of replacing the
 * whole editor document, it computes the single contiguous region that actually
 * changed (the span between the common prefix and common suffix of the old and new
 * text) so only that region needs to be rewritten.
 *
 * <p>For a local edit this region is small regardless of document size, which avoids
 * the cost of re-styling and re-laying-out the entire code area and preserves the
 * caret/scroll position in the untouched parts of the document.</p>
 */
public final class TextDiff {

    private TextDiff() {
    }

    /**
     * Computes the minimal contiguous region to replace so that {@code oldText}
     * becomes {@code newText}.
     *
     * @param oldText the current text
     * @param newText the desired text
     * @return {@code [start, oldEnd, newEnd]} where {@code oldText[0,start) ==
     *     newText[0,start)} and {@code oldText[oldEnd,len) == newText[newEnd,len)};
     *     applying it means replacing {@code oldText[start, oldEnd)} with
     *     {@code newText[start, newEnd)}. If the texts are equal,
     *     {@code start == oldEnd == newEnd}.
     */
    public static int[] minimalReplaceRegion(String oldText, String newText) {
        int oldLen = oldText.length();
        int newLen = newText.length();
        int max = Math.min(oldLen, newLen);

        int prefix = 0;
        while (prefix < max && oldText.charAt(prefix) == newText.charAt(prefix)) {
            prefix++;
        }

        int oldEnd = oldLen;
        int newEnd = newLen;
        while (oldEnd > prefix && newEnd > prefix
                && oldText.charAt(oldEnd - 1) == newText.charAt(newEnd - 1)) {
            oldEnd--;
            newEnd--;
        }

        return new int[]{prefix, oldEnd, newEnd};
    }
}
