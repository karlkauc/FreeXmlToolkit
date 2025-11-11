package org.fxt.freexmltoolkit.controls.v2.editor.menu;

import javafx.scene.control.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import java.util.Optional;

/**
 * Factory for creating context-sensitive context menus for XSD editor nodes.
 * <p>
 * Creates appropriate menu items based on node type and context:
 * - Element nodes: Add child elements, attributes, change type, delete, duplicate
 * - ComplexType nodes: Add elements, attributes, compositors, convert to SimpleType
 * - SimpleType nodes: Edit restrictions, facets, convert to ComplexType
 * - Attribute nodes: Change type, make required/optional, delete
 * - Compositor nodes: Change type (sequence/choice/all), add elements
 *
 * @since 2.0
 */
public class XsdContextMenuFactory {

    private static final Logger logger = LogManager.getLogger(XsdContextMenuFactory.class);

    private final XsdEditorContext editorContext;

    /**
     * Creates a new context menu factory.
     *
     * @param editorContext the editor context
     */
    public XsdContextMenuFactory(XsdEditorContext editorContext) {
        this.editorContext = editorContext;
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

        return switch (node.getType()) {
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
    }

    /**
     * Creates a menu for empty canvas (no node selected).
     */
    private ContextMenu createEmptyCanvasMenu() {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Add Root Element", () -> logger.info("Add root element")),
                createMenuItem("Add Global Type", () -> logger.info("Add global type"))
        );
        return menu;
    }

    /**
     * Creates a context menu for element nodes.
     */
    private ContextMenu createElementMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        // Add submenu
        Menu addMenu = new Menu("Add");
        addMenu.getItems().addAll(
                createMenuItem("Child Element", () -> handleAddChildElement(node)),
                createMenuItem("Attribute", () -> logger.info("Add attribute to {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Sequence", () -> logger.info("Add sequence to {}", node.getLabel())),
                createMenuItem("Choice", () -> logger.info("Add choice to {}", node.getLabel())),
                createMenuItem("All", () -> logger.info("Add all to {}", node.getLabel()))
        );

        menu.getItems().addAll(
                addMenu,
                new SeparatorMenuItem(),
                createMenuItem("Change Type", () -> handleChangeType(node)),
                createMenuItem("Rename", () -> handleRename(node)),
                createMenuItem("Edit Cardinality", () -> handleChangeCardinality(node)),
                new SeparatorMenuItem(),
                createMenuItem("Duplicate", () -> handleDuplicate(node)),
                createMenuItem("Delete", () -> handleDelete(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for attribute nodes.
     */
    private ContextMenu createAttributeMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Change Type", () -> logger.info("Change type of attribute {}", node.getLabel())),
                createMenuItem("Rename", () -> handleRename(node)),
                createMenuItem("Toggle Required/Optional", () -> logger.info("Toggle required/optional for {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Delete", () -> handleDelete(node))
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
                addMenu,
                new SeparatorMenuItem(),
                createMenuItem("Rename", () -> handleRename(node)),
                createMenuItem("Convert to Simple Type", () -> logger.info("Convert to simple type {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Delete", () -> handleDelete(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for simple type nodes.
     */
    private ContextMenu createSimpleTypeMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Edit Restrictions", () -> logger.info("Edit restrictions for {}", node.getLabel())),
                createMenuItem("Edit Facets", () -> logger.info("Edit facets for {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Rename", () -> handleRename(node)),
                createMenuItem("Convert to Complex Type", () -> logger.info("Convert to complex type {}", node.getLabel())),
                new SeparatorMenuItem(),
                createMenuItem("Delete", () -> handleDelete(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for compositor nodes (sequence, choice, all).
     */
    private ContextMenu createCompositorMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        // Change compositor type submenu
        Menu changeTypeMenu = new Menu("Change Type To");

        if (node.getType() != NodeWrapperType.SEQUENCE) {
            changeTypeMenu.getItems().add(
                    createMenuItem("Sequence", () -> logger.info("Change compositor to sequence"))
            );
        }

        if (node.getType() != NodeWrapperType.CHOICE) {
            changeTypeMenu.getItems().add(
                    createMenuItem("Choice", () -> logger.info("Change compositor to choice"))
            );
        }

        if (node.getType() != NodeWrapperType.ALL) {
            changeTypeMenu.getItems().add(
                    createMenuItem("All", () -> logger.info("Change compositor to all"))
            );
        }

        menu.getItems().addAll(
                createMenuItem("Add Element", () -> handleAddChildElement(node)),
                changeTypeMenu,
                createMenuItem("Edit Cardinality", () -> logger.info("Edit cardinality of compositor")),
                new SeparatorMenuItem(),
                createMenuItem("Delete", () -> handleDelete(node))
        );

        return menu;
    }

    /**
     * Creates a context menu for schema root node.
     */
    private ContextMenu createSchemaMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Add Root Element", () -> logger.info("Add root element to schema")),
                createMenuItem("Add Global Type", () -> logger.info("Add global type to schema")),
                new SeparatorMenuItem(),
                createMenuItem("Edit Namespace", () -> logger.info("Edit schema namespace"))
        );

        return menu;
    }

    /**
     * Creates a context menu for group nodes.
     */
    private ContextMenu createGroupMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().addAll(
                createMenuItem("Add Element", () -> handleAddChildElement(node)),
                new SeparatorMenuItem(),
                createMenuItem("Rename", () -> handleRename(node)),
                createMenuItem("Delete", () -> handleDelete(node))
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
                createMenuItem("Delete", () -> handleDelete(node))
        );

        return menu;
    }

    /**
     * Creates a default context menu for unknown node types.
     */
    private ContextMenu createDefaultMenu(VisualNode node) {
        ContextMenu menu = new ContextMenu();

        menu.getItems().add(
                createMenuItem("Properties", () -> logger.info("Show properties for {}", node.getLabel()))
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
                    ChangeTypeCommand command = new ChangeTypeCommand(xsdNode, newType);
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
     * Handles add child element operation for a node.
     *
     * @param node the parent node
     */
    private void handleAddChildElement(VisualNode node) {
        TextInputDialog dialog = new TextInputDialog("newElement");
        dialog.setTitle("Add Child Element");
        dialog.setHeaderText("Add child element to '" + node.getLabel() + "'");
        dialog.setContentText("Element name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(elementName -> {
            if (!elementName.trim().isEmpty()) {
                // Extract XsdNode from VisualNode for the command
                Object modelObject = node.getModelObject();
                if (modelObject instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdNode parentNode) {
                    AddElementCommand command = new AddElementCommand(parentNode, elementName.trim());
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Added child element '{}' to '{}'", elementName, node.getLabel());
                } else {
                    logger.warn("Cannot add child element - model object is not an XsdNode: {}",
                            modelObject != null ? modelObject.getClass() : "null");
                }
            }
        });
    }
}
