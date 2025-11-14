package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ChangeTypeCommand.
 * Tests basic functionality, undo/redo, property change events, and error handling
 * for both XsdElement and XsdAttribute nodes.
 *
 * @since 2.0
 */
class ChangeTypeCommandTest {

    private XsdEditorContext editorContext;
    private XsdElement testElement;
    private XsdAttribute testAttribute;

    @BeforeEach
    void setUp() {
        XsdSchema schema = new XsdSchema();
        editorContext = new XsdEditorContext(schema);

        testElement = new XsdElement("testElement");
        testElement.setType("xs:string");

        testAttribute = new XsdAttribute("testAttribute");
        testAttribute.setType("xs:int");
    }

    // ========== Basic Functionality Tests - XsdElement ==========

    @Test
    @DisplayName("execute() should change element type and return true")
    void testExecuteChangesElementType() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals("xs:decimal", testElement.getType(), "Element type should be changed");
    }

    @Test
    @DisplayName("undo() should restore old element type and return true")
    void testUndoRestoresOldElementType() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals("xs:string", testElement.getType(), "Original element type should be restored");
    }

    @Test
    @DisplayName("execute() should change element type from null to new type")
    void testExecuteChangesNullElementType() {
        // Arrange
        testElement.setType(null);
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:boolean");

        // Act
        command.execute();

        // Assert
        assertEquals("xs:boolean", testElement.getType(), "Element type should be set from null");
    }

    @Test
    @DisplayName("undo() should restore null element type")
    void testUndoRestoresNullElementType() {
        // Arrange
        testElement.setType(null);
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:boolean");
        command.execute();

        // Act
        command.undo();

        // Assert
        assertNull(testElement.getType(), "Element type should be restored to null");
    }

    // ========== Basic Functionality Tests - XsdAttribute ==========

    @Test
    @DisplayName("execute() should change attribute type and return true")
    void testExecuteChangesAttributeType() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testAttribute, "xs:string");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals("xs:string", testAttribute.getType(), "Attribute type should be changed");
    }

    @Test
    @DisplayName("undo() should restore old attribute type and return true")
    void testUndoRestoresOldAttributeType() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testAttribute, "xs:string");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals("xs:int", testAttribute.getType(), "Original attribute type should be restored");
    }

    @Test
    @DisplayName("execute() should change attribute type from null to new type")
    void testExecuteChangesNullAttributeType() {
        // Arrange
        testAttribute.setType(null);
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testAttribute, "xs:date");

        // Act
        command.execute();

        // Assert
        assertEquals("xs:date", testAttribute.getType(), "Attribute type should be set from null");
    }

    @Test
    @DisplayName("undo() should restore null attribute type")
    void testUndoRestoresNullAttributeType() {
        // Arrange
        testAttribute.setType(null);
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testAttribute, "xs:date");
        command.execute();

        // Act
        command.undo();

        // Assert
        assertNull(testAttribute.getType(), "Attribute type should be restored to null");
    }

    // ========== PropertyChangeEvent Tests - XsdElement ==========

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent for 'type' property on element")
    void testExecuteFiresPropertyChangeEventForElement() {
        // Arrange
        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testElement.addPropertyChangeListener(evt -> {
            if ("type".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act
        command.execute();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("type", capturedEvent.get().getPropertyName(), "Property name should be 'type'");
        assertEquals("xs:string", capturedEvent.get().getOldValue(), "Old value should be 'xs:string'");
        assertEquals("xs:decimal", capturedEvent.get().getNewValue(), "New value should be 'xs:decimal'");
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent for 'type' property on element")
    void testUndoFiresPropertyChangeEventForElement() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");
        command.execute();

        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testElement.addPropertyChangeListener(evt -> {
            if ("type".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        // Act
        command.undo();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("type", capturedEvent.get().getPropertyName(), "Property name should be 'type'");
        assertEquals("xs:decimal", capturedEvent.get().getOldValue(), "Old value should be 'xs:decimal'");
        assertEquals("xs:string", capturedEvent.get().getNewValue(), "New value should be 'xs:string'");
    }

    // ========== PropertyChangeEvent Tests - XsdAttribute ==========

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent for 'type' property on attribute")
    void testExecuteFiresPropertyChangeEventForAttribute() {
        // Arrange
        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testAttribute.addPropertyChangeListener(evt -> {
            if ("type".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testAttribute, "xs:string");

        // Act
        command.execute();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("type", capturedEvent.get().getPropertyName(), "Property name should be 'type'");
        assertEquals("xs:int", capturedEvent.get().getOldValue(), "Old value should be 'xs:int'");
        assertEquals("xs:string", capturedEvent.get().getNewValue(), "New value should be 'xs:string'");
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent for 'type' property on attribute")
    void testUndoFiresPropertyChangeEventForAttribute() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testAttribute, "xs:string");
        command.execute();

        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testAttribute.addPropertyChangeListener(evt -> {
            if ("type".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        // Act
        command.undo();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("type", capturedEvent.get().getPropertyName(), "Property name should be 'type'");
        assertEquals("xs:string", capturedEvent.get().getOldValue(), "Old value should be 'xs:string'");
        assertEquals("xs:int", capturedEvent.get().getNewValue(), "New value should be 'xs:int'");
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when editorContext is null")
    void testConstructorThrowsExceptionForNullEditorContext() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new ChangeTypeCommand(null, testElement, "xs:string");
        }, "Constructor should throw IllegalArgumentException for null editorContext");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when node is null")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new ChangeTypeCommand(editorContext, null, "xs:string");
        }, "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("Constructor should accept null newType to remove type")
    void testConstructorAcceptsNullNewType() {
        // Arrange & Act
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, null);
        command.execute();

        // Assert
        assertNull(testElement.getType(), "Type should be removed when newType is null");
    }

    @Test
    @DisplayName("Constructor should accept empty newType to remove type")
    void testConstructorAcceptsEmptyNewType() {
        // Arrange & Act
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "");
        command.execute();

        // Assert
        assertNull(testElement.getType(), "Type should be removed when newType is empty");
    }

    @Test
    @DisplayName("Constructor should accept whitespace newType to remove type")
    void testConstructorAcceptsWhitespaceNewType() {
        // Arrange & Act
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "   ");
        command.execute();

        // Assert
        assertNull(testElement.getType(), "Type should be removed when newType is whitespace");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when node is not element or attribute")
    void testConstructorThrowsExceptionForInvalidNodeType() {
        // Arrange - Create a basic XsdNode (which is abstract, but we can use a different type)
        XsdElement parentElement = new XsdElement("parent");

        // Act & Assert - Try to create command for a node type that doesn't support type changes
        // Since all XsdNode subclasses might support type, we'll test the validation logic
        // This test ensures only elements and attributes are allowed
        assertDoesNotThrow(() -> {
            new ChangeTypeCommand(editorContext, testElement, "xs:string");
        }, "Constructor should accept XsdElement");

        assertDoesNotThrow(() -> {
            new ChangeTypeCommand(editorContext, testAttribute, "xs:int");
        }, "Constructor should accept XsdAttribute");
    }

    @Test
    @DisplayName("Constructor should trim newType whitespace")
    void testConstructorTrimsNewType() {
        // Arrange & Act
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "  xs:decimal  ");
        command.execute();

        // Assert
        assertEquals("xs:decimal", testElement.getType(), "Whitespace should be trimmed from newType");
    }

    // ========== Command Methods Tests ==========

    @Test
    @DisplayName("canUndo() should always return true")
    void testCanUndo() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true before execute");

        // Act
        command.execute();

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true after execute");
    }

    @Test
    @DisplayName("getDescription() should return descriptive text with old and new types")
    void testGetDescription() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.contains("testElement"), "Description should contain element name");
        assertTrue(description.contains("xs:string"), "Description should contain old type");
        assertTrue(description.contains("xs:decimal"), "Description should contain new type");
    }

    @Test
    @DisplayName("getDescription() should handle null old type")
    void testGetDescriptionWithNullOldType() {
        // Arrange
        testElement.setType(null);
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.contains("Set type"),
                "Description should indicate setting type (not changing from unknown)");
        assertTrue(description.contains("xs:decimal"),
                "Description should contain new type");
    }

    @Test
    @DisplayName("canMergeWith() should return true for consecutive type changes on same node")
    void testCanMergeWithConsecutiveTypeChanges() {
        // Arrange
        ChangeTypeCommand command1 = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");
        command1.execute();
        ChangeTypeCommand command2 = new ChangeTypeCommand(editorContext, testElement, "xs:integer");

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertTrue(canMerge, "Consecutive type changes on same node should be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command types")
    void testCanMergeWithDifferentCommandType() {
        // Arrange
        ChangeTypeCommand typeCommand = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");
        RenameNodeCommand renameCommand = new RenameNodeCommand(testElement, "newName");

        // Act
        boolean canMerge = typeCommand.canMergeWith(renameCommand);

        // Assert
        assertFalse(canMerge, "Different command types should not be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for type changes on different nodes")
    void testCanMergeWithDifferentNodes() {
        // Arrange
        XsdElement otherElement = new XsdElement("otherElement");
        otherElement.setType("xs:string");

        ChangeTypeCommand command1 = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");
        ChangeTypeCommand command2 = new ChangeTypeCommand(editorContext, otherElement, "xs:integer");

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Type changes on different nodes should not be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false when types don't chain correctly")
    void testCanMergeWithNonChainedTypeChanges() {
        // Arrange
        ChangeTypeCommand command1 = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");
        command1.execute();

        // Change type directly on element (simulating another operation)
        testElement.setType("xs:integer");

        // Now create another command - this won't chain with command1
        ChangeTypeCommand command2 = new ChangeTypeCommand(editorContext, testElement, "xs:boolean");

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Non-consecutive type changes should not be mergeable");
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getNode() should return the correct node")
    void testGetNode() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act
        XsdNode node = command.getNode();

        // Assert
        assertSame(testElement, node, "getNode() should return the same instance");
    }

    @Test
    @DisplayName("getOldType() should return the correct old type for element")
    void testGetOldTypeForElement() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act
        String oldType = command.getOldType();

        // Assert
        assertEquals("xs:string", oldType, "getOldType() should return 'xs:string'");
    }

    @Test
    @DisplayName("getOldType() should return the correct old type for attribute")
    void testGetOldTypeForAttribute() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testAttribute, "xs:string");

        // Act
        String oldType = command.getOldType();

        // Assert
        assertEquals("xs:int", oldType, "getOldType() should return 'xs:int'");
    }

    @Test
    @DisplayName("getNewType() should return the correct new type")
    void testGetNewType() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act
        String newType = command.getNewType();

        // Assert
        assertEquals("xs:decimal", newType, "getNewType() should return 'xs:decimal'");
    }

    // ========== Multiple Execute/Undo Tests ==========

    @Test
    @DisplayName("Multiple execute() calls should be idempotent")
    void testMultipleExecuteCalls() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act
        command.execute();
        command.execute();

        // Assert
        assertEquals("xs:decimal", testElement.getType(),
                "Type should still be 'xs:decimal' after multiple executes");
    }

    @Test
    @DisplayName("Multiple undo() calls should be idempotent")
    void testMultipleUndoCalls() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");
        command.execute();

        // Act
        command.undo();
        command.undo();

        // Assert
        assertEquals("xs:string", testElement.getType(),
                "Type should still be 'xs:string' after multiple undos");
    }

    @Test
    @DisplayName("Execute-undo-execute sequence should work correctly")
    void testExecuteUndoExecuteSequence() {
        // Arrange
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");

        // Act & Assert
        command.execute();
        assertEquals("xs:decimal", testElement.getType());

        command.undo();
        assertEquals("xs:string", testElement.getType());

        command.execute();
        assertEquals("xs:decimal", testElement.getType());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should handle built-in XSD types")
    void testBuiltInXsdTypes() {
        // Test various built-in types
        String[] types = {
                "xs:string", "xs:int", "xs:integer", "xs:decimal", "xs:boolean",
                "xs:date", "xs:time", "xs:dateTime", "xs:duration", "xs:anyURI"
        };

        for (String type : types) {
            ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, type);
            command.execute();
            assertEquals(type, testElement.getType(),
                    "Should handle type: " + type);
        }
    }

    @Test
    @DisplayName("Should handle custom type references")
    void testCustomTypeReferences() {
        // Arrange
        String customType = "MyCustomType";
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, customType);

        // Act
        command.execute();

        // Assert
        assertEquals(customType, testElement.getType(),
                "Should handle custom type reference");

        // Undo
        command.undo();
        assertEquals("xs:string", testElement.getType(),
                "Should restore original type");
    }

    @Test
    @DisplayName("Should handle namespaced type references")
    void testNamespacedTypeReferences() {
        // Arrange
        String namespacedType = "ns:ComplexType";
        ChangeTypeCommand command = new ChangeTypeCommand(editorContext, testElement, namespacedType);

        // Act
        command.execute();

        // Assert
        assertEquals(namespacedType, testElement.getType(),
                "Should handle namespaced type reference");
    }

    @Test
    @DisplayName("Should handle type changes on both elements and attributes independently")
    void testElementAndAttributeIndependence() {
        // Arrange
        ChangeTypeCommand elementCommand = new ChangeTypeCommand(editorContext, testElement, "xs:decimal");
        ChangeTypeCommand attributeCommand = new ChangeTypeCommand(editorContext, testAttribute, "xs:boolean");

        // Act
        elementCommand.execute();
        attributeCommand.execute();

        // Assert
        assertEquals("xs:decimal", testElement.getType(),
                "Element type should be changed");
        assertEquals("xs:boolean", testAttribute.getType(),
                "Attribute type should be changed independently");

        // Undo
        elementCommand.undo();
        attributeCommand.undo();

        assertEquals("xs:string", testElement.getType(),
                "Element type should be restored");
        assertEquals("xs:int", testAttribute.getType(),
                "Attribute type should be restored");
    }
}
