package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XmlValidationError;
import org.fxt.freexmltoolkit.service.XmlValidationResult;
import org.fxt.freexmltoolkit.service.XsdValidationService;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Advanced XML Validation Panel with error reporting, navigation, and quick fixes.
 * Provides comprehensive validation results display with professional IDE-like features.
 */
public class XmlValidationPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlValidationPanel.class);

    // Services
    private final XsdValidationService validationService;
    private final ExecutorService executor;

    // UI Components
    private Label statusLabel;
    private ProgressBar validationProgress;
    private Button validateButton;
    private Button clearButton;
    private CheckBox realTimeValidationCheckBox;
    private CheckBox validationOnSaveCheckBox;
    private TextField schemaPathField;
    private Button browseSchemaButton;

    // Results Display
    private TableView<XmlValidationError> errorTable;
    private ObservableList<XmlValidationError> errorList;
    private TextArea detailsArea;
    private TabPane resultsTabPane;

    // Statistics
    private Label errorCountLabel;
    private Label warningCountLabel;
    private Label validationTimeLabel;
    private Label schemaInfoLabel;

    // Current validation state
    private XmlValidationResult currentResult;
    private String currentXmlContent;
    private Task<XmlValidationResult> currentValidationTask;

    // Callbacks
    private Consumer<Integer> onNavigateToLine;
    private Consumer<XmlValidationError> onErrorSelected;
    private Runnable onValidationStarted;
    private Consumer<XmlValidationResult> onValidationCompleted;

    public XmlValidationPanel() {
        this.validationService = XsdValidationService.getInstance();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("XML-Validation-Thread");
            t.setDaemon(true);
            return t;
        });

        initializeComponents();
        setupLayout();
        setupEventHandlers();
        updateValidationSettings();

        logger.debug("XML Validation Panel initialized");
    }

    // ========== Initialization ==========

    private void initializeComponents() {
        // Status and progress
        statusLabel = new Label("Ready for validation");
        statusLabel.setFont(Font.font(null, FontWeight.BOLD, 12));

        validationProgress = new ProgressBar();
        validationProgress.setVisible(false);
        validationProgress.setMaxWidth(Double.MAX_VALUE);

        // Control buttons
        validateButton = new Button("Validate XML");
        validateButton.getStyleClass().addAll("primary-button");

        clearButton = new Button("Clear Results");
        clearButton.getStyleClass().addAll("secondary-button");
        clearButton.setDisable(true);

        // Validation settings
        realTimeValidationCheckBox = new CheckBox("Real-time validation");
        realTimeValidationCheckBox.setSelected(validationService.isRealTimeValidationEnabled());

        validationOnSaveCheckBox = new CheckBox("Validate on save");
        validationOnSaveCheckBox.setSelected(validationService.isValidationOnSaveEnabled());

        // Schema selection
        schemaPathField = new TextField();
        schemaPathField.setPromptText("XSD schema path (optional - auto-discovery enabled)");

        browseSchemaButton = new Button("Browse...");
        browseSchemaButton.getStyleClass().addAll("secondary-button");

        // Error table
        setupErrorTable();

        // Details area
        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setPrefRowCount(4);
        detailsArea.setPromptText("Select an error to see detailed information...");
        detailsArea.getStyleClass().add("xml-details-area");

        // Results tabs
        resultsTabPane = new TabPane();
        resultsTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Statistics labels
        errorCountLabel = new Label("Errors: 0");
        warningCountLabel = new Label("Warnings: 0");
        validationTimeLabel = new Label("Time: 0ms");
        schemaInfoLabel = new Label("Schema: None");

        // Style statistics labels
        errorCountLabel.getStyleClass().add("validation-stat-error");
        warningCountLabel.getStyleClass().add("validation-stat-warning");
        validationTimeLabel.getStyleClass().add("validation-stat-time");
        schemaInfoLabel.getStyleClass().add("validation-stat-schema");
    }

    private void setupErrorTable() {
        errorList = FXCollections.observableArrayList();
        errorTable = new TableView<>(errorList);
        errorTable.setPlaceholder(new Label("No validation results"));
        errorTable.getStyleClass().add("xml-validation-table");

        // Type column with icon
        TableColumn<XmlValidationError, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setPrefWidth(80);
        typeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getErrorType().getDisplayName()));
        typeColumn.setCellFactory(col -> new TableCell<XmlValidationError, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().removeAll("error-cell", "warning-cell", "info-cell", "fatal-cell");
                } else {
                    setText(item);
                    XmlValidationError error = getTableView().getItems().get(getIndex());
                    getStyleClass().removeAll("error-cell", "warning-cell", "info-cell", "fatal-cell");
                    getStyleClass().add(error.getCssClass() + "-cell");
                }
            }
        });

        // Line column
        TableColumn<XmlValidationError, Integer> lineColumn = new TableColumn<>("Line");
        lineColumn.setPrefWidth(60);
        lineColumn.setCellValueFactory(new PropertyValueFactory<>("lineNumber"));
        lineColumn.setCellFactory(col -> new TableCell<XmlValidationError, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item <= 0) {
                    setText("");
                } else {
                    setText(item.toString());
                }
            }
        });

        // Column column
        TableColumn<XmlValidationError, Integer> columnColumn = new TableColumn<>("Col");
        columnColumn.setPrefWidth(50);
        columnColumn.setCellValueFactory(new PropertyValueFactory<>("columnNumber"));
        columnColumn.setCellFactory(col -> new TableCell<XmlValidationError, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item <= 0) {
                    setText("");
                } else {
                    setText(item.toString());
                }
            }
        });

        // Message column
        TableColumn<XmlValidationError, String> messageColumn = new TableColumn<>("Description");
        messageColumn.setPrefWidth(400);
        messageColumn.setCellValueFactory(new PropertyValueFactory<>("message"));
        messageColumn.setCellFactory(col -> new TableCell<XmlValidationError, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    // Add tooltip with full message
                    setTooltip(new Tooltip(item));
                }
            }
        });

        errorTable.getColumns().addAll(typeColumn, lineColumn, columnColumn, messageColumn);

        // Make table sortable by severity by default
        typeColumn.setSortType(TableColumn.SortType.DESCENDING);
        errorTable.getSortOrder().add(typeColumn);
    }

    private void setupLayout() {
        setPadding(new Insets(10));
        setSpacing(8);
        getStyleClass().add("xml-validation-panel");

        // Header section with title and settings
        VBox headerSection = new VBox(5);
        headerSection.getStyleClass().add("validation-header");

        Label titleLabel = new Label("XML Validation");
        titleLabel.getStyleClass().add("section-title");

        HBox settingsBox = new HBox(10);
        settingsBox.setAlignment(Pos.CENTER_LEFT);
        settingsBox.getChildren().addAll(
                realTimeValidationCheckBox,
                validationOnSaveCheckBox
        );

        headerSection.getChildren().addAll(titleLabel, settingsBox);

        // Schema selection section
        VBox schemaSection = new VBox(5);
        Label schemaLabel = new Label("XSD Schema:");
        schemaLabel.getStyleClass().add("field-label");

        HBox schemaBox = new HBox(5);
        HBox.setHgrow(schemaPathField, Priority.ALWAYS);
        schemaBox.getChildren().addAll(schemaPathField, browseSchemaButton);

        schemaSection.getChildren().addAll(schemaLabel, schemaBox);

        // Control section
        HBox controlSection = new HBox(10);
        controlSection.setAlignment(Pos.CENTER_LEFT);
        controlSection.getChildren().addAll(
                validateButton,
                clearButton,
                new Region(), // Spacer
                validationProgress
        );
        HBox.setHgrow(controlSection.getChildren().get(2), Priority.ALWAYS);

        // Status section
        VBox statusSection = new VBox(5);
        statusSection.getChildren().addAll(statusLabel);

        // Statistics section
        HBox statsSection = new HBox(15);
        statsSection.setAlignment(Pos.CENTER_LEFT);
        statsSection.getChildren().addAll(
                errorCountLabel,
                warningCountLabel,
                validationTimeLabel,
                schemaInfoLabel
        );
        statsSection.getStyleClass().add("validation-statistics");

        // Results section with tabs
        setupResultsTabs();
        VBox.setVgrow(resultsTabPane, Priority.ALWAYS);

        // Add all sections
        getChildren().addAll(
                headerSection,
                new Separator(),
                schemaSection,
                controlSection,
                statusSection,
                statsSection,
                new Separator(),
                resultsTabPane
        );
    }

    private void setupResultsTabs() {
        // Errors/Warnings tab
        Tab errorsTab = new Tab("Issues");
        VBox errorsContent = new VBox(5);
        VBox.setVgrow(errorTable, Priority.ALWAYS);
        errorsContent.getChildren().addAll(errorTable);
        errorsTab.setContent(errorsContent);

        // Details tab
        Tab detailsTab = new Tab("Details");
        VBox detailsContent = new VBox(5);
        VBox.setVgrow(detailsArea, Priority.ALWAYS);
        detailsContent.getChildren().addAll(detailsArea);
        detailsTab.setContent(detailsContent);

        resultsTabPane.getTabs().addAll(errorsTab, detailsTab);
    }

    private void setupEventHandlers() {
        // Validate button
        validateButton.setOnAction(e -> {
            if (currentXmlContent != null && !currentXmlContent.trim().isEmpty()) {
                validateXml(currentXmlContent, schemaPathField.getText());
            } else {
                showStatus("No XML content to validate", false);
            }
        });

        // Clear button
        clearButton.setOnAction(e -> clearResults());

        // Settings checkboxes
        realTimeValidationCheckBox.setOnAction(e -> {
            boolean enabled = realTimeValidationCheckBox.isSelected();
            validationService.setRealTimeValidationEnabled(enabled);
            logger.debug("Real-time validation {}", enabled ? "enabled" : "disabled");
        });

        validationOnSaveCheckBox.setOnAction(e -> {
            boolean enabled = validationOnSaveCheckBox.isSelected();
            validationService.setValidationOnSaveEnabled(enabled);
            logger.debug("Validation on save {}", enabled ? "enabled" : "disabled");
        });

        // Schema browse button
        browseSchemaButton.setOnAction(e -> browseForSchema());

        // Error table selection
        errorTable.getSelectionModel().selectedItemProperty().addListener((obs, oldError, newError) -> {
            if (newError != null) {
                showErrorDetails(newError);
                if (onErrorSelected != null) {
                    onErrorSelected.accept(newError);
                }
            }
        });

        // Double-click to navigate to error location
        errorTable.setRowFactory(tv -> {
            TableRow<XmlValidationError> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    XmlValidationError error = row.getItem();
                    if (error.hasLocation() && onNavigateToLine != null) {
                        onNavigateToLine.accept(error.getLineNumber());
                    }
                }
            });
            return row;
        });
    }

    // ========== Public API ==========

    /**
     * Validate XML content with optional schema
     */
    public void validateXml(String xmlContent, String schemaPath) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            showStatus("No XML content provided", false);
            return;
        }

        // Cancel current validation if running
        if (currentValidationTask != null && !currentValidationTask.isDone()) {
            currentValidationTask.cancel();
        }

        currentXmlContent = xmlContent;

        // Show progress
        validationProgress.setVisible(true);
        validationProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        validateButton.setDisable(true);
        showStatus("Validating XML...", true);

        if (onValidationStarted != null) {
            onValidationStarted.run();
        }

        // Create validation task
        currentValidationTask = validationService.validateXmlAsync(xmlContent, schemaPath);

        currentValidationTask.setOnSucceeded(e -> Platform.runLater(() -> {
            XmlValidationResult result = currentValidationTask.getValue();
            handleValidationResult(result);
        }));

        currentValidationTask.setOnFailed(e -> Platform.runLater(() -> {
            Throwable exception = currentValidationTask.getException();
            handleValidationError(exception);
        }));

        currentValidationTask.setOnCancelled(e -> Platform.runLater(() -> {
            showStatus("Validation cancelled", false);
            validationProgress.setVisible(false);
            validateButton.setDisable(false);
        }));

        // Execute task
        executor.submit(currentValidationTask);
    }

    /**
     * Set XML content for validation
     */
    public void setXmlContent(String xmlContent) {
        this.currentXmlContent = xmlContent;

        // Trigger real-time validation if enabled
        if (realTimeValidationCheckBox.isSelected() &&
                xmlContent != null && !xmlContent.trim().isEmpty()) {
            // Debounce rapid changes
            Platform.runLater(() -> validateXml(xmlContent, schemaPathField.getText()));
        }
    }

    /**
     * Get current validation result
     */
    public XmlValidationResult getCurrentResult() {
        return currentResult;
    }

    /**
     * Clear all validation results
     */
    public void clearResults() {
        errorList.clear();
        detailsArea.clear();
        currentResult = null;

        updateStatistics(null);
        showStatus("Results cleared", false);
        clearButton.setDisable(true);

        logger.debug("Validation results cleared");
    }

    // ========== Event Handlers ==========

    private void handleValidationResult(XmlValidationResult result) {
        currentResult = result;

        // Update UI
        validationProgress.setVisible(false);
        validateButton.setDisable(false);
        clearButton.setDisable(false);

        // Update error list
        errorList.clear();
        errorList.addAll(result.getAllIssues());

        // Update statistics
        updateStatistics(result);

        // Show status
        String status = result.isValid() ?
                "Validation successful" :
                String.format("Validation failed (%d errors, %d warnings)",
                        result.getErrorCount(), result.getWarningCount());
        showStatus(status, result.isValid());

        // Show validation summary in details
        detailsArea.setText(result.getDetailedReport());

        // Update schema path if auto-discovered
        if (result.isAutoDiscoveredSchema() && result.getSchemaPath() != null) {
            schemaPathField.setText(result.getSchemaPath());
        }

        // Notify callback
        if (onValidationCompleted != null) {
            onValidationCompleted.accept(result);
        }

        logger.debug("Validation completed: {} issues found", result.getTotalIssueCount());
    }

    private void handleValidationError(Throwable exception) {
        validationProgress.setVisible(false);
        validateButton.setDisable(false);

        String errorMessage = "Validation failed: " + exception.getMessage();
        showStatus(errorMessage, false);

        // Show error in details
        detailsArea.setText("Validation Error:\n" + exception.getMessage() +
                "\n\nPlease check the XML content and schema path.");

        logger.error("Validation failed with exception", exception);
    }

    private void showErrorDetails(XmlValidationError error) {
        StringBuilder details = new StringBuilder();
        details.append("Error Details:\n");
        details.append("=============\n\n");

        details.append("Type: ").append(error.getErrorType().getDisplayName()).append("\n");
        if (error.hasLocation()) {
            details.append("Location: ").append(error.getLocationString()).append("\n");
        }
        details.append("Message: ").append(error.getMessage()).append("\n");

        if (error.getSuggestion() != null && !error.getSuggestion().isEmpty()) {
            details.append("Suggestion: ").append(error.getSuggestion()).append("\n");
        }

        String context = error.getContextString();
        if (!context.isEmpty()) {
            details.append("Context: ").append(context).append("\n");
        }

        if (error.getSchemaReference() != null) {
            details.append("Schema Reference: ").append(error.getSchemaReference()).append("\n");
        }

        details.append("Timestamp: ").append(
                error.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ).append("\n");

        detailsArea.setText(details.toString());
    }

    private void updateStatistics(XmlValidationResult result) {
        if (result == null) {
            errorCountLabel.setText("Errors: 0");
            warningCountLabel.setText("Warnings: 0");
            validationTimeLabel.setText("Time: 0ms");
            schemaInfoLabel.setText("Schema: None");
        } else {
            errorCountLabel.setText("Errors: " + result.getErrorCount());
            warningCountLabel.setText("Warnings: " + result.getWarningCount());
            validationTimeLabel.setText("Time: " + result.getValidationDuration() + "ms");

            String schemaText = result.getSchemaPath() != null ?
                    (result.isAutoDiscoveredSchema() ? "Auto: " : "") +
                            getShortSchemaName(result.getSchemaPath()) : "None";
            schemaInfoLabel.setText("Schema: " + schemaText);
        }
    }

    private String getShortSchemaName(String schemaPath) {
        if (schemaPath == null) return "None";

        // Extract filename from path
        String[] parts = schemaPath.replace('\\', '/').split("/");
        return parts[parts.length - 1];
    }

    private void showStatus(String message, boolean isSuccess) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-success", "status-error", "status-info");

        if (isSuccess) {
            statusLabel.getStyleClass().add("status-success");
        } else if (message.toLowerCase().contains("error") || message.toLowerCase().contains("failed")) {
            statusLabel.getStyleClass().add("status-error");
        } else {
            statusLabel.getStyleClass().add("status-info");
        }
    }

    private void updateValidationSettings() {
        realTimeValidationCheckBox.setSelected(validationService.isRealTimeValidationEnabled());
        validationOnSaveCheckBox.setSelected(validationService.isValidationOnSaveEnabled());
    }

    private void browseForSchema() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Schema File");

        // Add file extension filters
        FileChooser.ExtensionFilter xsdFilter = new FileChooser.ExtensionFilter("XSD Schema Files (*.xsd)", "*.xsd");
        FileChooser.ExtensionFilter dtdFilter = new FileChooser.ExtensionFilter("DTD Files (*.dtd)", "*.dtd");
        FileChooser.ExtensionFilter rngFilter = new FileChooser.ExtensionFilter("RelaxNG Files (*.rng)", "*.rng");
        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Schema Files", "*.xsd", "*.dtd", "*.rng");

        fileChooser.getExtensionFilters().addAll(allFilter, xsdFilter, dtdFilter, rngFilter);
        fileChooser.setSelectedExtensionFilter(allFilter);

        // Set initial directory to the current working directory or last used directory
        try {
            File initialDir = new File(System.getProperty("user.dir"));
            if (initialDir.exists() && initialDir.isDirectory()) {
                fileChooser.setInitialDirectory(initialDir);
            }
        } catch (Exception e) {
            logger.debug("Could not set initial directory: {}", e.getMessage());
        }

        // Show the file chooser dialog
        File selectedFile = fileChooser.showOpenDialog(this.getScene().getWindow());

        if (selectedFile != null && selectedFile.exists()) {
            try {
                String schemaPath = selectedFile.getAbsolutePath();

                // Update the schema field if it exists
                if (schemaPathField != null) {
                    schemaPathField.setText(schemaPath);
                }

                logger.info("Selected schema file: {}", schemaPath);

                // Optionally trigger validation immediately
                if (validationService != null && validationService.isRealTimeValidationEnabled()) {
                    // Store the schema path for future validation
                    logger.info("Schema selected for validation: {}", schemaPath);
                    // Note: The validation will use the schema from schemaPathField when triggered
                }

            } catch (Exception e) {
                logger.error("Error handling selected schema file: {}", e.getMessage());
                showErrorAlert("File Error", "Could not process the selected schema file: " + e.getMessage());
            }
        } else {
            logger.debug("No schema file selected or file does not exist");
        }
    }

    // ========== Callback Setters ==========

    public void setOnNavigateToLine(Consumer<Integer> onNavigateToLine) {
        this.onNavigateToLine = onNavigateToLine;
    }

    public void setOnErrorSelected(Consumer<XmlValidationError> onErrorSelected) {
        this.onErrorSelected = onErrorSelected;
    }

    public void setOnValidationStarted(Runnable onValidationStarted) {
        this.onValidationStarted = onValidationStarted;
    }

    public void setOnValidationCompleted(Consumer<XmlValidationResult> onValidationCompleted) {
        this.onValidationCompleted = onValidationCompleted;
    }

    /**
     * Shows an error alert dialog to the user
     */
    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========== Cleanup ==========

    public void shutdown() {
        if (currentValidationTask != null && !currentValidationTask.isDone()) {
            currentValidationTask.cancel();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        logger.debug("XML Validation Panel shutdown");
    }
}