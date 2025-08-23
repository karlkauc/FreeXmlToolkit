package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * Visual Rule Builder for creating Schematron rules through a graphical interface.
 * Provides drag & drop functionality and form-based rule creation.
 */
public class SchematronVisualBuilder extends VBox {

    private static final Logger logger = LogManager.getLogger(SchematronVisualBuilder.class);

    // UI Components
    private ComboBox<String> patternSelector;
    private TextField patternIdField;
    private TextField patternTitleField;
    private TextField ruleContextField;
    private TextField ruleIdField;
    private TableView<AssertionItem> assertionsTable;
    private TableView<ReportItem> reportsTable;
    private TextArea previewArea;
    private Button addPatternButton;
    private Button addRuleButton;
    private Button addAssertionButton;
    private Button addReportButton;
    private Button generateCodeButton;

    // Data models
    private final ObservableList<PatternItem> patterns = FXCollections.observableArrayList();
    private final ObservableList<AssertionItem> assertions = FXCollections.observableArrayList();
    private final ObservableList<ReportItem> reports = FXCollections.observableArrayList();

    /**
     * Constructor - Initialize the Visual Rule Builder
     */
    public SchematronVisualBuilder() {
        this.setSpacing(10);
        this.setPadding(new Insets(10));

        initializeComponents();
        layoutComponents();
        setupEventHandlers();

        logger.debug("SchematronVisualBuilder initialized");
    }

    /**
     * Initialize all UI components
     */
    private void initializeComponents() {
        // Pattern section
        patternSelector = new ComboBox<>();
        patternSelector.setPromptText("Select pattern or create new");
        patternSelector.setPrefWidth(250);

        patternIdField = new TextField();
        patternIdField.setPromptText("Pattern ID (optional)");
        patternIdField.setPrefWidth(200);

        patternTitleField = new TextField();
        patternTitleField.setPromptText("Pattern Title");
        patternTitleField.setPrefWidth(300);

        addPatternButton = new Button("Add Pattern");
        addPatternButton.getStyleClass().add("primary-button");

        // Rule section
        ruleContextField = new TextField();
        ruleContextField.setPromptText("XPath context (e.g., //Order, /root/element)");
        ruleContextField.setPrefWidth(400);

        ruleIdField = new TextField();
        ruleIdField.setPromptText("Rule ID (optional)");
        ruleIdField.setPrefWidth(200);

        addRuleButton = new Button("Add Rule");
        addRuleButton.getStyleClass().add("primary-button");

        // Assertions table
        assertionsTable = createAssertionsTable();
        addAssertionButton = new Button("Add Assertion");
        addAssertionButton.getStyleClass().add("secondary-button");

        // Reports table
        reportsTable = createReportsTable();
        addReportButton = new Button("Add Report");
        addReportButton.getStyleClass().add("secondary-button");

        // Preview and generation
        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefHeight(200);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 11px;");

        generateCodeButton = new Button("Generate Schematron Code");
        generateCodeButton.getStyleClass().add("primary-button");
        generateCodeButton.setPrefWidth(200);
    }

