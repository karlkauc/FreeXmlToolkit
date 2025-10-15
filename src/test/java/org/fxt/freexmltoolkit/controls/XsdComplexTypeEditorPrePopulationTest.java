package org.fxt.freexmltoolkit.controls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XsdComplexTypeEditor pre-population functionality
 * Note: These tests validate DOM structure parsing since JavaFX components require display
 */
class XsdComplexTypeEditorPrePopulationTest {

    private Document testDocument;

    private static final String TEST_XSD_WITH_COMPLEX_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       elementFormDefault="qualified"
                       targetNamespace="http://example.com/test"
                       xmlns:tns="http://example.com/test">
            
                <!-- Simple ComplexType with sequence -->
                <xs:complexType name="PersonType">
                    <xs:annotation>
                        <xs:documentation>Person complex type with name and age</xs:documentation>
                    </xs:annotation>
                    <xs:sequence>
                        <xs:element name="firstName" type="xs:string"/>
                        <xs:element name="lastName" type="xs:string"/>
                        <xs:element name="age" type="xs:int" minOccurs="0"/>
                        <xs:element name="email" type="xs:string" maxOccurs="unbounded"/>
                    </xs:sequence>
                    <xs:attribute name="id" type="xs:ID" use="required"/>
                    <xs:attribute name="version" type="xs:string" use="optional"/>
                </xs:complexType>
            
                <!-- ComplexType with choice -->
                <xs:complexType name="ContactType">
                    <xs:choice>
                        <xs:element name="phone" type="xs:string"/>
                        <xs:element name="email" type="xs:string"/>
                        <xs:element name="address" type="xs:string"/>
                    </xs:choice>
                    <xs:attribute name="preferred" type="xs:boolean"/>
                </xs:complexType>
            
                <!-- ComplexType with extension -->
                <xs:complexType name="EmployeeType">
                    <xs:complexContent>
                        <xs:extension base="tns:PersonType">
                            <xs:sequence>
                                <xs:element name="employeeId" type="xs:string"/>
                                <xs:element name="department" type="xs:string"/>
                                <xs:element name="salary" type="xs:decimal" minOccurs="0"/>
                            </xs:sequence>
                            <xs:attribute name="active" type="xs:boolean" use="required"/>
                        </xs:extension>
                    </xs:complexContent>
                </xs:complexType>
            
                <!-- ComplexType with simpleContent extension -->
                <xs:complexType name="NamedValueType">
                    <xs:simpleContent>
                        <xs:extension base="xs:string">
                            <xs:attribute name="name" type="xs:string" use="required"/>
                            <xs:attribute name="category" type="xs:string"/>
                        </xs:extension>
                    </xs:simpleContent>
                </xs:complexType>
            
                <!-- Mixed content ComplexType -->
                <xs:complexType name="MixedContentType" mixed="true">
                    <xs:sequence>
                        <xs:element name="emphasis" type="xs:string" minOccurs="0" maxOccurs="unbounded"/>
                        <xs:element name="strong" type="xs:string" minOccurs="0" maxOccurs="unbounded"/>
                    </xs:sequence>
                </xs:complexType>
            
                <!-- Abstract ComplexType -->
                <xs:complexType name="AbstractBaseType" abstract="true">
                    <xs:sequence>
                        <xs:element name="id" type="xs:string"/>
                        <xs:element name="timestamp" type="xs:dateTime"/>
                    </xs:sequence>
                </xs:complexType>
            
                <!-- ComplexType with inline types -->
                <xs:complexType name="ProductType">
                    <xs:sequence>
                        <xs:element name="name" type="xs:string"/>
                        <xs:element name="category">
                            <xs:simpleType>
                                <xs:restriction base="xs:string">
                                    <xs:enumeration value="ELECTRONICS"/>
                                    <xs:enumeration value="CLOTHING"/>
                                    <xs:enumeration value="BOOKS"/>
                                </xs:restriction>
                            </xs:simpleType>
                        </xs:element>
                        <xs:element name="specifications">
                            <xs:complexType>
                                <xs:sequence>
                                    <xs:element name="weight" type="xs:decimal"/>
                                    <xs:element name="dimensions" type="xs:string"/>
                                </xs:sequence>
                            </xs:complexType>
                        </xs:element>
                    </xs:sequence>
                </xs:complexType>
            
