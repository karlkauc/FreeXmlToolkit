package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for MoveNodeCommand.
 * Tests basic functionality, undo/redo, property change events, and error handling.
 *
 * @since 2.0
 */
class MoveNodeCommandTest {

    private XsdElement oldParent;
    private XsdElement newParent;
    private XsdElement nodeToMove;

    @BeforeEach
    void setUp() {
        oldParent = new XsdElement("oldParent");
        newParent = new XsdElement("newParent");
        nodeToMove = new XsdElement("nodeToMove");
        oldParent.addChild(nodeToMove);
    }

    // ========== Basic Functionality Tests ==========

    @Test
    @DisplayName("execute() should move node from old parent to new parent")
    void testExecuteMovesNode() {
        // Arrange
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertFalse(oldParent.getChildren().contains(nodeToMove), "Node should be removed from old parent");
        assertTrue(newParent.getChildren().contains(nodeToMove), "Node should be added to new parent");
        assertSame(newParent, nodeToMove.getParent(), "Node's parent reference should be updated");
    }

    @Test
    @DisplayName("execute() should move node to specific index in new parent")
    void testExecuteMovesNodeToSpecificIndex() {
        // Arrange
        XsdElement existingChild1 = new XsdElement("existingChild1");
        XsdElement existingChild2 = new XsdElement("existingChild2");
        newParent.addChild(existingChild1);
        newParent.addChild(existingChild2);

        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, 1);

        // Act
        command.execute();

