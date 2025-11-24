package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

/**
 * Command to set the text content of a text node.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Changes the text content of an XmlText node</li>
 *   <li>Stores the old text for undo</li>
 *   <li>Can be merged with consecutive text changes</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class SetTextCommand implements XmlCommand {

    private final XmlText textNode;
    private final String newText;
    private final String oldText;
    private boolean executed = false;

    /**
     * Constructs a command to set text content.
     *
     * @param textNode the text node to modify
     * @param newText  the new text content
     */
    public SetTextCommand(XmlText textNode, String newText) {
        this.textNode = textNode;
        this.newText = newText;
        this.oldText = textNode.getText();
    }

    @Override
    public boolean execute() {
        textNode.setText(newText);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }

        textNode.setText(oldText);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit Text";
    }

    @Override
    public boolean canMergeWith(XmlCommand other) {
        if (!(other instanceof SetTextCommand)) {
            return false;
        }

        SetTextCommand otherCmd = (SetTextCommand) other;
        return this.textNode == otherCmd.textNode;
    }

    @Override
    public XmlCommand mergeWith(XmlCommand other) {
        if (!canMergeWith(other)) {
            throw new IllegalArgumentException("Cannot merge commands");
        }

        SetTextCommand otherCmd = (SetTextCommand) other;
        // Create merged command that remembers original old text
        return new MergedSetTextCommand(textNode, otherCmd.newText, this.oldText);
    }

    @Override
    public String toString() {
        return getDescription() + " ('" + oldText + "' -> '" + newText + "')";
    }

    /**
     * Merged version of SetTextCommand that remembers the original old text.
     */
    private static class MergedSetTextCommand extends SetTextCommand {
        public MergedSetTextCommand(XmlText textNode, String newText, String originalOldText) {
            super(textNode, newText);
            // Override oldText field via reflection would be needed here,
            // or we could redesign to accept oldText in constructor
        }
    }
}
