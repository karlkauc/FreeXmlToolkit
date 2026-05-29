package org.fxt.freexmltoolkit.controls.shell.schema;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Virtualized Tree view of an XSD (UI rebuild Phase 4, increment 1). Reuses the
 * V2 model ({@link XsdNodeFactory} + {@link XsdNode}) and renders it through a
 * JavaFX {@link TreeView}, which virtualizes the cells (only visible rows are
 * materialized — see the Phase-2 feasibility spike). Read-only for now; editing
 * via the command stack lands in a later increment.
 */
public class XsdTreeView extends TreeView<XsdNode> {

    public XsdTreeView() {
        getStyleClass().add("fxt-xsd-tree");
        setShowRoot(true);
        setCellFactory(tv -> new XsdNodeCell());
    }

    /** Renders the given parsed schema. */
    public void setSchema(XsdSchema schema) {
        setRoot(XsdTreeBuilder.build(schema));
    }

    /**
     * Parses XSD text and renders it.
     *
     * @return {@code true} if parsing succeeded; on failure the tree is cleared
     */
    public boolean setXsdFromText(String xsdContent) {
        try {
            setSchema(new XsdNodeFactory().fromString(xsdContent));
            return true;
        } catch (Exception e) {
            setRoot(null);
            return false;
        }
    }

    /** Cell rendering a node's type icon and display text. */
    private static final class XsdNodeCell extends TreeCell<XsdNode> {
        @Override
        protected void updateItem(XsdNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(XsdNodeLabels.displayText(item));
            IconifyIcon icon = new IconifyIcon(XsdNodeLabels.icon(item.getNodeType()));
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }
}
