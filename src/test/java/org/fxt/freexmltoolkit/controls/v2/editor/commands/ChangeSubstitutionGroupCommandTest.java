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
 * Unit tests for ChangeSubstitutionGroupCommand.
 *
 * @since 2.0
 */
class ChangeSubstitutionGroupCommandTest {

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
                () -> new ChangeSubstitutionGroupCommand(null, element, "BaseElement"));
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeSubstitutionGroupCommand(editorContext, null, "BaseElement"));
    }

    @Test
    @DisplayName("constructor should throw exception for non-element node")
    void testConstructorThrowsExceptionForNonElement() {
        XsdAttribute attribute = new XsdAttribute("TestAttribute");
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeSubstitutionGroupCommand(editorContext, attribute, "BaseElement"));
    }

    @Test
    @DisplayName("constructor should accept valid element and substitution group")
    void testConstructorAcceptsValidParameters() {
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "BaseElement");
        assertNotNull(command);
    }

    @Test
    @DisplayName("constructor should normalize empty string to null")
    void testConstructorNormalizesEmptyStringToNull() {
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "   ");
        assertNull(command.getNewSubstitutionGroup());
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should set substitution group on element")
    void testExecuteSetsSubstitutionGroup() {
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "BaseElement");
        boolean result = command.execute();

        assertTrue(result);
        assertEquals("BaseElement", element.getSubstitutionGroup());
        assertTrue(editorContext.isDirty());
    }

    @Test
    @DisplayName("execute() should change substitution group")
    void testExecuteChangesSubstitutionGroup() {
        element.setSubstitutionGroup("OldBase");
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "NewBase");
        command.execute();

        assertEquals("NewBase", element.getSubstitutionGroup());
    }

    @Test
    @DisplayName("execute() should remove substitution group when null")
    void testExecuteRemovesSubstitutionGroup() {
        element.setSubstitutionGroup("BaseElement");
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, null);
        command.execute();

        assertNull(element.getSubstitutionGroup());
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore old substitution group")
    void testUndoRestoresOldSubstitutionGroup() {
        element.setSubstitutionGroup("OldBase");
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "NewBase");
        command.execute();
        boolean result = command.undo();

        assertTrue(result);
        assertEquals("OldBase", element.getSubstitutionGroup());
    }

    @Test
    @DisplayName("undo() should restore null substitution group")
    void testUndoRestoresNullSubstitutionGroup() {
        element.setSubstitutionGroup(null);
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "BaseElement");
        command.execute();
        command.undo();

        assertNull(element.getSubstitutionGroup());
    }

    // ========== Other Tests ==========

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "BaseElement");
        assertTrue(command.canUndo());
    }

    @Test
    @DisplayName("getDescription() should describe adding substitution group")
    void testGetDescriptionForAdd() {
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "BaseElement");
        String description = command.getDescription();

        assertTrue(description.contains("Add substitution group"));
        assertTrue(description.contains("TestElement"));
    }

    @Test
    @DisplayName("getDescription() should describe removing substitution group")
    void testGetDescriptionForRemove() {
        element.setSubstitutionGroup("BaseElement");
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, null);
        String description = command.getDescription();

        assertTrue(description.contains("Remove substitution group"));
        assertTrue(description.contains("TestElement"));
    }

    @Test
    @DisplayName("getDescription() should describe changing substitution group")
    void testGetDescriptionForChange() {
        element.setSubstitutionGroup("OldBase");
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "NewBase");
        String description = command.getDescription();

        assertTrue(description.contains("Change substitution group"));
        assertTrue(description.contains("TestElement"));
        assertTrue(description.contains("NewBase"));
    }

    @Test
    @DisplayName("canMergeWith() should return true for same element")
    void testCanMergeWithSameElement() {
        ChangeSubstitutionGroupCommand command1 = new ChangeSubstitutionGroupCommand(editorContext, element, "Base1");
        ChangeSubstitutionGroupCommand command2 = new ChangeSubstitutionGroupCommand(editorContext, element, "Base2");

        assertTrue(command1.canMergeWith(command2));
    }

    @Test
    @DisplayName("canMergeWith() should return false for different elements")
    void testCanMergeWithDifferentElements() {
        XsdElement element2 = new XsdElement("OtherElement");
        ChangeSubstitutionGroupCommand command1 = new ChangeSubstitutionGroupCommand(editorContext, element, "Base1");
        ChangeSubstitutionGroupCommand command2 = new ChangeSubstitutionGroupCommand(editorContext, element2, "Base2");

        assertFalse(command1.canMergeWith(command2));
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command type")
    void testCanMergeWithDifferentCommandType() {
        ChangeSubstitutionGroupCommand command1 = new ChangeSubstitutionGroupCommand(editorContext, element, "Base");
        RenameNodeCommand command2 = new RenameNodeCommand(element, "NewName");

        assertFalse(command1.canMergeWith(command2));
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getElement() should return the element")
    void testGetElementReturnsElement() {
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "BaseElement");
        assertEquals(element, command.getElement());
    }

    @Test
    @DisplayName("getOldSubstitutionGroup() should return old value")
    void testGetOldSubstitutionGroupReturnsOldValue() {
        element.setSubstitutionGroup("OldBase");
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "NewBase");
        assertEquals("OldBase", command.getOldSubstitutionGroup());
    }

    @Test
    @DisplayName("getNewSubstitutionGroup() should return new value")
    void testGetNewSubstitutionGroupReturnsNewValue() {
        ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(editorContext, element, "BaseElement");
        assertEquals("BaseElement", command.getNewSubstitutionGroup());
    }
}
