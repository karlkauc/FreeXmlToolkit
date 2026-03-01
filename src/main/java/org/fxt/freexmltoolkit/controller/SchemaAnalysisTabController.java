package org.fxt.freexmltoolkit.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SchemaAnalysisTabPane;

/**
 * Controller for the Schema Analysis Tab.
 * Handles lazy initialization and updates of the schema analysis view.
 */
public class SchemaAnalysisTabController {

    private static final Logger logger = LogManager.getLogger(SchemaAnalysisTabController.class);

    @FXML
    private Tab schemaAnalysisTab;
    @FXML
    private StackPane schemaAnalysisStackPane;
    @FXML
    private VBox noFileLoadedPane;

    private XsdController parentController;
    private XsdSchema currentSchema;
    private boolean initialized = false;

    @FXML
    public void initialize() {
        if (schemaAnalysisTab != null) {
            schemaAnalysisTab.setOnSelectionChanged(event -> {
                if (schemaAnalysisTab.isSelected()) {
                    ensureInitialized();
                }
            });
        }
    }

    private void ensureInitialized() {
        if (!initialized && currentSchema != null) {
            long startTime = System.currentTimeMillis();
            Platform.runLater(() -> {
                if (schemaAnalysisStackPane != null) {
                    schemaAnalysisStackPane.getChildren().clear();
                    SchemaAnalysisTabPane analysisPane = new SchemaAnalysisTabPane(currentSchema);
                    schemaAnalysisStackPane.getChildren().add(analysisPane);
                    initialized = true;
                    if (noFileLoadedPane != null) noFileLoadedPane.setVisible(false);
                    long elapsed = System.currentTimeMillis() - startTime;
                    logger.info("SchemaAnalysisTabPane initialized in {}ms", elapsed);
                }
            });
        }
    }

    public void setSchema(XsdSchema schema) {
        this.currentSchema = schema;
        this.initialized = false; // Reset to force re-initialization on next tab selection
        if (schemaAnalysisTab != null && schemaAnalysisTab.isSelected()) {
            ensureInitialized();
        } else if (schema == null) {
            if (noFileLoadedPane != null) noFileLoadedPane.setVisible(true);
            if (schemaAnalysisStackPane != null) schemaAnalysisStackPane.getChildren().clear();
        }
    }

    public void setParentController(XsdController parentController) {
        this.parentController = parentController;
    }
}
