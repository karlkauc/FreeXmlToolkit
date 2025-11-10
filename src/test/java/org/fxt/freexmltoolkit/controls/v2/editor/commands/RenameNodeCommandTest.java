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
 * Comprehensive unit tests for RenameNodeCommand.
 * Tests basic functionality, undo/redo, property change events, and error handling.
 *
 * @since 2.0
 */
class RenameNodeCommandTest {

    private XsdElement testElement;

    @BeforeEach
    void setUp() {
        testElement = new XsdElement("originalName");
    }

    // ========== Basic Functionality Tests ==========

    @Test
    @DisplayName("execute() should rename node and return true")
    void testExecuteRenamesNode() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals("newName", testElement.getName(), "Node should be renamed");
    }

    @Test
    @DisplayName("undo() should restore old name and return true")
    void testUndoRestoresOldName() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals("originalName", testElement.getName(), "Original name should be restored");
    }

    @Test
    @DisplayName("canUndo() should always return true")
    void testCanUndo() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true before execute");

        // Act
        command.execute();

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true after execute");
    }

    @Test
    @DisplayName("getDescription() should return descriptive text")
    void testGetDescription() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.contains("originalName"), "Description should contain old name");
        assertTrue(description.contains("newName"), "Description should contain new name");
    }

    @Test
    @DisplayName("canMergeWith() should return true for consecutive renames on same node")
    void testCanMergeWithConsecutiveRenames() {
        // Arrange
        RenameNodeCommand command1 = new RenameNodeCommand(testElement, "newName");
        command1.execute();
        RenameNodeCommand command2 = new RenameNodeCommand(testElement, "newerName");

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertTrue(canMerge, "Consecutive renames on same node should be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command types")
    void testCanMergeWithDifferentCommandType() {
        // Arrange
        RenameNodeCommand renameCommand = new RenameNodeCommand(testElement, "newName");
        DeleteNodeCommand deleteCommand = new DeleteNodeCommand(testElement);

        // Act
        boolean canMerge = renameCommand.canMergeWith(deleteCommand);

        // Assert
        assertFalse(canMerge, "Different command types should not be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for renames on different nodes")
    void testCanMergeWithDifferentNodes() {
        // Arrange
        XsdElement otherElement = new XsdElement("otherElement");
        RenameNodeCommand command1 = new RenameNodeCommand(testElement, "newName");
        RenameNodeCommand command2 = new RenameNodeCommand(otherElement, "otherNewName");

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Renames on different nodes should not be mergeable");
    }

    // ========== PropertyChangeEvent Tests ==========

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent for 'name' property")
    void testExecuteFiresPropertyChangeEvent() {
        // Arrange
        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testElement.addPropertyChangeListener(evt -> {
            if ("name".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");

        // Act
        command.execute();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("name", capturedEvent.get().getPropertyName(), "Property name should be 'name'");
        assertEquals("originalName", capturedEvent.get().getOldValue(), "Old value should be 'originalName'");
        assertEquals("newName", capturedEvent.get().getNewValue(), "New value should be 'newName'");
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent for 'name' property")
    void testUndoFiresPropertyChangeEvent() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");
        command.execute();

        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testElement.addPropertyChangeListener(evt -> {
            if ("name".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        // Act
        command.undo();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("name", capturedEvent.get().getPropertyName(), "Property name should be 'name'");
        assertEquals("newName", capturedEvent.get().getOldValue(), "Old value should be 'newName'");
        assertEquals("originalName", capturedEvent.get().getNewValue(), "New value should be 'originalName'");
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when node is null")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new RenameNodeCommand(null, "newName");
        }, "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when newName is null")
    void testConstructorThrowsExceptionForNullNewName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new RenameNodeCommand(testElement, null);
        }, "Constructor should throw IllegalArgumentException for null newName");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when newName is empty")
    void testConstructorThrowsExceptionForEmptyNewName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new RenameNodeCommand(testElement, "");
        }, "Constructor should throw IllegalArgumentException for empty newName");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when newName is whitespace")
    void testConstructorThrowsExceptionForWhitespaceNewName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new RenameNodeCommand(testElement, "   ");
        }, "Constructor should throw IllegalArgumentException for whitespace newName");
    }

    @Test
    @DisplayName("Constructor should trim newName whitespace")
    void testConstructorTrimsNewName() {
        // Arrange & Act
        RenameNodeCommand command = new RenameNodeCommand(testElement, "  newName  ");
        command.execute();

        // Assert
        assertEquals("newName", testElement.getName(), "Whitespace should be trimmed from newName");
    }

    // ========== Multiple Execute/Undo Tests ==========

    @Test
    @DisplayName("Multiple execute() calls should use most recent name change")
    void testMultipleExecuteCalls() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");

        // Act
        command.execute();
        command.execute();

        // Assert
        assertEquals("newName", testElement.getName(), "Name should still be 'newName' after multiple executes");
    }

    @Test
    @DisplayName("Multiple undo() calls should restore to original name")
    void testMultipleUndoCalls() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");
        command.execute();

        // Act
        command.undo();
        command.undo();

        // Assert
        assertEquals("originalName", testElement.getName(), "Name should be 'originalName' after multiple undos");
    }

    @Test
    @DisplayName("Execute-undo-execute sequence should work correctly")
    void testExecuteUndoExecuteSequence() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");

        // Act & Assert
        command.execute();
        assertEquals("newName", testElement.getName());

        command.undo();
        assertEquals("originalName", testElement.getName());

        command.execute();
        assertEquals("newName", testElement.getName());
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getNode() should return the correct node")
    void testGetNode() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");

        // Act
        XsdElement node = (XsdElement) command.getNode();

        // Assert
        assertSame(testElement, node, "getNode() should return the same instance");
    }

    @Test
    @DisplayName("getNewName() should return the correct new name")
    void testGetNewName() {
        // Arrange
        RenameNodeCommand command = new RenameNodeCommand(testElement, "newName");

        // Act
        String newName = command.getNewName();

        // Assert
        assertEquals("newName", newName, "getNewName() should return 'newName'");
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should handle renaming with special characters")
    void testRenameWithSpecialCharacters() {
        // Arrange
        String specialName = "element_with-special.chars:123";
        RenameNodeCommand command = new RenameNodeCommand(testElement, specialName);

        // Act
        command.execute();

        // Assert
        assertEquals(specialName, testElement.getName(), "Special characters should be preserved");
    }

    @Test
    @DisplayName("Should handle renaming with unicode characters")
    void testRenameWithUnicodeCharacters() {
        // Arrange
        String unicodeName = "元素名称";
        RenameNodeCommand command = new RenameNodeCommand(testElement, unicodeName);

        // Act
        command.execute();

        // Assert
        assertEquals(unicodeName, testElement.getName(), "Unicode characters should be preserved");
    }
}
