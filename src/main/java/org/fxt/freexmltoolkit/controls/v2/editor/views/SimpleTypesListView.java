package org.fxt.freexmltoolkit.controls.v2.editor.views;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;
import java.util.function.Consumer;

/**
 * View showing a list of all SimpleTypes in the schema.
 * TableView with filter, sort, preview, and actions.
 *
 * Phase 4 Implementation - Real functionality with schema integration
 *
 * @since 2.0
 */
public class SimpleTypesListView extends BorderPane {

    private static final Logger logger = LogManager.getLogger(SimpleTypesListView.class);

    private final XsdSchema schema;

    // Callbacks for actions
    private Consumer<XsdSimpleType> onEditType;
    private Consumer<XsdSimpleType> onDuplicateType;
    private Consumer<XsdSimpleType> onDeleteType;
    private Consumer<XsdSimpleType> onFindUsage;
    private Runnable onAddType;

    // UI Components
    private TextField filterField;
    private ComboBox<String> sortCombo;
    private TableView<SimpleTypeRow> tableView;
    private TextArea previewArea;
    private ToolBar actionToolbar;

    // Data
    private ObservableList<SimpleTypeRow> allData;
    private FilteredList<SimpleTypeRow> filteredData;
    private SortedList<SimpleTypeRow> sortedData;

    /**
     * Creates a new SimpleTypes list view.
     *
     * @param schema the schema to display types from
     */
    public SimpleTypesListView(XsdSchema schema) {
        this.schema = schema;
        initializeUI();
        loadDataFromSchema();
    }

    /**
     * Sets the callback for editing a type.
     *
     * @param callback the callback
     */
    public void setOnEditType(Consumer<XsdSimpleType> callback) {
        this.onEditType = callback;
    }

    /**
     * Sets the callback for duplicating a type.
     *
     * @param callback the callback
     */
    public void setOnDuplicateType(Consumer<XsdSimpleType> callback) {
        this.onDuplicateType = callback;
    }

    /**
     * Sets the callback for deleting a type.
     *
     * @param callback the callback
     */
    public void setOnDeleteType(Consumer<XsdSimpleType> callback) {
        this.onDeleteType = callback;
    }

    /**
     * Sets the callback for finding usage.
     *
     * @param callback the callback
     */
    public void setOnFindUsage(Consumer<XsdSimpleType> callback) {
        this.onFindUsage = callback;
    }

    /**
     * Sets the callback for adding a new type.
     *
     * @param callback the callback
     */
    public void setOnAddType(Runnable callback) {
        this.onAddType = callback;
    }

    /**
     * Initializes the UI components.
     * Phase 4: Real implementation with data binding
     */
    private void initializeUI() {
        setPadding(new Insets(10));

        // Top: Title and Add button
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        Label title = new Label("SimpleTypes Overview");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("Add SimpleType");
        addBtn.setGraphic(new FontIcon(BootstrapIcons.PLUS_CIRCLE));
        addBtn.setTooltip(new Tooltip("Add new SimpleType (Ctrl+N)"));
        addBtn.setStyle("-fx-font-weight: bold;");
        addBtn.setOnAction(e -> handleAddType());

        topBar.getChildren().addAll(title, spacer, addBtn);
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
    }

