package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdSequence - represents XSD sequence compositor (xs:sequence).
 * Tests sequence-specific behavior and inherited XsdNode properties.
 */
class XsdSequenceTest {

    private XsdSequence sequence;

    @BeforeEach
    void setUp() {
        sequence = new XsdSequence();
    }

    @Test
    @DisplayName("Should create sequence with fixed name 'sequence'")
    void testConstructor() {
        assertEquals("sequence", sequence.getName());
        assertEquals(XsdNodeType.SEQUENCE, sequence.getNodeType());
        assertNotNull(sequence.getId());
    }

    @Test
    @DisplayName("Should verify node type is SEQUENCE")
    void testNodeType() {
        assertEquals(XsdNodeType.SEQUENCE, sequence.getNodeType());
    }

    @Test
    @DisplayName("Should deep copy sequence without applying suffix to name")
    void testDeepCopyNoSuffixOnName() {
        sequence.setMinOccurs(1);
        sequence.setMaxOccurs(10);
        sequence.setDocumentation("Test documentation");

        XsdSequence copy = (XsdSequence) sequence.deepCopy("_copy");

        // Name should remain "sequence" (suffix not applied)
        assertEquals("sequence", copy.getName());

        // Properties should be copied
        assertEquals(1, copy.getMinOccurs());
        assertEquals(10, copy.getMaxOccurs());
        assertEquals("Test documentation", copy.getDocumentation());

        // Should have different ID
        assertNotEquals(sequence.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy without suffix parameter")
    void testDeepCopyWithNullSuffix() {
        XsdSequence copy = (XsdSequence) sequence.deepCopy(null);

        assertEquals("sequence", copy.getName());
        assertNotEquals(sequence.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy children recursively")
    void testDeepCopyWithChildren() {
        XsdElement element1 = new XsdElement("element1");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("element2");
        element2.setType("xs:int");

        sequence.addChild(element1);
        sequence.addChild(element2);

        XsdSequence copy = (XsdSequence) sequence.deepCopy("_copy");

        assertEquals(2, copy.getChildren().size());

        XsdElement copiedElement1 = (XsdElement) copy.getChildren().get(0);
        assertEquals("element1_copy", copiedElement1.getName());
        assertEquals("xs:string", copiedElement1.getType());
        assertNotEquals(element1.getId(), copiedElement1.getId());
        assertSame(copy, copiedElement1.getParent());

        XsdElement copiedElement2 = (XsdElement) copy.getChildren().get(1);
        assertEquals("element2_copy", copiedElement2.getName());
        assertEquals("xs:int", copiedElement2.getType());
        assertNotEquals(element2.getId(), copiedElement2.getId());
        assertSame(copy, copiedElement2.getParent());
    }

    @Test
    @DisplayName("Should inherit properties from XsdNode")
    void testInheritedProperties() {
        sequence.setMinOccurs(2);
        sequence.setMaxOccurs(XsdNode.UNBOUNDED);
        sequence.setDocumentation("Documentation text");
        sequence.setAppinfo("Appinfo text");

        assertEquals(2, sequence.getMinOccurs());
        assertEquals(XsdNode.UNBOUNDED, sequence.getMaxOccurs());
        assertEquals("Documentation text", sequence.getDocumentation());
        assertEquals("Appinfo text", sequence.getAppinfo());
    }

    @Test
    @DisplayName("Should handle parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexType parent = new XsdComplexType("parentType");
        XsdSequence child = new XsdSequence();

        parent.addChild(child);

        assertSame(parent, child.getParent());
        assertEquals(1, parent.getChildren().size());
        assertTrue(parent.getChildren().contains(child));
    }

    @Test
    @DisplayName("Should add multiple element children")
    void testAddMultipleChildren() {
        XsdElement element1 = new XsdElement("element1");
        XsdElement element2 = new XsdElement("element2");
        XsdElement element3 = new XsdElement("element3");

        sequence.addChild(element1);
        sequence.addChild(element2);
        sequence.addChild(element3);

        assertEquals(3, sequence.getChildren().size());
        assertTrue(sequence.getChildren().contains(element1));
        assertTrue(sequence.getChildren().contains(element2));
        assertTrue(sequence.getChildren().contains(element3));
    }

    @Test
    @DisplayName("Should remove child from sequence")
    void testRemoveChild() {
        XsdElement element1 = new XsdElement("element1");
        XsdElement element2 = new XsdElement("element2");

        sequence.addChild(element1);
        sequence.addChild(element2);

        sequence.removeChild(element1);

        assertEquals(1, sequence.getChildren().size());
        assertFalse(sequence.getChildren().contains(element1));
        assertTrue(sequence.getChildren().contains(element2));
        assertNull(element1.getParent());
    }

    @Test
    @DisplayName("Should fire property change events for inherited properties")
    void testInheritedPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        sequence.addPropertyChangeListener(events::add);

        sequence.setMinOccurs(5);

        assertEquals(1, events.size());
        assertEquals("minOccurs", events.get(0).getPropertyName());
        assertEquals(1, events.get(0).getOldValue());
        assertEquals(5, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should fire property change events when adding children")
    void testChildrenPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        sequence.addPropertyChangeListener(events::add);

        XsdElement element = new XsdElement("element1");
        sequence.addChild(element);

        assertEquals(1, events.size());
        assertEquals("children", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should handle property change listener removal")
    void testPropertyChangeListenerRemoval() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        sequence.addPropertyChangeListener(listener);
        sequence.setMinOccurs(5);

        assertEquals(1, events.size());

        events.clear();
        sequence.removePropertyChangeListener(listener);
        sequence.setMinOccurs(10);

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("Should handle nested sequences")
    void testNestedSequences() {
        XsdSequence nestedSequence = new XsdSequence();
        XsdElement element = new XsdElement("nestedElement");

        nestedSequence.addChild(element);
        sequence.addChild(nestedSequence);

        assertEquals(1, sequence.getChildren().size());
        assertSame(sequence, nestedSequence.getParent());
        assertEquals(1, nestedSequence.getChildren().size());
        assertSame(nestedSequence, element.getParent());
    }

    @Test
    @DisplayName("Should deep copy nested sequences")
    void testDeepCopyNestedSequences() {
        XsdSequence nestedSequence = new XsdSequence();
        XsdElement element = new XsdElement("nestedElement");
        element.setType("xs:string");

        nestedSequence.addChild(element);
        sequence.addChild(nestedSequence);

        XsdSequence copy = (XsdSequence) sequence.deepCopy("_copy");

        assertEquals(1, copy.getChildren().size());
        XsdSequence copiedNested = (XsdSequence) copy.getChildren().get(0);
        assertEquals("sequence", copiedNested.getName());
        assertNotEquals(nestedSequence.getId(), copiedNested.getId());

        assertEquals(1, copiedNested.getChildren().size());
        XsdElement copiedElement = (XsdElement) copiedNested.getChildren().get(0);
        assertEquals("nestedElement_copy", copiedElement.getName());
        assertEquals("xs:string", copiedElement.getType());
    }

    @Test
    @DisplayName("Should handle unbounded maxOccurs")
    void testUnboundedMaxOccurs() {
        sequence.setMaxOccurs(XsdNode.UNBOUNDED);

        assertEquals(XsdNode.UNBOUNDED, sequence.getMaxOccurs());
        assertEquals(-1, sequence.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle minOccurs=0 for optional sequence")
    void testOptionalSequence() {
        sequence.setMinOccurs(0);
        sequence.setMaxOccurs(1);

        assertEquals(0, sequence.getMinOccurs());
        assertEquals(1, sequence.getMaxOccurs());
    }

    @Test
    @DisplayName("Should clear all children")
    void testClearChildren() {
        sequence.addChild(new XsdElement("element1"));
        sequence.addChild(new XsdElement("element2"));
        sequence.addChild(new XsdElement("element3"));

        assertEquals(3, sequence.getChildren().size());

        for (XsdNode child : new ArrayList<>(sequence.getChildren())) {
            sequence.removeChild(child);
        }

        assertEquals(0, sequence.getChildren().size());
    }

    @Test
    @DisplayName("Should maintain children order")
    void testChildrenOrder() {
        XsdElement element1 = new XsdElement("element1");
        XsdElement element2 = new XsdElement("element2");
        XsdElement element3 = new XsdElement("element3");

        sequence.addChild(element1);
        sequence.addChild(element2);
        sequence.addChild(element3);

        List<XsdNode> children = sequence.getChildren();
        assertSame(element1, children.get(0));
        assertSame(element2, children.get(1));
        assertSame(element3, children.get(2));
    }

    @Test
    @DisplayName("Should handle documentation and appinfo")
    void testDocumentationAndAppinfo() {
        sequence.setDocumentation("This is a sequence documentation");
        sequence.setAppinfo("This is appinfo");

        assertEquals("This is a sequence documentation", sequence.getDocumentation());
        assertEquals("This is appinfo", sequence.getAppinfo());

        XsdSequence copy = (XsdSequence) sequence.deepCopy(null);
        assertEquals("This is a sequence documentation", copy.getDocumentation());
        assertEquals("This is appinfo", copy.getAppinfo());
    }

    @Test
    @DisplayName("Should have no sequence-specific properties")
    void testNoSequenceSpecificProperties() {
        // XsdSequence has no specific properties beyond those inherited from XsdNode
        // This test verifies that the class is simple and only uses base functionality

        XsdSequence seq = new XsdSequence();
        assertEquals("sequence", seq.getName());
        assertEquals(XsdNodeType.SEQUENCE, seq.getNodeType());
        assertNotNull(seq.getId());
        assertEquals(1, seq.getMinOccurs());
        assertEquals(1, seq.getMaxOccurs());
        assertNull(seq.getDocumentation());
        assertNull(seq.getAppinfo());
        assertEquals(0, seq.getChildren().size());
    }
}
