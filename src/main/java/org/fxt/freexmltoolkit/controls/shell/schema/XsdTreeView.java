package org.fxt.freexmltoolkit.controls.shell.schema;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Virtualized Tree view of an XSD (UI rebuild Phase 4, increment 1). Reuses the
 * V2 model ({@link XsdNodeFactory} + {@link XsdNode}) and renders it through a
 * JavaFX {@link TreeView}, which virtualizes the cells (only visible rows are
 * materialized — see the Phase-2 feasibility spike). Read-only for now; editing
 * via the command stack lands in a later increment.
 */
public class XsdTreeView extends TreeView<XsdNode> {

    public XsdTreeView() {
        getStyleClass().add("fxt-xsd-tree");
        setShowRoot(true);
        setCellFactory(tv -> new XsdNodeCell());
    }

    /** Installs a context menu (Add Element / Rename / Cardinality / Delete) wired to the actions. */
    public void setEditActions(NodeEditActions actions) {
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        menu.getItems().addAll(
                menuItem("Add Element…", "bi-plus-circle", node -> promptName(actions::addElement, node, "Add Element", "NewElement")),
                menuItem("Add Attribute…", "bi-at", node -> promptName(actions::addAttribute, node, "Add Attribute", "newAttribute")),
                menuItem("Add Sequence", "bi-list-ol", actions::addSequence),
                menuItem("Add Choice", "bi-signpost-split", actions::addChoice),
                new javafx.scene.control.SeparatorMenuItem(),
                menuItem("Rename…", "bi-pencil", node -> promptRename(actions, node)),
                menuItem("Change Type…", "bi-type", node -> promptChangeType(actions, node)),
                menuItem("Change Cardinality…", "bi-arrows-expand", node -> promptCardinality(actions, node)),
                new javafx.scene.control.SeparatorMenuItem(),
                menuItem("Delete", "bi-trash", actions::delete));
        setContextMenu(menu);
    }

    private javafx.scene.control.MenuItem menuItem(String text, String icon,
                                                  java.util.function.Consumer<XsdNode> action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(text, graphic);
        item.setOnAction(e -> {
            javafx.scene.control.TreeItem<XsdNode> selected = getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() != null) {
                action.accept(selected.getValue());
            }
        });
        return item;
    }

    private void promptName(java.util.function.BiConsumer<XsdNode, String> action, XsdNode node,
                            String title, String defaultName) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(defaultName);
        dialog.setTitle(title);
        dialog.setHeaderText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                action.accept(node, name.trim());
            }
        });
    }

    private void promptChangeType(NodeEditActions actions, XsdNode node) {
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("xs:string");
        dialog.setTitle("Change Type");
        dialog.setHeaderText("New type:");
        dialog.showAndWait().ifPresent(type -> {
            if (!type.isBlank()) {
                actions.changeType(node, type.trim());
            }
        });
    }

    private void promptRename(NodeEditActions actions, XsdNode node) {
        javafx.scene.control.TextInputDialog dialog =
                new javafx.scene.control.TextInputDialog(node.getName() != null ? node.getName() : "");
        dialog.setTitle("Rename");
        dialog.setHeaderText("New name:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                actions.rename(node, name.trim());
            }
        });
    }

    private void promptCardinality(NodeEditActions actions, XsdNode node) {
        String current = node.getMinOccurs() + ".." + (node.getMaxOccurs() < 0 ? "*" : node.getMaxOccurs());
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog(current);
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

    /** Renders the given parsed schema. */
    public void setSchema(XsdSchema schema) {
        setRoot(XsdTreeBuilder.build(schema));
    }

    /** Selects the tree item backing the given node (by identity), if present. */
    public void selectNode(XsdNode node) {
        javafx.scene.control.TreeItem<XsdNode> item = findItem(getRoot(), node);
        if (item != null) {
            getSelectionModel().select(item);
        }
    }

    private javafx.scene.control.TreeItem<XsdNode> findItem(
            javafx.scene.control.TreeItem<XsdNode> item, XsdNode target) {
        if (item == null) {
            return null;
        }
        if (item.getValue() == target) {
            return item;
        }
        for (javafx.scene.control.TreeItem<XsdNode> child : item.getChildren()) {
            javafx.scene.control.TreeItem<XsdNode> found = findItem(child, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Parses XSD text and renders it.
     *
     * @return {@code true} if parsing succeeded; on failure the tree is cleared
     */
    public boolean setXsdFromText(String xsdContent) {
        try {
            setSchema(new XsdNodeFactory().fromString(xsdContent));
            return true;
        } catch (Exception e) {
            setRoot(null);
            return false;
        }
    }

    /** Cell rendering a node's type icon and display text. */
    private static final class XsdNodeCell extends TreeCell<XsdNode> {
        @Override
        protected void updateItem(XsdNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(XsdNodeLabels.displayText(item));
            IconifyIcon icon = new IconifyIcon(XsdNodeLabels.icon(item.getNodeType()));
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }
}
