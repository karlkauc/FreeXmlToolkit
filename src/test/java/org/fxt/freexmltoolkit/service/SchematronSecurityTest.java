package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

/**
 * Schematron runs on untrusted documents and rules: no external entities (local files) from the
 * document or the Schematron, no network access from {@code doc()} in a rule.
 */
@DisplayName("Schematron security")
class SchematronSecurityTest {

    private static final String SECRET = "TOP-SECRET-1234";

    @TempDir
    Path dir;

    private static String messages(List<SchematronService.SchematronValidationError> errors) {
        return errors.stream().map(SchematronService.SchematronValidationError::message).reduce("", (a, b) -> a + "|" + b);
    }

    @Test
    @DisplayName("an external entity in the document does not read a local file")
    void noXxeFromTheDocument() throws Exception {
        Path secret = Files.writeString(dir.resolve("secret.txt"), SECRET);
        Path sch = Files.writeString(dir.resolve("rules.sch"), """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern><rule context="/a"><report test="true()">value: <value-of select="."/></report></rule></pattern>
                </schema>
                """);
        String xml = "<!DOCTYPE a [<!ENTITY s SYSTEM \"" + secret.toUri() + "\">]><a>&s;</a>";
        List<SchematronService.SchematronValidationError> errors;
        try {
            errors = new SchematronServiceImpl().validateXml(xml, sch.toFile());
        } catch (SchematronLoadException refused) {
            return;    // refusing the document is fine
        }
        assertFalse(messages(errors).contains(SECRET), messages(errors));
        Path file = Files.writeString(dir.resolve("doc.xml"), xml);
        List<SchematronService.SchematronValidationError> fromFile;
        try {
            fromFile = new SchematronServiceImpl().validateXmlFile(file.toFile(), sch.toFile());
        } catch (SchematronLoadException refused) {
            return;
        }
        assertFalse(messages(fromFile).contains(SECRET), messages(fromFile));
    }

    @Test
    @DisplayName("an external entity in the Schematron does not read a local file")
    void noXxeFromTheSchematron() throws Exception {
        Path secret = Files.writeString(dir.resolve("secret.txt"), SECRET);
        Path sch = Files.writeString(dir.resolve("rules.sch"), "<!DOCTYPE schema [<!ENTITY s SYSTEM \"" + secret.toUri()
                + "\">]>\n<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\" queryBinding=\"xslt2\">"
                + "<pattern><rule context=\"/a\"><report test=\"true()\">&s;</report></rule></pattern></schema>");
        List<SchematronService.SchematronValidationError> errors;
        try {
            errors = new SchematronServiceImpl().validateXml("<a/>", sch.toFile());
        } catch (SchematronLoadException refused) {
            return;
        }
        assertFalse(messages(errors).contains(SECRET), messages(errors));
    }

    @Test
    @DisplayName("doc() of a remote URL in a rule makes no request")
    void noNetworkFromRules() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "<leak/>".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/exfiltrate";
            Path sch = Files.writeString(dir.resolve("rules.sch"), """
                    <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                      <pattern><rule context="/a"><report test="exists(doc('%s'))">fetched</report></rule></pattern>
                    </schema>
                    """.formatted(url));
            try {
                new SchematronServiceImpl().validateXml("<a/>", sch.toFile());
            } catch (SchematronLoadException failed) {
                // a refused doc() may fail the run: fine
            }
            assertEquals(0, requests.get(), "doc() reached the server");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("local doc() still works")
    void localDocWorks() throws Exception {
        Files.writeString(dir.resolve("codes.xml"), "<codes><c>A</c></codes>");
        Path sch = Files.writeString(dir.resolve("rules.sch"), """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern><rule context="/a"><assert test=". = doc('codes.xml')//c">bad code</assert></rule></pattern>
                </schema>
                """);
        assertTrue(new SchematronServiceImpl().validateXml("<a>A</a>", sch.toFile()).isEmpty());
        assertEquals("bad code", messages(new SchematronServiceImpl().validateXml("<a>B</a>", sch.toFile())).substring(1));
    }
}
