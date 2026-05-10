package org.fxt.freexmltoolkit.controls.diff;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffMergeTest {

    @Test
    void applyChunk_leftToRight_changesOnlyRight() {
        String left = "a\nB\nc";
        String right = "a\nb\nc";
        List<DiffChunk> chunks = DiffEngine.compute(left, right);
        DiffChunk change = chunks.stream().filter(c -> !c.isEqual()).findFirst().orElseThrow();

        DiffMerge.Result r = DiffMerge.applyChunk(left, right, change, DiffMerge.Direction.LEFT_TO_RIGHT);

        assertEquals(left, r.left(), "left side must remain unchanged");
        assertEquals(left, r.right(), "right should now match left");
    }

    @Test
    void applyChunk_rightToLeft_changesOnlyLeft() {
        String left = "a\nB\nc";
        String right = "a\nb\nc";
        List<DiffChunk> chunks = DiffEngine.compute(left, right);
        DiffChunk change = chunks.stream().filter(c -> !c.isEqual()).findFirst().orElseThrow();

        DiffMerge.Result r = DiffMerge.applyChunk(left, right, change, DiffMerge.Direction.RIGHT_TO_LEFT);

        assertEquals(right, r.right(), "right side must remain unchanged");
        assertEquals(right, r.left(), "left should now match right");
    }

    @Test
    void applyAll_leftToRight_makesRightEqualLeft() {
        String left = "a\nB\nc\nD\ne\nF";
        String right = "a\nb\nc\nd\ne\nf";

        List<DiffChunk> chunks = DiffEngine.compute(left, right);
        DiffMerge.Result r = DiffMerge.applyAll(left, right, chunks, DiffMerge.Direction.LEFT_TO_RIGHT);
        assertEquals(left, r.left());
        assertEquals(left, r.right());
    }

    @Test
    void applyAll_rightToLeft_makesLeftEqualRight() {
        String left = "a\nB\nc\nD\ne\nF";
        String right = "a\nb\nc\nd\ne\nf";

        List<DiffChunk> chunks = DiffEngine.compute(left, right);
        DiffMerge.Result r = DiffMerge.applyAll(left, right, chunks, DiffMerge.Direction.RIGHT_TO_LEFT);
        assertEquals(right, r.right());
        assertEquals(right, r.left());
    }

    @Test
    void applyAll_handlesIndexShiftWhenChunksDifferInLineCount() {
        // Left has 5 lines; right inserts two extra lines and removes one.
        // Index shift would be wrong if iteration were forward; we apply in reverse.
        String left = "a\nb\nc\nd\ne";
        String right = "a\nb\nNEW1\nNEW2\nc\ne";

        List<DiffChunk> chunks = DiffEngine.compute(left, right);

        DiffMerge.Result toRight = DiffMerge.applyAll(left, right, chunks, DiffMerge.Direction.LEFT_TO_RIGHT);
        assertEquals(left, toRight.right(), "applying all L->R should produce text identical to left");

        DiffMerge.Result toLeft = DiffMerge.applyAll(left, right, chunks, DiffMerge.Direction.RIGHT_TO_LEFT);
        assertEquals(right, toLeft.left(), "applying all R->L should produce text identical to right");
    }

    @Test
    void applyChunk_pureInsert_addsLinesOnLeft() {
        String left = "a\nb";
        String right = "a\nINS\nb";
        List<DiffChunk> chunks = DiffEngine.compute(left, right);
        DiffChunk insert = chunks.stream()
                .filter(c -> c.getType() == DiffChunk.Type.INSERT)
                .findFirst().orElseThrow();

        DiffMerge.Result r = DiffMerge.applyChunk(left, right, insert, DiffMerge.Direction.RIGHT_TO_LEFT);
        assertEquals(right, r.left());
        assertEquals(right, r.right());
    }

    @Test
    void applyChunk_pureDelete_removesLinesFromRight() {
        String left = "a\nb";
        String right = "a\nDEL\nb";
        List<DiffChunk> chunks = DiffEngine.compute(left, right);
        DiffChunk insertOnRight = chunks.stream()
                .filter(c -> !c.isEqual())
                .findFirst().orElseThrow();

        DiffMerge.Result r = DiffMerge.applyChunk(left, right, insertOnRight, DiffMerge.Direction.LEFT_TO_RIGHT);
        assertEquals(left, r.right());
    }
}
