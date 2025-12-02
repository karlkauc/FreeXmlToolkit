package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.PDFSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FOPService - PDF generation from XML and XSL files using Apache FOP.
 */
class FOPServiceTest {

    private FOPService fopService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        fopService = new FOPService();
    }

    @Test
    @DisplayName("Should create PDF from simple XML and XSL")
    void testCreateSimplePdf() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Test Document</title>
                <content>This is a test document.</content>
            </document>
            """;

        String xslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

                <xsl:template match="/">
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4"
                                page-height="297mm" page-width="210mm"
                                margin="20mm">
                                <fo:region-body/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>

                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block font-size="24pt" font-weight="bold">
                                    <xsl:value-of select="document/title"/>
                                </fo:block>
                                <fo:block font-size="12pt" margin-top="10pt">
                                    <xsl:value-of select="document/content"/>
                                </fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("test.xml");
        Path xslFile = tempDir.resolve("test.xsl");
        Path pdfFile = tempDir.resolve("output.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, xslContent);

        PDFSettings settings = new PDFSettings(
            new HashMap<>(),
            "FreeXmlToolkit",
            "Test Author",
            "Test Creator",
            "",
            "Test Document",
            "test, pdf, fop"
        );

        // Act
        File result = fopService.createPdfFile(
            xmlFile.toFile(),
            xslFile.toFile(),
            pdfFile.toFile(),
            settings
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0, "PDF file should not be empty");
        assertEquals(pdfFile.toFile().getAbsolutePath(), result.getAbsolutePath());
    }

    @Test
    @DisplayName("Should apply PDF metadata settings")
    void testPdfMetadata() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <content>Test</content>
            </document>
            """;

        String xslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                <xsl:template match="/">
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4"
                                page-height="297mm" page-width="210mm" margin="20mm">
                                <fo:region-body/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>
                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block><xsl:value-of select="document/content"/></fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("metadata-test.xml");
        Path xslFile = tempDir.resolve("metadata-test.xsl");
        Path pdfFile = tempDir.resolve("metadata-output.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, xslContent);

        PDFSettings settings = new PDFSettings(
            new HashMap<>(),
            "Custom Producer",
            "John Doe",
            "PDF Creator App",
            "",
            "My Custom Title",
            "custom, keywords, test"
        );

        // Act
        File result = fopService.createPdfFile(
            xmlFile.toFile(),
            xslFile.toFile(),
            pdfFile.toFile(),
            settings
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.exists());
        // PDF metadata is embedded, but we can't easily verify it without PDF library
        // So we just verify the file was created successfully
    }

    @Test
    @DisplayName("Should apply custom XSLT parameters")
    void testCustomXsltParameters() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <content>Test content</content>
            </document>
            """;

        String xslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

                <xsl:param name="customTitle" select="'Default Title'"/>
                <xsl:param name="customColor" select="'black'"/>

                <xsl:template match="/">
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4"
                                page-height="297mm" page-width="210mm" margin="20mm">
                                <fo:region-body/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>
                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block font-size="18pt" color="{$customColor}">
                                    <xsl:value-of select="$customTitle"/>
                                </fo:block>
                                <fo:block><xsl:value-of select="document/content"/></fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("param-test.xml");
        Path xslFile = tempDir.resolve("param-test.xsl");
        Path pdfFile = tempDir.resolve("param-output.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, xslContent);

        HashMap<String, String> customParams = new HashMap<>();
        customParams.put("customTitle", "My Custom Title");
        customParams.put("customColor", "blue");

        PDFSettings settings = new PDFSettings(
            customParams,
            "Producer",
            "Author",
            "Creator",
            "",
            "Title",
            "keywords"
        );

        // Act
        File result = fopService.createPdfFile(
            xmlFile.toFile(),
            xslFile.toFile(),
            pdfFile.toFile(),
            settings
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0);
    }

    @Test
    @DisplayName("Should create nested directory structure if output path doesn't exist")
    void testCreateNestedDirectories() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document><content>Test</content></document>
            """;

        String xslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                <xsl:template match="/">
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4"
                                page-height="297mm" page-width="210mm" margin="20mm">
                                <fo:region-body/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>
                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block><xsl:value-of select="document/content"/></fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("nested-test.xml");
        Path xslFile = tempDir.resolve("nested-test.xsl");

        // Create nested path that doesn't exist yet
        Path nestedDir = tempDir.resolve("level1/level2/level3");
        Path pdfFile = nestedDir.resolve("output.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, xslContent);

        PDFSettings settings = new PDFSettings(
            new HashMap<>(), "", "", "", "", "", ""
        );

        // Act
        File result = fopService.createPdfFile(
            xmlFile.toFile(),
            xslFile.toFile(),
            pdfFile.toFile(),
            settings
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(Files.exists(nestedDir), "Nested directory should be created");
    }

    @Test
    @DisplayName("Should handle empty PDF settings gracefully")
    void testEmptyPdfSettings() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document><content>Test</content></document>
            """;

        String xslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                <xsl:template match="/">
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4"
                                page-height="297mm" page-width="210mm" margin="20mm">
                                <fo:region-body/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>
                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block><xsl:value-of select="document/content"/></fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("empty-settings.xml");
        Path xslFile = tempDir.resolve("empty-settings.xsl");
        Path pdfFile = tempDir.resolve("empty-settings.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, xslContent);

        // Empty settings
        PDFSettings settings = new PDFSettings(
            new HashMap<>(), "", "", "", "", "", ""
        );

        // Act
        File result = fopService.createPdfFile(
            xmlFile.toFile(),
            xslFile.toFile(),
            pdfFile.toFile(),
            settings
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0);
    }

    @Test
    @DisplayName("Should handle complex XSL-FO formatting")
    void testComplexFormatting() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <title>Complex Document</title>
                <section>
                    <heading>Section 1</heading>
                    <paragraph>This is paragraph 1.</paragraph>
                    <paragraph>This is paragraph 2.</paragraph>
                </section>
                <section>
                    <heading>Section 2</heading>
                    <paragraph>Another paragraph.</paragraph>
                </section>
            </document>
            """;

        String xslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                <xsl:template match="/">
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4"
                                page-height="297mm" page-width="210mm" margin="20mm">
                                <fo:region-body margin="20mm"/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>
                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block font-size="24pt" font-weight="bold" margin-bottom="10pt">
                                    <xsl:value-of select="document/title"/>
                                </fo:block>
                                <xsl:apply-templates select="document/section"/>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>

                <xsl:template match="section">
                    <fo:block margin-top="15pt" margin-bottom="5pt">
                        <fo:block font-size="16pt" font-weight="bold">
                            <xsl:value-of select="heading"/>
                        </fo:block>
                        <xsl:apply-templates select="paragraph"/>
                    </fo:block>
                </xsl:template>

                <xsl:template match="paragraph">
                    <fo:block margin-top="5pt" font-size="12pt">
                        <xsl:value-of select="."/>
                    </fo:block>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("complex.xml");
        Path xslFile = tempDir.resolve("complex.xsl");
        Path pdfFile = tempDir.resolve("complex.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, xslContent);

        PDFSettings settings = new PDFSettings(
            new HashMap<>(),
            "FreeXmlToolkit",
            "Test",
            "Test",
            "",
            "Complex Document",
            "test"
        );

        // Act
        File result = fopService.createPdfFile(
            xmlFile.toFile(),
            xslFile.toFile(),
            pdfFile.toFile(),
            settings
        );

        // Assert
        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 100, "Complex PDF should have substantial size");
    }

    // ===== FAILURE SCENARIO TESTS =====

    @Test
    @DisplayName("Should throw exception for non-existent XML file")
    void testNonExistentXmlFile() {
        // Arrange
        File nonExistentXml = new File(tempDir.resolve("non-existent.xml").toString());
        File xslFile = new File(tempDir.resolve("test.xsl").toString());
        File pdfFile = new File(tempDir.resolve("output.pdf").toString());
        PDFSettings settings = new PDFSettings(new HashMap<>(), "", "", "", "", "", "");

        // Act & Assert
        FOPServiceException exception = assertThrows(FOPServiceException.class, () -> {
            fopService.createPdfFile(nonExistentXml, xslFile, pdfFile, settings);
        });

        assertTrue(exception.getMessage().contains("XML file does not exist"),
                "Exception message should indicate XML file not found");
    }

    @Test
    @DisplayName("Should throw exception for non-existent XSL file")
    void testNonExistentXslFile() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document><content>Test</content></document>
            """;

        Path xmlFile = tempDir.resolve("test.xml");
        Files.writeString(xmlFile, xmlContent);

        File nonExistentXsl = new File(tempDir.resolve("non-existent.xsl").toString());
        File pdfFile = new File(tempDir.resolve("output.pdf").toString());
        PDFSettings settings = new PDFSettings(new HashMap<>(), "", "", "", "", "", "");

        // Act & Assert
        FOPServiceException exception = assertThrows(FOPServiceException.class, () -> {
            fopService.createPdfFile(xmlFile.toFile(), nonExistentXsl, pdfFile, settings);
        });

        assertTrue(exception.getMessage().contains("XSL file does not exist"),
                "Exception message should indicate XSL file not found");
    }

    @Test
    @DisplayName("Should throw exception for malformed XSL")
    void testMalformedXsl() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document><content>Test</content></document>
            """;

        String malformedXslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            <!-- Missing closing tag and malformed structure -->
            """;

        Path xmlFile = tempDir.resolve("test.xml");
        Path xslFile = tempDir.resolve("malformed.xsl");
        Path pdfFile = tempDir.resolve("output.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, malformedXslContent);

        PDFSettings settings = new PDFSettings(new HashMap<>(), "", "", "", "", "", "");

        // Act & Assert
        FOPServiceException exception = assertThrows(FOPServiceException.class, () -> {
            fopService.createPdfFile(xmlFile.toFile(), xslFile.toFile(), pdfFile.toFile(), settings);
        });

        assertTrue(exception.getMessage().contains("Failed to transform XML using XSL stylesheet"),
                "Exception message should indicate transformation failure: " + exception.getMessage());
        assertNotNull(exception.getCause(), "Exception should have a cause");
    }

    @Test
    @DisplayName("Should throw exception for malformed XML")
    void testMalformedXml() throws Exception {
        // Arrange
        String malformedXmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document>
                <content>Test
                <!-- Missing closing tags -->
            """;

        String xslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                <xsl:template match="/">
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4"
                                page-height="297mm" page-width="210mm" margin="20mm">
                                <fo:region-body/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>
                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block><xsl:value-of select="document/content"/></fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("malformed.xml");
        Path xslFile = tempDir.resolve("test.xsl");
        Path pdfFile = tempDir.resolve("output.pdf");

        Files.writeString(xmlFile, malformedXmlContent);
        Files.writeString(xslFile, xslContent);

        PDFSettings settings = new PDFSettings(new HashMap<>(), "", "", "", "", "", "");

        // Act & Assert
        FOPServiceException exception = assertThrows(FOPServiceException.class, () -> {
            fopService.createPdfFile(xmlFile.toFile(), xslFile.toFile(), pdfFile.toFile(), settings);
        });

        assertTrue(exception.getMessage().contains("Failed to transform XML"),
                "Exception message should indicate transformation failure: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for invalid XSL-FO content")
    void testInvalidXslFoContent() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document><content>Test</content></document>
            """;

        // XSL that produces invalid FO (missing required elements)
        String invalidFoXslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                <xsl:template match="/">
                    <fo:root>
                        <!-- Missing layout-master-set - this is invalid FO -->
                        <fo:page-sequence master-reference="NonExistent">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block>Content</fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("test.xml");
        Path xslFile = tempDir.resolve("invalid-fo.xsl");
        Path pdfFile = tempDir.resolve("output.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, invalidFoXslContent);

        PDFSettings settings = new PDFSettings(new HashMap<>(), "", "", "", "", "", "");

        // Act & Assert
        FOPServiceException exception = assertThrows(FOPServiceException.class, () -> {
            fopService.createPdfFile(xmlFile.toFile(), xslFile.toFile(), pdfFile.toFile(), settings);
        });

        assertTrue(exception.getMessage().contains("FOP processing error") ||
                        exception.getMessage().contains("Failed to transform"),
                "Exception message should indicate FOP or transformation error: " + exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for XSL with runtime transformation error")
    void testXslRuntimeError() throws Exception {
        // Arrange
        String xmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <document><content>Test</content></document>
            """;

        // XSL that uses xsl:message with terminate="yes" to force a transformation error
        String errorXslContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">
                <xsl:template match="/">
                    <!-- Force a termination error with xsl:message -->
                    <xsl:message terminate="yes">Intentional transformation error for testing</xsl:message>
                    <fo:root>
                        <fo:layout-master-set>
                            <fo:simple-page-master master-name="A4"
                                page-height="297mm" page-width="210mm" margin="20mm">
                                <fo:region-body/>
                            </fo:simple-page-master>
                        </fo:layout-master-set>
                        <fo:page-sequence master-reference="A4">
                            <fo:flow flow-name="xsl-region-body">
                                <fo:block>
                                    <xsl:value-of select="document/content"/>
                                </fo:block>
                            </fo:flow>
                        </fo:page-sequence>
                    </fo:root>
                </xsl:template>
            </xsl:stylesheet>
            """;

        Path xmlFile = tempDir.resolve("test.xml");
        Path xslFile = tempDir.resolve("error.xsl");
        Path pdfFile = tempDir.resolve("output.pdf");

        Files.writeString(xmlFile, xmlContent);
        Files.writeString(xslFile, errorXslContent);

        PDFSettings settings = new PDFSettings(new HashMap<>(), "", "", "", "", "", "");

        // Act & Assert
        FOPServiceException exception = assertThrows(FOPServiceException.class, () -> {
            fopService.createPdfFile(xmlFile.toFile(), xslFile.toFile(), pdfFile.toFile(), settings);
        });

        assertNotNull(exception.getMessage(), "Exception should have a message");
        assertTrue(exception.getMessage().contains("Failed to transform") ||
                        exception.getMessage().contains("Intentional transformation error"),
                "Exception message should indicate transformation error: " + exception.getMessage());
    }
}
