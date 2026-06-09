package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link PdfPreviewRenderer} (no UI): reports the page count and renders a
 * single page on demand (lazy — not the whole document), reusing PDFBox.
 */
class PdfPreviewRendererTest {

    private File twoPagePdf(Path tmp) throws Exception {
        File pdf = tmp.resolve("doc.pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(pdf);
        }
        return pdf;
    }

    @Test
    void reportsPageCount(@TempDir Path tmp) throws Exception {
        assertEquals(2, PdfPreviewRenderer.pageCount(twoPagePdf(tmp)));
    }

    @Test
    void rendersASinglePageOnDemand(@TempDir Path tmp) throws Exception {
        BufferedImage page = PdfPreviewRenderer.renderPage(twoPagePdf(tmp), 0, 96f);
        assertNotNull(page);
        assertTrue(page.getWidth() > 0 && page.getHeight() > 0, "rendered page must have positive dimensions");
    }

    @Test
    void rejectsOutOfRangePage(@TempDir Path tmp) throws Exception {
        File pdf = twoPagePdf(tmp);
        assertThrows(Exception.class, () -> PdfPreviewRenderer.renderPage(pdf, 9, 96f));
    }
}