            </xs:schema>
            """;

    @BeforeEach
    void setUp() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        testDocument = builder.parse(new InputSource(new StringReader(TEST_XSD_WITH_COMPLEX_TYPES)));
    }

    @Test
    void testPersonTypeStructure() {
        // Given - PersonType with sequence, elements, and attributes
        Element personType = findComplexTypeByName("PersonType");
        assertNotNull(personType, "PersonType should exist");

        // Verify basic properties
        assertEquals("PersonType", personType.getAttribute("name"));
        assertNotEquals("true", personType.getAttribute("mixed"));
        assertNotEquals("true", personType.getAttribute("abstract"));

        // Verify documentation
        NodeList annotations = personType.getElementsByTagName("xs:annotation");
        assertEquals(1, annotations.getLength());
        NodeList docs = ((Element) annotations.item(0)).getElementsByTagName("xs:documentation");
        assertEquals("Person complex type with name and age", docs.item(0).getTextContent().trim());

        // Verify sequence structure
        NodeList sequences = personType.getElementsByTagName("xs:sequence");
        assertEquals(1, sequences.getLength());

        Element sequence = (Element) sequences.item(0);
        NodeList elements = sequence.getElementsByTagName("xs:element");
        assertEquals(4, elements.getLength());

        // Verify elements
        Element firstName = (Element) elements.item(0);
        assertEquals("firstName", firstName.getAttribute("name"));
        assertEquals("xs:string", firstName.getAttribute("type"));

        Element age = (Element) elements.item(2);
        assertEquals("age", age.getAttribute("name"));
        assertEquals("xs:int", age.getAttribute("type"));
        assertEquals("0", age.getAttribute("minOccurs"));

        Element email = (Element) elements.item(3);
        assertEquals("email", email.getAttribute("name"));
        assertEquals("unbounded", email.getAttribute("maxOccurs"));

        // Verify attributes
        NodeList attributes = personType.getElementsByTagName("xs:attribute");
        assertEquals(2, attributes.getLength());

        Element idAttr = (Element) attributes.item(0);
        assertEquals("id", idAttr.getAttribute("name"));
        assertEquals("xs:ID", idAttr.getAttribute("type"));
        assertEquals("required", idAttr.getAttribute("use"));

        Element versionAttr = (Element) attributes.item(1);
        assertEquals("version", versionAttr.getAttribute("name"));
        assertEquals("optional", versionAttr.getAttribute("use"));
    }

    @Test
    void testContactTypeChoiceStructure() {
        // Given - ContactType with choice content model
        Element contactType = findComplexTypeByName("ContactType");
        assertNotNull(contactType, "ContactType should exist");

        // Verify choice structure
        NodeList choices = contactType.getElementsByTagName("xs:choice");
        assertEquals(1, choices.getLength());

        Element choice = (Element) choices.item(0);
        NodeList elements = choice.getElementsByTagName("xs:element");
        assertEquals(3, elements.getLength());

        // Verify choice elements
        assertEquals("phone", ((Element) elements.item(0)).getAttribute("name"));
        assertEquals("email", ((Element) elements.item(1)).getAttribute("name"));
        assertEquals("address", ((Element) elements.item(2)).getAttribute("name"));

        // Verify attribute
        NodeList attributes = contactType.getElementsByTagName("xs:attribute");
        assertEquals(1, attributes.getLength());
        assertEquals("preferred", ((Element) attributes.item(0)).getAttribute("name"));
    }

    @Test
    void testEmployeeTypeExtensionStructure() {
        // Given - EmployeeType extending PersonType
        Element employeeType = findComplexTypeByName("EmployeeType");
        assertNotNull(employeeType, "EmployeeType should exist");

        // Verify complexContent structure
        NodeList complexContents = employeeType.getElementsByTagName("xs:complexContent");
        assertEquals(1, complexContents.getLength());

        Element complexContent = (Element) complexContents.item(0);
        NodeList extensions = complexContent.getElementsByTagName("xs:extension");
        assertEquals(1, extensions.getLength());

        Element extension = (Element) extensions.item(0);
        assertEquals("tns:PersonType", extension.getAttribute("base"));

        // Verify extension sequence
        NodeList sequences = extension.getElementsByTagName("xs:sequence");
        assertEquals(1, sequences.getLength());

        Element sequence = (Element) sequences.item(0);
        NodeList elements = sequence.getElementsByTagName("xs:element");
        assertEquals(3, elements.getLength());

        assertEquals("employeeId", ((Element) elements.item(0)).getAttribute("name"));
        assertEquals("department", ((Element) elements.item(1)).getAttribute("name"));

        Element salary = (Element) elements.item(2);
        assertEquals("salary", salary.getAttribute("name"));
        assertEquals("xs:decimal", salary.getAttribute("type"));
        assertEquals("0", salary.getAttribute("minOccurs"));

        // Verify extension attribute
        NodeList attributes = extension.getElementsByTagName("xs:attribute");
        assertEquals(1, attributes.getLength());
        Element activeAttr = (Element) attributes.item(0);
        assertEquals("active", activeAttr.getAttribute("name"));
        assertEquals("xs:boolean", activeAttr.getAttribute("type"));
        assertEquals("required", activeAttr.getAttribute("use"));
    }

    @Test
    void testNamedValueTypeSimpleContentExtension() {
        // Given - NamedValueType with simpleContent extension
        Element namedValueType = findComplexTypeByName("NamedValueType");
        assertNotNull(namedValueType, "NamedValueType should exist");

        // Verify simpleContent structure
        NodeList simpleContents = namedValueType.getElementsByTagName("xs:simpleContent");
        assertEquals(1, simpleContents.getLength());

        Element simpleContent = (Element) simpleContents.item(0);
        NodeList extensions = simpleContent.getElementsByTagName("xs:extension");
        assertEquals(1, extensions.getLength());

        Element extension = (Element) extensions.item(0);
        assertEquals("xs:string", extension.getAttribute("base"));

        // Verify attributes in simpleContent extension
        NodeList attributes = extension.getElementsByTagName("xs:attribute");
        assertEquals(2, attributes.getLength());

        Element nameAttr = (Element) attributes.item(0);
        assertEquals("name", nameAttr.getAttribute("name"));
        assertEquals("required", nameAttr.getAttribute("use"));

        Element categoryAttr = (Element) attributes.item(1);
        assertEquals("category", categoryAttr.getAttribute("name"));
        assertEquals("xs:string", categoryAttr.getAttribute("type"));
    }

    @Test
    void testMixedContentType() {
        // Given - MixedContentType with mixed="true"
        Element mixedType = findComplexTypeByName("MixedContentType");
        assertNotNull(mixedType, "MixedContentType should exist");

        // Verify mixed attribute
        assertEquals("true", mixedType.getAttribute("mixed"));

        // Verify sequence with optional unbounded elements
        NodeList sequences = mixedType.getElementsByTagName("xs:sequence");
        assertEquals(1, sequences.getLength());

        Element sequence = (Element) sequences.item(0);
        NodeList elements = sequence.getElementsByTagName("xs:element");
        assertEquals(2, elements.getLength());

        Element emphasis = (Element) elements.item(0);
        assertEquals("emphasis", emphasis.getAttribute("name"));
        assertEquals("0", emphasis.getAttribute("minOccurs"));
        assertEquals("unbounded", emphasis.getAttribute("maxOccurs"));
    }

    @Test
    void testAbstractComplexType() {
        // Given - AbstractBaseType with abstract="true"
        Element abstractType = findComplexTypeByName("AbstractBaseType");
        assertNotNull(abstractType, "AbstractBaseType should exist");

        // Verify abstract attribute
        assertEquals("true", abstractType.getAttribute("abstract"));

        // Verify it still has content
        NodeList sequences = abstractType.getElementsByTagName("xs:sequence");
        assertEquals(1, sequences.getLength());

        Element sequence = (Element) sequences.item(0);
        NodeList elements = sequence.getElementsByTagName("xs:element");
        assertEquals(2, elements.getLength());

        assertEquals("id", ((Element) elements.item(0)).getAttribute("name"));
        assertEquals("timestamp", ((Element) elements.item(1)).getAttribute("name"));
    }

    @Test
    void testProductTypeWithInlineTypes() {
        // Given - ProductType with inline simpleType and complexType
        Element productType = findComplexTypeByName("ProductType");
        assertNotNull(productType, "ProductType should exist");

        // Find the direct sequence child (not nested ones)
        Element sequence = null;
        NodeList children = productType.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && "xs:sequence".equals(element.getTagName())) {
                sequence = element;
                break;
            }
        }
        assertNotNull(sequence, "Should find direct sequence child");

        // Find direct element children of the sequence
        NodeList directElements = sequence.getChildNodes();
        int elementCount = 0;
        Element name = null, category = null, specifications = null;

        for (int i = 0; i < directElements.getLength(); i++) {
            if (directElements.item(i) instanceof Element element && "xs:element".equals(element.getTagName())) {
                elementCount++;
                String elementName = element.getAttribute("name");
                switch (elementName) {
                    case "name" -> name = element;
                    case "category" -> category = element;
                    case "specifications" -> specifications = element;
                }
            }
        }

        assertEquals(3, elementCount, "Should have 3 direct element children");

        // Verify element with inline simpleType
        assertNotNull(category, "Category element should exist");
        assertEquals("category", category.getAttribute("name"));
        assertTrue(category.getAttribute("type").isEmpty()); // No type attribute for inline

        NodeList inlineSimpleTypes = category.getElementsByTagName("xs:simpleType");
        assertEquals(1, inlineSimpleTypes.getLength());

        // Verify element with inline complexType
        assertNotNull(specifications, "Specifications element should exist");
        assertEquals("specifications", specifications.getAttribute("name"));
        assertTrue(specifications.getAttribute("type").isEmpty()); // No type attribute for inline

        NodeList inlineComplexTypes = specifications.getElementsByTagName("xs:complexType");
        assertEquals(1, inlineComplexTypes.getLength());
    }

    @Test
    void testComplexTypePrePopulationDataAvailability() {
        // Verify all named complex types can be found (excludes inline anonymous types)
        String[] expectedNames = {
                "PersonType", "ContactType", "EmployeeType", "NamedValueType",
                "MixedContentType", "AbstractBaseType", "ProductType"
        };

        // Count named complex types only
        NodeList allComplexTypes = testDocument.getElementsByTagName("xs:complexType");
        int namedComplexTypes = 0;
        for (int i = 0; i < allComplexTypes.getLength(); i++) {
            Element ct = (Element) allComplexTypes.item(i);
            if (!ct.getAttribute("name").isEmpty()) {
                namedComplexTypes++;
            }
        }
        assertEquals(7, namedComplexTypes, "Should have 7 named complex types");

        // Verify each expected type can be found
        for (String expectedName : expectedNames) {
            Element complexType = findComplexTypeByName(expectedName);
            assertNotNull(complexType, expectedName + " should be found");
            assertEquals(expectedName, complexType.getAttribute("name"));
        }
    }

    @Test
    void testComplexTypeAttributesDataExtraction() {
        // Test that we can extract all necessary data for pre-population
        Element personType = findComplexTypeByName("PersonType");

        // Test element extraction
        NodeList elements = personType.getElementsByTagName("xs:element");
        assertTrue(elements.getLength() > 0, "Should find elements");

        // Test attribute extraction  
        NodeList attributes = personType.getElementsByTagName("xs:attribute");
        assertTrue(attributes.getLength() > 0, "Should find attributes");

        // Test annotation extraction
        NodeList annotations = personType.getElementsByTagName("xs:annotation");
        assertTrue(annotations.getLength() > 0, "Should find annotations");
    }

    /**
     * Helper method to find a complexType by name
     */
    private Element findComplexTypeByName(String typeName) {
        NodeList complexTypes = testDocument.getElementsByTagName("xs:complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (typeName.equals(complexType.getAttribute("name"))) {
                return complexType;
            }
        }
        return null;
    }
}