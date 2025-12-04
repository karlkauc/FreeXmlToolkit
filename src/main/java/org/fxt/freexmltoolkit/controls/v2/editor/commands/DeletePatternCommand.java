package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to delete a regex pattern constraint from an XSD element.
 * <p>
 * Supports full undo/redo functionality by storing the deleted pattern.
 *
 * @since 2.0
 */
public class DeletePatternCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeletePatternCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdElement element;
    private final String pattern;

    /**
     * Creates a new delete pattern command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param pattern       the pattern to delete
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or pattern is empty
     */
    public DeletePatternCommand(XsdEditorContext editorContext, XsdNode node, String pattern) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement)) {
            throw new IllegalArgumentException("Patterns can only be deleted from elements, not from " +
                    node.getClass().getSimpleName());
        }
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty");
        }

        this.editorContext = editorContext;
        this.element = (XsdElement) node;
        this.pattern = pattern.trim();
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Deleting pattern '{}' from element '{}'", pattern, element.getName());

            boolean removed = element.removePattern(pattern);
            if (!removed) {
                logger.warn("Pattern '{}' not found in element '{}'", pattern, element.getName());
                return false;
            }

            editorContext.markNodeDirty(element);

            logger.info("Successfully deleted pattern from element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to delete pattern from element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Re-adding pattern '{}' to element '{}'", pattern, element.getName());

            element.addPattern(pattern);
            editorContext.markNodeDirty(element);

            logger.info("Successfully restored pattern to element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to restore pattern to element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        return "Delete pattern from " + elementName;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Pattern commands should not be merged
        return false;
    }

    /**
     * Gets the element being modified.
     *
     * @return the XSD element
     */
    public XsdElement getElement() {
        return element;
    }

    /**
     * Gets the pattern being deleted.
     *
     * @return the pattern
     */
    public String getPattern() {
        return pattern;
    }
}
