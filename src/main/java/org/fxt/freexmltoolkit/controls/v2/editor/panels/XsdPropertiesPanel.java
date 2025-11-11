package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.*;
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

        // Create accordion with sections
        Accordion accordion = new Accordion();
        accordion.getPanes().addAll(
                createGeneralSection(),
                createDocumentationSection(),
                createConstraintsSection(),
                createAdvancedSection()
        );

        // Expand General section by default
        accordion.setExpandedPane(accordion.getPanes().get(0));

        VBox.setVgrow(accordion, Priority.ALWAYS);

        getChildren().addAll(titleLabel, new Separator(), accordion);

        // Initially disabled until a node is selected
        setDisable(true);
    }

    /**
     * Creates the General properties section.
     */
    private TitledPane createGeneralSection() {
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

        TitledPane pane = new TitledPane("General", grid);
        pane.setAnimated(false);
        return pane;
    }

    /**
     * Creates the Documentation section.
     */
    private TitledPane createDocumentationSection() {
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

        TitledPane pane = new TitledPane("Documentation", vbox);
        pane.setAnimated(false);
        return pane;
    }

    /**
     * Creates the Constraints section.
     */
    private TitledPane createConstraintsSection() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        nillableCheckBox = new CheckBox("Nillable (allows xsi:nil='true')");
        abstractCheckBox = new CheckBox("Abstract (cannot be used directly)");
        fixedCheckBox = new CheckBox("Fixed value");

        vbox.getChildren().addAll(nillableCheckBox, abstractCheckBox, fixedCheckBox);

        TitledPane pane = new TitledPane("Constraints", vbox);
        pane.setAnimated(false);
        pane.setExpanded(false);
        return pane;
    }

    /**
     * Creates the Advanced section.
     */
    private TitledPane createAdvancedSection() {
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

        TitledPane pane = new TitledPane("Advanced", grid);
        pane.setAnimated(false);
        pane.setExpanded(false);
        return pane;
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
     * Sets up change listeners for property controls.
     */
    private void setupListeners() {
        // Name field
        nameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && !updating && currentNode != null) {
                handleNameChange();
            }
        });

        // Type field
        typeField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && !updating && currentNode != null) {
                handleTypeChange();
            }
        });

        // Cardinality controls
        minOccursSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updating && currentNode != null) {
                handleCardinalityChange();
            }
        });

        maxOccursSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updating && currentNode != null) {
                handleCardinalityChange();
            }
        });

        unboundedCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            maxOccursSpinner.setDisable(newVal);
            if (!updating && currentNode != null) {
                handleCardinalityChange();
            }
        });

        // Documentation field
        documentationArea.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && !updating && currentNode != null) {
                handleDocumentationChange();
            }
        });

        // AppInfo field
        appinfoArea.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && !updating && currentNode != null) {
                handleAppinfoChange();
            }
        });
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

            logger.debug("Controls editable state set: nameField={}, documentationArea={}",
                    nameField.isEditable(), documentationArea.isEditable());

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
                String appinfo = xsdNode.getAppinfo();
                logger.debug("Loading documentation: '{}', appinfo: '{}'", documentation, appinfo);
                documentationArea.setText(documentation != null ? documentation : "");
                appinfoArea.setText(appinfo != null ? appinfo : "");
                logger.debug("Documentation panel updated with values");
            } else {
                logger.warn("ModelObject is not an XsdNode, cannot load documentation/appinfo");
                documentationArea.setText("");
                appinfoArea.setText("");
            }

            // Update Constraints section (placeholder)
            nillableCheckBox.setSelected(false);
            abstractCheckBox.setSelected(false);
            fixedCheckBox.setSelected(false);

            // Update Advanced section (placeholder)
            formComboBox.setValue(null);
            useComboBox.setValue(null);
            substitutionGroupField.setText("");

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

        } finally {
            updating = false;
        }
    }

    /**
     * Handles name change via RenameNodeCommand.
     */
    private void handleNameChange() {
        String newName = nameField.getText().trim();
        if (!newName.isEmpty() && !newName.equals(currentNode.getLabel())) {
            // Extract XsdNode from VisualNode for the command
            Object modelObject = currentNode.getModelObject();
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                RenameNodeCommand command = new RenameNodeCommand(xsdNode, newName);
                editorContext.getCommandManager().executeCommand(command);
                logger.info("Renamed node to: {}", newName);
            } else {
                logger.warn("Cannot rename node - model object is not an XsdNode: {}",
                        modelObject != null ? modelObject.getClass() : "null");
            }
        }
    }

    /**
     * Handles type change via ChangeTypeCommand.
     */
    private void handleTypeChange() {
        String newType = typeField.getText().trim();
        if (!newType.isEmpty() && currentNode != null) {
            // Extract XsdNode from VisualNode for the command
            Object modelObject = currentNode.getModelObject();
            if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                ChangeTypeCommand command = new ChangeTypeCommand(xsdNode, newType);
                editorContext.getCommandManager().executeCommand(command);
                logger.info("Changed type to: {}", newType);
            } else {
                logger.warn("Cannot change type - model object is not an XsdNode: {}",
                        modelObject != null ? modelObject.getClass() : "null");
            }
        }
    }

    /**
     * Handles cardinality change via ChangeCardinalityCommand.
     */
    private void handleCardinalityChange() {
        if (currentNode == null) {
            return;
        }

        int minOccurs = minOccursSpinner.getValue();
        int maxOccurs = unboundedCheckBox.isSelected()
                ? ChangeCardinalityCommand.UNBOUNDED
                : maxOccursSpinner.getValue();

        // Extract XsdNode from VisualNode for the command
        Object modelObject = currentNode.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            ChangeCardinalityCommand command = new ChangeCardinalityCommand(xsdNode, minOccurs, maxOccurs);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Changed cardinality to [{}..{}]", minOccurs,
                    maxOccurs == ChangeCardinalityCommand.UNBOUNDED ? "*" : maxOccurs);
        } else {
            logger.warn("Cannot change cardinality - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles documentation change via ChangeDocumentationCommand.
     */
    private void handleDocumentationChange() {
        String newDocumentation = documentationArea.getText().trim();

        // Extract XsdNode from VisualNode for the command
        Object modelObject = currentNode.getModelObject();
        if (modelObject instanceof XsdNode xsdNode) {
            // Only create command if documentation actually changed
            String oldDocumentation = xsdNode.getDocumentation();
            if (!newDocumentation.equals(oldDocumentation != null ? oldDocumentation : "")) {
                ChangeDocumentationCommand command = new ChangeDocumentationCommand(
                        editorContext, xsdNode, newDocumentation.isEmpty() ? null : newDocumentation);
                editorContext.getCommandManager().executeCommand(command);
                logger.info("Changed documentation for node: {}", xsdNode.getName());
            }
        } else {
            logger.warn("Cannot change documentation - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles appinfo change via ChangeAppinfoCommand.
     */
    private void handleAppinfoChange() {
        String newAppinfo = appinfoArea.getText().trim();

        // Extract XsdNode from VisualNode for the command
        Object modelObject = currentNode.getModelObject();
        if (modelObject instanceof XsdNode xsdNode) {
            // Only create command if appinfo actually changed
            String oldAppinfo = xsdNode.getAppinfo();
            if (!newAppinfo.equals(oldAppinfo != null ? oldAppinfo : "")) {
                ChangeAppinfoCommand command = new ChangeAppinfoCommand(
                        editorContext, xsdNode, newAppinfo.isEmpty() ? null : newAppinfo);
                editorContext.getCommandManager().executeCommand(command);
                logger.info("Changed appinfo for node: {}", xsdNode.getName());
            }
        } else {
            logger.warn("Cannot change appinfo - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
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
}
