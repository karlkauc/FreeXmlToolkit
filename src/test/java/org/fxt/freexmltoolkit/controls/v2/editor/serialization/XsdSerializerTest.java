package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for XsdSerializer.
 * Focuses on Group and AttributeGroup serialization.
 *
 * @since 2.0
 */
class XsdSerializerTest {

    private XsdSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new XsdSerializer();
    }

    // ========== Group Definition Tests ==========

    @Test
    @DisplayName("serializeGroup() should serialize group definition with sequence")
    void testSerializeGroupDefinitionWithSequence() {
        XsdSchema schema = new XsdSchema();

        // Create group definition
        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        XsdSequence sequence = new XsdSequence();

        XsdElement street = new XsdElement("street");
        street.setType("xs:string");
        XsdElement city = new XsdElement("city");
        city.setType("xs:string");

        sequence.addChild(street);
        sequence.addChild(city);
        addressGroup.addChild(sequence);
        schema.addChild(addressGroup);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:group name=\"AddressGroup\">"));
        assertTrue(xml.contains("<xs:sequence>"));
        assertTrue(xml.contains("<xs:element name=\"street\" type=\"xs:string\"/>"));
        assertTrue(xml.contains("<xs:element name=\"city\" type=\"xs:string\"/>"));
        assertTrue(xml.contains("</xs:sequence>"));
        assertTrue(xml.contains("</xs:group>"));
    }

    @Test
    @DisplayName("serializeGroup() should serialize group definition with choice")
    void testSerializeGroupDefinitionWithChoice() {
        XsdSchema schema = new XsdSchema();

        // Create group definition with choice
        XsdGroup contactGroup = new XsdGroup("ContactGroup");
        XsdChoice choice = new XsdChoice();

        XsdElement email = new XsdElement("email");
        email.setType("xs:string");
        XsdElement phone = new XsdElement("phone");
        phone.setType("xs:string");

        choice.addChild(email);
        choice.addChild(phone);
        contactGroup.addChild(choice);
        schema.addChild(contactGroup);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:group name=\"ContactGroup\">"));
        assertTrue(xml.contains("<xs:choice>"));
        assertTrue(xml.contains("<xs:element name=\"email\" type=\"xs:string\"/>"));
        assertTrue(xml.contains("<xs:element name=\"phone\" type=\"xs:string\"/>"));
        assertTrue(xml.contains("</xs:choice>"));
        assertTrue(xml.contains("</xs:group>"));
    }

    @Test
    @DisplayName("serializeGroup() should serialize group definition with all")
    void testSerializeGroupDefinitionWithAll() {
        XsdSchema schema = new XsdSchema();

        // Create group definition with all
        XsdGroup personGroup = new XsdGroup("PersonGroup");
        XsdAll all = new XsdAll();

        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        XsdElement age = new XsdElement("age");
        age.setType("xs:integer");

        all.addChild(name);
        all.addChild(age);
        personGroup.addChild(all);
        schema.addChild(personGroup);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:group name=\"PersonGroup\">"));
        assertTrue(xml.contains("<xs:all>"));
        assertTrue(xml.contains("<xs:element name=\"name\" type=\"xs:string\"/>"));
        assertTrue(xml.contains("<xs:element name=\"age\" type=\"xs:integer\"/>"));
        assertTrue(xml.contains("</xs:all>"));
        assertTrue(xml.contains("</xs:group>"));
    }

    // ========== Group Reference Tests ==========

    @Test
    @DisplayName("serializeGroup() should serialize group reference")
    void testSerializeGroupReference() {
        XsdSchema schema = new XsdSchema();

        // Create complexType with group reference
        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();

        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);

        // Add group reference
        XsdGroup addressGroupRef = new XsdGroup();
        addressGroupRef.setRef("AddressGroup");
        sequence.addChild(addressGroupRef);

        personType.addChild(sequence);
        schema.addChild(personType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:group ref=\"AddressGroup\"/>"));
        assertFalse(xml.contains("</xs:group>")); // Self-closing for reference
    }

    @Test
    @DisplayName("serializeGroup() should serialize group reference with namespace prefix")
    void testSerializeGroupReferenceWithNamespace() {
        XsdSchema schema = new XsdSchema();

        // Create complexType with namespaced group reference
        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();

        XsdGroup groupRef = new XsdGroup();
        groupRef.setRef("tns:AddressGroup");
        sequence.addChild(groupRef);

        personType.addChild(sequence);
        schema.addChild(personType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:group ref=\"tns:AddressGroup\"/>"));
    }

    // ========== AttributeGroup Definition Tests ==========

    @Test
    @DisplayName("serializeAttributeGroup() should serialize attributeGroup definition")
    void testSerializeAttributeGroupDefinition() {
        XsdSchema schema = new XsdSchema();

        // Create attributeGroup definition
        XsdAttributeGroup commonAttrs = new XsdAttributeGroup("CommonAttributes");

        XsdAttribute id = new XsdAttribute("id");
        id.setType("xs:string");
        id.setUse("required");

        XsdAttribute version = new XsdAttribute("version");
        version.setType("xs:string");

        commonAttrs.addChild(id);
        commonAttrs.addChild(version);
        schema.addChild(commonAttrs);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:attributeGroup name=\"CommonAttributes\">"), "Should contain attributeGroup start tag");
        assertTrue(xml.contains("<xs:attribute name=\"id\" type=\"xs:string\" use=\"required\"/>"), "Should contain id attribute");
        assertTrue(xml.contains("<xs:attribute name=\"version\" type=\"xs:string\" use=\"optional\"/>"), "Should contain version attribute");
        assertTrue(xml.contains("</xs:attributeGroup>"), "Should contain attributeGroup end tag");
    }

    @Test
    @DisplayName("serializeAttributeGroup() should serialize metadata attributes")
    void testSerializeMetadataAttributes() {
        XsdSchema schema = new XsdSchema();

        // Create metadata attributeGroup
        XsdAttributeGroup metadataAttrs = new XsdAttributeGroup("MetadataAttributes");

        XsdAttribute created = new XsdAttribute("created");
        created.setType("xs:dateTime");
        XsdAttribute createdBy = new XsdAttribute("createdBy");
        createdBy.setType("xs:string");

        metadataAttrs.addChild(created);
        metadataAttrs.addChild(createdBy);
        schema.addChild(metadataAttrs);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:attributeGroup name=\"MetadataAttributes\">"));
        assertTrue(xml.contains("<xs:attribute name=\"created\" type=\"xs:dateTime\" use=\"optional\"/>"));
        assertTrue(xml.contains("<xs:attribute name=\"createdBy\" type=\"xs:string\" use=\"optional\"/>"));
        assertTrue(xml.contains("</xs:attributeGroup>"));
    }

    // ========== AttributeGroup Reference Tests ==========

    @Test
    @DisplayName("serializeAttributeGroup() should serialize attributeGroup reference")
    void testSerializeAttributeGroupReference() {
        XsdSchema schema = new XsdSchema();

        // Create complexType with attributeGroup reference
        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();

        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);

        personType.addChild(sequence);

        // Add attributeGroup reference
        XsdAttributeGroup commonAttrsRef = new XsdAttributeGroup();
        commonAttrsRef.setRef("CommonAttributes");
        personType.addChild(commonAttrsRef);

        schema.addChild(personType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:attributeGroup ref=\"CommonAttributes\"/>"));
        assertFalse(xml.contains("</xs:attributeGroup>")); // Self-closing for reference
    }

    @Test
    @DisplayName("serializeAttributeGroup() should serialize attributeGroup reference with namespace")
    void testSerializeAttributeGroupReferenceWithNamespace() {
        XsdSchema schema = new XsdSchema();

        // Create complexType with namespaced attributeGroup reference
        XsdComplexType personType = new XsdComplexType("PersonType");

        XsdAttributeGroup attrGroupRef = new XsdAttributeGroup();
        attrGroupRef.setRef("tns:CommonAttributes");
        personType.addChild(attrGroupRef);

        schema.addChild(personType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:attributeGroup ref=\"tns:CommonAttributes\"/>"));
    }

    // ========== Integration Tests ==========

    @Test
    @DisplayName("serialize complete schema with group definition and reference")
    void testCompleteSchemaWithGroupDefinitionAndReference() {
        XsdSchema schema = new XsdSchema();

        // 1. Create global group definition
        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        XsdSequence groupSequence = new XsdSequence();

        XsdElement street = new XsdElement("street");
        street.setType("xs:string");
        XsdElement city = new XsdElement("city");
        city.setType("xs:string");
        XsdElement zip = new XsdElement("zip");
        zip.setType("xs:string");

        groupSequence.addChild(street);
        groupSequence.addChild(city);
        groupSequence.addChild(zip);
        addressGroup.addChild(groupSequence);
        schema.addChild(addressGroup);

        // 2. Create complexType that references the group
        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence typeSequence = new XsdSequence();

        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        typeSequence.addChild(name);

        XsdGroup addressGroupRef = new XsdGroup();
        addressGroupRef.setRef("AddressGroup");
        typeSequence.addChild(addressGroupRef);

        personType.addChild(typeSequence);
        schema.addChild(personType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Check group definition
        assertTrue(xml.contains("<xs:group name=\"AddressGroup\">"));
        assertTrue(xml.contains("</xs:group>"));
        // Check group reference
        assertTrue(xml.contains("<xs:group ref=\"AddressGroup\"/>"));
        // Check complexType
        assertTrue(xml.contains("<xs:complexType name=\"PersonType\">"));
    }

    @Test
    @DisplayName("serialize complete schema with attributeGroup definition and reference")
    void testCompleteSchemaWithAttributeGroupDefinitionAndReference() {
        XsdSchema schema = new XsdSchema();

        // 1. Create global attributeGroup definition
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
        schema.addChild(commonAttrs);

        // 2. Create complexType that references the attributeGroup
        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();

        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);

        personType.addChild(sequence);

        XsdAttributeGroup commonAttrsRef = new XsdAttributeGroup();
        commonAttrsRef.setRef("CommonAttributes");
        personType.addChild(commonAttrsRef);

        schema.addChild(personType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Check attributeGroup definition
        assertTrue(xml.contains("<xs:attributeGroup name=\"CommonAttributes\">"));
        assertTrue(xml.contains("</xs:attributeGroup>"));
        // Check attributeGroup reference
        assertTrue(xml.contains("<xs:attributeGroup ref=\"CommonAttributes\"/>"));
        // Check complexType
        assertTrue(xml.contains("<xs:complexType name=\"PersonType\">"));
    }

    @Test
    @DisplayName("serialize schema with both group and attributeGroup")
    void testSchemaWithBothGroupAndAttributeGroup() {
        XsdSchema schema = new XsdSchema();

        // 1. Create group definition
        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        XsdSequence groupSeq = new XsdSequence();
        XsdElement street = new XsdElement("street");
        street.setType("xs:string");
        groupSeq.addChild(street);
        addressGroup.addChild(groupSeq);
        schema.addChild(addressGroup);

        // 2. Create attributeGroup definition
        XsdAttributeGroup metadataAttrs = new XsdAttributeGroup("MetadataAttributes");
        XsdAttribute created = new XsdAttribute("created");
        created.setType("xs:dateTime");
        metadataAttrs.addChild(created);
        schema.addChild(metadataAttrs);

        // 3. Create complexType using both
        XsdComplexType personType = new XsdComplexType("PersonType");
        XsdSequence typeSeq = new XsdSequence();

        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        typeSeq.addChild(name);

        XsdGroup groupRef = new XsdGroup();
        groupRef.setRef("AddressGroup");
        typeSeq.addChild(groupRef);

        personType.addChild(typeSeq);

        XsdAttributeGroup attrGroupRef = new XsdAttributeGroup();
        attrGroupRef.setRef("MetadataAttributes");
        personType.addChild(attrGroupRef);

        schema.addChild(personType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Check group
        assertTrue(xml.contains("<xs:group name=\"AddressGroup\">"));
        assertTrue(xml.contains("<xs:group ref=\"AddressGroup\"/>"));
        // Check attributeGroup
        assertTrue(xml.contains("<xs:attributeGroup name=\"MetadataAttributes\">"));
        assertTrue(xml.contains("<xs:attributeGroup ref=\"MetadataAttributes\"/>"));
        // Check complexType
        assertTrue(xml.contains("<xs:complexType name=\"PersonType\">"));
    }

    // ========== Realistic XSD Examples ==========

    @Test
    @DisplayName("serialize realistic employee schema with groups and attributeGroups")
    void testRealisticEmployeeSchema() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/employee");

        // 1. AddressGroup definition
        XsdGroup addressGroup = new XsdGroup("AddressGroup");
        XsdSequence addrSeq = new XsdSequence();

        XsdElement street = new XsdElement("street");
        street.setType("xs:string");
        XsdElement city = new XsdElement("city");
        city.setType("xs:string");
        XsdElement country = new XsdElement("country");
        country.setType("xs:string");

        addrSeq.addChild(street);
        addrSeq.addChild(city);
        addrSeq.addChild(country);
        addressGroup.addChild(addrSeq);
        schema.addChild(addressGroup);

        // 2. MetadataAttributes definition
        XsdAttributeGroup metadataAttrs = new XsdAttributeGroup("MetadataAttributes");

        XsdAttribute created = new XsdAttribute("created");
        created.setType("xs:dateTime");
        XsdAttribute modified = new XsdAttribute("modified");
        modified.setType("xs:dateTime");

        metadataAttrs.addChild(created);
        metadataAttrs.addChild(modified);
        schema.addChild(metadataAttrs);

        // 3. EmployeeType using both
        XsdComplexType employeeType = new XsdComplexType("EmployeeType");
        XsdSequence empSeq = new XsdSequence();

        XsdElement empId = new XsdElement("employeeId");
        empId.setType("xs:string");
        XsdElement name = new XsdElement("name");
        name.setType("xs:string");

        empSeq.addChild(empId);
        empSeq.addChild(name);

        // Reference AddressGroup
        XsdGroup addrRef = new XsdGroup();
        addrRef.setRef("AddressGroup");
        empSeq.addChild(addrRef);

        employeeType.addChild(empSeq);

        // Reference MetadataAttributes
        XsdAttributeGroup metaRef = new XsdAttributeGroup();
        metaRef.setRef("MetadataAttributes");
        employeeType.addChild(metaRef);

        schema.addChild(employeeType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("targetNamespace=\"http://example.com/employee\""));
        assertTrue(xml.contains("<xs:group name=\"AddressGroup\">"));
        assertTrue(xml.contains("<xs:attributeGroup name=\"MetadataAttributes\">"));
        assertTrue(xml.contains("<xs:complexType name=\"EmployeeType\">"));
        assertTrue(xml.contains("<xs:group ref=\"AddressGroup\"/>"));
        assertTrue(xml.contains("<xs:attributeGroup ref=\"MetadataAttributes\"/>"));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("serialize empty group definition")
    void testSerializeEmptyGroupDefinition() {
        XsdSchema schema = new XsdSchema();

        XsdGroup emptyGroup = new XsdGroup("EmptyGroup");
        schema.addChild(emptyGroup);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:group name=\"EmptyGroup\">"));
        assertTrue(xml.contains("</xs:group>"));
    }

    @Test
    @DisplayName("serialize empty attributeGroup definition")
    void testSerializeEmptyAttributeGroupDefinition() {
        XsdSchema schema = new XsdSchema();

        XsdAttributeGroup emptyAttrGroup = new XsdAttributeGroup("EmptyAttributeGroup");
        schema.addChild(emptyAttrGroup);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:attributeGroup name=\"EmptyAttributeGroup\">"));
        assertTrue(xml.contains("</xs:attributeGroup>"));
    }

    @Test
    @DisplayName("serialize group with XML special characters in name")
    void testSerializeGroupWithSpecialCharacters() {
        XsdSchema schema = new XsdSchema();

        XsdGroup group = new XsdGroup("Group<with>&\"special\"");
        XsdSequence seq = new XsdSequence();
        group.addChild(seq);
        schema.addChild(group);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Should escape special characters
        assertTrue(xml.contains("&lt;"));
        assertTrue(xml.contains("&amp;"));
        assertTrue(xml.contains("&quot;"));
    }

    @Test
    @DisplayName("XML declaration should be present")
    void testXmlDeclarationPresent() {
        XsdSchema schema = new XsdSchema();

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
    }

    @Test
    @DisplayName("schema element should have namespace")
    void testSchemaElementHasNamespace() {
        XsdSchema schema = new XsdSchema();

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:schema"));
        assertTrue(xml.contains("xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""));
        assertTrue(xml.contains("elementFormDefault=\"qualified\""));
        assertTrue(xml.contains("</xs:schema>"));
    }
}
