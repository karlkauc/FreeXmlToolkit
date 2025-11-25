package org.fxt.freexmltoolkit.controls.v2.view;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.MoveNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for drag & drop functionality in the XSD Editor.
 * Tests the underlying logic for move operations triggered by drag & drop.
 *
 * @since 2.0
 */
class DragDropTest {

    private XsdSchema schema;
    private XsdEditorContext context;
    private XsdSequence sequence;
    private XsdElement element1;
    private XsdElement element2;
    private XsdElement element3;

    @BeforeEach
    void setUp() {
        // Create a simple schema structure
        schema = new XsdSchema();
        schema.setTargetNamespace("http://test.example.com");

        // Create a complexType with a sequence
        XsdComplexType complexType = new XsdComplexType("TestType");
        sequence = new XsdSequence();

        element1 = new XsdElement("FirstElement");
        element1.setType("xs:string");

        element2 = new XsdElement("SecondElement");
        element2.setType("xs:int");

        element3 = new XsdElement("ThirdElement");
        element3.setType("xs:boolean");

        sequence.addChild(element1);
        sequence.addChild(element2);
        sequence.addChild(element3);
        complexType.addChild(sequence);
        schema.addChild(complexType);

        context = new XsdEditorContext(schema);
    }

    @Test
    void testMoveNodeUp_shouldMoveElementToPreviousPosition() {
        // Initially: element1, element2, element3
        assertEquals(0, sequence.getChildren().indexOf(element1));
        assertEquals(1, sequence.getChildren().indexOf(element2));
        assertEquals(2, sequence.getChildren().indexOf(element3));

        // Move element2 up (to index 0)
        MoveNodeCommand command = new MoveNodeCommand(element2, sequence, 0);
        boolean result = command.execute();

        assertTrue(result);
        // After: element2, element1, element3
        assertEquals(0, sequence.getChildren().indexOf(element2));
        assertEquals(1, sequence.getChildren().indexOf(element1));
        assertEquals(2, sequence.getChildren().indexOf(element3));
    }

    @Test
    void testMoveNodeDown_shouldMoveElementToNextPosition() {
        // Move element1 down (to index 1)
        MoveNodeCommand command = new MoveNodeCommand(element1, sequence, 1);
        boolean result = command.execute();

        assertTrue(result);
        // After: element2, element1, element3
        assertEquals(0, sequence.getChildren().indexOf(element2));
        assertEquals(1, sequence.getChildren().indexOf(element1));
        assertEquals(2, sequence.getChildren().indexOf(element3));
    }

    @Test
    void testMoveNodeToEnd_shouldMoveElementToLastPosition() {
        // Move element1 to end (index 2)
        MoveNodeCommand command = new MoveNodeCommand(element1, sequence, 2);
        boolean result = command.execute();

        assertTrue(result);
        // After: element2, element3, element1
        assertEquals(0, sequence.getChildren().indexOf(element2));
        assertEquals(1, sequence.getChildren().indexOf(element3));
        assertEquals(2, sequence.getChildren().indexOf(element1));
    }

    @Test
    void testMoveNodeUndo_shouldRestoreOriginalOrder() {
        // Move element2 to index 2
        MoveNodeCommand command = new MoveNodeCommand(element2, sequence, 2);
        command.execute();

        // After move: element1, element3, element2
        assertEquals(0, sequence.getChildren().indexOf(element1));
        assertEquals(1, sequence.getChildren().indexOf(element3));
        assertEquals(2, sequence.getChildren().indexOf(element2));

        // Undo
        command.undo();

        // Should restore: element1, element2, element3
        assertEquals(0, sequence.getChildren().indexOf(element1));
        assertEquals(1, sequence.getChildren().indexOf(element2));
        assertEquals(2, sequence.getChildren().indexOf(element3));
    }

    @Test
    void testMoveNodeToDifferentParent_shouldMoveElementToNewSequence() {
        // Create another sequence
        XsdComplexType anotherType = new XsdComplexType("AnotherType");
        XsdSequence anotherSequence = new XsdSequence();
        anotherType.addChild(anotherSequence);
        schema.addChild(anotherType);

        // Move element1 to anotherSequence at index 0
        MoveNodeCommand command = new MoveNodeCommand(element1, anotherSequence, 0);
        boolean result = command.execute();

        assertTrue(result);

        // element1 should now be in anotherSequence
        assertFalse(sequence.getChildren().contains(element1));
        assertTrue(anotherSequence.getChildren().contains(element1));
        assertEquals(0, anotherSequence.getChildren().indexOf(element1));

        // Original sequence should have 2 elements
        assertEquals(2, sequence.getChildren().size());
    }

