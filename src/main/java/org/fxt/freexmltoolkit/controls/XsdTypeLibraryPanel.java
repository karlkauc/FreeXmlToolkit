package org.fxt.freexmltoolkit.controls;

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
import java.io.FileWriter;
import java.io.IOException;
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

        // Export types button
        Button exportButton = new Button();
        FontIcon exportIcon = new FontIcon("bi-download");
        exportButton.setGraphic(exportIcon);
        exportButton.setTooltip(new Tooltip("Export types to file"));
        exportButton.getStyleClass().add("icon-button");
        exportButton.setOnAction(e -> exportTypes());

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
                extractButton, refreshButton);
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
        editItem.setOnAction(e -> editSelectedType());

        // Delete Type
        MenuItem deleteItem = new MenuItem("Delete Type");
        deleteItem.setGraphic(new FontIcon("bi-trash"));
        deleteItem.setOnAction(e -> deleteSelectedType());

        // Find Usages
        MenuItem findUsagesItem = new MenuItem("Find Usages");
        findUsagesItem.setGraphic(new FontIcon("bi-search"));
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

        // Double-click to edit
        typeTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && typeTable.getSelectionModel().getSelectedItem() != null) {
                editSelectedType();
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

        // TODO: Open appropriate editor dialog based on type category
        logger.info("Edit type requested for: {}", selectedType.name());
        // This will be implemented when we create the edit dialogs
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

        logger.info("Navigate to definition for type: {}", selectedType.name());
        // TODO: Implement navigation to schema location
        // This could scroll to the type in the text editor or highlight in diagram
    }

    /**
     * Show dialog for extracting inline types to global types.
     */
    private void showExtractTypeDialog() {
        // TODO: Implement extract type dialog
        logger.info("Extract type dialog requested");
        // This will be implemented as a separate dialog class
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
}