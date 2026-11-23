package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.LineRecorder;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Attributes on lines of their own stay there when the model is written back (a grid edit
 * serializes the whole document): the parser records each start tag's whitespace
 * ({@code StartTagLayout}) and the serializer replays it.
 */
class StartTagLayoutTest {

    private static final String HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static final String ORDER = HEAD
            + "<order xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "       xsi:noNamespaceSchemaLocation=\"order.xsd\"\n"
            + "       id=\"PO-1042\" date=\"2026-03-14\">\n"
            + "    <items>\n"
            + "        <item sku=\"A\" quantity=\"2\">One</item>\n"
            + "        <item sku=\"B\" quantity=\"1\">Two</item>\n"
            + "    </items>\n"
            + "</order>\n";

    private static XmlDocument parse(String xml) {
        return new StreamingXmlParser().parse(xml);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<root a=\"1\"\n      b=\"2\">\n    <child/>\n</root>\n",
            "<root\n    a=\"1\"\n    b=\"2\"\n>\n    <child x=\"1\"  y=\"2\" />\n</root>\n",
            "<root>\n    <child\tx=\"1\"/>\n</root>\n",
            "<root>\n    <!-- <fake a=\"1\"\n b=\"2\"> -->\n    <child a=\"1\"\n           b=\"2\"/>\n</root>\n",
            "<root>\n    <![CDATA[<fake  a=\"1\">]]>\n</root>\n",
            "<root>\n    <?pi <fake a='1'?>\n    <child a=\"1\"\n           b=\"2\"/>\n</root>\n",
            "<p>Text <b a=\"1\"\n   c=\"2\">bold</b> end</p>\n",
    })
    void parseThenSerializeKeepsTheStartTags(String body) {
        String xml = HEAD + body;
        assertEquals(xml, parse(xml).serialize(0, 4));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!DOCTYPE root [\n  <!ELEMENT root ANY>\n  <!ATTLIST root a CDATA #IMPLIED>\n  <!-- <fake> -->\n]>\n"
                    + "<root a=\"1\"\n      b=\"2\"/>\n",
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n"
                    + "  \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n<html/>\n",
            "<!-- before -->\n<?xml-stylesheet href=\"a.xsl\"?>\n<!DOCTYPE root>\n<!-- after -->\n<root/>\n",
    })
    void theDoctypeIsKeptAsWritten(String body) {
        String xml = HEAD + body;
        assertEquals(xml, parse(xml).serialize(0, 4));
        assertEquals(xml, new XmlParser().parse(xml).serialize(0, 4), "DOM parser");
    }

    @Test
    void anEditKeepsTheDoctypeAndALineRecorderCountsItsLines() {
        String xml = HEAD + "<!DOCTYPE order [\n  <!ELEMENT order ANY>\n]>\n<order id=\"1\">\n    <item/>\n</order>\n";
        XmlDocument doc = parse(xml);
        doc.getRootElement().setAttribute("id", "2");
        LineRecorder lines = new LineRecorder();
        assertEquals(xml.replace("id=\"1\"", "id=\"2\""), doc.serialize(4, lines));
        lines.apply();
        assertEquals(5, doc.getRootElement().getSourceLineNumber());
        assertEquals(xml, ((XmlDocument) parse(xml).deepCopy(null)).serialize(0, 4), "a copy keeps it");
    }

    @Test
    void aStreamParseKeepsTheDoctypeToo() throws Exception {
        String xml = HEAD + "<!DOCTYPE root SYSTEM \"root.dtd\">\n<root/>\n";
        XmlDocument doc = new StreamingXmlParser().parse(
                new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)), null);
        assertEquals(xml, doc.serialize(0, 4));
    }

    @Test
    void anEditedValueKeepsTheLayout() {
        XmlDocument doc = parse(ORDER);
        doc.getRootElement().setAttribute("id", "PO-2000");
        assertEquals(ORDER.replace("PO-1042", "PO-2000"), doc.serialize(0, 4));
    }

    @Test
    void anAddedAttributeGetsOneSpaceAndARemovedOneLeavesTheRestAlone() {
        XmlDocument doc = parse(ORDER);
        doc.getRootElement().removeAttribute("xsi:noNamespaceSchemaLocation");
        doc.getRootElement().setAttribute("status", "open");
        String expected = ORDER.replace("\n       xsi:noNamespaceSchemaLocation=\"order.xsd\"", "")
                .replace("date=\"2026-03-14\">", "date=\"2026-03-14\" status=\"open\">");
        assertEquals(expected, doc.serialize(0, 4));
    }

    @Test
    void aTagMovedToAnotherDepthTakesItsIndentationAlong() {
        String xml = HEAD + "<root>\n    <a>\n        <b x=\"1\"\n           y=\"2\"/>\n    </a>\n</root>\n";
        XmlDocument doc = parse(xml);
        XmlElement a = child(doc.getRootElement(), 0);
        XmlElement b = child(a, 0);
        a.removeChild(b);
        doc.getRootElement().addChild(b);
        assertEquals(HEAD + "<root>\n    <a/>\n    <b x=\"1\"\n       y=\"2\"/>\n</root>\n", doc.serialize(0, 4));
    }

    @Test
    void aCopyKeepsTheLayout() {
        XmlDocument doc = parse(ORDER);
        XmlElement copy = (XmlElement) doc.getRootElement().deepCopy(null);
        assertEquals(doc.getRootElement().getStartTagLayout(), copy.getStartTagLayout());
    }

    @Test
    void plainTagsCarryNoLayout() {
        XmlDocument doc = parse(ORDER);
        XmlElement items = child(doc.getRootElement(), 0);
        assertNull(items.getStartTagLayout());
        assertNull(child(items, 0).getStartTagLayout(), "one space before each attribute is the serializer's form");
    }

    @Test
    void theLineRecorderCountsTheKeptLineBreaks() {
        XmlDocument doc = parse(ORDER);
        LineRecorder lines = new LineRecorder();
        doc.serialize(4, lines);
        lines.apply();
        XmlElement items = child(doc.getRootElement(), 0);
        assertEquals(4, doc.getRootElement().getSourceLineNumber(), "the line where <order …> ends");
        assertEquals(5, items.getSourceLineNumber());
        assertEquals(6, child(items, 0).getSourceLineNumber());
    }

    @Test
    void theDomParserKeepsTheLayoutToo() {
        String xml = HEAD + "<root a=\"1\"\n      b=\"2\">\n    <child/>\n</root>\n";
        assertEquals(xml, new XmlParser().parse(xml).serialize(0, 4));
    }

    private static XmlElement child(XmlElement parent, int index) {
        List<XmlNode> elements = parent.getChildren().stream().filter(XmlElement.class::isInstance).toList();
        return (XmlElement) elements.get(index);
    }
}
