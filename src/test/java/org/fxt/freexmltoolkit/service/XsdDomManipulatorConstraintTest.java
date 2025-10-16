package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XsdDomManipulator constraint functionality
 */
class XsdDomManipulatorConstraintTest {

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
    void testUpdateElementConstraints_PatternConstraint() throws Exception {
        // Given
        String parentXPath = "/";
        Element newElement = domManipulator.createElement(parentXPath, "PatternElement", "xs:string", "1", "1");
        assertNotNull(newElement, "Element should be created successfully");

        String elementXPath = "/PatternElement";
        Map<String, String> constraints = new HashMap<>();
        constraints.put("pattern", "[A-Z][a-z]+");

        // When
        boolean result = domManipulator.updateElementConstraints(elementXPath, constraints);

        // Then
        assertTrue(result, "Constraint update should succeed");

        Element foundElement = domManipulator.findElementByXPath(elementXPath);
        assertNotNull(foundElement, "Element should still exist");

        // Verify pattern constraint was added
        NodeList simpleTypes = foundElement.getElementsByTagName("xs:simpleType");
        assertEquals(1, simpleTypes.getLength(), "Should have one simpleType");

        Element simpleType = (Element) simpleTypes.item(0);
        NodeList restrictions = simpleType.getElementsByTagName("xs:restriction");
        assertEquals(1, restrictions.getLength(), "Should have one restriction");

        Element restriction = (Element) restrictions.item(0);
        NodeList patterns = restriction.getElementsByTagName("xs:pattern");
        assertEquals(1, patterns.getLength(), "Should have one pattern");

        Element pattern = (Element) patterns.item(0);
        assertEquals("[A-Z][a-z]+", pattern.getAttribute("value"), "Pattern value should match");
    }

    @Test
    void testUpdateElementConstraints_MultipleConstraints() throws Exception {
        // Given
        String parentXPath = "/";
        Element newElement = domManipulator.createElement(parentXPath, "MultiConstraintElement", "xs:string", "1", "1");
        assertNotNull(newElement, "Element should be created successfully");

        String elementXPath = "/MultiConstraintElement";
        Map<String, String> constraints = new HashMap<>();
        constraints.put("minLength", "5");
        constraints.put("maxLength", "50");
        constraints.put("pattern", "[A-Za-z0-9]+");

        // When
        boolean result = domManipulator.updateElementConstraints(elementXPath, constraints);

        // Then
        assertTrue(result, "Constraint update should succeed");

        Element foundElement = domManipulator.findElementByXPath(elementXPath);
        NodeList simpleTypes = foundElement.getElementsByTagName("xs:simpleType");
        assertEquals(1, simpleTypes.getLength(), "Should have one simpleType");

        Element simpleType = (Element) simpleTypes.item(0);
        Element restriction = (Element) simpleType.getElementsByTagName("xs:restriction").item(0);

        // Check all constraints were added
        assertEquals(1, restriction.getElementsByTagName("xs:minLength").getLength(), "Should have minLength");
        assertEquals(1, restriction.getElementsByTagName("xs:maxLength").getLength(), "Should have maxLength");
        assertEquals(1, restriction.getElementsByTagName("xs:pattern").getLength(), "Should have pattern");

        // Verify values
        Element minLength = (Element) restriction.getElementsByTagName("xs:minLength").item(0);
        assertEquals("5", minLength.getAttribute("value"), "MinLength value should match");

        Element maxLength = (Element) restriction.getElementsByTagName("xs:maxLength").item(0);
        assertEquals("50", maxLength.getAttribute("value"), "MaxLength value should match");

        Element pattern = (Element) restriction.getElementsByTagName("xs:pattern").item(0);
        assertEquals("[A-Za-z0-9]+", pattern.getAttribute("value"), "Pattern value should match");
    }

    @Test
    void testUpdateElementConstraints_EmptyConstraints() throws Exception {
        // Given - first add some constraints
        String parentXPath = "/";
        Element newElement = domManipulator.createElement(parentXPath, "EmptyConstraintElement", "xs:string", "1", "1");
        String elementXPath = "/EmptyConstraintElement";

        Map<String, String> initialConstraints = new HashMap<>();
        initialConstraints.put("pattern", "[A-Z]+");
        domManipulator.updateElementConstraints(elementXPath, initialConstraints);

        // Verify constraint was added
        Element foundElement = domManipulator.findElementByXPath(elementXPath);
        assertTrue(foundElement.getElementsByTagName("xs:simpleType").getLength() > 0, "Should have simpleType initially");

        // When - clear all constraints
        Map<String, String> emptyConstraints = new HashMap<>();
        boolean result = domManipulator.updateElementConstraints(elementXPath, emptyConstraints);

        // Then
        assertTrue(result, "Constraint update should succeed");

        foundElement = domManipulator.findElementByXPath(elementXPath);
        assertEquals(0, foundElement.getElementsByTagName("xs:simpleType").getLength(), "Should have no simpleType after clearing");
    }

    @Test
    void testUpdateElementConstraints_InvalidXPath() {
        // Given
        String invalidXPath = "/NonExistentElement";
        Map<String, String> constraints = new HashMap<>();
        constraints.put("pattern", "[A-Z]+");

        // When
        boolean result = domManipulator.updateElementConstraints(invalidXPath, constraints);

        // Then
        assertFalse(result, "Should fail for invalid xpath");
    }

    @Test
    void testUpdateElementConstraints_NullConstraints() throws Exception {
        // Given
        String parentXPath = "/";
        Element newElement = domManipulator.createElement(parentXPath, "NullConstraintElement", "xs:string", "1", "1");
        String elementXPath = "/NullConstraintElement";

        // When
        boolean result = domManipulator.updateElementConstraints(elementXPath, null);

        // Then
        assertTrue(result, "Should handle null constraints gracefully");

        Element foundElement = domManipulator.findElementByXPath(elementXPath);
        assertEquals(0, foundElement.getElementsByTagName("xs:simpleType").getLength(), "Should have no simpleType");
    }

    @Test
    void testUpdateElementConstraints_RangeConstraints() throws Exception {
        // Given
        String parentXPath = "/";
        Element newElement = domManipulator.createElement(parentXPath, "RangeElement", "xs:int", "1", "1");
        String elementXPath = "/RangeElement";

        Map<String, String> constraints = new HashMap<>();
        constraints.put("minInclusive", "1");
        constraints.put("maxInclusive", "100");
        constraints.put("totalDigits", "3");

        // When
        boolean result = domManipulator.updateElementConstraints(elementXPath, constraints);

        // Then
        assertTrue(result, "Constraint update should succeed");

        Element foundElement = domManipulator.findElementByXPath(elementXPath);
        Element restriction = (Element) foundElement.getElementsByTagName("xs:restriction").item(0);

        assertEquals(1, restriction.getElementsByTagName("xs:minInclusive").getLength(), "Should have minInclusive");
        assertEquals(1, restriction.getElementsByTagName("xs:maxInclusive").getLength(), "Should have maxInclusive");
        assertEquals(1, restriction.getElementsByTagName("xs:totalDigits").getLength(), "Should have totalDigits");

        Element minInclusive = (Element) restriction.getElementsByTagName("xs:minInclusive").item(0);
        assertEquals("1", minInclusive.getAttribute("value"), "MinInclusive value should match");
    }
}