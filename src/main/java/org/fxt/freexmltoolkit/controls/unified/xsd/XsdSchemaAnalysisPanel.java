package org.fxt.freexmltoolkit.controls.unified.xsd;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SchemaAnalysisTabPane;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Panel wrapping SchemaAnalysisTabPane for embedding in XsdUnifiedTab.
 * Provides schema statistics, identity constraints, and quality checks.
 * Lazy-initialized on first display.
 */
public class XsdSchemaAnalysisPanel extends StackPane {

    private static final Logger logger = LogManager.getLogger(XsdSchemaAnalysisPanel.class);

    private SchemaAnalysisTabPane analysisTabPane;
    private XsdSchema currentSchema;
    private boolean initialized = false;

    public XsdSchemaAnalysisPanel() {
        getChildren().add(createEmptyState());
    }

    /**
     * Sets the schema. Resets lazy initialization flag.
     */
    public void setSchema(XsdSchema schema) {
        this.currentSchema = schema;
        this.initialized = false;

        if (schema == null) {
            getChildren().clear();
            getChildren().add(createEmptyState());
        }
    }

    /**
     * Initializes the analysis view if not yet done.
     * Should be called when the tab becomes visible.
     */
    public void ensureInitialized() {
        if (initialized || currentSchema == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                getChildren().clear();
                analysisTabPane = new SchemaAnalysisTabPane(currentSchema);
                getChildren().add(analysisTabPane);
                initialized = true;
                logger.debug("Schema Analysis panel initialized");
            } catch (Exception e) {
                logger.error("Failed to create Schema Analysis view: {}", e.getMessage());
                getChildren().add(new Label("Error: " + e.getMessage()));
            }
        });
    }

    private Label createEmptyState() {
        Label label = new Label("Load an XSD file to analyze schema");
        label.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 14px;");
        return label;
    }
}
