package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the application information (appinfo) of an XSD node.
 * <p>
 * This command modifies the xs:appinfo annotation of an XSD element,
 * attribute, type, or other XSD construct. The appinfo is typically
 * used to provide machine-readable information for tools and applications.
 * <p>
 * Supports full undo/redo functionality by storing both old and new values.
 *
 * @since 2.0
 */
public class ChangeAppinfoCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeAppinfoCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdNode node;
    private final String oldAppinfo;
    private final String newAppinfo;

    /**
     * Creates a new change appinfo command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node whose appinfo should be changed
     * @param newAppinfo    the new appinfo text (can be null or empty to remove appinfo)
     * @throws IllegalArgumentException if editorContext or node is null
     */
    public ChangeAppinfoCommand(XsdEditorContext editorContext, XsdNode node, String newAppinfo) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        this.editorContext = editorContext;
        this.node = node;
        this.oldAppinfo = node.getAppinfo();
        this.newAppinfo = newAppinfo;
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing appinfo of node '{}' from '{}' to '{}'",
                    node.getName(), oldAppinfo, newAppinfo);

            node.setAppinfo(newAppinfo);
            editorContext.setDirty(true);

            logger.info("Successfully changed appinfo of node '{}'", node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change appinfo of node '{}'", node.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing appinfo change of node '{}' back to '{}'",
                    node.getName(), oldAppinfo);

            node.setAppinfo(oldAppinfo);
            editorContext.setDirty(true);

            logger.info("Successfully undone appinfo change of node '{}'", node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo appinfo change of node '{}'", node.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String nodeName = node.getName() != null ? node.getName() : "(unnamed)";
        if (newAppinfo == null || newAppinfo.isEmpty()) {
            return "Remove appinfo from " + nodeName;
        } else if (oldAppinfo == null || oldAppinfo.isEmpty()) {
            return "Add appinfo to " + nodeName;
        } else {
            return "Change appinfo of " + nodeName;
        }
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Allow merging consecutive appinfo changes on the same node
        if (!(other instanceof ChangeAppinfoCommand otherCmd)) {
            return false;
        }

        // Only merge if it's the same node
        return this.node.getId().equals(otherCmd.node.getId());
    }

    /**
     * Gets the node being modified.
     *
     * @return the XSD node
     */
    public XsdNode getNode() {
        return node;
    }

    /**
     * Gets the old appinfo text.
     *
     * @return the old appinfo (can be null)
     */
    public String getOldAppinfo() {
        return oldAppinfo;
    }

    /**
     * Gets the new appinfo text.
     *
     * @return the new appinfo (can be null)
     */
    public String getNewAppinfo() {
        return newAppinfo;
    }
}
