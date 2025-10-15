package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XsdDomManipulator enumeration functionality
 */
class XsdDomManipulatorEnumerationTest {

    private XsdDomManipulator domManipulator;
    private static final String TEST_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" 
                       elementFormDefault="qualified"
                       targetNamespace="http://example.com/test"
                       xmlns:tns="http://example.com/test">
            
                <xs:element name="TestElement" type="xs:string"/>
            
            </xs:schema>
            """;

    @BeforeEach
    void setUp() throws Exception {
        domManipulator = new XsdDomManipulator();
        domManipulator.loadXsd(TEST_XSD);
    }

    @Test
    void testUpdateElementEnumerations_BasicFunctionality() throws Exception {
        // Given - create a new element using the DOM manipulator itself
        String parentXPath = "/";  // Use root path to reference the schema element
        Element newElement = domManipulator.createElement(parentXPath, "MyElement", "xs:string", "1", "1");
        assertNotNull(newElement, "Element should be created successfully");

        // Get the xpath of the created element - this will be the correct format
        String elementXPath = "/MyElement";

        // Verify the element exists
        Element foundElement = domManipulator.findElementByXPath(elementXPath);
        assertNotNull(foundElement, "Created element should be findable");

        // Given - enumeration values to add
        List<String> enumerations = Arrays.asList("VALUE1", "VALUE2", "VALUE3");

        // When - add enumerations
        boolean result = domManipulator.updateElementEnumerations(elementXPath, enumerations);

        // Then - verify success
        assertTrue(result, "Should successfully update enumerations");

        // Debug: Print the resulting XSD
        String resultXsd = domManipulator.getXsdAsString();
        System.out.println("DEBUG: Resulting XSD after enumeration update:");
        System.out.println(resultXsd);

        // Verify that enumerations were added correctly
        Element element = domManipulator.findElementByXPath(elementXPath);
        NodeList simpleTypes = element.getElementsByTagName("xs:simpleType");
        assertTrue(simpleTypes.getLength() > 0, "Should have a simpleType element");

        Element simpleType = (Element) simpleTypes.item(0);
        NodeList restrictions = simpleType.getElementsByTagName("xs:restriction");
        assertTrue(restrictions.getLength() > 0, "Should have a restriction element");

        Element restriction = (Element) restrictions.item(0);
        NodeList enumerationElements = restriction.getElementsByTagName("xs:enumeration");
        assertEquals(3, enumerationElements.getLength(), "Should have 3 enumeration values");

        // Verify the actual enumeration values
        List<String> actualValues = new ArrayList<>();
        for (int i = 0; i < enumerationElements.getLength(); i++) {
            Element enumElement = (Element) enumerationElements.item(i);
            actualValues.add(enumElement.getAttribute("value"));
        }

        for (String expectedValue : enumerations) {
            assertTrue(actualValues.contains(expectedValue),
                    "Should contain enumeration value: " + expectedValue + ". Actual: " + actualValues);
        }
    }

    @Test
    void testUpdateElementEnumerations_RemoveExistingEnumerations() throws Exception {
        // Given - first add some enumerations
        List<String> initialEnumerations = Arrays.asList("INITIAL1", "INITIAL2");
        String xpath = "/TestElement";
        domManipulator.updateElementEnumerations(xpath, initialEnumerations);

        // When - remove all enumerations by passing empty list
        boolean result = domManipulator.updateElementEnumerations(xpath, List.of());

        // Then
        assertTrue(result, "Should successfully remove enumerations");

        Element element = domManipulator.findElementByXPath(xpath);
        assertNotNull(element, "Element should exist");

        // Check that simpleType was removed
        NodeList simpleTypes = element.getElementsByTagName("xs:simpleType");
        assertEquals(0, simpleTypes.getLength(), "Should have no simpleType after removal");
    }

    @Test
    void testUpdateElementEnumerations_UpdateExistingEnumerations() throws Exception {
        // Given - first add some enumerations
        List<String> initialEnumerations = Arrays.asList("OLD1", "OLD2");
        String xpath = "/TestElement";
        domManipulator.updateElementEnumerations(xpath, initialEnumerations);

        // When - update with new enumerations
        List<String> newEnumerations = Arrays.asList("NEW1", "NEW2", "NEW3");
        boolean result = domManipulator.updateElementEnumerations(xpath, newEnumerations);

        // Then
        assertTrue(result, "Should successfully update enumerations");

        Element element = domManipulator.findElementByXPath(xpath);
        Element simpleType = (Element) element.getElementsByTagName("xs:simpleType").item(0);
        Element restriction = (Element) simpleType.getElementsByTagName("xs:restriction").item(0);
        NodeList enumerationElements = restriction.getElementsByTagName("xs:enumeration");

        assertEquals(3, enumerationElements.getLength(), "Should have 3 new enumeration values");

        // Verify new values are present and old values are gone
        for (int i = 0; i < enumerationElements.getLength(); i++) {
            Element enumElement = (Element) enumerationElements.item(i);
            String value = enumElement.getAttribute("value");
            assertTrue(newEnumerations.contains(value), "Should contain new enumeration value: " + value);
            assertFalse(initialEnumerations.contains(value), "Should not contain old enumeration value: " + value);
        }
    }

    @Test
    void testUpdateElementEnumerations_InvalidXPath() {
        // Given
        List<String> enumerations = Arrays.asList("VALUE1", "VALUE2");
        String invalidXPath = "/NonExistentElement";

        // When
        boolean result = domManipulator.updateElementEnumerations(invalidXPath, enumerations);

        // Then
        assertFalse(result, "Should return false for invalid xpath");
    }

    @Test
    void testUpdateElementEnumerations_EmptyValues() throws Exception {
        // Given
        List<String> enumerationsWithEmpty = Arrays.asList("VALID1", "", "  ", "VALID2", null);
        String xpath = "/TestElement";

        // When
        boolean result = domManipulator.updateElementEnumerations(xpath, enumerationsWithEmpty);

        // Then
        assertTrue(result, "Should successfully handle mixed valid/invalid values");

        Element element = domManipulator.findElementByXPath(xpath);
        Element simpleType = (Element) element.getElementsByTagName("xs:simpleType").item(0);
        Element restriction = (Element) simpleType.getElementsByTagName("xs:restriction").item(0);
        NodeList enumerationElements = restriction.getElementsByTagName("xs:enumeration");

        // Should only have the 2 valid values (empty/null values filtered out)
        assertEquals(2, enumerationElements.getLength(), "Should only have valid enumeration values");
    }
}