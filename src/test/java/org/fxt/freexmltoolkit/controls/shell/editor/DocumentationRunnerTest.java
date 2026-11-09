package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void defaultsMatchTheDocumentationPage() {
        DocumentationOptions options = new DocumentationOptions();
        assertEquals(DocumentationOptions.Format.HTML, options.format());
        assertEquals(org.fxt.freexmltoolkit.service.XsdDocumentationService.MarkdownMode.ALL, options.markdown());
        assertTrue(options.typeDefinitionsInSource());
        assertTrue(options.metadata());
        assertTrue(options.svgOverview());
        assertFalse(options.diagramDocumentation());
        assertTrue(options.diagramConstraints());
        assertFalse(options.pdf().isIncludeCoverPage(), "the page starts without a cover page");
        assertFalse(options.word().isIncludeCoverPage());
        assertTrue(options.pdf().isIncludeSchemaDiagram());
        assertNull(options.languages(), "all languages");
    }

    @Test
    void generatesWithOptions(@TempDir Path tmp) throws Exception {
        File out = tmp.resolve("doc.pdf").toFile();
        DocumentationOptions options = new DocumentationOptions()
                .format(DocumentationOptions.Format.PDF)
                .landscape(true)
                .schemaDiagram(false)
                .elementDiagrams(false);
        options.pdf().setTitle("Purchase orders");
        java.util.List<String> steps = new java.util.ArrayList<>();
        DocumentationRunner.generate(XSD, out, options, update -> steps.add(update.taskName()));
        assertTrue(out.length() > 0, "PDF written");
        assertFalse(steps.isEmpty(), "progress reported");
    }

    @Test
    void schemaDiagramStrokesHoldOneColour(@TempDir Path tmp) throws Exception {
        // Repeatable elements once got stroke="#4a90e2,#87ceeb", which FOP rejects in PDF diagrams
        File site = tmp.resolve("site").toFile();
        DocumentationRunner.generate(new File("src/test/resources/testSchema.xsd"), site,
                new DocumentationOptions(), null);
        String svgPage = Files.readString(site.toPath().resolve("schema-svg.html"));
        assertFalse(java.util.regex.Pattern.compile("stroke=\"#[0-9a-fA-F]{6},").matcher(svgPage).find(),
                "a stroke takes one colour");
        assertTrue(svgPage.contains("#87ceeb"), "repeatable elements keep their inner border");
    }

    @Test
    void missingFileReturnsError(@TempDir Path tmp) {
        String result = DocumentationRunner.exportHtml(new File("/no/such.xsd"), tmp.toFile());
        assertTrue(result.startsWith("ERROR:"), result);
    }
}
