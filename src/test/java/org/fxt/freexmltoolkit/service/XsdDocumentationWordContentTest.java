package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the Word documentation says and how it is built: the schema diagram's root, restriction
 * text, usage counts, declared types, full values, a complete index, real heading styles for the
 * table of contents, and diagrams in their own aspect ratio.
 */
class XsdDocumentationWordContentTest {

    private static final String LONG_DOC = "This description is deliberately much longer than forty characters "
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

    private static XWPFDocument document;
    private static String text;
    private static String settingsXml;

    @BeforeAll
    static void generate(@TempDir Path tmp) throws Exception {
        File xsd = tmp.resolve("library.xsd").toFile();
        Files.writeString(xsd.toPath(), XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.processXsd(false);

        XsdDocumentationWordService word = new XsdDocumentationWordService();
        WordDocumentationConfig config = new WordDocumentationConfig();
        config.setIncludeElementDiagrams(true);
        word.setConfig(config);
        word.setImageService(new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap()));
        File out = tmp.resolve("library.docx").toFile();
        word.generateWordDocumentation(out, service.xsdDocumentationData);

        try (ZipFile zip = new ZipFile(out)) {
            settingsXml = new String(zip.getInputStream(zip.getEntry("word/settings.xml")).readAllBytes());
        }
        document = new XWPFDocument(new FileInputStream(out));
        StringBuilder all = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            all.append(paragraph.getText()).append('\n');
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                row.getTableCells().forEach(cell -> cells.add(cell.getText()));
                all.append(String.join(" | ", cells)).append('\n');
            }
        }
        text = all.toString();
    }

    @AfterAll
    static void close() throws Exception {
        document.close();
    }

    @Test
    void headingsAreHeadingsSoTheTableOfContentsWorks() {
        assertTrue(document.getStyles().styleExist("Heading1"), "Heading1 is defined");
        assertTrue(document.getStyles().styleExist("Heading2"), "Heading2 is defined");
        XWPFParagraph dictionary = document.getParagraphs().stream()
                .filter(p -> "Data Dictionary".equals(p.getText())).findFirst().orElseThrow();
        assertEquals("Heading1", dictionary.getStyle());
        assertTrue(settingsXml.contains("updateFields"), "Word updates the table of contents on opening");
    }

    @Test
    void schemaDiagramStartsAtTheRootElement() {
        assertTrue(text.contains("Figure 1: Structure of Library"), "the root is Library, not a container");
    }

    @Test
    void tablesShowFullCorrectValues() {
        assertFalse(text.contains("<br"), "no HTML in the restrictions");
        assertTrue(text.contains("MaxLength: 256") || text.contains("maxLength"), "the facet as text");
        assertTrue(text.contains("BaseType | - | 1"), "BaseType is used as a base");
        assertTrue(text.contains("PriceType | xs:decimal | 1"), "Price uses PriceType");
        assertTrue(text.contains("/Library/Price | PriceType |"), "the declared type, not its base");
        assertTrue(text.contains(LONG_DOC), "descriptions are not cut");
    }

    @Test
    void indexListsElementsAndAttributesWithTheirPaths() {
        assertTrue(text.contains("@ccy | Attribute | /Library/Price/@ccy"), "attributes, with their path");
        assertTrue(text.contains("Title | Element | /Library/Title"), "local elements");
        assertFalse(text.contains("SEQUENCE_"), "no internal container names");
        assertTrue(text.contains("XML Schema"), "namespaces say what they are");
    }

    @Test
    void diagramsKeepTheirAspectRatio() throws Exception {
        List<XWPFPicture> pictures = new ArrayList<>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                pictures.addAll(run.getEmbeddedPictures());
            }
        }
        assertFalse(pictures.isEmpty(), "diagrams are embedded");
        for (XWPFPicture picture : pictures) {
            var image = ImageIO.read(new ByteArrayInputStream(picture.getPictureData().getData()));
            double imageRatio = (double) image.getWidth() / image.getHeight();
            double shownRatio = picture.getWidth() / picture.getDepth();
            assertEquals(imageRatio, shownRatio, imageRatio * 0.02, picture.getPictureData().getFileName());
        }
    }
}
