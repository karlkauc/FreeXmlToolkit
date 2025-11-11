package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdAttributeGroup.
 *
 * @since 2.0
 */
class XsdAttributeGroupTest {

    private XsdAttributeGroup attributeGroup;

    @BeforeEach
    void setUp() {
        attributeGroup = new XsdAttributeGroup();
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("default constructor should set default name")
    void testDefaultConstructor() {
        XsdAttributeGroup attrGrp = new XsdAttributeGroup();
        assertEquals("attributeGroup", attrGrp.getName());
        assertNull(attrGrp.getRef());
        assertFalse(attrGrp.isReference());
    }

    @Test
    @DisplayName("constructor with name should set custom name")
    void testConstructorWithName() {
        XsdAttributeGroup attrGrp = new XsdAttributeGroup("MetadataAttributes");
        assertEquals("MetadataAttributes", attrGrp.getName());
        assertNull(attrGrp.getRef());
        assertFalse(attrGrp.isReference());
    }

    // ========== NodeType Tests ==========

    @Test
    @DisplayName("getNodeType() should return ATTRIBUTE_GROUP")
    void testGetNodeType() {
        assertEquals(XsdNodeType.ATTRIBUTE_GROUP, attributeGroup.getNodeType());
    }

    // ========== Ref Property Tests ==========

    @Test
    @DisplayName("getRef() should return null by default")
    void testGetRefDefaultValue() {
        assertNull(attributeGroup.getRef());
    }

    @Test
    @DisplayName("setRef() should set reference")
    void testSetRef() {
        attributeGroup.setRef("CommonAttributes");
        assertEquals("CommonAttributes", attributeGroup.getRef());

        attributeGroup.setRef("tns:MetadataAttributes");
        assertEquals("tns:MetadataAttributes", attributeGroup.getRef());
    }

    @Test
    @DisplayName("setRef() should fire PropertyChangeEvent")
    void testSetRefFiresPropertyChange() {
        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("ref", evt.getPropertyName());
            assertNull(evt.getOldValue());
            assertEquals("CommonAttributes", evt.getNewValue());
            eventFired.set(true);
        };

        attributeGroup.addPropertyChangeListener(listener);
        attributeGroup.setRef("CommonAttributes");

        assertTrue(eventFired.get(), "PropertyChangeEvent should have been fired");
    }

    @Test
    @DisplayName("setRef() should allow null value")
    void testSetRefAllowsNull() {
        attributeGroup.setRef("CommonAttributes");
        assertEquals("CommonAttributes", attributeGroup.getRef());

        attributeGroup.setRef(null);
        assertNull(attributeGroup.getRef());
    }

    @Test
    @DisplayName("setRef() should accept qualified names with namespace prefix")
    void testSetRefWithNamespacePrefix() {
        attributeGroup.setRef("tns:CommonAttributes");
        assertEquals("tns:CommonAttributes", attributeGroup.getRef());

        attributeGroup.setRef("ns:MetadataAttributes");
        assertEquals("ns:MetadataAttributes", attributeGroup.getRef());
    }

    // ========== isReference() Tests ==========

    @Test
    @DisplayName("isReference() should return false when ref is null")
    void testIsReferenceWhenRefIsNull() {
        attributeGroup.setRef(null);
        assertFalse(attributeGroup.isReference());
    }

    @Test
    @DisplayName("isReference() should return false when ref is empty string")
    void testIsReferenceWhenRefIsEmpty() {
        attributeGroup.setRef("");
        assertFalse(attributeGroup.isReference());
    }

    @Test
    @DisplayName("isReference() should return true when ref is set")
    void testIsReferenceWhenRefIsSet() {
        attributeGroup.setRef("CommonAttributes");
        assertTrue(attributeGroup.isReference());
    }

    @Test
    @DisplayName("isReference() should return true for qualified ref")
    void testIsReferenceWithQualifiedName() {
        attributeGroup.setRef("tns:CommonAttributes");
        assertTrue(attributeGroup.isReference());
    }

    // ========== Attribute Children Tests ==========

