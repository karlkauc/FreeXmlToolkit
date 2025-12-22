package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.FacetsPanel;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * Main view for editing a SimpleType.
 * Shows tabbed panels: General, Restriction, List, Union, Annotation
 *
 * Phase 3 Implementation - Real panels with model integration
 *
 * @since 2.0
 */
public class SimpleTypeEditorView extends BorderPane {

    private static final Logger logger = LogManager.getLogger(SimpleTypeEditorView.class);

    private final XsdSimpleType simpleType;
    private final XsdEditorContext editorContext;
    private Runnable onChangeCallback;
    private Runnable onSaveCallback;
    private Runnable onCloseCallback;
    private Runnable onFindUsageCallback;

    // UI Components
    private ToolBar toolbar;
    private TabPane tabPane;
    private Button saveBtn;
    private Button closeBtn;
    private Button findUsageBtn;

    // Restriction Panel Components
    private ComboBox<String> baseTypeCombo;
    private FacetsPanel facetsPanel;

    // Enumerations, Patterns, Assertions Lists
    private ListView<String> enumerationsListView;
    private ListView<String> patternsListView;
    private ListView<String> assertionsListView;

    /**
     * Creates a new SimpleType editor view.
     *
     * @param simpleType the simple type to edit
     * @param editorContext the editor context
     */
    public SimpleTypeEditorView(XsdSimpleType simpleType, XsdEditorContext editorContext) {
        this.simpleType = simpleType;
        this.editorContext = editorContext;
        initializeUI();
    }

    /**
     * Initializes the UI components.
     * Phase 3: Real panels with model integration
     */
    private void initializeUI() {
        // Top: Toolbar
        toolbar = createToolbar();
        setTop(toolbar);

        // Center: Tab Pane with 5 tabs
        tabPane = createTabPane();
        setCenter(tabPane);

        // Setup change tracking
        setupChangeTracking();

        // Setup keyboard shortcuts
        setupKeyboardShortcuts();
    }

    /**
     * Sets up change tracking for all model changes.
     */
    private void setupChangeTracking() {
        // Listen to SimpleType changes
        simpleType.addPropertyChangeListener(evt -> {
            logger.debug("SimpleType property changed: {}", evt.getPropertyName());
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });
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
                        if (onSaveCallback != null) {
                            onSaveCallback.run();
                            event.consume();
                        }
                        break;

                    case U:
                        // Ctrl+U: Find usage
                        if (onFindUsageCallback != null) {
                            onFindUsageCallback.run();
                            event.consume();
                        }
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
     * Sets the callback to be called when changes are detected.
     * Used by the parent tab to set dirty flag.
     *
     * @param callback the callback to run on change
     */
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    /**
     * Sets the callback for save action.
     *
     * @param callback the callback to run on save
     */
    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
        if (saveBtn != null) {
            saveBtn.setDisable(false);
        }
    }

