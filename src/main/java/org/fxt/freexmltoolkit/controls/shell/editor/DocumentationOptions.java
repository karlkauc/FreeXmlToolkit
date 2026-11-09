package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;

/**
 * Every option of the XSD documentation generator, shared by the Documentation page
 * ({@link DocumentationView}) and {@code fxt-cli xsd-doc}; run with
 * {@link DocumentationRunner#generate}. A new instance holds the defaults of the Documentation page.
 *
 * <p>The PDF and Word layout options live in their own config objects ({@link #pdf()},
 * {@link #word()}); the options both formats share are set on both with the setters here.</p>
 */
public final class DocumentationOptions {

    /** Output formats. */
    public enum Format { HTML, PDF, DOCX }

    private Format format = Format.HTML;
    private Set<String> languages;
    private String fallbackLanguage;
    private XsdDocumentationService.MarkdownMode markdown = XsdDocumentationService.MarkdownMode.ALL;
    private boolean typeDefinitionsInSource = true;
    private boolean deduplicateDataDictionary;
    private boolean parallel = true;

    private XsdDocumentationService.ImageOutputMethod imageFormat = XsdDocumentationService.ImageOutputMethod.SVG;
    private boolean diagramDocumentation;
    private boolean diagramConstraints = true;
    private boolean rootConstraints;
    private boolean constraintTargets;

    private boolean svgOverview = true;
    private boolean metadata = true;
    private File favicon;

    private final PdfDocumentationConfig pdf = new PdfDocumentationConfig();
    private final WordDocumentationConfig word = new WordDocumentationConfig();

    /** Creates the options with the defaults of the Documentation page. */
    public DocumentationOptions() {
        pdf.setIncludeCoverPage(false);
        word.setIncludeCoverPage(false);
    }

    // ----- general ----------------------------------------------------------------------------

    public Format format() {
        return format;
    }

    public DocumentationOptions format(Format format) {
        this.format = format == null ? Format.HTML : format;
        return this;
    }

    /** Documentation languages to include ({@code null} or empty = all). */
    public Set<String> languages() {
        return languages;
    }

    public DocumentationOptions languages(Set<String> languages) {
        this.languages = languages == null || languages.isEmpty() ? null : new LinkedHashSet<>(languages);
        return this;
    }

    public String fallbackLanguage() {
        return fallbackLanguage;
    }

    public DocumentationOptions fallbackLanguage(String fallbackLanguage) {
        this.fallbackLanguage = fallbackLanguage == null || fallbackLanguage.isBlank() ? null : fallbackLanguage;
        return this;
    }

    public XsdDocumentationService.MarkdownMode markdown() {
        return markdown;
    }

    public DocumentationOptions markdown(XsdDocumentationService.MarkdownMode markdown) {
        this.markdown = markdown == null ? XsdDocumentationService.MarkdownMode.ALL : markdown;
        return this;
    }

    public boolean typeDefinitionsInSource() {
        return typeDefinitionsInSource;
    }

    public DocumentationOptions typeDefinitionsInSource(boolean value) {
        this.typeDefinitionsInSource = value;
        return this;
    }

    public boolean deduplicateDataDictionary() {
        return deduplicateDataDictionary;
    }

    public DocumentationOptions deduplicateDataDictionary(boolean value) {
        this.deduplicateDataDictionary = value;
        return this;
    }

    public boolean parallel() {
        return parallel;
    }

    public DocumentationOptions parallel(boolean value) {
        this.parallel = value;
        return this;
    }

    // ----- diagrams ---------------------------------------------------------------------------

    public XsdDocumentationService.ImageOutputMethod imageFormat() {
        return imageFormat;
    }

    /** Diagram format of the HTML site; PDF keeps vector diagrams unless PNG or JPG is chosen. */
    public DocumentationOptions imageFormat(XsdDocumentationService.ImageOutputMethod imageFormat) {
        this.imageFormat = imageFormat == null ? XsdDocumentationService.ImageOutputMethod.SVG : imageFormat;
        pdf.setVectorDiagrams(this.imageFormat == XsdDocumentationService.ImageOutputMethod.SVG);
        return this;
    }

