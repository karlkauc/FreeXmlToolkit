package org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.selection;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SelectionModel class.
 *
 * @author Claude Code
 * @since 2.0
 */
class SelectionModelTest {

    private SelectionModel selectionModel;
    private XmlElement element1;
    private XmlElement element2;
    private XmlElement element3;

    @BeforeEach
    void setUp() {
        selectionModel = new SelectionModel();
        element1 = new XmlElement("element1");
        element2 = new XmlElement("element2");
        element3 = new XmlElement("element3");
    }

    // ==================== Single Selection Tests ====================

    @Test
    void testSetSelectedNode() {
        selectionModel.setSelectedNode(element1);

        assertEquals(element1, selectionModel.getSelectedNode());
        assertTrue(selectionModel.hasSelection());
        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    void testSetSelectedNodeNull() {
        selectionModel.setSelectedNode(element1);
        selectionModel.setSelectedNode(null);

        assertNull(selectionModel.getSelectedNode());
        assertFalse(selectionModel.hasSelection());
        assertEquals(0, selectionModel.getSelectionCount());
    }

    @Test
    void testSetSelectedNodeReplacesOld() {
        selectionModel.setSelectedNode(element1);
        selectionModel.setSelectedNode(element2);

        assertEquals(element2, selectionModel.getSelectedNode());
        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    void testIsSelected() {
        selectionModel.setSelectedNode(element1);

        assertTrue(selectionModel.isSelected(element1));
        assertFalse(selectionModel.isSelected(element2));
        assertFalse(selectionModel.isSelected(null));
    }

    @Test
    void testClearSelection() {
        selectionModel.setSelectedNode(element1);
        selectionModel.clearSelection();

        assertNull(selectionModel.getSelectedNode());
        assertFalse(selectionModel.hasSelection());
    }

    // ==================== Multiple Selection Tests ====================

    @Test
    void testSetMultipleSelectionEnabled() {
        assertFalse(selectionModel.isMultipleSelectionEnabled());

        selectionModel.setMultipleSelectionEnabled(true);

        assertTrue(selectionModel.isMultipleSelectionEnabled());
    }

    @Test
    void testSetSelectedNodesMultiple() {
        selectionModel.setMultipleSelectionEnabled(true);

        List<XmlNode> nodes = Arrays.asList(element1, element2, element3);
        selectionModel.setSelectedNodes(nodes);

        assertEquals(3, selectionModel.getSelectionCount());
        assertEquals(3, selectionModel.getSelectedNodes().size());
        assertTrue(selectionModel.isSelected(element1));
        assertTrue(selectionModel.isSelected(element2));
        assertTrue(selectionModel.isSelected(element3));
    }

    @Test
    void testSetSelectedNodesThrowsIfNotEnabled() {
        // Multiple selection not enabled
        List<XmlNode> nodes = Arrays.asList(element1, element2);

        assertThrows(UnsupportedOperationException.class, () -> {
            selectionModel.setSelectedNodes(nodes);
        });
    }

    @Test
    void testSetSelectedNodesAllowsSingleEvenIfNotEnabled() {
        // Multiple selection not enabled, but single node should work
        List<XmlNode> nodes = List.of(element1);

        assertDoesNotThrow(() -> {
            selectionModel.setSelectedNodes(nodes);
        });

        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    void testAddToSelection() {
        selectionModel.setMultipleSelectionEnabled(true);

        selectionModel.addToSelection(element1);
        selectionModel.addToSelection(element2);

        assertEquals(2, selectionModel.getSelectionCount());
        assertTrue(selectionModel.isSelected(element1));
        assertTrue(selectionModel.isSelected(element2));
    }

    @Test
    void testAddToSelectionThrowsIfNotEnabled() {
        selectionModel.setSelectedNode(element1);

        assertThrows(UnsupportedOperationException.class, () -> {
            selectionModel.addToSelection(element2);
        });
    }

    @Test
    void testAddToSelectionIgnoresDuplicates() {
        selectionModel.setMultipleSelectionEnabled(true);

        selectionModel.addToSelection(element1);
        selectionModel.addToSelection(element1); // Duplicate

        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    void testRemoveFromSelection() {
        selectionModel.setMultipleSelectionEnabled(true);
        selectionModel.setSelectedNodes(Arrays.asList(element1, element2, element3));

        selectionModel.removeFromSelection(element2);

        assertEquals(2, selectionModel.getSelectionCount());
        assertTrue(selectionModel.isSelected(element1));
        assertFalse(selectionModel.isSelected(element2));
        assertTrue(selectionModel.isSelected(element3));
    }

    @Test
    void testToggleSelection() {
        selectionModel.setMultipleSelectionEnabled(true);

        selectionModel.toggleSelection(element1);
        assertTrue(selectionModel.isSelected(element1));

        selectionModel.toggleSelection(element1);
        assertFalse(selectionModel.isSelected(element1));
    }

    @Test
    void testToggleSelectionWithoutMultipleSelection() {
        // Without multiple selection, toggle should just set selection
        selectionModel.toggleSelection(element1);
        assertTrue(selectionModel.isSelected(element1));

        selectionModel.toggleSelection(element2);
        assertTrue(selectionModel.isSelected(element2));
        assertFalse(selectionModel.isSelected(element1)); // Replaced
    }

    @Test
    void testDisablingMultipleSelectionKeepsFirstNode() {
        selectionModel.setMultipleSelectionEnabled(true);
        selectionModel.setSelectedNodes(Arrays.asList(element1, element2, element3));

        selectionModel.setMultipleSelectionEnabled(false);

        assertEquals(1, selectionModel.getSelectionCount());
        assertEquals(element1, selectionModel.getSelectedNode());
    }

    // ==================== PropertyChangeSupport Tests ====================

    @Test
    void testSelectedNodePropertyChange() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        selectionModel.addPropertyChangeListener("selectedNode", listener);
        selectionModel.setSelectedNode(element1);

        assertEquals(1, events.size());
        assertEquals("selectedNode", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals(element1, events.get(0).getNewValue());
    }

    @Test
    void testSelectedNodesPropertyChange() {
        selectionModel.setMultipleSelectionEnabled(true);

        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        selectionModel.addPropertyChangeListener("selectedNodes", listener);
        selectionModel.setSelectedNodes(Arrays.asList(element1, element2));

        assertEquals(1, events.size());
        assertEquals("selectedNodes", events.get(0).getPropertyName());
    }

    @Test
    void testSelectionClearedPropertyChange() {
        selectionModel.setSelectedNode(element1);

        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        selectionModel.addPropertyChangeListener("selectionCleared", listener);
        selectionModel.clearSelection();

        assertEquals(1, events.size());
        assertEquals("selectionCleared", events.get(0).getPropertyName());
    }

    @Test
    void testMultipleSelectionEnabledPropertyChange() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        selectionModel.addPropertyChangeListener("multipleSelectionEnabled", listener);
        selectionModel.setMultipleSelectionEnabled(true);

        assertEquals(1, events.size());
        assertEquals("multipleSelectionEnabled", events.get(0).getPropertyName());
        assertEquals(false, events.get(0).getOldValue());
        assertEquals(true, events.get(0).getNewValue());
    }

    @Test
    void testRemovePropertyChangeListener() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        selectionModel.addPropertyChangeListener("selectedNode", listener);
        selectionModel.setSelectedNode(element1);
        assertEquals(1, events.size());

        selectionModel.removePropertyChangeListener("selectedNode", listener);
        selectionModel.setSelectedNode(element2);
        assertEquals(1, events.size()); // No new event
    }

    // ==================== Edge Cases ====================

    @Test
    void testSetSelectedNodeNullDoesNotThrow() {
        assertDoesNotThrow(() -> {
            selectionModel.setSelectedNode(null);
        });
    }

    @Test
    void testAddToSelectionNull() {
        selectionModel.setMultipleSelectionEnabled(true);

        selectionModel.addToSelection(null);

        assertEquals(0, selectionModel.getSelectionCount());
    }

    @Test
    void testRemoveFromSelectionNull() {
        selectionModel.setMultipleSelectionEnabled(true);
        selectionModel.addToSelection(element1);

        selectionModel.removeFromSelection(null);

        assertEquals(1, selectionModel.getSelectionCount());
    }

    @Test
    void testToggleSelectionNull() {
        selectionModel.setMultipleSelectionEnabled(true);

        selectionModel.toggleSelection(null);

        assertEquals(0, selectionModel.getSelectionCount());
    }

    @Test
    void testGetSelectedNodesIsUnmodifiable() {
        selectionModel.setMultipleSelectionEnabled(true);
        selectionModel.addToSelection(element1);

        List<XmlNode> nodes = selectionModel.getSelectedNodes();

        assertThrows(UnsupportedOperationException.class, () -> {
            nodes.add(element2);
        });
    }

    @Test
    void testClearSelectionWhenEmpty() {
        assertDoesNotThrow(() -> {
            selectionModel.clearSelection();
        });

        assertFalse(selectionModel.hasSelection());
    }

    @Test
    void testToString() {
        selectionModel.setSelectedNode(element1);

        String str = selectionModel.toString();

        assertNotNull(str);
        assertTrue(str.contains("SelectionModel"));
        assertTrue(str.contains("selectedCount=1"));
    }
}
