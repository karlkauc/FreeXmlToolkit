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
