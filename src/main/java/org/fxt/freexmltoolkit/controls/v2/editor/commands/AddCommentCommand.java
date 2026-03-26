package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComment;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to add a new XML comment to an XSD node.
 * Supports undo by removing the added comment.
 *
 * @since 2.0
 */
public class AddCommentCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddCommentCommand.class);

    private final XsdNode parentNode;
    private final String commentContent;
    private XsdComment addedComment;

    /**
     * Creates a new add comment command.
     *
     * @param parentNode     the parent node to add the comment to
     * @param commentContent the comment text
     */
    public AddCommentCommand(XsdNode parentNode, String commentContent) {
        if (parentNode == null) {
            throw new IllegalArgumentException("Parent node cannot be null");
        }
        if (commentContent == null) {
            throw new IllegalArgumentException("Comment content cannot be null");
        }
        this.parentNode = parentNode;
        this.commentContent = commentContent;
    }

    @Override
    public boolean execute() {
        addedComment = new XsdComment(commentContent);
        parentNode.addChild(addedComment);
        logger.info("Added comment to '{}'", parentNode.getName());
        return true;
    }

    @Override
    public boolean undo() {
        if (addedComment == null) {
            logger.warn("Cannot undo: no comment was added");
            return false;
        }
        parentNode.removeChild(addedComment);
        logger.info("Removed comment from '{}'", parentNode.getName());
        return true;
    }

    @Override
    public String getDescription() {
        String parentName = parentNode.getName() != null ? parentNode.getName() : "(unnamed)";
        return "Add comment to '" + parentName + "'";
    }

    @Override
    public boolean canUndo() {
        return addedComment != null;
    }

    public XsdComment getAddedComment() {
        return addedComment;
    }
}
