package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;

/**
 * The element and type sections of the Word documentation, built like the PDF's: a section per
 * element declaration and per global type with diagram, documentation in every language,
 * enumerations, identity constraints, assertions and usage; bookmarks, and links to them from the
 * tables; and the deduplicated data dictionary.
 */
class XsdDocumentationWordReferenceTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:simpleType name="Colour">
                    <xs:restriction base="xs:string">
                        <xs:enumeration value="red">
                            <xs:annotation><xs:documentation>Like a tomato.</xs:documentation></xs:annotation>
                        </xs:enumeration>
                        <xs:enumeration value="green"/>
                    </xs:restriction>
                </xs:simpleType>
                <xs:complexType name="AddressType">
                    <xs:sequence>
                        <xs:element name="Street" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
                <xs:complexType name="MeasureType">
                    <xs:sequence>
                        <xs:element name="v" type="xs:int"/>
                    </xs:sequence>
                    <xs:assert test="v &gt; 0"/>
                </xs:complexType>
                <xs:element name="Library">
                    <xs:annotation>
                        <xs:documentation xml:lang="en">A library of books.</xs:documentation>
                        <xs:documentation xml:lang="de">Eine Bibliothek mit Buechern.</xs:documentation>
                    </xs:annotation>
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Book" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="ISBN" type="xs:string"/>
                                        <xs:element name="Cover" type="Colour"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                            <xs:element name="HomeAddress" type="AddressType"/>
                            <xs:element name="WorkAddress" type="AddressType"/>
                            <xs:element name="Measure" type="MeasureType"/>
                        </xs:sequence>
                    </xs:complexType>
                    <xs:key name="BookKey">
                        <xs:selector xpath="Book"/>
                        <xs:field xpath="ISBN"/>
                    </xs:key>
                </xs:element>
            </xs:schema>
            """;

    private static XWPFDocument document;
    private static String text;
    private static List<String> sectionHeadings;
    private static Set<String> bookmarks;
    private static List<String> linkAnchors;
    private static String dedupedText;

    @BeforeAll
    static void generate(@TempDir Path tmp) throws Exception {
        File xsd = tmp.resolve("library.xsd").toFile();
        Files.writeString(xsd.toPath(), XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.processXsd(false);

        File out = tmp.resolve("library.docx").toFile();
        render(service, false, out);
        document = new XWPFDocument(new FileInputStream(out));
        text = textOf(document);
        sectionHeadings = new ArrayList<>();
        bookmarks = new HashSet<>();
        linkAnchors = new ArrayList<>();
        for (XWPFParagraph paragraph : allParagraphs(document)) {
            if ("Heading3".equals(paragraph.getStyle())) {
                sectionHeadings.add(paragraph.getText());
            }
            for (CTBookmark bookmark : paragraph.getCTP().getBookmarkStartList()) {
                bookmarks.add(bookmark.getName());
            }
            for (CTHyperlink link : paragraph.getCTP().getHyperlinkList()) {
                linkAnchors.add(link.getAnchor());
            }
        }

        File deduped = tmp.resolve("deduped.docx").toFile();
        render(service, true, deduped);
        try (XWPFDocument dedupedDocument = new XWPFDocument(new FileInputStream(deduped))) {
            dedupedText = textOf(dedupedDocument);
        }
    }

    private static void render(XsdDocumentationService service, boolean dedupe, File out) throws Exception {
        XsdDocumentationWordService word = new XsdDocumentationWordService();
        WordDocumentationConfig config = new WordDocumentationConfig();
        config.setElementReference(PdfDocumentationConfig.ElementReference.DECLARATIONS);
        word.setConfig(config);
        word.setDeduplicateDataDictionaryByType(dedupe);
        word.setImageService(new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap()));
        word.generateWordDocumentation(out, service.xsdDocumentationData);
    }

    @AfterAll
    static void close() throws Exception {
        document.close();
    }

    @Test
    void elementsAndTypesHaveSections() {
        assertTrue(sectionHeadings.contains("Library  Element"), "an element section: " + sectionHeadings);
        assertTrue(sectionHeadings.contains("AddressType  Complex Type"), "a complex type section: " + sectionHeadings);
        assertTrue(sectionHeadings.contains("Colour  Simple Type"), "a simple type section: " + sectionHeadings);
        assertEquals(1, sectionHeadings.stream().filter(h -> h.startsWith("Street ")).count(),
                "one section for a declaration used at two paths");
        assertTrue(text.contains("Used at 2 paths"), "its other path is named");
    }

    @Test
    void sectionsHoldTheDetails() {
        assertTrue(text.contains("[en] A library of books."), "every language");
        assertTrue(text.contains("[de] Eine Bibliothek mit Buechern."), "every language");
        assertTrue(text.contains("red - Like a tomato."), "values with their documentation");
        assertTrue(text.contains("Selector | Book"), "identity constraints in detail");
        assertTrue(text.contains("Assert | v > 0"), "assertions");
        assertTrue(text.contains("/Library/HomeAddress") && text.contains("Used In"), "where a type is used");
    }

    @Test
    void sectionsAreBookmarkedAndTablesLinkToThem() {
        assertFalse(linkAnchors.isEmpty(), "the tables link to sections");
        for (String anchor : linkAnchors) {
            assertTrue(bookmarks.contains(anchor), "every link has its bookmark: " + anchor);
        }
        assertTrue(bookmarks.size() >= sectionHeadings.size(), "a bookmark per section");
    }

    @Test
    void theDictionaryCanListEachNamedTypeOnce() {
        assertTrue(text.contains("/Library/WorkAddress | AddressType"), "without the option every occurrence");
        assertFalse(dedupedText.contains("/Library/WorkAddress | AddressType"), "with it, the second is left out");
    }

    private static List<XWPFParagraph> allParagraphs(XWPFDocument document) {
        List<XWPFParagraph> paragraphs = new ArrayList<>(document.getParagraphs());
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    paragraphs.addAll(cell.getParagraphs());
                }
            }
        }
        return paragraphs;
    }

    private static String textOf(XWPFDocument document) {
        StringBuilder all = new StringBuilder();
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph paragraph) {
                all.append(paragraph.getText()).append('\n');
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    row.getTableCells().forEach(cell -> cells.add(cell.getText()));
                    all.append(String.join(" | ", cells)).append('\n');
                }
            }
        }
        return all.toString().replace("​", "");
    }
}
