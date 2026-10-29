package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Inserts any node (element, text, comment, CDATA, PI) into an element at an index, e.g. a
 * copy dropped in the grid or nodes parsed from a dropped XML fragment. Unlike
 * {@link AddElementCommand} it does not refuse mixed content: the user placed the node there.
 */
public final class InsertNodeCommand implements XmlCommand {

    private final XmlElement parent;
    private final XmlNode node;
    private final int index;
    private boolean executed;

    /**
     * @param parent the element to insert into
     * @param node   the node (must not have a parent yet)
     * @param index  the child index, -1 = append
     */
    public InsertNodeCommand(XmlElement parent, XmlNode node, int index) {
        this.parent = parent;
        this.node = node;
        this.index = index;
    }

    @Override
    public boolean execute() {
        if (node.getParent() != null) {
            return false;
        }
        if (index >= 0 && index <= parent.getChildCount()) {
            parent.addChild(index, node);
        } else {
            parent.addChild(node);
        }
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        parent.removeChild(node);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Insert " + node.getClass().getSimpleName().replace("Xml", "").toLowerCase();
    }
}
