package org.fxt.freexmltoolkit.controls.v2.editor.core;

import static org.junit.jupiter.api.Assertions.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the generic {@link NodeSelectionModel}, using plain Strings as node type.
 */
class NodeSelectionModelTest {

    private NodeSelectionModel<String> selectionModel;
    private final String node1 = "node1";
    private final String node2 = "node2";
    private final String node3 = "node3";

    @BeforeEach
    void setUp() {
        selectionModel = new NodeSelectionModel<>();
    }

    // ==================== Single Selection ====================

    @Test
    void setSelectedNodeSelectsSingleNode() {
        selectionModel.setSelectedNode(node1);

        assertEquals(node1, selectionModel.getSelectedNode());
        assertTrue(selectionModel.hasSelection());
        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    void setSelectedNodeNullClearsSelection() {
        selectionModel.setSelectedNode(node1);
        selectionModel.setSelectedNode(null);

        assertNull(selectionModel.getSelectedNode());
        assertFalse(selectionModel.hasSelection());
    }

    @Test
    void setSelectedNodeReplacesPreviousSelection() {
        selectionModel.setSelectedNode(node1);
        selectionModel.setSelectedNode(node2);

        assertEquals(node2, selectionModel.getSelectedNode());
        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    void isSelectedReflectsSelection() {
        selectionModel.setSelectedNode(node1);

        assertTrue(selectionModel.isSelected(node1));
        assertFalse(selectionModel.isSelected(node2));
        assertFalse(selectionModel.isSelected(null));
    }

    @Test
    void clearSelectionEmptiesSelection() {
        selectionModel.setSelectedNode(node1);
        selectionModel.clearSelection();

        assertNull(selectionModel.getSelectedNode());
        assertFalse(selectionModel.hasSelection());
    }

    // ==================== Multiple Selection ====================

    @Test
    void multipleSelectionIsDisabledByDefault() {
        assertFalse(selectionModel.isMultipleSelectionEnabled());
        assertTrue(new NodeSelectionModel<String>(true).isMultipleSelectionEnabled());
    }

    @Test
    void setSelectedNodesSelectsAll() {
        selectionModel.setMultipleSelectionEnabled(true);
        selectionModel.setSelectedNodes(Arrays.asList(node1, node2, node3));

        assertEquals(3, selectionModel.getSelectionCount());
        assertTrue(selectionModel.isSelected(node1));
        assertTrue(selectionModel.isSelected(node2));
        assertTrue(selectionModel.isSelected(node3));
    }

    @Test
    void setSelectedNodesThrowsWhenMultipleSelectionDisabled() {
        List<String> nodes = Arrays.asList(node1, node2);
        assertThrows(UnsupportedOperationException.class, () -> selectionModel.setSelectedNodes(nodes));
    }

    @Test
    void setSelectedNodesAllowsSingleNodeWhenDisabled() {
        assertDoesNotThrow(() -> selectionModel.setSelectedNodes(List.of(node1)));
        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    void addToSelectionAppendsAndIgnoresDuplicates() {
        selectionModel.setMultipleSelectionEnabled(true);

        selectionModel.addToSelection(node1);
        selectionModel.addToSelection(node2);
        selectionModel.addToSelection(node1);

        assertEquals(2, selectionModel.getSelectionCount());
    }

    @Test
    void addToSelectionThrowsWhenDisabled() {
        selectionModel.setSelectedNode(node1);
        assertThrows(UnsupportedOperationException.class, () -> selectionModel.addToSelection(node2));
    }

    @Test
    void removeFromSelectionRemovesNode() {
        selectionModel.setMultipleSelectionEnabled(true);
        selectionModel.setSelectedNodes(Arrays.asList(node1, node2, node3));

        selectionModel.removeFromSelection(node2);

        assertEquals(2, selectionModel.getSelectionCount());
        assertFalse(selectionModel.isSelected(node2));
    }

    @Test
    void toggleSelectionAddsAndRemoves() {
        selectionModel.setMultipleSelectionEnabled(true);

        selectionModel.toggleSelection(node1);
        assertTrue(selectionModel.isSelected(node1));

        selectionModel.toggleSelection(node1);
        assertFalse(selectionModel.isSelected(node1));
    }

    @Test
    void toggleSelectionWithoutMultipleSelectionReplaces() {
        selectionModel.toggleSelection(node1);
        selectionModel.toggleSelection(node2);

        assertTrue(selectionModel.isSelected(node2));
        assertFalse(selectionModel.isSelected(node1));
    }

    @Test
    void disablingMultipleSelectionKeepsFirstNode() {
        selectionModel.setMultipleSelectionEnabled(true);
        selectionModel.setSelectedNodes(Arrays.asList(node1, node2, node3));

        selectionModel.setMultipleSelectionEnabled(false);

        assertEquals(1, selectionModel.getSelectionCount());
        assertEquals(node1, selectionModel.getSelectedNode());
    }

    @Test
    void getSelectedNodesIsUnmodifiable() {
        selectionModel.setMultipleSelectionEnabled(true);
        selectionModel.addToSelection(node1);

        List<String> nodes = selectionModel.getSelectedNodes();
        assertThrows(UnsupportedOperationException.class, () -> nodes.add(node2));
    }

    // ==================== Events ====================

    @Test
    void selectedNodeEventIsFired() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        selectionModel.addPropertyChangeListener("selectedNode", events::add);

        selectionModel.setSelectedNode(node1);

        assertEquals(1, events.size());
        assertNull(events.get(0).getOldValue());
        assertEquals(node1, events.get(0).getNewValue());
    }

    @Test
    void selectedNodesEventIsFired() {
        selectionModel.setMultipleSelectionEnabled(true);
        List<PropertyChangeEvent> events = new ArrayList<>();
        selectionModel.addPropertyChangeListener("selectedNodes", events::add);

        selectionModel.setSelectedNodes(Arrays.asList(node1, node2));

        assertEquals(1, events.size());
        assertEquals("selectedNodes", events.get(0).getPropertyName());
    }

    @Test
    void selectionClearedEventIsFired() {
        selectionModel.setSelectedNode(node1);
        List<PropertyChangeEvent> events = new ArrayList<>();
        selectionModel.addPropertyChangeListener("selectionCleared", events::add);

        selectionModel.clearSelection();

        assertEquals(1, events.size());
    }

    @Test
    void multipleSelectionEnabledEventIsFired() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        selectionModel.addPropertyChangeListener("multipleSelectionEnabled", events::add);

        selectionModel.setMultipleSelectionEnabled(true);

        assertEquals(1, events.size());
        assertEquals(false, events.get(0).getOldValue());
        assertEquals(true, events.get(0).getNewValue());
    }

    @Test
    void removedListenerReceivesNoEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;
        selectionModel.addPropertyChangeListener(listener);
        selectionModel.setSelectedNode(node1);
        int count = events.size();

        selectionModel.removePropertyChangeListener(listener);
        selectionModel.setSelectedNode(node2);

        assertEquals(count, events.size());
    }

    // ==================== Edge cases ====================

    @Test
    void nullArgumentsAreIgnored() {
        selectionModel.setMultipleSelectionEnabled(true);

        assertDoesNotThrow(() -> selectionModel.setSelectedNode(null));
        selectionModel.addToSelection(null);
        selectionModel.removeFromSelection(null);
        selectionModel.toggleSelection(null);

        assertEquals(0, selectionModel.getSelectionCount());
        assertDoesNotThrow(() -> selectionModel.clearSelection());
    }

    @Test
    void toStringContainsClassNameAndCount() {
        selectionModel.setSelectedNode(node1);
        String str = selectionModel.toString();

        assertTrue(str.contains("NodeSelectionModel"));
        assertTrue(str.contains("selectedCount=1"));
    }
}
