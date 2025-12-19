package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Optional;

/**
 * Context menu for XML Grid View with all editing operations.
 */
public class XmlGridContextMenu {

    private static final Logger logger = LogManager.getLogger(XmlGridContextMenu.class);

    private final XmlEditorContext context;
    private final ContextMenu contextMenu;
    private final Runnable refreshCallback;

    // Clipboard
    private XmlElement clipboardElement;
    private boolean isCut;

    // Table cell context (for grid operations)
    private RepeatingElementsTable clickedTable;
    private int clickedRowIndex = -1;
    private String clickedColumnName;

    // Menu Items
    private MenuItem addElementItem;
    private MenuItem addAttributeItem;
    private MenuItem addTextItem;
    private MenuItem addSiblingBeforeItem;
    private MenuItem addSiblingAfterItem;
    private MenuItem renameItem;
    private MenuItem duplicateItem;
    private MenuItem copyItem;
    private MenuItem cutItem;
    private MenuItem pasteItem;
    private MenuItem pasteAsChildItem;
    private MenuItem copyCellContentItem;
    private MenuItem copyXPathItem;
    private MenuItem moveUpItem;
    private MenuItem moveDownItem;
    private MenuItem deleteItem;
    private MenuItem expandAllItem;
    private MenuItem collapseAllItem;

    // Sort menu items
    private Menu sortMenu;
    private MenuItem sortAscendingItem;
    private MenuItem sortDescendingItem;

    public XmlGridContextMenu(XmlEditorContext context, Runnable refreshCallback) {
        this.context = context;
        this.refreshCallback = refreshCallback;
        this.contextMenu = buildContextMenu();
    }

