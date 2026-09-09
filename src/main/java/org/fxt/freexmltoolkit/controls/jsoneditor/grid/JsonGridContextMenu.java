package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddArrayItemCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddPropertyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.ChangeValueTypeCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.DeleteNodeCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.DuplicateNodeCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.MoveNodeCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.RenameKeyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.ReplaceNodeCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SortArrayCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.theme.DesignTokens;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridContextMenu;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;

/**
 * Context menu of the JSON grid: the JSON counterpart of {@code XmlGridContextMenu} with
 * the same structure, icons and accelerators — Add (Property / Array Item / Sibling
 * Before / After), Rename Key, Duplicate, Copy / Cut / Paste, Copy Cell Content, Copy
 * JSONPath, Copy Node (JSON), Change Type, Move Up / Down, Expand / Collapse All, Sort
 * Column and Delete. All edits go through the context's command stack.
 */
public class JsonGridContextMenu implements GridContextMenu<JsonNode> {

    private static final Logger logger = LogManager.getLogger(JsonGridContextMenu.class);

    /** The value types offered when adding a node or changing its type. */
    static final List<JsonNodeType> VALUE_TYPES = List.of(JsonNodeType.STRING, JsonNodeType.NUMBER,
            JsonNodeType.BOOLEAN, JsonNodeType.NULL, JsonNodeType.OBJECT, JsonNodeType.ARRAY);

    private final JsonEditorContext context;
    private final ContextMenu contextMenu;
    private final Runnable refreshCallback;

    // Clipboard
    private JsonNode clipboardNode;
    private boolean isCut;

    // Table cell context (for grid operations)
    private RepeatingElementsTable clickedTable;
    private int clickedRowIndex = -1;
    private String clickedColumnName;

    // Menu Items
    private MenuItem addPropertyItem;
    private MenuItem addArrayItemItem;
    private MenuItem addSiblingBeforeItem;
    private MenuItem addSiblingAfterItem;
    private MenuItem renameItem;
    private MenuItem duplicateItem;
    private MenuItem copyItem;
    private MenuItem cutItem;
    private MenuItem pasteItem;
    private MenuItem pasteAsChildItem;
    private MenuItem copyCellContentItem;
    private MenuItem copyPathItem;
    private MenuItem copyNodeItem;
    private Menu changeTypeMenu;
    private MenuItem moveUpItem;
    private MenuItem moveDownItem;
    private MenuItem deleteItem;
    private MenuItem expandAllItem;
    private MenuItem collapseAllItem;

    // Sort menu items
    private Menu sortMenu;
    private MenuItem sortAscendingItem;
    private MenuItem sortDescendingItem;

    public JsonGridContextMenu(JsonEditorContext context, Runnable refreshCallback) {
        this.context = context;
        this.refreshCallback = refreshCallback;
        this.contextMenu = buildContextMenu();
    }

