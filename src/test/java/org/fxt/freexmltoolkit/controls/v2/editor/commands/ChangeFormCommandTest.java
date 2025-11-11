package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChangeFormCommand.
 *
 * @since 2.0
 */
class ChangeFormCommandTest {

    private XsdEditorContext editorContext;
    private XsdElement element;
    private XsdAttribute attribute;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        editorContext = new XsdEditorContext(schema);
        element = new XsdElement("TestElement");
        attribute = new XsdAttribute("TestAttribute");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should throw exception for null context")
    void testConstructorThrowsExceptionForNullContext() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeFormCommand(null, element, "qualified"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeFormCommand(editorContext, null, "qualified"));
    }

    @Test
    @DisplayName("constructor should throw exception for invalid node type")
    void testConstructorThrowsExceptionForInvalidNodeType() {
        XsdComplexType complexType = new XsdComplexType("TestType");
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeFormCommand(editorContext, complexType, "qualified"));
    }

    @Test
    @DisplayName("constructor should accept XsdElement")
    void testConstructorAcceptsElement() {
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "qualified");
        assertNotNull(command);
    }

    @Test
    @DisplayName("constructor should accept XsdAttribute")
    void testConstructorAcceptsAttribute() {
        ChangeFormCommand command = new ChangeFormCommand(editorContext, attribute, "qualified");
        assertNotNull(command);
    }

    // ========== Execute Tests for Elements ==========

    @Test
    @DisplayName("execute() should set form on element")
    void testExecuteSetsFormOnElement() {
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "qualified");
        boolean result = command.execute();

        assertTrue(result);
        assertEquals("qualified", element.getForm());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should change element form to unqualified")
    void testExecuteChangesElementFormToUnqualified() {
        element.setForm("qualified");
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "unqualified");
        command.execute();

        assertEquals("unqualified", element.getForm());
    }

    @Test
    @DisplayName("execute() should set null form on element")
    void testExecuteSetsNullFormOnElement() {
        element.setForm("qualified");
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, null);
        command.execute();

        assertNull(element.getForm());
    }

    // ========== Execute Tests for Attributes ==========

    @Test
    @DisplayName("execute() should set form on attribute")
    void testExecuteSetsFormOnAttribute() {
        ChangeFormCommand command = new ChangeFormCommand(editorContext, attribute, "qualified");
        boolean result = command.execute();

        assertTrue(result);
        assertEquals("qualified", attribute.getForm());
        assertTrue(editorContext.isDirty());
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore old element form")
    void testUndoRestoresOldElementForm() {
        element.setForm("qualified");
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "unqualified");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertEquals("qualified", element.getForm());
    }

    @Test
    @DisplayName("undo() should restore old attribute form")
    void testUndoRestoresOldAttributeForm() {
        attribute.setForm("qualified");
        ChangeFormCommand command = new ChangeFormCommand(editorContext, attribute, "unqualified");
        command.execute();
        command.undo();

        assertEquals("qualified", attribute.getForm());
    }

    @Test
    @DisplayName("undo() should restore null form")
    void testUndoRestoresNullForm() {
        element.setForm(null);
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "qualified");
        command.execute();
        command.undo();

        assertNull(element.getForm());
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "qualified");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe form change")
    void testGetDescriptionDescribesFormChange() {
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "qualified");
        String description = command.getDescription();

        assertTrue(description.contains("Change form"));
        assertTrue(description.contains("TestElement"));
        assertTrue(description.contains("qualified"));
    }

    @Test
    @DisplayName("canMergeWith() should return true for same node")
    void testCanMergeWithSameNode() {
        ChangeFormCommand command1 = new ChangeFormCommand(editorContext, element, "qualified");
        ChangeFormCommand command2 = new ChangeFormCommand(editorContext, element, "unqualified");

        assertTrue(command1.canMergeWith(command2));
    }

    @Test
    @DisplayName("canMergeWith() should return false for different nodes")
    void testCanMergeWithDifferentNodes() {
        XsdElement element2 = new XsdElement("OtherElement");
        ChangeFormCommand command1 = new ChangeFormCommand(editorContext, element, "qualified");
        ChangeFormCommand command2 = new ChangeFormCommand(editorContext, element2, "qualified");

        assertFalse(command1.canMergeWith(command2));
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command type")
    void testCanMergeWithDifferentCommandType() {
        ChangeFormCommand command1 = new ChangeFormCommand(editorContext, element, "qualified");
        RenameNodeCommand command2 = new RenameNodeCommand(element, "NewName");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getNode() should return the node")
    void testGetNodeReturnsNode() {
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "qualified");
        assertEquals(element, command.getNode());
    }

    @Test
    @DisplayName("getOldForm() should return old value")
    void testGetOldFormReturnsOldValue() {
        element.setForm("qualified");
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "unqualified");
        assertEquals("qualified", command.getOldForm());
    }

    @Test
    @DisplayName("getNewForm() should return new value")
    void testGetNewFormReturnsNewValue() {
        ChangeFormCommand command = new ChangeFormCommand(editorContext, element, "qualified");
        assertEquals("qualified", command.getNewForm());
    }
}
