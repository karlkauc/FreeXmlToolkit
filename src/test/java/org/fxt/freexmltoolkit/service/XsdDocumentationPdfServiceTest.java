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

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdDocumentationPdfService.
 */
class XsdDocumentationPdfServiceTest {

    private XsdDocumentationPdfService pdfService;
    private XsdDocumentationData testData;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        pdfService = new XsdDocumentationPdfService();
        testData = createTestDocumentationData();
    }

    @Test
    void testGeneratePdfDocumentation_BasicGeneration() throws Exception {
        // Given
        File outputFile = tempDir.resolve("test-output.pdf").toFile();

        // When
        pdfService.generatePdfDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");

        // Verify PDF header
        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "r")) {
            byte[] header = new byte[5];
            raf.read(header);
            String headerStr = new String(header);
            assertEquals("%PDF-", headerStr, "File should start with PDF header");
        }
    }

    @Test
    void testGeneratePdfDocumentation_WithEmptyData() throws Exception {
        // Given
        File outputFile = tempDir.resolve("empty-output.pdf").toFile();
        XsdDocumentationData emptyData = new XsdDocumentationData();
        emptyData.setXsdFilePath("/test/empty.xsd");
        emptyData.setGlobalElements(Collections.emptyList());
        emptyData.setGlobalComplexTypes(Collections.emptyList());
        emptyData.setGlobalSimpleTypes(Collections.emptyList());
        emptyData.setExtendedXsdElementMap(new LinkedHashMap<>());

        // When
        pdfService.generatePdfDocumentation(outputFile, emptyData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist even with empty data");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");
    }

    @Test
    void testGeneratePdfDocumentation_WithProgressListener() throws Exception {
        // Given
        File outputFile = tempDir.resolve("progress-output.pdf").toFile();
        List<String> progressMessages = new ArrayList<>();

        pdfService.setProgressListener(update -> progressMessages.add(update.taskName()));

        // When
        pdfService.generatePdfDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist");
        assertFalse(progressMessages.isEmpty(), "Progress messages should be received");
        assertTrue(progressMessages.stream().anyMatch(msg -> msg.toLowerCase().contains("xml")),
                "Should report XML creation");
    }

    @Test
    void testGeneratePdfDocumentation_CreatesParentDirectories() throws Exception {
        // Given
        File outputFile = tempDir.resolve("subdir/nested/test-output.pdf").toFile();

        // When
        pdfService.generatePdfDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist in nested directory");
    }

    @Test
    void testGeneratePdfDocumentation_WithComplexTypes() throws Exception {
        // Given
        File outputFile = tempDir.resolve("complex-types.pdf").toFile();

        // When
        pdfService.generatePdfDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist");
        // PDF with complex types should be larger
        assertTrue(outputFile.length() > 1000, "PDF with content should have reasonable size");
    }

    @Test
    void testGeneratePdfDocumentation_WithSimpleTypes() throws Exception {
        // Given
        File outputFile = tempDir.resolve("simple-types.pdf").toFile();
        testData.getGlobalComplexTypes().clear(); // Remove complex types

        // When
        pdfService.generatePdfDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist");
    }

    @Test
    void testGeneratePdfDocumentation_LargeDataDictionary() throws Exception {
        // Given
        File outputFile = tempDir.resolve("large-dict.pdf").toFile();

        // Add many elements to test pagination
        Map<String, XsdExtendedElement> largeMap = new LinkedHashMap<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        for (int i = 0; i < 100; i++) {
            Element elem = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
            elem.setAttribute("name", "Element" + i);
            elem.setAttribute("type", "xs:string");

            XsdExtendedElement extElement = new XsdExtendedElement();
            extElement.setElementName("Element" + i);
            extElement.setElementType("xs:string");
            extElement.setCurrentXpath("/Root/Element" + i);
            extElement.setLevel(1);
            extElement.setCurrentNode(elem);
            largeMap.put("/Root/Element" + i, extElement);
        }
        testData.setExtendedXsdElementMap(largeMap);

        // When
        pdfService.generatePdfDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 5000, "Large PDF should have reasonable size");
    }

    @Test
    void testGeneratePdfDocumentation_SpecialCharacters() throws Exception {
        // Given
        File outputFile = tempDir.resolve("special-chars.pdf").toFile();
        testData.setTargetNamespace("http://example.com/test?param=value&other=123");

        // Add element with special characters in documentation
        Map<String, XsdExtendedElement> elementMap = testData.getExtendedXsdElementMap();
        XsdExtendedElement element = elementMap.values().iterator().next();
        List<XsdExtendedElement.DocumentationInfo> docs = new ArrayList<>();
        docs.add(new XsdExtendedElement.DocumentationInfo("default", "Test with <special> & \"characters\""));
        element.setDocumentations(docs);

        // When
        pdfService.generatePdfDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist with special characters");
    }

    /**
     * Creates test documentation data for testing.
     */
    private XsdDocumentationData createTestDocumentationData() throws Exception {
        XsdDocumentationData data = new XsdDocumentationData();
        data.setXsdFilePath("/test/TestSchema.xsd");
        data.setTargetNamespace("http://example.com/test");
        data.setVersion("1.0");
        data.setElementFormDefault("qualified");
        data.setAttributeFormDefault("unqualified");

        // Create test DOM elements
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // Create a sample complexType
        Element complexType = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "PersonType");

        List<org.w3c.dom.Node> complexTypes = new ArrayList<>();
        complexTypes.add(complexType);
        data.setGlobalComplexTypes(complexTypes);

        // Create a sample simpleType
        Element simpleType = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:simpleType");
        simpleType.setAttribute("name", "NameType");
        Element restriction = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:restriction");
        restriction.setAttribute("base", "xs:string");
        simpleType.appendChild(restriction);

        List<org.w3c.dom.Node> simpleTypes = new ArrayList<>();
        simpleTypes.add(simpleType);
        data.setGlobalSimpleTypes(simpleTypes);

        // Create sample elements
        Element globalElement = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        globalElement.setAttribute("name", "Person");
        globalElement.setAttribute("type", "PersonType");

        List<org.w3c.dom.Node> globalElements = new ArrayList<>();
        globalElements.add(globalElement);
        data.setGlobalElements(globalElements);

        // Create extended element map
        Map<String, XsdExtendedElement> elementMap = new LinkedHashMap<>();
        XsdExtendedElement extElement = new XsdExtendedElement();
        extElement.setElementName("Person");
        extElement.setElementType("PersonType");
        extElement.setCurrentXpath("/Person");
        extElement.setLevel(0);
        extElement.setCurrentNode(globalElement);
        elementMap.put("/Person", extElement);
        data.setExtendedXsdElementMap(elementMap);

        // Create type usage map
        Map<String, List<XsdExtendedElement>> typeUsageMap = new HashMap<>();
        typeUsageMap.put("PersonType", List.of(extElement));
        data.setTypeUsageMap(typeUsageMap);

        return data;
    }
}
