package org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests that StreamingXmlParser correctly preserves namespace declarations
 * during parse→serialize round-trips.
 */
class StreamingXmlParserNamespaceTest {

    private StreamingXmlParser parser;
    private XmlSerializer serializer;

    @BeforeEach
    void setUp() {
        parser = new StreamingXmlParser();
        serializer = new XmlSerializer();
    }

    @Nested
    @DisplayName("Namespace Declaration Preservation")
    class NamespaceDeclarationTests {

        @Test
        @DisplayName("Prefixed namespace declaration is preserved")
        void prefixedNamespaceDeclarationPreserved() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"/>";

            XmlDocument doc = parser.parse(xml);
            XmlElement root = doc.getRootElement();

            assertEquals("schema", root.getName());
            assertEquals("xsd", root.getNamespacePrefix());
            assertEquals("http://www.w3.org/2001/XMLSchema",
                    root.getAttribute("xmlns:xsd"));
        }

        @Test
        @DisplayName("Default namespace declaration is preserved")
        void defaultNamespaceDeclarationPreserved() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<root xmlns=\"http://example.com/default\"/>";

            XmlDocument doc = parser.parse(xml);
            XmlElement root = doc.getRootElement();

            assertEquals("http://example.com/default",
                    root.getAttribute("xmlns"));
        }

        @Test
        @DisplayName("Multiple namespace declarations preserved")
        void multipleNamespaceDeclarationsPreserved() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<root xmlns:a=\"http://a.com\" xmlns:b=\"http://b.com\">" +
                    "<a:child/><b:child/></root>";

            XmlDocument doc = parser.parse(xml);
            XmlElement root = doc.getRootElement();

            assertEquals("http://a.com", root.getAttribute("xmlns:a"));
            assertEquals("http://b.com", root.getAttribute("xmlns:b"));
        }
    }

    @Nested
    @DisplayName("Namespace Round-Trip")
    class NamespaceRoundTripTests {

        @Test
        @DisplayName("Round-trip preserves prefixed namespace - can re-parse serialized output")
        void roundTripPrefixedNamespace() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">" +
                    "<xsd:element name=\"test\"/></xsd:schema>";

            // First parse
            XmlDocument doc1 = parser.parse(xml);
            String serialized = serializer.serialize(doc1);

            // Serialized output must contain the namespace declaration
            assertTrue(serialized.contains("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\""),
                    "Serialized XML must contain namespace declaration, got: " + serialized);

            // Second parse must succeed (not throw)
            XmlDocument doc2 = assertDoesNotThrow(() -> parser.parse(serialized),
                    "Re-parsing serialized XML should not fail");

            assertEquals("schema", doc2.getRootElement().getName());
            assertEquals("xsd", doc2.getRootElement().getNamespacePrefix());
        }

        @Test
        @DisplayName("Round-trip preserves default namespace - can re-parse serialized output")
        void roundTripDefaultNamespace() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<root xmlns=\"http://example.com\">" +
                    "<child>text</child></root>";

            XmlDocument doc1 = parser.parse(xml);
            String serialized = serializer.serialize(doc1);

            assertTrue(serialized.contains("xmlns=\"http://example.com\""),
                    "Serialized XML must contain default namespace, got: " + serialized);

            XmlDocument doc2 = assertDoesNotThrow(() -> parser.parse(serialized));
            assertEquals("root", doc2.getRootElement().getName());
        }

        @Test
        @DisplayName("Round-trip preserves multiple namespaces with child elements")
        void roundTripMultipleNamespacesWithChildren() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<root xmlns:ns1=\"http://ns1.com\" xmlns:ns2=\"http://ns2.com\">" +
                    "<ns1:child attr=\"val\"/><ns2:child/></root>";

            XmlDocument doc1 = parser.parse(xml);
            String serialized = serializer.serialize(doc1);

            // Must contain both namespace declarations
            assertTrue(serialized.contains("xmlns:ns1="), "Must contain ns1 declaration");
            assertTrue(serialized.contains("xmlns:ns2="), "Must contain ns2 declaration");

            // Must round-trip without error
            XmlDocument doc2 = assertDoesNotThrow(() -> parser.parse(serialized));
            assertEquals(2, doc2.getRootElement().getChildElements().size());
        }

        @Test
        @DisplayName("Round-trip preserves namespace on nested elements")
        void roundTripNestedNamespaceDeclarations() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<root xmlns:a=\"http://a.com\">" +
                    "<child xmlns:b=\"http://b.com\">" +
                    "<b:grandchild/></child></root>";

            XmlDocument doc1 = parser.parse(xml);
            String serialized = serializer.serialize(doc1);

            assertTrue(serialized.contains("xmlns:a="), "Must contain ns a on root");
            assertTrue(serialized.contains("xmlns:b="), "Must contain ns b on child");

            assertDoesNotThrow(() -> parser.parse(serialized));
        }
    }

    @Nested
    @DisplayName("Attributes and Namespaces Together")
    class AttributesWithNamespacesTests {

        @Test
        @DisplayName("Regular attributes preserved alongside namespace declarations")
        void attributesAndNamespacesPreserved() {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<root xmlns:ns=\"http://example.com\" id=\"123\" ns:type=\"test\"/>";

            XmlDocument doc = parser.parse(xml);
            XmlElement root = doc.getRootElement();

            assertEquals("http://example.com", root.getAttribute("xmlns:ns"));
            assertEquals("123", root.getAttribute("id"));
            assertEquals("test", root.getAttribute("ns:type"));
        }
    }
}
