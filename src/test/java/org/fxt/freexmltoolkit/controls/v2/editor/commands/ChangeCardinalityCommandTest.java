package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeCardinalityCommand.UNBOUNDED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ChangeCardinalityCommand.
 * Tests basic functionality, undo/redo, property change events, and error handling
 * for various cardinality combinations.
 *
 * @since 2.0
 */
class ChangeCardinalityCommandTest {

    private XsdElement testElement;

    @BeforeEach
    void setUp() {
        testElement = new XsdElement("testElement");
        // Default cardinality is [1..1]
        testElement.setMinOccurs(1);
        testElement.setMaxOccurs(1);
    }

    // ========== Basic Functionality Tests ==========

    @Test
    @DisplayName("execute() should change cardinality and return true")
    void testExecuteChangesCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);

        // Act
        boolean result = command.execute();

        // Assert
        assertTrue(result, "execute() should return true");
        assertEquals(0, testElement.getMinOccurs(), "minOccurs should be 0");
        assertEquals(1, testElement.getMaxOccurs(), "maxOccurs should be 1");
    }

    @Test
    @DisplayName("undo() should restore old cardinality and return true")
    void testUndoRestoresOldCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);
        command.execute();

        // Act
        boolean result = command.undo();

        // Assert
        assertTrue(result, "undo() should return true");
        assertEquals(1, testElement.getMinOccurs(), "minOccurs should be restored to 1");
        assertEquals(1, testElement.getMaxOccurs(), "maxOccurs should be restored to 1");
    }

    @Test
    @DisplayName("execute() should handle UNBOUNDED maxOccurs")
    void testExecuteWithUnboundedMaxOccurs() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);

        // Act
        command.execute();

        // Assert
        assertEquals(0, testElement.getMinOccurs(), "minOccurs should be 0");
        assertEquals(UNBOUNDED, testElement.getMaxOccurs(), "maxOccurs should be UNBOUNDED (-1)");
    }

    @Test
    @DisplayName("undo() should restore UNBOUNDED maxOccurs")
    void testUndoRestoresUnboundedMaxOccurs() {
        // Arrange
        testElement.setMinOccurs(0);
        testElement.setMaxOccurs(UNBOUNDED);
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 1, 1);
        command.execute();

        // Act
        command.undo();

        // Assert
        assertEquals(0, testElement.getMinOccurs(), "minOccurs should be restored to 0");
        assertEquals(UNBOUNDED, testElement.getMaxOccurs(), "maxOccurs should be restored to UNBOUNDED");
    }

    // ========== Valid Cardinality Combinations Tests ==========

    @Test
    @DisplayName("Should handle [0..1] cardinality (optional)")
    void testOptionalCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);

        // Act
        command.execute();

        // Assert
        assertEquals(0, testElement.getMinOccurs());
        assertEquals(1, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle [1..1] cardinality (required)")
    void testRequiredCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 1, 1);

        // Act
        command.execute();

        // Assert
        assertEquals(1, testElement.getMinOccurs());
        assertEquals(1, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle [0..*] cardinality (optional, unbounded)")
    void testOptionalUnboundedCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);

        // Act
        command.execute();

        // Assert
        assertEquals(0, testElement.getMinOccurs());
        assertEquals(UNBOUNDED, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle [1..*] cardinality (required, unbounded)")
    void testRequiredUnboundedCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 1, UNBOUNDED);

        // Act
        command.execute();

        // Assert
        assertEquals(1, testElement.getMinOccurs());
        assertEquals(UNBOUNDED, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle [2..5] cardinality (specific range)")
    void testSpecificRangeCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 2, 5);

        // Act
        command.execute();

        // Assert
        assertEquals(2, testElement.getMinOccurs());
        assertEquals(5, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle [0..0] cardinality (prohibited)")
    void testProhibitedCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 0);

        // Act
        command.execute();

        // Assert
        assertEquals(0, testElement.getMinOccurs());
        assertEquals(0, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle [5..5] cardinality (exact count)")
    void testExactCountCardinality() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 5, 5);

        // Act
        command.execute();

        // Assert
        assertEquals(5, testElement.getMinOccurs());
        assertEquals(5, testElement.getMaxOccurs());
    }

    // ========== PropertyChangeEvent Tests ==========

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent for 'minOccurs' property")
    void testExecuteFiresMinOccursPropertyChangeEvent() {
        // Arrange
        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testElement.addPropertyChangeListener(evt -> {
            if ("minOccurs".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);

        // Act
        command.execute();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired for minOccurs");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("minOccurs", capturedEvent.get().getPropertyName());
        assertEquals(1, capturedEvent.get().getOldValue());
        assertEquals(0, capturedEvent.get().getNewValue());
    }

    @Test
    @DisplayName("execute() should fire PropertyChangeEvent for 'maxOccurs' property")
    void testExecuteFiresMaxOccursPropertyChangeEvent() {
        // Arrange
        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testElement.addPropertyChangeListener(evt -> {
            if ("maxOccurs".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 1, UNBOUNDED);

        // Act
        command.execute();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired for maxOccurs");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("maxOccurs", capturedEvent.get().getPropertyName());
        assertEquals(1, capturedEvent.get().getOldValue());
        assertEquals(UNBOUNDED, capturedEvent.get().getNewValue());
    }

    @Test
    @DisplayName("execute() should fire both minOccurs and maxOccurs events")
    void testExecuteFiresBothPropertyChangeEvents() {
        // Arrange
        AtomicInteger eventCount = new AtomicInteger(0);
        AtomicBoolean minOccursEventFired = new AtomicBoolean(false);
        AtomicBoolean maxOccursEventFired = new AtomicBoolean(false);

        testElement.addPropertyChangeListener(evt -> {
            if ("minOccurs".equals(evt.getPropertyName())) {
                minOccursEventFired.set(true);
                eventCount.incrementAndGet();
            } else if ("maxOccurs".equals(evt.getPropertyName())) {
                maxOccursEventFired.set(true);
                eventCount.incrementAndGet();
            }
        });

        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);

        // Act
        command.execute();

        // Assert
        assertEquals(2, eventCount.get(), "Should fire exactly 2 events");
        assertTrue(minOccursEventFired.get(), "minOccurs event should have been fired");
        assertTrue(maxOccursEventFired.get(), "maxOccurs event should have been fired");
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent for 'minOccurs' property")
    void testUndoFiresMinOccursPropertyChangeEvent() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);
        command.execute();

        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testElement.addPropertyChangeListener(evt -> {
            if ("minOccurs".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        // Act
        command.undo();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired for minOccurs");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("minOccurs", capturedEvent.get().getPropertyName());
        assertEquals(0, capturedEvent.get().getOldValue());
        assertEquals(1, capturedEvent.get().getNewValue());
    }

    @Test
    @DisplayName("undo() should fire PropertyChangeEvent for 'maxOccurs' property")
    void testUndoFiresMaxOccursPropertyChangeEvent() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);
        command.execute();

        AtomicBoolean eventFired = new AtomicBoolean(false);
        AtomicReference<PropertyChangeEvent> capturedEvent = new AtomicReference<>();

        testElement.addPropertyChangeListener(evt -> {
            if ("maxOccurs".equals(evt.getPropertyName())) {
                eventFired.set(true);
                capturedEvent.set(evt);
            }
        });

        // Act
        command.undo();

        // Assert
        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired for maxOccurs");
        assertNotNull(capturedEvent.get(), "Event should be captured");
        assertEquals("maxOccurs", capturedEvent.get().getPropertyName());
        assertEquals(UNBOUNDED, capturedEvent.get().getOldValue());
        assertEquals(1, capturedEvent.get().getNewValue());
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when node is null")
    void testConstructorThrowsExceptionForNullNode() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new ChangeCardinalityCommand(null, 0, 1);
        }, "Constructor should throw IllegalArgumentException for null node");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when minOccurs is negative")
    void testConstructorThrowsExceptionForNegativeMinOccurs() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new ChangeCardinalityCommand(testElement, -1, 1);
        }, "Constructor should throw IllegalArgumentException for negative minOccurs");
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when maxOccurs < minOccurs")
    void testConstructorThrowsExceptionWhenMaxLessThanMin() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            new ChangeCardinalityCommand(testElement, 5, 2);
        }, "Constructor should throw IllegalArgumentException when maxOccurs < minOccurs");
    }

    @Test
    @DisplayName("Constructor should accept UNBOUNDED regardless of minOccurs")
    void testConstructorAcceptsUnboundedWithAnyMinOccurs() {
        // Act & Assert - Should not throw
        assertDoesNotThrow(() -> {
            new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);
        }, "Constructor should accept UNBOUNDED with minOccurs=0");

        assertDoesNotThrow(() -> {
            new ChangeCardinalityCommand(testElement, 100, UNBOUNDED);
        }, "Constructor should accept UNBOUNDED with any valid minOccurs");
    }

    @Test
    @DisplayName("Constructor should accept equal minOccurs and maxOccurs")
    void testConstructorAcceptsEqualMinAndMaxOccurs() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            new ChangeCardinalityCommand(testElement, 5, 5);
        }, "Constructor should accept equal minOccurs and maxOccurs");
    }

    // ========== Command Methods Tests ==========

    @Test
    @DisplayName("canUndo() should always return true")
    void testCanUndo() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true before execute");

        // Act
        command.execute();

        // Assert
        assertTrue(command.canUndo(), "canUndo() should return true after execute");
    }

    @Test
    @DisplayName("getDescription() should return descriptive text with formatted cardinality")
    void testGetDescription() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);

        // Act
        String description = command.getDescription();

        // Assert
        assertNotNull(description, "Description should not be null");
        assertTrue(description.contains("testElement"), "Description should contain element name");
        assertTrue(description.contains("[1..1]"), "Description should contain old cardinality");
        assertTrue(description.contains("[0..1]"), "Description should contain new cardinality");
    }

    @Test
    @DisplayName("getDescription() should format UNBOUNDED as asterisk")
    void testGetDescriptionWithUnbounded() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);

        // Act
        String description = command.getDescription();

        // Assert
        assertTrue(description.contains("[0..*]"), "Description should format UNBOUNDED as *");
    }

    @Test
    @DisplayName("getDescription() should handle various cardinality formats")
    void testGetDescriptionWithVariousCardinalities() {
        // Test [2..5]
        ChangeCardinalityCommand cmd1 = new ChangeCardinalityCommand(testElement, 2, 5);
        assertTrue(cmd1.getDescription().contains("[2..5]"),
                "Should format [2..5] correctly");

        // Test [1..*]
        ChangeCardinalityCommand cmd2 = new ChangeCardinalityCommand(testElement, 1, UNBOUNDED);
        assertTrue(cmd2.getDescription().contains("[1..*]"),
                "Should format [1..*] correctly");

        // Test [0..0]
        ChangeCardinalityCommand cmd3 = new ChangeCardinalityCommand(testElement, 0, 0);
        assertTrue(cmd3.getDescription().contains("[0..0]"),
                "Should format [0..0] correctly");
    }

    @Test
    @DisplayName("canMergeWith() should return true for consecutive cardinality changes on same node")
    void testCanMergeWithConsecutiveCardinalityChanges() {
        // Arrange
        ChangeCardinalityCommand command1 = new ChangeCardinalityCommand(testElement, 0, 1);
        command1.execute();
        ChangeCardinalityCommand command2 = new ChangeCardinalityCommand(testElement, 1, UNBOUNDED);

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertTrue(canMerge, "Consecutive cardinality changes on same node should be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for different command types")
    void testCanMergeWithDifferentCommandType() {
        // Arrange
        ChangeCardinalityCommand cardinalityCommand = new ChangeCardinalityCommand(testElement, 0, 1);
        RenameNodeCommand renameCommand = new RenameNodeCommand(testElement, "newName");

        // Act
        boolean canMerge = cardinalityCommand.canMergeWith(renameCommand);

        // Assert
        assertFalse(canMerge, "Different command types should not be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false for cardinality changes on different nodes")
    void testCanMergeWithDifferentNodes() {
        // Arrange
        XsdElement otherElement = new XsdElement("otherElement");
        ChangeCardinalityCommand command1 = new ChangeCardinalityCommand(testElement, 0, 1);
        ChangeCardinalityCommand command2 = new ChangeCardinalityCommand(otherElement, 1, UNBOUNDED);

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Cardinality changes on different nodes should not be mergeable");
    }

    @Test
    @DisplayName("canMergeWith() should return false when cardinalities don't chain correctly")
    void testCanMergeWithNonChainedCardinalityChanges() {
        // Arrange
        ChangeCardinalityCommand command1 = new ChangeCardinalityCommand(testElement, 0, 1);
        command1.execute();

        // Change cardinality directly on element (simulating another operation)
        testElement.setMinOccurs(2);
        testElement.setMaxOccurs(5);

        // Now create another command - this won't chain with command1
        ChangeCardinalityCommand command2 = new ChangeCardinalityCommand(testElement, 1, UNBOUNDED);

        // Act
        boolean canMerge = command1.canMergeWith(command2);

        // Assert
        assertFalse(canMerge, "Non-consecutive cardinality changes should not be mergeable");
    }

    // ========== Getter Tests ==========

    @Test
    @DisplayName("getNode() should return the correct node")
    void testGetNode() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);

        // Act
        XsdNode node = command.getNode();

        // Assert
        assertSame(testElement, node, "getNode() should return the same instance");
    }

    @Test
    @DisplayName("getNewMinOccurs() should return the correct new minOccurs")
    void testGetNewMinOccurs() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, 1);

        // Act
        int newMinOccurs = command.getNewMinOccurs();

        // Assert
        assertEquals(0, newMinOccurs, "getNewMinOccurs() should return 0");
    }

    @Test
    @DisplayName("getNewMaxOccurs() should return the correct new maxOccurs")
    void testGetNewMaxOccurs() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);

        // Act
        int newMaxOccurs = command.getNewMaxOccurs();

        // Assert
        assertEquals(UNBOUNDED, newMaxOccurs, "getNewMaxOccurs() should return UNBOUNDED");
    }

    // ========== Multiple Execute/Undo Tests ==========

    @Test
    @DisplayName("Multiple execute() calls should be idempotent")
    void testMultipleExecuteCalls() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);

        // Act
        command.execute();
        command.execute();

        // Assert
        assertEquals(0, testElement.getMinOccurs(),
                "minOccurs should still be 0 after multiple executes");
        assertEquals(UNBOUNDED, testElement.getMaxOccurs(),
                "maxOccurs should still be UNBOUNDED after multiple executes");
    }

    @Test
    @DisplayName("Multiple undo() calls should be idempotent")
    void testMultipleUndoCalls() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);
        command.execute();

        // Act
        command.undo();
        command.undo();

        // Assert
        assertEquals(1, testElement.getMinOccurs(),
                "minOccurs should still be 1 after multiple undos");
        assertEquals(1, testElement.getMaxOccurs(),
                "maxOccurs should still be 1 after multiple undos");
    }

    @Test
    @DisplayName("Execute-undo-execute sequence should work correctly")
    void testExecuteUndoExecuteSequence() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 0, UNBOUNDED);

        // Act & Assert
        command.execute();
        assertEquals(0, testElement.getMinOccurs());
        assertEquals(UNBOUNDED, testElement.getMaxOccurs());

        command.undo();
        assertEquals(1, testElement.getMinOccurs());
        assertEquals(1, testElement.getMaxOccurs());

        command.execute();
        assertEquals(0, testElement.getMinOccurs());
        assertEquals(UNBOUNDED, testElement.getMaxOccurs());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("Should handle transition from bounded to unbounded")
    void testBoundedToUnboundedTransition() {
        // Arrange
        testElement.setMinOccurs(2);
        testElement.setMaxOccurs(10);
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 1, UNBOUNDED);

        // Act
        command.execute();

        // Assert
        assertEquals(1, testElement.getMinOccurs());
        assertEquals(UNBOUNDED, testElement.getMaxOccurs());

        // Undo
        command.undo();
        assertEquals(2, testElement.getMinOccurs());
        assertEquals(10, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle transition from unbounded to bounded")
    void testUnboundedToBoundedTransition() {
        // Arrange
        testElement.setMinOccurs(0);
        testElement.setMaxOccurs(UNBOUNDED);
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 1, 5);

        // Act
        command.execute();

        // Assert
        assertEquals(1, testElement.getMinOccurs());
        assertEquals(5, testElement.getMaxOccurs());

        // Undo
        command.undo();
        assertEquals(0, testElement.getMinOccurs());
        assertEquals(UNBOUNDED, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle large minOccurs values")
    void testLargeMinOccursValue() {
        // Arrange
        ChangeCardinalityCommand command = new ChangeCardinalityCommand(testElement, 1000, 2000);

        // Act
        command.execute();

        // Assert
        assertEquals(1000, testElement.getMinOccurs());
        assertEquals(2000, testElement.getMaxOccurs());
    }

    @Test
    @DisplayName("UNBOUNDED constant should be -1")
    void testUnboundedConstantValue() {
        // Assert
        assertEquals(-1, UNBOUNDED, "UNBOUNDED constant should be -1");
    }
}
