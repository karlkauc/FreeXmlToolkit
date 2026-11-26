package org.fxt.freexmltoolkit.service.fundsxml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import org.fxt.freexmltoolkit.service.XmlServiceImpl;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Assignment;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Entry;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Identifier;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Level;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.NewDocument;
import org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDocuments.Party;
import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.fxt.freexmltoolkit.service.xmledit.TextEdit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Listing, decoding, embedding and linking the Documents/Document elements of FundsXML. */
@DisplayName("FundsXML documents (linked and embedded)")
class FundsXmlDocumentsTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 11, 26);
    /** The FundsXML schema bundled with the tests. */
    private static final File SCHEMA = new File("src/test/resources/FundsXML4.xsd");

    static String fixture() throws IOException {
        try (InputStream in = FundsXmlDocumentsTest.class.getResourceAsStream("/fundsxml/documents-fixture.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The fixture without its Documents element. */
    static String withoutDocuments() throws IOException {
        String xml = fixture();
        int start = xml.indexOf("  <Documents>");
        int end = xml.indexOf("</Documents>\n") + "</Documents>\n".length();
        return xml.substring(0, start) + xml.substring(end);
    }

    private static String apply(String xml, List<TextEdit> edits) {
        return new EditPlan(edits).applyTo(xml);
    }

    private static List<String> schemaErrors(String xml) {
        return XmlServiceImpl.getInstance().validateText(xml, SCHEMA).stream()
                .map(e -> e.getLineNumber() + ": " + e.getMessage()).toList();
    }

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        new Random(42).nextBytes(b);
        return b;
    }

    @Test
    @DisplayName("the fixture is schema-valid")
    void fixtureIsValid() throws Exception {
        assertEquals(List.of(), schemaErrors(fixture()));
        assertEquals(List.of(), schemaErrors(withoutDocuments()));
        assertFalse(schemaErrors(fixture().replace("<ListedType>Prospectus", "<ListedType>NoSuchType")).isEmpty(),
                "the validation finds errors");
        assertFalse(schemaErrors(fixture().replace("<SizeInBytes>15</SizeInBytes>\n", "")
                .replace("</Funds>", "</Funds>\n  <Documents/>")).isEmpty(), "a second Documents is an error");
    }

    @Test
    @DisplayName("lists a linked and an embedded document")
    void listsDocuments() throws Exception {
        List<Entry> entries = FundsXmlDocuments.list(fixture());
        assertEquals(2, entries.size());

        Entry linked = entries.get(0);
        assertEquals(1, linked.index());
        assertTrue(linked.listed());
        assertEquals("Prospectus", linked.type());
        assertEquals("Verkaufsprospekt", linked.name());
        assertEquals("de", linked.language());
        assertEquals(Level.FUND, linked.assignments().getFirst().level());
        assertEquals(new Identifier("LEI", "529900T8BM49AURSDO55"),
                linked.assignments().getFirst().parties().getFirst().main());
        assertTrue(linked.linked());
        assertFalse(linked.embedded());
        assertEquals("https://example.com/docs/prospectus.pdf", linked.url());
        assertEquals(123456L, linked.sizeInBytes());
        assertNull(linked.decodedSize());
        assertEquals("prospectus.pdf", linked.fileName());
        assertEquals("application/pdf", linked.format());
        assertFalse(linked.sizeMismatch());

        Entry embedded = entries.get(1);
        assertFalse(embedded.listed());
        assertEquals("Hello note", embedded.type());
        assertEquals(Level.SHARE_CLASS, embedded.assignments().getFirst().level());
        assertEquals("AT0000A00XX1", embedded.assignments().getFirst().parties().getFirst().main().value());
        assertTrue(embedded.embedded());
        assertFalse(embedded.linked());
        assertEquals(15L, embedded.sizeInBytes());
        assertEquals(15L, embedded.decodedSize());
        assertFalse(embedded.sizeMismatch());
        assertTrue(embedded.line() > 0);
    }

    @Test
    @DisplayName("decodes wrapped, indented base64; a wrong SizeInBytes is reported")
    void decodesAndChecksSize() throws Exception {
        FundsXmlDocuments.Decoded decoded = FundsXmlDocuments.decode(fixture(), 2);
        assertEquals("Hello FundsXML\n", new String(decoded.bytes(), StandardCharsets.UTF_8));
        assertEquals("hello.txt", decoded.fileName());
        assertFalse(decoded.sizeMismatch());

        String wrong = fixture().replace("<SizeInBytes>15</SizeInBytes>", "<SizeInBytes>16</SizeInBytes>");
        assertTrue(FundsXmlDocuments.decode(wrong, 2).sizeMismatch());
        assertTrue(FundsXmlDocuments.list(wrong).get(1).sizeMismatch());

        assertThrows(IOException.class, () -> FundsXmlDocuments.decode(fixture(), 1), "a linked document");
        assertThrows(IOException.class, () -> FundsXmlDocuments.decode(fixture(), 3), "no such document");
        assertThrows(IOException.class, () -> FundsXmlDocuments.decodeBase64("SGV*bG8="));
    }

    @Test
    @DisplayName("MIME type from the file extension")
    void mimeTypes() {
        assertEquals("application/pdf", FundsXmlDocuments.mimeType("Factsheet.PDF"));
        assertEquals("text/html", FundsXmlDocuments.mimeType("a.html"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FundsXmlDocuments.mimeType("a.docx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FundsXmlDocuments.mimeType("a.xlsx"));
        assertEquals("text/plain", FundsXmlDocuments.mimeType("a.txt"));
        assertEquals("application/xml", FundsXmlDocuments.mimeType("a.xml"));
        assertEquals("application/octet-stream", FundsXmlDocuments.mimeType("a.zip"));
        assertEquals("application/octet-stream", FundsXmlDocuments.mimeType("README"));
    }

    @Test
    @DisplayName("base64 is written in lines of 76 characters")
    void base64Lines() throws Exception {
        String text = FundsXmlDocuments.base64Lines(bytes(1000), "\n");
        String[] lines = text.split("\n");
        assertEquals(18, lines.length); // 1336 characters
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(76, lines[i].length());
        }
        assertTrue(lines[lines.length - 1].length() <= 76);
        assertArrayEquals(bytes(1000), FundsXmlDocuments.decodeBase64(text));
    }

    @Test
    @DisplayName("embed → list → decode gives the same bytes; the rest of the text is untouched; schema-valid")
    void embedRoundTrip() throws Exception {
        String xml = fixture();
        byte[] content = bytes(5000);
        TextEdit edit = FundsXmlDocuments.embed(xml, new NewDocument(content, "Factsheet 2025-10.pdf", null,
                "Factsheet", "en", Assignment.shareClasses(List.of("AT0000A00XX2"))), TODAY);
        assertEquals(edit.start(), edit.end(), "an insertion");
        String result = apply(xml, List.of(edit));
        assertEquals(xml, result.substring(0, edit.start()) + result.substring(edit.start() + edit.replacement().length()));

        List<Entry> entries = FundsXmlDocuments.list(result);
        assertEquals(3, entries.size());
        Entry added = entries.get(2);
        assertEquals("Factsheet", added.type());
        assertEquals("Factsheet 2025-10", added.name());
        assertEquals("Factsheet 2025-10.pdf", added.fileName());
        assertEquals("application/pdf", added.format());
        assertEquals("en", added.language());
        assertEquals("AT0000A00XX2", added.assignments().getFirst().parties().getFirst().main().value());
        assertEquals(5000L, added.sizeInBytes());
        assertEquals(5000L, added.decodedSize());
        assertArrayEquals(content, FundsXmlDocuments.decode(result, 3).bytes());
        assertTrue(result.contains("\n    <Document>\n      <Type><ListedType>Factsheet</ListedType></Type>\n"),
                "indented like the other documents");
        assertTrue(result.contains("      <CreationDate>2025-11-26</CreationDate>\n"));
        assertTrue(result.contains("\n    </Document>\n  </Documents>"));
        for (String line : edit.replacement().split("\n")) {
            if (line.strip().matches("[A-Za-z0-9+/=]+") && !line.contains("<")) {
                assertTrue(line.strip().length() <= 76, line);
                assertTrue(line.startsWith("        "), "base64 indented below BinaryData");
            }
        }
        assertEquals(List.of(), schemaErrors(result));
    }

    @Test
    @DisplayName("Documents is created after Funds/AssetMasterData when absent, with fund identifiers; schema-valid")
    void createsDocuments() throws Exception {
        String xml = withoutDocuments();
        Party fund = new Party(List.of(new Identifier("LEI", "529900T8BM49AURSDO55")), "Test Fund");
        TextEdit edit = FundsXmlDocuments.embed(xml, new NewDocument("<p>A & B</p>".getBytes(StandardCharsets.UTF_8),
                "notes.html", "Notes & more", "AnnualReport", "de-AT", Assignment.fund(fund)), TODAY);
        String result = apply(xml, List.of(edit));
        assertEquals(xml.indexOf("</Funds>") + "</Funds>".length(), edit.start(), "right after Funds");
        assertTrue(result.contains("</Funds>\n  <Documents>\n    <Document>\n"));
        assertTrue(result.endsWith("    </Document>\n  </Documents>\n</FundsXML4>\n"));
        assertTrue(result.contains("<Name>Notes &amp; more</Name>"));
        assertTrue(result.contains("<Format>text/html</Format>"));

        Entry entry = FundsXmlDocuments.list(result).getFirst();
        assertEquals(Level.FUND, entry.assignments().getFirst().level());
        assertEquals("Test Fund", entry.assignments().getFirst().parties().getFirst().name());
        assertEquals("<p>A & B</p>", new String(FundsXmlDocuments.decode(result, 1).bytes(), StandardCharsets.UTF_8));
        assertEquals(List.of(), schemaErrors(result));

        // with AssetMasterData after Funds, Documents follows AssetMasterData
        String withAssets = xml.replace("  </Funds>\n", "  </Funds>\n  <AssetMasterData>\n    <Asset><UniqueID>A1</UniqueID>"
                + "<Identifiers><ISIN>US0378331005</ISIN></Identifiers><Currency>EUR</Currency><Country>US</Country>"
                + "<Name>APPLE INC.</Name><AssetType>EQ</AssetType></Asset>\n  </AssetMasterData>\n");
        String withAssetsResult = apply(withAssets, List.of(FundsXmlDocuments.embed(withAssets, new NewDocument(
                bytes(10), "x.bin", null, "Factsheet", "en", Assignment.fund(fund)), TODAY)));
        assertTrue(withAssetsResult.contains("</AssetMasterData>\n  <Documents>\n"));
        assertEquals(List.of(), schemaErrors(withAssetsResult));
    }

    @Test
    @DisplayName("Windows line breaks are kept")
    void windowsLineBreaks() throws Exception {
        String xml = fixture().replace("\n", "\r\n");
        String result = apply(xml, List.of(FundsXmlDocuments.embed(xml, new NewDocument(bytes(200), "a.txt", null,
                "Factsheet", "en", Assignment.shareClasses(List.of("AT0000A00XX1"))), TODAY)));
        assertFalse(result.replace("\r\n", "").contains("\n"));
    }

    @Test
    @DisplayName("invalid input is rejected")
    void rejectsInvalidInput() throws Exception {
        Assignment a = Assignment.shareClasses(List.of("AT0000A00XX1"));
        assertThrows(IllegalArgumentException.class, () -> FundsXmlDocuments.embed(fixture(),
                new NewDocument(new byte[0], "a.pdf", null, "Factsheet", "en", a), TODAY));
        assertThrows(IllegalArgumentException.class, () -> FundsXmlDocuments.embed(fixture(),
                new NewDocument(bytes(3), "a.pdf", null, "Factsheet", "e n", a), TODAY));
        assertThrows(IllegalArgumentException.class, () -> FundsXmlDocuments.toLink(fixture(), 2, " "));
    }

    @Test
    @DisplayName("a link becomes an embedded document and back; schema-valid each time")
    void linkAndEmbedded() throws Exception {
        String xml = fixture();
        byte[] pdf = bytes(321);
        String embedded = apply(xml, FundsXmlDocuments.toEmbedded(xml, 1, pdf));
        Entry first = FundsXmlDocuments.list(embedded).getFirst();
        assertTrue(first.embedded());
        assertFalse(first.linked());
        assertEquals(321L, first.sizeInBytes());
        assertArrayEquals(pdf, FundsXmlDocuments.decode(embedded, 1).bytes());
        assertTrue(embedded.contains("      <SizeInBytes>321</SizeInBytes>\n      <BinaryData>\n        "));
        assertEquals(List.of(), schemaErrors(embedded));

        String linked = apply(embedded, FundsXmlDocuments.toLink(embedded, 1, "https://example.com/new.pdf"));
        Entry back = FundsXmlDocuments.list(linked).getFirst();
        assertTrue(back.linked());
        assertFalse(back.embedded());
        assertEquals(321L, back.sizeInBytes());
        assertEquals(xml.replace("123456", "321").replace("docs/prospectus.pdf", "new.pdf"), linked,
                "the same text as before, with the new size and URL");
        assertEquals(List.of(), schemaErrors(linked));
    }

    @Test
    @DisplayName("embedding another kind of file updates FileName and Format")
    void embeddedFileNameAndFormatFollowTheFile() throws Exception {
        String xml = fixture();
        String embedded = apply(xml, FundsXmlDocuments.toEmbedded(xml, 1, bytes(9), "terms & conditions.docx"));
        assertTrue(embedded.contains("<FileName>terms &amp; conditions.docx</FileName>"), embedded);
        assertTrue(embedded.contains(
                "<Format>application/vnd.openxmlformats-officedocument.wordprocessingml.document</Format>"), embedded);
        assertFalse(embedded.contains("<Format>application/pdf</Format>"));
        assertEquals(List.of(), schemaErrors(embedded));
    }

    @Test
    @DisplayName("replacing by a link keeps a comment before BinaryData and refuses an invalid address")
    void toLinkKeepsCommentsAndChecksTheAddress() throws Exception {
        String xml = fixture().replace("      <BinaryData>", "      <!-- delivered by the hub -->\n      <BinaryData>");
        String linked = apply(xml, FundsXmlDocuments.toLink(xml, 2, "https://example.com/hello.txt"));
        assertTrue(linked.contains("<!-- delivered by the hub -->"), linked);
        assertEquals(List.of(), schemaErrors(linked));
        assertThrows(IllegalArgumentException.class, () -> FundsXmlDocuments.toLink(fixture(), 2, "docs/a b.pdf"));
        assertThrows(IllegalArgumentException.class, () -> FundsXmlDocuments.toLink(fixture(), 2, "no scheme"));
        assertTrue(FundsXmlDocuments.isUri("https://example.com/a%20b.pdf"));
        assertFalse(FundsXmlDocuments.isUri("example.com/a.pdf"));
    }

    @Test
    @DisplayName("names are cut to 256 characters and lose characters XML forbids")
    void namesFitText256() throws Exception {
        String longName = "x".repeat(300) + ".pdf";
        String xml = apply(fixture(), List.of(FundsXmlDocuments.embed(fixture(), new NewDocument(bytes(3),
                longName, "Bad\u0001Name", "Factsheet", "en", Assignment.shareClasses(List.of("AT0000A00XX1"))), TODAY)));
        assertTrue(xml.contains("<Name>BadName</Name>"), "control character removed");
        assertTrue(xml.contains("<FileName>" + "x".repeat(256) + "</FileName>"), "file name cut to 256");
        assertEquals(List.of(), schemaErrors(xml));
        assertEquals(256, FundsXmlDocuments.text256("y".repeat(400)).length());
    }

    @Test
    @DisplayName("the size of unpadded base64 is counted right")
    void decodedLengthWithoutPadding() {
        assertEquals(1, FundsXmlDocuments.decodedLength("QQ=="));
        assertEquals(1, FundsXmlDocuments.decodedLength("QQ"));
        assertEquals(2, FundsXmlDocuments.decodedLength("QUI"));
        assertEquals(3, FundsXmlDocuments.decodedLength("QUJD"));
    }

    @Test
    @DisplayName("without SizeInBytes it is added before DocumentURL / BinaryData")
    void sizeIsAdded() throws Exception {
        String xml = fixture().replace("      <SizeInBytes>123456</SizeInBytes>\n", "")
                .replace("      <SizeInBytes>15</SizeInBytes>\n", "");
        String linked = apply(xml, FundsXmlDocuments.toLink(xml, 2, "https://example.com/hello.txt"));
        assertTrue(linked.contains("      <SizeInBytes>15</SizeInBytes>\n      <DocumentURL>https://example.com/hello.txt</DocumentURL>\n    </Document>"));
        String embedded = apply(xml, FundsXmlDocuments.toEmbedded(xml, 1, bytes(7)));
        assertTrue(embedded.contains("      <SizeInBytes>7</SizeInBytes>\n      <BinaryData>"));
        assertEquals(List.of(), schemaErrors(linked));
        assertEquals(List.of(), schemaErrors(embedded));
    }

    @Test
    @DisplayName("ListedType values are read from the schema; the 4.2.11 list when it cannot be read")
    void listedTypes() {
        List<String> types = FundsXmlDocuments.listedTypes(SCHEMA.toPath());
        assertTrue(types.contains("AnnualReport"));
        assertTrue(types.contains("Factsheet"));
        assertEquals(FundsXmlDocuments.DEFAULT_LISTED_TYPES, FundsXmlDocuments.listedTypes(Path.of("missing.xsd")));
        assertEquals(FundsXmlDocuments.DEFAULT_LISTED_TYPES, FundsXmlDocuments.listedTypes(null));
    }

    @Test
    @DisplayName("ListedType of DocumentType, not an attribute ListedType elsewhere in a flattened schema")
    void listedTypesOfDocumentType(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Path xsd = java.nio.file.Files.writeString(dir.resolve("s.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="B"><xs:attribute name="ListedType" type="T"/></xs:complexType>
                  <xs:simpleType name="T"><xs:restriction base="xs:string"><xs:enumeration value="Wrong"/></xs:restriction></xs:simpleType>
                  <xs:complexType name="DocumentType"><xs:sequence><xs:element name="Type"><xs:complexType><xs:choice>
                    <xs:element name="ListedType"><xs:simpleType><xs:restriction base="xs:string">
                      <xs:enumeration value="Factsheet"/><xs:enumeration value="KID / Simplified Prospectus"/>
                    </xs:restriction></xs:simpleType></xs:element>
                  </xs:choice></xs:complexType></xs:element></xs:sequence></xs:complexType>
                </xs:schema>""");
        assertEquals(List.of("Factsheet", "KID / Simplified Prospectus"), FundsXmlDocuments.listedTypes(xsd));
    }

    @Test
    @DisplayName("candidates of a delivery of documents only: the parties the documents are assigned to")
    void candidatesFromDocuments() throws Exception {
        String xml = fixture();
        int start = xml.indexOf("  <Funds>");
        String docsOnly = xml.substring(0, start) + xml.substring(xml.indexOf("  <Documents>"));
        List<FundsXmlDocuments.Candidate> candidates = FundsXmlDocuments.candidates(docsOnly);
        assertEquals(List.of(Level.FUND, Level.SHARE_CLASS),
                candidates.stream().map(FundsXmlDocuments.Candidate::level).toList());
        assertEquals("529900T8BM49AURSDO55", candidates.getFirst().party().main().value());
        assertEquals("AT0000A00XX1", candidates.get(1).party().main().value());
        // the fund's file: no duplicates
        assertEquals(3, FundsXmlDocuments.candidates(xml).size());
    }

    @Test
    @DisplayName("candidates: the fund and its share classes with ISIN or LEI")
    void candidates() throws Exception {
        List<FundsXmlDocuments.Candidate> candidates = FundsXmlDocuments.candidates(fixture());
        assertEquals(List.of(Level.FUND, Level.SHARE_CLASS, Level.SHARE_CLASS),
                candidates.stream().map(FundsXmlDocuments.Candidate::level).toList());
        assertEquals("Test Fund", candidates.getFirst().party().name());
        assertEquals("AT0000A00XX2", candidates.get(2).party().main().value());
        assertEquals("Test Fund EUR I", candidates.get(2).party().name());
    }
}
