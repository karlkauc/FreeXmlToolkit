package org.fxt.freexmltoolkit.controls.commands;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for adding assertions to parent complexType when working with child elements.
 * Verifies the fix for adding assertions to elements like "label" within "MenuItemType".
 */
class AddAssertionToParentComplexTypeTest {

    private XsdDomManipulator domManipulator;

    private static final String TEST_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       vc:minVersion="1.1">
            
                <xs:complexType name="MenuItemType">
                    <xs:sequence>
                        <xs:element name="label" type="xs:string"/>
                        <xs:element name="link" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
            
                <xs:element name="menuItem" type="MenuItemType"/>
            </xs:schema>
            """;

    @BeforeEach
    void setUp() throws Exception {
        domManipulator = new XsdDomManipulator();
        domManipulator.loadXsd(TEST_XSD);
    }

    /**
     * Test adding assertion directly to MenuItemType complexType
     */
    @Test
    void testAddAssertionToComplexType() {
        // Given - XsdNodeInfo for MenuItemType complexType
        XsdNodeInfo complexTypeNode = new XsdNodeInfo(
                "MenuItemType",
                null,
                "/MenuItemType",  // Simple path format
                null,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                null,
                null,
                XsdNodeInfo.NodeType.COMPLEX_TYPE
        );

        // When - Add assertion
        XsdCommand command = new AddAssertionCommand(
                domManipulator,
                complexTypeNode,
                "label = ('Home', 'About', 'Contact', 'Products')",
                null,
                "Label must be one of the predefined values"
        );

        // Then - Assertion should be added successfully
        assertTrue(command.execute(), "Command should execute successfully");

        // Verify - Assertion was added to the DOM
        Element complexType = domManipulator.findElementByXPath("/MenuItemType");
        assertNotNull(complexType, "ComplexType should be found");

        // Check for assert element
        org.w3c.dom.NodeList assertions = complexType.getElementsByTagNameNS(
                "http://www.w3.org/2001/XMLSchema", "assert");
        assertEquals(1, assertions.getLength(), "Should have 1 assertion");

        Element assertElement = (Element) assertions.item(0);
        assertEquals("label = ('Home', 'About', 'Contact', 'Products')",
                assertElement.getAttribute("test"),
                "Assertion test should match");
    }

    /**
     * Test undo functionality
     */
    @Test
    void testUndoAssertion() {
        // Given
        XsdNodeInfo complexTypeNode = new XsdNodeInfo(
                "MenuItemType",
                null,
                "/MenuItemType",
                null,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                null,
                null,
                XsdNodeInfo.NodeType.COMPLEX_TYPE
        );

        XsdCommand command = new AddAssertionCommand(
                domManipulator,
                complexTypeNode,
                "count(label) = 1",
                null,
                null
        );

        // When - Execute and then undo
        assertTrue(command.execute(), "Command should execute");
        assertTrue(command.undo(), "Undo should succeed");

        // Then - Assertion should be removed
        Element complexType = domManipulator.findElementByXPath("/MenuItemType");
        org.w3c.dom.NodeList assertions = complexType.getElementsByTagNameNS(
                "http://www.w3.org/2001/XMLSchema", "assert");
        assertEquals(0, assertions.getLength(), "Should have no assertions after undo");
    }

    /**
     * Test adding multiple assertions to the same complexType
     */
    @Test
    void testAddMultipleAssertions() {
        // Given
        XsdNodeInfo complexTypeNode = new XsdNodeInfo(
                "MenuItemType",
                null,
                "/MenuItemType",
                null,
                java.util.Collections.emptyList(),
                java.util.Collections.emptyList(),
                null,
                null,
                XsdNodeInfo.NodeType.COMPLEX_TYPE
        );

        // When - Add first assertion
        XsdCommand command1 = new AddAssertionCommand(
                domManipulator,
                complexTypeNode,
                "string-length(label) > 0",
                null,
                "Label must not be empty"
        );
        assertTrue(command1.execute(), "First command should execute");

        // When - Add second assertion
        XsdCommand command2 = new AddAssertionCommand(
                domManipulator,
                complexTypeNode,
                "starts-with(link, 'http')",
                null,
                "Link must start with http"
        );
        assertTrue(command2.execute(), "Second command should execute");

        // Then - Both assertions should be present
        Element complexType = domManipulator.findElementByXPath("/MenuItemType");
        org.w3c.dom.NodeList assertions = complexType.getElementsByTagNameNS(
                "http://www.w3.org/2001/XMLSchema", "assert");
        assertEquals(2, assertions.getLength(), "Should have 2 assertions");
    }

    /**
     * Test that the XPath format is correct for parent complexType
     */
    @Test
    void testCorrectXPathFormat() {
        // This test verifies that the simple path format "/MenuItemType" works correctly
        // instead of the XPath predicate format "//xs:complexType[@name='MenuItemType']"

        Element found = domManipulator.findElementByXPath("/MenuItemType");
        assertNotNull(found, "Should find complexType with simple path format");
        assertEquals("complexType", found.getLocalName(), "Should be a complexType element");
        assertEquals("MenuItemType", found.getAttribute("name"), "Should have correct name");
    }
}
