package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.TransformRunner;
import org.fxt.freexmltoolkit.controls.shell.editor.XProcRunner;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli transform}: runs an XSLT stylesheet, an XQuery or an XProc pipeline with the
 * application's engines (Saxon-HE, XML Calabash), including its restrictions: no Java extension
 * calls, and {@code doc()} / {@code unparsed-text()} refuse remote URLs.
 */
@Command(name = "transform",
        mixinStandardHelpOptions = true,
        header = "Run an XSLT stylesheet, an XQuery or an XProc pipeline.",
        footer = {
                "  # XSLT with a parameter, result to a file",
                "  fxt-cli transform order.xml -s invoice.xsl -p currency=EUR -o invoice.html",
                "",
                "  # Many inputs into a folder",
                "  fxt-cli transform orders/*.xml -s normalize.xsl -d out/",
                "",
                "  # XQuery to standard output; profile an XSLT run",
                "  fxt-cli transform catalog.xml -s report.xq",
                "  fxt-cli transform big.xml -s slow.xsl -o out.xml --profile profile.json"
        },
        description = {
                "Run an XSLT stylesheet, an XQuery or an XProc pipeline.",
                "The language follows the extension of --stylesheet (.xsl/.xslt, .xq/.xquery/.xqy/.xql, .xpl) "
                        + "unless --language is given."
        },
        sortOptions = false)
final class TransformCommand implements Callable<Integer> {

    /** The languages {@code transform} runs. */
    enum Language { XSLT, XQUERY, XPROC }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Option(names = {"-s", "--stylesheet"}, required = true, paramLabel = "FILE",
            description = "XSLT stylesheet, XQuery or XProc pipeline.")
    Path stylesheet;

    @Parameters(arity = "0..*", paramLabel = "INPUT",
            description = "Input XML documents. XQuery and XProc may run without one.")
    List<Path> inputs = new ArrayList<>();

    @Option(names = {"-o", "--output"}, paramLabel = "FILE",
            description = "Output file (one input). Without -o and --output-dir the result goes to standard output.")
    Path output;

    @Option(names = {"-d", "--output-dir"}, paramLabel = "DIR",
            description = "Output folder for several inputs; each result is named after its input.")
    Path outputDir;

    @Option(names = {"-p", "--param"}, paramLabel = "NAME=VALUE",
            description = "Stylesheet parameter or external XQuery variable (string value); repeatable.")
    Map<String, String> parameters = new LinkedHashMap<>();

    @Option(names = {"-m", "--method"}, paramLabel = "METHOD",
            description = "Output method: xml, html, xhtml, text or json. Default: the stylesheet's xsl:output, else xml.")
    OutputFormat method;

    @Option(names = {"-l", "--language"}, paramLabel = "LANG",
            description = "Language: xslt, xquery or xproc (default: from the file extension).")
    Language language;

    @Option(names = "--profile", paramLabel = "REPORT",
            description = "XSLT: profile the run and write the report to REPORT (.json: JSON, otherwise text; "
                    + "'-' for standard error). One input.")
    String profileReport;

