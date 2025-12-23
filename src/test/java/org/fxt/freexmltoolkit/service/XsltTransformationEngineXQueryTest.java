/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XQuery transformation functionality in XsltTransformationEngine.
 */
class XsltTransformationEngineXQueryTest {

    private XsltTransformationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new XsltTransformationEngine();
    }

    @Test
    void testSimpleXQueryTransformation() {
        String xml = """
                <books>
                    <book>
                        <title>XML Fundamentals</title>
                        <author>John Doe</author>
                    </book>
                    <book>
                        <title>XQuery in Action</title>
                        <author>Jane Smith</author>
                    </book>
                </books>
                """;

        String xquery = """
                for $book in //book
                return $book/title/text()
                """;

        XsltTransformationResult result = engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.XML);

        assertTrue(result.isSuccess(), "XQuery transformation should succeed");
        assertNotNull(result.getOutputContent(), "Output should not be null");
        assertTrue(result.getOutputContent().contains("XML Fundamentals"), "Output should contain first book title");
        assertTrue(result.getOutputContent().contains("XQuery in Action"), "Output should contain second book title");
    }

    @Test
    void testXQueryWithFlwor() {
        String xml = """
                <catalog>
                    <product id="1">
                        <name>Widget</name>
                        <price>19.99</price>
                    </product>
                    <product id="2">
                        <name>Gadget</name>
                        <price>29.99</price>
                    </product>
                </catalog>
                """;

        String xquery = """
                for $p in /catalog/product
                let $name := $p/name/text()
                let $price := $p/price/text()
                order by xs:decimal($price) descending
                return <item name="{$name}" price="{$price}"/>
                """;

        XsltTransformationResult result = engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.XML);

        assertTrue(result.isSuccess(), "XQuery with FLWOR should succeed");
        assertNotNull(result.getOutputContent());
        // Gadget should come first (higher price)
        int gadgetPos = result.getOutputContent().indexOf("Gadget");
        int widgetPos = result.getOutputContent().indexOf("Widget");
        assertTrue(gadgetPos < widgetPos, "Gadget should appear before Widget (higher price)");
    }

    @Test
    void testXQueryHtmlOutput() {
        String xml = """
                <data>
                    <item>First</item>
                    <item>Second</item>
                </data>
                """;

        // Simple XQuery that produces HTML elements
        String xquery = """
                <html>
                    <body>
                        <ul>
                        {
                            for $item in /data/item
                            return <li>{$item/text()}</li>
                        }
                        </ul>
                    </body>
                </html>
                """;

        XsltTransformationResult result = engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.HTML);

        assertTrue(result.isSuccess(), "XQuery HTML output should succeed");
        assertNotNull(result.getOutputContent());
        assertTrue(result.getOutputContent().contains("<html>") || result.getOutputContent().contains("<HTML>"),
                "Output should contain HTML element");
        assertTrue(result.getOutputContent().contains("First"), "Output should contain first item");
        assertTrue(result.getOutputContent().contains("Second"), "Output should contain second item");
    }

    @Test
    void testXQueryWithExternalVariable() {
        String xml = """
                <items>
                    <item category="A">Item 1</item>
                    <item category="B">Item 2</item>
                    <item category="A">Item 3</item>
                </items>
                """;

        String xquery = """
                declare variable $filterCategory external;
                for $item in /items/item[@category = $filterCategory]
                return $item/text()
                """;

        Map<String, Object> variables = new HashMap<>();
        variables.put("filterCategory", "A");

        XsltTransformationResult result = engine.transformXQuery(xml, xquery, variables, XsltTransformationEngine.OutputFormat.TEXT);

        assertTrue(result.isSuccess(), "XQuery with external variable should succeed");
        assertNotNull(result.getOutputContent());
        assertTrue(result.getOutputContent().contains("Item 1"), "Output should contain Item 1");
        assertTrue(result.getOutputContent().contains("Item 3"), "Output should contain Item 3");
        assertFalse(result.getOutputContent().contains("Item 2"), "Output should NOT contain Item 2 (category B)");
    }

    @Test
    void testXQueryValidation() {
        String validXQuery = "for $x in /root return $x";
        String invalidXQuery = "for $x in /root return";

        String validResult = engine.validateXQuery(validXQuery);
        // Valid XQuery returns success message containing "valid"
        assertNotNull(validResult, "Valid XQuery should return success message");
        assertTrue(validResult.toLowerCase().contains("valid"), "Success message should mention 'valid'");

        String invalidResult = engine.validateXQuery(invalidXQuery);
        assertNotNull(invalidResult, "Invalid XQuery should return error message");
        // Invalid XQuery returns error message not containing "valid" success pattern
        assertFalse(invalidResult.toLowerCase().contains("successfully"), "Error message should not mention success");
    }

    @Test
    void testXQueryWithEmptyXml() {
        String xml = "<empty/>";
        String xquery = "count(//item)";

        XsltTransformationResult result = engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.TEXT);

        assertTrue(result.isSuccess(), "XQuery on empty XML should succeed");
        assertNotNull(result.getOutputContent());
        assertEquals("0", result.getOutputContent().trim(), "Count of items should be 0");
    }

    @Test
    void testQuickXQueryTransform() {
        String xml = "<root><value>42</value></root>";
        String xquery = "/root/value/text()";

        XsltTransformationResult result = engine.quickXQueryTransform(xml, xquery);

        assertNotNull(result, "Quick transform should return result");
        assertTrue(result.isSuccess(), "Quick transform should succeed");
        // Output may include XML declaration, so check with contains
        assertTrue(result.getOutputContent().contains("42"), "Result should contain 42");
    }

    @Test
    void testXQueryCacheStatistics() {
        String xml = "<test/>";
        String xquery = "1 + 1";

        // Execute same XQuery twice to test caching
        engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.TEXT);
        engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.TEXT);

        XsltTransformationEngine.TransformationStatistics stats = engine.getStatistics();
        assertNotNull(stats, "Statistics should not be null");
        assertTrue(stats.getCachedXQueries() > 0, "XQuery cache should have entries");
    }

    @Test
    void testClearCacheIncludesXQuery() {
        String xml = "<test/>";
        String xquery = "1 + 1";

        engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.TEXT);

        XsltTransformationEngine.TransformationStatistics statsBefore = engine.getStatistics();
        assertTrue(statsBefore.getCachedXQueries() > 0, "Cache should have entries before cleanup");

        engine.clearCache();

        XsltTransformationEngine.TransformationStatistics statsAfter = engine.getStatistics();
        assertEquals(0, statsAfter.getCachedXQueries(), "Cache should be empty after clearCache");
    }

    @Test
    void testInvalidXmlHandling() {
        String invalidXml = "<unclosed>";
        String xquery = "/root";

        XsltTransformationResult result = engine.transformXQuery(invalidXml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.XML);

        assertFalse(result.isSuccess(), "XQuery on invalid XML should fail");
        assertNotNull(result.getErrorMessage(), "Error message should be present");
    }

    @Test
    void testXQueryWithNamespaces() {
        String xml = """
                <root xmlns:ns="http://example.com">
                    <ns:element>Hello</ns:element>
                </root>
                """;

        String xquery = """
                declare namespace ns = "http://example.com";
                /root/ns:element/text()
                """;

        XsltTransformationResult result = engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.TEXT);

        assertTrue(result.isSuccess(), "XQuery with namespaces should succeed");
        assertEquals("Hello", result.getOutputContent().trim(), "Output should be 'Hello'");
    }

    @Test
    void testXQueryAggregation() {
        String xml = """
                <sales>
                    <sale amount="100"/>
                    <sale amount="200"/>
                    <sale amount="300"/>
                </sales>
                """;

        String xquery = "sum(/sales/sale/@amount)";

        XsltTransformationResult result = engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.TEXT);

        assertTrue(result.isSuccess(), "XQuery aggregation should succeed");
        assertEquals("600", result.getOutputContent().trim(), "Sum should be 600");
    }

    @Test
    void testXQueryJsonOutput() {
        String xml = """
                <person>
                    <name>John</name>
                    <age>30</age>
                </person>
                """;

        // XQuery 3.1 supports map and array constructors for JSON output
        String xquery = """
                map {
                    "name": /person/name/text(),
                    "age": xs:integer(/person/age/text())
                }
                """;

        XsltTransformationResult result = engine.transformXQuery(xml, xquery, new HashMap<>(), XsltTransformationEngine.OutputFormat.JSON);

        assertTrue(result.isSuccess(), "XQuery JSON output should succeed");
        assertNotNull(result.getOutputContent());
        // JSON output should contain the values
        assertTrue(result.getOutputContent().contains("John") || result.getOutputContent().contains("name"),
                "Output should contain name data");
    }
}
