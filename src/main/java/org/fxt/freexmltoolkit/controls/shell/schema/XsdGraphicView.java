package org.fxt.freexmltoolkit.controls.shell.schema;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Graphic view of an XSD (UI rebuild Phase 4, increment b): an XMLSpy-style
 * diagram rendered with the scene graph. Containers are compact header rows with
 * their children nested vertically below (indented, with a left guide), expanded
 * lazily. Repeating elements (maxOccurs &gt; 1) render as an embedded grid of
 * their field columns instead of N identical cards — there is no standalone Grid
 * mode (decision: the grid is part of Graphic).
 * <p>
 * Lazy expansion keeps the materialized scene graph bounded to expanded paths.
 * Read-only for now; command-based editing lands later.
 */
public class XsdGraphicView extends ScrollPane {

    private final Consumer<XsdNode> onSelect;
    private NodeCard selectedCard;
    private javafx.scene.control.ContextMenu contextMenu;

    public XsdGraphicView(Consumer<XsdNode> onSelect) {
        this.onSelect = onSelect;
        getStyleClass().add("fxt-graphic-view");
        setFitToWidth(true);
    }

    /** Installs the shared node-editing context menu (right-click a card) — Tree-parity editing. */
    public void setEditActions(NodeEditActions actions) {
        this.contextMenu = NodeContextMenu.build(actions, () -> selectedCard != null ? selectedCard.node : null);
    }

    /** Top levels expanded by default for a useful overview; deeper nodes stay lazy. */
    private static final int DEFAULT_EXPAND_DEPTH = 8;

    public void setSchema(XsdSchema schema) {
        // Preserve the user's expand/collapse state (by node id) across re-renders,
        // e.g. after a structured edit where the model nodes persist (matrix #50).
        boolean hadPriorRender = getContent() instanceof VBox c && !c.getChildren().isEmpty()
                && c.getChildren().get(0) instanceof NodeCard;
        Set<String> expandedIds = currentExpandedIds();
        NodeCard root = new NodeCard(schema, true);
        VBox canvas = new VBox(root);
        canvas.getStyleClass().add("fxt-graphic-canvas");
        setContent(canvas);
        if (hadPriorRender) {
            restoreExpansion(root, expandedIds); // empty set ⇒ everything collapsed (user's choice)
        } else {
            expandToDepth(root, DEFAULT_EXPAND_DEPTH);
        }
    }

    /** @return the node ids of all currently-expanded container cards. */
    private Set<String> currentExpandedIds() {
        Set<String> ids = new HashSet<>();
        if (getContent() instanceof VBox canvas && !canvas.getChildren().isEmpty()
                && canvas.getChildren().get(0) instanceof NodeCard root) {
            collectExpanded(root, ids);
        }
        return ids;
    }

    private void collectExpanded(NodeCard card, Set<String> ids) {
        if (card.container && card.expanded) {
            ids.add(card.node.getId());
            if (card.childrenBox != null) {
                for (var child : card.childrenBox.getChildren()) {
                    if (child instanceof NodeCard nested) {
                        collectExpanded(nested, ids);
                    }
                }
            }
        }
    }

    private void restoreExpansion(NodeCard card, Set<String> ids) {
        if (card.container && ids.contains(card.node.getId())) {
            card.setExpanded(true);
            if (card.childrenBox != null) {
                for (var child : card.childrenBox.getChildren()) {
                    if (child instanceof NodeCard nested) {
                        restoreExpansion(nested, ids);
                    }
                }
            }
        }
    }

    private void expandToDepth(NodeCard card, int depth) {
        if (depth <= 0 || !card.container) {
            return;
        }
        card.setExpanded(true);
        if (card.childrenBox != null) {
            for (var child : card.childrenBox.getChildren()) {
                if (child instanceof NodeCard nested) {
                    expandToDepth(nested, depth - 1);
                }
            }
        }
    }

    /** @return {@code true} if the XSD parsed; on failure the view is cleared. */
    public boolean setXsdFromText(String xsdContent) {
        try {
            setSchema(new XsdNodeFactory().fromString(xsdContent));
            return true;
        } catch (Exception e) {
            setContent(null);
            return false;
        }
    }

    /** @return the schema root node currently rendered, or {@code null}. */
    public XsdNode getSchemaRoot() {
        if (getContent() instanceof VBox canvas && !canvas.getChildren().isEmpty()
                && canvas.getChildren().get(0) instanceof NodeCard root) {
            return root.node;
        }
        return null;
    }

    /** Selects (reveals) the card for the given node, if present. */
    public void selectNode(XsdNode node) {
        NodeCard root = rootCard();
        if (root != null) {
            NodeCard card = root.find(node);
            if (card != null) {
                card.select();
            }
        }
    }