    @Test
    void testMoveNodeToDifferentParent_undoShouldRestoreOriginalParent() {
        // Create another sequence
        XsdComplexType anotherType = new XsdComplexType("AnotherType");
        XsdSequence anotherSequence = new XsdSequence();
        anotherType.addChild(anotherSequence);
        schema.addChild(anotherType);

        // Move element2 to anotherSequence
        MoveNodeCommand command = new MoveNodeCommand(element2, anotherSequence, 0);
        command.execute();

        // Verify it moved
        assertFalse(sequence.getChildren().contains(element2));
        assertTrue(anotherSequence.getChildren().contains(element2));

        // Undo
        command.undo();

        // Should be back in original sequence at original index
        assertTrue(sequence.getChildren().contains(element2));
        assertFalse(anotherSequence.getChildren().contains(element2));
        assertEquals(1, sequence.getChildren().indexOf(element2));
    }

    @Test
    void testDragDropViaCommandManager_shouldTrackInHistory() {
        // Use command manager to execute move
        MoveNodeCommand command = new MoveNodeCommand(element3, sequence, 0);
        context.getCommandManager().executeCommand(command);

        // Verify move happened
        assertEquals(0, sequence.getChildren().indexOf(element3));

        // Should be able to undo via command manager
        assertTrue(context.getCommandManager().canUndo());

        context.getCommandManager().undo();

        // Should restore original order
        assertEquals(2, sequence.getChildren().indexOf(element3));
    }

    @Test
    void testCannotMoveNodeOntoItself() {
        // Move element1 to its current position (should effectively do nothing)
        int originalIndex = sequence.getChildren().indexOf(element1);
        MoveNodeCommand command = new MoveNodeCommand(element1, sequence, originalIndex);
        boolean result = command.execute();

        // Command should succeed but element stays in same place
        assertTrue(result);
        assertEquals(originalIndex, sequence.getChildren().indexOf(element1));
    }

    @Test
    void testVisualNodeDragState() {
        // Create a VisualNode for testing
        XsdNodeRenderer.VisualNode visualNode = new XsdNodeRenderer.VisualNode(
                "TestElement",
                "xs:string",
                XsdNodeRenderer.NodeWrapperType.ELEMENT,
                element1,
                null,
                1, 1, null);

        // Test dragging state
        assertFalse(visualNode.isDragging());
        visualNode.setDragging(true);
        assertTrue(visualNode.isDragging());
        visualNode.setDragging(false);
        assertFalse(visualNode.isDragging());
    }

    @Test
    void testVisualNodeDropTargetState() {
        // Create a VisualNode for testing
        XsdNodeRenderer.VisualNode visualNode = new XsdNodeRenderer.VisualNode(
                "TestElement",
                "xs:string",
                XsdNodeRenderer.NodeWrapperType.ELEMENT,
                element1,
                null,
                1, 1, null);

        // Test drop target state
        assertFalse(visualNode.isDropTarget());
        visualNode.setDropTarget(true);
        assertTrue(visualNode.isDropTarget());
        visualNode.setDropTarget(false);
        assertFalse(visualNode.isDropTarget());
    }

    @Test
    void testMoveAttributeToDifferentElement() {
        // Create elements with attributes
        XsdElement parentElement = new XsdElement("Parent");
        XsdComplexType parentComplexType = new XsdComplexType("");
        parentElement.addChild(parentComplexType);

        XsdAttribute attr1 = new XsdAttribute("attribute1");
        attr1.setType("xs:string");
        parentComplexType.addChild(attr1);

        XsdElement targetElement = new XsdElement("Target");
        XsdComplexType targetComplexType = new XsdComplexType("");
        targetElement.addChild(targetComplexType);

        schema.addChild(parentElement);
        schema.addChild(targetElement);

        // Move attribute to target element's complexType
        MoveNodeCommand command = new MoveNodeCommand(attr1, targetComplexType, 0);
        boolean result = command.execute();

        assertTrue(result);
        assertFalse(parentComplexType.getChildren().contains(attr1));
        assertTrue(targetComplexType.getChildren().contains(attr1));
    }
}
