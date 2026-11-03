package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The element reference of the PDF documentation: a section per element declaration with its
 * diagram, documentation in every language, restrictions with values, children, constraints and
 * assertions; links and bookmarks lead there.
 */
class XsdDocumentationPdfReferenceTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:simpleType name="Colour">
                    <xs:restriction base="xs:string">
                        <xs:enumeration value="red"/>
                        <xs:enumeration value="green"/>
                        <xs:enumeration value="blue"/>
                    </xs:restriction>
                </xs:simpleType>
                <xs:complexType name="AddressType">
                    <xs:sequence>
                        <xs:element name="Street" type="xs:string">
                            <xs:annotation><xs:documentation>The street line.</xs:documentation></xs:annotation>
                        </xs:element>
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
                                    <xs:attribute name="lang" type="xs:language" use="required"/>
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

    private static String text;
    private static int linkCount;
    private static int imageCount;
    private static List<String> referenceBookmarks;
    private static String textWithoutReference;

    @BeforeAll
    static void generate(@TempDir Path tmp) throws Exception {
        File xsd = tmp.resolve("library.xsd").toFile();
        Files.writeString(xsd.toPath(), XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.processXsd(false);

        File out = tmp.resolve("library.pdf").toFile();
        render(service, new PdfDocumentationConfig(), out); // defaults: reference per declaration, diagrams on
        try (PDDocument document = Loader.loadPDF(out)) {
            text = new PDFTextStripper().getText(document).replace("​", "");
            for (PDPage page : document.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink) {
                        linkCount++;
                    }
                }
                for (COSName name : page.getResources().getXObjectNames()) {
                    if (page.getResources().getXObject(name) instanceof PDImageXObject) {
                        imageCount++;
                    }
                }
            }
            referenceBookmarks = new ArrayList<>();
            for (PDOutlineItem item : document.getDocumentCatalog().getDocumentOutline().children()) {
                if ("Element Reference".equals(item.getTitle())) {
                    item.children().forEach(child -> referenceBookmarks.add(child.getTitle()));
                }
            }
        }

        PdfDocumentationConfig off = new PdfDocumentationConfig();
        off.setElementReference(PdfDocumentationConfig.ElementReference.OFF);
        File plain = tmp.resolve("plain.pdf").toFile();
        render(service, off, plain);
        try (PDDocument document = Loader.loadPDF(plain)) {
            textWithoutReference = new PDFTextStripper().getText(document);
        }
    }

    private static void render(XsdDocumentationService service, PdfDocumentationConfig config, File out) throws Exception {
        XsdDocumentationPdfService pdf = new XsdDocumentationPdfService();
        pdf.setConfig(config);
        pdf.setImageService(new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap()));
        pdf.generatePdfDocumentation(out, service.xsdDocumentationData);
    }

    @Test
    void everyElementHasASectionWithItsDetails() {
        assertTrue(text.contains("Element Reference"), "the reference is there");
        String library = section("Library");
        assertTrue(library.contains("[en] A library of books."), "every language: " + library);
        assertTrue(library.contains("[de] Eine Bibliothek mit Buechern."), "every language: " + library);
        assertTrue(library.contains("Identity Constraints") && library.contains("BookKey"), library);
        assertTrue(Pattern.compile("Selector\\s+Book").matcher(library).find(), "the selector: " + library);
        assertTrue(library.contains("Children and Attributes") && library.contains("HomeAddress"), library);
    }

    @Test
    void restrictionsListTheirValues() {
        String cover = section("Cover");
        assertTrue(cover.contains("Values (3)"), cover);
        assertTrue(cover.contains("red, green, blue"), cover);
    }

    @Test
    void attributesAndAssertionsHaveTheirOwnRows() {
        assertTrue(section("@lang").contains("required"), "an attribute's use: " + section("@lang"));
        String measure = section("Measure");
        assertTrue(measure.contains("Assertions (XSD 1.1)") && measure.contains("v > 0"), measure);
    }

    @Test
    void aDeclarationUsedAtSeveralPathsGetsOneSection() {
        assertEquals(1, count("\\bStreet\\s+Element\\b"), "one section for the Street declaration");
        assertTrue(section("Street").contains("Used at 2 paths"), section("Street"));
    }

    @Test
    void sectionsHaveDiagramsLinksAndBookmarks() {
        assertTrue(imageCount >= 3, "the schema diagram and diagrams of the sections: " + imageCount);
        assertTrue(linkCount > 10, "the table of contents, data dictionary, index and child tables link: " + linkCount);
        assertTrue(referenceBookmarks.contains("Library") && referenceBookmarks.contains("ISBN"),
                "a bookmark per section: " + referenceBookmarks);
    }

    @Test
    void theReferenceCanBeSwitchedOff() {
        assertFalse(textWithoutReference.contains("Element Reference"));
    }

    /** @return the text of the reference section of {@code name}, up to the next section */
    private static String section(String name) {
        Matcher start = Pattern.compile("(?m)^" + Pattern.quote(name) + "\\s+(Element|Attribute)\\s*$").matcher(text);
        assertTrue(start.find(), "section " + name + " expected");
        Matcher next = Pattern.compile("(?m)^\\S+\\s+(Element|Attribute)\\s*$").matcher(text);
        int end = next.find(start.end()) ? next.start() : text.length();
        return text.substring(start.start(), end);
    }

    private static int count(String regex) {
        Matcher m = Pattern.compile("(?m)^" + regex + "\\s*$").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
