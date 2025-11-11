package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for DuplicateNodeCommand.
 * Tests basic functionality, undo/redo, property change events, and error handling.
 *
 * @since 2.0
 */
class DuplicateNodeCommandTest {

    private XsdElement parentElement;
    private XsdElement originalElement;

    @BeforeEach
    void setUp() {
        parentElement = new XsdElement("parent");
        originalElement = new XsdElement("original");
        originalElement.setType("xs:string");
        originalElement.setMinOccurs(0);
        originalElement.setMaxOccurs(5);
        originalElement.setDocumentation("Test documentation");
        originalElement.setAppinfoFromString("Test appinfo");
        parentElement.addChild(originalElement);
    }

    // ========== Basic Functionality Tests ==========

    @Test
    @DisplayName("execute() should create deep copy with '_copy' suffix")
    void testExecuteCreatesCopyWithSuffix() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(2, parentElement.getChildren().size(), "Parent should have 2 children");

        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();
        assertNotNull(duplicatedElement, "Duplicated element should not be null");
        assertEquals("original_copy", duplicatedElement.getName(), "Duplicated element should have '_copy' suffix");
    }

    @Test
    @DisplayName("execute() should copy all properties")
    void testExecuteCopiesAllProperties() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();
        assertEquals(originalElement.getType(), duplicatedElement.getType(), "Type should be copied");
        assertEquals(originalElement.getMinOccurs(), duplicatedElement.getMinOccurs(), "minOccurs should be copied");
        assertEquals(originalElement.getMaxOccurs(), duplicatedElement.getMaxOccurs(), "maxOccurs should be copied");
        assertEquals(originalElement.getDocumentation(), duplicatedElement.getDocumentation(), "Documentation should be copied");
        assertEquals(originalElement.getAppinfo(), duplicatedElement.getAppinfo(), "Appinfo should be copied");
    }

    @Test
    @DisplayName("execute() should insert copy after original")
    void testExecuteInsertsCopyAfterOriginal() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        assertEquals(2, parentElement.getChildren().size(), "Parent should have 2 children");
        assertSame(originalElement, parentElement.getChildren().get(0), "Original should be at index 0");

        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();
        assertSame(duplicatedElement, parentElement.getChildren().get(1), "Copy should be at index 1");
    }

    @Test
    @DisplayName("execute() should recursively copy all children")
    void testExecuteCopiesChildrenRecursively() {
        // Arrange
        XsdElement child1 = new XsdElement("child1");
        child1.setType("xs:int");
        XsdElement grandchild = new XsdElement("grandchild");
        grandchild.setType("xs:boolean");

        originalElement.addChild(child1);
        child1.addChild(grandchild);

        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();
        assertEquals(1, duplicatedElement.getChildren().size(), "Duplicated element should have 1 child");

        XsdElement copiedChild = (XsdElement) duplicatedElement.getChildren().get(0);
        assertEquals("child1", copiedChild.getName(), "Child name should be copied (no suffix for children)");
        assertEquals("xs:int", copiedChild.getType(), "Child type should be copied");
        assertEquals(1, copiedChild.getChildren().size(), "Child should have 1 grandchild");

        XsdElement copiedGrandchild = (XsdElement) copiedChild.getChildren().get(0);
        assertEquals("grandchild", copiedGrandchild.getName(), "Grandchild name should be copied");
        assertEquals("xs:boolean", copiedGrandchild.getType(), "Grandchild type should be copied");
    }

    @Test
    @DisplayName("execute() should create independent copy (not affecting original)")
    void testExecuteCreatesIndependentCopy() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);
        command.execute();

        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();

        // Act - modify the copy
        duplicatedElement.setType("xs:int");
        duplicatedElement.setMinOccurs(1);

        // Assert - original should be unchanged
        assertEquals("xs:string", originalElement.getType(), "Original type should be unchanged");
        assertEquals(0, originalElement.getMinOccurs(), "Original minOccurs should be unchanged");
    }

    @Test
    @DisplayName("undo() should remove duplicated node")
    void testUndoRemovesDuplicatedNode() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals(1, parentElement.getChildren().size(), "Parent should have 1 child after undo");
        assertSame(originalElement, parentElement.getChildren().get(0), "Only original should remain");
    }

    @Test
    @DisplayName("undo() should return false when not executed")
    void testUndoReturnsFalseWhenNotExecuted() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        boolean result = command.undo();

        // Assert
        assertFalse(result, "undo() should return false when not executed");
    }

    @Test
    @DisplayName("canUndo() should return false before execute")
    void testCanUndoBeforeExecute() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Assert
        assertFalse(command.canUndo(), "canUndo() should return false before execute");
    }

    @Test
    @DisplayName("canUndo() should return true after execute")
    void testCanUndoAfterExecute() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true after execute");
    }

    @Test
    @DisplayName("getDescription() should return descriptive text with node type and name")
    void testGetDescription() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.toLowerCase().contains("duplicate"), "Description should contain 'duplicate'");
        assertTrue(description.contains("original"), "Description should contain node name");
        assertTrue(description.toLowerCase().contains("element"), "Description should contain node type");
    }

    @Test
    @DisplayName("canMergeWith() should always return false")
    void testCanMergeWith() {
        // Arrange
        DuplicateNodeCommand command1 = new DuplicateNodeCommand(originalElement);
        DuplicateNodeCommand command2 = new DuplicateNodeCommand(originalElement);

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Duplicate commands should not be mergeable");
    }

    @Test
    @DisplayName("getDuplicatedNode() should return null before execute")
    void testGetDuplicatedNodeBeforeExecute() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        XsdNode duplicatedNode = command.getDuplicatedNode();

        // Assert
        assertNull(duplicatedNode, "getDuplicatedNode() should return null before execute");
    }

    @Test
    @DisplayName("getDuplicatedNode() should return duplicated node after execute")
    void testGetDuplicatedNodeAfterExecute() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();
        XsdNode duplicatedNode = command.getDuplicatedNode();

        // Assert
        assertNotNull(duplicatedNode, "getDuplicatedNode() should return node after execute");
        assertEquals("original_copy", duplicatedNode.getName(), "Duplicated node should have correct name");
    }

    // ========== PropertyChangeEvent Tests ==========

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent for 'children' property on parent")
    void testExecuteFiresPropertyChangeEvent() {
        // Arrange
        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        parentElement.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("children", capturedEvent.get().getPropertyName(), "Property name should be 'children'");
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent for 'children' property on parent")
    void testUndoFiresPropertyChangeEvent() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);
        command.execute();

        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        parentElement.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        // Act
        command.undo();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("children", capturedEvent.get().getPropertyName(), "Property name should be 'children'");
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when node is null")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new DuplicateNodeCommand(null);
        }, "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("execute() should return false when trying to duplicate root node")
    void testExecuteReturnsFalseForRootNode() {
        // Arrange - create a root node without parent
        XsdElement rootElement = new XsdElement("root");
        DuplicateNodeCommand command = new DuplicateNodeCommand(rootElement);

        // Act
        boolean result = command.execute();

        // Assert
        assertFalse(result, "execute() should return false for root node");
    }

    @Test
    @DisplayName("undo() should return false when parent is null")
    void testUndoReturnsFalseWhenParentIsNull() {
        // Arrange - create a root node without parent
        XsdElement rootElement = new XsdElement("root");
        DuplicateNodeCommand command = new DuplicateNodeCommand(rootElement);

        // Act
        boolean result = command.undo();

        // Assert
        assertFalse(result, "undo() should return false when parent is null");
    }

    // ========== Multiple Execute/Undo Tests ==========

    @Test
    @DisplayName("Execute-undo-execute sequence should work correctly")
    void testExecuteUndoExecuteSequence() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act & Assert
        command.execute();
        assertEquals(2, parentElement.getChildren().size(), "Parent should have 2 children after execute");

        command.undo();
        assertEquals(1, parentElement.getChildren().size(), "Parent should have 1 child after undo");

        command.execute();
        assertEquals(2, parentElement.getChildren().size(), "Parent should have 2 children after re-execute");
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should duplicate node with complex XsdElement properties")
    void testDuplicateNodeWithComplexProperties() {
        // Arrange
        originalElement.setNillable(true);
        originalElement.setAbstract(true);
        originalElement.setFixed("fixedValue");
        originalElement.setDefaultValue("defaultValue");
        originalElement.setSubstitutionGroup("substitutionGroup");

        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();
        assertEquals(originalElement.isNillable(), duplicatedElement.isNillable(), "Nillable should be copied");
        assertEquals(originalElement.isAbstract(), duplicatedElement.isAbstract(), "Abstract should be copied");
        assertEquals(originalElement.getFixed(), duplicatedElement.getFixed(), "Fixed should be copied");
        assertEquals(originalElement.getDefaultValue(), duplicatedElement.getDefaultValue(), "DefaultValue should be copied");
        assertEquals(originalElement.getSubstitutionGroup(), duplicatedElement.getSubstitutionGroup(), "SubstitutionGroup should be copied");
    }

    @Test
    @DisplayName("Should insert copy at correct position when original has siblings")
    void testInsertCopyWithSiblings() {
        // Arrange
        XsdElement sibling1 = new XsdElement("sibling1");
        XsdElement sibling2 = new XsdElement("sibling2");
        parentElement.addChild(sibling1);
        parentElement.addChild(sibling2);
        // Order: originalElement (0), sibling1 (1), sibling2 (2)

        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        assertEquals(4, parentElement.getChildren().size(), "Parent should have 4 children");
        assertSame(originalElement, parentElement.getChildren().get(0), "Original should be at index 0");
        assertSame(command.getDuplicatedNode(), parentElement.getChildren().get(1), "Copy should be at index 1");
        assertSame(sibling1, parentElement.getChildren().get(2), "sibling1 should be at index 2");
        assertSame(sibling2, parentElement.getChildren().get(3), "sibling2 should be at index 3");
    }

    @Test
    @DisplayName("Should duplicate last child correctly")
    void testDuplicateLastChild() {
        // Arrange
        XsdElement sibling1 = new XsdElement("sibling1");
        XsdElement lastChild = new XsdElement("lastChild");
        parentElement.addChild(sibling1);
        parentElement.addChild(lastChild);
        // Order: originalElement (0), sibling1 (1), lastChild (2)

        DuplicateNodeCommand command = new DuplicateNodeCommand(lastChild);

        // Act
        command.execute();

        // Assert
        assertEquals(4, parentElement.getChildren().size(), "Parent should have 4 children");
        assertSame(lastChild, parentElement.getChildren().get(2), "lastChild should be at index 2");
        assertSame(command.getDuplicatedNode(), parentElement.getChildren().get(3), "Copy should be at index 3");
    }

    @Test
    @DisplayName("Should handle duplicating multiple times")
    void testMultipleDuplications() {
        // Arrange & Act
        DuplicateNodeCommand command1 = new DuplicateNodeCommand(originalElement);
        command1.execute();

        DuplicateNodeCommand command2 = new DuplicateNodeCommand(originalElement);
        command2.execute();

        // Assert
        assertEquals(3, parentElement.getChildren().size(), "Parent should have 3 children");
        assertEquals("original", parentElement.getChildren().get(0).getName(), "Original at index 0");
        assertEquals("original_copy", parentElement.getChildren().get(1).getName(), "First copy at index 1");
        assertEquals("original_copy", parentElement.getChildren().get(2).getName(), "Second copy at index 2");
    }

    @Test
    @DisplayName("Should handle deep hierarchy duplication")
    void testDeepHierarchyDuplication() {
        // Arrange - create a 3-level deep hierarchy
        XsdElement level1 = new XsdElement("level1");
        level1.setType("xs:string");
        XsdElement level2 = new XsdElement("level2");
        level2.setType("xs:int");
        XsdElement level3 = new XsdElement("level3");
        level3.setType("xs:boolean");

        originalElement.addChild(level1);
        level1.addChild(level2);
        level2.addChild(level3);

        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();

        // Check level 1
        assertEquals(1, duplicatedElement.getChildren().size(), "Duplicated should have 1 child");
        XsdElement copiedLevel1 = (XsdElement) duplicatedElement.getChildren().get(0);
        assertEquals("level1", copiedLevel1.getName(), "Level 1 name should be copied");
        assertEquals("xs:string", copiedLevel1.getType(), "Level 1 type should be copied");

        // Check level 2
        assertEquals(1, copiedLevel1.getChildren().size(), "Level 1 should have 1 child");
        XsdElement copiedLevel2 = (XsdElement) copiedLevel1.getChildren().get(0);
        assertEquals("level2", copiedLevel2.getName(), "Level 2 name should be copied");
        assertEquals("xs:int", copiedLevel2.getType(), "Level 2 type should be copied");

        // Check level 3
        assertEquals(1, copiedLevel2.getChildren().size(), "Level 2 should have 1 child");
        XsdElement copiedLevel3 = (XsdElement) copiedLevel2.getChildren().get(0);
        assertEquals("level3", copiedLevel3.getName(), "Level 3 name should be copied");
        assertEquals("xs:boolean", copiedLevel3.getType(), "Level 3 type should be copied");
    }

    @Test
    @DisplayName("Should ensure copied nodes have unique IDs")
    void testCopiedNodesHaveUniqueIds() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();
        assertNotEquals(originalElement.getId(), duplicatedElement.getId(),
                "Duplicated element should have a different ID than original");
    }

    @Test
    @DisplayName("Should set correct parent reference on duplicated node")
    void testParentReferenceOnDuplicatedNode() {
        // Arrange
        DuplicateNodeCommand command = new DuplicateNodeCommand(originalElement);

        // Act
        command.execute();

        // Assert
        XsdElement duplicatedElement = (XsdElement) command.getDuplicatedNode();
        assertSame(parentElement, duplicatedElement.getParent(), "Duplicated element should have correct parent reference");
    }
}