    @Test
    @DisplayName("attributeGroup should support attribute children")
    void testAttributeChildren() {
        XsdAttribute attr1 = new XsdAttribute("created");
        attr1.setType("xs:dateTime");
        XsdAttribute attr2 = new XsdAttribute("modified");
        attr2.setType("xs:dateTime");

        attributeGroup.addChild(attr1);
        attributeGroup.addChild(attr2);

        assertEquals(2, attributeGroup.getChildren().size());
        assertTrue(attributeGroup.getChildren().get(0) instanceof XsdAttribute);
        assertTrue(attributeGroup.getChildren().get(1) instanceof XsdAttribute);
    }

    @Test
    @DisplayName("attributeGroup should support multiple attributes")
    void testMultipleAttributes() {
        XsdAttribute id = new XsdAttribute("id");
        id.setType("xs:string");
        XsdAttribute version = new XsdAttribute("version");
        version.setType("xs:string");
        XsdAttribute lang = new XsdAttribute("lang");
        lang.setType("xs:string");

        attributeGroup.addChild(id);
        attributeGroup.addChild(version);
        attributeGroup.addChild(lang);

        assertEquals(3, attributeGroup.getChildren().size());
    }

    @Test
    @DisplayName("attributeGroup definition should have attributes, not ref")
    void testAttributeGroupDefinitionStructure() {
        // AttributeGroup definition: has name + attributes, no ref
        XsdAttributeGroup commonAttrs = new XsdAttributeGroup("CommonAttributes");
        XsdAttribute id = new XsdAttribute("id");
        id.setType("xs:string");
        XsdAttribute created = new XsdAttribute("created");
        created.setType("xs:dateTime");
        commonAttrs.addChild(id);
        commonAttrs.addChild(created);

        assertEquals("CommonAttributes", commonAttrs.getName());
        assertNull(commonAttrs.getRef());
        assertFalse(commonAttrs.isReference());
        assertEquals(2, commonAttrs.getChildren().size());
    }

    @Test
    @DisplayName("attributeGroup reference should have ref, not attributes")
    void testAttributeGroupReferenceStructure() {
        // AttributeGroup reference: has ref, no children
        XsdAttributeGroup commonAttrsRef = new XsdAttributeGroup();
        commonAttrsRef.setRef("CommonAttributes");

        assertNotNull(commonAttrsRef.getRef());
        assertTrue(commonAttrsRef.isReference());
        assertEquals(0, commonAttrsRef.getChildren().size());
    }

    // ========== Parent-Child Relationship Tests ==========

    @Test
    @DisplayName("attributeGroup should support parent-child relationships")
    void testParentChildRelationships() {
        XsdComplexType parent = new XsdComplexType("PersonType");

        attributeGroup.setRef("CommonAttributes");
        parent.addChild(attributeGroup);

        assertEquals(parent, attributeGroup.getParent());
        assertTrue(parent.getChildren().contains(attributeGroup));
    }

    @Test
    @DisplayName("attributeGroup can be child of complexType")
    void testAttributeGroupInComplexType() {
        XsdComplexType complexType = new XsdComplexType("PersonType");
        XsdAttributeGroup attrGroupRef = new XsdAttributeGroup();
        attrGroupRef.setRef("CommonAttributes");

        complexType.addChild(attrGroupRef);

        assertEquals(1, complexType.getChildren().size());
        assertEquals(attrGroupRef, complexType.getChildren().get(0));
        assertEquals(complexType, attrGroupRef.getParent());
    }

    @Test
    @DisplayName("attributeGroup can be child of extension")
    void testAttributeGroupInExtension() {
        XsdExtension extension = new XsdExtension("BaseType");
        XsdAttributeGroup attrGroupRef = new XsdAttributeGroup();
        attrGroupRef.setRef("MetadataAttributes");

        extension.addChild(attrGroupRef);

        assertEquals(1, extension.getChildren().size());
        assertEquals(attrGroupRef, extension.getChildren().get(0));
    }

    // ========== DeepCopy Tests ==========

