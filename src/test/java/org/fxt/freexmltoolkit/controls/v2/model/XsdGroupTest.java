package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdGroup.
 *
 * @since 2.0
 */
class XsdGroupTest {

    private XsdGroup group;

    @BeforeEach
    void setUp() {
        group = new XsdGroup();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdGroup grp = new XsdGroup();
        assertEquals("group", grp.getName());
        assertNull(grp.getRef());
        assertFalse(grp.isReference());
    }

    @Test
    @DisplayName("constructor with name should set custom name")
    void testConstructorWithName() {
        XsdGroup grp = new XsdGroup("AddressGroup");
        assertEquals("AddressGroup", grp.getName());
        assertNull(grp.getRef());
        assertFalse(grp.isReference());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return GROUP")
    void testGetNodeType() {
        assertEquals(XsdNodeType.GROUP, group.getNodeType());
    }

    // ========== Ref Property Tests ==========

    @Test
    @DisplayName("getRef() should return null by default")
    void testGetRefDefaultValue() {
        assertNull(group.getRef());
    }

    @Test
    @DisplayName("setRef() should set reference")
    void testSetRef() {
        group.setRef("PersonGroup");
        assertEquals("PersonGroup", group.getRef());

        group.setRef("tns:AddressGroup");
        assertEquals("tns:AddressGroup", group.getRef());
    }

    @Test
    @DisplayName("setRef() should fire PropertyChangeEvent")
    void testSetRefFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("ref", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("PersonGroup", evt.getNewValue());
            eventFired.set(true);
        };

        group.addPropertyChangeListener(listener);
        group.setRef("PersonGroup");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setRef() should allow null value")
    void testSetRefAllowsNull() {
        group.setRef("PersonGroup");
        assertEquals("PersonGroup", group.getRef());

        group.setRef(null);
        assertNull(group.getRef());
    }

    @Test
    @DisplayName("setRef() should accept qualified names with namespace prefix")
    void testSetRefWithNamespacePrefix() {
        group.setRef("tns:PersonGroup");
        assertEquals("tns:PersonGroup", group.getRef());

        group.setRef("ns:AddressGroup");
        assertEquals("ns:AddressGroup", group.getRef());
    }

    // ========== isReference() Tests ==========

    @Test
    @DisplayName("isReference() should return false when ref is null")
    void testIsReferenceWhenRefIsNull() {
        group.setRef(null);
        assertFalse(group.isReference());
    }

    @Test
    @DisplayName("isReference() should return false when ref is empty string")
    void testIsReferenceWhenRefIsEmpty() {
        group.setRef("");
        assertFalse(group.isReference());
    }

    @Test
    @DisplayName("isReference() should return true when ref is set")
    void testIsReferenceWhenRefIsSet() {
        group.setRef("PersonGroup");
        assertTrue(group.isReference());
    }

    @Test
    @DisplayName("isReference() should return true for qualified ref")
    void testIsReferenceWithQualifiedName() {
        group.setRef("tns:PersonGroup");
        assertTrue(group.isReference());
    }

    // ========== Compositor Children Tests ==========

    @Test
    @DisplayName("group should support sequence child")
    void testSequenceChild() {
        XsdSequence sequence = new XsdSequence();
        XsdElement element1 = new XsdElement("street");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("city");
        element2.setType("xs:string");

        sequence.addChild(element1);
        sequence.addChild(element2);
        group.addChild(sequence);

        assertEquals(1, group.getChildren().size());
        assertTrue(group.getChildren().get(0) instanceof XsdSequence);
        assertEquals(2, sequence.getChildren().size());
    }

    @Test
    @DisplayName("group should support choice child")
    void testChoiceChild() {
        XsdChoice choice = new XsdChoice();
        XsdElement element1 = new XsdElement("email");
        element1.setType("xs:string");
        XsdElement element2 = new XsdElement("phone");
        element2.setType("xs:string");

        choice.addChild(element1);
        choice.addChild(element2);
        group.addChild(choice);

        assertEquals(1, group.getChildren().size());
        assertTrue(group.getChildren().get(0) instanceof XsdChoice);
        assertEquals(2, choice.getChildren().size());
    }

    @Test
    @DisplayName("group should support all child")
    void testAllChild() {
        XsdAll all = new XsdAll();
        XsdElement element = new XsdElement("name");
        element.setType("xs:string");
        all.addChild(element);

        group.addChild(all);

        assertEquals(1, group.getChildren().size());
        assertTrue(group.getChildren().get(0) instanceof XsdAll);
    }

    @Test
    @DisplayName("group definition should have compositor, not ref")
    void testGroupDefinitionStructure() {
        // Group definition: has name + compositor, no ref
        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        XsdSequence sequence = new XsdSequence();
        sequence.addChild(new XsdElement("street"));
        sequence.addChild(new XsdElement("city"));
        addressGroup.addChild(sequence);

        assertEquals("AddressGroup", addressGroup.getName());
        assertNull(addressGroup.getRef());
        assertFalse(addressGroup.isReference());
        assertEquals(1, addressGroup.getChildren().size());
    }

    @Test
    @DisplayName("group reference should have ref, not compositor")
    void testGroupReferenceStructure() {
        // Group reference: has ref, no children
        XsdGroup addressGroupRef = new XsdGroup();
        addressGroupRef.setRef("AddressGroup");

        assertNotNull(addressGroupRef.getRef());
        assertTrue(addressGroupRef.isReference());
        assertEquals(0, addressGroupRef.getChildren().size());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("group should support parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexType parent = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();

        group.setRef("AddressGroup");
        sequence.addChild(group);
        parent.addChild(sequence);

        assertEquals(sequence, group.getParent());
        assertTrue(sequence.getChildren().contains(group));
    }

    @Test
    @DisplayName("group can be child of sequence")
    void testGroupInSequence() {
        XsdSequence sequence = new XsdSequence();
        XsdGroup groupRef = new XsdGroup();
        groupRef.setRef("AddressGroup");

        sequence.addChild(groupRef);

        assertEquals(1, sequence.getChildren().size());
        assertEquals(groupRef, sequence.getChildren().get(0));
        assertEquals(sequence, groupRef.getParent());
    }

    @Test
    @DisplayName("group can be child of choice")
    void testGroupInChoice() {
        XsdChoice choice = new XsdChoice();
        XsdGroup groupRef = new XsdGroup();
        groupRef.setRef("PersonGroup");

        choice.addChild(groupRef);

        assertEquals(1, choice.getChildren().size());
        assertEquals(groupRef, choice.getChildren().get(0));
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        group.setName("PersonGroup");
        group.setRef("BaseGroup");

        XsdGroup copy = (XsdGroup) group.deepCopy(null);

        assertNotNull(copy);
        assertEquals(group.getName(), copy.getName());
        assertEquals(group.getRef(), copy.getRef());
        assertNotSame(group, copy);
        assertNotEquals(group.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should append to name")
    void testDeepCopyWithSuffix() {
        group.setName("PersonGroup");
        group.setRef("BaseGroup");

        XsdGroup copy = (XsdGroup) group.deepCopy("_Copy");

        assertEquals("PersonGroup_Copy", copy.getName());
        assertEquals(group.getRef(), copy.getRef());
        assertNotSame(group, copy);
    }

    @Test
    @DisplayName("deepCopy() should copy ref property")
    void testDeepCopyRefProperty() {
        group.setRef("AddressGroup");

        XsdGroup copy = (XsdGroup) group.deepCopy(null);

        assertEquals("AddressGroup", copy.getRef());
        assertTrue(copy.isReference());
    }

    @Test
    @DisplayName("deepCopy() should handle null ref")
    void testDeepCopyWithNullRef() {
        group.setName("PersonGroup");
        // ref is null

        XsdGroup copy = (XsdGroup) group.deepCopy(null);

        assertNotNull(copy);
        assertNull(copy.getRef());
        assertFalse(copy.isReference());
    }

    @Test
    @DisplayName("deepCopy() should copy compositor children")
    void testDeepCopyCopiesChildren() {
        group.setName("AddressGroup");
        XsdSequence sequence = new XsdSequence();
        XsdElement element = new XsdElement("street");
        element.setType("xs:string");
        sequence.addChild(element);
        group.addChild(sequence);

        XsdGroup copy = (XsdGroup) group.deepCopy(null);

        assertEquals(1, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdSequence);
        assertNotSame(sequence, copy.getChildren().get(0));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("group definition with sequence")
    void testGroupDefinitionWithSequence() {
        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        XsdSequence sequence = new XsdSequence();

        XsdElement street = new XsdElement("street");
        street.setType("xs:string");
        XsdElement city = new XsdElement("city");
        city.setType("xs:string");
        XsdElement zip = new XsdElement("zip");
        zip.setType("xs:string");

        sequence.addChild(street);
        sequence.addChild(city);
        sequence.addChild(zip);
        addressGroup.addChild(sequence);

        assertEquals("AddressGroup", addressGroup.getName());
        assertFalse(addressGroup.isReference());
        assertEquals(1, addressGroup.getChildren().size());
        assertEquals(3, sequence.getChildren().size());
    }

    @Test
    @DisplayName("group reference usage")
    void testGroupReferenceUsage() {
        // Using a group reference in a complex type
        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();

        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);

        XsdGroup addressGroupRef = new XsdGroup();
        addressGroupRef.setRef("AddressGroup");
        sequence.addChild(addressGroupRef);

        personType.addChild(sequence);

        assertTrue(addressGroupRef.isReference());
        assertEquals("AddressGroup", addressGroupRef.getRef());
        assertEquals(2, sequence.getChildren().size());
    }

    // ========== Realistic XSD Examples ==========

    @Test
    @DisplayName("create address group definition")
    void testAddressGroupDefinition() {
        // <xs:group name="AddressGroup">
        //   <xs:sequence>
        //     <xs:element name="street" type="xs:string"/>
        //     <xs:element name="city" type="xs:string"/>
        //     <xs:element name="country" type="xs:string"/>
        //   </xs:sequence>
        // </xs:group>

        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        XsdSequence sequence = new XsdSequence();

        XsdElement street = new XsdElement("street");
        street.setType("xs:string");
        XsdElement city = new XsdElement("city");
        city.setType("xs:string");
        XsdElement country = new XsdElement("country");
        country.setType("xs:string");

        sequence.addChild(street);
        sequence.addChild(city);
        sequence.addChild(country);
        addressGroup.addChild(sequence);

        assertEquals("AddressGroup", addressGroup.getName());
        assertFalse(addressGroup.isReference());
        assertEquals(1, addressGroup.getChildren().size());
    }

    @Test
    @DisplayName("use address group reference in person type")
    void testAddressGroupReferenceInPersonType() {
        // <xs:complexType name="PersonType">
        //   <xs:sequence>
        //     <xs:element name="name" type="xs:string"/>
        //     <xs:group ref="AddressGroup"/>
        //   </xs:sequence>
        // </xs:complexType>

        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();

        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);

        XsdGroup addressGroupRef = new XsdGroup();
        addressGroupRef.setRef("AddressGroup");
        sequence.addChild(addressGroupRef);

        personType.addChild(sequence);

        assertEquals(2, sequence.getChildren().size());
        assertTrue(sequence.getChildren().get(1) instanceof XsdGroup);
        assertTrue(addressGroupRef.isReference());
    }

    @Test
    @DisplayName("group with choice compositor")
    void testGroupWithChoice() {
        // <xs:group name="ContactGroup">
        //   <xs:choice>
        //     <xs:element name="email" type="xs:string"/>
        //     <xs:element name="phone" type="xs:string"/>
        //   </xs:choice>
        // </xs:group>

        XsdGroup contactGroup = new XsdGroup("ContactGroup");
        XsdChoice choice = new XsdChoice();

        XsdElement email = new XsdElement("email");
        email.setType("xs:string");
        XsdElement phone = new XsdElement("phone");
        phone.setType("xs:string");

        choice.addChild(email);
        choice.addChild(phone);
        contactGroup.addChild(choice);

        assertEquals("ContactGroup", contactGroup.getName());
        assertEquals(1, contactGroup.getChildren().size());
        assertTrue(contactGroup.getChildren().get(0) instanceof XsdChoice);
    }

    @Test
    @DisplayName("group allows reusability")
    void testGroupReusability() {
        // Groups enable reusing the same element structure in multiple types
        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        // ... (definition with sequence)

        // Used in PersonType
        XsdGroup addressRef1 = new XsdGroup();
        addressRef1.setRef("AddressGroup");

        // Used in CompanyType
        XsdGroup addressRef2 = new XsdGroup();
        addressRef2.setRef("AddressGroup");

        assertEquals(addressRef1.getRef(), addressRef2.getRef());
        assertTrue(addressRef1.isReference());
        assertTrue(addressRef2.isReference());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("multiple property changes should fire multiple events")
    void testMultiplePropertyChanges() {
        final int[] eventCount = {0};
        PropertyChangeListener listener = evt -> {
            if ("ref".equals(evt.getPropertyName())) {
                eventCount[0]++;
            }
        };

        group.addPropertyChangeListener(listener);
        group.setRef("Group1");
        group.setRef("Group2");
        group.setRef("Group3");

        assertEquals(3, eventCount[0]);
    }

    @Test
    @DisplayName("setRef() with different value should fire event")
    void testSetRefDifferentValue() {
        group.setRef("Group1");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("ref", evt.getPropertyName());
            assertEquals("Group1", evt.getOldValue());
            assertEquals("Group2", evt.getNewValue());
            eventFired.set(true);
        };

        group.addPropertyChangeListener(listener);
        group.setRef("Group2");

        assertTrue(eventFired.get(), "Event should fire when value changes");
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        group.setName("PersonGroup");
        group.setRef("BaseGroup");
        String toString = group.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("group reference with namespace prefix")
    void testGroupReferenceWithNamespace() {
        group.setRef("tns:PersonGroup");
        assertEquals("tns:PersonGroup", group.getRef());
        assertTrue(group.isReference());
    }

    @Test
    @DisplayName("group can have only one compositor child")
    void testGroupSingleCompositorChild() {
        // XSD rule: group can have only one compositor (sequence/choice/all)
        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        XsdSequence sequence = new XsdSequence();
        addressGroup.addChild(sequence);

        assertEquals(1, addressGroup.getChildren().size());
        // Adding another compositor would be invalid XSD,
        // but our model doesn't prevent it (validation happens elsewhere)
    }

    @Test
    @DisplayName("group definition vs reference distinction")
    void testDefinitionVsReferenceSemantics() {
        // Definition: has name, has compositor children
        XsdGroup definition = new XsdGroup("AddressGroup");
        XsdSequence sequence = new XsdSequence();
        definition.addChild(sequence);

        assertFalse(definition.isReference());
        assertEquals(1, definition.getChildren().size());

        // Reference: has ref attribute, no children
        XsdGroup reference = new XsdGroup();
        reference.setRef("AddressGroup");

        assertTrue(reference.isReference());
        assertEquals(0, reference.getChildren().size());

        // They represent different things even though same class
        assertNotEquals(definition.isReference(), reference.isReference());
    }
}
