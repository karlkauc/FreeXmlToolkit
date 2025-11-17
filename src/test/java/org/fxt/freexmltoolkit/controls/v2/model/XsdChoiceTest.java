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
 * Unit tests for XsdChoice - represents XSD choice compositor (xs:choice).
 * Tests choice-specific behavior and inherited XsdNode properties.
 */
class XsdChoiceTest {

    private XsdChoice choice;

    @BeforeEach
    void setUp() {
        choice = new XsdChoice();
    }

    @Test
    @DisplayName("Should create choice with fixed name 'choice'")
    void testConstructor() {
        assertEquals("choice", choice.getName());
        assertEquals(XsdNodeType.CHOICE, choice.getNodeType());
        assertNotNull(choice.getId());
    }

    @Test
    @DisplayName("Should verify node type is CHOICE")
    void testNodeType() {
        assertEquals(XsdNodeType.CHOICE, choice.getNodeType());
    }

    @Test
    @DisplayName("Should deep copy choice without applying suffix to name")
    void testDeepCopyNoSuffixOnName() {
        choice.setMinOccurs(1);
        choice.setMaxOccurs(10);
        choice.setDocumentation("Test documentation");

        XsdChoice copy = (XsdChoice) choice.deepCopy("_copy");

        // Name should remain "choice" (suffix not applied)
        assertEquals("choice", copy.getName());

        // Properties should be copied
        assertEquals(1, copy.getMinOccurs());
        assertEquals(10, copy.getMaxOccurs());
        assertEquals("Test documentation", copy.getDocumentation());

        // Should have different ID
        assertNotEquals(choice.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy without suffix parameter")
    void testDeepCopyWithNullSuffix() {
        XsdChoice copy = (XsdChoice) choice.deepCopy(null);

        assertEquals("choice", copy.getName());
        assertNotEquals(choice.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy children recursively")
    void testDeepCopyWithChildren() {
        XsdElement element1 = new XsdElement("element1");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("element2");
        element2.setType("xs:int");

        choice.addChild(element1);
        choice.addChild(element2);

        XsdChoice copy = (XsdChoice) choice.deepCopy("_copy");

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
        choice.setMinOccurs(2);
        choice.setMaxOccurs(XsdNode.UNBOUNDED);
        choice.setDocumentation("Documentation text");
        choice.setAppinfo("Appinfo text");

        assertEquals(2, choice.getMinOccurs());
        assertEquals(XsdNode.UNBOUNDED, choice.getMaxOccurs());
        assertEquals("Documentation text", choice.getDocumentation());
        assertEquals("Appinfo text", choice.getAppinfo());
    }

    @Test
    @DisplayName("Should handle parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexType parent = new XsdComplexType("parentType");
        XsdChoice child = new XsdChoice();

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

        choice.addChild(element1);
        choice.addChild(element2);
        choice.addChild(element3);

        assertEquals(3, choice.getChildren().size());
        assertTrue(choice.getChildren().contains(element1));
        assertTrue(choice.getChildren().contains(element2));
        assertTrue(choice.getChildren().contains(element3));
    }

    @Test
    @DisplayName("Should remove child from choice")
    void testRemoveChild() {
        XsdElement element1 = new XsdElement("element1");
        XsdElement element2 = new XsdElement("element2");

        choice.addChild(element1);
        choice.addChild(element2);

        choice.removeChild(element1);

        assertEquals(1, choice.getChildren().size());
        assertFalse(choice.getChildren().contains(element1));
        assertTrue(choice.getChildren().contains(element2));
        assertNull(element1.getParent());
    }

    @Test
    @DisplayName("Should fire property change events for inherited properties")
    void testInheritedPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        choice.addPropertyChangeListener(events::add);

        choice.setMinOccurs(5);

        assertEquals(1, events.size());
        assertEquals("minOccurs", events.get(0).getPropertyName());
        assertEquals(1, events.get(0).getOldValue());
        assertEquals(5, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should fire property change events when adding children")
    void testChildrenPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        choice.addPropertyChangeListener(events::add);

        XsdElement element = new XsdElement("element1");
        choice.addChild(element);

        assertEquals(1, events.size());
        assertEquals("children", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should handle property change listener removal")
    void testPropertyChangeListenerRemoval() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        choice.addPropertyChangeListener(listener);
        choice.setMinOccurs(5);

        assertEquals(1, events.size());

        events.clear();
        choice.removePropertyChangeListener(listener);
        choice.setMinOccurs(10);

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("Should handle nested choices")
    void testNestedChoices() {
        XsdChoice nestedChoice = new XsdChoice();
        XsdElement element = new XsdElement("nestedElement");

        nestedChoice.addChild(element);
        choice.addChild(nestedChoice);

        assertEquals(1, choice.getChildren().size());
        assertSame(choice, nestedChoice.getParent());
        assertEquals(1, nestedChoice.getChildren().size());
        assertSame(nestedChoice, element.getParent());
    }

    @Test
    @DisplayName("Should deep copy nested choices")
    void testDeepCopyNestedChoices() {
        XsdChoice nestedChoice = new XsdChoice();
        XsdElement element = new XsdElement("nestedElement");
        element.setType("xs:string");

        nestedChoice.addChild(element);
        choice.addChild(nestedChoice);

        XsdChoice copy = (XsdChoice) choice.deepCopy("_copy");

        assertEquals(1, copy.getChildren().size());
        XsdChoice copiedNested = (XsdChoice) copy.getChildren().get(0);
        assertEquals("choice", copiedNested.getName());
        assertNotEquals(nestedChoice.getId(), copiedNested.getId());

        assertEquals(1, copiedNested.getChildren().size());
        XsdElement copiedElement = (XsdElement) copiedNested.getChildren().get(0);
        assertEquals("nestedElement_copy", copiedElement.getName());
        assertEquals("xs:string", copiedElement.getType());
    }

    @Test
    @DisplayName("Should handle unbounded maxOccurs")
    void testUnboundedMaxOccurs() {
        choice.setMaxOccurs(XsdNode.UNBOUNDED);

        assertEquals(XsdNode.UNBOUNDED, choice.getMaxOccurs());
        assertEquals(-1, choice.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle minOccurs=0 for optional choice")
    void testOptionalChoice() {
        choice.setMinOccurs(0);
        choice.setMaxOccurs(1);

        assertEquals(0, choice.getMinOccurs());
        assertEquals(1, choice.getMaxOccurs());
    }

    @Test
    @DisplayName("Should clear all children")
    void testClearChildren() {
        choice.addChild(new XsdElement("element1"));
        choice.addChild(new XsdElement("element2"));
        choice.addChild(new XsdElement("element3"));

        assertEquals(3, choice.getChildren().size());

        for (XsdNode child : new ArrayList<>(choice.getChildren())) {
            choice.removeChild(child);
        }

        assertEquals(0, choice.getChildren().size());
    }

    @Test
    @DisplayName("Should maintain children order")
    void testChildrenOrder() {
        XsdElement element1 = new XsdElement("element1");
        XsdElement element2 = new XsdElement("element2");
        XsdElement element3 = new XsdElement("element3");

        choice.addChild(element1);
        choice.addChild(element2);
        choice.addChild(element3);

        List<XsdNode> children = choice.getChildren();
        assertSame(element1, children.get(0));
        assertSame(element2, children.get(1));
        assertSame(element3, children.get(2));
    }

    @Test
    @DisplayName("Should handle documentation and appinfo")
    void testDocumentationAndAppinfo() {
        choice.setDocumentation("This is a choice documentation");
        choice.setAppinfo("This is appinfo");

        assertEquals("This is a choice documentation", choice.getDocumentation());
        assertEquals("This is appinfo", choice.getAppinfo());

        XsdChoice copy = (XsdChoice) choice.deepCopy(null);
        assertEquals("This is a choice documentation", copy.getDocumentation());
        assertEquals("This is appinfo", copy.getAppinfo());
    }

    @Test
    @DisplayName("Should have no choice-specific properties")
    void testNoChoiceSpecificProperties() {
        // XsdChoice has no specific properties beyond those inherited from XsdNode
        // This test verifies that the class is simple and only uses base functionality

        XsdChoice ch = new XsdChoice();
        assertEquals("choice", ch.getName());
        assertEquals(XsdNodeType.CHOICE, ch.getNodeType());
        assertNotNull(ch.getId());
        assertEquals(1, ch.getMinOccurs());
        assertEquals(1, ch.getMaxOccurs());
        assertNull(ch.getDocumentation());
        assertNull(ch.getAppinfo());
        assertEquals(0, ch.getChildren().size());
    }

    @Test
    @DisplayName("Should handle mixed element types in choice")
    void testMixedElementTypes() {
        XsdElement element = new XsdElement("stringElement");
        element.setType("xs:string");

        XsdSequence sequence = new XsdSequence();
        XsdElement nestedElement = new XsdElement("nestedElement");
        sequence.addChild(nestedElement);

        choice.addChild(element);
        choice.addChild(sequence);

        assertEquals(2, choice.getChildren().size());
        assertTrue(choice.getChildren().get(0) instanceof XsdElement);
        assertTrue(choice.getChildren().get(1) instanceof XsdSequence);
    }

    @Test
    @DisplayName("Should deep copy mixed element types")
    void testDeepCopyMixedElementTypes() {
        XsdElement element = new XsdElement("stringElement");
        element.setType("xs:string");

        XsdSequence sequence = new XsdSequence();
        XsdElement nestedElement = new XsdElement("nestedElement");
        sequence.addChild(nestedElement);

        choice.addChild(element);
        choice.addChild(sequence);

        XsdChoice copy = (XsdChoice) choice.deepCopy("_copy");

        assertEquals(2, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdElement);
        assertTrue(copy.getChildren().get(1) instanceof XsdSequence);

        XsdElement copiedElement = (XsdElement) copy.getChildren().get(0);
        assertEquals("stringElement_copy", copiedElement.getName());
        assertEquals("xs:string", copiedElement.getType());
    }
}
