package org.fxt.freexmltoolkit.controls;

import java.util.Optional;
import java.util.function.Consumer;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;

/**
 * Template library for common Schematron patterns and rules.
 * Provides a collection of reusable templates for business rules validation.
 */
public class SchematronTemplateLibrary extends VBox {

    private static final Logger logger = LogManager.getLogger(SchematronTemplateLibrary.class);

    // UI components (defined in /pages/views/SchematronTemplateLibrary.fxml)
    @FXML private ComboBox<String> categoryComboBox;
    @FXML private TextField searchField;
    @FXML private TableView<TemplateItem> templatesTable;
    @FXML private TableColumn<TemplateItem, String> nameColumn;
    @FXML private TableColumn<TemplateItem, String> categoryColumn;
    @FXML private TableColumn<TemplateItem, String> descColumn;
    @FXML private TextArea previewArea;
    @FXML private Button insertButton;
    @FXML private Button customizeButton;

    // Data models
    private final ObservableList<TemplateItem> allTemplates = FXCollections.observableArrayList();
    private final ObservableList<TemplateItem> filteredTemplates = FXCollections.observableArrayList();

    // Callback for inserting templates
    private Consumer<String> templateInsertCallback;

    /**
     * Constructor - loads the layout from {@code /pages/views/SchematronTemplateLibrary.fxml},
     * wires the table and the filters and loads the default templates.
     */
    public SchematronTemplateLibrary() {
        FxmlViews.load(this, "/pages/views/SchematronTemplateLibrary.fxml");

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("category"));
        descColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        templatesTable.setItems(filteredTemplates);

        // Filter by category (wired after the initial value so it does not count as a user choice)
        categoryComboBox.setValue("All Categories");
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

        loadDefaultTemplates();

        logger.debug("SchematronTemplateLibrary initialized");
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
    @FXML
    public void onInsertTemplate() {
        TemplateItem selected = templatesTable.getSelectionModel().getSelectedItem();
        if (selected != null && templateInsertCallback != null) {
            templateInsertCallback.accept(selected.getTemplate());
            logger.debug("Inserted template: {}", selected.getName());
        }
    }

    /**
     * Customize and insert template
     */
    @FXML
    public void onCustomizeAndInsert() {
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
        TemplateCustomizationDialog(TemplateItem template) {
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
