package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.telemetry.RecordingTelemetryService;
import org.fxt.freexmltoolkit.service.telemetry.Telemetry;
import org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchTransformRunnerTest {

    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="text"/>
              <xsl:template match="/"><xsl:value-of select="count(//item)"/></xsl:template>
            </xsl:stylesheet>
            """;

    @Test
    void runsXsltOverEveryFileAndReportsErrors(@TempDir Path dir) throws Exception {
        File good1 = write(dir, "a.xml", "<root><item/><item/></root>");
        File good2 = write(dir, "b.xml", "<root><item/></root>");
        File bad = write(dir, "c.xml", "<root><item></root>"); // malformed

        List<BatchFileResult> results = BatchTransformRunner.runXsltBatch(
                List.of(good1, good2, bad), XSLT, Map.of(), OutputFormat.TEXT);

        assertEquals(3, results.size());
        assertTrue(results.get(0).ok());
        assertEquals("2", results.get(0).output().strip());
        assertTrue(results.get(1).ok());
        assertFalse(results.get(2).ok(), "malformed XML should fail");
    }

    @Test
    void resolvesRelativeImportsAndInputUrisPerFile(@TempDir Path dir) throws Exception {
        // the stylesheet imports a sibling module; each input reads the "data.xml" next to itself
        Path xsl = dir.resolve("xsl");
        Files.createDirectories(xsl);
        Files.writeString(xsl.resolve("module.xslt"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template name="from-module">module:<xsl:value-of select="doc(resolve-uri('data.xml', base-uri(/)))/d"/></xsl:template>
                </xsl:stylesheet>
                """);
        Path main = xsl.resolve("main.xslt");
        Files.writeString(main, """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:import href="module.xslt"/>
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:call-template name="from-module"/></xsl:template>
                </xsl:stylesheet>
                """);
        Files.createDirectories(dir.resolve("one"));
        Files.createDirectories(dir.resolve("two"));
        write(dir.resolve("one"), "data.xml", "<d>first</d>");
        write(dir.resolve("two"), "data.xml", "<d>second</d>");
        File in1 = write(dir.resolve("one"), "in.xml", "<in/>");
        File in2 = write(dir.resolve("two"), "in.xml", "<in/>");
        String xslt = Files.readString(main);

        List<BatchFileResult> results = BatchTransformRunner.runXsltBatch(
                List.of(in1, in2), xslt, main.toUri().toString(), Map.of(), OutputFormat.TEXT);
        assertTrue(results.get(0).ok(), results.get(0).error());
        assertEquals("module:first", results.get(0).output().strip());
        assertEquals("module:second", results.get(1).output().strip());

        List<BatchFileResult> withoutLocation = BatchTransformRunner.runXsltBatch(
                List.of(in1), xslt, Map.of(), OutputFormat.TEXT);
        assertFalse(withoutLocation.get(0).ok(), "without the stylesheet location the import is not found");
    }

    @Test
    void resolvesRelativeModuleImportsOfAQueryAndInputUrisPerFile(@TempDir Path dir) throws Exception {
        Path xq = dir.resolve("xq");
        Files.createDirectories(xq);
        Files.writeString(xq.resolve("lib.xqm"), """
                module namespace lib = "urn:test:lib";
                declare function lib:label($n as node()) as xs:string {
                  'module:' || doc(resolve-uri('data.xml', base-uri($n)))/d
                };
                """);
        Path main = xq.resolve("main.xq");
        Files.writeString(main, """
                import module namespace lib = "urn:test:lib" at "lib.xqm";
                lib:label(.)
                """);
        Files.createDirectories(dir.resolve("one"));
        Files.createDirectories(dir.resolve("two"));
        write(dir.resolve("one"), "data.xml", "<d>first</d>");
        write(dir.resolve("two"), "data.xml", "<d>second</d>");
        File in1 = write(dir.resolve("one"), "in.xml", "<in/>");
        File in2 = write(dir.resolve("two"), "in.xml", "<in/>");
        String query = Files.readString(main);

        List<BatchFileResult> results = BatchTransformRunner.runXQueryBatch(
                List.of(in1, in2), query, main.toUri().toString(), Map.of(), OutputFormat.TEXT);
        assertTrue(results.get(0).ok(), results.get(0).error());
        assertEquals("module:first", results.get(0).output().strip());
        assertEquals("module:second", results.get(1).output().strip());

        List<BatchFileResult> withoutLocation = BatchTransformRunner.runXQueryBatch(
                List.of(in1), query, Map.of(), OutputFormat.TEXT);
        assertFalse(withoutLocation.get(0).ok(), "without the query location the module is not found");
    }

    @Test
    void recordsOneAnonymousUsageEventPerBatch(@TempDir Path dir) throws Exception {
        File good = write(dir, "secret-name.xml", "<root><item/></root>");
        File bad = write(dir, "b.xml", "<root><item></root>"); // malformed
        RecordingTelemetryService telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        try {
            BatchTransformRunner.runXsltBatch(List.of(good, bad), XSLT, Map.of(), OutputFormat.TEXT);
        } finally {
            Telemetry.reset();
        }

        List<TelemetryEvent> events = telemetry.events("xslt_transform");
        assertEquals(1, events.size());
        TelemetryEvent e = events.get(0);
        assertEquals(2, e.fileCount());
        assertEquals(1, e.errorCount());
        assertEquals(TelemetryEvent.Status.OK, e.status());
        assertFalse(e.toJson().toString().contains("secret-name"), "file names must never be sent");
    }

    @Test
    void writeAllWritesOneFilePerResult(@TempDir Path dir) throws Exception {
        File in = write(dir, "a.xml", "<root><item/></root>");
        List<BatchFileResult> results = BatchTransformRunner.runXsltBatch(
                List.of(in), XSLT, Map.of(), OutputFormat.TEXT);
        Path out = Files.createDirectory(dir.resolve("out"));

        int written = BatchTransformRunner.writeAll(results, out, "txt");

        assertEquals(1, written);
        assertTrue(Files.exists(out.resolve("a.txt")));
    }

    @Test
    void writeAllAutoSuffixesSameBasenameOutputs(@TempDir Path dir) throws Exception {
        Path subA = Files.createDirectory(dir.resolve("a"));
        Path subB = Files.createDirectory(dir.resolve("b"));
        File in1 = write(subA, "data.xml", "<root><item/><item/></root>");
        File in2 = write(subB, "data.xml", "<root><item/></root>");

        List<BatchFileResult> results = BatchTransformRunner.runXsltBatch(
                List.of(in1, in2), XSLT, Map.of(), OutputFormat.TEXT);
        Path out = Files.createDirectory(dir.resolve("out"));

        int written = BatchTransformRunner.writeAll(results, out, "txt");

        assertEquals(2, written);
        assertTrue(Files.exists(out.resolve("data.txt")), "first output keeps base name");
        assertTrue(Files.exists(out.resolve("data_1.txt")), "second output is auto-suffixed");
    }

    private static File write(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p.toFile();
    }
}
