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
 * Unit tests for XsdElement - the most common XSD node type representing xs:element.
 * Tests all element-specific properties, patterns, enumerations, assertions, and deep copy.
 */
class XsdElementTest {

    private XsdElement element;

    @BeforeEach
    void setUp() {
        element = new XsdElement("testElement");
    }

    @Test
    @DisplayName("Should create element with name")
    void testConstructor() {
        assertEquals("testElement", element.getName());
        assertEquals(XsdNodeType.ELEMENT, element.getNodeType());
        assertNotNull(element.getId());
    }

    @Test
    @DisplayName("Should set and get type with property change event")
    void testSetType() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.setType("xs:string");

        assertEquals("xs:string", element.getType());
        assertEquals(1, events.size());
        assertEquals("type", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("xs:string", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should handle null type")
    void testSetNullType() {
        element.setType("xs:string");
        element.setType(null);

        assertNull(element.getType());
    }

    @Test
    @DisplayName("Should set and get nillable property")
    void testSetNillable() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.setNillable(true);

        assertTrue(element.isNillable());
        assertEquals(1, events.size());
        assertEquals("nillable", events.get(0).getPropertyName());
        assertFalse((Boolean) events.get(0).getOldValue());
        assertTrue((Boolean) events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should set and get abstract property")
    void testSetAbstract() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.setAbstract(true);

        assertTrue(element.isAbstract());
        assertEquals(1, events.size());
        assertEquals("abstract", events.get(0).getPropertyName());
        assertFalse((Boolean) events.get(0).getOldValue());
        assertTrue((Boolean) events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should set and get fixed value")
    void testSetFixed() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.setFixed("fixedValue");

        assertEquals("fixedValue", element.getFixed());
        assertEquals(1, events.size());
        assertEquals("fixed", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("fixedValue", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should set and get default value")
    void testSetDefault() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.setDefaultValue("defaultValue");

        assertEquals("defaultValue", element.getDefaultValue());
        assertEquals(1, events.size());
        assertEquals("defaultValue", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("defaultValue", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should set and get substitution group")
    void testSetSubstitutionGroup() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.setSubstitutionGroup("baseElement");

        assertEquals("baseElement", element.getSubstitutionGroup());
        assertEquals(1, events.size());
        assertEquals("substitutionGroup", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("baseElement", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should set and get form property")
    void testSetForm() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.setForm("qualified");

        assertEquals("qualified", element.getForm());
        assertEquals(1, events.size());
        assertEquals("form", events.get(0).getPropertyName());
        assertNull(events.get(0).getOldValue());
        assertEquals("qualified", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should add pattern and fire property change")
    void testAddPattern() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.addPattern("[A-Z]+");

        assertEquals(1, element.getPatterns().size());
        assertTrue(element.getPatterns().contains("[A-Z]+"));
        assertEquals(1, events.size());
        assertEquals("patterns", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should not add null or empty pattern")
    void testAddNullOrEmptyPattern() {
        element.addPattern(null);
        element.addPattern("");
        element.addPattern("   ");

        assertEquals(0, element.getPatterns().size());
    }

    @Test
    @DisplayName("Should trim pattern before adding")
    void testAddPatternTrims() {
        element.addPattern("  [A-Z]+  ");

        assertTrue(element.getPatterns().contains("[A-Z]+"));
    }

    @Test
    @DisplayName("Should remove pattern")
    void testRemovePattern() {
        element.addPattern("[A-Z]+");
        element.addPattern("[0-9]+");

        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.removePattern("[A-Z]+");

        assertEquals(1, element.getPatterns().size());
        assertFalse(element.getPatterns().contains("[A-Z]+"));
        assertTrue(element.getPatterns().contains("[0-9]+"));
        assertEquals(1, events.size());
        assertEquals("patterns", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should clear patterns")
    void testClearPatterns() {
        element.addPattern("[A-Z]+");
        element.addPattern("[0-9]+");

        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.clearPatterns();

        assertEquals(0, element.getPatterns().size());
        assertEquals(1, events.size());
        assertEquals("patterns", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should not fire event when clearing empty patterns")
    void testClearEmptyPatterns() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.clearPatterns();

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("Should return unmodifiable patterns list")
    void testGetPatternsUnmodifiable() {
        element.addPattern("[A-Z]+");

        List<String> patterns = element.getPatterns();

        assertThrows(UnsupportedOperationException.class, () ->
            patterns.add("newPattern")
        );
    }

    @Test
    @DisplayName("Should add enumeration and fire property change")
    void testAddEnumeration() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.addEnumeration("VALUE1");

        assertEquals(1, element.getEnumerations().size());
        assertTrue(element.getEnumerations().contains("VALUE1"));
        assertEquals(1, events.size());
        assertEquals("enumerations", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should not add null or empty enumeration")
    void testAddNullOrEmptyEnumeration() {
        element.addEnumeration(null);
        element.addEnumeration("");
        element.addEnumeration("   ");

        assertEquals(0, element.getEnumerations().size());
    }

    @Test
    @DisplayName("Should trim enumeration before adding")
    void testAddEnumerationTrims() {
        element.addEnumeration("  VALUE1  ");

        assertTrue(element.getEnumerations().contains("VALUE1"));
    }

    @Test
    @DisplayName("Should remove enumeration")
    void testRemoveEnumeration() {
        element.addEnumeration("VALUE1");
        element.addEnumeration("VALUE2");

        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.removeEnumeration("VALUE1");

        assertEquals(1, element.getEnumerations().size());
        assertFalse(element.getEnumerations().contains("VALUE1"));
        assertTrue(element.getEnumerations().contains("VALUE2"));
        assertEquals(1, events.size());
        assertEquals("enumerations", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should clear enumerations")
    void testClearEnumerations() {
        element.addEnumeration("VALUE1");
        element.addEnumeration("VALUE2");

        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.clearEnumerations();

        assertEquals(0, element.getEnumerations().size());
        assertEquals(1, events.size());
        assertEquals("enumerations", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should return unmodifiable enumerations list")
    void testGetEnumerationsUnmodifiable() {
        element.addEnumeration("VALUE1");

        List<String> enumerations = element.getEnumerations();

        assertThrows(UnsupportedOperationException.class, () ->
            enumerations.add("VALUE2")
        );
    }

    @Test
    @DisplayName("Should add assertion and fire property change")
    void testAddAssertion() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.addAssertion("@value > 0");

        assertEquals(1, element.getAssertions().size());
        assertTrue(element.getAssertions().contains("@value > 0"));
        assertEquals(1, events.size());
        assertEquals("assertions", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should not add null or empty assertion")
    void testAddNullOrEmptyAssertion() {
        element.addAssertion(null);
        element.addAssertion("");
        element.addAssertion("   ");

        assertEquals(0, element.getAssertions().size());
    }

    @Test
    @DisplayName("Should remove assertion")
    void testRemoveAssertion() {
        element.addAssertion("@value > 0");
        element.addAssertion("@value < 100");

        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.removeAssertion("@value > 0");

        assertEquals(1, element.getAssertions().size());
        assertFalse(element.getAssertions().contains("@value > 0"));
        assertTrue(element.getAssertions().contains("@value < 100"));
        assertEquals(1, events.size());
        assertEquals("assertions", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should clear assertions")
    void testClearAssertions() {
        element.addAssertion("@value > 0");
        element.addAssertion("@value < 100");

        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.clearAssertions();

        assertEquals(0, element.getAssertions().size());
        assertEquals(1, events.size());
        assertEquals("assertions", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("Should return unmodifiable assertions list")
    void testGetAssertionsUnmodifiable() {
        element.addAssertion("@value > 0");

        List<String> assertions = element.getAssertions();

        assertThrows(UnsupportedOperationException.class, () ->
            assertions.add("@value < 100")
        );
    }

    @Test
    @DisplayName("Should deep copy element with all properties")
    void testDeepCopy() {
        element.setType("xs:string");
        element.setNillable(true);
        element.setAbstract(true);
        element.setFixed("fixedValue");
        element.setDefaultValue("defaultValue");
        element.setSubstitutionGroup("baseElement");
        element.setForm("qualified");
        element.addPattern("[A-Z]+");
        element.addEnumeration("VALUE1");
        element.addAssertion("@value > 0");
        element.setMinOccurs(1);
        element.setMaxOccurs(10);
        element.setDocumentation("Test documentation");

        XsdElement copy = (XsdElement) element.deepCopy("_copy");

        // Name should have suffix
        assertEquals("testElement_copy", copy.getName());

        // All properties should be copied
        assertEquals("xs:string", copy.getType());
        assertTrue(copy.isNillable());
        assertTrue(copy.isAbstract());
        assertEquals("fixedValue", copy.getFixed());
        assertEquals("defaultValue", copy.getDefaultValue());
        assertEquals("baseElement", copy.getSubstitutionGroup());
        assertEquals("qualified", copy.getForm());

        // Lists should be copied
        assertEquals(1, copy.getPatterns().size());
        assertTrue(copy.getPatterns().contains("[A-Z]+"));
        assertEquals(1, copy.getEnumerations().size());
        assertTrue(copy.getEnumerations().contains("VALUE1"));
        assertEquals(1, copy.getAssertions().size());
        assertTrue(copy.getAssertions().contains("@value > 0"));

        // Inherited properties
        assertEquals(1, copy.getMinOccurs());
        assertEquals(10, copy.getMaxOccurs());
        assertEquals("Test documentation", copy.getDocumentation());

        // Should have different ID
        assertNotEquals(element.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy without suffix")
    void testDeepCopyWithoutSuffix() {
        XsdElement copy = (XsdElement) element.deepCopy(null);

        assertEquals("testElement", copy.getName());
        assertNotEquals(element.getId(), copy.getId());
    }

    @Test
    @DisplayName("Should deep copy children recursively")
    void testDeepCopyWithChildren() {
        XsdElement child = new XsdElement("child");
        child.setType("xs:int");
        element.addChild(child);

        XsdElement copy = (XsdElement) element.deepCopy("_copy");

        assertEquals(1, copy.getChildren().size());
        XsdElement copiedChild = (XsdElement) copy.getChildren().get(0);
        assertEquals("child_copy", copiedChild.getName());
        assertEquals("xs:int", copiedChild.getType());
        assertNotEquals(child.getId(), copiedChild.getId());
        assertSame(copy, copiedChild.getParent());
    }

    @Test
    @DisplayName("Should inherit properties from XsdNode")
    void testInheritedProperties() {
        element.setMinOccurs(2);
        element.setMaxOccurs(XsdNode.UNBOUNDED);
        element.setDocumentation("Documentation text");
        element.setAppinfo("Appinfo text");

        assertEquals(2, element.getMinOccurs());
        assertEquals(XsdNode.UNBOUNDED, element.getMaxOccurs());
        assertEquals("Documentation text", element.getDocumentation());
        assertEquals("Appinfo text", element.getAppinfo());
    }

    @Test
    @DisplayName("Should handle parent-child relationships")
    void testParentChildRelationships() {
        XsdElement parent = new XsdElement("parent");
        XsdElement child = new XsdElement("child");

        parent.addChild(child);

        assertSame(parent, child.getParent());
        assertEquals(1, parent.getChildren().size());
        assertTrue(parent.getChildren().contains(child));
    }

    @Test
    @DisplayName("Should remove child from parent")
    void testRemoveChild() {
        XsdElement parent = new XsdElement("parent");
        XsdElement child = new XsdElement("child");

        parent.addChild(child);
        parent.removeChild(child);

        assertNull(child.getParent());
        assertEquals(0, parent.getChildren().size());
    }

    @Test
    @DisplayName("Should fire property change events for inherited properties")
    void testInheritedPropertyChangeEvents() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        element.addPropertyChangeListener(events::add);

        element.setMinOccurs(5);

        assertEquals(1, events.size());
        assertEquals("minOccurs", events.get(0).getPropertyName());
        assertEquals(1, events.get(0).getOldValue());
        assertEquals(5, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Should handle property change listener removal")
    void testPropertyChangeListenerRemoval() {
        List<PropertyChangeEvent> events = new ArrayList<>();
        PropertyChangeListener listener = events::add;

        element.addPropertyChangeListener(listener);
        element.setType("xs:string");

        assertEquals(1, events.size());

        events.clear();
        element.removePropertyChangeListener(listener);
        element.setType("xs:int");

        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("Should verify node type is ELEMENT")
    void testNodeType() {
        assertEquals(XsdNodeType.ELEMENT, element.getNodeType());
    }

    @Test
    @DisplayName("Should handle multiple patterns")
    void testMultiplePatterns() {
        element.addPattern("[A-Z]+");
        element.addPattern("[0-9]+");
        element.addPattern("[a-z]+");

        assertEquals(3, element.getPatterns().size());
        assertTrue(element.getPatterns().contains("[A-Z]+"));
        assertTrue(element.getPatterns().contains("[0-9]+"));
        assertTrue(element.getPatterns().contains("[a-z]+"));
    }

    @Test
    @DisplayName("Should handle multiple enumerations")
    void testMultipleEnumerations() {
        element.addEnumeration("SMALL");
        element.addEnumeration("MEDIUM");
        element.addEnumeration("LARGE");

        assertEquals(3, element.getEnumerations().size());
        assertTrue(element.getEnumerations().contains("SMALL"));
        assertTrue(element.getEnumerations().contains("MEDIUM"));
        assertTrue(element.getEnumerations().contains("LARGE"));
    }

    @Test
    @DisplayName("Should handle multiple assertions")
    void testMultipleAssertions() {
        element.addAssertion("@value > 0");
        element.addAssertion("@value < 100");
        element.addAssertion("@value mod 2 = 0");

        assertEquals(3, element.getAssertions().size());
        assertTrue(element.getAssertions().contains("@value > 0"));
        assertTrue(element.getAssertions().contains("@value < 100"));
        assertTrue(element.getAssertions().contains("@value mod 2 = 0"));
    }
}
