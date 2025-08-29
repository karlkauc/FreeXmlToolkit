package org.fxt.freexmltoolkit.controls.dialogs;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdUndoManager;
import org.fxt.freexmltoolkit.domain.command.AddImportCommand;
import org.fxt.freexmltoolkit.domain.command.AddIncludeCommand;
import org.fxt.freexmltoolkit.domain.command.RemoveImportCommand;
import org.fxt.freexmltoolkit.domain.command.RemoveIncludeCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Optional;

/**
 * Dialog for managing XSD import and include statements.
 * Allows viewing, adding, and removing schema dependencies.
 */
public class ImportIncludeManagerDialog extends Dialog<Void> {
    private static final Logger logger = LogManager.getLogger(ImportIncludeManagerDialog.class);

    private final XsdDomManipulator domManipulator;
    private final XsdUndoManager undoManager;

    // UI Components
    private TableView<ImportInfo> importTable;
    private TableView<IncludeInfo> includeTable;
    private ObservableList<ImportInfo> importData;
    private ObservableList<IncludeInfo> includeData;

    public ImportIncludeManagerDialog(XsdDomManipulator domManipulator, XsdUndoManager undoManager) {
        this.domManipulator = domManipulator;
        this.undoManager = undoManager;

        setTitle("Import/Include Manager");
        setHeaderText("Manage schema dependencies (imports and includes)");
        setGraphic(new FontIcon("bi-diagram-2"));

        initializeComponents();
        loadExistingImportsAndIncludes();

        // Set result converter
        setResultConverter(buttonType -> null);
    }

    private void initializeComponents() {
        // Create main layout
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Imports tab
        Tab importsTab = new Tab("Imports");
        importsTab.setGraphic(new FontIcon("bi-box-arrow-in-right"));
        importsTab.setContent(createImportsPane());

        // Includes tab
        Tab includesTab = new Tab("Includes");
        includesTab.setGraphic(new FontIcon("bi-files"));
        includesTab.setContent(createIncludesPane());

        tabPane.getTabs().addAll(importsTab, includesTab);

        getDialogPane().setContent(tabPane);
        getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        getDialogPane().setPrefSize(700, 500);

        // Apply CSS styling
        getDialogPane().getStylesheets().add(
                getClass().getResource("/css/import-include-manager.css").toExternalForm());
        getDialogPane().getStyleClass().add("import-include-manager-dialog");
    }

    private BorderPane createImportsPane() {
        BorderPane pane = new BorderPane();

        // Create imports table
        importData = FXCollections.observableArrayList();
        importTable = new TableView<>(importData);
        setupImportsTable();

        // Create toolbar
        ToolBar importToolbar = new ToolBar();

        Button addImportButton = new Button("Add Import");
        addImportButton.setGraphic(new FontIcon("bi-plus-circle"));
        addImportButton.setOnAction(e -> showAddImportDialog());

        Button removeImportButton = new Button("Remove");
        removeImportButton.setGraphic(new FontIcon("bi-trash"));
        removeImportButton.setOnAction(e -> removeSelectedImport());
        removeImportButton.disableProperty().bind(importTable.getSelectionModel().selectedItemProperty().isNull());

        Button refreshButton = new Button("Refresh");
        refreshButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        refreshButton.setOnAction(e -> loadExistingImportsAndIncludes());

        importToolbar.getItems().addAll(addImportButton, removeImportButton, new Separator(), refreshButton);

        pane.setTop(importToolbar);
        pane.setCenter(importTable);

        return pane;
    }

    private BorderPane createIncludesPane() {
        BorderPane pane = new BorderPane();

        // Create includes table
        includeData = FXCollections.observableArrayList();
        includeTable = new TableView<>(includeData);
        setupIncludesTable();

        // Create toolbar
        ToolBar includeToolbar = new ToolBar();

        Button addIncludeButton = new Button("Add Include");
        addIncludeButton.setGraphic(new FontIcon("bi-plus-circle"));
        addIncludeButton.setOnAction(e -> showAddIncludeDialog());

        Button removeIncludeButton = new Button("Remove");
        removeIncludeButton.setGraphic(new FontIcon("bi-trash"));
        removeIncludeButton.setOnAction(e -> removeSelectedInclude());
        removeIncludeButton.disableProperty().bind(includeTable.getSelectionModel().selectedItemProperty().isNull());

        Button refreshButton = new Button("Refresh");
        refreshButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        refreshButton.setOnAction(e -> loadExistingImportsAndIncludes());

        includeToolbar.getItems().addAll(addIncludeButton, removeIncludeButton, new Separator(), refreshButton);

        pane.setTop(includeToolbar);
        pane.setCenter(includeTable);

        return pane;
    }

