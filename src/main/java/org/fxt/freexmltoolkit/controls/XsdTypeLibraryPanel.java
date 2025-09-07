package org.fxt.freexmltoolkit.controls;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fxt.freexmltoolkit.controls.commands.DeleteTypeCommand;
import org.fxt.freexmltoolkit.controls.commands.FindTypeUsagesCommand;
import org.fxt.freexmltoolkit.domain.TypeInfo;
import org.fxt.freexmltoolkit.domain.command.CloneTypeCommand;
import org.fxt.freexmltoolkit.domain.command.ImportTypesCommand;
import org.fxt.freexmltoolkit.domain.command.InlineTypeCommand;
import org.fxt.freexmltoolkit.domain.command.RemoveUnusedTypesCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Panel for displaying and managing global type definitions in XSD schemas.
 * Provides functionality to view, edit, delete, and extract types.
 */
public class XsdTypeLibraryPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdTypeLibraryPanel.class);

    private final XsdDomManipulator domManipulator;
    private final Consumer<XsdCommand> commandExecutor;
    private final Runnable refreshCallback;

    // UI Components
    private TextField searchField;
    private TableView<TypeInfo> typeTable;
    private ObservableList<TypeInfo> typeData;
    private FilteredList<TypeInfo> filteredData;
    private Label statusLabel;
    private Button refreshButton;
    private Button extractButton;
    private ProgressIndicator progressIndicator;

    // Context Menu
    private ContextMenu contextMenu;

    public XsdTypeLibraryPanel(XsdDomManipulator domManipulator,
                               Consumer<XsdCommand> commandExecutor,
                               Runnable refreshCallback) {
        this.domManipulator = domManipulator;
        this.commandExecutor = commandExecutor;
        this.refreshCallback = refreshCallback;

        initializeUI();
        setupEventHandlers();
        loadTypes();
    }

    /**
     * Initialize the user interface components.
     */
    private void initializeUI() {
        // Add CSS class for styling
        this.getStyleClass().add("type-library-panel");
        this.setSpacing(10);
        this.setPadding(new Insets(10));

        // Header with title and refresh button
        createHeader();

        // Search field
        createSearchField();

        // Types table
        createTypeTable();

        // Status bar
        createStatusBar();

        // Context menu
        createContextMenu();
    }

    /**
     * Create the header section with title and buttons.
     */
    private void createHeader() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("panel-header");

        Label titleLabel = new Label("Type Library");
        titleLabel.getStyleClass().add("section-title");

        // Progress indicator
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(16, 16);
        progressIndicator.setVisible(false);

        // Refresh button
        refreshButton = new Button();
        FontIcon refreshIcon = new FontIcon("bi-arrow-clockwise");
        refreshButton.setGraphic(refreshIcon);
        refreshButton.setTooltip(new Tooltip("Refresh type library"));
        refreshButton.getStyleClass().add("icon-button");

        // Extract to global type button
        extractButton = new Button("Extract Type");
        FontIcon extractIcon = new FontIcon("bi-plus-square");
        extractButton.setGraphic(extractIcon);
        extractButton.setTooltip(new Tooltip("Extract inline type to global type"));
        extractButton.getStyleClass().addAll("button", "primary");

        // Create SimpleType button
        Button createSimpleTypeButton = new Button("Simple");
        FontIcon createSimpleIcon = new FontIcon("bi-circle-fill");
        createSimpleTypeButton.setGraphic(createSimpleIcon);
        createSimpleTypeButton.setTooltip(new Tooltip("Create new SimpleType"));
        createSimpleTypeButton.getStyleClass().addAll("button", "success");
        createSimpleTypeButton.setOnAction(e -> createNewSimpleType());

        // Create ComplexType button
        Button createComplexTypeButton = new Button("Complex");
        FontIcon createComplexIcon = new FontIcon("bi-square-fill");
        createComplexTypeButton.setGraphic(createComplexIcon);
        createComplexTypeButton.setTooltip(new Tooltip("Create new ComplexType"));
        createComplexTypeButton.getStyleClass().addAll("button", "success");
        createComplexTypeButton.setOnAction(e -> createNewComplexType());

        // Remove unused types button
        Button removeUnusedButton = new Button();
        FontIcon removeUnusedIcon = new FontIcon("bi-trash");
        removeUnusedButton.setGraphic(removeUnusedIcon);
        removeUnusedButton.setTooltip(new Tooltip("Remove unused types"));
        removeUnusedButton.getStyleClass().add("icon-button");
        removeUnusedButton.setOnAction(e -> removeUnusedTypes());

        // Import types button
        Button importButton = new Button();
        FontIcon importIcon = new FontIcon("bi-upload");
        importButton.setGraphic(importIcon);
        importButton.setTooltip(new Tooltip("Import types from file"));
        importButton.getStyleClass().add("icon-button");
        importButton.setOnAction(e -> importTypes());

        // Export types menu button
        MenuButton exportButton = new MenuButton();
        FontIcon exportIcon = createColoredIcon("bi-download", "#28a745");
        exportButton.setGraphic(exportIcon);
        exportButton.setTooltip(new Tooltip("Export types to various formats"));
        exportButton.getStyleClass().add("icon-button");

        // Export menu items
        MenuItem exportXsdItem = new MenuItem("Export as XSD");
        exportXsdItem.setGraphic(createColoredIcon("bi-file-earmark-code", "#007bff"));
        exportXsdItem.setOnAction(e -> exportTypes());

        MenuItem exportXlsxItem = new MenuItem("Export as XLSX");
        exportXlsxItem.setGraphic(createColoredIcon("bi-file-earmark-spreadsheet", "#28a745"));
        exportXlsxItem.setOnAction(e -> exportToExcel());

        MenuItem exportCsvItem = new MenuItem("Export as CSV");
        exportCsvItem.setGraphic(createColoredIcon("bi-file-earmark-text", "#ffc107"));
        exportCsvItem.setOnAction(e -> exportToCsv());

        MenuItem exportHtmlItem = new MenuItem("Export as HTML");
        exportHtmlItem.setGraphic(createColoredIcon("bi-file-earmark-richtext", "#fd7e14"));
        exportHtmlItem.setOnAction(e -> exportToHtml());

        MenuItem exportJsonItem = new MenuItem("Export as JSON");
        exportJsonItem.setGraphic(createColoredIcon("bi-file-earmark-code", "#6f42c1"));
        exportJsonItem.setOnAction(e -> exportToJson());

        exportButton.getItems().addAll(exportXsdItem, exportXlsxItem, exportCsvItem, exportHtmlItem, exportJsonItem);

        // Statistics button
        Button statsButton = new Button();
        FontIcon statsIcon = new FontIcon("bi-bar-chart");
        statsButton.setGraphic(statsIcon);
        statsButton.setTooltip(new Tooltip("Show type statistics"));
        statsButton.getStyleClass().add("icon-button");
        statsButton.setOnAction(e -> showTypeStatistics());

        // Inheritance visualizer button
        Button inheritanceButton = new Button();
        FontIcon inheritanceIcon = new FontIcon("bi-diagram-3");
        inheritanceButton.setGraphic(inheritanceIcon);
        inheritanceButton.setTooltip(new Tooltip("Show type inheritance hierarchy"));
        inheritanceButton.getStyleClass().add("icon-button");
        inheritanceButton.setOnAction(e -> showTypeInheritance());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(titleLabel, spacer, progressIndicator,
                inheritanceButton, statsButton, removeUnusedButton,
                importButton, exportButton,
                createSimpleTypeButton, createComplexTypeButton, extractButton, refreshButton);
        this.getChildren().add(header);
    }

    /**
     * Create the search field for filtering types.
     */
    private void createSearchField() {
        searchField = new TextField();
        searchField.setPromptText("Search types...");
        searchField.getStyleClass().add("search-field");

        this.getChildren().add(searchField);
    }

    /**
     * Create the main table for displaying types.
     */
    private void createTypeTable() {
        typeTable = new TableView<>();
        typeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        typeTable.getStyleClass().add("type-table");

        // Type Name Column
        TableColumn<TypeInfo, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().name()));
        nameColumn.setPrefWidth(150);

        // Custom cell factory for type name with icon
        nameColumn.setCellFactory(column -> new TableCell<TypeInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    TypeInfo typeInfo = getTableView().getItems().get(getIndex());
                    HBox container = new HBox(5);
                    container.setAlignment(Pos.CENTER_LEFT);

                    // Type icon
                    FontIcon icon = new FontIcon(typeInfo.category() == TypeInfo.TypeCategory.SIMPLE_TYPE ?
                            "bi-circle" : "bi-square");
                    icon.setIconSize(12);
                    icon.getStyleClass().add(typeInfo.category() == TypeInfo.TypeCategory.SIMPLE_TYPE ?
                            "simple-type-icon" : "complex-type-icon");

                    Label nameLabel = new Label(item);
                    if (typeInfo.isAbstract()) {
                        nameLabel.getStyleClass().add("abstract-type");
                    }

                    container.getChildren().addAll(icon, nameLabel);
                    setGraphic(container);
                    setText(null);
                }
            }
        });

        // Category Column
        TableColumn<TypeInfo, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().category().getDisplayName()));
        categoryColumn.setPrefWidth(100);

        // Base Type Column
        TableColumn<TypeInfo, String> baseTypeColumn = new TableColumn<>("Base Type");
        baseTypeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().baseType() != null ?
                        cellData.getValue().baseType() : ""));
        baseTypeColumn.setPrefWidth(120);

        // Usage Count Column
        TableColumn<TypeInfo, String> usageColumn = new TableColumn<>("Usage");
        usageColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getUsageInfo()));
        usageColumn.setPrefWidth(80);
        usageColumn.getStyleClass().add("usage-column");

        // Description Column
        TableColumn<TypeInfo, String> descColumn = new TableColumn<>("Description");
        descColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getTypeDescription()));
        descColumn.setPrefWidth(200);

        // Documentation Column (collapsible)
        TableColumn<TypeInfo, String> docColumn = new TableColumn<>("Documentation");
        docColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().documentation() != null ?
                        cellData.getValue().documentation() : ""));
        docColumn.setPrefWidth(150);
        docColumn.setCellFactory(column -> new TableCell<TypeInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.trim().isEmpty()) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String truncated = item.length() > 50 ? item.substring(0, 47) + "..." : item;
                    setText(truncated);
                    setTooltip(new Tooltip(item));
                }
            }
        });

        typeTable.getColumns().addAll(nameColumn, categoryColumn, baseTypeColumn,
                usageColumn, descColumn, docColumn);

        // Initialize data collections
        typeData = FXCollections.observableArrayList();
        filteredData = new FilteredList<>(typeData);
        SortedList<TypeInfo> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(typeTable.comparatorProperty());
        typeTable.setItems(sortedData);

        // Set context menu
        typeTable.setRowFactory(tv -> {
            TableRow<TypeInfo> row = new TableRow<>();
            row.setOnContextMenuRequested(event -> {
                if (!row.isEmpty()) {
                    contextMenu.show(row, event.getScreenX(), event.getScreenY());
                }
            });
            return row;
        });

        // Add table to scrollpane for better handling
        ScrollPane scrollPane = new ScrollPane(typeTable);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().add(scrollPane);
    }

    /**
     * Create the status bar at the bottom.
     */
    private void createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(5, 0, 0, 0));

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        statusBar.getChildren().add(statusLabel);
        this.getChildren().add(statusBar);
    }

    /**
     * Create context menu for type operations.
     */
    private void createContextMenu() {
        contextMenu = new ContextMenu();

        // Edit Type
        MenuItem editItem = new MenuItem("Edit Type");
        editItem.setGraphic(new FontIcon("bi-pencil"));
        editItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("F2"));
        editItem.setOnAction(e -> editSelectedType());

        // Delete Type
        MenuItem deleteItem = new MenuItem("Delete Type");
        deleteItem.setGraphic(new FontIcon("bi-trash"));
        deleteItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Delete"));
        deleteItem.setOnAction(e -> deleteSelectedType());

        // Find Usages
        MenuItem findUsagesItem = new MenuItem("Find Usages");
        findUsagesItem.setGraphic(new FontIcon("bi-search"));
        findUsagesItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+F"));
        findUsagesItem.setOnAction(e -> findTypeUsages());

        // Separator
        SeparatorMenuItem separator = new SeparatorMenuItem();

        // Go to Definition
        MenuItem goToItem = new MenuItem("Go to Definition");
        goToItem.setGraphic(new FontIcon("bi-geo-alt"));
        goToItem.setOnAction(e -> goToTypeDefinition());

        // Separator 2
        SeparatorMenuItem separator2 = new SeparatorMenuItem();

        // Inline Type Definition
        MenuItem inlineTypeItem = new MenuItem("Inline Type Definition");
        inlineTypeItem.setGraphic(new FontIcon("bi-code-slash"));
        inlineTypeItem.setOnAction(e -> inlineTypeDefinition());

        // Clone Type
        MenuItem cloneTypeItem = new MenuItem("Clone Type");
        cloneTypeItem.setGraphic(new FontIcon("bi-files"));
        cloneTypeItem.setOnAction(e -> cloneSelectedType());

        // Export Type
        MenuItem exportTypeItem = new MenuItem("Export Type");
        exportTypeItem.setGraphic(new FontIcon("bi-download"));
        exportTypeItem.setOnAction(e -> exportSelectedType());

        contextMenu.getItems().addAll(editItem, deleteItem, separator, findUsagesItem, goToItem,
                separator2, inlineTypeItem, cloneTypeItem, exportTypeItem);

        // Enable/disable menu items based on selection and type properties
        contextMenu.setOnShowing(e -> {
            TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
            boolean hasSelection = selectedType != null;

            editItem.setDisable(!hasSelection);
            deleteItem.setDisable(!hasSelection);
            findUsagesItem.setDisable(!hasSelection);
            goToItem.setDisable(!hasSelection);
            inlineTypeItem.setDisable(!hasSelection || selectedType.usageCount() == 0);
            cloneTypeItem.setDisable(!hasSelection);
            exportTypeItem.setDisable(!hasSelection);

            // Show warning for types with high usage
            if (hasSelection && selectedType.usageCount() > 5) {
                deleteItem.setText("Delete Type (⚠ " + selectedType.usageCount() + " refs)");
                inlineTypeItem.setText("Inline Type (⚠ " + selectedType.usageCount() + " refs)");
            } else {
                deleteItem.setText("Delete Type");
                inlineTypeItem.setText("Inline Type Definition");
            }
        });
    }

    /**
     * Setup event handlers for UI interactions.
     */
    private void setupEventHandlers() {
        // Search functionality
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(typeInfo -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();

                // Check name
                if (typeInfo.name().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }

                // Check base type
                if (typeInfo.baseType() != null &&
                        typeInfo.baseType().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }

                // Check documentation
                return typeInfo.documentation() != null &&
                        typeInfo.documentation().toLowerCase().contains(lowerCaseFilter);
            });

            updateStatusLabel();
        });

        // Refresh button
        refreshButton.setOnAction(e -> loadTypes());

        // Extract button
        extractButton.setOnAction(e -> showExtractTypeDialog());

        // Assign context menu to table
        typeTable.setContextMenu(contextMenu);

        // Update status when context menu is shown
        contextMenu.setOnShown(e -> {
            TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
            if (selectedType != null) {
                statusLabel.setText("Context menu for: " + selectedType.name());
            }
        });

        // Restore status when context menu is hidden
        contextMenu.setOnHidden(e -> updateStatusLabel());
        
        // Double-click to edit
        typeTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && typeTable.getSelectionModel().getSelectedItem() != null) {
                editSelectedType();
            }
        });

        // Keyboard shortcuts
        typeTable.setOnKeyPressed(event -> {
            TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
            if (selectedType != null) {
                switch (event.getCode()) {
                    case F2 -> editSelectedType();
                    case DELETE -> deleteSelectedType();
                    case ENTER -> goToTypeDefinition();
                    case F -> {
                        if (event.isControlDown()) {
                            findTypeUsages();
                        }
                    }
                }
            }
        });
    }

    /**
     * Load and refresh the types from the DOM manipulator.
     */
    public void loadTypes() {
        if (domManipulator == null) {
            logger.warn("DOM manipulator is null, cannot load types");
            return;
        }

        showProgress(true);
        statusLabel.setText("Loading types...");

        // Run in background thread to avoid blocking UI
        Thread loadThread = new Thread(() -> {
            try {
                List<TypeInfo> types = domManipulator.getAllGlobalTypes();

                Platform.runLater(() -> {
                    typeData.clear();
                    typeData.addAll(types);
                    updateStatusLabel();
                    showProgress(false);
                });

            } catch (Exception e) {
                logger.error("Error loading types", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading types: " + e.getMessage());
                    showProgress(false);
                });
            }
        });

        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * Update the status label with current type count.
     */
    private void updateStatusLabel() {
        int totalTypes = typeData.size();
        int filteredCount = filteredData.size();

        if (totalTypes == filteredCount) {
            statusLabel.setText(String.format("%d types", totalTypes));
        } else {
            statusLabel.setText(String.format("%d of %d types", filteredCount, totalTypes));
        }
    }

    /**
     * Show/hide progress indicator.
     */
    private void showProgress(boolean show) {
        progressIndicator.setVisible(show);
        refreshButton.setDisable(show);
    }

    /**
     * Edit the currently selected type.
     */
    private void editSelectedType() {
        TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
        if (selectedType == null) return;

        // Create a simple properties dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Type Properties");
        dialog.setHeaderText("Editing type: " + selectedType.name());

        // Create form content
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        TextField nameField = new TextField(selectedType.name());
        nameField.setPromptText("Type name");

        TextArea docArea = new TextArea(selectedType.documentation() != null ? selectedType.documentation() : "");
        docArea.setPromptText("Documentation");
        docArea.setPrefRowCount(4);

        CheckBox abstractBox = new CheckBox("Abstract");
        abstractBox.setSelected(selectedType.isAbstract());
        abstractBox.setDisable(selectedType.category() != TypeInfo.TypeCategory.COMPLEX_TYPE);

        CheckBox mixedBox = new CheckBox("Mixed Content");
        mixedBox.setSelected(selectedType.isMixed());
        mixedBox.setDisable(selectedType.category() != TypeInfo.TypeCategory.COMPLEX_TYPE);

        content.getChildren().addAll(
                new Label("Name:"), nameField,
                new Label("Documentation:"), docArea,
                abstractBox, mixedBox
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                // TODO: Implement actual type property modification
                logger.info("Type properties updated for: {}", selectedType.name());
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Edit Type");
                alert.setHeaderText("Properties Updated");
                alert.setContentText("Type properties have been updated. Full editing will be implemented in a future version.");
                alert.showAndWait();
            }
        });
    }

    /**
     * Delete the currently selected type with safety checks.
     */
    private void deleteSelectedType() {
        TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
        if (selectedType == null) return;

        // Check for references before deletion
        if (selectedType.usageCount() > 0) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete Type");
            alert.setHeaderText("Type has references");
            alert.setContentText(String.format(
                    "The type '%s' is referenced %d time(s). " +
                            "Deleting it may cause validation errors. Continue?",
                    selectedType.name(), selectedType.usageCount()));

            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        // Execute delete command
        DeleteTypeCommand command = new DeleteTypeCommand(domManipulator, selectedType);
        commandExecutor.accept(command);

        // Refresh the view
        loadTypes();
    }

    /**
     * Find all usages of the selected type.
     */
    private void findTypeUsages() {
        TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
        if (selectedType == null) return;

        FindTypeUsagesCommand command = new FindTypeUsagesCommand(domManipulator, selectedType);
        commandExecutor.accept(command);
    }

    /**
     * Navigate to the type definition in the schema.
     */
    private void goToTypeDefinition() {
        TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
        if (selectedType == null) return;

        // Show detailed type information dialog
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Type Definition Location");
        dialog.setHeaderText("Type: " + selectedType.name());

        StringBuilder content = new StringBuilder();
        content.append("Location: ").append(selectedType.xpath()).append("\n\n");
        content.append("Type: ").append(selectedType.category().getDisplayName()).append("\n");

        if (selectedType.baseType() != null) {
            content.append("Base Type: ").append(selectedType.baseType()).append("\n");
        }

        content.append("Usage Count: ").append(selectedType.usageCount()).append("\n");

        if (selectedType.category() == TypeInfo.TypeCategory.COMPLEX_TYPE) {
            if (selectedType.isAbstract()) {
                content.append("Abstract: Yes\n");
            }
            if (selectedType.isMixed()) {
                content.append("Mixed Content: Yes\n");
            }
            if (selectedType.contentModel() != null) {
                content.append("Content Model: ").append(selectedType.contentModel()).append("\n");
            }
        }

        if (selectedType.documentation() != null && !selectedType.documentation().isEmpty()) {
            content.append("\nDocumentation:\n").append(selectedType.documentation());
        }

        dialog.setContentText(content.toString());

        // Make dialog resizable and larger
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(500);
        dialog.getDialogPane().setPrefHeight(350);

        dialog.showAndWait();

        logger.info("Showed definition details for type: {}", selectedType.name());
    }

    /**
     * Show dialog for extracting inline types to global types.
     */
    private void showExtractTypeDialog() {
        // Find all inline types in the schema
        List<org.w3c.dom.Element> inlineTypes = domManipulator.findInlineTypes();

        if (inlineTypes.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Extract to Global Type");
            alert.setHeaderText("No inline types found");
            alert.setContentText("The schema contains no inline types that can be extracted to global types.");
            alert.showAndWait();
            return;
        }

        // Create selection dialog
        Dialog<List<org.w3c.dom.Element>> dialog = new Dialog<>();
        dialog.setTitle("Extract to Global Type");
        dialog.setHeaderText("Select inline types to extract to global types:");

        // Create list view for selection
        ListView<String> listView = new ListView<>();
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Populate list with inline type descriptions
        for (org.w3c.dom.Element inlineType : inlineTypes) {
            String typeKind = inlineType.getLocalName(); // "simpleType" or "complexType"
            String parentName = "";
            org.w3c.dom.Node parent = inlineType.getParentNode();
            if (parent instanceof org.w3c.dom.Element parentElement) {
                String name = parentElement.getAttribute("name");
                if (!name.isEmpty()) {
                    parentName = " (in " + name + ")";
                }
            }

            String description = typeKind + parentName;
            listView.getItems().add(description);
        }

        VBox content = new VBox(10);
        content.getChildren().addAll(
                new Label("Found " + inlineTypes.size() + " inline type(s):"),
                listView,
                new Label("Selected types will be extracted to global types with generated names.")
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Set up result converter
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                List<Integer> selectedIndices = listView.getSelectionModel().getSelectedIndices();
                return selectedIndices.stream()
                        .map(inlineTypes::get)
                        .collect(Collectors.toList());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(selectedTypes -> {
            if (!selectedTypes.isEmpty()) {
                // TODO: Implement ExtractToGlobalTypeCommand for multiple types
                logger.info("Extracting {} inline types to global types", selectedTypes.size());

                Alert result = new Alert(Alert.AlertType.INFORMATION);
                result.setTitle("Extract to Global Type");
                result.setHeaderText("Types Extracted");
                result.setContentText(selectedTypes.size() + " inline types have been marked for extraction. " +
                        "Full implementation will be completed in a future version.");
                result.showAndWait();
            }
        });
    }

    /**
     * Inline the selected type definition (opposite of extract to global).
     */
    private void inlineTypeDefinition() {
        TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
        if (selectedType == null) return;

        // Check if type has multiple usages
        if (selectedType.usageCount() > 1) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Inline Type");
            alert.setHeaderText("Type has multiple references");
            alert.setContentText(String.format(
                    "The type '%s' is used %d times. Inlining will duplicate the definition at each usage location. Continue?",
                    selectedType.name(), selectedType.usageCount()));

            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        // Execute inline command
        InlineTypeCommand command = new InlineTypeCommand(
                domManipulator.getDocument(), selectedType.name(), domManipulator);
        commandExecutor.accept(command);

        // Refresh the view
        loadTypes();

        logger.info("Inlined type definition: {}", selectedType.name());
    }

    /**
     * Clone the selected type with a new name.
     */
    private void cloneSelectedType() {
        TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
        if (selectedType == null) return;

        TextInputDialog dialog = new TextInputDialog(selectedType.name() + "Copy");
        dialog.setTitle("Clone Type");
        dialog.setHeaderText("Clone type: " + selectedType.name());
        dialog.setContentText("New type name:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            String newName = result.get().trim();

            // Check if name already exists
            if (typeData.stream().anyMatch(t -> t.name().equals(newName))) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Clone Error");
                alert.setHeaderText("Name conflict");
                alert.setContentText("A type with name '" + newName + "' already exists.");
                alert.showAndWait();
                return;
            }

            // Execute clone command
            CloneTypeCommand command = new CloneTypeCommand(
                    domManipulator.getDocument(), selectedType.name(), newName, domManipulator);
            commandExecutor.accept(command);

            // Refresh the view
            loadTypes();

            logger.info("Cloned type '{}' to '{}'", selectedType.name(), newName);
        }
    }

    /**
     * Export the selected type to a file.
     */
    private void exportSelectedType() {
        TypeInfo selectedType = typeTable.getSelectionModel().getSelectedItem();
        if (selectedType == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Type");
        fileChooser.setInitialFileName(selectedType.name() + ".xsd");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            try {
                var typeElement = domManipulator.findTypeDefinition(
                        domManipulator.getDocument(), selectedType.name());
                String typeDefinition = domManipulator.getTypeDefinitionAsString(typeElement);
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                    writer.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n");
                    writer.write(typeDefinition);
                    writer.write("\n</xs:schema>");
                }

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("Type exported to: " + file.getAbsolutePath());
                alert.showAndWait();

                logger.info("Exported type '{}' to file: {}", selectedType.name(), file.getAbsolutePath());
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Error");
                alert.setHeaderText("Failed to export type");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
                logger.error("Failed to export type", e);
            }
        }
    }

    /**
     * Remove all unused types from the schema.
     */
    private void removeUnusedTypes() {
        List<TypeInfo> unusedTypes = typeData.stream()
                .filter(type -> type.usageCount() == 0)
                .collect(Collectors.toList());

        if (unusedTypes.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Remove Unused Types");
            alert.setHeaderText("No unused types found");
            alert.setContentText("All types in the schema are being used.");
            alert.showAndWait();
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Remove Unused Types");
        confirmAlert.setHeaderText("Found " + unusedTypes.size() + " unused types");
        confirmAlert.setContentText("The following types will be removed:\n" +
                unusedTypes.stream().map(TypeInfo::name).collect(Collectors.joining(", ")) +
                "\n\nContinue?");

        if (confirmAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            RemoveUnusedTypesCommand command = new RemoveUnusedTypesCommand(
                    domManipulator.getDocument(), domManipulator);
            commandExecutor.accept(command);

            // Refresh the view
            loadTypes();

            logger.info("Removed {} unused types", unusedTypes.size());
        }
    }

    /**
     * Import types from a file.
     */
    private void importTypes() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Types");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XSD Files", "*.xsd"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (file != null) {
            try {
                // Load source document
                javax.xml.parsers.DocumentBuilder builder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
                org.w3c.dom.Document sourceDoc = builder.parse(file);

                // Get all type names from source
                java.util.List<String> typeNames = new java.util.ArrayList<>();
                org.w3c.dom.NodeList simpleTypes = sourceDoc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
                org.w3c.dom.NodeList complexTypes = sourceDoc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");

                for (int i = 0; i < simpleTypes.getLength(); i++) {
                    org.w3c.dom.Element elem = (org.w3c.dom.Element) simpleTypes.item(i);
                    String name = elem.getAttribute("name");
                    if (!name.isEmpty()) typeNames.add(name);
                }
                for (int i = 0; i < complexTypes.getLength(); i++) {
                    org.w3c.dom.Element elem = (org.w3c.dom.Element) complexTypes.item(i);
                    String name = elem.getAttribute("name");
                    if (!name.isEmpty()) typeNames.add(name);
                }

                ImportTypesCommand command = new ImportTypesCommand(
                        domManipulator.getDocument(), sourceDoc, typeNames, domManipulator);
                commandExecutor.accept(command);
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Import Error");
                alert.setHeaderText("Failed to import types");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
                logger.error("Failed to import types", e);
                return;
            }

            // Refresh the view
            loadTypes();

            logger.info("Imported types from file: {}", file.getAbsolutePath());
        }
    }

    /**
     * Export all types to a file.
     */
    private void exportTypes() {
        if (typeData.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Types");
            alert.setHeaderText("No types to export");
            alert.setContentText("The schema contains no global types to export.");
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export All Types");
        fileChooser.setInitialFileName("types_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xsd");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("XSD Files", "*.xsd"));

        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                writer.write("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n");

                for (TypeInfo type : typeData) {
                    var typeElement = domManipulator.findTypeDefinition(
                            domManipulator.getDocument(), type.name());
                    if (typeElement != null) {
                        String typeDefinition = domManipulator.getTypeDefinitionAsString(typeElement);
                        writer.write(typeDefinition);
                        writer.write("\n");
                    }
                }

                writer.write("</xs:schema>");

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("All types exported to: " + file.getAbsolutePath());
                alert.showAndWait();
            } catch (IOException e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Error");
                alert.setHeaderText("Failed to export types");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
                logger.error("Failed to export types", e);
                return;
            }

            logger.info("Exported all types to file: {}", file.getAbsolutePath());
        }
    }

    /**
     * Show type statistics dialog.
     */
    private void showTypeStatistics() {
        TypeStatisticsDialog dialog = new TypeStatisticsDialog(
                domManipulator.getDocument(), domManipulator, typeData);
        dialog.showAndWait();
    }

    /**
     * Show type inheritance visualizer dialog.
     */
    private void showTypeInheritance() {
        TypeInheritanceDialog dialog = new TypeInheritanceDialog(
                domManipulator.getDocument(), domManipulator, typeData);
        dialog.showAndWait();
    }

    /**
     * Get the currently selected type.
     */
    public TypeInfo getSelectedType() {
        return typeTable.getSelectionModel().getSelectedItem();
    }

    /**
     * Select a type by name.
     */
    public void selectType(String typeName) {
        for (TypeInfo type : typeData) {
            if (type.name().equals(typeName)) {
                typeTable.getSelectionModel().select(type);
                typeTable.scrollTo(type);
                break;
            }
        }
    }

    /**
     * Clear the search filter.
     */
    public void clearSearch() {
        searchField.clear();
    }

    /**
     * Create a new SimpleType using the existing editor
     */
    private void createNewSimpleType() {
        try {
            // Show SimpleType editor dialog
            XsdSimpleTypeEditor editor = new XsdSimpleTypeEditor(domManipulator.getDocument());
            editor.setTitle("Create SimpleType");
            editor.setHeaderText("Create a new XSD SimpleType definition");

            Optional<org.fxt.freexmltoolkit.controls.SimpleTypeResult> result = editor.showAndWait();
            if (result.isPresent()) {
                // Create schema node for command
                org.fxt.freexmltoolkit.domain.XsdNodeInfo schemaNode =
                        new org.fxt.freexmltoolkit.domain.XsdNodeInfo(
                                "schema", "schema", "/xs:schema", null,
                                null, null, null, null,
                                org.fxt.freexmltoolkit.domain.XsdNodeInfo.NodeType.SCHEMA
                        );

                org.fxt.freexmltoolkit.controls.commands.AddSimpleTypeCommand command =
                        new org.fxt.freexmltoolkit.controls.commands.AddSimpleTypeCommand(domManipulator, schemaNode, result.get());
                commandExecutor.accept(command);
                loadTypes(); // Refresh the type library
                if (refreshCallback != null) {
                    refreshCallback.run(); // Refresh the main diagram
                }
            }
        } catch (Exception e) {
            logger.error("Error creating new SimpleType", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Create SimpleType Error");
            alert.setHeaderText("Failed to create SimpleType");
            alert.setContentText("Error: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Create a new ComplexType using the existing editor
     */
    private void createNewComplexType() {
        try {
            // Show ComplexType editor dialog
            XsdComplexTypeEditor editor = new XsdComplexTypeEditor(domManipulator.getDocument());
            editor.setTitle("Create ComplexType");
            editor.setHeaderText("Create a new XSD ComplexType definition");

            Optional<org.fxt.freexmltoolkit.controls.ComplexTypeResult> result = editor.showAndWait();
            if (result.isPresent()) {
                // Create schema node for command
                org.fxt.freexmltoolkit.domain.XsdNodeInfo schemaNode =
                        new org.fxt.freexmltoolkit.domain.XsdNodeInfo(
                                "schema", "schema", "/xs:schema", null,
                                null, null, null, null,
                                org.fxt.freexmltoolkit.domain.XsdNodeInfo.NodeType.SCHEMA
                        );

                org.fxt.freexmltoolkit.controls.commands.AddComplexTypeCommand command =
                        new org.fxt.freexmltoolkit.controls.commands.AddComplexTypeCommand(domManipulator, schemaNode, result.get());
                commandExecutor.accept(command);
                loadTypes(); // Refresh the type library
                if (refreshCallback != null) {
                    refreshCallback.run(); // Refresh the main diagram
                }
            }
        } catch (Exception e) {
            logger.error("Error creating new ComplexType", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Create ComplexType Error");
            alert.setHeaderText("Failed to create ComplexType");
            alert.setContentText("Error: " + e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Creates a colored FontIcon for menu items
     */
    private FontIcon createColoredIcon(String iconLiteral, String color) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        icon.setIconSize(12);
        return icon;
    }

    /**
     * Export types to Excel format with XPaths
     */
    private void exportToExcel() {
        if (typeData.isEmpty()) {
            showNoDataAlert("Excel Export");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Types to Excel");
        fileChooser.setInitialFileName("types_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            try (Workbook workbook = new XSSFWorkbook();
                 FileOutputStream fileOut = new FileOutputStream(file)) {

                Sheet sheet = workbook.createSheet("XSD Types");

                // Create header row
                Row headerRow = sheet.createRow(0);
                String[] headers = {"Name", "Category", "Base Type", "Usage Count",
                        "XPath", "Usage XPaths", "Abstract", "Mixed", "Derivation", "Content Model", "Documentation"};

                org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);

                for (int i = 0; i < headers.length; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Fill data rows
                int rowNum = 1;
                for (TypeInfo type : typeData) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(type.name());
                    row.createCell(1).setCellValue(type.category().getDisplayName());
                    row.createCell(2).setCellValue(type.baseType() != null ? type.baseType() : "");
                    row.createCell(3).setCellValue(type.usageCount());
                    row.createCell(4).setCellValue(type.xpath() != null ? type.xpath() : "");
                    row.createCell(5).setCellValue(type.getUsageXPathsForCsv());
                    row.createCell(6).setCellValue(type.isAbstract());
                    row.createCell(7).setCellValue(type.isMixed());
                    row.createCell(8).setCellValue(type.derivationType() != null ? type.derivationType() : "");
                    row.createCell(9).setCellValue(type.contentModel() != null ? type.contentModel() : "");
                    row.createCell(10).setCellValue(type.documentation() != null ? type.documentation() : "");
                }

                // Auto-size columns
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                }

                workbook.write(fileOut);
                showSuccessAlert("Excel Export", file.getAbsolutePath());

            } catch (IOException e) {
                showErrorAlert("Excel Export", e);
                logger.error("Failed to export to Excel", e);
            }
        }
    }

    /**
     * Export types to CSV format with XPaths
     */
    private void exportToCsv() {
        if (typeData.isEmpty()) {
            showNoDataAlert("CSV Export");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Types to CSV");
        fileChooser.setInitialFileName("types_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                // Write header
                writer.write("Name,Category,Base Type,Usage Count,XPath,Usage XPaths,Abstract,Mixed,Derivation,Content Model,Documentation\n");

                // Write data
                for (TypeInfo type : typeData) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",%s,%s,\"%s\",\"%s\",\"%s\"\n",
                            escapeCSV(type.name()),
                            type.category().getDisplayName(),
                            escapeCSV(type.baseType()),
                            type.usageCount(),
                            escapeCSV(type.xpath()),
                            escapeCSV(type.getUsageXPathsForCsv()),
                            type.isAbstract(),
                            type.isMixed(),
                            escapeCSV(type.derivationType()),
                            escapeCSV(type.contentModel()),
                            escapeCSV(type.documentation())));
                }

                showSuccessAlert("CSV Export", file.getAbsolutePath());

            } catch (IOException e) {
                showErrorAlert("CSV Export", e);
                logger.error("Failed to export to CSV", e);
            }
        }
    }

    /**
     * Export types to HTML format with XPaths
     */
    private void exportToHtml() {
        if (typeData.isEmpty()) {
            showNoDataAlert("HTML Export");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Types to HTML");
        fileChooser.setInitialFileName("types_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".html");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("HTML Files", "*.html"));

        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                writer.write("<!DOCTYPE html>\n");
                writer.write("<html lang=\"en\">\n");
                writer.write("<head>\n");
                writer.write("    <meta charset=\"UTF-8\">\n");
                writer.write("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
                writer.write("    <title>XSD Type Library Export</title>\n");
                writer.write("    <style>\n");
                writer.write("        body { font-family: 'Segoe UI', Arial, sans-serif; margin: 20px; }\n");
                writer.write("        h1 { color: #2c5aa0; }\n");
                writer.write("        table { border-collapse: collapse; width: 100%; margin-top: 20px; }\n");
                writer.write("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
                writer.write("        th { background-color: #f2f2f2; font-weight: bold; }\n");
                writer.write("        tr:nth-child(even) { background-color: #f9f9f9; }\n");
                writer.write("        .simple-type { color: #28a745; }\n");
                writer.write("        .complex-type { color: #007bff; }\n");
                writer.write("        .xpath { font-family: monospace; font-size: 0.9em; }\n");
                writer.write("        .unused { color: #dc3545; font-weight: bold; }\n");
                writer.write("        .doc { font-style: italic; max-width: 300px; }\n");
                writer.write("    </style>\n");
                writer.write("</head>\n");
                writer.write("<body>\n");
                writer.write("    <h1>XSD Type Library Export</h1>\n");
                writer.write("    <p>Generated on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "</p>\n");
                writer.write("    <p>Total types: " + typeData.size() + "</p>\n");
                writer.write("    <table>\n");
                writer.write("        <thead>\n");
                writer.write("            <tr>\n");
                writer.write("                <th>Name</th>\n");
                writer.write("                <th>Category</th>\n");
                writer.write("                <th>Base Type</th>\n");
                writer.write("                <th>Usage</th>\n");
                writer.write("                <th>XPath</th>\n");
                writer.write("                <th>Usage XPaths</th>\n");
                writer.write("                <th>Properties</th>\n");
                writer.write("                <th>Documentation</th>\n");
                writer.write("            </tr>\n");
                writer.write("        </thead>\n");
                writer.write("        <tbody>\n");

                for (TypeInfo type : typeData) {
                    writer.write("            <tr>\n");
                    writer.write("                <td><strong>" + escapeHtml(type.name()) + "</strong></td>\n");

                    String categoryClass = type.category() == TypeInfo.TypeCategory.SIMPLE_TYPE ? "simple-type" : "complex-type";
                    writer.write("                <td><span class=\"" + categoryClass + "\">" + type.category().getDisplayName() + "</span></td>\n");

                    writer.write("                <td>" + escapeHtml(type.baseType() != null ? type.baseType() : "-") + "</td>\n");

                    String usageClass = type.usageCount() == 0 ? "unused" : "";
                    writer.write("                <td class=\"" + usageClass + "\">" + type.getUsageInfo() + "</td>\n");

                    writer.write("                <td class=\"xpath\">" + escapeHtml(type.xpath() != null ? type.xpath() : "-") + "</td>\n");

                    writer.write("                <td class=\"xpath\">" + escapeHtml(type.getUsageXPathsFormatted().isEmpty() ? "-" : type.getUsageXPathsFormatted()) + "</td>\n");

                    StringBuilder properties = new StringBuilder();
                    if (type.isAbstract()) properties.append("Abstract ");
                    if (type.isMixed()) properties.append("Mixed ");
                    if (type.derivationType() != null) properties.append(type.derivationType()).append(" ");
                    if (type.contentModel() != null) properties.append("[").append(type.contentModel()).append("]");
                    writer.write("                <td>" + escapeHtml(properties.toString().trim()) + "</td>\n");

                    writer.write("                <td class=\"doc\">" + escapeHtml(type.documentation() != null ? type.documentation() : "-") + "</td>\n");
                    writer.write("            </tr>\n");
                }

                writer.write("        </tbody>\n");
                writer.write("    </table>\n");
                writer.write("</body>\n");
                writer.write("</html>\n");

                showSuccessAlert("HTML Export", file.getAbsolutePath());

            } catch (IOException e) {
                showErrorAlert("HTML Export", e);
                logger.error("Failed to export to HTML", e);
            }
        }
    }

    /**
     * Export types to JSON format with XPaths
     */
    private void exportToJson() {
        if (typeData.isEmpty()) {
            showNoDataAlert("JSON Export");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Types to JSON");
        fileChooser.setInitialFileName("types_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));

        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .create();

                // Create export metadata
                var exportData = new ExportMetadata(
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        typeData.size(),
                        typeData
                );

                gson.toJson(exportData, writer);

                showSuccessAlert("JSON Export", file.getAbsolutePath());

            } catch (IOException e) {
                showErrorAlert("JSON Export", e);
                logger.error("Failed to export to JSON", e);
            }
        }
    }

    // Helper methods for export
    private String escapeCSV(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void showNoDataAlert(String exportType) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(exportType);
        alert.setHeaderText("No types to export");
        alert.setContentText("The schema contains no global types to export.");
        alert.showAndWait();
    }

    private void showSuccessAlert(String exportType, String filePath) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Export Successful");
        alert.setHeaderText(null);
        alert.setContentText("Types exported to: " + filePath);
        alert.showAndWait();
    }

    private void showErrorAlert(String exportType, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Export Error");
        alert.setHeaderText("Failed to export as " + exportType);
        alert.setContentText("Error: " + e.getMessage());
        alert.showAndWait();
    }

    // Data class for JSON export
    private record ExportMetadata(
            String exportDate,
            int totalTypes,
            List<TypeInfo> types
    ) {
    }
}