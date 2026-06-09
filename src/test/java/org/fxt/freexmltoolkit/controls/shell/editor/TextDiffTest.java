package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TextDiff#minimalReplaceRegion(String, String)}, the thin
 * diff layer (P6) that lets the model→text round-trip rewrite only the changed
 * region instead of replacing the whole document.
 */
class TextDiffTest {

    /** Applies the computed minimal region to {@code oldText} and returns the result. */
    private String apply(String oldText, String newText) {
        int[] r = TextDiff.minimalReplaceRegion(oldText, newText);
        return oldText.substring(0, r[0]) + newText.substring(r[0], r[2]) + oldText.substring(r[1]);
    }

    @Test
    void identicalTextProducesAnEmptyNoOpRegion() {
        int[] r = TextDiff.minimalReplaceRegion("abc", "abc");
        assertEquals(r[0], r[1], "no change: start == oldEnd");
        assertEquals(r[0], r[2], "no change: start == newEnd");
    }

    @Test
    void replacementTouchesOnlyTheChangedMiddle() {
        int[] r = TextDiff.minimalReplaceRegion("ABXYCD", "ABZWCD");
        assertEquals(2, r[0], "common prefix 'AB'");
        assertEquals(4, r[1], "old middle ends before 'CD'");
        assertEquals(4, r[2], "new middle ends before 'CD'");
        assertEquals("ABZWCD", apply("ABXYCD", "ABZWCD"));
    }

    @Test
    void handlesInsertion() {
        assertEquals("ABXCD", apply("ABCD", "ABXCD"));
    }

    @Test
    void handlesDeletion() {
        assertEquals("ABCD", apply("ABXCD", "ABCD"));
    }

    @Test
    void handlesEmptyOldText() {
        int[] r = TextDiff.minimalReplaceRegion("", "hello");
        assertEquals(0, r[0]);
        assertEquals(0, r[1]);
        assertEquals(5, r[2]);
        assertEquals("hello", apply("", "hello"));
    }

    @Test
    void localEditInALargeDocumentYieldsASmallRegion() {
        String big = "<root>\n" + "  <item>x</item>\n".repeat(1000) + "</root>";
        // Change a single character deep in the document.
        int idx = big.indexOf("x", big.length() / 2);
        String edited = big.substring(0, idx) + "Y" + big.substring(idx + 1);

        int[] r = TextDiff.minimalReplaceRegion(big, edited);
        assertTrue((r[1] - r[0]) <= 1 && (r[2] - r[0]) <= 1,
                "a one-char change must yield a one-char region, regardless of document size");
        assertEquals(edited, apply(big, edited));
    }
}
