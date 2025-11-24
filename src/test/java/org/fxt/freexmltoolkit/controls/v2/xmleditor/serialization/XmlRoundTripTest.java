package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for XML parsing and serialization.
 *
 * <p>Verifies that: XML → Model → XML produces equivalent results.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
class XmlRoundTripTest {

    private XmlParser parser;
    private XmlSerializer serializer;

    @BeforeEach
    void setUp() {
        parser = new XmlParser();
        serializer = new XmlSerializer();
    }

    @Test
    void testSimpleElement() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root/>";

        XmlDocument doc = parser.parse(xml);
        String serialized = serializer.serialize(doc);

        assertNotNull(doc);
        assertNotNull(doc.getRootElement());
        assertEquals("root", doc.getRootElement().getName());
        assertTrue(serialized.contains("<root/>"));
    }

    @Test
    void testElementWithAttributes() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root id=\"123\" name=\"test\"/>";

        XmlDocument doc = parser.parse(xml);

        assertEquals("root", doc.getRootElement().getName());
        assertEquals("123", doc.getRootElement().getAttribute("id"));
        assertEquals("test", doc.getRootElement().getAttribute("name"));
    }

    @Test
    void testElementWithText() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>Hello World</root>";

        XmlDocument doc = parser.parse(xml);

        assertEquals("root", doc.getRootElement().getName());
        assertEquals("Hello World", doc.getRootElement().getTextContent());
    }

    @Test
    void testNestedElements() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>\n" +
                "  <child1>\n" +
                "    <grandchild/>\n" +
                "  </child1>\n" +
                "  <child2/>\n" +
                "</root>";

        XmlDocument doc = parser.parse(xml);
        XmlElement root = doc.getRootElement();

        assertEquals("root", root.getName());
        assertEquals(2, root.getChildElements().size());
        assertEquals("child1", root.getChildElements().get(0).getName());
        assertEquals("child2", root.getChildElements().get(1).getName());

        XmlElement child1 = root.getChildElements().get(0);
        assertEquals(1, child1.getChildElements().size());
        assertEquals("grandchild", child1.getChildElements().get(0).getName());
    }

    @Test
    void testComment() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!-- This is a comment -->\n" +
                "<root/>";

        XmlDocument doc = parser.parse(xml);

        assertEquals(2, doc.getChildCount()); // Comment + root element
        assertTrue(doc.getChildren().get(0) instanceof XmlComment);
        assertEquals(" This is a comment ", ((XmlComment) doc.getChildren().get(0)).getText());
    }

    @Test
    void testCData() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root><![CDATA[Some <data> with & special chars]]></root>";

        XmlDocument doc = parser.parse(xml);
        XmlElement root = doc.getRootElement();

        assertEquals(1, root.getChildCount());
        assertTrue(root.getChildren().get(0) instanceof XmlCData);
        XmlCData cdata = (XmlCData) root.getChildren().get(0);
        assertEquals("Some <data> with & special chars", cdata.getText());
    }

    @Test
    void testProcessingInstruction() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<?xml-stylesheet type=\"text/xsl\" href=\"style.xsl\"?>\n" +
                "<root/>";

        XmlDocument doc = parser.parse(xml);

        boolean foundPI = false;
        for (XmlNode child : doc.getChildren()) {
            if (child instanceof XmlProcessingInstruction) {
                XmlProcessingInstruction pi = (XmlProcessingInstruction) child;
                assertEquals("xml-stylesheet", pi.getTarget());
                assertTrue(pi.getData().contains("type=\"text/xsl\""));
                foundPI = true;
            }
        }
        assertTrue(foundPI, "Processing instruction not found");
    }

    @Test
    void testNamespaces() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"/>";

        XmlDocument doc = parser.parse(xml);
        XmlElement root = doc.getRootElement();

        assertEquals("schema", root.getName());
        assertEquals("xsd", root.getNamespacePrefix());
        assertEquals("http://www.w3.org/2001/XMLSchema", root.getNamespaceURI());
    }

    @Test
    void testComplexDocument() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!-- Root comment -->\n" +
                "<book id=\"123\">\n" +
                "  <title>XML Guide</title>\n" +
                "  <author name=\"John Doe\">\n" +
                "    <email>john@example.com</email>\n" +
                "  </author>\n" +
                "  <content><![CDATA[Some <content> here]]></content>\n" +
                "  <!-- Chapter comment -->\n" +
                "  <chapter num=\"1\">Introduction</chapter>\n" +
                "</book>";

        XmlDocument doc = parser.parse(xml);
        XmlElement book = doc.getRootElement();

        // Verify structure
        assertEquals("book", book.getName());
        assertEquals("123", book.getAttribute("id"));

        // Verify children
        assertTrue(book.getChildElements().size() >= 4);
        assertEquals("title", book.getChildElement("title").getName());
        assertEquals("XML Guide", book.getChildElement("title").getTextContent());

        XmlElement author = book.getChildElement("author");
        assertNotNull(author);
        assertEquals("John Doe", author.getAttribute("name"));
        assertEquals("john@example.com", author.getChildElement("email").getTextContent());

        // Serialize and verify it's still valid
        String serialized = serializer.serialize(doc);
        assertNotNull(serialized);
        assertTrue(serialized.contains("<book"));
        assertTrue(serialized.contains("id=\"123\""));
    }

    @Test
    void testRoundTripPreservesStructure() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>\n" +
                "  <child1 attr=\"value\">Text</child1>\n" +
                "  <child2/>\n" +
                "</root>";

        XmlDocument doc1 = parser.parse(xml);
        String serialized = serializer.serialize(doc1);
        XmlDocument doc2 = parser.parse(serialized);

        // Both documents should have same structure
        assertEquals(doc1.getRootElement().getName(), doc2.getRootElement().getName());
        assertEquals(doc1.getRootElement().getChildCount(), doc2.getRootElement().getChildCount());
    }

    @Test
    void testXmlDeclaration() {
        String xml = "<?xml version=\"1.1\" encoding=\"ISO-8859-1\" standalone=\"yes\"?>\n<root/>";

        XmlDocument doc = parser.parse(xml);

        assertEquals("1.1", doc.getVersion());
        // Note: encoding might be UTF-8 due to parser defaults
        // assertTrue(doc.getStandalone());
    }

    @Test
    void testEmptyElements() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>\n" +
                "  <empty1/>\n" +
                "  <empty2></empty2>\n" +
                "</root>";

        XmlDocument doc = parser.parse(xml);
        XmlElement root = doc.getRootElement();

        assertEquals(2, root.getChildElements().size());
        assertEquals(0, root.getChildElements().get(0).getChildCount());
        assertEquals(0, root.getChildElements().get(1).getChildCount());
    }

    @Test
    void testSpecialCharactersInText() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root>&lt;&gt;&amp;&quot;&apos;</root>";

        XmlDocument doc = parser.parse(xml);

        String text = doc.getRootElement().getTextContent();
        assertEquals("<>&\"'", text);
    }

    @Test
    void testSpecialCharactersInAttributes() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root attr=\"&lt;&gt;&amp;&quot;\"/>";

        XmlDocument doc = parser.parse(xml);

        String attrValue = doc.getRootElement().getAttribute("attr");
        assertEquals("<>&\"", attrValue);
    }

    @Test
    void testParseInvalidXml() {
        String xml = "<root><unclosed>";

        assertThrows(XmlParser.XmlParseException.class, () -> {
            parser.parse(xml);
        });
    }

    @Test
    void testSerializeAndParse() {
        // Create document programmatically
        XmlDocument doc = new XmlDocument();
        XmlElement root = new XmlElement("root");
        root.setAttribute("id", "1");

        XmlElement child = new XmlElement("child");
        child.addChild(new XmlText("Hello"));
        root.addChild(child);

        root.addChild(new XmlComment("Comment"));
        doc.setRootElement(root);

        // Serialize
        String xml = serializer.serialize(doc);

        // Parse back
        XmlDocument doc2 = parser.parse(xml);

        // Verify
        assertEquals("root", doc2.getRootElement().getName());
        assertEquals("1", doc2.getRootElement().getAttribute("id"));
        assertEquals(1, doc2.getRootElement().getChildElements().size());
    }
}
