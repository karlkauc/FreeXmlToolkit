package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdComplexType.
 * Tests both new ComplexContent/SimpleContent support and backwards-compatible direct compositor support.
 *
 * @since 2.0
 */
class XsdComplexTypeTest {

    private XsdComplexType complexType;

    @BeforeEach
    void setUp() {
        complexType = new XsdComplexType("TestComplexType");
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("constructor should set name")
    void testConstructor() {
        XsdComplexType type = new XsdComplexType("PersonType");
        assertEquals("PersonType", type.getName());
        assertFalse(type.isMixed());
        assertFalse(type.isAbstract());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return COMPLEX_TYPE")
    void testGetNodeType() {
        assertEquals(XsdNodeType.COMPLEX_TYPE, complexType.getNodeType());
    }

    // ========== Mixed Property Tests ==========

    @Test
    @DisplayName("isMixed() should be false by default")
    void testIsMixedDefaultValue() {
        assertFalse(complexType.isMixed());
    }

    @Test
    @DisplayName("setMixed() should set mixed flag")
    void testSetMixed() {
        complexType.setMixed(true);
        assertTrue(complexType.isMixed());

        complexType.setMixed(false);
        assertFalse(complexType.isMixed());
    }

    @Test
    @DisplayName("setMixed() should fire PropertyChangeEvent")
    void testSetMixedFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("mixed", evt.getPropertyName());
            assertEquals(false, evt.getOldValue());
            assertEquals(true, evt.getNewValue());
            eventFired.set(true);
        };

        complexType.addPropertyChangeListener(listener);
        complexType.setMixed(true);

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    // ========== Abstract Property Tests ==========

    @Test
    @DisplayName("isAbstract() should be false by default")
    void testIsAbstractDefaultValue() {
        assertFalse(complexType.isAbstract());
    }

    @Test
    @DisplayName("setAbstract() should set abstract flag")
    void testSetAbstract() {
        complexType.setAbstract(true);
        assertTrue(complexType.isAbstract());

        complexType.setAbstract(false);
        assertFalse(complexType.isAbstract());
    }

    @Test
    @DisplayName("setAbstract() should fire PropertyChangeEvent")
    void testSetAbstractFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("abstract", evt.getPropertyName());
            assertEquals(false, evt.getOldValue());
            assertEquals(true, evt.getNewValue());
            eventFired.set(true);
        };

        complexType.addPropertyChangeListener(listener);
        complexType.setAbstract(true);

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    // ========== ComplexContent Support Tests ==========

    @Test
    @DisplayName("getComplexContent() should return null when no complexContent child")
    void testGetComplexContentNull() {
        assertNull(complexType.getComplexContent());
    }

    @Test
    @DisplayName("getComplexContent() should return complexContent child when present")
    void testGetComplexContentPresent() {
        XsdComplexContent complexContent = new XsdComplexContent();
        complexType.addChild(complexContent);

        XsdComplexContent found = complexType.getComplexContent();
        assertNotNull(found);
        assertEquals(complexContent, found);
    }

    @Test
    @DisplayName("complexType with complexContent and extension")
    void testComplexTypeWithComplexContentExtension() {
        XsdComplexContent complexContent = new XsdComplexContent();
        XsdExtension extension = new XsdExtension("PersonType");
        complexContent.addChild(extension);
        complexType.addChild(complexContent);

        assertNotNull(complexType.getComplexContent());
        assertNotNull(complexType.getComplexContent().getExtension());
        assertEquals("PersonType", complexType.getComplexContent().getExtension().getBase());
    }

    @Test
    @DisplayName("complexType with complexContent and restriction")
    void testComplexTypeWithComplexContentRestriction() {
        XsdComplexContent complexContent = new XsdComplexContent();
        XsdRestriction restriction = new XsdRestriction("PersonType");
        complexContent.addChild(restriction);
        complexType.addChild(complexContent);

        assertNotNull(complexType.getComplexContent());
        assertNotNull(complexType.getComplexContent().getRestriction());
        assertEquals("PersonType", complexType.getComplexContent().getRestriction().getBase());
    }

