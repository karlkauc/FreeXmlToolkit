package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.HashMap;
import java.util.Map;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
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

    /** Installs the shared node-editing context menu wired to the actions. */
    public void setEditActions(NodeEditActions actions) {
        setContextMenu(NodeContextMenu.build(actions, () -> {
            javafx.scene.control.TreeItem<XsdNode> selected = getSelectionModel().getSelectedItem();
            return selected != null ? selected.getValue() : null;
        }));
    }

    /**
     * Renders the given parsed schema, preserving the user's expand/collapse
     * state (by node id) across re-renders — e.g. after a structured edit, where
     * the model nodes (and their immutable ids) persist (matrix #50).
     */
    public void setSchema(XsdSchema schema) {
        Map<String, Boolean> expansion = captureExpansion(getRoot(), new HashMap<>());
        TreeItem<XsdNode> root = XsdTreeBuilder.build(schema);
        if (!expansion.isEmpty()) {
            applyExpansion(root, expansion);
        }
        setRoot(root);
    }

    /** Records {@code nodeId -> isExpanded} for every non-leaf item in the current tree. */
    private Map<String, Boolean> captureExpansion(TreeItem<XsdNode> item, Map<String, Boolean> acc) {
        if (item == null) {
            return acc;
        }
        if (item.getValue() != null && !item.isLeaf()) {
            acc.put(item.getValue().getId(), item.isExpanded());
        }
        for (TreeItem<XsdNode> child : item.getChildren()) {
            captureExpansion(child, acc);
        }
        return acc;
    }

    /** Restores the captured expand/collapse state onto the freshly built tree (by node id). */
    private void applyExpansion(TreeItem<XsdNode> item, Map<String, Boolean> expansion) {
        if (item.getValue() != null) {
            Boolean expanded = expansion.get(item.getValue().getId());
            if (expanded != null) {
                item.setExpanded(expanded);
            }
        }
        for (TreeItem<XsdNode> child : item.getChildren()) {
            applyExpansion(child, expansion);
        }
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
