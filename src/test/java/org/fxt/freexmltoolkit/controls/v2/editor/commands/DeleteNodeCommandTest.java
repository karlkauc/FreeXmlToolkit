package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for DeleteNodeCommand.
 * Tests basic functionality, undo/redo, property change events, and error handling.
 *
 * @since 2.0
 */
class DeleteNodeCommandTest {

    private XsdElement parentElement;
    private XsdElement childElement;

    @BeforeEach
    void setUp() {
        parentElement = new XsdElement("parent");
        childElement = new XsdElement("child");
        parentElement.addChild(childElement);
    }

    // ========== Basic Functionality Tests ==========

    @Test
    @DisplayName("execute() should remove node from parent and return true")
    void testExecuteRemovesNode() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertFalse(parentElement.getChildren().contains(childElement), "Child should be removed from parent");
        assertEquals(0, parentElement.getChildren().size(), "Parent should have no children");
    }

    @Test
    @DisplayName("undo() should restore node to parent at original index")
    void testUndoRestoresNode() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertTrue(parentElement.getChildren().contains(childElement), "Child should be restored to parent");
        assertEquals(1, parentElement.getChildren().size(), "Parent should have one child");
        assertSame(childElement, parentElement.getChildren().get(0), "Child should be at index 0");
    }

    @Test
    @DisplayName("undo() should restore node at correct index when parent has multiple children")
    void testUndoRestoresNodeAtCorrectIndex() {
        // Arrange
        XsdElement child1 = new XsdElement("child1");
        XsdElement child2 = new XsdElement("child2");
        XsdElement child3 = new XsdElement("child3");

        parentElement.addChild(child1);
        parentElement.addChild(child2);
        parentElement.addChild(child3);

        // Delete child2 (index 1)
        DeleteNodeCommand command = new DeleteNodeCommand(child2);
        command.execute();

        // Act
        command.undo();

        // Assert
        assertEquals(4, parentElement.getChildren().size(), "Parent should have 4 children");
        assertSame(child2, parentElement.getChildren().get(2), "child2 should be at index 2 (after original child at index 0)");
    }

    @Test
    @DisplayName("canUndo() should return true when node has parent")
    void testCanUndoWithParent() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true when node has parent");
    }

    @Test
    @DisplayName("canUndo() should return false when trying to delete root node")
    void testCanUndoRootNode() {
        // Arrange - create a root node without parent
        XsdElement rootElement = new XsdElement("root");
        DeleteNodeCommand command = new DeleteNodeCommand(rootElement);

        // Assert
        assertFalse(command.canUndo(), "canUndo() should return false for root node");
    }

    @Test
    @DisplayName("getDescription() should return descriptive text with node type and name")
    void testGetDescription() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.toLowerCase().contains("delete"), "Description should contain 'delete'");
        assertTrue(description.contains("child"), "Description should contain node name");
        assertTrue(description.toLowerCase().contains("element"), "Description should contain node type");
    }

    @Test
    @DisplayName("canMergeWith() should always return false")
    void testCanMergeWith() {
        // Arrange
        XsdElement anotherChild = new XsdElement("anotherChild");
        parentElement.addChild(anotherChild);

        DeleteNodeCommand command1 = new DeleteNodeCommand(childElement);
        DeleteNodeCommand command2 = new DeleteNodeCommand(anotherChild);

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Delete commands should not be mergeable");
    }

    // ========== PropertyChangeEvent Tests ==========

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent for 'children' property on parent")
    void testExecuteFiresPropertyChangeEvent() {
        // Arrange
        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        parentElement.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Act
        command.execute();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("children", capturedEvent.get().getPropertyName(), "Property name should be 'children'");
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent for 'children' property on parent")
    void testUndoFiresPropertyChangeEvent() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);
        command.execute();

        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        parentElement.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        // Act
        command.undo();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("children", capturedEvent.get().getPropertyName(), "Property name should be 'children'");
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when node is null")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new DeleteNodeCommand(null);
        }, "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("execute() should return false when trying to delete root node")
    void testExecuteReturnsFalseForRootNode() {
        // Arrange - create a root node without parent
        XsdElement rootElement = new XsdElement("root");
        DeleteNodeCommand command = new DeleteNodeCommand(rootElement);

        // Act
        boolean result = command.execute();

        // Assert
        assertFalse(result, "execute() should return false for root node");
    }

    @Test
    @DisplayName("undo() should return false when trying to undo deletion of root node")
    void testUndoReturnsFalseForRootNode() {
        // Arrange - create a root node without parent
        XsdElement rootElement = new XsdElement("root");
        DeleteNodeCommand command = new DeleteNodeCommand(rootElement);

        // Act
        boolean result = command.undo();

        // Assert
        assertFalse(result, "undo() should return false for root node");
    }

    // ========== Multiple Execute/Undo Tests ==========

    @Test
    @DisplayName("Multiple execute() calls should be idempotent")
    void testMultipleExecuteCalls() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Act
        command.execute();
        command.execute();

        // Assert
        assertFalse(parentElement.getChildren().contains(childElement), "Child should still be deleted");
        assertEquals(0, parentElement.getChildren().size(), "Parent should still have no children");
    }

    @Test
    @DisplayName("Execute-undo-execute sequence should work correctly")
    void testExecuteUndoExecuteSequence() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Act & Assert
        command.execute();
        assertFalse(parentElement.getChildren().contains(childElement), "Child should be deleted");

        command.undo();
        assertTrue(parentElement.getChildren().contains(childElement), "Child should be restored");

        command.execute();
        assertFalse(parentElement.getChildren().contains(childElement), "Child should be deleted again");
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should preserve parent reference correctly during delete and undo")
    void testParentReferencePreservation() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Act & Assert - before delete
        assertSame(parentElement, childElement.getParent(), "Child should have parent reference");

        command.execute();
        assertNull(childElement.getParent(), "Child should have null parent after deletion");

        command.undo();
        assertSame(parentElement, childElement.getParent(), "Child should have parent reference restored");
    }

    @Test
    @DisplayName("Should handle deleting first child correctly")
    void testDeleteFirstChild() {
        // Arrange
        XsdElement child1 = new XsdElement("child1");
        XsdElement child2 = new XsdElement("child2");
        parentElement.addChild(child1);
        parentElement.addChild(child2);

        DeleteNodeCommand command = new DeleteNodeCommand(childElement); // This is at index 0

        // Act
        command.execute();

        // Assert
        assertEquals(2, parentElement.getChildren().size(), "Parent should have 2 children");
        assertSame(child1, parentElement.getChildren().get(0), "child1 should now be at index 0");

        // Undo
        command.undo();
        assertEquals(3, parentElement.getChildren().size(), "Parent should have 3 children");
        assertSame(childElement, parentElement.getChildren().get(0), "childElement should be restored at index 0");
    }

    @Test
    @DisplayName("Should handle deleting last child correctly")
    void testDeleteLastChild() {
        // Arrange
        XsdElement child1 = new XsdElement("child1");
        XsdElement child2 = new XsdElement("child2");
        parentElement.addChild(child1);
        parentElement.addChild(child2);

        DeleteNodeCommand command = new DeleteNodeCommand(child2); // This is the last child

        // Act
        command.execute();

        // Assert
        assertEquals(2, parentElement.getChildren().size(), "Parent should have 2 children");
        assertSame(child1, parentElement.getChildren().get(1), "child1 should still be at index 1");

        // Undo
        command.undo();
        assertEquals(3, parentElement.getChildren().size(), "Parent should have 3 children");
        assertSame(child2, parentElement.getChildren().get(2), "child2 should be restored at index 2");
    }

    @Test
    @DisplayName("Should handle deleting only child correctly")
    void testDeleteOnlyChild() {
        // Arrange
        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Act
        command.execute();

        // Assert
        assertEquals(0, parentElement.getChildren().size(), "Parent should have no children");
        assertFalse(parentElement.hasChildren(), "Parent should report no children");

        // Undo
        command.undo();
        assertEquals(1, parentElement.getChildren().size(), "Parent should have 1 child");
        assertTrue(parentElement.hasChildren(), "Parent should report having children");
    }

    @Test
    @DisplayName("Should handle node with children being deleted")
    void testDeleteNodeWithChildren() {
        // Arrange
        XsdElement grandchild = new XsdElement("grandchild");
        childElement.addChild(grandchild);

        DeleteNodeCommand command = new DeleteNodeCommand(childElement);

        // Act
        command.execute();

        // Assert
        assertFalse(parentElement.getChildren().contains(childElement), "Child should be deleted");
        assertTrue(childElement.getChildren().contains(grandchild), "Grandchild should still be in deleted child");

        // Undo
        command.undo();
        assertTrue(parentElement.getChildren().contains(childElement), "Child should be restored");
        assertTrue(childElement.getChildren().contains(grandchild), "Grandchild should still be present");
    }
}
