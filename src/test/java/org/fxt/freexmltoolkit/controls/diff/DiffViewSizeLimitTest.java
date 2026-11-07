package org.fxt.freexmltoolkit.controls.diff;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Two very large documents are refused before the diff exhausts the heap. */
class DiffViewSizeLimitTest {

    @Test
    void documentsAboveTheCombinedLimitAreTooLarge() {
        long mb = 1024L * 1024;
        assertFalse(DiffView.tooLargeToCompare(10 * mb, 10 * mb));
        assertFalse(DiffView.tooLargeToCompare(DiffView.MAX_COMPARE_CHARS, 0));
        assertTrue(DiffView.tooLargeToCompare(166 * mb, 177 * mb));
        assertTrue(DiffView.tooLargeMessage(166 * mb, 177 * mb).contains("343 MB"));
    }
}
