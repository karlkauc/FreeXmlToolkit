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
 */

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XSLT output method detection in XmlService.
 * Verifies that the xsl:output/@method attribute is correctly detected from XSLT files.
 */
@DisplayName("XSLT Output Method Detection Tests")
class XmlServiceXsltOutputMethodTest {

    private XmlService xmlService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        xmlService = new XmlServiceImpl();
    }

    @Test
    @DisplayName("Should detect 'html' output method from xsl:stylesheet with xsl:output")
    void testDetectHtmlOutputMethod() throws IOException {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="html" indent="yes" encoding="UTF-8"/>
                    <xsl:template match="/">
                        <html><body><xsl:apply-templates/></body></html>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        File xsltFile = createTempXsltFile("test-html.xslt", xsltContent);
        xmlService.setCurrentXsltFile(xsltFile);

        assertEquals("html", xmlService.getXsltOutputMethod(),
                "Should detect 'html' output method from xsl:output");
    }

    @Test
    @DisplayName("Should detect 'xml' output method from xsl:stylesheet")
    void testDetectXmlOutputMethod() throws IOException {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
                    <xsl:template match="/">
                        <result><xsl:apply-templates/></result>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        File xsltFile = createTempXsltFile("test-xml.xslt", xsltContent);
        xmlService.setCurrentXsltFile(xsltFile);

        assertEquals("xml", xmlService.getXsltOutputMethod(),
                "Should detect 'xml' output method from xsl:output");
    }

    @Test
    @DisplayName("Should detect 'text' output method from xsl:stylesheet")
    void testDetectTextOutputMethod() throws IOException {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="text" encoding="UTF-8"/>
                    <xsl:template match="/">
                        <xsl:value-of select="."/>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        File xsltFile = createTempXsltFile("test-text.xslt", xsltContent);
        xmlService.setCurrentXsltFile(xsltFile);

        assertEquals("text", xmlService.getXsltOutputMethod(),
                "Should detect 'text' output method from xsl:output");
    }

    @Test
    @DisplayName("Should detect 'xhtml' output method from xsl:stylesheet")
    void testDetectXhtmlOutputMethod() throws IOException {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="xhtml" indent="yes" doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN"/>
                    <xsl:template match="/">
                        <html><body><xsl:apply-templates/></body></html>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        File xsltFile = createTempXsltFile("test-xhtml.xslt", xsltContent);
        xmlService.setCurrentXsltFile(xsltFile);

        assertEquals("xhtml", xmlService.getXsltOutputMethod(),
                "Should detect 'xhtml' output method from xsl:output");
    }

    @Test
    @DisplayName("Should detect output method from xsl:transform element (alternative root)")
    void testDetectOutputMethodFromXslTransform() throws IOException {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:transform version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="html" indent="yes"/>
                    <xsl:template match="/">
                        <html><body>Hello</body></html>
                    </xsl:template>
                </xsl:transform>
                """;

        File xsltFile = createTempXsltFile("test-transform.xslt", xsltContent);
        xmlService.setCurrentXsltFile(xsltFile);

        assertEquals("html", xmlService.getXsltOutputMethod(),
                "Should detect output method from xsl:transform element");
    }

    @Test
    @DisplayName("Should return null when no xsl:output element is present")
    void testNoOutputElement() throws IOException {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <result><xsl:apply-templates/></result>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        File xsltFile = createTempXsltFile("test-no-output.xslt", xsltContent);
        xmlService.setCurrentXsltFile(xsltFile);

        assertNull(xmlService.getXsltOutputMethod(),
                "Should return null when no xsl:output element is present");
    }

    @Test
    @DisplayName("Should return null when xsl:output has no method attribute")
    void testOutputElementWithoutMethodAttribute() throws IOException {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output indent="yes" encoding="UTF-8"/>
                    <xsl:template match="/">
                        <result><xsl:apply-templates/></result>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        File xsltFile = createTempXsltFile("test-no-method.xslt", xsltContent);
        xmlService.setCurrentXsltFile(xsltFile);

        assertNull(xmlService.getXsltOutputMethod(),
                "Should return null when xsl:output has no method attribute");
    }

    @Test
    @DisplayName("Should handle XSLT 3.0 stylesheets")
    void testXslt30Stylesheet() throws IOException {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:output method="json" indent="yes"/>
                    <xsl:template match="/">
                        <xsl:map>
                            <xsl:map-entry key="'data'" select="."/>
                        </xsl:map>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        File xsltFile = createTempXsltFile("test-xslt30.xslt", xsltContent);
        xmlService.setCurrentXsltFile(xsltFile);

        assertEquals("json", xmlService.getXsltOutputMethod(),
                "Should detect 'json' output method from XSLT 3.0 stylesheet");
    }

    /**
     * Helper method to create a temporary XSLT file.
     */
    private File createTempXsltFile(String fileName, String content) throws IOException {
        Path filePath = tempDir.resolve(fileName);
        Files.writeString(filePath, content);
        return filePath.toFile();
    }
}
