package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlAttribute;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.fxt.freexmltoolkit.i18n.I18n;

/**
 * A selectable tree view of an <em>XML instance</em> document (as opposed to {@link XsdTreeView}
 * which renders an XSD schema). It renders the shared {@link XmlNode} model — the same node
 * instances the Grid view and inspector edit — so selecting a node feeds the inspector and edits
 * round-trip. Element, text, comment, CDATA and processing-instruction nodes are shown (the node
 * types the inspector can edit); whitespace-only text is omitted. An element's attributes are rows of
 * their own before its children ({@code name = "value"}), backed by {@link XmlAttribute} stand-ins that
 * are not part of the model: selecting one reports its element, Delete hands the stand-in over (its
 * element via {@link #ownerOf}).
 */
public class XmlInstanceTreeView extends TreeView<XmlNode> {

    private Consumer<XmlNode> onSelectionChanged;
    private Consumer<XmlNode> onGoToDefinition;
    private Consumer<XmlNode> onDeleteRequested;
    /** Collapsed nodes to keep collapsed while the items are built again ({@link #rebuildPreservingState}). */
    private Set<java.util.UUID> keepCollapsed = Set.of();
    private final Map<java.util.UUID, TreeItem<XmlNode>> nodeToItem = new HashMap<>();
    /** The element of each attribute row's stand-in (the stand-ins are not in the model). */
    private final Map<XmlAttribute, XmlElement> attributeOwners = new java.util.IdentityHashMap<>();

