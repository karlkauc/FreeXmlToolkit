package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeSearch;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Virtualized Tree view of an XSD. Reuses the
 * V2 model ({@link XsdNodeFactory} + {@link XsdNode}) and renders it through a
 * JavaFX {@link TreeView}, which virtualizes the cells (only visible rows are
 * materialized). Editing goes through the command stack via the shared
 * node-editing context menu ({@link #setEditActions(NodeEditActions)}).
 *
 * <p>Implements {@link XmlSearchTarget} so the shell's search bar (Ctrl+F) can
 * find and cycle through nodes by name, documentation, attributes etc.</p>
 */
public class XsdTreeView extends TreeView<XsdNode> implements XmlSearchTarget {

    private String lastSearchText = "";
    private List<XsdNode> searchMatches = List.of();
    private int currentMatchIndex = -1;
    private XsdNode searchRoot;

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

    // ==================== Search (XmlSearchTarget) ====================

    /**
     * Navigates to the next or previous node whose searchable text (name,
     * documentation, attributes, facet values, comments …) matches. A match
     * hidden inside a collapsed branch is revealed by expanding its ancestors.
     *
     * @param searchText the text to find (case-insensitive)
     * @param forward    true to move to the next match, false for the previous
     * @return true if a match was found and navigated to
     */
    @Override
    public boolean find(String searchText, boolean forward) {
        if (searchText == null || searchText.isEmpty()) {
            return false;
        }
        XsdNode root = getRoot() != null ? getRoot().getValue() : null;
        if (!searchText.equals(lastSearchText) || root != searchRoot) {
            rebuildMatches(searchText);
        }
        if (searchMatches.isEmpty()) {
            return false;
        }

        if (currentMatchIndex < 0) {
            currentMatchIndex = forward ? 0 : searchMatches.size() - 1;
        } else if (forward) {
            currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size();
        } else {
            currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
        }

        if (!revealMatch(searchMatches.get(currentMatchIndex))) {
            // Node edited away since the match list was built: rebuild once and retry.
            rebuildMatches(searchText);
            if (searchMatches.isEmpty()) {
                return false;
            }
            currentMatchIndex = 0;
            return revealMatch(searchMatches.get(0));
        }
        return true;
    }

    /**
     * Counts matches for the given text and navigates to the first one.
     *
     * @param searchText the text to find (case-insensitive)
     * @return the number of matching nodes
     */
    @Override
    public int findAll(String searchText) {
        rebuildMatches(searchText);
        if (!searchMatches.isEmpty()) {
            currentMatchIndex = 0;
            revealMatch(searchMatches.get(0));
        }
        return searchMatches.size();
    }

    /** Clears the cached search state. */
    @Override
    public void clearSearch() {
        lastSearchText = "";
        searchMatches = List.of();
        currentMatchIndex = -1;
        searchRoot = null;
    }

    /** Rebuilds the match list for the given search text and resets the cursor. */
    private void rebuildMatches(String searchText) {
        lastSearchText = searchText;
        searchRoot = getRoot() != null ? getRoot().getValue() : null;
        searchMatches = XsdNodeSearch.findMatches(searchRoot, searchText);
        currentMatchIndex = -1;
    }

    /**
     * Expands the node's ancestors, selects it and scrolls it into view.
     *
     * @return true if the node is (still) present in the tree
     */
    private boolean revealMatch(XsdNode node) {
        return revealNode(node);
    }

    /**
     * Expands the ancestors of the given node (by identity), selects it and scrolls it into
     * view — unlike {@link #selectNode(XsdNode)}, which silently no-ops on collapsed items.
     *
     * @return true if the node is present in the tree
     */
    public boolean revealNode(XsdNode node) {
        TreeItem<XsdNode> item = findItem(getRoot(), node);
        if (item == null) {
            return false;
        }
        for (TreeItem<XsdNode> parent = item.getParent(); parent != null; parent = parent.getParent()) {
            parent.setExpanded(true);
        }
        getSelectionModel().select(item);
        scrollTo(getRow(item));
        return true;
    }

    /**
     * Parses XSD text and renders it.
     *
     * @return {@code true} if parsing succeeded; on failure the tree is cleared
     */
    public boolean setXsdFromText(String xsdContent) {
        return setXsdFromText(xsdContent, null);
    }

    /**
     * Parses XSD text and renders it, resolving relative {@code xs:import}/{@code xs:include}
     * {@code schemaLocation}s against {@code schemaFile}'s directory when it is on disk (issue #36:
     * without a base directory, relative imports fall back to a namespace-URL download instead of
     * being read from disk).
     *
     * @param schemaFile the document's path on disk, or {@code null} when unsaved/not on disk
     * @return {@code true} if parsing succeeded; on failure the tree is cleared
     */
    public boolean setXsdFromText(String xsdContent, java.nio.file.Path schemaFile) {
        try {
            XsdNodeFactory factory = new XsdNodeFactory();
            XsdSchema schema = schemaFile != null
                    ? factory.fromStringWithSchemaFile(xsdContent, schemaFile, schemaFile.getParent())
                    : factory.fromString(xsdContent);
            setSchema(schema);
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
