package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.SchematronService;
import org.fxt.freexmltoolkit.service.SchematronServiceImpl;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Comprehensive testing framework for Schematron rules against XML files.
 * Provides batch testing, result visualization, and detailed reporting.
 */
public class SchematronTester extends VBox {

    private static final Logger logger = LogManager.getLogger(SchematronTester.class);

    // UI Components
    private TextField schematronFileField;
    private Button browseSchematronButton;
    private ListView<TestFile> testFilesListView;
    private Button addTestFileButton;
    private Button removeTestFileButton;
    private Button runTestsButton;
    private Button clearResultsButton;
    private TableView<TestResult> resultsTable;
    private TextArea detailsTextArea;
    private ProgressBar progressBar;
    private Label statusLabel;

    // Data models
    private final ObservableList<TestFile> testFiles = FXCollections.observableArrayList();
    private final ObservableList<TestResult> testResults = FXCollections.observableArrayList();

    // Services
    private final SchematronService schematronService;

    // State
    private File currentSchematronFile;
    private boolean testingInProgress = false;

    /**
     * Constructor - Initialize the Schematron Tester
     */
    public SchematronTester() {
        this.schematronService = new SchematronServiceImpl();

        this.setSpacing(10);
        this.setPadding(new Insets(10));

        initializeComponents();
        layoutComponents();
        setupEventHandlers();

        logger.debug("SchematronTester initialized");
    }

    /**
     * Initialize all UI components
     */
    private void initializeComponents() {
        // Schematron file selection
        schematronFileField = new TextField();
        schematronFileField.setPromptText("Select Schematron file to test...");
        schematronFileField.setEditable(false);
        schematronFileField.setPrefWidth(400);

        browseSchematronButton = new Button("Browse...");
        browseSchematronButton.setPrefWidth(80);

        // Test files management
        testFilesListView = new ListView<>(testFiles);
        testFilesListView.setPrefHeight(150);
        testFilesListView.setCellFactory(listView -> new TestFileListCell());

        addTestFileButton = new Button("Add XML Files");
        addTestFileButton.getStyleClass().add("primary-button");

        removeTestFileButton = new Button("Remove Selected");
        removeTestFileButton.getStyleClass().add("secondary-button");
        removeTestFileButton.setDisable(true);

        // Test execution controls
        runTestsButton = new Button("Run Tests");
        runTestsButton.getStyleClass().add("primary-button");
        runTestsButton.setPrefWidth(120);
        runTestsButton.setDisable(true);

        clearResultsButton = new Button("Clear Results");
        clearResultsButton.getStyleClass().add("secondary-button");
        clearResultsButton.setPrefWidth(120);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        // Results display
        resultsTable = createResultsTable();

        detailsTextArea = new TextArea();
        detailsTextArea.setEditable(false);
        detailsTextArea.setPrefHeight(120);
        detailsTextArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 11px;");
        detailsTextArea.setPromptText("Select a test result to view details...");
    }

