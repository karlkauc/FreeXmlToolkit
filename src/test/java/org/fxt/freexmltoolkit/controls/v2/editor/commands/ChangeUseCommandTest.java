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
 * Unit tests for ChangeUseCommand.
 *
 * @since 2.0
 */
class ChangeUseCommandTest {

    private XsdEditorContext editorContext;
    private XsdAttribute attribute;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        editorContext = new XsdEditorContext(schema);
        attribute = new XsdAttribute("TestAttribute");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw exception for null context")
    void testConstructorThrowsExceptionForNullContext() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeUseCommand(null, attribute, "required"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeUseCommand(editorContext, null, "required"));
    }

    @Test
    @DisplayName("constructor should throw exception for non-attribute node")
    void testConstructorThrowsExceptionForNonAttributeNode() {
        XsdElement element = new XsdElement("TestElement");
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeUseCommand(editorContext, element, "required"));
    }

    @Test
    @DisplayName("constructor should throw exception for invalid use value")
    void testConstructorThrowsExceptionForInvalidUseValue() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeUseCommand(editorContext, attribute, "invalid"));
    }

    @Test
    @DisplayName("constructor should accept valid attribute and use")
    void testConstructorAcceptsValidAttribute() {
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "required");
        assertNotNull(command);
    }

    @Test
    @DisplayName("constructor should default to optional for null use")
    void testConstructorDefaultsToOptionalForNullUse() {
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, null);
        assertEquals("optional", command.getNewUse());
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should set use to required")
    void testExecuteSetsUseToRequired() {
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "required");
        boolean result = command.execute();

        assertTrue(result);
        assertEquals("required", attribute.getUse());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should set use to optional")
    void testExecuteSetsUseToOptional() {
        attribute.setUse("required");
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "optional");
        command.execute();

        assertEquals("optional", attribute.getUse());
    }

    @Test
    @DisplayName("execute() should set use to prohibited")
    void testExecuteSetsUseToProhibited() {
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "prohibited");
        command.execute();

        assertEquals("prohibited", attribute.getUse());
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore old use value")
    void testUndoRestoresOldUseValue() {
        attribute.setUse("required");
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "optional");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertEquals("required", attribute.getUse());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("undo() should restore default optional use")
    void testUndoRestoresDefaultOptionalUse() {
        // XsdAttribute defaults to "optional" in constructor
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "required");
        command.execute();
        command.undo();

        assertEquals("optional", attribute.getUse());
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "required");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe use change")
    void testGetDescriptionDescribesUseChange() {
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "required");
        String description = command.getDescription();

        assertTrue(description.contains("Change use"));
        assertTrue(description.contains("TestAttribute"));
        assertTrue(description.contains("required"));
    }

    @Test
    @DisplayName("canMergeWith() should return true for same attribute")
    void testCanMergeWithSameAttribute() {
        ChangeUseCommand command1 = new ChangeUseCommand(editorContext, attribute, "required");
        ChangeUseCommand command2 = new ChangeUseCommand(editorContext, attribute, "optional");

        assertTrue(command1.canMergeWith(command2));
    }

    @Test
    @DisplayName("canMergeWith() should return false for different attributes")
    void testCanMergeWithDifferentAttributes() {
        XsdAttribute attribute2 = new XsdAttribute("OtherAttribute");
        ChangeUseCommand command1 = new ChangeUseCommand(editorContext, attribute, "required");
        ChangeUseCommand command2 = new ChangeUseCommand(editorContext, attribute2, "required");

        assertFalse(command1.canMergeWith(command2));
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command type")
    void testCanMergeWithDifferentCommandType() {
        ChangeUseCommand command1 = new ChangeUseCommand(editorContext, attribute, "required");
        RenameNodeCommand command2 = new RenameNodeCommand(attribute, "NewName");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getAttribute() should return the attribute")
    void testGetAttributeReturnsAttribute() {
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "required");
        assertEquals(attribute, command.getAttribute());
    }

    @Test
    @DisplayName("getOldUse() should return old value")
    void testGetOldUseReturnsOldValue() {
        attribute.setUse("required");
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "optional");
        assertEquals("required", command.getOldUse());
    }

    @Test
    @DisplayName("getNewUse() should return new value")
    void testGetNewUseReturnsNewValue() {
        ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, "required");
        assertEquals("required", command.getNewUse());
    }
}
