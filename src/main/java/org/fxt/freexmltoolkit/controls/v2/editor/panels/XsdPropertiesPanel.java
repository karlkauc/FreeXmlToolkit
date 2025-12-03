package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Alert;
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
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDatatypeFacets;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
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
    private TableView<XsdDocumentation> documentationTableView;
    private Button addDocBtn;
    private Button editDocBtn;
    private Button deleteDocBtn;
    private TextArea documentationArea; // Legacy field, hidden
    private TextArea appinfoArea; // Kept for backward compatibility
    private AppInfoEditorPanel appInfoEditorPanel; // New structured editor

    // Constraints section controls
    private CheckBox nillableCheckBox;
    private CheckBox abstractCheckBox;
    private CheckBox fixedCheckBox;
    private TextField fixedValueField;

    // Advanced section controls
    private ComboBox<String> formComboBox;
    private ComboBox<String> useComboBox;
    private TextField substitutionGroupField;

    // New tabs for XSD constraints
    private GridPane facetsGridPane; // Grid for facet name/value pairs
    private ListView<String> patternsListView;
    private ListView<String> enumerationsListView;
    private ListView<String> assertionsListView;
    private TabPane tabPane;

    // Buttons for pattern editing
    private Button addPatternBtn;
    private Button removePatternBtn;

    // Facet controls (will be created dynamically based on datatype)
    private final java.util.Map<XsdFacetType, TextField> facetFields = new java.util.HashMap<>();
    private final java.util.Map<XsdFacetType, Label> facetLabels = new java.util.HashMap<>();

    private boolean updating = false; // Prevent recursive updates
    private boolean patternsFromReferencedType = false; // Track if patterns come from referenced type (read-only)
    private boolean facetsFromReferencedType = false; // Track if facets come from referenced type (read-only)

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

        // Grid for facet name/value pairs
        facetsGridPane = new GridPane();
        facetsGridPane.setHgap(10);
        facetsGridPane.setVgap(8);
        facetsGridPane.setPadding(new Insets(10));

        // Wrap grid in ScrollPane for long lists of facets
        ScrollPane scrollPane = new ScrollPane(facetsGridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        vbox.getChildren().addAll(titleLabel, descLabel, new Separator(), scrollPane);
        
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

        // Custom cell factory to show read-only indicator
        patternsListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (patternsFromReferencedType) {
                        // Add lock icon and gray out text for read-only patterns
                        FontIcon lockIcon = new FontIcon("bi-lock-fill");
                        lockIcon.setIconSize(12);
                        lockIcon.setStyle("-fx-icon-color: #999999;");
                        setGraphic(lockIcon);
                        setStyle("-fx-text-fill: #666666;");
                    } else {
                        setGraphic(null);
                        setStyle("");
                    }
                }
            }
        });

        // Add/Remove buttons
        HBox buttonBox = new HBox(10);
        addPatternBtn = new Button("Add");
        addPatternBtn.setGraphic(new FontIcon("bi-plus-circle"));
        removePatternBtn = new Button("Remove");
        removePatternBtn.setGraphic(new FontIcon("bi-trash"));
        removePatternBtn.setDisable(true);

        // Enable/disable remove button based on selection and whether patterns are from referenced type
        patternsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removePatternBtn.setDisable(newVal == null || patternsFromReferencedType);
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

        // Documentation - no listener needed anymore, changes are handled by table edit commits and buttons

        // AppInfo handling is now done by AppInfoEditorPanel internally
        // It creates commands directly when fields change

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

        // Constraints - fixed checkbox (enables/disables the text field)
        fixedCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!updating && currentNode != null) {
                // Enable/disable text field based on checkbox
                fixedValueField.setDisable(!newValue);
                handleConstraintsChange();
            }
        });

        // Constraints - fixed value text field
        fixedValueField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!updating && wasFocused && !isNowFocused && currentNode != null && fixedCheckBox.isSelected()) {
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

        // Documentation Section
        Label docLabel = new Label("Documentation (Multi-Language Support):");
        docLabel.setStyle("-fx-font-weight: bold;");
        vbox.getChildren().add(docLabel);

        // TableView for documentation entries
        documentationTableView = new TableView<>();
        documentationTableView.setEditable(true);
        documentationTableView.setPrefHeight(150);
        documentationTableView.setPlaceholder(new Label("No documentation entries"));

        // Language column (xml:lang)
        TableColumn<XsdDocumentation, String> langColumn = new TableColumn<>("Language (xml:lang)");
        langColumn.setPrefWidth(150);
        langColumn.setCellValueFactory(cellData -> {
            String lang = cellData.getValue().getLang();
            return new javafx.beans.property.SimpleStringProperty(lang != null ? lang : "");
        });
        langColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        langColumn.setOnEditCommit(event -> {
            XsdDocumentation doc = event.getRowValue();
            String newLang = event.getNewValue();
            doc.setLang(newLang.isEmpty() ? null : newLang);
            handleDocumentationsChange();
        });

        // Text column
        TableColumn<XsdDocumentation, String> textColumn = new TableColumn<>("Documentation Text");
        textColumn.setPrefWidth(350);
        textColumn.setCellValueFactory(cellData -> {
            String text = cellData.getValue().getText();
            return new javafx.beans.property.SimpleStringProperty(text != null ? text : "");
        });
        textColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        textColumn.setOnEditCommit(event -> {
            XsdDocumentation doc = event.getRowValue();
            doc.setText(event.getNewValue());
            handleDocumentationsChange();
        });

        documentationTableView.getColumns().addAll(langColumn, textColumn);

        vbox.getChildren().add(documentationTableView);

        // Buttons for Add/Edit/Delete
        HBox buttonBox = new HBox(10);
        addDocBtn = new Button("Add");
        addDocBtn.setGraphic(new FontIcon("bi-plus-circle"));
        addDocBtn.setOnAction(e -> handleAddDocumentation());

        editDocBtn = new Button("Edit");
        editDocBtn.setGraphic(new FontIcon("bi-pencil"));
        editDocBtn.setDisable(true);
        editDocBtn.setOnAction(e -> handleEditDocumentation());

        deleteDocBtn = new Button("Delete");
        deleteDocBtn.setGraphic(new FontIcon("bi-trash"));
        deleteDocBtn.setDisable(true);
        deleteDocBtn.setOnAction(e -> handleDeleteDocumentation());

        // Enable/disable edit and delete buttons based on selection
        documentationTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editDocBtn.setDisable(!hasSelection);
            deleteDocBtn.setDisable(!hasSelection);
        });

        buttonBox.getChildren().addAll(addDocBtn, editDocBtn, deleteDocBtn);
        vbox.getChildren().add(buttonBox);

        // Separator
        Separator separator = new Separator();
        separator.setPadding(new Insets(5, 0, 5, 0));
        vbox.getChildren().add(separator);

        // AppInfo - Structured Editor (XsdDoc)
        Label appInfoLabel = new Label("AppInfo (XsdDoc):");
        appInfoLabel.setStyle("-fx-font-weight: bold;");
        vbox.getChildren().add(appInfoLabel);

        appInfoEditorPanel = new AppInfoEditorPanel(editorContext);
        vbox.getChildren().add(appInfoEditorPanel);

        // Hidden legacy fields for backward compatibility (not shown in UI)
        documentationArea = new TextArea();
        documentationArea.setManaged(false);
        documentationArea.setVisible(false);

        appinfoArea = new TextArea();
        appinfoArea.setManaged(false);
        appinfoArea.setVisible(false);

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

        // Fixed value section with checkbox and text field
        fixedCheckBox = new CheckBox("Fixed value");
        fixedValueField = new TextField();
        fixedValueField.setPromptText("Enter fixed value");
        fixedValueField.setDisable(true); // Initially disabled, enabled when checkbox is checked

        HBox fixedHBox = new HBox(10);
        fixedHBox.getChildren().addAll(fixedCheckBox, fixedValueField);
        HBox.setHgrow(fixedValueField, Priority.ALWAYS);

        vbox.getChildren().addAll(nillableCheckBox, abstractCheckBox, fixedHBox);

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

            // Documentation table and buttons
            documentationTableView.setDisable(!isEditMode);
            addDocBtn.setDisable(!isEditMode);
            editDocBtn.setDisable(!isEditMode || documentationTableView.getSelectionModel().getSelectedItem() == null);
            deleteDocBtn.setDisable(!isEditMode || documentationTableView.getSelectionModel().getSelectedItem() == null);

            // Legacy fields (hidden)
            documentationArea.setEditable(isEditMode);
            appinfoArea.setEditable(isEditMode);

            // Constraint checkboxes only for elements
            boolean isElement = node.getModelObject() instanceof XsdElement;
            boolean isAttribute = node.getModelObject() instanceof XsdAttribute;
            boolean constraintsEnabled = isEditMode && isElement;
            nillableCheckBox.setDisable(!constraintsEnabled);
            abstractCheckBox.setDisable(!constraintsEnabled);
            fixedCheckBox.setDisable(!constraintsEnabled);
            // fixedValueField is enabled only when checkbox is checked and in edit mode
            fixedValueField.setEditable(isEditMode);

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
                // Load multi-language documentations into TableView
                java.util.List<XsdDocumentation> docs = xsdNode.getDocumentations();
                logger.debug("Loading {} documentation entries", docs.size());
                documentationTableView.setItems(FXCollections.observableArrayList(docs));

                // Update the structured AppInfo editor panel
                appInfoEditorPanel.setNode(xsdNode);
                logger.debug("Documentation panel and AppInfo editor updated with values");
            } else {
                logger.warn("ModelObject is not an XsdNode, cannot load documentation/appinfo");
                documentationTableView.setItems(FXCollections.observableArrayList());
                appInfoEditorPanel.setNode(null);
            }

            // Update Constraints section
            if (modelObject instanceof XsdElement xsdElement) {
                nillableCheckBox.setSelected(xsdElement.isNillable());
                abstractCheckBox.setSelected(xsdElement.isAbstract());

                // Load fixed value
                String fixedValue = xsdElement.getFixed();
                boolean hasFixed = fixedValue != null && !fixedValue.isEmpty();
                fixedCheckBox.setSelected(hasFixed);
                fixedValueField.setText(hasFixed ? fixedValue : "");
                fixedValueField.setDisable(!hasFixed); // Enable field only if checkbox is checked

                logger.debug("Loaded constraints: nillable={}, abstract={}, fixed={}",
                        xsdElement.isNillable(), xsdElement.isAbstract(), fixedValue);
            } else {
                nillableCheckBox.setSelected(false);
                abstractCheckBox.setSelected(false);
                fixedCheckBox.setSelected(false);
                fixedValueField.setText("");
                fixedValueField.setDisable(true);
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

            documentationTableView.setItems(FXCollections.observableArrayList());
            appInfoEditorPanel.setNode(null); // Clear the structured AppInfo editor

            nillableCheckBox.setSelected(false);
            abstractCheckBox.setSelected(false);
            fixedCheckBox.setSelected(false);
            fixedValueField.clear();
            fixedValueField.setDisable(true);

            formComboBox.setValue(null);
            useComboBox.setValue(null);
            substitutionGroupField.clear();

            // Clear constraint tabs
            facetsGridPane.getChildren().clear();
            facetFields.clear();
            facetLabels.clear();
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
        facetsGridPane.getChildren().clear();
        facetFields.clear();
        facetLabels.clear();
        patternsListView.getItems().clear();
        enumerationsListView.getItems().clear();
        assertionsListView.getItems().clear();

        if (!(modelObject instanceof XsdElement xsdElement)) {
            logger.debug("Model object is not an XsdElement, cannot update constraint tabs");
            return;
        }

        // Update facets based on element's datatype
        updateFacets(xsdElement);

        // Load patterns from model (including patterns from referenced types)
        java.util.List<String> effectivePatterns = getEffectivePatterns(xsdElement);
        patternsListView.getItems().addAll(effectivePatterns);

        // Update button states based on whether patterns are from referenced type
        addPatternBtn.setDisable(patternsFromReferencedType);
        removePatternBtn.setDisable(true); // Will be enabled by selection listener if not from referenced type

        // Update placeholder text if patterns are from referenced type
        if (patternsFromReferencedType && !effectivePatterns.isEmpty()) {
            patternsListView.setPlaceholder(new Label("Patterns from referenced type (read-only)"));
        } else {
            patternsListView.setPlaceholder(new Label("No patterns defined for this element"));
        }

        logger.debug("Loaded {} patterns from element '{}' (from referenced type: {})",
                effectivePatterns.size(), xsdElement.getName(), patternsFromReferencedType);

        // Load enumerations from model
        enumerationsListView.getItems().addAll(xsdElement.getEnumerations());
        logger.debug("Loaded {} enumerations from element '{}'", xsdElement.getEnumerations().size(), xsdElement.getName());

        // Load assertions from model
        assertionsListView.getItems().addAll(xsdElement.getAssertions());
        logger.debug("Loaded {} assertions from element '{}'", xsdElement.getAssertions().size(), xsdElement.getName());

        // Note: Facets are now implemented in the model via XsdRestriction.getFacets()
        // However, facets belong to SimpleType restrictions, not directly to elements
        // Elements can have inherited facets from their type reference, which are displayed via FacetsPanel
        // Direct facet editing on elements is handled through the referenced SimpleType

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
     * Handles changes to the documentation list.
     * Called when inline editing is done or when Add/Edit/Delete buttons are used.
     */
    private void handleDocumentationsChange() {
        if (currentNode == null || currentNode.getModelObject() == null || updating) {
            logger.debug("handleDocumentationsChange skipped: currentNode={}, updating={}", currentNode, updating);
            return;
        }

        XsdNode node = (XsdNode) currentNode.getModelObject();
        java.util.List<XsdDocumentation> newDocumentations = new java.util.ArrayList<>(documentationTableView.getItems());

        logger.info("handleDocumentationsChange: node={}, tableView.items.size={}, newDocs.size={}",
                    node.getName(), documentationTableView.getItems().size(), newDocumentations.size());
        for (int i = 0; i < newDocumentations.size(); i++) {
            XsdDocumentation doc = newDocumentations.get(i);
            logger.info("  Doc[{}]: lang='{}', text='{}'", i, doc.getLang(),
                        doc.getText().length() > 50 ? doc.getText().substring(0, 47) + "..." : doc.getText());
        }

        // Create and execute command
        ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(editorContext, node, newDocumentations);
        editorContext.getCommandManager().executeCommand(command);
        logger.info("Executed ChangeDocumentationsCommand with {} entries for node '{}'", newDocumentations.size(), node.getName());
    }

    /**
     * Handles adding a new documentation entry.
     */
    private void handleAddDocumentation() {
        if (currentNode == null || currentNode.getModelObject() == null) {
            return;
        }

        // Show input dialog for language and text
        Dialog<XsdDocumentation> dialog = new Dialog<>();
        dialog.setTitle("Add Documentation");
        dialog.setHeaderText("Add new documentation entry");

        // Set the button types
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create the form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField langField = new TextField();
        langField.setPromptText("e.g., en, de (leave empty for no language)");
        TextArea textArea = new TextArea();
        textArea.setPromptText("Documentation text");
        textArea.setPrefRowCount(3);

        grid.add(new Label("Language (xml:lang):"), 0, 0);
        grid.add(langField, 1, 0);
        grid.add(new Label("Text:"), 0, 1);
        grid.add(textArea, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Convert the result when the add button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String text = textArea.getText();
                if (text != null && !text.trim().isEmpty()) {
                    String lang = langField.getText();
                    return new XsdDocumentation(
                            text.trim(),
                            (lang != null && !lang.trim().isEmpty()) ? lang.trim() : null
                    );
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newDoc -> {
            documentationTableView.getItems().add(newDoc);
            handleDocumentationsChange();
            logger.debug("Added new documentation entry with lang='{}'", newDoc.getLang());
        });
    }

    /**
     * Handles editing a selected documentation entry.
     */
    private void handleEditDocumentation() {
        XsdDocumentation selected = documentationTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        // Show input dialog for language and text
        Dialog<XsdDocumentation> dialog = new Dialog<>();
        dialog.setTitle("Edit Documentation");
        dialog.setHeaderText("Edit documentation entry");

        // Set the button types
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Create the form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField langField = new TextField(selected.getLang() != null ? selected.getLang() : "");
        langField.setPromptText("e.g., en, de (leave empty for no language)");
        TextArea textArea = new TextArea(selected.getText());
        textArea.setPromptText("Documentation text");
        textArea.setPrefRowCount(3);

        grid.add(new Label("Language (xml:lang):"), 0, 0);
        grid.add(langField, 1, 0);
        grid.add(new Label("Text:"), 0, 1);
        grid.add(textArea, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Convert the result when the save button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                String text = textArea.getText();
                if (text != null && !text.trim().isEmpty()) {
                    String lang = langField.getText();
                    selected.setLang((lang != null && !lang.trim().isEmpty()) ? lang.trim() : null);
                    selected.setText(text.trim());
                    return selected;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(editedDoc -> {
            documentationTableView.refresh();
            handleDocumentationsChange();
            logger.debug("Edited documentation entry with lang='{}'", editedDoc.getLang());
        });
    }

    /**
     * Handles deleting a selected documentation entry.
     */
    private void handleDeleteDocumentation() {
        XsdDocumentation selected = documentationTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        // Confirm deletion
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Documentation");
        alert.setHeaderText("Delete documentation entry?");
        alert.setContentText("Are you sure you want to delete this documentation entry?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                documentationTableView.getItems().remove(selected);
                handleDocumentationsChange();
                logger.debug("Deleted documentation entry");
            }
        });
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

        // Get fixed value from text field if checkbox is checked
        String newFixed = null;
        if (fixedCheckBox.isSelected()) {
            String fixedValue = fixedValueField.getText();
            if (fixedValue != null && !fixedValue.trim().isEmpty()) {
                newFixed = fixedValue.trim();
            } else {
                // If checkbox is checked but no value provided, uncheck the checkbox
                updating = true;
                fixedCheckBox.setSelected(false);
                fixedValueField.setDisable(true);
                updating = false;
                logger.warn("Fixed checkbox unchecked because no value was provided");
                return; // Don't create command if no valid fixed value
            }
        }

        // Only create command if values actually changed
        if (newNillable != element.isNillable()
                || newAbstract != element.isAbstract()
                || !java.util.Objects.equals(newFixed, element.getFixed())) {
            ChangeConstraintsCommand command = new ChangeConstraintsCommand(
                    editorContext, element, newNillable, newAbstract, newFixed);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeConstraintsCommand with fixed='{}'", newFixed);
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
     * Gets the effective patterns of an element.
     * Patterns can come from:
     * 1. Inline simpleType with restriction in the element itself (editable)
     * 2. Referenced type (e.g., element type="ISINType" where ISINType has patterns) (read-only)
     *
     * This method also sets the patternsFromReferencedType flag to indicate whether
     * the patterns are editable or read-only.
     *
     * @param element the XSD element
     * @return list of effective patterns (from element or referenced type)
     */
    private java.util.List<String> getEffectivePatterns(XsdElement element) {
        java.util.List<String> patterns = new java.util.ArrayList<>();
        patternsFromReferencedType = false; // Reset flag

        // First, check for patterns directly on the element (inline simpleType)
        if (!element.getPatterns().isEmpty()) {
            patterns.addAll(element.getPatterns());
            patternsFromReferencedType = false; // Inline patterns are editable
            logger.debug("Found {} inline patterns on element '{}'", patterns.size(), element.getName());
            return patterns;
        }

        // If no direct patterns, check for patterns in referenced type
        String typeRef = element.getType();
        if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:")) {
            // Remove namespace prefix if present
            String typeName = typeRef;
            if (typeName.contains(":")) {
                typeName = typeName.substring(typeName.indexOf(":") + 1);
            }

            logger.debug("Element '{}' references type '{}', searching for patterns", element.getName(), typeName);

            // Find the schema root
            XsdNode current = element;
            while (current != null && !(current instanceof XsdSchema)) {
                current = current.getParent();
            }

            if (current instanceof XsdSchema schema) {
                // Search for the type definition
                patterns.addAll(findPatternsInType(schema, typeName));
                if (!patterns.isEmpty()) {
                    patternsFromReferencedType = true; // Referenced type patterns are read-only
                    logger.debug("Found {} patterns in referenced type '{}' (read-only)", patterns.size(), typeName);
                }
            }
        }

        return patterns;
    }

    /**
     * Finds patterns in a named type definition within the schema.
     *
     * @param schema the schema to search
     * @param typeName the name of the type to find
     * @return list of patterns found in the type
     */
    private java.util.List<String> findPatternsInType(XsdSchema schema, String typeName) {
        java.util.List<String> patterns = new java.util.ArrayList<>();

        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                if (typeName.equals(simpleType.getName())) {
                    // Found the type, now extract patterns from restriction
                    for (XsdNode typeChild : simpleType.getChildren()) {
                        if (typeChild instanceof XsdRestriction restriction) {
                            for (org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet : restriction.getFacets()) {
                                if (facet.getFacetType() == org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType.PATTERN) {
                                    patterns.add(facet.getValue());
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }

        return patterns;
    }

    /**
     * Gets the effective facets of an element.
     * Facets can come from:
     * 1. Inline simpleType with restriction in the element itself (editable)
     * 2. Referenced type (e.g., element type="PostalCodeType" where PostalCodeType has facets) (read-only)
     *
     * This method also sets the facetsFromReferencedType flag.
     *
     * @param element the XSD element
     * @return map of facet types to their values
     */
    private java.util.Map<XsdFacetType, String> getEffectiveFacets(XsdElement element) {
        java.util.Map<XsdFacetType, String> facets = new java.util.HashMap<>();
        facetsFromReferencedType = false; // Reset flag

        // First, check for inline simpleType with restriction
        for (XsdNode child : element.getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                for (XsdNode typeChild : simpleType.getChildren()) {
                    if (typeChild instanceof XsdRestriction restriction) {
                        for (org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet : restriction.getFacets()) {
                            facets.put(facet.getFacetType(), facet.getValue());
                        }
                    }
                }
                if (!facets.isEmpty()) {
                    facetsFromReferencedType = false; // Inline facets are editable
                    logger.debug("Found {} inline facets on element '{}'", facets.size(), element.getName());
                    return facets;
                }
            }
        }

        // If no inline facets, check for facets in referenced type
        String typeRef = element.getType();
        if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:")) {
            // Remove namespace prefix if present
            String typeName = typeRef;
            if (typeName.contains(":")) {
                typeName = typeName.substring(typeName.indexOf(":") + 1);
            }

            logger.debug("Element '{}' references type '{}', searching for facets", element.getName(), typeName);

            // Find the schema root
            XsdNode current = element;
            while (current != null && !(current instanceof XsdSchema)) {
                current = current.getParent();
            }

            if (current instanceof XsdSchema schema) {
                // Search for the type definition
                facets.putAll(findFacetsInType(schema, typeName));
                if (!facets.isEmpty()) {
                    facetsFromReferencedType = true; // Referenced type facets are read-only
                    logger.debug("Found {} facets in referenced type '{}' (read-only)", facets.size(), typeName);
                }
            }
        }

        return facets;
    }

    /**
     * Finds facets in a named type definition within the schema.
     *
     * @param schema the schema to search
     * @param typeName the name of the type to find
     * @return map of facets found in the type
     */
    private java.util.Map<XsdFacetType, String> findFacetsInType(XsdSchema schema, String typeName) {
        java.util.Map<XsdFacetType, String> facets = new java.util.HashMap<>();

        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                if (typeName.equals(simpleType.getName())) {
                    // Found the type, now extract facets from restriction
                    for (XsdNode typeChild : simpleType.getChildren()) {
                        if (typeChild instanceof XsdRestriction restriction) {
                            for (org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet : restriction.getFacets()) {
                                facets.put(facet.getFacetType(), facet.getValue());
                            }
                        }
                    }
                    break;
                }
            }
        }

        return facets;
    }

    /**
     * Handles facet value changes (when user edits a facet value).
     *
     * @param element the element being edited
     * @param facetType the facet type being changed
     * @param newValue the new value (may be empty to delete facet)
     */
    private void handleFacetValueChange(XsdElement element, XsdFacetType facetType, String newValue) {
        if (facetsFromReferencedType) {
            logger.warn("Cannot edit facets from referenced type");
            return;
        }

        logger.debug("Facet {} changed to '{}' for element '{}'",
                     facetType.getXmlName(), newValue, element.getName());

        // Get or create inline restriction
        XsdRestriction restriction = getOrCreateInlineRestriction(element);
        if (restriction == null) {
            logger.error("Failed to get or create inline restriction for element '{}'", element.getName());
            return;
        }

        // Find existing facet with this type
        org.fxt.freexmltoolkit.controls.v2.model.XsdFacet existingFacet = null;
        for (org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet : restriction.getFacets()) {
            if (facet.getFacetType() == facetType) {
                existingFacet = facet;
                break;
            }
        }

        // Determine which command to execute
        if (newValue == null || newValue.trim().isEmpty()) {
            // Delete facet if it exists
            if (existingFacet != null) {
                DeleteFacetCommand command = new DeleteFacetCommand(restriction, existingFacet);
                editorContext.getCommandManager().executeCommand(command);
                editorContext.setDirty(true);
                logger.info("Deleted {} facet from element '{}'", facetType.getXmlName(), element.getName());
            }
        } else {
            // Add or edit facet
            if (existingFacet != null) {
                // Edit existing facet
                if (!newValue.equals(existingFacet.getValue())) {
                    EditFacetCommand command = new EditFacetCommand(existingFacet, newValue);
                    editorContext.getCommandManager().executeCommand(command);
                    editorContext.setDirty(true);
                    logger.info("Edited {} facet to '{}' for element '{}'",
                               facetType.getXmlName(), newValue, element.getName());
                }
            } else {
                // Add new facet
                AddFacetCommand command = new AddFacetCommand(restriction, facetType, newValue);
                editorContext.getCommandManager().executeCommand(command);
                editorContext.setDirty(true);
                logger.info("Added {} facet with value '{}' to element '{}'",
                           facetType.getXmlName(), newValue, element.getName());
            }
        }
    }

    /**
     * Gets or creates the inline simpleType/restriction structure for an element.
     * Creates the structure: Element -> SimpleType -> Restriction
     *
     * @param element the element
     * @return the restriction, or null if it cannot be created
     */
    private XsdRestriction getOrCreateInlineRestriction(XsdElement element) {
        // Check for existing inline simpleType with restriction
        for (XsdNode child : element.getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                for (XsdNode typeChild : simpleType.getChildren()) {
                    if (typeChild instanceof XsdRestriction restriction) {
                        return restriction;
                    }
                }
                // SimpleType exists but no restriction - create one
                XsdRestriction newRestriction = createRestrictionForElement(element);
                simpleType.addChild(newRestriction);
                return newRestriction;
            }
        }

        // No inline simpleType - create the full structure
        // Determine base type
        String baseType = element.getType();
        if (baseType == null || baseType.isEmpty()) {
            baseType = "xs:string"; // Default to string if no type specified
        }

        // Create simpleType
        XsdSimpleType simpleType = new XsdSimpleType(null);

        // Create restriction with base type
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase(baseType);

        // Build hierarchy
        simpleType.addChild(restriction);
        element.addChild(simpleType);

        // Clear the type attribute since we're using inline type now
        element.setType(null);

        logger.debug("Created inline simpleType/restriction for element '{}' with base type '{}'",
                    element.getName(), baseType);

        return restriction;
    }

    /**
     * Creates a new restriction based on the element's current type.
     *
     * @param element the element
     * @return a new XsdRestriction with appropriate base type
     */
    private XsdRestriction createRestrictionForElement(XsdElement element) {
        String baseType = element.getType();
        if (baseType == null || baseType.isEmpty()) {
            baseType = "xs:string";
        }

        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase(baseType);
        return restriction;
    }

    /**
     * Updates the facets tab with applicable facets based on the element's datatype.
     * Creates UI controls (label + textfield) for each applicable facet.
     * Phase 1: LENGTH, MIN_LENGTH, MAX_LENGTH
     * Phase 2: TOTAL_DIGITS, FRACTION_DIGITS, MIN_INCLUSIVE, MAX_INCLUSIVE, MIN_EXCLUSIVE, MAX_EXCLUSIVE
     * Phase 3: WHITE_SPACE, EXPLICIT_TIMEZONE
     *
     * @param element the XSD element
     */
    private void updateFacets(XsdElement element) {
        // Get effective type
        String datatype = getEffectiveType(element);
        if (datatype == null || datatype.isEmpty()) {
            logger.debug("No datatype for element '{}'", element.getName());
            return;
        }

        logger.debug("Loading facets for element '{}' with datatype '{}'", element.getName(), datatype);

        // Get applicable facets for this datatype
        java.util.Set<XsdFacetType> applicableFacets = XsdDatatypeFacets.getApplicableFacets(datatype);

        // Load effective facets (from element or referenced type)
        java.util.Map<XsdFacetType, String> effectiveFacets = getEffectiveFacets(element);

        // Define implemented facets (Phase 1 + Phase 2 + Phase 3)
        // Ordered logically: length constraints, numeric constraints, range constraints, other
        java.util.List<XsdFacetType> implementedFacets = java.util.List.of(
                // Phase 1: String length constraints
                XsdFacetType.LENGTH,
                XsdFacetType.MIN_LENGTH,
                XsdFacetType.MAX_LENGTH,
                // Phase 2: Numeric constraints
                XsdFacetType.TOTAL_DIGITS,
                XsdFacetType.FRACTION_DIGITS,
                // Phase 2: Range constraints (inclusive)
                XsdFacetType.MIN_INCLUSIVE,
                XsdFacetType.MAX_INCLUSIVE,
                // Phase 2: Range constraints (exclusive)
                XsdFacetType.MIN_EXCLUSIVE,
                XsdFacetType.MAX_EXCLUSIVE,
                // Phase 3: Whitespace and timezone
                XsdFacetType.WHITE_SPACE,
                XsdFacetType.EXPLICIT_TIMEZONE
        );

        int row = 0;
        for (XsdFacetType facetType : implementedFacets) {
            if (!applicableFacets.contains(facetType)) {
                continue; // Skip facets not applicable to this datatype
            }

            // Create label
            Label label = new Label(facetType.getXmlName() + ":");
            label.setMinWidth(100);
            facetLabels.put(facetType, label);

            // Create text field
            TextField textField = new TextField();
            textField.setPromptText("Enter " + facetType.getXmlName() + " value");
            HBox.setHgrow(textField, Priority.ALWAYS);

            // Check if facet is fixed
            boolean isFixed = XsdDatatypeFacets.isFacetFixed(datatype, facetType);
            if (isFixed) {
                String fixedValue = XsdDatatypeFacets.getFixedFacetValue(datatype, facetType);
                textField.setText(fixedValue);
                textField.setDisable(true);
                textField.setStyle("-fx-opacity: 0.6;");
                label.setStyle("-fx-text-fill: #999999;");
                logger.debug("Facet {} is fixed to '{}' for datatype '{}'", facetType.getXmlName(), fixedValue, datatype);
            } else {
                // Load current facet value from model
                String currentValue = effectiveFacets.get(facetType);
                if (currentValue != null) {
                    textField.setText(currentValue);
                }

                // Check if facet is from referenced type (read-only)
                if (facetsFromReferencedType) {
                    textField.setDisable(true);
                    textField.setStyle("-fx-opacity: 0.7;");
                    label.setStyle("-fx-text-fill: #666666;");
                    // Add lock icon to indicate read-only
                    FontIcon lockIcon = new FontIcon("bi-lock-fill");
                    lockIcon.setIconSize(12);
                    lockIcon.setStyle("-fx-icon-color: #999999;");
                    Label labelWithIcon = new Label(facetType.getXmlName() + ":", lockIcon);
                    labelWithIcon.setMinWidth(100);
                    labelWithIcon.setStyle("-fx-text-fill: #666666;");
                    facetLabels.put(facetType, labelWithIcon);
                    facetsGridPane.add(labelWithIcon, 0, row);
                    facetsGridPane.add(textField, 1, row);
                } else {
                    // Add listener for value changes (editable facets only)
                    textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                        if (!newVal && !updating) { // Lost focus
                            handleFacetValueChange(element, facetType, textField.getText());
                        }
                    });
                    facetsGridPane.add(label, 0, row);
                    facetsGridPane.add(textField, 1, row);
                }
            }

            facetFields.put(facetType, textField);

            // Add to grid (if not already added above)
            if (!isFixed && !facetsFromReferencedType) {
                // Already added in the else branch above
            } else if (isFixed) {
                facetsGridPane.add(label, 0, row);
                facetsGridPane.add(textField, 1, row);
            }

            row++;
        }

        if (row == 0) {
            Label noFacetsLabel = new Label("No applicable facets for datatype: " + datatype);
            noFacetsLabel.setStyle("-fx-text-fill: #999999;");
            facetsGridPane.add(noFacetsLabel, 0, 0, 2, 1);
        }

        logger.debug("Created {} facet controls for element '{}' (from referenced type: {})",
                row, element.getName(), facetsFromReferencedType);
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
