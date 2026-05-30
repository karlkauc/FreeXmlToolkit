package org.fxt.freexmltoolkit.controls.shell.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link DocumentationRunner} (no UI): wraps the existing
 * XSD documentation pipeline to export HTML / PDF / Word from an XSD file.
 */
class DocumentationRunnerTest {

    private static final File XSD = new File("src/test/resources/purchageOrder.xsd");

    @Test
    void exportsHtmlDocumentation(@TempDir Path tmp) {
        File outDir = tmp.resolve("html").toFile();
        String result = DocumentationRunner.exportHtml(XSD, outDir);

        assertTrue(result.startsWith("OK:"), result);
        File index = new File(outDir, "index.html");
        assertTrue(index.exists(), "index.html must be generated");
        assertTrue(index.length() > 0, "index.html must not be empty");
    }

    @Test
    void exportsPdfDocumentation(@TempDir Path tmp) throws Exception {
        File out = tmp.resolve("doc.pdf").toFile();
        String result = DocumentationRunner.exportPdf(XSD, out);

        assertTrue(result.startsWith("OK:"), result);
        assertTrue(out.exists() && out.length() > 0, "PDF must be generated");
        byte[] header = new byte[5];
        try (var in = Files.newInputStream(out.toPath())) {
            assertEquals(5, in.read(header));
        }
        assertEquals("%PDF-", new String(header), "must be a real PDF");
    }

    @Test
    void exportsWordDocumentation(@TempDir Path tmp) {
        File out = tmp.resolve("doc.docx").toFile();
        String result = DocumentationRunner.exportWord(XSD, out);

        assertTrue(result.startsWith("OK:"), result);
        assertTrue(out.exists() && out.length() > 0, "Word document must be generated");
    }

    @Test
    void missingFileReturnsError(@TempDir Path tmp) {
        String result = DocumentationRunner.exportHtml(new File("/no/such.xsd"), tmp.toFile());
        assertTrue(result.startsWith("ERROR:"), result);
    }
}
