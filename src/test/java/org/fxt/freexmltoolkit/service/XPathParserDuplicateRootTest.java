package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to reproduce and fix the duplicate root element issue
 */
class XPathParserDuplicateRootTest {

    private XPathParser xpathParser;
    private Document doc;

    @BeforeEach
    void setUp() throws Exception {
        xpathParser = new XPathParser();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.newDocument();
    }

    @Test
    void testNoDuplicateRootElementCreation() throws Exception {
        // Simulate the scenario: first create root element, then add child

        // First XPath: root element with attributes
        xpathParser.createNodeFromXPath(doc, "/FundsXML4", "", "element");
        xpathParser.createNodeFromXPath(doc, "/FundsXML4/@xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance", "attribute");
        xpathParser.createNodeFromXPath(doc, "/FundsXML4/@xsi:noNamespaceSchemaLocation", "https://github.com/fundsxml/schema/releases/download/4.2.2/FundsXML.xsd", "attribute");

        // Then add child elements
        xpathParser.createNodeFromXPath(doc, "/FundsXML4/AssetMasterData", "", "element");

        // Check that there's only one root element
        Element rootElement = doc.getDocumentElement();
        assertNotNull(rootElement, "Root element should exist");
        assertEquals("FundsXML4", rootElement.getNodeName(), "Root element should be FundsXML4");

        // Check that there are no duplicate root elements
        NodeList fundsXmlElements = rootElement.getElementsByTagName("FundsXML4");
        assertEquals(0, fundsXmlElements.getLength(), "There should be no nested FundsXML4 elements (duplicates)");

        // Check that AssetMasterData is a direct child of the root
        NodeList assetMasterDataElements = rootElement.getElementsByTagName("AssetMasterData");
        assertEquals(1, assetMasterDataElements.getLength(), "There should be exactly one AssetMasterData element");

        Element assetMasterData = (Element) assetMasterDataElements.item(0);
        assertEquals(rootElement, assetMasterData.getParentNode(), "AssetMasterData should be direct child of root");

        // Print the resulting XML structure for debugging
        System.out.println("Generated XML structure:");
        System.out.println(doc.getDocumentElement().getNodeName());
        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                System.out.println("  - " + children.item(i).getNodeName());
            }
        }
    }

    @Test
    void testRootElementWithValueThenChildren() throws Exception {
        // Test another common scenario

        // Create root element first (this might have a value initially)
        xpathParser.createNodeFromXPath(doc, "/root", "some text", "element");

        // Then add child elements (this should not create duplicate root)
        xpathParser.createNodeFromXPath(doc, "/root/child1", "child value", "element");
        xpathParser.createNodeFromXPath(doc, "/root/child2", "another value", "element");

        // Verify structure
        Element rootElement = doc.getDocumentElement();
        assertNotNull(rootElement);
        assertEquals("root", rootElement.getNodeName());

        // Should not have nested root elements
        NodeList nestedRoots = rootElement.getElementsByTagName("root");
        assertEquals(0, nestedRoots.getLength(), "No nested root elements should exist");

        // Should have the child elements
        NodeList child1Elements = rootElement.getElementsByTagName("child1");
        NodeList child2Elements = rootElement.getElementsByTagName("child2");
        assertEquals(1, child1Elements.getLength());
        assertEquals(1, child2Elements.getLength());
    }
}