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

    /**
     * Outcome of an XSD inference.
     *
     * @param text         the XSD content, or {@code "ERROR: …"}
     * @param errorCode    fixed identifier of a failure for usage statistics (exception class,
     *                     or {@code not_well_formed}); null on success
     * @param invalidInput whether the failure is the user's input (no well-formed XML)
     */
    public record XsdGeneration(String text, String errorCode, boolean invalidInput) {

        /** @return whether an XSD was generated */
        public boolean ok() {
            return errorCode == null;
        }

        static XsdGeneration of(SchemaGenerationResult result) {
            if (!result.isSuccess()) {
                String code = result.isInvalidInput() ? "not_well_formed"
                        : result.getExceptionType() != null ? result.getExceptionType() : "generation_failed";
                return new XsdGeneration("ERROR: " + result.getErrorMessage(), code, result.isInvalidInput());
            }
            String formatted = result.getFormattedXsdContent();
            return new XsdGeneration((formatted != null && !formatted.isBlank()) ? formatted : result.getXsdContent(),
                    null, false);
        }

        static XsdGeneration failed(Exception e, boolean invalidInput) {
            return new XsdGeneration("ERROR: " + e.getMessage(), e.getClass().getSimpleName(), invalidInput);
        }
    }

    /** Infers an XSD from the given XML. @return the XSD content, or {@code "ERROR: …"}. */
    public static String generateXsdFromXml(String xmlContent) {
        return generateXsd(xmlContent).text();
    }

    /** Infers an XSD from the given XML, keeping the failure classification. */
    public static XsdGeneration generateXsd(String xmlContent) {
        try {
            return XsdGeneration.of(SchemaGenerationEngine.getInstance()
                    .generateSchema(xmlContent, new SchemaGenerationOptions()));
        } catch (Exception e) {
            return XsdGeneration.failed(e, false);
        }
    }

    /**
     * Infers a single XSD from several XML sample files (batch), reusing the
     * engine's multi-document analysis. @return the XSD content, or {@code "ERROR: …"}.
     */
    public static String generateXsdFromMultiple(java.util.List<java.io.File> xmlFiles) {
        return generateXsdBatch(xmlFiles).text();
    }

    /** Batch variant of {@link #generateXsd(String)}; an unreadable file counts as invalid input. */
    public static XsdGeneration generateXsdBatch(java.util.List<java.io.File> xmlFiles) {
        if (xmlFiles == null || xmlFiles.isEmpty()) {
            return new XsdGeneration("ERROR: no XML files selected", "no_input", true);
        }
        java.util.List<String> documents = new java.util.ArrayList<>();
        try {
            for (java.io.File file : xmlFiles) {
                documents.add(java.nio.file.Files.readString(file.toPath()));
            }
        } catch (Exception e) {
            return XsdGeneration.failed(e, true);
        }
        try {
            return XsdGeneration.of(SchemaGenerationEngine.getInstance()
                    .generateSchemaFromMultipleDocuments(documents, new SchemaGenerationOptions()));
        } catch (Exception e) {
            return XsdGeneration.failed(e, false);
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
        return flatten(xsdContent, baseDirectory, null, options);
    }

    /**
     * Flattens an XSD, additionally passing the path of the schema the content came from.
     * Knowing the main schema file enables per-node origin tracking during parsing, which is
     * what {@link FlattenOptions#trackSourceFiles()} writes into the output.
     *
     * @param xsdContent     the schema content (may contain unsaved edits)
     * @param baseDirectory  directory used to resolve xs:include locations (may be null)
     * @param mainSchemaFile the file the content belongs to (may be null for unsaved content)
     * @param options        the reduction options
     * @return the flattened XSD, or {@code "ERROR: …"}
     */
    public static String flatten(String xsdContent, Path baseDirectory, Path mainSchemaFile,
                                 FlattenOptions options) {
        try {
            XsdNodeFactory factory = new XsdNodeFactory();
            XsdSchema schema = factory.fromStringWithSchemaFile(xsdContent, mainSchemaFile, baseDirectory);
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