    @Option(names = "--warmup", paramLabel = "N", defaultValue = "1",
            description = "With --profile: runs before the measured one, so the JIT has warmed up (default: ${DEFAULT-VALUE}).")
    int warmupRuns;

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        CliSupport.requireFile(commandLine, stylesheet, "Stylesheet");
        for (Path input : inputs) {
            CliSupport.requireFile(commandLine, input, "Input");
        }
        if (output != null && outputDir != null) {
            throw new CommandLine.ParameterException(commandLine, "Use either --output or --output-dir");
        }
        if (inputs.size() > 1 && outputDir == null) {
            throw new CommandLine.ParameterException(commandLine,
                    "Several inputs need --output-dir (one result file per input)");
        }
        Language lang = language != null ? language : languageOf(stylesheet);
        if (lang == null) {
            throw new CommandLine.ParameterException(commandLine,
                    "Unknown language of " + stylesheet.getFileName() + "; give --language xslt|xquery|xproc");
        }
        if (lang == Language.XSLT && inputs.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine, "An XSLT transformation needs an input document");
        }
        if (profileReport != null && (lang != Language.XSLT || inputs.size() != 1)) {
            throw new CommandLine.ParameterException(commandLine, "--profile works for XSLT with one input");
        }
        PrintWriter err = commandLine.getErr(); // NOPMD - picocli's stream, owned by CommandLine
        if (lang == Language.XPROC && (!parameters.isEmpty() || method != null)) {
            err.println("fxt-cli: --param and --method are ignored for XProc pipelines");
        }
        CliSupport.initServices();

        String code = CliSupport.read(stylesheet);
        List<Path> runs = new ArrayList<>(inputs);
        if (runs.isEmpty()) {
            runs.add(null);
        }
        int exitCode = ExitCodes.OK;
        for (Path input : runs) {
            Outcome outcome = run(lang, code, input);
            for (String message : outcome.messages()) {
                err.println(message);
            }
            if (outcome.error() != null) {
                err.println("fxt-cli: " + (input != null ? input + ": " : "") + outcome.error());
                exitCode = ExitCodes.FAILED;
                continue;
            }
            Path target = targetFor(input, outcome.format());
            if (target == null) {
                PrintWriter out = commandLine.getOut(); // NOPMD - picocli's stream, owned by CommandLine
                out.print(outcome.text());
                out.flush();
            } else {
                CliSupport.write(target, outcome.text());
                if (parent == null || !parent.quiet) {
                    err.println("Wrote " + target);
                }
            }
        }
        return exitCode;
    }

    private void writeProfile(org.fxt.freexmltoolkit.service.XsltProfile profile) throws java.io.IOException {
        boolean json = profileReport.toLowerCase(java.util.Locale.ROOT).endsWith(".json");
        String report = json ? profile.toJson() : profile.toText(30);
        if ("-".equals(profileReport)) {
            spec.commandLine().getErr().print(report);
            spec.commandLine().getErr().flush();
        } else {
            CliSupport.write(Path.of(profileReport), report);
            if (parent == null || !parent.quiet) {
                spec.commandLine().getErr().println("Wrote profile " + profileReport);
            }
        }
    }

    /** The result of one run: output text and format, or an error; plus xsl:message output. */
    private record Outcome(String text, OutputFormat format, String error, List<String> messages) {
    }

    private Outcome run(Language lang, String code, Path input) throws Exception {
        String xml = input != null ? CliSupport.read(input) : null;
        String inputUri = input != null ? input.toAbsolutePath().toUri().toString() : null;
        String codeUri = stylesheet.toAbsolutePath().toUri().toString();
        Map<String, Object> params = new LinkedHashMap<>(parameters);
        switch (lang) {
            case XSLT -> {
                OutputFormat format = method != null ? method : TransformRunner.detectXsltOutputFormat(code);
                if (profileReport != null) {
                    var profiled = TransformRunner.profile(xml, inputUri, code, codeUri, params, format, warmupRuns);
                    if (profiled.profile() != null) {
                        writeProfile(profiled.profile());
                    }
                    return outcome(profiled.result(), format);
                }
                XsltTransformationResult result = TransformRunner.xsltTransformResult(
                        xml, inputUri, code, codeUri, params, format);
                return outcome(result, format);
            }
            case XQUERY -> {
                OutputFormat format = method != null ? method : OutputFormat.XML;
                XsltTransformationResult result;
                try {
                    result = XsltTransformationEngine.getInstance()
                            .transformXQuery(xml, inputUri, code, codeUri, params, format);
                } catch (Exception e) {
                    result = XsltTransformationResult.error(e.getMessage(), e);
                }
                return outcome(result, format);
            }
            case XPROC -> {
                XProcRunner.Result result = XProcRunner.runPipeline(code, stylesheet.toAbsolutePath().toFile(),
                        null, input != null ? input.toAbsolutePath().toFile() : null);
                return result.isError()
                        ? new Outcome(null, null, result.text().substring("ERROR:".length()).trim(), List.of())
                        : new Outcome(result.text(), result.format(), null, List.of());
            }
            default -> throw new IllegalStateException(lang.name());
        }
    }

    private static Outcome outcome(XsltTransformationResult result, OutputFormat format) {
        List<String> messages = new ArrayList<>();
        if (result.getMessages() != null) {
            for (XsltTransformationResult.TransformationMessage m : result.getMessages()) {
                StringBuilder line = new StringBuilder("[").append(m.getLevel()).append("] ");
                if (m.getLocation() != null && !m.getLocation().isBlank()) {
                    line.append(m.getLocation()).append(": ");
                }
                messages.add(line.append(m.getMessage()).toString());
            }
        }
        if (!result.isSuccess()) {
            String error = result.getErrorMessage() != null ? result.getErrorMessage() : "The transformation failed";
            return new Outcome(null, format, error, messages);
        }
        String text = result.getOutputContent() != null ? result.getOutputContent() : "";
        return new Outcome(text, format, null, messages);
    }

    private Path targetFor(Path input, OutputFormat format) {
        if (output != null) {
            return output;
        }
        if (outputDir == null) {
            return null;
        }
        String extension = format != null ? format.getFileExtension() : "xml";
        String base = input != null ? CliSupport.baseName(input) : CliSupport.baseName(stylesheet);
        Path target = outputDir.resolve(base + "." + extension);
        if (input != null && Files.exists(target) && target.toAbsolutePath().normalize()
                .equals(input.toAbsolutePath().normalize())) {
            // never overwrite the input with its own result
            target = outputDir.resolve(base + ".out." + extension);
        }
        return target;
    }

    static Language languageOf(Path file) {
        return switch (CliSupport.extension(file)) {
            case "xsl", "xslt" -> Language.XSLT;
            case "xq", "xquery", "xqy", "xql", "xqm" -> Language.XQUERY;
            case "xpl" -> Language.XPROC;
            default -> null;
        };
    }
}
