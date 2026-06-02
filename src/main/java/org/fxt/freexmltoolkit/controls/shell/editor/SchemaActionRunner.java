package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
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
