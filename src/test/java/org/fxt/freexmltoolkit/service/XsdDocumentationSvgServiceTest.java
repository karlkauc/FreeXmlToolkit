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

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsdDocumentationSvgServiceTest {

    @Test
    void testSvgGeneration(@TempDir Path tempDir) throws Exception {
        // Create test data
        XsdDocumentationData data = createTestData();
        
        // Create SVG service
        XsdDocumentationSvgService service = new XsdDocumentationSvgService();
        service.setDocumentationData(data);
        service.setOutputDirectory(tempDir.toFile());

        // Generate SVG page
        assertDoesNotThrow(() -> service.generateSvgPage());

        // Verify that the SVG file was created
        File svgFile = tempDir.resolve("schema-svg.html").toFile();
        assertTrue(svgFile.exists(), "SVG HTML file should be created");
        assertTrue(svgFile.length() > 0, "SVG HTML file should not be empty");

        // Read the file and verify it contains SVG content
        String content = java.nio.file.Files.readString(svgFile.toPath());
        assertTrue(content.contains("<svg"), "Generated file should contain SVG element");
        assertTrue(content.contains("XSD Schema Diagram"), "Generated file should contain diagram title");
        assertTrue(content.contains("zoom"), "Generated file should contain zoom functionality");
    }

    private XsdDocumentationData createTestData() throws Exception {
        XsdDocumentationData data = new XsdDocumentationData();
        data.setXsdFilePath("test.xsd");
        data.setVersion("1.0");
        data.setTargetNamespace("http://example.com/test");

        // Create test elements
        XsdExtendedElement rootElement = createTestElement("RootElement", "RootElementType", 0, true);
        XsdExtendedElement childElement = createTestElement("ChildElement", "xs:string", 1, false);
        XsdExtendedElement attributeElement = createTestElement("@id", "xs:ID", 1, true);

        // Set up parent-child relationships
        rootElement.getChildren().add("/RootElement/ChildElement");
        rootElement.getChildren().add("/RootElement/@id");
        childElement.setParentXpath("/RootElement");
        attributeElement.setParentXpath("/RootElement");

        // Add elements to data
        data.getExtendedXsdElementMap().put("/RootElement", rootElement);
        data.getExtendedXsdElementMap().put("/RootElement/ChildElement", childElement);
        data.getExtendedXsdElementMap().put("/RootElement/@id", attributeElement);

        return data;
    }

    private XsdExtendedElement createTestElement(String name, String type, int level, boolean mandatory) throws Exception {
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementName(name);
        element.setElementType(type);
        element.setLevel(level);
        element.setCurrentXpath("/RootElement" + (level > 0 ? "/" + name : ""));
        element.setChildren(new java.util.ArrayList<>());

        // Create a simple DOM node for the element
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element domElement = doc.createElement("element");
        domElement.setAttribute("name", name.replace("@", ""));
        domElement.setAttribute("type", type);

        if (mandatory) {
            domElement.setAttribute("minOccurs", "1");
            domElement.setAttribute("maxOccurs", "1");
        } else {
            domElement.setAttribute("minOccurs", "0");
            domElement.setAttribute("maxOccurs", "1");
        }

        element.setCurrentNode(domElement);

        return element;
    }
}
