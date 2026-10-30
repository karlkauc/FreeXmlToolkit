package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The per-document grid view state: expand state, selection, column widths and table toggles by key. */
class GridViewStateTest {

    @Test
    void expandStateIsRememberedByKey() {
        GridViewState state = new GridViewState();
        assertNull(state.expandedState("/a"));
        state.setExpanded("/a", true);
        state.setExpanded("/a/b[2]", false);
        assertEquals(Boolean.TRUE, state.expandedState("/a"));
        assertEquals(Boolean.FALSE, state.expandedState("/a/b[2]"));
        state.setExpanded(null, true);
        assertNull(state.expandedState(null));
        state.clearExpanded();
        assertNull(state.expandedState("/a"));
    }

    @Test
    void nameColumnWidthsAreKeyedByBlockWithTheRootBlockAsEmptyKey() {
        GridViewState state = new GridViewState();
        assertFalse(state.hasNameColumnWidths());
        state.setNameColumnWidth(null, 150.0);
        state.setNameColumnWidth("/a", 200.0);
        assertEquals(150.0, state.nameColumnWidth(GridViewState.ROOT_BLOCK));
        assertEquals(150.0, state.nameColumnWidth(null));
        assertEquals(200.0, state.nameColumnWidth("/a"));
        assertTrue(state.hasNameColumnWidths());
        state.setNameColumnWidth("/a", null);
        assertNull(state.nameColumnWidth("/a"));
        state.clearNameColumnWidths();
        assertNull(state.nameColumnWidth(null));
    }

    @Test
    void groupsAreTablesUnlessSwitchedOffAndRememberTheFlip() {
        GridViewState state = new GridViewState();
        assertTrue(state.isTable("/root/item"));
        state.setTable("/root/item", false);
        assertFalse(state.isTable("/root/item"));
        state.setTable("/root/item", true);
        assertTrue(state.isTable("/root/item"));
        assertFalse(state.isFlipped("/root/item"));
        state.setFlipped("/root/item", true);
        assertTrue(state.isFlipped("/root/item"));
        state.setFlipped("/root/item", false);
        assertFalse(state.isFlipped("/root/item"));
    }

    @Test
    void openCellsAreRememberedPerGroup() {
        GridViewState state = new GridViewState();
        assertTrue(state.cellExpansion("/root/item").isEmpty());
        state.setCellExpansion("/root/item", java.util.Set.of("0:address", "0:address:2"));
        assertEquals(java.util.Set.of("0:address", "0:address:2"), state.cellExpansion("/root/item"));
        state.setCellExpansion("/root/item", java.util.Set.of());
        assertTrue(state.cellExpansion("/root/item").isEmpty(), "an empty set forgets the group");
    }
}
