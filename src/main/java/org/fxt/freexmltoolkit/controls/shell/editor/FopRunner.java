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

    /**
     * The panel's generation options (Figma mockup node 49:2): PDF metadata
     * (title/author/subject), PDF/A-1b conformance, and the page size/orientation,
     * which are passed to the stylesheet as the XSLT parameters {@code page-size}
     * and {@code page-orientation} (honoured by stylesheets that declare them).
     */
    public record PdfOptions(String title, String author, String subject,
                             boolean pdfACompliant, String pageSize, String orientation) {

        public static PdfOptions none() {
            return new PdfOptions("", "", "", false, "", "");
        }
    }

    /** @return {@code "OK: <path>"} on success, otherwise {@code "ERROR: <message>"}. */
    public static String generate(File xmlFile, File xslFile, File pdfOutput) {
        return generate(xmlFile, xslFile, pdfOutput, PdfOptions.none());
    }

    /** @return {@code "OK: <path>"} on success, otherwise {@code "ERROR: <message>"}. */
    public static String generate(File xmlFile, File xslFile, File pdfOutput, PdfOptions options) {
        try {
            HashMap<String, String> parameters = new HashMap<>();
            if (options.pageSize() != null && !options.pageSize().isBlank()) {
                parameters.put("page-size", options.pageSize());
            }
            if (options.orientation() != null && !options.orientation().isBlank()) {
                parameters.put("page-orientation", options.orientation());
            }
            PDFSettings settings = new PDFSettings(parameters, "",
                    nullToEmpty(options.author()), "", "", nullToEmpty(options.title()), "");
            File result = new FOPService().createPdfFile(xmlFile, xslFile, pdfOutput, settings,
                    options.subject(), options.pdfACompliant());
            return "OK: " + result.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
