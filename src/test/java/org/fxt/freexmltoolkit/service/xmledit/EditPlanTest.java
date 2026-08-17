package org.fxt.freexmltoolkit.service.xmledit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class EditPlanTest {

    @Test
    void appliesEditsRegardlessOfInputOrder() {
        String text = "aaa bbb ccc";
        EditPlan plan = new EditPlan(List.of(
                new TextEdit(0, 3, "XX"),
                new TextEdit(8, 11, "YYYY")));
        assertEquals("XX bbb YYYY", plan.applyTo(text));
    }

    @Test
    void rejectsOverlappingEdits() {
        assertThrows(IllegalArgumentException.class, () -> new EditPlan(List.of(
                new TextEdit(0, 5, "x"),
                new TextEdit(4, 8, "y"))));
    }

    @Test
    void adjacentEditsAreAllowed() {
        EditPlan plan = new EditPlan(List.of(
                new TextEdit(0, 2, "x"),
                new TextEdit(2, 4, "y")));
        assertEquals("xy", plan.applyTo("abcd"));
    }

    @Test
    void mergedRegionMatchesFullApply() {
        String text = "<a>1</a><b>2</b><c>3</c>";
        EditPlan plan = new EditPlan(List.of(
                new TextEdit(3, 4, "ONE"),
                new TextEdit(19, 20, "THREE")));
        String merged = plan.mergedRegion(text);
        String viaMerge = text.substring(0, plan.minStart()) + merged + text.substring(plan.maxEnd());
        assertEquals(plan.applyTo(text), viaMerge);
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new TextEdit(-1, 2, "x"));
        assertThrows(IllegalArgumentException.class, () -> new TextEdit(5, 2, "x"));
        assertThrows(IllegalArgumentException.class, () -> new TextEdit(0, 1, null));
    }
}