    /**
     * Creates the filter and sort bar.
     */
    private HBox createFilterBar() {
        HBox filterBar = new HBox(15);
        filterBar.setPadding(new Insets(5));

        Label filterLabel = new Label("🔍");
        filterField = new TextField();
        filterField.setPromptText("Filter by name...");
        filterField.setPrefWidth(250);
        filterField.setTooltip(new Tooltip("Filter types by name (Ctrl+F)"));

        // Wire up filter
        filterField.textProperty().addListener((obs, oldVal, newVal) -> {
            applyFilter(newVal);
        });

        Label sortLabel = new Label("Sort by:");
        sortCombo = new ComboBox<>();
        sortCombo.getItems().addAll("Name", "Base Type", "Usage Count");
        sortCombo.setValue("Name");

        // Wire up sort
        sortCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            applySort(newVal);
        });

        filterBar.getChildren().addAll(filterLabel, filterField, sortLabel, sortCombo);

        return filterBar;
    }

    /**
     * Creates the table view.
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
            private final Button editBtn = new Button();
            private final Button delBtn = new Button();

            {
                editBtn.setGraphic(new FontIcon(BootstrapIcons.PENCIL));
                editBtn.setTooltip(new Tooltip("Edit type (Double-click)"));
                editBtn.setOnAction(e -> {
                    SimpleTypeRow row = getTableView().getItems().get(getIndex());
                    handleEditType(row.getSimpleType());
                });

                delBtn.setGraphic(new FontIcon(BootstrapIcons.TRASH));
                delBtn.setTooltip(new Tooltip("Delete type (Delete key)"));
                delBtn.setStyle("-fx-text-fill: red;");
                delBtn.setOnAction(e -> {
                    SimpleTypeRow row = getTableView().getItems().get(getIndex());
                    handleDeleteType(row.getSimpleType());
                });
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

        // Double-click to edit
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                SimpleTypeRow selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    handleEditType(selected.getSimpleType());
                }
            }
        });

        // Selection listener for preview
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updatePreview(newVal.getSimpleType());
            }
        });

        return table;
    }

    /**
     * Creates the preview panel.
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
     */
    private ToolBar createActionToolbar() {
        ToolBar toolbar = new ToolBar();

        Button editBtn = new Button("Edit Selected");
        editBtn.setGraphic(new FontIcon(BootstrapIcons.PENCIL_SQUARE));
        editBtn.setTooltip(new Tooltip("Edit selected type (Enter)"));
        editBtn.setOnAction(e -> {
            SimpleTypeRow selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleEditType(selected.getSimpleType());
            }
        });

        Button duplicateBtn = new Button("Duplicate");
        duplicateBtn.setGraphic(new FontIcon(BootstrapIcons.FILES));
        duplicateBtn.setTooltip(new Tooltip("Duplicate selected type (Ctrl+D)"));
        duplicateBtn.setOnAction(e -> {
            SimpleTypeRow selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleDuplicateType(selected.getSimpleType());
            }
        });

        Button findUsageBtn = new Button("Find Usage");
        findUsageBtn.setGraphic(new FontIcon(BootstrapIcons.SEARCH));
        findUsageBtn.setTooltip(new Tooltip("Find where this type is used (Ctrl+U)"));
        findUsageBtn.setOnAction(e -> {
            SimpleTypeRow selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleFindUsage(selected.getSimpleType());
            }
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.setGraphic(new FontIcon(BootstrapIcons.TRASH));
        deleteBtn.setTooltip(new Tooltip("Delete selected type (Delete key)"));
        deleteBtn.setStyle("-fx-text-fill: red;");
        deleteBtn.setOnAction(e -> {
            SimpleTypeRow selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                handleDeleteType(selected.getSimpleType());
            }
        });

        // Enable/disable based on selection
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editBtn.setDisable(!hasSelection);
            duplicateBtn.setDisable(!hasSelection);
            findUsageBtn.setDisable(!hasSelection);
            deleteBtn.setDisable(!hasSelection);
        });

        // Initially disabled
        editBtn.setDisable(true);
        duplicateBtn.setDisable(true);
        findUsageBtn.setDisable(true);
        deleteBtn.setDisable(true);

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
     * Loads data from the schema.
     */
    private void loadDataFromSchema() {
        allData = FXCollections.observableArrayList();

        // Find all SimpleTypes in schema
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType) {
                XsdSimpleType simpleType = (XsdSimpleType) child;
                allData.add(createRowFromSimpleType(simpleType));
            }
        }

        logger.info("Loaded {} SimpleTypes from schema", allData.size());

        // Setup filtered and sorted lists
        filteredData = new FilteredList<>(allData, p -> true);
        sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(tableView.comparatorProperty());

        tableView.setItems(sortedData);

        // Apply initial sort
        applySort("Name");
    }

    /**
     * Creates a row from a SimpleType.
     *
     * @param simpleType the simple type
     * @return the row
     */
    private SimpleTypeRow createRowFromSimpleType(XsdSimpleType simpleType) {
        String name = simpleType.getName();
        String baseType = extractBaseType(simpleType);
        String facets = extractFacets(simpleType);
        int usage = calculateUsage(simpleType);

        return new SimpleTypeRow(name, baseType, facets, usage, simpleType);
    }

    /**
     * Extracts the base type from a SimpleType.
     *
     * @param simpleType the simple type
     * @return the base type string
     */
    private String extractBaseType(XsdSimpleType simpleType) {
        // Check for restriction
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction) {
                XsdRestriction restriction = (XsdRestriction) child;
                return restriction.getBase() != null ? restriction.getBase() : "xs:string";
            }
            if (child instanceof XsdList) {
                return "List";
            }
            if (child instanceof XsdUnion) {
                return "Union";
            }
        }
        return simpleType.getBase() != null ? simpleType.getBase() : "-";
    }

    /**
     * Extracts facets from a SimpleType.
     *
     * @param simpleType the simple type
     * @return facets string
     */
    private String extractFacets(XsdSimpleType simpleType) {
        StringBuilder facets = new StringBuilder();

        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction) {
                XsdRestriction restriction = (XsdRestriction) child;
                for (XsdFacet facet : restriction.getFacets()) {
                    if (facets.length() > 0) facets.append(", ");
                    facets.append(facet.getFacetType().toString());
                }
            }
        }

        return facets.length() > 0 ? facets.toString() : "-";
    }

    /**
     * Calculates usage count for a SimpleType.
     *
     * @param simpleType the simple type
     * @return usage count
     */
    private int calculateUsage(XsdSimpleType simpleType) {
        // TODO Phase 5: Implement actual usage finder
        // For now return 0
        return 0;
    }

    /**
     * Applies filter to the table.
     *
     * @param filterText the filter text
     */
    private void applyFilter(String filterText) {
        if (filterText == null || filterText.trim().isEmpty()) {
            filteredData.setPredicate(p -> true);
        } else {
            String lowerCaseFilter = filterText.toLowerCase();
            filteredData.setPredicate(row ->
                    row.getName().toLowerCase().contains(lowerCaseFilter)
            );
        }
        logger.debug("Applied filter '{}', showing {}/{} types", filterText, filteredData.size(), allData.size());
    }

    /**
     * Applies sort to the table.
     *
     * @param sortBy the sort criterion
     */
    private void applySort(String sortBy) {
        Comparator<SimpleTypeRow> comparator;

        switch (sortBy) {
            case "Base Type":
                comparator = Comparator.comparing(SimpleTypeRow::getBaseType);
                break;
            case "Usage Count":
                comparator = Comparator.comparingInt(SimpleTypeRow::getUsage).reversed();
                break;
            case "Name":
            default:
                comparator = Comparator.comparing(SimpleTypeRow::getName);
                break;
        }

        sortedData.setComparator(comparator);
        logger.debug("Applied sort by '{}'", sortBy);
    }

    /**
     * Updates the preview area with XSD for the selected type.
     *
     * @param simpleType the simple type
     */
    private void updatePreview(XsdSimpleType simpleType) {
        // TODO Phase 4: Use XsdSerializer for real XSD output
        // For now, generate simple XSD preview
        StringBuilder xsd = new StringBuilder();
        xsd.append("<xs:simpleType name=\"").append(simpleType.getName()).append("\">\n");

        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction) {
                XsdRestriction restriction = (XsdRestriction) child;
                xsd.append("  <xs:restriction base=\"").append(restriction.getBase()).append("\">\n");
                for (XsdFacet facet : restriction.getFacets()) {
                    xsd.append("    <xs:").append(facet.getFacetType().toString().toLowerCase())
                            .append(" value=\"").append(facet.getValue()).append("\"/>\n");
                }
                xsd.append("  </xs:restriction>\n");
            }
        }

        xsd.append("</xs:simpleType>");

        previewArea.setText(xsd.toString());
    }

    // Action handlers

    private void handleAddType() {
        logger.info("Add type clicked");
        if (onAddType != null) {
            onAddType.run();
        }
    }

    private void handleEditType(XsdSimpleType simpleType) {
        logger.info("Edit type: {}", simpleType.getName());
        if (onEditType != null) {
            onEditType.accept(simpleType);
        }
    }

    private void handleDuplicateType(XsdSimpleType simpleType) {
        logger.info("Duplicate type: {}", simpleType.getName());
        if (onDuplicateType != null) {
            onDuplicateType.accept(simpleType);
        }
    }

    private void handleDeleteType(XsdSimpleType simpleType) {
        logger.info("Delete type: {}", simpleType.getName());
        if (onDeleteType != null) {
            onDeleteType.accept(simpleType);
        }
    }

    private void handleFindUsage(XsdSimpleType simpleType) {
        logger.info("Find usage: {}", simpleType.getName());
        if (onFindUsage != null) {
            onFindUsage.accept(simpleType);
        }
    }

    /**
     * Refreshes the data from schema.
     */
    public void refresh() {
        loadDataFromSchema();
    }

    /**
     * Row model for the table.
     */
    public static class SimpleTypeRow {
        private final String name;
        private final String baseType;
        private final String facets;
        private final int usage;
        private final XsdSimpleType simpleType;

        public SimpleTypeRow(String name, String baseType, String facets, int usage, XsdSimpleType simpleType) {
            this.name = name;
            this.baseType = baseType;
            this.facets = facets;
            this.usage = usage;
            this.simpleType = simpleType;
        }

        public String getName() { return name; }
        public String getBaseType() { return baseType; }
        public String getFacets() { return facets; }
        public int getUsage() { return usage; }
        public XsdSimpleType getSimpleType() { return simpleType; }
    }
}