    private ContextMenu buildContextMenu() {
        ContextMenu menu = new ContextMenu();

        // === Add Submenu ===
        Menu addMenu = new Menu("Add");
        addMenu.setGraphic(createColoredIcon("bi-plus-circle", DesignTokens.ColorToken.SUCCESS));

        addPropertyItem = new MenuItem("Property");
        addPropertyItem.setGraphic(createColoredIcon("bi-braces", DesignTokens.ColorToken.SUCCESS));
        addPropertyItem.setOnAction(e -> addProperty());

        addArrayItemItem = new MenuItem("Array Item");
        addArrayItemItem.setGraphic(createColoredIcon("bi-list-ol", DesignTokens.ColorToken.SUCCESS));
        addArrayItemItem.setOnAction(e -> addArrayItem());

        addSiblingBeforeItem = new MenuItem("Sibling Before");
        addSiblingBeforeItem.setGraphic(createColoredIcon("bi-arrow-up", DesignTokens.ColorToken.SUCCESS));
        addSiblingBeforeItem.setOnAction(e -> addSibling(true));

        addSiblingAfterItem = new MenuItem("Sibling After");
        addSiblingAfterItem.setGraphic(createColoredIcon("bi-arrow-down", DesignTokens.ColorToken.SUCCESS));
        addSiblingAfterItem.setOnAction(e -> addSibling(false));

        addMenu.getItems().addAll(
                addPropertyItem, addArrayItemItem,
                new SeparatorMenuItem(),
                addSiblingBeforeItem, addSiblingAfterItem
        );

        // === Edit Items ===
        renameItem = new MenuItem("Rename Key");
        renameItem.setGraphic(createColoredIcon("bi-pencil", DesignTokens.ColorToken.ACCENT));
        renameItem.setOnAction(e -> renameKey());
        renameItem.setAccelerator(new KeyCodeCombination(KeyCode.F2));

        duplicateItem = new MenuItem("Duplicate");
        duplicateItem.setGraphic(createColoredIcon("bi-files", DesignTokens.ColorToken.TEAL));
        duplicateItem.setOnAction(e -> duplicateNode());
        duplicateItem.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN));

        // === Clipboard ===
        copyItem = new MenuItem("Copy");
        copyItem.setGraphic(createColoredIcon("bi-clipboard", DesignTokens.ColorToken.NEUTRAL));
        copyItem.setOnAction(e -> copyNodeToInternalClipboard());
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));

        cutItem = new MenuItem("Cut");
        cutItem.setGraphic(createColoredIcon("bi-scissors", DesignTokens.ColorToken.ACCENT));
        cutItem.setOnAction(e -> cutNode());
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));

        pasteItem = new MenuItem("Paste as Sibling");
        pasteItem.setGraphic(createColoredIcon("bi-clipboard-check", DesignTokens.ColorToken.NEUTRAL));
        pasteItem.setOnAction(e -> pasteAsSibling());
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));

        pasteAsChildItem = new MenuItem("Paste as Child");
        pasteAsChildItem.setGraphic(createColoredIcon("bi-clipboard-plus", DesignTokens.ColorToken.NEUTRAL));
        pasteAsChildItem.setOnAction(e -> pasteAsChild());
        pasteAsChildItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        copyCellContentItem = new MenuItem("Copy Cell Content");
        copyCellContentItem.setGraphic(createColoredIcon("bi-clipboard-data", DesignTokens.ColorToken.INFO));
        copyCellContentItem.setOnAction(e -> copyCellContent());
        copyCellContentItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        copyPathItem = new MenuItem("Copy JSONPath");
        copyPathItem.setGraphic(createColoredIcon("bi-diagram-3", DesignTokens.ColorToken.PURPLE));
        copyPathItem.setOnAction(e -> copyPath());
        copyPathItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        copyNodeItem = new MenuItem("Copy Node (JSON)");
        copyNodeItem.setGraphic(createColoredIcon("bi-clipboard-data", DesignTokens.ColorToken.PURPLE));
        copyNodeItem.setOnAction(e -> copyNodeJson());
        copyNodeItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN));

        // === Change Type ===
        changeTypeMenu = new Menu("Change Type");
        changeTypeMenu.setGraphic(createColoredIcon("bi-arrow-repeat", DesignTokens.ColorToken.INFO));
        for (JsonNodeType type : VALUE_TYPES) {
            MenuItem item = new MenuItem(typeLabel(type));
            item.setGraphic(createColoredIcon(typeIcon(type), DesignTokens.ColorToken.INFO));
            item.setOnAction(e -> changeType(type));
            changeTypeMenu.getItems().add(item);
        }

        // === Move ===
        moveUpItem = new MenuItem("Move Up");
        moveUpItem.setGraphic(createColoredIcon("bi-arrow-up-circle", DesignTokens.ColorToken.NEUTRAL));
        moveUpItem.setOnAction(e -> moveNode(-1));
        moveUpItem.setAccelerator(new KeyCodeCombination(KeyCode.UP, KeyCombination.ALT_DOWN));

        moveDownItem = new MenuItem("Move Down");
        moveDownItem.setGraphic(createColoredIcon("bi-arrow-down-circle", DesignTokens.ColorToken.NEUTRAL));
        moveDownItem.setOnAction(e -> moveNode(1));
        moveDownItem.setAccelerator(new KeyCodeCombination(KeyCode.DOWN, KeyCombination.ALT_DOWN));

        // === Expand/Collapse ===
        expandAllItem = new MenuItem("Expand All");
        expandAllItem.setGraphic(createColoredIcon("bi-arrows-expand", DesignTokens.ColorToken.PRIMARY));
        expandAllItem.setOnAction(e -> expandAll());

        collapseAllItem = new MenuItem("Collapse All");
        collapseAllItem.setGraphic(createColoredIcon("bi-arrows-collapse", DesignTokens.ColorToken.PRIMARY));
        collapseAllItem.setOnAction(e -> collapseAll());

        // === Sort (for table columns) ===
        sortMenu = new Menu("Sort Column");
        sortMenu.setGraphic(createColoredIcon("bi-sort-down", DesignTokens.ColorToken.PRIMARY));

        sortAscendingItem = new MenuItem("Sort Ascending");
        sortAscendingItem.setGraphic(createColoredIcon("bi-sort-up", DesignTokens.ColorToken.SUCCESS));
        sortAscendingItem.setOnAction(e -> sortColumn(true));

        sortDescendingItem = new MenuItem("Sort Descending");
        sortDescendingItem.setGraphic(createColoredIcon("bi-sort-down-alt", DesignTokens.ColorToken.DANGER));
        sortDescendingItem.setOnAction(e -> sortColumn(false));

        sortMenu.getItems().addAll(sortAscendingItem, sortDescendingItem);

        // === Delete ===
        deleteItem = new MenuItem("Delete");
        deleteItem.setGraphic(createColoredIcon("bi-trash", DesignTokens.ColorToken.DANGER));
        deleteItem.setOnAction(e -> deleteNode());
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));

        menu.getItems().addAll(
                addMenu,
                new SeparatorMenuItem(),
                renameItem, duplicateItem,
                new SeparatorMenuItem(),
                copyItem, cutItem, pasteItem, pasteAsChildItem,
                new SeparatorMenuItem(),
                copyCellContentItem, copyPathItem, copyNodeItem,
                new SeparatorMenuItem(),
                changeTypeMenu,
                new SeparatorMenuItem(),
                moveUpItem, moveDownItem,
                new SeparatorMenuItem(),
                expandAllItem, collapseAllItem,
                new SeparatorMenuItem(),
                sortMenu,
                new SeparatorMenuItem(),
                deleteItem
        );

        // Apply uniform font styling to match the XML grid and other menus
        menu.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");
        return menu;
    }

    private IconifyIcon createColoredIcon(String icon, DesignTokens.ColorToken color) {
        IconifyIcon fontIcon = new IconifyIcon(icon);
        fontIcon.setIconSize(12);
        return SemanticIcon.paint(fontIcon, color);
    }

    static String typeLabel(JsonNodeType type) {
        return switch (type) {
            case STRING -> "String";
            case NUMBER -> "Number";
            case BOOLEAN -> "Boolean";
            case NULL -> "Null";
            case OBJECT -> "Object";
            case ARRAY -> "Array";
            default -> type.name();
        };
    }

    private static String typeIcon(JsonNodeType type) {
        return switch (type) {
            case STRING -> "bi-quote";
            case NUMBER -> "bi-123";
            case BOOLEAN -> "bi-toggle-on";
            case NULL -> "bi-slash-circle";
            case OBJECT -> "bi-braces";
            case ARRAY -> "bi-list-ol";
            default -> "bi-question-circle";
        };
    }

    /** Creates an empty node of the given type ({@code ""}, {@code 0}, {@code false}, {@code null}, {@code {}}, {@code []}). */
    static JsonNode newNode(JsonNodeType type) {
        return switch (type) {
            case NUMBER -> new JsonPrimitive(BigInteger.ZERO);
            case BOOLEAN -> new JsonPrimitive(Boolean.FALSE);
            case NULL -> JsonPrimitive.nullValue();
            case OBJECT -> new JsonObject();
            case ARRAY -> new JsonArray();
            default -> new JsonPrimitive("");
        };
    }

    // ==================== Showing ====================

    @Override
    public void show(Node anchor, double screenX, double screenY, JsonNode selectedNode) {
        clickedTable = null;
        clickedRowIndex = -1;
        clickedColumnName = null;
        updateMenuState(selectedNode);
        contextMenu.show(anchor, screenX, screenY);
    }

    @Override
    public void show(Node anchor, double screenX, double screenY, JsonNode selectedNode,
                     RepeatingElementsTable table, int rowIndex, String columnName) {
        clickedTable = table;
        clickedRowIndex = rowIndex;
        clickedColumnName = columnName;
        updateMenuState(selectedNode);
        contextMenu.show(anchor, screenX, screenY);
    }

    @Override
    public void hide() {
        contextMenu.hide();
    }

    /** Enables/disables the items for the node; package-private test seam. */
    void updateMenuState(JsonNode node) {
        boolean hasSelection = node != null;
        boolean isRoot = isRoot(node);
        boolean isObject = node instanceof JsonObject;
        boolean isArray = node instanceof JsonArray;
        boolean parentIsObject = node != null && node.getParent() instanceof JsonObject;
        boolean parentIsArray = node != null && node.getParent() instanceof JsonArray;
        boolean hasParent = parentIsObject || parentIsArray;
        boolean hasClipboard = clipboardNode != null;
        boolean isInTableCell = clickedTable != null && clickedRowIndex >= 0 && clickedColumnName != null;

        addPropertyItem.setDisable(!(isObject || parentIsObject));
        addArrayItemItem.setDisable(!(isArray || parentIsArray));
        addSiblingBeforeItem.setDisable(!hasParent);
        addSiblingAfterItem.setDisable(!hasParent);
        renameItem.setDisable(!parentIsObject);
        duplicateItem.setDisable(!hasParent);
        copyItem.setDisable(!hasSelection);
        cutItem.setDisable(!hasParent);
        pasteItem.setDisable(!hasClipboard || !hasParent);
        pasteAsChildItem.setDisable(!hasClipboard || !(isObject || isArray));
        copyCellContentItem.setDisable(!isInTableCell && !(node instanceof JsonPrimitive));
        copyPathItem.setDisable(!hasSelection);
        copyNodeItem.setDisable(!hasSelection);
        changeTypeMenu.setDisable(!hasSelection || isRoot);
        moveUpItem.setDisable(!hasParent || isFirstChild(node));
        moveDownItem.setDisable(!hasParent || isLastChild(node));
        deleteItem.setDisable(!hasSelection || isRoot);

        boolean isInTableColumn = clickedTable != null && clickedColumnName != null;
        boolean canSort = isInTableColumn && clickedTable.isColumnSortable(clickedColumnName);
        sortMenu.setVisible(canSort);
    }

    private static boolean isRoot(JsonNode node) {
        return node != null && (node.getParent() == null || node.getParent() instanceof JsonDocument);
    }

    private static boolean isFirstChild(JsonNode node) {
        JsonNode parent = node != null ? node.getParent() : null;
        return parent == null || parent.indexOf(node) == 0;
    }

    private static boolean isLastChild(JsonNode node) {
        JsonNode parent = node != null ? node.getParent() : null;
        return parent == null || parent.indexOf(node) == parent.getChildCount() - 1;
    }

    // ==================== Actions ====================

    private JsonNode selected() {
        return context.getSelectionModel().getSelectedNode();
    }

    /** Adds a property to the selected object, or next to the selected property. */
    private void addProperty() {
        JsonNode selected = selected();
        JsonObject target;
        int index;
        if (selected instanceof JsonObject object) {
            target = object;
            index = -1;
        } else if (selected != null && selected.getParent() instanceof JsonObject parent) {
            target = parent;
            index = parent.indexOf(selected) + 1;
        } else {
            return;
        }
        NewValue value = showNewValueDialog("Add Property", true, "newProperty");
        if (value == null) {
            return;
        }
        if (target.hasProperty(value.key())) {
            showWarningAlert("Cannot Add Property", "The object already has a property \"" + value.key() + "\".");
            return;
        }
        execute(new AddPropertyCommand(target, value.key(), newNode(value.type()), index));
    }

    /** Appends an item to the selected array, or inserts one after the selected item. */
    private void addArrayItem() {
        JsonNode selected = selected();
        JsonArray target;
        int index;
        if (selected instanceof JsonArray array) {
            target = array;
            index = -1;
        } else if (selected != null && selected.getParent() instanceof JsonArray parent) {
            target = parent;
            index = parent.indexOf(selected) + 1;
        } else {
            return;
        }
        NewValue value = showNewValueDialog("Add Array Item", false, null);
        if (value == null) {
            return;
        }
        execute(new AddArrayItemCommand(target, index, newNode(value.type())));
    }

    private void addSibling(boolean before) {
        JsonNode selected = selected();
        if (selected == null) {
            return;
        }
        JsonNode parent = selected.getParent();
        int index = parent != null ? parent.indexOf(selected) + (before ? 0 : 1) : -1;
        if (parent instanceof JsonObject object) {
            NewValue value = showNewValueDialog("Add Sibling Property", true, "newProperty");
            if (value == null) {
                return;
            }
            if (object.hasProperty(value.key())) {
                showWarningAlert("Cannot Add Property", "The object already has a property \"" + value.key() + "\".");
                return;
            }
            execute(new AddPropertyCommand(object, value.key(), newNode(value.type()), index));
        } else if (parent instanceof JsonArray array) {
            NewValue value = showNewValueDialog("Add Sibling Item", false, null);
            if (value == null) {
                return;
            }
            execute(new AddArrayItemCommand(array, index, newNode(value.type())));
        }
    }

    private void renameKey() {
        JsonNode selected = selected();
        if (selected == null || !(selected.getParent() instanceof JsonObject parent)) {
            return;
        }
        String newKey = showInputDialog("Rename Key", "New key:", selected.getKey());
        if (newKey == null || newKey.trim().isEmpty() || newKey.trim().equals(selected.getKey())) {
            return;
        }
        if (parent.hasProperty(newKey.trim())) {
            showWarningAlert("Cannot Rename Key", "The object already has a property \"" + newKey.trim() + "\".");
            return;
        }
        execute(new RenameKeyCommand(selected, newKey.trim()));
    }

    private void duplicateNode() {
        JsonNode selected = selected();
        if (selected == null || isRoot(selected)) {
            return;
        }
        execute(new DuplicateNodeCommand(selected));
    }

    private void copyNodeToInternalClipboard() {
        JsonNode selected = selected();
        if (selected == null) {
            return;
        }
        clipboardNode = selected.deepCopy();
        clipboardNode.setKey(selected.getKey());
        isCut = false;
    }

    private void cutNode() {
        JsonNode selected = selected();
        if (selected == null || isRoot(selected)) {
            return;
        }
        clipboardNode = selected;
        isCut = true;
    }

    private void pasteAsSibling() {
        JsonNode selected = selected();
        if (clipboardNode == null || selected == null || isRoot(selected)) {
            return;
        }
        JsonNode parent = selected.getParent();
        int index = parent.indexOf(selected) + 1;
        pasteInto(parent, index, selected);
    }

    private void pasteAsChild() {
        JsonNode selected = selected();
        if (clipboardNode == null || !(selected instanceof JsonObject || selected instanceof JsonArray)) {
            return;
        }
        pasteInto(selected, -1, null);
    }

    /**
     * Pastes the internal clipboard into a container; a cut node is moved (delete + add as
     * two commands, like the XML grid), a copied node is deep-copied. Object targets ask for
     * the key, defaulting to a unique variant of the clipboard node's key.
     */
    private void pasteInto(JsonNode target, int index, JsonNode anchor) {
        JsonNode toPaste = isCut ? clipboardNode : clipboardNode.deepCopy();
        if (target instanceof JsonObject object) {
            String defaultKey = uniqueKey(object, clipboardNode.getKey() != null ? clipboardNode.getKey() : "pasted");
            String key = showInputDialog("Paste Property", "Key:", defaultKey);
            if (key == null || key.trim().isEmpty()) {
                return;
            }
            key = key.trim();
            if (object.hasProperty(key) && object.getProperty(key) != clipboardNode) {
                showWarningAlert("Cannot Paste", "The object already has a property \"" + key + "\".");
                return;
            }
            if (isCut) {
                context.executeCommand(new DeleteNodeCommand(clipboardNode));
                if (anchor != null && anchor.getParent() == object) {
                    index = object.indexOf(anchor) + 1;
                }
                clipboardNode = null;
                isCut = false;
            }
            execute(new AddPropertyCommand(object, key, toPaste, Math.min(index, object.getChildCount())));
        } else if (target instanceof JsonArray array) {
            if (isCut) {
                context.executeCommand(new DeleteNodeCommand(clipboardNode));
                if (anchor != null && anchor.getParent() == array) {
                    index = array.indexOf(anchor) + 1;
                }
                clipboardNode = null;
                isCut = false;
            }
            toPaste.setKey(null);
            execute(new AddArrayItemCommand(array, index < 0 ? -1 : Math.min(index, array.size()), toPaste));
        }
    }

    /** @return {@code base}, or {@code base_copy}, {@code base_copy2}, … if taken */
    static String uniqueKey(JsonObject object, String base) {
        if (!object.hasProperty(base)) {
            return base;
        }
        String candidate = base + "_copy";
        int n = 2;
        while (object.hasProperty(candidate)) {
            candidate = base + "_copy" + n++;
        }
        return candidate;
    }

    private void copyCellContent() {
        if (clickedTable != null && clickedRowIndex >= 0 && clickedColumnName != null) {
            String content = clickedTable.getRows().get(clickedRowIndex).getValue(clickedColumnName);
            if (content != null && !content.isEmpty()) {
                copyToClipboard(content);
            }
            return;
        }
        if (selected() instanceof JsonPrimitive primitive) {
            copyToClipboard(JsonGridRecord.cellText(primitive));
        }
    }

    private void copyPath() {
        if (clickedTable != null && clickedRowIndex >= 0 && clickedColumnName != null) {
            Object rowNode = clickedTable.getRows().get(clickedRowIndex).getNode();
            if (rowNode instanceof JsonObject object) {
                JsonNode property = object.getProperty(clickedColumnName);
                copyToClipboard(property != null ? property.getPath()
                        : object.getPath() + pathSegment(clickedColumnName));
                return;
            }
        }
        JsonNode selected = selected();
        if (selected != null) {
            copyToClipboard(selected.getPath());
        }
    }

    /** @return {@code .key} or {@code ['key']} for keys that are not plain identifiers */
    static String pathSegment(String key) {
        return key.matches("[a-zA-Z_][a-zA-Z0-9_]*") ? "." + key : "['" + key.replace("'", "\\'") + "']";
    }

    private void copyNodeJson() {
        JsonNode selected = selected();
        if (selected == null) {
            return;
        }
        copyToClipboard(selected.serialize(2, 0));
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** Converts the selected value: primitive → primitive via {@link ChangeValueTypeCommand}, otherwise a fresh node. */
    private void changeType(JsonNodeType type) {
        JsonNode selected = selected();
        if (selected == null || isRoot(selected) || selected.getNodeType() == type) {
            return;
        }
        boolean primitiveTarget = type != JsonNodeType.OBJECT && type != JsonNodeType.ARRAY;
        if (selected instanceof JsonPrimitive primitive && primitiveTarget) {
            execute(new ChangeValueTypeCommand(primitive, type));
        } else {
            execute(new ReplaceNodeCommand(selected, newNode(type)));
        }
    }

    private void moveNode(int direction) {
        JsonNode selected = selected();
        if (selected == null || isRoot(selected)) {
            return;
        }
        JsonNode parent = selected.getParent();
        int newIndex = parent.indexOf(selected) + direction;
        if (newIndex < 0 || newIndex >= parent.getChildCount()) {
            return;
        }
        execute(new MoveNodeCommand(selected, newIndex));
    }

    private void deleteNode() {
        JsonNode selected = selected();
        if (selected == null || isRoot(selected)) {
            return;
        }
        Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                Alert.AlertType.CONFIRMATION, "Delete Node", "Delete \"" + nodeName(selected) + "\"?",
                "This action cannot be undone from this dialog.");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (context.executeCommand(new DeleteNodeCommand(selected))) {
                context.getSelectionModel().clearSelection();
                refresh();
            }
        }
    }

    private void expandAll() {
        // Expand-all is handled by the view; placeholder no-op (parity with the XML grid).
    }

    private void collapseAll() {
        // Collapse-all is handled by the view; placeholder no-op (parity with the XML grid).
    }

    private void sortColumn(boolean ascending) {
        if (clickedTable == null || clickedColumnName == null) {
            logger.warn("Sort column called without table context");
            return;
        }
        if (!clickedTable.isColumnSortable(clickedColumnName)) {
            showWarningAlert("Cannot Sort Column", "This column contains nested values and cannot be sorted.");
            return;
        }
        List<Object> nodes = clickedTable.getNodes();
        if (nodes.isEmpty() || !(nodes.get(0) instanceof JsonNode first) || !(first.getParent() instanceof JsonArray array)) {
            return;
        }
        logger.info("Sorting column '{}' {}", clickedColumnName, ascending ? "ascending" : "descending");
        context.executeCommand(new SortArrayCommand(array, clickedColumnName, ascending,
                clickedTable.detectColumnDataType(clickedColumnName)));
        clickedTable.setSortState(clickedColumnName, ascending);
        refresh();
    }

    private static String nodeName(JsonNode node) {
        String label = JsonGridAdapter.labelOf(node);
        if (node instanceof JsonPrimitive primitive) {
            String text = JsonGridRecord.cellText(primitive);
            return label + " = " + (text.length() > 20 ? text.substring(0, 20) + "..." : text);
        }
        return label;
    }

    private void execute(JsonCommand command) {
        if (context.executeCommand(command)) {
            refresh();
        }
    }

    private void refresh() {
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }

    // ==================== Dialogs ====================

    /** Result of the add dialogs: the key (object targets only) and the value type. */
    record NewValue(String key, JsonNodeType type) {
    }

    private NewValue showNewValueDialog(String title, boolean askKey, String defaultKey) {
        Dialog<NewValue> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(askKey ? "Enter the key and choose the value type:" : "Choose the value type:");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField keyField = new TextField(defaultKey != null ? defaultKey : "");
        keyField.setPrefWidth(250);
        ComboBox<JsonNodeType> typeBox = new ComboBox<>(FXCollections.observableArrayList(VALUE_TYPES));
        typeBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(JsonNodeType type) {
                return type == null ? "" : typeLabel(type);
            }

            @Override
            public JsonNodeType fromString(String s) {
                return VALUE_TYPES.stream().filter(t -> typeLabel(t).equals(s)).findFirst().orElse(JsonNodeType.STRING);
            }
        });
        typeBox.getSelectionModel().select(JsonNodeType.STRING);
        typeBox.setPrefWidth(250);

        int row = 0;
        if (askKey) {
            grid.add(new Label("Key:"), 0, row);
            grid.add(keyField, 1, row++);
        }
        grid.add(new Label("Type:"), 0, row);
        grid.add(typeBox, 1, row);
        dialog.getDialogPane().setContent(grid);

        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        if (askKey) {
            addButton.setDisable(keyField.getText().trim().isEmpty());
            keyField.textProperty().addListener((obs, o, n) -> addButton.setDisable(n == null || n.trim().isEmpty()));
        }
        dialog.setOnShown(e -> (askKey ? keyField : typeBox).requestFocus());
        dialog.setResultConverter(button -> button == addButtonType
                ? new NewValue(askKey ? keyField.getText().trim() : null, typeBox.getValue())
                : null);
        return dialog.showAndWait().orElse(null);
    }

    private String showInputDialog(String title, String header, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(null);
        return dialog.showAndWait().orElse(null);
    }

    private void showWarningAlert(String title, String message) {
        Alert alert = org.fxt.freexmltoolkit.util.DialogHelper.createStyledAlert(
                Alert.AlertType.WARNING, title, null, message);
        alert.showAndWait();
    }

    // ==================== Keyboard ====================

    @Override
    public void handleKeyPress(KeyEvent event, JsonNode selectedNode) {
        if (selectedNode == null) {
            return;
        }
        if (event.getCode() == KeyCode.DELETE) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            deleteNode();
            event.consume();
        } else if (event.getCode() == KeyCode.F2) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            renameKey();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.C) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            copyNodeToInternalClipboard();
            event.consume();
        } else if (event.isControlDown() && event.getCode() == KeyCode.X) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            cutNode();
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
            duplicateNode();
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.UP) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            moveNode(-1);
            event.consume();
        } else if (event.isAltDown() && event.getCode() == KeyCode.DOWN) {
            context.getSelectionModel().setSelectedNode(selectedNode);
            moveNode(1);
            event.consume();
        }
    }

    @Override
    public boolean hasClipboard() {
        return clipboardNode != null;
    }
}
