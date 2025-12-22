package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.VirtualSchemaFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Main view for editing a ComplexType in the graphic editor.
 * Uses XsdGraphView for graphical editing (reuses existing component).
 *
 * IMPLEMENTATION - Phase 2:
 * - Creates "virtual" XsdSchema with ComplexType as single global element
 * - Passes virtual schema to XsdGraphView
 * - XsdGraphView displays ComplexType as root with all children
 * - All editing functions work immediately (Add Element, Delete, etc.)
 * - On save: Merge changes back to main schema
 *
 * @since 2.0
 */
public class ComplexTypeEditorView extends BorderPane {

    private static final Logger logger = LogManager.getLogger(ComplexTypeEditorView.class);

    private final XsdComplexType complexType;
    private final XsdSchema mainSchema;
    private XsdSchema virtualSchema;
    private XsdGraphView graphView;
    private Runnable onChangeCallback;
    private Runnable onCloseCallback;

    /**
     * Creates a new ComplexType editor view.
     *
     * @param complexType the complex type to edit
     * @param mainSchema the main schema (for merging changes back)
     */
    public ComplexTypeEditorView(XsdComplexType complexType, XsdSchema mainSchema) {
        this.complexType = complexType;
        this.mainSchema = mainSchema;
        initializeUI();
    }

