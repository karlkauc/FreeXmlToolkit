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
 * Unit tests for XsdAttribute - represents XSD attribute declarations (xs:attribute).
 * Tests all attribute-specific properties, use constraints, and deep copy.
 */
class XsdAttributeTest {

    private XsdAttribute attribute;

    @BeforeEach
    void setUp() {
        attribute = new XsdAttribute("testAttribute");
    }

    @Test
    @DisplayName("Should create attribute with name and default use='optional'")
    void testConstructor() {
        assertEquals("testAttribute", attribute.getName());
        assertEquals("optional", attribute.getUse());
        assertEquals(XsdNodeType.ATTRIBUTE, attribute.getNodeType());
        assertNotNull(attribute.getId());
    }

    @Test
    @DisplayName("Should set and get type with property change event")
    void testSetType() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        attribute.setType("xs:string");

        assertEquals("xs:string", attribute.getType());
        assertEquals(1, events.size());
        assertEquals("type", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("xs:string", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should handle null type")
    void testSetNullType() {
        attribute.setType("xs:string");
        attribute.setType(null);

        assertNull(attribute.getType());
    }

    @Test
    @DisplayName("Should set and get use property")
    void testSetUse() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        attribute.setUse("required");

        assertEquals("required", attribute.getUse());
        assertEquals(1, events.size());
        assertEquals("use", events.get(0).getPropertyName());
        assertEquals("optional", events.get(0).getOldValue());
        assertEquals("required", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should handle use='prohibited'")
    void testSetUseProhibited() {
        attribute.setUse("prohibited");

        assertEquals("prohibited", attribute.getUse());
    }

    @Test
    @DisplayName("Should set and get fixed value")
    void testSetFixed() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        attribute.setFixed("fixedValue");

        assertEquals("fixedValue", attribute.getFixed());
        assertEquals(1, events.size());
        assertEquals("fixed", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("fixedValue", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should handle null fixed value")
    void testSetNullFixed() {
        attribute.setFixed("fixedValue");
        attribute.setFixed(null);

        assertNull(attribute.getFixed());
    }

    @Test
    @DisplayName("Should set and get default value")
    void testSetDefault() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        attribute.setDefaultValue("defaultValue");

        assertEquals("defaultValue", attribute.getDefaultValue());
        assertEquals(1, events.size());
        assertEquals("defaultValue", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("defaultValue", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should handle null default value")
    void testSetNullDefault() {
        attribute.setDefaultValue("defaultValue");
        attribute.setDefaultValue(null);

        assertNull(attribute.getDefaultValue());
    }

    @Test
    @DisplayName("Should set and get form property")
    void testSetForm() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        attribute.setForm("qualified");

        assertEquals("qualified", attribute.getForm());
        assertEquals(1, events.size());
        assertEquals("form", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("qualified", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should handle form='unqualified'")
    void testSetFormUnqualified() {
        attribute.setForm("unqualified");

        assertEquals("unqualified", attribute.getForm());
    }

    @Test
    @DisplayName("Should deep copy attribute with all properties")
    void testDeepCopy() {
        attribute.setType("xs:string");
        attribute.setUse("required");
        attribute.setFixed("fixedValue");
        attribute.setDefaultValue("defaultValue");
        attribute.setForm("qualified");
        attribute.setMinOccurs(1);
        attribute.setMaxOccurs(1);
        attribute.setDocumentation("Test documentation");
        attribute.setAppinfo("Test appinfo");

        XsdAttribute copy = (XsdAttribute) attribute.deepCopy("_copy");

        // Name should have suffix
        assertEquals("testAttribute_copy", copy.getName());

        // All properties should be copied
        assertEquals("xs:string", copy.getType());
        assertEquals("required", copy.getUse());
        assertEquals("fixedValue", copy.getFixed());
        assertEquals("defaultValue", copy.getDefaultValue());
        assertEquals("qualified", copy.getForm());

        // Inherited properties
        assertEquals(1, copy.getMinOccurs());
        assertEquals(1, copy.getMaxOccurs());
        assertEquals("Test documentation", copy.getDocumentation());
        assertEquals("Test appinfo", copy.getAppinfoAsString());

        // Should have different ID
        assertNotEquals(attribute.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy without suffix")
    void testDeepCopyWithoutSuffix() {
        XsdAttribute copy = (XsdAttribute) attribute.deepCopy(null);

        assertEquals("testAttribute", copy.getName());
        assertNotEquals(attribute.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy children recursively")
    void testDeepCopyWithChildren() {
        XsdSimpleType childType = new XsdSimpleType("stringType");
        attribute.addChild(childType);

        XsdAttribute copy = (XsdAttribute) attribute.deepCopy("_copy");

        assertEquals(1, copy.getChildren().size());
        XsdSimpleType copiedChild = (XsdSimpleType) copy.getChildren().get(0);
        // Children should NOT get the suffix - only the root node being copied gets it
        assertEquals("stringType", copiedChild.getName(), "Child name should be copied without suffix");
        assertNotEquals(childType.getId(), copiedChild.getId());
        assertSame(copy, copiedChild.getParent());
    }

    @Test
    @DisplayName("Should inherit properties from XsdNode")
    void testInheritedProperties() {
        attribute.setMinOccurs(0);
        attribute.setMaxOccurs(1);
        attribute.setDocumentation("Documentation text");
        attribute.setAppinfo("Appinfo text");

        assertEquals(0, attribute.getMinOccurs());
        assertEquals(1, attribute.getMaxOccurs());
        assertEquals("Documentation text", attribute.getDocumentation());
        assertEquals("Appinfo text", attribute.getAppinfoAsString());
    }

    @Test
    @DisplayName("Should handle parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexType parent = new XsdComplexType("parentType");
        XsdAttribute child = new XsdAttribute("childAttr");

        parent.addChild(child);

        assertSame(parent, child.getParent());
        assertEquals(1, parent.getChildren().size());
        assertTrue(parent.getChildren().contains(child));
    }

    @Test
    @DisplayName("Should remove child from parent")
    void testRemoveChild() {
        XsdComplexType parent = new XsdComplexType("parentType");
        XsdAttribute child = new XsdAttribute("childAttr");

        parent.addChild(child);
        parent.removeChild(child);

        assertNull(child.getParent());
        assertEquals(0, parent.getChildren().size());
    }

    @Test
    @DisplayName("Should fire property change events for inherited properties")
    void testInheritedPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        attribute.setDocumentation("New documentation");

        assertEquals(1, events.size());
        assertEquals("documentation", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("New documentation", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should handle property change listener removal")
    void testPropertyChangeListenerRemoval() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        attribute.addPropertyChangeListener(listener);
        attribute.setType("xs:string");

        assertEquals(1, events.size());

        events.clear();
        attribute.removePropertyChangeListener(listener);
        attribute.setType("xs:int");

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("Should verify node type is ATTRIBUTE")
    void testNodeType() {
        assertEquals(XsdNodeType.ATTRIBUTE, attribute.getNodeType());
    }

    @Test
    @DisplayName("Should handle multiple property changes")
    void testMultiplePropertyChanges() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        attribute.setType("xs:string");
        attribute.setUse("required");
        attribute.setFixed("fixedValue");

        assertEquals(3, events.size());
        assertEquals("type", events.get(0).getPropertyName());
        assertEquals("use", events.get(1).getPropertyName());
        assertEquals("fixed", events.get(2).getPropertyName());
    }

    @Test
    @DisplayName("Should not fire event when setting same value")
    void testNoEventWhenValueUnchanged() {
        attribute.setType("xs:string");

        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        // Setting the same value should NOT fire event (JavaBeans pattern)
        // PropertyChangeSupport.firePropertyChange() does not fire if old == new
        attribute.setType("xs:string");

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("Should handle use values: required, optional, prohibited")
    void testUseValues() {
        attribute.setUse("required");
        assertEquals("required", attribute.getUse());

        attribute.setUse("optional");
        assertEquals("optional", attribute.getUse());

        attribute.setUse("prohibited");
        assertEquals("prohibited", attribute.getUse());
    }

    @Test
    @DisplayName("Should handle form values: qualified, unqualified")
    void testFormValues() {
        attribute.setForm("qualified");
        assertEquals("qualified", attribute.getForm());

        attribute.setForm("unqualified");
        assertEquals("unqualified", attribute.getForm());
    }

    @Test
    @DisplayName("Should copy default use='optional' on deep copy")
    void testDeepCopyDefaultUse() {
        // Create new attribute with default use
        XsdAttribute newAttr = new XsdAttribute("newAttr");

        XsdAttribute copy = (XsdAttribute) newAttr.deepCopy("_copy");

        assertEquals("optional", copy.getUse());
    }

    @Test
    @DisplayName("Should handle null values for all properties")
    void testNullPropertyValues() {
        attribute.setType("xs:string");
        attribute.setFixed("fixed");
        attribute.setDefaultValue("default");
        attribute.setForm("qualified");

        attribute.setType(null);
        attribute.setFixed(null);
        attribute.setDefaultValue(null);
        attribute.setForm(null);

        assertNull(attribute.getType());
        assertNull(attribute.getFixed());
        assertNull(attribute.getDefaultValue());
        assertNull(attribute.getForm());
    }

    @Test
    @DisplayName("Should fire events for all property changes")
    void testAllPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        attribute.addPropertyChangeListener(events::add);

        attribute.setType("xs:string");
        attribute.setUse("required");
        attribute.setFixed("fixedValue");
        attribute.setDefaultValue("defaultValue");
        attribute.setForm("qualified");

        assertEquals(5, events.size());

        // Verify all property names
        List<String> propertyNames = events.stream()
            .map(PropertyChangeEvent::getPropertyName)
            .toList();

        assertTrue(propertyNames.contains("type"));
        assertTrue(propertyNames.contains("use"));
        assertTrue(propertyNames.contains("fixed"));
        assertTrue(propertyNames.contains("defaultValue"));
        assertTrue(propertyNames.contains("form"));
    }
}
