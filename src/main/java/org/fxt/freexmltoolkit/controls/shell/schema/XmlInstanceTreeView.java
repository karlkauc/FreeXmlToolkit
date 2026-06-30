package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.paint.Color;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

/**
 * A selectable tree view of an <em>XML instance</em> document (as opposed to {@link XsdTreeView}
 * which renders an XSD schema). It renders the shared {@link XmlNode} model — the same node
 * instances the Grid view and inspector edit — so selecting a node feeds the inspector and edits
 * round-trip. Element, text, comment, CDATA and processing-instruction nodes are shown (the node
 * types the inspector can edit); whitespace-only text is omitted.
 */
public class XmlInstanceTreeView extends TreeView<XmlNode> {

    private Consumer<XmlNode> onSelectionChanged;
    private Consumer<XmlNode> onGoToDefinition;
    private final Map<java.util.UUID, TreeItem<XmlNode>> nodeToItem = new HashMap<>();

    public XmlInstanceTreeView() {
        getStyleClass().add("fxt-xml-tree");
        setCellFactory(tv -> new XmlNodeCell());
        getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null && onSelectionChanged != null) {
                onSelectionChanged.accept(newItem.getValue());
            }
        });
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
        item.setExpanded(true);
        nodeToItem.put(node.getId(), item);
        if (node instanceof XmlElement element) {
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
            String iconLiteral = "bi-code-slash";
            if (item instanceof XmlElement element) {
                StringBuilder label = new StringBuilder(element.getQualifiedName());
                for (Map.Entry<String, String> attr : element.getAttributes().entrySet()) {
                    label.append(' ').append(attr.getKey()).append("=\"").append(attr.getValue()).append('"');
                }
                setText(label.toString());
            } else if (item instanceof XmlText text) {
                setText("\"" + text.getText().strip() + "\"");
                iconLiteral = "bi-fonts";
            } else if (item instanceof XmlComment comment) {
                setText("<!-- " + comment.getText().strip() + " -->");
                iconLiteral = "bi-chat-left-text";
            } else if (item instanceof XmlCData cdata) {
                setText("<![CDATA[ " + cdata.getText().strip() + " ]]>");
                iconLiteral = "bi-file-earmark-code";
            } else if (item instanceof XmlProcessingInstruction pi) {
                setText("<?" + pi.getTarget() + " " + pi.getData() + "?>");
                iconLiteral = "bi-gear";
            } else if (item instanceof XmlDocument) {
                setText("(document)");
                iconLiteral = "bi-file-earmark";
            } else {
                setText(item.toString());
            }
            IconifyIcon icon = new IconifyIcon(iconLiteral);
            icon.setIconSize(14);
            setGraphic(icon);
        }

        /** Lazily builds this cell's context menu (a single "Go to Definition" item). */
        private ContextMenu cellMenu() {
            if (cellMenu == null) {
                MenuItem goToDefinitionItem = new MenuItem("Go to Definition");
                IconifyIcon menuIcon = new IconifyIcon("bi-box-arrow-up-right");
                menuIcon.setIconColor(Color.web("#17a2b8"));
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
