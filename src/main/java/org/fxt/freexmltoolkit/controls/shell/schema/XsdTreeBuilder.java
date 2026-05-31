package org.fxt.freexmltoolkit.controls.shell.schema;

import javafx.scene.control.TreeItem;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Converts an {@link XsdNode} model tree into a JavaFX {@link TreeItem} tree for
 * the virtualized Schema Tree view. UI-free (no toolkit), so it is unit-testable.
 * <p>
 * The XsdNode tree is finite (structural children only; type references are not
 * expanded), so a straight recursive walk is safe against the circular-type
 * concern that affects type-resolution traversals.
 */
public final class XsdTreeBuilder {

    private XsdTreeBuilder() {
    }

    /** Builds an expanded TreeItem tree mirroring the model rooted at {@code node}. */
    public static TreeItem<XsdNode> build(XsdNode node) {
        TreeItem<XsdNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        for (XsdNode child : node.getChildren()) {
            item.getChildren().add(build(child));
        }
        return item;
    }
}
