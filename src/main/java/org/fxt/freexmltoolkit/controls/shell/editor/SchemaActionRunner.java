package org.fxt.freexmltoolkit.controls.shell.editor;

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
}
