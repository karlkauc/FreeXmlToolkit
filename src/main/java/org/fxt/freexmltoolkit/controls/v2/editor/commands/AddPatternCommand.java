package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to add a regex pattern constraint to an XSD element.
 * <p>
 * Patterns define regular expressions that element values must match.
 * For example: "[0-9]{3}-[0-9]{2}-[0-9]{4}" for US Social Security numbers.
 * <p>
 * Supports full undo/redo functionality.
 *
 * @since 2.0
 */
public class AddPatternCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddPatternCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdElement element;
    private final String pattern;

    /**
     * Creates a new add pattern command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param pattern       the regex pattern to add
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or pattern is empty
     */
    public AddPatternCommand(XsdEditorContext editorContext, XsdNode node, String pattern) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement)) {
            throw new IllegalArgumentException("Patterns can only be added to elements, not to " +
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
            logger.debug("Adding pattern '{}' to element '{}'", pattern, element.getName());

            element.addPattern(pattern);
            editorContext.markNodeDirty(element);

            logger.info("Successfully added pattern to element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to add pattern to element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Removing pattern '{}' from element '{}'", pattern, element.getName());

            element.removePattern(pattern);
            editorContext.markNodeDirty(element);

            logger.info("Successfully removed pattern from element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to remove pattern from element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        return "Add pattern to " + elementName;
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
     * Gets the pattern being added.
     *
     * @return the pattern
     */
    public String getPattern() {
        return pattern;
    }
}
