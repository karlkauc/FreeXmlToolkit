package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to change the substitution group of an XSD element.
 * <p>
 * The substitutionGroup attribute allows an element to be substituted for another
 * element in instance documents. The element must have the same type or a derived
 * type from the head element of the substitution group.
 * <p>
 * Note: Substitution groups are only applicable to global elements, not attributes.
 * <p>
 * Supports full undo/redo functionality by storing both old and new values.
 *
 * @since 2.0
 */
public class ChangeSubstitutionGroupCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ChangeSubstitutionGroupCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdElement element;
    private final String oldSubstitutionGroup;
    private final String newSubstitutionGroup;

    /**
     * Creates a new change substitution group command.
     *
     * @param editorContext        the editor context
     * @param node                 the XSD node (must be an XsdElement)
     * @param newSubstitutionGroup the new substitution group name (can be null or empty to remove)
     * @throws IllegalArgumentException if editorContext is null, node is null, or node is not an XsdElement
     */
    public ChangeSubstitutionGroupCommand(XsdEditorContext editorContext, XsdNode node, String newSubstitutionGroup) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement)) {
            throw new IllegalArgumentException("Substitution group can only be set on elements, not on " +
                    node.getClass().getSimpleName());
        }

        this.editorContext = editorContext;
        this.element = (XsdElement) node;
        this.oldSubstitutionGroup = element.getSubstitutionGroup();
        this.newSubstitutionGroup = (newSubstitutionGroup == null || newSubstitutionGroup.trim().isEmpty())
                ? null
                : newSubstitutionGroup.trim();
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Changing substitution group of element '{}' from '{}' to '{}'",
                    element.getName(), oldSubstitutionGroup, newSubstitutionGroup);

            element.setSubstitutionGroup(newSubstitutionGroup);
            editorContext.markNodeDirty(element);

            logger.info("Successfully changed substitution group of element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to change substitution group of element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Undoing substitution group change of element '{}' back to '{}'",
                    element.getName(), oldSubstitutionGroup);

            element.setSubstitutionGroup(oldSubstitutionGroup);
            editorContext.markNodeDirty(element);

            logger.info("Successfully undone substitution group change of element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to undo substitution group change of element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        if (newSubstitutionGroup == null || newSubstitutionGroup.isEmpty()) {
            return "Remove substitution group from " + elementName;
        } else if (oldSubstitutionGroup == null || oldSubstitutionGroup.isEmpty()) {
            return "Add substitution group to " + elementName;
        } else {
            return "Change substitution group of " + elementName + " to " + newSubstitutionGroup;
        }
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Allow merging consecutive substitution group changes on the same element
        if (!(other instanceof ChangeSubstitutionGroupCommand otherCmd)) {
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
     * Gets the old substitution group.
     *
     * @return the old substitution group (can be null)
     */
    public String getOldSubstitutionGroup() {
        return oldSubstitutionGroup;
    }

    /**
     * Gets the new substitution group.
     *
     * @return the new substitution group (can be null)
     */
    public String getNewSubstitutionGroup() {
        return newSubstitutionGroup;
    }
}
