package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to delete an enumeration value from an XSD element.
 * <p>
 * Supports full undo/redo functionality by storing the deleted enumeration.
 *
 * @since 2.0
 */
public class DeleteEnumerationCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteEnumerationCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdElement element;
    private final String enumeration;

    /**
     * Creates a new delete enumeration command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param enumeration   the enumeration value to delete
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or enumeration is empty
     */
    public DeleteEnumerationCommand(XsdEditorContext editorContext, XsdNode node, String enumeration) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement)) {
            throw new IllegalArgumentException("Enumerations can only be deleted from elements, not from " +
                    node.getClass().getSimpleName());
        }
        if (enumeration == null || enumeration.trim().isEmpty()) {
            throw new IllegalArgumentException("Enumeration value cannot be null or empty");
        }

        this.editorContext = editorContext;
        this.element = (XsdElement) node;
        this.enumeration = enumeration.trim();
    }

    @Override
    public boolean execute() {
        try {
            logger.debug("Deleting enumeration '{}' from element '{}'", enumeration, element.getName());

            boolean removed = element.removeEnumeration(enumeration);
            if (!removed) {
                logger.warn("Enumeration '{}' not found in element '{}'", enumeration, element.getName());
                return false;
            }

            editorContext.markNodeDirty(element);

            logger.info("Successfully deleted enumeration from element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to delete enumeration from element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Re-adding enumeration '{}' to element '{}'", enumeration, element.getName());

            element.addEnumeration(enumeration);
            editorContext.markNodeDirty(element);

            logger.info("Successfully restored enumeration to element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to restore enumeration to element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        return "Delete enumeration value from " + elementName;
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        // Enumeration commands should not be merged
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
     * Gets the enumeration value being deleted.
     *
     * @return the enumeration value
     */
    public String getEnumeration() {
        return enumeration;
    }
}
