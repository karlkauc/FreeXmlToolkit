package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.LinkedHashMap;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;

/**
 * Command to rename an attribute key while preserving its value and position in the element's
 * (insertion-ordered) attribute map. Implemented by rebuilding the attribute map; undo restores the
 * original map.
 *
 * @since 2.0
 */
public class RenameAttributeCommand implements XmlCommand {

    private final XmlElement element;
    private final String oldName;
    private final String newName;
    private final LinkedHashMap<String, String> oldAttributes;
    private boolean executed = false;

    public RenameAttributeCommand(XmlElement element, String oldName, String newName) {
        this.element = element;
        this.oldName = oldName;
        this.newName = newName == null ? null : newName.trim();
        this.oldAttributes = new LinkedHashMap<>(element.getAttributes());
    }

    @Override
    public boolean execute() {
        if (oldName == null || newName == null || newName.isBlank()
                || newName.equals(oldName) || !element.hasAttribute(oldName)) {
            return false;
        }
        LinkedHashMap<String, String> rebuilt = new LinkedHashMap<>();
        for (var entry : element.getAttributes().entrySet()) {
            if (entry.getKey().equals(oldName)) {
                rebuilt.put(newName, entry.getValue()); // rename in place (preserve position)
            } else if (!entry.getKey().equals(newName)) {
                rebuilt.put(entry.getKey(), entry.getValue()); // drop any pre-existing collision
            }
        }
        element.clearAttributes();
        rebuilt.forEach(element::setAttribute);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        element.clearAttributes();
        oldAttributes.forEach(element::setAttribute);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Rename Attribute '" + oldName + "' to '" + newName + "'";
    }

    @Override
    public String toString() {
        return getDescription() + " on '" + element.getName() + "'";
    }
}
