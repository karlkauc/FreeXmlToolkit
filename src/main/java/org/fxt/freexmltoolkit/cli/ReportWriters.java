package org.fxt.freexmltoolkit.cli;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.fxt.freexmltoolkit.cli.ValidationReport.FileResult;
import org.fxt.freexmltoolkit.cli.ValidationReport.Finding;
import org.fxt.freexmltoolkit.cli.ValidationReport.Severity;
import org.fxt.freexmltoolkit.util.VersionUtil;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Renders a {@link ValidationReport} in the formats of {@code fxt-cli validate --report-format}.
 * File paths are written relative to the base directory (the working directory) when they lie
 * below it, with {@code /} as separator, so reports read the same on every platform.
 */
final class ReportWriters {

    /** The report formats. */
    enum Format {
        /** One line per problem ({@code file:line:column: severity: [source] message}) and a summary. */
        TEXT,
        /** The whole report as JSON. */
        JSON,
        /** JUnit XML: one test case per file, a failure per invalid file (CI test reports). */
        JUNIT,
        /** SARIF 2.1.0 (GitHub code scanning, Azure DevOps, GitLab). */
        SARIF
    }

    static final String INFORMATION_URI = "https://github.com/karlkauc/FreeXmlToolkit";

    /** The SARIF rules: one per kind of check. */
    private static final Map<String, String> RULES = new LinkedHashMap<>();

    static {
        RULES.put("well-formed", "XML is not well-formed");
        RULES.put("xsd", "XML does not match its XML Schema");
        RULES.put("schematron", "Schematron assertion failed or report fired");
        RULES.put("json", "JSON is not well-formed");
        RULES.put("json-schema", "JSON does not match its JSON Schema");
        RULES.put("not-run", "The validation could not run");
    }

    private final Path baseDir;
    private final boolean warningsFail;

