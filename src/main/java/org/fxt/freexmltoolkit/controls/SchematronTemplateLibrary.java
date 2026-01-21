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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Template library for common Schematron patterns and rules.
 * Provides a collection of reusable templates for business rules validation.
 */
public class SchematronTemplateLibrary extends VBox {

    private static final Logger logger = LogManager.getLogger(SchematronTemplateLibrary.class);

    // UI Components
    private ComboBox<String> categoryComboBox;
    private TextField searchField;
    private TableView<TemplateItem> templatesTable;
    private TextArea previewArea;
    private Button insertButton;
    private Button customizeButton;

    // Data models
    private final ObservableList<TemplateItem> allTemplates = FXCollections.observableArrayList();
    private final ObservableList<TemplateItem> filteredTemplates = FXCollections.observableArrayList();

    // Callback for inserting templates
    private Consumer<String> templateInsertCallback;

    /**
     * Constructor - Initialize the Template Library
     */
    public SchematronTemplateLibrary() {
        this.setSpacing(10);
        this.setPadding(new Insets(10));

        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        loadDefaultTemplates();

        logger.debug("SchematronTemplateLibrary initialized");
    }

    /**
     * Initialize all UI components
     */
    private void initializeComponents() {
        // Category filter
        categoryComboBox = new ComboBox<>();
        categoryComboBox.getItems().addAll(
                "All Categories",
                "Business Rules",
                "Data Validation",
                "Structure Validation",
                "Cross-References",
                "Financial",
                "Healthcare",
                "Government",
                "Custom"
        );
        categoryComboBox.setValue("All Categories");
        categoryComboBox.setPrefWidth(150);

        // Search
        searchField = new TextField();
        searchField.setPromptText("Search templates...");
        searchField.setPrefWidth(200);

        // Templates table
        templatesTable = createTemplatesTable();

        // Preview area
        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefHeight(200);
        previewArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 11px;");
        previewArea.setPromptText("Select a template to preview...");

        // Action buttons
        insertButton = new Button("Insert Template");
        insertButton.getStyleClass().add("primary-button");
        insertButton.setDisable(true);

        customizeButton = new Button("Customize & Insert");
        customizeButton.getStyleClass().add("secondary-button");
        customizeButton.setDisable(true);
    }

    /**
     * Create the templates table
     */
    private TableView<TemplateItem> createTemplatesTable() {
        TableView<TemplateItem> table = new TableView<>(filteredTemplates);
        table.setPrefHeight(250);

        // Name column
        TableColumn<TemplateItem, String> nameColumn = new TableColumn<>("Template");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(200);

        // Category column
        TableColumn<TemplateItem, String> categoryColumn = new TableColumn<>("Category");
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryColumn.setPrefWidth(120);

        // Description column
        TableColumn<TemplateItem, String> descColumn = new TableColumn<>("Description");
        descColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        descColumn.setPrefWidth(300);

        table.getColumns().addAll(nameColumn, categoryColumn, descColumn);

        return table;
    }

    /**
     * Layout all components in the UI
     */
    private void layoutComponents() {
        // Header
        Label headerLabel = new Label("Schematron Template Library");
        headerLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        // Filter section
        VBox filterSection = createSection("Filter Templates", createFilterControls());

        // Templates section
        VBox templatesSection = createSection("Available Templates", createTemplatesControls());

        // Preview section
        VBox previewSection = createSection("Template Preview", createPreviewControls());

        // Add all sections
        this.getChildren().addAll(
                headerLabel,
                new Separator(),
                filterSection,
                templatesSection,
                previewSection
        );

        // Make the templates table expand
        VBox.setVgrow(templatesSection, Priority.ALWAYS);
    }

    /**
     * Create a titled section with content
     */
    private VBox createSection(String title, VBox content) {
        VBox section = new VBox(5);
        section.setPadding(new Insets(5));
        section.getStyleClass().add("template-library-section");

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        titleLabel.getStyleClass().add("section-title");

        section.getChildren().addAll(titleLabel, content);

        return section;
    }

    /**
     * Create filter controls
     */
    private VBox createFilterControls() {
        HBox filterRow = new HBox(10);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getChildren().addAll(
                new Label("Category:"), categoryComboBox,
                new Label("Search:"), searchField
        );

        VBox controls = new VBox(5);
        controls.getChildren().addAll(filterRow);

        return controls;
    }

