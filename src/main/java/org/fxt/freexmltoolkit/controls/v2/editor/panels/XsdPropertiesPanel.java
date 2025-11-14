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
    private TextField typeField;
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
        // TODO: Add listeners back once handlers are implemented
        // Currently disabled to allow compilation
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
        typeField = new TextField();
        typeField.setPromptText("xs:string, MyCustomType");
        grid.add(typeField, 1, row++);

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
            typeField.setEditable(isEditMode);
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

            // Extract type from detail (if available)
            String detail = node.getDetail();
            if (detail != null && detail.contains("type:")) {
                int typeIndex = detail.indexOf("type:");
                int endIndex = detail.indexOf('\n', typeIndex);
                if (endIndex < 0) endIndex = detail.length();
                String type = detail.substring(typeIndex + 5, endIndex).trim();
                typeField.setText(type);
            } else {
                typeField.setText("");
            }

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
            Object modelObject = node.getModelObject();
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
            typeField.clear();
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

        if (!(modelObject instanceof XsdNode xsdNode)) {
            logger.debug("Model object is not an XsdNode, cannot update constraint tabs");
            return;
        }

        // TODO: Implement actual data extraction from XsdNode model
        // For now, populate with placeholder data to demonstrate functionality
        
        // Facets - extract from XSD restrictions (minLength, maxLength, pattern, etc.)
        if (xsdNode instanceof XsdElement xsdElement) {
            // Example facets (in real implementation, these would come from the XSD model)
            if (xsdElement.getType() != null && xsdElement.getType().contains("string")) {
                facetsListView.getItems().addAll(
                    "minLength: 1",
                    "maxLength: 100"
                );
            }
        }

        // Patterns - extract regex patterns from XSD
        // Example: patterns would be extracted from xs:pattern facets
        if (xsdNode.getName() != null && xsdNode.getName().toLowerCase().contains("email")) {
            patternsListView.getItems().add("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        }

        // Enumerations - extract from xs:enumeration facets
        if (xsdNode.getName() != null && xsdNode.getName().toLowerCase().contains("status")) {
            enumerationsListView.getItems().addAll(
                "active",
                "inactive", 
                "pending"
            );
        }

        // Assertions - extract XSD 1.1 xs:assert elements
        if (xsdNode.getName() != null && xsdNode.getName().toLowerCase().contains("price")) {
            assertionsListView.getItems().add("@value > 0");
        }

        logger.debug("Updated constraint tabs for node: {}", xsdNode.getName());
    }

    /**
     * Refreshes the current properties display.
     */
    public void refresh() {
        if (currentNode != null) {
            updateProperties(currentNode);
        }
    }
}
