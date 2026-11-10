package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.FopRunner;
import org.fxt.freexmltoolkit.service.FopEvent;
import org.fxt.freexmltoolkit.service.fo.FoProcessorConfig;
import org.fxt.freexmltoolkit.service.fo.FoProcessorStore;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli fop}: renders a PDF with Apache FOP or an external XSL-FO processor configured in
 * the settings, either from XML plus an XSL-FO stylesheet or from a finished XSL-FO document.
 */
@Command(name = "fop",
        mixinStandardHelpOptions = true,
        header = "Render a PDF with Apache FOP or an external FO processor from XML + XSL-FO stylesheet or an XSL-FO file.",
        footer = {
                "  # XML + stylesheet producing XSL-FO",
                "  fxt-cli fop -i order.xml -s order-fo.xsl -o order.pdf",
                "",
                "  # A finished XSL-FO document as PDF/A",
                "  fxt-cli fop --fo order.fo -o order.pdf --pdfa",
                "",
                "  # With an external processor configured in Settings > XSL-FO Processors",
                "  fxt-cli fop --list-processors",
                "  fxt-cli fop -i order.xml -s order-fo.xsl -o order.pdf --processor \"RenderX XEP\""
        },
        description = "Render a PDF from XML + XSL-FO stylesheet, or from an XSL-FO document, with the bundled "
                + "Apache FOP or an external XSL-FO processor configured in the settings.",
        sortOptions = false)
final class FopCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Option(names = {"-i", "--input"}, paramLabel = "XML", description = "The XML document.")
    Path input;

    @Option(names = {"-s", "--stylesheet"}, paramLabel = "XSL", description = "Stylesheet producing XSL-FO.")
    Path stylesheet;

    @Option(names = "--fo", paramLabel = "FO", description = "A finished XSL-FO document (instead of -i and -s).")
    Path fo;

    @Option(names = {"-o", "--output"}, paramLabel = "PDF", description = "The PDF to write.")
    Path output;

    @Option(names = {"-p", "--processor"}, paramLabel = "NAME",
            description = "The XSL-FO processor: \"builtin\" (the bundled Apache FOP) or the name or id of an external "
                    + "processor configured in the settings. Default: the default processor of the settings.")
    String processor;

    @Option(names = "--list-processors", description = "List the configured XSL-FO processors and exit.")
    boolean listProcessors;

    @Option(names = "--title", description = "PDF title.")
    String title = "";

    @Option(names = "--author", description = "PDF author.")
    String author = "";

    @Option(names = "--subject", description = "PDF subject.")
    String subject = "";

    @Option(names = "--pdfa", description = "Produce PDF/A-1b.")
    boolean pdfA;

    @Option(names = "--page-size", paramLabel = "SIZE",
            description = "Passed to the stylesheet as parameter page-size (e.g. A4, Letter).")
    String pageSize = "";

    @Option(names = "--orientation", paramLabel = "ORIENTATION",
            description = "Passed to the stylesheet as parameter page-orientation (portrait, landscape).")
    String orientation = "";

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        if (listProcessors) {
            CliSupport.initServices();
            return listProcessors(commandLine.getOut());
        }
        if (output == null) {
            throw new CommandLine.ParameterException(commandLine, "Missing required option: '--output=PDF'");
        }
        boolean fromFo = fo != null;
        if (fromFo == (input != null || stylesheet != null)) {
            throw new CommandLine.ParameterException(commandLine, "Give either --fo, or --input and --stylesheet");
        }
        if (!fromFo && (input == null || stylesheet == null)) {
            throw new CommandLine.ParameterException(commandLine, "--input and --stylesheet belong together");
        }
        CliSupport.requireFile(commandLine, fo, "XSL-FO document");
        CliSupport.requireFile(commandLine, input, "Input");
        CliSupport.requireFile(commandLine, stylesheet, "Stylesheet");
        CliSupport.initServices();

        Path parentDir = output.toAbsolutePath().getParent();
        if (parentDir != null) {
            java.nio.file.Files.createDirectories(parentDir);
        }
        String chosen = processor != null ? processor : FoProcessorStore.settings().defaultId();
        FopRunner.PdfOptions options = new FopRunner.PdfOptions(title, author, subject, pdfA, pageSize, orientation,
                chosen);
        FopRunner.FopResult result = fromFo
                ? FopRunner.renderFo(CliSupport.read(fo), output.toFile(), options,
                        new FopRunner.RenderContext(null, null, fo.toAbsolutePath()))
                : FopRunner.generateResult(input.toFile(), stylesheet.toFile(), output.toFile(), options);

        PrintWriter err = commandLine.getErr(); // NOPMD - picocli's stream, owned by CommandLine
        boolean quiet = parent != null && parent.quiet;
        if (!quiet) {
            for (var message : result.messages()) {
                err.println("[" + message.getLevel() + "] " + message.getMessage());
            }
            // FOP repeats an event (e.g. a font substitution) for every occurrence; show each once
            java.util.Set<String> shown = new java.util.LinkedHashSet<>();
            for (FopEvent event : result.events()) {
                if (event.isWarningOrWorse()) {
                    shown.add("[" + event.severity() + "] " + event.message());
                }
            }
            shown.forEach(err::println);
        }
        if (!result.ok()) {
            err.println("fxt-cli: " + result.error());
            return ExitCodes.FAILED;
        }
        if (!quiet) {
            err.println("Wrote " + output);
        }
        return ExitCodes.OK;
    }

    private int listProcessors(PrintWriter out) {
        FoProcessorStore store = FoProcessorStore.settings();
        String defaultId = store.defaultId();
        out.println((FoProcessorStore.BUILTIN_ID.equals(defaultId) ? "* " : "  ") + FoProcessorStore.BUILTIN_ID
                + "\tApache FOP (bundled)");
        for (FoProcessorConfig p : store.list()) {
            String program = p.resolveExecutable().map(Path::toString).orElse("not found");
            out.println((p.id().equals(defaultId) ? "* " : "  ") + p.name() + "\t" + program + "\t" + p.id());
        }
        if (!store.externalAllowed()) {
            out.println("  (external processors are disabled by the administrator)");
        }
        out.flush();
        return ExitCodes.OK;
    }
}
