package org.fxt.freexmltoolkit.controls.shell.editor;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * UI-free PDF rasterisation for the in-app preview, reusing PDFBox. Pages are
 * rendered <em>on demand</em> (one at a time) rather than all up front, so a
 * large document does not blow up memory the way the legacy viewer did.
 */
public final class PdfPreviewRenderer {

    private PdfPreviewRenderer() {
    }

    /** @return the number of pages in {@code pdf}. */
    public static int pageCount(File pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    /**
     * Renders a single page to an image at the given DPI.
     *
     * @param pageIndex zero-based page index
     * @return the rendered page
     * @throws IOException              if the PDF cannot be read
     * @throws IndexOutOfBoundsException if {@code pageIndex} is out of range
     */
    public static BufferedImage renderPage(File pdf, int pageIndex, float dpi) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (pageIndex < 0 || pageIndex >= document.getNumberOfPages()) {
                throw new IndexOutOfBoundsException(
                        "page " + pageIndex + " out of range (0.." + (document.getNumberOfPages() - 1) + ")");
            }
            return new PDFRenderer(document).renderImageWithDPI(pageIndex, dpi);
        }
    }
}
