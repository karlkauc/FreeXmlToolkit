package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to change the complete list of multi-language documentations of an XSD node.
 * <p>
 * This command manages multiple xs:documentation elements with optional xml:lang attributes.
 * Unlike {@link ChangeDocumentationCommand} which manages a single legacy documentation string,
 * this command operates on the full {@code List<XsdDocumentation>}.
 * <p>
 * Each XsdDocumentation entry can have:
 * <ul>
 *   <li>text: The documentation content</li>
 *   <li>lang: Optional xml:lang attribute (empty/null = no attribute)</li>
 *   <li>source: Optional source attribute</li>
 * </ul>
 * <p>
 * When executed, this command:
 * <ul>
 *   <li>Replaces the entire documentations list</li>
 *   <li>Clears the legacy documentation string (ensures serializer uses the new list)</li>
 *   <li>Marks the editor context as dirty</li>
 * </ul>
 * <p>
 * Supports full undo/redo functionality by storing both old and new lists.
 *
 * @since 2.0
 */
public class ChangeDocumentationsCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeDocumentationsCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdNode node;
    private final List<XsdDocumentation> oldDocumentations;
    private final List<XsdDocumentation> newDocumentations;
    private final String oldLegacyDocumentation;

    /**
     * Creates a new change documentations command.
     *
     * @param editorContext      the editor context
     * @param node               the XSD node whose documentations should be changed
     * @param newDocumentations  the new list of documentations (can be empty but not null)
     * @throws IllegalArgumentException if editorContext, node, or newDocumentations is null
     */
    public ChangeDocumentationsCommand(XsdEditorContext editorContext, XsdNode node, List<XsdDocumentation> newDocumentations) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (newDocumentations == null) {
            throw new IllegalArgumentException("New documentations list cannot be null (use empty list instead)");
        }

        this.editorContext = editorContext;
        this.node = node;
        this.oldDocumentations = new ArrayList<>(node.getDocumentations());
        this.newDocumentations = new ArrayList<>(newDocumentations);
        this.oldLegacyDocumentation = node.getDocumentation();
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing documentations of node '{}': {} old entries -> {} new entries",
                    node.getName(), oldDocumentations.size(), newDocumentations.size());

            // Set the new documentations list
            node.setDocumentations(newDocumentations);

            // CRITICAL: Clear the legacy documentation string.
            // This ensures the XsdSerializer uses the new documentations list
            // instead of the stale legacy string when saving the file.
            node.setDocumentation(null);

            editorContext.markNodeDirty(node);

            logger.info("Successfully changed documentations of node '{}'", node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change documentations of node '{}'", node.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing documentations change of node '{}': {} entries -> {} entries",
                    node.getName(), newDocumentations.size(), oldDocumentations.size());

            // Restore the old documentations list
            node.setDocumentations(oldDocumentations);

            // Restore the old legacy documentation string
            node.setDocumentation(oldLegacyDocumentation);

            editorContext.markNodeDirty(node);

            logger.info("Successfully undone documentations change of node '{}'", node.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo documentations change of node '{}'", node.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String nodeName = node.getName() != null ? node.getName() : "(unnamed)";
        int newCount = newDocumentations.size();
        int oldCount = oldDocumentations.size();

        if (newCount == 0 && oldCount > 0) {
            return "Remove all documentations from " + nodeName;
        } else if (newCount > 0 && oldCount == 0) {
            return "Add " + newCount + " documentation(s) to " + nodeName;
        } else if (newCount != oldCount) {
            return "Change documentations of " + nodeName + " (" + oldCount + " -> " + newCount + ")";
        } else {
            return "Edit documentations of " + nodeName;
        }
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Allow merging consecutive documentation changes on the same node
        if (!(other instanceof ChangeDocumentationsCommand otherCmd)) {
            return false;
        }

        // Only merge if it's the same node
        return this.node.getId().equals(otherCmd.node.getId());
    }

    @Override
    public XsdCommand mergeWith(XsdCommand other) {
        if (!canMergeWith(other)) {
            return null;
        }

        ChangeDocumentationsCommand otherCmd = (ChangeDocumentationsCommand) other;

        // Create merged command: keep the old state from this command,
        // but use the new state from the other command
        return new ChangeDocumentationsCommand(editorContext, node, otherCmd.newDocumentations);
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
     * Gets the old documentations list.
     *
     * @return immutable copy of the old documentations list
     */
    public List<XsdDocumentation> getOldDocumentations() {
        return new ArrayList<>(oldDocumentations);
    }

    /**
     * Gets the new documentations list.
     *
     * @return immutable copy of the new documentations list
     */
    public List<XsdDocumentation> getNewDocumentations() {
        return new ArrayList<>(newDocumentations);
    }

    /**
     * Gets the old legacy documentation string.
     *
     * @return the old legacy documentation (can be null)
     */
    public String getOldLegacyDocumentation() {
        return oldLegacyDocumentation;
    }
}
