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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsdIncludeTest {

    @Test
    void testXsIncludeProcessing(@TempDir Path tempDir) throws Exception {
        // Create test schema files
        File baseSchema = createBaseSchema(tempDir);
        File includedSchema = createIncludedSchema(tempDir);

        // Test that the documentation service processes xs:include correctly
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(baseSchema.getAbsolutePath());
        service.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);

        // Process the schema
        service.processXsd(true);

        // Verify that elements from the included schema are present
        var elements = service.xsdDocumentationData.getExtendedXsdElementMap();

        // Should contain elements from both schemas
        assertTrue(elements.containsKey("/RootElement"));
        // Note: XPath may include SEQUENCE container, so check by element name
        boolean hasIncludedElement = elements.values().stream()
                .anyMatch(e -> "IncludedElement".equals(e.getElementName()));
        assertTrue(hasIncludedElement, "IncludedElement should be present from included schema");

        // Verify that complex types from included schema are available
        var complexTypes = service.xsdDocumentationData.getGlobalComplexTypes();
        boolean foundIncludedType = false;
        for (Node typeNode : complexTypes) {
            String typeName = getAttributeValue(typeNode, "name");
            if ("IncludedComplexType".equals(typeName)) {
                foundIncludedType = true;
                break;
            }
        }
        assertTrue(foundIncludedType, "Complex type from included schema should be available");

        // Verify that simple types from included schema are available
        var simpleTypes = service.xsdDocumentationData.getGlobalSimpleTypes();
        boolean foundIncludedSimpleType = false;
        for (Node typeNode : simpleTypes) {
            String typeName = getAttributeValue(typeNode, "name");
            if ("IncludedSimpleType".equals(typeName)) {
                foundIncludedSimpleType = true;
                break;
            }
        }
        assertTrue(foundIncludedSimpleType, "Simple type from included schema should be available");
    }

    private File createBaseSchema(Path tempDir) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element schema = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:schema");
        schema.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
        schema.setAttribute("targetNamespace", "http://example.com/base");
        doc.appendChild(schema);

        // Add xs:include
        Element include = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
        include.setAttribute("schemaLocation", "included.xsd");
        schema.appendChild(include);

        // Add root element
        Element rootElement = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        rootElement.setAttribute("name", "RootElement");
        rootElement.setAttribute("type", "RootElementType");
        schema.appendChild(rootElement);

        // Add root element type
        Element rootType = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        rootType.setAttribute("name", "RootElementType");
        schema.appendChild(rootType);

        Element sequence = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:sequence");
        rootType.appendChild(sequence);

        Element includedElement = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        includedElement.setAttribute("name", "IncludedElement");
        includedElement.setAttribute("type", "IncludedComplexType");
        sequence.appendChild(includedElement);

        // Write to file
        File schemaFile = tempDir.resolve("base.xsd").toFile();
        writeDocumentToFile(doc, schemaFile);
        return schemaFile;
    }

    private File createIncludedSchema(Path tempDir) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element schema = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:schema");
        schema.setAttribute("xmlns:xs", "http://www.w3.org/2001/XMLSchema");
        schema.setAttribute("targetNamespace", "http://example.com/base");
        doc.appendChild(schema);

        // Add complex type
        Element complexType = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
        complexType.setAttribute("name", "IncludedComplexType");
        schema.appendChild(complexType);

        Element sequence = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:sequence");
        complexType.appendChild(sequence);

        Element element = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
        element.setAttribute("name", "IncludedChild");
        element.setAttribute("type", "IncludedSimpleType");
        sequence.appendChild(element);

        // Add simple type
        Element simpleType = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:simpleType");
        simpleType.setAttribute("name", "IncludedSimpleType");
        schema.appendChild(simpleType);

        Element restriction = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:restriction");
        restriction.setAttribute("base", "xs:string");
        simpleType.appendChild(restriction);

        Element maxLength = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:maxLength");
        maxLength.setAttribute("value", "100");
        restriction.appendChild(maxLength);

        // Write to file
        File schemaFile = tempDir.resolve("included.xsd").toFile();
        writeDocumentToFile(doc, schemaFile);
        return schemaFile;
    }

    private File createSchemaWithDoctype(Path tempDir) throws Exception {
        // Create a simple schema file with DOCTYPE declaration
        File schemaFile = tempDir.resolve("schema-with-doctype.xsd").toFile();

        // Create XML content with DOCTYPE similar to xmldsig-core-schema.xsd
        String xmlContent = """ 
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE schema
                  PUBLIC "-//W3C//DTD XMLSchema 200102//EN" "http://www.w3.org/2001/XMLSchema.dtd">
                
                <schema xmlns="http://www.w3.org/2001/XMLSchema"
                        targetNamespace="http://example.com/test"
                        version="1.0" elementFormDefault="qualified">
                
                <element name="TestElement" type="xs:string"/>
                
                </schema>
                """;

        try (FileWriter writer = new FileWriter(schemaFile)) {
            writer.write(xmlContent);
        }

        return schemaFile;
    }

    private String documentToString(Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private void writeDocumentToFile(Document doc, File file) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
    }

    private String getAttributeValue(Node node, String attrName) {
        if (node == null || node.getAttributes() == null) return null;
        Node attrNode = node.getAttributes().getNamedItem(attrName);
        return (attrNode != null) ? attrNode.getNodeValue() : null;
    }
}
