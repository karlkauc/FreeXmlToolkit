package org.fxt.freexmltoolkit.controls.shell.editor;

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

/** XProc pipelines run no programs and reach no network unless allowed. */
@DisplayName("XProc security")
class XProcSecurityTest {

    @TempDir
    Path dir;

    private static String pipeline(String body) {
        return """
                <p:declare-step xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:output port="result"/>
                  %s
                </p:declare-step>
                """.formatted(body);
    }

    @Test
    @DisplayName("os-exec, run and http-request are refused, also from imported libraries")
    void restrictedSteps() throws Exception {
        String osExec = pipeline("<p:os-exec command=\"rm\" args=\"-rf /\"><p:with-input><p:empty/></p:with-input></p:os-exec>");
        assertEquals(List.of("p:os-exec"), XProcSecurity.restrictedSteps(osExec, null));
        XProcRunner.Result result = XProcRunner.runPipeline(osExec, null, null, null);
        assertTrue(result.isError());
        assertTrue(result.text().contains("p:os-exec") && result.text().contains("not allowed"), result.text());

        Files.writeString(dir.resolve("lib.xpl"), """
                <p:library xmlns:p="http://www.w3.org/ns/xproc" version="3.0">
                  <p:declare-step type="my:fetch" xmlns:my="urn:my">
                    <p:output port="result"/>
                    <p:http-request href="http://example.org/"/>
                  </p:declare-step>
                </p:library>
                """);
        Path main = dir.resolve("main.xpl");
        String withImport = pipeline("<p:import href=\"lib.xpl\"/><p:identity><p:with-input><doc/></p:with-input></p:identity>");
        Files.writeString(main, withImport);
        assertEquals(List.of("p:http-request"), XProcSecurity.restrictedSteps(withImport, main.toFile()));
        assertEquals(List.of("p:run"), XProcSecurity.restrictedSteps(pipeline("<p:run><p:with-input/></p:run>"), null));
    }

    @Test
    @DisplayName("documents and doc() from the network are not loaded; local pipelines run")
    void noNetwork() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "<leak/>".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/doc.xml";
            XProcRunner.Result load = XProcRunner.runPipeline(pipeline("<p:load href=\"" + url + "\"/>"), null, null, null);
            assertTrue(load.isError(), load.text());
            assertTrue(load.text().contains("not allowed") || load.text().toLowerCase().contains("network"), load.text());

            XProcRunner.Result doc = XProcRunner.runPipeline(pipeline(
                    "<p:identity><p:with-input select=\"doc('" + url + "')\"><doc/></p:with-input></p:identity>"),
                    null, null, null);
            assertTrue(doc.isError(), doc.text());

            XProcRunner.Result inline = XProcRunner.runPipeline(pipeline(
                    "<p:identity><p:with-input select=\"/\"><p:inline><greeting>hi</greeting></p:inline></p:with-input></p:identity>"),
                    null, null, null);
            assertFalse(inline.isError(), inline.text());
            assertTrue(inline.text().contains("<greeting>hi</greeting>"), inline.text());
            assertEquals(0, requests.get(), "the network was reached");

            // the setting (or the administrator policy) lifts the restriction
            var props = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
            props.set(XProcSecurity.UNRESTRICTED, "true");
            try {
                XProcRunner.Result allowed = XProcRunner.runPipeline(pipeline("<p:load href=\"" + url + "\"/>"), null, null, null);
                assertFalse(allowed.isError(), allowed.text());
                assertEquals(1, requests.get());
            } finally {
                props.set(XProcSecurity.UNRESTRICTED, "false");
            }
        } finally {
            server.stop(0);
        }
    }
}
