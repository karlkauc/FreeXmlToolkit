package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the type reference of an XSD element or attribute.
 * Supports undo by restoring the original type.
 * <p>
 * Guarded by {@link XsdEditRules#changeType}: it refuses (returns {@code false}) a type on a
 * reference and a type next to an inline {@code xs:complexType}/{@code xs:simpleType}. The
 * {@link #replacingInlineType} variant removes that inline type and sets the type in one
 * undoable step (after the user agreed to drop the inline definition).
 * <p>
 * Updates the underlying XsdNode model, which automatically triggers
 * view refresh via PropertyChangeListener (Phase 2.3).
 *
 * @since 2.0
 */
public class ChangeTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeTypeCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdNode node;
    private final String oldType;
    private final String newType;
    private final boolean replaceInlineType;
    private final java.util.List<XsdNode> removedInline = new java.util.ArrayList<>();
    private final java.util.List<Integer> removedIndices = new java.util.ArrayList<>();

    /**
     * Creates a new change type command.
     *
     * @param editorContext the editor context
     * @param node          the node whose type to change (must be XsdElement or XsdAttribute)
     * @param newType       the new type reference (e.g., "xs:string", "MyCustomType", or null/empty to remove)
     */
    public ChangeTypeCommand(XsdEditorContext editorContext, XsdNode node, String newType) {
        this(editorContext, node, newType, false);
    }

    /**
     * A type change that first removes the node's inline type definition, for the case
     * {@link XsdEditRules#changeType} reports as {@link EditCheck.Status#REPLACEABLE}.
     */
    public static ChangeTypeCommand replacingInlineType(XsdEditorContext editorContext, XsdNode node, String newType) {
        return new ChangeTypeCommand(editorContext, node, newType, true);
    }

    private ChangeTypeCommand(XsdEditorContext editorContext, XsdNode node, String newType, boolean replaceInlineType) {
        CommandValidation.requireEditorContextAndNode(editorContext, node);
        CommandValidation.requireNodeTypeOneOf(node, "Change type", XsdElement.class, XsdAttribute.class);

        this.editorContext = editorContext;
        this.node = node;
        this.oldType = getTypeFromNode(node);
        this.newType = (newType == null || newType.trim().isEmpty()) ? null : newType.trim();
        this.replaceInlineType = replaceInlineType;
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing type of {} '{}' from '{}' to '{}'",
                    node.getClass().getSimpleName(), node.getName(), oldType, newType);

            EditCheck check = XsdEditRules.changeType(node, newType);
            if (check.isBlocked() || (check.needsConfirmation() && !replaceInlineType)) {
                logger.warn("Refused type change of '{}': {}", node.getName(), check.reason());
                return false;
            }
            removedInline.clear();
            removedIndices.clear();
            if (check.needsConfirmation()) {
                for (XsdNode child : java.util.List.copyOf(node.getChildren())) {
                    if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType
                            || child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType) {
                        removedIndices.add(node.getChildren().indexOf(child));
                        removedInline.add(child);
                        node.removeChild(child);
                    }
                }
            }

            // Update the model - this will fire PropertyChangeEvent
            // which triggers automatic view refresh via VisualNode's listener
            setTypeOnNode(node, newType);
            editorContext.markNodeDirty(node);

            logger.info("Successfully changed type of {} '{}'", node.getClass().getSimpleName(), node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change type of {} '{}'", node.getClass().getSimpleName(), node.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing type change of {} '{}' back to '{}'",
                    node.getClass().getSimpleName(), node.getName(), oldType);

            // Update the model - this will fire PropertyChangeEvent
            setTypeOnNode(node, oldType);
            for (int i = 0; i < removedInline.size(); i++) {
                int index = Math.min(removedIndices.get(i), node.getChildren().size());
                node.addChild(index, removedInline.get(i));
            }
            editorContext.markNodeDirty(node);

            logger.info("Successfully undone type change of {} '{}'", node.getClass().getSimpleName(), node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo type change of {} '{}'", node.getClass().getSimpleName(), node.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String nodeName = node.getName() != null ? node.getName() : "(unnamed)";
        String nodeType = node instanceof XsdElement ? "element" : "attribute";

        if (replaceInlineType) {
            return "Replace inline type of " + nodeType + " " + nodeName + " with " + newType;
        } else if (newType == null || newType.isEmpty()) {
            return "Remove type from " + nodeType + " " + nodeName;
        } else if (oldType == null || oldType.isEmpty()) {
            return "Set type of " + nodeType + " " + nodeName + " to " + newType;
        } else {
            return "Change type of " + nodeType + " " + nodeName + " from " + oldType + " to " + newType;
        }
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Consecutive type changes on the same node can be merged
        if (!(other instanceof ChangeTypeCommand otherChange)
                || replaceInlineType || otherChange.replaceInlineType) {
            return false;
        }

        return this.node.getId().equals(otherChange.node.getId()) &&
                java.util.Objects.equals(this.newType, otherChange.oldType);
    }

    @Override
    public XsdCommand mergeWith(XsdCommand other) {
        return new XsdMergedCommand(this, other);
    }

    /**
     * Gets the node being modified.
     *
     * @return the node
     */
    public XsdNode getNode() {
        return node;
    }

    /**
     * Gets the old type.
     *
     * @return the old type
     */
    public String getOldType() {
        return oldType;
    }

    /**
     * Gets the new type.
     *
     * @return the new type
     */
    public String getNewType() {
        return newType;
    }

    /**
     * Gets the type from a node (supports XsdElement and XsdAttribute).
     *
     * @param node the node
     * @return the type, or null if not set
     */
    private String getTypeFromNode(XsdNode node) {
        if (node instanceof XsdElement element) {
            return element.getType();
        } else if (node instanceof XsdAttribute attribute) {
            return attribute.getType();
        }
        return null;
    }

    /**
     * Sets the type on a node (supports XsdElement and XsdAttribute).
     *
     * @param node the node
     * @param type the type to set
     */
    private void setTypeOnNode(XsdNode node, String type) {
        if (node instanceof XsdElement element) {
            element.setType(type);
        } else if (node instanceof XsdAttribute attribute) {
            attribute.setType(type);
        }
    }
}
