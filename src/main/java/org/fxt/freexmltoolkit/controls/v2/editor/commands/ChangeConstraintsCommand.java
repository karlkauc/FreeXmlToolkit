package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change constraints (nillable, abstract, fixed) of an XSD element.
 * <p>
 * This command modifies the constraint properties of an XSD element:
 * <ul>
 *   <li>nillable: allows element content to be nil (xsi:nil="true")</li>
 *   <li>abstract: element cannot be used directly, only through substitution group</li>
 *   <li>fixed: element must have a specific fixed value</li>
 * </ul>
 * <p>
 * Note: These constraints are only applicable to XSD elements, not attributes or types.
 * <p>
 * Supports full undo/redo functionality by storing both old and new values.
 *
 * @since 2.0
 */
public class ChangeConstraintsCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeConstraintsCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdElement element;

    // Old values for undo
    private final boolean oldNillable;
    private final boolean oldAbstract;
    private final String oldFixed;

    // New values for execute
    private final boolean newNillable;
    private final boolean newAbstract;
    private final String newFixed;

    /**
     * Creates a new change constraints command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param nillable      the new nillable flag
     * @param abstractFlag  the new abstract flag
     * @param fixed         the new fixed value (can be null or empty)
     * @throws IllegalArgumentException if editorContext is null, node is null, or node is not an XsdElement
     */
    public ChangeConstraintsCommand(XsdEditorContext editorContext, XsdNode node,
                                   boolean nillable, boolean abstractFlag, String fixed) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement)) {
            throw new IllegalArgumentException("Constraints can only be set on elements, not on " +
                    node.getClass().getSimpleName());
        }

        this.editorContext = editorContext;
        this.element = (XsdElement) node;

        // Store old values for undo
        this.oldNillable = element.isNillable();
        this.oldAbstract = element.isAbstract();
        this.oldFixed = element.getFixed();

        // Store new values for execute
        this.newNillable = nillable;
        this.newAbstract = abstractFlag;
        this.newFixed = (fixed == null || fixed.trim().isEmpty()) ? null : fixed.trim();
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing constraints of element '{}': nillable={}, abstract={}, fixed='{}'",
                    element.getName(), newNillable, newAbstract, newFixed);

            element.setNillable(newNillable);
            element.setAbstract(newAbstract);
            element.setFixed(newFixed);
            editorContext.setDirty(true);

            logger.info("Successfully changed constraints of element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change constraints of element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing constraints change of element '{}': nillable={}, abstract={}, fixed='{}'",
                    element.getName(), oldNillable, oldAbstract, oldFixed);

            element.setNillable(oldNillable);
            element.setAbstract(oldAbstract);
            element.setFixed(oldFixed);
            editorContext.setDirty(true);

            logger.info("Successfully undone constraints change of element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo constraints change of element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        return "Change constraints of " + elementName;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Allow merging consecutive constraint changes on the same element
        if (!(other instanceof ChangeConstraintsCommand otherCmd)) {
            return false;
        }

        // Only merge if it's the same element
        return this.element.getId().equals(otherCmd.element.getId());
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
     * Gets the old nillable value.
     *
     * @return the old nillable flag
     */
    public boolean getOldNillable() {
        return oldNillable;
    }

    /**
     * Gets the old abstract value.
     *
     * @return the old abstract flag
     */
    public boolean getOldAbstract() {
        return oldAbstract;
    }

    /**
     * Gets the old fixed value.
     *
     * @return the old fixed value (can be null)
     */
    public String getOldFixed() {
        return oldFixed;
    }

    /**
     * Gets the new nillable value.
     *
     * @return the new nillable flag
     */
    public boolean getNewNillable() {
        return newNillable;
    }

    /**
     * Gets the new abstract value.
     *
     * @return the new abstract flag
     */
    public boolean getNewAbstract() {
        return newAbstract;
    }

    /**
     * Gets the new fixed value.
     *
     * @return the new fixed value (can be null)
     */
    public String getNewFixed() {
        return newFixed;
    }
}
