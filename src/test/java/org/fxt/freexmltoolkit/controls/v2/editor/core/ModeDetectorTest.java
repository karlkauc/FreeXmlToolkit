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

package org.fxt.freexmltoolkit.controls.v2.editor.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModeDetector")
class ModeDetectorTest {

    @Nested
    @DisplayName("detectMode with null/empty content")
    class NullEmptyContentTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Returns XML_WITHOUT_XSD for null/empty/blank content without XSD")
        void returnsXmlWithoutXsdForEmptyContent(String content) {
            EditorMode mode = ModeDetector.detectMode(content, false);
            assertEquals(EditorMode.XML_WITHOUT_XSD, mode);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Returns XML_WITH_XSD for null/empty/blank content with XSD")
        void returnsXmlWithXsdForEmptyContentWithXsd(String content) {
            EditorMode mode = ModeDetector.detectMode(content, true);
            assertEquals(EditorMode.XML_WITH_XSD, mode);
        }
    }

    @Nested
    @DisplayName("XSLT Detection")
    class XsltDetectionTests {

        @Test
        @DisplayName("Detects xsl:stylesheet with namespace")
        void detectsXslStylesheetWithNamespace() {
            String xslt = """
                <?xml version="1.0"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <html/>
                    </xsl:template>
                </xsl:stylesheet>
                """;

            EditorMode mode = ModeDetector.detectMode(xslt, false);
            assertEquals(EditorMode.XSLT, mode);
        }

        @Test
        @DisplayName("Detects xsl:transform with namespace")
        void detectsXslTransformWithNamespace() {
            String xslt = """
                <?xml version="1.0"?>
                <xsl:transform version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="xml"/>
                </xsl:transform>
                """;

            EditorMode mode = ModeDetector.detectMode(xslt, false);
            assertEquals(EditorMode.XSLT, mode);
        }

        @Test
        @DisplayName("Detects stylesheet without prefix")
        void detectsStylesheetWithoutPrefix() {
            String xslt = """
                <stylesheet version="1.0" xmlns="http://www.w3.org/1999/XSL/Transform">
                    <template match="/"/>
                </stylesheet>
                """;

            // Without prefix, it should check the default namespace
            EditorMode mode = ModeDetector.detectMode(xslt, false);
            assertEquals(EditorMode.XSLT, mode);
        }

        @Test
        @DisplayName("Detects XSLT even with XSD flag")
        void detectsXsltEvenWithXsdFlag() {
            String xslt = """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                </xsl:stylesheet>
                """;

            EditorMode mode = ModeDetector.detectMode(xslt, true);
            assertEquals(EditorMode.XSLT, mode);
        }
    }

    @Nested
    @DisplayName("XSL-FO Detection")
    class XslFoDetectionTests {

        @Test
        @DisplayName("Detects fo:root with namespace")
        void detectsFoRootWithNamespace() {
            String xslFo = """
                <?xml version="1.0"?>
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
                    <fo:layout-master-set>
                        <fo:simple-page-master master-name="simple"/>
                    </fo:layout-master-set>
                </fo:root>
                """;

            EditorMode mode = ModeDetector.detectMode(xslFo, false);
            assertEquals(EditorMode.XSL_FO, mode);
        }

        @Test
        @DisplayName("Detects root element without prefix with FO namespace")
        void detectsRootWithoutPrefixWithFoNamespace() {
            String xslFo = """
                <root xmlns="http://www.w3.org/1999/XSL/Format">
                    <layout-master-set/>
                </root>
                """;

            EditorMode mode = ModeDetector.detectMode(xslFo, false);
            assertEquals(EditorMode.XSL_FO, mode);
        }
    }

    @Nested
    @DisplayName("Schematron Detection")
    class SchematronDetectionTests {

        @Test
        @DisplayName("Detects sch:schema with ISO Schematron namespace")
        void detectsSchSchemaWithIsoNamespace() {
            String schematron = """
                <?xml version="1.0"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                    <sch:pattern>
                        <sch:rule context="//element">
                            <sch:assert test="@required">Missing required attribute</sch:assert>
                        </sch:rule>
                    </sch:pattern>
                </sch:schema>
                """;

            EditorMode mode = ModeDetector.detectMode(schematron, false);
            assertEquals(EditorMode.SCHEMATRON, mode);
        }

