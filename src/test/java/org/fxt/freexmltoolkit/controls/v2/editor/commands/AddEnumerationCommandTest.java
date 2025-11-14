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
 * Unit tests for AddEnumerationCommand.
 *
 * @since 2.0
 */
class AddEnumerationCommandTest {

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
                () -> new AddEnumerationCommand(null, element, "active"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddEnumerationCommand(editorContext, null, "active"));
    }

    @Test
    @DisplayName("constructor should throw exception for non-element node")
    void testConstructorThrowsExceptionForNonElement() {
        XsdAttribute attribute = new XsdAttribute("TestAttribute");
        assertThrows(IllegalArgumentException.class,
                () -> new AddEnumerationCommand(editorContext, attribute, "active"));
    }

    @Test
    @DisplayName("constructor should throw exception for null enumeration")
    void testConstructorThrowsExceptionForNullEnumeration() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddEnumerationCommand(editorContext, element, null));
    }

    @Test
    @DisplayName("constructor should throw exception for empty enumeration")
    void testConstructorThrowsExceptionForEmptyEnumeration() {
        assertThrows(IllegalArgumentException.class,
                () -> new AddEnumerationCommand(editorContext, element, "   "));
    }

    @Test
    @DisplayName("constructor should accept valid enumeration value")
    void testConstructorAcceptsValidEnumeration() {
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "active");
        assertNotNull(command);
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should add enumeration to element")
    void testExecuteAddsEnumeration() {
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "active");
        boolean result = command.execute();

        assertTrue(result);
        assertTrue(element.getEnumerations().contains("active"));
        assertEquals(1, element.getEnumerations().size());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should add multiple enumerations")
    void testExecuteAddsMultipleEnumerations() {
        AddEnumerationCommand command1 = new AddEnumerationCommand(editorContext, element, "active");
        AddEnumerationCommand command2 = new AddEnumerationCommand(editorContext, element, "inactive");
        AddEnumerationCommand command3 = new AddEnumerationCommand(editorContext, element, "pending");
        command1.execute();
        command2.execute();
        command3.execute();

        assertEquals(3, element.getEnumerations().size());
        assertTrue(element.getEnumerations().contains("active"));
        assertTrue(element.getEnumerations().contains("inactive"));
        assertTrue(element.getEnumerations().contains("pending"));
    }

    @Test
    @DisplayName("execute() should trim whitespace from enumeration")
    void testExecuteTrimsWhitespace() {
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "  active  ");
        command.execute();

        assertTrue(element.getEnumerations().contains("active"));
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should remove added enumeration")
    void testUndoRemovesAddedEnumeration() {
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "active");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertFalse(element.getEnumerations().contains("active"));
        assertEquals(0, element.getEnumerations().size());
    }

    @Test
    @DisplayName("undo() should not affect other enumerations")
    void testUndoDoesNotAffectOtherEnumerations() {
        element.addEnumeration("inactive");
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "active");
        command.execute();
        command.undo();

        assertEquals(1, element.getEnumerations().size());
        assertTrue(element.getEnumerations().contains("inactive"));
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "active");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe enumeration addition")
    void testGetDescriptionDescribesEnumerationAddition() {
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "active");
        String description = command.getDescription();

        assertTrue(description.contains("Add enumeration"));
        assertTrue(description.contains("TestElement"));
    }

    @Test
    @DisplayName("canMergeWith() should return false")
    void testCanMergeWithReturnsFalse() {
        AddEnumerationCommand command1 = new AddEnumerationCommand(editorContext, element, "active");
        AddEnumerationCommand command2 = new AddEnumerationCommand(editorContext, element, "inactive");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getElement() should return the element")
    void testGetElementReturnsElement() {
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "active");
        assertEquals(element, command.getElement());
    }

    @Test
    @DisplayName("getEnumeration() should return the enumeration value")
    void testGetEnumerationReturnsEnumeration() {
        AddEnumerationCommand command = new AddEnumerationCommand(editorContext, element, "active");
        assertEquals("active", command.getEnumeration());
    }
}
