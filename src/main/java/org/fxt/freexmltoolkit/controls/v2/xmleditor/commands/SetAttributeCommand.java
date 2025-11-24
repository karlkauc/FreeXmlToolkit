package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;

/**
 * Command to set an attribute value on an element.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Sets an attribute value on an element</li>
 *   <li>Stores the old value for undo (null if attribute didn't exist)</li>
 *   <li>Can handle both adding new attributes and modifying existing ones</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class SetAttributeCommand implements XmlCommand {

    private final XmlElement element;
    private final String attributeName;
    private final String newValue;
    private final String oldValue;
    private boolean executed = false;

    /**
     * Constructs a command to set an attribute.
     *
     * @param element       the element to modify
     * @param attributeName the attribute name
     * @param newValue      the new attribute value
     */
    public SetAttributeCommand(XmlElement element, String attributeName, String newValue) {
        this.element = element;
        this.attributeName = attributeName;
        this.newValue = newValue;
        this.oldValue = element.getAttribute(attributeName);
    }

    @Override
    public boolean execute() {
        element.setAttribute(attributeName, newValue);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }

        if (oldValue == null) {
            // Attribute didn't exist before, remove it
            element.removeAttribute(attributeName);
        } else {
            // Restore old value
            element.setAttribute(attributeName, oldValue);
        }
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        if (oldValue == null) {
            return "Add Attribute '" + attributeName + "'";
        } else {
            return "Edit Attribute '" + attributeName + "'";
        }
    }

    @Override
    public String toString() {
        return getDescription() + " on '" + element.getName() + "'";
    }
}
