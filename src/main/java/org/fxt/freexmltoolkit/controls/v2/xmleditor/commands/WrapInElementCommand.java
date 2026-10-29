package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Wraps a node in a new element (XMLSpy "Wrap in Element", Ctrl+Alt+W): the new element takes
 * the node's place under its parent and the node becomes its only child. Undo puts the node
 * back at its old position.
 */
public final class WrapInElementCommand implements XmlCommand {

    private final XmlNode node;
    private final XmlElement wrapper;
    private XmlNode parent;
    private int index = -1;
    private boolean executed;

    /**
     * @param node        the node to wrap (an element, text, comment, CDATA or PI node)
     * @param wrapperName the name of the new parent element
     */
    public WrapInElementCommand(XmlNode node, String wrapperName) {
        this.node = node;
        this.wrapper = new XmlElement(wrapperName);
    }

    /** @return the new wrapper element */
    public XmlElement getWrapper() {
        return wrapper;
    }

    @Override
    public boolean execute() {
        parent = node.getParent();
        if (parent instanceof XmlElement parentElement) {
            index = parentElement.indexOf(node);
            if (index < 0) {
                return false;
            }
            parentElement.removeChild(node);
            wrapper.addChild(node);
            parentElement.addChild(index, wrapper);
        } else if (parent instanceof XmlDocument doc) {
            index = doc.getChildren().indexOf(node);
            if (index < 0) {
                return false;
            }
            doc.removeChild(node);
            wrapper.addChild(node);
            doc.addChild(index, wrapper);
        } else {
            return false;
        }
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        wrapper.removeChild(node);
        if (parent instanceof XmlElement parentElement) {
            parentElement.removeChild(wrapper);
            parentElement.addChild(index, node);
        } else if (parent instanceof XmlDocument doc) {
            doc.removeChild(wrapper);
            doc.addChild(index, node);
        }
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Wrap in element " + wrapper.getName();
    }
}
