package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import java.util.Optional;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shared.CustomizableSectionContainer;
import org.fxt.freexmltoolkit.controls.shared.SectionDefinition;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.schema.TypeChoices;
import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerField;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddAssertionCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddEnumerationCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddPatternCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeCardinalityCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeConstraintsCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeFormCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeSubstitutionGroupCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeTypeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.ChangeUseCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteAssertionCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteEnumerationCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeletePatternCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCommentCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAssert;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComment;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDatatypeFacets;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

/**
 * Properties panel for editing XSD node properties.
 * Displays properties in a 3-column layout:
 * <ul>
 *   <li><b>Attributes</b> (left): Schema info, General, Cardinality, Constraints, Advanced</li>
 *   <li><b>Facets</b> (middle): Facets grid, Patterns, Enumerations, Assertions</li>
 *   <li><b>Docs</b> (right): Documentation cards, AppInfo</li>
 * </ul>
 *
 * @since 2.0
 */
public class XsdPropertiesPanel extends BorderPane {

    private static final Logger logger = LogManager.getLogger(XsdPropertiesPanel.class);

    private final XsdEditorContext editorContext;
    private VisualNode currentNode;

    // Main TabPane container
    @FXML private TabPane mainTabPane;

    // Tabs and the scroll panes that take the section containers
    @FXML private Tab attributesTab;
    @FXML private Tab facetsTab;
    @FXML private Tab docsTab;
    @FXML private ScrollPane attributesScroll;
    @FXML private ScrollPane facetsScroll;
    @FXML private ScrollPane docsScroll;

    // Sections (declared in fx:define, registered in the section containers)
    @FXML private TitledPane generalPane;
    @FXML private TitledPane cardinalityPane;
    @FXML private TitledPane constraintsPane;
    @FXML private TitledPane advancedPane;
    @FXML private TitledPane facetsPane;
    @FXML private TitledPane patternsPane;
    @FXML private TitledPane enumerationsPane;
    @FXML private TitledPane assertionsPane;
    @FXML private TitledPane documentationPane;
    @FXML private TitledPane appInfoPane;
    @FXML private VBox appInfoBox;

    // Customizable section containers (one per tab)
    private CustomizableSectionContainer attributesSectionContainer;
    private CustomizableSectionContainer facetsSectionContainer;
    private CustomizableSectionContainer docsSectionContainer;

    // Header
    @FXML private Button gearButton;

    // General section controls
    @FXML private TextField nameField;
    @FXML private TypePickerField typePicker;
    @FXML private IconifyIcon typeIcon;
    @FXML private Spinner<Integer> minOccursSpinner;
    @FXML private Spinner<Integer> maxOccursSpinner;
    @FXML private CheckBox unboundedCheckBox;

    // Documentation section controls
    private InlineDocumentationEditor documentationEditor;
    private AppInfoEditorPanel appInfoEditorPanel;

    // Constraints section controls
    @FXML private CheckBox nillableCheckBox;
    @FXML private CheckBox abstractCheckBox;
    @FXML private CheckBox fixedCheckBox;
    @FXML private TextField fixedValueField;

    // Advanced section controls
    @FXML private ComboBox<String> formComboBox;
    @FXML private ComboBox<String> useComboBox;
    @FXML private TextField substitutionGroupField;

    // Facets controls
    @FXML private GridPane facetsGridPane;
    @FXML private ListView<String> patternsListView;
    @FXML private ListView<String> enumerationsListView;
    @FXML private ListView<String> assertionsListView;

    // Buttons for pattern editing
    @FXML private Button addPatternBtn;
    @FXML private Button removePatternBtn;

    // Buttons for enumeration editing
    @FXML private Button addEnumerationBtn;
    @FXML private Button removeEnumerationBtn;

    // Buttons for assertion editing
    @FXML private Button addAssertionBtn;
    @FXML private Button removeAssertionBtn;

    // Facet controls (will be created dynamically based on datatype)
    private final java.util.Map<XsdFacetType, TextField> facetFields = new java.util.HashMap<>();
    private final java.util.Map<XsdFacetType, Label> facetLabels = new java.util.HashMap<>();

    private boolean updating = false; // Prevent recursive updates
    private boolean patternsFromReferencedType = false; // Track if patterns come from referenced type (read-only)
    private boolean facetsFromReferencedType = false; // Track if facets come from referenced type (read-only)
    private boolean enumerationsFromReferencedType = false; // Track if enumerations come from referenced type (read-only)
    private boolean assertionsFromReferencedType = false; // Track if assertions come from referenced type (read-only)

    // Schema Information section controls
    @FXML private Label schemaFilePathLabel;
    @FXML private Label schemaNamespaceLabel;
    @FXML private Label schemaVersionLabel;
    @FXML private TitledPane schemaInfoPane;

    /**
     * Creates a new properties panel. The layout is loaded from
     * {@code /pages/views/XsdPropertiesPanel.fxml}.
     *
     * @param editorContext the editor context
     */
    public XsdPropertiesPanel(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
        FxmlViews.load(this, "/pages/views/XsdPropertiesPanel.fxml");

        initializeUI();
        setupListeners();

        // Listen to selection changes
        editorContext.getSelectionModel().addSelectionListener((oldSelection, newSelection) -> {
            logger.debug("Selection changed in XsdPropertiesPanel: oldSize={}, newSize={}",
                    oldSelection != null ? oldSelection.size() : 0,
                    newSelection != null ? newSelection.size() : 0);
            if (!newSelection.isEmpty()) {
                // Get the first (primary) selected node
                VisualNode firstNode = newSelection.iterator().next();
                logger.debug("Updating properties for node: {}", firstNode.getLabel());
                updateProperties(firstNode);
            } else {
                logger.debug("Clearing properties (no selection)");
                clearProperties();
            }
        });

        // Check if there's already a selection when the panel is created
        // This ensures properties are shown immediately if a node is already selected
        java.util.Set<VisualNode> currentSelection = editorContext.getSelectionModel().getSelectedNodes();
        if (currentSelection != null && !currentSelection.isEmpty()) {
            VisualNode firstNode = currentSelection.iterator().next();
            logger.debug("Initial selection found: {}, updating properties", firstNode.getLabel());
            updateProperties(firstNode);
        }
    }

