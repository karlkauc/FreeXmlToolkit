package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.VirtualSchemaFactory;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeAbstractCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeMixedCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdGraphView;
import org.fxt.freexmltoolkit.i18n.I18n;

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
    /**
     * The schema document's context: name, abstract and mixed act on the type in the main schema,
     * so they run as commands on its undo stack. Null (standalone use): they are set directly.
     */
    private final XsdEditorContext mainContext;
    /** Keeps the toolbar in step with the type; removed by {@link #dispose()}. */
    private java.beans.PropertyChangeListener typeListener;
    private XsdSchema virtualSchema;
    private XsdGraphView graphView;
    private Runnable onChangeCallback;
    private Runnable onCloseCallback;
    /** Opens a named type (one framed inside this type) in its own type editor; may be null. */
    private java.util.function.Consumer<String> openTypeEditorCallback;

    /**
     * Creates a new ComplexType editor view.
     *
     * @param complexType the complex type to edit
     * @param mainSchema the main schema (for merging changes back)
     */
    public ComplexTypeEditorView(XsdComplexType complexType, XsdSchema mainSchema) {
        this(complexType, mainSchema, null);
    }

    /**
     * Creates a new ComplexType editor view whose type-level changes (name, abstract, mixed) are
     * commands of the schema document, undoable there.
     *
     * @param complexType the complex type to edit
     * @param mainSchema  the main schema (for merging changes back)
     * @param mainContext the schema document's editor context (nullable)
     */
    public ComplexTypeEditorView(XsdComplexType complexType, XsdSchema mainSchema, XsdEditorContext mainContext) {
        this.complexType = complexType;
        this.mainSchema = mainSchema;
        this.mainContext = mainContext;
        initializeUI();
    }

    /** Detaches from the edited type (call when the editor tab closes). */
    public void dispose() {
        if (typeListener != null) {
            complexType.removePropertyChangeListener(typeListener);
            typeListener = null;
        }
    }

    /** Runs a change of the type in the main schema: as a command when the document's context is known. */
    private void apply(XsdCommand command, Runnable direct) {
        if (mainContext != null) {
            mainContext.getCommandManager().executeCommand(command);
        } else {
            direct.run();
        }
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
            graphView.setRootKind("complexType"); // the root card is a stand-in element for the type
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
            Label errorLabel = new Label(I18n.t("ComplexTypeEditorView.error-loading-type-editor", e.getMessage()));
            errorLabel.setStyle("-fx-text-fill: red;");
            errorLabel.getStyleClass().add("fxt-text-lg");
            setCenter(new VBox(20, errorLabel));
        }
    }

    /**
     * Creates the top toolbar with name field and other controls.
     */
    private ToolBar createTopToolbar() {
        ToolBar toolbar = new ToolBar();

        // Name label and field
        Label nameLabel = new Label(I18n.t("ComplexTypeEditorView.name"));
        nameLabel.setStyle("-fx-font-weight: bold;");

        TextField nameField = new TextField(complexType.getName());
        nameField.setPrefWidth(250);
        nameField.setPromptText(I18n.t("ComplexTypeEditorView.type-name"));
        nameField.setTooltip(new Tooltip(I18n.t("ComplexTypeEditorView.the-name-of-this-complextype")));

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
        CheckBox abstractCheck = new CheckBox(I18n.t("ComplexTypeEditorView.abstract"));
        abstractCheck.setSelected(complexType.isAbstract());
        abstractCheck.setTooltip(new Tooltip(I18n.t("ComplexTypeEditorView.abstract-types-cannot-be-used")));
        abstractCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == complexType.isAbstract()) {
                return; // follows the model (undo / redo), nothing to change
            }
            apply(mainContext == null ? null : new ChangeAbstractCommand(mainContext, complexType, newVal),
                    () -> complexType.setAbstract(newVal));
            logger.info("ComplexType abstract changed to: {}", newVal);
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });

        // Mixed checkbox
        CheckBox mixedCheck = new CheckBox(I18n.t("ComplexTypeEditorView.mixed"));
        mixedCheck.setSelected(complexType.isMixed());
        mixedCheck.setTooltip(new Tooltip(I18n.t("ComplexTypeEditorView.mixed-content-allows-text-between")));
        mixedCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == complexType.isMixed()) {
                return; // follows the model (undo / redo), nothing to change
            }
            apply(mainContext == null ? null : new ChangeMixedCommand(mainContext, complexType, newVal),
                    () -> complexType.setMixed(newVal));
            logger.info("ComplexType mixed changed to: {}", newVal);
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });

        // Undo / redo in the schema document change the type: the controls follow
        typeListener = evt -> javafx.application.Platform.runLater(() -> {
            switch (evt.getPropertyName()) {
                case "abstract" -> abstractCheck.setSelected(complexType.isAbstract());
                case "mixed" -> mixedCheck.setSelected(complexType.isMixed());
                case "name" -> {
                    if (!nameField.isFocused()) {
                        nameField.setText(complexType.getName());
                    }
                }
                default -> { }
            }
        });
        complexType.addPropertyChangeListener(typeListener);

        // Close button
        Button closeBtn = new Button(I18n.t("ComplexTypeEditorView.close"));
        closeBtn.setGraphic(new IconifyIcon("bi-x-circle"));
        closeBtn.setTooltip(new Tooltip(I18n.t("ComplexTypeEditorView.close-editor-esc")));
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
            String name = newName.trim();
            apply(new RenameNodeCommand(complexType, name), () -> complexType.setName(name));
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
                    default:
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
                    default:
                        break;
                }
            }
        });

        // Ensure the BorderPane can receive keyboard events
        setFocusTraversable(true);
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
            graphView.setRootKind("complexType");
            wireTypeCallbacks();
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
     * Sets how a named type used inside this type is opened in its own type editor (context menu
     * "Edit Type", "Open in Type Editor", double-click on a type frame's title). Anonymous types
     * have no name and are ignored.
     *
     * @param callback receives the type's name
     */
    public void setOpenTypeEditorCallback(java.util.function.Consumer<String> callback) {
        this.openTypeEditorCallback = callback;
        wireTypeCallbacks();
    }

    /** Hands the open-type callback to the (possibly recreated) graph view. */
    private void wireTypeCallbacks() {
        if (graphView == null || openTypeEditorCallback == null) {
            return;
        }
        graphView.setOpenComplexTypeEditorCallback(ct -> openNamed(ct == null ? null : ct.getName()));
        graphView.setOpenSimpleTypeEditorCallback(st -> openNamed(st == null ? null : st.getName()));
    }

    private void openNamed(String typeName) {
        if (openTypeEditorCallback != null && typeName != null && !typeName.isBlank()) {
            openTypeEditorCallback.accept(typeName);
        }
    }

    /**
     * Sets the callback for close action.
     *
     * @param callback the callback to run on close
     */
    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    /**
     * @return the embedded graphical view (may be {@code null} if initialization failed), so a
     * host can slim its chrome (e.g. {@code useMinimalChrome()}).
     */
    public XsdGraphView getGraphView() {
        return graphView;
    }
}
