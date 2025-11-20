package org.fxt.freexmltoolkit.controls.v2.editor.tabs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.views.SimpleTypeEditorView;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

/**
 * Tab for editing a SimpleType.
 * Shows panels for General, Restriction, List, Union, and Annotation.
 *
 * Phase 3 Implementation - Real panels with model integration
 *
 * @since 2.0
 */
public class SimpleTypeEditorTab extends AbstractTypeEditorTab {

    private static final Logger logger = LogManager.getLogger(SimpleTypeEditorTab.class);

    private final XsdSimpleType simpleType;
    private final XsdSchema mainSchema;
    private final XsdEditorContext editorContext;
    private SimpleTypeEditorView editorView;

    /**
     * Creates a new SimpleType editor tab.
     *
     * @param simpleType the simple type to edit
     * @param mainSchema the main schema (for context)
     */
    public SimpleTypeEditorTab(XsdSimpleType simpleType, XsdSchema mainSchema) {
        super(simpleType, "SimpleType: " + simpleType.getName());
        this.simpleType = simpleType;
        this.mainSchema = mainSchema;
        this.editorContext = new XsdEditorContext(mainSchema);
        initializeContent(); // Call after field initialization
    }

    @Override
    protected void initializeContent() {
        // Create real editor view with editor context
        editorView = new SimpleTypeEditorView(simpleType, editorContext);
        setContent(editorView);

        // Setup change tracking
        editorView.setOnChangeCallback(() -> setDirty(true));

        // Setup action callbacks (Phase 6)
        editorView.setOnSaveCallback(this::save);
        editorView.setOnCloseCallback(() -> {
            // Request tab close (will trigger the close handler in TypeEditorTabManager)
            getTabPane().getTabs().remove(this);
        });
        editorView.setOnFindUsageCallback(() -> {
            // TODO Phase 5: Implement find usage
            logger.info("Find usage for SimpleType: {}", simpleType.getName());
        });
    }

    /**
     * Gets the SimpleType being edited.
     *
     * @return the simple type
     */
    public XsdSimpleType getSimpleType() {
        return simpleType;
    }

    @Override
    public boolean save() {
        try {
            // For SimpleType, changes are applied directly to the model
            // (unlike ComplexType which uses a virtual schema)
            // So save just means: accept all changes and clear dirty flag

            setDirty(false);
            logger.info("SimpleType saved successfully: {}", simpleType.getName());

            // Phase 6: User feedback for successful save
            showSuccessMessage("Save Successful",
                "SimpleType '" + simpleType.getName() + "' has been saved successfully.");

            return true;

        } catch (Exception e) {
            logger.error("Error saving SimpleType: {}", simpleType.getName(), e);

            // Phase 6: User feedback for save error
            showErrorMessage("Save Failed",
                "Failed to save SimpleType '" + simpleType.getName() + "'",
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
            // Discard changes by recreating the view
            // This will reload all data from the model's current state
            // (assuming model is reloaded from original source externally)

            editorView = new SimpleTypeEditorView(simpleType, editorContext);
            setContent(editorView);
            editorView.setOnChangeCallback(() -> setDirty(true));

            setDirty(false);
            logger.info("SimpleType changes discarded: {}", simpleType.getName());

        } catch (Exception e) {
            logger.error("Error discarding changes for SimpleType: {}", simpleType.getName(), e);
        }
    }
}
