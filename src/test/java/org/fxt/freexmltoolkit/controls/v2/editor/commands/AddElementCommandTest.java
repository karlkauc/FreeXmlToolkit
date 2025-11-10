package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for AddElementCommand.
 * Tests basic functionality, undo/redo, property change events, and error handling.
 *
 * @since 2.0
 */
class AddElementCommandTest {

    private XsdElement parentElement;

    @BeforeEach
    void setUp() {
        parentElement = new XsdElement("parent");
    }

    // ========== Basic Functionality Tests ==========

    @Test
    @DisplayName("execute() should create and add element with default type")
    void testExecuteCreatesElementWithDefaultType() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(1, parentElement.getChildren().size(), "Parent should have 1 child");

        XsdElement addedElement = (XsdElement) parentElement.getChildren().get(0);
        assertEquals("newElement", addedElement.getName(), "Element name should be 'newElement'");
        assertEquals("xs:string", addedElement.getType(), "Default type should be 'xs:string'");
    }

    @Test
    @DisplayName("execute() should create and add element with specified type")
    void testExecuteCreatesElementWithSpecifiedType() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement", "xs:int");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(1, parentElement.getChildren().size(), "Parent should have 1 child");

        XsdElement addedElement = (XsdElement) parentElement.getChildren().get(0);
        assertEquals("newElement", addedElement.getName(), "Element name should be 'newElement'");
        assertEquals("xs:int", addedElement.getType(), "Type should be 'xs:int'");
    }

    @Test
    @DisplayName("undo() should remove added element")
    void testUndoRemovesElement() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals(0, parentElement.getChildren().size(), "Parent should have no children");
    }

    @Test
    @DisplayName("undo() should return false when element was not added")
    void testUndoReturnsFalseWhenNotExecuted() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act (no execute call)
        boolean result = command.undo();

        // Assert
        assertFalse(result, "undo() should return false when element was not added");
    }

    @Test
    @DisplayName("canUndo() should return false before execute")
    void testCanUndoBeforeExecute() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Assert
        assertFalse(command.canUndo(), "canUndo() should return false before execute");
    }

    @Test
    @DisplayName("canUndo() should return true after execute")
    void testCanUndoAfterExecute() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act
        command.execute();

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true after execute");
    }

    @Test
    @DisplayName("getDescription() should return descriptive text")
    void testGetDescription() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.toLowerCase().contains("add"), "Description should contain 'add'");
        assertTrue(description.contains("newElement"), "Description should contain element name");
        assertTrue(description.contains("parent"), "Description should contain parent name");
    }

    @Test
    @DisplayName("canMergeWith() should always return false")
    void testCanMergeWith() {
        // Arrange
        AddElementCommand command1 = new AddElementCommand(parentElement, "element1");
        AddElementCommand command2 = new AddElementCommand(parentElement, "element2");

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Add commands should not be mergeable");
    }

    @Test
    @DisplayName("getAddedElement() should return null before execute")
    void testGetAddedElementBeforeExecute() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act
        XsdElement element = command.getAddedElement();

        // Assert
        assertNull(element, "getAddedElement() should return null before execute");
    }

    @Test
    @DisplayName("getAddedElement() should return added element after execute")
    void testGetAddedElementAfterExecute() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act
        command.execute();
        XsdElement element = command.getAddedElement();

        // Assert
        assertNotNull(element, "getAddedElement() should return element after execute");
        assertEquals("newElement", element.getName(), "Element name should be 'newElement'");
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

        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

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
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");
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
    @DisplayName("Constructor should throw IllegalArgumentException when parent is null")
    void testConstructorThrowsExceptionForNullParent() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new AddElementCommand(null, "newElement");
        }, "Constructor should throw IllegalArgumentException for null parent");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when element name is null")
    void testConstructorThrowsExceptionForNullElementName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new AddElementCommand(parentElement, null);
        }, "Constructor should throw IllegalArgumentException for null element name");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when element name is empty")
    void testConstructorThrowsExceptionForEmptyElementName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new AddElementCommand(parentElement, "");
        }, "Constructor should throw IllegalArgumentException for empty element name");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when element name is whitespace")
    void testConstructorThrowsExceptionForWhitespaceElementName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new AddElementCommand(parentElement, "   ");
        }, "Constructor should throw IllegalArgumentException for whitespace element name");
    }

    @Test
    @DisplayName("Constructor should trim element name whitespace")
    void testConstructorTrimsElementName() {
        // Arrange & Act
        AddElementCommand command = new AddElementCommand(parentElement, "  newElement  ");
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertEquals("newElement", addedElement.getName(), "Whitespace should be trimmed from element name");
    }

    @Test
    @DisplayName("Constructor should use default type when type is null")
    void testConstructorUsesDefaultTypeWhenTypeIsNull() {
        // Arrange & Act
        AddElementCommand command = new AddElementCommand(parentElement, "newElement", null);
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertEquals("xs:string", addedElement.getType(), "Default type should be used when type is null");
    }

    // ========== Multiple Execute/Undo Tests ==========

    @Test
    @DisplayName("Execute-undo-execute sequence should work correctly")
    void testExecuteUndoExecuteSequence() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act & Assert
        command.execute();
        assertEquals(1, parentElement.getChildren().size(), "Parent should have 1 child after execute");

        command.undo();
        assertEquals(0, parentElement.getChildren().size(), "Parent should have no children after undo");

        command.execute();
        assertEquals(1, parentElement.getChildren().size(), "Parent should have 1 child after re-execute");
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should add element to parent with existing children")
    void testAddElementToParentWithExistingChildren() {
        // Arrange
        XsdElement existingChild = new XsdElement("existingChild");
        parentElement.addChild(existingChild);

        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act
        command.execute();

        // Assert
        assertEquals(2, parentElement.getChildren().size(), "Parent should have 2 children");
        assertSame(existingChild, parentElement.getChildren().get(0), "Existing child should be at index 0");
        assertEquals("newElement", parentElement.getChildren().get(1).getName(), "New element should be at index 1");
    }

    @Test
    @DisplayName("Should set parent reference correctly on added element")
    void testParentReferenceOnAddedElement() {
        // Arrange
        AddElementCommand command = new AddElementCommand(parentElement, "newElement");

        // Act
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertSame(parentElement, addedElement.getParent(), "Added element should have correct parent reference");
    }

    @Test
    @DisplayName("Should handle special characters in element name")
    void testAddElementWithSpecialCharacters() {
        // Arrange
        String specialName = "element_with-special.chars:123";
        AddElementCommand command = new AddElementCommand(parentElement, specialName);

        // Act
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertEquals(specialName, addedElement.getName(), "Special characters should be preserved");
    }

    @Test
    @DisplayName("Should handle unicode characters in element name")
    void testAddElementWithUnicodeCharacters() {
        // Arrange
        String unicodeName = "元素名称";
        AddElementCommand command = new AddElementCommand(parentElement, unicodeName);

        // Act
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertEquals(unicodeName, addedElement.getName(), "Unicode characters should be preserved");
    }

    @Test
    @DisplayName("Should handle custom type names")
    void testAddElementWithCustomType() {
        // Arrange
        String customType = "myNamespace:CustomType";
        AddElementCommand command = new AddElementCommand(parentElement, "newElement", customType);

        // Act
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertEquals(customType, addedElement.getType(), "Custom type should be preserved");
    }

    @Test
    @DisplayName("Should handle adding multiple elements sequentially")
    void testAddMultipleElements() {
        // Arrange
        AddElementCommand command1 = new AddElementCommand(parentElement, "element1", "xs:string");
        AddElementCommand command2 = new AddElementCommand(parentElement, "element2", "xs:int");
        AddElementCommand command3 = new AddElementCommand(parentElement, "element3", "xs:boolean");

        // Act
        command1.execute();
        command2.execute();
        command3.execute();

        // Assert
        assertEquals(3, parentElement.getChildren().size(), "Parent should have 3 children");
        assertEquals("element1", parentElement.getChildren().get(0).getName(), "First element should be 'element1'");
        assertEquals("element2", parentElement.getChildren().get(1).getName(), "Second element should be 'element2'");
        assertEquals("element3", parentElement.getChildren().get(2).getName(), "Third element should be 'element3'");
    }

    @Test
    @DisplayName("Should handle undoing multiple adds in reverse order")
    void testUndoMultipleAddsInReverseOrder() {
        // Arrange
        AddElementCommand command1 = new AddElementCommand(parentElement, "element1");
        AddElementCommand command2 = new AddElementCommand(parentElement, "element2");
        AddElementCommand command3 = new AddElementCommand(parentElement, "element3");

        command1.execute();
        command2.execute();
        command3.execute();

        // Act - undo in reverse order (LIFO)
        command3.undo();
        assertEquals(2, parentElement.getChildren().size(), "Parent should have 2 children after first undo");

        command2.undo();
        assertEquals(1, parentElement.getChildren().size(), "Parent should have 1 child after second undo");

        command1.undo();
        assertEquals(0, parentElement.getChildren().size(), "Parent should have no children after third undo");
    }
}
