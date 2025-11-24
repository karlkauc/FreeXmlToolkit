package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;

/**
 * Command to remove an attribute from an element.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Removes an attribute from an element</li>
 *   <li>Stores the old value for undo</li>
 *   <li>Can be undone by restoring the attribute</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class RemoveAttributeCommand implements XmlCommand {

    private final XmlElement element;
    private final String attributeName;
    private final String oldValue;
    private boolean executed = false;

    /**
     * Constructs a command to remove an attribute.
     *
     * @param element       the element to modify
     * @param attributeName the attribute name to remove
     */
    public RemoveAttributeCommand(XmlElement element, String attributeName) {
        this.element = element;
        this.attributeName = attributeName;
        this.oldValue = element.getAttribute(attributeName);
    }

    @Override
    public boolean execute() {
        if (oldValue == null) {
            // Attribute doesn't exist
            return false;
        }

        element.removeAttribute(attributeName);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }

        element.setAttribute(attributeName, oldValue);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Remove Attribute '" + attributeName + "'";
    }

    @Override
    public String toString() {
        return getDescription() + " from '" + element.getName() + "'";
    }
}