    /**
     * Wires the loaded layout: registers the sections in the customizable section containers of
     * the three tabs and sets up the data-driven parts (editors, list cells, autocomplete).
     */
    private void initializeUI() {
        // Attributes tab. Schema Information is a fixed section at the top of the panel,
        // outside the TabPane, so it stays visible regardless of selection.
        attributesSectionContainer = new CustomizableSectionContainer("xsd-attributes");
        attributesSectionContainer.addSection(new SectionDefinition("general", "General", generalPane, true, 1));
        attributesSectionContainer.addSection(new SectionDefinition("cardinality", "Cardinality", cardinalityPane, true, 2));
        attributesSectionContainer.addSection(new SectionDefinition("constraints", "Constraints", constraintsPane, true, 3));
        attributesSectionContainer.addSection(new SectionDefinition("advanced", "Advanced", advancedPane, true, 4));
        attributesSectionContainer.setPadding(new Insets(10));
        attributesSectionContainer.initialize();
        attributesScroll.setContent(attributesSectionContainer);

        // Facets tab
        facetsSectionContainer = new CustomizableSectionContainer("xsd-facets");
        facetsSectionContainer.addSection(new SectionDefinition("facets", "Facets", facetsPane, true, 1));
        facetsSectionContainer.addSection(new SectionDefinition("enumerations", "Enumerations", enumerationsPane, true, 2));
        facetsSectionContainer.addSection(new SectionDefinition("patterns", "Patterns", patternsPane, true, 3));
        facetsSectionContainer.addSection(new SectionDefinition("assertions", "Assertions", assertionsPane, true, 4));
        facetsSectionContainer.setPadding(new Insets(10));
        facetsSectionContainer.initialize();
        facetsScroll.setContent(facetsSectionContainer);

        // Docs tab: inline documentation editor and the structured AppInfo editor (XsdDoc)
        documentationEditor = new InlineDocumentationEditor(editorContext);
        documentationPane.setContent(documentationEditor);
        appInfoEditorPanel = new AppInfoEditorPanel(editorContext);
        appInfoBox.getChildren().add(appInfoEditorPanel);
        VBox.setVgrow(appInfoEditorPanel, Priority.ALWAYS);

        docsSectionContainer = new CustomizableSectionContainer("xsd-docs");
        docsSectionContainer.addSection(new SectionDefinition("documentation", "Documentation", documentationPane, true, 1));
        docsSectionContainer.addSection(new SectionDefinition("appInfo", "AppInfo", appInfoPane, true, 2));
        docsSectionContainer.setPadding(new Insets(10));
        docsSectionContainer.initialize();
        docsScroll.setContent(docsSectionContainer);

        // The type picker lists the schema's named types (incl. imports) and every built-in type;
        // choices are rebuilt each time the dropdown opens so later imports are seen.
        typePicker.setChoices(() -> TypeChoices.of(editorContext.getSchema()));

        // Custom cell factory to show read-only indicator
        patternsListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().remove("theme-text-secondary");
                } else {
                    setText(item);
                    if (patternsFromReferencedType) {
                        IconifyIcon lockIcon = new IconifyIcon("bi-lock-fill");
                        lockIcon.setIconSize(12);
                        lockIcon.getStyleClass().add("lock-icon-muted");
                        setGraphic(lockIcon);
                        if (!getStyleClass().contains("theme-text-secondary")) {
                            getStyleClass().add("theme-text-secondary");
                        }
                    } else {
                        setGraphic(null);
                        getStyleClass().remove("theme-text-secondary");
                    }
                }
            }
        });

        // Enable/disable the remove buttons based on selection
        patternsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removePatternBtn.setDisable(newVal == null || patternsFromReferencedType);
        });
        enumerationsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeEnumerationBtn.setDisable(newVal == null || enumerationsFromReferencedType);
        });
        assertionsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeAssertionBtn.setDisable(newVal == null || assertionsFromReferencedType);
        });
    }

    /** Header gear: opens the section settings for the active tab. */
    @FXML
    public void onCustomizeSections() {
        showActiveTabSectionSettings(gearButton);
    }

    /** Header close button: hides the panel. */
    @FXML
    public void onHidePanel() {
        setVisible(false);
        setManaged(false);
    }

    @FXML
    public void onAddPattern() {
        handleAddPattern();
    }

    @FXML
    public void onRemovePattern() {
        String selectedPattern = patternsListView.getSelectionModel().getSelectedItem();
        if (selectedPattern != null) {
            handleDeletePattern(selectedPattern);
        }
    }

    @FXML
    public void onAddEnumeration() {
        handleAddEnumeration();
    }

    @FXML
    public void onRemoveEnumeration() {
        String selectedEnum = enumerationsListView.getSelectionModel().getSelectedItem();
        if (selectedEnum != null) {
            handleDeleteEnumeration(selectedEnum);
        }
    }

    @FXML
    public void onAddAssertion() {
        handleAddAssertion();
    }

    @FXML
    public void onRemoveAssertion() {
        String selectedAssertion = assertionsListView.getSelectionModel().getSelectedItem();
        if (selectedAssertion != null) {
            handleDeleteAssertion(selectedAssertion);
        }
    }

    /**
     * Opens the section settings popup for the section container of the currently active tab.
     */
    private void showActiveTabSectionSettings(javafx.scene.Node anchor) {
        CustomizableSectionContainer container = getActiveSectionContainer();
        if (container == null || anchor == null) return;

        org.fxt.freexmltoolkit.controls.shared.SectionSettingsPopup popup =
                new org.fxt.freexmltoolkit.controls.shared.SectionSettingsPopup(container);
        var order = container.getCurrentOrder();
        var visibility = new java.util.HashMap<String, Boolean>();
        var enabled = new java.util.HashMap<String, Boolean>();
        for (String id : order) {
            visibility.put(id, container.isSectionVisible(id));
            enabled.put(id, container.isSectionEnabled(id));
        }
        popup.refresh(order, visibility, enabled);
        popup.show(anchor);
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

        // Name field - also fire command when Enter is pressed
        nameField.setOnAction(e -> {
            if (!updating && currentNode != null) {
                handleNameChange();
            }
        });

        // Type picker - fire command on a user commit (Enter, pick, focus lost)
        typePicker.setOnCommit(newType -> {
            if (!updating && currentNode != null) {
                handleTypeChange(newType);
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
     * Updates the properties panel with the given node's data.
     * Can be called externally to force an update.
     */
    public void updateProperties(VisualNode node) {
        this.currentNode = node;
        updating = true;

        // Special handling for comment nodes
        if (node.getModelObject() instanceof XsdComment) {
            showCommentProperties(node);
            return;
        }

        // Restore normal tab pane if it was replaced by comment view
        if (getCenter() != mainTabPane) {
            setCenter(mainTabPane);
        }

        try {
            // Enable panel for editing only in edit mode, but always allow viewing
            boolean isEditMode = editorContext.isEditMode();
            logger.info("==== Updating properties panel for node: {}, Edit Mode: {} ====", node.getLabel(), isEditMode);

            // Always enable the tab pane for viewing, but disable editing controls.
            // Header + Schema Information stay enabled all the time.
            mainTabPane.setDisable(false);

            // Disable editing controls if not in edit mode
            nameField.setEditable(isEditMode);
            typePicker.setDisable(!isEditMode);
            minOccursSpinner.setDisable(!isEditMode);
            maxOccursSpinner.setDisable(!isEditMode || unboundedCheckBox.isSelected());
            unboundedCheckBox.setDisable(!isEditMode);

            // Documentation editor
            documentationEditor.setEditMode(isEditMode);

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

            logger.debug("Controls editable state set: nameField={}, isEditMode={}, isElement={}, constraintsEnabled={}",
                    nameField.isEditable(), isEditMode, isElement, constraintsEnabled);

            // Update General section
            nameField.setText(node.getLabel());

            // Get model object for property access
            Object modelObject = node.getModelObject();

            // Get effective type from the model object
            // This handles both explicit type references and inline simpleTypes with restrictions
            String currentType = getEffectiveType(modelObject);

            // Show the current type (silent: setValue never fires the commit handler)
            typePicker.setValue(currentType);

            // Update type icon
            updateTypeIcon(currentType);

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
                documentationEditor.setNode(xsdNode);
                appInfoEditorPanel.setNode(xsdNode);
                logger.debug("Documentation editor and AppInfo editor updated with values");
            } else {
                logger.warn("ModelObject is not an XsdNode, cannot load documentation/appinfo");
                documentationEditor.setNode(null);
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

            // Update facets column content based on model data
            updateFacetsColumn(modelObject);

            logger.debug("Updated properties panel for node: {}", node.getLabel());

        } finally {
            updating = false;
        }
    }

    /**
     * Updates the facets column (Facets, Patterns, Enumerations, Assertions) with data from the model.
     */
    private void updateFacetsColumn(Object modelObject) {
        // Clear all lists first
        facetsGridPane.getChildren().clear();
        facetFields.clear();
        facetLabels.clear();
        patternsListView.getItems().clear();
        enumerationsListView.getItems().clear();
        assertionsListView.getItems().clear();

        if (modelObject instanceof XsdElement xsdElement) {
            updateFacetsColumnForElement(xsdElement);
        } else if (modelObject instanceof XsdAttribute xsdAttribute) {
            updateFacetsColumnForAttribute(xsdAttribute);
        } else {
            logger.debug("Model object is not an XsdElement or XsdAttribute, cannot update facets column");
        }
    }

    /**
     * Updates the facets column for an XsdElement (supports both inline and referenced type facets).
     */
    private void updateFacetsColumnForElement(XsdElement xsdElement) {
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

        // Load enumerations from model (with type resolution)
        enumerationsFromReferencedType = false;
        java.util.List<String> effectiveEnumerations = getEffectiveEnumerations(xsdElement);
        enumerationsListView.getItems().addAll(effectiveEnumerations);

        // Update button states based on whether enumerations are from referenced type
        addEnumerationBtn.setDisable(enumerationsFromReferencedType);
        removeEnumerationBtn.setDisable(true); // Will be enabled by selection listener if not from referenced type

        // Update placeholder text if enumerations are from referenced type
        if (enumerationsFromReferencedType && !effectiveEnumerations.isEmpty()) {
            enumerationsListView.setPlaceholder(new Label("Enumerations from referenced type (read-only)"));
        } else {
            enumerationsListView.setPlaceholder(new Label("No enumeration values defined"));
        }

        logger.debug("Loaded {} enumerations from element '{}' (from referenced type: {})",
                effectiveEnumerations.size(), xsdElement.getName(), enumerationsFromReferencedType);

        // Load assertions from model (with type resolution)
        assertionsFromReferencedType = false;
        java.util.List<String> effectiveAssertions = getEffectiveAssertions(xsdElement);
        assertionsListView.getItems().addAll(effectiveAssertions);

        // Update button states based on whether assertions are from referenced type
        addAssertionBtn.setDisable(assertionsFromReferencedType);
        removeAssertionBtn.setDisable(true); // Will be enabled by selection listener if not from referenced type

        // Update placeholder text if assertions are from referenced type
        if (assertionsFromReferencedType && !effectiveAssertions.isEmpty()) {
            assertionsListView.setPlaceholder(new Label("Assertions from referenced type (read-only)"));
        } else {
            assertionsListView.setPlaceholder(new Label("No assertions defined (requires XSD 1.1)"));
        }

        logger.debug("Loaded {} assertions from element '{}' (from referenced type: {})",
                effectiveAssertions.size(), xsdElement.getName(), assertionsFromReferencedType);

        logger.debug("Updated facets column for element: {}", xsdElement.getName());
    }

    /**
     * Updates the facets column for an XsdAttribute by resolving its referenced type.
     * All facets, enumerations, patterns, and assertions are displayed as read-only
     * since attributes always reference a type (they don't have inline constraints).
     */
    private void updateFacetsColumnForAttribute(XsdAttribute xsdAttribute) {
        String typeRef = xsdAttribute.getType();

        // All buttons disabled for attributes (always read-only)
        addPatternBtn.setDisable(true);
        removePatternBtn.setDisable(true);
        addEnumerationBtn.setDisable(true);
        removeEnumerationBtn.setDisable(true);
        addAssertionBtn.setDisable(true);
        removeAssertionBtn.setDisable(true);

        if (typeRef == null || typeRef.isEmpty() || typeRef.startsWith("xs:") || typeRef.startsWith("xsd:")) {
            // Built-in type or no type — show applicable facets grid for the built-in type but no values
            if (typeRef != null && !typeRef.isEmpty()) {
                updateFacetsForAttribute(xsdAttribute, typeRef);
            }
            patternsListView.setPlaceholder(new Label("No patterns defined for this attribute"));
            enumerationsListView.setPlaceholder(new Label("No enumeration values defined"));
            assertionsListView.setPlaceholder(new Label("No assertions defined"));
            logger.debug("Attribute '{}' has built-in type '{}', no referenced type to resolve",
                    xsdAttribute.getName(), typeRef);
            return;
        }

        // Remove namespace prefix if present
        String typeName = typeRef;
        if (typeName.contains(":")) {
            typeName = typeName.substring(typeName.indexOf(":") + 1);
        }

        // Find the schema root
        XsdNode current = xsdAttribute;
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }

        if (!(current instanceof XsdSchema schema)) {
            logger.debug("Cannot find schema root for attribute '{}'", xsdAttribute.getName());
            return;
        }

        // Resolve the referenced SimpleType's restriction
        XsdRestriction restriction = null;
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType st && typeName.equals(st.getName())) {
                for (XsdNode typeChild : st.getChildren()) {
                    if (typeChild instanceof XsdRestriction r) {
                        restriction = r;
                        break;
                    }
                }
                break;
            }
        }

        // Update facets grid with resolved type info
        if (restriction != null && restriction.getBase() != null) {
            updateFacetsForAttribute(xsdAttribute, restriction.getBase());
            // Show resolved facet values as read-only
            for (org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet : restriction.getFacets()) {
                XsdFacetType ft = facet.getFacetType();
                if (ft != XsdFacetType.ENUMERATION && ft != XsdFacetType.PATTERN && ft != XsdFacetType.ASSERTION) {
                    TextField field = facetFields.get(ft);
                    if (field != null) {
                        field.setPromptText(facet.getValue());
                    }
                }
            }
        }

        // Load enumerations from referenced type
        java.util.List<String> enumerations = findEnumerationsInType(schema, typeName);
        if (!enumerations.isEmpty()) {
            enumerationsFromReferencedType = true;
            enumerationsListView.getItems().addAll(enumerations);
            enumerationsListView.setPlaceholder(new Label("Enumerations from type '" + typeName + "' (read-only)"));
        } else {
            enumerationsListView.setPlaceholder(new Label("No enumeration values defined"));
        }

        // Load patterns from referenced type
        java.util.List<String> patterns = findPatternsInType(schema, typeName);
        if (!patterns.isEmpty()) {
            patternsFromReferencedType = true;
            patternsListView.getItems().addAll(patterns);
            patternsListView.setPlaceholder(new Label("Patterns from type '" + typeName + "' (read-only)"));
        } else {
            patternsListView.setPlaceholder(new Label("No patterns defined for this attribute"));
        }

        // Load assertions from referenced type
        java.util.List<String> assertions = findAssertionsInType(schema, typeName);
        if (!assertions.isEmpty()) {
            assertionsFromReferencedType = true;
            assertionsListView.getItems().addAll(assertions);
            assertionsListView.setPlaceholder(new Label("Assertions from type '" + typeName + "' (read-only)"));
        } else {
            assertionsListView.setPlaceholder(new Label("No assertions defined"));
        }

        logger.debug("Updated facets column for attribute '{}' (type '{}': {} enumerations, {} patterns, {} assertions)",
                xsdAttribute.getName(), typeName, enumerations.size(), patterns.size(), assertions.size());
    }

    /**
     * Updates the facets grid for an attribute with read-only facet fields.
     * Shows applicable facets for the given datatype but all disabled.
     */
    private void updateFacetsForAttribute(XsdAttribute attribute, String datatype) {
        facetsFromReferencedType = true; // All attribute facets are read-only

        java.util.Set<XsdFacetType> applicableFacets = XsdDatatypeFacets.getApplicableFacets(datatype);

        java.util.List<XsdFacetType> implementedFacets = java.util.List.of(
                XsdFacetType.LENGTH, XsdFacetType.MIN_LENGTH, XsdFacetType.MAX_LENGTH,
                XsdFacetType.TOTAL_DIGITS, XsdFacetType.FRACTION_DIGITS,
                XsdFacetType.MIN_INCLUSIVE, XsdFacetType.MAX_INCLUSIVE,
                XsdFacetType.MIN_EXCLUSIVE, XsdFacetType.MAX_EXCLUSIVE,
                XsdFacetType.WHITE_SPACE, XsdFacetType.EXPLICIT_TIMEZONE
        );

        int row = 0;
        for (XsdFacetType facetType : implementedFacets) {
            if (!applicableFacets.contains(facetType)) {
                continue;
            }

            boolean isFixed = XsdDatatypeFacets.isFacetFixed(datatype, facetType);

            TextField textField = new TextField();
            String defaultValue = XsdDatatypeFacets.getDefaultFacetPlaceholder(datatype, facetType);
            textField.setPromptText(defaultValue != null ? defaultValue : facetType.getXmlName());
            HBox.setHgrow(textField, Priority.ALWAYS);
            textField.setDisable(true);
            textField.setStyle("-fx-opacity: 0.7;");

            Label label;
            if (isFixed) {
                String fixedValue = XsdDatatypeFacets.getFixedFacetValue(datatype, facetType);
                textField.setPromptText(fixedValue);
                textField.setStyle("-fx-opacity: 0.6;");
                label = new Label(facetType.getXmlName() + ":");
                label.setMinWidth(100);
                label.getStyleClass().add("theme-text-muted");
            } else {
                IconifyIcon lockIcon = new IconifyIcon("bi-lock-fill");
                lockIcon.setIconSize(12);
                lockIcon.getStyleClass().add("lock-icon-muted");
                label = new Label(facetType.getXmlName() + ":", lockIcon);
                label.setMinWidth(100);
                label.getStyleClass().add("theme-text-secondary");
            }

            facetLabels.put(facetType, label);
            facetFields.put(facetType, textField);
            facetsGridPane.add(label, 0, row);
            facetsGridPane.add(textField, 1, row);
            row++;
        }

        if (row == 0) {
            Label noFacetsLabel = new Label("No applicable facets for datatype: " + datatype);
            noFacetsLabel.getStyleClass().add("theme-text-muted");
            facetsGridPane.add(noFacetsLabel, 0, 0, 2, 1);
        }

        logger.debug("Created {} read-only facet controls for attribute '{}' (datatype '{}')",
                row, attribute.getName(), datatype);
    }

    /**
     * Clears all property fields.
     */
    private void clearProperties() {
        this.currentNode = null;
        updating = true;

        try {
            mainTabPane.setDisable(true);

            nameField.clear();
            typePicker.setValue("");
            minOccursSpinner.getValueFactory().setValue(1);
            maxOccursSpinner.getValueFactory().setValue(1);
            unboundedCheckBox.setSelected(false);
            maxOccursSpinner.setDisable(false);

            documentationEditor.setNode(null);
            appInfoEditorPanel.setNode(null);

            nillableCheckBox.setSelected(false);
            abstractCheckBox.setSelected(false);
            fixedCheckBox.setSelected(false);
            fixedValueField.clear();
            fixedValueField.setDisable(true);

            formComboBox.setValue(null);
            useComboBox.setValue(null);
            substitutionGroupField.clear();

            // Clear facets column
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
     *
     * @param newType the committed type reference (trimmed; empty removes the type)
     */
    private void handleTypeChange(String newType) {
        if (currentNode == null || currentNode.getModelObject() == null) {
            return;
        }
        Object modelObject = currentNode.getModelObject();

        // Get current type from model
        String currentType;
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

            // Check if we're setting a type on an element that has a compositor
            // When setting a type on an element that already has a compositor, ask the user first.
            if (node instanceof XsdElement element && newType != null && !newType.isEmpty()
                    && element.hasCompositor()) {
                Alert confirmDialog = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                        Alert.AlertType.CONFIRMATION, "Compositor Present", "Element '" + element.getName() + "' already has a Compositor (Sequence/Choice/All)", "Should the Compositor be removed to set a type?");

                ButtonType removeButton = new ButtonType("Yes, remove Compositor");
                ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                confirmDialog.getButtonTypes().setAll(removeButton, cancelButton);

                Optional<ButtonType> result = confirmDialog.showAndWait();
                if (!result.isPresent() || result.get() != removeButton) {
                    logger.debug("User cancelled type change due to existing compositor");
                    // Reset the field to the old value
                    updating = true;
                    typePicker.setValue(currentType);
                    updating = false;
                    return;
                }

                // User confirmed removal - we'll remove compositor below
                // First, remove the compositor
                for (XsdNode child : new java.util.ArrayList<>(element.getChildren())) {
                    if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType complexType) {
                        // Remove compositor from complexType
                        for (XsdNode ctChild : new java.util.ArrayList<>(complexType.getChildren())) {
                            if (ctChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSequence ||
                                ctChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdChoice ||
                                ctChild instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAll) {
                                // Remove the compositor via command (for undo/redo support)
                                DeleteNodeCommand deleteCmd = new DeleteNodeCommand(ctChild);
                                editorContext.getCommandManager().executeCommand(deleteCmd);
                                logger.info("Removed compositor '{}' from element '{}'",
                                        ctChild.getClass().getSimpleName(), element.getName());
                                break;
                            }
                        }
                        // If complexType is now empty (no other children), remove it too
                        if (complexType.getChildren().isEmpty()) {
                            DeleteNodeCommand deleteComplexTypeCmd = new DeleteNodeCommand(complexType);
                            editorContext.getCommandManager().executeCommand(deleteComplexTypeCmd);
                            logger.info("Removed empty complexType from element '{}'", element.getName());
                        }
                        break;
                    }
                }
            }

            // Now set the type
            ChangeTypeCommand command = new ChangeTypeCommand(editorContext, node, newType);
            editorContext.getCommandManager().executeCommand(command);
            logger.debug("Executed ChangeTypeCommand: {} -> {}", currentType, newType);

            // Update facets after type change to reflect applicable facets for the new type
            updateFacetsColumn(modelObject);
            logger.debug("Updated facets after type change");

            // Update type icon
            updateTypeIcon(newType);
        }
    }

    /**
     * Updates the type icon based on the given datatype string.
     */
    private void updateTypeIcon(String datatype) {
        if (typeIcon == null) {
            return;
        }

        String iconLiteral = getDataTypeIcon(datatype);
        typeIcon.setIconLiteral(iconLiteral);
        logger.debug("Updated type icon to {} for datatype {}", iconLiteral, datatype);
    }

    /**
     * Maps specific XSD datatype strings to unique Bootstrap icons.
     * Each common datatype has its own distinct icon for visual differentiation.
     */
    private String getDataTypeIcon(String datatype) {
        if (datatype == null || datatype.isEmpty()) {
            return "bi-diagram-3";  // Default type
        }

        String lower = datatype.toLowerCase();

        // String types - different icons for variations
        if (lower.endsWith("string")) {
            return "bi-file-text";
        }
        if (lower.endsWith("token")) {
            return "bi-chat-left-quote";
        }
        if (lower.endsWith("ncname")) {
            return "bi-tag";
        }
        if (lower.endsWith("name")) {
            return "bi-tag-fill";
        }
        if (lower.endsWith(":id") || lower.equals("id")) {
            return "bi-shield-check";
        }

        // Integer types - distinct icons for each
        if (lower.endsWith("integer")) {
            return "bi-hash";
        }
        if (lower.endsWith(":int") || lower.equals("int")) {
            return "bi-circle-fill";
        }
        if (lower.endsWith("long")) {
            return "bi-circle";
        }
        if (lower.endsWith("short")) {
            return "bi-dash-square";
        }
        if (lower.endsWith("byte")) {
            return "bi-subtract";
        }

        // Decimal/Float types - distinct icons
        if (lower.endsWith("decimal")) {
            return "bi-percent";
        }
        if (lower.endsWith("float")) {
            return "bi-hexagon-fill";
        }
        if (lower.endsWith("double")) {
            return "bi-box";
        }

        // Date/Time types - each its own icon
        if (lower.endsWith("datetime")) {
            return "bi-calendar-event";
        }
        if (lower.endsWith("duration")) {
            return "bi-hourglass-split";
        }
        if (lower.endsWith("gmonth")) {
            return "bi-calendar3";
        }
        if (lower.endsWith("gyear")) {
            return "bi-calendar2";
        }
        if (lower.endsWith("date")) {
            return "bi-calendar";
        }
        if (lower.endsWith("time")) {
            return "bi-clock";
        }

        // Boolean
        if (lower.endsWith("boolean")) {
            return "bi-toggle-on";
        }

        // Binary/encoded types - distinct icons
        if (lower.endsWith("base64binary")) {
            return "bi-lock";
        }
        if (lower.endsWith("hexbinary")) {
            return "bi-shield-lock";
        }

        // URI/URL types
        if (lower.endsWith("uri") || lower.endsWith("anyuri")) {
            return "bi-link-45deg";
        }
        if (lower.endsWith("qname")) {
            return "bi-question-square";
        }

        // Complex/structured types
        if (lower.endsWith("complextype")) {
            return "bi-diagram-2";
        }
        if (lower.endsWith("simpletype")) {
            return "bi-diagram-3";
        }

        // Default for unknown types
        return "bi-diagram-3";
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
        String currentForm;
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

    // ========== Facets Column Button Handlers ==========

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
            if (child instanceof XsdSimpleType simpleType && typeName.equals(simpleType.getName())) {
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

        return patterns;
    }

    /**
     * Gets the effective enumerations of an element.
     * Enumerations can come from:
     * 1. Inline simpleType with restriction in the element itself (editable)
     * 2. Referenced type (e.g., element type="DayCountConventionType" where DayCountConventionType has enumerations) (read-only)
     *
     * This method also sets the enumerationsFromReferencedType flag to indicate whether
     * the enumerations are editable or read-only.
     *
     * @param element the XSD element
     * @return list of effective enumerations (from element or referenced type)
     */
    private java.util.List<String> getEffectiveEnumerations(XsdElement element) {
        java.util.List<String> enumerations = new java.util.ArrayList<>();
        enumerationsFromReferencedType = false; // Reset flag

        // First, check for enumerations directly on the element (inline simpleType)
        if (!element.getEnumerations().isEmpty()) {
            enumerations.addAll(element.getEnumerations());
            enumerationsFromReferencedType = false; // Inline enumerations are editable
            logger.debug("Found {} inline enumerations on element '{}'", enumerations.size(), element.getName());
            return enumerations;
        }

        // If no direct enumerations, check for enumerations in referenced type
        String typeRef = element.getType();
        if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:")) {
            // Remove namespace prefix if present
            String typeName = typeRef;
            if (typeName.contains(":")) {
                typeName = typeName.substring(typeName.indexOf(":") + 1);
            }

            logger.debug("Element '{}' references type '{}', searching for enumerations", element.getName(), typeName);

            // Find the schema root
            XsdNode current = element;
            while (current != null && !(current instanceof XsdSchema)) {
                current = current.getParent();
            }

            if (current instanceof XsdSchema schema) {
                // Search for the type definition
                enumerations.addAll(findEnumerationsInType(schema, typeName));
                if (!enumerations.isEmpty()) {
                    enumerationsFromReferencedType = true; // Referenced type enumerations are read-only
                    logger.debug("Found {} enumerations in referenced type '{}' (read-only)", enumerations.size(), typeName);
                }
            }
        }

        return enumerations;
    }

    /**
     * Finds enumerations in a named type definition within the schema.
     *
     * @param schema the schema to search
     * @param typeName the name of the type to find
     * @return list of enumerations found in the type
     */
    private java.util.List<String> findEnumerationsInType(XsdSchema schema, String typeName) {
        java.util.List<String> enumerations = new java.util.ArrayList<>();

        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType && typeName.equals(simpleType.getName())) {
                // Found the type, now extract enumerations from restriction
                for (XsdNode typeChild : simpleType.getChildren()) {
                    if (typeChild instanceof XsdRestriction restriction) {
                        for (org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet : restriction.getFacets()) {
                            if (facet.getFacetType() == org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType.ENUMERATION) {
                                enumerations.add(facet.getValue());
                            }
                        }
                    }
                }
                break;
            }
        }

        return enumerations;
    }

    /**
     * Gets the effective assertions of an element.
     * Assertions can come from:
     * 1. Inline type definition in the element itself (editable)
     * 2. Referenced type (e.g., element type="ValidatedType" where ValidatedType has assertions) (read-only)
     *
     * This method also sets the assertionsFromReferencedType flag to indicate whether
     * the assertions are editable or read-only.
     *
     * @param element the XSD element
     * @return list of effective assertions (from element or referenced type)
     */
    private java.util.List<String> getEffectiveAssertions(XsdElement element) {
        java.util.List<String> assertions = new java.util.ArrayList<>();
        assertionsFromReferencedType = false; // Reset flag

        // First, check for assertions directly on the element
        if (!element.getAssertions().isEmpty()) {
            assertions.addAll(element.getAssertions());
            assertionsFromReferencedType = false; // Inline assertions are editable
            logger.debug("Found {} inline assertions on element '{}'", assertions.size(), element.getName());
            return assertions;
        }

        // If no direct assertions, check for assertions in referenced type
        String typeRef = element.getType();
        if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:")) {
            // Remove namespace prefix if present
            String typeName = typeRef;
            if (typeName.contains(":")) {
                typeName = typeName.substring(typeName.indexOf(":") + 1);
            }

            logger.debug("Element '{}' references type '{}', searching for assertions", element.getName(), typeName);

            // Find the schema root
            XsdNode current = element;
            while (current != null && !(current instanceof XsdSchema)) {
                current = current.getParent();
            }

            if (current instanceof XsdSchema schema) {
                // Search for the type definition
                assertions.addAll(findAssertionsInType(schema, typeName));
                if (!assertions.isEmpty()) {
                    assertionsFromReferencedType = true; // Referenced type assertions are read-only
                    logger.debug("Found {} assertions in referenced type '{}' (read-only)", assertions.size(), typeName);
                }
            }
        }

        return assertions;
    }

    /**
     * Finds assertions in a named type definition within the schema.
     * Assertions can be found in both SimpleType (via xs:assertion facet) and ComplexType definitions.
     *
     * @param schema the schema to search
     * @param typeName the name of the type to find
     * @return list of assertions found in the type
     */
    private java.util.List<String> findAssertionsInType(XsdSchema schema, String typeName) {
        java.util.List<String> assertions = new java.util.ArrayList<>();

        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType && typeName.equals(simpleType.getName())) {
                // Found the type, now extract assertions from restriction
                for (XsdNode typeChild : simpleType.getChildren()) {
                    if (typeChild instanceof XsdRestriction restriction) {
                        for (org.fxt.freexmltoolkit.controls.v2.model.XsdFacet facet : restriction.getFacets()) {
                            if (facet.getFacetType() == org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType.ASSERTION) {
                                assertions.add(facet.getValue());
                            }
                        }
                    }
                }
                break;
            }
            // Also check ComplexTypes for assertions (XSD 1.1)
            if (child instanceof XsdComplexType complexType && typeName.equals(complexType.getName())) {
                // ComplexTypes can have assertions directly or in their content model
                // Check for XsdAssert children
                for (XsdNode typeChild : complexType.getChildren()) {
                    if (typeChild instanceof XsdAssert assertNode) {
                        String test = assertNode.getTest();
                        if (test != null && !test.isEmpty()) {
                            assertions.add(test);
                        }
                    }
                }
                break;
            }
        }

        return assertions;
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
            if (child instanceof XsdSimpleType simpleType && typeName.equals(simpleType.getName())) {
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
     * Updates the facets grid with applicable facets based on the element's datatype.
     * Creates UI controls (label + textfield) for each applicable facet.
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
            String defaultValue = XsdDatatypeFacets.getDefaultFacetPlaceholder(datatype, facetType);
            textField.setPromptText(defaultValue != null ? defaultValue : facetType.getXmlName());
            HBox.setHgrow(textField, Priority.ALWAYS);

            // Check if facet is fixed
            boolean isFixed = XsdDatatypeFacets.isFacetFixed(datatype, facetType);
            if (isFixed) {
                String fixedValue = XsdDatatypeFacets.getFixedFacetValue(datatype, facetType);
                textField.setPromptText(fixedValue);
                textField.setDisable(true);
                textField.setStyle("-fx-opacity: 0.6;");
                label.getStyleClass().add("theme-text-muted");
                logger.debug("Facet {} is fixed to '{}' for datatype '{}'", facetType.getXmlName(), fixedValue, datatype);
            } else {
                // Load current facet value from model
                String currentValue = effectiveFacets.get(facetType);
                if (currentValue != null) {
                    if (facetsFromReferencedType) {
                        // Inherited values shown as placeholder, not real text
                        textField.setPromptText(currentValue);
                    } else {
                        textField.setText(currentValue);
                    }
                }

                // Check if facet is from referenced type (read-only)
                if (facetsFromReferencedType) {
                    textField.setDisable(true);
                    textField.setStyle("-fx-opacity: 0.7;");
                    label.getStyleClass().add("theme-text-secondary");
                    // Add lock icon to indicate read-only
                    IconifyIcon lockIcon = new IconifyIcon("bi-lock-fill");
                    lockIcon.setIconSize(12);
                    lockIcon.getStyleClass().add("lock-icon-muted");
                    Label labelWithIcon = new Label(facetType.getXmlName() + ":", lockIcon);
                    labelWithIcon.setMinWidth(100);
                    labelWithIcon.getStyleClass().add("theme-text-secondary");
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

            // Fixed facets are added here; the editable / referenced-type cases were already added above.
            if (isFixed) {
                facetsGridPane.add(label, 0, row);
                facetsGridPane.add(textField, 1, row);
            }

            row++;
        }

        if (row == 0) {
            Label noFacetsLabel = new Label("No applicable facets for datatype: " + datatype);
            noFacetsLabel.getStyleClass().add("theme-text-muted");
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

    /**
     * Updates the schema information section with file path, namespace, and version.
     *
     * @param filePath the absolute path to the XSD file
     * @param targetNamespace the target namespace of the schema
     * @param version the XSD version (e.g., "1.0" or "1.1")
     */
    public void updateSchemaInformation(String filePath, String targetNamespace, String version) {
        javafx.application.Platform.runLater(() -> {
            if (schemaFilePathLabel != null) {
                schemaFilePathLabel.setText(filePath != null && !filePath.isEmpty()
                    ? filePath
                    : "No file loaded");
            }
            if (schemaNamespaceLabel != null) {
                schemaNamespaceLabel.setText(targetNamespace != null && !targetNamespace.isEmpty()
                    ? targetNamespace
                    : "No target namespace");
            }
            if (schemaVersionLabel != null) {
                schemaVersionLabel.setText(version != null && !version.isEmpty()
                    ? "XSD " + version
                    : "-");
            }
        });
    }

    /**
     * Shows a simplified properties view for comment nodes.
     * Replaces the standard tab pane with a TextArea for editing the comment content.
     */
    private void showCommentProperties(VisualNode node) {
        try {
            mainTabPane.setDisable(false);

            XsdComment comment = (XsdComment) node.getModelObject();
            boolean isEditMode = editorContext.isEditMode();

            VBox commentPanel = new VBox(10);
            commentPanel.setPadding(new Insets(15));

            // Title
            HBox titleBox = new HBox(8);
            titleBox.setAlignment(Pos.CENTER_LEFT);
            IconifyIcon commentIcon = new IconifyIcon("bi-chat-left-quote");
            commentIcon.setIconSize(18);
            commentIcon.setIconColor(Color.web("#6c757d"));
            Label titleLabel = new Label("Comment");
            titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            titleBox.getChildren().addAll(commentIcon, titleLabel);

            // TextArea for comment content
            javafx.scene.control.TextArea contentArea = new javafx.scene.control.TextArea(
                    comment.getContent() != null ? comment.getContent() : "");
            contentArea.setWrapText(true);
            contentArea.setPrefRowCount(8);
            contentArea.setEditable(isEditMode);
            contentArea.setPromptText("Enter comment text...");
            VBox.setVgrow(contentArea, Priority.ALWAYS);

            // Apply button
            Button applyBtn = new Button("Apply");
            IconifyIcon applyIcon = new IconifyIcon("bi-check-circle");
            applyIcon.setIconSize(16);
            applyIcon.setIconColor(Color.web("#28a745"));
            applyBtn.setGraphic(applyIcon);
            applyBtn.getStyleClass().add("btn-success");
            applyBtn.setDisable(!isEditMode);
            applyBtn.setOnAction(e -> {
                String newContent = contentArea.getText();
                if (!java.util.Objects.equals(newContent, comment.getContent())) {
                    EditCommentCommand cmd = new EditCommentCommand(comment, newContent);
                    editorContext.getCommandManager().executeCommand(cmd);
                }
            });

            commentPanel.getChildren().addAll(titleBox, contentArea, applyBtn);

            setCenter(commentPanel);
        } finally {
            updating = false;
        }
    }

    /**
     * Returns the section container for the currently active tab.
     * Used by MultiFunctionalSidePane to show the settings popup.
     */
    public CustomizableSectionContainer getActiveSectionContainer() {
        if (mainTabPane == null) return null;
        Tab selectedTab = mainTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab == attributesTab) return attributesSectionContainer;
        if (selectedTab == facetsTab) return facetsSectionContainer;
        if (selectedTab == docsTab) return docsSectionContainer;
        return attributesSectionContainer;
    }
}
