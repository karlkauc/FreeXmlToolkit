package org.fxt.freexmltoolkit.controls.v2.view;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

/**
 * The title chip at the top-left of the diagram (Figma 07.6b): the symbol of the node in focus,
 * its name, and a muted line with its kind, type and content model, e.g.
 * {@code ControlDataType  complexType · sequence}.
 */
final class XsdGraphTitleChip extends HBox {

    private static final String SEPARATOR = " · ";

    private final IconifyIcon icon = new IconifyIcon("bi-diagram-3");
    private final ObjectProperty<Paint> tint = new SimpleObjectProperty<>(Color.GRAY);
    private final Label name = new Label();
    private final Label meta = new Label();

    XsdGraphTitleChip() {
        getStyleClass().add("fxt-graph-title-chip");
        setId("graph-title-chip");
        setAlignment(Pos.CENTER_LEFT);
        setMouseTransparent(true);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        icon.setIconSize(15);
        // Bound, so no themed -fx-icon-color rule can override the per-kind colour
        icon.iconColorProperty().bind(tint);
        name.getStyleClass().add("fxt-graph-title-name");
        name.setId("graph-title-name");
        meta.getStyleClass().add("fxt-graph-title-meta");
        meta.setId("graph-title-meta");
        getChildren().addAll(icon, name, meta);
    }

    /**
     * Shows a node, or the fallback text when there is none.
     *
     * @param node     the node in focus (the selection, else the diagram root), may be null
     * @param fallback the name to show without a node
     * @param color    the node kind's accent colour for the symbol
     */
    void show(VisualNode node, String fallback, Color color) {
        show(node, fallback, color, null);
    }

    /**
     * Same as {@link #show(VisualNode, String, Color)}, naming the node's kind explicitly.
     *
     * @param kind the kind to show instead of the card's own (e.g. "complexType" for the root of a
     *             type editor, which is a stand-in element), or null
     */
    void show(VisualNode node, String fallback, Color color, String kind) {
        tint.set(color);
        if (node == null) {
            icon.setIconLiteral("bi-hdd-stack");
            name.setText(fallback);
            meta.setText("");
        } else {
            icon.setIconLiteral(XsdNodeRenderer.cardIcon(node));
            name.setText(node.getLabel() != null ? node.getLabel() : "");
            meta.setText(metaOf(node, kind));
        }
        meta.setManaged(!meta.getText().isEmpty());
        meta.setVisible(meta.isManaged());
    }

    String nameText() {
        return name.getText();
    }

    String metaText() {
        return meta.getText();
    }

    /**
     * The muted line: kind, type (when it differs from the kind) and the compositor of the
     * node's content, joined with " · ".
     */
    static String metaOf(VisualNode node) {
        return metaOf(node, null);
    }

    /**
     * @param kindOverride the kind to name instead of the card's own; the type line is then left out
     *                     when it only repeats the name (a type editor's root names its own type)
     */
    static String metaOf(VisualNode node, String kindOverride) {
        List<String> parts = new ArrayList<>();
        String kind = kindOverride != null ? kindOverride : XsdNodeRenderer.kindName(node.getType());
        parts.add(kind);
        String type = XsdNodeRenderer.typeLine(node);
        boolean repeatsName = kindOverride != null && type.equals(node.getLabel());
        if (!type.equals(XsdNodeRenderer.kindName(node.getType())) && !repeatsName) {
            parts.add(type);
        }
        for (VisualNode child : node.getChildren()) {
            NodeWrapperType childType = child.getType();
            if (childType == NodeWrapperType.SEQUENCE || childType == NodeWrapperType.CHOICE
                    || childType == NodeWrapperType.ALL) {
                parts.add(XsdNodeRenderer.kindName(childType));
                break;
            }
        }
        return String.join(SEPARATOR, parts);
    }
}
