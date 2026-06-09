package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;

/** Command to set the text of a comment node, with undo. @since 2.0 */
public class SetCommentTextCommand implements XmlCommand {

    private final XmlComment comment;
    private final String newText;
    private final String oldText;
    private boolean executed = false;

    public SetCommentTextCommand(XmlComment comment, String newText) {
        this.comment = comment;
        this.newText = newText;
        this.oldText = comment.getText();
    }

    @Override
    public boolean execute() {
        comment.setText(newText);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        comment.setText(oldText);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit Comment";
    }
}
