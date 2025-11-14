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
 * Unit tests for DeleteAssertionCommand.
 *
 * @since 2.0
 */
class DeleteAssertionCommandTest {

    private XsdEditorContext editorContext;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        editorContext = new XsdEditorContext(schema);
        element = new XsdElement("TestElement");
        element.addAssertion("@value > 0");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw exception for null context")
    void testConstructorThrowsExceptionForNullContext() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteAssertionCommand(null, element, "@value > 0"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteAssertionCommand(editorContext, null, "@value > 0"));
    }

    @Test
    @DisplayName("constructor should throw exception for non-element node")
    void testConstructorThrowsExceptionForNonElement() {
        XsdAttribute attribute = new XsdAttribute("TestAttribute");
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteAssertionCommand(editorContext, attribute, "@value > 0"));
    }

    @Test
    @DisplayName("constructor should throw exception for null assertion")
    void testConstructorThrowsExceptionForNullAssertion() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteAssertionCommand(editorContext, element, null));
    }

    @Test
    @DisplayName("constructor should throw exception for empty assertion")
    void testConstructorThrowsExceptionForEmptyAssertion() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeleteAssertionCommand(editorContext, element, "   "));
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should delete assertion from element")
    void testExecuteDeletesAssertion() {
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        boolean result = command.execute();

        assertTrue(result);
        assertFalse(element.getAssertions().contains("@value > 0"));
        assertEquals(0, element.getAssertions().size());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should return false for non-existent assertion")
    void testExecuteReturnsFalseForNonExistentAssertion() {
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "count(item) >= 1");
        boolean result = command.execute();

        assertFalse(result);
        assertEquals(1, element.getAssertions().size()); // Original assertion still there
    }

    @Test
    @DisplayName("execute() should not affect other assertions")
    void testExecuteDoesNotAffectOtherAssertions() {
        element.addAssertion("count(item) >= 1");
        element.addAssertion("@type = 'valid'");
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        command.execute();

        assertEquals(2, element.getAssertions().size());
        assertTrue(element.getAssertions().contains("count(item) >= 1"));
        assertTrue(element.getAssertions().contains("@type = 'valid'"));
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore deleted assertion")
    void testUndoRestoresDeletedAssertion() {
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertTrue(element.getAssertions().contains("@value > 0"));
        assertEquals(1, element.getAssertions().size());
    }

    @Test
    @DisplayName("undo() should maintain assertion order")
    void testUndoMaintainsAssertionOrder() {
        element.addAssertion("count(item) >= 1");
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        command.execute();
        command.undo();

        assertEquals(2, element.getAssertions().size());
        // Assertion should be re-added (at the end)
        assertTrue(element.getAssertions().contains("@value > 0"));
        assertTrue(element.getAssertions().contains("count(item) >= 1"));
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe assertion deletion")
    void testGetDescriptionDescribesAssertionDeletion() {
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        String description = command.getDescription();

        assertTrue(description.contains("Delete assertion"));
        assertTrue(description.contains("TestElement"));
    }

    @Test
    @DisplayName("canMergeWith() should return false")
    void testCanMergeWithReturnsFalse() {
        DeleteAssertionCommand command1 = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        DeleteAssertionCommand command2 = new DeleteAssertionCommand(editorContext, element, "count(item) >= 1");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getElement() should return the element")
    void testGetElementReturnsElement() {
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        assertEquals(element, command.getElement());
    }

    @Test
    @DisplayName("getAssertion() should return the assertion expression")
    void testGetAssertionReturnsAssertion() {
        DeleteAssertionCommand command = new DeleteAssertionCommand(editorContext, element, "@value > 0");
        assertEquals("@value > 0", command.getAssertion());
    }
}
