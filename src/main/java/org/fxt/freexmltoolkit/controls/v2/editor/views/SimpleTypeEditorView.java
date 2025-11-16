package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;

/**
 * Main view for editing a SimpleType.
 * Shows tabbed panels: General, Restriction, List, Union, Annotation
 *
 * DUMMY IMPLEMENTATION - Phase 0
 * This shows placeholder content matching the mockups.
 *
 * @since 2.0
 */
public class SimpleTypeEditorView extends BorderPane {

    private final XsdSimpleType simpleType;

    // UI Components (DUMMY)
    private ToolBar toolbar;
    private TabPane tabPane;

    /**
     * Creates a new SimpleType editor view.
     *
     * @param simpleType the simple type to edit
     */
    public SimpleTypeEditorView(XsdSimpleType simpleType) {
        this.simpleType = simpleType;
        initializeUI();
    }

    /**
     * Initializes the UI components.
     * DUMMY: Shows placeholder layout
     */
    private void initializeUI() {
        // Top: Toolbar
        toolbar = createToolbar();
        setTop(toolbar);

        // Center: Tab Pane with 5 tabs
        tabPane = createTabPane();
        setCenter(tabPane);

        // TODO Phase 3:
        // - Implement real General panel
        // - Wire up FacetsPanel for Restriction
        // - Implement List panel
        // - Implement Union panel
        // - Implement Annotation panel
        // - Connect to model
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
     * DUMMY: Shows name and final checkboxes
     */
    private VBox createGeneralPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("General Properties");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        Label nameLabel = new Label("Name:");
        TextField nameField = new TextField(simpleType.getName());
        nameField.setDisable(true); // DUMMY

        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);

        Label finalLabel = new Label("Final:");
        HBox finalBox = new HBox(10);
        CheckBox finalRestriction = new CheckBox("restriction");
        CheckBox finalList = new CheckBox("list");
        CheckBox finalUnion = new CheckBox("union");
        finalRestriction.setDisable(true); // DUMMY
        finalList.setDisable(true); // DUMMY
        finalUnion.setDisable(true); // DUMMY
        finalBox.getChildren().addAll(finalRestriction, finalList, finalUnion);

        grid.add(finalLabel, 0, 1);
        grid.add(finalBox, 1, 1);

        Label dummyNote = new Label("(Dummy Panel - Phase 0)");
        dummyNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");

        panel.getChildren().addAll(title, grid, new Label(""), dummyNote);

        return panel;
    }

    /**
     * Creates the Restriction panel.
     * DUMMY: Placeholder for FacetsPanel integration
     */
    private VBox createRestrictionPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("Restriction");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label baseLabel = new Label("Base Type:");
        ComboBox<String> baseCombo = new ComboBox<>();
        baseCombo.getItems().addAll("xs:string", "xs:int", "xs:decimal", "xs:date");
        baseCombo.setValue("xs:string");
        baseCombo.setDisable(true); // DUMMY

        Label facetsLabel = new Label("Facets:");
        facetsLabel.setStyle("-fx-font-weight: bold;");

        // Placeholder for FacetsPanel
        VBox facetsPlaceholder = new VBox(10);
        facetsPlaceholder.setStyle("-fx-border-color: #dee2e6; -fx-padding: 10;");
        facetsPlaceholder.setAlignment(Pos.CENTER);
        Label facetsNote = new Label("FacetsPanel will be integrated here");
        facetsNote.setStyle("-fx-font-style: italic; -fx-text-fill: #6c757d;");
        facetsPlaceholder.getChildren().add(facetsNote);

        Label dummyNote = new Label("(Dummy Panel - Phase 0)");
        dummyNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");

        panel.getChildren().addAll(
                title,
                new Label(""),
                baseLabel, baseCombo,
                new Label(""),
                facetsLabel,
                facetsPlaceholder,
                new Label(""),
                dummyNote
        );

        return panel;
    }

    /**
     * Creates the List panel.
     * DUMMY: Placeholder for list item type selector
     */
    private VBox createListPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("List");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label itemTypeLabel = new Label("Item Type:");
        ComboBox<String> itemTypeCombo = new ComboBox<>();
        itemTypeCombo.setDisable(true); // DUMMY

        Label description = new Label(
                "A list creates space-separated values of the specified item type.\n" +
                "Example: \"DE AT CH FR\" (list of country codes)"
        );
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        Label dummyNote = new Label("(Dummy Panel - Phase 0)");
        dummyNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");

        panel.getChildren().addAll(
                title,
                new Label(""),
                itemTypeLabel, itemTypeCombo,
                new Label(""),
                description,
                new Label(""),
                dummyNote
        );

        return panel;
    }

    /**
     * Creates the Union panel.
     * DUMMY: Placeholder for member types selector
     */
    private VBox createUnionPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("Union");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label memberTypesLabel = new Label("Member Types:");
        ListView<String> memberTypesList = new ListView<>();
        memberTypesList.setPrefHeight(150);
        memberTypesList.setDisable(true); // DUMMY

        Button addBtn = new Button("Add Member Type");
        Button removeBtn = new Button("Remove");
        addBtn.setDisable(true); // DUMMY
        removeBtn.setDisable(true); // DUMMY

        HBox buttonsBox = new HBox(10, addBtn, removeBtn);

        Label description = new Label(
                "A union allows values from any of the specified member types.\n" +
                "Example: Value can be either string OR decimal."
        );
        description.setWrapText(true);
        description.setStyle("-fx-font-size: 11px; -fx-text-fill: #6c757d;");

        Label dummyNote = new Label("(Dummy Panel - Phase 0)");
        dummyNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");

        panel.getChildren().addAll(
                title,
                new Label(""),
                memberTypesLabel,
                memberTypesList,
                buttonsBox,
                new Label(""),
                description,
                new Label(""),
                dummyNote
        );

        return panel;
    }

    /**
     * Creates the Annotation panel.
     * DUMMY: Placeholder for documentation and appinfo
     */
    private VBox createAnnotationPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label title = new Label("Annotation");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label docLabel = new Label("Documentation:");
        TextArea docArea = new TextArea();
        docArea.setPrefRowCount(5);
        docArea.setWrapText(true);
        docArea.setDisable(true); // DUMMY

        Label appInfoLabel = new Label("AppInfo:");
        TextArea appInfoArea = new TextArea();
        appInfoArea.setPrefRowCount(3);
        appInfoArea.setWrapText(true);
        appInfoArea.setDisable(true); // DUMMY

        Label dummyNote = new Label("(Dummy Panel - Phase 0)");
        dummyNote.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");

        panel.getChildren().addAll(
                title,
                new Label(""),
                docLabel, docArea,
                new Label(""),
                appInfoLabel, appInfoArea,
                new Label(""),
                dummyNote
        );

        return panel;
    }
}
