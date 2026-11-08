package org.fxt.freexmltoolkit.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.DocumentationRunner;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli xsd-doc}: generates the documentation of an XML Schema as an HTML site, a PDF or
 * a Word document, with the application's documentation generator.
 */
@Command(name = "xsd-doc",
        description = "Generate the documentation of an XML Schema as HTML site, PDF or Word document.",
        sortOptions = false)
final class XsdDocCommand implements Callable<Integer> {

    /** Documentation formats. */
    enum DocFormat { HTML, PDF, DOCX }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(index = "0", paramLabel = "XSD", description = "The XML Schema.")
    Path xsd;

    @Option(names = {"-o", "--output"}, required = true, paramLabel = "PATH",
            description = "Output folder (HTML) or file (PDF, Word).")
    Path output;

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT",
            description = "${COMPLETION-CANDIDATES}; default: from the extension of --output (.pdf, .docx), else html.")
    DocFormat format;

    @Override
    public Integer call() {
        CommandLine commandLine = spec.commandLine();
        CliSupport.requireFile(commandLine, xsd, "XML Schema");
        DocFormat target = format != null ? format : formatOf(output);
        CliSupport.initServices();
        java.io.File schema = xsd.toAbsolutePath().toFile();
        java.io.File out = output.toAbsolutePath().toFile();
        String result = switch (target) {
            case HTML -> DocumentationRunner.exportHtml(schema, out);
            case PDF -> DocumentationRunner.exportPdf(schema, out);
            case DOCX -> DocumentationRunner.exportWord(schema, out);
        };
        if (result.startsWith("ERROR:")) {
            commandLine.getErr().println("fxt-cli: " + result.substring("ERROR:".length()).trim());
            return ExitCodes.FAILED;
        }
        if (parent == null || !parent.quiet) {
            commandLine.getErr().println("Wrote " + result.substring("OK:".length()).trim());
        }
        return ExitCodes.OK;
    }

    static DocFormat formatOf(Path output) {
        return switch (CliSupport.extension(output)) {
            case "pdf" -> DocFormat.PDF;
            case "docx" -> DocFormat.DOCX;
            default -> DocFormat.HTML;
        };
    }
}
