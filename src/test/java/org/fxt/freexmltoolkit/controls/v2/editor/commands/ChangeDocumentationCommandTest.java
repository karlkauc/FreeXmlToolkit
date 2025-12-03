package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
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
        // Create test schema
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");

        // Create editor context
        editorContext = new XsdEditorContext(schema);

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

    // ========== Multi-Language Documentation Tests ==========

    @Test
    @DisplayName("execute() should clear multi-language documentations list")
    void testExecuteClearsDocumentationsList() {
        // Arrange: Add multi-language documentations
        element.addDocumentation(new XsdDocumentation("English text", "en"));
        element.addDocumentation(new XsdDocumentation("German text", "de"));
        assertEquals(2, element.getDocumentations().size(), "Should have 2 documentation entries initially");

        String newDocumentation = "New single documentation";
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, newDocumentation);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(newDocumentation, element.getDocumentation(), "Legacy documentation should be set");
        assertTrue(element.getDocumentations().isEmpty(), "Documentations list should be cleared after execute()");
    }

    @Test
    @DisplayName("undo() should restore multi-language documentations list")
    void testUndoRestoresDocumentationsList() {
        // Arrange: Add multi-language documentations
        XsdDocumentation doc1 = new XsdDocumentation("English text", "en");
        XsdDocumentation doc2 = new XsdDocumentation("German text", "de");
        element.addDocumentation(doc1);
        element.addDocumentation(doc2);
        element.setDocumentation("[en] English text\n\n[de] German text"); // Legacy string

        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "New documentation");
        command.execute();

        // Verify state after execute
        assertTrue(element.getDocumentations().isEmpty(), "Documentations list should be empty after execute");

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals("[en] English text\n\n[de] German text", element.getDocumentation(),
                "Legacy documentation should be restored");
        assertEquals(2, element.getDocumentations().size(), "Documentations list should be restored to 2 entries");
        assertEquals("en", element.getDocumentations().get(0).getLang(), "First doc should have 'en' lang");
        assertEquals("de", element.getDocumentations().get(1).getLang(), "Second doc should have 'de' lang");
    }

    @Test
    @DisplayName("execute() should work correctly when documentations list is empty")
    void testExecuteWithEmptyDocumentationsList() {
        // Arrange: Element has no multi-language docs, only legacy string
        element.setDocumentation("Old documentation");
        assertTrue(element.getDocumentations().isEmpty(), "Documentations list should be empty initially");

        String newDocumentation = "New documentation";
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, newDocumentation);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(newDocumentation, element.getDocumentation(), "Documentation should be updated");
        assertTrue(element.getDocumentations().isEmpty(), "Documentations list should remain empty");
    }

    @Test
    @DisplayName("undo() after execute() should correctly restore both legacy and multi-language docs")
    void testUndoAfterExecuteRestoresBothFormats() {
        // Arrange: Set up element with both legacy string and multi-language docs
        element.setDocumentation("Original legacy doc");
        element.addDocumentation(new XsdDocumentation("Original English", "en"));

        // Create and execute command
        ChangeDocumentationCommand command = new ChangeDocumentationCommand(editorContext, element, "Modified doc");
        command.execute();

        // Verify execution cleared the list
        assertTrue(element.getDocumentations().isEmpty(), "List should be empty after execute");
        assertEquals("Modified doc", element.getDocumentation(), "Legacy doc should be modified");

        // Act: Undo
        command.undo();

        // Assert: Both formats should be restored
        assertEquals("Original legacy doc", element.getDocumentation(), "Legacy doc should be restored");
        assertEquals(1, element.getDocumentations().size(), "Multi-language list should be restored");
        assertEquals("en", element.getDocumentations().get(0).getLang(), "Doc lang should be restored");
        assertEquals("Original English", element.getDocumentations().get(0).getText(), "Doc text should be restored");
    }
}
