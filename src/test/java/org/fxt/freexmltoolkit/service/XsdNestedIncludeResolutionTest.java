package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A relative {@code schemaLocation} is relative to the document that declares it, not to the main schema. SIRI and
 * JATS include modules that include siblings and {@code ../} paths; resolved against the main schema's directory, those
 * modules were silently skipped and their types were missing from the generated samples.
 */
class XsdNestedIncludeResolutionTest {

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
    }

    @Test
    void nestedIncludesAndImportsResolveRelativeToTheirOwnDocument() throws Exception {
        Path main = write("main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="modules/order.xsd"/>
                  <xs:element name="purchase" type="OrderType"/>
                </xs:schema>
                """);
        write("modules/order.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:ext="urn:ext">
                  <xs:include schemaLocation="lines.xsd"/>
                  <xs:include schemaLocation="../common/address.xsd"/>
                  <xs:import namespace="urn:ext" schemaLocation="../ext/ext.xsd"/>
                  <xs:complexType name="OrderType">
                    <xs:sequence>
                      <xs:element name="shipTo" type="AddressType"/>
                      <xs:element name="line" type="LineType"/>
                      <xs:element ref="ext:code"/>
                    </xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """);
        write("modules/lines.xsd", LINES);
        write("common/address.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="AddressType">
                    <xs:sequence><xs:element name="street" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """);
        write("ext/ext.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:ext">
                  <xs:element name="code" type="xs:string"/>
                </xs:schema>
                """);

        String xml = service(main).generateSampleXml(false, 1);

        assertTrue(xml.contains("<street>"), "type from ../common/address.xsd missing:\n" + xml);
        assertTrue(xml.contains("<sku>"), "type from the sibling modules/lines.xsd missing:\n" + xml);
        assertTrue(xml.contains(":code"), "element imported from ../ext/ext.xsd missing:\n" + xml);
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(main.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    /**
     * A remote include is resolved through the catalog, and a relative include of that remote document resolves
     * against its URL when the catalog copy has no sibling file. Offline: the test suite disables downloads.
     */
    @Test
    void remoteIncludesResolveThroughTheCatalogAndAgainstTheirUrl() throws Exception {
        Path main = write("main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="https://example.org/schemas/order.xsd"/>
                  <xs:element name="purchase" type="OrderType"/>
                </xs:schema>
                """);
        write("mirror/order.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="lines.xsd"/>
                  <xs:complexType name="OrderType">
                    <xs:sequence><xs:element name="line" type="LineType"/></xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """);
        write("elsewhere/lines.xsd", LINES);
        Path catalog = write("catalog.xml", """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                  <system systemId="https://example.org/schemas/order.xsd" uri="mirror/order.xsd"/>
                  <system systemId="https://example.org/schemas/lines.xsd" uri="elsewhere/lines.xsd"/>
                </catalog>
                """);
        SchemaLibraryServiceImpl library = new SchemaLibraryServiceImpl(dir.resolve("lib.json"),
                new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes(StandardCharsets.UTF_8)));
        library.addCatalog(catalog);
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, library);

        String xml = service(main).generateSampleXml(false, 1);

        assertTrue(xml.contains("<sku>"), "type from the catalog-mapped remote includes missing:\n" + xml);
    }

    private static final String LINES = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="LineType">
                <xs:sequence><xs:element name="sku" type="xs:string"/></xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    private Path write(String relativePath, String content) throws Exception {
        Path file = dir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static XsdDocumentationService service(Path xsd) {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        return service;
    }
}
