package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Command to paste a node from the clipboard to a target parent.
 * Creates a deep copy of the clipboard content and adds it to the parent.
 * <p>
 * For cut operations, also removes the original node from its parent.
 * <p>
 * Supports undo by removing the pasted node (and restoring the original for cut).
 *
 * @since 2.0
 */
public class PasteNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(PasteNodeCommand.class);

    private final XsdClipboard clipboard;
    private final XsdNode targetParent;

    private XsdNode pastedNode;
    private XsdNode actualParent;

    // For cut operations - store info to restore original on undo
    private boolean wasCut;
    private XsdNode cutOriginalNode;
    private XsdNode cutOriginalParent;
    private int cutOriginalIndex;

    /**
     * Creates a new paste command.
     *
     * @param clipboard    the clipboard containing the node to paste
     * @param targetParent the parent to paste into
     */
    public PasteNodeCommand(XsdClipboard clipboard, XsdNode targetParent) {
        if (clipboard == null) {
            throw new IllegalArgumentException("Clipboard cannot be null");
        }
        if (targetParent == null) {
            throw new IllegalArgumentException("Target parent cannot be null");
        }
        if (!clipboard.hasContent()) {
            throw new IllegalArgumentException("Clipboard is empty");
        }

        this.clipboard = clipboard;
        this.targetParent = targetParent;
        this.wasCut = clipboard.isCut();

        // For cut operations, store original node info for undo
        if (wasCut) {
            this.cutOriginalNode = clipboard.getClipboardNode();
            this.cutOriginalParent = cutOriginalNode.getParent();
            this.cutOriginalIndex = cutOriginalParent != null
                    ? cutOriginalParent.getChildren().indexOf(cutOriginalNode)
                    : -1;
        }
    }

    @Override
    public boolean execute() {
        // Create deep copy from clipboard
        pastedNode = clipboard.paste();
        if (pastedNode == null) {
            logger.warn("Failed to create paste copy from clipboard");
            return false;
        }

        // Find the correct parent to add to (same logic as AddElementCommand)
        actualParent = findTargetParent(targetParent);
        actualParent.addChild(pastedNode);

        logger.info("Pasted node '{}' to '{}'", pastedNode.getName(), actualParent.getName());

        // For cut operations, remove the original node
        if (wasCut && cutOriginalNode != null && cutOriginalParent != null) {
            cutOriginalParent.removeChild(cutOriginalNode);
            clipboard.clear(); // Clear clipboard after cut-paste completes
            logger.info("Removed cut original node '{}' from '{}'",
                    cutOriginalNode.getName(), cutOriginalParent.getName());
        }

        return true;
    }

    /**
     * Finds the correct parent node to add the new element to.
     * If the parent element has a complexType with a compositor (sequence/choice/all),
     * returns the compositor. Otherwise returns the parent itself.
     */
    private XsdNode findTargetParent(XsdNode parent) {
        // First, check if parent is an Element that references a ComplexType
        if (parent instanceof XsdElement element) {
            String typeName = element.getType();
            if (typeName != null && !typeName.isEmpty() && !typeName.startsWith("xs:")) {
                // Element references a custom type - find it in schema
                XsdComplexType referencedType = findComplexTypeInSchema(element, typeName);
                if (referencedType != null) {
                    // Search for compositor in the referenced ComplexType
                    for (XsdNode complexTypeChild : referencedType.getChildren()) {
                        if (complexTypeChild instanceof XsdSequence ||
                                complexTypeChild instanceof XsdChoice ||
                                complexTypeChild instanceof XsdAll) {
                            return complexTypeChild;
                        }
                    }
                }
            }
        }

        // Check if parent has children (inline complexType, sequence, etc.)
        for (XsdNode child : parent.getChildren()) {
            // If it's a complexType, look for compositor inside
            if (child instanceof XsdComplexType) {
                for (XsdNode complexTypeChild : child.getChildren()) {
                    if (complexTypeChild instanceof XsdSequence ||
                            complexTypeChild instanceof XsdChoice ||
                            complexTypeChild instanceof XsdAll) {
                        return complexTypeChild;
                    }
                }
            }
            // If it's directly a compositor
            else if (child instanceof XsdSequence ||
                    child instanceof XsdChoice ||
                    child instanceof XsdAll) {
                return child;
            }
        }

        // No compositor found, add directly to parent
        return parent;
    }

    /**
     * Finds a ComplexType by name in the schema.
     */
    private XsdComplexType findComplexTypeInSchema(XsdElement element, String typeName) {
        XsdNode current = element.getParent();
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }

        if (current instanceof XsdSchema schema) {
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType complexType) {
                    if (typeName.equals(complexType.getName())) {
                        return complexType;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public boolean undo() {
        if (pastedNode == null || actualParent == null) {
            logger.warn("Cannot undo: no node was pasted");
            return false;
        }

        // Remove the pasted node
        actualParent.removeChild(pastedNode);
        logger.info("Removed pasted node '{}' from '{}'", pastedNode.getName(), actualParent.getName());

        // For cut operations, restore the original node
        if (wasCut && cutOriginalNode != null && cutOriginalParent != null) {
            if (cutOriginalIndex >= 0 && cutOriginalIndex <= cutOriginalParent.getChildren().size()) {
                cutOriginalParent.addChild(cutOriginalIndex, cutOriginalNode);
            } else {
                cutOriginalParent.addChild(cutOriginalNode);
            }
            // Restore the cut node to clipboard so it can be pasted again
            clipboard.cut(cutOriginalNode);
            logger.info("Restored cut original node '{}' to '{}'",
                    cutOriginalNode.getName(), cutOriginalParent.getName());
        }

        return true;
    }

    @Override
    public String getDescription() {
        String action = wasCut ? "Cut and paste" : "Paste";
        return action + " node to '" + targetParent.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return pastedNode != null;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return false;
    }

    /**
     * Gets the pasted node (after execute() has been called).
     *
     * @return the pasted node, or null if not yet executed
     */
    public XsdNode getPastedNode() {
        return pastedNode;
    }
}