    /**
     * Create the assertions table
     */
    private TableView<AssertionItem> createAssertionsTable() {
        TableView<AssertionItem> table = new TableView<>();
        table.setEditable(true);
        table.setPrefHeight(150);

        // Test column
        TableColumn<AssertionItem, String> testColumn = new TableColumn<>("XPath Test");
        testColumn.setCellValueFactory(new PropertyValueFactory<>("test"));
        testColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        testColumn.setPrefWidth(200);
        testColumn.setOnEditCommit(event -> {
            event.getRowValue().setTest(event.getNewValue());
            updatePreview();
        });

        // Message column
        TableColumn<AssertionItem, String> messageColumn = new TableColumn<>("Message");
        messageColumn.setCellValueFactory(new PropertyValueFactory<>("message"));
        messageColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        messageColumn.setPrefWidth(250);
        messageColumn.setOnEditCommit(event -> {
            event.getRowValue().setMessage(event.getNewValue());
            updatePreview();
        });

        // Flag column
        TableColumn<AssertionItem, String> flagColumn = new TableColumn<>("Flag");
        flagColumn.setCellValueFactory(new PropertyValueFactory<>("flag"));
        flagColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        flagColumn.setPrefWidth(100);
        flagColumn.setOnEditCommit(event -> {
            event.getRowValue().setFlag(event.getNewValue());
            updatePreview();
        });

        // Actions column
        TableColumn<AssertionItem, Void> actionsColumn = new TableColumn<>("Actions");
        actionsColumn.setCellFactory(param -> new TableCell<AssertionItem, Void>() {
            private final Button deleteButton = new Button("Delete");

            {
                deleteButton.setOnAction(event -> {
                    AssertionItem item = getTableView().getItems().get(getIndex());
                    assertions.remove(item);
                    updatePreview();
                });
                deleteButton.getStyleClass().add("danger-button");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteButton);
                }
            }
        });
        actionsColumn.setPrefWidth(80);

        table.getColumns().addAll(testColumn, messageColumn, flagColumn, actionsColumn);
        table.setItems(assertions);

        return table;
    }

    /**
     * Create the reports table
     */
    private TableView<ReportItem> createReportsTable() {
        TableView<ReportItem> table = new TableView<>();
        table.setEditable(true);
        table.setPrefHeight(150);

        // Test column
        TableColumn<ReportItem, String> testColumn = new TableColumn<>("XPath Test");
        testColumn.setCellValueFactory(new PropertyValueFactory<>("test"));
        testColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        testColumn.setPrefWidth(200);
        testColumn.setOnEditCommit(event -> {
            event.getRowValue().setTest(event.getNewValue());
            updatePreview();
        });

        // Message column
        TableColumn<ReportItem, String> messageColumn = new TableColumn<>("Message");
        messageColumn.setCellValueFactory(new PropertyValueFactory<>("message"));
        messageColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        messageColumn.setPrefWidth(250);
        messageColumn.setOnEditCommit(event -> {
            event.getRowValue().setMessage(event.getNewValue());
            updatePreview();
        });

        // Flag column
        TableColumn<ReportItem, String> flagColumn = new TableColumn<>("Flag");
        flagColumn.setCellValueFactory(new PropertyValueFactory<>("flag"));
        flagColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        flagColumn.setPrefWidth(100);
        flagColumn.setOnEditCommit(event -> {
            event.getRowValue().setFlag(event.getNewValue());
            updatePreview();
        });

        // Actions column
        TableColumn<ReportItem, Void> actionsColumn = new TableColumn<>("Actions");
        actionsColumn.setCellFactory(param -> new TableCell<ReportItem, Void>() {
            private final Button deleteButton = new Button("Delete");

            {
                deleteButton.setOnAction(event -> {
                    ReportItem item = getTableView().getItems().get(getIndex());
                    reports.remove(item);
                    updatePreview();
                });
                deleteButton.getStyleClass().add("danger-button");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteButton);
                }
            }
        });
        actionsColumn.setPrefWidth(80);

        table.getColumns().addAll(testColumn, messageColumn, flagColumn, actionsColumn);
        table.setItems(reports);

        return table;
    }

    /**
     * Layout all components in the UI
     */
    private void layoutComponents() {
        // Header
        Label headerLabel = new Label("Visual Schematron Rule Builder");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        // Pattern section
        VBox patternSection = createSection("Pattern Configuration",
                createPatternControls());

        // Rule section
        VBox ruleSection = createSection("Rule Configuration",
                createRuleControls());

        // Assertions section
        VBox assertionsSection = createSection("Assertions (Must be true)",
                createAssertionsControls());

        // Reports section
        VBox reportsSection = createSection("Reports (Warnings when true)",
                createReportsControls());

        // Preview section
        VBox previewSection = createSection("Generated Code Preview",
                createPreviewControls());

        // Add all sections
        this.getChildren().addAll(
                headerLabel,
                new Separator(),
                patternSection,
                ruleSection,
                assertionsSection,
                reportsSection,
                previewSection
        );
    }

    /**
     * Create a titled section with content
     */
    private VBox createSection(String title, VBox content) {
        VBox section = new VBox(5);
        section.setPadding(new Insets(5));
        section.getStyleClass().add("visual-builder-section");

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        titleLabel.getStyleClass().add("section-title");

        section.getChildren().addAll(titleLabel, content);

        return section;
    }

    /**
     * Create pattern configuration controls
     */
    private VBox createPatternControls() {
        HBox patternRow1 = new HBox(10);
        patternRow1.setAlignment(Pos.CENTER_LEFT);
        patternRow1.getChildren().addAll(
                new Label("Pattern:"), patternSelector,
                new Label("ID:"), patternIdField
        );

        HBox patternRow2 = new HBox(10);
        patternRow2.setAlignment(Pos.CENTER_LEFT);
        patternRow2.getChildren().addAll(
                new Label("Title:"), patternTitleField,
                addPatternButton
        );

        VBox patternControls = new VBox(5);
        patternControls.getChildren().addAll(patternRow1, patternRow2);

        return patternControls;
    }

    /**
     * Create rule configuration controls
     */
    private VBox createRuleControls() {
        HBox ruleRow = new HBox(10);
        ruleRow.setAlignment(Pos.CENTER_LEFT);
        ruleRow.getChildren().addAll(
                new Label("Context:"), ruleContextField,
                new Label("ID:"), ruleIdField,
                addRuleButton
        );

        VBox ruleControls = new VBox(5);
        ruleControls.getChildren().addAll(ruleRow);

        return ruleControls;
    }

    /**
     * Create assertions section controls
     */
    private VBox createAssertionsControls() {
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(addAssertionButton);

        VBox assertionsControls = new VBox(5);
        assertionsControls.getChildren().addAll(buttonRow, assertionsTable);

        return assertionsControls;
    }

    /**
     * Create reports section controls
     */
    private VBox createReportsControls() {
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(addReportButton);

        VBox reportsControls = new VBox(5);
        reportsControls.getChildren().addAll(buttonRow, reportsTable);

        return reportsControls;
    }

    /**
     * Create preview section controls
     */
    private VBox createPreviewControls() {
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(generateCodeButton);

        VBox previewControls = new VBox(5);
        previewControls.getChildren().addAll(buttonRow, previewArea);

        return previewControls;
    }

    /**
     * Set up event handlers for all interactive components
     */
    private void setupEventHandlers() {
        addPatternButton.setOnAction(e -> addNewPattern());
        addRuleButton.setOnAction(e -> addNewRule());
        addAssertionButton.setOnAction(e -> addNewAssertion());
        addReportButton.setOnAction(e -> addNewReport());
        generateCodeButton.setOnAction(e -> generateAndDisplayCode());

        // Update preview when fields change
        patternTitleField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        ruleContextField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        ruleIdField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview());
    }

    /**
     * Add a new pattern
     */
    private void addNewPattern() {
        String title = patternTitleField.getText().trim();
        String id = patternIdField.getText().trim();

        if (title.isEmpty()) {
            showAlert("Pattern title is required");
            return;
        }

        PatternItem pattern = new PatternItem(id.isEmpty() ? null : id, title);
        patterns.add(pattern);
        patternSelector.getItems().add(pattern.getDisplayName());
        patternSelector.setValue(pattern.getDisplayName());

        // Clear fields
        patternIdField.clear();
        patternTitleField.clear();

        updatePreview();
        logger.debug("Added new pattern: {}", title);
    }

    /**
     * Add a new rule (currently just updates UI state)
     */
    private void addNewRule() {
        String context = ruleContextField.getText().trim();

        if (context.isEmpty()) {
            showAlert("Rule context is required");
            return;
        }

        updatePreview();
        logger.debug("Rule context set: {}", context);
    }

    /**
     * Add a new assertion
     */
    private void addNewAssertion() {
        AssertionDialog dialog = new AssertionDialog();
        Optional<AssertionItem> result = dialog.showAndWait();

        result.ifPresent(assertion -> {
            assertions.add(assertion);
            updatePreview();
            logger.debug("Added assertion: {}", assertion.getTest());
        });
    }

    /**
     * Add a new report
     */
    private void addNewReport() {
        ReportDialog dialog = new ReportDialog();
        Optional<ReportItem> result = dialog.showAndWait();

        result.ifPresent(report -> {
            reports.add(report);
            updatePreview();
            logger.debug("Added report: {}", report.getTest());
        });
    }

    /**
     * Generate and display the complete Schematron code
     */
    private void generateAndDisplayCode() {
        String code = generateSchematronCode();
        previewArea.setText(code);
        logger.debug("Generated Schematron code preview");
    }

    /**
     * Update the preview area with current configuration
     */
    private void updatePreview() {
        if (previewArea != null) {
            String preview = generateSchematronCode();
            previewArea.setText(preview);
        }
    }

    /**
     * Generate complete Schematron code from current configuration
     */
    private String generateSchematronCode() {
        StringBuilder code = new StringBuilder();

        // Schema header
        code.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        code.append("<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"\n");
        code.append("        xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n");
        code.append("        queryBinding=\"xslt2\">\n\n");

        // Schema title
        code.append("    <title>Generated Schematron Rules</title>\n\n");

        // Pattern
        String patternTitle = patternTitleField.getText().trim();
        String patternId = patternIdField.getText().trim();

        if (patternTitle.isEmpty()) {
            patternTitle = "Generated Pattern";
        }

        code.append("    <pattern");
        if (!patternId.isEmpty()) {
            code.append(" id=\"").append(patternId).append("\"");
        }
        code.append(">\n");
        code.append("        <title>").append(patternTitle).append("</title>\n\n");

        // Rule
        String context = ruleContextField.getText().trim();
        String ruleId = ruleIdField.getText().trim();

        if (!context.isEmpty()) {
            code.append("        <rule");
            if (!ruleId.isEmpty()) {
                code.append(" id=\"").append(ruleId).append("\"");
            }
            code.append(" context=\"").append(context).append("\">\n");

            // Assertions
            for (AssertionItem assertion : assertions) {
                code.append("            <assert test=\"").append(assertion.getTest()).append("\"");
                if (assertion.getFlag() != null && !assertion.getFlag().trim().isEmpty()) {
                    code.append(" flag=\"").append(assertion.getFlag()).append("\"");
                }
                code.append(">\n");
                code.append("                ").append(assertion.getMessage()).append("\n");
                code.append("            </assert>\n");
            }

            // Reports
            for (ReportItem report : reports) {
                code.append("            <report test=\"").append(report.getTest()).append("\"");
                if (report.getFlag() != null && !report.getFlag().trim().isEmpty()) {
                    code.append(" flag=\"").append(report.getFlag()).append("\"");
                }
                code.append(">\n");
                code.append("                ").append(report.getMessage()).append("\n");
                code.append("            </report>\n");
            }

            code.append("        </rule>\n");
        } else {
            code.append("        <!-- Add rules here -->\n");
        }

        code.append("\n    </pattern>\n\n");
        code.append("</schema>");

        return code.toString();
    }

    /**
     * Get the generated Schematron code
     */
    public String getGeneratedCode() {
        return generateSchematronCode();
    }

    /**
     * Clear all form data
     */
    public void clearAll() {
        patternIdField.clear();
        patternTitleField.clear();
        ruleContextField.clear();
        ruleIdField.clear();
        assertions.clear();
        reports.clear();
        patterns.clear();
        patternSelector.getItems().clear();
        previewArea.clear();

        logger.debug("Cleared all visual builder data");
    }

    /**
     * Show an alert dialog
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Input Required");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========== Inner Classes ==========

    /**
     * Pattern data model
     */
    public static class PatternItem {
        private final String id;
        private final String title;

        public PatternItem(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getDisplayName() {
            return id != null ? title + " (" + id + ")" : title;
        }
    }

    /**
     * Assertion data model
     */
    public static class AssertionItem {
        private final StringProperty test = new SimpleStringProperty("");
        private final StringProperty message = new SimpleStringProperty("");
        private final StringProperty flag = new SimpleStringProperty("");

        public AssertionItem() {
        }

        public AssertionItem(String test, String message, String flag) {
            this.test.set(test);
            this.message.set(message);
            this.flag.set(flag);
        }

        public String getTest() {
            return test.get();
        }

        public void setTest(String test) {
            this.test.set(test);
        }

        public StringProperty testProperty() {
            return test;
        }

        public String getMessage() {
            return message.get();
        }

        public void setMessage(String message) {
            this.message.set(message);
        }

        public StringProperty messageProperty() {
            return message;
        }

        public String getFlag() {
            return flag.get();
        }

        public void setFlag(String flag) {
            this.flag.set(flag);
        }

        public StringProperty flagProperty() {
            return flag;
        }
    }

    /**
     * Report data model
     */
    public static class ReportItem {
        private final StringProperty test = new SimpleStringProperty("");
        private final StringProperty message = new SimpleStringProperty("");
        private final StringProperty flag = new SimpleStringProperty("");

        public ReportItem() {
        }

        public ReportItem(String test, String message, String flag) {
            this.test.set(test);
            this.message.set(message);
            this.flag.set(flag);
        }

        public String getTest() {
            return test.get();
        }

        public void setTest(String test) {
            this.test.set(test);
        }

        public StringProperty testProperty() {
            return test;
        }

        public String getMessage() {
            return message.get();
        }

        public void setMessage(String message) {
            this.message.set(message);
        }

        public StringProperty messageProperty() {
            return message;
        }

        public String getFlag() {
            return flag.get();
        }

        public void setFlag(String flag) {
            this.flag.set(flag);
        }

        public StringProperty flagProperty() {
            return flag;
        }
    }

    /**
     * Dialog for adding new assertions
     */
    private static class AssertionDialog extends Dialog<AssertionItem> {
        public AssertionDialog() {
            setTitle("Add Assertion");
            setHeaderText("Create a new assertion rule");

            ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField testField = new TextField();
            testField.setPromptText("XPath expression (e.g., count(item) > 0)");
            testField.setPrefWidth(300);

            TextField messageField = new TextField();
            messageField.setPromptText("Error message");
            messageField.setPrefWidth(300);

            TextField flagField = new TextField();
            flagField.setPromptText("Flag (optional)");
            flagField.setPrefWidth(200);

            grid.add(new Label("Test:"), 0, 0);
            grid.add(testField, 1, 0);
            grid.add(new Label("Message:"), 0, 1);
            grid.add(messageField, 1, 1);
            grid.add(new Label("Flag:"), 0, 2);
            grid.add(flagField, 1, 2);

            getDialogPane().setContent(grid);

            setResultConverter(dialogButton -> {
                if (dialogButton == addButtonType) {
                    return new AssertionItem(testField.getText(), messageField.getText(), flagField.getText());
                }
                return null;
            });
        }
    }

    /**
     * Dialog for adding new reports
     */
    private static class ReportDialog extends Dialog<ReportItem> {
        public ReportDialog() {
            setTitle("Add Report");
            setHeaderText("Create a new report rule");

            ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField testField = new TextField();
            testField.setPromptText("XPath expression (e.g., @deprecated = 'true')");
            testField.setPrefWidth(300);

            TextField messageField = new TextField();
            messageField.setPromptText("Warning message");
            messageField.setPrefWidth(300);

            TextField flagField = new TextField();
            flagField.setPromptText("Flag (optional)");
            flagField.setPrefWidth(200);

            grid.add(new Label("Test:"), 0, 0);
            grid.add(testField, 1, 0);
            grid.add(new Label("Message:"), 0, 1);
            grid.add(messageField, 1, 1);
            grid.add(new Label("Flag:"), 0, 2);
            grid.add(flagField, 1, 2);

            getDialogPane().setContent(grid);

            setResultConverter(dialogButton -> {
                if (dialogButton == addButtonType) {
                    return new ReportItem(testField.getText(), messageField.getText(), flagField.getText());
                }
                return null;
            });
        }
    }
}