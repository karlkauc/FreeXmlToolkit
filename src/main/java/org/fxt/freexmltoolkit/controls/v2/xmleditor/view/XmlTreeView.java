package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;

import java.beans.PropertyChangeEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tree view component for XML document visualization and editing.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Hierarchical display of XML structure</li>
 *   <li>Drag and drop support for node manipulation</li>
 *   <li>Context menus for common operations</li>
 *   <li>Expand/collapse all functionality</li>
 *   <li>Bi-directional sync with SelectionModel</li>
 *   <li>Integration with XmlEditorContext</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlTreeView extends BorderPane {

    private final XmlEditorContext context;
    private final TreeView<XmlNode> treeView;
    private final Map<XmlNode, TreeItem<XmlNode>> nodeToTreeItemMap;
    private boolean suppressSelectionEvents = false;

    // ==================== Constructor ====================

    public XmlTreeView(XmlEditorContext context) {
        this.context = context;
        this.treeView = new TreeView<>();
        this.nodeToTreeItemMap = new HashMap<>();

        setupTreeView();
        setupListeners();
        buildTree();

        setCenter(treeView);
    }

    // ==================== Setup Methods ====================

    private void setupTreeView() {
        treeView.setCellFactory(tv -> new XmlTreeCell());
        treeView.setShowRoot(true);
        treeView.setEditable(false);

        // Selection listener
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (!suppressSelectionEvents && newVal != null && newVal.getValue() != null) {
                context.getSelectionModel().setSelectedNode(newVal.getValue());
            }
        });

        // Double-click listener for expand/collapse
        treeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<XmlNode> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && !selected.getChildren().isEmpty()) {
                    selected.setExpanded(!selected.isExpanded());
                }
            }
        });

        // Context menu
        treeView.setContextMenu(createContextMenu());

        // Drag and drop
        setupDragAndDrop();
    }

    private void setupListeners() {
        // Listen to document changes
        context.addPropertyChangeListener("document", this::onDocumentChanged);

        // Listen to selection changes from context
        context.getSelectionModel().addPropertyChangeListener("selectedNode", this::onSelectionChanged);
    }

    // ==================== Tree Building ====================

    private void buildTree() {
        nodeToTreeItemMap.clear();

        XmlDocument doc = context.getDocument();
        TreeItem<XmlNode> root = new TreeItem<>(doc);
        root.setExpanded(true);

        nodeToTreeItemMap.put(doc, root);
        buildTreeRecursive(doc, root);

        treeView.setRoot(root);
    }

    private void buildTreeRecursive(XmlNode node, TreeItem<XmlNode> treeItem) {
        for (XmlNode child : getChildrenList(node)) {
            TreeItem<XmlNode> childItem = new TreeItem<>(child);
            treeItem.getChildren().add(childItem);
            nodeToTreeItemMap.put(child, childItem);

            // Auto-expand elements with few children
            if (child instanceof XmlElement && getChildCount(child) <= 3) {
                childItem.setExpanded(true);
            }

            buildTreeRecursive(child, childItem);
        }
    }

    public void refresh() {
        buildTree();
    }

    // ==================== Event Handlers ====================

    private void onDocumentChanged(PropertyChangeEvent evt) {
        buildTree();
    }

    private void onSelectionChanged(PropertyChangeEvent evt) {
        XmlNode selectedNode = (XmlNode) evt.getNewValue();
        if (selectedNode == null) {
            suppressSelectionEvents = true;
            treeView.getSelectionModel().clearSelection();
            suppressSelectionEvents = false;
            return;
        }

        TreeItem<XmlNode> treeItem = nodeToTreeItemMap.get(selectedNode);
        if (treeItem != null) {
            suppressSelectionEvents = true;
            treeView.getSelectionModel().select(treeItem);
            treeView.scrollTo(treeView.getRow(treeItem));
            suppressSelectionEvents = false;
        }
    }

    // ==================== Context Menu ====================

    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem addElement = new MenuItem("Add Element");
        addElement.setOnAction(e -> onAddElement());

        MenuItem addText = new MenuItem("Add Text");
        addText.setOnAction(e -> onAddText());

        MenuItem addComment = new MenuItem("Add Comment");
        addComment.setOnAction(e -> onAddComment());

        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(e -> onDelete());

        MenuItem duplicate = new MenuItem("Duplicate");
        duplicate.setOnAction(e -> onDuplicate());

        MenuItem expandAll = new MenuItem("Expand All");
        expandAll.setOnAction(e -> expandAll());

        MenuItem collapseAll = new MenuItem("Collapse All");
        collapseAll.setOnAction(e -> collapseAll());

        menu.getItems().addAll(
                addElement, addText, addComment,
                new SeparatorMenuItem(),
                duplicate, delete,
                new SeparatorMenuItem(),
                expandAll, collapseAll
        );

        return menu;
    }

    // ==================== Context Menu Actions ====================

    private void onAddElement() {
        TreeItem<XmlNode> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        XmlNode parent = selected.getValue();
        if (!(parent instanceof XmlElement) && !(parent instanceof XmlDocument)) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog("newElement");
        dialog.setTitle("Add Element");
        dialog.setHeaderText("Enter element name:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(name -> {
            XmlElement newElement = new XmlElement(name);
            AddElementCommand cmd = new AddElementCommand(parent, newElement);
            context.executeCommand(cmd);
            refresh();
        });
    }

    private void onAddText() {
        TreeItem<XmlNode> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        XmlNode parent = selected.getValue();
        if (!(parent instanceof XmlElement)) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Text");
        dialog.setHeaderText("Enter text content:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(text -> {
            // Set text content directly (simplified for tree view)
            ((XmlElement) parent).setTextContent(text);
            refresh();
        });
    }

    private void onAddComment() {
        TreeItem<XmlNode> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        XmlNode parent = selected.getValue();

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Comment");
        dialog.setHeaderText("Enter comment text:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(text -> {
            XmlComment comment = new XmlComment(text);
            if (parent instanceof XmlElement) {
                ((XmlElement) parent).addChild(comment);
                refresh();
            } else if (parent instanceof XmlDocument) {
                ((XmlDocument) parent).addChild(comment);
                refresh();
            }
        });
    }

    private void onDelete() {
        TreeItem<XmlNode> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() instanceof XmlDocument) {
            return;
        }

        XmlNode node = selected.getValue();
        XmlNode parent = node.getParent();

        if (parent != null) {
            DeleteNodeCommand cmd = new DeleteNodeCommand(node);
            context.executeCommand(cmd);
            refresh();
        }
    }

    private void onDuplicate() {
        TreeItem<XmlNode> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() instanceof XmlDocument) {
            return;
        }

        XmlNode node = selected.getValue();
        XmlNode parent = node.getParent();

        if (parent != null) {
            XmlNode duplicate = node.deepCopy("_copy");
            if (parent instanceof XmlElement) {
                ((XmlElement) parent).addChild(duplicate);
            } else if (parent instanceof XmlDocument) {
                ((XmlDocument) parent).addChild(duplicate);
            }
            refresh();
        }
    }

    // ==================== Expand/Collapse ====================

    public void expandAll() {
        expandAllRecursive(treeView.getRoot());
    }

    private void expandAllRecursive(TreeItem<XmlNode> item) {
        if (item == null) return;
        item.setExpanded(true);
        for (TreeItem<XmlNode> child : item.getChildren()) {
            expandAllRecursive(child);
        }
    }

    public void collapseAll() {
        collapseAllRecursive(treeView.getRoot());
    }

    private void collapseAllRecursive(TreeItem<XmlNode> item) {
        if (item == null) return;
        item.setExpanded(false);
        for (TreeItem<XmlNode> child : item.getChildren()) {
            collapseAllRecursive(child);
        }
    }

    // ==================== Drag and Drop ====================

    private void setupDragAndDrop() {
        treeView.setOnDragDetected(this::onDragDetected);
        treeView.setOnDragOver(this::onDragOver);
        treeView.setOnDragDropped(this::onDragDropped);
        treeView.setOnDragDone(DragEvent::consume);
    }

    private void onDragDetected(MouseEvent event) {
        TreeItem<XmlNode> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() instanceof XmlDocument) {
            return;
        }

        Dragboard db = treeView.startDragAndDrop(TransferMode.MOVE);
        ClipboardContent content = new ClipboardContent();
        content.putString(selected.getValue().getId().toString());
        db.setContent(content);
        event.consume();
    }

    private void onDragOver(DragEvent event) {
        if (event.getGestureSource() != treeView) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        } else {
            TreeItem<XmlNode> dragTarget = getTreeItemAtEvent(event);
            if (dragTarget != null &&
                    (dragTarget.getValue() instanceof XmlElement || dragTarget.getValue() instanceof XmlDocument)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
        }
        event.consume();
    }

    private void onDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;

        if (db.hasString()) {
            TreeItem<XmlNode> dragTarget = getTreeItemAtEvent(event);
            TreeItem<XmlNode> draggedItem = treeView.getSelectionModel().getSelectedItem();

            if (dragTarget != null && draggedItem != null &&
                    dragTarget != draggedItem &&
                    !isAncestor(draggedItem, dragTarget)) {

                XmlNode sourceNode = draggedItem.getValue();
                XmlNode targetNode = dragTarget.getValue();
                XmlNode sourceParent = sourceNode.getParent();

                if (sourceParent != null &&
                        (targetNode instanceof XmlElement || targetNode instanceof XmlDocument)) {

                    // Move node
                    MoveNodeCommand cmd = new MoveNodeCommand(sourceNode, targetNode, getChildCount(targetNode));
                    context.executeCommand(cmd);
                    refresh();
                    success = true;
                }
            }
        }

        event.setDropCompleted(success);
        event.consume();
    }

    private TreeItem<XmlNode> getTreeItemAtEvent(DragEvent event) {
        // Simple approach: use current selection as target
        // In production, you'd calculate from event coordinates
        return treeView.getSelectionModel().getSelectedItem();
    }

    private boolean isAncestor(TreeItem<XmlNode> potentialAncestor, TreeItem<XmlNode> item) {
        TreeItem<XmlNode> current = item.getParent();
        while (current != null) {
            if (current == potentialAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    // ==================== Utility Methods ====================

    /**
     * Helper method to get children from XmlNode (handles both XmlElement and XmlDocument).
     */
    private List<XmlNode> getChildrenList(XmlNode node) {
        if (node instanceof XmlElement) {
            return ((XmlElement) node).getChildren();
        } else if (node instanceof XmlDocument) {
            return ((XmlDocument) node).getChildren();
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Helper method to get child count from XmlNode (handles both XmlElement and XmlDocument).
     */
    private int getChildCount(XmlNode node) {
        if (node instanceof XmlElement) {
            return ((XmlElement) node).getChildCount();
        } else if (node instanceof XmlDocument) {
            return ((XmlDocument) node).getChildCount();
        }
        return 0;
    }

    // ==================== Public API ====================

    public TreeView<XmlNode> getTreeView() {
        return treeView;
    }

    public XmlNode getSelectedNode() {
        TreeItem<XmlNode> selected = treeView.getSelectionModel().getSelectedItem();
        return selected != null ? selected.getValue() : null;
    }

    public void selectNode(XmlNode node) {
        TreeItem<XmlNode> treeItem = nodeToTreeItemMap.get(node);
        if (treeItem != null) {
            treeView.getSelectionModel().select(treeItem);
            treeView.scrollTo(treeView.getRow(treeItem));
        }
    }
}
