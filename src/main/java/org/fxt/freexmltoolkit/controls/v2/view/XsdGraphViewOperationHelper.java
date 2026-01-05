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
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

/**
 * Helper class for node operations in XsdGraphView.
 *
 * <p>Handles clipboard operations (copy, cut, paste) and node ordering operations.</p>
 *
 * @since 2.0
 */
public class XsdGraphViewOperationHelper {
    private static final Logger logger = LogManager.getLogger(XsdGraphViewOperationHelper.class);

    private XsdNode clipboardContent = null;
    private boolean isClipboardCut = false;

    /**
     * Copies a node to clipboard.
     *
     * @param node the node to copy
     */
    public void copyToClipboard(XsdNode node) {
        if (node == null) {
            logger.warn("Cannot copy null node");
            return;
        }

        this.clipboardContent = node.deepCopy("_copy");
        this.isClipboardCut = false;
        logger.debug("Copied node to clipboard: {}", node.getName());
    }

    /**
     * Cuts a node to clipboard.
     *
     * @param node the node to cut
     */
    public void cutToClipboard(XsdNode node) {
        if (node == null) {
            logger.warn("Cannot cut null node");
            return;
        }

        this.clipboardContent = node;
        this.isClipboardCut = true;
        logger.debug("Cut node to clipboard: {}", node.getName());
    }

    /**
     * Gets clipboard content.
     *
     * @return the clipboard node or null if empty
     */
    public XsdNode getClipboardContent() {
        return clipboardContent;
    }

    /**
     * Checks if clipboard has content.
     *
     * @return true if clipboard is not empty
     */
    public boolean hasClipboardContent() {
        return clipboardContent != null;
    }

    /**
     * Checks if clipboard contains a cut operation.
     *
     * @return true if clipboard was cut
     */
    public boolean isClipboardCut() {
        return isClipboardCut && hasClipboardContent();
    }

    /**
     * Clears the clipboard.
     */
    public void clearClipboard() {
        clipboardContent = null;
        isClipboardCut = false;
        logger.debug("Clipboard cleared");
    }

    /**
     * Calculates position of a node in its parent's children list.
     * Optimized to cache the children list reference.
     *
     * @param parent the parent node
     * @param child the child node to find
     * @return the index of the child, -1 if not found
     */
    public int getNodeIndex(XsdNode parent, XsdNode child) {
        if (parent == null || child == null) {
            return -1;
        }

        java.util.List<XsdNode> children = parent.getChildren();
        if (children == null) {
            return -1;
        }

        String childId = child.getId();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).getId().equals(childId)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Checks if a node can be moved up (has previous sibling).
     *
     * @param parent the parent node
     * @param node the node to check
     * @return true if node can move up
     */
    public boolean canMoveUp(XsdNode parent, XsdNode node) {
        int index = getNodeIndex(parent, node);
        return index > 0;
    }

    /**
     * Checks if a node can be moved down (has next sibling).
     *
     * @param parent the parent node
     * @param node the node to check
     * @return true if node can move down
     */
    public boolean canMoveDown(XsdNode parent, XsdNode node) {
        if (parent == null || parent.getChildren() == null) {
            return false;
        }

        int index = getNodeIndex(parent, node);
        return index >= 0 && index < parent.getChildren().size() - 1;
    }

    /**
     * Checks if a node can be deleted.
     *
     * @param node the node to check
     * @return true if node can be deleted
     */
    public boolean canDelete(XsdNode node) {
        // Most nodes can be deleted except schema root
        if (node == null) {
            return false;
        }

        return !(node instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSchema);
    }

    /**
     * Checks if a node can accept children for pasting.
     *
     * @param node the potential parent node
     * @return true if node can accept paste
     */
    public boolean canPasteTo(XsdNode node) {
        if (node == null) {
            return false;
        }

        // Schema nodes can generally accept children
        // Complex types can accept elements
        // Most container nodes can accept children
        return true;
    }

    /**
     * Gets the next sibling of a node.
     *
     * @param parent the parent node
     * @param node the node to find sibling of
     * @return the next sibling or null
     */
    public XsdNode getNextSibling(XsdNode parent, XsdNode node) {
        int index = getNodeIndex(parent, node);
        if (index >= 0 && index < parent.getChildren().size() - 1) {
            return parent.getChildren().get(index + 1);
        }
        return null;
    }

    /**
     * Gets the previous sibling of a node.
     *
     * @param parent the parent node
     * @param node the node to find sibling of
     * @return the previous sibling or null
     */
    public XsdNode getPreviousSibling(XsdNode parent, XsdNode node) {
        int index = getNodeIndex(parent, node);
        if (index > 0) {
            return parent.getChildren().get(index - 1);
        }
        return null;
    }

    /**
     * Logs operation information.
     *
     * @param operationType the operation type (COPY, CUT, PASTE, MOVE, DELETE)
     * @param nodeName the node name
     * @param details additional details
     */
    public void logOperation(String operationType, String nodeName, String details) {
        logger.debug("Node operation {} on '{}': {}", operationType, nodeName, details);
    }
}
