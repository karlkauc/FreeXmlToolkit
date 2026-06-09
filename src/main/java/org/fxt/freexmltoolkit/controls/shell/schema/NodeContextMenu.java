package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Builds the shared XSD node-editing context menu (Add Element/Attribute/
 * Sequence/Choice, Rename, Change Type, Change Cardinality, Delete) used by the
 * {@link XsdTreeView}. The menu acts on the node supplied by {@code currentNode}
 * (the selected tree item).
 */
public final class NodeContextMenu {

    private NodeContextMenu() {
    }

    /** @return a context menu whose items invoke {@code actions} on {@code currentNode.get()}. */
    public static ContextMenu build(NodeEditActions actions, Supplier<XsdNode> currentNode) {
        ContextMenu menu = new ContextMenu();
        MenuItem paste = item("Paste", "bi-clipboard-plus", currentNode, actions::paste);
        menu.getItems().addAll(
                item("Add Element…", "bi-plus-circle", currentNode,
                        node -> promptName(actions::addElement, node, "Add Element", "NewElement")),
                item("Add Container Element…", "bi-node-plus", currentNode,
                        node -> promptName(actions::addContainerElement, node, "Add Container Element", "NewContainer")),
                item("Add Attribute…", "bi-at", currentNode,
                        node -> promptName(actions::addAttribute, node, "Add Attribute", "newAttribute")),
                item("Add Sequence", "bi-list-ol", currentNode, actions::addSequence),
                item("Add Choice", "bi-signpost-split", currentNode, actions::addChoice),
                item("Add All", "bi-list-check", currentNode, actions::addAll),
                item("Add Comment…", "bi-chat-left-text", currentNode,
                        node -> promptComment(actions, node)),
                new SeparatorMenuItem(),
                item("Rename…", "bi-pencil", currentNode, node -> promptRename(actions, node)),
                item("Change Type…", "bi-type", currentNode, node -> promptChangeType(actions, node)),
                item("Change Cardinality…", "bi-arrows-expand", currentNode, node -> promptCardinality(actions, node)),
                new SeparatorMenuItem(),
                item("Copy", "bi-clipboard", currentNode, actions::copy),
                item("Cut", "bi-scissors", currentNode, actions::cut),
                paste,
                new SeparatorMenuItem(),
                item("Duplicate", "bi-copy", currentNode, actions::duplicate),
                item("Move Up", "bi-arrow-up", currentNode, actions::moveUp),
                item("Move Down", "bi-arrow-down", currentNode, actions::moveDown),
                new SeparatorMenuItem(),
                item("Delete", "bi-trash", currentNode, actions::delete));
        // Enable Paste only when the clipboard holds a node (re-checked on each open).
        menu.setOnShowing(e -> paste.setDisable(!actions.canPaste()));
        return menu;
    }

    private static MenuItem item(String text, String icon, Supplier<XsdNode> currentNode, Consumer<XsdNode> action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        MenuItem menuItem = new MenuItem(text, graphic);
        menuItem.setOnAction(e -> {
            XsdNode node = currentNode.get();
            if (node != null) {
                action.accept(node);
            }
        });
        return menuItem;
    }

    private static void promptComment(NodeEditActions actions, XsdNode node) {
        TextInputDialog dialog = new TextInputDialog("comment");
        dialog.setTitle("Add Comment");
        dialog.setHeaderText("Comment text:");
        dialog.showAndWait().ifPresent(text -> {
            if (!text.isBlank()) {
                actions.addComment(node, text.trim());
            }
        });
    }

    private static void promptName(BiConsumer<XsdNode, String> action, XsdNode node,
                                   String title, String defaultName) {
        TextInputDialog dialog = new TextInputDialog(defaultName);
        dialog.setTitle(title);
        dialog.setHeaderText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                action.accept(node, name.trim());
            }
        });
    }

    private static void promptChangeType(NodeEditActions actions, XsdNode node) {
        TextInputDialog dialog = new TextInputDialog("xs:string");
        dialog.setTitle("Change Type");
        dialog.setHeaderText("New type:");
        dialog.showAndWait().ifPresent(type -> {
            if (!type.isBlank()) {
                actions.changeType(node, type.trim());
            }
        });
    }

    private static void promptRename(NodeEditActions actions, XsdNode node) {
        TextInputDialog dialog = new TextInputDialog(node.getName() != null ? node.getName() : "");
        dialog.setTitle("Rename");
        dialog.setHeaderText("New name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                actions.rename(node, name.trim());
            }
        });
    }

    private static void promptCardinality(NodeEditActions actions, XsdNode node) {
        String current = node.getMinOccurs() + ".." + (node.getMaxOccurs() < 0 ? "*" : node.getMaxOccurs());
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle("Change Cardinality");
        dialog.setHeaderText("Cardinality as min..max (use * for unbounded):");
        dialog.showAndWait().ifPresent(text -> {
            String[] parts = text.split("\\.\\.");
            if (parts.length == 2) {
                try {
                    int min = Integer.parseInt(parts[0].trim());
                    String maxText = parts[1].trim();
                    int max = (maxText.equals("*") || maxText.equalsIgnoreCase("unbounded"))
                            ? -1 : Integer.parseInt(maxText);
                    actions.changeCardinality(node, min, max);
                } catch (NumberFormatException ignored) {
                    // invalid input: ignore
                }
            }
        });
    }
}
