package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The namespace overview from the schema's own declarations, imports and includes; the XSD source
 * and sample values in the sections; the overview diagram of the whole schema; and page numbers
 * across the page-sequences the PDF is split into.
 */
class XsdDocumentationPdfExtrasTest {

    private static final String MAIN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:lib="urn:example:library" xmlns:addr="urn:example:address"
                       targetNamespace="urn:example:library" elementFormDefault="qualified">
                <xs:import namespace="urn:example:address" schemaLocation="address.xsd"/>
                <xs:include schemaLocation="common.xsd"/>
                <xs:element name="Library">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Title" type="xs:string" fixed="Main Library"/>
                            <xs:element name="Opened" type="xs:date"/>
                            <xs:element ref="addr:Address"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    private static final String ADDRESS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:example:address"
                       elementFormDefault="qualified">
                <xs:element name="Address" type="xs:string"/>
            </xs:schema>
            """;

    private static final String COMMON = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:example:library">
                <xs:simpleType name="Code"><xs:restriction base="xs:string"/></xs:simpleType>
            </xs:schema>
            """;

    private static String text;
    private static int pages;
    private static String bigSchemaText;

    @BeforeAll
    static void generate(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("address.xsd"), ADDRESS);
        Files.writeString(tmp.resolve("common.xsd"), COMMON);
        File main = tmp.resolve("library.xsd").toFile();
        Files.writeString(main.toPath(), MAIN);

        PdfDocumentationConfig config = new PdfDocumentationConfig();
        config.setIncludeSourceCode(true);
        File out = tmp.resolve("library.pdf").toFile();
        render(main, config, out);
        try (PDDocument document = Loader.loadPDF(out)) {
            pages = document.getNumberOfPages();
            text = new PDFTextStripper().getText(document).replace("​", "");
        }

        // A schema too big for an overview diagram on one page
        StringBuilder big = new StringBuilder("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
                + "<xs:element name=\"Big\"><xs:complexType><xs:sequence>");
        for (int i = 0; i <= XsdDocumentationPdfService.OVERVIEW_MAX_ELEMENTS; i++) {
            big.append("<xs:element name=\"E").append(i).append("\" type=\"xs:string\"/>");
        }
        big.append("</xs:sequence></xs:complexType></xs:element></xs:schema>");
        File bigXsd = tmp.resolve("big.xsd").toFile();
        Files.writeString(bigXsd.toPath(), big.toString());
        PdfDocumentationConfig lean = new PdfDocumentationConfig();
        lean.setElementReference(PdfDocumentationConfig.ElementReference.OFF);
        File bigOut = tmp.resolve("big.pdf").toFile();
        render(bigXsd, lean, bigOut);
        try (PDDocument document = Loader.loadPDF(bigOut)) {
            bigSchemaText = new PDFTextStripper().getText(document);
        }
    }

    private static void render(File xsd, PdfDocumentationConfig config, File out) throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.processXsd(false);
        XsdDocumentationPdfService pdf = new XsdDocumentationPdfService();
        pdf.setConfig(config);
        pdf.setImageService(new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap()));
        pdf.generatePdfDocumentation(out, service.xsdDocumentationData);
    }

    @Test
    void namespacesComeFromTheSchemaDeclarations() {
        assertTrue(Pattern.compile("lib\\s+urn:example:library\\s+Target namespace").matcher(text).find(), excerpt("Namespace URI"));
        assertTrue(Pattern.compile("addr\\s+urn:example:address\\s+Imported").matcher(text).find(), excerpt("Namespace URI"));
        assertTrue(text.contains("Imports and Includes"), "the references of the schema");
        assertTrue(Pattern.compile("import\\s+urn:example:address\\s+address.xsd").matcher(text).find(), excerpt("Imports and Includes"));
        assertTrue(Pattern.compile("include\\s+-\\s+common.xsd").matcher(text).find(), excerpt("Imports and Includes"));
    }

    @Test
    void sectionsShowTheirSourceAndProperties() {
        assertTrue(text.contains("XSD Source"), "the source is included when asked for");
        assertTrue(Pattern.compile("<xs:element[^>]*name=\"Title\"", Pattern.DOTALL).matcher(text).find(),
                "the declaration itself: " + excerpt("XSD Source"));
        assertTrue(Pattern.compile("Fixed\\s+Main Library").matcher(text).find(), "fixed values: " + excerpt("Title"));
        assertTrue(Pattern.compile("Sample\\s+Main Library").matcher(text).find(), "a sample value: " + excerpt("Title"));
    }

    @Test
    void smallSchemasGetAnOverviewOfTheWholeSchema() {
        assertTrue(text.contains("Overview of the Whole Schema"));
        assertTrue(bigSchemaText.contains("No overview of the whole schema"), "too big for one page");
        assertFalse(bigSchemaText.contains("Overview of the Whole Schema"));
    }

    @Test
    void pageNumbersCountAcrossTheWholeDocument() {
        assertTrue(text.contains("Page " + pages + " of " + pages), "the last page knows the total: " + pages);
    }

    private static String excerpt(String around) {
        int i = text.indexOf(around);
        return i < 0 ? "(not found)" : text.substring(i, Math.min(text.length(), i + 400)).replace('\n', ' ');
    }
}
