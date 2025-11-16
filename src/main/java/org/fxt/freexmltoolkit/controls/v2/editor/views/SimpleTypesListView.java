package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * View showing a list of all SimpleTypes in the schema.
 * TableView with filter, sort, preview, and actions.
 *
 * DUMMY IMPLEMENTATION - Phase 0
 * This shows placeholder content matching the mockups.
 *
 * @since 2.0
 */
public class SimpleTypesListView extends BorderPane {

    // UI Components (DUMMY)
    private TextField filterField;
    private ComboBox<String> sortCombo;
    private TableView<SimpleTypeRow> tableView;
    private TextArea previewArea;
    private ToolBar actionToolbar;

    /**
     * Creates a new SimpleTypes list view.
     */
    public SimpleTypesListView() {
        initializeUI();
        loadDummyData();
    }

    /**
     * Initializes the UI components.
     * DUMMY: Shows placeholder layout
     */
    private void initializeUI() {
        setPadding(new Insets(10));

        // Top: Title and Add button
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        Label title = new Label("SimpleTypes Overview");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Button addBtn = new Button("+ Add SimpleType");
        addBtn.setDisable(true); // DUMMY

        topBar.getChildren().addAll(title, new Label(" ".repeat(50)), addBtn);
        setTop(topBar);

        // Center: Main content (table + preview)
        VBox centerBox = new VBox(10);
        centerBox.setPadding(new Insets(10));

        // Filter and sort bar
        HBox filterBar = createFilterBar();

        // Table
        tableView = createTable();

        // Preview panel
        VBox previewPanel = createPreviewPanel();

        centerBox.getChildren().addAll(filterBar, tableView, previewPanel);
        setCenter(centerBox);

        // Bottom: Action toolbar
        actionToolbar = createActionToolbar();
        setBottom(actionToolbar);

        // TODO Phase 4:
        // - Wire up filter functionality
        // - Wire up sort functionality
        // - Load real data from schema
        // - Update preview on selection
        // - Wire up action buttons
        // - Implement double-click to open editor
    }

    /**
     * Creates the filter and sort bar.
     * DUMMY: Disabled controls
     */
    private HBox createFilterBar() {
        HBox filterBar = new HBox(15);
        filterBar.setPadding(new Insets(5));

        Label filterLabel = new Label("🔍");
        filterField = new TextField();
        filterField.setPromptText("Filter by name...");
        filterField.setPrefWidth(250);
        filterField.setDisable(true); // DUMMY

        Label sortLabel = new Label("Sort by:");
        sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Name", "Base Type", "Usage Count");
        sortCombo.setValue("Name");
        sortCombo.setDisable(true); // DUMMY

        filterBar.getChildren().addAll(filterLabel, filterField, sortLabel, sortCombo);

        return filterBar;
    }

    /**
     * Creates the table view.
     * DUMMY: Sample columns and data
     */
    private TableView<SimpleTypeRow> createTable() {
        TableView<SimpleTypeRow> table = new TableView<>();

        // Columns
        TableColumn<SimpleTypeRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);

        TableColumn<SimpleTypeRow, String> baseTypeCol = new TableColumn<>("Base Type");
        baseTypeCol.setCellValueFactory(new PropertyValueFactory<>("baseType"));
        baseTypeCol.setPrefWidth(150);

        TableColumn<SimpleTypeRow, String> facetsCol = new TableColumn<>("Facets");
        facetsCol.setCellValueFactory(new PropertyValueFactory<>("facets"));
        facetsCol.setPrefWidth(200);

        TableColumn<SimpleTypeRow, Integer> usageCol = new TableColumn<>("Usage");
        usageCol.setCellValueFactory(new PropertyValueFactory<>("usage"));
        usageCol.setPrefWidth(80);

        TableColumn<SimpleTypeRow, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(150);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button delBtn = new Button("Del");

