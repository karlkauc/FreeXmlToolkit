package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.service.XsdDocumentationPdfService;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.fxt.freexmltoolkit.service.XsdDocumentationWordService;

import java.io.File;

/**
 * UI-free XSD documentation export for the shell, reusing the existing
 * {@link XsdDocumentationService} pipeline (HTML), and the Word/PDF services
 * fed from the parsed {@link XsdDocumentationData}. Results are returned as
 * {@code "OK: <path>"} or {@code "ERROR: …"}.
 *
 * <p>Diagram images (which require the heavy {@code XsdDocumentationImageService})
 * are disabled here; the shell exports text documentation. Run these off the UI
 * thread — generation walks the whole schema and can be slow.
 */
public final class DocumentationRunner {

    private DocumentationRunner() {
    }

    /** Generates an HTML documentation site into {@code outputDir}. @return {@code "OK: <index.html>"} or error. */
    public static String exportHtml(File xsd, File outputDir) {
        if (!xsd.isFile()) {
            return "ERROR: file not found: " + xsd;
        }
        try {
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return "ERROR: cannot create output directory: " + outputDir;
            }
            XsdDocumentationService service = new XsdDocumentationService();
            service.setXsdFilePath(xsd.getAbsolutePath());
            service.generateXsdDocumentation(outputDir); // parses + writes the site
            return "OK: " + new File(outputDir, "index.html").getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Generates a single PDF document. @return {@code "OK: <path>"} or error. */
    public static String exportPdf(File xsd, File outputFile) {
        if (!xsd.isFile()) {
            return "ERROR: file not found: " + xsd;
        }
        try {
            XsdDocumentationData data = parse(xsd);
            PdfDocumentationConfig config = new PdfDocumentationConfig();
            config.setIncludeSchemaDiagram(false);
            config.setIncludeElementDiagrams(false);
            XsdDocumentationPdfService pdf = new XsdDocumentationPdfService();
            pdf.setConfig(config);
            pdf.setDocumentationData(data);
            pdf.generatePdfDocumentation(outputFile, data);
            return "OK: " + outputFile.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Generates a single Word (.docx) document. @return {@code "OK: <path>"} or error. */
    public static String exportWord(File xsd, File outputFile) {
        if (!xsd.isFile()) {
            return "ERROR: file not found: " + xsd;
        }
        try {
            XsdDocumentationData data = parse(xsd);
            WordDocumentationConfig config = new WordDocumentationConfig();
            config.setIncludeSchemaDiagram(false);
            config.setIncludeElementDiagrams(false);
            XsdDocumentationWordService word = new XsdDocumentationWordService();
            word.setConfig(config);
            word.setDocumentationData(data);
            word.generateWordDocumentation(outputFile, data);
            return "OK: " + outputFile.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Parses the XSD into documentation data shared by the Word/PDF generators. */
    private static XsdDocumentationData parse(File xsd) throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.processXsd(false);
        return service.xsdDocumentationData;
    }
}
