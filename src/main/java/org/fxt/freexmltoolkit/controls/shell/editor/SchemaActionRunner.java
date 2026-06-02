package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatisticsCollector;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.SchemaGenerationEngine;
import org.fxt.freexmltoolkit.service.SchemaGenerationOptions;
import org.fxt.freexmltoolkit.service.SchemaGenerationResult;

/**
 * UI-free schema actions for the Schema activity, reusing the existing services.
 * Errors are returned as {@code "ERROR: …"} text rather than thrown.
 */
public final class SchemaActionRunner {

    private SchemaActionRunner() {
    }

    /** Infers an XSD from the given XML. @return the XSD content, or {@code "ERROR: …"}. */
    public static String generateXsdFromXml(String xmlContent) {
        try {
            SchemaGenerationResult result = SchemaGenerationEngine.getInstance()
                    .generateSchema(xmlContent, new SchemaGenerationOptions());
            if (!result.isSuccess()) {
                return "ERROR: " + result.getErrorMessage();
            }
            String formatted = result.getFormattedXsdContent();
            return (formatted != null && !formatted.isBlank()) ? formatted : result.getXsdContent();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Infers a single XSD from several XML sample files (batch), reusing the
     * engine's multi-document analysis. @return the XSD content, or {@code "ERROR: …"}.
     */
    public static String generateXsdFromMultiple(java.util.List<java.io.File> xmlFiles) {
        if (xmlFiles == null || xmlFiles.isEmpty()) {
            return "ERROR: no XML files selected";
        }
        try {
            java.util.List<String> documents = new java.util.ArrayList<>();
            for (java.io.File file : xmlFiles) {
                documents.add(java.nio.file.Files.readString(file.toPath()));
            }
            SchemaGenerationResult result = SchemaGenerationEngine.getInstance()
                    .generateSchemaFromMultipleDocuments(documents, new SchemaGenerationOptions());
            if (!result.isSuccess()) {
                return "ERROR: " + result.getErrorMessage();
            }
            String formatted = result.getFormattedXsdContent();
            return (formatted != null && !formatted.isBlank()) ? formatted : result.getXsdContent();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Collects schema statistics into a plain-text report. @return report or {@code "ERROR: …"}. */
    public static String statistics(String xsdContent) {
        try {
            XsdSchema schema = new XsdNodeFactory().fromString(xsdContent);
            XsdStatistics s = new XsdStatisticsCollector(schema).collect();
            java.util.Set<String> unused = s.unusedTypes() == null
                    ? java.util.Set.of() : s.unusedTypes();
            String unusedList = unused.isEmpty() ? ""
                    : "  (" + unused.stream().sorted().limit(20).collect(java.util.stream.Collectors.joining(", "))
                    + (unused.size() > 20 ? ", …" : "") + ")";
            return "Schema Statistics\n"
                    + "=================\n"
                    + "XSD version:     " + (s.isXsd11() ? "1.1" : "1.0") + "\n"
                    + "Elements:        " + s.getElementCount() + "\n"
                    + "Attributes:      " + s.getAttributeCount() + "\n"
                    + "Complex types:   " + s.getComplexTypeCount() + "\n"
                    + "Simple types:    " + s.getSimpleTypeCount() + "\n"
                    + "Groups:          " + s.getGroupCount() + "\n"
                    + "Attribute groups:" + s.getAttributeGroupCount() + "\n"
                    + "Includes:        " + s.getIncludeCount() + "\n"
                    + "Imports:         " + s.getImportCount() + "\n"
                    + "\nConstraints\n"
                    + "-----------\n"
                    + "Keys:            " + s.getNodeCount(XsdNodeType.KEY) + "\n"
                    + "Key refs:        " + s.getNodeCount(XsdNodeType.KEYREF) + "\n"
                    + "Unique:          " + s.getNodeCount(XsdNodeType.UNIQUE) + "\n"
                    + "Assertions:      " + s.getNodeCount(XsdNodeType.ASSERT) + "\n"
                    + "\nCardinality\n"
                    + "-----------\n"
                    + "Optional elements:  " + s.optionalElements() + "\n"
                    + "Required elements:  " + s.requiredElements() + "\n"
                    + "Unbounded elements: " + s.unboundedElements() + "\n"
                    + "\nQuality\n"
                    + "-------\n"
                    + "Documentation coverage: "
                    + String.format(java.util.Locale.ROOT, "%.1f%%", s.documentationCoveragePercent()) + "\n"
                    + "Unused named types:     " + unused.size() + unusedList + "\n"
                    + "Total nodes:            " + s.totalNodeCount() + "\n";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Runs the V2 {@link XsdQualityChecker} and renders a readable report: overall score, naming
     * convention, checks passed, and the issues grouped by severity (with suggestion + location).
     *
     * @return the report text, or {@code "ERROR: …"} if the schema could not be parsed
     */
    public static String qualityReport(String xsdContent) {
        try {
            XsdSchema schema = new XsdNodeFactory().fromString(xsdContent);
            XsdQualityChecker.QualityResult result = new XsdQualityChecker(schema).check();

            StringBuilder sb = new StringBuilder();
            sb.append("Schema Quality Report\n")
                    .append("=====================\n")
                    .append("Score:             ").append(result.score()).append(" / 100 (")
                    .append(result.getScoreDescription()).append(")\n")
                    .append("Naming convention: ")
                    .append(result.dominantNamingConvention() == null
                            ? "—" : result.dominantNamingConvention().getDisplayName()).append("\n")
                    .append("Checks passed:     ").append(result.passedChecks())
                    .append(" / ").append(result.totalChecks()).append("\n")
                    .append("Issues:            ").append(result.issues().size()).append("\n");

            for (XsdQualityChecker.IssueSeverity severity : XsdQualityChecker.IssueSeverity.values()) {
                var bySeverity = result.issues().stream()
                        .filter(i -> i.severity() == severity).toList();
                if (bySeverity.isEmpty()) {
                    continue;
                }
                String header = severity + " (" + bySeverity.size() + ")";
                sb.append("\n").append(header).append("\n").append("-".repeat(header.length())).append("\n");
                for (XsdQualityChecker.QualityIssue issue : bySeverity) {
                    sb.append("• ").append(issue.message());
                    String where = issueLocation(issue);
                    if (!where.isEmpty()) {
                        sb.append("  [").append(where).append("]");
                    }
                    sb.append("\n");
                    if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
                        sb.append("    ↳ ").append(issue.suggestion()).append("\n");
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** @return a short location hint for an issue (xpath, affected elements, or source file). */
    private static String issueLocation(XsdQualityChecker.QualityIssue issue) {
        if (issue.xpath() != null && !issue.xpath().isBlank()) {
            return issue.xpath();
        }
        if (issue.affectedElements() != null && !issue.affectedElements().isEmpty()) {
            return String.join(", ", issue.affectedElements());
        }
        String file = issue.getSourceFileName();
        return file == null ? "" : file;
    }

    /**
     * Flattens an XSD: parses (resolving includes relative to {@code baseDirectory})
     * and re-serializes. @return the flattened XSD, or {@code "ERROR: …"}.
     */
    public static String flatten(String xsdContent, Path baseDirectory) {
        try {
            XsdNodeFactory factory = new XsdNodeFactory();
            XsdSchema schema = baseDirectory != null
                    ? factory.fromString(xsdContent, baseDirectory)
                    : factory.fromString(xsdContent);
            return new XsdSerializer().serialize(schema);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
