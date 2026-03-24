package org.fxt.freexmltoolkit.controls.unified.xsd;

import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.TypeLibraryView;

/**
 * Panel wrapping TypeLibraryView for embedding in XsdUnifiedTab.
 * Provides type browsing with filter, search, usage count, and preview.
 */
public class XsdTypeLibraryPanel extends StackPane {

    private static final Logger logger = LogManager.getLogger(XsdTypeLibraryPanel.class);

    private TypeLibraryView typeLibraryView;
    private XsdSchema currentSchema;

    public XsdTypeLibraryPanel() {
        getChildren().add(createEmptyState());
    }

    /**
     * Updates the panel with a new schema.
     */
    public void setSchema(XsdSchema schema) {
        this.currentSchema = schema;
        getChildren().clear();

        if (schema == null) {
            getChildren().add(createEmptyState());
            return;
        }

        try {
            typeLibraryView = new TypeLibraryView(schema);
            VBox.setVgrow(typeLibraryView, Priority.ALWAYS);
            getChildren().add(typeLibraryView);
            logger.debug("Type Library updated with schema");
        } catch (Exception e) {
            logger.error("Failed to create Type Library view: {}", e.getMessage());
            getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    /**
     * Gets the TypeLibraryView for callback wiring.
     */
    public TypeLibraryView getTypeLibraryView() {
        return typeLibraryView;
    }

    private Label createEmptyState() {
        Label label = new Label("Load an XSD file to browse types");
        label.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 14px;");
        return label;
    }
}
