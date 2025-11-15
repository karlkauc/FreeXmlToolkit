package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.kordamp.ikonli.javafx.FontIcon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

/**
 * Properties panel for editing XSD node properties.
 * Displays and allows editing of properties based on selected node type.
 * <p>
 * Sections:
 * - General: name, type, cardinality
 * - Documentation: xs:documentation, xs:appinfo
 * - Constraints: nillable, abstract, fixed
 * - Advanced: form, use, substitutionGroup
 *
 * @since 2.0
 */
public class XsdPropertiesPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdPropertiesPanel.class);

    private final XsdEditorContext editorContext;
    private VisualNode currentNode;

    // General section controls
    private TextField nameField;
    private ComboBox<String> typeComboBox;
    private Spinner<Integer> minOccursSpinner;
    private Spinner<Integer> maxOccursSpinner;
    private CheckBox unboundedCheckBox;

    // Documentation section controls
    private TextArea documentationArea;
    private TextArea appinfoArea;

    // Constraints section controls
    private CheckBox nillableCheckBox;
    private CheckBox abstractCheckBox;
    private CheckBox fixedCheckBox;

    // Advanced section controls
    private ComboBox<String> formComboBox;
    private ComboBox<String> useComboBox;
    private TextField substitutionGroupField;

    // New tabs for XSD constraints
    private ListView<String> facetsListView;
    private ListView<String> patternsListView;
    private ListView<String> enumerationsListView;
    private ListView<String> assertionsListView;
    private TabPane tabPane;

    private boolean updating = false; // Prevent recursive updates

    /**
     * Creates a new properties panel.
     *
     * @param editorContext the editor context
     */
    public XsdPropertiesPanel(XsdEditorContext editorContext) {
        this.editorContext = editorContext;

        initializeUI();
        setupListeners();

        // Listen to selection changes
        editorContext.getSelectionModel().addSelectionListener((oldSelection, newSelection) -> {
            if (!newSelection.isEmpty()) {
                // Get the first (primary) selected node
                VisualNode firstNode = newSelection.iterator().next();
                updateProperties(firstNode);
            } else {
                clearProperties();
            }
        });
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setPadding(new Insets(10));
        setSpacing(10);

        // Title
        Label titleLabel = new Label("Properties");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Create TitledPanes for general properties
        TitledPane generalPane = createGeneralTitledPane();
        TitledPane documentationPane = createDocumentationTitledPane();
        TitledPane constraintsPane = createConstraintsTitledPane();
        TitledPane advancedPane = createAdvancedTitledPane();

        // Create TabPane for specific constraint properties
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Add tabs - only the four specified constraint tabs
        tabPane.getTabs().addAll(
                createFacetsTab(),
                createPatternsTab(),
                createEnumerationsTab(),
                createAssertionsTab()
        );

        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(
                titleLabel, 
                new Separator(), 
                generalPane, 
                documentationPane,
                constraintsPane,
                advancedPane,
                new Separator(),
                tabPane
        );

        // Initially disabled until a node is selected
        setDisable(true);
    }





    /**
     * Creates the Facets tab.
     */
    private Tab createFacetsTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // Title and description
        Label titleLabel = new Label("XSD Facets");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label descLabel = new Label("Facets define restrictions on data types (e.g., minLength, maxLength, totalDigits)");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Facets list
        facetsListView = new ListView<>();
        facetsListView.setPrefHeight(150);
        facetsListView.setPlaceholder(new Label("No facets defined for this element"));

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);
        Button addFacetBtn = new Button("Add");
        addFacetBtn.setGraphic(new FontIcon("bi-plus-circle"));
        Button removeFacetBtn = new Button("Remove");
        removeFacetBtn.setGraphic(new FontIcon("bi-trash"));
        removeFacetBtn.setDisable(true);
        
        // Enable/disable remove button based on selection
        facetsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeFacetBtn.setDisable(newVal == null);
        });
        
        buttonBox.getChildren().addAll(addFacetBtn, removeFacetBtn);

        vbox.getChildren().addAll(titleLabel, descLabel, new Separator(), facetsListView, buttonBox);
        
        Tab tab = new Tab("Facets", vbox);
        tab.setGraphic(new FontIcon("bi-funnel"));
        return tab;
    }

    /**
     * Creates the Patterns tab.
     */
    private Tab createPatternsTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // Title and description
        Label titleLabel = new Label("Regular Expression Patterns");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label descLabel = new Label("Define regex patterns that values must match (e.g., [0-9]{3}-[0-9]{2}-[0-9]{4})");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Patterns list
        patternsListView = new ListView<>();
        patternsListView.setPrefHeight(150);
        patternsListView.setPlaceholder(new Label("No patterns defined for this element"));

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);
        Button addPatternBtn = new Button("Add");
        addPatternBtn.setGraphic(new FontIcon("bi-plus-circle"));
        Button removePatternBtn = new Button("Remove");
        removePatternBtn.setGraphic(new FontIcon("bi-trash"));
        removePatternBtn.setDisable(true);

        // Enable/disable remove button based on selection
        patternsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removePatternBtn.setDisable(newVal == null);
        });

        // Add button action
        addPatternBtn.setOnAction(e -> handleAddPattern());

        // Remove button action
        removePatternBtn.setOnAction(e -> {
            String selectedPattern = patternsListView.getSelectionModel().getSelectedItem();
            if (selectedPattern != null) {
                handleDeletePattern(selectedPattern);
            }
        });

        buttonBox.getChildren().addAll(addPatternBtn, removePatternBtn);

        vbox.getChildren().addAll(titleLabel, descLabel, new Separator(), patternsListView, buttonBox);
        
        Tab tab = new Tab("Patterns", vbox);
        tab.setGraphic(new FontIcon("bi-braces"));
        return tab;
    }

    /**
     * Creates the Enumerations tab.
     */
    private Tab createEnumerationsTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // Title and description
        Label titleLabel = new Label("Enumeration Values");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label descLabel = new Label("Define a list of allowed values for this element (e.g., 'red', 'green', 'blue')");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Enumerations list
        enumerationsListView = new ListView<>();
        enumerationsListView.setPrefHeight(150);
        enumerationsListView.setPlaceholder(new Label("No enumeration values defined"));

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);
        Button addEnumBtn = new Button("Add");
        addEnumBtn.setGraphic(new FontIcon("bi-plus-circle"));
        Button removeEnumBtn = new Button("Remove");
        removeEnumBtn.setGraphic(new FontIcon("bi-trash"));
        removeEnumBtn.setDisable(true);

        // Enable/disable remove button based on selection
        enumerationsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeEnumBtn.setDisable(newVal == null);
        });

        // Add button action
        addEnumBtn.setOnAction(e -> handleAddEnumeration());

        // Remove button action
        removeEnumBtn.setOnAction(e -> {
            String selectedEnum = enumerationsListView.getSelectionModel().getSelectedItem();
            if (selectedEnum != null) {
                handleDeleteEnumeration(selectedEnum);
            }
        });

        buttonBox.getChildren().addAll(addEnumBtn, removeEnumBtn);

        vbox.getChildren().addAll(titleLabel, descLabel, new Separator(), enumerationsListView, buttonBox);
        
        Tab tab = new Tab("Enumerations", vbox);
        tab.setGraphic(new FontIcon("bi-list-ul"));
        return tab;
    }

    /**
     * Creates the Assertions tab (XSD 1.1 feature).
     */
    private Tab createAssertionsTab() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // Title and description
        Label titleLabel = new Label("XSD 1.1 Assertions");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label descLabel = new Label("XPath-based assertions for complex validation rules (XSD 1.1 feature)");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Assertions list
        assertionsListView = new ListView<>();
        assertionsListView.setPrefHeight(150);
        assertionsListView.setPlaceholder(new Label("No assertions defined (requires XSD 1.1)"));

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);
        Button addAssertBtn = new Button("Add");
        addAssertBtn.setGraphic(new FontIcon("bi-plus-circle"));
        Button removeAssertBtn = new Button("Remove");
        removeAssertBtn.setGraphic(new FontIcon("bi-trash"));
        removeAssertBtn.setDisable(true);

        // Enable/disable remove button based on selection
        assertionsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeAssertBtn.setDisable(newVal == null);
        });

        // Add button action
        addAssertBtn.setOnAction(e -> handleAddAssertion());

        // Remove button action
        removeAssertBtn.setOnAction(e -> {
            String selectedAssertion = assertionsListView.getSelectionModel().getSelectedItem();
            if (selectedAssertion != null) {
                handleDeleteAssertion(selectedAssertion);
            }
        });

        buttonBox.getChildren().addAll(addAssertBtn, removeAssertBtn);

        // Info note about XSD 1.1
        Label infoLabel = new Label("Note: Assertions are an XSD 1.1 feature and may not be supported by all validators");
        infoLabel.setStyle("-fx-text-fill: #ff8c00; -fx-font-size: 10px; -fx-font-style: italic;");
        infoLabel.setWrapText(true);

        vbox.getChildren().addAll(titleLabel, descLabel, new Separator(), assertionsListView, buttonBox, infoLabel);
        
        Tab tab = new Tab("Assertions", vbox);
        tab.setGraphic(new FontIcon("bi-check2-square"));
        return tab;
    }


    /**
     * Sets up change listeners for property controls.
     */
    private void setupListeners() {
        // Name field - fire command when focus lost
        nameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!updating && wasFocused && !isNowFocused && currentNode != null) {
                handleNameChange();
            }
        });

        // Type combobox - fire command when value changes
        typeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null) {
                handleTypeChange();
            }
        });

        // Cardinality - minOccurs spinner
        minOccursSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null && newValue != null) {
                handleCardinalityChange();
            }
        });

        // Cardinality - maxOccurs spinner
        maxOccursSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null && newValue != null && !unboundedCheckBox.isSelected()) {
                handleCardinalityChange();
            }
        });

        // Cardinality - unbounded checkbox
        unboundedCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null) {
                // Disable/enable maxOccurs spinner
                maxOccursSpinner.setDisable(newValue);
                handleCardinalityChange();
            }
        });

        // Documentation - fire command when focus lost
        documentationArea.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!updating && wasFocused && !isNowFocused && currentNode != null) {
                handleDocumentationChange();
            }
        });

        // AppInfo - fire command when focus lost
        appinfoArea.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!updating && wasFocused && !isNowFocused && currentNode != null) {
                handleAppinfoChange();
            }
        });

        // Constraints - nillable checkbox
        nillableCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null) {
                handleConstraintsChange();
            }
        });

        // Constraints - abstract checkbox
        abstractCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null) {
                handleConstraintsChange();
            }
        });

        // Constraints - fixed checkbox (for now just stores boolean, later could add TextField for value)
        fixedCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null) {
                handleConstraintsChange();
            }
        });

        // Advanced - form ComboBox
        formComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null) {
                handleFormChange();
            }
        });

        // Advanced - use ComboBox
        useComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null) {
                handleUseChange();
            }
        });

        // Advanced - substitution group field
        substitutionGroupField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!updating && wasFocused && !isNowFocused && currentNode != null) {
                handleSubstitutionGroupChange();
            }
        });
    }

    /**
     * Creates the General properties TitledPane.
     */
    private TitledPane createGeneralTitledPane() {
        GridPane grid = createGridPane();
        int row = 0;

        // Name
        grid.add(new Label("Name:"), 0, row);
        nameField = new TextField();
        nameField.setPromptText("Element/Attribute name");
        grid.add(nameField, 1, row++);

        // Type
        grid.add(new Label("Type:"), 0, row);
        typeComboBox = new ComboBox<>();
        typeComboBox.setEditable(true);
        typeComboBox.setPromptText("xs:string, MyCustomType");
        typeComboBox.setMaxWidth(Double.MAX_VALUE);
        grid.add(typeComboBox, 1, row++);

        // Cardinality section
        Label cardinalityLabel = new Label("Cardinality:");
        cardinalityLabel.setStyle("-fx-font-weight: bold;");
        grid.add(cardinalityLabel, 0, row++, 2, 1);

        // minOccurs
        grid.add(new Label("  Min Occurs:"), 0, row);
        minOccursSpinner = new Spinner<>(0, 999, 1);
        minOccursSpinner.setEditable(true);
        minOccursSpinner.setPrefWidth(100);
        grid.add(minOccursSpinner, 1, row++);

        // maxOccurs
        grid.add(new Label("  Max Occurs:"), 0, row);
        maxOccursSpinner = new Spinner<>(1, 999, 1);
        maxOccursSpinner.setEditable(true);
        maxOccursSpinner.setPrefWidth(100);
        grid.add(maxOccursSpinner, 1, row++);

        // Unbounded checkbox
        unboundedCheckBox = new CheckBox("Unbounded");
        grid.add(unboundedCheckBox, 1, row++);

        TitledPane titledPane = new TitledPane("General", grid);
        titledPane.setExpanded(true);
        return titledPane;
    }

    /**
     * Creates the Documentation TitledPane.
     */
    private TitledPane createDocumentationTitledPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // Documentation
        vbox.getChildren().add(new Label("Documentation:"));
        documentationArea = new TextArea();
        documentationArea.setPromptText("xs:documentation content");
        documentationArea.setPrefRowCount(3);
        documentationArea.setWrapText(true);
        vbox.getChildren().add(documentationArea);

        // AppInfo
        vbox.getChildren().add(new Label("AppInfo:"));
        appinfoArea = new TextArea();
        appinfoArea.setPromptText("xs:appinfo content");
        appinfoArea.setPrefRowCount(3);
        appinfoArea.setWrapText(true);
        vbox.getChildren().add(appinfoArea);

        TitledPane titledPane = new TitledPane("Documentation", vbox);
        titledPane.setExpanded(false);
        return titledPane;
    }

    /**
     * Creates the Constraints TitledPane.
     */
    private TitledPane createConstraintsTitledPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        nillableCheckBox = new CheckBox("Nillable (allows xsi:nil='true')");
        abstractCheckBox = new CheckBox("Abstract (cannot be used directly)");
        fixedCheckBox = new CheckBox("Fixed value");

        vbox.getChildren().addAll(nillableCheckBox, abstractCheckBox, fixedCheckBox);

        TitledPane titledPane = new TitledPane("Constraints", vbox);
        titledPane.setExpanded(false);
        return titledPane;
    }

    /**
     * Creates the Advanced TitledPane.
     */
    private TitledPane createAdvancedTitledPane() {
        GridPane grid = createGridPane();
        int row = 0;

        // Form
        grid.add(new Label("Form:"), 0, row);
        formComboBox = new ComboBox<>();
        formComboBox.getItems().addAll("qualified", "unqualified");
        formComboBox.setPromptText("Select form");
        grid.add(formComboBox, 1, row++);

        // Use
        grid.add(new Label("Use:"), 0, row);
        useComboBox = new ComboBox<>();
        useComboBox.getItems().addAll("required", "optional", "prohibited");
        useComboBox.setPromptText("Select use");
        grid.add(useComboBox, 1, row++);

        // Substitution Group
        grid.add(new Label("Substitution Group:"), 0, row);
        substitutionGroupField = new TextField();
        substitutionGroupField.setPromptText("Element name");
        grid.add(substitutionGroupField, 1, row++);

        TitledPane titledPane = new TitledPane("Advanced", grid);
        titledPane.setExpanded(false);
        return titledPane;
    }

    /**
     * Creates a standard grid pane for forms.
     */
    private GridPane createGridPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        return grid;
    }

    /**
     * Updates the properties panel with the given node's data.
     */
    private void updateProperties(VisualNode node) {
        this.currentNode = node;
        updating = true;

        try {
            // Enable panel for editing only in edit mode, but always allow viewing
            boolean isEditMode = editorContext.isEditMode();
            logger.info("==== Updating properties panel for node: {}, Edit Mode: {} ====", node.getLabel(), isEditMode);

            // Always enable the panel for viewing, but disable editing controls
            setDisable(false);

            // Disable editing controls if not in edit mode
            nameField.setEditable(isEditMode);
            typeComboBox.setDisable(!isEditMode);
            minOccursSpinner.setDisable(!isEditMode);
            maxOccursSpinner.setDisable(!isEditMode || unboundedCheckBox.isSelected());
            unboundedCheckBox.setDisable(!isEditMode);
            documentationArea.setEditable(isEditMode);
            appinfoArea.setEditable(isEditMode);

            // Constraint checkboxes only for elements
            boolean isElement = node.getModelObject() instanceof XsdElement;
            boolean isAttribute = node.getModelObject() instanceof XsdAttribute;
            boolean constraintsEnabled = isEditMode && isElement;
            nillableCheckBox.setDisable(!constraintsEnabled);
            abstractCheckBox.setDisable(!constraintsEnabled);
            fixedCheckBox.setDisable(!constraintsEnabled);

            // Form ComboBox for both elements and attributes
            formComboBox.setDisable(!isEditMode || (!isElement && !isAttribute));

            // Use ComboBox only for attributes
            useComboBox.setDisable(!isEditMode || !isAttribute);

            logger.debug("Controls editable state set: nameField={}, documentationArea={}, isEditMode={}, isElement={}, constraintsEnabled={}",
                    nameField.isEditable(), documentationArea.isEditable(), isEditMode, isElement, constraintsEnabled);

            // Update General section
            nameField.setText(node.getLabel());

            // Populate type combobox with available types
            populateTypeComboBox();

            // Get model object for property access
            Object modelObject = node.getModelObject();

            // Get effective type from the model object
            // This handles both explicit type references and inline simpleTypes with restrictions
            String currentType = getEffectiveType(modelObject);

            // Set the current type in the combobox (pre-select it)
            typeComboBox.setValue(currentType);

            // Update cardinality
            int minOccurs = node.getMinOccurs();
            int maxOccurs = node.getMaxOccurs();

            minOccursSpinner.getValueFactory().setValue(minOccurs);

            if (maxOccurs == ChangeCardinalityCommand.UNBOUNDED) {
                unboundedCheckBox.setSelected(true);
                maxOccursSpinner.setDisable(true);
                maxOccursSpinner.getValueFactory().setValue(1);
            } else {
                unboundedCheckBox.setSelected(false);
                maxOccursSpinner.setDisable(false);
                maxOccursSpinner.getValueFactory().setValue(maxOccurs);
            }

            // Update Documentation section from model
            logger.debug("ModelObject type: {}", modelObject != null ? modelObject.getClass().getName() : "null");
            if (modelObject instanceof XsdNode xsdNode) {
                String documentation = xsdNode.getDocumentation();
                String appinfo = xsdNode.getAppinfoAsString();
                logger.debug("Loading documentation: '{}', appinfo: '{}'", documentation, appinfo);
                documentationArea.setText(documentation != null ? documentation : "");
                appinfoArea.setText(appinfo != null ? appinfo : "");
                logger.debug("Documentation panel updated with values");
            } else {
                logger.warn("ModelObject is not an XsdNode, cannot load documentation/appinfo");
                documentationArea.setText("");
                appinfoArea.setText("");
            }

            // Update Constraints section
            if (modelObject instanceof XsdElement xsdElement) {
                nillableCheckBox.setSelected(xsdElement.isNillable());
                abstractCheckBox.setSelected(xsdElement.isAbstract());
                fixedCheckBox.setSelected(xsdElement.getFixed() != null && !xsdElement.getFixed().isEmpty());
                logger.debug("Loaded constraints: nillable={}, abstract={}, fixed={}",
                        xsdElement.isNillable(), xsdElement.isAbstract(), xsdElement.getFixed());
            } else {
                nillableCheckBox.setSelected(false);
                abstractCheckBox.setSelected(false);
                fixedCheckBox.setSelected(false);
            }

            // Update Advanced section
            if (modelObject instanceof XsdElement xsdElement) {
                formComboBox.setValue(xsdElement.getForm());
                useComboBox.setValue(null); // Elements don't have use
            } else if (modelObject instanceof XsdAttribute xsdAttribute) {
                formComboBox.setValue(xsdAttribute.getForm());
                useComboBox.setValue(xsdAttribute.getUse());
            } else {
                formComboBox.setValue(null);
                useComboBox.setValue(null);
            }
            substitutionGroupField.setText("");

            // Update constraint tabs based on model data
            updateConstraintTabs(modelObject);

            logger.debug("Updated properties panel for node: {}", node.getLabel());

        } finally {
            updating = false;
        }
    }

    /**
     * Clears all property fields.
     */
    private void clearProperties() {
        this.currentNode = null;
        updating = true;

        try {
            setDisable(true);

            nameField.clear();
            typeComboBox.setValue(null);
            minOccursSpinner.getValueFactory().setValue(1);
            maxOccursSpinner.getValueFactory().setValue(1);
            unboundedCheckBox.setSelected(false);
            maxOccursSpinner.setDisable(false);

            documentationArea.clear();
            appinfoArea.clear();

            nillableCheckBox.setSelected(false);
            abstractCheckBox.setSelected(false);
            fixedCheckBox.setSelected(false);

            formComboBox.setValue(null);
            useComboBox.setValue(null);
            substitutionGroupField.clear();

            // Clear constraint tabs
            facetsListView.getItems().clear();
            patternsListView.getItems().clear();
            enumerationsListView.getItems().clear();
            assertionsListView.getItems().clear();

        } finally {
            updating = false;
        }
    }









    /**
     * Updates the constraint tabs (Facets, Patterns, Enumerations, Assertions) with data from the model.
     */
    private void updateConstraintTabs(Object modelObject) {
        // Clear all lists first
        facetsListView.getItems().clear();
        patternsListView.getItems().clear();
        enumerationsListView.getItems().clear();
        assertionsListView.getItems().clear();

        if (!(modelObject instanceof XsdElement xsdElement)) {
            logger.debug("Model object is not an XsdElement, cannot update constraint tabs");
            return;
        }

        // Load patterns from model
        patternsListView.getItems().addAll(xsdElement.getPatterns());
        logger.debug("Loaded {} patterns from element '{}'", xsdElement.getPatterns().size(), xsdElement.getName());

        // Load enumerations from model
        enumerationsListView.getItems().addAll(xsdElement.getEnumerations());
        logger.debug("Loaded {} enumerations from element '{}'", xsdElement.getEnumerations().size(), xsdElement.getName());

        // Load assertions from model
        assertionsListView.getItems().addAll(xsdElement.getAssertions());
        logger.debug("Loaded {} assertions from element '{}'", xsdElement.getAssertions().size(), xsdElement.getName());

        // TODO: Facets - currently not implemented in model
        // Facets would need a proper model class (XsdFacet) with type and value
        // For now, facets tab remains empty

        logger.debug("Updated constraint tabs for element: {}", xsdElement.getName());
    }

    /**
     * Refreshes the current properties display.
     */
    public void refresh() {
        if (currentNode != null) {
            updateProperties(currentNode);
        }
    }

    // ========== Event Handlers ==========

    /**
     * Handles changes to the name field.
     */
    private void handleNameChange() {
        if (currentNode == null || currentNode.getModelObject() == null) {
            return;
        }

        String newName = nameField.getText();
        if (newName == null || newName.trim().isEmpty()) {
            logger.warn("Cannot set empty name");
            // Revert to current name
            nameField.setText(currentNode.getLabel());
            return;
        }

        // Only create command if value actually changed
        if (!newName.equals(currentNode.getLabel())) {
            XsdNode node = (XsdNode) currentNode.getModelObject();
            RenameNodeCommand command = new RenameNodeCommand(node, newName);
            editorContext.getCommandManager().executeCommand(command);
            editorContext.setDirty(true);
            logger.debug("Executed RenameNodeCommand: {} -> {}", currentNode.getLabel(), newName);
        }
    }

    /**
     * Handles changes to the type field.
     */
    private void handleTypeChange() {
        if (currentNode == null || currentNode.getModelObject() == null) {
            return;
        }

        String newType = typeComboBox.getValue();
        Object modelObject = currentNode.getModelObject();

        // Get current type from model
        String currentType = null;
        if (modelObject instanceof XsdElement element) {
            currentType = element.getType();
        } else if (modelObject instanceof XsdAttribute attribute) {
            currentType = attribute.getType();
        } else {
            return; // Not an element or attribute
        }

        // Only create command if value actually changed
        if (!java.util.Objects.equals(newType, currentType)) {
            XsdNode node = (XsdNode) modelObject;
            ChangeTypeCommand command = new ChangeTypeCommand(editorContext, node, newType);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeTypeCommand: {} -> {}", currentType, newType);
        }
    }

    /**
     * Handles changes to cardinality (minOccurs/maxOccurs/unbounded).
     */
    private void handleCardinalityChange() {
        if (currentNode == null || currentNode.getModelObject() == null) {
            return;
        }

        int newMinOccurs = minOccursSpinner.getValue();
        int newMaxOccurs = unboundedCheckBox.isSelected()
                ? ChangeCardinalityCommand.UNBOUNDED
                : maxOccursSpinner.getValue();

        // Only create command if values actually changed
        if (newMinOccurs != currentNode.getMinOccurs() || newMaxOccurs != currentNode.getMaxOccurs()) {
            XsdNode node = (XsdNode) currentNode.getModelObject();
            ChangeCardinalityCommand command = new ChangeCardinalityCommand(node, newMinOccurs, newMaxOccurs);
            editorContext.getCommandManager().executeCommand(command);
            editorContext.setDirty(true);
            logger.debug("Executed ChangeCardinalityCommand: {},{} -> {},{}",
                    currentNode.getMinOccurs(), currentNode.getMaxOccurs(), newMinOccurs, newMaxOccurs);
        }
    }

    /**
     * Handles changes to the documentation field.
     */
    private void handleDocumentationChange() {
        if (currentNode == null || currentNode.getModelObject() == null) {
            return;
        }

        String newDocumentation = documentationArea.getText();
        XsdNode node = (XsdNode) currentNode.getModelObject();
        String currentDocumentation = node.getDocumentation();

        // Only create command if value actually changed
        if (!java.util.Objects.equals(newDocumentation, currentDocumentation)) {
            ChangeDocumentationCommand command = new ChangeDocumentationCommand(
                    editorContext, node, newDocumentation);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeDocumentationCommand");
        }
    }

    /**
     * Handles changes to the appinfo field.
     */
    private void handleAppinfoChange() {
        if (currentNode == null || currentNode.getModelObject() == null) {
            return;
        }

        String newAppinfo = appinfoArea.getText();
        XsdNode node = (XsdNode) currentNode.getModelObject();
        String currentAppinfo = node.getAppinfoAsString();

        // Only create command if value actually changed
        if (!java.util.Objects.equals(newAppinfo, currentAppinfo)) {
            ChangeAppinfoCommand command = new ChangeAppinfoCommand(editorContext, node, newAppinfo);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeAppinfoCommand");
        }
    }

    /**
     * Handles changes to constraint checkboxes (nillable, abstract, fixed).
     */
    private void handleConstraintsChange() {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdElement)) {
            return;
        }

        XsdElement element = (XsdElement) currentNode.getModelObject();
        boolean newNillable = nillableCheckBox.isSelected();
        boolean newAbstract = abstractCheckBox.isSelected();
        String newFixed = fixedCheckBox.isSelected() ? "" : null; // TODO: Add TextField for actual value

        // Only create command if values actually changed
        if (newNillable != element.isNillable()
                || newAbstract != element.isAbstract()
                || !java.util.Objects.equals(newFixed, element.getFixed())) {
            ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                    editorContext, element, newNillable, newAbstract, newFixed);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeConstraintsCommand");
        }
    }

    /**
     * Handles changes to the form ComboBox.
     */
    private void handleFormChange() {
        if (currentNode == null || currentNode.getModelObject() == null) {
            return;
        }

        String newForm = formComboBox.getValue();
        Object modelObject = currentNode.getModelObject();

        // Get current form from model
        String currentForm = null;
        if (modelObject instanceof XsdElement element) {
            currentForm = element.getForm();
        } else if (modelObject instanceof XsdAttribute attribute) {
            currentForm = attribute.getForm();
        } else {
            return; // Not an element or attribute
        }

        // Only create command if value actually changed
        if (!java.util.Objects.equals(newForm, currentForm)) {
            XsdNode node = (XsdNode) modelObject;
            ChangeFormCommand command = new ChangeFormCommand(editorContext, node, newForm);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeFormCommand: {} -> {}", currentForm, newForm);
        }
    }

    /**
     * Handles changes to the use ComboBox (attributes only).
     */
    private void handleUseChange() {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdAttribute)) {
            return;
        }

        XsdAttribute attribute = (XsdAttribute) currentNode.getModelObject();
        String newUse = useComboBox.getValue();
        String currentUse = attribute.getUse();

        // Only create command if value actually changed
        if (!java.util.Objects.equals(newUse, currentUse)) {
            ChangeUseCommand command = new ChangeUseCommand(editorContext, attribute, newUse);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeUseCommand: {} -> {}", currentUse, newUse);
        }
    }

    /**
     * Handles changes to the substitution group field.
     */
    private void handleSubstitutionGroupChange() {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdElement)) {
            return;
        }

        XsdElement element = (XsdElement) currentNode.getModelObject();
        String newSubstitutionGroup = substitutionGroupField.getText();
        String currentSubstitutionGroup = element.getSubstitutionGroup();

        // Only create command if value actually changed
        if (!java.util.Objects.equals(newSubstitutionGroup, currentSubstitutionGroup)) {
            ChangeSubstitutionGroupCommand command = new ChangeSubstitutionGroupCommand(
                    editorContext, element, newSubstitutionGroup);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeSubstitutionGroupCommand: {} -> {}",
                    currentSubstitutionGroup, newSubstitutionGroup);
        }
    }

    // ========== Tab Button Handlers ==========

    /**
     * Handles adding a new pattern.
     */
    private void handleAddPattern() {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdElement)) {
            return;
        }

        // Show input dialog for pattern
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Pattern");
        dialog.setHeaderText("Add Regex Pattern Constraint");
        dialog.setContentText("Enter regex pattern:");

        dialog.showAndWait().ifPresent(pattern -> {
            if (pattern != null && !pattern.trim().isEmpty()) {
                AddPatternCommand command = new AddPatternCommand(
                        editorContext, (XsdNode) currentNode.getModelObject(), pattern);
                editorContext.getCommandManager().executeCommand(command);
                logger.debug("Executed AddPatternCommand");
            }
        });
    }

    /**
     * Handles deleting a pattern.
     */
    private void handleDeletePattern(String pattern) {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdElement)) {
            return;
        }

        DeletePatternCommand command = new DeletePatternCommand(
                editorContext, (XsdNode) currentNode.getModelObject(), pattern);
        editorContext.getCommandManager().executeCommand(command);
        logger.debug("Executed DeletePatternCommand");
    }

    /**
     * Handles adding a new enumeration value.
     */
    private void handleAddEnumeration() {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdElement)) {
            return;
        }

        // Show input dialog for enumeration value
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Enumeration");
        dialog.setHeaderText("Add Enumeration Value");
        dialog.setContentText("Enter value:");

        dialog.showAndWait().ifPresent(enumValue -> {
            if (enumValue != null && !enumValue.trim().isEmpty()) {
                AddEnumerationCommand command = new AddEnumerationCommand(
                        editorContext, (XsdNode) currentNode.getModelObject(), enumValue);
                editorContext.getCommandManager().executeCommand(command);
                logger.debug("Executed AddEnumerationCommand");
            }
        });
    }

    /**
     * Handles deleting an enumeration value.
     */
    private void handleDeleteEnumeration(String enumValue) {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdElement)) {
            return;
        }

        DeleteEnumerationCommand command = new DeleteEnumerationCommand(
                editorContext, (XsdNode) currentNode.getModelObject(), enumValue);
        editorContext.getCommandManager().executeCommand(command);
        logger.debug("Executed DeleteEnumerationCommand");
    }

    /**
     * Handles adding a new assertion.
     */
    private void handleAddAssertion() {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdElement)) {
            return;
        }

        // Show input dialog for assertion XPath expression
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Assertion");
        dialog.setHeaderText("Add XSD 1.1 Assertion");
        dialog.setContentText("Enter XPath expression:");

        dialog.showAndWait().ifPresent(assertion -> {
            if (assertion != null && !assertion.trim().isEmpty()) {
                AddAssertionCommand command = new AddAssertionCommand(
                        editorContext, (XsdNode) currentNode.getModelObject(), assertion);
                editorContext.getCommandManager().executeCommand(command);
                logger.debug("Executed AddAssertionCommand");
            }
        });
    }

    /**
     * Handles deleting an assertion.
     */
    private void handleDeleteAssertion(String assertion) {
        if (currentNode == null || !(currentNode.getModelObject() instanceof XsdElement)) {
            return;
        }

        DeleteAssertionCommand command = new DeleteAssertionCommand(
                editorContext, (XsdNode) currentNode.getModelObject(), assertion);
        editorContext.getCommandManager().executeCommand(command);
        logger.debug("Executed DeleteAssertionCommand");
    }

    /**
     * Populates the type combobox with all available types:
     * - Built-in XML Schema types (xs:string, xs:int, etc.)
     * - User-defined types from the schema (simpleType, complexType)
     */
    private void populateTypeComboBox() {
        if (typeComboBox == null) {
            return;
        }

        // Collect all available types
        java.util.List<String> availableTypes = new java.util.ArrayList<>();

        // 1. Add Built-in XML Schema Types
        availableTypes.add("xs:string");
        availableTypes.add("xs:boolean");
        availableTypes.add("xs:decimal");
        availableTypes.add("xs:float");
        availableTypes.add("xs:double");
        availableTypes.add("xs:duration");
        availableTypes.add("xs:dateTime");
        availableTypes.add("xs:time");
        availableTypes.add("xs:date");
        availableTypes.add("xs:gYearMonth");
        availableTypes.add("xs:gYear");
        availableTypes.add("xs:gMonthDay");
        availableTypes.add("xs:gDay");
        availableTypes.add("xs:gMonth");
        availableTypes.add("xs:hexBinary");
        availableTypes.add("xs:base64Binary");
        availableTypes.add("xs:anyURI");
        availableTypes.add("xs:QName");
        availableTypes.add("xs:NOTATION");
        availableTypes.add("xs:normalizedString");
        availableTypes.add("xs:token");
        availableTypes.add("xs:language");
        availableTypes.add("xs:NMTOKEN");
        availableTypes.add("xs:NMTOKENS");
        availableTypes.add("xs:Name");
        availableTypes.add("xs:NCName");
        availableTypes.add("xs:ID");
        availableTypes.add("xs:IDREF");
        availableTypes.add("xs:IDREFS");
        availableTypes.add("xs:ENTITY");
        availableTypes.add("xs:ENTITIES");
        availableTypes.add("xs:integer");
        availableTypes.add("xs:nonPositiveInteger");
        availableTypes.add("xs:negativeInteger");
        availableTypes.add("xs:long");
        availableTypes.add("xs:int");
        availableTypes.add("xs:short");
        availableTypes.add("xs:byte");
        availableTypes.add("xs:nonNegativeInteger");
        availableTypes.add("xs:unsignedLong");
        availableTypes.add("xs:unsignedInt");
        availableTypes.add("xs:unsignedShort");
        availableTypes.add("xs:unsignedByte");
        availableTypes.add("xs:positiveInteger");

        // 2. Add user-defined types from the schema
        XsdSchema schema = editorContext.getSchema();
        if (schema != null) {
            collectUserDefinedTypes(schema, availableTypes);
        }

        // Sort the list alphabetically
        java.util.Collections.sort(availableTypes);

        // Update ComboBox items
        typeComboBox.getItems().clear();
        typeComboBox.getItems().addAll(availableTypes);

        logger.debug("Populated type combobox with {} types", availableTypes.size());
    }

    /**
     * Recursively collects user-defined type names from the schema.
     *
     * @param node           the node to search
     * @param availableTypes the list to add type names to
     */
    private void collectUserDefinedTypes(XsdNode node, java.util.List<String> availableTypes) {
        if (node == null) {
            return;
        }

        // Check if this node is a named type definition
        if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType simpleType) {
            String name = simpleType.getName();
            if (name != null && !name.isEmpty() && !name.equals("simpleType")) {
                availableTypes.add(name);
            }
        } else if (node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
            String name = complexType.getName();
            if (name != null && !name.isEmpty() && !name.equals("complexType")) {
                availableTypes.add(name);
            }
        }

        // Recursively search children
        for (XsdNode child : node.getChildren()) {
            collectUserDefinedTypes(child, availableTypes);
        }
    }

    /**
     * Gets the effective type of an element or attribute.
     * For elements/attributes with explicit type reference, returns that type.
     * For elements with inline simpleType and restriction, returns the base type from the restriction.
     *
     * @param modelObject the model object (XsdElement or XsdAttribute)
     * @return the effective type, or null if not found
     */
    private String getEffectiveType(Object modelObject) {
        if (modelObject instanceof XsdElement element) {
            // First check for explicit type reference
            String explicitType = element.getType();
            if (explicitType != null && !explicitType.isEmpty()) {
                return explicitType;
            }

            // Check for inline simpleType with restriction
            for (XsdNode child : element.getChildren()) {
                if (child instanceof XsdSimpleType simpleType) {
                    // Look for restriction in simpleType children
                    for (XsdNode restrictionChild : simpleType.getChildren()) {
                        if (restrictionChild instanceof XsdRestriction restriction) {
                            String base = restriction.getBase();
                            if (base != null && !base.isEmpty()) {
                                return base;
                            }
                        }
                    }
                }
            }
        } else if (modelObject instanceof XsdAttribute attribute) {
            // Attributes typically only have explicit type references
            return attribute.getType();
        }

        return null;
    }
}
