package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlGridQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

/**
 * Saxon processors that evaluate expressions from documents or the user (Query Console XPath mode,
 * grid formulas and filters) neither reach the network nor let a document read local files or
 * exhaust memory through entities.
 */
class SaxonResourceAccessTest {

    @TempDir
    Path tmp;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private String remote;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "<remote>leaked</remote>".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        remote = "http://127.0.0.1:" + server.getAddress().getPort() + "/data.xml";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static String billionLaughs() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?>\n<!DOCTYPE lolz [\n<!ENTITY lol \"lol\">\n");
        String previous = "lol";
        for (int i = 1; i <= 9; i++) {
            String name = "lol" + i;
            xml.append("<!ENTITY ").append(name).append(" \"").append(("&" + previous + ";").repeat(10)).append("\">\n");
            previous = name;
        }
        return xml.append("]>\n<lolz>&").append(previous).append(";</lolz>\n").toString();
    }

    private static XdmNode parse(Processor processor, String xml) throws SaxonApiException {
        return processor.newDocumentBuilder().build(new StreamSource(new StringReader(xml)));
    }

    @Test
    void entityExpansionIsBounded() {
        Processor processor = SaxonResourceAccess.restrictedProcessor();
        // Unbounded, Saxon's parser ran out of memory after two minutes
        SaxonApiException e = assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> assertThrows(SaxonApiException.class, () -> parse(processor, billionLaughs())));
        assertTrue(e.getMessage().contains("entity expansions"), e.getMessage());
    }

    @Test
    void externalEntitiesDoNotReadLocalFiles() throws Exception {
        Path secret = Files.writeString(tmp.resolve("secret.txt"), "TOP-SECRET");
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY s SYSTEM \"" + secret.toUri() + "\">]><d>&s;</d>";
        XdmNode doc = parse(SaxonResourceAccess.restrictedProcessor(), xxe);
        assertFalse(doc.getStringValue().contains("TOP-SECRET"));
    }

    @Test
    void internalEntitiesStillWork() throws Exception {
        XdmNode doc = parse(SaxonResourceAccess.restrictedProcessor(),
                "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY co \"ACME\">]><d>&co; Inc.</d>");
        assertEquals("ACME Inc.", doc.getStringValue());
    }

    @Test
    void theXPathModeRefusesRemoteDocButReadsLocalFiles() throws Exception {
        XmlServiceImpl service = new XmlServiceImpl();
        RuntimeException refused = assertThrows(RuntimeException.class,
                () -> service.getXmlFromXpath("<r/>", "string(doc('" + remote + "'))"));
        assertTrue(refused.getMessage().contains("Blocked remote resource"), refused.getMessage());
        assertEquals(0, requests.get(), "no request reached the server");

        Path local = Files.writeString(tmp.resolve("local.xml"), "<l>local value</l>");
        assertEquals("local value", service.getXmlFromXpath("<r/>", "string(doc('" + local.toUri() + "'))"));
    }

    @Test
    void gridFormulasRefuseRemoteDoc() throws Exception {
        XmlElement root = new StreamingXmlParser().parse("<r><a>1</a></r>").getRootElement();
        assertThrows(IllegalArgumentException.class,
                () -> XmlGridQuery.evaluate(root, "string(doc('" + remote + "'))"));
        assertEquals(0, requests.get(), "no request reached the server");
        assertEquals("1", XmlGridQuery.evaluate(root, "string(a)").text(), "ordinary formulas still work");
    }
}
