package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComment;

/**
 * Command to edit the content of an existing XML comment.
 * Supports undo and merging of consecutive edits on the same comment.
 *
 * @since 2.0
 */
public class EditCommentCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(EditCommentCommand.class);

    private final XsdComment comment;
    private final String oldContent;
    private final String newContent;

    /**
     * Creates a new edit comment command.
     *
     * @param comment    the comment to edit
     * @param newContent the new comment text
     */
    public EditCommentCommand(XsdComment comment, String newContent) {
        if (comment == null) {
            throw new IllegalArgumentException("Comment cannot be null");
        }
        if (newContent == null) {
            throw new IllegalArgumentException("New content cannot be null");
        }
        this.comment = comment;
        this.oldContent = comment.getContent();
        this.newContent = newContent;
    }

    @Override
    public boolean execute() {
        comment.setContent(newContent);
        logger.info("Changed comment content");
        return true;
    }

    @Override
    public boolean undo() {
        comment.setContent(oldContent);
        logger.info("Restored comment content");
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit comment";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        if (!(other instanceof EditCommentCommand otherCmd)) {
            return false;
        }
        return this.comment.getId().equals(otherCmd.comment.getId());
    }

    @Override
    public XsdCommand mergeWith(XsdCommand other) {
        if (!(other instanceof EditCommentCommand otherCmd)) {
            throw new IllegalArgumentException("Cannot merge with non-EditCommentCommand");
        }
        return new EditCommentCommand(this.comment, otherCmd.newContent);
    }

    public XsdComment getComment() {
        return comment;
    }

    public String getOldContent() {
        return oldContent;
    }

    public String getNewContent() {
        return newContent;
    }
}
