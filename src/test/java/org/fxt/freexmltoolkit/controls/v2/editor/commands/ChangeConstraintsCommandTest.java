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
 * Unit tests for ChangeConstraintsCommand.
 *
 * @since 2.0
 */
class ChangeConstraintsCommandTest {

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
                () -> new ChangeConstraintsCommand(null, element, true, false, null),
                "Constructor should throw IllegalArgumentException for null context");
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeConstraintsCommand(editorContext, null, true, false, null),
                "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("constructor should throw exception for non-element node")
    void testConstructorThrowsExceptionForNonElementNode() {
        // Arrange
        XsdAttribute attribute = new XsdAttribute("TestAttribute");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new ChangeConstraintsCommand(editorContext, attribute, true, false, null),
                "Constructor should throw IllegalArgumentException for non-element node");

        assertTrue(exception.getMessage().contains("elements"),
                "Error message should mention that constraints are for elements only");
    }

    @Test
    @DisplayName("constructor should accept valid element")
    void testConstructorAcceptsValidElement() {
        // Act & Assert (should not throw)
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, true, false, "fixedValue");
        assertNotNull(command, "Command should be created with valid element");
    }

    @Test
    @DisplayName("constructor should trim and null-ify empty fixed value")
    void testConstructorTrimsFixedValue() {
        // Act
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, true, false, "  ");

        // Assert
        assertNull(command.getNewFixed(), "Empty fixed value should be null");
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should set nillable constraint")
    void testExecuteSetsNillable() {
        // Arrange
        element.setNillable(false);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, true, false, null);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertTrue(element.isNillable(), "Nillable should be set to true");
        assertTrue(editorContext.isDirty(), "Context should be marked dirty");
    }

    @Test
    @DisplayName("execute() should set abstract constraint")
    void testExecuteSetsAbstract() {
        // Arrange
        element.setAbstract(false);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, true, null);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertTrue(element.isAbstract(), "Abstract should be set to true");
    }

    @Test
    @DisplayName("execute() should set fixed constraint")
    void testExecuteSetsFixed() {
        // Arrange
        element.setFixed(null);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, "fixedValue");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals("fixedValue", element.getFixed(), "Fixed value should be set");
    }

    @Test
    @DisplayName("execute() should set all constraints together")
    void testExecuteSetsAllConstraints() {
        // Arrange
        element.setNillable(false);
        element.setAbstract(false);
        element.setFixed(null);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, true, true, "fixedValue");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertTrue(element.isNillable(), "Nillable should be true");
        assertTrue(element.isAbstract(), "Abstract should be true");
        assertEquals("fixedValue", element.getFixed(), "Fixed should be set");
    }

    @Test
    @DisplayName("execute() should clear fixed value with null")
    void testExecuteClearsFixedValue() {
        // Arrange
        element.setFixed("oldValue");
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, null);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertNull(element.getFixed(), "Fixed value should be cleared");
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore old nillable value")
    void testUndoRestoresNillable() {
        // Arrange
        element.setNillable(true);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, null);
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertTrue(element.isNillable(), "Nillable should be restored to true");
        assertTrue(editorContext.isDirty(), "Context should still be marked dirty after undo");
    }

    @Test
    @DisplayName("undo() should restore old abstract value")
    void testUndoRestoresAbstract() {
        // Arrange
        element.setAbstract(true);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, null);
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertTrue(element.isAbstract(), "Abstract should be restored to true");
    }

    @Test
    @DisplayName("undo() should restore old fixed value")
    void testUndoRestoresFixed() {
        // Arrange
        element.setFixed("oldValue");
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, "newValue");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals("oldValue", element.getFixed(), "Fixed should be restored to old value");
    }

    @Test
    @DisplayName("undo() should restore all old constraints")
    void testUndoRestoresAllConstraints() {
        // Arrange
        element.setNillable(true);
        element.setAbstract(true);
        element.setFixed("oldFixed");
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, "newFixed");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertTrue(element.isNillable(), "Nillable should be restored");
        assertTrue(element.isAbstract(), "Abstract should be restored");
        assertEquals("oldFixed", element.getFixed(), "Fixed should be restored");
    }

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        // Arrange
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, true, false, null);

        // Act & Assert
        assertTrue(command.canUndo(), "Constraint changes should be undoable");
    }

    // ========== Description Tests ==========

    @Test
    @DisplayName("getDescription() should describe constraint change")
    void testGetDescriptionForConstraintChange() {
        // Arrange
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, true, false, "fixedValue");

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("Change constraints"), "Description should mention changing constraints");
        assertTrue(description.contains("TestElement"), "Description should contain element name");
    }

    @Test
    @DisplayName("getDescription() should handle unnamed element")
    void testGetDescriptionForUnnamedElement() {
        // Arrange
        XsdElement unnamed = new XsdElement(null);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, unnamed, true, false, null);

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("(unnamed)"), "Description should mention unnamed element");
    }

    // ========== Merge Tests ==========

    @Test
    @DisplayName("canMergeWith() should return true for same element")
    void testCanMergeWithSameElement() {
        // Arrange
        ChangeConstraintsCommand command1 = new ChangeConstraintsCommand(
                editorContext, element, true, false, null);
        ChangeConstraintsCommand command2 = new ChangeConstraintsCommand(
                editorContext, element, false, true, "fixed");

        // Act & Assert
        assertTrue(command1.canMergeWith(command2), "Commands on same element should be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different elements")
    void testCanMergeWithDifferentElements() {
        // Arrange
        XsdElement element2 = new XsdElement("OtherElement");
        ChangeConstraintsCommand command1 = new ChangeConstraintsCommand(
                editorContext, element, true, false, null);
        ChangeConstraintsCommand command2 = new ChangeConstraintsCommand(
                editorContext, element2, false, true, null);

        // Act & Assert
        assertFalse(command1.canMergeWith(command2), "Commands on different elements should not be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command type")
    void testCanMergeWithDifferentCommandType() {
        // Arrange
        ChangeConstraintsCommand command1 = new ChangeConstraintsCommand(
                editorContext, element, true, false, null);
        RenameNodeCommand command2 = new RenameNodeCommand(element, "NewName");

        // Act & Assert
        assertFalse(command1.canMergeWith(command2), "Commands of different types should not be mergeable");
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getElement() should return the correct element")
    void testGetElementReturnsCorrectElement() {
        // Arrange
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, true, false, null);

        // Act & Assert
        assertEquals(element, command.getElement(), "getElement() should return the correct element");
    }

    @Test
    @DisplayName("getOldNillable() should return the old nillable value")
    void testGetOldNillableReturnsOldValue() {
        // Arrange
        element.setNillable(true);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, null);

        // Act & Assert
        assertTrue(command.getOldNillable(), "getOldNillable() should return old value");
    }

    @Test
    @DisplayName("getOldAbstract() should return the old abstract value")
    void testGetOldAbstractReturnsOldValue() {
        // Arrange
        element.setAbstract(true);
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, null);

        // Act & Assert
        assertTrue(command.getOldAbstract(), "getOldAbstract() should return old value");
    }

    @Test
    @DisplayName("getOldFixed() should return the old fixed value")
    void testGetOldFixedReturnsOldValue() {
        // Arrange
        element.setFixed("oldValue");
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, "newValue");

        // Act & Assert
        assertEquals("oldValue", command.getOldFixed(), "getOldFixed() should return old value");
    }

    @Test
    @DisplayName("getNewNillable() should return the new nillable value")
    void testGetNewNillableReturnsNewValue() {
        // Arrange
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, true, false, null);

        // Act & Assert
        assertTrue(command.getNewNillable(), "getNewNillable() should return new value");
    }

    @Test
    @DisplayName("getNewAbstract() should return the new abstract value")
    void testGetNewAbstractReturnsNewValue() {
        // Arrange
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, true, null);

        // Act & Assert
        assertTrue(command.getNewAbstract(), "getNewAbstract() should return new value");
    }

    @Test
    @DisplayName("getNewFixed() should return the new fixed value")
    void testGetNewFixedReturnsNewValue() {
        // Arrange
        ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                editorContext, element, false, false, "newValue");

        // Act & Assert
        assertEquals("newValue", command.getNewFixed(), "getNewFixed() should return new value");
    }
}
