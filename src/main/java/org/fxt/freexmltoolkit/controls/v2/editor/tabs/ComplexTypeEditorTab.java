package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.views.ComplexTypeEditorView;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Tab for editing a ComplexType in the graphic editor.
 * Shows the ComplexType as root node with all child elements.
 *
 * IMPLEMENTATION - Phase 2:
 * Uses ComplexTypeEditorView with XsdGraphView for graphical editing.
 *
 * @since 2.0
 */
public class ComplexTypeEditorTab extends AbstractTypeEditorTab {

    private static final Logger logger = LogManager.getLogger(ComplexTypeEditorTab.class);

    private final XsdComplexType complexType;
    private final XsdSchema mainSchema;
    private ComplexTypeEditorView editorView;

    /**
     * Creates a new ComplexType editor tab.
     *
     * @param complexType the complex type to edit
     * @param mainSchema the main XSD schema (for merging changes)
     */
    public ComplexTypeEditorTab(XsdComplexType complexType, XsdSchema mainSchema) {
        super(complexType, "ComplexType: " + complexType.getName());
        this.complexType = complexType;
        this.mainSchema = mainSchema;
        initializeContent(); // Call after field initialization
    }

    @Override
    protected void initializeContent() {
        // Create editor view with virtual schema integration
        editorView = new ComplexTypeEditorView(complexType, mainSchema);
        setContent(editorView);

        // Setup change callback for dirty tracking
        editorView.setOnChangeCallback(() -> setDirty(true));

        // Setup close callback (Phase 6)
        editorView.setOnCloseCallback(() -> {
            // Request tab close (will trigger the close handler in TypeEditorTabManager)
            getTabPane().getTabs().remove(this);
        });

        logger.debug("ComplexTypeEditorTab initialized for type: {}", complexType.getName());
    }

    /**
     * Gets the ComplexType being edited.
     *
     * @return the complex type
     */
    public XsdComplexType getComplexType() {
        return complexType;
    }

    @Override
    public boolean save() {
        try {
            // Ask view to save changes
            boolean success = editorView.save();

            if (success) {
                setDirty(false);
                logger.info("ComplexType saved successfully: {}", complexType.getName());

                // Phase 6: User feedback for successful save
                showSuccessMessage("Save Successful",
                    "ComplexType '" + complexType.getName() + "' has been saved successfully.");
            } else {
                logger.warn("Failed to save ComplexType: {}", complexType.getName());

                // Phase 6: User feedback for save failure
                showErrorMessage("Save Failed",
                    "Failed to save ComplexType '" + complexType.getName() + "'",
                    "The save operation did not complete successfully.");
            }

            return success;

        } catch (Exception e) {
            logger.error("Error saving ComplexType: {}", complexType.getName(), e);

            // Phase 6: User feedback for save error
            showErrorMessage("Save Error",
                "Error saving ComplexType '" + complexType.getName() + "'",
                "Error: " + e.getMessage());

            return false;
        }
    }

    /**
     * Shows a success message to the user.
     * Phase 6: Error Handling & Validation
     */
    private void showSuccessMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Shows an error message to the user.
     * Phase 6: Error Handling & Validation
     */
    private void showErrorMessage(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void discardChanges() {
        try {
            // Ask view to reload
            editorView.reload();
            setDirty(false);
            logger.info("ComplexType changes discarded: {}", complexType.getName());

        } catch (Exception e) {
            logger.error("Error discarding changes for ComplexType: {}", complexType.getName(), e);
        }
    }
}
