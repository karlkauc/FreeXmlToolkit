package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SaxonXPathHelper utility class.
 */
class SaxonXPathHelperTest {

    private Document xsdDocument;
    private Document xmlDocument;

    @BeforeEach
    void setUp() throws Exception {
        // Sample XSD document for testing
        String xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="person" type="PersonType">
                    <xs:annotation>
                        <xs:documentation>A person element</xs:documentation>
                    </xs:annotation>
                </xs:element>
                <xs:complexType name="PersonType">
                    <xs:annotation>
                        <xs:documentation>Person complex type</xs:documentation>
                    </xs:annotation>
                    <xs:sequence>
                        <xs:element name="name" type="xs:string"/>
                        <xs:element name="age" type="xs:integer"/>
                    </xs:sequence>
                </xs:complexType>
                <xs:simpleType name="AgeType">
                    <xs:restriction base="xs:integer">
                        <xs:minInclusive value="0"/>
                        <xs:maxInclusive value="150"/>
                    </xs:restriction>
                </xs:simpleType>
            </xs:schema>
            """;

        // Sample XML document for testing
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root>
                <person id="1">
                    <name>John Doe</name>
                    <age>30</age>
                </person>
                <person id="2">
                    <name>Jane Smith</name>
                    <age>25</age>
                </person>
            </root>
            """;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        xsdDocument = builder.parse(new InputSource(new StringReader(xsd)));
        xmlDocument = builder.parse(new InputSource(new StringReader(xml)));
    }

    @Test
    void evaluateNodes_findsElementsWithNamespace() {
        List<XdmNode> elements = SaxonXPathHelper.evaluateNodes(
                xsdDocument,
                "//xs:element",
                SaxonXPathHelper.XSD_NAMESPACES
        );

        assertFalse(elements.isEmpty());
        assertTrue(elements.size() >= 3); // person, name, age
    }

    @Test
    void evaluateSingleNode_findsFirstMatch() {
        XdmNode element = SaxonXPathHelper.evaluateSingleNode(
                xsdDocument,
                "//xs:element[@name='person']",
                SaxonXPathHelper.XSD_NAMESPACES
        );

        assertNotNull(element);
        assertEquals("person", SaxonXPathHelper.getAttributeValue(element, "name"));
    }

    @Test
    void evaluateString_extractsTextContent() {
        String documentation = SaxonXPathHelper.evaluateString(
                xsdDocument,
                "//xs:element[@name='person']/xs:annotation/xs:documentation",
                SaxonXPathHelper.XSD_NAMESPACES
        );

        assertNotNull(documentation);
        assertEquals("A person element", documentation.trim());
    }

    @Test
    void evaluateStringList_extractsMultipleValues() {
        List<String> names = SaxonXPathHelper.evaluateStringList(
                xmlDocument,
                "//person/name",
                null
        );

        assertEquals(2, names.size());
        assertTrue(names.contains("John Doe"));
        assertTrue(names.contains("Jane Smith"));
    }

    @Test
    void evaluateBoolean_returnsTrueWhenMatches() {
        boolean hasPersons = SaxonXPathHelper.evaluateBoolean(
                xmlDocument,
                "//person",
                null
        );

        assertTrue(hasPersons);
    }

    @Test
    void evaluateBoolean_returnsFalseWhenNoMatches() {
        boolean hasEmployees = SaxonXPathHelper.evaluateBoolean(
                xmlDocument,
                "//employee",
                null
        );

        assertFalse(hasEmployees);
    }

    @Test
    void evaluateCount_countsMatches() {
        int count = SaxonXPathHelper.evaluateCount(
                xmlDocument,
                "//person",
                null
        );

        assertEquals(2, count);
    }

    @Test
    void getAttributeValue_returnsAttributeValue() {
        XdmNode person = SaxonXPathHelper.evaluateSingleNode(
                xmlDocument,
                "//person[@id='1']",
                null
        );

        assertNotNull(person);
        assertEquals("1", SaxonXPathHelper.getAttributeValue(person, "id"));
    }

    @Test
    void getAttributeValue_returnsNullForMissingAttribute() {
        XdmNode person = SaxonXPathHelper.evaluateSingleNode(
                xmlDocument,
                "//person[@id='1']",
                null
        );

        assertNotNull(person);
        assertNull(SaxonXPathHelper.getAttributeValue(person, "nonexistent"));
    }

    @Test
    void combineNamespaces_mergesMultipleMaps() {
        Map<String, String> combined = SaxonXPathHelper.combineNamespaces(
                SaxonXPathHelper.XSD_NAMESPACES,
                SaxonXPathHelper.SVRL_NAMESPACES
        );

        assertTrue(combined.containsKey("xs"));
        assertTrue(combined.containsKey("xsd"));
        assertTrue(combined.containsKey("svrl"));
    }

    @Test
    void evaluateNodes_returnsEmptyListForInvalidXPath() {
        List<XdmNode> nodes = SaxonXPathHelper.evaluateNodes(
                xmlDocument,
                "//nonexistent",
                null
        );

        assertNotNull(nodes);
        assertTrue(nodes.isEmpty());
    }

    @Test
    void evaluateSingleNode_returnsNullForNoMatch() {
        XdmNode node = SaxonXPathHelper.evaluateSingleNode(
                xmlDocument,
                "//nonexistent",
                null
        );

        assertNull(node);
    }

    @Test
    void xpath31Features_workCorrectly() {
        // Test XPath 3.1 string function
        String result = SaxonXPathHelper.evaluateString(
                xmlDocument,
                "string-join(//person/name, ', ')",
                null
        );

        assertNotNull(result);
        assertEquals("John Doe, Jane Smith", result);
    }

    @Test
    void xpath31Features_regexMatchesWorks() {
        // Test XPath 3.1 regex matches function
        boolean hasJohn = SaxonXPathHelper.evaluateBoolean(
                xmlDocument,
                "//person[matches(name, '^John')]",
                null
        );

        assertTrue(hasJohn);
    }
}
