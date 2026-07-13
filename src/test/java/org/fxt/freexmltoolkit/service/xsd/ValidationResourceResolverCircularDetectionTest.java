package org.fxt.freexmltoolkit.service.xsd;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.ls.LSInput;

/**
 * Tests the circular-import detection of ValidationResourceResolver.
 * Only a schema that is already an ancestor on the resolution path is circular;
 * repeated requests for the same schema (diamond imports, repeated compilation)
 * must resolve normally instead of being rejected.
 */
class ValidationResourceResolverCircularDetectionTest {

    private static final String XSD_TYPE = "http://www.w3.org/2001/XMLSchema";

    @TempDir
    Path tempDir;

    private SchemaResolver.ValidationResourceResolver resolver;

    @BeforeEach
    void setUp() {
        var schemaResolver = new SchemaResolver(XsdParseOptions.defaults());
        resolver = (SchemaResolver.ValidationResourceResolver) schemaResolver.createLSResourceResolver(null);
        resolver.resetCircularDetection();
    }

    private Path createSchema(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                """);
        return file;
    }

    private LSInput resolve(String systemId, Path baseFile) {
        LSInput input = resolver.resolveResource(XSD_TYPE, null, null, systemId, baseFile.toUri().toString());
        if (input != null && input.getByteStream() != null) {
            try {
                input.getByteStream().close();
            } catch (IOException ignored) {
                // Stream content is irrelevant for these tests
            }
        }
        return input;
    }

    @Test
    void shouldDetectDirectCycle() throws IOException {
        Path a = createSchema("a.xsd");
        Path b = createSchema("b.xsd");

        assertNotNull(resolve("b.xsd", a), "a -> b must resolve");
        assertNull(resolve("a.xsd", b), "b -> a closes the cycle a -> b -> a and must be rejected");
    }

    @Test
    void shouldDetectTransitiveCycle() throws IOException {
        Path a = createSchema("a.xsd");
        Path b = createSchema("b.xsd");
        Path c = createSchema("c.xsd");

        assertNotNull(resolve("b.xsd", a), "a -> b must resolve");
        assertNotNull(resolve("c.xsd", b), "b -> c must resolve");
        assertNull(resolve("a.xsd", c), "c -> a closes the cycle a -> b -> c -> a and must be rejected");
    }

    @Test
    void shouldDetectSelfImport() throws IOException {
        Path a = createSchema("a.xsd");

        assertNull(resolve("a.xsd", a), "a schema importing itself must be rejected");
    }

    @Test
    void shouldAllowDiamondImports() throws IOException {
        Path a = createSchema("a.xsd");
        Path b = createSchema("b.xsd");
        Path c = createSchema("c.xsd");
        createSchema("d.xsd");

        assertNotNull(resolve("b.xsd", a), "a -> b must resolve");
        assertNotNull(resolve("c.xsd", a), "a -> c must resolve");
        assertNotNull(resolve("d.xsd", b), "b -> d must resolve");
        assertNotNull(resolve("d.xsd", c), "c -> d is a diamond import, not a cycle");
    }

    @Test
    void shouldAllowRepeatedRequestsAcrossCompilations() throws IOException {
        Path a = createSchema("a.xsd");
        createSchema("b.xsd");

        assertNotNull(resolve("b.xsd", a), "first compilation must resolve the import");
        assertNotNull(resolve("b.xsd", a), "a repeated compilation must resolve the same import again");
    }

    @Test
    void shouldCompileSchemaWithSharedImportTwice() throws IOException {
        // Regression test for the FundsXML false positive: compiling the same schema
        // twice with one resolver produced a bogus "Circular import detected" warning
        // because the second request for the imported schema was treated as a cycle.
        Path imported = tempDir.resolve("common.xsd");
        Files.writeString(imported, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="urn:common" elementFormDefault="qualified">
                    <xs:element name="Common" type="xs:string"/>
                </xs:schema>
                """);
        Path main = tempDir.resolve("main.xsd");
        Files.writeString(main, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:c="urn:common">
                    <xs:import namespace="urn:common" schemaLocation="common.xsd"/>
                    <xs:element name="Root">
                        <xs:complexType>
                            <xs:sequence>
                                <xs:element ref="c:Common"/>
                            </xs:sequence>
                        </xs:complexType>
                    </xs:element>
                </xs:schema>
                """);

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver(resolver);

        try {
            // Two compilations without a reset in between - the import must resolve both times.
            for (int i = 0; i < 2; i++) {
                StreamSource source = new StreamSource(main.toFile());
                source.setSystemId(main.toUri().toString());
                assertNotNull(factory.newSchema(source), "compilation " + (i + 1) + " must succeed");
            }
        } catch (org.xml.sax.SAXException e) {
            throw new AssertionError("Schema compilation failed: " + e.getMessage(), e);
        }
    }
}