    private ContextMenu buildContextMenu() {
        ContextMenu menu = new ContextMenu();

        // === Add Submenu ===
        Menu addMenu = new Menu("Add");
        addMenu.setGraphic(createColoredIcon(BootstrapIcons.PLUS_CIRCLE, "#28a745")); // Green

        addElementItem = new MenuItem("Child Element");
        addElementItem.setGraphic(createColoredIcon(BootstrapIcons.CODE_SLASH, "#28a745")); // Green
        addElementItem.setOnAction(e -> addChildElement());

        addAttributeItem = new MenuItem("Attribute");
        addAttributeItem.setGraphic(createColoredIcon(BootstrapIcons.AT, "#ffc107")); // Yellow
        addAttributeItem.setOnAction(e -> addAttribute());

        addTextItem = new MenuItem("Text Content");
        addTextItem.setGraphic(createColoredIcon(BootstrapIcons.FONTS, "#17a2b8")); // Teal
        addTextItem.setOnAction(e -> addTextContent());

        addSiblingBeforeItem = new MenuItem("Sibling Before");
        addSiblingBeforeItem.setGraphic(createColoredIcon(BootstrapIcons.ARROW_UP, "#28a745")); // Green
        addSiblingBeforeItem.setOnAction(e -> addSiblingElement(true));

        addSiblingAfterItem = new MenuItem("Sibling After");
        addSiblingAfterItem.setGraphic(createColoredIcon(BootstrapIcons.ARROW_DOWN, "#28a745")); // Green
        addSiblingAfterItem.setOnAction(e -> addSiblingElement(false));

        addMenu.getItems().addAll(
                addElementItem, addAttributeItem, addTextItem,
                new SeparatorMenuItem(),
                addSiblingBeforeItem, addSiblingAfterItem
        );

        // === Edit Items ===
        renameItem = new MenuItem("Rename");
        renameItem.setGraphic(createColoredIcon(BootstrapIcons.PENCIL, "#fd7e14")); // Orange
        renameItem.setOnAction(e -> renameElement());
        renameItem.setAccelerator(new KeyCodeCombination(KeyCode.F2));

        duplicateItem = new MenuItem("Duplicate");
        duplicateItem.setGraphic(createColoredIcon(BootstrapIcons.FILES, "#20c997")); // Teal green
        duplicateItem.setOnAction(e -> duplicateElement());
        duplicateItem.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN));

        // === Clipboard ===
        copyItem = new MenuItem("Copy");
        copyItem.setGraphic(createColoredIcon(BootstrapIcons.CLIPBOARD, "#6c757d")); // Gray
        copyItem.setOnAction(e -> copyElement());
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));

        cutItem = new MenuItem("Cut");
        cutItem.setGraphic(createColoredIcon(BootstrapIcons.SCISSORS, "#fd7e14")); // Orange
        cutItem.setOnAction(e -> cutElement());
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));

        pasteItem = new MenuItem("Paste as Sibling");
        pasteItem.setGraphic(createColoredIcon(BootstrapIcons.CLIPBOARD_CHECK, "#6c757d")); // Gray
        pasteItem.setOnAction(e -> pasteAsSibling());
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));

        pasteAsChildItem = new MenuItem("Paste as Child");
        pasteAsChildItem.setGraphic(createColoredIcon(BootstrapIcons.CLIPBOARD_PLUS, "#6c757d")); // Gray
        pasteAsChildItem.setOnAction(e -> pasteAsChild());
        pasteAsChildItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        copyCellContentItem = new MenuItem("Copy Cell Content");
        copyCellContentItem.setGraphic(createColoredIcon(BootstrapIcons.CLIPBOARD_DATA, "#17a2b8")); // Teal
        copyCellContentItem.setOnAction(e -> copyCellContent());
        copyCellContentItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        copyXPathItem = new MenuItem("Copy XPath");
        copyXPathItem.setGraphic(createColoredIcon(BootstrapIcons.DIAGRAM_3, "#6f42c1")); // Purple
        copyXPathItem.setOnAction(e -> copyXPath());
        copyXPathItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        // === Move ===
        moveUpItem = new MenuItem("Move Up");
        moveUpItem.setGraphic(createColoredIcon(BootstrapIcons.ARROW_UP_CIRCLE, "#6c757d")); // Gray
        moveUpItem.setOnAction(e -> moveElement(-1));
        moveUpItem.setAccelerator(new KeyCodeCombination(KeyCode.UP, KeyCombination.ALT_DOWN));

        moveDownItem = new MenuItem("Move Down");
        moveDownItem.setGraphic(createColoredIcon(BootstrapIcons.ARROW_DOWN_CIRCLE, "#6c757d")); // Gray
        moveDownItem.setOnAction(e -> moveElement(1));
        moveDownItem.setAccelerator(new KeyCodeCombination(KeyCode.DOWN, KeyCombination.ALT_DOWN));

        // === Expand/Collapse ===
        expandAllItem = new MenuItem("Expand All");
        expandAllItem.setGraphic(createColoredIcon(BootstrapIcons.ARROWS_EXPAND, "#007bff")); // Blue
        expandAllItem.setOnAction(e -> expandAll());

        collapseAllItem = new MenuItem("Collapse All");
        collapseAllItem.setGraphic(createColoredIcon(BootstrapIcons.ARROWS_COLLAPSE, "#007bff")); // Blue
        collapseAllItem.setOnAction(e -> collapseAll());

        // === Sort (for table columns) ===
        sortMenu = new Menu("Sort Column");
        sortMenu.setGraphic(createColoredIcon(BootstrapIcons.SORT_DOWN, "#007bff")); // Blue

        sortAscendingItem = new MenuItem("Sort Ascending");
        sortAscendingItem.setGraphic(createColoredIcon(BootstrapIcons.SORT_UP, "#28a745")); // Green
        sortAscendingItem.setOnAction(e -> sortColumn(true));

        sortDescendingItem = new MenuItem("Sort Descending");
        sortDescendingItem.setGraphic(createColoredIcon(BootstrapIcons.SORT_DOWN_ALT, "#dc3545")); // Red
        sortDescendingItem.setOnAction(e -> sortColumn(false));

        sortMenu.getItems().addAll(sortAscendingItem, sortDescendingItem);

        // === Delete ===
        deleteItem = new MenuItem("Delete");
        deleteItem.setGraphic(createColoredIcon(BootstrapIcons.TRASH, "#dc3545")); // Red
        deleteItem.setOnAction(e -> deleteElement());
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));

        // Build menu
        menu.getItems().addAll(
                addMenu,
                new SeparatorMenuItem(),
                renameItem, duplicateItem,
                new SeparatorMenuItem(),
                copyItem, cutItem, pasteItem, pasteAsChildItem,
                new SeparatorMenuItem(),
                copyCellContentItem, copyXPathItem,
                new SeparatorMenuItem(),
                moveUpItem, moveDownItem,
                new SeparatorMenuItem(),
                expandAllItem, collapseAllItem,
                new SeparatorMenuItem(),
                sortMenu,
                new SeparatorMenuItem(),
                deleteItem
        );

        // Apply uniform font styling to match XSD Editor and other menus
        menu.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");

        return menu;
    }

    /**
     * Creates a colored FontIcon for menu items.
     * Matches the style from XsdContextMenuFactory for consistent look & feel.
     *
     * @param icon the Bootstrap icon
     * @param color the hex color code (e.g., "#28a745")
     * @return the configured FontIcon
     */
    private FontIcon createColoredIcon(BootstrapIcons icon, String color) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconColor(Color.web(color));
        fontIcon.setIconSize(12);
        return fontIcon;
    }

    public void show(Node anchor, double screenX, double screenY, XmlNode selectedNode) {
        // Clear table cell context for non-table nodes
        clickedTable = null;
        clickedRowIndex = -1;
        clickedColumnName = null;

        updateMenuState(selectedNode);
        contextMenu.show(anchor, screenX, screenY);
    }

    public void show(Node anchor, double screenX, double screenY, XmlNode selectedNode,
                     RepeatingElementsTable table, int rowIndex, String columnName) {
        // Store table cell context
        clickedTable = table;
        clickedRowIndex = rowIndex;
        clickedColumnName = columnName;

        updateMenuState(selectedNode);
        contextMenu.show(anchor, screenX, screenY);
    }

    public void hide() {
        contextMenu.hide();
    }

    private void updateMenuState(XmlNode node) {
        boolean hasSelection = node != null;
        boolean isElement = node instanceof XmlElement;
        boolean isRoot = node != null && node.getParent() instanceof XmlDocument;
        boolean canMove = isElement && !isRoot && node.getParent() != null;
        boolean hasClipboard = clipboardElement != null;
        boolean hasTextContent = false;
        boolean hasElementChildren = false;

        // Check if we're in a table cell context
        boolean isInTableCell = clickedTable != null && clickedRowIndex >= 0 && clickedColumnName != null;

        if (isElement) {
            XmlElement element = (XmlElement) node;
            hasTextContent = element.hasNonWhitespaceTextContent();
            hasElementChildren = element.hasElementChildren();
        }

        // Mutual exclusivity: cannot add child if has text, cannot add text if has children
        addElementItem.setDisable(!isElement || hasTextContent);
        addAttributeItem.setDisable(!isElement);
        addTextItem.setDisable(!isElement || hasElementChildren);
        addSiblingBeforeItem.setDisable(!isElement || isRoot);
        addSiblingAfterItem.setDisable(!isElement || isRoot);
        renameItem.setDisable(!isElement);
        duplicateItem.setDisable(!isElement || isRoot);
        copyItem.setDisable(!isElement);
        cutItem.setDisable(!isElement || isRoot);
        pasteItem.setDisable(!hasClipboard || !isElement || isRoot);
        pasteAsChildItem.setDisable(!hasClipboard || !isElement || hasTextContent);

        // Copy Cell Content: Enable in table cell context OR if element has text content
        copyCellContentItem.setDisable(!isInTableCell && !hasTextContent);

        copyXPathItem.setDisable(!isElement);
        moveUpItem.setDisable(!canMove || isFirstChild(node));
        moveDownItem.setDisable(!canMove || isLastChild(node));
        deleteItem.setDisable(!hasSelection || isRoot);

        // Sort menu: Visible when clicking on a sortable table column (either header or data cell)
        // Note: clickedRowIndex can be -1 for column header clicks
        boolean isInTableColumn = clickedTable != null && clickedColumnName != null;
        boolean canSort = isInTableColumn && clickedTable.isColumnSortable(clickedColumnName);
        sortMenu.setVisible(canSort);
    }

    private boolean isFirstChild(XmlNode node) {
        if (node == null || node.getParent() == null) return true;
        XmlNode parent = node.getParent();
        if (parent instanceof XmlElement) {
            List<XmlNode> children = ((XmlElement) parent).getChildren();
            return children.indexOf(node) == 0;
        } else if (parent instanceof XmlDocument) {
            List<XmlNode> children = ((XmlDocument) parent).getChildren();
            return children.indexOf(node) == 0;
        }
        return true;
    }

    private boolean isLastChild(XmlNode node) {
        if (node == null || node.getParent() == null) return true;
        XmlNode parent = node.getParent();
        if (parent instanceof XmlElement) {
            List<XmlNode> children = ((XmlElement) parent).getChildren();
            return children.indexOf(node) == children.size() - 1;
        } else if (parent instanceof XmlDocument) {
            List<XmlNode> children = ((XmlDocument) parent).getChildren();
            return children.indexOf(node) == children.size() - 1;
        }
        return true;
    }

    // ==================== Actions ====================

    private void addChildElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement parent)) return;

        // Prevent mixed content: check for existing non-whitespace text content
        if (parent.hasNonWhitespaceTextContent()) {
            showWarningAlert("Cannot Add Child Element",
                    "This element has text content. Remove the text content first before adding child elements.");
            return;
        }

        String name;

        // Check if schema is available for schema-based element selection
        logger.debug("Adding child element - hasSchema: {}", context.hasSchema());
        if (context.hasSchema()) {
            String parentXPath = buildXPath(parent);
            logger.debug("Parent XPath: {}", parentXPath);
            List<String> validElements = context.getValidChildElements(parentXPath);
            logger.debug("Valid child elements: {}", validElements);

            if (validElements != null && !validElements.isEmpty()) {
                // Show schema-based selection dialog
                name = showSchemaBasedElementDialog(validElements, parentXPath);
            } else {
                // Schema available but no specific constraints - use free-text
                logger.debug("Schema available but no constraints for parent: {}", parentXPath);
                name = showInputDialog("Add Child Element", "Element name:", "newElement");
            }
        } else {
            // No schema - use free-text input
            logger.debug("No schema available, using free-text input");
            name = showInputDialog("Add Child Element", "Element name:", "newElement");
        }

        if (name == null || name.trim().isEmpty()) return;

        XmlElement child = new XmlElement(name.trim());

        XmlCommand cmd = new AddElementCommand(parent, child);
        context.executeCommand(cmd);
        refresh();
    }

    /**
     * Shows a dialog for schema-based element selection.
     * Displays valid child elements with optional documentation and cardinality.
     */
    private String showSchemaBasedElementDialog(List<String> validElements, String parentXPath) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Add Child Element");
        dialog.setHeaderText("Select an element to add:");

        // Set dialog buttons
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create the content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // ComboBox with valid elements
        ComboBox<String> elementComboBox = new ComboBox<>(FXCollections.observableArrayList(validElements));
        elementComboBox.setPromptText("Select element...");
        elementComboBox.setPrefWidth(250);

        // Pre-select first element
        if (!validElements.isEmpty()) {
            elementComboBox.getSelectionModel().selectFirst();
        }

        // Info label for documentation/cardinality
        Label infoLabel = new Label();
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(300);
        infoLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        // Update info when selection changes
        elementComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && context.hasSchema()) {
                String childXPath = parentXPath + "/" + newVal;
                Optional<XmlSchemaProvider.ElementTypeInfo> typeInfo =
                        context.getSchemaProvider().getElementTypeInfo(childXPath);

                if (typeInfo.isPresent()) {
                    XmlSchemaProvider.ElementTypeInfo info = typeInfo.get();
                    StringBuilder sb = new StringBuilder();

                    // Add type info
                    if (info.typeName() != null && !info.typeName().isEmpty()) {
                        sb.append("Type: ").append(info.typeName());
                    }

                    // Add cardinality
                    String cardinality = formatCardinality(info.minOccurs(), info.maxOccurs());
                    if (cardinality != null && !cardinality.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append("Cardinality: ").append(cardinality);
                    }

                    // Add documentation
                    if (info.documentation() != null && !info.documentation().isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(info.documentation());
                    }

                    infoLabel.setText(sb.toString());
                } else {
                    infoLabel.setText("");
                }
            }
        });

        // Trigger initial info update
        if (!validElements.isEmpty()) {
            elementComboBox.getSelectionModel().selectFirst();
        }

        // Option for custom element name (if schema allows any)
        CheckBox customCheckBox = new CheckBox("Enter custom name");
        TextField customField = new TextField();
        customField.setPromptText("Custom element name...");
        customField.setDisable(true);
        customField.setPrefWidth(250);

        customCheckBox.selectedProperty().addListener((obs, oldVal, isCustom) -> {
            elementComboBox.setDisable(isCustom);
            customField.setDisable(!isCustom);
            if (isCustom) {
                customField.requestFocus();
            }
        });

        grid.add(new Label("Element:"), 0, 0);
        grid.add(elementComboBox, 1, 0);
        grid.add(infoLabel, 1, 1);
        grid.add(customCheckBox, 0, 2, 2, 1);
        grid.add(customField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // Enable/Disable add button based on selection
        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(validElements.isEmpty());

        elementComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            addButton.setDisable(newVal == null && !customCheckBox.isSelected());
        });

        customField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (customCheckBox.isSelected()) {
                addButton.setDisable(newVal == null || newVal.trim().isEmpty());
            }
        });

        customCheckBox.selectedProperty().addListener((obs, oldVal, isCustom) -> {
            if (isCustom) {
                addButton.setDisable(customField.getText() == null || customField.getText().trim().isEmpty());
            } else {
                addButton.setDisable(elementComboBox.getSelectionModel().getSelectedItem() == null);
            }
        });

        // Request focus on the combo box
        dialog.setOnShown(e -> elementComboBox.requestFocus());

        // Convert the result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                if (customCheckBox.isSelected()) {
                    return customField.getText();
                }
                return elementComboBox.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    /**
     * Formats cardinality information for display.
     *
     * @param minOccursStr minOccurs value as string (e.g., "0", "1")
     * @param maxOccursStr maxOccurs value as string (e.g., "1", "unbounded")
     * @return formatted cardinality string
     */
    private String formatCardinality(String minOccursStr, String maxOccursStr) {
        if (minOccursStr == null && maxOccursStr == null) {
            return "";
        }

        int minOccurs = 1; // Default
        int maxOccurs = 1; // Default

        try {
            if (minOccursStr != null && !minOccursStr.isEmpty()) {
                minOccurs = Integer.parseInt(minOccursStr);
            }
        } catch (NumberFormatException e) {
            minOccurs = 0;
        }

        try {
            if (maxOccursStr != null && !maxOccursStr.isEmpty()) {
                if ("unbounded".equalsIgnoreCase(maxOccursStr)) {
                    maxOccurs = -1;
                } else {
                    maxOccurs = Integer.parseInt(maxOccursStr);
                }
            }
        } catch (NumberFormatException e) {
            maxOccurs = 1;
        }

        if (minOccurs == 1 && maxOccurs == 1) {
            return "[1] (required)";
        } else if (minOccurs == 0 && maxOccurs == 1) {
            return "[0..1] (optional)";
        } else if (minOccurs == 0 && maxOccurs == -1) {
            return "[0..*] (optional, unbounded)";
        } else if (minOccurs == 1 && maxOccurs == -1) {
            return "[1..*] (required, unbounded)";
        } else if (maxOccurs == -1) {
            return "[" + minOccurs + "..*]";
        } else {
            return "[" + minOccurs + ".." + maxOccurs + "]";
        }
    }

    private void addAttribute() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement element)) return;

        String elementXPath = buildXPath(element);

        String name;
        String defaultValue = "";

        // Check if schema is available for schema-based attribute selection
        if (context.hasSchema()) {
            List<String> validAttributes = context.getValidAttributes(elementXPath);

            // Filter out already existing attributes
            List<String> availableAttributes = validAttributes.stream()
                    .filter(attr -> element.getAttribute(attr) == null)
                    .toList();

            if (!availableAttributes.isEmpty()) {
                // Show schema-based selection dialog
                AttributeDialogResult attrResult = showSchemaBasedAttributeDialog(availableAttributes, elementXPath);
                if (attrResult == null) return;
                name = attrResult.name();
                defaultValue = attrResult.defaultValue() != null ? attrResult.defaultValue() : "";
            } else if (!validAttributes.isEmpty()) {
                // All valid attributes already exist
                showWarningAlert("No Available Attributes",
                        "All schema-defined attributes for this element are already present.");
                return;
            } else {
                // Schema available but no specific constraints - use free-text
                logger.debug("Schema available but no attribute constraints for element: {}", elementXPath);
                name = showInputDialog("Add Attribute", "Attribute name:", "newAttribute");
            }
        } else {
            // No schema - use free-text input
            name = showInputDialog("Add Attribute", "Attribute name:", "newAttribute");
        }

        if (name == null || name.trim().isEmpty()) return;

        // Get the value (may already have a default from schema)
        String value = showInputDialog("Add Attribute", "Attribute value for '" + name + "':", defaultValue);
        if (value == null) return;

        XmlCommand cmd = new SetAttributeCommand(element, name.trim(), value);
        context.executeCommand(cmd);
        refresh();
    }

    /**
     * Result record for attribute dialog.
     */
    private record AttributeDialogResult(String name, String defaultValue) {
    }

    /**
     * Shows a dialog for schema-based attribute selection.
     * Displays valid attributes with type info, required/optional status, and documentation.
     */
    private AttributeDialogResult showSchemaBasedAttributeDialog(List<String> validAttributes, String elementXPath) {
        Dialog<AttributeDialogResult> dialog = new Dialog<>();
        dialog.setTitle("Add Attribute");
        dialog.setHeaderText("Select an attribute to add:");

        // Set dialog buttons
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Create the content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        // ComboBox with valid attributes
        ComboBox<String> attributeComboBox = new ComboBox<>(FXCollections.observableArrayList(validAttributes));
        attributeComboBox.setPromptText("Select attribute...");
        attributeComboBox.setPrefWidth(250);

        // Pre-select first attribute
        if (!validAttributes.isEmpty()) {
            attributeComboBox.getSelectionModel().selectFirst();
        }

        // Info label for documentation/type/required
        Label infoLabel = new Label();
        infoLabel.setWrapText(true);
        infoLabel.setMaxWidth(300);
        infoLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        // Store default values for later use
        final String[] currentDefaultValue = {""};

        // Update info when selection changes
        attributeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && context.hasSchema()) {
                Optional<XmlSchemaProvider.AttributeTypeInfo> typeInfo =
                        context.getSchemaProvider().getAttributeTypeInfo(elementXPath, newVal);

                if (typeInfo.isPresent()) {
                    XmlSchemaProvider.AttributeTypeInfo info = typeInfo.get();
                    StringBuilder sb = new StringBuilder();

                    // Add required/optional status
                    String status = info.isRequired() ? "Required" : "Optional";
                    sb.append("Status: ").append(status);

                    // Add type info
                    if (info.typeName() != null && !info.typeName().isEmpty()) {
                        sb.append("\nType: ").append(info.typeName());
                    }

                    // Add default value info
                    if (info.defaultValue() != null && !info.defaultValue().isEmpty()) {
                        sb.append("\nDefault: ").append(info.defaultValue());
                        currentDefaultValue[0] = info.defaultValue();
                    } else {
                        currentDefaultValue[0] = "";
                    }

                    // Add fixed value info
                    if (info.fixedValue() != null && !info.fixedValue().isEmpty()) {
                        sb.append("\nFixed: ").append(info.fixedValue());
                        currentDefaultValue[0] = info.fixedValue();
                    }

                    // Add enumeration values
                    if (info.enumerationValues() != null && !info.enumerationValues().isEmpty()) {
                        sb.append("\nAllowed values: ").append(String.join(", ", info.enumerationValues()));
                    }

                    // Add documentation
                    if (info.documentation() != null && !info.documentation().isEmpty()) {
                        sb.append("\n").append(info.documentation());
                    }

                    infoLabel.setText(sb.toString());
                } else {
                    infoLabel.setText("");
                    currentDefaultValue[0] = "";
                }
            }
        });

        // Trigger initial info update
        if (!validAttributes.isEmpty()) {
            attributeComboBox.getSelectionModel().selectFirst();
        }

        // Option for custom attribute name
        CheckBox customCheckBox = new CheckBox("Enter custom name");
        TextField customField = new TextField();
        customField.setPromptText("Custom attribute name...");
        customField.setDisable(true);
        customField.setPrefWidth(250);

        customCheckBox.selectedProperty().addListener((obs, oldVal, isCustom) -> {
            attributeComboBox.setDisable(isCustom);
            customField.setDisable(!isCustom);
            if (isCustom) {
                customField.requestFocus();
                currentDefaultValue[0] = ""; // Clear default for custom attributes
            }
        });

        grid.add(new Label("Attribute:"), 0, 0);
        grid.add(attributeComboBox, 1, 0);
        grid.add(infoLabel, 1, 1);
        grid.add(customCheckBox, 0, 2, 2, 1);
        grid.add(customField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // Enable/Disable add button based on selection
        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(validAttributes.isEmpty());

        attributeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            addButton.setDisable(newVal == null && !customCheckBox.isSelected());
        });

        customField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (customCheckBox.isSelected()) {
                addButton.setDisable(newVal == null || newVal.trim().isEmpty());
            }
        });

        customCheckBox.selectedProperty().addListener((obs, oldVal, isCustom) -> {
            if (isCustom) {
                addButton.setDisable(customField.getText() == null || customField.getText().trim().isEmpty());
            } else {
                addButton.setDisable(attributeComboBox.getSelectionModel().getSelectedItem() == null);
            }
        });

        // Request focus on the combo box
        dialog.setOnShown(e -> attributeComboBox.requestFocus());

        // Convert the result
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                String selectedName = customCheckBox.isSelected()
                        ? customField.getText()
                        : attributeComboBox.getSelectionModel().getSelectedItem();
                return new AttributeDialogResult(selectedName, currentDefaultValue[0]);
            }
            return null;
        });

        Optional<AttributeDialogResult> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void addTextContent() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement element)) return;

        // Prevent mixed content: check for existing child elements
        if (element.hasElementChildren()) {
            showWarningAlert("Cannot Add Text Content",
                    "This element has child elements. Remove all child elements first before adding text content.");
            return;
        }

        String text = showInputDialog("Add Text Content", "Text:", "");
        if (text == null) return;

        XmlCommand cmd = new SetElementTextCommand(element, text);
        context.executeCommand(cmd);
        refresh();
    }

    private void addSiblingElement(boolean before) {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlNode parent = selected.getParent();
        if (!(parent instanceof XmlElement parentElement)) return;

        String name = showInputDialog("Add Sibling Element", "Element name:", "newElement");
        if (name == null || name.trim().isEmpty()) return;

        XmlElement sibling = new XmlElement(name.trim());

        int index = parentElement.getChildren().indexOf(selected);
        if (!before) index++;

        XmlCommand cmd = new AddElementCommand(parentElement, sibling, index);
        context.executeCommand(cmd);
        refresh();
    }

    private void renameElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement element)) return;

        String newName = showInputDialog("Rename Element", "New name:", element.getName());
        if (newName == null || newName.trim().isEmpty()) return;

        XmlCommand cmd = new RenameNodeCommand(element, newName.trim());
        context.executeCommand(cmd);
        refresh();
    }

    private void duplicateElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement element)) return;

        XmlNode parent = selected.getParent();
        if (!(parent instanceof XmlElement parentElement)) return;

        XmlElement copy = (XmlElement) element.deepCopy("");

        int index = parentElement.getChildren().indexOf(selected) + 1;
        XmlCommand cmd = new AddElementCommand(parentElement, copy, index);
        context.executeCommand(cmd);
        refresh();
    }

    private void copyElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        clipboardElement = (XmlElement) selected.deepCopy("");
        isCut = false;
    }

    private void cutElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        clipboardElement = (XmlElement) selected;
        isCut = true;
    }

    private void pasteAsSibling() {
        if (clipboardElement == null) return;

        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlNode parent = selected.getParent();
        if (!(parent instanceof XmlElement parentElement)) return;

        XmlElement toPaste = isCut ? clipboardElement : (XmlElement) clipboardElement.deepCopy("");

        int index = parentElement.getChildren().indexOf(selected) + 1;

        if (isCut) {
            // First delete, then add
            XmlCommand deleteCmd = new DeleteNodeCommand(clipboardElement);
            context.executeCommand(deleteCmd);
            clipboardElement = null;
            isCut = false;
        }

        XmlCommand addCmd = new AddElementCommand(parentElement, toPaste, index);
        context.executeCommand(addCmd);
        refresh();
    }

    private void pasteAsChild() {
        if (clipboardElement == null) return;

        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement parentElement)) return;

        // Prevent mixed content: check for existing non-whitespace text content
        if (parentElement.hasNonWhitespaceTextContent()) {
            showWarningAlert("Cannot Paste as Child",
                    "This element has text content. Remove the text content first before pasting child elements.");
            return;
        }

        XmlElement toPaste = isCut ? clipboardElement : (XmlElement) clipboardElement.deepCopy("");

        if (isCut) {
            XmlCommand deleteCmd = new DeleteNodeCommand(clipboardElement);
            context.executeCommand(deleteCmd);
            clipboardElement = null;
            isCut = false;
        }

        XmlCommand addCmd = new AddElementCommand(parentElement, toPaste);
        context.executeCommand(addCmd);
        refresh();
    }

    private void copyCellContent() {
        // Check if we're in a table cell context
        if (clickedTable != null && clickedRowIndex >= 0 && clickedColumnName != null) {
            RepeatingElementsTable.TableRow row = clickedTable.getRows().get(clickedRowIndex);
            RepeatingElementsTable.TableColumn col = clickedTable.getColumn(clickedColumnName);

            String content = "";

            if (col != null) {
                if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
                    // For attributes, use the stored value directly
                    content = row.getValue(clickedColumnName);
                } else if (col.getType() == RepeatingElementsTable.ColumnType.CHILD_ELEMENT) {
                    // For child elements, extract actual text content from the element
                    XmlElement rowElement = row.getElement();
                    for (XmlNode child : rowElement.getChildren()) {
                        if (child instanceof XmlElement && ((XmlElement) child).getName().equals(clickedColumnName)) {
                            // Extract all text content from this child element
                            StringBuilder sb = new StringBuilder();
                            extractTextContent((XmlElement) child, sb);
                            content = sb.toString().trim();
                            break;
                        }
                    }
                } else if (col.getType() == RepeatingElementsTable.ColumnType.TEXT_CONTENT) {
                    // Direct text content
                    content = row.getValue(clickedColumnName);
                }
            }

            if (!content.isEmpty()) {
                copyToClipboard(content);
            }
            return;
        }

        // Otherwise, use regular node content
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (selected == null) return;

        String content = "";
        if (selected instanceof XmlElement element) {
            // Get text content of the element
            StringBuilder sb = new StringBuilder();
            for (XmlNode child : element.getChildren()) {
                if (child instanceof XmlText) {
                    sb.append(((XmlText) child).getText());
                }
            }
            content = sb.toString();
        } else if (selected instanceof XmlText) {
            content = ((XmlText) selected).getText();
        }

        if (!content.isEmpty()) {
            copyToClipboard(content);
        }
    }

    private void copyXPath() {
        // Check if we're in a table cell context
        if (clickedTable != null && clickedRowIndex >= 0 && clickedColumnName != null) {
            // Build XPath for the specific cell
            RepeatingElementsTable.TableRow row = clickedTable.getRows().get(clickedRowIndex);
            XmlElement rowElement = row.getElement();

            // Start with the row element's XPath
            String baseXPath = buildXPath(rowElement);

            // Check if the column represents an attribute or child element
            RepeatingElementsTable.TableColumn col = clickedTable.getColumn(clickedColumnName);
            if (col != null) {
                if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
                    // Attribute column
                    baseXPath += "/@" + clickedColumnName;
                } else if (col.getType() == RepeatingElementsTable.ColumnType.CHILD_ELEMENT) {
                    // Child element column - find the actual child element
                    for (XmlNode child : rowElement.getChildren()) {
                        if (child instanceof XmlElement && ((XmlElement) child).getName().equals(clickedColumnName)) {
                            baseXPath = buildXPath(child);
                            break;
                        }
                    }
                } else if (col.getType() == RepeatingElementsTable.ColumnType.TEXT_CONTENT) {
                    // Text content
                    baseXPath += "/text()";
                }
            }

            copyToClipboard(baseXPath);
            return;
        }

        // Otherwise, use regular node XPath
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (selected == null) return;

        String xpath = buildXPath(selected);
        copyToClipboard(xpath);
    }

    private String buildXPath(XmlNode node) {
        if (node == null) return "";
        if (node instanceof XmlDocument) return "";

        StringBuilder xpath = new StringBuilder();
        XmlNode current = node;

        while (current != null && !(current instanceof XmlDocument)) {
            if (current instanceof XmlElement element) {
                String name = element.getName();

                // Calculate position among siblings with same name
                int position = 1;
                if (current.getParent() != null) {
                    List<XmlNode> siblings;
                    if (current.getParent() instanceof XmlElement) {
                        siblings = ((XmlElement) current.getParent()).getChildren();
                    } else if (current.getParent() instanceof XmlDocument) {
                        siblings = ((XmlDocument) current.getParent()).getChildren();
                    } else {
                        siblings = List.of();
                    }

                    // Count siblings with same name before this element
                    int sameNameCount = 0;
                    for (XmlNode sibling : siblings) {
                        if (sibling instanceof XmlElement && ((XmlElement) sibling).getName().equals(name)) {
                            sameNameCount++;
                            if (sibling == current) {
                                position = sameNameCount;
                                break;
                            }
                        }
                    }

                    // Only add [position] if there are multiple elements with same name
                    long totalSameNameCount = siblings.stream()
                            .filter(s -> s instanceof XmlElement && ((XmlElement) s).getName().equals(name))
                            .count();

                    if (totalSameNameCount > 1) {
                        xpath.insert(0, "/" + name + "[" + position + "]");
                    } else {
                        xpath.insert(0, "/" + name);
                    }
                } else {
                    xpath.insert(0, "/" + name);
                }
            }
            current = current.getParent();
        }

        return xpath.length() > 0 ? xpath.toString() : "/";
    }

    /**
     * Recursively extracts all text content from an element and its descendants.
     */
    private void extractTextContent(XmlElement element, StringBuilder sb) {
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlText) {
                String text = ((XmlText) child).getText();
                if (text != null && !text.trim().isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(text.trim());
                }
            } else if (child instanceof XmlElement) {
                // Recursively extract from child elements
                extractTextContent((XmlElement) child, sb);
            }
        }
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private void moveElement(int direction) {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlNode parent = selected.getParent();
        if (!(parent instanceof XmlElement parentElement)) return;

        List<XmlNode> children = parentElement.getChildren();
        int currentIndex = children.indexOf(selected);
        int newIndex = currentIndex + direction;

        if (newIndex < 0 || newIndex >= children.size()) return;

        XmlCommand cmd = new MoveNodeCommand(selected, parentElement, newIndex);
        context.executeCommand(cmd);
        refresh();
    }

    private void deleteElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (selected == null) return;

        // Confirm deletion
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Element");
        alert.setHeaderText("Delete \"" + getNodeName(selected) + "\"?");
        alert.setContentText("This action cannot be undone from this dialog.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            XmlCommand cmd = new DeleteNodeCommand(selected);
            context.executeCommand(cmd);
            context.getSelectionModel().clearSelection();
            refresh();
        }
    }

    private void expandAll() {
        // This will be handled by the view
        if (refreshCallback != null) {
            // Signal to expand all nodes
        }
    }

    private void collapseAll() {
        // This will be handled by the view
        if (refreshCallback != null) {
            // Signal to collapse all nodes
        }
    }

    private String getNodeName(XmlNode node) {
        if (node instanceof XmlElement) {
            return ((XmlElement) node).getName();
        } else if (node instanceof XmlText) {
            String text = ((XmlText) node).getText();
            return text.length() > 20 ? text.substring(0, 20) + "..." : text;
        }
        return "Node";
    }

    private String showInputDialog(String title, String header, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(null);

        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    /**
     * Sorts the table column by the clicked column.
     *
     * @param ascending true for ascending order, false for descending
     */
    private void sortColumn(boolean ascending) {
        if (clickedTable == null || clickedColumnName == null) {
            logger.warn("Sort column called without table context");
            return;
        }

        if (!clickedTable.isColumnSortable(clickedColumnName)) {
            showWarningAlert("Cannot Sort Column",
                    "This column contains complex data and cannot be sorted.");
            return;
        }

        logger.info("Sorting column '{}' {}", clickedColumnName, ascending ? "ascending" : "descending");

        SortElementsCommand cmd = new SortElementsCommand(clickedTable, clickedColumnName, ascending);
        context.executeCommand(cmd);
        refresh();
    }

    private void refresh() {
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }

    // ==================== Public Keyboard Actions ====================

    public void handleKeyPress(javafx.scene.input.KeyEvent event, XmlNode selectedNode) {
        if (selectedNode == null) return;

        if (event.getCode() == KeyCode.DELETE) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            deleteElement();
            event.consume();
        } else if (event.getCode() == KeyCode.F2) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            renameElement();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.C) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            copyElement();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.X) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            cutElement();
            event.consume();
        } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.V) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            pasteAsChild();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.V) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            pasteAsSibling();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.D) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            duplicateElement();
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.UP) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            moveElement(-1);
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.DOWN) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            moveElement(1);
            event.consume();
        }
    }

    public boolean hasClipboard() {
        return clipboardElement != null;
    }

    /**
     * Shows a warning alert dialog to inform the user about invalid operations.
     *
     * @param title   the alert title
     * @param message the warning message
     */
    private void showWarningAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
