package org.fxt.freexmltoolkit.cli;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.cli.ReportWriters.Format;
import org.fxt.freexmltoolkit.cli.ValidationReport.FileResult;
import org.fxt.freexmltoolkit.cli.ValidationReport.Finding;
import org.fxt.freexmltoolkit.cli.ValidationReport.Severity;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli validate}: checks XML files against an XSD and/or Schematron, and JSON files
 * against a JSON Schema, with the same validators as the application's Validation activity.
 * Without {@code --xsd} each XML file is checked against the schema it declares
 * ({@code xsi:schemaLocation}, Schema Library, catalogs), else for well-formedness.
 */
@Command(name = "validate",
        description = {
                "Validate XML against XML Schema and/or Schematron, JSON against JSON Schema.",
                "Without --xsd, every XML file is checked against the schema it declares "
                        + "(xsi:schemaLocation, Schema Library, XML catalogs), else for well-formedness."
        },
        sortOptions = false)
final class ValidateCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(arity = "1..*", paramLabel = "FILE",
            description = "Files or folders. Folders are searched recursively (see --include).")
    List<Path> inputs;

    @Option(names = {"-x", "--xsd"}, paramLabel = "XSD",
            description = "XML Schema (1.0 or 1.1) for all XML files.")
    Path xsd;

    @Option(names = {"-s", "--schematron"}, paramLabel = "SCH",
            description = "Schematron (.sch) checked in addition to the schema.")
    Path schematron;

    @Option(names = {"-j", "--json-schema"}, paramLabel = "SCHEMA",
            description = "JSON Schema for all JSON files.")
    Path jsonSchema;

    @Option(names = "--no-schema-lookup",
            description = "Do not use the schema an XML file declares; without --xsd only well-formedness is checked.")
    boolean noSchemaLookup;

    @Option(names = "--include", split = ",", paramLabel = "GLOB", defaultValue = "*.xml,*.json",
            description = "File names to validate inside folders (default: ${DEFAULT-VALUE}).")
    List<String> include;

    @Option(names = {"-f", "--report-format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "Report format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    Format format;

    @Option(names = {"-r", "--report"}, paramLabel = "FILE",
            description = "Write the report to this file; the text summary still goes to standard output.")
    Path reportFile;

    @Option(names = "--fail-on-warning",
            description = "Warnings make a file invalid too (exit code 1).")
    boolean failOnWarning;

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        CliSupport.requireFile(commandLine, xsd, "XML Schema");
        CliSupport.requireFile(commandLine, schematron, "Schematron");
        CliSupport.requireFile(commandLine, jsonSchema, "JSON Schema");
        List<Path> files = CliSupport.expand(commandLine, inputs, include);
        if (files.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "No files to validate (folders are searched for " + String.join(", ", include) + ")");
        }
        CliSupport.initServices();

        long start = System.nanoTime();
        List<FileResult> results = new ArrayList<>();
        for (Path file : files) {
            results.add(validate(file));
        }
        ValidationReport report = new ValidationReport(results, (System.nanoTime() - start) / 1_000_000);

        ReportWriters writers = new ReportWriters(Path.of("").toAbsolutePath(), failOnWarning);
        boolean quiet = parent != null && parent.quiet;
        var out = commandLine.getOut();
        if (reportFile != null) {
            CliSupport.write(reportFile, writers.write(report, format));
            if (!quiet) {
                out.print(writers.text(report));
            }
        } else if (format == Format.TEXT) {
            out.print(quiet ? writers.findings(report) : writers.text(report));
        } else {
            out.print(writers.write(report, format));
        }
        out.flush();
        return report.exitCode(failOnWarning);
    }

    /** XML files from this size on are validated as a stream (without Schematron): memory stays flat. */
    static final long STREAMING_BYTES = 64L * 1024 * 1024;
    /** The threshold in use (tests lower it). */
    static long streamingBytes = STREAMING_BYTES;

    private FileResult validate(Path file) {
        long start = System.nanoTime();
        if (!isJson(file) && schematron == null && sizeOf(file) >= streamingBytes) {
            return validateStreaming(file, start);
        }
        String content;
        try {
            content = CliSupport.read(file);
        } catch (Exception e) {
            String reason = "Cannot read the file: " + FxtCli.messageOf(e);
            return new FileResult(file, null, List.of(new Finding("File", Severity.ERROR, 0, 0, reason, null, null)),
                    reason, millisSince(start));
        }
        if (isJson(file)) {
            List<ValidationProblem> problems = ValidationRunner.validateJson(content,
                    jsonSchema != null ? jsonSchema.toFile() : null);
            return new FileResult(file, jsonSchema, findings(problems), null, millisSince(start));
        }
        Path schema = xsd;
        if (schema == null && !noSchemaLookup) {
            File baseDir = file.toAbsolutePath().getParent() != null ? file.toAbsolutePath().getParent().toFile() : null;
            schema = ValidationRunner.declaredSchema(content, baseDir).map(File::toPath).orElse(null);
        }
        ValidationRunner.RunResult run = ValidationRunner.runWithReport(content,
                schema != null ? schema.toFile() : null,
                schematron != null ? schematron.toFile() : null,
                file.getFileName().toString());
        List<Finding> findings = findings(run.problems());
        String notRun = run.failed() ? FxtCli.messageOf(run.error()) : schemaNotLoaded(findings);
        return new FileResult(file, schema, findings, notRun, millisSince(start));
    }

    private static long sizeOf(Path file) {
        try {
            return java.nio.file.Files.size(file);
        } catch (java.io.IOException e) {
            return -1;
        }
    }

    /** A large XML file: XSD (or well-formedness) validation reading the file as a stream. */
    private FileResult validateStreaming(Path file, long start) {
        Path schema = xsd;
        if (schema == null && !noSchemaLookup) {
            schema = org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.declaredSchema(file)
                    .map(File::toPath).orElse(null);
        }
        try {
            var result = org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.validate(file,
                    schema != null ? schema.toFile() : null, 10_000, null, null);
            if (result.schemaError() != null) {
                return new FileResult(file, schema, List.of(new Finding("XSD", Severity.ERROR, 0, 0,
                        result.schemaError(), null, null)), result.schemaError(), millisSince(start));
            }
            List<Finding> findings = new ArrayList<>();
            for (var problem : result.problems()) {
                findings.add(new Finding(problem.severity().equals("Fatal") ? "Well-formed" : schema != null ? "XSD" : "Well-formed",
                        problem.severity().equals("Warning") ? Severity.WARNING : Severity.ERROR,
                        (int) Math.min(Integer.MAX_VALUE, problem.line()), problem.column(), problem.message(), null, null));
            }
            if (result.truncated()) {
                findings.add(new Finding("XSD", Severity.INFO, 0, 0, "Stopped after 10,000 problems.", null, null));
            }
            return new FileResult(file, schema, findings, null, millisSince(start));
        } catch (java.io.IOException | RuntimeException e) {
            String reason = "Cannot read the file: " + FxtCli.messageOf(e);
            return new FileResult(file, schema, List.of(new Finding("File", Severity.ERROR, 0, 0, reason, null, null)),
                    reason, millisSince(start));
        }
    }

    /**
     * The validators report a schema that does not load as a problem without a line (and fall back
     * to a well-formedness check) instead of failing; these are the texts they use.
     */
    private static final List<String> SCHEMA_NOT_LOADED = List.of(
            "Schema is invalid or unreadable",      // SaxonXmlValidationService
            "Schema validation error:",             // XercesXmlValidationService, XSD 1.0
            "Could not read schema file");          // both

    /** @return the message of a "schema does not load" finding, else {@code null} */
    static String schemaNotLoaded(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.line() <= 0 && f.message() != null
                    && SCHEMA_NOT_LOADED.stream().anyMatch(f.message()::startsWith)) {
                return f.message();
            }
        }
        return null;
    }

    private static boolean isJson(Path file) {
        String extension = CliSupport.extension(file);
        return extension.equals("json") || extension.equals("json5");
    }

    /** Converts the problems; a fatal parse error the parser reports twice is listed once. */
    private static List<Finding> findings(List<ValidationProblem> problems) {
        java.util.Set<Finding> findings = new java.util.LinkedHashSet<>();
        for (ValidationProblem p : problems) {
            findings.add(new Finding(p.source(), Severity.of(p.severity()), Math.max(0, p.line()),
                    Math.max(0, p.column()), p.message(), p.ruleId(), p.context()));
        }
        return new ArrayList<>(findings);
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