    @Test
    @DisplayName("deepCopy() should create independent copy")
    void testDeepCopy() {
        attributeGroup.setName("CommonAttributes");
        attributeGroup.setRef("BaseAttributes");

        XsdAttributeGroup copy = (XsdAttributeGroup) attributeGroup.deepCopy(null);

        assertNotNull(copy);
        assertEquals(attributeGroup.getName(), copy.getName());
        assertEquals(attributeGroup.getRef(), copy.getRef());
        assertNotSame(attributeGroup, copy);
        assertNotEquals(attributeGroup.getId(), copy.getId());
    }

    @Test
    @DisplayName("deepCopy() with suffix should append to name")
    void testDeepCopyWithSuffix() {
        attributeGroup.setName("CommonAttributes");
        attributeGroup.setRef("BaseAttributes");

        XsdAttributeGroup copy = (XsdAttributeGroup) attributeGroup.deepCopy("_Copy");

        assertEquals("CommonAttributes_Copy", copy.getName());
        assertEquals(attributeGroup.getRef(), copy.getRef());
        assertNotSame(attributeGroup, copy);
    }

    @Test
    @DisplayName("deepCopy() should copy ref property")
    void testDeepCopyRefProperty() {
        attributeGroup.setRef("MetadataAttributes");

        XsdAttributeGroup copy = (XsdAttributeGroup) attributeGroup.deepCopy(null);

        assertEquals("MetadataAttributes", copy.getRef());
        assertTrue(copy.isReference());
    }

    @Test
    @DisplayName("deepCopy() should handle null ref")
    void testDeepCopyWithNullRef() {
        attributeGroup.setName("CommonAttributes");
        // ref is null

        XsdAttributeGroup copy = (XsdAttributeGroup) attributeGroup.deepCopy(null);

        assertNotNull(copy);
        assertNull(copy.getRef());
        assertFalse(copy.isReference());
    }

    @Test
    @DisplayName("deepCopy() should copy attribute children")
    void testDeepCopyCopiesChildren() {
        attributeGroup.setName("CommonAttributes");
        XsdAttribute id = new XsdAttribute("id");
        id.setType("xs:string");
        XsdAttribute version = new XsdAttribute("version");
        version.setType("xs:string");
        attributeGroup.addChild(id);
        attributeGroup.addChild(version);

        XsdAttributeGroup copy = (XsdAttributeGroup) attributeGroup.deepCopy(null);

        assertEquals(2, copy.getChildren().size());
        assertTrue(copy.getChildren().get(0) instanceof XsdAttribute);
        assertTrue(copy.getChildren().get(1) instanceof XsdAttribute);
        assertNotSame(id, copy.getChildren().get(0));
        assertNotSame(version, copy.getChildren().get(1));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("attributeGroup definition with attributes")
    void testAttributeGroupDefinitionWithAttributes() {
        XsdAttributeGroup commonAttrs = new XsdAttributeGroup("CommonAttributes");

        XsdAttribute id = new XsdAttribute("id");
        id.setType("xs:string");
        id.setUse("required");

        XsdAttribute created = new XsdAttribute("created");
        created.setType("xs:dateTime");

        XsdAttribute modified = new XsdAttribute("modified");
        modified.setType("xs:dateTime");

        commonAttrs.addChild(id);
        commonAttrs.addChild(created);
        commonAttrs.addChild(modified);

        assertEquals("CommonAttributes", commonAttrs.getName());
        assertFalse(commonAttrs.isReference());
        assertEquals(3, commonAttrs.getChildren().size());
    }

    @Test
    @DisplayName("attributeGroup reference usage")
    void testAttributeGroupReferenceUsage() {
        // Using an attribute group reference in a complex type
        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();
        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);
        personType.addChild(sequence);

        XsdAttributeGroup commonAttrsRef = new XsdAttributeGroup();
        commonAttrsRef.setRef("CommonAttributes");
        personType.addChild(commonAttrsRef);

        assertTrue(commonAttrsRef.isReference());
        assertEquals("CommonAttributes", commonAttrsRef.getRef());
        assertEquals(2, personType.getChildren().size());
    }

