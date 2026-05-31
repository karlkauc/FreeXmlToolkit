package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.HashMap;

import org.fxt.freexmltoolkit.domain.PDFSettings;
import org.fxt.freexmltoolkit.service.FOPService;

/**
 * UI-free PDF generation for the PDF/FOP activity: transforms an XML file with
 * an XSL(-FO) stylesheet to PDF via the reused {@link FOPService}. Errors are
 * returned as {@code "ERROR: …"} text rather than thrown.
 */
public final class FopRunner {

    private FopRunner() {
    }

    /** @return {@code "OK: <path>"} on success, otherwise {@code "ERROR: <message>"}. */
    public static String generate(File xmlFile, File xslFile, File pdfOutput) {
        try {
            PDFSettings settings = new PDFSettings(new HashMap<>(), "", "", "", "", "", "");
            File result = new FOPService().createPdfFile(xmlFile, xslFile, pdfOutput, settings);
            return "OK: " + result.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
