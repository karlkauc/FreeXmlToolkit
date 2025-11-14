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
 * Unit tests for AddAssertionCommand.
 *
 * @since 2.0
 */
class AddAssertionCommandTest {

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
                () -> new AddAssertionCommand(null, element, "@value > 0"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddAssertionCommand(editorContext, null, "@value > 0"));
    }

    @Test
    @DisplayName("constructor should throw exception for non-element node")
    void testConstructorThrowsExceptionForNonElement() {
        XsdAttribute attribute = new XsdAttribute("TestAttribute");
        assertThrows(IllegalArgumentException.class,
                () -> new AddAssertionCommand(editorContext, attribute, "@value > 0"));
    }

    @Test
    @DisplayName("constructor should throw exception for null assertion")
    void testConstructorThrowsExceptionForNullAssertion() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddAssertionCommand(editorContext, element, null));
    }

    @Test
    @DisplayName("constructor should throw exception for empty assertion")
    void testConstructorThrowsExceptionForEmptyAssertion() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddAssertionCommand(editorContext, element, "   "));
    }

    @Test
    @DisplayName("constructor should accept valid assertion expression")
    void testConstructorAcceptsValidAssertion() {
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "@value > 0");
        assertNotNull(command);
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should add assertion to element")
    void testExecuteAddsAssertion() {
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "@value > 0");
        boolean result = command.execute();

        assertTrue(result);
        assertTrue(element.getAssertions().contains("@value > 0"));
        assertEquals(1, element.getAssertions().size());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should add multiple assertions")
    void testExecuteAddsMultipleAssertions() {
        AddAssertionCommand command1 = new AddAssertionCommand(editorContext, element, "@value > 0");
        AddAssertionCommand command2 = new AddAssertionCommand(editorContext, element, "count(item) >= 1");
        command1.execute();
        command2.execute();

        assertEquals(2, element.getAssertions().size());
        assertTrue(element.getAssertions().contains("@value > 0"));
        assertTrue(element.getAssertions().contains("count(item) >= 1"));
    }

    @Test
    @DisplayName("execute() should trim whitespace from assertion")
    void testExecuteTrimsWhitespace() {
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "  @value > 0  ");
        command.execute();

        assertTrue(element.getAssertions().contains("@value > 0"));
    }

    @Test
    @DisplayName("execute() should handle complex XPath expressions")
    void testExecuteHandlesComplexXPath() {
        String complexAssertion = "if (@type='credit') then @number else true()";
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, complexAssertion);
        command.execute();

        assertTrue(element.getAssertions().contains(complexAssertion));
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should remove added assertion")
    void testUndoRemovesAddedAssertion() {
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "@value > 0");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertFalse(element.getAssertions().contains("@value > 0"));
        assertEquals(0, element.getAssertions().size());
    }

    @Test
    @DisplayName("undo() should not affect other assertions")
    void testUndoDoesNotAffectOtherAssertions() {
        element.addAssertion("count(item) >= 1");
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "@value > 0");
        command.execute();
        command.undo();

        assertEquals(1, element.getAssertions().size());
        assertTrue(element.getAssertions().contains("count(item) >= 1"));
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "@value > 0");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe assertion addition")
    void testGetDescriptionDescribesAssertionAddition() {
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "@value > 0");
        String description = command.getDescription();

        assertTrue(description.contains("Add assertion"));
        assertTrue(description.contains("TestElement"));
    }

    @Test
    @DisplayName("canMergeWith() should return false")
    void testCanMergeWithReturnsFalse() {
        AddAssertionCommand command1 = new AddAssertionCommand(editorContext, element, "@value > 0");
        AddAssertionCommand command2 = new AddAssertionCommand(editorContext, element, "count(item) >= 1");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getElement() should return the element")
    void testGetElementReturnsElement() {
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "@value > 0");
        assertEquals(element, command.getElement());
    }

    @Test
    @DisplayName("getAssertion() should return the assertion expression")
    void testGetAssertionReturnsAssertion() {
        AddAssertionCommand command = new AddAssertionCommand(editorContext, element, "@value > 0");
        assertEquals("@value > 0", command.getAssertion());
    }
}