    /**
     * Create templates controls
     */
    private VBox createTemplatesControls() {
        VBox controls = new VBox(5);
        controls.getChildren().addAll(templatesTable);
        VBox.setVgrow(templatesTable, Priority.ALWAYS);

        return controls;
    }

    /**
     * Create preview controls
     */
    private VBox createPreviewControls() {
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(insertButton, customizeButton);

        VBox controls = new VBox(5);
        controls.getChildren().addAll(previewArea, buttonRow);

        return controls;
    }

    /**
     * Set up event handlers for all interactive components
     */
    private void setupEventHandlers() {
        // Filter by category
        categoryComboBox.setOnAction(e -> filterTemplates());

        // Search filter
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTemplates());

        // Template selection
        templatesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showTemplatePreview(newVal);
                insertButton.setDisable(false);
                customizeButton.setDisable(false);
            } else {
                previewArea.clear();
                insertButton.setDisable(true);
                customizeButton.setDisable(true);
            }
        });

        // Button actions
        insertButton.setOnAction(e -> insertSelectedTemplate());
        customizeButton.setOnAction(e -> customizeAndInsertTemplate());
    }

    /**
     * Load default template collection
     */
    private void loadDefaultTemplates() {
        allTemplates.clear();

        // Business Rules Templates
        allTemplates.add(new TemplateItem(
                "Mandatory Field Check",
                "Business Rules",
                "Ensures required fields are present and non-empty",
                createMandatoryFieldTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Conditional Field Check",
                "Business Rules",
                "Validates fields based on conditions",
                createConditionalFieldTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Enumeration Validation",
                "Data Validation",
                "Validates field values against allowed list",
                createEnumerationTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Date Range Validation",
                "Data Validation",
                "Validates dates within acceptable ranges",
                createDateRangeTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Numeric Range Check",
                "Data Validation",
                "Validates numeric values within min/max bounds",
                createNumericRangeTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Cross-Reference Validation",
                "Cross-References",
                "Validates references between elements",
                createCrossReferenceTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "ID Uniqueness Check",
                "Structure Validation",
                "Ensures ID attributes are unique",
                createIdUniquenessTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Element Order Validation",
                "Structure Validation",
                "Validates correct element ordering",
                createElementOrderTemplate()
        ));

        // Financial Templates
        allTemplates.add(new TemplateItem(
                "Currency Code Validation",
                "Financial",
                "Validates ISO currency codes",
                createCurrencyCodeTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Amount Precision Check",
                "Financial",
                "Validates decimal precision for amounts",
                createAmountPrecisionTemplate()
        ));

        // Healthcare Templates
        allTemplates.add(new TemplateItem(
                "Patient ID Format",
                "Healthcare",
                "Validates patient identifier formats",
                createPatientIdTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Medical Code Validation",
                "Healthcare",
                "Validates medical coding systems",
                createMedicalCodeTemplate()
        ));

        // Government Templates
        allTemplates.add(new TemplateItem(
                "Tax ID Validation",
                "Government",
                "Validates tax identification numbers",
                createTaxIdTemplate()
        ));

        allTemplates.add(new TemplateItem(
                "Legal Entity Validation",
                "Government",
                "Validates legal entity identifiers",
                createLegalEntityTemplate()
        ));

        // Initial filter
        filterTemplates();

        logger.info("Loaded {} default templates", allTemplates.size());
    }

    /**
     * Filter templates based on category and search criteria
     */
    private void filterTemplates() {
        String selectedCategory = categoryComboBox.getValue();
        String searchText = searchField.getText().toLowerCase();

        filteredTemplates.clear();

        for (TemplateItem template : allTemplates) {
            boolean categoryMatch = "All Categories".equals(selectedCategory) ||
                    template.getCategory().equals(selectedCategory);

            boolean searchMatch = searchText.isEmpty() ||
                    template.getName().toLowerCase().contains(searchText) ||
                    template.getDescription().toLowerCase().contains(searchText);

            if (categoryMatch && searchMatch) {
                filteredTemplates.add(template);
            }
        }

        logger.debug("Filtered templates: {} results", filteredTemplates.size());
    }

    /**
     * Show template preview
     */
    private void showTemplatePreview(TemplateItem template) {
        previewArea.setText(template.getTemplate());
    }

    /**
     * Insert selected template
     */
    private void insertSelectedTemplate() {
        TemplateItem selected = templatesTable.getSelectionModel().getSelectedItem();
        if (selected != null && templateInsertCallback != null) {
            templateInsertCallback.accept(selected.getTemplate());
            logger.debug("Inserted template: {}", selected.getName());
        }
    }

    /**
     * Customize and insert template
     */
    private void customizeAndInsertTemplate() {
        TemplateItem selected = templatesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            TemplateCustomizationDialog dialog = new TemplateCustomizationDialog(selected);
            Optional<String> result = dialog.showAndWait();

            result.ifPresent(customizedTemplate -> {
                if (templateInsertCallback != null) {
                    templateInsertCallback.accept(customizedTemplate);
                    logger.debug("Inserted customized template: {}", selected.getName());
                }
            });
        }
    }

    /**
     * Sets the callback function that will be invoked when a template is inserted.
     * The callback receives the template content as a string.
     *
     * @param callback the consumer function to handle template insertion
     */
    public void setTemplateInsertCallback(Consumer<String> callback) {
        this.templateInsertCallback = callback;
    }

    // ========== Template Generation Methods ==========

    /**
     * Generate mandatory field template
     */
    private String createMandatoryFieldTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="{FIELD_NAME}">
                        {FIELD_NAME} is mandatory
                    </assert>
                    <assert test="normalize-space({FIELD_NAME}) != ''">
                        {FIELD_NAME} cannot be empty
                    </assert>
                </rule>""";
    }

    /**
     * Generate conditional field template
     */
    private String createConditionalFieldTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="not({CONDITION}) or {FIELD_NAME}">
                        {FIELD_NAME} is required when {CONDITION_DESC}
                    </assert>
                </rule>""";
    }

    /**
     * Generate enumeration validation template
     */
    private String createEnumerationTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="{FIELD_NAME} = ('{VALUE1}', '{VALUE2}', '{VALUE3}')">
                        {FIELD_NAME} must be one of: {VALUE1}, {VALUE2}, {VALUE3}
                    </assert>
                </rule>""";
    }

    /**
     * Generate date range template
     */
    private String createDateRangeTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="xs:date({DATE_FIELD}) >= xs:date('{MIN_DATE}')">
                        {DATE_FIELD} must be on or after {MIN_DATE}
                    </assert>
                    <assert test="xs:date({DATE_FIELD}) <= xs:date('{MAX_DATE}')">
                        {DATE_FIELD} must be on or before {MAX_DATE}
                    </assert>
                </rule>""";
    }

    /**
     * Generate numeric range template
     */
    private String createNumericRangeTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="number({FIELD_NAME}) >= {MIN_VALUE}">
                        {FIELD_NAME} must be greater than or equal to {MIN_VALUE}
                    </assert>
                    <assert test="number({FIELD_NAME}) <= {MAX_VALUE}">
                        {FIELD_NAME} must be less than or equal to {MAX_VALUE}
                    </assert>
                </rule>""";
    }

    /**
     * Generate cross-reference template
     */
    private String createCrossReferenceTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="key('{KEY_NAME}', {REFERENCE_FIELD})">
                        {REFERENCE_FIELD} must reference an existing {TARGET_ELEMENT}
                    </assert>
                </rule>""";
    }

    /**
     * Generate ID uniqueness template
     */
    private String createIdUniquenessTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="count(//{ELEMENT_NAME}[@{ID_ATTRIBUTE} = current()/@{ID_ATTRIBUTE}]) = 1">
                        {ID_ATTRIBUTE} must be unique within the document
                    </assert>
                </rule>""";
    }

    /**
     * Generate element order template
     */
    private String createElementOrderTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="{ELEMENT1}/following-sibling::*[1][self::{ELEMENT2}]">
                        {ELEMENT2} must immediately follow {ELEMENT1}
                    </assert>
                </rule>""";
    }

    /**
     * Generate currency code template
     */
    private String createCurrencyCodeTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="string-length({CURRENCY_FIELD}) = 3">
                        Currency code must be 3 characters long
                    </assert>
                    <assert test="matches({CURRENCY_FIELD}, '^[A-Z]{3}$')">
                        Currency code must contain only uppercase letters
                    </assert>
                </rule>""";
    }

    /**
     * Generate amount precision template
     */
    private String createAmountPrecisionTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="matches({AMOUNT_FIELD}, '^\\d+(\\.\\d{1,{PRECISION}})?$')">
                        {AMOUNT_FIELD} must have at most {PRECISION} decimal places
                    </assert>
                </rule>""";
    }

    /**
     * Generate patient ID template
     */
    private String createPatientIdTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="matches({PATIENT_ID_FIELD}, '^P\\d{8}$')">
                        Patient ID must be in format P12345678
                    </assert>
                </rule>""";
    }

    /**
     * Generate medical code template
     */
    private String createMedicalCodeTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="@codeSystem = '{CODE_SYSTEM_OID}'">
                        Medical code must use the {CODE_SYSTEM_NAME} coding system
                    </assert>
                    <assert test="string-length(@code) > 0">
                        Medical code cannot be empty
                    </assert>
                </rule>""";
    }

    /**
     * Generate tax ID template
     */
    private String createTaxIdTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="matches({TAX_ID_FIELD}, '^\\d{2}-\\d{7}$')">
                        Tax ID must be in format 12-1234567
                    </assert>
                </rule>""";
    }

    /**
     * Generate legal entity template
     */
    private String createLegalEntityTemplate() {
        return """
                <rule context="{CONTEXT}">
                    <assert test="string-length({ENTITY_ID_FIELD}) >= 8">
                        Legal entity identifier must be at least 8 characters
                    </assert>
                    <assert test="{ENTITY_TYPE_FIELD} = ('CORP', 'LLC', 'PART', 'SOLE')">
                        Entity type must be one of: CORP, LLC, PART, SOLE
                    </assert>
                </rule>""";
    }

    // ========== Inner Classes ==========

    /**
     * Template item data model
     */
    public static class TemplateItem {
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty category = new SimpleStringProperty();
        private final StringProperty description = new SimpleStringProperty();
        private final String template;

        /**
         * Creates a new template item with the specified properties.
         *
         * @param name        the display name of the template
         * @param category    the category for filtering templates
         * @param description a brief description of what the template does
         * @param template    the actual Schematron template content
         */
        public TemplateItem(String name, String category, String description, String template) {
            this.name.set(name);
            this.category.set(category);
            this.description.set(description);
            this.template = template;
        }

        /**
         * Gets the template name.
         *
         * @return the template name
         */
        public String getName() {
            return name.get();
        }

        /**
         * Returns the name property for JavaFX binding.
         *
         * @return the name StringProperty
         */
        public StringProperty nameProperty() {
            return name;
        }

        /**
         * Gets the template category.
         *
         * @return the template category
         */
        public String getCategory() {
            return category.get();
        }

        /**
         * Returns the category property for JavaFX binding.
         *
         * @return the category StringProperty
         */
        public StringProperty categoryProperty() {
            return category;
        }

        /**
         * Gets the template description.
         *
         * @return the template description
         */
        public String getDescription() {
            return description.get();
        }

        /**
         * Returns the description property for JavaFX binding.
         *
         * @return the description StringProperty
         */
        public StringProperty descriptionProperty() {
            return description;
        }

        /**
         * Gets the Schematron template content.
         *
         * @return the template content string
         */
        public String getTemplate() {
            return template;
        }
    }

    /**
     * Dialog for customizing templates before insertion
     */
    private static class TemplateCustomizationDialog extends Dialog<String> {
        public TemplateCustomizationDialog(TemplateItem template) {
            setTitle("Customize Template");
            setHeaderText("Customize: " + template.getName());

            ButtonType insertButtonType = new ButtonType("Insert", ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(insertButtonType, ButtonType.CANCEL);

            VBox content = new VBox(10);
            content.setPadding(new Insets(20));

            Label instructionLabel = new Label("Replace placeholders in the template:");
            instructionLabel.setStyle("-fx-font-weight: bold;");

            TextArea templateArea = new TextArea();
            templateArea.setText(template.getTemplate());
            templateArea.setPrefRowCount(15);
            templateArea.setPrefColumnCount(60);
            templateArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace;");

            Label helpLabel = new Label("Placeholders: {CONTEXT}, {FIELD_NAME}, {CONDITION}, etc.");
            helpLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

            content.getChildren().addAll(instructionLabel, templateArea, helpLabel);
            getDialogPane().setContent(content);

            setResultConverter(dialogButton -> {
                if (dialogButton == insertButtonType) {
                    return templateArea.getText();
                }
                return null;
            });
        }
    }
}