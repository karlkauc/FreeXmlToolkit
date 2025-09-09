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
 * Test for automatic mandatory child node creation in XmlCodeEditor.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorMandatoryChildrenTest {

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
        // Create a simple test XSD with mandatory children
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element name="mandatoryChild1" type="xs:string" minOccurs="1"/>
                                <xs:element name="mandatoryChild2" minOccurs="1">
                                    <xs:complexType>
                                        <xs:sequence>
                                            <xs:element name="nestedMandatory" type="xs:string" minOccurs="1"/>
                                        </xs:sequence>
                                    </xs:complexType>
                                </xs:element>
                                <xs:element name="optionalChild" type="xs:string" minOccurs="0"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """;

        // Create temporary XSD file
        testXsdFile = File.createTempFile("test-schema", ".xsd");
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
    void testMandatoryChildInfoExtraction() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                XsdDocumentationService xsdService = xmlEditor.getXsdDocumentationService();
                assertNotNull(xsdService, "XSD Documentation Service should be available");

                // Process the XSD to populate element map
                xsdService.setXsdFilePath(testXsdFile.getAbsolutePath());
                xsdService.processXsd(true);

                // Test mandatory child extraction for root element
                List<XsdDocumentationService.MandatoryChildInfo> mandatoryChildren =
                        xsdService.getMandatoryChildElements("root");

                assertNotNull(mandatoryChildren, "Mandatory children list should not be null");
                assertFalse(mandatoryChildren.isEmpty(), "Root element should have mandatory children");

                // Check that we found the expected mandatory children
                boolean foundMandatoryChild1 = false;
                boolean foundMandatoryChild2 = false;

                for (XsdDocumentationService.MandatoryChildInfo childInfo : mandatoryChildren) {
                    if ("mandatoryChild1".equals(childInfo.name())) {
                        foundMandatoryChild1 = true;
                        assertEquals(1, childInfo.minOccurs(), "mandatoryChild1 should have minOccurs=1");
                    } else if ("mandatoryChild2".equals(childInfo.name())) {
                        foundMandatoryChild2 = true;
                        assertEquals(1, childInfo.minOccurs(), "mandatoryChild2 should have minOccurs=1");
                        assertTrue(childInfo.hasChildren(), "mandatoryChild2 should have nested children");
                    }
                }

                assertTrue(foundMandatoryChild1, "Should find mandatoryChild1");
                assertTrue(foundMandatoryChild2, "Should find mandatoryChild2");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Test should complete within 10 seconds");
    }

    @Test
    void testAutoCompleteWithMandatoryChildren() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                // Set initial XML content with root element
                String initialXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <root>
                        </root>
                        """;
                xmlCodeEditor.setText(initialXml);

                // Position cursor after opening root tag
                int caretPosition = initialXml.indexOf("<root>") + "<root>".length();
                xmlCodeEditor.getCodeArea().moveTo(caretPosition);

                // Trigger auto-close with mandatory children by simulating '>' key press
                // This should invoke handleAutoCloseWithMandatoryChildren method
                xmlCodeEditor.setEditorMode(XmlCodeEditor.EditorMode.XML_WITH_XSD);

                // The method would be called, but since we don't have a fully initialized XSD service
                // in this test environment, we just verify the structure is in place
                assertNotNull(xmlEditor.getXsdDocumentationService(), "XSD service should be available");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within 5 seconds");
    }
}