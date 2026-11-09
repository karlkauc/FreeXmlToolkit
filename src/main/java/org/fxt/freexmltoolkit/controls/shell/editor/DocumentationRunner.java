package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.service.TaskProgressListener;
import org.fxt.freexmltoolkit.service.XsdDocumentationImageService;
import org.fxt.freexmltoolkit.service.XsdDocumentationPdfService;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.fxt.freexmltoolkit.service.XsdDocumentationWordService;

/**
 * UI-free XSD documentation generation, shared by the Documentation page ({@link DocumentationView})
 * and {@code fxt-cli xsd-doc}: the HTML site from {@link XsdDocumentationService}, PDF and Word from
 * their services fed with the parsed {@link XsdDocumentationData}, all configured by
 * {@link DocumentationOptions}. Diagrams are rendered with Batik and need no JavaFX toolkit.
 *
 * <p>Run off the UI thread — generation walks the whole schema and can be slow.</p>
 */
public final class DocumentationRunner {

    private DocumentationRunner() {
    }

    /**
     * Generates the documentation of {@code xsd}.
     *
     * @param output   the output folder (HTML) or file (PDF, Word)
     * @param options  every generator option
     * @param listener progress of the pipeline steps (nullable)
     * @throws Exception when the schema cannot be read or the output cannot be written
     */
    public static void generate(File xsd, File output, DocumentationOptions options,
                                TaskProgressListener listener) throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.setMarkdownMode(options.markdown());
        service.setParallelProcessing(options.parallel());
        service.setIncludeTypeDefinitionsInSourceCode(options.typeDefinitionsInSource());
        service.setShowDocumentationInSvg(options.diagramDocumentation());
        service.setShowConstraintsInSvg(options.diagramConstraints());
        service.setShowRootConstraintsInSvg(options.rootConstraints());
        service.setShowConstraintTargets(options.constraintTargets());
        service.setGenerateSvgOverviewPage(options.svgOverview());
        service.setAddMetadataInOutput(options.metadata());
        service.setDeduplicateDataDictionaryByType(options.deduplicateDataDictionary());
        if (options.languages() != null) {
            service.setIncludedLanguages(options.languages()); // before parsing: it filters while reading
        }
        if (options.fallbackLanguage() != null) {
            service.setFallbackLanguage(options.fallbackLanguage());
        }
        if (options.favicon() != null) {
            service.setFaviconPath(options.favicon().getAbsolutePath());
        }
        service.setMethod(options.imageFormat());
        if (listener != null) {
            service.setProgressListener(listener);
        }

        switch (options.format()) {
            case PDF -> {
                service.processXsd(options.markdown());
                XsdDocumentationPdfService pdf = new XsdDocumentationPdfService();
                if (listener != null) {
                    pdf.setProgressListener(listener);
                }
                if (options.languages() != null) {
                    pdf.setIncludedLanguages(options.languages());
                }
                pdf.setConfig(options.pdf());
                pdf.setDeduplicateDataDictionaryByType(options.deduplicateDataDictionary());
                pdf.setImageService(imageService(service, options));
                pdf.generatePdfDocumentation(output, service.xsdDocumentationData);
            }
            case DOCX -> {
                service.processXsd(options.markdown());
                XsdDocumentationWordService word = new XsdDocumentationWordService();
                if (listener != null) {
                    word.setProgressListener(listener);
                }
                if (options.languages() != null) {
                    word.setIncludedLanguages(options.languages());
                }
                word.setConfig(options.word());
                word.setDeduplicateDataDictionaryByType(options.deduplicateDataDictionary());
                word.setImageService(imageService(service, options));
                word.generateWordDocumentation(output, service.xsdDocumentationData);
            }
            default -> {
                if (!output.exists() && !output.mkdirs()) {
                    throw new java.io.IOException("cannot create output directory: " + output);
                }
                service.generateXsdDocumentation(output);
            }
        }
    }

    /** The diagram renderer of PDF and Word, with the diagram options of the HTML site. */
    private static XsdDocumentationImageService imageService(XsdDocumentationService service,
                                                             DocumentationOptions options) {
        XsdDocumentationImageService images = new XsdDocumentationImageService(
                service.xsdDocumentationData.getExtendedXsdElementMap());
        images.setShowDocumentation(options.diagramDocumentation());
        images.setShowConstraints(options.diagramConstraints());
        images.setShowRootConstraints(options.rootConstraints());
        images.setShowConstraintTargets(options.constraintTargets());
        return images;
    }

    /**
     * The documentation languages ({@code xml:lang}) used in {@code xsd}; "default" stands for
     * documentation without a language.
     */
    public static Set<String> discoverLanguages(File xsd) throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.getAbsolutePath());
        service.processXsd(false);
        return new LinkedHashSet<>(service.getDiscoveredLanguages());
    }

    /** Generates an HTML documentation site with the default options. @return {@code "OK: <index.html>"} or error. */
    public static String exportHtml(File xsd, File outputDir) {
        String result = run(xsd, outputDir, new DocumentationOptions().format(DocumentationOptions.Format.HTML));
        return result.startsWith("OK:") ? "OK: " + new File(outputDir, "index.html").getAbsolutePath() : result;
    }

    /** Generates a PDF document with the default options. @return {@code "OK: <path>"} or error. */
    public static String exportPdf(File xsd, File outputFile) {
        return run(xsd, outputFile, new DocumentationOptions().format(DocumentationOptions.Format.PDF));
    }

    /** Generates a Word (.docx) document with the default options. @return {@code "OK: <path>"} or error. */
    public static String exportWord(File xsd, File outputFile) {
        return run(xsd, outputFile, new DocumentationOptions().format(DocumentationOptions.Format.DOCX));
    }

    /**
     * {@link #generate} with the result as text.
     *
     * @return {@code "OK: <output>"} or {@code "ERROR: …"}
     */
    public static String run(File xsd, File output, DocumentationOptions options) {
        if (!xsd.isFile()) {
            return "ERROR: file not found: " + xsd;
        }
        try {
            generate(xsd, output, options, null);
            return "OK: " + output.getAbsolutePath();
        } catch (Exception e) {
            return "ERROR: " + (e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