        @Test
        @DisplayName("Detects schema element with ISO Schematron namespace")
        void detectsSchemaWithIsoNamespace() {
            String schematron = """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern>
                        <rule context="//element"/>
                    </pattern>
                </schema>
                """;

            EditorMode mode = ModeDetector.detectMode(schematron, false);
            assertEquals(EditorMode.SCHEMATRON, mode);
        }

        @Test
        @DisplayName("Detects old ASCC Schematron namespace")
        void detectsOldAsccSchematronNamespace() {
            String schematron = """
                <schema xmlns="http://www.ascc.net/xml/schematron">
                    <pattern name="Test Pattern"/>
                </schema>
                """;

            EditorMode mode = ModeDetector.detectMode(schematron, false);
            assertEquals(EditorMode.SCHEMATRON, mode);
        }
    }

    @Nested
    @DisplayName("Plain XML Detection")
    class PlainXmlDetectionTests {

        @Test
        @DisplayName("Detects plain XML without XSD")
        void detectsPlainXmlWithoutXsd() {
            String xml = """
                <?xml version="1.0"?>
                <catalog>
                    <book id="1">
                        <title>Test Book</title>
                    </book>
                </catalog>
                """;

            EditorMode mode = ModeDetector.detectMode(xml, false);
            assertEquals(EditorMode.XML_WITHOUT_XSD, mode);
        }

        @Test
        @DisplayName("Detects plain XML with XSD flag")
        void detectsPlainXmlWithXsdFlag() {
            String xml = """
                <?xml version="1.0"?>
                <catalog>
                    <book id="1"/>
                </catalog>
                """;

            EditorMode mode = ModeDetector.detectMode(xml, true);
            assertEquals(EditorMode.XML_WITH_XSD, mode);
        }

        @Test
        @DisplayName("XML with schema element but wrong namespace is plain XML")
        void xmlWithSchemaElementButWrongNamespace() {
            String xml = """
                <schema xmlns="http://example.com/myschema">
                    <element name="test"/>
                </schema>
                """;

            EditorMode mode = ModeDetector.detectMode(xml, false);
            assertEquals(EditorMode.XML_WITHOUT_XSD, mode);
        }
    }

    @Nested
    @DisplayName("XML Declaration and Comments")
    class XmlDeclarationAndCommentsTests {

