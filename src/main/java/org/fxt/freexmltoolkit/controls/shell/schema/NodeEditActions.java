package org.fxt.freexmltoolkit.controls.shell.schema;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Editing actions a structured view (Tree/Graphic) can request for a node. The
 * view handles the user interaction (dialogs); the implementation routes the
 * request through the command stack. Keeps the renderer decoupled from the
 * command layer.
 */
public interface NodeEditActions {

    void addElement(XsdNode parent, String name);

    void rename(XsdNode node, String newName);

    void changeCardinality(XsdNode node, int minOccurs, int maxOccurs);

    void delete(XsdNode node);
}
