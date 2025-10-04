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

package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for verifying the exact IntelliSense data for FundsXML4 element and comparing
 * with expected XML generation output.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorFundsXML4Test {

    private XmlCodeEditor xmlCodeEditor;
    private XmlEditor xmlEditor;
    private File testXsdFile;
    private File testXmlFile;

    @Start
    void start(Stage stage) {
        xmlCodeEditor = new XmlCodeEditor();
        xmlEditor = new XmlEditor();

        // Set up the parent-child relationship
        xmlCodeEditor.setParentXmlEditor(xmlEditor);

        stage.setScene(new javafx.scene.Scene(xmlCodeEditor, 800, 600));
        stage.show();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Use the actual FundsXML4 XSD structure that causes the problem
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="FundsXML4">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="ControlData" minOccurs="1">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element name="UniqueDocumentID" type="xs:string" minOccurs="1"/>
                                            <xs:element name="DocumentGenerated" type="xs:string" minOccurs="1"/>
                                            <xs:element name="ContentDate" type="xs:string" minOccurs="1"/>
                                            <xs:element name="DataSupplier" minOccurs="1">
                                                <xs:complexType>
                                                    <xs:sequence>
                                                        <xs:element name="SystemCountry" type="xs:string" minOccurs="1"/>
                                                        <xs:element name="Short" type="xs:string" minOccurs="1"/>
                                                        <xs:element name="Name" minOccurs="1">
                                                            <xs:complexType>
                                                                <xs:simpleContent>
                                                                    <xs:extension base="xs:string">
                                                                        <xs:attribute name="language" type="xs:string" use="required"/>
                                                                        <xs:attribute name="maxLen" type="xs:int" use="required"/>
                                                                    </xs:extension>
                                                                </xs:simpleContent>
                                                            </xs:complexType>
                                                        </xs:element>
                                                        <xs:element name="Type" minOccurs="0">
                                                            <xs:complexType>
                                                                <xs:choice>
                                                                    <xs:element name="ListedType" type="xs:string"/>
                                                                    <xs:element name="UnlistedType" type="xs:string"/>
                                                                </xs:choice>
                                                            </xs:complexType>
                                                        </xs:element>
                                                    </xs:sequence>
                                                </xs:complexType>
                                            </xs:element>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        // Create temporary XSD file
        testXsdFile = File.createTempFile("funds-xml4-real", ".xsd");
        testXsdFile.deleteOnExit();
        Files.write(testXsdFile.toPath(), xsdContent.getBytes());

        // Create temporary XML file
        testXmlFile = File.createTempFile("test-xml", ".xml");
        testXmlFile.deleteOnExit();
        Files.write(testXmlFile.toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes());

        // Set up the XmlEditor with XSD
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                xmlEditor.setXsdFile(testXsdFile);
                xmlEditor.setXmlFile(testXmlFile);
                xmlCodeEditor.setEditorMode(XmlCodeEditor.EditorMode.XML_WITH_XSD);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Setup should complete within 5 seconds");
    }

    @Test
    void testFundsXML4IntelliSenseDataExact() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                XsdDocumentationService xsdService = xmlEditor.getXsdDocumentationService();
                assertNotNull(xsdService, "XSD Documentation Service should be available");

                // Process the XSD to populate element map
                xsdService.setXsdFilePath(testXsdFile.getAbsolutePath());
                xsdService.processXsd(true);

                // Test the exact IntelliSense data for FundsXML4
                List<XsdDocumentationService.MandatoryChildInfo> fundsXML4Children =
                        xsdService.getMandatoryChildElements("FundsXML4");

                System.out.println("=== FundsXML4 Mandatory Children ===");
                printMandatoryChildren(fundsXML4Children, 0);

                // Expected structure: Only ControlData, no attributes, no recursive nesting
                assertEquals(1, fundsXML4Children.size(), "FundsXML4 should have exactly 1 mandatory child");
                assertEquals("ControlData", fundsXML4Children.get(0).name(), "First child should be ControlData");
                assertTrue(fundsXML4Children.get(0).children().isEmpty(), "ControlData should have no nested children");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test should complete within 10 seconds");
    }

    @Test
    void testActualXMLGenerationWithFundsXML4() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                // Simulate the actual XML generation process that happens during IntelliSense
                xmlCodeEditor.setText("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<");

                // Position cursor after the opening bracket
                xmlCodeEditor.getCodeArea().moveTo(xmlCodeEditor.getText().length());

                // Simulate typing "FundsXML4" and triggering IntelliSense completion
                String elementName = "FundsXML4";

                // Get mandatory children using the same method as the actual IntelliSense
                List<String> mandatoryChildren = getMandatoryChildElements(elementName);

                System.out.println("=== Actual Mandatory Children from getMandatoryChildElements ===");
                for (int i = 0; i < mandatoryChildren.size(); i++) {
                    System.out.println(i + ": " + mandatoryChildren.get(i));
                }

                // Generate the actual XML structure that would be created
                String generatedXML = createElementWithMandatoryChildren(elementName, "", 0);
                System.out.println("=== Generated XML Structure ===");
                System.out.println(generatedXML);

                // Expected XML structure (what we want)
                String expectedXML = """
                        <FundsXML4>
                            <ControlData>
                                <UniqueDocumentID></UniqueDocumentID>
                                <DocumentGenerated></DocumentGenerated>
                                <ContentDate></ContentDate>
                                <DataSupplier>
                                    <SystemCountry></SystemCountry>
                                    <Short></Short>
                                    <Name></Name>
                                </DataSupplier>
                            </ControlData>
                        </FundsXML4>""";

                System.out.println("=== Expected XML Structure ===");
                System.out.println(expectedXML);

                // Verify that attributes are not included
                assertFalse(generatedXML.contains("@language"), "Generated XML should not contain @language attribute");
                assertFalse(generatedXML.contains("@maxLen"), "Generated XML should not contain @maxLen attribute");
                assertFalse(generatedXML.contains("<language>"), "Generated XML should not contain language element");
                assertFalse(generatedXML.contains("<maxLen>"), "Generated XML should not contain maxLen element");

                // Verify that choice elements are not included (Type is optional)
                assertFalse(generatedXML.contains("<Type>"), "Generated XML should not contain optional Type element");
                assertFalse(generatedXML.contains("<ListedType>"), "Generated XML should not contain ListedType element");
                assertFalse(generatedXML.contains("<UnlistedType>"), "Generated XML should not contain UnlistedType element");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test should complete within 10 seconds");
    }

    // Helper methods that mirror the actual methods in XmlCodeEditor
    private List<String> getMandatoryChildElements(String elementName) {
        try {
            if (xmlEditor != null && xmlEditor.getXsdDocumentationService() != null) {
                var xsdService = xmlEditor.getXsdDocumentationService();
                var mandatoryChildInfos = xsdService.getMandatoryChildElements(elementName, null);

                return mandatoryChildInfos.stream()
                        .map(info -> info.name())
                        .distinct()
                        .toList();
            }
        } catch (Exception e) {
            System.err.println("Error getting mandatory children: " + e.getMessage());
        }
        return List.of();
    }

    private String createElementWithMandatoryChildren(String elementName, String baseIndentation, int depth) {
        // Prevent infinite recursion
        if (depth > 10) {
            return baseIndentation + "<" + elementName + "></" + elementName + ">";
        }

        // Get mandatory children for this element
        List<String> mandatoryChildren = getMandatoryChildElements(elementName);

        StringBuilder elementBuilder = new StringBuilder();
        elementBuilder.append(baseIndentation).append("<").append(elementName).append(">");

        if (!mandatoryChildren.isEmpty()) {
            // Element has mandatory children - create them recursively
            String childIndentation = baseIndentation + "    "; // 4 spaces

            for (String childElement : mandatoryChildren) {
                elementBuilder.append("\n");
                elementBuilder.append(createElementWithMandatoryChildren(childElement, childIndentation, depth + 1));
            }

            // Add closing tag with proper indentation
            elementBuilder.append("\n").append(baseIndentation);
        }

        elementBuilder.append("</").append(elementName).append(">");
        return elementBuilder.toString();
    }

    private void printMandatoryChildren(List<XsdDocumentationService.MandatoryChildInfo> children, int depth) {
        String indent = "  ".repeat(depth);
        for (XsdDocumentationService.MandatoryChildInfo child : children) {
            System.out.println(indent + "- " + child.name() + " (minOccurs=" + child.minOccurs() +
                    ", children=" + child.children().size() + ")");
            if (!child.children().isEmpty()) {
                printMandatoryChildren(child.children(), depth + 1);
            }
        }
    }
}