        @Test
        @DisplayName("Handles XML with declaration")
        void handlesXmlWithDeclaration() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root/>
                """;

            EditorMode mode = ModeDetector.detectMode(xml, false);
            assertEquals(EditorMode.XML_WITHOUT_XSD, mode);
        }

        @Test
        @DisplayName("Handles XML with comments before root")
        void handlesXmlWithCommentsBeforeRoot() {
            String xml = """
                <?xml version="1.0"?>
                <!-- This is a comment -->
                <root/>
                """;

            EditorMode mode = ModeDetector.detectMode(xml, false);
            assertEquals(EditorMode.XML_WITHOUT_XSD, mode);
        }

        @Test
        @DisplayName("Detects XSLT after comments")
        void detectsXsltAfterComments() {
            String xslt = """
                <?xml version="1.0"?>
                <!-- XSLT stylesheet -->
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                </xsl:stylesheet>
                """;

            EditorMode mode = ModeDetector.detectMode(xslt, false);
            assertEquals(EditorMode.XSLT, mode);
        }
    }

    @Nested
    @DisplayName("detectDocumentType")
    class DetectDocumentTypeTests {

        @Test
        @DisplayName("Returns 'XSLT' for XSLT document")
        void returnsXsltForXsltDocument() {
            String xslt = """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"/>
                """;

            assertEquals("XSLT", ModeDetector.detectDocumentType(xslt));
        }

        @Test
        @DisplayName("Returns 'XSL-FO' for XSL-FO document")
        void returnsXslFoForXslFoDocument() {
            String xslFo = """
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format"/>
                """;

            assertEquals("XSL-FO", ModeDetector.detectDocumentType(xslFo));
        }

        @Test
        @DisplayName("Returns 'Schematron' for Schematron document")
        void returnsSchematronForSchematronDocument() {
            String schematron = """
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron"/>
                """;

            assertEquals("Schematron", ModeDetector.detectDocumentType(schematron));
        }

        @Test
        @DisplayName("Returns 'XML' for plain XML")
        void returnsXmlForPlainXml() {
            String xml = "<root><child/></root>";

            assertEquals("XML", ModeDetector.detectDocumentType(xml));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Handles malformed XML gracefully")
        void handlesMalformedXmlGracefully() {
            String malformed = "This is not XML at all";

            // Should not throw, just return default
            EditorMode mode = ModeDetector.detectMode(malformed, false);
            assertEquals(EditorMode.XML_WITHOUT_XSD, mode);
        }

        @Test
        @DisplayName("Handles XML with only declaration")
        void handlesXmlWithOnlyDeclaration() {
            String xml = "<?xml version=\"1.0\"?>";

            EditorMode mode = ModeDetector.detectMode(xml, false);
            assertEquals(EditorMode.XML_WITHOUT_XSD, mode);
        }

        @Test
        @DisplayName("Handles namespaced elements without prefix correctly")
        void handlesNamespacedElementsWithoutPrefix() {
            String xml = """
                <stylesheet xmlns="http://www.w3.org/1999/XSL/Transform" version="1.0">
                </stylesheet>
                """;

            EditorMode mode = ModeDetector.detectMode(xml, false);
            assertEquals(EditorMode.XSLT, mode);
        }

        @Test
        @DisplayName("Handles element names with colons in attributes")
        void handlesElementNamesWithColonsInAttributes() {
            String xslt = """
                <xsl:stylesheet
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:fn="http://www.w3.org/2005/xpath-functions"
                    version="2.0">
                </xsl:stylesheet>
                """;

            EditorMode mode = ModeDetector.detectMode(xslt, false);
            assertEquals(EditorMode.XSLT, mode);
        }
    }

    @Nested
    @DisplayName("Real World Examples")
    class RealWorldExamplesTests {

        @Test
        @DisplayName("Detects FundsXML transformation XSLT")
        void detectsFundsXmlTransformationXslt() {
            String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet
                    version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                    xmlns:fxml="http://www.fundsxml.org/FundsXML4">

                    <xsl:output method="xml" indent="yes"/>

                    <xsl:template match="/">
                        <result>
                            <xsl:apply-templates/>
                        </result>
                    </xsl:template>
                </xsl:stylesheet>
                """;

            EditorMode mode = ModeDetector.detectMode(xslt, false);
            assertEquals(EditorMode.XSLT, mode);
        }

        @Test
        @DisplayName("Detects PDF generation XSL-FO")
        void detectsPdfGenerationXslFo() {
            String xslFo = """
                <?xml version="1.0" encoding="UTF-8"?>
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
                    <fo:layout-master-set>
                        <fo:simple-page-master master-name="A4" page-height="29.7cm" page-width="21cm">
                            <fo:region-body margin="2cm"/>
                        </fo:simple-page-master>
                    </fo:layout-master-set>
                    <fo:page-sequence master-reference="A4">
                        <fo:flow flow-name="xsl-region-body">
                            <fo:block>Hello World</fo:block>
                        </fo:flow>
                    </fo:page-sequence>
                </fo:root>
                """;

            EditorMode mode = ModeDetector.detectMode(xslFo, false);
            assertEquals(EditorMode.XSL_FO, mode);
        }

        @Test
        @DisplayName("Detects business rule Schematron")
        void detectsBusinessRuleSchematron() {
            String schematron = """
                <?xml version="1.0" encoding="UTF-8"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron"
                           queryBinding="xslt2">

                    <sch:title>FundsXML Business Rules</sch:title>

                    <sch:pattern id="fund-validation">
                        <sch:rule context="Fund">
                            <sch:assert test="ISIN">Fund must have ISIN</sch:assert>
                        </sch:rule>
                    </sch:pattern>
                </sch:schema>
                """;

            EditorMode mode = ModeDetector.detectMode(schematron, false);
            assertEquals(EditorMode.SCHEMATRON, mode);
        }
    }
}
