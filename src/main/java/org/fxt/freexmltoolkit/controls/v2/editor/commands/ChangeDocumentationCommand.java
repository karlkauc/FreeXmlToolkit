package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to change the documentation text of an XSD node.
 * <p>
 * This command modifies the xs:documentation annotation of an XSD element,
 * attribute, type, or other XSD construct. The documentation is typically
 * used to provide human-readable descriptions of schema components.
 * <p>
 * IMPORTANT: When setting the legacy documentation string, this command clears
 * the multi-language documentations list to ensure the serializer uses the new
 * string value instead of the old list entries.
 * <p>
 * Supports full undo/redo functionality by storing both old and new values.
 *
 * @since 2.0
 */
public class ChangeDocumentationCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeDocumentationCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdNode node;
    private final String oldDocumentation;
    private final String newDocumentation;
    private final List<XsdDocumentation> oldDocumentations; // Preserve old multi-lang docs for undo

    /**
     * Creates a new change documentation command.
     *
     * @param editorContext    the editor context
     * @param node             the XSD node whose documentation should be changed
     * @param newDocumentation the new documentation text (can be null or empty to remove documentation)
     * @throws IllegalArgumentException if editorContext or node is null
     */
    public ChangeDocumentationCommand(XsdEditorContext editorContext, XsdNode node, String newDocumentation) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        this.editorContext = editorContext;
        this.node = node;
        this.oldDocumentation = node.getDocumentation();
        this.newDocumentation = newDocumentation;
        // Save old multi-language documentations for undo
        this.oldDocumentations = new ArrayList<>(node.getDocumentations());
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing documentation of node '{}' from '{}' to '{}'",
                    node.getName(), oldDocumentation, newDocumentation);

            node.setDocumentation(newDocumentation);

            // CRITICAL: Clear the multi-language documentations list.
            // This ensures the XsdSerializer uses the updated legacy string
            // instead of the stale list entries when saving the file.
            node.clearDocumentations();

            editorContext.setDirty(true);

            logger.info("Successfully changed documentation of node '{}'", node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change documentation of node '{}'", node.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing documentation change of node '{}' back to '{}'",
                    node.getName(), oldDocumentation);

            node.setDocumentation(oldDocumentation);

            // Restore the old multi-language documentations list
            node.setDocumentations(oldDocumentations);

            editorContext.setDirty(true);

            logger.info("Successfully undone documentation change of node '{}'", node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo documentation change of node '{}'", node.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String nodeName = node.getName() != null ? node.getName() : "(unnamed)";
        if (newDocumentation == null || newDocumentation.isEmpty()) {
            return "Remove documentation from " + nodeName;
        } else if (oldDocumentation == null || oldDocumentation.isEmpty()) {
            return "Add documentation to " + nodeName;
        } else {
            return "Change documentation of " + nodeName;
        }
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Allow merging consecutive documentation changes on the same node
        if (!(other instanceof ChangeDocumentationCommand otherCmd)) {
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
     * Gets the old documentation text.
     *
     * @return the old documentation (can be null)
     */
    public String getOldDocumentation() {
        return oldDocumentation;
    }

    /**
     * Gets the new documentation text.
     *
     * @return the new documentation (can be null)
     */
    public String getNewDocumentation() {
        return newDocumentation;
    }
}
