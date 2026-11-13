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

    /** A merged command: already applied, spanning the first command's old text to the last's new text. */
    private SetTextCommand(XmlText textNode, String oldText, String newText, boolean executed) {
        this.textNode = textNode;
        this.newText = newText;
        this.oldText = oldText;
        this.executed = executed;
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
        // Both edits are already applied: the merged step goes from the first old text to the last new
        // text and is itself "executed", or undo would refuse it
        return new SetTextCommand(textNode, this.oldText, otherCmd.newText, true);
    }

    @Override
    public String toString() {
        return getDescription() + " ('" + oldText + "' -> '" + newText + "')";
    }
}
