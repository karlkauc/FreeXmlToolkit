package org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.selection;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

/**
 * Manages selection state in the XML editor.
 *
 * <p>Tracks currently selected XmlNode(s) and fires PropertyChangeEvents
 * when the selection changes. Supports both single and multiple selection.</p>
 *
 * <p>PropertyChangeEvents fired:</p>
 * <ul>
 *   <li>"selectedNode" - Single selection changed (oldValue, newValue)</li>
 *   <li>"selectedNodes" - Multiple selection changed (oldValue, newValue)</li>
 *   <li>"selectionCleared" - Selection was cleared (oldValue, null)</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * SelectionModel selectionModel = new SelectionModel();
 * selectionModel.addPropertyChangeListener(evt -> {
 *     if ("selectedNode".equals(evt.getPropertyName())) {
 *         XmlNode node = (XmlNode) evt.getNewValue();
 *         updatePropertyPanel(node);
 *     }
 * });
 *
 * // Single selection
 * selectionModel.setSelectedNode(element);
 *
 * // Multiple selection
 * selectionModel.setSelectedNodes(Arrays.asList(elem1, elem2));
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public class SelectionModel {

    /**
     * PropertyChangeSupport for firing selection change events.
     */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Currently selected nodes (may be empty).
     */
    private final List<XmlNode> selectedNodes = new ArrayList<>();

    /**
     * Whether multiple selection is enabled.
     */
    private boolean multipleSelectionEnabled = false;

    // ==================== Constructor ====================

    /**
     * Constructs a new SelectionModel with single selection mode.
     */
    public SelectionModel() {
    }

    /**
     * Constructs a new SelectionModel with specified selection mode.
     *
     * @param multipleSelectionEnabled true to enable multiple selection
     */
    public SelectionModel(boolean multipleSelectionEnabled) {
        this.multipleSelectionEnabled = multipleSelectionEnabled;
    }

    // ==================== Selection Methods ====================

    /**
     * Sets the selected node (single selection).
     * Clears any previous selection.
     *
     * @param node the node to select (null to clear selection)
     */
    public void setSelectedNode(XmlNode node) {
        XmlNode oldNode = getSelectedNode();

        selectedNodes.clear();
        if (node != null) {
            selectedNodes.add(node);
        }

        pcs.firePropertyChange("selectedNode", oldNode, node);

        if (node == null && oldNode != null) {
            pcs.firePropertyChange("selectionCleared", oldNode, null);
        }
    }

    /**
     * Returns the currently selected node (single selection).
     *
     * @return the selected node, or null if no selection
     */
    public XmlNode getSelectedNode() {
        return selectedNodes.isEmpty() ? null : selectedNodes.get(0);
    }

    /**
     * Sets multiple selected nodes.
     * Only works if multiple selection is enabled.
     *
     * @param nodes the nodes to select (null or empty to clear)
     * @throws UnsupportedOperationException if multiple selection is not enabled
     */
    public void setSelectedNodes(List<XmlNode> nodes) {
        if (!multipleSelectionEnabled && nodes != null && nodes.size() > 1) {
            throw new UnsupportedOperationException(
                    "Multiple selection is not enabled. Use setMultipleSelectionEnabled(true) first.");
        }

        List<XmlNode> oldNodes = new ArrayList<>(selectedNodes);
        selectedNodes.clear();

        if (nodes != null && !nodes.isEmpty()) {
            selectedNodes.addAll(nodes);
        }

        pcs.firePropertyChange("selectedNodes", oldNodes, new ArrayList<>(selectedNodes));

        if (selectedNodes.isEmpty() && !oldNodes.isEmpty()) {
            pcs.firePropertyChange("selectionCleared", oldNodes, null);
        }
    }

    /**
     * Returns all currently selected nodes.
     *
     * @return unmodifiable list of selected nodes
     */
    public List<XmlNode> getSelectedNodes() {
        return Collections.unmodifiableList(selectedNodes);
    }

    /**
     * Adds a node to the current selection (multiple selection).
     * Only works if multiple selection is enabled.
     *
     * @param node the node to add to selection
     * @throws UnsupportedOperationException if multiple selection is not enabled
     */
    public void addToSelection(XmlNode node) {
        if (!multipleSelectionEnabled) {
            throw new UnsupportedOperationException(
                    "Multiple selection is not enabled. Use setSelectedNode() for single selection.");
        }

        if (node == null || selectedNodes.contains(node)) {
            return;
        }

        List<XmlNode> oldNodes = new ArrayList<>(selectedNodes);
        selectedNodes.add(node);
        pcs.firePropertyChange("selectedNodes", oldNodes, new ArrayList<>(selectedNodes));
    }

    /**
     * Removes a node from the current selection.
     *
     * @param node the node to remove
     */
    public void removeFromSelection(XmlNode node) {
        if (node == null || !selectedNodes.contains(node)) {
            return;
        }

        List<XmlNode> oldNodes = new ArrayList<>(selectedNodes);
        selectedNodes.remove(node);
        pcs.firePropertyChange("selectedNodes", oldNodes, new ArrayList<>(selectedNodes));

        if (selectedNodes.isEmpty()) {
            pcs.firePropertyChange("selectionCleared", oldNodes, null);
        }
    }

    /**
     * Toggles selection of a node (adds if not selected, removes if selected).
     *
     * @param node the node to toggle
     * @throws UnsupportedOperationException if multiple selection is not enabled and trying to add
     */
    public void toggleSelection(XmlNode node) {
        if (node == null) {
            return;
        }

        if (selectedNodes.contains(node)) {
            removeFromSelection(node);
        } else {
            if (multipleSelectionEnabled) {
                addToSelection(node);
            } else {
                setSelectedNode(node);
            }
        }
    }

    /**
     * Clears all selection.
     */
    public void clearSelection() {
        if (selectedNodes.isEmpty()) {
            return;
        }

        List<XmlNode> oldNodes = new ArrayList<>(selectedNodes);
        selectedNodes.clear();

        pcs.firePropertyChange("selectedNode", oldNodes.isEmpty() ? null : oldNodes.get(0), null);
        pcs.firePropertyChange("selectedNodes", oldNodes, Collections.emptyList());
        pcs.firePropertyChange("selectionCleared", oldNodes, null);
    }

    /**
     * Checks if a node is selected.
     *
     * @param node the node to check
     * @return true if selected
     */
    public boolean isSelected(XmlNode node) {
        return node != null && selectedNodes.contains(node);
    }

    /**
     * Checks if there is any selection.
     *
     * @return true if at least one node is selected
     */
    public boolean hasSelection() {
        return !selectedNodes.isEmpty();
    }

    /**
     * Returns the number of selected nodes.
     *
     * @return selection count
     */
    public int getSelectionCount() {
        return selectedNodes.size();
    }

    // ==================== Configuration Methods ====================

    /**
     * Sets whether multiple selection is enabled.
     *
     * @param enabled true to enable multiple selection
     */
    public void setMultipleSelectionEnabled(boolean enabled) {
        boolean oldValue = this.multipleSelectionEnabled;
        this.multipleSelectionEnabled = enabled;

        // If disabling multiple selection, keep only first selected node
        if (!enabled && selectedNodes.size() > 1) {
            XmlNode firstNode = selectedNodes.get(0);
            setSelectedNode(firstNode);
        }

        pcs.firePropertyChange("multipleSelectionEnabled", oldValue, enabled);
    }

    /**
     * Returns whether multiple selection is enabled.
     *
     * @return true if enabled
     */
    public boolean isMultipleSelectionEnabled() {
        return multipleSelectionEnabled;
    }

    // ==================== PropertyChangeSupport Methods ====================

    /**
     * Adds a PropertyChangeListener.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Adds a PropertyChangeListener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to remove
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    // ==================== Utility Methods ====================

    /**
     * Returns a string representation of the selection model.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "SelectionModel{" +
                "selectedCount=" + selectedNodes.size() +
                ", multipleSelectionEnabled=" + multipleSelectionEnabled +
                ", nodes=" + selectedNodes +
                '}';
    }
}
