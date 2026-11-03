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
 * Checks what the PDF documentation actually says: the schema diagram's root, the restriction
 * text, the index paths, the type usage counts and that nothing is cut off.
 */
class XsdDocumentationPdfContentTest {

    private static final String LONG_DOC = "This description is deliberately much longer than one hundred characters "
            + "so that the data dictionary would have cut it off before; now it must appear in full.";

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:complexType name="BaseType">
                    <xs:sequence>
                        <xs:element name="v" type="xs:int"/>
                    </xs:sequence>
                </xs:complexType>
                <xs:complexType name="DerivedType">
                    <xs:complexContent>
                        <xs:extension base="BaseType">
                            <xs:sequence>
                                <xs:element name="w" type="xs:int" minOccurs="0"/>
                            </xs:sequence>
                        </xs:extension>
                    </xs:complexContent>
                </xs:complexType>
                <xs:simpleType name="Text256">
                    <xs:restriction base="xs:string">
                        <xs:maxLength value="256"/>
                    </xs:restriction>
                </xs:simpleType>
                <xs:complexType name="PriceType">
                    <xs:simpleContent>
                        <xs:extension base="xs:decimal">
                            <xs:attribute name="ccy" type="xs:string"/>
                        </xs:extension>
                    </xs:simpleContent>
                </xs:complexType>
                <xs:element name="Library">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Title" type="Text256">
                                <xs:annotation><xs:documentation>%s</xs:documentation></xs:annotation>
                            </xs:element>
                            <xs:element name="Measure" type="DerivedType"/>
                            <xs:element name="Price" type="PriceType"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """.formatted(LONG_DOC);

    private static String text;

    @BeforeAll
    static void generate(@TempDir Path tmp) throws Exception {
        File xsd = tmp.resolve("library.xsd").toFile();
        Files.writeString(xsd.toPath(), XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.processXsd(false);

        XsdDocumentationPdfService pdf = new XsdDocumentationPdfService();
        pdf.setConfig(new PdfDocumentationConfig()); // defaults: schema diagram on, element diagrams off
        pdf.setImageService(new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap()));
        File out = tmp.resolve("library.pdf").toFile();
        pdf.generatePdfDocumentation(out, service.xsdDocumentationData);

        try (PDDocument document = Loader.loadPDF(out)) {
            // Break opportunities are zero-width spaces; drop them for the checks
            text = new PDFTextStripper().getText(document).replace("​", "");
        }
    }

    @Test
    void schemaDiagramStartsAtTheRootElementWithDefaultSettings() {
        assertTrue(text.contains("Schema Diagram"), "the schema diagram is there without element diagrams");
        assertTrue(Pattern.compile("root element:\\s*Library").matcher(text).find(), "root is Library");
        assertFalse(Pattern.compile("root element:\\s*SEQUENCE").matcher(text).find(), "not a container");
    }

    @Test
    void restrictionsAreTextNotHtml() {
        assertFalse(text.contains("<br"), "no HTML in the restrictions");
        assertTrue(text.contains("MaxLength: 256") || text.contains("Max Length: 256") || text.contains("maxLength"),
                "the facet is listed: " + excerpt("Title"));
    }

    @Test
    void indexShowsCleanPathsAndAttributes() {
        assertFalse(text.contains("SEQUENCE_"), "no internal container names in paths");
        assertTrue(Pattern.compile("@ccy\\s+Attribute").matcher(text).find(), "attributes are listed as such");
    }

    @Test
    void usageCountsIncludeDerivationsAndSimpleContentTypes() {
        assertTrue(Pattern.compile("BaseType\\s+-\\s+1\\b").matcher(text).find(), "DerivedType extends it: " + excerpt("BaseType"));
        assertTrue(Pattern.compile("PriceType\\s+xs:decimal\\s+1\\b").matcher(text).find(), "Price uses it: " + excerpt("PriceType"));
    }

    @Test
    void dataDictionaryShowsTheDeclaredType() {
        assertTrue(Pattern.compile("/Library/Price\\s+PriceType").matcher(text).find(),
                "the declared type, not its base: " + excerpt("/Library/Price"));
    }

    @Test
    void longDescriptionsAreNotCut() {
        String flat = text.replaceAll("\\s+", " ");
        assertTrue(flat.contains(LONG_DOC), "the whole description is printed");
    }

    private static String excerpt(String around) {
        int i = text.indexOf(around);
        return i < 0 ? "(not found)" : text.substring(i, Math.min(text.length(), i + 160)).replace('\n', ' ');
    }
}
