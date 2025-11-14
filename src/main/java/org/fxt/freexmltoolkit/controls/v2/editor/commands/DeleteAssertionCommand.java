package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to delete an XSD 1.1 assertion from an XSD element.
 * <p>
 * Supports full undo/redo functionality by storing the deleted assertion.
 *
 * @since 2.0
 */
public class DeleteAssertionCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteAssertionCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdElement element;
    private final String assertion;

    /**
     * Creates a new delete assertion command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param assertion     the assertion to delete
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or assertion is empty
     */
    public DeleteAssertionCommand(XsdEditorContext editorContext, XsdNode node, String assertion) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement)) {
            throw new IllegalArgumentException("Assertions can only be deleted from elements, not from " +
                    node.getClass().getSimpleName());
        }
        if (assertion == null || assertion.trim().isEmpty()) {
            throw new IllegalArgumentException("Assertion expression cannot be null or empty");
        }

        this.editorContext = editorContext;
        this.element = (XsdElement) node;
        this.assertion = assertion.trim();
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Deleting assertion '{}' from element '{}'", assertion, element.getName());

            boolean removed = element.removeAssertion(assertion);
            if (!removed) {
                logger.warn("Assertion '{}' not found in element '{}'", assertion, element.getName());
                return false;
            }

            editorContext.setDirty(true);

            logger.info("Successfully deleted assertion from element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to delete assertion from element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Re-adding assertion '{}' to element '{}'", assertion, element.getName());

            element.addAssertion(assertion);
            editorContext.setDirty(true);

            logger.info("Successfully restored assertion to element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to restore assertion to element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        return "Delete assertion from " + elementName;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Assertion commands should not be merged
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
     * Gets the assertion being deleted.
     *
     * @return the assertion expression
     */
    public String getAssertion() {
        return assertion;
    }
}
