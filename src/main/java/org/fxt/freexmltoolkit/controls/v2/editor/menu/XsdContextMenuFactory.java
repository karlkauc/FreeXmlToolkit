package org.fxt.freexmltoolkit.controls.v2.editor.menu;

import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Factory for creating context-sensitive context menus for XSD editor nodes.
 * <p>
 * Creates appropriate menu items based on node type and context:
 * - Element nodes: Add child elements, attributes, change type, delete, duplicate
 * - ComplexType nodes: Add elements, attributes, compositors, convert to SimpleType, edit in Type Editor
 * - SimpleType nodes: Edit restrictions, facets, convert to ComplexType, edit in Type Editor
 * - Attribute nodes: Change type, make required/optional, delete
 * - Compositor nodes: Change type (sequence/choice/all), add elements
 *
 * @since 2.0
 */
public class XsdContextMenuFactory {

    private static final Logger logger = LogManager.getLogger(XsdContextMenuFactory.class);

    private final XsdEditorContext editorContext;
    private Consumer<XsdComplexType> openComplexTypeEditorCallback;
    private Consumer<XsdSimpleType> openSimpleTypeEditorCallback;

    /**
     * Creates a new context menu factory.
     *
     * @param editorContext the editor context
     */
    public XsdContextMenuFactory(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
    }

    /**
     * Sets the callback for opening ComplexType in Type Editor.
     *
     * @param callback the callback to invoke when "Edit Type" is selected
     */
    public void setOpenComplexTypeEditorCallback(Consumer<XsdComplexType> callback) {
        this.openComplexTypeEditorCallback = callback;
    }

    /**
     * Sets the callback for opening SimpleType in Type Editor.
     *
     * @param callback the callback to invoke when "Edit Type" is selected
     */
    public void setOpenSimpleTypeEditorCallback(Consumer<XsdSimpleType> callback) {
        this.openSimpleTypeEditorCallback = callback;
    }

    /**
     * Creates a context menu for the given node.
     *
     * @param node the node to create the menu for
     * @return the context menu
     */
    public ContextMenu createContextMenu(VisualNode node) {
        if (node == null) {
            return createEmptyCanvasMenu();
        }

        ContextMenu menu = switch (node.getType()) {
            case ELEMENT -> createElementMenu(node);
            case ATTRIBUTE -> createAttributeMenu(node);
            case COMPLEX_TYPE -> createComplexTypeMenu(node);
            case SIMPLE_TYPE -> createSimpleTypeMenu(node);
            case SEQUENCE, CHOICE, ALL -> createCompositorMenu(node);
            case SCHEMA -> createSchemaMenu(node);
            case GROUP -> createGroupMenu(node);
            case ENUMERATION -> createEnumerationMenu(node);
            default -> createDefaultMenu(node);
        };

        // Apply uniform font styling to match XML Editor
        menu.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");

        return menu;
    }

