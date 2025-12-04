package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Command to add an enumeration value to an XSD element.
 * <p>
 * Enumerations define a fixed set of allowed values for an element.
 * For example: "red", "green", "blue" for a color element.
 * <p>
 * Supports full undo/redo functionality.
 *
 * @since 2.0
 */
public class AddEnumerationCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddEnumerationCommand.class);

    private final XsdEditorContext editorContext;
    private final XsdElement element;
    private final String enumeration;

    /**
     * Creates a new add enumeration command.
     *
     * @param editorContext the editor context
     * @param node          the XSD node (must be an XsdElement)
     * @param enumeration   the enumeration value to add
     * @throws IllegalArgumentException if editorContext is null, node is null, node is not an XsdElement, or enumeration is empty
     */
    public AddEnumerationCommand(XsdEditorContext editorContext, XsdNode node, String enumeration) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (!(node instanceof XsdElement)) {
            throw new IllegalArgumentException("Enumerations can only be added to elements, not to " +
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
            logger.debug("Adding enumeration '{}' to element '{}'", enumeration, element.getName());

            element.addEnumeration(enumeration);
            editorContext.markNodeDirty(element);

            logger.info("Successfully added enumeration to element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to add enumeration to element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            logger.debug("Removing enumeration '{}' from element '{}'", enumeration, element.getName());

            element.removeEnumeration(enumeration);
            editorContext.markNodeDirty(element);

            logger.info("Successfully removed enumeration from element '{}'", element.getName());
            return true;

        } catch (Exception e) {
            logger.error("Failed to remove enumeration from element '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String elementName = element.getName() != null ? element.getName() : "(unnamed)";
        return "Add enumeration value to " + elementName;
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
     * Gets the enumeration value being added.
     *
     * @return the enumeration value
     */
    public String getEnumeration() {
        return enumeration;
    }
}
