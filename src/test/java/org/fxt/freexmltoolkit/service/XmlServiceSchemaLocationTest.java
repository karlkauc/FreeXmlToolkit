package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Declared schema locations that name local files: relative, absolute paths and {@code file:} URLs in
 * the forms found in real documents. Absolute paths used to be appended to the document's directory
 * (and then rejected as "no URL"), so they never bound even when the file existed.
 */
class XmlServiceSchemaLocationTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root" type="xs:string"/>
            </xs:schema>
            """;

    @TempDir
    Path tmp;

    private Path docDir;
    private Path schema;

    @BeforeEach
    void files() throws Exception {
        docDir = Files.createDirectories(tmp.resolve("docs"));
        Path schemaDir = Files.createDirectories(tmp.resolve("my schemas"));
        schema = Files.writeString(schemaDir.resolve("order.xsd"), XSD);
    }

    private static String noNamespace(String location) {
        return "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\""
                + location + "\">x</root>";
    }

    private Optional<String> declared(String xml) {
        return new XmlServiceImpl().getSchemaNameFromXmlContent(xml, docDir.toFile());
    }

    @Test
    void anAbsolutePathIsUsedAsIsNotBelowTheDocument() {
        Optional<String> found = declared(noNamespace(schema.toString()));

        assertEquals(Optional.of(schema.toFile().toURI().toString()), found);
    }

    @Test
    void anAbsolutePathBindsThroughLoadSchemaFromLocation() {
        XmlServiceImpl service = new XmlServiceImpl();

        assertTrue(service.loadSchemaFromLocation(schema.toString()), "plain absolute path");
        assertEquals(schema.toFile().getAbsoluteFile(), service.getCurrentXsdFile().getAbsoluteFile());
    }

    @Test
    void aFileUrlWithAnUnencodedSpaceBinds() {
        String url = "file://" + schema.toString().replace('\\', '/'); // "…/my schemas/order.xsd"
        XmlServiceImpl service = new XmlServiceImpl();

        assertTrue(service.loadSchemaFromLocation(url), url);
        assertEquals(schema.toFile().getAbsoluteFile(), service.getCurrentXsdFile().getAbsoluteFile());
    }

    @Test
    void aStrictFileUrlStillBinds() {
        XmlServiceImpl service = new XmlServiceImpl();

        assertTrue(service.loadSchemaFromLocation(schema.toUri().toString()));
    }

    @Test
    void aRelativeLocationResolvesAgainstTheDocument() throws Exception {
        Files.writeString(docDir.resolve("local.xsd"), XSD);

        assertEquals(Optional.of(docDir.resolve("local.xsd").toFile().toURI().toString()), declared(noNamespace("local.xsd")));
    }

    @Test
    void schemaLocationToleratesLeadingAndInnerWhitespace() throws Exception {
        Files.writeString(docDir.resolve("ns.xsd"), XSD);
        String xml = "<root xmlns=\"urn:x\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:schemaLocation=\"   urn:x\t\tns.xsd  \">x</root>";

        assertEquals(Optional.of(docDir.resolve("ns.xsd").toFile().toURI().toString()), declared(xml));
    }

    @Test
    void aMissingAbsoluteFileDoesNotBind() {
        assertFalse(new XmlServiceImpl().loadSchemaFromLocation(tmp.resolve("nowhere/x.xsd").toString()));
    }

    @Test
    void fileUrlFormsMapToTheirPath() {
        assertEquals(new File("C:/Schemas/a.xsd"), XmlServiceImpl.fileUrlToFile("file://C:/Schemas/a.xsd"));
        assertEquals(new File("C:/My Schemas/a.xsd"), XmlServiceImpl.fileUrlToFile("file:///C:/My Schemas/a.xsd"));
        assertEquals(new File("C:/My Schemas/a.xsd"), XmlServiceImpl.fileUrlToFile("file:///C:\\My Schemas\\a.xsd"));
        assertEquals(new File("/srv/x y/a.xsd"), XmlServiceImpl.fileUrlToFile("file://localhost/srv/x y/a.xsd"));
        assertEquals(new File("//server/share/a.xsd"), XmlServiceImpl.fileUrlToFile("file://server/share/a.xsd"));
        // the space forces the lenient path, where a literal plus must stay a plus
        assertEquals(new File("/srv/a+b c/a.xsd"), XmlServiceImpl.fileUrlToFile("file:///srv/a+b c/a.xsd"));
        assertEquals(new File("/srv/a b/a.xsd"), XmlServiceImpl.fileUrlToFile("file:///srv/a%20b/a.xsd"));
    }

    @Test
    void theKindOfALocationDecidesHowItIsResolved() {
        File base = docDir.toFile();
        assertNull(XmlServiceImpl.localSchemaFile("http://example.org/a.xsd", base), "remote");
        assertNull(XmlServiceImpl.localSchemaFile("urn:example:schema", base), "urn");
        assertEquals(new File("C:\\x\\a.xsd"), XmlServiceImpl.localSchemaFile("C:\\x\\a.xsd", base), "drive path");
        assertEquals(new File("\\\\server\\share\\a.xsd"),
                XmlServiceImpl.localSchemaFile("\\\\server\\share\\a.xsd", base), "UNC path");
        assertEquals(new File(base, "sub/a.xsd"), XmlServiceImpl.localSchemaFile("sub/a.xsd", base), "relative");
        assertNull(XmlServiceImpl.localSchemaFile("sub/a.xsd", null), "relative without a document directory");
    }

    @Test
    void aSchemaLocationOnALoopbackAddressIsNotDownloaded() throws Exception {
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/ssrf-" + System.nanoTime() + ".xsd";
            assertFalse(new XmlServiceImpl().loadSchemaFromLocation(url),
                    "opening a document must not make the app call a loopback address");
            assertEquals(0, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
