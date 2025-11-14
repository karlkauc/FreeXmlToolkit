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
 * Unit tests for DeletePatternCommand.
 *
 * @since 2.0
 */
class DeletePatternCommandTest {

    private XsdEditorContext editorContext;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        editorContext = new XsdEditorContext(schema);
        element = new XsdElement("TestElement");
        element.addPattern("[0-9]+");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw exception for null context")
    void testConstructorThrowsExceptionForNullContext() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeletePatternCommand(null, element, "[0-9]+"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeletePatternCommand(editorContext, null, "[0-9]+"));
    }

    @Test
    @DisplayName("constructor should throw exception for non-element node")
    void testConstructorThrowsExceptionForNonElement() {
        XsdAttribute attribute = new XsdAttribute("TestAttribute");
        assertThrows(IllegalArgumentException.class,
                () -> new DeletePatternCommand(editorContext, attribute, "[0-9]+"));
    }

    @Test
    @DisplayName("constructor should throw exception for null pattern")
    void testConstructorThrowsExceptionForNullPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeletePatternCommand(editorContext, element, null));
    }

    @Test
    @DisplayName("constructor should throw exception for empty pattern")
    void testConstructorThrowsExceptionForEmptyPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new DeletePatternCommand(editorContext, element, "   "));
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should delete pattern from element")
    void testExecuteDeletesPattern() {
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[0-9]+");
        boolean result = command.execute();

        assertTrue(result);
        assertFalse(element.getPatterns().contains("[0-9]+"));
        assertEquals(0, element.getPatterns().size());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should return false for non-existent pattern")
    void testExecuteReturnsFalseForNonExistentPattern() {
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[a-zA-Z]+");
        boolean result = command.execute();

        assertFalse(result);
        assertEquals(1, element.getPatterns().size()); // Original pattern still there
    }

    @Test
    @DisplayName("execute() should not affect other patterns")
    void testExecuteDoesNotAffectOtherPatterns() {
        element.addPattern("[a-zA-Z]+");
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[0-9]+");
        command.execute();

        assertEquals(1, element.getPatterns().size());
        assertTrue(element.getPatterns().contains("[a-zA-Z]+"));
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore deleted pattern")
    void testUndoRestoresDeletedPattern() {
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[0-9]+");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertTrue(element.getPatterns().contains("[0-9]+"));
        assertEquals(1, element.getPatterns().size());
    }

    @Test
    @DisplayName("undo() should maintain pattern order")
    void testUndoMaintainsPatternOrder() {
        element.addPattern("[a-zA-Z]+");
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[0-9]+");
        command.execute();
        command.undo();

        assertEquals(2, element.getPatterns().size());
        // Pattern should be re-added (at the end)
        assertTrue(element.getPatterns().contains("[0-9]+"));
        assertTrue(element.getPatterns().contains("[a-zA-Z]+"));
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[0-9]+");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe pattern deletion")
    void testGetDescriptionDescribesPatternDeletion() {
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[0-9]+");
        String description = command.getDescription();

        assertTrue(description.contains("Delete pattern"));
        assertTrue(description.contains("TestElement"));
    }

    @Test
    @DisplayName("canMergeWith() should return false")
    void testCanMergeWithReturnsFalse() {
        DeletePatternCommand command1 = new DeletePatternCommand(editorContext, element, "[0-9]+");
        DeletePatternCommand command2 = new DeletePatternCommand(editorContext, element, "[a-zA-Z]+");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getElement() should return the element")
    void testGetElementReturnsElement() {
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[0-9]+");
        assertEquals(element, command.getElement());
    }

    @Test
    @DisplayName("getPattern() should return the pattern")
    void testGetPatternReturnsPattern() {
        DeletePatternCommand command = new DeletePatternCommand(editorContext, element, "[0-9]+");
        assertEquals("[0-9]+", command.getPattern());
    }
}
