package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fxt.freexmltoolkit.controls.shell.editor.LargeFileGuard.Verdict;
import org.junit.jupiter.api.Test;

class LargeFileGuardTest {

    private static final long MB = 1024L * 1024;
    private static final long GB = 1024 * MB;

    @Test
    void smallFilesOpenDirectly() {
        assertEquals(Verdict.OK, LargeFileGuard.check(10 * MB, 4 * GB));
        assertEquals(Verdict.OK, LargeFileGuard.check(LargeFileGuard.WARN_BYTES - 1, 4 * GB));
    }

    @Test
    void largeFilesThatFitAskFirst() {
        assertEquals(Verdict.WARN, LargeFileGuard.check(LargeFileGuard.WARN_BYTES, 4 * GB));
        // telemetry 2.5.1: a 62 MB file with a 4 GB heap
        assertEquals(Verdict.WARN, LargeFileGuard.check(62 * MB, 4 * GB));
    }

    @Test
    void filesThatCannotFitTheHeapAreRefused() {
        // telemetry 2.5.1: a 294 MB file with a 4 GB heap ended in OutOfMemoryErrors
        assertEquals(Verdict.REFUSE, LargeFileGuard.check(294 * MB, 4 * GB));
        assertEquals(Verdict.WARN, LargeFileGuard.check(294 * MB, 16 * GB));
        assertEquals(Verdict.REFUSE, LargeFileGuard.check(60 * MB, 512 * MB));
        assertEquals(Verdict.WARN, LargeFileGuard.check(62 * MB, 4 * GB));
    }

    @Test
    void unknownSizeOrUnboundedHeapNeverRefuses() {
        assertEquals(Verdict.OK, LargeFileGuard.check(-1, 4 * GB));
        assertEquals(Verdict.WARN, LargeFileGuard.check(10 * GB, Long.MAX_VALUE));
    }

    @Test
    void sizesAreHumanReadable() {
        assertEquals("294 MB", LargeFileGuard.formatSize(294 * MB));
        assertEquals("1.5 GB", LargeFileGuard.formatSize(1536 * MB));
        assertEquals("256 MB", LargeFileGuard.formatSize(LargeFileGuard.largestOpenable(4 * GB)));
    }
}
