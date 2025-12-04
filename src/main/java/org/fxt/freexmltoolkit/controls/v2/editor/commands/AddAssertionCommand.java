package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to add an XSD 1.1 assertion to an XSD element.
 * <p>
 * Assertions are XPath-based validation rules that allow complex constraints
 * beyond what facets can express. For example: "@price > 0" or "count(item) >= 1".
 * <p>
 * Note: Assertions are an XSD 1.1 feature and may not be supported by all validators.
 * <p>
 * Supports full undo/redo functionality.
 *
 * @since 2.0
 */
public class AddAssertionCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddAssertionCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdElement element;
    private final String assertion;

    /**
     * Creates a new add assertion command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param assertion     the XPath assertion expression to add
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or assertion is empty
     */
    public AddAssertionCommand(XsdEditorContext editorContext, XsdNode node, String assertion) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement)) {
            throw new IllegalArgumentException("Assertions can only be added to elements, not to " +
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
            logger.debug("Adding assertion '{}' to element '{}'", assertion, element.getName());

            element.addAssertion(assertion);
            editorContext.markNodeDirty(element);

            logger.info("Successfully added assertion to element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to add assertion to element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Removing assertion '{}' from element '{}'", assertion, element.getName());

            element.removeAssertion(assertion);
            editorContext.markNodeDirty(element);

            logger.info("Successfully removed assertion from element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to remove assertion from element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        return "Add assertion to " + elementName;
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
     * Gets the assertion expression being added.
     *
     * @return the assertion expression
     */
    public String getAssertion() {
        return assertion;
    }
}
