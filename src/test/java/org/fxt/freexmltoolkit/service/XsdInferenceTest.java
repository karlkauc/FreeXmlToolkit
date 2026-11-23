package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The schema {@link XsdInference} derives must be a valid XSD, and every sample it was derived from
 * must be valid against it - that is what {@link #inferAndValidate} checks for each case.
 */
class XsdInferenceTest {

    private static String inferAndValidate(String... documents) throws Exception {
        String xsd = XsdInference.infer(List.of(documents));
        Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(xsd)));
        for (String document : documents) {
            try {
                schema.newValidator().validate(new StreamSource(new StringReader(document)));
            } catch (Exception e) {
                throw new AssertionError("sample is not valid: " + e.getMessage() + "\n" + document + "\n" + xsd, e);
            }
        }
        return xsd;
    }

    private static boolean valid(String xsd, String document) throws Exception {
        Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(xsd)));
        try {
            schema.newValidator().validate(new StreamSource(new StringReader(document)));
            return true;
        } catch (org.xml.sax.SAXException e) {
            return false;
        }
    }

    @Test
    void aRepeatedElementKeepsItsContent() throws Exception {
        String xsd = inferAndValidate("""
                <order id="1">
                  <item sku="A1"><name>Desk</name><price>349.00</price></item>
                  <item sku="B2"><name>Chair</name><price>189.50</price></item>
                  <item sku="C3"><name>Lamp</name><price>79</price></item>
                </order>""");
        assertTrue(xsd.contains("<xs:element name=\"item\" maxOccurs=\"unbounded\">"), xsd);
        assertTrue(xsd.contains("<xs:element name=\"name\" type=\"xs:string\"/>"), xsd);
        assertTrue(xsd.contains("<xs:element name=\"price\" type=\"xs:decimal\"/>"), "79 and 349.00 are decimals: " + xsd);
        assertTrue(xsd.contains("<xs:attribute name=\"sku\" type=\"xs:string\" use=\"required\"/>"), xsd);
        assertFalse(xsd.contains("maxOccurs=\"3\""), "the count of the sample is no limit: " + xsd);
    }

    @Test
    void whatIsMissingSomewhereIsOptional() throws Exception {
        String xsd = inferAndValidate("""
                <list>
                  <entry lang="en"><title>One</title><note>first</note></entry>
                  <entry><title>Two</title></entry>
                </list>""");
        assertTrue(xsd.contains("<xs:element name=\"note\" type=\"xs:string\" minOccurs=\"0\"/>"), xsd);
        assertTrue(xsd.contains("<xs:element name=\"title\" type=\"xs:string\"/>"), xsd);
        assertTrue(xsd.contains("<xs:attribute name=\"lang\" type=\"xs:string\"/>"), "not required: " + xsd);
    }

    @Test
    void anElementFirstSeenLaterIsOptionalToo() throws Exception {
        String xsd = inferAndValidate("<l><e><a>1</a></e><e><a>2</a><b>x</b></e></l>");
        assertTrue(xsd.contains("<xs:element name=\"b\" type=\"xs:string\" minOccurs=\"0\"/>"), xsd);
    }

    @Test
    void childrenKeepTheirDocumentOrder() throws Exception {
        String xsd = inferAndValidate("<p><zeta>1</zeta><alpha>2</alpha><mid>3</mid></p>");
        assertTrue(xsd.indexOf("\"zeta\"") < xsd.indexOf("\"alpha\"") && xsd.indexOf("\"alpha\"") < xsd.indexOf("\"mid\""), xsd);
    }

    @Test
    void ordersOfSeveralOccurrencesAreMerged() throws Exception {
        String xsd = inferAndValidate("<l><e><a/><c/></e><e><a/><b/><c/></e></l>");
        assertTrue(xsd.contains("<xs:sequence>"), xsd);
        assertTrue(xsd.indexOf("\"a\"") < xsd.indexOf("\"b\"") && xsd.indexOf("\"b\"") < xsd.indexOf("\"c\""), xsd);
    }

    @Test
    void contradictingOrdersBecomeAChoice() throws Exception {
        String xsd = inferAndValidate("<l><e><a/><b/></e><e><b/><a/></e></l>");
        assertTrue(xsd.contains("<xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">"), xsd);
        inferAndValidate("<e><a/><b/><a/></e>"); // a comes back after b
    }

    @ParameterizedTest
    @CsvSource({
            "true,xs:boolean", "1,xs:integer", "0,xs:integer", "-42,xs:integer", "3.14,xs:decimal",
            "2026-03-31,xs:date", "2026-03-31T10:15:00,xs:dateTime", "2026-03-31T10:15:00+02:00,xs:dateTime",
            "10:15:00,xs:time", "https://example.org/a,xs:anyURI", "hello,xs:string",
            "01234,xs:string", "2026-02-30,xs:string", "1e5,xs:string", "+5,xs:string"
    })
    void aValueGetsTheNarrowestType(String value, String type) throws Exception {
        String xsd = inferAndValidate("<v>" + value + "</v>");
        assertTrue(xsd.contains("<xs:element name=\"v\" type=\"" + type + "\"/>"), xsd);
    }

    @Test
    void theTypeFitsEveryValue() throws Exception {
        String xsd = inferAndValidate("<l><n>1</n><n>2.5</n><d>2026-01-01</d><d>soon</d><b>true</b><b>1</b>"
                + "<e>7</e><e/></l>");
        assertTrue(xsd.contains("<xs:element name=\"n\" type=\"xs:decimal\""), xsd);
        assertTrue(xsd.contains("<xs:element name=\"d\" type=\"xs:string\""), xsd);
        assertTrue(xsd.contains("<xs:element name=\"b\" type=\"xs:string\""), xsd);
        assertTrue(xsd.contains("<xs:element name=\"e\" type=\"xs:string\""), "an empty value fits no number: " + xsd);
    }

    @Test
    void anIdOfOneIsNoBoolean() throws Exception {
        String xsd = inferAndValidate("<r id=\"1\"><x>a</x></r>");
        assertTrue(xsd.contains("<xs:attribute name=\"id\" type=\"xs:integer\" use=\"required\"/>"), xsd);
    }

    @Test
    void textWithAttributesIsSimpleContent() throws Exception {
        String xsd = inferAndValidate("<price currency=\"EUR\">12.50</price>");
        assertTrue(xsd.contains("<xs:extension base=\"xs:decimal\">"), xsd);
    }

    @Test
    void textBetweenChildrenIsMixedContent() throws Exception {
        String xsd = inferAndValidate("<p>Some <b>bold</b> text</p>");
        assertTrue(xsd.contains("<xs:complexType mixed=\"true\">"), xsd);
    }

    @Test
    void severalDocumentsAreMerged() throws Exception {
        String xsd = inferAndValidate(
                "<order><id>1</id><customer>A</customer></order>",
                "<order><id>2</id><customer>B</customer><note>urgent</note></order>",
                "<invoice><number>7</number></invoice>");
        assertTrue(xsd.contains("Source documents: 3"), xsd);
        assertTrue(xsd.contains("<xs:element name=\"note\" type=\"xs:string\" minOccurs=\"0\"/>"), xsd);
        assertTrue(xsd.contains("<xs:element name=\"invoice\">"), "a second root element: " + xsd);
        assertEquals(1, xsd.split("<xs:element name=\"order\">", -1).length - 1, "one declaration for both orders");
    }

    @Test
    void theRootNamespaceIsTheTargetNamespace() throws Exception {
        String xsd = inferAndValidate("""
                <o:order xmlns:o="urn:example:orders" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="urn:example:orders order.xsd">
                  <o:id>1</o:id><plain>x</plain>
                </o:order>""");
        assertTrue(xsd.contains("targetNamespace=\"urn:example:orders\""), xsd);
        assertTrue(xsd.contains("elementFormDefault=\"qualified\""), xsd);
        assertTrue(xsd.contains("<xs:element name=\"plain\" type=\"xs:string\" form=\"unqualified\"/>"), xsd);
        assertFalse(xsd.contains("schemaLocation"), "xsi attributes are not declared: " + xsd);
    }

    @Test
    void otherNamespacesGetWildcards() throws Exception {
        String xsd = inferAndValidate("""
                <a xmlns="urn:a" xmlns:b="urn:b" xml:lang="en" b:flag="1"><x>1</x><b:ext><b:deep/></b:ext><y>2</y></a>""");
        assertTrue(xsd.contains("<xs:any namespace=\"urn:b\" processContents=\"lax\""), xsd);
        assertTrue(xsd.contains("<xs:anyAttribute namespace="), xsd);
    }

    @Test
    void theSchemaRejectsWhatTheSamplesDoNotShow() throws Exception {
        String xsd = inferAndValidate("<order><id>1</id><item><name>a</name></item><item><name>b</name></item></order>");
        assertTrue(valid(xsd, "<order><id>5</id><item><name>c</name></item></order>"));
        assertFalse(valid(xsd, "<order><id>x</id><item><name>c</name></item></order>"), "id is a number");
        assertFalse(valid(xsd, "<order><id>5</id><item/></order>"), "name is required");
        assertFalse(valid(xsd, "<order><item><name>c</name></item><id>5</id></order>"), "order of the children");
    }

    @Test
    void namesAndNamespacesAreEscaped() throws Exception {
        String xsd = inferAndValidate("<r xmlns=\"urn:x?a=1&amp;b=2\"><c/></r>");
        assertTrue(xsd.contains("targetNamespace=\"urn:x?a=1&amp;b=2\""), xsd);
    }

    @Test
    void notWellFormedIsInvalidInput() {
        var e = assertThrows(XsdInference.InvalidInputException.class, () -> XsdInference.infer(List.of("<a><b></a>")));
        assertTrue(e.getMessage().contains("not well-formed"), e.getMessage());
        var second = assertThrows(XsdInference.InvalidInputException.class,
                () -> XsdInference.infer(List.of("<a/>", "<a>")));
        assertTrue(second.getMessage().startsWith("Document 2"), second.getMessage());
        assertThrows(XsdInference.InvalidInputException.class, () -> XsdInference.infer(List.of()));
    }

    @Test
    void documentsOfDifferentNamespacesAreRefused() {
        var e = assertThrows(XsdInference.InvalidInputException.class,
                () -> XsdInference.infer(List.of("<a xmlns=\"urn:a\"/>", "<a xmlns=\"urn:b\"/>")));
        assertTrue(e.getMessage().contains("urn:a") && e.getMessage().contains("urn:b"), e.getMessage());
    }

    @Test
    void filesAreReadInTheirOwnEncoding(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("latin.xml");
        Files.write(file, "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><straße nr=\"7\">Hauptstraße</straße>"
                .getBytes(StandardCharsets.ISO_8859_1));
        String xsd = XsdInference.inferFiles(List.of(file));
        assertTrue(xsd.contains("<xs:element name=\"straße\">"), xsd);
        assertThrows(XsdInference.InvalidInputException.class,
                () -> XsdInference.inferFiles(List.of(folder.resolve("missing.xml"))));
    }
}