    // ========== SimpleContent Support Tests ==========

    @Test
    @DisplayName("getSimpleContent() should return null when no simpleContent child")
    void testGetSimpleContentNull() {
        assertNull(complexType.getSimpleContent());
    }

    @Test
    @DisplayName("getSimpleContent() should return simpleContent child when present")
    void testGetSimpleContentPresent() {
        XsdSimpleContent simpleContent = new XsdSimpleContent();
        complexType.addChild(simpleContent);

        XsdSimpleContent found = complexType.getSimpleContent();
        assertNotNull(found);
        assertEquals(simpleContent, found);
    }

    @Test
    @DisplayName("complexType with simpleContent and extension")
    void testComplexTypeWithSimpleContentExtension() {
        XsdSimpleContent simpleContent = new XsdSimpleContent();
        XsdExtension extension = new XsdExtension("xs:string");
        simpleContent.addChild(extension);
        complexType.addChild(simpleContent);

        assertNotNull(complexType.getSimpleContent());
        assertNotNull(complexType.getSimpleContent().getExtension());
        assertEquals("xs:string", complexType.getSimpleContent().getExtension().getBase());
    }

    @Test
    @DisplayName("complexType with simpleContent and restriction")
    void testComplexTypeWithSimpleContentRestriction() {
        XsdSimpleContent simpleContent = new XsdSimpleContent();
        XsdRestriction restriction = new XsdRestriction("xs:integer");
        simpleContent.addChild(restriction);
        complexType.addChild(simpleContent);

        assertNotNull(complexType.getSimpleContent());
        assertNotNull(complexType.getSimpleContent().getRestriction());
        assertEquals("xs:integer", complexType.getSimpleContent().getRestriction().getBase());
    }

    // ========== Backwards Compatibility: Direct Compositor Tests ==========

    @Test
    @DisplayName("getSequence() should return null when no sequence child")
    void testGetSequenceNull() {
        assertNull(complexType.getSequence());
    }

    @Test
    @DisplayName("getSequence() should return sequence child when present (backwards compatible)")
    void testGetSequencePresent() {
        XsdSequence sequence = new XsdSequence();
        complexType.addChild(sequence);

        XsdSequence found = complexType.getSequence();
        assertNotNull(found);
        assertEquals(sequence, found);
    }

    @Test
    @DisplayName("getChoice() should return null when no choice child")
    void testGetChoiceNull() {
        assertNull(complexType.getChoice());
    }

    @Test
    @DisplayName("getChoice() should return choice child when present (backwards compatible)")
    void testGetChoicePresent() {
        XsdChoice choice = new XsdChoice();
        complexType.addChild(choice);

        XsdChoice found = complexType.getChoice();
        assertNotNull(found);
        assertEquals(choice, found);
    }

    @Test
    @DisplayName("getAll() should return null when no all child")
    void testGetAllNull() {
        assertNull(complexType.getAll());
    }

    @Test
    @DisplayName("getAll() should return all child when present (backwards compatible)")
    void testGetAllPresent() {
        XsdAll all = new XsdAll();
        complexType.addChild(all);

        XsdAll found = complexType.getAll();
        assertNotNull(found);
        assertEquals(all, found);
    }

    @Test
    @DisplayName("complexType with direct sequence (backwards compatible)")
    void testComplexTypeWithDirectSequence() {
        XsdSequence sequence = new XsdSequence();
        XsdElement element1 = new XsdElement("name");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("age");
        element2.setType("xs:integer");
        sequence.addChild(element1);
        sequence.addChild(element2);
        complexType.addChild(sequence);

        assertNotNull(complexType.getSequence());
        assertEquals(2, complexType.getSequence().getChildren().size());
        assertNull(complexType.getComplexContent()); // No complexContent used
    }

