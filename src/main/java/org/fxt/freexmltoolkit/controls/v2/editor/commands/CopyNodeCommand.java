package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to insert a deep copy of a node at an exact position (parent + index), as a drag with
 * the copy modifier does. Unlike {@link PasteNodeCommand} the parent is used as given (no
 * compositor lookup) and the copy is made once, so redo re-inserts the same node.
 * <p>
 * The copy keeps the original name unless a sibling in the new parent already has it; then it
 * gets the {@code _copy} suffix.
 *
 * @since 2.0
 */
public class CopyNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(CopyNodeCommand.class);

    private final XsdNode original;
    private final XsdNode newParent;
    private final int newIndex;
    private final XsdNode copy;

    /**
     * Creates a new copy command.
     *
     * @param original  the node to copy
     * @param newParent the parent the copy is inserted into
     * @param newIndex  the index in the new parent's children list (-1 to append at end)
     */
    public CopyNodeCommand(XsdNode original, XsdNode newParent, int newIndex) {
        if (original == null) {
            throw new IllegalArgumentException("Node to copy cannot be null");
        }
        if (newParent == null) {
            throw new IllegalArgumentException("New parent cannot be null");
        }
        this.original = original;
        this.newParent = newParent;
        this.newIndex = newIndex;
        this.copy = original.deepCopy(nameTaken(newParent, original.getName()) ? "_copy" : null);
    }

    private static boolean nameTaken(XsdNode parent, String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (XsdNode child : parent.getChildren()) {
            if (name.equals(child.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean execute() {
        // Never put content under a simple-typed or referencing element
        if ((copy instanceof XsdElement || copy instanceof XsdAttribute)
                && XsdEditRules.contentConflict(newParent).isBlocked()) {
            logger.warn("Refused copy of '{}': {}", original.getName(),
                    XsdEditRules.contentConflict(newParent).reason());
            return false;
        }
        if (newIndex >= 0 && newIndex <= newParent.getChildren().size()) {
            newParent.addChild(newIndex, copy);
        } else {
            newParent.addChild(copy);
        }
        logger.info("Copied node '{}' to '{}' at index {}", original.getName(), newParent.getName(), newIndex);
        return true;
    }

    @Override
    public boolean undo() {
        newParent.removeChild(copy);
        logger.info("Removed copy '{}' from '{}'", copy.getName(), newParent.getName());
        return true;
    }

    @Override
    public String getDescription() {
        return "Copy '" + original.getName() + "' to '" + newParent.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return false;
    }

    /**
     * @return the inserted copy (the same node across redo)
     */
    public XsdNode getCopy() {
        return copy;
    }
}
