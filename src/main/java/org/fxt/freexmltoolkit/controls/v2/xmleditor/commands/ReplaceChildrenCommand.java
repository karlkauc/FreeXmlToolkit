package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Replaces all children of an element with new nodes (XMLSpy "Edit as Raw Text": the element's
 * content edited as markup and parsed back). Undo restores the original child nodes.
 */
public final class ReplaceChildrenCommand implements XmlCommand {

    private final XmlElement element;
    private final List<XmlNode> newChildren;
    private List<XmlNode> oldChildren;
    private boolean executed;

    /**
     * @param element     the element whose content is replaced
     * @param newChildren the new child nodes (without a parent)
     */
    public ReplaceChildrenCommand(XmlElement element, List<XmlNode> newChildren) {
        this.element = element;
        this.newChildren = new ArrayList<>(newChildren);
    }

    @Override
    public boolean execute() {
        oldChildren = new ArrayList<>(element.getChildren());
        element.clearChildren();
        for (XmlNode child : newChildren) {
            element.addChild(child);
        }
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        element.clearChildren();
        for (XmlNode child : oldChildren) {
            element.addChild(child);
        }
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit content of " + element.getName() + " as raw text";
    }
}