    @SuppressWarnings("unchecked")
    private void setupImportsTable() {
        TableColumn<ImportInfo, String> namespaceCol = new TableColumn<>("Namespace");
        namespaceCol.setCellValueFactory(new PropertyValueFactory<>("namespace"));
        namespaceCol.setPrefWidth(300);

        TableColumn<ImportInfo, String> locationCol = new TableColumn<>("Schema Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("schemaLocation"));
        locationCol.setPrefWidth(350);

        TableColumn<ImportInfo, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);

        importTable.getColumns().addAll(namespaceCol, locationCol, statusCol);
        importTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    @SuppressWarnings("unchecked")
    private void setupIncludesTable() {
        TableColumn<IncludeInfo, String> locationCol = new TableColumn<>("Schema Location");
        locationCol.setCellValueFactory(new PropertyValueFactory<>("schemaLocation"));
        locationCol.setPrefWidth(400);

        TableColumn<IncludeInfo, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);

        includeTable.getColumns().addAll(locationCol, statusCol);
        includeTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private void showAddImportDialog() {
        Dialog<ImportInfo> dialog = new Dialog<>();
        dialog.setTitle("Add Import");
        dialog.setHeaderText("Add schema import");

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField namespaceField = new TextField();
        namespaceField.setPromptText("Target namespace (optional)");
        namespaceField.setPrefWidth(300);

        TextField locationField = new TextField();
        locationField.setPromptText("Schema location (URL or file path)");
        locationField.setPrefWidth(300);

        Button browseButton = new Button("Browse...");
        browseButton.setGraphic(new FontIcon("bi-folder"));
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select XSD Schema File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("XSD Files", "*.xsd", "*.xml"));

            File selectedFile = fileChooser.showOpenDialog(dialog.getOwner());
            if (selectedFile != null) {
                locationField.setText(selectedFile.getAbsolutePath());
            }
        });

        HBox locationBox = new HBox(10, locationField, browseButton);

        grid.add(new Label("Namespace:"), 0, 0);
        grid.add(namespaceField, 1, 0);
        grid.add(new Label("Schema Location:"), 0, 1);
        grid.add(locationBox, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Enable OK button only when location is provided
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
                locationField.textProperty().isEmpty());

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new ImportInfo(
                        namespaceField.getText().trim(),
                        locationField.getText().trim(),
                        "New"
                );
            }
            return null;
        });

        Optional<ImportInfo> result = dialog.showAndWait();
        result.ifPresent(this::addImport);
    }

    private void showAddIncludeDialog() {
        Dialog<IncludeInfo> dialog = new Dialog<>();
        dialog.setTitle("Add Include");
        dialog.setHeaderText("Add schema include");

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField locationField = new TextField();
        locationField.setPromptText("Schema location (URL or file path)");
        locationField.setPrefWidth(300);

        Button browseButton = new Button("Browse...");
        browseButton.setGraphic(new FontIcon("bi-folder"));
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select XSD Schema File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("XSD Files", "*.xsd", "*.xml"));

            File selectedFile = fileChooser.showOpenDialog(dialog.getOwner());
            if (selectedFile != null) {
                locationField.setText(selectedFile.getAbsolutePath());
            }
        });

        HBox locationBox = new HBox(10, locationField, browseButton);

        grid.add(new Label("Schema Location:"), 0, 0);
        grid.add(locationBox, 1, 0);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Enable OK button only when location is provided
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
                locationField.textProperty().isEmpty());

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new IncludeInfo(
                        locationField.getText().trim(),
                        "New"
                );
            }
            return null;
        });

        Optional<IncludeInfo> result = dialog.showAndWait();
        result.ifPresent(this::addInclude);
    }

    private void addImport(ImportInfo importInfo) {
        try {
            AddImportCommand command = new AddImportCommand(
                    domManipulator.getDocument(),
                    importInfo.namespace(),
                    importInfo.schemaLocation(),
                    domManipulator
            );

            if (undoManager.executeCommand(command)) {
                logger.info("Import added successfully: {}", importInfo.namespace());
                loadExistingImportsAndIncludes(); // Refresh tables
            }
        } catch (Exception e) {
            logger.error("Failed to add import", e);
            showErrorAlert("Add Import Failed", "Failed to add import: " + e.getMessage());
        }
    }

    private void addInclude(IncludeInfo includeInfo) {
        try {
            AddIncludeCommand command = new AddIncludeCommand(
                    domManipulator.getDocument(),
                    includeInfo.schemaLocation(),
                    domManipulator
            );

            if (undoManager.executeCommand(command)) {
                logger.info("Include added successfully: {}", includeInfo.schemaLocation());
                loadExistingImportsAndIncludes(); // Refresh tables
            }
        } catch (Exception e) {
            logger.error("Failed to add include", e);
            showErrorAlert("Add Include Failed", "Failed to add include: " + e.getMessage());
        }
    }

    private void removeSelectedImport() {
        ImportInfo selected = importTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Remove Import");
            confirmAlert.setHeaderText("Remove import statement");
            confirmAlert.setContentText("Are you sure you want to remove the import for namespace '" +
                    selected.namespace() + "'?");

            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    RemoveImportCommand command = new RemoveImportCommand(
                            domManipulator.getDocument(),
                            selected.namespace(),
                            domManipulator
                    );

                    if (undoManager.executeCommand(command)) {
                        logger.info("Import removed successfully: {}", selected.namespace());
                        loadExistingImportsAndIncludes(); // Refresh tables
                    }
                } catch (Exception e) {
                    logger.error("Failed to remove import", e);
                    showErrorAlert("Remove Import Failed", "Failed to remove import: " + e.getMessage());
                }
            }
        }
    }

    private void removeSelectedInclude() {
        IncludeInfo selected = includeTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("Remove Include");
            confirmAlert.setHeaderText("Remove include statement");
            confirmAlert.setContentText("Are you sure you want to remove the include for schema '" +
                    selected.schemaLocation() + "'?");

            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                try {
                    RemoveIncludeCommand command = new RemoveIncludeCommand(
                            domManipulator.getDocument(),
                            selected.schemaLocation(),
                            domManipulator
                    );

                    if (undoManager.executeCommand(command)) {
                        logger.info("Include removed successfully: {}", selected.schemaLocation());
                        loadExistingImportsAndIncludes(); // Refresh tables
                    }
                } catch (Exception e) {
                    logger.error("Failed to remove include", e);
                    showErrorAlert("Remove Include Failed", "Failed to remove include: " + e.getMessage());
                }
            }
        }
    }

    private void loadExistingImportsAndIncludes() {
        importData.clear();
        includeData.clear();

        if (domManipulator != null && domManipulator.getDocument() != null) {
            Document document = domManipulator.getDocument();
            Element root = document.getDocumentElement();

            if (root != null) {
                loadImports(root);
                loadIncludes(root);
            }
        }
    }

    private void loadImports(Element schemaRoot) {
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");

        for (int i = 0; i < imports.getLength(); i++) {
            Element importElement = (Element) imports.item(i);
            String namespace = importElement.getAttribute("namespace");
            String schemaLocation = importElement.getAttribute("schemaLocation");

            importData.add(new ImportInfo(namespace, schemaLocation, "Existing"));
        }
    }

    private void loadIncludes(Element schemaRoot) {
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");

        for (int i = 0; i < includes.getLength(); i++) {
            Element includeElement = (Element) includes.item(i);
            String schemaLocation = includeElement.getAttribute("schemaLocation");

            includeData.add(new IncludeInfo(schemaLocation, "Existing"));
        }
    }

    private void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfoAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Data classes
        public record ImportInfo(String namespace, String schemaLocation, String status) {
    }

    public record IncludeInfo(String schemaLocation, String status) {
    }
}