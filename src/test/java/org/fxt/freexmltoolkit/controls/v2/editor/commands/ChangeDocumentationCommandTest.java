package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchemaModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChangeDocumentationCommand.
 *
 * @since 2.0
 */
class ChangeDocumentationCommandTest {

    private XsdEditorContext editorContext;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        // Create editor context
        XsdSchemaModel schemaModel = new XsdSchemaModel();
        schemaModel.setTargetNamespace("http://example.com/test");
        editorContext = new XsdEditorContext(schemaModel);

        // Create test element
        element = new XsdElement("TestElement");
        element.setType("xs:string");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw exception for null context")
    void testConstructorThrowsExceptionForNullContext() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeDocumentationCommand(null, element, "New documentation"),
                "Constructor should throw IllegalArgumentException for null context");
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeDocumentationCommand(editorContext, null, "New documentation"),
                "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("constructor should accept null documentation")
    void testConstructorAcceptsNullDocumentation() {
        // Act & Assert (should not throw)
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, null);
        assertNotNull(command, "Command should be created with null documentation");
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should set new documentation")
    void testExecuteSetsDocumentation() {
        // Arrange
        String newDocumentation = "This is a test element";
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, newDocumentation);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(newDocumentation, element.getDocumentation(), "Documentation should be set");
        assertTrue(editorContext.isDirty(), "Context should be marked dirty");
    }

    @Test
    @DisplayName("execute() should replace existing documentation")
    void testExecuteReplacesExistingDocumentation() {
        // Arrange
        element.setDocumentation("Old documentation");
        String newDocumentation = "New documentation";
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, newDocumentation);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(newDocumentation, element.getDocumentation(), "Documentation should be replaced");
    }

    @Test
    @DisplayName("execute() should remove documentation with null value")
    void testExecuteRemovesDocumentationWithNull() {
        // Arrange
        element.setDocumentation("Old documentation");
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, null);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertNull(element.getDocumentation(), "Documentation should be removed");
    }

    @Test
    @DisplayName("execute() should remove documentation with empty string")
    void testExecuteRemovesDocumentationWithEmptyString() {
        // Arrange
        element.setDocumentation("Old documentation");
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals("", element.getDocumentation(), "Documentation should be empty");
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore old documentation")
    void testUndoRestoresOldDocumentation() {
        // Arrange
        String oldDocumentation = "Old documentation";
        element.setDocumentation(oldDocumentation);
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "New documentation");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals(oldDocumentation, element.getDocumentation(), "Old documentation should be restored");
        assertTrue(editorContext.isDirty(), "Context should still be marked dirty after undo");
    }

    @Test
    @DisplayName("undo() should restore null documentation")
    void testUndoRestoresNullDocumentation() {
        // Arrange
        element.setDocumentation(null);
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "New documentation");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertNull(element.getDocumentation(), "Documentation should be null after undo");
    }

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        // Arrange
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "New documentation");

        // Act & Assert
        assertTrue(command.canUndo(), "Documentation changes should be undoable");
    }

    // ========== Description Tests ==========

    @Test
    @DisplayName("getDescription() should describe adding documentation")
    void testGetDescriptionForAddingDocumentation() {
        // Arrange
        element.setDocumentation(null);
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "New documentation");

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("Add documentation"), "Description should mention adding documentation");
        assertTrue(description.contains("TestElement"), "Description should contain element name");
    }

    @Test
    @DisplayName("getDescription() should describe changing documentation")
    void testGetDescriptionForChangingDocumentation() {
        // Arrange
        element.setDocumentation("Old documentation");
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "New documentation");

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("Change documentation"), "Description should mention changing documentation");
        assertTrue(description.contains("TestElement"), "Description should contain element name");
    }

    @Test
    @DisplayName("getDescription() should describe removing documentation")
    void testGetDescriptionForRemovingDocumentation() {
        // Arrange
        element.setDocumentation("Old documentation");
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, null);

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("Remove documentation"), "Description should mention removing documentation");
        assertTrue(description.contains("TestElement"), "Description should contain element name");
    }

    // ========== Merge Tests ==========

    @Test
    @DisplayName("canMergeWith() should return true for same node")
    void testCanMergeWithSameNode() {
        // Arrange
        ChangeDocumentationCommand command1 = new ChangeDocumentationCommand(editorContext, element, "Doc 1");
        ChangeDocumentationCommand command2 = new ChangeDocumentationCommand(editorContext, element, "Doc 2");

        // Act & Assert
        assertTrue(command1.canMergeWith(command2), "Commands on same node should be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different nodes")
    void testCanMergeWithDifferentNodes() {
        // Arrange
        XsdElement element2 = new XsdElement("OtherElement");
        ChangeDocumentationCommand command1 = new ChangeDocumentationCommand(editorContext, element, "Doc 1");
        ChangeDocumentationCommand command2 = new ChangeDocumentationCommand(editorContext, element2, "Doc 2");

        // Act & Assert
        assertFalse(command1.canMergeWith(command2), "Commands on different nodes should not be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command type")
    void testCanMergeWithDifferentCommandType() {
        // Arrange
        ChangeDocumentationCommand command1 = new ChangeDocumentationCommand(editorContext, element, "Doc 1");
        RenameNodeCommand command2 = new RenameNodeCommand(element, "NewName");

        // Act & Assert
        assertFalse(command1.canMergeWith(command2), "Commands of different types should not be mergeable");
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getNode() should return the correct node")
    void testGetNodeReturnsCorrectNode() {
        // Arrange
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "New documentation");

        // Act & Assert
        assertEquals(element, command.getNode(), "getNode() should return the correct node");
    }

    @Test
    @DisplayName("getOldDocumentation() should return the old documentation")
    void testGetOldDocumentationReturnsOldValue() {
        // Arrange
        String oldDocumentation = "Old documentation";
        element.setDocumentation(oldDocumentation);
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "New documentation");

        // Act & Assert
        assertEquals(oldDocumentation, command.getOldDocumentation(), "getOldDocumentation() should return old value");
    }

    @Test
    @DisplayName("getNewDocumentation() should return the new documentation")
    void testGetNewDocumentationReturnsNewValue() {
        // Arrange
        String newDocumentation = "New documentation";
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, newDocumentation);

        // Act & Assert
        assertEquals(newDocumentation, command.getNewDocumentation(), "getNewDocumentation() should return new value");
    }
}
