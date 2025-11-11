package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdExtension.
 *
 * @since 2.0
 */
class XsdExtensionTest {

    private XsdExtension extension;

    @BeforeEach
    void setUp() {
        extension = new XsdExtension();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdExtension ext = new XsdExtension();
        assertEquals("extension", ext.getName());
        assertNull(ext.getBase());
    }

    @Test
    @DisplayName("constructor with base should set base type")
    void testConstructorWithBase() {
        XsdExtension ext = new XsdExtension("xs:string");
        assertEquals("extension", ext.getName());
        assertEquals("xs:string", ext.getBase());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return EXTENSION")
    void testGetNodeType() {
        assertEquals(XsdNodeType.EXTENSION, extension.getNodeType());
    }

    // ========== Base Property Tests ==========

    @Test
    @DisplayName("getBase() should return null by default")
    void testGetBaseDefaultValue() {
        assertNull(extension.getBase());
    }

    @Test
    @DisplayName("setBase() should set base type")
    void testSetBase() {
        extension.setBase("xs:string");
        assertEquals("xs:string", extension.getBase());

        extension.setBase("PersonType");
        assertEquals("PersonType", extension.getBase());
    }

    @Test
    @DisplayName("setBase() should fire PropertyChangeEvent")
    void testSetBaseFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("base", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("xs:integer", evt.getNewValue());
            eventFired.set(true);
        };

        extension.addPropertyChangeListener(listener);
        extension.setBase("xs:integer");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setBase() should allow null value")
    void testSetBaseAllowsNull() {
        extension.setBase("xs:string");
        extension.setBase(null);
        assertNull(extension.getBase());
    }

    @Test
    @DisplayName("setBase() should accept built-in types")
    void testSetBaseBuiltInTypes() {
        extension.setBase("xs:integer");
        assertEquals("xs:integer", extension.getBase());

        extension.setBase("xs:string");
        assertEquals("xs:string", extension.getBase());

        extension.setBase("xs:decimal");
        assertEquals("xs:decimal", extension.getBase());
    }

    @Test
    @DisplayName("setBase() should accept custom type references")
    void testSetBaseCustomTypes() {
        extension.setBase("PersonType");
        assertEquals("PersonType", extension.getBase());

        extension.setBase("ns:CustomType");
        assertEquals("ns:CustomType", extension.getBase());
    }

    // ========== Children Tests (Elements, Attributes, Compositors) ==========

    @Test
    @DisplayName("extension should support element children")
    void testElementChildren() {
        XsdElement element1 = new XsdElement("department");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("salary");
        element2.setType("xs:decimal");

        extension.addChild(element1);
        extension.addChild(element2);

        assertEquals(2, extension.getChildren().size());
        assertEquals(element1, extension.getChildren().get(0));
        assertEquals(element2, extension.getChildren().get(1));
    }

    @Test
    @DisplayName("extension should support attribute children")
    void testAttributeChildren() {
        XsdAttribute attr1 = new XsdAttribute("currency");
        attr1.setType("xs:string");
        XsdAttribute attr2 = new XsdAttribute("lang");
        attr2.setType("xs:string");

        extension.addChild(attr1);
        extension.addChild(attr2);

        assertEquals(2, extension.getChildren().size());
        assertTrue(extension.getChildren().get(0) instanceof XsdAttribute);
        assertTrue(extension.getChildren().get(1) instanceof XsdAttribute);
    }

    @Test
    @DisplayName("extension should support compositor children")
    void testCompositorChildren() {
        XsdSequence sequence = new XsdSequence();
        XsdElement element = new XsdElement("name");
        element.setType("xs:string");
        sequence.addChild(element);

        extension.addChild(sequence);

        assertEquals(1, extension.getChildren().size());
        assertTrue(extension.getChildren().get(0) instanceof XsdSequence);
    }

    @Test
    @DisplayName("extension should support mixed children (compositor + attributes)")
    void testMixedChildren() {
        // Extension can have: sequence/choice/all + attributes
        XsdSequence sequence = new XsdSequence();
        XsdElement element = new XsdElement("department");
        element.setType("xs:string");
        sequence.addChild(element);

        XsdAttribute attr = new XsdAttribute("id");
        attr.setType("xs:string");

        extension.addChild(sequence);
        extension.addChild(attr);

        assertEquals(2, extension.getChildren().size());
        assertTrue(extension.getChildren().get(0) instanceof XsdSequence);
        assertTrue(extension.getChildren().get(1) instanceof XsdAttribute);
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("extension should support parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexContent parent = new XsdComplexContent();
        extension.setParent(parent);
        parent.addChild(extension);

        assertEquals(parent, extension.getParent());
        assertTrue(parent.getChildren().contains(extension));
    }

    @Test
    @DisplayName("extension should work as child of simpleContent")
    void testExtensionInSimpleContent() {
        XsdSimpleContent simpleContent = new XsdSimpleContent();
        extension.setBase("xs:string");
        extension.setParent(simpleContent);
        simpleContent.addChild(extension);

        assertEquals(simpleContent, extension.getParent());
        assertEquals(extension, simpleContent.getExtension());
    }

    @Test
    @DisplayName("extension should work as child of complexContent")
    void testExtensionInComplexContent() {
        XsdComplexContent complexContent = new XsdComplexContent();
        extension.setBase("PersonType");
        extension.setParent(complexContent);
        complexContent.addChild(extension);

        assertEquals(complexContent, extension.getParent());
        assertEquals(extension, complexContent.getExtension());
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        extension.setBase("PersonType");
        XsdElement element = new XsdElement("department");
        element.setType("xs:string");
        extension.addChild(element);

        XsdExtension copy = (XsdExtension) extension.deepCopy(null);

        assertNotNull(copy);
        assertEquals(extension.getBase(), copy.getBase());
        assertEquals(extension.getName(), copy.getName());
        assertNotSame(extension, copy);
        assertNotEquals(extension.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should not change extension name")
    void testDeepCopyWithSuffix() {
        extension.setBase("BaseType");

        XsdExtension copy = (XsdExtension) extension.deepCopy("_Copy");

        // Extension name is always "extension", suffix should not be applied
        assertEquals("extension", copy.getName());
        assertEquals(extension.getBase(), copy.getBase());
    }

    @Test
    @DisplayName("deepCopy() should copy base property")
    void testDeepCopyBaseProperty() {
        extension.setBase("PersonType");

        XsdExtension copy = (XsdExtension) extension.deepCopy(null);

        assertEquals("PersonType", copy.getBase());
    }

    @Test
    @DisplayName("deepCopy() should copy children")
    void testDeepCopyCopiesChildren() {
        extension.setBase("PersonType");
        XsdElement element = new XsdElement("department");
        element.setType("xs:string");
        XsdAttribute attr = new XsdAttribute("id");
        attr.setType("xs:string");

        extension.addChild(element);
        extension.addChild(attr);

        XsdExtension copy = (XsdExtension) extension.deepCopy(null);

        assertEquals(2, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdElement);
        assertTrue(copy.getChildren().get(1) instanceof XsdAttribute);
        assertNotSame(element, copy.getChildren().get(0));
        assertNotSame(attr, copy.getChildren().get(1));
    }

    @Test
    @DisplayName("deepCopy() should handle null base")
    void testDeepCopyWithNullBase() {
        // extension has null base
        XsdExtension copy = (XsdExtension) extension.deepCopy(null);

        assertNotNull(copy);
        assertNull(copy.getBase());
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("extension extending a simple type")
    void testExtendingSimpleType() {
        // SimpleContent extension: base type + attributes
        XsdSimpleContent simpleContent = new XsdSimpleContent();

        XsdExtension ext = new XsdExtension("xs:string");
        XsdAttribute langAttr = new XsdAttribute("lang");
        langAttr.setType("xs:string");
        ext.addChild(langAttr);

        simpleContent.addChild(ext);

        assertEquals("xs:string", ext.getBase());
        assertEquals(1, ext.getChildren().size());
    }

    @Test
    @DisplayName("extension extending a complex type")
    void testExtendingComplexType() {
        // ComplexContent extension: base type + new elements
        XsdComplexContent complexContent = new XsdComplexContent();

        XsdExtension ext = new XsdExtension("PersonType");
        XsdSequence sequence = new XsdSequence();
        XsdElement deptElement = new XsdElement("department");
        deptElement.setType("xs:string");
        sequence.addChild(deptElement);
        ext.addChild(sequence);

        complexContent.addChild(ext);

        assertEquals("PersonType", ext.getBase());
        assertEquals(1, ext.getChildren().size());
        assertTrue(ext.getChildren().get(0) instanceof XsdSequence);
    }

    @Test
    @DisplayName("extension with sequence and attributes")
    void testExtensionWithSequenceAndAttributes() {
        extension.setBase("PersonType");

        XsdSequence sequence = new XsdSequence();
        XsdElement element = new XsdElement("department");
        element.setType("xs:string");
        sequence.addChild(element);

        XsdAttribute attr = new XsdAttribute("employeeId");
        attr.setType("xs:string");

        extension.addChild(sequence);
        extension.addChild(attr);

        assertEquals(2, extension.getChildren().size());
        assertEquals("PersonType", extension.getBase());
    }

    // ========== Realistic XSD Examples ==========

    @Test
    @DisplayName("create employee type extending person type")
    void testEmployeeTypeExample() {
        // <xs:complexType name="EmployeeType">
        //   <xs:complexContent>
        //     <xs:extension base="PersonType">
        //       <xs:sequence>
        //         <xs:element name="department" type="xs:string"/>
        //         <xs:element name="salary" type="xs:decimal"/>
        //       </xs:sequence>
        //       <xs:attribute name="employeeId" type="xs:string"/>
        //     </xs:extension>
        //   </xs:complexContent>
        // </xs:complexType>

        XsdExtension ext = new XsdExtension("PersonType");

        XsdSequence sequence = new XsdSequence();
        XsdElement dept = new XsdElement("department");
        dept.setType("xs:string");
        XsdElement salary = new XsdElement("salary");
        salary.setType("xs:decimal");
        sequence.addChild(dept);
        sequence.addChild(salary);

        XsdAttribute empId = new XsdAttribute("employeeId");
        empId.setType("xs:string");

        ext.addChild(sequence);
        ext.addChild(empId);

        assertEquals("PersonType", ext.getBase());
        assertEquals(2, ext.getChildren().size());
    }

    @Test
    @DisplayName("create price with currency attribute")
    void testPriceWithCurrencyExample() {
        // <xs:complexType name="PriceType">
        //   <xs:simpleContent>
        //     <xs:extension base="xs:decimal">
        //       <xs:attribute name="currency" type="xs:string"/>
        //     </xs:extension>
        //   </xs:simpleContent>
        // </xs:complexType>

        XsdExtension ext = new XsdExtension("xs:decimal");

        XsdAttribute currency = new XsdAttribute("currency");
        currency.setType("xs:string");
        currency.setUse("required");

        ext.addChild(currency);

        assertEquals("xs:decimal", ext.getBase());
        assertEquals(1, ext.getChildren().size());
    }

    @Test
    @DisplayName("extension represents type inheritance")
    void testExtensionSemantics() {
        // Extension means: inherit from base type and add new elements/attributes
        XsdExtension ext = new XsdExtension("BaseType");

        // Adding new content to the base type
        XsdElement newElement = new XsdElement("additionalField");
        newElement.setType("xs:string");
        ext.addChild(newElement);

        assertEquals("BaseType", ext.getBase());
        assertEquals(1, ext.getChildren().size());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> {
            if ("base".equals(evt.getPropertyName())) {
                eventCount[0]++;
            }
        };

        extension.addPropertyChangeListener(listener);
        extension.setBase("xs:string");
        extension.setBase("xs:integer");
        extension.setBase("PersonType");

        assertEquals(3, eventCount[0]);
    }

    @Test
    @DisplayName("setBase() with different value should fire event")
    void testSetBaseDifferentValue() {
        extension.setBase("xs:string");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("base", evt.getPropertyName());
            assertEquals("xs:string", evt.getOldValue());
            assertEquals("xs:integer", evt.getNewValue());
            eventFired.set(true);
        };

        extension.addPropertyChangeListener(listener);
        extension.setBase("xs:integer");

        assertTrue(eventFired.get(), "Event should fire when value changes");
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        extension.setBase("PersonType");
        String toString = extension.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("extension name should always be 'extension'")
    void testExtensionNameAlwaysExtension() {
        assertEquals("extension", extension.getName());

        extension.setBase("PersonType");
        assertEquals("extension", extension.getName());

        XsdExtension another = new XsdExtension("BaseType");
        assertEquals("extension", another.getName());
    }

    @Test
    @DisplayName("extension with namespace prefix in base")
    void testExtensionWithNamespacePrefix() {
        extension.setBase("tns:PersonType");
        assertEquals("tns:PersonType", extension.getBase());

        extension.setBase("xs:string");
        assertEquals("xs:string", extension.getBase());
    }

    @Test
    @DisplayName("extension with choice compositor")
    void testExtensionWithChoice() {
        extension.setBase("BaseType");

        XsdChoice choice = new XsdChoice();
        XsdElement element1 = new XsdElement("option1");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("option2");
        element2.setType("xs:integer");

        choice.addChild(element1);
        choice.addChild(element2);
        extension.addChild(choice);

        assertEquals(1, extension.getChildren().size());
        assertTrue(extension.getChildren().get(0) instanceof XsdChoice);
    }

    @Test
    @DisplayName("extension with all compositor")
    void testExtensionWithAll() {
        extension.setBase("BaseType");

        XsdAll all = new XsdAll();
        XsdElement element = new XsdElement("field");
        element.setType("xs:string");
        all.addChild(element);
        extension.addChild(all);

        assertEquals(1, extension.getChildren().size());
        assertTrue(extension.getChildren().get(0) instanceof XsdAll);
    }
}
