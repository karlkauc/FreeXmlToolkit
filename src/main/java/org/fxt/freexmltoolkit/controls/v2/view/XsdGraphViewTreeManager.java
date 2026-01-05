/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.v2.view;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class for managing visual tree operations in XsdGraphView.
 *
 * <p>Handles tree expansion/collapse, node traversal, and tree state management.</p>
 *
 * @since 2.0
 */
public class XsdGraphViewTreeManager {
    private static final Logger logger = LogManager.getLogger(XsdGraphViewTreeManager.class);

    /**
     * Expands all nodes in a tree recursively.
     *
     * @param node the root node to expand from
     */
    public void expandAllNodes(VisualNode node) {
        if (node == null) {
            logger.warn("Cannot expand null node");
            return;
        }

        expandNodeRecursive(node);
        logger.debug("Expanded all nodes from: {}", node.getLabel());
    }

    /**
     * Collapses all nodes in a tree recursively.
     *
     * @param node the root node to collapse from
     */
    public void collapseAllNodes(VisualNode node) {
        if (node == null) {
            logger.warn("Cannot collapse null node");
            return;
        }

        collapseNodeRecursive(node);
        logger.debug("Collapsed all nodes from: {}", node.getLabel());
    }

    /**
     * Expands a node recursively.
     *
     * @param node the node to expand
     */
    private void expandNodeRecursive(VisualNode node) {
        if (node == null) {
            return;
        }

        node.setExpanded(true);

        if (node.getChildren() != null) {
            for (VisualNode child : node.getChildren()) {
                expandNodeRecursive(child);
            }
        }
    }

    /**
     * Collapses a node recursively.
     *
     * @param node the node to collapse
     */
    private void collapseNodeRecursive(VisualNode node) {
        if (node == null) {
            return;
        }

        node.setExpanded(false);

        if (node.getChildren() != null) {
            for (VisualNode child : node.getChildren()) {
                collapseNodeRecursive(child);
            }
        }
    }

    /**
     * Collects labels of all collapsed nodes in the tree.
     *
     * @param node the root node
     * @param collapsedNodes set to collect collapsed node labels
     */
    public void collectCollapsedNodeIds(VisualNode node, Set<String> collapsedNodes) {
        if (node == null) {
            return;
        }

        if (!node.isExpanded()) {
            collapsedNodes.add(node.getLabel());
        }

        if (node.getChildren() != null) {
            for (VisualNode child : node.getChildren()) {
                collectCollapsedNodeIds(child, collapsedNodes);
            }
        }
    }

    /**
     * Restores expansion state from a set of collapsed node labels.
     *
     * @param node the root node
     * @param collapsedNodes set of collapsed node labels
     */
    public void restoreExpansionState(VisualNode node, Set<String> collapsedNodes) {
        if (node == null) {
            return;
        }

        node.setExpanded(!collapsedNodes.contains(node.getLabel()));

        if (node.getChildren() != null) {
            for (VisualNode child : node.getChildren()) {
                restoreExpansionState(child, collapsedNodes);
            }
        }
    }

    /**
     * Gets the number of visible (non-collapsed) nodes in tree.
     *
     * @param node the root node
     * @return count of visible nodes
     */
    public int countVisibleNodes(VisualNode node) {
        if (node == null) {
            return 0;
        }

        int count = 1; // Count this node
        if (node.isExpanded() && node.getChildren() != null) {
            for (VisualNode child : node.getChildren()) {
                count += countVisibleNodes(child);
            }
        }

        return count;
    }

    /**
     * Gets the total number of nodes in tree (including collapsed).
     *
     * @param node the root node
     * @return total node count
     */
    public int countTotalNodes(VisualNode node) {
        if (node == null) {
            return 0;
        }

        int count = 1; // Count this node
        if (node.getChildren() != null) {
            for (VisualNode child : node.getChildren()) {
                count += countTotalNodes(child);
            }
        }

        return count;
    }

    /**
     * Finds a node by label in the tree.
     *
     * @param node the root node to search from
     * @param label the label to search for
     * @return the node if found, null otherwise
     */
    public VisualNode findNodeById(VisualNode node, String label) {
        if (node == null || label == null) {
            return null;
        }

        if (label.equals(node.getLabel())) {
            return node;
        }

        if (node.getChildren() != null) {
            for (VisualNode child : node.getChildren()) {
                VisualNode found = findNodeById(child, label);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Gets the depth of a node in the tree.
     *
     * @param node the node to check
     * @return depth level (0 for root)
     */
    public int getNodeDepth(VisualNode node) {
        if (node == null) {
            return 0;
        }

        int depth = 0;
        VisualNode parent = node.getParent();
        while (parent != null) {
            depth++;
            parent = parent.getParent();
        }

        return depth;
    }

    /**
     * Checks if a node is a descendant of another node.
     *
     * @param potentialAncestor the potential ancestor
     * @param node the node to check
     * @return true if node is descendant of ancestor
     */
    public boolean isDescendant(VisualNode potentialAncestor, VisualNode node) {
        if (potentialAncestor == null || node == null) {
            return false;
        }

        VisualNode current = node.getParent();
        while (current != null) {
            if (current.getLabel().equals(potentialAncestor.getLabel())) {
                return true;
            }
            current = current.getParent();
        }

        return false;
    }

    /**
     * Logs tree operation information.
     *
     * @param operation the operation name (EXPAND, COLLAPSE, etc.)
     * @param nodeLabel the node label
     * @param details additional details
     */
    public void logTreeOperation(String operation, String nodeLabel, String details) {
        logger.debug("Tree operation {} on '{}': {}", operation, nodeLabel, details);
    }
}