    /**
     * Initializes the UI components.
     * Creates virtual schema and XsdGraphView for graphical editing.
     */
    private void initializeUI() {
        try {
            logger.info("Initializing ComplexTypeEditorView for type: {}", complexType.getName());
            logger.debug("ComplexType '{}' has {} children", complexType.getName(), complexType.getChildren().size());

            // Create top toolbar with name field
            ToolBar topToolbar = createTopToolbar();
            setTop(topToolbar);

            // Create virtual schema for this ComplexType (pass mainSchema for type resolution)
            virtualSchema = VirtualSchemaFactory.createVirtualSchemaForComplexType(complexType, mainSchema);
            logger.info("Virtual schema created with {} children", virtualSchema.getChildren().size());

            // Create XsdGraphView with virtual schema
            graphView = new XsdGraphView(virtualSchema);
            logger.info("XsdGraphView created successfully");

            // Setup change tracking for dirty flag
            setupChangeTracking();

            // Note: Save is handled via keyboard shortcuts (Ctrl+S) in parent controller

            // Set XsdGraphView as center (it includes toolbar, canvas, properties panel)
            setCenter(graphView);
            logger.debug("XsdGraphView set as center content");

            // Setup keyboard shortcuts
            setupKeyboardShortcuts();

            logger.info("ComplexTypeEditorView initialized successfully - ready to display");

        } catch (Exception e) {
            logger.error("Error initializing ComplexTypeEditorView", e);
            // Show error in UI
            Label errorLabel = new Label("Error loading type editor: " + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px;");
            setCenter(new VBox(20, errorLabel));
        }
    }

    /**
     * Creates the top toolbar with name field and other controls.
     */
    private ToolBar createTopToolbar() {
        ToolBar toolbar = new ToolBar();

        // Name label and field
        Label nameLabel = new Label("Name:");
        nameLabel.setStyle("-fx-font-weight: bold;");

        TextField nameField = new TextField(complexType.getName());
        nameField.setPrefWidth(250);
        nameField.setPromptText("Type name");
        nameField.setTooltip(new Tooltip("The name of this ComplexType"));

        // Listen to name changes
        nameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                // Lost focus - save the change
                handleNameChange(nameField.getText());
            }
        });

        // Also handle Enter key
        nameField.setOnAction(e -> handleNameChange(nameField.getText()));

        // Abstract checkbox
        CheckBox abstractCheck = new CheckBox("Abstract");
        abstractCheck.setSelected(complexType.isAbstract());
        abstractCheck.setTooltip(new Tooltip("Abstract types cannot be used directly"));
        abstractCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            complexType.setAbstract(newVal);
            logger.info("ComplexType abstract changed to: {}", newVal);
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });

        // Mixed checkbox
        CheckBox mixedCheck = new CheckBox("Mixed");
        mixedCheck.setSelected(complexType.isMixed());
        mixedCheck.setTooltip(new Tooltip("Mixed content allows text between elements"));
        mixedCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            complexType.setMixed(newVal);
            logger.info("ComplexType mixed changed to: {}", newVal);
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });

        // Close button
        Button closeBtn = new Button("Close");
        closeBtn.setGraphic(new FontIcon(BootstrapIcons.X_CIRCLE));
        closeBtn.setTooltip(new Tooltip("Close editor (Esc)"));
        closeBtn.setOnAction(e -> {
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        });

        toolbar.getItems().addAll(
                nameLabel, nameField,
                new Separator(),
                abstractCheck,
                mixedCheck,
                new Separator(),
                closeBtn
        );

        return toolbar;
    }

    /**
     * Handles name change for the ComplexType.
     */
    private void handleNameChange(String newName) {
        if (newName != null && !newName.trim().isEmpty() && !newName.equals(complexType.getName())) {
            String oldName = complexType.getName();
            complexType.setName(newName.trim());
            logger.info("ComplexType name changed: {} -> {}", oldName, newName.trim());

            // Trigger change callback
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        }
    }

    /**
     * Sets up change tracking for the virtual schema.
     * When changes occur, the onChangeCallback is triggered to set dirty flag.
     */
    private void setupChangeTracking() {
        if (virtualSchema != null) {
            virtualSchema.addPropertyChangeListener(evt -> {
                logger.debug("Change detected in virtual schema: {}", evt.getPropertyName());
                if (onChangeCallback != null) {
                    onChangeCallback.run();
                }
            });
        }
    }

    /**
     * Sets up keyboard shortcuts for the editor.
     * Phase 6: Keyboard shortcuts implementation
     */
    private void setupKeyboardShortcuts() {
        setOnKeyPressed(event -> {
            // Check for Ctrl key combinations
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case S:
                        // Ctrl+S: Save
                        save();
                        event.consume();
                        break;
                }
            } else {
                // Non-Ctrl shortcuts
                switch (event.getCode()) {
                    case ESCAPE:
                        // Esc: Close editor
                        if (onCloseCallback != null) {
                            onCloseCallback.run();
                            event.consume();
                        }
                        break;
                }
            }
        });

        // Ensure the BorderPane can receive keyboard events
        setFocusTraversable(true);
    }

    /**
     * Handles save action from XsdGraphView toolbar.
     * Merges changes from virtual schema back to main schema.
     */
    private void handleSave() {
        logger.info("Save requested for ComplexType: {}", complexType.getName());
        save();
    }

    /**
     * Saves changes from virtual schema back to main schema.
     * Called by the parent tab's save() method.
     *
     * @return true if save successful
     */
    public boolean save() {
        try {
            logger.info("Saving ComplexType: {}", complexType.getName());

            // Merge changes back to main schema
            VirtualSchemaFactory.mergeChangesBackToMainSchema(
                    virtualSchema,
                    complexType,
                    mainSchema
            );

            logger.info("ComplexType saved successfully: {}", complexType.getName());
            return true;

        } catch (Exception e) {
            logger.error("Error saving ComplexType: {}", complexType.getName(), e);
            return false;
        }
    }

    /**
     * Reloads the ComplexType from main schema, discarding changes.
     * Called by the parent tab's discardChanges() method.
     */
    public void reload() {
        try {
            logger.info("Reloading ComplexType: {}", complexType.getName());

            // Recreate virtual schema with fresh data (pass mainSchema for type resolution)
            virtualSchema = VirtualSchemaFactory.createVirtualSchemaForComplexType(complexType, mainSchema);

            // Recreate XsdGraphView with new virtual schema
            graphView = new XsdGraphView(virtualSchema);
            setupChangeTracking();

            // Replace view
            setCenter(graphView);

            logger.info("ComplexType reloaded successfully: {}", complexType.getName());

        } catch (Exception e) {
            logger.error("Error reloading ComplexType: {}", complexType.getName(), e);
        }
    }

    /**
     * Sets the callback to be called when changes are detected.
     * Used by the parent tab to set dirty flag.
     *
     * @param callback the callback to run on change
     */
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    /**
     * Sets the callback for close action.
     *
     * @param callback the callback to run on close
     */
    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }
}
