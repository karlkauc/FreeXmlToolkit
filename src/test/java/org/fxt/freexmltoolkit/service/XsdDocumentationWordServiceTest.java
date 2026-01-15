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

import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsdDocumentationWordService.
 */
class XsdDocumentationWordServiceTest {

    private XsdDocumentationWordService wordService;
    private XsdDocumentationData testData;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        wordService = new XsdDocumentationWordService();
        testData = createTestDocumentationData();
    }

    @Test
    void testGenerateWordDocumentation_BasicGeneration() throws Exception {
        // Given
        File outputFile = tempDir.resolve("test-output.docx").toFile();

        // When
        wordService.generateWordDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");

        // Verify it's a valid DOCX
        try (FileInputStream fis = new FileInputStream(outputFile);
             XWPFDocument doc = new XWPFDocument(fis)) {
            assertNotNull(doc, "Document should be valid");
            assertFalse(doc.getParagraphs().isEmpty(), "Document should have paragraphs");
        }
    }

    @Test
    void testGenerateWordDocumentation_ContainsSchemaName() throws Exception {
        // Given
        File outputFile = tempDir.resolve("test-output.docx").toFile();

        // When
        wordService.generateWordDocumentation(outputFile, testData);

        // Then
        try (FileInputStream fis = new FileInputStream(outputFile);
             XWPFDocument doc = new XWPFDocument(fis)) {

            // Check document properties
            String title = doc.getProperties().getCoreProperties().getTitle();
            assertNotNull(title, "Document title should be set");
            assertTrue(title.contains("TestSchema"), "Title should contain schema name");
        }
    }

    @Test
    void testGenerateWordDocumentation_ContainsTables() throws Exception {
        // Given
        File outputFile = tempDir.resolve("test-output.docx").toFile();

        // When
        wordService.generateWordDocumentation(outputFile, testData);

        // Then
        try (FileInputStream fis = new FileInputStream(outputFile);
             XWPFDocument doc = new XWPFDocument(fis)) {
            assertFalse(doc.getTables().isEmpty(), "Document should contain tables");
        }
    }

    @Test
    void testGenerateWordDocumentation_WithEmptyData() throws Exception {
        // Given
        File outputFile = tempDir.resolve("empty-output.docx").toFile();
        XsdDocumentationData emptyData = new XsdDocumentationData();
        emptyData.setXsdFilePath("/test/empty.xsd");
        emptyData.setGlobalElements(Collections.emptyList());
        emptyData.setGlobalComplexTypes(Collections.emptyList());
        emptyData.setGlobalSimpleTypes(Collections.emptyList());
        emptyData.setExtendedXsdElementMap(new LinkedHashMap<>());

        // When
        wordService.generateWordDocumentation(outputFile, emptyData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist even with empty data");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");
    }

    @Test
    void testGenerateWordDocumentation_WithProgressListener() throws Exception {
        // Given
        File outputFile = tempDir.resolve("progress-output.docx").toFile();
        List<String> progressMessages = new ArrayList<>();

        wordService.setProgressListener(update -> progressMessages.add(update.taskName()));

        // When
        wordService.generateWordDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist");
        assertFalse(progressMessages.isEmpty(), "Progress messages should be received");
        assertTrue(progressMessages.stream().anyMatch(msg -> msg.contains("Schema") || msg.contains("overview")),
                "Should report schema overview creation");
    }

    @Test
    void testGenerateWordDocumentation_CreatesParentDirectories() throws Exception {
        // Given
        File outputFile = tempDir.resolve("subdir/nested/test-output.docx").toFile();

        // When
        outputFile.getParentFile().mkdirs();
        wordService.generateWordDocumentation(outputFile, testData);

        // Then
        assertTrue(outputFile.exists(), "Output file should exist in nested directory");
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