            {
                editBtn.setDisable(true); // DUMMY
                delBtn.setDisable(true); // DUMMY
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(5, editBtn, delBtn);
                    setGraphic(buttons);
                }
            }
        });

        table.getColumns().addAll(nameCol, baseTypeCol, facetsCol, usageCol, actionsCol);
        table.setPrefHeight(300);

        return table;
    }

    /**
     * Creates the preview panel.
     * DUMMY: Placeholder text area
     */
    private VBox createPreviewPanel() {
        VBox panel = new VBox(5);
        panel.setPadding(new Insets(10));

        Label title = new Label("Preview (XSD)");
        title.setStyle("-fx-font-weight: bold;");

        previewArea = new TextArea();
        previewArea.setPrefRowCount(8);
        previewArea.setEditable(false);
        previewArea.setStyle("-fx-font-family: monospace;");
        previewArea.setText("<!-- Select a SimpleType to see XSD preview -->");

        panel.getChildren().addAll(title, previewArea);

        return panel;
    }

    /**
     * Creates the action toolbar.
     * DUMMY: Disabled buttons
     */
    private ToolBar createActionToolbar() {
        ToolBar toolbar = new ToolBar();

        Button editBtn = new Button("Edit Selected");
        Button duplicateBtn = new Button("Duplicate");
        Button findUsageBtn = new Button("Find Usage");
        Button deleteBtn = new Button("Delete");

        editBtn.setDisable(true); // DUMMY
        duplicateBtn.setDisable(true); // DUMMY
        findUsageBtn.setDisable(true); // DUMMY
        deleteBtn.setDisable(true); // DUMMY

        toolbar.getItems().addAll(
                editBtn,
                duplicateBtn,
                new Separator(),
                findUsageBtn,
                new Separator(),
                deleteBtn
        );

        return toolbar;
    }

    /**
     * Loads dummy data into the table.
     * DUMMY: Sample data for visualization
     */
    private void loadDummyData() {
        ObservableList<SimpleTypeRow> data = FXCollections.observableArrayList(
                new SimpleTypeRow("📄 BicCodeType", "xs:string", "minL, maxL", 12),
                new SimpleTypeRow("📄 EmailAddressType", "xs:string", "pattern", 45),
                new SimpleTypeRow("📄 FrequencyType", "xs:string", "enumeration", 23),
                new SimpleTypeRow("📄 ISINType", "xs:string", "length, pattern", 156),
                new SimpleTypeRow("📄 ISOCountryCodeType", "xs:string", "minL, maxL", 289),
                new SimpleTypeRow("📄 ISOCurrencyCodeType", "xs:string", "minL, maxL, pattern", 178),
                new SimpleTypeRow("📄 ISOLanguageCodeType", "xs:string", "minL, maxL", 67),
                new SimpleTypeRow("📄 LEICodeType", "xs:string", "pattern", 34),
                new SimpleTypeRow("📄 MICCodeType", "xs:string", "length", 89),
                new SimpleTypeRow("📄 PercentageType", "xs:decimal", "min, max", 234),
                new SimpleTypeRow("📄 Text16Type", "xs:string", "maxLength", 45),
                new SimpleTypeRow("📄 Text256Type", "xs:string", "maxLength", 567),
                new SimpleTypeRow("📄 Text32Type", "xs:string", "maxLength", 123),
                new SimpleTypeRow("📄 Text64Type", "xs:string", "maxLength", 89)
        );

        tableView.setItems(data);

        // Add selection listener for preview (DUMMY)
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // DUMMY: Show sample XSD
                previewArea.setText(
                        "<xs:simpleType name=\"" + newVal.getName().replace("📄 ", "") + "\">\n" +
                        "  <xs:restriction base=\"" + newVal.getBaseType() + "\">\n" +
                        "    <!-- Facets: " + newVal.getFacets() + " -->\n" +
                        "  </xs:restriction>\n" +
                        "</xs:simpleType>"
                );
            }
        });
    }

    /**
     * Row model for the table.
     * DUMMY: Simple data class
     */
    public static class SimpleTypeRow {
        private final String name;
        private final String baseType;
        private final String facets;
        private final int usage;

        public SimpleTypeRow(String name, String baseType, String facets, int usage) {
            this.name = name;
            this.baseType = baseType;
            this.facets = facets;
            this.usage = usage;
        }

        public String getName() { return name; }
        public String getBaseType() { return baseType; }
        public String getFacets() { return facets; }
        public int getUsage() { return usage; }
    }
}