    @Test
    @DisplayName("complexType with direct choice (backwards compatible)")
    void testComplexTypeWithDirectChoice() {
        XsdChoice choice = new XsdChoice();
        XsdElement element1 = new XsdElement("option1");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("option2");
        element2.setType("xs:integer");
        choice.addChild(element1);
        choice.addChild(element2);
        complexType.addChild(choice);

        assertNotNull(complexType.getChoice());
        assertEquals(2, complexType.getChoice().getChildren().size());
        assertNull(complexType.getComplexContent()); // No complexContent used
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        complexType.setMixed(true);
        complexType.setAbstract(true);
        XsdSequence sequence = new XsdSequence();
        complexType.addChild(sequence);

        XsdComplexType copy = (XsdComplexType) complexType.deepCopy(null);

        assertNotNull(copy);
        assertEquals(complexType.getName(), copy.getName());
        assertEquals(complexType.isMixed(), copy.isMixed());
        assertEquals(complexType.isAbstract(), copy.isAbstract());
        assertNotSame(complexType, copy);
        assertNotEquals(complexType.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should append suffix to name")
    void testDeepCopyWithSuffix() {
        complexType.setMixed(true);

        XsdComplexType copy = (XsdComplexType) complexType.deepCopy("_Copy");

        assertEquals("TestComplexType_Copy", copy.getName());
        assertEquals(complexType.isMixed(), copy.isMixed());
    }

    @Test
    @DisplayName("deepCopy() should copy children")
    void testDeepCopyCopiesChildren() {
        XsdSequence sequence = new XsdSequence();
        XsdElement element = new XsdElement("name");
        element.setType("xs:string");
        sequence.addChild(element);
        complexType.addChild(sequence);

        XsdComplexType copy = (XsdComplexType) complexType.deepCopy(null);

        assertEquals(1, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdSequence);
        assertNotSame(sequence, copy.getChildren().get(0));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("complexType should work in schema context")
    void testComplexTypeInSchema() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");

        XsdComplexType personType = new XsdComplexType("PersonType");
        schema.addChild(personType);

        assertEquals(schema, personType.getParent());
        assertEquals(1, schema.getChildren().size());
    }

    // ========== Realistic XSD Examples ==========

    @Test
    @DisplayName("create PersonType with direct sequence (simple, backwards compatible)")
    void testPersonTypeSimple() {
        // <xs:complexType name="PersonType">
        //   <xs:sequence>
        //     <xs:element name="name" type="xs:string"/>
        //     <xs:element name="age" type="xs:integer"/>
        //   </xs:sequence>
        // </xs:complexType>

        XsdComplexType personType = new XsdComplexType("PersonType");

        XsdSequence sequence = new XsdSequence();
        XsdElement nameElement = new XsdElement("name");
        nameElement.setType("xs:string");
        XsdElement ageElement = new XsdElement("age");
        ageElement.setType("xs:integer");

        sequence.addChild(nameElement);
        sequence.addChild(ageElement);
        personType.addChild(sequence);

        assertNotNull(personType.getSequence());
        assertEquals(2, personType.getSequence().getChildren().size());
    }

    @Test
    @DisplayName("create EmployeeType extending PersonType")
    void testEmployeeTypeExtendingPerson() {
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

        XsdComplexType employeeType = new XsdComplexType("EmployeeType");

        XsdComplexContent complexContent = new XsdComplexContent();
        XsdExtension extension = new XsdExtension("PersonType");

        XsdSequence sequence = new XsdSequence();
        XsdElement deptElement = new XsdElement("department");
        deptElement.setType("xs:string");
        XsdElement salaryElement = new XsdElement("salary");
        salaryElement.setType("xs:decimal");
        sequence.addChild(deptElement);
        sequence.addChild(salaryElement);

        XsdAttribute empIdAttr = new XsdAttribute("employeeId");
        empIdAttr.setType("xs:string");

        extension.addChild(sequence);
        extension.addChild(empIdAttr);
        complexContent.addChild(extension);
        employeeType.addChild(complexContent);

        assertNotNull(employeeType.getComplexContent());
        assertNotNull(employeeType.getComplexContent().getExtension());
        assertEquals("PersonType", employeeType.getComplexContent().getExtension().getBase());
    }

    @Test
    @DisplayName("create PriceType with simpleContent and attribute")
    void testPriceTypeWithSimpleContent() {
        // <xs:complexType name="PriceType">
        //   <xs:simpleContent>
        //     <xs:extension base="xs:decimal">
        //       <xs:attribute name="currency" type="xs:string" use="required"/>
        //     </xs:extension>
        //   </xs:simpleContent>
        // </xs:complexType>

        XsdComplexType priceType = new XsdComplexType("PriceType");

        XsdSimpleContent simpleContent = new XsdSimpleContent();
        XsdExtension extension = new XsdExtension("xs:decimal");

        XsdAttribute currencyAttr = new XsdAttribute("currency");
        currencyAttr.setType("xs:string");
        currencyAttr.setUse("required");

        extension.addChild(currencyAttr);
        simpleContent.addChild(extension);
        priceType.addChild(simpleContent);

        assertNotNull(priceType.getSimpleContent());
        assertNotNull(priceType.getSimpleContent().getExtension());
        assertEquals("xs:decimal", priceType.getSimpleContent().getExtension().getBase());
    }

    @Test
    @DisplayName("create mixed content type")
    void testMixedContentType() {
        // <xs:complexType name="MixedType" mixed="true">
        //   <xs:sequence>
        //     <xs:element name="bold" type="xs:string" minOccurs="0" maxOccurs="unbounded"/>
        //   </xs:sequence>
        // </xs:complexType>

        XsdComplexType mixedType = new XsdComplexType("MixedType");
        mixedType.setMixed(true);

        XsdSequence sequence = new XsdSequence();
        XsdElement boldElement = new XsdElement("bold");
        boldElement.setType("xs:string");
        boldElement.setMinOccurs(0);
        boldElement.setMaxOccurs(XsdNode.UNBOUNDED);
        sequence.addChild(boldElement);

        mixedType.addChild(sequence);

        assertTrue(mixedType.isMixed());
        assertNotNull(mixedType.getSequence());
    }

    @Test
    @DisplayName("create abstract base type")
    void testAbstractBaseType() {
        // <xs:complexType name="AbstractPersonType" abstract="true">
        //   <xs:sequence>
        //     <xs:element name="name" type="xs:string"/>
        //   </xs:sequence>
        // </xs:complexType>

        XsdComplexType abstractType = new XsdComplexType("AbstractPersonType");
        abstractType.setAbstract(true);

        XsdSequence sequence = new XsdSequence();
        XsdElement nameElement = new XsdElement("name");
        nameElement.setType("xs:string");
        sequence.addChild(nameElement);

        abstractType.addChild(sequence);

        assertTrue(abstractType.isAbstract());
        assertNotNull(abstractType.getSequence());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> eventCount[0]++;

        complexType.addPropertyChangeListener(listener);
        complexType.setMixed(true);
        complexType.setAbstract(true);
        complexType.setMixed(false);

        assertEquals(3, eventCount[0]);
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        complexType.setMixed(true);
        String toString = complexType.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("complexType should not have both complexContent and simpleContent")
    void testMutuallyExclusiveContent() {
        // XSD rule: complexType can have either complexContent OR simpleContent, not both
        XsdComplexContent complexContent = new XsdComplexContent();
        complexType.addChild(complexContent);

        assertNotNull(complexType.getComplexContent());
        assertNull(complexType.getSimpleContent());

        // If we add simpleContent (which shouldn't happen in practice),
        // both getters would work but only one should be used
        XsdSimpleContent simpleContent = new XsdSimpleContent();
        complexType.addChild(simpleContent);

        assertNotNull(complexType.getComplexContent());
        assertNotNull(complexType.getSimpleContent());
    }

    @Test
    @DisplayName("backwards compatibility: direct compositor and attributes")
    void testBackwardsCompatibilityWithAttributes() {
        // Old style: complexType with direct sequence + attributes
        XsdSequence sequence = new XsdSequence();
        XsdElement element = new XsdElement("name");
        element.setType("xs:string");
        sequence.addChild(element);

        XsdAttribute attr = new XsdAttribute("id");
        attr.setType("xs:string");

        complexType.addChild(sequence);
        complexType.addChild(attr);

        assertNotNull(complexType.getSequence());
        assertEquals(2, complexType.getChildren().size());
    }
}