        // Assert
        assertEquals(3, newParent.getChildren().size(), "New parent should have 3 children");
        assertSame(existingChild1, newParent.getChildren().get(0), "existingChild1 should be at index 0");
        assertSame(nodeToMove, newParent.getChildren().get(1), "nodeToMove should be at index 1");
        assertSame(existingChild2, newParent.getChildren().get(2), "existingChild2 should be at index 2");
    }

    @Test
    @DisplayName("execute() should append node when index is -1")
    void testExecuteAppendsNodeWhenIndexIsNegative() {
        // Arrange
        XsdElement existingChild = new XsdElement("existingChild");
        newParent.addChild(existingChild);

        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Act
        command.execute();

        // Assert
        assertEquals(2, newParent.getChildren().size(), "New parent should have 2 children");
        assertSame(existingChild, newParent.getChildren().get(0), "existingChild should be at index 0");
        assertSame(nodeToMove, newParent.getChildren().get(1), "nodeToMove should be appended at index 1");
    }

    @Test
    @DisplayName("execute() should append node when index exceeds children size")
    void testExecuteAppendsNodeWhenIndexExceedsSize() {
        // Arrange
        XsdElement existingChild = new XsdElement("existingChild");
        newParent.addChild(existingChild);

        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, 999);

        // Act
        command.execute();

        // Assert
        assertEquals(2, newParent.getChildren().size(), "New parent should have 2 children");
        assertSame(nodeToMove, newParent.getChildren().get(1), "nodeToMove should be appended at end");
    }

    @Test
    @DisplayName("undo() should restore node to original parent and position")
    void testUndoRestoresNodeToOriginalPosition() {
        // Arrange
        XsdElement sibling1 = new XsdElement("sibling1");
        XsdElement sibling2 = new XsdElement("sibling2");
        oldParent.addChild(sibling1);
        oldParent.addChild(sibling2);

        // nodeToMove is at index 0
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertTrue(oldParent.getChildren().contains(nodeToMove), "Node should be restored to old parent");
        assertFalse(newParent.getChildren().contains(nodeToMove), "Node should be removed from new parent");
        assertSame(oldParent, nodeToMove.getParent(), "Node's parent reference should be restored");
        assertSame(nodeToMove, oldParent.getChildren().get(0), "Node should be at original index 0");
    }

    @Test
    @DisplayName("canUndo() should return true when node has old parent")
    void testCanUndo() {
        // Arrange
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true when node has old parent");
    }

    @Test
    @DisplayName("canUndo() should return false when trying to move root node")
    void testCanUndoRootNode() {
        // Arrange - create a root node without parent
        XsdElement rootNode = new XsdElement("root");
        MoveNodeCommand command = new MoveNodeCommand(rootNode, newParent, -1);

        // Assert
        assertFalse(command.canUndo(), "canUndo() should return false for root node");
    }

    @Test
    @DisplayName("getDescription() should return descriptive text")
    void testGetDescription() {
        // Arrange
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.toLowerCase().contains("move"), "Description should contain 'move'");
        assertTrue(description.contains("nodeToMove"), "Description should contain node name");
        assertTrue(description.contains("oldParent"), "Description should contain old parent name");
        assertTrue(description.contains("newParent"), "Description should contain new parent name");
    }

    @Test
    @DisplayName("canMergeWith() should always return false")
    void testCanMergeWith() {
        // Arrange
        MoveNodeCommand command1 = new MoveNodeCommand(nodeToMove, newParent, -1);
        MoveNodeCommand command2 = new MoveNodeCommand(nodeToMove, oldParent, -1);

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Move commands should not be mergeable");
    }

    // ========== PropertyChangeEvent Tests ==========

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent on both old and new parents")
    void testExecuteFiresPropertyChangeEvents() {
        // Arrange
        AtomicInteger oldParentEventCount = new AtomicInteger(0);
        AtomicInteger newParentEventCount = new AtomicInteger(0);

        oldParent.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                oldParentEventCount.incrementAndGet();
            }
        });

        newParent.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                newParentEventCount.incrementAndGet();
            }
        });

        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Act
        command.execute();

        // Assert
        assertEquals(1, oldParentEventCount.get(), "Old parent should fire one PropertyChangeEvent");
        assertEquals(1, newParentEventCount.get(), "New parent should fire one PropertyChangeEvent");
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent on both old and new parents")
    void testUndoFiresPropertyChangeEvents() {
        // Arrange
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);
        command.execute();

        AtomicInteger oldParentEventCount = new AtomicInteger(0);
        AtomicInteger newParentEventCount = new AtomicInteger(0);

        oldParent.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                oldParentEventCount.incrementAndGet();
            }
        });

        newParent.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                newParentEventCount.incrementAndGet();
            }
        });

        // Act
        command.undo();

        // Assert
        assertEquals(1, oldParentEventCount.get(), "Old parent should fire one PropertyChangeEvent");
        assertEquals(1, newParentEventCount.get(), "New parent should fire one PropertyChangeEvent");
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when node is null")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new MoveNodeCommand(null, newParent, -1);
        }, "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when new parent is null")
    void testConstructorThrowsExceptionForNullNewParent() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new MoveNodeCommand(nodeToMove, null, -1);
        }, "Constructor should throw IllegalArgumentException for null new parent");
    }

    @Test
    @DisplayName("execute() should return false when trying to move root node")
    void testExecuteReturnsFalseForRootNode() {
        // Arrange - create a root node without parent
        XsdElement rootNode = new XsdElement("root");
        MoveNodeCommand command = new MoveNodeCommand(rootNode, newParent, -1);

        // Act
        boolean result = command.execute();

        // Assert
        assertFalse(result, "execute() should return false for root node");
    }

    @Test
    @DisplayName("execute() should return false when trying to move node into itself")
    void testExecuteReturnsFalseForMovingNodeIntoItself() {
        // Arrange
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, nodeToMove, -1);

        // Act
        boolean result = command.execute();

        // Assert
        assertFalse(result, "execute() should return false when moving node into itself");
        assertTrue(oldParent.getChildren().contains(nodeToMove), "Node should remain in old parent");
    }

    @Test
    @DisplayName("execute() should return false when trying to move node into its descendant")
    void testExecuteReturnsFalseForMovingNodeIntoDescendant() {
        // Arrange
        XsdElement child = new XsdElement("child");
        XsdElement grandchild = new XsdElement("grandchild");
        nodeToMove.addChild(child);
        child.addChild(grandchild);

        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, grandchild, -1);

        // Act
        boolean result = command.execute();

        // Assert
        assertFalse(result, "execute() should return false when moving node into its descendant");
        assertTrue(oldParent.getChildren().contains(nodeToMove), "Node should remain in old parent");
    }

    @Test
    @DisplayName("undo() should return false when old parent is null")
    void testUndoReturnsFalseWhenOldParentIsNull() {
        // Arrange - create a root node without parent
        XsdElement rootNode = new XsdElement("root");
        MoveNodeCommand command = new MoveNodeCommand(rootNode, newParent, -1);

        // Act
        boolean result = command.undo();

        // Assert
        assertFalse(result, "undo() should return false when old parent is null");
    }

    // ========== Multiple Execute/Undo Tests ==========

    @Test
    @DisplayName("Execute-undo-execute sequence should work correctly")
    void testExecuteUndoExecuteSequence() {
        // Arrange
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Act & Assert
        command.execute();
        assertTrue(newParent.getChildren().contains(nodeToMove), "Node should be in new parent after execute");
        assertFalse(oldParent.getChildren().contains(nodeToMove), "Node should not be in old parent after execute");

        command.undo();
        assertTrue(oldParent.getChildren().contains(nodeToMove), "Node should be in old parent after undo");
        assertFalse(newParent.getChildren().contains(nodeToMove), "Node should not be in new parent after undo");

        command.execute();
        assertTrue(newParent.getChildren().contains(nodeToMove), "Node should be in new parent after re-execute");
        assertFalse(oldParent.getChildren().contains(nodeToMove), "Node should not be in old parent after re-execute");
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should preserve node's children when moving")
    void testPreservesChildrenWhenMoving() {
        // Arrange
        XsdElement child1 = new XsdElement("child1");
        XsdElement child2 = new XsdElement("child2");
        nodeToMove.addChild(child1);
        nodeToMove.addChild(child2);

        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Act
        command.execute();

        // Assert
        assertEquals(2, nodeToMove.getChildren().size(), "Node should still have its children");
        assertTrue(nodeToMove.getChildren().contains(child1), "child1 should still be present");
        assertTrue(nodeToMove.getChildren().contains(child2), "child2 should still be present");
    }

    @Test
    @DisplayName("Should move node between siblings correctly")
    void testMoveNodeBetweenSiblings() {
        // Arrange - create a hierarchy with multiple siblings
        XsdElement sibling1 = new XsdElement("sibling1");
        XsdElement sibling2 = new XsdElement("sibling2");
        XsdElement sibling3 = new XsdElement("sibling3");

        oldParent.addChild(sibling1);
        oldParent.addChild(sibling2);
        oldParent.addChild(sibling3);

        // nodeToMove is at index 0, move it to index 2
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, oldParent, 2);

        // Act
        command.execute();

        // Assert
        assertEquals(4, oldParent.getChildren().size(), "Parent should still have 4 children");
        assertSame(sibling1, oldParent.getChildren().get(0), "sibling1 should be at index 0");
        assertSame(sibling2, oldParent.getChildren().get(1), "sibling2 should be at index 1");
        assertSame(nodeToMove, oldParent.getChildren().get(2), "nodeToMove should be at index 2");
        assertSame(sibling3, oldParent.getChildren().get(3), "sibling3 should be at index 3");
    }

    @Test
    @DisplayName("Should move node to empty parent")
    void testMoveNodeToEmptyParent() {
        // Arrange
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Act
        command.execute();

        // Assert
        assertEquals(1, newParent.getChildren().size(), "New parent should have 1 child");
        assertSame(nodeToMove, newParent.getChildren().get(0), "nodeToMove should be the only child");
    }

    @Test
    @DisplayName("Should handle moving last child from parent")
    void testMoveLastChildFromParent() {
        // Arrange - nodeToMove is the only child
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);

        // Act
        command.execute();

        // Assert
        assertEquals(0, oldParent.getChildren().size(), "Old parent should have no children");
        assertFalse(oldParent.hasChildren(), "Old parent should report no children");
        assertEquals(1, newParent.getChildren().size(), "New parent should have 1 child");
    }

    @Test
    @DisplayName("Should restore correct index when undoing move from middle of sibling list")
    void testUndoRestoresCorrectIndexInMiddle() {
        // Arrange
        XsdElement sibling1 = new XsdElement("sibling1");
        XsdElement sibling2 = new XsdElement("sibling2");

        oldParent.addChild(sibling1);
        oldParent.addChild(sibling2);
        // Now order is: nodeToMove (0), sibling1 (1), sibling2 (2)

        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, newParent, -1);
        command.execute();

        // Act
        command.undo();

        // Assert
        assertEquals(3, oldParent.getChildren().size(), "Old parent should have 3 children");
        assertSame(nodeToMove, oldParent.getChildren().get(0), "nodeToMove should be restored at index 0");
        assertSame(sibling1, oldParent.getChildren().get(1), "sibling1 should still be at index 1");
        assertSame(sibling2, oldParent.getChildren().get(2), "sibling2 should still be at index 2");
    }

    @Test
    @DisplayName("Should handle complex move scenarios with multiple levels")
    void testComplexMoveScenario() {
        // Arrange - create a deeper hierarchy
        XsdElement level1 = new XsdElement("level1");
        XsdElement level2 = new XsdElement("level2");
        oldParent.addChild(level1);
        level1.addChild(level2);

        // Move nodeToMove from oldParent to level2
        MoveNodeCommand command = new MoveNodeCommand(nodeToMove, level2, -1);

        // Act
        command.execute();

        // Assert
        assertFalse(oldParent.getChildren().contains(nodeToMove), "nodeToMove should be removed from oldParent");
        assertTrue(level2.getChildren().contains(nodeToMove), "nodeToMove should be in level2");
        assertSame(level2, nodeToMove.getParent(), "nodeToMove's parent should be level2");

        // Undo
        command.undo();
        assertTrue(oldParent.getChildren().contains(nodeToMove), "nodeToMove should be restored to oldParent");
        assertFalse(level2.getChildren().contains(nodeToMove), "nodeToMove should be removed from level2");
        assertSame(oldParent, nodeToMove.getParent(), "nodeToMove's parent should be oldParent");
    }
}