    /**
     * Sets the callback for close action.
     *
     * @param callback the callback to run on close
     */
    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
        if (closeBtn != null) {
            closeBtn.setDisable(false);
        }
    }

    /**
     * Sets the callback for find usage action.
     *
     * @param callback the callback to run on find usage
     */
    public void setOnFindUsageCallback(Runnable callback) {
        this.onFindUsageCallback = callback;
        if (findUsageBtn != null) {
            findUsageBtn.setDisable(false);
        }
    }

    /**
     * Creates the toolbar.
     * Phase 6: Functional buttons with callbacks
     */
    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();

        saveBtn = new Button("Save Type");
        saveBtn.setGraphic(new FontIcon(BootstrapIcons.SAVE));
        saveBtn.setTooltip(new Tooltip("Save changes (Ctrl+S)"));
        saveBtn.setStyle("-fx-font-weight: bold;");
        saveBtn.setDisable(true); // Enabled when callback is set
        saveBtn.setOnAction(e -> {
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
        });

        closeBtn = new Button("Close");
        closeBtn.setGraphic(new FontIcon(BootstrapIcons.X_CIRCLE));
        closeBtn.setTooltip(new Tooltip("Close editor (Esc)"));
        closeBtn.setDisable(true); // Enabled when callback is set
        closeBtn.setOnAction(e -> {
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        });

        findUsageBtn = new Button("Find Usage");
        findUsageBtn.setGraphic(new FontIcon(BootstrapIcons.SEARCH));
        findUsageBtn.setTooltip(new Tooltip("Find where this type is used (Ctrl+U)"));
        findUsageBtn.setDisable(true); // Enabled when callback is set
        findUsageBtn.setOnAction(e -> {
            if (onFindUsageCallback != null) {
                onFindUsageCallback.run();
            }
        });

        toolbar.getItems().addAll(
                saveBtn,
                new Separator(),
                findUsageBtn,
                new Separator(),
                closeBtn
        );

        return toolbar;
    }

    /**
     * Creates the tab pane with 5 tabs.
     * DUMMY: Placeholder content in each tab
     */
    private TabPane createTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: General
        Tab generalTab = new Tab("General", createGeneralPanel());

        // Tab 2: Restriction
        Tab restrictionTab = new Tab("Restriction", createRestrictionPanel());

        // Tab 3: List
        Tab listTab = new Tab("List", createListPanel());
        listTab.setDisable(true); // DUMMY: Enabled if type is list

        // Tab 4: Union
        Tab unionTab = new Tab("Union", createUnionPanel());
        unionTab.setDisable(true); // DUMMY: Enabled if type is union

        // Tab 5: Annotation
        Tab annotationTab = new Tab("Annotation", createAnnotationPanel());

        tabPane.getTabs().addAll(generalTab, restrictionTab, listTab, unionTab, annotationTab);

        return tabPane;
    }

    /**
     * Creates the General panel.
     * Real implementation with name and final attributes.
     */
    private VBox createGeneralPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("General Properties");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        // Name field - editable
        Label nameLabel = new Label("Name:");
        TextField nameField = new TextField(simpleType.getName());
        nameField.setPromptText("Type name");
        nameField.setTooltip(new Tooltip("The name of this SimpleType"));

        // Listen to name changes
        nameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (wasFocused && !isNowFocused) {
                // Lost focus - save the change
                String newName = nameField.getText();
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(simpleType.getName())) {
                    String oldName = simpleType.getName();
                    simpleType.setName(newName.trim());
                    logger.info("SimpleType name changed: {} -> {}", oldName, newName.trim());

                    // Trigger change callback
                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }
                }
            }
        });

        // Also handle Enter key
        nameField.setOnAction(e -> {
            String newName = nameField.getText();
            if (newName != null && !newName.trim().isEmpty() && !newName.equals(simpleType.getName())) {
                String oldName = simpleType.getName();
                simpleType.setName(newName.trim());
                logger.info("SimpleType name changed: {} -> {}", oldName, newName.trim());

                // Trigger change callback
                if (onChangeCallback != null) {
                    onChangeCallback.run();
                }
            }
        });

        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);

        // Final attribute - prevents further derivation
        Label finalLabel = new Label("Final:");

        CheckBox finalCheck = new CheckBox("Prevent further derivation");
        finalCheck.setSelected(simpleType.isFinal());
        finalCheck.setTooltip(new Tooltip("When checked, this type cannot be further derived"));

        // Listen to changes
        finalCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            logger.info("Updating final attribute to: {}", newVal);
            simpleType.setFinal(newVal);

            // Trigger change callback
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });

        grid.add(finalLabel, 0, 1);
        grid.add(finalCheck, 1, 1);

        // Information label
        Label infoLabel = new Label(
                "The 'final' attribute prevents this type from being further derived.\n" +
                "When set, other types cannot use this type as a base."
        );
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");
        infoLabel.setPrefWidth(400);

        panel.getChildren().addAll(title, grid, new Label(""), infoLabel);

        return panel;
    }

    /**
     * Creates the Restriction panel.
     * Real implementation with FacetsPanel integration and tabs for Enumerations, Patterns, Assertions.
     */
    private VBox createRestrictionPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label title = new Label("Restriction");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Base Type selector
        HBox baseTypeBox = new HBox(10);
        baseTypeBox.setAlignment(Pos.CENTER_LEFT);
        Label baseLabel = new Label("Base Type:");
        baseTypeCombo = new ComboBox<>();
        baseTypeCombo.setPrefWidth(200);

        // Populate with common XSD built-in types
        baseTypeCombo.getItems().addAll(
                "xs:string", "xs:normalizedString", "xs:token",
                "xs:int", "xs:integer", "xs:long", "xs:short", "xs:byte",
                "xs:decimal", "xs:float", "xs:double",
                "xs:boolean",
                "xs:date", "xs:time", "xs:dateTime",
                "xs:duration", "xs:gYear", "xs:gYearMonth", "xs:gMonth", "xs:gMonthDay", "xs:gDay",
                "xs:hexBinary", "xs:base64Binary",
                "xs:anyURI", "xs:QName",
                "xs:positiveInteger", "xs:nonNegativeInteger", "xs:negativeInteger", "xs:nonPositiveInteger",
                "xs:unsignedLong", "xs:unsignedInt", "xs:unsignedShort", "xs:unsignedByte"
        );

        // Find existing restriction to get base type
        XsdRestriction currentRestriction = findRestrictionInSimpleType();
        if (currentRestriction != null && currentRestriction.getBase() != null) {
            baseTypeCombo.setValue(currentRestriction.getBase());
        } else {
            baseTypeCombo.setValue("xs:string"); // Default
        }

        // Listen to base type changes
        baseTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            handleBaseTypeChange(newVal);
        });

        baseTypeBox.getChildren().addAll(baseLabel, baseTypeCombo);

        // Create TabPane for Facets, Enumerations, Patterns, Assertions
        TabPane restrictionTabPane = new TabPane();
        restrictionTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: Facets
        Tab facetsTab = new Tab("Facets");
        facetsTab.setGraphic(new FontIcon("bi-funnel"));
        facetsPanel = new FacetsPanel(editorContext);
        if (currentRestriction != null) {
            facetsPanel.setRestriction(currentRestriction);
            logger.debug("Loaded restriction with base '{}' and {} facets",
                    currentRestriction.getBase(), currentRestriction.getFacets().size());
        }
        facetsTab.setContent(facetsPanel);

        // Tab 2: Enumerations
        Tab enumerationsTab = createEnumerationsTab(currentRestriction);

        // Tab 3: Patterns
        Tab patternsTab = createPatternsTab(currentRestriction);

        // Tab 4: Assertions
        Tab assertionsTab = createAssertionsTab(currentRestriction);

        restrictionTabPane.getTabs().addAll(facetsTab, enumerationsTab, patternsTab, assertionsTab);
        VBox.setVgrow(restrictionTabPane, Priority.ALWAYS);

        panel.getChildren().addAll(
                title,
                baseTypeBox,
                new Separator(),
                restrictionTabPane
        );

        return panel;
    }

    /**
     * Creates the Enumerations tab.
     *
     * @param restriction the current restriction (may be null)
     * @return the Enumerations tab
     */
    private Tab createEnumerationsTab(XsdRestriction restriction) {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // Title and description
        Label titleLabel = new Label("Enumeration Values");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label descLabel = new Label("Define a list of allowed values for this type (e.g., 'red', 'green', 'blue')");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Enumerations list
        enumerationsListView = new ListView<>();
        enumerationsListView.setPrefHeight(200);
        enumerationsListView.setPlaceholder(new Label("No enumeration values defined"));
        VBox.setVgrow(enumerationsListView, Priority.ALWAYS);

        // Load existing enumerations
        if (restriction != null) {
            loadEnumerationsFromRestriction(restriction);
        }

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);
        Button addEnumBtn = new Button("Add");
        addEnumBtn.setGraphic(new FontIcon("bi-plus-circle"));
        Button editEnumBtn = new Button("Edit");
        editEnumBtn.setGraphic(new FontIcon("bi-pencil"));
        editEnumBtn.setDisable(true);
        Button removeEnumBtn = new Button("Remove");
        removeEnumBtn.setGraphic(new FontIcon("bi-trash"));
        removeEnumBtn.setDisable(true);

        // Enable/disable buttons based on selection
        enumerationsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editEnumBtn.setDisable(!hasSelection);
            removeEnumBtn.setDisable(!hasSelection);
        });

        // Add button action
        addEnumBtn.setOnAction(e -> handleAddEnumeration());

        // Edit button action
        editEnumBtn.setOnAction(e -> handleEditEnumeration());

        // Remove button action
        removeEnumBtn.setOnAction(e -> handleRemoveEnumeration());

        buttonBox.getChildren().addAll(addEnumBtn, editEnumBtn, removeEnumBtn);

        vbox.getChildren().addAll(titleLabel, descLabel, new Separator(), enumerationsListView, buttonBox);

        Tab tab = new Tab("Enumerations", vbox);
        tab.setGraphic(new FontIcon("bi-list-ul"));
        return tab;
    }

    /**
     * Creates the Patterns tab.
     *
     * @param restriction the current restriction (may be null)
     * @return the Patterns tab
     */
    private Tab createPatternsTab(XsdRestriction restriction) {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // Title and description
        Label titleLabel = new Label("Regular Expression Patterns");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label descLabel = new Label("Define regex patterns that values must match (e.g., [0-9]{3}-[0-9]{2}-[0-9]{4})");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Patterns list
        patternsListView = new ListView<>();
        patternsListView.setPrefHeight(200);
        patternsListView.setPlaceholder(new Label("No patterns defined"));
        VBox.setVgrow(patternsListView, Priority.ALWAYS);

        // Load existing patterns
        if (restriction != null) {
            loadPatternsFromRestriction(restriction);
        }

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);
        Button addPatternBtn = new Button("Add");
        addPatternBtn.setGraphic(new FontIcon("bi-plus-circle"));
        Button editPatternBtn = new Button("Edit");
        editPatternBtn.setGraphic(new FontIcon("bi-pencil"));
        editPatternBtn.setDisable(true);
        Button removePatternBtn = new Button("Remove");
        removePatternBtn.setGraphic(new FontIcon("bi-trash"));
        removePatternBtn.setDisable(true);

        // Enable/disable buttons based on selection
        patternsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editPatternBtn.setDisable(!hasSelection);
            removePatternBtn.setDisable(!hasSelection);
        });

        // Button actions
        addPatternBtn.setOnAction(e -> handleAddPattern());
        editPatternBtn.setOnAction(e -> handleEditPattern());
        removePatternBtn.setOnAction(e -> handleRemovePattern());

        buttonBox.getChildren().addAll(addPatternBtn, editPatternBtn, removePatternBtn);

        vbox.getChildren().addAll(titleLabel, descLabel, new Separator(), patternsListView, buttonBox);

        Tab tab = new Tab("Patterns", vbox);
        tab.setGraphic(new FontIcon("bi-braces"));
        return tab;
    }

    /**
     * Creates the Assertions tab (XSD 1.1 feature).
     *
     * @param restriction the current restriction (may be null)
     * @return the Assertions tab
     */
    private Tab createAssertionsTab(XsdRestriction restriction) {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // Title and description
        Label titleLabel = new Label("XSD 1.1 Assertions");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label descLabel = new Label("XPath-based assertions for complex validation rules (XSD 1.1 feature)");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Assertions list
        assertionsListView = new ListView<>();
        assertionsListView.setPrefHeight(200);
        assertionsListView.setPlaceholder(new Label("No assertions defined (requires XSD 1.1)"));
        VBox.setVgrow(assertionsListView, Priority.ALWAYS);

        // Load existing assertions
        if (restriction != null) {
            loadAssertionsFromRestriction(restriction);
        }

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);
        Button addAssertBtn = new Button("Add");
        addAssertBtn.setGraphic(new FontIcon("bi-plus-circle"));
        Button editAssertBtn = new Button("Edit");
        editAssertBtn.setGraphic(new FontIcon("bi-pencil"));
        editAssertBtn.setDisable(true);
        Button removeAssertBtn = new Button("Remove");
        removeAssertBtn.setGraphic(new FontIcon("bi-trash"));
        removeAssertBtn.setDisable(true);

        // Enable/disable buttons based on selection
        assertionsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editAssertBtn.setDisable(!hasSelection);
            removeAssertBtn.setDisable(!hasSelection);
        });

        // Button actions
        addAssertBtn.setOnAction(e -> handleAddAssertion());
        editAssertBtn.setOnAction(e -> handleEditAssertion());
        removeAssertBtn.setOnAction(e -> handleRemoveAssertion());

        buttonBox.getChildren().addAll(addAssertBtn, editAssertBtn, removeAssertBtn);

        // Info note about XSD 1.1
        Label infoLabel = new Label("Note: Assertions are an XSD 1.1 feature and may not be supported by all validators");
        infoLabel.setStyle("-fx-text-fill: #ff8c00; -fx-font-size: 10px; -fx-font-style: italic;");
        infoLabel.setWrapText(true);

        vbox.getChildren().addAll(titleLabel, descLabel, new Separator(), assertionsListView, buttonBox, infoLabel);

        Tab tab = new Tab("Assertions", vbox);
        tab.setGraphic(new FontIcon("bi-check2-square"));
        return tab;
    }

    // ========== Load methods ==========

    /**
     * Loads enumerations from a restriction into the ListView.
     */
    private void loadEnumerationsFromRestriction(XsdRestriction restriction) {
        enumerationsListView.getItems().clear();
        for (XsdFacet facet : restriction.getFacets()) {
            if (facet.getFacetType() == XsdFacetType.ENUMERATION) {
                enumerationsListView.getItems().add(facet.getValue());
            }
        }
        logger.debug("Loaded {} enumerations from restriction", enumerationsListView.getItems().size());
    }

    /**
     * Loads patterns from a restriction into the ListView.
     */
    private void loadPatternsFromRestriction(XsdRestriction restriction) {
        patternsListView.getItems().clear();
        for (XsdFacet facet : restriction.getFacets()) {
            if (facet.getFacetType() == XsdFacetType.PATTERN) {
                patternsListView.getItems().add(facet.getValue());
            }
        }
        logger.debug("Loaded {} patterns from restriction", patternsListView.getItems().size());
    }

    /**
     * Loads assertions from a restriction into the ListView.
     */
    private void loadAssertionsFromRestriction(XsdRestriction restriction) {
        assertionsListView.getItems().clear();
        for (XsdFacet facet : restriction.getFacets()) {
            if (facet.getFacetType() == XsdFacetType.ASSERTION) {
                assertionsListView.getItems().add(facet.getValue());
            }
        }
        logger.debug("Loaded {} assertions from restriction", assertionsListView.getItems().size());
    }

    // ========== Enumeration handlers ==========

    /**
     * Handles adding a new enumeration value.
     */
    private void handleAddEnumeration() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Enumeration");
        dialog.setHeaderText("Add Enumeration Value");
        dialog.setContentText("Enter value:");

        dialog.showAndWait().ifPresent(value -> {
            if (value != null && !value.trim().isEmpty()) {
                String trimmedValue = value.trim();

                // Get or create restriction
                XsdRestriction restriction = getOrCreateRestriction();
                if (restriction != null) {
                    // Create new facet
                    XsdFacet facet = new XsdFacet(XsdFacetType.ENUMERATION, trimmedValue);
                    restriction.addFacet(facet);

                    // Update ListView
                    enumerationsListView.getItems().add(trimmedValue);

                    // Trigger change callback
                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }

                    logger.info("Added enumeration value: {}", trimmedValue);
                }
            }
        });
    }

    /**
     * Handles editing an enumeration value.
     */
    private void handleEditEnumeration() {
        String selected = enumerationsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected);
        dialog.setTitle("Edit Enumeration");
        dialog.setHeaderText("Edit Enumeration Value");
        dialog.setContentText("Enter new value:");

        dialog.showAndWait().ifPresent(newValue -> {
            if (newValue != null && !newValue.trim().isEmpty() && !newValue.equals(selected)) {
                String trimmedValue = newValue.trim();

                XsdRestriction restriction = findRestrictionInSimpleType();
                if (restriction != null) {
                    // Find and update the facet
                    for (XsdFacet facet : restriction.getFacets()) {
                        if (facet.getFacetType() == XsdFacetType.ENUMERATION && selected.equals(facet.getValue())) {
                            facet.setValue(trimmedValue);
                            break;
                        }
                    }

                    // Update ListView
                    int index = enumerationsListView.getItems().indexOf(selected);
                    if (index >= 0) {
                        enumerationsListView.getItems().set(index, trimmedValue);
                    }

                    // Trigger change callback
                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }

                    logger.info("Edited enumeration value: {} -> {}", selected, trimmedValue);
                }
            }
        });
    }

    /**
     * Handles removing an enumeration value.
     */
    private void handleRemoveEnumeration() {
        String selected = enumerationsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        XsdRestriction restriction = findRestrictionInSimpleType();
        if (restriction != null) {
            // Find and remove the facet
            XsdFacet toRemove = null;
            for (XsdFacet facet : restriction.getFacets()) {
                if (facet.getFacetType() == XsdFacetType.ENUMERATION && selected.equals(facet.getValue())) {
                    toRemove = facet;
                    break;
                }
            }
            if (toRemove != null) {
                restriction.removeFacet(toRemove);
            }

            // Update ListView
            enumerationsListView.getItems().remove(selected);

            // Trigger change callback
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }

            logger.info("Removed enumeration value: {}", selected);
        }
    }

    // ========== Pattern handlers ==========

    /**
     * Handles adding a new pattern.
     */
    private void handleAddPattern() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Pattern");
        dialog.setHeaderText("Add Regex Pattern");
        dialog.setContentText("Enter pattern:");

        dialog.showAndWait().ifPresent(value -> {
            if (value != null && !value.trim().isEmpty()) {
                String trimmedValue = value.trim();

                XsdRestriction restriction = getOrCreateRestriction();
                if (restriction != null) {
                    XsdFacet facet = new XsdFacet(XsdFacetType.PATTERN, trimmedValue);
                    restriction.addFacet(facet);

                    patternsListView.getItems().add(trimmedValue);

                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }

                    logger.info("Added pattern: {}", trimmedValue);
                }
            }
        });
    }

    /**
     * Handles editing a pattern.
     */
    private void handleEditPattern() {
        String selected = patternsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected);
        dialog.setTitle("Edit Pattern");
        dialog.setHeaderText("Edit Regex Pattern");
        dialog.setContentText("Enter new pattern:");

        dialog.showAndWait().ifPresent(newValue -> {
            if (newValue != null && !newValue.trim().isEmpty() && !newValue.equals(selected)) {
                String trimmedValue = newValue.trim();

                XsdRestriction restriction = findRestrictionInSimpleType();
                if (restriction != null) {
                    for (XsdFacet facet : restriction.getFacets()) {
                        if (facet.getFacetType() == XsdFacetType.PATTERN && selected.equals(facet.getValue())) {
                            facet.setValue(trimmedValue);
                            break;
                        }
                    }

                    int index = patternsListView.getItems().indexOf(selected);
                    if (index >= 0) {
                        patternsListView.getItems().set(index, trimmedValue);
                    }

                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }

                    logger.info("Edited pattern: {} -> {}", selected, trimmedValue);
                }
            }
        });
    }

    /**
     * Handles removing a pattern.
     */
    private void handleRemovePattern() {
        String selected = patternsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        XsdRestriction restriction = findRestrictionInSimpleType();
        if (restriction != null) {
            XsdFacet toRemove = null;
            for (XsdFacet facet : restriction.getFacets()) {
                if (facet.getFacetType() == XsdFacetType.PATTERN && selected.equals(facet.getValue())) {
                    toRemove = facet;
                    break;
                }
            }
            if (toRemove != null) {
                restriction.removeFacet(toRemove);
            }

            patternsListView.getItems().remove(selected);

            if (onChangeCallback != null) {
                onChangeCallback.run();
            }

            logger.info("Removed pattern: {}", selected);
        }
    }

    // ========== Assertion handlers ==========

    /**
     * Handles adding a new assertion.
     */
    private void handleAddAssertion() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Assertion");
        dialog.setHeaderText("Add XPath Assertion");
        dialog.setContentText("Enter XPath expression:");

        dialog.showAndWait().ifPresent(value -> {
            if (value != null && !value.trim().isEmpty()) {
                String trimmedValue = value.trim();

                XsdRestriction restriction = getOrCreateRestriction();
                if (restriction != null) {
                    XsdFacet facet = new XsdFacet(XsdFacetType.ASSERTION, trimmedValue);
                    restriction.addFacet(facet);

                    assertionsListView.getItems().add(trimmedValue);

                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }

                    logger.info("Added assertion: {}", trimmedValue);
                }
            }
        });
    }

    /**
     * Handles editing an assertion.
     */
    private void handleEditAssertion() {
        String selected = assertionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected);
        dialog.setTitle("Edit Assertion");
        dialog.setHeaderText("Edit XPath Assertion");
        dialog.setContentText("Enter new XPath expression:");

        dialog.showAndWait().ifPresent(newValue -> {
            if (newValue != null && !newValue.trim().isEmpty() && !newValue.equals(selected)) {
                String trimmedValue = newValue.trim();

                XsdRestriction restriction = findRestrictionInSimpleType();
                if (restriction != null) {
                    for (XsdFacet facet : restriction.getFacets()) {
                        if (facet.getFacetType() == XsdFacetType.ASSERTION && selected.equals(facet.getValue())) {
                            facet.setValue(trimmedValue);
                            break;
                        }
                    }

                    int index = assertionsListView.getItems().indexOf(selected);
                    if (index >= 0) {
                        assertionsListView.getItems().set(index, trimmedValue);
                    }

                    if (onChangeCallback != null) {
                        onChangeCallback.run();
                    }

                    logger.info("Edited assertion: {} -> {}", selected, trimmedValue);
                }
            }
        });
    }

    /**
     * Handles removing an assertion.
     */
    private void handleRemoveAssertion() {
        String selected = assertionsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        XsdRestriction restriction = findRestrictionInSimpleType();
        if (restriction != null) {
            XsdFacet toRemove = null;
            for (XsdFacet facet : restriction.getFacets()) {
                if (facet.getFacetType() == XsdFacetType.ASSERTION && selected.equals(facet.getValue())) {
                    toRemove = facet;
                    break;
                }
            }
            if (toRemove != null) {
                restriction.removeFacet(toRemove);
            }

            assertionsListView.getItems().remove(selected);

            if (onChangeCallback != null) {
                onChangeCallback.run();
            }

            logger.info("Removed assertion: {}", selected);
        }
    }

    /**
     * Gets or creates a restriction for the SimpleType.
     *
     * @return the restriction, or null if creation fails
     */
    private XsdRestriction getOrCreateRestriction() {
        XsdRestriction restriction = findRestrictionInSimpleType();
        if (restriction == null) {
            // Create new restriction with current base type
            String baseType = baseTypeCombo.getValue();
            if (baseType == null || baseType.isEmpty()) {
                baseType = "xs:string";
            }
            restriction = new XsdRestriction(baseType);
            simpleType.addChild(restriction);
            logger.info("Created new restriction with base '{}'", baseType);
        }
        return restriction;
    }

    /**
     * Finds the restriction child in the SimpleType.
     *
     * @return the restriction, or null if not found
     */
    private XsdRestriction findRestrictionInSimpleType() {
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction) {
                return (XsdRestriction) child;
            }
        }
        return null;
    }

    /**
     * Handles base type changes.
     * Creates or updates the restriction with the new base type.
     *
     * @param newBaseType the new base type
     */
    private void handleBaseTypeChange(String newBaseType) {
        if (newBaseType == null || newBaseType.isEmpty()) {
            return;
        }

        logger.info("Base type changed to: {}", newBaseType);

        XsdRestriction restriction = findRestrictionInSimpleType();

        if (restriction == null) {
            // Create new restriction
            restriction = new XsdRestriction(newBaseType);
            simpleType.addChild(restriction);
            logger.info("Created new restriction with base '{}'", newBaseType);
        } else {
            // Update existing restriction
            restriction.setBase(newBaseType);
            logger.info("Updated restriction base to '{}'", newBaseType);
        }

        // Update FacetsPanel with the restriction
        facetsPanel.setRestriction(restriction);

        // Trigger change callback
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    /**
     * Creates the List panel.
     * Real implementation with itemType selector.
     */
    private VBox createListPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("List");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label itemTypeLabel = new Label("Item Type:");
        ComboBox<String> itemTypeCombo = new ComboBox<>();

        // Populate with XSD built-in types
        itemTypeCombo.getItems().addAll(
                "xs:string", "xs:normalizedString", "xs:token",
                "xs:int", "xs:integer", "xs:long", "xs:short", "xs:byte",
                "xs:decimal", "xs:float", "xs:double",
                "xs:boolean",
                "xs:date", "xs:time", "xs:dateTime",
                "xs:hexBinary", "xs:base64Binary",
                "xs:anyURI", "xs:QName"
        );

        // Find existing list to get itemType
        XsdList currentList = findListInSimpleType();
        if (currentList != null && currentList.getItemType() != null) {
            itemTypeCombo.setValue(currentList.getItemType());
        } else {
            itemTypeCombo.setValue("xs:string"); // Default
        }

        // Listen to changes
        itemTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            handleItemTypeChange(newVal);
        });

        Label description = new Label(
                "A list creates space-separated values of the specified item type.\n" +
                "Example: \"DE AT CH FR\" (list of country codes)"
        );
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        panel.getChildren().addAll(
                title,
                new Label(""),
                itemTypeLabel, itemTypeCombo,
                new Label(""),
                description
        );

        return panel;
    }

    /**
     * Finds the list child in the SimpleType.
     *
     * @return the list, or null if not found
     */
    private XsdList findListInSimpleType() {
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdList) {
                return (XsdList) child;
            }
        }
        return null;
    }

    /**
     * Handles item type changes for list.
     * Creates or updates the list with the new item type.
     *
     * @param newItemType the new item type
     */
    private void handleItemTypeChange(String newItemType) {
        if (newItemType == null || newItemType.isEmpty()) {
            return;
        }

        logger.info("Item type changed to: {}", newItemType);

        XsdList list = findListInSimpleType();

        if (list == null) {
            // Create new list
            list = new XsdList();
            list.setItemType(newItemType);
            simpleType.addChild(list);
            logger.info("Created new list with itemType '{}'", newItemType);
        } else {
            // Update existing list
            list.setItemType(newItemType);
            logger.info("Updated list itemType to '{}'", newItemType);
        }

        // Trigger change callback
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    /**
     * Creates the Union panel.
     * Real implementation with member types list.
     */
    private VBox createUnionPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("Union");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label memberTypesLabel = new Label("Member Types:");
        ListView<String> memberTypesList = new ListView<>();
        memberTypesList.setPrefHeight(150);

        // Find existing union to get member types
        XsdUnion currentUnion = findUnionInSimpleType();
        if (currentUnion != null) {
            memberTypesList.getItems().addAll(currentUnion.getMemberTypes());
        }

        Button addBtn = new Button("Add Member Type");
        addBtn.setGraphic(new FontIcon(BootstrapIcons.PLUS_CIRCLE));
        addBtn.setTooltip(new Tooltip("Add a type to the union"));
        addBtn.setOnAction(e -> handleAddMemberType(memberTypesList));

        Button removeBtn = new Button("Remove");
        removeBtn.setGraphic(new FontIcon(BootstrapIcons.TRASH));
        removeBtn.setTooltip(new Tooltip("Remove selected type"));
        removeBtn.setStyle("-fx-text-fill: red;");
        removeBtn.setOnAction(e -> handleRemoveMemberType(memberTypesList));
        removeBtn.setDisable(true);

        // Enable/disable remove button based on selection
        memberTypesList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeBtn.setDisable(newVal == null);
        });

        HBox buttonsBox = new HBox(10, addBtn, removeBtn);

        Label description = new Label(
                "A union allows values from any of the specified member types.\n" +
                "Example: Value can be either string OR decimal."
        );
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        panel.getChildren().addAll(
                title,
                new Label(""),
                memberTypesLabel,
                memberTypesList,
                buttonsBox,
                new Label(""),
                description
        );

        return panel;
    }

    /**
     * Finds the union child in the SimpleType.
     *
     * @return the union, or null if not found
     */
    private XsdUnion findUnionInSimpleType() {
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdUnion) {
                return (XsdUnion) child;
            }
        }
        return null;
    }

    /**
     * Handles adding a member type to the union.
     *
     * @param memberTypesList the member types list view
     */
    private void handleAddMemberType(ListView<String> memberTypesList) {
        // Show dialog to select type
        ChoiceDialog<String> dialog = new ChoiceDialog<>("xs:string",
                "xs:string", "xs:normalizedString", "xs:token",
                "xs:int", "xs:integer", "xs:long", "xs:short", "xs:byte",
                "xs:decimal", "xs:float", "xs:double",
                "xs:boolean",
                "xs:date", "xs:time", "xs:dateTime",
                "xs:hexBinary", "xs:base64Binary",
                "xs:anyURI", "xs:QName"
        );
        dialog.setTitle("Add Member Type");
        dialog.setHeaderText("Select a type to add to the union");
        dialog.setContentText("Type:");

        dialog.showAndWait().ifPresent(selectedType -> {
            logger.info("Adding member type: {}", selectedType);

            XsdUnion union = findUnionInSimpleType();

            if (union == null) {
                // Create new union
                union = new XsdUnion();
                simpleType.addChild(union);
                logger.info("Created new union");
            }

            // Add member type
            union.addMemberType(selectedType);
            memberTypesList.getItems().add(selectedType);

            // Trigger change callback
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });
    }

    /**
     * Handles removing a member type from the union.
     *
     * @param memberTypesList the member types list view
     */
    private void handleRemoveMemberType(ListView<String> memberTypesList) {
        String selected = memberTypesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        logger.info("Removing member type: {}", selected);

        XsdUnion union = findUnionInSimpleType();
        if (union != null) {
            union.removeMemberType(selected);
            memberTypesList.getItems().remove(selected);

            // Trigger change callback
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        }
    }

    /**
     * Creates the Annotation panel.
     * Real implementation with documentation and appinfo support.
     */
    private VBox createAnnotationPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("Annotation");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Documentation section
        Label docLabel = new Label("Documentation:");
        docLabel.setStyle("-fx-font-weight: bold;");

        TextArea docArea = new TextArea();
        docArea.setPrefRowCount(5);
        docArea.setWrapText(true);
        docArea.setPromptText("Enter user-facing documentation for this type...");

        // Load existing documentation
        if (simpleType.getDocumentation() != null) {
            docArea.setText(simpleType.getDocumentation());
        }

        // Listen to changes
        docArea.textProperty().addListener((obs, oldVal, newVal) -> {
            logger.debug("Documentation changed");
            simpleType.setDocumentation(newVal);

            // Trigger change callback
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });

        Label docDescription = new Label(
                "Documentation is user-facing text that describes this type's purpose and usage.\n" +
                "It will appear in generated documentation and tooltips."
        );
        docDescription.setWrapText(true);
        docDescription.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        VBox.setVgrow(docArea, Priority.ALWAYS);

        // AppInfo section
        Label appInfoLabel = new Label("Application Info:");
        appInfoLabel.setStyle("-fx-font-weight: bold;");

        TextArea appInfoArea = new TextArea();
        appInfoArea.setPrefRowCount(3);
        appInfoArea.setWrapText(true);
        appInfoArea.setPromptText("Enter application-specific information...");

        // Load existing appinfo
        String existingAppInfo = simpleType.getAppinfoAsString();
        if (existingAppInfo != null && !existingAppInfo.isEmpty()) {
            appInfoArea.setText(existingAppInfo);
        }

        // Listen to changes
        appInfoArea.textProperty().addListener((obs, oldVal, newVal) -> {
            logger.debug("AppInfo changed");
            simpleType.setAppinfoFromString(newVal);

            // Trigger change callback
            if (onChangeCallback != null) {
                onChangeCallback.run();
            }
        });

        Label appInfoDescription = new Label(
                "AppInfo contains application-specific metadata.\n" +
                "This can include validation rules, UI hints, or other custom information."
        );
        appInfoDescription.setWrapText(true);
        appInfoDescription.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        panel.getChildren().addAll(
                title,
                new Label(""),
                docLabel,
                docArea,
                docDescription,
                new Label(""),
                appInfoLabel,
                appInfoArea,
                appInfoDescription
        );

        return panel;
    }
}
