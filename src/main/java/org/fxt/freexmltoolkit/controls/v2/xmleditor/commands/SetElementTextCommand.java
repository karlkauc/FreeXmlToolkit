package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to set the text content of an element.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Replaces all text node children of an element</li>
 *   <li>Stores the old text nodes for undo</li>
 *   <li>Creates a new text node if none exists</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class SetElementTextCommand implements XmlCommand {

    private final XmlElement element;
    private final String newText;
    private final List<XmlText> oldTextNodes;
    private final List<Integer> oldTextIndices;
    private XmlText newTextNode;
    private boolean executed = false;

    /**
     * Constructs a command to set element text content.
     *
     * @param element the element to modify
     * @param newText the new text content
     */
    public SetElementTextCommand(XmlElement element, String newText) {
        this.element = element;
        this.newText = newText;
        this.oldTextNodes = new ArrayList<>();
        this.oldTextIndices = new ArrayList<>();

        // Store existing text nodes and their positions
        List<XmlNode> children = element.getChildren();
        for (int i = 0; i < children.size(); i++) {
            XmlNode child = children.get(i);
            if (child instanceof XmlText) {
                oldTextNodes.add((XmlText) child);
                oldTextIndices.add(i);
            }
        }
    }

    @Override
    public boolean execute() {
        // Prevent mixed content: don't add text if element has child elements
        if (newText != null && !newText.isEmpty() && element.hasElementChildren()) {
            return false; // Reject operation to prevent mixed content
        }

        // Remove old text nodes
        for (XmlText oldText : oldTextNodes) {
            element.removeChild(oldText);
        }

        // Add new text node if content is not empty
        if (newText != null && !newText.isEmpty()) {
            newTextNode = new XmlText(newText);
            element.addChild(newTextNode);
        }

        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }

        // Remove the new text node
        if (newTextNode != null) {
            element.removeChild(newTextNode);
        }

        // Restore old text nodes at their original positions
        for (int i = 0; i < oldTextNodes.size(); i++) {
            XmlText oldText = oldTextNodes.get(i);
            int index = oldTextIndices.get(i);
            // Ensure index is within bounds
            if (index >= element.getChildCount()) {
                element.addChild(oldText);
            } else {
                element.addChild(index, oldText);
            }
        }

        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit Element Text";
    }

    @Override
    public boolean canMergeWith(XmlCommand other) {
        if (!(other instanceof SetElementTextCommand otherCmd)) {
            return false;
        }

        return this.element == otherCmd.element;
    }

    @Override
    public XmlCommand mergeWith(XmlCommand other) {
        if (!canMergeWith(other)) {
            throw new IllegalArgumentException("Cannot merge commands");
        }

        SetElementTextCommand otherCmd = (SetElementTextCommand) other;
        // Create merged command that uses the original old text nodes
        SetElementTextCommand merged = new SetElementTextCommand(element, otherCmd.newText);
        merged.oldTextNodes.clear();
        merged.oldTextNodes.addAll(this.oldTextNodes);
        merged.oldTextIndices.clear();
        merged.oldTextIndices.addAll(this.oldTextIndices);
        return merged;
    }

    @Override
    public String toString() {
        String oldText = oldTextNodes.isEmpty() ? "" : oldTextNodes.get(0).getText();
        return getDescription() + " ('" + oldText + "' -> '" + newText + "')";
    }
}