    public boolean diagramDocumentation() {
        return diagramDocumentation;
    }

    public DocumentationOptions diagramDocumentation(boolean value) {
        this.diagramDocumentation = value;
        return this;
    }

    public boolean diagramConstraints() {
        return diagramConstraints;
    }

    public DocumentationOptions diagramConstraints(boolean value) {
        this.diagramConstraints = value;
        return this;
    }

    public boolean rootConstraints() {
        return rootConstraints;
    }

    public DocumentationOptions rootConstraints(boolean value) {
        this.rootConstraints = value;
        return this;
    }

    public boolean constraintTargets() {
        return constraintTargets;
    }

    public DocumentationOptions constraintTargets(boolean value) {
        this.constraintTargets = value;
        return this;
    }

    // ----- HTML -------------------------------------------------------------------------------

    public boolean svgOverview() {
        return svgOverview;
    }

    public DocumentationOptions svgOverview(boolean value) {
        this.svgOverview = value;
        return this;
    }

    public boolean metadata() {
        return metadata;
    }

    public DocumentationOptions metadata(boolean value) {
        this.metadata = value;
        return this;
    }

    public File favicon() {
        return favicon;
    }

    public DocumentationOptions favicon(File favicon) {
        this.favicon = favicon;
        return this;
    }

    // ----- PDF + Word -------------------------------------------------------------------------

    /** The PDF layout (also holds the options PDF and Word share; set those with the setters here). */
    public PdfDocumentationConfig pdf() {
        return pdf;
    }

    /** The Word layout. */
    public WordDocumentationConfig word() {
        return word;
    }

    /**
     * Page size of PDF and Word. Word has no A3 and falls back to A4.
     */
    public DocumentationOptions pageSize(PdfDocumentationConfig.PageSize size) {
        pdf.setPageSize(size);
        word.setPageSize(switch (size) {
            case LETTER -> WordDocumentationConfig.PageSize.LETTER;
            case LEGAL -> WordDocumentationConfig.PageSize.LEGAL;
            default -> WordDocumentationConfig.PageSize.A4;
        });
        return this;
    }

    public DocumentationOptions landscape(boolean landscape) {
        pdf.setOrientation(landscape ? PdfDocumentationConfig.Orientation.LANDSCAPE
                : PdfDocumentationConfig.Orientation.PORTRAIT);
        word.setOrientation(landscape ? WordDocumentationConfig.Orientation.LANDSCAPE
                : WordDocumentationConfig.Orientation.PORTRAIT);
        return this;
    }

    public DocumentationOptions coverPage(boolean value) {
        pdf.setIncludeCoverPage(value);
        word.setIncludeCoverPage(value);
        return this;
    }

    public DocumentationOptions toc(boolean value) {
        pdf.setIncludeToc(value);
        word.setIncludeToc(value);
        return this;
    }

    public DocumentationOptions dataDictionary(boolean value) {
        pdf.setIncludeDataDictionary(value);
        word.setIncludeDataDictionary(value);
        return this;
    }

    public DocumentationOptions schemaDiagram(boolean value) {
        pdf.setIncludeSchemaDiagram(value);
        word.setIncludeSchemaDiagram(value);
        return this;
    }

    public DocumentationOptions elementDiagrams(boolean value) {
        pdf.setIncludeElementDiagrams(value);
        word.setIncludeElementDiagrams(value);
        return this;
    }

    public DocumentationOptions elementReference(PdfDocumentationConfig.ElementReference value) {
        pdf.setElementReference(value);
        word.setElementReference(value);
        return this;
    }

    public DocumentationOptions sourceCode(boolean value) {
        pdf.setIncludeSourceCode(value);
        word.setIncludeSourceCode(value);
        return this;
    }
}
