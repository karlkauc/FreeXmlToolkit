package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ChangeAppinfoCommand.
 *
 * @since 2.0
 */
class ChangeAppinfoCommandTest {

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
                () -> new ChangeAppinfoCommand(null, element, "New appinfo"),
                "Constructor should throw IllegalArgumentException for null context");
    }

    @Test
    @DisplayName("constructor should throw exception for null node")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeAppinfoCommand(editorContext, null, "New appinfo"),
                "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("constructor should accept null appinfo")
    void testConstructorAcceptsNullAppinfo() {
        // Act & Assert (should not throw)
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, null);
        assertNotNull(command, "Command should be created with null appinfo");
    }

    // ========== Execute Tests ==========

    @Test
    @DisplayName("execute() should set new appinfo")
    void testExecuteSetsAppinfo() {
        // Arrange
        String newAppinfo = "<custom:data>test</custom:data>";
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, newAppinfo);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(newAppinfo, element.getAppinfoAsString(), "Appinfo should be set");
        assertTrue(editorContext.isDirty(), "Context should be marked dirty");
    }

    @Test
    @DisplayName("execute() should replace existing appinfo")
    void testExecuteReplacesExistingAppinfo() {
        // Arrange
        element.setAppinfoFromString("<old>data</old>");
        String newAppinfo = "<new>data</new>";
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, newAppinfo);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(newAppinfo, element.getAppinfoAsString(), "Appinfo should be replaced");
    }

    @Test
    @DisplayName("execute() should remove appinfo with null value")
    void testExecuteRemovesAppinfoWithNull() {
        // Arrange
        element.setAppinfoFromString("<old>data</old>");
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, null);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals("", element.getAppinfoAsString(), "Appinfo should be removed (returns empty string)");
    }

    @Test
    @DisplayName("execute() should remove appinfo with empty string")
    void testExecuteRemovesAppinfoWithEmptyString() {
        // Arrange
        element.setAppinfoFromString("<old>data</old>");
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, "");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals("", element.getAppinfoAsString(), "Appinfo should be empty string");
    }

    // ========== Undo Tests ==========

    @Test
    @DisplayName("undo() should restore old appinfo")
    void testUndoRestoresOldAppinfo() {
        // Arrange
        String oldAppinfo = "<old>data</old>";
        element.setAppinfoFromString(oldAppinfo);
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, "<new>data</new>");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals(oldAppinfo, element.getAppinfoAsString(), "Old appinfo should be restored");
        assertTrue(editorContext.isDirty(), "Context should still be marked dirty after undo");
    }

    @Test
    @DisplayName("undo() should restore null appinfo")
    void testUndoRestoresNullAppinfo() {
        // Arrange
        element.setAppinfo((XsdAppInfo) null);
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, "<new>data</new>");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals("", element.getAppinfoAsString(), "Appinfo should be empty string after undo (null returns empty)");
    }

    @Test
    @DisplayName("canUndo() should return true")
    void testCanUndoReturnsTrue() {
        // Arrange
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, "<new>data</new>");

        // Act & Assert
        assertTrue(command.canUndo(), "Appinfo changes should be undoable");
    }

    // ========== Description Tests ==========

    @Test
    @DisplayName("getDescription() should describe adding appinfo")
    void testGetDescriptionForAddingAppinfo() {
        // Arrange
        element.setAppinfo((XsdAppInfo) null);
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, "<new>data</new>");

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("Add appinfo"), "Description should mention adding appinfo");
        assertTrue(description.contains("TestElement"), "Description should contain element name");
    }

    @Test
    @DisplayName("getDescription() should describe changing appinfo")
    void testGetDescriptionForChangingAppinfo() {
        // Arrange
        element.setAppinfoFromString("<old>data</old>");
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, "<new>data</new>");

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("Change appinfo"), "Description should mention changing appinfo");
        assertTrue(description.contains("TestElement"), "Description should contain element name");
    }

    @Test
    @DisplayName("getDescription() should describe removing appinfo")
    void testGetDescriptionForRemovingAppinfo() {
        // Arrange
        element.setAppinfoFromString("<old>data</old>");
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, null);

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("Remove appinfo"), "Description should mention removing appinfo");
        assertTrue(description.contains("TestElement"), "Description should contain element name");
    }

    // ========== Merge Tests ==========

    @Test
    @DisplayName("canMergeWith() should return true for same node")
    void testCanMergeWithSameNode() {
        // Arrange
        ChangeAppinfoCommand command1 = new ChangeAppinfoCommand(editorContext, element, "<data1/>");
        ChangeAppinfoCommand command2 = new ChangeAppinfoCommand(editorContext, element, "<data2/>");

        // Act & Assert
        assertTrue(command1.canMergeWith(command2), "Commands on same node should be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different nodes")
    void testCanMergeWithDifferentNodes() {
        // Arrange
        XsdElement element2 = new XsdElement("OtherElement");
        ChangeAppinfoCommand command1 = new ChangeAppinfoCommand(editorContext, element, "<data1/>");
        ChangeAppinfoCommand command2 = new ChangeAppinfoCommand(editorContext, element2, "<data2/>");

        // Act & Assert
        assertFalse(command1.canMergeWith(command2), "Commands on different nodes should not be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command type")
    void testCanMergeWithDifferentCommandType() {
        // Arrange
        ChangeAppinfoCommand command1 = new ChangeAppinfoCommand(editorContext, element, "<data1/>");
        RenameNodeCommand command2 = new RenameNodeCommand(element, "NewName");

        // Act & Assert
        assertFalse(command1.canMergeWith(command2), "Commands of different types should not be mergeable");
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getNode() should return the correct node")
    void testGetNodeReturnsCorrectNode() {
        // Arrange
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, "<new>data</new>");

        // Act & Assert
        assertEquals(element, command.getNode(), "getNode() should return the correct node");
    }

    @Test
    @DisplayName("getOldAppinfo() should return the old appinfo")
    void testGetOldAppinfoReturnsOldValue() {
        // Arrange
        String oldAppinfo = "<old>data</old>";
        element.setAppinfoFromString(oldAppinfo);
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, "<new>data</new>");

        // Act & Assert
        assertEquals(oldAppinfo, command.getOldAppinfo().toDisplayString(), "getOldAppinfo() should return old value");
    }

    @Test
    @DisplayName("getNewAppinfo() should return the new appinfo")
    void testGetNewAppinfoReturnsNewValue() {
        // Arrange
        String newAppinfo = "<new>data</new>";
        ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, element, newAppinfo);

        // Act & Assert
        assertEquals(newAppinfo, command.getNewAppinfo().toDisplayString(), "getNewAppinfo() should return new value");
    }
}
