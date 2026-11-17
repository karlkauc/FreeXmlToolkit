package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;

/**
 * Adds a child element at the place the expected child order gives it ({@link ChildOrder}), not at
 * the end. The position is resolved when the command runs, so several of these inside one
 * {@link CompositeXmlCommand} each see the children the earlier parts added.
 */
public class AddElementInOrderCommand implements XmlCommand {

    private final XmlElement parent;
    private final XmlElement elementToAdd;
    private final List<String> order;
    private AddElementCommand delegate;

    /**
     * @param parent       the element receiving the child
     * @param elementToAdd the new child
     * @param order        expected child names in order (see {@link ChildOrder#orderFor}); empty = append
     */
    public AddElementInOrderCommand(XmlElement parent, XmlElement elementToAdd, List<String> order) {
        this.parent = parent;
        this.elementToAdd = elementToAdd;
        this.order = order == null ? List.of() : List.copyOf(order);
    }

    @Override
    public boolean execute() {
        int index = ChildOrder.insertionIndex(parent, elementToAdd.getName(), order);
        delegate = new AddElementCommand(parent, elementToAdd, index);
        return delegate.execute();
    }

    @Override
    public boolean undo() {
        return delegate != null && delegate.undo();
    }

    @Override
    public String getDescription() {
        return "Add Element '" + elementToAdd.getName() + "'";
    }

    @Override
    public String toString() {
        return getDescription() + " to " + parent.getName();
    }
}