    /**
     * Creates a menu for empty canvas (no node selected).
     */
    private ContextMenu createEmptyCanvasMenu() {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Add Root Element", () -> {
                    org.fxt.freexmltoolkit.controls.v2.model.XsdSchema schema = editorContext.getSchema();
                    if (schema != null) {
                        handleAddRootElement(schema);
                    }
                }),
                createMenuItem("Add Global Type", () -> logger.info("Add global type"))
        );
        return menu;
    }

    /**
     * Creates a context menu for element nodes.
     */
    private ContextMenu createElementMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        // Add submenu with icon
        Menu addMenu = new Menu("Add");
        addMenu.setGraphic(createColoredIcon("bi-plus-circle", "#28a745"));
        addMenu.getItems().addAll(
                createMenuItemConditional("Element", "bi-plus", "#28a745",
                        () -> handleAddElement(node), () -> canAddElement(node)),
                createMenuItemConditional("Attribute", "bi-at", "#ffc107",
                        () -> handleAddAttribute(node), () -> canAddAttribute(node)),
                new SeparatorMenuItem(),
                createMenuItemConditional("Sequence", "bi-list-ol", "#6c757d",
                        () -> handleAddSequence(node), () -> canAddCompositor(node)),
                createMenuItemConditional("Choice", "bi-card-list", "#6c757d",
                        () -> handleAddChoice(node), () -> canAddCompositor(node)),
                createMenuItemConditional("All", "bi-grid-3x3", "#6c757d",
                        () -> handleAddAll(node), () -> canAddCompositor(node))
        );

        // Check if element has a ComplexType or SimpleType reference - if so, add "Edit Type in Editor" option
        boolean hasComplexTypeReference = hasComplexTypeReference(node);
        boolean hasSimpleTypeReference = hasSimpleTypeReference(node);

        // Create Move submenu
        Menu moveMenu = createMoveMenu(node);

        // Create clipboard menu items
        MenuItem copyItem = createMenuItem("Copy Node", "bi-clipboard", "#6c757d", () -> handleCopy(node));
        MenuItem cutItem = createMenuItem("Cut Node", "bi-scissors", "#fd7e14", () -> handleCut(node));
        MenuItem pasteItem = createPasteMenuItem(node);

        if (hasComplexTypeReference) {
            menu.getItems().addAll(
                    createMenuItemAlwaysEnabled("Edit Referenced Type in Editor", "bi-box-arrow-up-right", "#17a2b8",
                            () -> handleEditReferencedComplexType(node)),
                    new SeparatorMenuItem(),
                    addMenu,
                    new SeparatorMenuItem(),
                    createMenuItem("Change Type", "bi-arrow-left-right", "#007bff", () -> handleChangeType(node)),
                    createMenuItem("Rename", "bi-pencil", "#fd7e14", () -> handleRename(node)),
                    createMenuItem("Edit Cardinality", "bi-hash", "#6f42c1", () -> handleChangeCardinality(node)),
                    new SeparatorMenuItem(),
                    moveMenu,
                    copyItem,
                    cutItem,
                    pasteItem,
                    createMenuItem("Duplicate", "bi-files", "#20c997", () -> handleDuplicate(node)),
                    createMenuItem("Delete", "bi-trash", "#dc3545", () -> handleDelete(node)),
                    new SeparatorMenuItem(),
                    createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
            );
        } else if (hasSimpleTypeReference) {
            // Element references a SimpleType - show Edit Type option without Add submenu
            menu.getItems().addAll(
                    createMenuItemAlwaysEnabled("Edit Referenced Type in Editor", "bi-box-arrow-up-right", "#17a2b8",
                            () -> handleEditReferencedSimpleType(node)),
                    new SeparatorMenuItem(),
                    createMenuItem("Change Type", "bi-arrow-left-right", "#007bff", () -> handleChangeType(node)),
                    createMenuItem("Rename", "bi-pencil", "#fd7e14", () -> handleRename(node)),
                    createMenuItem("Edit Cardinality", "bi-hash", "#6f42c1", () -> handleChangeCardinality(node)),
                    new SeparatorMenuItem(),
                    moveMenu,
                    copyItem,
                    cutItem,
                    pasteItem,
                    createMenuItem("Duplicate", "bi-files", "#20c997", () -> handleDuplicate(node)),
                    createMenuItem("Delete", "bi-trash", "#dc3545", () -> handleDelete(node)),
                    new SeparatorMenuItem(),
                    createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
            );
        } else {
            menu.getItems().addAll(
                    addMenu,
                    new SeparatorMenuItem(),
                    createMenuItem("Change Type", "bi-arrow-left-right", "#007bff", () -> handleChangeType(node)),
                    createMenuItem("Rename", "bi-pencil", "#fd7e14", () -> handleRename(node)),
                    createMenuItem("Edit Cardinality", "bi-hash", "#6f42c1", () -> handleChangeCardinality(node)),
                    new SeparatorMenuItem(),
                    moveMenu,
                    copyItem,
                    cutItem,
                    pasteItem,
                    createMenuItem("Duplicate", "bi-files", "#20c997", () -> handleDuplicate(node)),
                    createMenuItem("Delete", "bi-trash", "#dc3545", () -> handleDelete(node)),
                    new SeparatorMenuItem(),
                    createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
            );
        }

        return menu;
    }

    /**
     * Creates a context menu for attribute nodes.
     */
    private ContextMenu createAttributeMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        // Check if attribute references a custom SimpleType
        boolean hasSimpleTypeReference = hasAttributeSimpleTypeReference(node);

        if (hasSimpleTypeReference) {
            menu.getItems().add(
                    createMenuItemAlwaysEnabled("Edit Referenced Type in Editor", "bi-box-arrow-up-right", "#17a2b8",
                            () -> handleEditReferencedAttributeSimpleType(node))
            );
            menu.getItems().add(new SeparatorMenuItem());
        }

        menu.getItems().addAll(
                createMenuItem("Change Type", "bi-arrow-left-right", "#007bff",
                        () -> logger.info("Change type of attribute {}", node.getLabel())),
                createMenuItem("Rename", "bi-pencil", "#fd7e14", () -> handleRename(node)),
                createMenuItem("Toggle Required/Optional", "bi-toggle-on", "#6c757d",
                        () -> logger.info("Toggle required/optional for {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Delete", "bi-trash", "#dc3545", () -> handleDelete(node)),
                new SeparatorMenuItem(),
                createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for complex type nodes.
     */
    private ContextMenu createComplexTypeMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        // Add submenu
        Menu addMenu = new Menu("Add");
        addMenu.getItems().addAll(
                createMenuItem("Element", () -> logger.info("Add element to complex type {}", node.getLabel())),
                createMenuItem("Attribute", () -> logger.info("Add attribute to complex type {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Sequence", () -> logger.info("Add sequence to complex type {}", node.getLabel())),
                createMenuItem("Choice", () -> logger.info("Add choice to complex type {}", node.getLabel())),
                createMenuItem("All", () -> logger.info("Add all to complex type {}", node.getLabel()))
        );

        menu.getItems().addAll(
                createMenuItemAlwaysEnabled("Edit Type in Editor", () -> handleEditComplexType(node)),
                new SeparatorMenuItem(),
                addMenu,
                new SeparatorMenuItem(),
                createMenuItem("Rename", () -> handleRename(node)),
                createMenuItem("Convert to Simple Type", () -> logger.info("Convert to simple type {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Delete", () -> handleDelete(node)),
                new SeparatorMenuItem(),
                createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for simple type nodes.
     */
    private ContextMenu createSimpleTypeMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItemAlwaysEnabled("Edit Type in Editor", () -> handleEditSimpleType(node)),
                new SeparatorMenuItem(),
                createMenuItem("Edit Restrictions", () -> logger.info("Edit restrictions for {}", node.getLabel())),
                createMenuItem("Edit Facets", () -> logger.info("Edit facets for {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Rename", () -> handleRename(node)),
                createMenuItem("Convert to Complex Type", () -> logger.info("Convert to complex type {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Delete", () -> handleDelete(node)),
                new SeparatorMenuItem(),
                createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for compositor nodes (sequence, choice, all).
     */
    private ContextMenu createCompositorMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        // Add Element submenu with icon
        Menu addMenu = new Menu("Add");
        addMenu.setGraphic(createColoredIcon("bi-plus-circle", "#28a745"));
        addMenu.getItems().add(
                createMenuItemConditional("Element", "bi-plus", "#28a745",
                        () -> handleAddElement(node), () -> canAddElement(node))
        );

        // Change Type submenu with icon
        Menu changeTypeMenu = new Menu("Change Type");
        changeTypeMenu.setGraphic(createColoredIcon("bi-arrow-repeat", "#17a2b8"));

        if (node.getType() != NodeWrapperType.SEQUENCE) {
            changeTypeMenu.getItems().add(
                    createMenuItem("Sequence", "bi-list-ol", "#6c757d",
                            () -> logger.info("Change compositor to sequence"))
            );
        }

        if (node.getType() != NodeWrapperType.CHOICE) {
            changeTypeMenu.getItems().add(
                    createMenuItem("Choice", "bi-card-list", "#6c757d",
                            () -> logger.info("Change compositor to choice"))
            );
        }

        if (node.getType() != NodeWrapperType.ALL) {
            changeTypeMenu.getItems().add(
                    createMenuItem("All", "bi-grid-3x3", "#6c757d",
                            () -> logger.info("Change compositor to all"))
            );
        }

        // Edit Cardinality
        MenuItem editCardinalityItem = createMenuItem("Edit Cardinality", "bi-arrow-left-right", "#17a2b8",
                () -> logger.info("Edit cardinality of compositor"));

        // Delete
        MenuItem deleteItem = createMenuItem("Delete", "bi-trash", "#dc3545",
                () -> handleDelete(node));

        menu.getItems().addAll(
                addMenu,
                changeTypeMenu,
                editCardinalityItem,
                new SeparatorMenuItem(),
                deleteItem,
                new SeparatorMenuItem(),
                createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for schema root node.
     */
    private ContextMenu createSchemaMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Add Root Element", () -> {
                    Object modelObject = node.getModelObject();
                    if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode parentNode) {
                        handleAddRootElement(parentNode);
                    }
                }),
                createMenuItem("Add Global Type", () -> logger.info("Add global type to schema")),
                new SeparatorMenuItem(),
                createMenuItem("Edit Namespace", () -> logger.info("Edit schema namespace")),
                new SeparatorMenuItem(),
                createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for group nodes.
     */
    private ContextMenu createGroupMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Add Element", () -> handleAddElement(node)),
                new SeparatorMenuItem(),
                createMenuItem("Rename", () -> handleRename(node)),
                createMenuItem("Delete", () -> handleDelete(node)),
                new SeparatorMenuItem(),
                createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for enumeration nodes.
     */
    private ContextMenu createEnumerationMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Edit Value", () -> logger.info("Edit enumeration value {}", node.getLabel())),
                createMenuItem("Delete", () -> handleDelete(node)),
                new SeparatorMenuItem(),
                createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
        );

        return menu;
    }

    /**
     * Creates a default context menu for unknown node types.
     */
    private ContextMenu createDefaultMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Properties", () -> logger.info("Show properties for {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItemAlwaysEnabled("Copy XPath", "bi-signpost-2", "#ffc107", () -> handleCopyXPath(node))
        );

        return menu;
    }

    /**
     * Creates a menu item with action.
     *
     * @param text   the menu item text
     * @param action the action to execute
     * @return the menu item
     */
    private MenuItem createMenuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> {
            if (!editorContext.isEditMode()) {
                logger.warn("Cannot execute action '{}' in view mode", text);
                return;
            }
            action.run();
        });

        // Disable menu items when not in edit mode
        item.setDisable(!editorContext.isEditMode());

        return item;
    }

    /**
     * Creates a menu item with icon and action.
     *
     * @param text        the menu item text
     * @param iconLiteral the icon literal (e.g., "bi-plus-circle")
     * @param iconColor   the icon color (e.g., "#28a745")
     * @param action      the action to execute
     * @return the menu item
     */
    private MenuItem createMenuItem(String text, String iconLiteral, String iconColor, Runnable action) {
        MenuItem item = createMenuItem(text, action);
        item.setGraphic(createColoredIcon(iconLiteral, iconColor));
        return item;
    }

    /**
     * Creates a menu item that is always enabled (regardless of edit mode).
     * Used for navigation actions like "Edit Type in Editor".
     *
     * @param text   the menu item text
     * @param action the action to execute
     * @return the menu item
     */
    private MenuItem createMenuItemAlwaysEnabled(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    /**
     * Creates a menu item with icon that is always enabled (regardless of edit mode).
     * Used for navigation actions like "Edit Type in Editor".
     *
     * @param text        the menu item text
     * @param iconLiteral the icon literal (e.g., "bi-box-arrow-up-right")
     * @param iconColor   the icon color (e.g., "#17a2b8")
     * @param action      the action to execute
     * @return the menu item
     */
    private MenuItem createMenuItemAlwaysEnabled(String text, String iconLiteral, String iconColor, Runnable action) {
        MenuItem item = createMenuItemAlwaysEnabled(text, action);
        item.setGraphic(createColoredIcon(iconLiteral, iconColor));
        return item;
    }

    /**
     * Creates a conditional menu item with icon.
     * The menu item is enabled/disabled based on a condition.
     *
     * @param text              the menu item text
     * @param iconLiteral       the icon literal (e.g., "bi-list-ol")
     * @param iconColor         the icon color (e.g., "#6c757d")
     * @param action            the action to execute
     * @param enabledCondition  supplier that returns true if item should be enabled
     * @return the menu item
     */
    private MenuItem createMenuItemConditional(String text, String iconLiteral, String iconColor,
                                               Runnable action, Supplier<Boolean> enabledCondition) {
        MenuItem item = createMenuItem(text, iconLiteral, iconColor, action);
        item.setDisable(!enabledCondition.get());
        return item;
    }

    /**
     * Handles delete operation for a node.
     *
     * @param node the node to delete
     */
    private void handleDelete(VisualNode node) {
        // Extract XsdNode from VisualNode for the command
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            DeleteNodeCommand command = new DeleteNodeCommand(xsdNode);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Executed delete command for node: {}", node.getLabel());
        } else {
            logger.warn("Cannot delete node - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles rename operation for a node.
     *
     * @param node the node to rename
     */
    private void handleRename(VisualNode node) {
        TextInputDialog dialog = new TextInputDialog(node.getLabel());
        dialog.setTitle("Rename Node");
        dialog.setHeaderText("Rename '" + node.getLabel() + "'");
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.trim().isEmpty() && !newName.equals(node.getLabel())) {
                // Extract XsdNode from VisualNode for the command
                Object modelObject = node.getModelObject();
                if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                    RenameNodeCommand command = new RenameNodeCommand(xsdNode, newName);
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Executed rename command: '{}' -> '{}'", node.getLabel(), newName);
                } else {
                    logger.warn("Cannot rename node - model object is not an XsdNode: {}",
                            modelObject != null ? modelObject.getClass() : "null");
                }
            }
        });
    }

    /**
     * Handles duplicate operation for a node.
     *
     * @param node the node to duplicate
     */
    private void handleDuplicate(VisualNode node) {
        // Extract XsdNode from VisualNode for the command
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            DuplicateNodeCommand command = new DuplicateNodeCommand(xsdNode);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Executed duplicate command for node: {}", node.getLabel());
        } else {
            logger.warn("Cannot duplicate node - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles change type operation for a node.
     *
     * @param node the node whose type to change
     */
    private void handleChangeType(VisualNode node) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Change Type");
        dialog.setHeaderText("Change type of '" + node.getLabel() + "'");
        dialog.setContentText("New type (e.g., xs:string, MyCustomType):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newType -> {
            if (!newType.trim().isEmpty()) {
                // Extract XsdNode from VisualNode for the command
                Object modelObject = node.getModelObject();
                if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                    ChangeTypeCommand command = new ChangeTypeCommand(editorContext, xsdNode, newType);
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Executed change type command for node: {}", node.getLabel());
                } else {
                    logger.warn("Cannot change type - model object is not an XsdNode: {}",
                            modelObject != null ? modelObject.getClass() : "null");
                }
            }
        });
    }

    /**
     * Handles change cardinality operation for a node.
     *
     * @param node the node whose cardinality to change
     */
    private void handleChangeCardinality(VisualNode node) {
        // Create a simple dialog for cardinality input
        // In a more complete implementation, this would be a custom dialog
        // with spinners for minOccurs/maxOccurs

        TextInputDialog dialog = new TextInputDialog("1..1");
        dialog.setTitle("Change Cardinality");
        dialog.setHeaderText("Change cardinality of '" + node.getLabel() + "'");
        dialog.setContentText("Enter cardinality [min..max] (e.g., 0..1, 1..*, 0..*):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(cardinality -> {
            if (!cardinality.trim().isEmpty()) {
                try {
                    // Parse cardinality string [min..max]
                    String cleaned = cardinality.trim().replaceAll("[\\[\\]]", "");
                    String[] parts = cleaned.split("\\.\\.");

                    if (parts.length == 2) {
                        int minOccurs = Integer.parseInt(parts[0].trim());
                        int maxOccurs;

                        if (parts[1].trim().equals("*") || parts[1].trim().equalsIgnoreCase("unbounded")) {
                            maxOccurs = ChangeCardinalityCommand.UNBOUNDED;
                        } else {
                            maxOccurs = Integer.parseInt(parts[1].trim());
                        }

                        // Extract XsdNode from VisualNode for the command
                        Object modelObject = node.getModelObject();
                        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
                            ChangeCardinalityCommand command = new ChangeCardinalityCommand(xsdNode, minOccurs, maxOccurs);
                            editorContext.getCommandManager().executeCommand(command);
                            logger.info("Executed change cardinality command for node: {}", node.getLabel());
                        } else {
                            logger.warn("Cannot change cardinality - model object is not an XsdNode: {}",
                                    modelObject != null ? modelObject.getClass() : "null");
                        }
                    } else {
                        logger.warn("Invalid cardinality format: {}", cardinality);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse cardinality: {}", cardinality, e);
                }
            }
        });
    }

    /**
     * Handles add element operation for a node.
     * Creates a new element without a type (following XSD standard).
     * Element can then have either a type or child compositors (Sequence/Choice/All).
     *
     * @param node the parent node
     */
    private void handleAddElement(VisualNode node) {
        TextInputDialog dialog = new TextInputDialog("newElement");
        dialog.setTitle("Add Element");
        dialog.setHeaderText("Add element to '" + node.getLabel() + "'");
        dialog.setContentText("Element name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(elementName -> {
            if (!elementName.trim().isEmpty()) {
                // Extract XsdNode from VisualNode for the command
                Object modelObject = node.getModelObject();
                if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode parentNode) {
                    // Create element without type (type will be xs:string by default in AddElementCommand)
                    // But we create with null type instead
                    AddElementCommand command = new AddElementCommand(parentNode, elementName.trim(), null);
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Added element '{}' to '{}'", elementName, node.getLabel());
                } else {
                    logger.warn("Cannot add element - model object is not an XsdNode: {}",
                            modelObject != null ? modelObject.getClass() : "null");
                }
            }
        });
    }

    /**
     * Handles add attribute operation for a node.
     * Creates a new attribute without a type (type can be set in Properties Panel later).
     *
     * @param node the parent node
     */
    private void handleAddAttribute(VisualNode node) {
        TextInputDialog dialog = new TextInputDialog("newAttribute");
        dialog.setTitle("Add Attribute");
        dialog.setHeaderText("Add attribute to '" + node.getLabel() + "'");
        dialog.setContentText("Attribute name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(attributeName -> {
            if (!attributeName.trim().isEmpty()) {
                // Extract XsdNode from VisualNode for the command
                Object modelObject = node.getModelObject();
                if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode parentNode) {
                    AddAttributeCommand command = new AddAttributeCommand(parentNode, attributeName.trim());
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Added attribute '{}' to '{}'", attributeName, node.getLabel());
                } else {
                    logger.warn("Cannot add attribute - model object is not an XsdNode: {}",
                            modelObject != null ? modelObject.getClass() : "null");
                }
            }
        });
    }

    /**
     * Handles add root element operation for the schema.
     * Creates a new element directly under the schema.
     *
     * @param parentNode the parent node (schema)
     */
    private void handleAddRootElement(org.fxt.freexmltoolkit.controls.v2.model.XsdNode parentNode) {
        TextInputDialog dialog = new TextInputDialog("newElement");
        dialog.setTitle("Add Root Element");
        dialog.setHeaderText("Add element to schema");
        dialog.setContentText("Element name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(elementName -> {
            if (!elementName.trim().isEmpty()) {
                AddElementCommand command = new AddElementCommand(parentNode, elementName.trim(), null);
                editorContext.getCommandManager().executeCommand(command);
                logger.info("Added root element '{}' to schema", elementName);
            }
        });
    }

    /**
     * Handles add sequence operation for an element.
     * Creates an inline complexType with a sequence if needed.
     *
     * @param node the element node
     */
    private void handleAddSequence(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            AddSequenceCommand command = new AddSequenceCommand(element);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Added sequence to element '{}'", element.getName());
        } else {
            logger.warn("Cannot add sequence - model object is not an XsdElement: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles add choice operation for an element.
     * Creates an inline complexType with a choice if needed.
     *
     * @param node the element node
     */
    private void handleAddChoice(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            AddChoiceCommand command = new AddChoiceCommand(element);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Added choice to element '{}'", element.getName());
        } else {
            logger.warn("Cannot add choice - model object is not an XsdElement: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles add all operation for an element.
     * Creates an inline complexType with an all compositor if needed.
     *
     * @param node the element node
     */
    private void handleAddAll(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            AddAllCommand command = new AddAllCommand(element);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Added all to element '{}'", element.getName());
        } else {
            logger.warn("Cannot add all - model object is not an XsdElement: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Creates a Move submenu with Move Up and Move Down options.
     * Menu items are enabled/disabled based on node position.
     *
     * @param node the node to create move menu for
     * @return the Move menu
     */
    private Menu createMoveMenu(VisualNode node) {
        Menu moveMenu = new Menu("Move");
        moveMenu.setGraphic(createColoredIcon("bi-arrows-move", "#6c757d"));

        // Determine if Move Up/Down should be enabled
        Object modelObject = node.getModelObject();
        boolean canMoveUp = false;
        boolean canMoveDown = false;

        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            org.fxt.freexmltoolkit.controls.v2.model.XsdNode parent = xsdNode.getParent();
            if (parent != null) {
                int index = parent.getChildren().indexOf(xsdNode);
                canMoveUp = index > 0;
                canMoveDown = index >= 0 && index < parent.getChildren().size() - 1;
            }
        }

        MenuItem moveUpItem = createMenuItem("Move Up", "bi-arrow-up", "#28a745", () -> handleMoveUp(node));
        MenuItem moveDownItem = createMenuItem("Move Down", "bi-arrow-down", "#dc3545", () -> handleMoveDown(node));

        // Override enabled state based on position
        if (!canMoveUp) {
            moveUpItem.setDisable(true);
        }
        if (!canMoveDown) {
            moveDownItem.setDisable(true);
        }

        moveMenu.getItems().addAll(moveUpItem, moveDownItem);
        return moveMenu;
    }

    /**
     * Handles move up operation for a node.
     * Moves the node one position up among its siblings.
     *
     * @param node the node to move up
     */
    private void handleMoveUp(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            org.fxt.freexmltoolkit.controls.v2.model.XsdNode parent = xsdNode.getParent();
            if (parent != null) {
                int index = parent.getChildren().indexOf(xsdNode);
                if (index > 0) {
                    MoveNodeCommand command = new MoveNodeCommand(xsdNode, parent, index - 1);
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Moved node '{}' up in '{}'", node.getLabel(), parent.getName());
                } else {
                    logger.warn("Cannot move up: node '{}' is already at the top", node.getLabel());
                }
            }
        } else {
            logger.warn("Cannot move up - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles move down operation for a node.
     * Moves the node one position down among its siblings.
     *
     * @param node the node to move down
     */
    private void handleMoveDown(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            org.fxt.freexmltoolkit.controls.v2.model.XsdNode parent = xsdNode.getParent();
            if (parent != null) {
                int index = parent.getChildren().indexOf(xsdNode);
                if (index >= 0 && index < parent.getChildren().size() - 1) {
                    MoveNodeCommand command = new MoveNodeCommand(xsdNode, parent, index + 1);
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Moved node '{}' down in '{}'", node.getLabel(), parent.getName());
                } else {
                    logger.warn("Cannot move down: node '{}' is already at the bottom", node.getLabel());
                }
            }
        } else {
            logger.warn("Cannot move down - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles copy operation for a node.
     * Copies the node to the internal clipboard (for paste within editor) and
     * additionally copies the XSD definition to the system clipboard (for paste in other applications).
     *
     * @param node the node to copy
     */
    private void handleCopy(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            // Copy to internal clipboard for paste within editor
            editorContext.getClipboard().copy(xsdNode);

            // Also copy XSD definition to system clipboard
            copyXsdDefinitionToSystemClipboard(xsdNode);

            logger.info("Copied node '{}' to clipboard", node.getLabel());
        } else {
            logger.warn("Cannot copy - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Serializes the XSD node and copies its definition to the system clipboard.
     * This allows users to paste the XSD code in other applications.
     *
     * @param xsdNode the node to serialize and copy
     */
    private void copyXsdDefinitionToSystemClipboard(org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
        try {
            org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer serializer =
                    new org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer();
            String xsdDefinition = serializer.serializeNodeOnly(xsdNode);

            if (xsdDefinition != null && !xsdDefinition.isEmpty()) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(xsdDefinition);
                clipboard.setContent(content);
                logger.debug("XSD definition copied to system clipboard: {} characters", xsdDefinition.length());
            }
        } catch (Exception e) {
            logger.warn("Failed to copy XSD definition to system clipboard: {}", e.getMessage());
        }
    }

    /**
     * Handles cut operation for a node.
     * Cuts the node to the clipboard (will be deleted on paste).
     *
     * @param node the node to cut
     */
    private void handleCut(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode) {
            editorContext.getClipboard().cut(xsdNode);
            logger.info("Cut node '{}' to clipboard", node.getLabel());
        } else {
            logger.warn("Cannot cut - model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Creates a Paste menu item with proper enabled state.
     * Paste is enabled only when clipboard has content.
     *
     * @param node the target parent node for paste
     * @return the configured Paste menu item
     */
    private MenuItem createPasteMenuItem(VisualNode node) {
        XsdClipboard clipboard = editorContext.getClipboard();
        MenuItem pasteItem = createMenuItem("Paste Node", "bi-clipboard-check", "#28a745", () -> handlePaste(node));

        // Disable paste if clipboard is empty
        if (!clipboard.hasContent()) {
            pasteItem.setDisable(true);
        }

        return pasteItem;
    }

    /**
     * Handles paste operation for a node.
     * Pastes the clipboard content as a child of the target node.
     *
     * @param node the target parent node
     */
    private void handlePaste(VisualNode node) {
        XsdClipboard clipboard = editorContext.getClipboard();
        if (!clipboard.hasContent()) {
            logger.warn("Cannot paste: clipboard is empty");
            return;
        }

        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode targetParent) {
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetParent);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Pasted node to '{}'", node.getLabel());
        } else {
            logger.warn("Cannot paste - target is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Copies the XPath of the selected node to the system clipboard.
     * This operation is always available (view and edit mode).
     *
     * @param node the node whose XPath to copy
     */
    private void handleCopyXPath(VisualNode node) {
        if (node == null) {
            logger.warn("Cannot copy XPath: node is null");
            return;
        }

        Object modelObject = node.getModelObject();
        if (!(modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode xsdNode)) {
            logger.warn("Cannot copy XPath: model object is not an XsdNode: {}",
                    modelObject != null ? modelObject.getClass() : "null");
            return;
        }

        String xpath = xsdNode.getXPath();
        if (xpath != null && !xpath.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(xpath);
            clipboard.setContent(content);
            logger.debug("XPath copied to clipboard: {}", xpath);
        } else {
            logger.warn("Cannot copy XPath: XPath is null or empty for node '{}'", node.getLabel());
        }
    }

    /**
     * Handles opening a ComplexType in the Type Editor.
     *
     * @param node the ComplexType node to edit
     */
    private void handleEditComplexType(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof XsdComplexType complexType) {
            if (openComplexTypeEditorCallback != null) {
                logger.info("Opening ComplexType '{}' in Type Editor", node.getLabel());
                openComplexTypeEditorCallback.accept(complexType);
            } else {
                logger.warn("Cannot open Type Editor - callback not set");
            }
        } else {
            logger.warn("Cannot edit type - model object is not a ComplexType: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Handles opening a SimpleType in the Type Editor.
     *
     * @param node the SimpleType node to edit
     */
    private void handleEditSimpleType(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof XsdSimpleType simpleType) {
            if (openSimpleTypeEditorCallback != null) {
                logger.info("Opening SimpleType '{}' in Type Editor", node.getLabel());
                openSimpleTypeEditorCallback.accept(simpleType);
            } else {
                logger.warn("Cannot open Type Editor - callback not set");
            }
        } else {
            logger.warn("Cannot edit type - model object is not a SimpleType: {}",
                    modelObject != null ? modelObject.getClass() : "null");
        }
    }

    /**
     * Checks if an element node references a ComplexType.
     *
     * @param node the element node to check
     * @return true if element references a ComplexType
     */
    private boolean hasComplexTypeReference(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            String typeName = element.getType();
            if (typeName != null && !typeName.isEmpty() && !typeName.startsWith("xs:")) {
                // Element references a custom type - check if it's a ComplexType
                XsdComplexType complexType = findComplexTypeInSchema(typeName);
                return complexType != null;
            }
        }
        return false;
    }

    /**
     * Checks if an element node references a SimpleType.
     *
     * @param node the element node to check
     * @return true if element references a SimpleType
     */
    private boolean hasSimpleTypeReference(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            String typeName = element.getType();
            if (typeName != null && !typeName.isEmpty() && !typeName.startsWith("xs:")) {
                // Element references a custom type - check if it's a SimpleType
                XsdSimpleType simpleType = findSimpleTypeInSchema(typeName);
                return simpleType != null;
            }
        }
        return false;
    }

    /**
     * Checks if an attribute node references a SimpleType.
     *
     * @param node the attribute node to check
     * @return true if attribute references a SimpleType
     */
    private boolean hasAttributeSimpleTypeReference(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (modelObject instanceof XsdAttribute attribute) {
            String typeName = attribute.getType();
            if (typeName != null && !typeName.isEmpty() && !typeName.startsWith("xs:")) {
                // Attribute references a custom type - check if it's a SimpleType
                XsdSimpleType simpleType = findSimpleTypeInSchema(typeName);
                return simpleType != null;
            }
        }
        return false;
    }

    /**
     * Checks if a compositor (Sequence/Choice/All) can be added to an element.
     * Returns false if element has a type or ref attribute set.
     * Following XSD standard: element can have either a type OR child compositors, not both.
     *
     * @param node the element node to check
     * @return true if compositor can be added (element has no type/ref)
     */
    private boolean canAddCompositor(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (!(modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element)) {
            return false;
        }

        // Check if element has type attribute
        String type = element.getType();
        if (type != null && !type.trim().isEmpty()) {
            logger.debug("Element '{}' has type '{}', cannot add compositor", element.getName(), type);
            return false;  // Element has type, cannot add compositor
        }

        // Check if element has ref attribute
        String ref = element.getRef();
        if (ref != null && !ref.trim().isEmpty()) {
            logger.debug("Element '{}' has ref '{}', cannot add compositor", element.getName(), ref);
            return false;  // Element has ref, cannot add compositor
        }

        logger.debug("Element '{}' can have compositor added", element.getName());
        return true;  // Element can have compositor
    }

    /**
     * Checks if child elements can be added to a node.
     * According to XSD standard, child elements can only be added to:
     * - Compositor nodes (Sequence/Choice/All) directly
     * - Element nodes that have a compositor child
     *
     * Returns false if:
     * - Node is an Element with a SimpleType
     * - Node is an Element without a compositor child
     *
     * @param node the node to check (element or compositor)
     * @return true if child elements can be added to this node
     */
    private boolean canAddElement(VisualNode node) {
        Object modelObject = node.getModelObject();

        // Compositor nodes (Sequence/Choice/All) can always have child elements added
        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSequence ||
            modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdChoice ||
            modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAll) {
            logger.debug("Node is a compositor, child elements can be added");
            return true;
        }

        // Element nodes - check if they can have child elements
        if (!(modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element)) {
            return false;
        }

        String type = element.getType();

        // Check if it's a SimpleType (built-in or custom)
        if (type != null && !type.trim().isEmpty()) {
            if (type.startsWith("xs:")) {
                // Built-in SimpleType - cannot add child elements
                logger.debug("Element '{}' has built-in SimpleType '{}', cannot add child elements",
                        element.getName(), type);
                return false;
            }

            // Custom type - check if it's a SimpleType or ComplexType
            XsdSimpleType simpleType = findSimpleTypeInSchema(type);
            if (simpleType != null) {
                // It's a SimpleType - cannot add child elements
                logger.debug("Element '{}' references SimpleType '{}', cannot add child elements",
                        element.getName(), type);
                return false;
            }
        }

        // Check if element has a compositor child (required for adding child elements)
        if (element.hasCompositor()) {
            logger.debug("Element '{}' has a compositor, child elements can be added", element.getName());
            return true;
        }

        // No compositor - cannot add child elements according to XSD standard
        logger.debug("Element '{}' has no compositor (Sequence/Choice/All), cannot add child elements", element.getName());
        return false;
    }

    /**
     * Checks if attributes can be added to an element.
     * According to XSD standard, attributes cannot coexist with compositor children
     * (Sequence/Choice/All) in the same complexType.
     *
     * Returns false if:
     * - Element has a SimpleType
     * - Element has a compositor child (Sequence/Choice/All)
     *
     * @param node the element node to check
     * @return true if attributes can be added to the element
     */
    private boolean canAddAttribute(VisualNode node) {
        Object modelObject = node.getModelObject();
        if (!(modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element)) {
            return false;
        }

        // Check if element has a compositor - if so, cannot add attributes
        // (Attributes and compositors are mutually exclusive in XSD)
        if (element.hasCompositor()) {
            logger.debug("Element '{}' has a compositor, cannot add attributes (mutually exclusive)",
                    element.getName());
            return false;
        }

        String type = element.getType();

        // Check if it's a SimpleType (built-in or custom)
        if (type != null && !type.trim().isEmpty()) {
            if (type.startsWith("xs:")) {
                // Built-in SimpleType - cannot add attributes
                logger.debug("Element '{}' has built-in SimpleType '{}', cannot add attributes",
                        element.getName(), type);
                return false;
            }

            // Custom type - check if it's a SimpleType or ComplexType
            XsdSimpleType simpleType = findSimpleTypeInSchema(type);
            if (simpleType != null) {
                // It's a SimpleType - cannot add attributes
                logger.debug("Element '{}' references SimpleType '{}', cannot add attributes",
                        element.getName(), type);
                return false;
            }
        }

        // No compositor, no SimpleType - attributes can be added
        logger.debug("Element '{}' can have attributes added", element.getName());
        return true;
    }

    /**
     * Handles opening the ComplexType that an element references.
     *
     * @param node the element node
     */
    private void handleEditReferencedComplexType(VisualNode node) {
        logger.info("handleEditReferencedComplexType called for node: {}", node.getLabel());

        Object modelObject = node.getModelObject();
        logger.debug("Model object type: {}", modelObject != null ? modelObject.getClass().getSimpleName() : "null");

        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            String typeName = element.getType();
            logger.info("Element '{}' has type attribute: '{}'", node.getLabel(), typeName);

            if (typeName != null && !typeName.isEmpty()) {
                // Find the ComplexType in the schema
                XsdComplexType complexType = findComplexTypeInSchema(typeName);

                if (complexType != null) {
                    if (openComplexTypeEditorCallback != null) {
                        logger.info("Opening referenced ComplexType '{}' in Type Editor", typeName);
                        openComplexTypeEditorCallback.accept(complexType);
                    } else {
                        logger.error("Cannot open Type Editor - openComplexTypeEditorCallback is NOT SET!");
                    }
                } else {
                    logger.warn("Referenced type '{}' not found or is not a ComplexType", typeName);
                }
            } else {
                logger.warn("Element '{}' has no type attribute or empty type", node.getLabel());
            }
        } else {
            logger.warn("Model object is not an XsdElement: {}", modelObject);
        }
    }

    /**
     * Handles opening the SimpleType that an element references.
     *
     * @param node the element node
     */
    private void handleEditReferencedSimpleType(VisualNode node) {
        logger.info("handleEditReferencedSimpleType called for node: {}", node.getLabel());

        Object modelObject = node.getModelObject();
        logger.debug("Model object type: {}", modelObject != null ? modelObject.getClass().getSimpleName() : "null");

        if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdElement element) {
            String typeName = element.getType();
            logger.info("Element '{}' has type attribute: '{}'", node.getLabel(), typeName);

            if (typeName != null && !typeName.isEmpty()) {
                // Find the SimpleType in the schema
                XsdSimpleType simpleType = findSimpleTypeInSchema(typeName);

                if (simpleType != null) {
                    if (openSimpleTypeEditorCallback != null) {
                        logger.info("Opening referenced SimpleType '{}' in Type Editor", typeName);
                        openSimpleTypeEditorCallback.accept(simpleType);
                    } else {
                        logger.error("Cannot open Type Editor - openSimpleTypeEditorCallback is NOT SET!");
                    }
                } else {
                    logger.warn("Referenced type '{}' not found or is not a SimpleType", typeName);
                }
            } else {
                logger.warn("Element '{}' has no type attribute or empty type", node.getLabel());
            }
        } else {
            logger.warn("Model object is not an XsdElement: {}", modelObject);
        }
    }

    /**
     * Handles opening the SimpleType that an attribute references.
     *
     * @param node the attribute node
     */
    private void handleEditReferencedAttributeSimpleType(VisualNode node) {
        logger.info("handleEditReferencedAttributeSimpleType called for node: {}", node.getLabel());

        Object modelObject = node.getModelObject();
        logger.debug("Model object type: {}", modelObject != null ? modelObject.getClass().getSimpleName() : "null");

        if (modelObject instanceof XsdAttribute attribute) {
            String typeName = attribute.getType();
            logger.info("Attribute '{}' has type attribute: '{}'", node.getLabel(), typeName);

            if (typeName != null && !typeName.isEmpty()) {
                // Find the SimpleType in the schema
                XsdSimpleType simpleType = findSimpleTypeInSchema(typeName);

                if (simpleType != null) {
                    if (openSimpleTypeEditorCallback != null) {
                        logger.info("Opening referenced SimpleType '{}' in Type Editor", typeName);
                        openSimpleTypeEditorCallback.accept(simpleType);
                    } else {
                        logger.error("Cannot open Type Editor - openSimpleTypeEditorCallback is NOT SET!");
                    }
                } else {
                    logger.warn("Referenced type '{}' not found or is not a SimpleType", typeName);
                }
            } else {
                logger.warn("Attribute '{}' has no type attribute or empty type", node.getLabel());
            }
        } else {
            logger.warn("Model object is not an XsdAttribute: {}", modelObject);
        }
    }

    /**
     * Finds a ComplexType by name in the schema.
     * Handles namespace prefixes (e.g., "tns:MyType" matches "MyType").
     *
     * @param typeName the name of the type to find
     * @return the ComplexType, or null if not found
     */
    private XsdComplexType findComplexTypeInSchema(String typeName) {
        if (editorContext == null || editorContext.getSchema() == null) {
            logger.warn("Cannot find ComplexType '{}' - editorContext or schema is null", typeName);
            return null;
        }

        // Remove namespace prefix if present (e.g., "tns:MyType" -> "MyType")
        String localTypeName = typeName;
        if (typeName.contains(":")) {
            localTypeName = typeName.substring(typeName.indexOf(':') + 1);
            logger.debug("Stripped namespace prefix: '{}' -> '{}'", typeName, localTypeName);
        }

        logger.debug("Searching for ComplexType '{}' in schema with {} children",
                localTypeName, editorContext.getSchema().getChildren().size());

        // Search for ComplexType in schema's children
        for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : editorContext.getSchema().getChildren()) {
            if (child instanceof XsdComplexType complexType) {
                String complexTypeName = complexType.getName();
                logger.trace("Checking ComplexType: '{}' against '{}'", complexTypeName, localTypeName);

                // Match exact name or local name after prefix stripping
                if (localTypeName.equals(complexTypeName)) {
                    logger.info("Found ComplexType '{}' in schema", localTypeName);
                    return complexType;
                }
            }
        }

        logger.warn("ComplexType '{}' not found in schema children. Available types: {}",
                localTypeName,
                editorContext.getSchema().getChildren().stream()
                    .filter(c -> c instanceof XsdComplexType)
                    .map(c -> c.getName())
                    .toList());
        return null;
    }

    /**
     * Finds a SimpleType by name in the schema.
     * Handles namespace prefixes (e.g., "tns:MyType" matches "MyType").
     *
     * @param typeName the name of the type to find
     * @return the SimpleType, or null if not found
     */
    private XsdSimpleType findSimpleTypeInSchema(String typeName) {
        if (editorContext == null || editorContext.getSchema() == null) {
            logger.warn("Cannot find SimpleType '{}' - editorContext or schema is null", typeName);
            return null;
        }

        // Remove namespace prefix if present (e.g., "tns:MyType" -> "MyType")
        String localTypeName = typeName;
        if (typeName.contains(":")) {
            localTypeName = typeName.substring(typeName.indexOf(':') + 1);
            logger.debug("Stripped namespace prefix: '{}' -> '{}'", typeName, localTypeName);
        }

        logger.debug("Searching for SimpleType '{}' in schema with {} children",
                localTypeName, editorContext.getSchema().getChildren().size());

        // Search for SimpleType in schema's children
        for (org.fxt.freexmltoolkit.controls.v2.model.XsdNode child : editorContext.getSchema().getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                String simpleTypeName = simpleType.getName();
                logger.trace("Checking SimpleType: '{}' against '{}'", simpleTypeName, localTypeName);

                // Match exact name or local name after prefix stripping
                if (localTypeName.equals(simpleTypeName)) {
                    logger.info("Found SimpleType '{}' in schema", localTypeName);
                    return simpleType;
                }
            }
        }

        logger.warn("SimpleType '{}' not found in schema children. Available types: {}",
                localTypeName,
                editorContext.getSchema().getChildren().stream()
                    .filter(c -> c instanceof XsdSimpleType)
                    .map(c -> c.getName())
                    .toList());
        return null;
    }

    /**
     * Creates a colored FontIcon for menu items.
     * Matches the style from XmlGraphicEditor for consistent look & feel.
     *
     * @param iconLiteral the icon literal (e.g., "bi-plus-circle")
     * @param color the hex color code (e.g., "#28a745")
     * @return the configured FontIcon
     */
    private FontIcon createColoredIcon(String iconLiteral, String color) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(Color.web(color));
        icon.setIconSize(12);
        return icon;
    }
}
