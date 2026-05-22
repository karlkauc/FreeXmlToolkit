package org.fxt.freexmltoolkit.controls.diff;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class DiffEngineTest {

    @Test
    void emptyInputs_singleEqualEmptyLine() {
        List<DiffChunk> chunks = DiffEngine.compute("", "");
        assertEquals(1, chunks.size());
        DiffChunk c = chunks.get(0);
        assertEquals(DiffChunk.Type.EQUAL, c.getType());
        assertEquals(0, c.getLeftStart());
        assertEquals(1, c.getLeftEnd());
        assertEquals(0, c.getRightStart());
        assertEquals(1, c.getRightEnd());
    }

    @Test
    void identical_singleEqualChunk() {
        String text = "<a>\n  <b/>\n</a>";
        List<DiffChunk> chunks = DiffEngine.compute(text, text);
        assertEquals(1, chunks.size());
        DiffChunk c = chunks.get(0);
        assertEquals(DiffChunk.Type.EQUAL, c.getType());
        assertEquals(3, c.getLeftLength());
        assertEquals(3, c.getRightLength());
    }

    @Test
    void pureAdditions_insertChunkAtEnd() {
        List<DiffChunk> chunks = DiffEngine.compute(
                "a\nb\nc",
                "a\nb\nc\nd\ne"
        );
        DiffChunk insert = chunks.stream()
                .filter(c -> c.getType() == DiffChunk.Type.INSERT)
                .findFirst()
                .orElseThrow();
        assertEquals(0, insert.getLeftLength());
        assertEquals(2, insert.getRightLength());
    }

    @Test
    void pureRemovals_deleteChunk() {
        List<DiffChunk> chunks = DiffEngine.compute(
                "a\nb\nc\nd\ne",
                "a\nb\ne"
        );
        DiffChunk del = chunks.stream()
                .filter(c -> c.getType() == DiffChunk.Type.DELETE)
                .findFirst()
                .orElseThrow();
        assertEquals(2, del.getLeftLength());
        assertEquals(0, del.getRightLength());
    }

    @Test
    void modifiedLine_changeChunkWithWordPatches() {
        List<DiffChunk> chunks = DiffEngine.compute(
                "<root>\n  <a value=\"old\"/>\n</root>",
                "<root>\n  <a value=\"new\"/>\n</root>"
        );
        DiffChunk change = chunks.stream()
                .filter(c -> c.getType() == DiffChunk.Type.CHANGE)
                .findFirst()
                .orElseThrow();
        assertEquals(1, change.getLeftLength());
        assertEquals(1, change.getRightLength());
        assertNotNull(change.getWordPatches());
        assertEquals(1, change.getWordPatches().size());
        DiffChunk.LineWordDiff lw = change.getWordPatches().get(0);
        assertTrue(lw.getLeftSegments().stream()
                .anyMatch(s -> s.getKind() == DiffChunk.LineWordDiff.SegmentKind.REMOVED));
        assertTrue(lw.getRightSegments().stream()
                .anyMatch(s -> s.getKind() == DiffChunk.LineWordDiff.SegmentKind.ADDED));
    }

    @Test
    void coverageInvariant_chunksCoverWholeInput() {
        String left = "a\nb\nc\nd\ne";
        String right = "a\nB\nc\nX\nY";
        List<DiffChunk> chunks = DiffEngine.compute(left, right);

        int leftCovered = chunks.stream().mapToInt(DiffChunk::getLeftLength).sum();
        int rightCovered = chunks.stream().mapToInt(DiffChunk::getRightLength).sum();
        assertEquals(5, leftCovered);
        assertEquals(5, rightCovered);

        for (int i = 1; i < chunks.size(); i++) {
            DiffChunk prev = chunks.get(i - 1);
            DiffChunk cur = chunks.get(i);
            assertEquals(prev.getLeftEnd(), cur.getLeftStart());
            assertEquals(prev.getRightEnd(), cur.getRightStart());
        }
    }

    @Test
    void largeInput_completesQuickly() {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            a.append("line-").append(i).append('\n');
            b.append("line-").append(i == 2500 ? "MODIFIED" : i).append('\n');
        }
        long start = System.currentTimeMillis();
        List<DiffChunk> chunks = DiffEngine.compute(a.toString(), b.toString());
        long elapsed = System.currentTimeMillis() - start;
        assertFalse(chunks.isEmpty());
        assertTrue(elapsed < 2000, "Diff of 5000 lines should complete in under 2s, took " + elapsed + "ms");
    }
}
