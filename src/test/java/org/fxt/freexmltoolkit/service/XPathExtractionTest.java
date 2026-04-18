/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Standalone coverage for {@link ProfiledXmlGeneratorService#extractXPaths(XsdDocumentationData)}.
 * Verifies that auto-fill from the XSD yields XPaths in correct document order with the
 * right metadata (mandatory flag, attribute flag, type name).
 */
@DisplayName("XPath extraction from XSD")
class XPathExtractionTest {

    private ProfiledXmlGeneratorService service;
    private XsdDocumentationData data;

    @BeforeEach
    void setUp() throws Exception {
        service = new ProfiledXmlGeneratorService();
        File xsdFile = new File("src/test/resources/demo-xsd/test-profiled-generation.xsd");
        assertTrue(xsdFile.exists(), "Test XSD must exist at " + xsdFile.getAbsolutePath());

        XsdDocumentationService docService = new XsdDocumentationService();
        docService.setXsdFilePath(xsdFile.getAbsolutePath());
        docService.processXsd(false);
        data = docService.xsdDocumentationData;
    }

    @Test
    @DisplayName("Produces non-empty list")
    void produsesNonEmpty() {
        assertFalse(service.extractXPaths(data).isEmpty());
    }

    @Test
    @DisplayName("Structural containers (SEQUENCE/CHOICE/ALL) are filtered out")
    void filtersStructuralContainers() {
        List<XPathInfo> xpaths = service.extractXPaths(data);
        for (XPathInfo info : xpaths) {
            assertFalse(info.xpath().contains("/SEQUENCE"),
                    "Container segment must be stripped: " + info.xpath());
            assertFalse(info.xpath().contains("/CHOICE"),
                    "Container segment must be stripped: " + info.xpath());
            assertFalse(info.xpath().contains("/ALL"),
                    "Container segment must be stripped: " + info.xpath());
        }
    }

    @Test
    @DisplayName("Expected XPaths from test schema are present")
    void expectedXPathsPresent() {
        List<String> paths = service.extractXPaths(data).stream().map(XPathInfo::xpath).toList();

        assertTrue(paths.contains("/order"), "Root /order must be extracted; got " + paths);
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("/customer")),
                "customer element must be extracted; got " + paths);
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("/country")),
                "country element must be extracted; got " + paths);
        assertTrue(paths.stream().anyMatch(p -> p.endsWith("/item")),
                "item element must be extracted; got " + paths);
    }

    @Test
    @DisplayName("Attributes are detected and marked correctly")
    void attributesAreMarked() {
        List<XPathInfo> xpaths = service.extractXPaths(data);

        // /order/@id is declared required in the XSD
        Optional<XPathInfo> orderId = findByXpathEnding(xpaths, "/@id");
        assertTrue(orderId.isPresent(), "Attribute /order/@id must be present; got "
                + xpaths.stream().map(XPathInfo::xpath).toList());
        assertTrue(orderId.get().isAttribute(), "Attribute flag must be true for /@id");

        // Elements are NOT marked as attributes
        Optional<XPathInfo> country = findByXpathEnding(xpaths, "/country");
        assertTrue(country.isPresent());
        assertFalse(country.get().isAttribute(), "Element must not be flagged as attribute");
    }

    @Test
    @DisplayName("Mandatory flag matches XSD declarations")
    void mandatoryFlagMatchesSchema() {
        List<XPathInfo> xpaths = service.extractXPaths(data);

        // /order/@id is use="required" → mandatory
        Optional<XPathInfo> orderId = findByXpathEnding(xpaths, "/@id");
        assertTrue(orderId.isPresent());
        assertTrue(orderId.get().mandatory(), "/order/@id is required → mandatory must be true");

        // /order/@date has no use attribute (optional by default) → not mandatory
        Optional<XPathInfo> orderDate = findByXpathEnding(xpaths, "/@date");
        assertTrue(orderDate.isPresent());
        assertFalse(orderDate.get().mandatory(), "/order/@date is optional → mandatory must be false");

        // /order/notes has minOccurs="0" → not mandatory
        Optional<XPathInfo> notes = findByXpathEnding(xpaths, "/notes");
        assertTrue(notes.isPresent());
        assertFalse(notes.get().mandatory(), "/order/notes has minOccurs=0 → mandatory must be false");
    }

    @Test
    @DisplayName("Type name is populated for simple types")
    void typeNameIsPopulated() {
        List<XPathInfo> xpaths = service.extractXPaths(data);

        Optional<XPathInfo> country = findByXpathEnding(xpaths, "/country");
        assertTrue(country.isPresent());
        assertEquals("CountryType", country.get().typeName(),
                "country element should carry its declared type");

        Optional<XPathInfo> quantity = findByXpathEnding(xpaths, "/quantity");
        assertTrue(quantity.isPresent());
        assertTrue(quantity.get().typeName().contains("positiveInteger"),
                "quantity should keep its xs:positiveInteger type; got " + quantity.get().typeName());
    }

    @Test
    @DisplayName("XPaths are returned in ascending schema order")
    void xpathsAreSortedBySchemaOrder() {
        List<XPathInfo> xpaths = service.extractXPaths(data);
        for (int i = 1; i < xpaths.size(); i++) {
            assertTrue(xpaths.get(i - 1).schemaOrder() <= xpaths.get(i).schemaOrder(),
                    "schemaOrder must be non-decreasing; saw "
                            + xpaths.get(i - 1).schemaOrder() + " then "
                            + xpaths.get(i).schemaOrder());
        }
    }

    @Test
    @DisplayName("No duplicate XPaths")
    void noDuplicates() {
        List<String> paths = service.extractXPaths(data).stream().map(XPathInfo::xpath).toList();
        assertEquals(paths.size(), paths.stream().distinct().count(),
                "extractXPaths must not return duplicate paths; got " + paths);
    }

    private static Optional<XPathInfo> findByXpathEnding(List<XPathInfo> xpaths, String suffix) {
        return xpaths.stream().filter(x -> x.xpath().endsWith(suffix)).findFirst();
    }
}
