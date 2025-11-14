package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeleteEnumerationCommand.
 *
 * @since 2.0
 */
class DeleteEnumerationCommandTest {

    private XsdEditorContext editorContext;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        editorContext = new XsdEditorContext(schema);
        element = new XsdElement("TestElement");
        element.addEnumeration("active");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw exception for null context")
    void testConstructorThrowsExceptionForNullContext() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteEnumerationCommand(null, element, "active"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteEnumerationCommand(editorContext, null, "active"));
    }

    @Test
    @DisplayName("constructor should throw exception for non-element node")
    void testConstructorThrowsExceptionForNonElement() {
        XsdAttribute attribute = new XsdAttribute("TestAttribute");
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteEnumerationCommand(editorContext, attribute, "active"));
    }

    @Test
    @DisplayName("constructor should throw exception for null enumeration")
    void testConstructorThrowsExceptionForNullEnumeration() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteEnumerationCommand(editorContext, element, null));
    }

    @Test
    @DisplayName("constructor should throw exception for empty enumeration")
    void testConstructorThrowsExceptionForEmptyEnumeration() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteEnumerationCommand(editorContext, element, "   "));
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should delete enumeration from element")
    void testExecuteDeletesEnumeration() {
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "active");
        boolean result = command.execute();

        assertTrue(result);
        assertFalse(element.getEnumerations().contains("active"));
        assertEquals(0, element.getEnumerations().size());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should return false for non-existent enumeration")
    void testExecuteReturnsFalseForNonExistentEnumeration() {
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "inactive");
        boolean result = command.execute();

        assertFalse(result);
        assertEquals(1, element.getEnumerations().size()); // Original enumeration still there
    }

    @Test
    @DisplayName("execute() should not affect other enumerations")
    void testExecuteDoesNotAffectOtherEnumerations() {
        element.addEnumeration("inactive");
        element.addEnumeration("pending");
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "active");
        command.execute();

        assertEquals(2, element.getEnumerations().size());
        assertTrue(element.getEnumerations().contains("inactive"));
        assertTrue(element.getEnumerations().contains("pending"));
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore deleted enumeration")
    void testUndoRestoresDeletedEnumeration() {
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "active");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertTrue(element.getEnumerations().contains("active"));
        assertEquals(1, element.getEnumerations().size());
    }

    @Test
    @DisplayName("undo() should maintain enumeration order")
    void testUndoMaintainsEnumerationOrder() {
        element.addEnumeration("inactive");
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "active");
        command.execute();
        command.undo();

        assertEquals(2, element.getEnumerations().size());
        // Enumeration should be re-added (at the end)
        assertTrue(element.getEnumerations().contains("active"));
        assertTrue(element.getEnumerations().contains("inactive"));
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "active");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe enumeration deletion")
    void testGetDescriptionDescribesEnumerationDeletion() {
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "active");
        String description = command.getDescription();

        assertTrue(description.contains("Delete enumeration"));
        assertTrue(description.contains("TestElement"));
    }

    @Test
    @DisplayName("canMergeWith() should return false")
    void testCanMergeWithReturnsFalse() {
        DeleteEnumerationCommand command1 = new DeleteEnumerationCommand(editorContext, element, "active");
        DeleteEnumerationCommand command2 = new DeleteEnumerationCommand(editorContext, element, "inactive");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getElement() should return the element")
    void testGetElementReturnsElement() {
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "active");
        assertEquals(element, command.getElement());
    }

    @Test
    @DisplayName("getEnumeration() should return the enumeration value")
    void testGetEnumerationReturnsEnumeration() {
        DeleteEnumerationCommand command = new DeleteEnumerationCommand(editorContext, element, "active");
        assertEquals("active", command.getEnumeration());
    }
}
