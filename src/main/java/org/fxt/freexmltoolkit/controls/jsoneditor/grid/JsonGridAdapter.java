package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddPropertyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.RenameKeyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SetPrimitiveValueCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SortArrayCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridContextMenu;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridModelAdapter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ToastNotification;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.BooleanToggle;

/**
 * {@link GridModelAdapter} for JSON documents: flattens the {@link JsonDocument} of a
 * {@link JsonEditorContext} into grid rows, renders arrays of objects as embedded tables,
 * offers type-aware inline editing (string / number / boolean / null) and maps edits onto
 * the JSON command set.
 *
 * <p>Flatten rules (see the design spec): the root object/array is the {@code $} row
 * (expanded); object properties and array items ({@code [i]}) become child rows — scalar
 * ones as {@code key = value} leaves, containers as collapsed expandable rows; an array
 * with two or more items that are all objects becomes a single row with an embedded
 * table whose columns are the merged property keys.</p>
 */
public class JsonGridAdapter implements GridModelAdapter<JsonNode> {

    private static final Logger logger = LogManager.getLogger(JsonGridAdapter.class);

    /** Label of the root row. */
    public static final String ROOT_LABEL = "$";

    static final String LOSSY_FORMAT_MESSAGE =
            "Comments and JSON5 syntax are not preserved by the grid: the first edit rewrites the file as standard JSON.";

    private final JsonEditorContext context;
    private GridCanvasView<JsonNode> view;
    private boolean lossyToastShown;

    public JsonGridAdapter(JsonEditorContext context) {
        this.context = context;
    }

    /** @return the wrapped editor context */
    public JsonEditorContext getContext() {
        return context;
    }

    // ==================== Document → rows ====================

    @Override
    public boolean hasDocument() {
        return context.getDocument() != null && context.getDocument().getRootValue() != null;
    }

    @Override
    public List<FlatRow> flatten() {
        return flatten(context.getDocument());
    }

    /** Flattens a whole document: the root value is the expanded {@code $} row. */
    public static List<FlatRow> flatten(JsonDocument document) {
        List<FlatRow> rows = new ArrayList<>();
        JsonNode root = document != null ? document.getRootValue() : null;
        if (root != null) {
            flattenNode(root, ROOT_LABEL, 0, null, rows, true);
        }
        return rows;
    }

    /**
     * Flattens the children of a container for inline expansion inside a table cell.
     * A virtual anchor row (depth -1, not part of the result) serves as their parent so
     * {@link FlatRow#toggleExpand} and visibility work on the returned list; children
     * start collapsed, like {@link FlatRow#flattenElement}.
     */
    public static List<FlatRow> flattenValue(JsonNode container) {
        List<FlatRow> rows = new ArrayList<>();
        if (container == null) {
            return rows;
        }
        FlatRow anchor = new FlatRow(rowTypeOf(container), -1, container, null,
                labelOf(container), null, container.getChildCount());
        flattenChildren(container, 0, anchor, rows);
        return rows;
    }

    private static void flattenNode(JsonNode node, String label, int depth, FlatRow parent,
                                    List<FlatRow> rows, boolean expand) {
        FlatRow.RowType type = rowTypeOf(node);
        if (node instanceof JsonPrimitive primitive) {
            rows.add(new FlatRow(type, depth, node, parent, label, JsonGridRecord.cellText(primitive), 0));
            return;
        }
        FlatRow row = new FlatRow(type, depth, node, parent, label, null, node.getChildCount());
        row.setExpanded(expand);
        rows.add(row);
        if (node instanceof JsonArray array && isObjectTable(array)) {
            return; // items are shown as an embedded table, not as rows
        }
        flattenChildren(node, depth + 1, row, rows);
    }

    private static void flattenChildren(JsonNode container, int depth, FlatRow parent, List<FlatRow> rows) {
        boolean array = container instanceof JsonArray;
        int index = 0;
        for (JsonNode child : container.getChildren()) {
            String label = array ? "[" + index + "]" : child.getKey();
            flattenNode(child, label, depth, parent, rows, false);
            index++;
        }
    }

