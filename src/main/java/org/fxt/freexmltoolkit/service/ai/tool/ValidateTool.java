package org.fxt.freexmltoolkit.service.ai.tool;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.shell.editor.ValidationProblem;
import org.fxt.freexmltoolkit.controls.shell.editor.ValidationRunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * {@code validate}: well-formedness, XSD 1.1 / DTD / RELAX NG, Schematron and plugin validators on
 * {@link ValidationRunner#runWithReport(String, File, File, String, Path, boolean)} — the same run as the
 * Validation panel and {@code fxt-cli validate}. JSON and YAML documents are checked for syntax and
 * against a JSON Schema ({@link ValidationRunner#validateJson} / {@link ValidationRunner#validateYaml}).
 */
public final class ValidateTool implements AiTool {

    private static final Set<String> ARGUMENTS =
            Set.of("xml_path", "xml_content", "xsd_path", "schematron_path", "declared_schema", "xsd_version",
                    "xml_version", "schematron_binding", "json_schema_path", "format");

    private static final List<String> FORMATS = List.of("auto", "xml", "json", "yaml");

    @Override
    public String name() {
        return "validate";
    }

    @Override
    public String description() {
        return "Validate an XML, JSON or YAML document. XML: well-formedness, then the schema (XSD 1.0/1.1, DTD or RELAX NG), then an "
                + "optional Schematron, then the checks of enabled plugins (e.g. FundsXML, ISO 20022). The schema is "
                + "xsd_path if given, else the one the document declares (xsi:schemaLocation, DOCTYPE, Schema Library) "
                + "unless declared_schema is false. Returns every problem with source, severity, line, column and "
                + "message; 'valid' is true when the run had no errors (warnings allowed). 'not_run' means the "
                + "schema or Schematron itself could not be loaded, so the document was not checked. Use it after "
                + "every change to a document. xsd_version forces XSD 1.0 or 1.1 (default: detected from the schema), "
                + "xml_version 1.0 rejects documents declared as XML 1.1, schematron_binding sets the query binding of a "
                + "Schematron without @queryBinding (default xslt2). JSON and YAML (format auto: from the file "
                + "extension, inline content starting with { or [ is JSON; give format yaml for inline YAML): syntax, "
                + "then the JSON Schema json_schema_path, else the one the document declares ($schema, YAML modeline "
                + "# yaml-language-server: $schema=…) or a Schema Library file-name pattern matches, unless "
                + "declared_schema is false. Remote JSON Schemas are not downloaded.";
    }

    @Override
    public JsonObject inputSchema() {
        return ToolSchemas.builder()
                .input("xml", "XML, JSON or YAML document")
                .enumeration("format", "Document format; auto decides by the file extension or the content.",
                        FORMATS, "auto")
                .string("json_schema_path", "Path of the JSON Schema for a JSON or YAML document.", false)
                .string("xsd_path", "Path of the schema (XSD, DTD, .rng or .rnc) to validate against.", false)
                .string("schematron_path", "Path of a Schematron (.sch) to check after the schema.", false)
                .bool("declared_schema", "Use the schema the document declares when xsd_path is not given.", true)
                .version("xsd_version", "XSD version: auto (detected), 1.0 or 1.1.",
                        org.fxt.freexmltoolkit.service.standards.XsdVersionMode.class)
                .version("xml_version", "XML version: auto (as declared), 1.0 or 1.1.",
                        org.fxt.freexmltoolkit.service.standards.XmlVersionMode.class)
                .version("schematron_binding", "Query binding of a Schematron without its own @queryBinding.",
                        org.fxt.freexmltoolkit.service.standards.SchematronBinding.class)
                .build();
    }

    @Override
    public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
        ToolArgs args = new ToolArgs(arguments, ARGUMENTS);
        ToolInput xml = ToolInput.required(args, "xml", context);
        String format = args.optionalString("format").orElse("auto").trim().toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(format)) {
            throw new ToolException("'format' must be one of " + String.join(", ", FORMATS));
        }
        if (format.equals("auto")) {
            format = detectFormat(xml);
        }
        if (!format.equals("xml")) {
            if (args.has("xsd_path") || args.has("schematron_path")) {
                throw new ToolException("'xsd_path' and 'schematron_path' are for XML documents");
            }
            return validateData(xml, format.equals("yaml"), args, context);
        }
        if (args.has("json_schema_path")) {
            throw new ToolException("'json_schema_path' is for JSON and YAML documents");
        }
        File xsd = existingFile(args, "xsd_path", context);
        File schematron = existingFile(args, "schematron_path", context);
        boolean declared = args.optionalBoolean("declared_schema", true);
        var standards = new org.fxt.freexmltoolkit.service.standards.ValidationStandards(
                args.version("xsd_version", org.fxt.freexmltoolkit.service.standards.XsdVersionMode.class),
                args.version("xml_version", org.fxt.freexmltoolkit.service.standards.XmlVersionMode.class),
                args.version("schematron_binding", org.fxt.freexmltoolkit.service.standards.SchematronBinding.class));
        if (xsd == null && declared) {
            Path baseDir = xml.file().map(Path::getParent).orElse(context.files().roots().getFirst());
            xsd = ValidationRunner.declaredSchema(xml.text(), baseDir.toFile()).orElse(null);
        }
        String name = xml.file().map(f -> f.getFileName().toString()).orElse("inline.xml");
        ValidationRunner.RunResult run = ValidationRunner.runWithReport(xml.text(), xsd, schematron, name,
                xml.file().orElse(null), declared, standards);

        List<ValidationProblem> problems = new ArrayList<>(new LinkedHashSet<>(run.problems()));
        String notRunReason = run.failed() ? describeFailure(run) : schemaNotLoaded(problems);
        return report(problems, notRunReason, xsd, context);
    }

    /** The JSON / YAML run: syntax, then the JSON Schema given, declared or chosen by the file name. */
    private ToolResult validateData(ToolInput input, boolean yaml, ToolArgs args, ToolContext context)
            throws ToolException {
        File schema = existingFile(args, "json_schema_path", context);
        String notRunReason = null;
        if (schema == null && args.optionalBoolean("declared_schema", true)) {
            DataSchema found = dataSchemaOf(input, yaml, context);
            schema = found.schema();
            notRunReason = found.notFound();
        }
        List<ValidationProblem> problems = new ArrayList<>(new LinkedHashSet<>(yaml
                ? ValidationRunner.validateYaml(input.text(), notRunReason == null ? schema : null)
                : ValidationRunner.validateJson(input.text(), notRunReason == null ? schema : null)));
        return report(problems, notRunReason, schema, context);
    }

    private record DataSchema(File schema, String notFound) {
    }

    /**
     * The JSON Schema a document declares (resolved inside the allowed folders or through the Schema
     * Library, never downloaded), else the Schema Library entry its file name matches.
     */
    private static DataSchema dataSchemaOf(ToolInput input, boolean yaml, ToolContext context) {
        var json = new org.fxt.freexmltoolkit.service.JsonService();
        var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
        Optional<String> declared = yaml
                ? org.fxt.freexmltoolkit.service.yaml.YamlSchemaDeclaration.find(input.text())
                : json.getRawSchemaIdFromJsonContent(input.text());
        if (declared.isPresent()) {
            String location = declared.get().trim();
            Optional<Path> mapped = library.resolveJsonSchema(location).flatMap(e ->
                    org.fxt.freexmltoolkit.service.SchemaLibraryLookup.materializeIfAllowed(library, e, false));
            if (mapped.isPresent()) {
                return new DataSchema(mapped.get().toFile(), null);
            }
            if (!yaml && json.getSchemaLocationFromJsonContent(input.text()).isEmpty()) {
                return new DataSchema(null, null); // a meta-schema id: a schema document, no binding
            }
            if (location.startsWith("http://") || location.startsWith("https://")) {
                return new DataSchema(null, "the declared JSON Schema " + location
                        + " is remote and not downloaded; give json_schema_path");
            }
            try {
                Path base = input.file().map(Path::getParent).orElse(context.files().roots().getFirst());
                Path file = location.startsWith("file:") ? Path.of(java.net.URI.create(location))
                        : base.resolve(location);
                Path resolved = context.files().resolve(file.toString());
                return Files.isRegularFile(resolved) ? new DataSchema(resolved.toFile(), null)
                        : new DataSchema(null, "the declared JSON Schema was not found: " + location);
            } catch (ToolException | RuntimeException e) {
                return new DataSchema(null, "the declared JSON Schema cannot be read: " + location);
            }
        }
        Optional<Path> file = input.file();
        if (file.isEmpty()) {
            return new DataSchema(null, null);
        }
        var hit = org.fxt.freexmltoolkit.service.schemastore.JsonSchemaByFileName.find(file.get(), library, null);
        if (hit.isEmpty()) {
            return new DataSchema(null, null);
        }
        return org.fxt.freexmltoolkit.service.SchemaLibraryLookup.materializeIfAllowed(library, hit.get().entry(), false)
                .map(p -> new DataSchema(p.toFile(), null))
                .orElseGet(() -> new DataSchema(null, "the Schema Library schema for the file name pattern "
                        + hit.get().pattern() + " is not available locally: " + hit.get().entry().location()));
    }

    /** xml for XML files and content starting with '<', json / yaml by extension or a leading '{' / '['. */
    static String detectFormat(ToolInput input) {
        if (input.file().isPresent()) {
            String name = input.file().get().getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".json") || name.endsWith(".json5") || name.endsWith(".jsonc")) {
                return "json";
            }
            if (name.endsWith(".yaml") || name.endsWith(".yml")) {
                return "yaml";
            }
            return "xml";
        }
        String text = input.text().stripLeading();
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1).stripLeading();
        }
        return text.startsWith("{") || text.startsWith("[") ? "json" : "xml";
    }

    private static ToolResult report(List<ValidationProblem> problems, String notRunReason, File xsd,
                                     ToolContext context) {
        int errors = 0;
        int warnings = 0;
        JsonArray rows = new JsonArray();
        StringBuilder lines = new StringBuilder();
        int max = context.limits().maxItems();
        for (ValidationProblem p : problems) {
            String severity = severity(p.severity());
            if (severity.equals("error")) {
                errors++;
            } else if (severity.equals("warning")) {
                warnings++;
            }
            if (rows.size() < max) {
                rows.add(row(p, severity, context.limits()));
                lines.append('\n').append(Math.max(0, p.line())).append(':').append(Math.max(0, p.column()))
                        .append(": ").append(severity).append(": [").append(p.source()).append("] ")
                        .append(p.message());
            }
        }
        boolean notRun = notRunReason != null;
        boolean valid = !notRun && errors == 0;

        JsonObject result = new JsonObject();
        result.addProperty("valid", valid);
        result.addProperty("not_run", notRun);
        result.addProperty("not_run_reason", notRunReason);
        result.addProperty("schema", xsd == null ? null : xsd.getAbsolutePath());
        result.addProperty("error_count", errors);
        result.addProperty("warning_count", warnings);
        result.addProperty("total", problems.size());
        result.addProperty("truncated", problems.size() > rows.size());
        result.add("problems", rows);

        String summary = notRun ? "not validated: " + notRunReason
                : (valid ? "valid" : "invalid") + " (" + errors + " error" + (errors == 1 ? "" : "s") + ", "
                + warnings + " warning" + (warnings == 1 ? "" : "s") + ")"
                + (xsd != null ? " against " + xsd.getName() : "");
        if (problems.size() > rows.size()) {
            lines.append("\n… ").append(problems.size() - rows.size()).append(" more");
        }
        String text = summary + lines;
        return valid ? ToolResult.ok(text, result) : ToolResult.failed(text, result);
    }

    private static JsonObject row(ValidationProblem p, String severity, ToolLimits limits) {
        JsonObject row = new JsonObject();
        row.addProperty("source", p.source());
        row.addProperty("severity", severity);
        row.addProperty("line", Math.max(0, p.line()));
        row.addProperty("column", Math.max(0, p.column()));
        row.addProperty("message", limits.truncate(p.message()));
        row.addProperty("rule", p.ruleId());
        row.addProperty("context", p.context());
        return row;
    }

    /** error / warning / info, mapped like {@code fxt-cli validate} does. */
    static String severity(String text) {
        if (text == null) {
            return "error";
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "warning", "warn" -> "warning";
            case "info", "information", "informational", "note" -> "info";
            default -> "error";
        };
    }

    private static String schemaNotLoaded(List<ValidationProblem> problems) {
        return problems.stream().filter(p -> p.line() <= 0 && ValidationRunner.isSchemaNotLoaded(p.message()))
                .map(ValidationProblem::message).findFirst().orElse(null);
    }

    private static String describeFailure(ValidationRunner.RunResult run) {
        Throwable error = run.error();
        if (error != null && error.getMessage() != null) {
            return error.getMessage();
        }
        return run.problems().isEmpty() ? "the validation could not run" : run.problems().getFirst().message();
    }

    private static File existingFile(ToolArgs args, String name, ToolContext context) throws ToolException {
        Optional<String> path = args.optionalString(name);
        if (path.isEmpty()) {
            return null;
        }
        Path file = context.files().resolve(path.get());
        if (!Files.isRegularFile(file)) {
            throw new ToolException("'" + name + "': file not found: " + path.get());
        }
        return file.toFile();
    }
}
