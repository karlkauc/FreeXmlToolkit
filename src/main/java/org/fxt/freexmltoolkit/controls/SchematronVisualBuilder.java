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
 * Provides drag &amp; drop functionality and form-based rule creation.
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
     * Creates and configures the assertions table with editable columns
     * for test expression, message, flag, and delete action.
     *
     * @return the configured TableView for assertion items
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
     * Creates and configures the reports table with editable columns
     * for test expression, message, flag, and delete action.
     *
     * @return the configured TableView for report items
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
     * Creates a titled section with the specified content.
     * Each section has a bold title label and styled container.
     *
     * @param title the title text for the section
     * @param content the VBox containing the section content
     * @return the configured VBox container for the section
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
     * Creates the pattern configuration controls including pattern selector,
     * ID field, title field, and add button.
     *
     * @return the VBox containing pattern configuration controls
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
     * Creates the rule configuration controls including context field,
     * ID field, and add rule button.
     *
     * @return the VBox containing rule configuration controls
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
     * Creates the assertions section controls including the add assertion button
     * and the assertions table.
     *
     * @return the VBox containing assertions section controls
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
     * Creates the reports section controls including the add report button
     * and the reports table.
     *
     * @return the VBox containing reports section controls
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
     * Creates the preview section controls including the generate button
     * and the code preview text area.
     *
     * @return the VBox containing preview section controls
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
     * Returns the generated Schematron code based on the current configuration.
     * This method builds a complete Schematron schema document from the patterns,
     * rules, assertions, and reports defined through the visual builder interface.
     *
     * @return the complete Schematron XML code as a string
     */
    public String getGeneratedCode() {
        return generateSchematronCode();
    }

    /**
     * Clears all form data and resets the visual builder to its initial state.
     * This includes clearing pattern, rule, assertion, and report fields,
     * as well as the preview area.
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
     * Shows a warning alert dialog with the specified message.
     *
     * @param message the message to display in the alert dialog
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
     * Pattern data model that represents a Schematron pattern
     * with an optional ID and a required title.
     */
    public static class PatternItem {
        private final String id;
        private final String title;

        /**
         * Creates a new pattern item with the specified ID and title.
         *
         * @param id the optional pattern ID, may be null
         * @param title the pattern title
         */
        public PatternItem(String id, String title) {
            this.id = id;
            this.title = title;
        }

        /**
         * Returns the pattern ID.
         *
         * @return the pattern ID, or null if not set
         */
        public String getId() {
            return id;
        }

        /**
         * Returns the pattern title.
         *
         * @return the pattern title
         */
        public String getTitle() {
            return title;
        }

        /**
         * Returns the display name for this pattern.
         * Format is "title (id)" if ID is present, otherwise just "title".
         *
         * @return the formatted display name
         */
        public String getDisplayName() {
            return id != null ? title + " (" + id + ")" : title;
        }
    }

    /**
     * Assertion data model that represents a Schematron assertion rule.
     * An assertion contains an XPath test expression, a message to display
     * when the test fails, and an optional flag attribute.
     */
    public static class AssertionItem {
        private final StringProperty test = new SimpleStringProperty("");
        private final StringProperty message = new SimpleStringProperty("");
        private final StringProperty flag = new SimpleStringProperty("");

        /**
         * Creates a new empty assertion item with default values.
         */
        public AssertionItem() {
        }

        /**
         * Creates a new assertion item with the specified values.
         *
         * @param test the XPath test expression
         * @param message the error message displayed when the test fails
         * @param flag the optional flag attribute
         */
        public AssertionItem(String test, String message, String flag) {
            this.test.set(test);
            this.message.set(message);
            this.flag.set(flag);
        }

        /**
         * Returns the XPath test expression.
         *
         * @return the test expression
         */
        public String getTest() {
            return test.get();
        }

        /**
         * Sets the XPath test expression.
         *
         * @param test the test expression to set
         */
        public void setTest(String test) {
            this.test.set(test);
        }

        /**
         * Returns the test property for JavaFX binding.
         *
         * @return the test StringProperty
         */
        public StringProperty testProperty() {
            return test;
        }

        /**
         * Returns the error message.
         *
         * @return the error message
         */
        public String getMessage() {
            return message.get();
        }

        /**
         * Sets the error message.
         *
         * @param message the error message to set
         */
        public void setMessage(String message) {
            this.message.set(message);
        }

        /**
         * Returns the message property for JavaFX binding.
         *
         * @return the message StringProperty
         */
        public StringProperty messageProperty() {
            return message;
        }

        /**
         * Returns the flag attribute value.
         *
         * @return the flag attribute value
         */
        public String getFlag() {
            return flag.get();
        }

        /**
         * Sets the flag attribute value.
         *
         * @param flag the flag attribute value to set
         */
        public void setFlag(String flag) {
            this.flag.set(flag);
        }

        /**
         * Returns the flag property for JavaFX binding.
         *
         * @return the flag StringProperty
         */
        public StringProperty flagProperty() {
            return flag;
        }
    }

    /**
     * Report data model that represents a Schematron report rule.
     * A report contains an XPath test expression, a warning message to display
     * when the test evaluates to true, and an optional flag attribute.
     */
    public static class ReportItem {
        private final StringProperty test = new SimpleStringProperty("");
        private final StringProperty message = new SimpleStringProperty("");
        private final StringProperty flag = new SimpleStringProperty("");

        /**
         * Creates a new empty report item with default values.
         */
        public ReportItem() {
        }

        /**
         * Creates a new report item with the specified values.
         *
         * @param test the XPath test expression
         * @param message the warning message displayed when the test is true
         * @param flag the optional flag attribute
         */
        public ReportItem(String test, String message, String flag) {
            this.test.set(test);
            this.message.set(message);
            this.flag.set(flag);
        }

        /**
         * Returns the XPath test expression.
         *
         * @return the test expression
         */
        public String getTest() {
            return test.get();
        }

        /**
         * Sets the XPath test expression.
         *
         * @param test the test expression to set
         */
        public void setTest(String test) {
            this.test.set(test);
        }

        /**
         * Returns the test property for JavaFX binding.
         *
         * @return the test StringProperty
         */
        public StringProperty testProperty() {
            return test;
        }

        /**
         * Returns the warning message.
         *
         * @return the warning message
         */
        public String getMessage() {
            return message.get();
        }

        /**
         * Sets the warning message.
         *
         * @param message the warning message to set
         */
        public void setMessage(String message) {
            this.message.set(message);
        }

        /**
         * Returns the message property for JavaFX binding.
         *
         * @return the message StringProperty
         */
        public StringProperty messageProperty() {
            return message;
        }

        /**
         * Returns the flag attribute value.
         *
         * @return the flag attribute value
         */
        public String getFlag() {
            return flag.get();
        }

        /**
         * Sets the flag attribute value.
         *
         * @param flag the flag attribute value to set
         */
        public void setFlag(String flag) {
            this.flag.set(flag);
        }

        /**
         * Returns the flag property for JavaFX binding.
         *
         * @return the flag StringProperty
         */
        public StringProperty flagProperty() {
            return flag;
        }
    }

    /**
     * Dialog for adding new assertions.
     * Provides a form-based interface for creating new assertion rules
     * with test expression, message, and optional flag fields.
     */
    private static class AssertionDialog extends Dialog<AssertionItem> {
        /**
         * Creates a new assertion dialog with input fields for test, message, and flag.
         */
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
     * Dialog for adding new reports.
     * Provides a form-based interface for creating new report rules
     * with test expression, message, and optional flag fields.
     */
    private static class ReportDialog extends Dialog<ReportItem> {
        /**
         * Creates a new report dialog with input fields for test, message, and flag.
         */
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