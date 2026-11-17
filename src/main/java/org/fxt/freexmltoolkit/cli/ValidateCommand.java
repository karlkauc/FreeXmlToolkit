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
        mixinStandardHelpOptions = true,
        header = "Validate XML, JSON and project files against schemas and Schematron.",
        footer = {
                "  # Every XML file below src/ against the schema it declares",
                "  fxt-cli validate src/",
                "",
                "  # Against a given schema and Schematron, JUnit report for CI",
                "  fxt-cli validate orders/ --xsd order.xsd --schematron rules.sch \\",
                "      -f junit -r report.xml",
                "",
                "  # JSON files against a JSON Schema",
                "  fxt-cli validate data/ --json-schema order.schema.json --include \"*.json\"",
                "",
                "  # Files of a project, with its schema bindings",
                "  fxt-cli validate data/ --project shop.fxtproj",
                "",
                "  # Strictly as XSD 1.0 and XML 1.0 (for processors without XSD 1.1)",
                "  fxt-cli validate orders/ --xsd order.xsd --xsd-version 1.0 --xml-version 1.0",
                "",
                "  # Regulatory reports, with every LEI checked at GLEIF",
                "  fxt-cli validate reports/ --gleif --isin"
        },
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

    @Option(names = {"-x", "--xsd", "--schema"}, paramLabel = "SCHEMA",
            description = "Schema for all XML files: XML Schema (.xsd, 1.0 or 1.1), DTD (.dtd) or RELAX NG (.rng, .rnc).")
    Path xsd;

    @Option(names = {"-s", "--schematron"}, paramLabel = "SCH",
            description = "Schematron (.sch) checked in addition to the schema.")
    Path schematron;

    @Option(names = {"-j", "--json-schema"}, paramLabel = "SCHEMA",
            description = "JSON Schema for all JSON and YAML files.")
    Path jsonSchema;

    @Option(names = "--schemastore",
            description = "Find the JSON Schema of a JSON or YAML file that declares none by its name in the "
                    + "SchemaStore catalog (downloaded into the cache, refreshed after 7 days).")
    boolean schemaStore;

    @Option(names = "--schemastore-url", paramLabel = "URL",
            description = "Address of the SchemaStore catalog (a mirror; default: the public catalog).")
    String schemaStoreUrl;

    /** The catalog in use with {@link #schemaStore}, else {@code null}. */
    private org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog catalog;

    @Option(names = "--no-schema-lookup",
            description = "Do not use the schema an XML, JSON or YAML file declares or its file name chooses; "
                    + "without --xsd / --json-schema only well-formedness is checked.")
    boolean noSchemaLookup;

    @Option(names = "--include", split = ",", paramLabel = "GLOB", defaultValue = "*.xml,*.json,*.yaml,*.yml",
            description = "File names to validate inside folders (default: ${DEFAULT-VALUE}).")
    List<String> include;

    @Option(names = {"-f", "--report-format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "Report format: text, json, junit (JUnit XML) or sarif (default: ${DEFAULT-VALUE}).")
    Format format;

    @Option(names = {"-r", "--report"}, paramLabel = "FILE",
            description = "Write the report to this file; the text summary still goes to standard output.")
    Path reportFile;

    @Option(names = {"-p", "--project"}, paramLabel = "FXTPROJ",
            description = "Project file (.fxtproj): its bindings choose the schema and Schematron of each file "
                    + "(before the declared schema; --xsd, --schematron and --json-schema win), its catalogs "
                    + "resolve schema references.")
    Path projectFile;

    /** The loaded {@link #projectFile}, or {@code null}. */
    private org.fxt.freexmltoolkit.service.project.FxtProject project;

    @Option(names = "--xsd-version", paramLabel = "VERSION",
            description = "XSD version: auto (default: XSD 1.1 when the schema uses it), 1.0 or 1.1.")
    String xsdVersion;

    @Option(names = "--xml-version", paramLabel = "VERSION",
            description = "XML version: auto (default: as declared), 1.0 (a document declared as 1.1 is an error) or 1.1.")
    String xmlVersion;

    @Option(names = "--schematron-binding", paramLabel = "BINDING",
            description = "Query binding of a Schematron without @queryBinding: xslt, xslt2 (default) or xslt3.")
    String schematronBinding;

    /** The standards chosen on the command line (components not given are {@code null}). */
    private org.fxt.freexmltoolkit.service.standards.StandardsProfile cliStandards =
            org.fxt.freexmltoolkit.service.standards.StandardsProfile.EMPTY;

    @Option(names = "--gleif",
            description = "Also check every LEI of the files at GLEIF (api.gleif.org): unknown, lapsed, retired or "
                    + "annulled LEIs and inactive entities are findings. Sends the LEIs to GLEIF; answers are cached.")
    boolean gleif;

    @Option(names = "--isin",
            description = "Also check every ISIN of the files at ESMA FIRDS (OpenFIGI for ISINs FIRDS does not know): "
                    + "unknown ISINs and instruments no longer traded in the EU are findings. Sends the ISINs to these "
                    + "services; answers are cached.")
    boolean isin;

    @Option(names = "--no-openfigi",
            description = "With --isin: ask only ESMA FIRDS, never OpenFIGI.")
    boolean noOpenFigi;

    @Option(names = "--fail-on-warning",
            description = "Warnings make a file invalid too (exit code 1).")
    boolean failOnWarning;

    @Override
    public Integer call() throws Exception {
        try {
            return run();
        } finally {
            org.fxt.freexmltoolkit.service.finance.gleif.Gleif.setValidationOverride(null);
            org.fxt.freexmltoolkit.service.finance.isin.Isin.setValidationOverride(null);
            org.fxt.freexmltoolkit.service.finance.isin.Isin.setOpenFigiOverride(null);
        }
    }

    private Integer run() throws Exception {
        CommandLine commandLine = spec.commandLine();
        CliSupport.requireFile(commandLine, xsd, "XML Schema");
        CliSupport.requireFile(commandLine, schematron, "Schematron");
        CliSupport.requireFile(commandLine, jsonSchema, "JSON Schema");
        if (xsdVersion != null) {
            cliStandards = cliStandards.withXsd(CliSupport.version(commandLine,
                    org.fxt.freexmltoolkit.service.standards.XsdVersionMode.class, xsdVersion, "XSD"));
        }
        if (xmlVersion != null) {
            cliStandards = cliStandards.withXml(CliSupport.version(commandLine,
                    org.fxt.freexmltoolkit.service.standards.XmlVersionMode.class, xmlVersion, "XML"));
        }
        if (schematronBinding != null) {
            cliStandards = cliStandards.withSchematron(CliSupport.version(commandLine,
                    org.fxt.freexmltoolkit.service.standards.SchematronBinding.class, schematronBinding,
                    "Schematron query binding"));
        }
        List<Path> files = CliSupport.expand(commandLine, inputs, include);
        if (files.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "No files to validate (folders are searched for " + String.join(", ", include) + ")");
        }
        CliSupport.initServices();
        // the command line ignores the GUI's "check LEIs on validation" setting; --gleif adds the stage below
        org.fxt.freexmltoolkit.service.finance.gleif.Gleif.setValidationOverride(false);
        org.fxt.freexmltoolkit.service.finance.isin.Isin.setValidationOverride(false);
        if (noOpenFigi) {
            org.fxt.freexmltoolkit.service.finance.isin.Isin.setOpenFigiOverride(false);
        }
        if (isin && !org.fxt.freexmltoolkit.service.finance.isin.Isin.allowed()) {
            throw new CommandLine.ParameterException(commandLine,
                    "--isin: ISIN lookups are turned off by the administrator ("
                            + org.fxt.freexmltoolkit.service.AdminPolicy.KEY_ISIN_ALLOWED + "=false)");
        }
        if (gleif && !org.fxt.freexmltoolkit.service.finance.gleif.Gleif.allowed()) {
            throw new CommandLine.ParameterException(commandLine,
                    "--gleif: GLEIF lookups are turned off by the administrator ("
                            + org.fxt.freexmltoolkit.service.AdminPolicy.KEY_GLEIF_ALLOWED + "=false)");
        }
        if (schemaStore && !noSchemaLookup) {
            catalog = openSchemaStore(commandLine);
        }
        if (projectFile != null) {
            CliSupport.requireFile(commandLine, projectFile, "Project");
            try {
                project = org.fxt.freexmltoolkit.service.project.FxtProject.load(projectFile);
            } catch (java.io.IOException e) {
                throw new CommandLine.ParameterException(commandLine, e.getMessage(), e, null, null);
            }
            try {
                org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared().setProjectCatalogs(project.catalogs());
            } catch (RuntimeException e) {
                commandLine.getErr().println("warning: the project's catalogs are not used: " + FxtCli.messageOf(e));
            }
        }

        long start = System.nanoTime();
        List<FileResult> results = new ArrayList<>();
        for (Path file : files) {
            FileResult result = validate(file);
            results.add(gleif || isin ? withOnlineChecks(result) : result);
        }
        ValidationReport report = new ValidationReport(results, (System.nanoTime() - start) / 1_000_000);

        ReportWriters writers = new ReportWriters(Path.of("").toAbsolutePath(), failOnWarning);
        boolean quiet = parent != null && parent.quiet;
        var out = commandLine.getOut(); // NOPMD - picocli's stream, owned by CommandLine
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
        Path schematron = this.schematron != null ? this.schematron
                : project == null ? null : project.schematronFor(file).orElse(null);
        if (!isJson(file) && !isYaml(file) && schematron == null && sizeOf(file) >= streamingBytes
                && (xsd == null || org.fxt.freexmltoolkit.service.grammar.GrammarKind.ofFile(xsd)
                        .orElse(org.fxt.freexmltoolkit.service.grammar.GrammarKind.XSD) == org.fxt.freexmltoolkit.service.grammar.GrammarKind.XSD)) {
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
        if (isYaml(file) || isJson(file)) {
            boolean yaml = isYaml(file);
            JsonSchemaChoice choice = jsonSchemaOf(file, content, yaml);
            Path schemaOfData = choice.schema();
            List<ValidationProblem> problems = yaml
                    ? ValidationRunner.validateYaml(content, schemaOfData != null ? schemaOfData.toFile() : null)
                    : ValidationRunner.validateJson(content, schemaOfData != null ? schemaOfData.toFile() : null);
            List<Finding> findings = new ArrayList<>(findings(problems));
            if (choice.notLoaded() != null) {
                findings.add(0, new Finding("JSON Schema", Severity.ERROR, 0, 0, choice.notLoaded(), null, null));
            }
            return new FileResult(file, schemaOfData, findings, choice.notLoaded(), millisSince(start));
        }
        Path schema = xsd != null ? xsd : projectSchema(file);
        if (schema == null && !noSchemaLookup) {
            File baseDir = file.toAbsolutePath().getParent() != null ? file.toAbsolutePath().getParent().toFile() : null;
            schema = ValidationRunner.declaredSchema(content, baseDir).map(File::toPath).orElse(null);
        }
        ValidationRunner.RunResult run = ValidationRunner.runWithReport(content,
                schema != null ? schema.toFile() : null,
                schematron != null ? schematron.toFile() : null,
                file.getFileName().toString(), file, !noSchemaLookup, standardsFor(file));
        List<Finding> findings = findings(run.problems());
        String notRun = run.failed() ? FxtCli.messageOf(run.error()) : schemaNotLoaded(findings);
        return new FileResult(file, schema, findings, notRun, millisSince(start));
    }

    /** Adds the online findings of the file's LEIs (GLEIF) and ISINs (FIRDS, OpenFIGI) - files below the streaming size. */
    private FileResult withOnlineChecks(FileResult result) {
        if (sizeOf(result.file()) >= streamingBytes) {
            return result;
        }
        String content;
        try {
            content = CliSupport.read(result.file());
        } catch (Exception e) {
            return result;
        }
        var check = org.fxt.freexmltoolkit.service.finance.IdentifierCheck.check(content, gleif, isin);
        if (check.leis() + check.isins() + check.examples() == 0) {
            return result;
        }
        List<Finding> findings = new ArrayList<>(result.findings());
        if (check.examples() > 0) {
            findings.add(new Finding("Online", Severity.INFO, 0, 0, check.examples()
                    + " synthetic example identifiers (marker XMPL) not looked up online", null, null));
        }
        for (var f : check.failures()) {
            findings.add(new Finding(f.source(), Severity.INFO, 0, 0, "Not checked online: " + f.message(), null, null));
        }
        for (var f : check.findings()) {
            findings.add(new Finding(f.source(), Severity.of(f.severity().name()), f.line(), f.column(), f.message(),
                    f.rule(), null));
        }
        return new FileResult(result.file(), result.schema(), findings, result.notRun(), result.millis());
    }

    /** The standards of one file: the project's binding for it, then the command-line options. */
    private org.fxt.freexmltoolkit.service.standards.ValidationStandards standardsFor(Path file) {
        return org.fxt.freexmltoolkit.service.standards.ValidationStandards.of(
                org.fxt.freexmltoolkit.service.standards.StandardsProfile.resolve(null,
                        project == null ? null : project.standardsFor(file), null, cliStandards));
    }

    /** @return the schema the project binds to {@code file}, or {@code null} */
    private Path projectSchema(Path file) {
        return project == null ? null : project.schemaFor(file).orElse(null);
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
        Path schema = xsd != null ? xsd : projectSchema(file);
        if (schema == null && !noSchemaLookup) {
            schema = org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.declaredSchema(file)
                    .map(File::toPath).orElse(null);
        }
        try {
            var result = org.fxt.freexmltoolkit.service.largefile.LargeFileValidation.validate(file,
                    schema != null ? schema.toFile() : null, 10_000, null, null, standardsFor(file));
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
    /** @return the message of a "schema does not load" finding, else {@code null} */
    static String schemaNotLoaded(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.line() <= 0 && ValidationRunner.isSchemaNotLoaded(f.message())) {
                return f.message();
            }
        }
        return null;
    }

    private static boolean isJson(Path file) {
        String extension = CliSupport.extension(file);
        return extension.equals("json") || extension.equals("json5") || extension.equals("jsonc");
    }

    private static boolean isYaml(Path file) {
        String extension = CliSupport.extension(file);
        return extension.equals("yaml") || extension.equals("yml");
    }

    /**
     * The JSON Schema of a JSON or YAML file and, when one was chosen but does not load, why.
     *
     * @param schema    the schema file, {@code null} for a well-formedness check only
     * @param notLoaded the reason a declared or file-name schema is missing, else {@code null}
     */
    record JsonSchemaChoice(Path schema, String notLoaded) {
    }

    /**
     * The JSON Schema of a JSON or YAML file: {@code --json-schema}, the project's binding, the
     * schema the file declares (YAML modeline or {@code $schema}), then its file name (Schema
     * Library patterns, the SchemaStore catalog with {@code --schemastore}).
     */
    private JsonSchemaChoice jsonSchemaOf(Path file, String content, boolean yaml) {
        Path chosen = jsonSchema != null ? jsonSchema : projectSchema(file);
        if (chosen != null || noSchemaLookup) {
            return new JsonSchemaChoice(chosen, null);
        }
        var json = new org.fxt.freexmltoolkit.service.JsonService();
        java.util.Optional<String> declared = yaml
                ? org.fxt.freexmltoolkit.service.yaml.YamlSchemaDeclaration.find(content)
                : json.getSchemaLocationFromJsonContent(content);
        if (declared.isPresent()) {
            Path parent = file.toAbsolutePath().getParent();
            File schema = json.resolveJsonSchemaLocation(declared.get(), parent != null ? parent.toFile() : null);
            return schema != null && schema.exists() ? new JsonSchemaChoice(schema.toPath(), null)
                    : new JsonSchemaChoice(null, "The declared JSON Schema cannot be loaded: " + declared.get());
        }
        var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
        var hit = org.fxt.freexmltoolkit.service.schemastore.JsonSchemaByFileName
                .find(file.toAbsolutePath(), library, catalog);
        if (hit.isEmpty()) {
            return new JsonSchemaChoice(null, null);
        }
        var entry = hit.get().entry();
        java.util.Optional<Path> schema = org.fxt.freexmltoolkit.service.SchemaLibraryLookup
                .materializeIfAllowed(library, entry, library.isRemoteDownloadAllowed());
        if (schema.isEmpty() && entry.isRemote() && library.isRemoteDownloadAllowed()) {
            schema = java.util.Optional.ofNullable(json.resolveJsonSchemaLocation(entry.location(), null))
                    .map(File::toPath);
        }
        String what = hit.get().fromSchemaStore()
                ? "SchemaStore schema \"" + hit.get().catalogName() + "\""
                : "Schema Library schema";
        return schema.filter(java.nio.file.Files::isRegularFile)
                .map(p -> new JsonSchemaChoice(p, null))
                .orElseGet(() -> new JsonSchemaChoice(null, "The " + what + " for the file name pattern "
                        + hit.get().pattern() + " cannot be loaded: " + entry.location()));
    }

    /**
     * The SchemaStore catalog for {@code --schemastore}: refused when the administrator turned it
     * off; downloaded when there is none or it is older than 7 days (a failed refresh keeps the old one).
     */
    private org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog openSchemaStore(CommandLine commandLine) {
        var policy = org.fxt.freexmltoolkit.service.AdminPolicy.current();
        String enabledKey = org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog.ENABLED_KEY;
        String urlKey = org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog.URL_KEY;
        if (policy.isLocked(enabledKey) && !Boolean.parseBoolean(policy.lockedValue(enabledKey))) {
            throw new CommandLine.ParameterException(commandLine,
                    "--schemastore: the SchemaStore catalog is turned off by the administrator (" + enabledKey + ")");
        }
        String url = schemaStoreUrl;
        if (policy.isLocked(urlKey)) {
            if (url != null && !url.strip().equals(policy.lockedValue(urlKey).strip())) {
                throw new CommandLine.ParameterException(commandLine,
                        "--schemastore-url: the administrator set the catalog address (" + urlKey + ")");
            }
            url = policy.lockedValue(urlKey);
        } else if (url == null) {
            url = policy.defaultValue(urlKey);
        }
        var store = org.fxt.freexmltoolkit.service.schemastore.SchemaStoreCatalog.shared();
        if (store.isStale() || schemaStoreUrl != null) {
            try {
                store.refresh(url);
            } catch (java.io.IOException | RuntimeException e) {
                String reason = FxtCli.messageOf(e);
                if (store.lastUpdated().isEmpty()) {
                    throw new CommandLine.ParameterException(commandLine,
                            "--schemastore: the catalog cannot be downloaded: " + reason, e, null, null);
                }
                commandLine.getErr().println("warning: the SchemaStore catalog was not updated (" + reason
                        + "); the cached one is used");
            }
        }
        return store;
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