    /**
     * Create the test results table
     */
    private TableView<TestResult> createResultsTable() {
        TableView<TestResult> table = new TableView<>(testResults);
        table.setPrefHeight(200);

        // Test file column
        TableColumn<TestResult, String> fileColumn = new TableColumn<>("Test File");
        fileColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileColumn.setPrefWidth(200);

        // Status column
        TableColumn<TestResult, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setPrefWidth(80);
        statusColumn.setCellFactory(column -> new TableCell<TestResult, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status.toLowerCase()) {
                        case "passed" -> setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                        case "failed" -> setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                        case "error" -> setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                        default -> setStyle("");
                    }
                }
            }
        });

        // Errors column
        TableColumn<TestResult, Integer> errorsColumn = new TableColumn<>("Errors");
        errorsColumn.setCellValueFactory(new PropertyValueFactory<>("errorCount"));
        errorsColumn.setPrefWidth(60);

        // Warnings column
        TableColumn<TestResult, Integer> warningsColumn = new TableColumn<>("Warnings");
        warningsColumn.setCellValueFactory(new PropertyValueFactory<>("warningCount"));
        warningsColumn.setPrefWidth(70);

        // Duration column
        TableColumn<TestResult, String> durationColumn = new TableColumn<>("Duration");
        durationColumn.setCellValueFactory(new PropertyValueFactory<>("duration"));
        durationColumn.setPrefWidth(80);

        // Timestamp column
        TableColumn<TestResult, String> timestampColumn = new TableColumn<>("Time");
        timestampColumn.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        timestampColumn.setPrefWidth(150);

        table.getColumns().addAll(fileColumn, statusColumn, errorsColumn, warningsColumn, durationColumn, timestampColumn);

        return table;
    }

    /**
     * Layout all components in the UI
     */
    private void layoutComponents() {
        // Header
        Label headerLabel = new Label("Schematron Testing Framework");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        // Schematron file section
        VBox schematronSection = createSection("Schematron File",
                createSchematronFileControls());

        // Test files section  
        VBox testFilesSection = createSection("XML Test Files",
                createTestFilesControls());

        // Test execution section
        VBox executionSection = createSection("Test Execution",
                createExecutionControls());

        // Results section
        VBox resultsSection = createSection("Test Results",
                createResultsControls());

        // Add all sections
        this.getChildren().addAll(
                headerLabel,
                new Separator(),
                schematronSection,
                testFilesSection,
                executionSection,
                resultsSection
        );
    }

    /**
     * Create a titled section with content
     */
    private VBox createSection(String title, VBox content) {
        VBox section = new VBox(5);
        section.setPadding(new Insets(5));
        section.getStyleClass().add("testing-section");

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        titleLabel.getStyleClass().add("section-title");

        section.getChildren().addAll(titleLabel, content);

        return section;
    }

    /**
     * Create Schematron file selection controls
     */
    private VBox createSchematronFileControls() {
        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        fileRow.getChildren().addAll(
                new Label("File:"),
                schematronFileField,
                browseSchematronButton
        );
        HBox.setHgrow(schematronFileField, Priority.ALWAYS);

        VBox controls = new VBox(5);
        controls.getChildren().addAll(fileRow);

        return controls;
    }

    /**
     * Create test files management controls
     */
    private VBox createTestFilesControls() {
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(addTestFileButton, removeTestFileButton);

        VBox controls = new VBox(5);
        controls.getChildren().addAll(buttonRow, testFilesListView);

        return controls;
    }

    /**
     * Create test execution controls
     */
    private VBox createExecutionControls() {
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(runTestsButton, clearResultsButton);

        HBox statusRow = new HBox(10);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getChildren().addAll(statusLabel, progressBar);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        VBox controls = new VBox(5);
        controls.getChildren().addAll(buttonRow, statusRow);

        return controls;
    }

    /**
     * Create results display controls
     */
    private VBox createResultsControls() {
        Label tableLabel = new Label("Test Results:");
        tableLabel.setFont(Font.font("System", FontWeight.BOLD, 11));

        Label detailsLabel = new Label("Details:");
        detailsLabel.setFont(Font.font("System", FontWeight.BOLD, 11));

        VBox controls = new VBox(5);
        controls.getChildren().addAll(tableLabel, resultsTable, detailsLabel, detailsTextArea);
        VBox.setVgrow(resultsTable, Priority.ALWAYS);

        return controls;
    }

    /**
     * Set up event handlers for all interactive components
     */
    private void setupEventHandlers() {
        browseSchematronButton.setOnAction(e -> browseSchematronFile());
        addTestFileButton.setOnAction(e -> addTestFiles());
        removeTestFileButton.setOnAction(e -> removeSelectedTestFiles());
        runTestsButton.setOnAction(e -> runTests());
        clearResultsButton.setOnAction(e -> clearResults());

        // Enable/disable buttons based on selection
        testFilesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeTestFileButton.setDisable(newVal == null);
        });

        // Update test button state
        updateTestButtonState();
        testFiles.addListener((javafx.collections.ListChangeListener<? super TestFile>) change -> updateTestButtonState());

        // Show details when result is selected
        resultsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showTestResultDetails(newVal);
            }
        });
    }

    /**
     * Browse for Schematron file
     */
    private void browseSchematronFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Schematron File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Schematron files", "*.sch", "*.schematron"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(this.getScene().getWindow());
        if (file != null) {
            currentSchematronFile = file;
            schematronFileField.setText(file.getAbsolutePath());
            updateTestButtonState();
            logger.debug("Selected Schematron file: {}", file.getName());
        }
    }

    /**
     * Add XML test files
     */
    private void addTestFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select XML Test Files");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("XML files", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        List<File> files = fileChooser.showOpenMultipleDialog(this.getScene().getWindow());
        if (files != null) {
            for (File file : files) {
                TestFile testFile = new TestFile(file);
                if (!testFiles.contains(testFile)) {
                    testFiles.add(testFile);
                    logger.debug("Added test file: {}", file.getName());
                }
            }
            updateTestButtonState();
        }
    }

    /**
     * Remove selected test files
     */
    private void removeSelectedTestFiles() {
        TestFile selectedFile = testFilesListView.getSelectionModel().getSelectedItem();
        if (selectedFile != null) {
            testFiles.remove(selectedFile);
            updateTestButtonState();
            logger.debug("Removed test file: {}", selectedFile.file().getName());
        }
    }

    /**
     * Update the test button enabled state
     */
    private void updateTestButtonState() {
        boolean canRunTests = currentSchematronFile != null &&
                !testFiles.isEmpty() &&
                !testingInProgress;
        runTestsButton.setDisable(!canRunTests);
    }

    /**
     * Run tests against all XML files
     */
    private void runTests() {
        if (currentSchematronFile == null || testFiles.isEmpty()) {
            return;
        }

        testingInProgress = true;
        updateTestButtonState();

        progressBar.setProgress(0);
        progressBar.setVisible(true);
        statusLabel.setText("Running tests...");

        // Clear previous results
        testResults.clear();
        detailsTextArea.clear();

        // Run tests in background thread to avoid UI blocking
        Thread testThread = new Thread(() -> {
            try {
                runTestsInBackground();
            } catch (Exception e) {
                logger.error("Error during test execution", e);
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    showAlert("Test Error", "An error occurred during testing: " + e.getMessage());
                });
            } finally {
                javafx.application.Platform.runLater(() -> {
                    testingInProgress = false;
                    updateTestButtonState();
                    progressBar.setVisible(false);
                });
            }
        });

        testThread.setDaemon(true);
        testThread.start();
    }

    /**
     * Run tests in background thread
     */
    private void runTestsInBackground() {
        int totalFiles = testFiles.size();
        int completedFiles = 0;

        for (TestFile testFile : testFiles) {
            final int currentIndex = completedFiles;

            javafx.application.Platform.runLater(() -> {
                statusLabel.setText(String.format("Testing %s (%d/%d)",
                        testFile.file().getName(), currentIndex + 1, totalFiles));
                progressBar.setProgress((double) currentIndex / totalFiles);
            });

            // Perform the actual test
            TestResult result = performSchematronTest(testFile);

            // Update UI with result
            javafx.application.Platform.runLater(() -> {
                testResults.add(result);
            });

            completedFiles++;
        }

        // Final status update
        javafx.application.Platform.runLater(() -> {
            long passedTests = testResults.stream().mapToLong(r -> "Passed".equals(r.getStatus()) ? 1 : 0).sum();
            statusLabel.setText(String.format("Completed: %d passed, %d failed out of %d tests",
                    passedTests, totalFiles - passedTests, totalFiles));
            progressBar.setProgress(1.0);
        });

        logger.info("Completed testing {} files", totalFiles);
    }

    /**
     * Perform Schematron test on a single file with performance optimization
     */
    private TestResult performSchematronTest(TestFile testFile) {
        String fileName = testFile.file().getName();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try {
            long startTime = System.currentTimeMillis();

            // Check file size for performance optimization
            long fileSize = testFile.file().length();
            boolean isLargeFile = fileSize > 1024 * 1024; // 1MB threshold

            if (isLargeFile) {
                logger.info("Processing large XML file: {} ({} bytes)", fileName, fileSize);
                // Update status to show large file processing
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Processing large file: " + fileName + " (this may take longer)");
                });
            }

            // Use SchematronService to validate
            var validationResult = schematronService.validateXmlWithSchematron(
                    testFile.file(), currentSchematronFile);

            long endTime = System.currentTimeMillis();
            String duration = (endTime - startTime) + "ms";

            if (isLargeFile) {
                logger.info("Completed processing large file {} in {}", fileName, duration);
            }

            // Count errors and warnings from validation result
            int errorCount = 0;
            int warningCount = 0;
            StringBuilder details = new StringBuilder();

            if (validationResult.hasErrors()) {
                errorCount = validationResult.getErrors().size();
                details.append("ERRORS:\n");
                for (String error : validationResult.getErrors()) {
                    details.append("• ").append(error).append("\n");
                }
                details.append("\n");
            }

            if (validationResult.hasWarnings()) {
                warningCount = validationResult.getWarnings().size();
                details.append("WARNINGS:\n");
                for (String warning : validationResult.getWarnings()) {
                    details.append("• ").append(warning).append("\n");
                }
            }

            String status = errorCount > 0 ? "Failed" : "Passed";

            return new TestResult(fileName, status, errorCount, warningCount,
                    duration, timestamp, details.toString());

        } catch (Exception e) {
            logger.error("Error testing file: {}", fileName, e);
            return new TestResult(fileName, "Error", 0, 0, "N/A", timestamp,
                    "Error: " + e.getMessage());
        }
    }

    /**
     * Clear all test results
     */
    private void clearResults() {
        testResults.clear();
        detailsTextArea.clear();
        statusLabel.setText("Ready");
        progressBar.setProgress(0);

        logger.debug("Cleared test results");
    }

    /**
     * Show detailed information for a test result
     */
    private void showTestResultDetails(TestResult result) {
        String details = "Test File: " + result.getFileName() + "\n" +
                "Status: " + result.getStatus() + "\n" +
                "Errors: " + result.getErrorCount() + "\n" +
                "Warnings: " + result.getWarningCount() + "\n" +
                "Duration: " + result.getDuration() + "\n" +
                "Time: " + result.getTimestamp() + "\n" +
                "\n" + "Details:\n" +
                result.getDetails();

        detailsTextArea.setText(details);
    }

    /**
     * Set the current Schematron file for testing
     */
    public void setSchematronFile(File schematronFile) {
        this.currentSchematronFile = schematronFile;
        if (schematronFile != null) {
            schematronFileField.setText(schematronFile.getAbsolutePath());
        } else {
            schematronFileField.clear();
        }
        updateTestButtonState();
    }

    /**
     * Show an alert dialog
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========== Inner Classes ==========

    /**
         * Represents a test file in the list
         */
        public record TestFile(File file) {

        @Override
            public String toString() {
                return file.getName();
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                TestFile testFile = (TestFile) obj;
                return file.equals(testFile.file);
            }

    }

    /**
     * Custom list cell for test files
     */
    private static class TestFileListCell extends ListCell<TestFile> {
        @Override
        protected void updateItem(TestFile item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.file().getName());
                setTooltip(new Tooltip(item.file().getAbsolutePath()));
            }
        }
    }

    /**
     * Represents a test result
     */
    public static class TestResult {
        private final StringProperty fileName = new SimpleStringProperty();
        private final StringProperty status = new SimpleStringProperty();
        private final Integer errorCount;
        private final Integer warningCount;
        private final StringProperty duration = new SimpleStringProperty();
        private final StringProperty timestamp = new SimpleStringProperty();
        private final String details;

        public TestResult(String fileName, String status, Integer errorCount,
                          Integer warningCount, String duration, String timestamp, String details) {
            this.fileName.set(fileName);
            this.status.set(status);
            this.errorCount = errorCount;
            this.warningCount = warningCount;
            this.duration.set(duration);
            this.timestamp.set(timestamp);
            this.details = details;
        }

        // Property getters for TableView
        public String getFileName() {
            return fileName.get();
        }

        public StringProperty fileNameProperty() {
            return fileName;
        }

        public String getStatus() {
            return status.get();
        }

        public StringProperty statusProperty() {
            return status;
        }

        public Integer getErrorCount() {
            return errorCount;
        }

        public Integer getWarningCount() {
            return warningCount;
        }

        public String getDuration() {
            return duration.get();
        }

        public StringProperty durationProperty() {
            return duration;
        }

        public String getTimestamp() {
            return timestamp.get();
        }

        public StringProperty timestampProperty() {
            return timestamp;
        }

        public String getDetails() {
            return details;
        }
    }
}