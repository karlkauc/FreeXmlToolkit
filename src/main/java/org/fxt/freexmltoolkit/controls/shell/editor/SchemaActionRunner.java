package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.editor.flatten.FlattenOptions;
import org.fxt.freexmltoolkit.controls.v2.editor.flatten.SchemaFlattenTransformer;
import org.fxt.freexmltoolkit.controls.v2.editor.flatten.XsdOutputMinifier;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
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

    /**
     * Flattens an XSD: parses (resolving includes relative to {@code baseDirectory})
     * and re-serializes. @return the flattened XSD, or {@code "ERROR: …"}.
     */
    public static String flatten(String xsdContent, Path baseDirectory) {
        return flatten(xsdContent, baseDirectory, FlattenOptions.NONE);
    }

    /**
     * Flattens an XSD with reduction options (strip annotations/comments, drop
     * resolved include directives, remove unused global components, minify) —
     * see {@link FlattenOptions}. @return the flattened XSD, or {@code "ERROR: …"}.
     */
    public static String flatten(String xsdContent, Path baseDirectory, FlattenOptions options) {
        try {
            XsdNodeFactory factory = new XsdNodeFactory();
            XsdSchema schema = baseDirectory != null
                    ? factory.fromString(xsdContent, baseDirectory)
                    : factory.fromString(xsdContent);
            if (options.requiresTransform()) {
                new SchemaFlattenTransformer().apply(schema, options);
            }
            String result = new XsdSerializer().serialize(schema);
            return options.minify() ? XsdOutputMinifier.minify(result) : result;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