    ReportWriters(Path baseDir, boolean warningsFail) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.warningsFail = warningsFail;
    }

    String write(ValidationReport report, Format format) {
        return switch (format) {
            case TEXT -> text(report);
            case JSON -> json(report);
            case JUNIT -> junit(report);
            case SARIF -> sarif(report);
        };
    }

    // ---- text --------------------------------------------------------------------------------

    String text(ValidationReport report) {
        return findings(report) + summary(report) + "\n";
    }

    /** One line per problem, without the summary. */
    String findings(ValidationReport report) {
        StringBuilder sb = new StringBuilder();
        for (FileResult file : report.files()) {
            String name = display(file.file());
            for (Finding f : file.findings()) {
                sb.append(name);
                if (f.line() > 0) {
                    sb.append(':').append(f.line());
                    if (f.column() > 0) {
                        sb.append(':').append(f.column());
                    }
                }
                sb.append(": ").append(f.severity().name().toLowerCase(Locale.ROOT))
                        .append(": [").append(f.source()).append("] ").append(oneLine(f.message()));
                if (f.context() != null && !f.context().isBlank()) {
                    sb.append(" (at ").append(f.context()).append(')');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** One line: files, invalid files, errors, warnings, time. */
    String summary(ValidationReport report) {
        int files = report.files().size();
        long failed = report.failedFiles(warningsFail);
        long notRun = report.files().stream().filter(f -> f.notRun() != null).count();
        StringBuilder sb = new StringBuilder();
        sb.append(files).append(files == 1 ? " file" : " files").append(", ");
        sb.append(failed == 0 ? "all valid" : failed + " invalid");
        sb.append(", ").append(report.count(Severity.ERROR)).append(" error(s), ")
                .append(report.count(Severity.WARNING)).append(" warning(s)");
        if (notRun > 0) {
            sb.append(", ").append(notRun).append(" not validated");
        }
        sb.append(" (").append(report.totalMillis()).append(" ms)");
        return sb.toString();
    }

    // ---- JSON --------------------------------------------------------------------------------

    String json(ValidationReport report) {
        JsonObject root = new JsonObject();
        root.addProperty("tool", "FreeXmlToolkit");
        root.addProperty("version", VersionUtil.getVersion());
        root.addProperty("valid", report.failedFiles(warningsFail) == 0 && !report.anyNotRun());
        JsonObject summary = new JsonObject();
        summary.addProperty("files", report.files().size());
        summary.addProperty("invalidFiles", report.failedFiles(warningsFail));
        summary.addProperty("errors", report.count(Severity.ERROR));
        summary.addProperty("warnings", report.count(Severity.WARNING));
        summary.addProperty("infos", report.count(Severity.INFO));
        summary.addProperty("millis", report.totalMillis());
        root.add("summary", summary);
        JsonArray files = new JsonArray();
        for (FileResult file : report.files()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("file", display(file.file()));
            if (file.schema() != null) {
                entry.addProperty("schema", display(file.schema()));
            }
            entry.addProperty("valid", !file.failed(warningsFail));
            if (file.notRun() != null) {
                entry.addProperty("notRun", file.notRun());
            }
            entry.addProperty("millis", file.millis());
            JsonArray findings = new JsonArray();
            for (Finding f : file.findings()) {
                JsonObject finding = new JsonObject();
                finding.addProperty("severity", f.severity().name().toLowerCase(Locale.ROOT));
                finding.addProperty("source", f.source());
                finding.addProperty("line", f.line());
                finding.addProperty("column", f.column());
                finding.addProperty("message", f.message());
                if (f.ruleId() != null) {
                    finding.addProperty("rule", f.ruleId());
                }
                if (f.context() != null) {
                    finding.addProperty("context", f.context());
                }
                findings.add(finding);
            }
            entry.add("findings", findings);
            files.add(entry);
        }
        root.add("files", files);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    // ---- JUnit XML ---------------------------------------------------------------------------

    String junit(ValidationReport report) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeCharacters("\n");
            long failures = report.files().stream()
                    .filter(f -> f.notRun() == null && f.failed(warningsFail)).count();
            long errors = report.files().stream().filter(f -> f.notRun() != null).count();
            String time = seconds(report.totalMillis());
            xml.writeStartElement("testsuites");
            xml.writeAttribute("name", "fxt-cli validate");
            xml.writeAttribute("tests", String.valueOf(report.files().size()));
            xml.writeAttribute("failures", String.valueOf(failures));
            xml.writeAttribute("errors", String.valueOf(errors));
            xml.writeAttribute("time", time);
            xml.writeCharacters("\n  ");
            xml.writeStartElement("testsuite");
            xml.writeAttribute("name", "validate");
            xml.writeAttribute("tests", String.valueOf(report.files().size()));
            xml.writeAttribute("failures", String.valueOf(failures));
            xml.writeAttribute("errors", String.valueOf(errors));
            xml.writeAttribute("skipped", "0");
            xml.writeAttribute("time", time);
            for (FileResult file : report.files()) {
                String name = display(file.file());
                xml.writeCharacters("\n    ");
                xml.writeStartElement("testcase");
                xml.writeAttribute("classname", "validate");
                xml.writeAttribute("name", name);
                xml.writeAttribute("file", name);
                xml.writeAttribute("time", seconds(file.millis()));
                if (file.failed(warningsFail)) {
                    boolean notRun = file.notRun() != null;
                    xml.writeCharacters("\n      ");
                    xml.writeStartElement(notRun ? "error" : "failure");
                    xml.writeAttribute("message", notRun ? oneLine(file.notRun())
                            : file.count(Severity.ERROR) + " error(s), " + file.count(Severity.WARNING)
                            + " warning(s)");
                    xml.writeAttribute("type", notRun ? "not-run" : "invalid");
                    xml.writeCharacters(details(file));
                    xml.writeEndElement();
                    xml.writeCharacters("\n    ");
                } else if (!file.findings().isEmpty()) {
                    xml.writeCharacters("\n      ");
                    xml.writeStartElement("system-out");
                    xml.writeCharacters(details(file));
                    xml.writeEndElement();
                    xml.writeCharacters("\n    ");
                }
                xml.writeEndElement();
            }
            xml.writeCharacters("\n  ");
            xml.writeEndElement();
            xml.writeCharacters("\n");
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Cannot write the JUnit report: " + e.getMessage(), e);
        }
        return out + "\n";
    }

    private String details(FileResult file) {
        StringBuilder sb = new StringBuilder();
        for (Finding f : file.findings()) {
            sb.append(f.severity().name().toLowerCase(Locale.ROOT));
            if (f.line() > 0) {
                sb.append(" at line ").append(f.line());
                if (f.column() > 0) {
                    sb.append(", column ").append(f.column());
                }
            }
            sb.append(": [").append(f.source()).append("] ").append(oneLine(f.message())).append('\n');
        }
        return sb.toString();
    }

    private static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
    }

    // ---- SARIF 2.1.0 -------------------------------------------------------------------------

    String sarif(ValidationReport report) {
        JsonObject driver = new JsonObject();
        driver.addProperty("name", "FreeXmlToolkit");
        driver.addProperty("version", VersionUtil.getVersion());
        driver.addProperty("informationUri", INFORMATION_URI);
        JsonArray rules = new JsonArray();
        RULES.forEach((id, text) -> {
            JsonObject rule = new JsonObject();
            rule.addProperty("id", id);
            JsonObject shortDescription = new JsonObject();
            shortDescription.addProperty("text", text);
            rule.add("shortDescription", shortDescription);
            rules.add(rule);
        });
        driver.add("rules", rules);
        JsonObject tool = new JsonObject();
        tool.add("driver", driver);

        JsonArray results = new JsonArray();
        for (FileResult file : report.files()) {
            for (Finding f : file.findings()) {
                JsonObject result = new JsonObject();
                String ruleId = file.notRun() != null && f.line() <= 0 ? "not-run" : ruleIdOf(f.source());
                result.addProperty("ruleId", ruleId);
                result.addProperty("level", switch (f.severity()) {
                    case ERROR -> "error";
                    case WARNING -> "warning";
                    case INFO -> "note";
                });
                JsonObject message = new JsonObject();
                message.addProperty("text", f.message() == null ? "" : f.message());
                result.add("message", message);
                result.add("locations", location(file.file(), f));
                if (f.ruleId() != null || f.context() != null) {
                    JsonObject properties = new JsonObject();
                    if (f.ruleId() != null) {
                        properties.addProperty("rule", f.ruleId());
                    }
                    if (f.context() != null) {
                        properties.addProperty("context", f.context());
                    }
                    result.add("properties", properties);
                }
                results.add(result);
            }
        }
        JsonObject invocation = new JsonObject();
        invocation.addProperty("executionSuccessful", !report.anyNotRun());
        JsonArray invocations = new JsonArray();
        invocations.add(invocation);

        JsonObject run = new JsonObject();
        run.add("tool", tool);
        run.add("invocations", invocations);
        JsonObject srcRoot = new JsonObject();
        srcRoot.addProperty("uri", baseDir.toUri().toString());
        JsonObject bases = new JsonObject();
        bases.add("%SRCROOT%", srcRoot);
        run.add("originalUriBaseIds", bases);
        run.add("results", results);
        JsonArray runs = new JsonArray();
        runs.add(run);

        JsonObject root = new JsonObject();
        root.addProperty("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        root.addProperty("version", "2.1.0");
        root.add("runs", runs);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    private JsonArray location(Path file, Finding f) {
        JsonObject artifact = new JsonObject();
        Path absolute = file.toAbsolutePath().normalize();
        if (absolute.startsWith(baseDir)) {
            artifact.addProperty("uri", slashes(baseDir.relativize(absolute)));
            artifact.addProperty("uriBaseId", "%SRCROOT%");
        } else {
            artifact.addProperty("uri", absolute.toUri().toString());
        }
        JsonObject physical = new JsonObject();
        physical.add("artifactLocation", artifact);
        if (f.line() > 0) {
            JsonObject region = new JsonObject();
            region.addProperty("startLine", f.line());
            if (f.column() > 0) {
                region.addProperty("startColumn", f.column());
            }
            physical.add("region", region);
        }
        JsonObject location = new JsonObject();
        location.add("physicalLocation", physical);
        JsonArray locations = new JsonArray();
        locations.add(location);
        return locations;
    }

    static String ruleIdOf(String source) {
        if (source == null) {
            return "xsd";
        }
        return switch (source) {
            case "Well-formed" -> "well-formed";
            case "Schematron" -> "schematron";
            case "JSON" -> "json";
            case "JSON Schema" -> "json-schema";
            default -> "xsd";
        };
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** The path as shown in reports: relative to the base directory when below it. */
    String display(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        return absolute.startsWith(baseDir) ? slashes(baseDir.relativize(absolute)) : absolute.toString();
    }

    private static String slashes(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ").trim();
    }
}
