package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Abstract base class for constraint commands (pattern, enumeration, assertion).
 * <p>
 * Eliminates code duplication across similar constraint manipulation commands by
 * implementing the template method pattern. Subclasses only need to implement
 * three methods to define their specific constraint behavior.
 * <p>
 * Supports full undo/redo functionality for all constraint types.
 *
 * @since 2.0
 */
public abstract class AbstractConstraintCommand implements XsdCommand {

    /** Logger for this class. */
    protected static final Logger logger = LogManager.getLogger(AbstractConstraintCommand.class);

    /** The editor context. */
    protected final XsdEditorContext editorContext;
    /** The target XSD element. */
    protected final XsdElement element;
    /** The constraint value. */
    protected final String value;

    /**
     * Creates a new constraint command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param value         the constraint value to add/remove
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or value is empty
     */
    protected AbstractConstraintCommand(XsdEditorContext editorContext, XsdNode node, String value) {
        CommandValidation.requireEditorContextAndNode(editorContext, node);
        CommandValidation.requireNodeType(node, XsdElement.class,
            getConstraintTypeName() + " can only be added to elements");
        CommandValidation.requireNonEmpty(value, getConstraintTypeName() + " value");

        this.editorContext = editorContext;
        this.element = (XsdElement) node;
        this.value = value.trim();
    }

    /**
     * Returns the human-readable name of the constraint type.
     * Examples: "pattern", "enumeration", "assertion"
     *
     * @return the constraint type name
     */
    protected abstract String getConstraintTypeName();

    /**
     * Returns the action verb for the description (e.g., "Add" or "Delete").
     * Default implementation returns "Add", override for delete commands.
     *
     * @return the action verb for descriptions
     */
    protected String getActionVerb() {
        return "Add";
    }

    /**
     * Performs the main action of this command during execute().
     * Subclasses implement this to define what happens when the command executes.
     *
     * @param value the constraint value to operate on
     * @return true if the action succeeded, false if it failed (e.g., constraint not found for Delete)
     */
    protected abstract boolean performAction(String value);

    /**
     * Performs the undo action, reversing the effect of performAction().
     *
     * @param value the constraint value to operate on
     * @return true if the undo succeeded, false if the value was not found
     */
    protected abstract boolean performUndoAction(String value);

    @Override
    public boolean execute() {
        try {
            logger.debug("{} {} '{}' on element '{}'", getActionVerb(), getConstraintTypeName(), value, element.getName());

            boolean succeeded = performAction(value);
            if (!succeeded) {
                logger.warn("{} '{}' not found in element '{}'", getConstraintTypeName(), value, element.getName());
                return false;
            }

            editorContext.markNodeDirty(element);

            logger.info("Successfully {} {} on element '{}'", getActionVerb().toLowerCase(), getConstraintTypeName(), element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to {} {} on element '{}'", getActionVerb().toLowerCase(), getConstraintTypeName(), element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Reversing {} of {} '{}' on element '{}'", getActionVerb().toLowerCase(), getConstraintTypeName(), value, element.getName());

            boolean succeeded = performUndoAction(value);
            if (!succeeded) {
                logger.warn("{} '{}' not found in element '{}' during undo", getConstraintTypeName(), value, element.getName());
                return false;
            }

            editorContext.markNodeDirty(element);

            logger.info("Successfully reversed {} of {} on element '{}'", getActionVerb().toLowerCase(), getConstraintTypeName(), element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to reverse {} of {} on element '{}'", getActionVerb().toLowerCase(), getConstraintTypeName(), element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        return getActionVerb() + " " + getConstraintTypeName() + (getActionVerb().equals("Add") ? " to " : " from ") + elementName;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Constraint commands should not be merged
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
     * Gets the constraint value.
     *
     * @return the constraint value
     */
    public String getValue() {
        return value;
    }
}