    /** Expands or collapses the card for {@code node} (e.g. for expand/collapse-all). */
    public void setNodeExpanded(XsdNode node, boolean expanded) {
        NodeCard root = rootCard();
        NodeCard card = root != null ? root.find(node) : null;
        if (card != null) {
            card.setExpanded(expanded);
        }
    }

    /** @return {@code true} if the card for {@code node} is currently expanded. */
    public boolean isNodeExpanded(XsdNode node) {
        NodeCard root = rootCard();
        NodeCard card = root != null ? root.find(node) : null;
        return card != null && card.expanded;
    }

    private NodeCard rootCard() {
        if (getContent() instanceof VBox canvas && !canvas.getChildren().isEmpty()
                && canvas.getChildren().get(0) instanceof NodeCard root) {
            return root;
        }
        return null;
    }

    private void select(NodeCard card) {
        if (selectedCard != null) {
            selectedCard.header.getStyleClass().remove("selected");
        }
        selectedCard = card;
        card.header.getStyleClass().add("selected");
        if (onSelect != null) {
            onSelect.accept(card.node);
        }
    }

    /** A node rendered as a header row plus a lazily-built, indented children box. */
    private final class NodeCard extends VBox {
        private final XsdNode node;
        private final HBox header;
        private final Label chevron;
        private final boolean container;
        private final boolean repeating;
        private VBox childrenBox;
        private boolean expanded;
        private boolean built;

        NodeCard(XsdNode node, boolean isRoot) {
            this.node = node;
            this.repeating = SchemaGridModel.isRepeating(node);
            this.container = !node.getChildren().isEmpty();
            getStyleClass().add("fxt-graphic-node");

            chevron = new Label(container ? "▸" : "");
            chevron.getStyleClass().add("fxt-graphic-chevron");

            IconifyIcon icon = new IconifyIcon(XsdNodeLabels.icon(node.getNodeType()));
            icon.setIconSize(14);

            Label label = new Label(XsdNodeLabels.displayText(node));
            label.getStyleClass().add("fxt-graphic-label");

            header = new HBox(4, chevron, icon, label);
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("fxt-graphic-header");
            if (repeating) {
                Label badge = new Label("grid");
                badge.getStyleClass().add("fxt-grid-badge");
                header.getChildren().add(badge);
            }
            header.setOnMouseClicked(e -> {
                XsdGraphicView.this.select(this);
                if (container && e.getClickCount() == 2) {
                    setExpanded(!expanded);
                }
            });
            chevron.setOnMouseClicked(e -> {
                if (container) {
                    setExpanded(!expanded);
                    e.consume();
                }
            });
            header.setOnContextMenuRequested(e -> {
                XsdGraphicView.this.select(this);
                if (contextMenu != null) {
                    contextMenu.show(header, e.getScreenX(), e.getScreenY());
                    e.consume();
                }
            });

            getChildren().add(header);
            if (isRoot) {
                getStyleClass().add("fxt-graphic-root");
            }
        }

        void setExpanded(boolean expand) {
            if (!container) {
                return;
            }
            this.expanded = expand;
            chevron.setText(expand ? "▾" : "▸");
            if (expand) {
                if (!built) {
                    buildChildren();
                    built = true;
                }
                childrenBox.setVisible(true);
                childrenBox.setManaged(true);
            } else if (childrenBox != null) {
                childrenBox.setVisible(false);
                childrenBox.setManaged(false);
            }
        }

        private void buildChildren() {
            childrenBox = new VBox();
            childrenBox.getStyleClass().add("fxt-graphic-children");
            if (repeating) {
                childrenBox.getChildren().add(buildGrid());
            } else {
                for (XsdNode child : node.getChildren()) {
                    childrenBox.getChildren().add(new NodeCard(child, false));
                }
            }
            getChildren().add(childrenBox);
        }

        /** Embedded grid: a header row of the repeating element's field columns. */
        private Region buildGrid() {
            HBox row = new HBox();
            row.getStyleClass().add("fxt-grid");
            var columns = SchemaGridModel.gridColumns(node);
            if (columns.isEmpty()) {
                Label empty = new Label("(repeating)");
                empty.getStyleClass().add("fxt-placeholder-text");
                row.getChildren().add(empty);
            } else {
                for (XsdNode column : columns) {
                    Label cell = new Label(XsdNodeLabels.displayText(column));
                    cell.getStyleClass().add("fxt-grid-cell");
                    row.getChildren().add(cell);
                }
            }
            return row;
        }

        NodeCard find(XsdNode target) {
            if (node == target) {
                return this;
            }
            if (!container) {
                return null;
            }
            if (!built) {
                setExpanded(true);
            }
            for (var child : childrenBox.getChildren()) {
                if (child instanceof NodeCard card) {
                    NodeCard found = card.find(target);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        void select() {
            XsdGraphicView.this.select(this);
        }
    }
}