    // ========== Realistic XSD Examples ==========

    @Test
    @DisplayName("create common attributes group definition")
    void testCommonAttributesGroupDefinition() {
        // <xs:attributeGroup name="CommonAttributes">
        //   <xs:attribute name="id" type="xs:string" use="required"/>
        //   <xs:attribute name="version" type="xs:string"/>
        //   <xs:attribute name="lang" type="xs:string"/>
        // </xs:attributeGroup>

        XsdAttributeGroup commonAttrs = new XsdAttributeGroup("CommonAttributes");

        XsdAttribute id = new XsdAttribute("id");
        id.setType("xs:string");
        id.setUse("required");

        XsdAttribute version = new XsdAttribute("version");
        version.setType("xs:string");

        XsdAttribute lang = new XsdAttribute("lang");
        lang.setType("xs:string");

        commonAttrs.addChild(id);
        commonAttrs.addChild(version);
        commonAttrs.addChild(lang);

        assertEquals("CommonAttributes", commonAttrs.getName());
        assertFalse(commonAttrs.isReference());
        assertEquals(3, commonAttrs.getChildren().size());
    }

    @Test
    @DisplayName("use attribute group reference in complex type")
    void testAttributeGroupReferenceInComplexType() {
        // <xs:complexType name="PersonType">
        //   <xs:sequence>
        //     <xs:element name="name" type="xs:string"/>
        //   </xs:sequence>
        //   <xs:attributeGroup ref="CommonAttributes"/>
        // </xs:complexType>

        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();
        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);
        personType.addChild(sequence);

        XsdAttributeGroup commonAttrsRef = new XsdAttributeGroup();
        commonAttrsRef.setRef("CommonAttributes");
        personType.addChild(commonAttrsRef);

