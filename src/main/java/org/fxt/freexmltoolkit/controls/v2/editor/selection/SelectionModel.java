package org.fxt.freexmltoolkit.controls.v2.editor.selection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

/**
 * Manages selection state for the XSD editor.
 * Supports single and multi-selection.
 *
 * @since 2.0
 */
public class SelectionModel {

    private static final Logger logger = LogManager.getLogger(SelectionModel.class);

    private final Set<VisualNode> selectedNodes = new LinkedHashSet<>();
    private VisualNode primarySelection = null;
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * Constructs a new SelectionModel with an empty selection.
     * Initializes the internal selection tracking structures.
     */
    public SelectionModel() {
        // Default constructor
    }

    /**
     * Selects a single node, clearing previous selection.
     *
     * @param node the node to select
     */
    public void select(VisualNode node) {
        if (node == null) {
            clearSelection();
            return;
        }

        Set<VisualNode> oldSelection = new HashSet<>(selectedNodes);
        selectedNodes.clear();
        selectedNodes.add(node);
        primarySelection = node;

        fireSelectionChanged(oldSelection);
        logger.debug("Selected node: {}", node.getLabel());
    }

    /**
     * Adds a node to the current selection.
     *
     * @param node the node to add
     */
    public void addToSelection(VisualNode node) {
        if (node == null) {
            return;
        }

        Set<VisualNode> oldSelection = new HashSet<>(selectedNodes);
        boolean added = selectedNodes.add(node);

        if (added) {
            if (primarySelection == null) {
                primarySelection = node;
            }
            fireSelectionChanged(oldSelection);
            logger.debug("Added node to selection: {}", node.getLabel());
        }
    }

    /**
     * Removes a node from the current selection.
     *
     * @param node the node to remove
     */
    public void removeFromSelection(VisualNode node) {
        if (node == null) {
            return;
        }

        Set<VisualNode> oldSelection = new HashSet<>(selectedNodes);
        boolean removed = selectedNodes.remove(node);

        if (removed) {
            if (node == primarySelection) {
                primarySelection = selectedNodes.isEmpty() ? null : selectedNodes.iterator().next();
            }
            fireSelectionChanged(oldSelection);
            logger.debug("Removed node from selection: {}", node.getLabel());
        }
    }

    /**
     * Toggles selection state of a node.
     *
     * @param node the node to toggle
     */
    public void toggleSelection(VisualNode node) {
        if (node == null) {
            return;
        }

        if (isSelected(node)) {
            removeFromSelection(node);
        } else {
            addToSelection(node);
        }
    }

    /**
     * Selects multiple nodes.
     *
     * @param nodes the nodes to select
     */
    public void selectMultiple(Collection<VisualNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            clearSelection();
            return;
        }

        Set<VisualNode> oldSelection = new HashSet<>(selectedNodes);
        selectedNodes.clear();
        selectedNodes.addAll(nodes);
        primarySelection = nodes.iterator().next();

        fireSelectionChanged(oldSelection);
        logger.debug("Selected {} nodes", nodes.size());
    }

    /**
     * Clears all selection.
     */
    public void clearSelection() {
        if (selectedNodes.isEmpty()) {
            return;
        }

        Set<VisualNode> oldSelection = new HashSet<>(selectedNodes);
        selectedNodes.clear();
        primarySelection = null;

        fireSelectionChanged(oldSelection);
        logger.debug("Selection cleared");
    }

    /**
     * Checks if a node is selected.
     *
     * @param node the node to check
     * @return true if selected
     */
    public boolean isSelected(VisualNode node) {
        return node != null && selectedNodes.contains(node);
    }

    /**
     * Gets the primary selected node.
     *
     * @return the primary selection or null if empty
     */
    public VisualNode getPrimarySelection() {
        return primarySelection;
    }

    /**
     * Gets all selected nodes.
     *
     * @return unmodifiable set of selected nodes
     */
    public Set<VisualNode> getSelectedNodes() {
        return Collections.unmodifiableSet(selectedNodes);
    }

    /**
     * Gets the number of selected nodes.
     *
     * @return selection count
     */
    public int getSelectionCount() {
        return selectedNodes.size();
    }

    /**
     * Checks if selection is empty.
     *
     * @return true if no nodes are selected
     */
    public boolean isEmpty() {
        return selectedNodes.isEmpty();
    }

    /**
     * Checks if multiple nodes are selected.
     *
     * @return true if more than one node is selected
     */
    public boolean hasMultipleSelection() {
        return selectedNodes.size() > 1;
    }

    /**
     * Adds a selection listener.
     *
     * @param listener the listener to add
     */
    public void addSelectionListener(SelectionListener listener) {
        if (listener != null) {
            propertyChangeSupport.addPropertyChangeListener("selection", evt -> {
                @SuppressWarnings("unchecked")
                Set<VisualNode> oldSelection = (Set<VisualNode>) evt.getOldValue();
                @SuppressWarnings("unchecked")
                Set<VisualNode> newSelection = (Set<VisualNode>) evt.getNewValue();
                listener.selectionChanged(oldSelection, newSelection);
            });
        }
    }

    /**
     * Adds a property change listener.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    private void fireSelectionChanged(Set<VisualNode> oldSelection) {
        propertyChangeSupport.firePropertyChange("selection", oldSelection, new HashSet<>(selectedNodes));
    }
}