    /** @return the row type for a model node */
    public static FlatRow.RowType rowTypeOf(JsonNode node) {
        if (node instanceof JsonObject) {
            return FlatRow.RowType.JSON_OBJECT;
        }
        if (node instanceof JsonArray) {
            return FlatRow.RowType.JSON_ARRAY;
        }
        if (node instanceof JsonPrimitive p) {
            if (p.isNull()) {
                return FlatRow.RowType.JSON_NULL;
            }
            if (p.isNumber()) {
                return FlatRow.RowType.JSON_NUMBER;
            }
            if (p.isBoolean()) {
                return FlatRow.RowType.JSON_BOOLEAN;
            }
            return FlatRow.RowType.JSON_STRING;
        }
        return FlatRow.RowType.JSON_OBJECT;
    }

    /** @return the row label of a node: its key, {@code [i]} inside an array, {@code $} for the root */
    public static String labelOf(JsonNode node) {
        JsonNode parent = node.getParent();
        if (parent == null || parent instanceof JsonDocument) {
            return ROOT_LABEL;
        }
        if (parent instanceof JsonArray) {
            return "[" + parent.indexOf(node) + "]";
        }
        return node.getKey();
    }

    /** @return whether the array is rendered as an embedded table (≥ 2 items, all objects) */
    public static boolean isObjectTable(JsonArray array) {
        if (array.size() < 2) {
            return false;
        }
        for (JsonNode item : array.getChildren()) {
            if (!(item instanceof JsonObject)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void attachTables(List<FlatRow> rows, Runnable onLayoutChanged) {
        for (FlatRow row : rows) {
            if (row.getType() == FlatRow.RowType.JSON_ARRAY
                    && row.getModelNode() instanceof JsonArray array && isObjectTable(array)) {
                List<JsonGridRecord> records = new ArrayList<>();
                for (JsonNode item : array.getChildren()) {
                    records.add(new JsonGridRecord((JsonObject) item));
                }
                row.setRepeatingTable(RepeatingElementsTable.ofRecords(
                        row.getLabel(), records, row.getDepth(), onLayoutChanged));
            }
        }
    }

    @Override
    public void addModelListener(Runnable onModelChanged) {
        context.addPropertyChangeListener("document", evt -> onModelChanged.run());
        context.addPropertyChangeListener("modelChanged", evt -> onModelChanged.run());
    }

    @Override
    public void installViewHooks(GridCanvasView<JsonNode> view) {
        this.view = view;
        context.addPropertyChangeListener("lossyFormatDetected", evt -> showLossyToast());
        if (context.isLossyFormat()) {
            showLossyToast();
        }
    }

    /** Warns once per grid session that comments / JSON5 syntax will be lost on the first edit. */
    private void showLossyToast() {
        if (lossyToastShown || view == null) {
            return;
        }
        lossyToastShown = true;
        javafx.application.Platform.runLater(
                () -> view.showToast(LOSSY_FORMAT_MESSAGE, ToastNotification.Type.WARNING));
    }

    // ==================== Nodes & selection ====================

    @Override
    public JsonNode nodeOf(FlatRow row) {
        return row != null && row.getModelNode() instanceof JsonNode node ? node : null;
    }

    @Override
    public JsonNode nodeOf(RepeatingElementsTable.TableRow row) {
        return row != null && row.getNode() instanceof JsonNode node ? node : null;
    }

    @Override
    public void select(JsonNode node) {
        context.getSelectionModel().setSelectedNode(node);
    }

    // ==================== Rendering ====================

    @Override
    public String decorateLeafValue(FlatRow row) {
        return row.getType() == FlatRow.RowType.JSON_STRING ? "\"" + row.getValue() + "\"" : row.getValue();
    }

    @Override
    public String emptyStateText() {
        return "No JSON document loaded";
    }

    // ==================== Inline editing ====================

    /** @return whether the node's parent is an object (only object properties have keys) */
    static boolean hasEditableKey(JsonNode node) {
        return node != null && node.getParent() instanceof JsonObject;
    }

    @Override
    public boolean canEditName(FlatRow row) {
        return row.getDepth() >= 0 && hasEditableKey(nodeOf(row));
    }

    @Override
    public boolean canEditValue(FlatRow row) {
        return row.getType().isScalarLeaf() && nodeOf(row) instanceof JsonPrimitive;
    }

    @Override
    public EditSpec editSpec(FlatRow row, boolean nameEdit) {
        if (nameEdit) {
            return EditSpec.plain(row.getLabel());
        }
        return nodeOf(row) instanceof JsonPrimitive p ? primitiveSpec(p) : EditSpec.plain(row.getValue());
    }

    @Override
    public EditSpec cellEditSpec(RepeatingElementsTable table, int rowIndex, String columnName) {
        RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);
        JsonNode property = nodeOf(row) instanceof JsonObject object ? object.getProperty(columnName) : null;
        if (property instanceof JsonPrimitive p) {
            return primitiveSpec(p);
        }
        return EditSpec.plain(row.getValue(columnName));
    }

    /** A boolean toggle for booleans, a plain text field with the raw value otherwise. */
    private static EditSpec primitiveSpec(JsonPrimitive p) {
        String current = JsonGridRecord.cellText(p);
        if (p.isBoolean()) {
            return new EditSpec(current, new BooleanToggle(current, null), null);
        }
        return EditSpec.plain(current);
    }

    @Override
    public boolean commitRowEdit(FlatRow row, boolean nameEdit, String newValue) {
        JsonNode node = nodeOf(row);
        if (node == null) {
            return true;
        }
        if (nameEdit) {
            return renameKey(node, newValue);
        }
        return node instanceof JsonPrimitive p ? applyPrimitive(p, newValue) : true;
    }

    @Override
    public boolean commitCellEdit(RepeatingElementsTable table, int rowIndex, String columnName, String newValue) {
        if (!(nodeOf(table.getRows().get(rowIndex)) instanceof JsonObject object)) {
            return true;
        }
        JsonNode property = object.getProperty(columnName);
        if (property == null) {
            // The row lacks this column's property: create it as a string
            context.executeCommand(new AddPropertyCommand(object, columnName, new JsonPrimitive(newValue), -1));
            return true;
        }
        return property instanceof JsonPrimitive p ? applyPrimitive(p, newValue) : true;
    }

    /**
     * Renames an object property; a blank or unchanged key is ignored, a duplicate key is
     * rejected with a toast.
     */
    boolean renameKey(JsonNode node, String newKey) {
        if (!hasEditableKey(node)) {
            return true;
        }
        String key = newKey != null ? newKey.trim() : "";
        if (key.isEmpty() || key.equals(node.getKey())) {
            return true;
        }
        RenameKeyCommand command = new RenameKeyCommand(node, key);
        if (!context.executeCommand(command)) {
            toast("Key \"" + key + "\" already exists");
            return false;
        }
        return true;
    }

    /**
     * Sets a primitive's value from its text, keeping the current type: numbers and booleans
     * are parsed, an unparsable text is rejected with a toast and keeps the editor open.
     */
    boolean applyPrimitive(JsonPrimitive primitive, String text) {
        Object value;
        try {
            value = JsonValueParser.parse(text, primitive.getNodeType());
        } catch (IllegalArgumentException e) {
            toast(primitive.isNumber() ? "Not a valid number: " + text : "Not a valid boolean: " + text);
            return false;
        }
        if (Objects.equals(value, primitive.getValue())
                || (value != null && primitive.getValue() != null
                        && value.toString().equals(primitive.getValue().toString()))) {
            return true; // unchanged
        }
        context.executeCommand(new SetPrimitiveValueCommand(primitive, value));
        return true;
    }

    private void toast(String message) {
        if (view != null) {
            view.showToast(message, ToastNotification.Type.WARNING);
        } else {
            logger.warn(message);
        }
    }

    @Override
    public void sortTable(RepeatingElementsTable table, String columnName, boolean ascending) {
        List<Object> nodes = table.getNodes();
        if (nodes.isEmpty() || !(nodes.get(0) instanceof JsonNode first) || !(first.getParent() instanceof JsonArray array)) {
            return;
        }
        context.executeCommand(new SortArrayCommand(array, columnName, ascending, table.detectColumnDataType(columnName)));
        table.setSortState(columnName, ascending);
    }

    // ==================== Serialization & menu ====================

    @Override
    public String serialize() {
        return context.serializeToString();
    }

    @Override
    public GridContextMenu<JsonNode> createContextMenu(Runnable refresh) {
        return new JsonGridContextMenu(context, refresh);
    }
}
