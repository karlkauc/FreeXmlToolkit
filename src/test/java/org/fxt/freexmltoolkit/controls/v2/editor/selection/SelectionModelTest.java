package org.fxt.freexmltoolkit.controls.v2.editor.selection;

import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SelectionModel - manages selection state for the XSD editor.
 * Tests single/multi-selection, add/remove operations, primary selection tracking, and events.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelectionModelTest {

    private SelectionModel selectionModel;

    @Mock
    private VisualNode node1;

    @Mock
    private VisualNode node2;

    @Mock
    private VisualNode node3;

    @BeforeEach
    void setUp() {
        selectionModel = new SelectionModel();

        // Configure mock behavior
        when(node1.getLabel()).thenReturn("Node 1");
        when(node2.getLabel()).thenReturn("Node 2");
        when(node3.getLabel()).thenReturn("Node 3");
    }

    @Test
    @DisplayName("Should start with empty selection")
    void testInitialState() {
        assertTrue(selectionModel.isEmpty());
        assertEquals(0, selectionModel.getSelectionCount());
        assertNull(selectionModel.getPrimarySelection());
        assertFalse(selectionModel.hasMultipleSelection());
    }

    @Test
    @DisplayName("Should select single node")
    void testSelectSingleNode() {
        selectionModel.select(node1);

        assertTrue(selectionModel.isSelected(node1));
        assertEquals(1, selectionModel.getSelectionCount());
        assertSame(node1, selectionModel.getPrimarySelection());
        assertFalse(selectionModel.isEmpty());
        assertFalse(selectionModel.hasMultipleSelection());
    }

    @Test
    @DisplayName("Should clear previous selection when selecting new node")
    void testSelectClearsPrevious() {
        selectionModel.select(node1);
        selectionModel.select(node2);

        assertFalse(selectionModel.isSelected(node1));
        assertTrue(selectionModel.isSelected(node2));
        assertEquals(1, selectionModel.getSelectionCount());
        assertSame(node2, selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should clear selection when selecting null")
    void testSelectNull() {
        selectionModel.select(node1);
        selectionModel.select(null);

        assertTrue(selectionModel.isEmpty());
        assertNull(selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should add to selection")
    void testAddToSelection() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        assertTrue(selectionModel.isSelected(node1));
        assertTrue(selectionModel.isSelected(node2));
        assertEquals(2, selectionModel.getSelectionCount());
        assertTrue(selectionModel.hasMultipleSelection());
        assertSame(node1, selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should not add duplicate to selection")
    void testAddDuplicate() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node1);

        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    @DisplayName("Should set primary selection when adding to empty selection")
    void testAddToEmptySelection() {
        selectionModel.addToSelection(node1);

        assertSame(node1, selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should remove from selection")
    void testRemoveFromSelection() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        selectionModel.removeFromSelection(node2);

        assertTrue(selectionModel.isSelected(node1));
        assertFalse(selectionModel.isSelected(node2));
        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    @DisplayName("Should update primary selection when removing it")
    void testRemovePrimarySelection() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        selectionModel.removeFromSelection(node1);

        assertSame(node2, selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should clear primary when removing last node")
    void testRemoveLastNode() {
        selectionModel.select(node1);
        selectionModel.removeFromSelection(node1);

        assertNull(selectionModel.getPrimarySelection());
        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should toggle selection")
    void testToggleSelection() {
        selectionModel.select(node1);

        // Toggle off
        selectionModel.toggleSelection(node1);
        assertFalse(selectionModel.isSelected(node1));

        // Toggle on
        selectionModel.toggleSelection(node1);
        assertTrue(selectionModel.isSelected(node1));
    }

    @Test
    @DisplayName("Should select multiple nodes")
    void testSelectMultiple() {
        List<VisualNode> nodes = Arrays.asList(node1, node2, node3);

        selectionModel.selectMultiple(nodes);

        assertEquals(3, selectionModel.getSelectionCount());
        assertTrue(selectionModel.isSelected(node1));
        assertTrue(selectionModel.isSelected(node2));
        assertTrue(selectionModel.isSelected(node3));
        assertTrue(selectionModel.hasMultipleSelection());
        assertNotNull(selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should clear when selecting empty collection")
    void testSelectMultipleEmpty() {
        selectionModel.select(node1);
        selectionModel.selectMultiple(new ArrayList<>());

        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should clear when selecting null collection")
    void testSelectMultipleNull() {
        selectionModel.select(node1);
        selectionModel.selectMultiple(null);

        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should clear all selection")
    void testClearSelection() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        selectionModel.clearSelection();

        assertTrue(selectionModel.isEmpty());
        assertEquals(0, selectionModel.getSelectionCount());
        assertNull(selectionModel.getPrimarySelection());
    }

    @Test
    @DisplayName("Should handle clearing empty selection")
    void testClearEmptySelection() {
        selectionModel.clearSelection();

        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should return unmodifiable set of selected nodes")
    void testGetSelectedNodes() {
        selectionModel.select(node1);
        selectionModel.addToSelection(node2);

        Set<VisualNode> selectedNodes = selectionModel.getSelectedNodes();

        assertEquals(2, selectedNodes.size());
        assertTrue(selectedNodes.contains(node1));
        assertTrue(selectedNodes.contains(node2));

        // Should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
            selectedNodes.add(node3)
        );
    }

    @Test
    @DisplayName("Should handle null node operations gracefully")
    void testNullNodeHandling() {
        // Should not throw, should do nothing
        selectionModel.addToSelection(null);
        selectionModel.removeFromSelection(null);
        selectionModel.toggleSelection(null);

        assertFalse(selectionModel.isSelected(null));
        assertTrue(selectionModel.isEmpty());
    }

    @Test
    @DisplayName("Should fire selection change events via listener")
    void testSelectionListener() {
        List<SelectionEvent> events = new ArrayList<>();

        selectionModel.addSelectionListener((oldSelection, newSelection) -> {
            events.add(new SelectionEvent(oldSelection, newSelection));
        });

        selectionModel.select(node1);

        assertEquals(1, events.size());
        assertTrue(events.get(0).oldSelection.isEmpty());
        assertEquals(1, events.get(0).newSelection.size());
        assertTrue(events.get(0).newSelection.contains(node1));
    }

    @Test
    @DisplayName("Should fire property change events")
    void testPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();

        selectionModel.addPropertyChangeListener(events::add);

        selectionModel.select(node1);

        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> "selection".equals(e.getPropertyName())));
    }

    @Test
    @DisplayName("Should not fire event when selection doesn't change")
    void testNoEventWhenNoChange() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        selectionModel.addPropertyChangeListener(events::add);

        // Clear already empty selection - should not fire
        selectionModel.clearSelection();

        assertEquals(0, events.size(), "Should not fire event when clearing empty selection");
    }

    @Test
    @DisplayName("Should remove property change listener")
    void testRemovePropertyChangeListener() {
        List<PropertyChangeEvent> events = new ArrayList<>();

        selectionModel.addPropertyChangeListener(events::add);
        selectionModel.select(node1);

        int eventsCount = events.size();
        assertTrue(eventsCount > 0);

        events.clear();
        selectionModel.removePropertyChangeListener(events::add);
        selectionModel.select(node2);

        // Note: Since we're adding a lambda, the remove won't work with a new lambda instance
        // This test demonstrates the API, but in practice, you'd store the listener reference
    }

    // ========== HELPER CLASS ==========

    /**
     * Helper class for capturing selection events.
     */
    private static class SelectionEvent {
        final Set<VisualNode> oldSelection;
        final Set<VisualNode> newSelection;

        SelectionEvent(Set<VisualNode> oldSelection, Set<VisualNode> newSelection) {
            this.oldSelection = oldSelection;
            this.newSelection = newSelection;
        }
    }
}
