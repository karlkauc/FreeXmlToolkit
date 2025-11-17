package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditFacetCommand;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Panel for viewing and editing XSD facets (restrictions).
 * Displays facets in a grid layout with input fields for each applicable facet type.
 * Similar to XsdPropertiesPanel's facet display.
 *
 * @since 2.0
 */
public class FacetsPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(FacetsPanel.class);

    private final XsdEditorContext editorContext;
    private XsdRestriction currentRestriction;
    private XsdElement currentElement; // For showing referenced type facets
    private boolean isInheritedView; // True if showing facets from referenced type

    private GridPane facetsGridPane;
    private ScrollPane scrollPane;
    private Label infoLabel; // Shows info about inherited facets
    private Label noFacetsLabel; // Shows when no facets are applicable

    // Facet controls - created dynamically based on base type
    private final Map<XsdFacetType, Node> facetControls = new HashMap<>(); // Can be TextField, ComboBox, or Spinner
    private final Map<XsdFacetType, CheckBox> facetFixedCheckBoxes = new HashMap<>();

    private boolean updating = false; // Prevent recursive updates

    /**
     * Creates a new facets panel.
     *
     * @param editorContext the editor context
     */
    public FacetsPanel(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
        initializeUI();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeUI() {
        setPadding(new Insets(10));
        setSpacing(10);

        // Title
        Label titleLabel = new Label("Facets (Restrictions)");
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Description
        Label descLabel = new Label("Facets define restrictions on data types");
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        // Info label for inherited facets
        infoLabel = new Label();
        infoLabel.setStyle("-fx-background-color: #d1ecf1; -fx-text-fill: #0c5460; " +
                          "-fx-padding: 5; -fx-border-color: #bee5eb; -fx-border-radius: 3; " +
                          "-fx-background-radius: 3;");
        infoLabel.setWrapText(true);
        infoLabel.setVisible(false);
        infoLabel.setManaged(false);

        // Grid for facet name/value pairs
        facetsGridPane = new GridPane();
        facetsGridPane.setHgap(10);
        facetsGridPane.setVgap(8);
        facetsGridPane.setPadding(new Insets(10));

        // No facets label (initially visible)
        noFacetsLabel = new Label("No restriction defined. Select a base type to see applicable facets.");
        noFacetsLabel.setStyle("-fx-text-fill: #999999;");
        noFacetsLabel.setWrapText(true);

        facetsGridPane.add(noFacetsLabel, 0, 0, 3, 1);

        // Wrap grid in ScrollPane
        scrollPane = new ScrollPane(facetsGridPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(titleLabel, descLabel, new Separator(), infoLabel, scrollPane);

        // Initially disabled until a restriction is set
        setDisable(true);
    }

    /**
     * Sets the restriction to display/edit.
     *
     * @param restriction the restriction, or null to clear
     */
    public void setRestriction(XsdRestriction restriction) {
        this.currentRestriction = restriction;
        this.currentElement = null;
        this.isInheritedView = false;

        // Hide info label
        infoLabel.setVisible(false);
        infoLabel.setManaged(false);

        if (restriction == null) {
            clearFacets();
            setDisable(true);
            logger.debug("Cleared facets panel");
        } else {
            updateFacetsGrid(restriction.getBase());
            setDisable(!editorContext.isEditMode());
            logger.debug("Loaded facets for restriction (base: {})", restriction.getBase());
        }
    }

    /**
     * Sets an element to display facets from its referenced type (read-only).
     *
     * @param element the element, or null to clear
     */
    public void setElement(XsdElement element) {
        this.currentElement = element;
        this.currentRestriction = null;
        this.isInheritedView = false;

        if (element == null || element.getType() == null) {
            clearFacets();
            setDisable(true);
            infoLabel.setVisible(false);
            infoLabel.setManaged(false);
            logger.debug("Cleared facets panel (no element or type)");
            return;
        }

        // Try to resolve the referenced type
        String typeName = element.getType();
        XsdSimpleType simpleType = findSimpleType(typeName);

        if (simpleType == null) {
            clearFacets();
            setDisable(true);
            infoLabel.setVisible(false);
            infoLabel.setManaged(false);
            logger.debug("Type '{}' not found or not a simple type", typeName);
            return;
        }

        // Extract restriction from the simple type
        XsdRestriction restriction = findRestrictionInSimpleType(simpleType);

        if (restriction == null) {
            clearFacets();
            setDisable(true);
            infoLabel.setVisible(false);
            infoLabel.setManaged(false);
            logger.debug("No restriction found in type '{}'", typeName);
            return;
        }

        // Display facets as read-only
        this.isInheritedView = true;
        this.currentRestriction = restriction;
        updateFacetsGrid(restriction.getBase());
        setDisable(false); // Enable panel to show facets (but fields will be read-only)

        // Show info label
        infoLabel.setText("These facets are inherited from type '" + typeName + "' (read-only)");
        infoLabel.setVisible(true);
        infoLabel.setManaged(true);

        logger.debug("Loaded {} inherited facets from type '{}' for element '{}'",
                    facetControls.size(), typeName, element.getName());
    }

    /**
     * Refreshes the facets from the current restriction.
     * Updates the grid to reflect changes in the model.
     */
    public void refresh() {
        if (currentRestriction != null) {
            updateFacetsGrid(currentRestriction.getBase());
            logger.debug("Refreshed facets panel");
        }
    }

    /**
     * Updates the facets grid based on the base type.
     * Creates input fields only for applicable facets.
     *
     * @param baseType the base type (e.g., "xs:string", "xs:integer")
     */
    private void updateFacetsGrid(String baseType) {
        updating = true;
        try {
            // Clear existing controls
            facetsGridPane.getChildren().clear();
            facetControls.clear();
            facetFixedCheckBoxes.clear();

            if (baseType == null || baseType.isEmpty()) {
                noFacetsLabel.setText("No base type specified. Cannot determine applicable facets.");
                facetsGridPane.add(noFacetsLabel, 0, 0, 3, 1);
                return;
            }

            // Get applicable facets for this base type (create mutable copy)
            Set<XsdFacetType> applicableFacets = new HashSet<>(XsdDatatypeFacets.getApplicableFacets(baseType));

            if (applicableFacets.isEmpty()) {
                noFacetsLabel.setText("No applicable facets for datatype: " + baseType);
                facetsGridPane.add(noFacetsLabel, 0, 0, 3, 1);
                return;
            }

            // Filter out facets that have their own tabs (Enumeration, Pattern, Assertion)
            applicableFacets.removeIf(ft ->
                ft == XsdFacetType.ENUMERATION ||
                ft == XsdFacetType.PATTERN ||
                ft == XsdFacetType.ASSERTION
            );

            if (applicableFacets.isEmpty()) {
                noFacetsLabel.setText("All facets for this datatype are managed in separate tabs (Enumerations, Patterns, Assertions)");
                facetsGridPane.add(noFacetsLabel, 0, 0, 3, 1);
                return;
            }

            // Create input fields for each applicable facet
            int row = 0;
            for (XsdFacetType facetType : applicableFacets) {
                // Check if this facet is fixed for the base type
                boolean isFixed = XsdDatatypeFacets.isFacetFixed(baseType, facetType);

                // Get current value from restriction (if exists)
                String currentValue = "";
                boolean currentFixed = false;
                if (currentRestriction != null) {
                    XsdFacet facet = currentRestriction.getFacetByType(facetType);
                    if (facet != null) {
                        currentValue = facet.getValue() != null ? facet.getValue() : "";
                        currentFixed = facet.isFixed();
                    }
                }

                // Create label
                Label label = new Label(facetType.getXmlName() + ":");
                label.setMinWidth(120);

                // Create appropriate input control based on facet type
                Node inputControl = createFacetControl(facetType, currentValue);

                // Create fixed checkbox
                CheckBox fixedCheckBox = new CheckBox("Fixed");
                fixedCheckBox.setSelected(currentFixed);
                fixedCheckBox.setDisable(isFixed || isInheritedView);

                // Configure based on state
                if (isFixed) {
                    // Fixed by XSD specification
                    inputControl.setDisable(true);
                    inputControl.setStyle("-fx-opacity: 0.7;");
                    label.setStyle("-fx-text-fill: #856404;");
                    Tooltip tooltip = new Tooltip("Fixed value for " + baseType + " (defined by XSD specification)");
                    Tooltip.install(inputControl, tooltip);
                } else if (isInheritedView) {
                    // Inherited from referenced type (read-only)
                    inputControl.setDisable(true);
                    inputControl.setStyle("-fx-opacity: 0.7;");
                    label.setStyle("-fx-text-fill: #666666;");

                    // Add lock icon
                    FontIcon lockIcon = new FontIcon("bi-lock-fill");
                    lockIcon.setIconSize(12);
                    lockIcon.setStyle("-fx-icon-color: #999999;");
                    Label labelWithIcon = new Label(facetType.getXmlName() + ":", lockIcon);
                    labelWithIcon.setMinWidth(120);
                    labelWithIcon.setStyle("-fx-text-fill: #666666;");
                    label = labelWithIcon;
                } else {
                    // Editable facet - add change listeners
                    final XsdFacetType ft = facetType;
                    addChangeListeners(inputControl, ft, fixedCheckBox);
                }

                // Add to grid
                facetsGridPane.add(label, 0, row);
                facetsGridPane.add(inputControl, 1, row);
                facetsGridPane.add(fixedCheckBox, 2, row);

                // Store references
                facetControls.put(facetType, inputControl);
                facetFixedCheckBoxes.put(facetType, fixedCheckBox);

                row++;
            }

            logger.debug("Created {} facet controls for base type '{}'", row, baseType);

        } finally {
            updating = false;
        }
    }

    /**
     * Creates the appropriate input control for a facet type.
     *
     * @param facetType the facet type
     * @param currentValue the current value
     * @return the input control (TextField, ComboBox, or Spinner)
     */
    private Node createFacetControl(XsdFacetType facetType, String currentValue) {
        return switch (facetType) {
            // ComboBox for facets with fixed value lists
            case WHITE_SPACE -> {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll("preserve", "replace", "collapse");
                combo.setValue(currentValue != null && !currentValue.isEmpty() ? currentValue : null);
                combo.setPrefWidth(200);
                combo.setEditable(false);
                yield combo;
            }
            case EXPLICIT_TIMEZONE -> {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll("optional", "required", "prohibited");
                combo.setValue(currentValue != null && !currentValue.isEmpty() ? currentValue : null);
                combo.setPrefWidth(200);
                combo.setEditable(false);
                yield combo;
            }

            // Spinner for integer facets
            case TOTAL_DIGITS -> {
                Spinner<Integer> spinner = new Spinner<>(1, 999, parseIntOrDefault(currentValue, 1));
                spinner.setEditable(true);
                spinner.setPrefWidth(200);
                yield spinner;
            }
            case FRACTION_DIGITS -> {
                Spinner<Integer> spinner = new Spinner<>(0, 99, parseIntOrDefault(currentValue, 0));
                spinner.setEditable(true);
                spinner.setPrefWidth(200);
                yield spinner;
            }
            case LENGTH, MIN_LENGTH, MAX_LENGTH -> {
                Spinner<Integer> spinner = new Spinner<>(0, 999999, parseIntOrDefault(currentValue, 0));
                spinner.setEditable(true);
                spinner.setPrefWidth(200);
                yield spinner;
            }

            // TextField for all other facets
            default -> {
                TextField textField = new TextField(currentValue);
                textField.setPrefWidth(200);
                yield textField;
            }
        };
    }

    /**
     * Adds change listeners to an input control.
     *
     * @param control the input control
     * @param facetType the facet type
     * @param fixedCheckBox the fixed checkbox
     */
    private void addChangeListeners(Node control, XsdFacetType facetType, CheckBox fixedCheckBox) {
        if (control instanceof TextField textField) {
            textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal && !updating) { // Lost focus
                    handleFacetValueChange(facetType, textField.getText(), fixedCheckBox.isSelected());
                }
            });
            textField.setOnAction(e -> {
                if (!updating) {
                    handleFacetValueChange(facetType, textField.getText(), fixedCheckBox.isSelected());
                }
            });
        } else if (control instanceof ComboBox<?> combo) {
            combo.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (!updating) {
                    String value = newVal != null ? newVal.toString() : "";
                    handleFacetValueChange(facetType, value, fixedCheckBox.isSelected());
                }
            });
        } else if (control instanceof Spinner<?> spinner) {
            spinner.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (!updating) {
                    String value = newVal != null ? newVal.toString() : "";
                    handleFacetValueChange(facetType, value, fixedCheckBox.isSelected());
                }
            });
            // Also handle manual text input
            spinner.getEditor().setOnAction(e -> {
                if (!updating) {
                    spinner.commitValue();
                }
            });
        }

        // Fixed checkbox listener
        fixedCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (!updating && control instanceof TextField textField) {
                handleFacetValueChange(facetType, textField.getText(), newVal);
            } else if (!updating && control instanceof ComboBox<?> combo) {
                String value = combo.getValue() != null ? combo.getValue().toString() : "";
                handleFacetValueChange(facetType, value, newVal);
            } else if (!updating && control instanceof Spinner<?> spinner) {
                String value = spinner.getValue() != null ? spinner.getValue().toString() : "";
                handleFacetValueChange(facetType, value, newVal);
            }
        });
    }

    /**
     * Parses an integer value or returns a default.
     *
     * @param value the string value
     * @param defaultValue the default value
     * @return the parsed integer or default
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Handles changes to facet values.
     * Creates, updates, or deletes facets in the restriction.
     *
     * @param facetType the facet type
     * @param newValue  the new value
     * @param newFixed  the new fixed flag
     */
    private void handleFacetValueChange(XsdFacetType facetType, String newValue, boolean newFixed) {
        if (currentRestriction == null || isInheritedView) {
            return;
        }

        XsdFacet existingFacet = currentRestriction.getFacetByType(facetType);

        if (newValue == null || newValue.trim().isEmpty()) {
            // Value is empty - delete facet if it exists
            if (existingFacet != null) {
                DeleteFacetCommand command = new DeleteFacetCommand(currentRestriction, existingFacet);
                editorContext.getCommandManager().executeCommand(command);
                logger.info("Deleted {} facet", facetType.getXmlName());
            }
        } else {
            // Value is not empty
            if (existingFacet != null) {
                // Update existing facet
                String oldValue = existingFacet.getValue();
                boolean oldFixed = existingFacet.isFixed();

                if (!newValue.equals(oldValue) || newFixed != oldFixed) {
                    EditFacetCommand command = new EditFacetCommand(existingFacet, newValue, newFixed);
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Updated {} facet to '{}'", facetType.getXmlName(), newValue);
                }
            } else {
                // Create new facet
                AddFacetCommand command = new AddFacetCommand(currentRestriction, facetType, newValue, newFixed);
                editorContext.getCommandManager().executeCommand(command);
                logger.info("Added {} facet with value '{}'", facetType.getXmlName(), newValue);
            }
        }
    }

    /**
     * Clears all facet controls.
     */
    private void clearFacets() {
        facetsGridPane.getChildren().clear();
        facetControls.clear();
        facetFixedCheckBoxes.clear();
        noFacetsLabel.setText("No restriction defined.");
        facetsGridPane.add(noFacetsLabel, 0, 0, 3, 1);
    }

    /**
     * Finds a SimpleType in the schema by name.
     *
     * @param typeName the type name
     * @return the SimpleType, or null if not found
     */
    private XsdSimpleType findSimpleType(String typeName) {
        if (editorContext.getSchema() == null) {
            return null;
        }

        for (XsdNode child : editorContext.getSchema().getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                if (typeName.equals(simpleType.getName())) {
                    return simpleType;
                }
            }
        }
        return null;
    }

    /**
     * Finds the restriction within a SimpleType.
     *
     * @param simpleType the simple type
     * @return the restriction, or null if not found
     */
    private XsdRestriction findRestrictionInSimpleType(XsdSimpleType simpleType) {
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction restriction) {
                return restriction;
            }
        }
        return null;
    }
}
