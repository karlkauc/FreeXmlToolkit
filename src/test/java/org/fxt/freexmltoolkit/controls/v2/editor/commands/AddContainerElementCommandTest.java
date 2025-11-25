package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for AddContainerElementCommand.
 * Tests the creation of container elements (elements with inline complexType
 * and sequence, but no type attribute).
 *
 * @since 2.0
 */
class AddContainerElementCommandTest {

    private XsdElement parentElement;
    private XsdSequence parentSequence;

    @BeforeEach
    void setUp() {
        // Create parent element with inline complexType and sequence
        parentElement = new XsdElement("parent");
        XsdComplexType parentComplexType = new XsdComplexType("");
        parentSequence = new XsdSequence();
        parentComplexType.addChild(parentSequence);
        parentElement.addChild(parentComplexType);
    }

    // ========== Basic Functionality Tests ==========

    @Test
    @DisplayName("execute() should create container element with null type")
    void testExecuteCreatesContainerElementWithNullType() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        XsdElement addedElement = command.getAddedElement();
        assertNotNull(addedElement, "Added element should not be null");
        assertEquals("container", addedElement.getName(), "Element name should be 'container'");
        assertNull(addedElement.getType(), "Container element should have null type");
    }

    @Test
    @DisplayName("execute() should create inline complexType with sequence")
    void testExecuteCreatesInlineComplexTypeWithSequence() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertEquals(1, addedElement.getChildren().size(), "Container element should have 1 child (complexType)");

        XsdNode complexTypeChild = addedElement.getChildren().get(0);
        assertInstanceOf(XsdComplexType.class, complexTypeChild, "First child should be XsdComplexType");

        XsdComplexType inlineComplexType = (XsdComplexType) complexTypeChild;
        assertEquals("", inlineComplexType.getName(), "Inline complexType should have empty name (anonymous)");
        assertEquals(1, inlineComplexType.getChildren().size(), "ComplexType should have 1 child (sequence)");

        XsdNode sequenceChild = inlineComplexType.getChildren().get(0);
        assertInstanceOf(XsdSequence.class, sequenceChild, "ComplexType child should be XsdSequence");
    }

    @Test
    @DisplayName("execute() should add element to compositor when parent has one")
    void testExecuteAddsToCompositorWhenParentHasOne() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        command.execute();

        // Assert
        // Element should be added to the sequence (compositor) inside parent's complexType
        assertEquals(1, parentSequence.getChildren().size(), "Sequence should have 1 child");
        assertInstanceOf(XsdElement.class, parentSequence.getChildren().get(0), "Child should be XsdElement");
    }

    @Test
    @DisplayName("execute() should add element directly to parent when no compositor exists")
    void testExecuteAddsDirectlyToParentWhenNoCompositor() {
        // Arrange
        XsdElement simpleParent = new XsdElement("simpleParent");
        AddContainerElementCommand command = new AddContainerElementCommand(simpleParent, "container");

        // Act
        command.execute();

        // Assert
        assertEquals(1, simpleParent.getChildren().size(), "Parent should have 1 child");
        assertInstanceOf(XsdElement.class, simpleParent.getChildren().get(0), "Child should be XsdElement");
    }

    @Test
    @DisplayName("undo() should remove added element")
    void testUndoRemovesElement() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals(0, parentSequence.getChildren().size(), "Sequence should have no children after undo");
    }

    @Test
    @DisplayName("undo() should return false when element was not added")
    void testUndoReturnsFalseWhenNotExecuted() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act (no execute call)
        boolean result = command.undo();

        // Assert
        assertFalse(result, "undo() should return false when element was not added");
    }

    @Test
    @DisplayName("canUndo() should return false before execute")
    void testCanUndoBeforeExecute() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Assert
        assertFalse(command.canUndo(), "canUndo() should return false before execute");
    }

    @Test
    @DisplayName("canUndo() should return true after execute")
    void testCanUndoAfterExecute() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        command.execute();

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true after execute");
    }

    @Test
    @DisplayName("getDescription() should return descriptive text")
    void testGetDescription() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "myContainer");

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.toLowerCase().contains("container"), "Description should contain 'container'");
        assertTrue(description.contains("myContainer"), "Description should contain element name");
        assertTrue(description.contains("parent"), "Description should contain parent name");
    }

    @Test
    @DisplayName("canMergeWith() should always return false")
    void testCanMergeWith() {
        // Arrange
        AddContainerElementCommand command1 = new AddContainerElementCommand(parentElement, "container1");
        AddContainerElementCommand command2 = new AddContainerElementCommand(parentElement, "container2");

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Add container commands should not be mergeable");
    }

    @Test
    @DisplayName("getAddedElement() should return null before execute")
    void testGetAddedElementBeforeExecute() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        XsdElement element = command.getAddedElement();

        // Assert
        assertNull(element, "getAddedElement() should return null before execute");
    }

    @Test
    @DisplayName("getAddedElement() should return added element after execute")
    void testGetAddedElementAfterExecute() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        command.execute();
        XsdElement element = command.getAddedElement();

        // Assert
        assertNotNull(element, "getAddedElement() should return element after execute");
        assertEquals("container", element.getName(), "Element name should be 'container'");
    }

    // ========== PropertyChangeEvent Tests ==========

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent on parent")
    void testExecuteFiresPropertyChangeEvent() {
        // Arrange
        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        parentSequence.addPropertyChangeListener(evt -> {
            if ("children".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        command.execute();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("children", capturedEvent.get().getPropertyName(), "Property name should be 'children'");
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent on parent")
    void testUndoFiresPropertyChangeEvent() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");
        command.execute();

        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        parentSequence.addPropertyChangeListener(evt -> {
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
            new AddContainerElementCommand(null, "container");
        }, "Constructor should throw IllegalArgumentException for null parent");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when element name is null")
    void testConstructorThrowsExceptionForNullElementName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new AddContainerElementCommand(parentElement, null);
        }, "Constructor should throw IllegalArgumentException for null element name");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when element name is empty")
    void testConstructorThrowsExceptionForEmptyElementName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new AddContainerElementCommand(parentElement, "");
        }, "Constructor should throw IllegalArgumentException for empty element name");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when element name is whitespace")
    void testConstructorThrowsExceptionForWhitespaceElementName() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new AddContainerElementCommand(parentElement, "   ");
        }, "Constructor should throw IllegalArgumentException for whitespace element name");
    }

    @Test
    @DisplayName("Constructor should trim element name whitespace")
    void testConstructorTrimsElementName() {
        // Arrange & Act
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "  container  ");
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertEquals("container", addedElement.getName(), "Whitespace should be trimmed from element name");
    }

    // ========== Multiple Execute/Undo Tests ==========

    @Test
    @DisplayName("Execute-undo-execute sequence should work correctly")
    void testExecuteUndoExecuteSequence() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act & Assert
        command.execute();
        assertEquals(1, parentSequence.getChildren().size(), "Sequence should have 1 child after execute");

        command.undo();
        assertEquals(0, parentSequence.getChildren().size(), "Sequence should have no children after undo");

        command.execute();
        assertEquals(1, parentSequence.getChildren().size(), "Sequence should have 1 child after re-execute");
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should add container element to parent with existing children")
    void testAddContainerToParentWithExistingChildren() {
        // Arrange
        XsdElement existingChild = new XsdElement("existingChild");
        parentSequence.addChild(existingChild);

        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        command.execute();

        // Assert
        assertEquals(2, parentSequence.getChildren().size(), "Sequence should have 2 children");
        assertSame(existingChild, parentSequence.getChildren().get(0), "Existing child should be at index 0");
        assertEquals("container", parentSequence.getChildren().get(1).getName(), "New container should be at index 1");
    }

    @Test
    @DisplayName("Should set parent reference correctly on added container")
    void testParentReferenceOnAddedContainer() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");

        // Act
        command.execute();

        // Assert
        XsdElement addedElement = command.getAddedElement();
        assertSame(parentSequence, addedElement.getParent(), "Added element should have correct parent reference");
    }

    @Test
    @DisplayName("Container element structure should allow adding child elements")
    void testContainerStructureAllowsChildElements() {
        // Arrange
        AddContainerElementCommand command = new AddContainerElementCommand(parentElement, "container");
        command.execute();

        // Act - Get the sequence inside the container and add a child
        XsdElement container = command.getAddedElement();
        XsdComplexType inlineType = (XsdComplexType) container.getChildren().get(0);
        XsdSequence innerSequence = (XsdSequence) inlineType.getChildren().get(0);

        XsdElement childElement = new XsdElement("childElement");
        childElement.setType("xs:string");
        innerSequence.addChild(childElement);

        // Assert
        assertEquals(1, innerSequence.getChildren().size(), "Inner sequence should have 1 child");
        assertEquals("childElement", innerSequence.getChildren().get(0).getName(), "Child element should be 'childElement'");
    }

    @Test
    @DisplayName("Should handle adding multiple containers sequentially")
    void testAddMultipleContainers() {
        // Arrange
        AddContainerElementCommand command1 = new AddContainerElementCommand(parentElement, "container1");
        AddContainerElementCommand command2 = new AddContainerElementCommand(parentElement, "container2");
        AddContainerElementCommand command3 = new AddContainerElementCommand(parentElement, "container3");

        // Act
        command1.execute();
        command2.execute();
        command3.execute();

        // Assert
        assertEquals(3, parentSequence.getChildren().size(), "Sequence should have 3 children");
        assertEquals("container1", parentSequence.getChildren().get(0).getName());
        assertEquals("container2", parentSequence.getChildren().get(1).getName());
        assertEquals("container3", parentSequence.getChildren().get(2).getName());
    }

    @Test
    @DisplayName("Should handle undoing multiple adds in reverse order")
    void testUndoMultipleAddsInReverseOrder() {
        // Arrange
        AddContainerElementCommand command1 = new AddContainerElementCommand(parentElement, "container1");
        AddContainerElementCommand command2 = new AddContainerElementCommand(parentElement, "container2");
        AddContainerElementCommand command3 = new AddContainerElementCommand(parentElement, "container3");

        command1.execute();
        command2.execute();
        command3.execute();

        // Act - undo in reverse order (LIFO)
        command3.undo();
        assertEquals(2, parentSequence.getChildren().size(), "Sequence should have 2 children after first undo");

        command2.undo();
        assertEquals(1, parentSequence.getChildren().size(), "Sequence should have 1 child after second undo");

        command1.undo();
        assertEquals(0, parentSequence.getChildren().size(), "Sequence should have no children after third undo");
    }

    // ========== Special Parent Type Tests ==========

    @Test
    @DisplayName("Should add to XsdChoice compositor")
    void testAddToChoiceCompositor() {
        // Arrange
        XsdElement choiceParent = new XsdElement("choiceParent");
        XsdComplexType complexType = new XsdComplexType("");
        XsdChoice choice = new XsdChoice();
        complexType.addChild(choice);
        choiceParent.addChild(complexType);

        AddContainerElementCommand command = new AddContainerElementCommand(choiceParent, "container");

        // Act
        command.execute();

        // Assert
        assertEquals(1, choice.getChildren().size(), "Choice should have 1 child");
        assertEquals("container", choice.getChildren().get(0).getName());
    }

    @Test
    @DisplayName("Should add to XsdAll compositor")
    void testAddToAllCompositor() {
        // Arrange
        XsdElement allParent = new XsdElement("allParent");
        XsdComplexType complexType = new XsdComplexType("");
        XsdAll all = new XsdAll();
        complexType.addChild(all);
        allParent.addChild(complexType);

        AddContainerElementCommand command = new AddContainerElementCommand(allParent, "container");

        // Act
        command.execute();

        // Assert
        assertEquals(1, all.getChildren().size(), "All should have 1 child");
        assertEquals("container", all.getChildren().get(0).getName());
    }

    @Test
    @DisplayName("Should add to referenced ComplexType's compositor")
    void testAddToReferencedComplexType() {
        // Arrange - Create a schema with element referencing a named ComplexType
        XsdSchema schema = new XsdSchema();

        // Create named ComplexType with sequence
        XsdComplexType namedType = new XsdComplexType("MyType");
        XsdSequence typeSequence = new XsdSequence();
        namedType.addChild(typeSequence);
        schema.addChild(namedType);

        // Create element referencing the type
        XsdElement referencingElement = new XsdElement("myElement");
        referencingElement.setType("MyType");
        schema.addChild(referencingElement);

        AddContainerElementCommand command = new AddContainerElementCommand(referencingElement, "container");

        // Act
        command.execute();

        // Assert - Container should be added to the sequence in the referenced ComplexType
        assertEquals(1, typeSequence.getChildren().size(), "Type sequence should have 1 child");
        assertEquals("container", typeSequence.getChildren().get(0).getName());
    }
}
