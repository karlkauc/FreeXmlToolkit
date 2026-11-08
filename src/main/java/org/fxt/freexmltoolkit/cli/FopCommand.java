package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.FopRunner;
import org.fxt.freexmltoolkit.service.FopEvent;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli fop}: renders a PDF with Apache FOP, either from XML plus an XSL-FO stylesheet
 * or from a finished XSL-FO document.
 */
@Command(name = "fop",
        description = "Render a PDF with Apache FOP from XML + XSL-FO stylesheet, or from an XSL-FO document.",
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

    @Option(names = {"-o", "--output"}, required = true, paramLabel = "PDF", description = "The PDF to write.")
    Path output;

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
        FopRunner.PdfOptions options = new FopRunner.PdfOptions(title, author, subject, pdfA, pageSize, orientation);
        FopRunner.FopResult result = fromFo
                ? FopRunner.renderFo(CliSupport.read(fo), output.toFile(), options)
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
}
