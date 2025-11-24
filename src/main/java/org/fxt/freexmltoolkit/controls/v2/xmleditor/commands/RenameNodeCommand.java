package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;

/**
 * Command to rename an element.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Changes the name of an XmlElement</li>
 *   <li>Stores the old name for undo</li>
 *   <li>Can be merged with consecutive rename operations</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class RenameNodeCommand implements XmlCommand {

    private final XmlElement element;
    private final String newName;
    private final String oldName;
    private boolean executed = false;

    /**
     * Constructs a command to rename an element.
     *
     * @param element the element to rename
     * @param newName the new name
     */
    public RenameNodeCommand(XmlElement element, String newName) {
        this.element = element;
        this.newName = newName;
        this.oldName = element.getName();
    }

    @Override
    public boolean execute() {
        element.setName(newName);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }

        element.setName(oldName);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Rename Element";
    }

    @Override
    public boolean canMergeWith(XmlCommand other) {
        if (!(other instanceof RenameNodeCommand)) {
            return false;
        }

        RenameNodeCommand otherCmd = (RenameNodeCommand) other;
        return this.element == otherCmd.element;
    }

    @Override
    public XmlCommand mergeWith(XmlCommand other) {
        if (!canMergeWith(other)) {
            throw new IllegalArgumentException("Cannot merge commands");
        }

        RenameNodeCommand otherCmd = (RenameNodeCommand) other;
        // Create new command that remembers original old name
        return new MergedRenameCommand(element, otherCmd.newName, this.oldName);
    }

    @Override
    public String toString() {
        return getDescription() + " ('" + oldName + "' -> '" + newName + "')";
    }

    /**
     * Merged version that preserves the original old name.
     */
    private static class MergedRenameCommand extends RenameNodeCommand {
        private final XmlElement mergedElement;
        private final String originalOldName;

        public MergedRenameCommand(XmlElement element, String newName, String originalOldName) {
            super(element, newName);
            this.mergedElement = element;
            this.originalOldName = originalOldName;
        }

        @Override
        public boolean undo() {
            if (!super.executed) {
                return false;
            }

            mergedElement.setName(originalOldName);
            super.executed = false;
            return true;
        }
    }
}
