package org.fxt.freexmltoolkit.controls.shell.editor;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatisticsCollector;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.SchemaGenerationEngine;
import org.fxt.freexmltoolkit.service.SchemaGenerationOptions;
import org.fxt.freexmltoolkit.service.SchemaGenerationResult;

import java.nio.file.Path;

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

    /** Collects schema statistics into a plain-text report. @return report or {@code "ERROR: …"}. */
    public static String statistics(String xsdContent) {
        try {
            XsdSchema schema = new XsdNodeFactory().fromString(xsdContent);
            XsdStatistics s = new XsdStatisticsCollector(schema).collect();
            return "Schema Statistics\n"
                    + "=================\n"
                    + "Elements:        " + s.getElementCount() + "\n"
                    + "Attributes:      " + s.getAttributeCount() + "\n"
                    + "Complex types:   " + s.getComplexTypeCount() + "\n"
                    + "Simple types:    " + s.getSimpleTypeCount() + "\n"
                    + "Groups:          " + s.getGroupCount() + "\n"
                    + "Attribute groups:" + s.getAttributeGroupCount() + "\n"
                    + "Includes:        " + s.getIncludeCount() + "\n"
                    + "Imports:         " + s.getImportCount() + "\n";
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
