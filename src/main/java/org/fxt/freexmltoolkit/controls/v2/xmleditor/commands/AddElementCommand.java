package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Command to add a new element as a child of a parent node.
 *
 * <p>This command:</p>
 * <ul>
 *   <li>Adds an element to a parent at a specific index</li>
 *   <li>Can be undone by removing the element</li>
 *   <li>Supports both XmlElement and XmlDocument as parents</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class AddElementCommand implements XmlCommand {

    private final XmlNode parent;
    private final XmlElement elementToAdd;
    private final int index;
    private boolean executed = false;

    /**
     * Constructs a command to add an element at the end of the parent's children.
     *
     * @param parent       the parent node (XmlElement or XmlDocument)
     * @param elementToAdd the element to add
     */
    public AddElementCommand(XmlNode parent, XmlElement elementToAdd) {
        this(parent, elementToAdd, -1);
    }

    /**
     * Constructs a command to add an element at a specific index.
     *
     * @param parent       the parent node (XmlElement or XmlDocument)
     * @param elementToAdd the element to add
     * @param index        the index to insert at (-1 for end)
     */
    public AddElementCommand(XmlNode parent, XmlElement elementToAdd, int index) {
        this.parent = parent;
        this.elementToAdd = elementToAdd;
        this.index = index;
    }

    @Override
    public boolean execute() {
        if (parent instanceof XmlElement parentElement) {

            // Prevent mixed content: don't add element if parent has non-whitespace text content
            if (parentElement.hasNonWhitespaceTextContent()) {
                return false; // Reject operation to prevent mixed content
            }

            if (index >= 0 && index <= parentElement.getChildCount()) {
                parentElement.addChild(index, elementToAdd);
            } else {
                parentElement.addChild(elementToAdd);
            }
            executed = true;
            return true;
        } else if (parent instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument doc) {
            if (index >= 0 && index <= doc.getChildCount()) {
                doc.addChild(index, elementToAdd);
            } else {
                doc.addChild(elementToAdd);
            }
            executed = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }

        if (parent instanceof XmlElement parentElement) {
            parentElement.removeChild(elementToAdd);
            executed = false;
            return true;
        } else if (parent instanceof org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument doc) {
            doc.removeChild(elementToAdd);
            executed = false;
            return true;
        }
        return false;
    }

    @Override
    public String getDescription() {
        return "Add Element '" + elementToAdd.getName() + "'";
    }

    @Override
    public String toString() {
        return getDescription() + " to " + parent.getClass().getSimpleName();
    }
}