    public XmlInstanceTreeView() {
        getStyleClass().add("fxt-xml-tree");
        setCellFactory(tv -> new XmlNodeCell());
        org.fxt.freexmltoolkit.controls.shared.TreeScrollHeader.install(this,
                node -> node instanceof XmlElement element ? element.getQualifiedName()
                        : node instanceof XmlAttribute attribute ? "@" + attribute.getName()
                        : node.getClass().getSimpleName(),
                node -> {
                    IconifyIcon icon = new IconifyIcon(XmlNodeIcons.icon(node));
                    icon.setIconSize(14);
                    return icon;
                });
        getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null && onSelectionChanged != null) {
                // an attribute row stands for its element (the inspector edits the attributes there)
                XmlNode node = newItem.getValue();
                onSelectionChanged.accept(node instanceof XmlAttribute a && ownerOf(a) != null ? ownerOf(a) : node);
            }
        });
        // Delete removes the selected node completely (the shell runs it as one undo step)
        addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DELETE && !event.isShortcutDown() && !event.isAltDown()
                    && !event.isShiftDown() && onDeleteRequested != null) {
                TreeItem<XmlNode> selected = getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null) {
                    onDeleteRequested.accept(selected.getValue());
                    event.consume();
                }
            }
        });
    }

    /** Sets the callback the Delete key hands the selected node to; {@code null} = Delete does nothing. */
    public void setOnDeleteRequested(Consumer<XmlNode> onDeleteRequested) {
        this.onDeleteRequested = onDeleteRequested;
    }

    /** @return the element of an attribute row's stand-in, or {@code null} */
    public XmlElement ownerOf(XmlNode node) {
        return node instanceof XmlAttribute attribute ? attributeOwners.get(attribute) : null;
    }

    /** Selects the row of the element's attribute {@code name} (no-op if absent). */
    public void selectAttribute(XmlElement element, String name) {
        TreeItem<XmlNode> elementItem = element == null ? null : nodeToItem.get(element.getId());
        if (elementItem == null) {
            return;
        }
        for (TreeItem<XmlNode> child : elementItem.getChildren()) {
            if (child.getValue() instanceof XmlAttribute attribute && attribute.getName().equals(name)) {
                selectNode(element); // expands the ancestors
                getSelectionModel().select(child);
                scrollTo(getRow(child));
                return;
            }
        }
    }

    /**
     * Builds the items again from the (edited) model, keeping collapsed nodes collapsed and the
     * selection when its node still exists.
     */
    public void rebuildPreservingState(XmlEditorContext ctx) {
        Set<java.util.UUID> collapsed = new HashSet<>();
        for (Map.Entry<java.util.UUID, TreeItem<XmlNode>> e : nodeToItem.entrySet()) {
            if (!e.getValue().isExpanded()) {
                collapsed.add(e.getKey());
            }
        }
        TreeItem<XmlNode> selected = getSelectionModel().getSelectedItem();
        XmlElement selectedOwner = selected == null ? null : ownerOf(selected.getValue());
        keepCollapsed = collapsed;
        try {
            setModel(ctx);
        } finally {
            keepCollapsed = Set.of();
        }
        if (selectedOwner != null) {
            selectAttribute(selectedOwner, ((XmlAttribute) selected.getValue()).getName());
        } else if (selected != null) {
            selectNode(selected.getValue());
        }
    }

    /**
     * The node to select after {@code node} is deleted: the next shown sibling, else the
     * previous one, else the parent.
     */
    public static XmlNode neighbourAfterDelete(XmlNode node) {
        if (!(node.getParent() instanceof XmlElement parent)) {
            return null;
        }
        java.util.List<XmlNode> siblings = parent.getChildren();
        int index = siblings.indexOf(node);
        for (int i = index + 1; i < siblings.size(); i++) {
            if (isRenderable(siblings.get(i))) {
                return siblings.get(i);
            }
        }
        for (int i = index - 1; i >= 0; i--) {
            if (isRenderable(siblings.get(i))) {
                return siblings.get(i);
            }
        }
        return parent;
    }

    /** Sets the callback invoked when the tree's selected node changes (for the inspector). */
    public void setOnSelectionChanged(Consumer<XmlNode> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    /**
     * Sets the callback invoked by the "Go to Definition" context-menu item (element rows only).
     * When {@code null}, the item is not shown.
     */
    public void setOnGoToDefinition(Consumer<XmlNode> onGoToDefinition) {
        this.onGoToDefinition = onGoToDefinition;
    }

    /**
     * Renders the given shared editor context's document. A {@code null}/empty context clears the tree.
     */
    public void setModel(XmlEditorContext ctx) {
        nodeToItem.clear();
        attributeOwners.clear();
        if (ctx == null || ctx.getDocument() == null) {
            setRoot(null);
            return;
        }
        XmlElement rootElement = ctx.getDocument().getRootElement();
        if (rootElement == null) {
            setRoot(null);
            return;
        }
        setRoot(buildItem(rootElement));
    }

    /** Re-selects the given model node, expanding its ancestors (no-op if absent). */
    public void selectNode(XmlNode node) {
        if (node == null) {
            return;
        }
        TreeItem<XmlNode> item = nodeToItem.get(node.getId());
        if (item != null) {
            TreeItem<XmlNode> parent = item.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
            getSelectionModel().select(item);
            scrollTo(getRow(item));
        }
    }

    private TreeItem<XmlNode> buildItem(XmlNode node) {
        TreeItem<XmlNode> item = new TreeItem<>(node);
        item.setExpanded(!keepCollapsed.contains(node.getId()));
        nodeToItem.put(node.getId(), item);
        if (node instanceof XmlElement element) {
            for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
                XmlAttribute standIn = new XmlAttribute(attr.getKey(), attr.getValue());
                attributeOwners.put(standIn, element);
                item.getChildren().add(new TreeItem<>(standIn));
            }
            for (XmlNode child : element.getChildren()) {
                if (isRenderable(child)) {
                    item.getChildren().add(buildItem(child));
                }
            }
        }
        return item;
    }

    /** Whitespace-only text nodes are noise; every other supported node type is shown. */
    private static boolean isRenderable(XmlNode node) {
        if (node instanceof XmlText text) {
            return text.getText() != null && !text.getText().isBlank();
        }
        return node instanceof XmlElement || node instanceof XmlComment
                || node instanceof XmlCData || node instanceof XmlProcessingInstruction;
    }

    /** Renders an XML model node (element + inline attributes, or the value/label of other types). */
    private final class XmlNodeCell extends TreeCell<XmlNode> {
        private ContextMenu cellMenu;

        @Override
        protected void updateItem(XmlNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            // "Go to Definition" applies to element rows only (and only when wired).
            setContextMenu(item instanceof XmlElement && onGoToDefinition != null ? cellMenu() : null);
            if (item instanceof XmlElement element) {
                setText(element.getQualifiedName()); // the attributes are rows of their own
            } else if (item instanceof XmlAttribute attribute) {
                setText(attribute.getName() + " = \"" + attribute.getValue() + "\"");
            } else if (item instanceof XmlText text) {
                setText("\"" + text.getText().strip() + "\"");
            } else if (item instanceof XmlComment comment) {
                setText("<!-- " + comment.getText().strip() + " -->");
            } else if (item instanceof XmlCData cdata) {
                setText("<![CDATA[ " + cdata.getText().strip() + " ]]>");
            } else if (item instanceof XmlProcessingInstruction pi) {
                setText("<?" + pi.getTarget() + " " + pi.getData() + "?>");
            } else if (item instanceof XmlDocument) {
                setText(I18n.t("XmlInstanceTreeView.document"));
            } else {
                setText(item.toString());
            }
            IconifyIcon icon = new IconifyIcon(XmlNodeIcons.icon(item));
            icon.setIconSize(14);
            setGraphic(icon);
        }

        /** Lazily builds this cell's context menu (a single "Go to Definition" item). */
        private ContextMenu cellMenu() {
            if (cellMenu == null) {
                MenuItem goToDefinitionItem = new MenuItem(I18n.t("XmlInstanceTreeView.go-to-definition"));
                IconifyIcon menuIcon = new IconifyIcon("bi-box-arrow-up-right");
                SemanticIcon.paint(menuIcon, DesignTokens.ColorToken.INFO);
                menuIcon.setIconSize(14);
                goToDefinitionItem.setGraphic(menuIcon);
                goToDefinitionItem.setOnAction(e -> {
                    XmlNode node = getItem();
                    if (node instanceof XmlElement && onGoToDefinition != null) {
                        onGoToDefinition.accept(node);
                    }
                });
                cellMenu = new ContextMenu(goToDefinitionItem);
            }
            return cellMenu;
        }
    }
}
