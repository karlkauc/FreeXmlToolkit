package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to verify that node ordering is preserved during XLSX/CSV to XML conversion
 */
class XmlSpreadsheetNodeOrderingTest {

    private XmlSpreadsheetConverterService converterService;
    private XmlSpreadsheetConverterService.ConversionConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        converterService = new XmlSpreadsheetConverterService();
        config = new XmlSpreadsheetConverterService.ConversionConfig();
    }

    @Test
    void testNodeOrderingPreservedFromRowOrder() throws Exception {
        // Create a list of RowData in a specific order
        List<XmlSpreadsheetConverterService.RowData> rows = new ArrayList<>();

        // Add rows in a specific order that should be preserved
        rows.add(new XmlSpreadsheetConverterService.RowData("/root", "", "element", 0));
        rows.add(new XmlSpreadsheetConverterService.RowData("/root/firstChild", "first", "element", 1));
        rows.add(new XmlSpreadsheetConverterService.RowData("/root/secondChild", "second", "element", 2));
        rows.add(new XmlSpreadsheetConverterService.RowData("/root/thirdChild", "third", "element", 3));
        rows.add(new XmlSpreadsheetConverterService.RowData("/root/secondChild/@attr", "attribute", "attribute", 4));

        // Build XML from rows
        Document doc = converterService.buildXmlFromRows(rows, config);

        // Verify the document was created
        assertNotNull(doc);
        Element root = doc.getDocumentElement();
        assertNotNull(root);
        assertEquals("root", root.getNodeName());

        // Check that child elements appear in the correct order
        NodeList children = root.getChildNodes();
        List<String> elementNames = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elementNames.add(child.getNodeName());
            }
        }

        // The order should be: firstChild, secondChild, thirdChild
        assertEquals(3, elementNames.size());
        assertEquals("firstChild", elementNames.get(0), "First child should be 'firstChild'");
        assertEquals("secondChild", elementNames.get(1), "Second child should be 'secondChild'");
        assertEquals("thirdChild", elementNames.get(2), "Third child should be 'thirdChild'");

        // Verify values
        assertEquals("first", root.getElementsByTagName("firstChild").item(0).getTextContent());
        assertEquals("second", root.getElementsByTagName("secondChild").item(0).getTextContent());
        assertEquals("third", root.getElementsByTagName("thirdChild").item(0).getTextContent());

        // Verify attribute was added to secondChild
        Element secondChild = (Element) root.getElementsByTagName("secondChild").item(0);
        assertEquals("attribute", secondChild.getAttribute("attr"));
    }

    @Test
    void testSortingByOriginalIndex() throws Exception {
        // Create rows in "wrong" order but with correct original indexes
        List<XmlSpreadsheetConverterService.RowData> rows = new ArrayList<>();

        // Add them out of order intentionally
        rows.add(new XmlSpreadsheetConverterService.RowData("/root/thirdChild", "third", "element", 3));
        rows.add(new XmlSpreadsheetConverterService.RowData("/root", "", "element", 0));
        rows.add(new XmlSpreadsheetConverterService.RowData("/root/secondChild", "second", "element", 2));
        rows.add(new XmlSpreadsheetConverterService.RowData("/root/firstChild", "first", "element", 1));

        // Build XML from rows - should be reordered by originalIndex
        Document doc = converterService.buildXmlFromRows(rows, config);

        // Verify the document was created
        assertNotNull(doc);
        Element root = doc.getDocumentElement();
        assertNotNull(root);

        // Check that child elements appear in the correct order (based on originalIndex, not list order)
        NodeList children = root.getChildNodes();
        List<String> elementNames = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elementNames.add(child.getNodeName());
            }
        }

        // The order should be: firstChild, secondChild, thirdChild (based on originalIndex)
        assertEquals(3, elementNames.size());
        assertEquals("firstChild", elementNames.get(0), "First child should be 'firstChild' (originalIndex=1)");
        assertEquals("secondChild", elementNames.get(1), "Second child should be 'secondChild' (originalIndex=2)");
        assertEquals("thirdChild", elementNames.get(2), "Third child should be 'thirdChild' (originalIndex=3)");
    }
}