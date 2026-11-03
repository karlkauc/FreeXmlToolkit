package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import org.apache.xerces.jaxp.validation.XMLSchema11Factory;
import org.fxt.freexmltoolkit.service.xsd.ParsedSchema;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingService.XsdVersion;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingServiceImpl;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A schema whose DOCTYPE names a DTD loads on every path, and the DTD is never read. W3C schemas
 * name {@code XMLSchema.dtd}, which is neither shipped beside them nor needed.
 */
class SchemaDoctypeDtdTest {

    @TempDir
    Path tmp;

    @ParameterizedTest(name = "DTD {0}, protocols {1}, XSD {2}")
    @CsvSource({
            "XMLSchema.dtd, local, 1.0",
            "XMLSchema.dtd, local, 1.1",
            "XMLSchema.dtd, remote, 1.0",
            "XMLSchema.dtd, remote, 1.1",
            "http://www.w3.org/2001/XMLSchema.dtd, local, 1.0",
            "http://www.w3.org/2001/XMLSchema.dtd, local, 1.1",
            "http://www.w3.org/2001/XMLSchema.dtd, remote, 1.0",
            "http://www.w3.org/2001/XMLSchema.dtd, remote, 1.1",
    })
    void everySecureFactoryLoadsTheSchema(String dtd, String protocols, String version) throws Exception {
        File schema = write(schemaNaming(dtd));
        SchemaFactory factory = secureFactory(protocols, version);
        assertNotNull(factory.newSchema(schema));
    }

    @Test
    void aRemoteDtdIsNeverRequested() throws Exception {
        try (CountingServer server = new CountingServer()) {
            File schema = write(schemaNaming(server.url("XMLSchema.dtd")));
            for (String version : new String[]{"1.0", "1.1"}) {
                assertNotNull(secureFactory("remote", version).newSchema(schema));
                assertNotNull(secureFactory("local", version).newSchema(schema));
            }
            assertEquals(0, server.connections(), "no request may reach the DTD's host");
        }
    }

    @Test
    void aRemoteImportIsStillBlockedWithoutRemoteProtocols() throws Exception {
        try (CountingServer server = new CountingServer()) {
            File schema = write("""
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xs:import namespace="urn:other" schemaLocation="%s"/>
                      <xs:element name="root" type="xs:int"/>
                    </xs:schema>""".formatted(server.url("other.xsd")));
            SchemaFactory factory = secureFactory("local", "1.0");
            assertThrows(Exception.class, () -> factory.newSchema(schema));
            assertEquals(0, server.connections(), "the import must be blocked before any request");
        }
    }

    @Test
    void documentationServiceValidatesAgainstSuchASchema() throws Exception {
        File schema = write(schemaNaming("XMLSchema.dtd"));
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(schema.getAbsolutePath());
        service.processXsd(false);

        XsdDocumentationService.ValidationResult valid = service.validateXmlAgainstSchema("<root>5</root>");
        assertTrue(valid.isValid(), () -> String.valueOf(valid.errors()));
        assertFalse(service.validateXmlAgainstSchema("<root>five</root>").isValid(),
                "instance errors are still reported");
    }

    @Test
    void xmlServiceBuildsTheSchemaObject() throws Exception {
        File schema = write(schemaNaming("XMLSchema.dtd"));
        XmlServiceImpl service = new XmlServiceImpl();
        service.setCurrentXsdFile(schema);
        assertNotNull(service.schema, "the Schema object must be built, not dropped as XSD 1.1");
    }

    @Test
    void parsingServiceBuildsTheValidationSchema() throws Exception {
        File schema = write(schemaNaming("XMLSchema.dtd"));
        XsdParsingServiceImpl service = new XsdParsingServiceImpl();
        ParsedSchema parsed = service.parse(schema.toPath());
        assertNotNull(service.toValidationSchema(parsed, XsdVersion.XSD_1_0));
        assertNotNull(service.toValidationSchema(parsed, XsdVersion.XSD_1_1));
    }

    @Test
    void onlyDtdRequestsGetAnEmptyDocument() throws Exception {
        assertEquals(0, SecureXmlFactory.emptyDtd(XMLConstants.XML_DTD_NS_URI, null, "urn:doctype", null)
                .getByteStream().readAllBytes().length);
        assertNotNull(SecureXmlFactory.emptyDtd(null, null, " datatypes.DTD ", null), "suffix in any case");
        assertNull(SecureXmlFactory.emptyDtd(XMLConstants.W3C_XML_SCHEMA_NS_URI, null, "other.xsd", null));
        assertNull(SecureXmlFactory.emptyDtd(XMLConstants.W3C_XML_SCHEMA_NS_URI, null, null, null));
    }

    private static SchemaFactory secureFactory(String protocols, String version) {
        SchemaFactory factory = "1.1".equals(version)
                ? new XMLSchema11Factory()
                : SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return SecureXmlFactory.createSecureSchemaFactory(factory, "remote".equals(protocols)
                ? SecureXmlFactory.LOCAL_AND_REMOTE_SCHEMA_PROTOCOLS
                : SecureXmlFactory.DEFAULT_ALLOWED_SCHEMA_PROTOCOLS);
    }

    /** A schema in the style of the W3C's own: DOCTYPE with a DTD and an internal subset. */
    private static String schemaNaming(String dtd) {
        return """
                <?xml version="1.0"?>
                <!DOCTYPE xs:schema PUBLIC "-//W3C//DTD XMLSCHEMA 200102//EN" "%s" [
                  <!ATTLIST xs:schema id ID #IMPLIED>
                ]>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="xs:int"/>
                </xs:schema>""".formatted(dtd);
    }

    private File write(String content) throws IOException {
        Path file = Files.createTempFile(tmp, "schema", ".xsd");
        Files.writeString(file, content);
        return file.toFile();
    }

    /** Accepts and counts connections on a loopback port, answering none of them. */
    private static final class CountingServer implements AutoCloseable {
        private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        private final AtomicInteger connections = new AtomicInteger();

        CountingServer() throws IOException {
            Thread acceptor = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket ignored = socket.accept()) {
                        connections.incrementAndGet();
                    } catch (SocketException closed) {
                        return;
                    } catch (IOException e) {
                        // keep counting
                    }
                }
            }, "dtd-probe-server");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String url(String path) {
            return "http://127.0.0.1:" + socket.getLocalPort() + "/" + path;
        }

        int connections() {
            return connections.get();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
