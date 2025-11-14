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
 * Unit tests for AddPatternCommand.
 *
 * @since 2.0
 */
class AddPatternCommandTest {

    private XsdEditorContext editorContext;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        editorContext = new XsdEditorContext(schema);
        element = new XsdElement("TestElement");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw exception for null context")
    void testConstructorThrowsExceptionForNullContext() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddPatternCommand(null, element, "[0-9]+"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddPatternCommand(editorContext, null, "[0-9]+"));
    }

    @Test
    @DisplayName("constructor should throw exception for non-element node")
    void testConstructorThrowsExceptionForNonElement() {
        XsdAttribute attribute = new XsdAttribute("TestAttribute");
        assertThrows(IllegalArgumentException.class,
                () -> new AddPatternCommand(editorContext, attribute, "[0-9]+"));
    }

    @Test
    @DisplayName("constructor should throw exception for null pattern")
    void testConstructorThrowsExceptionForNullPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddPatternCommand(editorContext, element, null));
    }

    @Test
    @DisplayName("constructor should throw exception for empty pattern")
    void testConstructorThrowsExceptionForEmptyPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddPatternCommand(editorContext, element, "   "));
    }

    @Test
    @DisplayName("constructor should accept valid pattern")
    void testConstructorAcceptsValidPattern() {
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "[0-9]+");
        assertNotNull(command);
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should add pattern to element")
    void testExecuteAddsPattern() {
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "[0-9]+");
        boolean result = command.execute();

        assertTrue(result);
        assertTrue(element.getPatterns().contains("[0-9]+"));
        assertEquals(1, element.getPatterns().size());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should add multiple patterns")
    void testExecuteAddsMultiplePatterns() {
        AddPatternCommand command1 = new AddPatternCommand(editorContext, element, "[0-9]+");
        AddPatternCommand command2 = new AddPatternCommand(editorContext, element, "[a-zA-Z]+");
        command1.execute();
        command2.execute();

        assertEquals(2, element.getPatterns().size());
        assertTrue(element.getPatterns().contains("[0-9]+"));
        assertTrue(element.getPatterns().contains("[a-zA-Z]+"));
    }

    @Test
    @DisplayName("execute() should trim whitespace from pattern")
    void testExecuteTrimsWhitespace() {
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "  [0-9]+  ");
        command.execute();

        assertTrue(element.getPatterns().contains("[0-9]+"));
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should remove added pattern")
    void testUndoRemovesAddedPattern() {
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "[0-9]+");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertFalse(element.getPatterns().contains("[0-9]+"));
        assertEquals(0, element.getPatterns().size());
    }

    @Test
    @DisplayName("undo() should not affect other patterns")
    void testUndoDoesNotAffectOtherPatterns() {
        element.addPattern("[a-zA-Z]+");
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "[0-9]+");
        command.execute();
        command.undo();

        assertEquals(1, element.getPatterns().size());
        assertTrue(element.getPatterns().contains("[a-zA-Z]+"));
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "[0-9]+");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe pattern addition")
    void testGetDescriptionDescribesPatternAddition() {
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "[0-9]+");
        String description = command.getDescription();

        assertTrue(description.contains("Add pattern"));
        assertTrue(description.contains("TestElement"));
    }

    @Test
    @DisplayName("canMergeWith() should return false")
    void testCanMergeWithReturnsFalse() {
        AddPatternCommand command1 = new AddPatternCommand(editorContext, element, "[0-9]+");
        AddPatternCommand command2 = new AddPatternCommand(editorContext, element, "[a-zA-Z]+");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getElement() should return the element")
    void testGetElementReturnsElement() {
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "[0-9]+");
        assertEquals(element, command.getElement());
    }

    @Test
    @DisplayName("getPattern() should return the pattern")
    void testGetPatternReturnsPattern() {
        AddPatternCommand command = new AddPatternCommand(editorContext, element, "[0-9]+");
        assertEquals("[0-9]+", command.getPattern());
    }
}
