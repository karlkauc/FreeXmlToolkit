package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Optional;

/**
 * Context menu for XML Grid View with all editing operations.
 */
public class XmlGridContextMenu {

    private final XmlEditorContext context;
    private final ContextMenu contextMenu;
    private final Runnable refreshCallback;

    // Clipboard
    private XmlElement clipboardElement;
    private boolean isCut;

    // Menu Items
    private MenuItem addElementItem;
    private MenuItem addAttributeItem;
    private MenuItem addTextItem;
    private MenuItem addSiblingBeforeItem;
    private MenuItem addSiblingAfterItem;
    private MenuItem renameItem;
    private MenuItem duplicateItem;
    private MenuItem copyItem;
    private MenuItem cutItem;
    private MenuItem pasteItem;
    private MenuItem pasteAsChildItem;
    private MenuItem moveUpItem;
    private MenuItem moveDownItem;
    private MenuItem deleteItem;
    private MenuItem expandAllItem;
    private MenuItem collapseAllItem;

    public XmlGridContextMenu(XmlEditorContext context, Runnable refreshCallback) {
        this.context = context;
        this.refreshCallback = refreshCallback;
        this.contextMenu = buildContextMenu();
    }

    private ContextMenu buildContextMenu() {
        ContextMenu menu = new ContextMenu();

        // === Add Submenu ===
        Menu addMenu = new Menu("Add");
        addMenu.setGraphic(createIcon(BootstrapIcons.PLUS_CIRCLE));

        addElementItem = new MenuItem("Child Element");
        addElementItem.setGraphic(createIcon(BootstrapIcons.CODE_SLASH));
        addElementItem.setOnAction(e -> addChildElement());

        addAttributeItem = new MenuItem("Attribute");
        addAttributeItem.setGraphic(createIcon(BootstrapIcons.AT));
        addAttributeItem.setOnAction(e -> addAttribute());

        addTextItem = new MenuItem("Text Content");
        addTextItem.setGraphic(createIcon(BootstrapIcons.FONTS));
        addTextItem.setOnAction(e -> addTextContent());

        addSiblingBeforeItem = new MenuItem("Sibling Before");
        addSiblingBeforeItem.setGraphic(createIcon(BootstrapIcons.ARROW_UP));
        addSiblingBeforeItem.setOnAction(e -> addSiblingElement(true));

        addSiblingAfterItem = new MenuItem("Sibling After");
        addSiblingAfterItem.setGraphic(createIcon(BootstrapIcons.ARROW_DOWN));
        addSiblingAfterItem.setOnAction(e -> addSiblingElement(false));

        addMenu.getItems().addAll(
                addElementItem, addAttributeItem, addTextItem,
                new SeparatorMenuItem(),
                addSiblingBeforeItem, addSiblingAfterItem
        );

        // === Edit Items ===
        renameItem = new MenuItem("Rename");
        renameItem.setGraphic(createIcon(BootstrapIcons.PENCIL));
        renameItem.setOnAction(e -> renameElement());
        renameItem.setAccelerator(new KeyCodeCombination(KeyCode.F2));

        duplicateItem = new MenuItem("Duplicate");
        duplicateItem.setGraphic(createIcon(BootstrapIcons.FILES));
        duplicateItem.setOnAction(e -> duplicateElement());
        duplicateItem.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN));

        // === Clipboard ===
        copyItem = new MenuItem("Copy");
        copyItem.setGraphic(createIcon(BootstrapIcons.CLIPBOARD));
        copyItem.setOnAction(e -> copyElement());
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));

        cutItem = new MenuItem("Cut");
        cutItem.setGraphic(createIcon(BootstrapIcons.SCISSORS));
        cutItem.setOnAction(e -> cutElement());
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));

        pasteItem = new MenuItem("Paste as Sibling");
        pasteItem.setGraphic(createIcon(BootstrapIcons.CLIPBOARD_CHECK));
        pasteItem.setOnAction(e -> pasteAsSibling());
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));

        pasteAsChildItem = new MenuItem("Paste as Child");
        pasteAsChildItem.setGraphic(createIcon(BootstrapIcons.CLIPBOARD_PLUS));
        pasteAsChildItem.setOnAction(e -> pasteAsChild());
        pasteAsChildItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        // === Move ===
        moveUpItem = new MenuItem("Move Up");
        moveUpItem.setGraphic(createIcon(BootstrapIcons.ARROW_UP_CIRCLE));
        moveUpItem.setOnAction(e -> moveElement(-1));
        moveUpItem.setAccelerator(new KeyCodeCombination(KeyCode.UP, KeyCombination.ALT_DOWN));

        moveDownItem = new MenuItem("Move Down");
        moveDownItem.setGraphic(createIcon(BootstrapIcons.ARROW_DOWN_CIRCLE));
        moveDownItem.setOnAction(e -> moveElement(1));
        moveDownItem.setAccelerator(new KeyCodeCombination(KeyCode.DOWN, KeyCombination.ALT_DOWN));

        // === Expand/Collapse ===
        expandAllItem = new MenuItem("Expand All");
        expandAllItem.setGraphic(createIcon(BootstrapIcons.ARROWS_EXPAND));
        expandAllItem.setOnAction(e -> expandAll());

        collapseAllItem = new MenuItem("Collapse All");
        collapseAllItem.setGraphic(createIcon(BootstrapIcons.ARROWS_COLLAPSE));
        collapseAllItem.setOnAction(e -> collapseAll());

        // === Delete ===
        deleteItem = new MenuItem("Delete");
        deleteItem.setGraphic(createIcon(BootstrapIcons.TRASH));
        deleteItem.setOnAction(e -> deleteElement());
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));

        // Build menu
        menu.getItems().addAll(
                addMenu,
                new SeparatorMenuItem(),
                renameItem, duplicateItem,
                new SeparatorMenuItem(),
                copyItem, cutItem, pasteItem, pasteAsChildItem,
                new SeparatorMenuItem(),
                moveUpItem, moveDownItem,
                new SeparatorMenuItem(),
                expandAllItem, collapseAllItem,
                new SeparatorMenuItem(),
                deleteItem
        );

        return menu;
    }

    private FontIcon createIcon(BootstrapIcons icon) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(14);
        return fontIcon;
    }

    public void show(Node anchor, double screenX, double screenY, XmlNode selectedNode) {
        updateMenuState(selectedNode);
        contextMenu.show(anchor, screenX, screenY);
    }

    public void hide() {
        contextMenu.hide();
    }

    private void updateMenuState(XmlNode node) {
        boolean hasSelection = node != null;
        boolean isElement = node instanceof XmlElement;
        boolean isRoot = node != null && node.getParent() instanceof XmlDocument;
        boolean canMove = isElement && !isRoot && node.getParent() != null;
        boolean hasClipboard = clipboardElement != null;

        addElementItem.setDisable(!isElement);
        addAttributeItem.setDisable(!isElement);
        addTextItem.setDisable(!isElement);
        addSiblingBeforeItem.setDisable(!isElement || isRoot);
        addSiblingAfterItem.setDisable(!isElement || isRoot);
        renameItem.setDisable(!isElement);
        duplicateItem.setDisable(!isElement || isRoot);
        copyItem.setDisable(!isElement);
        cutItem.setDisable(!isElement || isRoot);
        pasteItem.setDisable(!hasClipboard || !isElement || isRoot);
        pasteAsChildItem.setDisable(!hasClipboard || !isElement);
        moveUpItem.setDisable(!canMove || isFirstChild(node));
        moveDownItem.setDisable(!canMove || isLastChild(node));
        deleteItem.setDisable(!hasSelection || isRoot);
    }

    private boolean isFirstChild(XmlNode node) {
        if (node == null || node.getParent() == null) return true;
        XmlNode parent = node.getParent();
        if (parent instanceof XmlElement) {
            List<XmlNode> children = ((XmlElement) parent).getChildren();
            return children.indexOf(node) == 0;
        } else if (parent instanceof XmlDocument) {
            List<XmlNode> children = ((XmlDocument) parent).getChildren();
            return children.indexOf(node) == 0;
        }
        return true;
    }

    private boolean isLastChild(XmlNode node) {
        if (node == null || node.getParent() == null) return true;
        XmlNode parent = node.getParent();
        if (parent instanceof XmlElement) {
            List<XmlNode> children = ((XmlElement) parent).getChildren();
            return children.indexOf(node) == children.size() - 1;
        } else if (parent instanceof XmlDocument) {
            List<XmlNode> children = ((XmlDocument) parent).getChildren();
            return children.indexOf(node) == children.size() - 1;
        }
        return true;
    }

    // ==================== Actions ====================

    private void addChildElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        String name = showInputDialog("Add Child Element", "Element name:", "newElement");
        if (name == null || name.trim().isEmpty()) return;

        XmlElement parent = (XmlElement) selected;
        XmlElement child = new XmlElement(name.trim());

        XmlCommand cmd = new AddElementCommand(parent, child);
        context.executeCommand(cmd);
        refresh();
    }

    private void addAttribute() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        String name = showInputDialog("Add Attribute", "Attribute name:", "newAttribute");
        if (name == null || name.trim().isEmpty()) return;

        String value = showInputDialog("Add Attribute", "Attribute value:", "");
        if (value == null) return;

        XmlElement element = (XmlElement) selected;
        XmlCommand cmd = new SetAttributeCommand(element, name.trim(), value);
        context.executeCommand(cmd);
        refresh();
    }

    private void addTextContent() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        String text = showInputDialog("Add Text Content", "Text:", "");
        if (text == null) return;

        XmlElement element = (XmlElement) selected;
        XmlCommand cmd = new SetElementTextCommand(element, text);
        context.executeCommand(cmd);
        refresh();
    }

    private void addSiblingElement(boolean before) {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlNode parent = selected.getParent();
        if (!(parent instanceof XmlElement)) return;

        String name = showInputDialog("Add Sibling Element", "Element name:", "newElement");
        if (name == null || name.trim().isEmpty()) return;

        XmlElement parentElement = (XmlElement) parent;
        XmlElement sibling = new XmlElement(name.trim());

        int index = parentElement.getChildren().indexOf(selected);
        if (!before) index++;

        XmlCommand cmd = new AddElementCommand(parentElement, sibling, index);
        context.executeCommand(cmd);
        refresh();
    }

    private void renameElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlElement element = (XmlElement) selected;
        String newName = showInputDialog("Rename Element", "New name:", element.getName());
        if (newName == null || newName.trim().isEmpty()) return;

        XmlCommand cmd = new RenameNodeCommand(element, newName.trim());
        context.executeCommand(cmd);
        refresh();
    }

    private void duplicateElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlNode parent = selected.getParent();
        if (!(parent instanceof XmlElement)) return;

        XmlElement element = (XmlElement) selected;
        XmlElement copy = (XmlElement) element.deepCopy("");
        XmlElement parentElement = (XmlElement) parent;

        int index = parentElement.getChildren().indexOf(selected) + 1;
        XmlCommand cmd = new AddElementCommand(parentElement, copy, index);
        context.executeCommand(cmd);
        refresh();
    }

    private void copyElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        clipboardElement = (XmlElement) ((XmlElement) selected).deepCopy("");
        isCut = false;
    }

    private void cutElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        clipboardElement = (XmlElement) selected;
        isCut = true;
    }

    private void pasteAsSibling() {
        if (clipboardElement == null) return;

        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlNode parent = selected.getParent();
        if (!(parent instanceof XmlElement)) return;

        XmlElement parentElement = (XmlElement) parent;
        XmlElement toPaste = isCut ? clipboardElement : (XmlElement) clipboardElement.deepCopy("");

        int index = parentElement.getChildren().indexOf(selected) + 1;

        if (isCut) {
            // First delete, then add
            XmlCommand deleteCmd = new DeleteNodeCommand(clipboardElement);
            context.executeCommand(deleteCmd);
            clipboardElement = null;
            isCut = false;
        }

        XmlCommand addCmd = new AddElementCommand(parentElement, toPaste, index);
        context.executeCommand(addCmd);
        refresh();
    }

    private void pasteAsChild() {
        if (clipboardElement == null) return;

        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlElement parentElement = (XmlElement) selected;
        XmlElement toPaste = isCut ? clipboardElement : (XmlElement) clipboardElement.deepCopy("");

        if (isCut) {
            XmlCommand deleteCmd = new DeleteNodeCommand(clipboardElement);
            context.executeCommand(deleteCmd);
            clipboardElement = null;
            isCut = false;
        }

        XmlCommand addCmd = new AddElementCommand(parentElement, toPaste);
        context.executeCommand(addCmd);
        refresh();
    }

    private void moveElement(int direction) {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (!(selected instanceof XmlElement)) return;

        XmlNode parent = selected.getParent();
        if (!(parent instanceof XmlElement)) return;

        XmlElement parentElement = (XmlElement) parent;
        List<XmlNode> children = parentElement.getChildren();
        int currentIndex = children.indexOf(selected);
        int newIndex = currentIndex + direction;

        if (newIndex < 0 || newIndex >= children.size()) return;

        XmlCommand cmd = new MoveNodeCommand((XmlElement) selected, parentElement, newIndex);
        context.executeCommand(cmd);
        refresh();
    }

    private void deleteElement() {
        XmlNode selected = context.getSelectionModel().getSelectedNode();
        if (selected == null) return;

        // Confirm deletion
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Element");
        alert.setHeaderText("Delete \"" + getNodeName(selected) + "\"?");
        alert.setContentText("This action cannot be undone from this dialog.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            XmlCommand cmd = new DeleteNodeCommand(selected);
            context.executeCommand(cmd);
            context.getSelectionModel().clearSelection();
            refresh();
        }
    }

    private void expandAll() {
        // This will be handled by the view
        if (refreshCallback != null) {
            // Signal to expand all nodes
        }
    }

    private void collapseAll() {
        // This will be handled by the view
        if (refreshCallback != null) {
            // Signal to collapse all nodes
        }
    }

    private String getNodeName(XmlNode node) {
        if (node instanceof XmlElement) {
            return ((XmlElement) node).getName();
        } else if (node instanceof XmlText) {
            String text = ((XmlText) node).getText();
            return text.length() > 20 ? text.substring(0, 20) + "..." : text;
        }
        return "Node";
    }

    private String showInputDialog(String title, String header, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(null);

        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    private void refresh() {
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }

    // ==================== Public Keyboard Actions ====================

    public void handleKeyPress(javafx.scene.input.KeyEvent event, XmlNode selectedNode) {
        if (selectedNode == null) return;

        if (event.getCode() == KeyCode.DELETE) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            deleteElement();
            event.consume();
        } else if (event.getCode() == KeyCode.F2) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            renameElement();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.C) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            copyElement();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.X) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            cutElement();
            event.consume();
        } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.V) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            pasteAsChild();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.V) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            pasteAsSibling();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.D) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            duplicateElement();
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.UP) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            moveElement(-1);
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.DOWN) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            moveElement(1);
            event.consume();
        }
    }

    public boolean hasClipboard() {
        return clipboardElement != null;
    }
}
