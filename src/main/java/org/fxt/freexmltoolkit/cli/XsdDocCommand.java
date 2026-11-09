package org.fxt.freexmltoolkit.cli;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.DocumentationOptions;
import org.fxt.freexmltoolkit.controls.shell.editor.DocumentationRunner;
import org.fxt.freexmltoolkit.domain.PdfDocumentationConfig;
import org.fxt.freexmltoolkit.domain.WordDocumentationConfig;
import org.fxt.freexmltoolkit.service.TaskProgressListener;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli xsd-doc}: generates the documentation of an XML Schema as an HTML site, a PDF or
 * a Word document with every option of the application's documentation generator
 * ({@link DocumentationOptions}, run by {@link DocumentationRunner#generate}).
 */
@Command(name = "xsd-doc",
        mixinStandardHelpOptions = true,
        header = "Generate the documentation of an XML Schema (HTML site, PDF or Word).",
        description = {
                "Documents every global element, type and group of the schema with diagrams, the "
                        + "documentation texts (all or some languages, Markdown rendered) and a data dictionary.",
                "The format follows the extension of --output (.pdf, .docx) unless --format is given; "
                        + "otherwise an HTML site is written into the folder --output. Options of another "
                        + "format are ignored with a warning."
        },
        sortOptions = false,
        footer = {
                "  # HTML site with the German and English documentation, German as fallback",
                "  fxt-cli xsd-doc order.xsd -o site --language de,en --fallback-language de",
                "",
                "  # PDF in landscape with a draft watermark and the green colour scheme",
                "  fxt-cli xsd-doc order.xsd -o order.pdf --landscape \\",
                "      --watermark draft --color-scheme green",
                "",
                "  # Word document without diagrams, with a cover page",
                "  fxt-cli xsd-doc order.xsd -o order.docx --cover-page \\",
                "      --no-schema-diagram --no-element-diagrams",
                "",
                "  # Which documentation languages does the schema use?",
                "  fxt-cli xsd-doc order.xsd --list-languages"
        })
final class XsdDocCommand implements Callable<Integer> {

    /** Documentation formats. */
    enum DocFormat { HTML, PDF, DOCX }

    /** Markdown rendering of the documentation texts. */
    enum MarkdownArg {
        all(XsdDocumentationService.MarkdownMode.ALL),
        marked(XsdDocumentationService.MarkdownMode.PER_NODE),
        off(XsdDocumentationService.MarkdownMode.OFF);

        final XsdDocumentationService.MarkdownMode mode;

        MarkdownArg(XsdDocumentationService.MarkdownMode mode) {
            this.mode = mode;
        }
    }

    enum ImageArg { svg, png, jpg }

    enum PageSizeArg {
        a4(PdfDocumentationConfig.PageSize.A4), letter(PdfDocumentationConfig.PageSize.LETTER),
        legal(PdfDocumentationConfig.PageSize.LEGAL), a3(PdfDocumentationConfig.PageSize.A3);

        final PdfDocumentationConfig.PageSize size;

        PageSizeArg(PdfDocumentationConfig.PageSize size) {
            this.size = size;
        }
    }

    enum ElementReferenceArg {
        declarations(PdfDocumentationConfig.ElementReference.DECLARATIONS),
        all(PdfDocumentationConfig.ElementReference.ALL_OCCURRENCES),
        off(PdfDocumentationConfig.ElementReference.OFF);

        final PdfDocumentationConfig.ElementReference value;

        ElementReferenceArg(PdfDocumentationConfig.ElementReference value) {
            this.value = value;
        }
    }

    enum ColorSchemeArg {
        blue(PdfDocumentationConfig.ColorScheme.BLUE), green(PdfDocumentationConfig.ColorScheme.GREEN),
        purple(PdfDocumentationConfig.ColorScheme.PURPLE), grayscale(PdfDocumentationConfig.ColorScheme.GRAYSCALE),
        professional(PdfDocumentationConfig.ColorScheme.PROFESSIONAL);

        final PdfDocumentationConfig.ColorScheme value;

        ColorSchemeArg(PdfDocumentationConfig.ColorScheme value) {
            this.value = value;
        }
    }

    enum WatermarkArg {
        none(PdfDocumentationConfig.Watermark.NONE), draft(PdfDocumentationConfig.Watermark.DRAFT),
        confidential(PdfDocumentationConfig.Watermark.CONFIDENTIAL),
        internal(PdfDocumentationConfig.Watermark.INTERNAL_USE_ONLY);

        final PdfDocumentationConfig.Watermark value;

        WatermarkArg(PdfDocumentationConfig.Watermark value) {
            this.value = value;
        }
    }

    enum PositionArg {
        left(PdfDocumentationConfig.PageNumberPosition.LEFT),
        center(PdfDocumentationConfig.PageNumberPosition.CENTER),
        right(PdfDocumentationConfig.PageNumberPosition.RIGHT);

        final PdfDocumentationConfig.PageNumberPosition value;

        PositionArg(PdfDocumentationConfig.PageNumberPosition value) {
            this.value = value;
        }
    }

    enum MarginsArg {
        narrow(PdfDocumentationConfig.Margins.NARROW), normal(PdfDocumentationConfig.Margins.NORMAL),
        wide(PdfDocumentationConfig.Margins.WIDE);

        final PdfDocumentationConfig.Margins value;

        MarginsArg(PdfDocumentationConfig.Margins value) {
            this.value = value;
        }
    }

    enum FontArg {
        helvetica(PdfDocumentationConfig.FontFamily.HELVETICA), times(PdfDocumentationConfig.FontFamily.TIMES),
        courier(PdfDocumentationConfig.FontFamily.COURIER);

        final PdfDocumentationConfig.FontFamily value;

        FontArg(PdfDocumentationConfig.FontFamily value) {
            this.value = value;
        }
    }

    enum HeadingArg {
        blue(PdfDocumentationConfig.HeadingStyle.BOLD_BLUE), black(PdfDocumentationConfig.HeadingStyle.BOLD_BLACK),
        underlined(PdfDocumentationConfig.HeadingStyle.UNDERLINED);

        final PdfDocumentationConfig.HeadingStyle value;

        HeadingArg(PdfDocumentationConfig.HeadingStyle value) {
            this.value = value;
        }
    }

    enum SpacingArg {
        compact(PdfDocumentationConfig.LineSpacing.COMPACT), normal(PdfDocumentationConfig.LineSpacing.NORMAL),
        relaxed(PdfDocumentationConfig.LineSpacing.RELAXED);

        final PdfDocumentationConfig.LineSpacing value;

        SpacingArg(PdfDocumentationConfig.LineSpacing value) {
            this.value = value;
        }
    }

    enum HeaderFooterArg {
        standard(PdfDocumentationConfig.HeaderFooterStyle.STANDARD),
        minimal(PdfDocumentationConfig.HeaderFooterStyle.MINIMAL),
        none(PdfDocumentationConfig.HeaderFooterStyle.NONE);

        final PdfDocumentationConfig.HeaderFooterStyle value;

        HeaderFooterArg(PdfDocumentationConfig.HeaderFooterStyle value) {
            this.value = value;
        }
    }

    enum TableArg {
        bordered(PdfDocumentationConfig.TableStyle.BORDERED), zebra(PdfDocumentationConfig.TableStyle.ZEBRA_STRIPES),
        minimal(PdfDocumentationConfig.TableStyle.MINIMAL), grid(PdfDocumentationConfig.TableStyle.FULL_GRID);

        final PdfDocumentationConfig.TableStyle value;

        TableArg(PdfDocumentationConfig.TableStyle value) {
            this.value = value;
        }
    }

    enum WordHeaderArg {
        professional(WordDocumentationConfig.HeaderStyle.PROFESSIONAL),
        minimal(WordDocumentationConfig.HeaderStyle.MINIMAL),
        colorful(WordDocumentationConfig.HeaderStyle.COLORFUL);

        final WordDocumentationConfig.HeaderStyle value;

        WordHeaderArg(WordDocumentationConfig.HeaderStyle value) {
            this.value = value;
        }
    }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(index = "0", paramLabel = "XSD", description = "The XML Schema.")
    Path xsd;

    @Option(names = {"-o", "--output"}, paramLabel = "PATH",
            description = "Output folder (HTML) or file (PDF, Word). Required unless --list-languages.")
    Path output;

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT",
            description = "html, pdf or docx. Default: from the extension of --output (.pdf, .docx), else html.")
    DocFormat format;

    @Option(names = "--list-languages",
            description = "Print the documentation languages (xml:lang) of the schema and exit.")
    boolean listLanguages;

    @ArgGroup(validate = false, heading = "%nContent:%n", order = 1)
    Content content = new Content();

    @ArgGroup(validate = false, heading = "%nDiagrams:%n", order = 2)
    Diagrams diagrams = new Diagrams();

    @ArgGroup(validate = false, heading = "%nHTML site:%n", order = 3)
    Html html = new Html();

    @ArgGroup(validate = false, heading = "%nPDF and Word:%n", order = 4)
    Document document = new Document();

    @ArgGroup(validate = false, heading = "%nPDF only:%n", order = 5)
    Pdf pdf = new Pdf();

    @ArgGroup(validate = false, heading = "%nWord only:%n", order = 6)
    Word word = new Word();

    static final class Content {
        @Option(names = "--language", split = ",", paramLabel = "LANG",
                description = "Only documentation in these languages (xml:lang, comma-separated; "
                        + "documentation without a language is always kept). Default: all.")
        List<String> languages;

        @Option(names = "--fallback-language", paramLabel = "LANG",
                description = "Language the HTML site shows when the reader's language is missing.")
        String fallbackLanguage;

        @Option(names = "--markdown", paramLabel = "MODE", defaultValue = "all",
                description = "Render documentation as Markdown: all, marked (only where xs:appinfo sets "
                        + "markdown=\"true\") or off. Default: ${DEFAULT-VALUE}.")
        MarkdownArg markdown = MarkdownArg.all;

        @Option(names = "--no-type-definitions", negatable = true, defaultValue = "true",
                description = "Show the type definitions in the source-code views. Default: ${DEFAULT-VALUE}.")
        boolean typeDefinitions = true;

        @Option(names = "--dedupe-data-dictionary",
                description = "One data-dictionary row per type instead of per usage.")
        boolean dedupe;

        @Option(names = "--no-parallel", negatable = true, defaultValue = "true",
                description = "Process the schema on several threads. Default: ${DEFAULT-VALUE}.")
        boolean parallel = true;
    }

    static final class Diagrams {
        @Option(names = "--image-format", paramLabel = "FORMAT", defaultValue = "svg",
                description = "Diagram images: svg, png or jpg (PDF: svg = vector diagrams). "
                        + "Default: ${DEFAULT-VALUE}.")
        ImageArg imageFormat = ImageArg.svg;

        @Option(names = "--diagram-docs", description = "Show documentation texts inside the diagrams.")
        boolean documentation;

        @Option(names = "--no-diagram-constraints", negatable = true, defaultValue = "true",
                description = "Show identity constraints and assertions on diagram cards. Default: ${DEFAULT-VALUE}.")
        boolean constraints = true;

        @Option(names = "--root-constraints", description = "Also show the constraints of the root card.")
        boolean rootConstraints;

        @Option(names = "--constraint-targets",
                description = "Mark the fields that identity constraints point at.")
        boolean constraintTargets;
    }

    static final class Html {
        @Option(names = "--no-svg-overview", negatable = true, defaultValue = "true",
                description = "Write the page with the whole-schema diagram. Default: ${DEFAULT-VALUE}.")
        boolean svgOverview = true;

        @Option(names = "--no-metadata", negatable = true, defaultValue = "true",
                description = "Add generator, date and schema metadata. Default: ${DEFAULT-VALUE}.")
        boolean metadata = true;

        @Option(names = "--favicon", paramLabel = "FILE", description = "Favicon of the site (.ico, .png, .svg).")
        Path favicon;
    }

    static final class Document {
        @Option(names = "--page-size", paramLabel = "SIZE", defaultValue = "a4",
                description = "a4, letter, legal or a3 (PDF only). Default: ${DEFAULT-VALUE}.")
        PageSizeArg pageSize = PageSizeArg.a4;

        @Option(names = "--landscape", description = "Landscape instead of portrait pages.")
        boolean landscape;

        @Option(names = "--cover-page", description = "Start with a cover page.")
        boolean coverPage;

        @Option(names = "--no-toc", negatable = true, defaultValue = "true",
                description = "Table of contents. Default: ${DEFAULT-VALUE}.")
        boolean toc = true;

        @Option(names = "--no-data-dictionary", negatable = true, defaultValue = "true",
                description = "Data dictionary of all elements and attributes. Default: ${DEFAULT-VALUE}.")
        boolean dataDictionary = true;

        @Option(names = "--no-schema-diagram", negatable = true, defaultValue = "true",
                description = "Diagram of the whole schema. Default: ${DEFAULT-VALUE}.")
        boolean schemaDiagram = true;

        @Option(names = "--no-element-diagrams", negatable = true, defaultValue = "true",
                description = "A diagram per element. Default: ${DEFAULT-VALUE}.")
        boolean elementDiagrams = true;

        @Option(names = "--element-reference", paramLabel = "SCOPE", defaultValue = "declarations",
                description = "Element sections: declarations (one per declaration), all (every "
                        + "occurrence) or off. Default: ${DEFAULT-VALUE}.")
        ElementReferenceArg elementReference = ElementReferenceArg.declarations;

        @Option(names = "--source-code", description = "Include the XSD source of each component.")
        boolean sourceCode;
    }

    static final class Pdf {
        @Option(names = "--color-scheme", paramLabel = "SCHEME", defaultValue = "blue",
                description = "blue, green, purple, grayscale or professional. Default: ${DEFAULT-VALUE}.")
        ColorSchemeArg colorScheme = ColorSchemeArg.blue;

        @Option(names = "--watermark", paramLabel = "TEXT", defaultValue = "none",
                description = "none, draft, confidential or internal. Default: ${DEFAULT-VALUE}.")
        WatermarkArg watermark = WatermarkArg.none;

        @Option(names = "--no-page-numbers", negatable = true, defaultValue = "true",
                description = "Page numbers. Default: ${DEFAULT-VALUE}.")
        boolean pageNumbers = true;

        @Option(names = "--page-number-position", paramLabel = "POS", defaultValue = "center",
                description = "left, center or right. Default: ${DEFAULT-VALUE}.")
        PositionArg pageNumberPosition = PositionArg.center;

        @Option(names = "--no-bookmarks", negatable = true, defaultValue = "true",
                description = "PDF bookmarks (outline). Default: ${DEFAULT-VALUE}.")
        boolean bookmarks = true;

        @Option(names = "--margins", paramLabel = "SIZE", defaultValue = "normal",
                description = "narrow (15 mm), normal (20 mm) or wide (30 mm). Default: ${DEFAULT-VALUE}.")
        MarginsArg margins = MarginsArg.normal;

        @Option(names = "--font", paramLabel = "FONT", defaultValue = "helvetica",
                description = "helvetica, times or courier. Default: ${DEFAULT-VALUE}.")
        FontArg font = FontArg.helvetica;

        @Option(names = "--font-size", paramLabel = "PT", defaultValue = "11",
                description = "Body text size, 9 to 12 points. Default: ${DEFAULT-VALUE}.")
        int fontSize = 11;

        @Option(names = "--heading-style", paramLabel = "STYLE", defaultValue = "blue",
                description = "blue, black or underlined. Default: ${DEFAULT-VALUE}.")
        HeadingArg headingStyle = HeadingArg.blue;

        @Option(names = "--line-spacing", paramLabel = "SPACING", defaultValue = "normal",
                description = "compact, normal or relaxed. Default: ${DEFAULT-VALUE}.")
        SpacingArg lineSpacing = SpacingArg.normal;

        @Option(names = "--header-style", paramLabel = "STYLE", defaultValue = "standard",
                description = "Page header: standard, minimal or none. Default: ${DEFAULT-VALUE}.")
        HeaderFooterArg headerStyle = HeaderFooterArg.standard;

        @Option(names = "--footer-style", paramLabel = "STYLE", defaultValue = "standard",
                description = "Page footer: standard, minimal or none. Default: ${DEFAULT-VALUE}.")
        HeaderFooterArg footerStyle = HeaderFooterArg.standard;

        @Option(names = "--table-style", paramLabel = "STYLE", defaultValue = "bordered",
                description = "bordered, zebra, minimal or grid. Default: ${DEFAULT-VALUE}.")
        TableArg tableStyle = TableArg.bordered;

        @Option(names = "--no-schema-overview", negatable = true, defaultValue = "true",
                description = "Schema overview chapter. Default: ${DEFAULT-VALUE}.")
        boolean schemaOverview = true;

        @Option(names = "--no-complex-types", negatable = true, defaultValue = "true",
                description = "Complex types chapter. Default: ${DEFAULT-VALUE}.")
        boolean complexTypes = true;

        @Option(names = "--no-simple-types", negatable = true, defaultValue = "true",
                description = "Simple types chapter. Default: ${DEFAULT-VALUE}.")
        boolean simpleTypes = true;

        @Option(names = "--raster-scale", paramLabel = "FACTOR", defaultValue = "1",
                description = "Scale of png/jpg diagrams, 0.5 to 4. Default: ${DEFAULT-VALUE}.")
        float rasterScale = 1f;

        @Option(names = "--title", paramLabel = "TEXT", description = "Document title. Default: the schema name.")
        String title;

        @Option(names = "--author", paramLabel = "TEXT", description = "Document author. Default: the user name.")
        String author;

        @Option(names = "--subject", paramLabel = "TEXT", description = "Document subject (PDF properties).")
        String subject;

        @Option(names = "--keywords", paramLabel = "TEXT", description = "Document keywords (PDF properties).")
        String keywords;
    }

    static final class Word {
        @Option(names = "--word-header-style", paramLabel = "STYLE", defaultValue = "professional",
                description = "professional, minimal or colorful. Default: ${DEFAULT-VALUE}.")
        WordHeaderArg headerStyle = WordHeaderArg.professional;
    }

    /** Options that only one or two formats use, with the formats they apply to. */
    private static final String[] HTML_ONLY = {"--fallback-language", "--svg-overview", "--metadata", "--favicon"};
    private static final String[] DOCUMENT_ONLY = {"--page-size", "--landscape", "--cover-page", "--toc",
            "--data-dictionary", "--schema-diagram", "--element-diagrams", "--element-reference", "--source-code"};
    private static final String[] PDF_ONLY = {"--color-scheme", "--watermark", "--page-numbers",
            "--page-number-position", "--bookmarks", "--margins", "--font", "--font-size", "--heading-style",
            "--line-spacing", "--header-style", "--footer-style", "--table-style", "--schema-overview",
            "--complex-types", "--simple-types", "--raster-scale", "--title", "--author", "--subject", "--keywords"};
    private static final String[] WORD_ONLY = {"--word-header-style"};

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        CliSupport.requireFile(commandLine, xsd, "XML Schema");
        CliSupport.initServices();
        File schema = xsd.toAbsolutePath().toFile();
        if (listLanguages) {
            Set<String> languages = DocumentationRunner.discoverLanguages(schema);
            languages.forEach(commandLine.getOut()::println);
            return ExitCodes.OK;
        }
        if (output == null) {
            throw new CommandLine.ParameterException(commandLine, "Missing required option: '--output=PATH'");
        }
        DocFormat target = format != null ? format : formatOf(output);
        DocumentationOptions options = toOptions(target);
        warnAboutIgnoredOptions(target);

        boolean quiet = parent != null && parent.quiet;
        TaskProgressListener progress = parent != null && parent.verbose ? this::printProgress : null;
        File out = output.toAbsolutePath().toFile();
        DocumentationRunner.generate(schema, out, options, progress);
        if (!quiet) {
            File written = target == DocFormat.HTML ? new File(out, "index.html") : out;
            commandLine.getErr().println("Wrote " + written.getPath());
        }
        return ExitCodes.OK;
    }

    /** Maps the command line onto the generator options (also used by the tests). */
    DocumentationOptions toOptions(DocFormat target) {
        CommandLine commandLine = spec.commandLine();
        Content c = content != null ? content : new Content();
        Diagrams d = diagrams != null ? diagrams : new Diagrams();
        Html h = html != null ? html : new Html();
        Document doc = document != null ? document : new Document();
        Pdf p = pdf != null ? pdf : new Pdf();
        Word w = word != null ? word : new Word();
        if (p.fontSize < 9 || p.fontSize > 12) {
            throw new CommandLine.ParameterException(commandLine,
                    "--font-size must be between 9 and 12, not " + p.fontSize);
        }
        if (p.rasterScale < 0.5f || p.rasterScale > 4f) {
            throw new CommandLine.ParameterException(commandLine,
                    "--raster-scale must be between 0.5 and 4, not " + p.rasterScale);
        }
        if (h.favicon != null && !java.nio.file.Files.isRegularFile(h.favicon)) {
            throw new CommandLine.ParameterException(commandLine, "Favicon not found: " + h.favicon);
        }

        DocumentationOptions options = new DocumentationOptions()
                .format(switch (target) {
                    case PDF -> DocumentationOptions.Format.PDF;
                    case DOCX -> DocumentationOptions.Format.DOCX;
                    case HTML -> DocumentationOptions.Format.HTML;
                })
                .languages(c.languages == null ? null : new LinkedHashSet<>(c.languages))
                .fallbackLanguage(c.fallbackLanguage)
                .markdown(c.markdown.mode)
                .typeDefinitionsInSource(c.typeDefinitions)
                .deduplicateDataDictionary(c.dedupe)
                .parallel(c.parallel)
                .imageFormat(switch (d.imageFormat) {
                    case png -> XsdDocumentationService.ImageOutputMethod.PNG;
                    case jpg -> XsdDocumentationService.ImageOutputMethod.JPG;
                    case svg -> XsdDocumentationService.ImageOutputMethod.SVG;
                })
                .diagramDocumentation(d.documentation)
                .diagramConstraints(d.constraints)
                .rootConstraints(d.rootConstraints)
                .constraintTargets(d.constraintTargets)
                .svgOverview(h.svgOverview)
                .metadata(h.metadata)
                .favicon(h.favicon == null ? null : h.favicon.toAbsolutePath().toFile())
                .pageSize(doc.pageSize.size)
                .landscape(doc.landscape)
                .coverPage(doc.coverPage)
                .toc(doc.toc)
                .dataDictionary(doc.dataDictionary)
                .schemaDiagram(doc.schemaDiagram)
                .elementDiagrams(doc.elementDiagrams)
                .elementReference(doc.elementReference.value)
                .sourceCode(doc.sourceCode);

        PdfDocumentationConfig config = options.pdf();
        config.setColorScheme(p.colorScheme.value);
        config.setWatermark(p.watermark.value);
        config.setIncludePageNumbers(p.pageNumbers);
        config.setPageNumberPosition(p.pageNumberPosition.value);
        config.setGenerateBookmarks(p.bookmarks);
        config.setMargins(p.margins.value);
        config.setFontFamily(p.font.value);
        config.setFontSize(PdfDocumentationConfig.FontSize.valueOf("PT_" + p.fontSize));
        config.setHeadingStyle(p.headingStyle.value);
        config.setLineSpacing(p.lineSpacing.value);
        config.setHeaderStyle(p.headerStyle.value);
        config.setFooterStyle(p.footerStyle.value);
        config.setTableStyle(p.tableStyle.value);
        config.setIncludeSchemaOverview(p.schemaOverview);
        config.setIncludeComplexTypes(p.complexTypes);
        config.setIncludeSimpleTypes(p.simpleTypes);
        config.setRasterScale(p.rasterScale);
        config.setTitle(p.title);
        config.setAuthor(p.author);
        config.setSubject(p.subject);
        config.setKeywords(p.keywords);
        options.word().setHeaderStyle(w.headerStyle.value);
        return options;
    }

    /** Options given for another format are ignored; say so instead of failing a shared script. */
    private void warnAboutIgnoredOptions(DocFormat target) {
        List<String> ignored = new ArrayList<>();
        if (target != DocFormat.HTML) {
            matched(HTML_ONLY, ignored);
        }
        if (target == DocFormat.HTML) {
            matched(DOCUMENT_ONLY, ignored);
        }
        if (target != DocFormat.PDF) {
            matched(PDF_ONLY, ignored);
        }
        if (target != DocFormat.DOCX) {
            matched(WORD_ONLY, ignored);
        }
        if (target == DocFormat.DOCX && document != null && document.pageSize == PageSizeArg.a3) {
            spec.commandLine().getErr().println("fxt-cli: warning: Word has no A3 pages; using A4");
        }
        for (String option : ignored) {
            spec.commandLine().getErr().println("fxt-cli: warning: " + option + " does not apply to "
                    + target.name().toLowerCase(java.util.Locale.ROOT) + " output; ignored");
        }
    }

    private void matched(String[] names, List<String> into) {
        var parseResult = spec.commandLine().getParseResult();
        for (String name : names) {
            // Options that are on by default are declared as --no-NAME (picocli's negatable rule)
            var option = spec.findOption(name);
            if (option == null) {
                option = spec.findOption("--no-" + name.substring(2));
            }
            if (option != null && parseResult.hasMatchedOption(option)) {
                into.add(name);
            }
        }
    }

    private void printProgress(TaskProgressListener.ProgressUpdate update) {
        if (update.status() == TaskProgressListener.ProgressUpdate.Status.RUNNING) {
            spec.commandLine().getErr().println("  " + update.taskName());
        } else if (update.status() == TaskProgressListener.ProgressUpdate.Status.FINISHED) {
            spec.commandLine().getErr().println("  " + update.taskName() + " done (" + update.durationMillis() + " ms)");
        } else if (update.status() == TaskProgressListener.ProgressUpdate.Status.FAILED) {
            spec.commandLine().getErr().println("  " + update.taskName() + " failed");
        }
    }

    static DocFormat formatOf(Path output) {
        return switch (CliSupport.extension(output)) {
            case "pdf" -> DocFormat.PDF;
            case "docx" -> DocFormat.DOCX;
            default -> DocFormat.HTML;
        };
    }
}