        assertEquals(2, personType.getChildren().size());
        assertTrue(personType.getChildren().get(1) instanceof XsdAttributeGroup);
        assertTrue(commonAttrsRef.isReference());
    }

    @Test
    @DisplayName("metadata attributes group")
    void testMetadataAttributesGroup() {
        // <xs:attributeGroup name="MetadataAttributes">
        //   <xs:attribute name="created" type="xs:dateTime"/>
        //   <xs:attribute name="createdBy" type="xs:string"/>
        //   <xs:attribute name="modified" type="xs:dateTime"/>
        //   <xs:attribute name="modifiedBy" type="xs:string"/>
        // </xs:attributeGroup>

        XsdAttributeGroup metadataAttrs = new XsdAttributeGroup("MetadataAttributes");

        XsdAttribute created = new XsdAttribute("created");
        created.setType("xs:dateTime");
        XsdAttribute createdBy = new XsdAttribute("createdBy");
        createdBy.setType("xs:string");
        XsdAttribute modified = new XsdAttribute("modified");
        modified.setType("xs:dateTime");
        XsdAttribute modifiedBy = new XsdAttribute("modifiedBy");
        modifiedBy.setType("xs:string");

        metadataAttrs.addChild(created);
        metadataAttrs.addChild(createdBy);
        metadataAttrs.addChild(modified);
        metadataAttrs.addChild(modifiedBy);

        assertEquals("MetadataAttributes", metadataAttrs.getName());
        assertEquals(4, metadataAttrs.getChildren().size());
    }

    @Test
    @DisplayName("attributeGroup allows reusability")
    void testAttributeGroupReusability() {
        // Attribute groups enable reusing the same attributes in multiple types
        XsdAttributeGroup commonAttrs = new XsdAttributeGroup("CommonAttributes");
        // ... (definition with attributes)

        // Used in PersonType
        XsdAttributeGroup commonAttrsRef1 = new XsdAttributeGroup();
        commonAttrsRef1.setRef("CommonAttributes");

        // Used in CompanyType
        XsdAttributeGroup commonAttrsRef2 = new XsdAttributeGroup();
        commonAttrsRef2.setRef("CommonAttributes");

        assertEquals(commonAttrsRef1.getRef(), commonAttrsRef2.getRef());
        assertTrue(commonAttrsRef1.isReference());
        assertTrue(commonAttrsRef2.isReference());
    }

    @Test
    @DisplayName("attributeGroup in extension with sequence - realistic example")
    void testAttributeGroupInExtensionWithSequence() {
        // <xs:complexType name="EmployeeType">
        //   <xs:complexContent>
        //     <xs:extension base="PersonType">
        //       <xs:sequence>
        //         <xs:element name="department" type="xs:string"/>
        //       </xs:sequence>
        //       <xs:attributeGroup ref="MetadataAttributes"/>
        //     </xs:extension>
        //   </xs:complexContent>
        // </xs:complexType>

        XsdExtension extension = new XsdExtension("PersonType");

        XsdSequence sequence = new XsdSequence();
        XsdElement dept = new XsdElement("department");
        dept.setType("xs:string");
        sequence.addChild(dept);
        extension.addChild(sequence);

        XsdAttributeGroup metadataAttrsRef = new XsdAttributeGroup();
        metadataAttrsRef.setRef("MetadataAttributes");
        extension.addChild(metadataAttrsRef);

        assertEquals(2, extension.getChildren().size());
        assertTrue(extension.getChildren().get(1) instanceof XsdAttributeGroup);
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

        attributeGroup.addPropertyChangeListener(listener);
        attributeGroup.setRef("AttrGroup1");
        attributeGroup.setRef("AttrGroup2");
        attributeGroup.setRef("AttrGroup3");

        assertEquals(3, eventCount[0]);
    }

    @Test
    @DisplayName("setRef() with different value should fire event")
    void testSetRefDifferentValue() {
        attributeGroup.setRef("AttrGroup1");

        AtomicBoolean eventFired = new AtomicBoolean(false);
        PropertyChangeListener listener = evt -> {
            assertEquals("ref", evt.getPropertyName());
            assertEquals("AttrGroup1", evt.getOldValue());
            assertEquals("AttrGroup2", evt.getNewValue());
            eventFired.set(true);
        };

        attributeGroup.addPropertyChangeListener(listener);
        attributeGroup.setRef("AttrGroup2");

        assertTrue(eventFired.get(), "Event should fire when value changes");
    }

    @Test
    @DisplayName("toString() should contain type information")
    void testToString() {
        attributeGroup.setName("CommonAttributes");
        attributeGroup.setRef("BaseAttributes");
        String toString = attributeGroup.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
    }

    @Test
    @DisplayName("attributeGroup reference with namespace prefix")
    void testAttributeGroupReferenceWithNamespace() {
        attributeGroup.setRef("tns:CommonAttributes");
        assertEquals("tns:CommonAttributes", attributeGroup.getRef());
        assertTrue(attributeGroup.isReference());
    }

    @Test
    @DisplayName("attributeGroup definition vs reference distinction")
    void testDefinitionVsReferenceSemantics() {
        // Definition: has name, has attribute children
        XsdAttributeGroup definition = new XsdAttributeGroup("CommonAttributes");
        XsdAttribute attr = new XsdAttribute("id");
        attr.setType("xs:string");
        definition.addChild(attr);

        assertFalse(definition.isReference());
        assertEquals(1, definition.getChildren().size());

        // Reference: has ref attribute, no children
        XsdAttributeGroup reference = new XsdAttributeGroup();
        reference.setRef("CommonAttributes");

        assertTrue(reference.isReference());
        assertEquals(0, reference.getChildren().size());

        // They represent different things even though same class
        assertNotEquals(definition.isReference(), reference.isReference());
    }

    @Test
    @DisplayName("empty attributeGroup is valid")
    void testEmptyAttributeGroup() {
        // An attribute group can be empty (no attributes)
        XsdAttributeGroup emptyGroup = new XsdAttributeGroup("EmptyGroup");

        assertEquals("EmptyGroup", emptyGroup.getName());
        assertEquals(0, emptyGroup.getChildren().size());
        assertFalse(emptyGroup.isReference());
    }
}
