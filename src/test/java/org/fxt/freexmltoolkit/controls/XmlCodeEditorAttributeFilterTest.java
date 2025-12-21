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

import org.junit.jupiter.api.Disabled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for verifying that attributes are filtered out from mandatory children generation
 * and that complex choice elements don't create unwanted nested structures.
 *
 * @deprecated V1 XmlCodeEditor tests - will be removed with V1 migration
 */
@Deprecated(since = "2.0", forRemoval = true)
@ExtendWith(ApplicationExtension.class)
@Disabled("V1 XmlCodeEditor tests disabled - will be removed in V1 to V2 migration")
class XmlCodeEditorAttributeFilterTest {

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
        // Create a test XSD that simulates the FundsXML4 structure with attributes and complex choice elements
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
        testXsdFile = File.createTempFile("funds-xml4-test", ".xsd");
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
    void testAttributesAreFilteredOutFromMandatoryChildren() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                XsdDocumentationService xsdService = xmlEditor.getXsdDocumentationService();
                assertNotNull(xsdService, "XSD Documentation Service should be available");

                // Process the XSD to populate element map
                xsdService.setXsdFilePath(testXsdFile.getAbsolutePath());
                xsdService.processXsd(true);

                // Test mandatory child extraction for Name element (which has attributes)
                List<XsdDocumentationService.MandatoryChildInfo> mandatoryChildren =
                        xsdService.getMandatoryChildElements("Name", "FundsXML4/ControlData/DataSupplier");

                assertNotNull(mandatoryChildren, "Mandatory children list should not be null");

                // Name element should have NO mandatory children (attributes should be filtered out)
                assertTrue(mandatoryChildren.isEmpty(),
                        "Name element should have no mandatory children since attributes should be filtered out. Found: "
                                + mandatoryChildren.stream().map(c -> c.name()).toList());

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS), "Test should complete within 30 seconds");
    }

    @Test
    void testFundsXML4MandatoryChildrenStructure() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                XsdDocumentationService xsdService = xmlEditor.getXsdDocumentationService();
                assertNotNull(xsdService, "XSD Documentation Service should be available");

                // Process the XSD to populate element map
                xsdService.setXsdFilePath(testXsdFile.getAbsolutePath());
                xsdService.processXsd(true);

                // Test mandatory child extraction for FundsXML4 root element
                List<XsdDocumentationService.MandatoryChildInfo> fundsChildren =
                        xsdService.getMandatoryChildElements("FundsXML4");

                assertNotNull(fundsChildren, "FundsXML4 children list should not be null");
                assertEquals(1, fundsChildren.size(), "FundsXML4 should have exactly 1 mandatory child (ControlData)");
                assertEquals("ControlData", fundsChildren.get(0).name(), "First child should be ControlData");

                // Test ControlData children
                List<XsdDocumentationService.MandatoryChildInfo> controlDataChildren =
                        xsdService.getMandatoryChildElements("ControlData", "FundsXML4");

                assertEquals(4, controlDataChildren.size(), "ControlData should have 4 mandatory children");

                // Verify specific children exist but without nested children
                String[] expectedChildren = {"UniqueDocumentID", "DocumentGenerated", "ContentDate", "DataSupplier"};
                for (String expectedChild : expectedChildren) {
                    boolean found = controlDataChildren.stream()
                            .anyMatch(child -> child.name().equals(expectedChild));
                    assertTrue(found, "Should find mandatory child: " + expectedChild);
                }

                // Test DataSupplier children - should include Name but not Type (since Type is minOccurs="0")
                List<XsdDocumentationService.MandatoryChildInfo> dataSupplierChildren =
                        xsdService.getMandatoryChildElements("DataSupplier", "FundsXML4/ControlData");

                String[] expectedDataSupplierChildren = {"SystemCountry", "Short", "Name"};
                assertEquals(expectedDataSupplierChildren.length, dataSupplierChildren.size(),
                        "DataSupplier should have exactly " + expectedDataSupplierChildren.length + " mandatory children");

                for (String expectedChild : expectedDataSupplierChildren) {
                    boolean found = dataSupplierChildren.stream()
                            .anyMatch(child -> child.name().equals(expectedChild));
                    assertTrue(found, "Should find mandatory child in DataSupplier: " + expectedChild);
                }

                // Verify that optional Type element is NOT included in mandatory children
                boolean foundType = dataSupplierChildren.stream()
                        .anyMatch(child -> child.name().equals("Type"));
                assertFalse(foundType, "Type element should NOT be included as it's optional (minOccurs='0')");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS), "Test should complete within 30 seconds");
    }

    @Test
    void testNoRecursiveNestedChildrenCreated() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                XsdDocumentationService xsdService = xmlEditor.getXsdDocumentationService();
                assertNotNull(xsdService, "XSD Documentation Service should be available");

                // Process the XSD to populate element map
                xsdService.setXsdFilePath(testXsdFile.getAbsolutePath());
                xsdService.processXsd(true);

                // Test that mandatory children don't have recursive nested children
                List<XsdDocumentationService.MandatoryChildInfo> fundsChildren =
                        xsdService.getMandatoryChildElements("FundsXML4");

                for (XsdDocumentationService.MandatoryChildInfo child : fundsChildren) {
                    assertTrue(child.children().isEmpty(),
                            "Child element '" + child.name() + "' should have no nested children to prevent recursive expansion");
                }

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS), "Test should complete within 30 seconds");
    }
}