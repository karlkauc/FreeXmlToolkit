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

    // ========== List Serialization Tests ==========

    @Test
    @DisplayName("serializeList() should serialize list with itemType")
    void testSerializeListWithItemType() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType integerListType = new XsdSimpleType("IntegerListType");
        XsdList list = new XsdList("xs:integer");
        integerListType.addChild(list);
        schema.addChild(integerListType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:simpleType name=\"IntegerListType\">"));
        assertTrue(xml.contains("<xs:list itemType=\"xs:integer\"/>"));
        assertTrue(xml.contains("</xs:simpleType>"));
    }

    @Test
    @DisplayName("serializeList() should serialize list with inline simpleType")
    void testSerializeListWithInlineSimpleType() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType colorListType = new XsdSimpleType("ColorListType");
        XsdList list = new XsdList();

        // Inline simpleType with restriction
        XsdSimpleType inlineType = new XsdSimpleType();
        XsdRestriction restriction = new XsdRestriction("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "red"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "green"));
        inlineType.addChild(restriction);
        list.addChild(inlineType);

        colorListType.addChild(list);
        schema.addChild(colorListType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:list>"));
        assertTrue(xml.contains("<xs:simpleType>"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("</xs:list>"));
    }

    @Test
    @DisplayName("serializeList() should serialize string list")
    void testSerializeStringList() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType stringList = new XsdSimpleType("StringListType");
        XsdList list = new XsdList("xs:string");
        stringList.addChild(list);
        schema.addChild(stringList);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:list itemType=\"xs:string\"/>"));
    }

    // ========== Union Serialization Tests ==========

    @Test
    @DisplayName("serializeUnion() should serialize union with memberTypes")
    void testSerializeUnionWithMemberTypes() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType numericOrString = new XsdSimpleType("NumericOrString");
        XsdUnion union = new XsdUnion();
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");
        numericOrString.addChild(union);
        schema.addChild(numericOrString);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:simpleType name=\"NumericOrString\">"));
        assertTrue(xml.contains("<xs:union memberTypes=\"xs:integer xs:string\"/>"));
        assertTrue(xml.contains("</xs:simpleType>"));
    }

    @Test
    @DisplayName("serializeUnion() should serialize union with inline simpleTypes")
    void testSerializeUnionWithInlineSimpleTypes() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType sizeType = new XsdSimpleType("SizeType");
        XsdUnion union = new XsdUnion();

        // Inline simpleType 1: integer restriction
        XsdSimpleType intType = new XsdSimpleType();
        XsdRestriction intRestriction = new XsdRestriction("xs:integer");
        intRestriction.addFacet(new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "1"));
        intType.addChild(intRestriction);
        union.addChild(intType);

        // Inline simpleType 2: string enumeration
        XsdSimpleType stringType = new XsdSimpleType();
        XsdRestriction stringRestriction = new XsdRestriction("xs:string");
        stringRestriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "small"));
        stringRestriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "large"));
        stringType.addChild(stringRestriction);
        union.addChild(stringType);

        sizeType.addChild(union);
        schema.addChild(sizeType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:union>"));
        assertTrue(xml.contains("<xs:simpleType>"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:integer\">"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("</xs:union>"));
    }

    @Test
    @DisplayName("serializeUnion() should serialize multiple member types")
    void testSerializeUnionWithMultipleMemberTypes() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType numericType = new XsdSimpleType("NumericType");
        XsdUnion union = new XsdUnion();
        union.addMemberType("xs:integer");
        union.addMemberType("xs:decimal");
        union.addMemberType("xs:double");
        numericType.addChild(union);
        schema.addChild(numericType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:union memberTypes=\"xs:integer xs:decimal xs:double\"/>"));
    }

    // ========== List and Union Integration Tests ==========

    @Test
    @DisplayName("serialize schema with both list and union types")
    void testSchemaWithListAndUnion() {
        XsdSchema schema = new XsdSchema();

        // List type
        XsdSimpleType intList = new XsdSimpleType("IntegerListType");
        XsdList list = new XsdList("xs:integer");
        intList.addChild(list);
        schema.addChild(intList);

        // Union type
        XsdSimpleType numOrString = new XsdSimpleType("NumericOrString");
        XsdUnion union = new XsdUnion();
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");
        numOrString.addChild(union);
        schema.addChild(numOrString);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Check list
        assertTrue(xml.contains("<xs:simpleType name=\"IntegerListType\">"));
        assertTrue(xml.contains("<xs:list itemType=\"xs:integer\"/>"));
        // Check union
        assertTrue(xml.contains("<xs:simpleType name=\"NumericOrString\">"));
        assertTrue(xml.contains("<xs:union memberTypes=\"xs:integer xs:string\"/>"));
    }

    @Test
    @DisplayName("serialize complex schema with list, union, and restriction")
    void testComplexSchemaWithListUnionRestriction() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");

        // 1. Restriction type
        XsdSimpleType zipCode = new XsdSimpleType("ZipCodeType");
        XsdRestriction restriction = new XsdRestriction("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}"));
        zipCode.addChild(restriction);
        schema.addChild(zipCode);

        // 2. List type
        XsdSimpleType zipCodeList = new XsdSimpleType("ZipCodeListType");
        XsdList list = new XsdList("ZipCodeType");
        zipCodeList.addChild(list);
        schema.addChild(zipCodeList);

        // 3. Union type
        XsdSimpleType flexibleId = new XsdSimpleType("FlexibleIdType");
        XsdUnion union = new XsdUnion();
        union.addMemberType("xs:integer");
        union.addMemberType("xs:string");
        flexibleId.addChild(union);
        schema.addChild(flexibleId);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("targetNamespace=\"http://example.com/test\""));
        // Check all three types
        assertTrue(xml.contains("<xs:simpleType name=\"ZipCodeType\">"));
        assertTrue(xml.contains("<xs:simpleType name=\"ZipCodeListType\">"));
        assertTrue(xml.contains("<xs:simpleType name=\"FlexibleIdType\">"));
        assertTrue(xml.contains("<xs:restriction"));
        assertTrue(xml.contains("<xs:list"));
        assertTrue(xml.contains("<xs:union"));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("serializeList() should handle empty itemType")
    void testSerializeListWithoutItemType() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType listType = new XsdSimpleType("EmptyListType");
        XsdList list = new XsdList(); // No itemType
        listType.addChild(list);
        schema.addChild(listType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:list/>"));
    }

    @Test
    @DisplayName("serializeUnion() should handle empty memberTypes")
    void testSerializeUnionWithoutMemberTypes() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType unionType = new XsdSimpleType("EmptyUnionType");
        XsdUnion union = new XsdUnion(); // No memberTypes
        unionType.addChild(union);
        schema.addChild(unionType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:union/>"));
    }

    @Test
    @DisplayName("serializeUnion() should handle single member type")
    void testSerializeUnionWithSingleMemberType() {
        XsdSchema schema = new XsdSchema();

        XsdSimpleType unionType = new XsdSimpleType("SingleMemberUnion");
        XsdUnion union = new XsdUnion();
        union.addMemberType("xs:string");
        unionType.addChild(union);
        schema.addChild(unionType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:union memberTypes=\"xs:string\"/>"));
    }

    // ========== Identity Constraint Serialization Tests ==========

    @Test
    @DisplayName("serializeKey() should serialize key constraint with selector and field")
    void testSerializeKey() {
        XsdSchema schema = new XsdSchema();
        XsdElement employeeElement = new XsdElement("Employee");

        XsdKey key = new XsdKey("employeeKey");
        XsdSelector selector = new XsdSelector(".//employee");
        XsdField field = new XsdField("@id");

        key.addChild(selector);
        key.addChild(field);
        employeeElement.addChild(key);
        schema.addChild(employeeElement);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:key name=\"employeeKey\">"));
        assertTrue(xml.contains("<xs:selector xpath=\".//employee\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@id\"/>"));
        assertTrue(xml.contains("</xs:key>"));
    }

    @Test
    @DisplayName("serializeKey() should serialize key with multiple fields")
    void testSerializeKeyMultipleFields() {
        XsdSchema schema = new XsdSchema();
        XsdElement personElement = new XsdElement("Person");

        XsdKey key = new XsdKey("personKey");
        XsdSelector selector = new XsdSelector(".//person");
        XsdField field1 = new XsdField("@firstName");
        XsdField field2 = new XsdField("@lastName");
        XsdField field3 = new XsdField("@birthDate");

        key.addChild(selector);
        key.addChild(field1);
        key.addChild(field2);
        key.addChild(field3);
        personElement.addChild(key);
        schema.addChild(personElement);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:key name=\"personKey\">"));
        assertTrue(xml.contains("<xs:selector xpath=\".//person\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@firstName\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@lastName\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@birthDate\"/>"));
        assertTrue(xml.contains("</xs:key>"));
    }

    @Test
    @DisplayName("serializeKeyRef() should serialize keyref constraint with refer attribute")
    void testSerializeKeyRef() {
        XsdSchema schema = new XsdSchema();
        XsdElement departmentElement = new XsdElement("Department");

        XsdKeyRef keyRef = new XsdKeyRef("employeeRef");
        keyRef.setRefer("employeeKey");
        XsdSelector selector = new XsdSelector(".//department");
        XsdField field = new XsdField("@managerId");

        keyRef.addChild(selector);
        keyRef.addChild(field);
        departmentElement.addChild(keyRef);
        schema.addChild(departmentElement);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:keyref name=\"employeeRef\" refer=\"employeeKey\">"));
        assertTrue(xml.contains("<xs:selector xpath=\".//department\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@managerId\"/>"));
        assertTrue(xml.contains("</xs:keyref>"));
    }

    @Test
    @DisplayName("serializeUnique() should serialize unique constraint")
    void testSerializeUnique() {
        XsdSchema schema = new XsdSchema();
        XsdElement userElement = new XsdElement("User");

        XsdUnique unique = new XsdUnique("emailUnique");
        XsdSelector selector = new XsdSelector(".//user");
        XsdField field = new XsdField("@email");

        unique.addChild(selector);
        unique.addChild(field);
        userElement.addChild(unique);
        schema.addChild(userElement);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:unique name=\"emailUnique\">"));
        assertTrue(xml.contains("<xs:selector xpath=\".//user\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@email\"/>"));
        assertTrue(xml.contains("</xs:unique>"));
    }

    @Test
    @DisplayName("serialize complete schema with key and keyref")
    void testSerializeSchemaWithKeyAndKeyRef() {
        XsdSchema schema = new XsdSchema();

        // Employee element with key
        XsdElement employeeElement = new XsdElement("Employee");
        XsdKey key = new XsdKey("employeeKey");
        XsdSelector keySelector = new XsdSelector(".//employee");
        XsdField keyField = new XsdField("@id");
        key.addChild(keySelector);
        key.addChild(keyField);
        employeeElement.addChild(key);
        schema.addChild(employeeElement);

        // Department element with keyref
        XsdElement departmentElement = new XsdElement("Department");
        XsdKeyRef keyRef = new XsdKeyRef("managerRef");
        keyRef.setRefer("employeeKey");
        XsdSelector refSelector = new XsdSelector(".//department");
        XsdField refField = new XsdField("@managerId");
        keyRef.addChild(refSelector);
        keyRef.addChild(refField);
        departmentElement.addChild(keyRef);
        schema.addChild(departmentElement);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Verify key
        assertTrue(xml.contains("<xs:key name=\"employeeKey\">"));
        assertTrue(xml.contains("<xs:selector xpath=\".//employee\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@id\"/>"));
        // Verify keyref
        assertTrue(xml.contains("<xs:keyref name=\"managerRef\" refer=\"employeeKey\">"));
        assertTrue(xml.contains("<xs:selector xpath=\".//department\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@managerId\"/>"));
    }

    @Test
    @DisplayName("serialize schema with multiple identity constraints")
    void testSerializeMultipleIdentityConstraints() {
        XsdSchema schema = new XsdSchema();
        XsdElement personElement = new XsdElement("Person");

        // Key constraint
        XsdKey key = new XsdKey("personKey");
        XsdSelector keySelector = new XsdSelector(".//person");
        XsdField keyField = new XsdField("@ssn");
        key.addChild(keySelector);
        key.addChild(keyField);
        personElement.addChild(key);

        // Unique constraint
        XsdUnique unique = new XsdUnique("emailUnique");
        XsdSelector uniqueSelector = new XsdSelector(".//person");
        XsdField uniqueField = new XsdField("@email");
        unique.addChild(uniqueSelector);
        unique.addChild(uniqueField);
        personElement.addChild(unique);

        schema.addChild(personElement);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Verify both constraints are present
        assertTrue(xml.contains("<xs:key name=\"personKey\">"));
        assertTrue(xml.contains("<xs:unique name=\"emailUnique\">"));
        assertTrue(xml.contains("<xs:field xpath=\"@ssn\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"@email\"/>"));
    }

    @Test
    @DisplayName("serialize identity constraint with complex XPath expressions")
    void testSerializeComplexXPathInConstraints() {
        XsdSchema schema = new XsdSchema();
        XsdElement rootElement = new XsdElement("Root");

        XsdKey key = new XsdKey("complexKey");
        XsdSelector selector = new XsdSelector("./ns:items/ns:item[@type='primary']");
        XsdField field = new XsdField("ns:id/@value");

        key.addChild(selector);
        key.addChild(field);
        rootElement.addChild(key);
        schema.addChild(rootElement);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:key name=\"complexKey\">"));
        assertTrue(xml.contains("<xs:selector xpath=\"./ns:items/ns:item[@type=&apos;primary&apos;]\"/>"));
        assertTrue(xml.contains("<xs:field xpath=\"ns:id/@value\"/>"));
    }

    @Test
    @DisplayName("serialize identity constraint without selector should serialize empty")
    void testSerializeIdentityConstraintWithoutSelector() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("Test");

        XsdKey key = new XsdKey("incompleteKey");
        // No selector or fields added
        element.addChild(key);
        schema.addChild(element);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:key name=\"incompleteKey\">"));
        assertTrue(xml.contains("</xs:key>"));
    }

    @Test
    @DisplayName("serialize identity constraint with XML special characters")
    void testSerializeIdentityConstraintWithSpecialCharacters() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("Test");

        XsdKey key = new XsdKey("key<>&\"'");
        XsdSelector selector = new XsdSelector(".//test[@attr='value<>&']");

        key.addChild(selector);
        element.addChild(key);
        schema.addChild(element);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Verify XML escaping
        assertTrue(xml.contains("key&lt;&gt;&amp;&quot;&apos;"));
        assertTrue(xml.contains(".//test[@attr=&apos;value&lt;&gt;&amp;&apos;]"));
    }

    // ========== Import/Include/Redefine Serialization Tests ==========

    @Test
    @DisplayName("serializeImport() should serialize import with namespace and schemaLocation")
    void testSerializeImportWithNamespaceAndLocation() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/main");

        XsdImport xsdImport = new XsdImport("http://example.com/types", "types.xsd");
        schema.addChild(xsdImport);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:import namespace=\"http://example.com/types\" schemaLocation=\"types.xsd\"/>"));
    }

    @Test
    @DisplayName("serializeImport() should serialize import with only namespace")
    void testSerializeImportWithNamespaceOnly() {
        XsdSchema schema = new XsdSchema();

        XsdImport xsdImport = new XsdImport("http://www.w3.org/2001/XMLSchema");
        schema.addChild(xsdImport);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:import namespace=\"http://www.w3.org/2001/XMLSchema\"/>"));
        assertFalse(xml.contains("schemaLocation"));
    }

    @Test
    @DisplayName("serializeImport() should handle multiple imports")
    void testSerializeMultipleImports() {
        XsdSchema schema = new XsdSchema();

        XsdImport import1 = new XsdImport("http://example.com/types", "types.xsd");
        XsdImport import2 = new XsdImport("http://example.com/elements", "elements.xsd");

        schema.addChild(import1);
        schema.addChild(import2);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:import namespace=\"http://example.com/types\" schemaLocation=\"types.xsd\"/>"));
        assertTrue(xml.contains("<xs:import namespace=\"http://example.com/elements\" schemaLocation=\"elements.xsd\"/>"));
    }

    @Test
    @DisplayName("serializeInclude() should serialize include with schemaLocation")
    void testSerializeIncludeWithSchemaLocation() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/ns");

        XsdInclude include = new XsdInclude("common-types.xsd");
        schema.addChild(include);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:include schemaLocation=\"common-types.xsd\"/>"));
    }

    @Test
    @DisplayName("serializeInclude() should handle multiple includes")
    void testSerializeMultipleIncludes() {
        XsdSchema schema = new XsdSchema();

        XsdInclude include1 = new XsdInclude("types.xsd");
        XsdInclude include2 = new XsdInclude("elements.xsd");

        schema.addChild(include1);
        schema.addChild(include2);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:include schemaLocation=\"types.xsd\"/>"));
        assertTrue(xml.contains("<xs:include schemaLocation=\"elements.xsd\"/>"));
    }

    @Test
    @DisplayName("serializeInclude() should serialize include with relative path")
    void testSerializeIncludeWithRelativePath() {
        XsdSchema schema = new XsdSchema();

        XsdInclude include = new XsdInclude("../shared/common.xsd");
        schema.addChild(include);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:include schemaLocation=\"../shared/common.xsd\"/>"));
    }

    @Test
    @DisplayName("serializeRedefine() should serialize redefine with schemaLocation")
    void testSerializeRedefineWithSchemaLocation() {
        XsdSchema schema = new XsdSchema();

        XsdRedefine redefine = new XsdRedefine("base-types.xsd");
        schema.addChild(redefine);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:redefine schemaLocation=\"base-types.xsd\"/>"));
    }

    @Test
    @DisplayName("serializeRedefine() should serialize redefine with redefined complexType")
    void testSerializeRedefineWithComplexType() {
        XsdSchema schema = new XsdSchema();

        XsdRedefine redefine = new XsdRedefine("types.xsd");

        // Redefine a complex type
        XsdComplexType complexType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();
        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);
        complexType.addChild(sequence);
        redefine.addChild(complexType);

        schema.addChild(redefine);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:redefine schemaLocation=\"types.xsd\">"));
        assertTrue(xml.contains("<xs:complexType name=\"PersonType\">"));
        assertTrue(xml.contains("<xs:sequence>"));
        assertTrue(xml.contains("<xs:element name=\"name\" type=\"xs:string\"/>"));
        assertTrue(xml.contains("</xs:sequence>"));
        assertTrue(xml.contains("</xs:complexType>"));
        assertTrue(xml.contains("</xs:redefine>"));
    }

    @Test
    @DisplayName("serializeRedefine() should serialize redefine with redefined simpleType")
    void testSerializeRedefineWithSimpleType() {
        XsdSchema schema = new XsdSchema();

        XsdRedefine redefine = new XsdRedefine("base-types.xsd");

        // Redefine a simple type with restriction
        XsdSimpleType simpleType = new XsdSimpleType("CountryCodeType");
        XsdRestriction restriction = new XsdRestriction("xs:string");
        XsdFacet facet = new XsdFacet(XsdFacetType.PATTERN, "[A-Z]{2}");
        restriction.addChild(facet);
        simpleType.addChild(restriction);
        redefine.addChild(simpleType);

        schema.addChild(redefine);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:redefine schemaLocation=\"base-types.xsd\">"));
        assertTrue(xml.contains("<xs:simpleType name=\"CountryCodeType\">"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("<xs:pattern value=\"[A-Z]{2}\"/>"));
        assertTrue(xml.contains("</xs:restriction>"));
        assertTrue(xml.contains("</xs:simpleType>"));
        assertTrue(xml.contains("</xs:redefine>"));
    }

    @Test
    @DisplayName("serializeRedefine() should handle multiple redefined components")
    void testSerializeRedefineWithMultipleComponents() {
        XsdSchema schema = new XsdSchema();

        XsdRedefine redefine = new XsdRedefine("base.xsd");

        // Add complex type
        XsdComplexType complexType = new XsdComplexType("Type1");
        redefine.addChild(complexType);

        // Add simple type
        XsdSimpleType simpleType = new XsdSimpleType("Type2");
        redefine.addChild(simpleType);

        schema.addChild(redefine);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:redefine schemaLocation=\"base.xsd\">"));
        assertTrue(xml.contains("<xs:complexType name=\"Type1\">"));
        assertTrue(xml.contains("<xs:simpleType name=\"Type2\">"));
        assertTrue(xml.contains("</xs:redefine>"));
    }

    @Test
    @DisplayName("serialize schema with import, include, and redefine together")
    void testSerializeSchemaWithAllReferences() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/main");

        // Add import
        XsdImport xsdImport = new XsdImport("http://example.com/external", "external.xsd");
        schema.addChild(xsdImport);

        // Add include
        XsdInclude include = new XsdInclude("common.xsd");
        schema.addChild(include);

        // Add redefine
        XsdRedefine redefine = new XsdRedefine("base.xsd");
        schema.addChild(redefine);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:import namespace=\"http://example.com/external\" schemaLocation=\"external.xsd\"/>"));
        assertTrue(xml.contains("<xs:include schemaLocation=\"common.xsd\"/>"));
        assertTrue(xml.contains("<xs:redefine schemaLocation=\"base.xsd\"/>"));
    }

    @Test
    @DisplayName("serialize import with XML special characters in namespace")
    void testSerializeImportWithSpecialCharacters() {
        XsdSchema schema = new XsdSchema();

        XsdImport xsdImport = new XsdImport("http://example.com/types?param=value&test=true", "types<>&.xsd");
        schema.addChild(xsdImport);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        // Verify XML escaping
        assertTrue(xml.contains("http://example.com/types?param=value&amp;test=true"));
        assertTrue(xml.contains("types&lt;&gt;&amp;.xsd"));
    }

    // ========== XSD 1.1 Features Serialization Tests ==========

    @Test
    @DisplayName("serializeAssert() should serialize assert with test expression")
    void testSerializeAssertWithTest() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType complexType = new XsdComplexType("ProductType");

        XsdAssert xsdAssert = new XsdAssert("@price > 0");
        complexType.addChild(xsdAssert);
        schema.addChild(complexType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:assert test=\"@price &gt; 0\"/>"));
    }

    @Test
    @DisplayName("serializeAssert() should serialize assert with xpathDefaultNamespace")
    void testSerializeAssertWithNamespace() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType complexType = new XsdComplexType("ProductType");

        XsdAssert xsdAssert = new XsdAssert("count(item) > 0");
        xsdAssert.setXpathDefaultNamespace("http://example.com/ns");
        complexType.addChild(xsdAssert);
        schema.addChild(complexType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:assert test=\"count(item) &gt; 0\" xpathDefaultNamespace=\"http://example.com/ns\"/>"));
    }

    @Test
    @DisplayName("serializeAssert() should handle multiple asserts in complexType")
    void testSerializeMultipleAsserts() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType complexType = new XsdComplexType("ProductType");

        XsdAssert assert1 = new XsdAssert("@price > 0");
        XsdAssert assert2 = new XsdAssert("@quantity >= 1");
        complexType.addChild(assert1);
        complexType.addChild(assert2);
        schema.addChild(complexType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:assert test=\"@price &gt; 0\"/>"));
        assertTrue(xml.contains("<xs:assert test=\"@quantity &gt;= 1\"/>"));
    }

    @Test
    @DisplayName("serializeAlternative() should serialize alternative with test and type")
    void testSerializeAlternativeWithTestAndType() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("value");

        XsdAlternative alternative = new XsdAlternative("@type='integer'", "xs:integer");
        element.addChild(alternative);
        schema.addChild(element);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:alternative test=\"@type=&apos;integer&apos;\" type=\"xs:integer\"/>"));
    }

    @Test
    @DisplayName("serializeAlternative() should serialize alternative with inline simpleType")
    void testSerializeAlternativeWithInlineType() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("value");

        XsdAlternative alternative = new XsdAlternative("@format='restricted'", null);
        XsdSimpleType simpleType = new XsdSimpleType();
        XsdRestriction restriction = new XsdRestriction("xs:string");
        XsdFacet facet = new XsdFacet(XsdFacetType.MAX_LENGTH, "50");
        restriction.addChild(facet);
        simpleType.addChild(restriction);
        alternative.addChild(simpleType);

        element.addChild(alternative);
        schema.addChild(element);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:alternative test=\"@format=&apos;restricted&apos;\">"));
        assertTrue(xml.contains("<xs:simpleType>"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("<xs:maxLength value=\"50\"/>"));
        assertTrue(xml.contains("</xs:restriction>"));
        assertTrue(xml.contains("</xs:simpleType>"));
        assertTrue(xml.contains("</xs:alternative>"));
    }

    @Test
    @DisplayName("serializeAlternative() should handle multiple alternatives")
    void testSerializeMultipleAlternatives() {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("value");

        XsdAlternative alt1 = new XsdAlternative("@type='integer'", "xs:integer");
        XsdAlternative alt2 = new XsdAlternative("@type='string'", "xs:string");

        element.addChild(alt1);
        element.addChild(alt2);
        schema.addChild(element);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:alternative test=\"@type=&apos;integer&apos;\" type=\"xs:integer\"/>"));
        assertTrue(xml.contains("<xs:alternative test=\"@type=&apos;string&apos;\" type=\"xs:string\"/>"));
    }

    @Test
    @DisplayName("serializeOpenContent() should serialize openContent with interleave mode")
    void testSerializeOpenContentInterleave() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType complexType = new XsdComplexType("ExtensibleType");

        XsdOpenContent openContent = new XsdOpenContent(XsdOpenContent.Mode.INTERLEAVE);
        complexType.addChild(openContent);
        schema.addChild(complexType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:openContent mode=\"interleave\"/>"));
    }

    @Test
    @DisplayName("serializeOpenContent() should serialize openContent with suffix mode")
    void testSerializeOpenContentSuffix() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType complexType = new XsdComplexType("ExtensibleType");

        XsdOpenContent openContent = new XsdOpenContent(XsdOpenContent.Mode.SUFFIX);
        complexType.addChild(openContent);
        schema.addChild(complexType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:openContent mode=\"suffix\"/>"));
    }

    @Test
    @DisplayName("serializeOpenContent() should serialize openContent with wildcard child")
    void testSerializeOpenContentWithWildcard() {
        XsdSchema schema = new XsdSchema();
        XsdComplexType complexType = new XsdComplexType("ExtensibleType");

        XsdOpenContent openContent = new XsdOpenContent(XsdOpenContent.Mode.INTERLEAVE);
        // Add wildcard child (using XsdElement as placeholder for any element)
        XsdElement wildcardNode = new XsdElement("any");
        openContent.addChild(wildcardNode);
        complexType.addChild(openContent);
        schema.addChild(complexType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:openContent mode=\"interleave\">"));
        assertTrue(xml.contains("</xs:openContent>"));
    }

    @Test
    @DisplayName("serializeOverride() should serialize override with schemaLocation")
    void testSerializeOverrideWithSchemaLocation() {
        XsdSchema schema = new XsdSchema();

        XsdOverride override = new XsdOverride("base-types.xsd");
        schema.addChild(override);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:override schemaLocation=\"base-types.xsd\"/>"));
    }

    @Test
    @DisplayName("serializeOverride() should serialize override with overridden complexType")
    void testSerializeOverrideWithComplexType() {
        XsdSchema schema = new XsdSchema();

        XsdOverride override = new XsdOverride("types.xsd");

        // Override a complex type
        XsdComplexType complexType = new XsdComplexType("PersonType");
        XsdSequence sequence = new XsdSequence();
        XsdElement name = new XsdElement("name");
        name.setType("xs:string");
        sequence.addChild(name);
        complexType.addChild(sequence);
        override.addChild(complexType);

        schema.addChild(override);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:override schemaLocation=\"types.xsd\">"));
        assertTrue(xml.contains("<xs:complexType name=\"PersonType\">"));
        assertTrue(xml.contains("<xs:sequence>"));
        assertTrue(xml.contains("<xs:element name=\"name\" type=\"xs:string\"/>"));
        assertTrue(xml.contains("</xs:sequence>"));
        assertTrue(xml.contains("</xs:complexType>"));
        assertTrue(xml.contains("</xs:override>"));
    }

    @Test
    @DisplayName("serializeOverride() should serialize override with overridden simpleType")
    void testSerializeOverrideWithSimpleType() {
        XsdSchema schema = new XsdSchema();

        XsdOverride override = new XsdOverride("base-types.xsd");

        // Override a simple type with restriction
        XsdSimpleType simpleType = new XsdSimpleType("CountryCodeType");
        XsdRestriction restriction = new XsdRestriction("xs:string");
        XsdFacet facet = new XsdFacet(XsdFacetType.PATTERN, "[A-Z]{2}");
        restriction.addChild(facet);
        simpleType.addChild(restriction);
        override.addChild(simpleType);

        schema.addChild(override);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:override schemaLocation=\"base-types.xsd\">"));
        assertTrue(xml.contains("<xs:simpleType name=\"CountryCodeType\">"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("<xs:pattern value=\"[A-Z]{2}\"/>"));
        assertTrue(xml.contains("</xs:restriction>"));
        assertTrue(xml.contains("</xs:simpleType>"));
        assertTrue(xml.contains("</xs:override>"));
    }

    @Test
    @DisplayName("serialize complete XSD 1.1 schema with all features")
    void testSerializeCompleteXsd11Schema() {
        XsdSchema schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/xsd11");

        // Add assert
        XsdComplexType productType = new XsdComplexType("ProductType");
        XsdAssert priceAssert = new XsdAssert("@price > 0");
        productType.addChild(priceAssert);
        schema.addChild(productType);

        // Add alternative
        XsdElement valueElement = new XsdElement("value");
        XsdAlternative alternative = new XsdAlternative("@type='integer'", "xs:integer");
        valueElement.addChild(alternative);
        schema.addChild(valueElement);

        // Add openContent
        XsdComplexType extensibleType = new XsdComplexType("ExtensibleType");
        XsdOpenContent openContent = new XsdOpenContent(XsdOpenContent.Mode.INTERLEAVE);
        extensibleType.addChild(openContent);
        schema.addChild(extensibleType);

        // Add override
        XsdOverride override = new XsdOverride("base.xsd");
        schema.addChild(override);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:assert test=\"@price &gt; 0\"/>"));
        assertTrue(xml.contains("<xs:alternative test=\"@type=&apos;integer&apos;\" type=\"xs:integer\"/>"));
        assertTrue(xml.contains("<xs:openContent mode=\"interleave\"/>"));
        assertTrue(xml.contains("<xs:override schemaLocation=\"base.xsd\"/>"));
    }
}
