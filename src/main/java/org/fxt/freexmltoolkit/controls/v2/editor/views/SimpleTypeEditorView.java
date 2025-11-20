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

    // UI Components
    private ToolBar toolbar;
    private TabPane tabPane;

    // Restriction Panel Components
    private ComboBox<String> baseTypeCombo;
    private FacetsPanel facetsPanel;

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
     * Sets the callback to be called when changes are detected.
     * Used by the parent tab to set dirty flag.
     *
     * @param callback the callback to run on change
     */
    public void setOnChangeCallback(Runnable callback) {
        this.onChangeCallback = callback;
    }

    /**
     * Creates the toolbar.
     * DUMMY: Placeholder buttons
     */
    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();

        Button saveBtn = new Button("💾 Save Type");
        saveBtn.setDisable(true); // DUMMY

        Button closeBtn = new Button("✕ Close");
        closeBtn.setDisable(true); // DUMMY

        Button findUsageBtn = new Button("🔍 Find Usage");
        findUsageBtn.setDisable(true); // DUMMY

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

        // Name field (read-only - name cannot be changed for existing types)
        Label nameLabel = new Label("Name:");
        TextField nameField = new TextField(simpleType.getName());
        nameField.setEditable(false);
        nameField.setStyle("-fx-background-color: #f0f0f0;");
        nameField.setTooltip(new Tooltip("Type name cannot be changed after creation"));

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
     * Real implementation with FacetsPanel integration.
     */
    private VBox createRestrictionPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("Restriction");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Base Type selector
        Label baseLabel = new Label("Base Type:");
        baseTypeCombo = new ComboBox<>();

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

        // FacetsPanel integration
        Label facetsLabel = new Label("Facets:");
        facetsLabel.setStyle("-fx-font-weight: bold;");

        facetsPanel = new FacetsPanel(editorContext);

        // Load current restriction into FacetsPanel
        if (currentRestriction != null) {
            facetsPanel.setRestriction(currentRestriction);
            logger.debug("Loaded restriction with base '{}' and {} facets",
                    currentRestriction.getBase(), currentRestriction.getFacets().size());
        } else {
            logger.debug("No restriction found in SimpleType, FacetsPanel disabled");
        }

        VBox.setVgrow(facetsPanel, Priority.ALWAYS);

        panel.getChildren().addAll(
                title,
                new Label(""),
                baseLabel, baseTypeCombo,
                new Label(""),
                facetsLabel,
                facetsPanel
        );

        return panel;
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
        addBtn.setOnAction(e -> handleAddMemberType(memberTypesList));

        Button removeBtn = new Button("Remove");
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
