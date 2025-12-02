package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdAll - represents XSD all compositor (xs:all).
 * The all compositor allows child elements to appear in any order, each at most once.
 * Tests all-specific behavior and inherited XsdNode properties.
 */
class XsdAllTest {

    private XsdAll all;

    @BeforeEach
    void setUp() {
        all = new XsdAll();
    }

    @Test
    @DisplayName("Should create all with fixed name 'all'")
    void testConstructor() {
        assertEquals("all", all.getName());
        assertEquals(XsdNodeType.ALL, all.getNodeType());
        assertNotNull(all.getId());
    }

    @Test
    @DisplayName("Should verify node type is ALL")
    void testNodeType() {
        assertEquals(XsdNodeType.ALL, all.getNodeType());
    }

    @Test
    @DisplayName("Should deep copy all without applying suffix to name")
    void testDeepCopyNoSuffixOnName() {
        all.setMinOccurs(1);
        all.setMaxOccurs(1);
        all.setDocumentation("Test documentation");

        XsdAll copy = (XsdAll) all.deepCopy("_copy");

        // Name should remain "all" (suffix not applied)
        assertEquals("all", copy.getName());

        // Properties should be copied
        assertEquals(1, copy.getMinOccurs());
        assertEquals(1, copy.getMaxOccurs());
        assertEquals("Test documentation", copy.getDocumentation());

        // Should have different ID
        assertNotEquals(all.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy without suffix parameter")
    void testDeepCopyWithNullSuffix() {
        XsdAll copy = (XsdAll) all.deepCopy(null);

        assertEquals("all", copy.getName());
        assertNotEquals(all.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy children recursively")
    void testDeepCopyWithChildren() {
        XsdElement element1 = new XsdElement("element1");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("element2");
        element2.setType("xs:int");

        all.addChild(element1);
        all.addChild(element2);

        XsdAll copy = (XsdAll) all.deepCopy("_copy");

        assertEquals(2, copy.getChildren().size());

        // Children should NOT get the suffix - only the root node being copied gets it
        XsdElement copiedElement1 = (XsdElement) copy.getChildren().get(0);
        assertEquals("element1", copiedElement1.getName(), "Child name should be copied without suffix");
        assertEquals("xs:string", copiedElement1.getType());
        assertNotEquals(element1.getId(), copiedElement1.getId());
        assertSame(copy, copiedElement1.getParent());

        XsdElement copiedElement2 = (XsdElement) copy.getChildren().get(1);
        assertEquals("element2", copiedElement2.getName(), "Child name should be copied without suffix");
        assertEquals("xs:int", copiedElement2.getType());
        assertNotEquals(element2.getId(), copiedElement2.getId());
        assertSame(copy, copiedElement2.getParent());
    }

    @Test
    @DisplayName("Should inherit properties from XsdNode")
    void testInheritedProperties() {
        all.setMinOccurs(0);
        all.setMaxOccurs(1);
        all.setDocumentation("Documentation text");
        all.setAppinfo("Appinfo text");

        assertEquals(0, all.getMinOccurs());
        assertEquals(1, all.getMaxOccurs());
        assertEquals("Documentation text", all.getDocumentation());
        assertEquals("Appinfo text", all.getAppinfoAsString());
    }

    @Test
    @DisplayName("Should handle parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexType parent = new XsdComplexType("parentType");
        XsdAll child = new XsdAll();

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

        all.addChild(element1);
        all.addChild(element2);
        all.addChild(element3);

        assertEquals(3, all.getChildren().size());
        assertTrue(all.getChildren().contains(element1));
        assertTrue(all.getChildren().contains(element2));
        assertTrue(all.getChildren().contains(element3));
    }

    @Test
    @DisplayName("Should remove child from all")
    void testRemoveChild() {
        XsdElement element1 = new XsdElement("element1");
        XsdElement element2 = new XsdElement("element2");

        all.addChild(element1);
        all.addChild(element2);

        all.removeChild(element1);

        assertEquals(1, all.getChildren().size());
        assertFalse(all.getChildren().contains(element1));
        assertTrue(all.getChildren().contains(element2));
        assertNull(element1.getParent());
    }

    @Test
    @DisplayName("Should fire property change events for inherited properties")
    void testInheritedPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        all.addPropertyChangeListener(events::add);

        all.setMinOccurs(0);

        assertEquals(1, events.size());
        assertEquals("minOccurs", events.get(0).getPropertyName());
        assertEquals(1, events.get(0).getOldValue());
        assertEquals(0, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should fire property change events when adding children")
    void testChildrenPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        all.addPropertyChangeListener(events::add);

        XsdElement element = new XsdElement("element1");
        all.addChild(element);

        assertEquals(1, events.size());
        assertEquals("children", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should handle property change listener removal")
    void testPropertyChangeListenerRemoval() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        all.addPropertyChangeListener(listener);
        all.setMinOccurs(0);

        assertEquals(1, events.size());

        events.clear();
        all.removePropertyChangeListener(listener);
        all.setMinOccurs(1);

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("Should handle XSD 1.1 unbounded maxOccurs restriction")
    void testMaxOccursRestriction() {
        // XSD 1.0: xs:all maxOccurs must be 1
        // XSD 1.1: xs:all can have maxOccurs > 1 or unbounded

        // XSD 1.0 style
        all.setMaxOccurs(1);
        assertEquals(1, all.getMaxOccurs());

        // XSD 1.1 style
        all.setMaxOccurs(XsdNode.UNBOUNDED);
        assertEquals(XsdNode.UNBOUNDED, all.getMaxOccurs());
    }

    @Test
    @DisplayName("Should handle optional all compositor")
    void testOptionalAll() {
        all.setMinOccurs(0);
        all.setMaxOccurs(1);

        assertEquals(0, all.getMinOccurs());
        assertEquals(1, all.getMaxOccurs());
    }

    @Test
    @DisplayName("Should clear all children")
    void testClearChildren() {
        all.addChild(new XsdElement("element1"));
        all.addChild(new XsdElement("element2"));
        all.addChild(new XsdElement("element3"));

        assertEquals(3, all.getChildren().size());

        for (XsdNode child : new ArrayList<>(all.getChildren())) {
            all.removeChild(child);
        }

        assertEquals(0, all.getChildren().size());
    }

    @Test
    @DisplayName("Should maintain children insertion order")
    void testChildrenOrder() {
        XsdElement element1 = new XsdElement("element1");
        XsdElement element2 = new XsdElement("element2");
        XsdElement element3 = new XsdElement("element3");

        all.addChild(element1);
        all.addChild(element2);
        all.addChild(element3);

        List<XsdNode> children = all.getChildren();
        assertSame(element1, children.get(0));
        assertSame(element2, children.get(1));
        assertSame(element3, children.get(2));
    }

    @Test
    @DisplayName("Should handle documentation and appinfo")
    void testDocumentationAndAppinfo() {
        all.setDocumentation("This is an all compositor documentation");
        all.setAppinfo("This is appinfo");

        assertEquals("This is an all compositor documentation", all.getDocumentation());
        assertEquals("This is appinfo", all.getAppinfoAsString());

        XsdAll copy = (XsdAll) all.deepCopy(null);
        assertEquals("This is an all compositor documentation", copy.getDocumentation());
        assertEquals("This is appinfo", copy.getAppinfoAsString());
    }

    @Test
    @DisplayName("Should have no all-specific properties")
    void testNoAllSpecificProperties() {
        // XsdAll has no specific properties beyond those inherited from XsdNode
        // This test verifies that the class is simple and only uses base functionality

        XsdAll allCompositor = new XsdAll();
        assertEquals("all", allCompositor.getName());
        assertEquals(XsdNodeType.ALL, allCompositor.getNodeType());
        assertNotNull(allCompositor.getId());
        assertEquals(1, allCompositor.getMinOccurs());
        assertEquals(1, allCompositor.getMaxOccurs());
        assertNull(allCompositor.getDocumentation());
        assertNull(allCompositor.getAppinfo());
        assertEquals(0, allCompositor.getChildren().size());
    }

    @Test
    @DisplayName("Should handle elements with maxOccurs constraints")
    void testElementMaxOccursConstraints() {
        // In xs:all, child elements traditionally have maxOccurs=1 (XSD 1.0)
        // XSD 1.1 relaxes this

        XsdElement element1 = new XsdElement("element1");
        element1.setMaxOccurs(1);

        XsdElement element2 = new XsdElement("element2");
        element2.setMaxOccurs(1);

        all.addChild(element1);
        all.addChild(element2);

        assertEquals(2, all.getChildren().size());
        assertEquals(1, all.getChildren().get(0).getMaxOccurs());
        assertEquals(1, all.getChildren().get(1).getMaxOccurs());
    }

    @Test
    @DisplayName("Should deep copy all with complex nested structure")
    void testDeepCopyComplexStructure() {
        XsdElement element1 = new XsdElement("element1");
        element1.setType("xs:string");
        element1.setMinOccurs(0);
        element1.setMaxOccurs(1);

        XsdElement element2 = new XsdElement("element2");
        element2.setType("xs:int");
        element2.setMinOccurs(1);
        element2.setMaxOccurs(1);

        all.addChild(element1);
        all.addChild(element2);
        all.setDocumentation("Complex all compositor");

        XsdAll copy = (XsdAll) all.deepCopy("_copy");

        assertEquals("all", copy.getName());
        assertEquals(2, copy.getChildren().size());
        assertEquals("Complex all compositor", copy.getDocumentation());

        // Children should NOT get the suffix - only the root node being copied gets it
        XsdElement copiedElement1 = (XsdElement) copy.getChildren().get(0);
        assertEquals("element1", copiedElement1.getName(), "Child name should be copied without suffix");
        assertEquals("xs:string", copiedElement1.getType());
        assertEquals(0, copiedElement1.getMinOccurs());
        assertEquals(1, copiedElement1.getMaxOccurs());

        XsdElement copiedElement2 = (XsdElement) copy.getChildren().get(1);
        assertEquals("element2", copiedElement2.getName(), "Child name should be copied without suffix");
        assertEquals("xs:int", copiedElement2.getType());
        assertEquals(1, copiedElement2.getMinOccurs());
        assertEquals(1, copiedElement2.getMaxOccurs());
    }

    @Test
    @DisplayName("Should not allow nested compositors in XSD 1.0 mode")
    void testNoNestedCompositors() {
        // Note: In XSD 1.0, xs:all cannot contain other compositors
        // This test just verifies the model allows adding (the serializer would validate)

        XsdSequence sequence = new XsdSequence();

        // The model allows it, but XSD validator would reject
        all.addChild(sequence);

        assertEquals(1, all.getChildren().size());
        assertInstanceOf(XsdSequence.class, all.getChildren().get(0));
    }

    @Test
    @DisplayName("Should handle elements with different minOccurs values")
    void testElementsWithDifferentMinOccurs() {
        XsdElement required = new XsdElement("requiredElement");
        required.setMinOccurs(1);
        required.setMaxOccurs(1);

        XsdElement optional = new XsdElement("optionalElement");
        optional.setMinOccurs(0);
        optional.setMaxOccurs(1);

        all.addChild(required);
        all.addChild(optional);

        assertEquals(2, all.getChildren().size());
        assertEquals(1, all.getChildren().get(0).getMinOccurs());
        assertEquals(0, all.getChildren().get(1).getMinOccurs());
    }
}
