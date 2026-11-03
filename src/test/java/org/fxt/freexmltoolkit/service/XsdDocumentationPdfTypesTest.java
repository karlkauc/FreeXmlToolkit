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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripper;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The type sections of the PDF documentation: one section per global complex and simple type
 * with derivation, content model, facets and enumerations with their documentation, where it is
 * used and which types derive from it; and the "deduplicate by type" data dictionary.
 */
class XsdDocumentationPdfTypesTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:simpleType name="Colour">
                    <xs:annotation><xs:documentation>A colour of a cover.</xs:documentation></xs:annotation>
                    <xs:restriction base="xs:string">
                        <xs:maxLength value="5"/>
                        <xs:enumeration value="red">
                            <xs:annotation><xs:documentation>Like a tomato.</xs:documentation></xs:annotation>
                        </xs:enumeration>
                        <xs:enumeration value="green"/>
                    </xs:restriction>
                </xs:simpleType>
                <xs:complexType name="AddressType">
                    <xs:annotation><xs:documentation>A postal address.</xs:documentation></xs:annotation>
                    <xs:sequence>
                        <xs:element name="Street" type="xs:string"/>
                        <xs:element name="City" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>
                <xs:complexType name="CompanyAddressType">
                    <xs:complexContent>
                        <xs:extension base="AddressType">
                            <xs:sequence>
                                <xs:element name="Company" type="xs:string"/>
                            </xs:sequence>
                        </xs:extension>
                    </xs:complexContent>
                </xs:complexType>
                <xs:element name="Library">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Cover" type="Colour"/>
                            <xs:element name="HomeAddress" type="AddressType"/>
                            <xs:element name="WorkAddress" type="AddressType"/>
                            <xs:element name="Office" type="CompanyAddressType"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    private static String text;
    private static String dedupedDictionary;
    private static List<String> complexBookmarks;

    @BeforeAll
    static void generate(@TempDir Path tmp) throws Exception {
        File xsd = tmp.resolve("library.xsd").toFile();
        Files.writeString(xsd.toPath(), XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.processXsd(false);

        File out = tmp.resolve("library.pdf").toFile();
        render(service, false, out);
        try (PDDocument document = Loader.loadPDF(out)) {
            text = new PDFTextStripper().getText(document).replace("​", "");
            complexBookmarks = new ArrayList<>();
            for (PDOutlineItem item : document.getDocumentCatalog().getDocumentOutline().children()) {
                if ("Complex Types".equals(item.getTitle())) {
                    item.children().forEach(child -> complexBookmarks.add(child.getTitle()));
                }
            }
        }

        File deduped = tmp.resolve("deduped.pdf").toFile();
        render(service, true, deduped);
        try (PDDocument document = Loader.loadPDF(deduped)) {
            String all = new PDFTextStripper().getText(document).replace("​", "");
            int start = all.indexOf("Complete listing of all elements");
            int end = all.indexOf("Element Reference", start);
            dedupedDictionary = all.substring(start, end < 0 ? all.length() : end);
        }
    }

    private static void render(XsdDocumentationService service, boolean dedupe, File out) throws Exception {
        XsdDocumentationPdfService pdf = new XsdDocumentationPdfService();
        pdf.setConfig(new PdfDocumentationConfig());
        pdf.setDeduplicateDataDictionaryByType(dedupe);
        pdf.setImageService(new XsdDocumentationImageService(service.xsdDocumentationData.getExtendedXsdElementMap()));
        pdf.generatePdfDocumentation(out, service.xsdDocumentationData);
    }

    @Test
    void complexTypesHaveTheirContentModelAndUsage() {
        String address = section("AddressType", "Complex Type");
        assertTrue(address.contains("A postal address."), address);
        assertTrue(address.contains("Children and Attributes") && address.contains("Street") && address.contains("City"), address);
        assertTrue(address.contains("Used In") && address.contains("/Library/HomeAddress")
                && address.contains("/Library/WorkAddress"), address);
        assertTrue(address.contains("Derived types: CompanyAddressType"), address);

        String company = section("CompanyAddressType", "Complex Type");
        assertTrue(Pattern.compile("Base\\s+AddressType").matcher(company).find(), company);
        assertTrue(company.contains("extension of complexContent"), company);
    }

    @Test
    void simpleTypesListFacetsAndDocumentedValues() {
        String colour = section("Colour", "Simple Type");
        assertTrue(colour.contains("A colour of a cover."), colour);
        assertTrue(Pattern.compile("maxLength\\s+5").matcher(colour).find(), colour);
        assertTrue(colour.contains("red - Like a tomato."), "a value with its documentation: " + colour);
        assertTrue(colour.contains("green"), colour);
        assertTrue(colour.contains("/Library/Cover"), "used in: " + colour);
    }

    @Test
    void typeSectionsHaveBookmarks() {
        assertTrue(complexBookmarks.contains("AddressType") && complexBookmarks.contains("CompanyAddressType"),
                "a bookmark per type: " + complexBookmarks);
    }

    @Test
    void theDictionaryCanListEachNamedTypeOnce() {
        assertTrue(text.contains("/Library/WorkAddress"), "without the option every occurrence is listed");
        assertTrue(dedupedDictionary.contains("/Library/HomeAddress"), dedupedDictionary);
        assertFalse(Pattern.compile("/Library/WorkAddress\\s+AddressType").matcher(dedupedDictionary).find(),
                "the second element of AddressType is left out: " + dedupedDictionary);
    }

    /** @return the text of a section, from its heading to the next heading of any section */
    private static String section(String name, String kind) {
        Matcher start = Pattern.compile("(?m)^" + Pattern.quote(name) + "\\s+" + kind + "\\s*$").matcher(text);
        assertTrue(start.find(), "section " + name + " expected");
        Matcher next = Pattern.compile("(?m)^\\S+\\s+(Element|Attribute|Complex Type|Simple Type)\\s*$").matcher(text);
        int end = next.find(start.end()) ? next.start() : text.length();
        return text.substring(start.start(), end);
    }
}
