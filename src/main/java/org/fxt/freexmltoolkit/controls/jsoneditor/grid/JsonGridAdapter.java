package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddArrayItemCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddPropertyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.CompositeJsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.RenameKeyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.ReparentNodeCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SetPrimitiveValueCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SortArrayCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonDocument;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeFactory;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNodeType;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.DropPosition;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridContextMenu;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridModelAdapter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ToastNotification;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.BooleanToggle;
import org.fxt.freexmltoolkit.i18n.I18n;

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

    /** The text the shown problems refer to (scanned once), or null: then rows have no source line. */
    private org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonTextLocator.Span sourceRoot;
    private int[] sourceLineStarts;

    public JsonGridAdapter(JsonEditorContext context) {
        this.context = context;
        // the source lines refer to the text before the edit
        context.addPropertyChangeListener("modelChanged", evt -> {
            setSourceText(null);
            modelRevision++;
        });
        context.addPropertyChangeListener("document", evt -> {
            setSourceText(null);
            modelRevision++;
        });
    }

    // ==================== Filters (JSONPath) ====================

    /** Bumped on every model change: cached filter results of an older model are stale. */
    private long modelRevision;
    /** The last filter results by container identity + expression (rows are rebuilt after every edit). */
    private final java.util.Map<String, java.util.Set<JsonNode>> filterCache = new java.util.HashMap<>();
    private long filterCacheRevision = -1;

    @Override
    public boolean supportsFilters() {
        return true;
    }

    @Override
    public String filterHint(String label) {
        return I18n.t("JsonGridAdapter.filter-hint", label);
    }

    @Override
    public java.util.function.Predicate<Object> filterPredicate(FlatRow row, String expression) {
        if (row.isVirtual() || !(row.getModelNode() instanceof JsonNode container)
                || !(container instanceof JsonObject || container instanceof JsonArray)) {
            return null;
        }
        java.util.Set<JsonNode> matches = filterMatches(container, expression);
        java.util.Set<JsonNode> ancestors = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (JsonNode match : matches) {
            for (JsonNode n = match.getParent(); n != null && n != container; n = n.getParent()) {
                ancestors.add(n);
            }
        }
        return candidate -> {
            if (!(candidate instanceof JsonNode node) || node == container || ancestors.contains(node)) {
                return true;
            }
            for (JsonNode n = node; n != null && n != container; n = n.getParent()) {
                if (matches.contains(n)) {
                    return true; // the match itself or a node below it
                }
            }
            return false;
        };
    }

    /**
     * Evaluates a JSONPath expression on a container ({@code $} = the container).
     *
     * @return the matched nodes (identity set)
     * @throws IllegalArgumentException when the expression is invalid
     */
    java.util.Set<JsonNode> filterMatches(JsonNode container, String expression) {
        if (filterCacheRevision != modelRevision) {
            filterCache.clear();
            filterCacheRevision = modelRevision;
        }
        String cacheKey = System.identityHashCode(container) + "\u0000" + expression;
        java.util.Set<JsonNode> cached = filterCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<String> paths;
        try {
            var config = com.jayway.jsonpath.Configuration.builder()
                    .options(com.jayway.jsonpath.Option.AS_PATH_LIST, com.jayway.jsonpath.Option.SUPPRESS_EXCEPTIONS,
                            com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST)
                    .build();
            Object result = com.jayway.jsonpath.JsonPath.using(config).parse(container.serialize(0, 0)).read(expression);
            paths = new ArrayList<>();
            if (result instanceof List<?> list) {
                list.forEach(p -> paths.add(String.valueOf(p)));
            }
        } catch (com.jayway.jsonpath.InvalidPathException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid JSONPath: " + e.getMessage(), e);
        }
        java.util.Set<JsonNode> matches = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (String path : paths) {
            JsonNode node = nodeAtPath(container, path);
            if (node != null) {
                matches.add(node);
            }
        }
        filterCache.put(cacheKey, matches);
        return matches;
    }

    private static final java.util.regex.Pattern PATH_STEP =
            java.util.regex.Pattern.compile("\\['((?:[^'\\\\]|\\\\.)*)'\\]|\\[(\\d+)\\]");

    /** @return the node a normalized Jayway path ({@code $['a'][0]}) addresses below the container */
    static JsonNode nodeAtPath(JsonNode container, String path) {
        JsonNode current = container;
        var matcher = PATH_STEP.matcher(path);
        while (matcher.find() && current != null) {
            if (matcher.group(2) != null) {
                current = current instanceof JsonArray ? current.getChild(Integer.parseInt(matcher.group(2))) : null;
            } else {
                String key = matcher.group(1).replace("\\'", "'").replace("\\\\", "\\");
                current = current instanceof JsonObject object ? object.getProperty(key) : null;
            }
        }
        return current;
    }

    @Override
    public String focusFilterExpression(FlatRow root, Object target) {
        if (!(root.getModelNode() instanceof JsonNode container) || !(target instanceof JsonNode node)
                || node == container) {
            return null;
        }
        java.util.LinkedList<String> steps = new java.util.LinkedList<>();
        for (JsonNode n = node; n != container; n = n.getParent()) {
            JsonNode parent = n.getParent();
            if (parent == null) {
                return null; // not below the root row
            }
            steps.addFirst(parent instanceof JsonArray ? "[" + parent.indexOf(n) + "]" : "[" + quoteKey(n.getKey()) + "]");
        }
        return "$" + String.join("", steps);
    }

    @Override
    public String valueFilterExpression(Object member, String columnKey, String value) {
        if (!(member instanceof JsonObject object) || columnKey == null || columnKey.isBlank()) {
            return null;
        }
        JsonNode property = object.getProperty(columnKey);
        String operand;
        if (property instanceof JsonPrimitive p && (p.isNumber() || p.isBoolean() || p.getValue() == null)) {
            operand = p.getValue() == null ? "null" : p.getValue().toString();
        } else {
            operand = quoteKey(value != null ? value : "");
        }
        return "$[?(@[" + quoteKey(columnKey) + "] == " + operand + ")]";
    }

    /** @return a single-quoted JSONPath string literal */
    private static String quoteKey(String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * Sets the text the rows' source lines are read from (the text validation problems refer to).
     * It is scanned once; any model change drops it again, so stale problem markers vanish.
     *
     * @param text the document text the model was parsed from, or {@code null}
     */
    public void setSourceText(String text) {
        if (text == null) {
            sourceRoot = null;
            sourceLineStarts = null;
            return;
        }
        sourceRoot = org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonTextLocator.scan(text);
        java.util.List<Integer> starts = new java.util.ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        sourceLineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public int sourceLine(FlatRow row) {
        return lineOf(nodeOf(row));
    }

    @Override
    public int[] memberSourceLines(FlatRow row) {
        List<?> members = row.getTableMembers();
        if (members == null) {
            return new int[0];
        }
        int[] lines = new int[members.size()];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = members.get(i) instanceof JsonNode node ? lineOf(node) : 0;
        }
        return lines;
    }

    /** @return the 1-based line the node starts on in the source text, 0 when unknown */
    private int lineOf(JsonNode node) {
        if (node == null || sourceRoot == null) {
            return 0;
        }
        var path = org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonTextLocator.pathOf(node);
        var span = org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonTextLocator.spanAt(sourceRoot, path);
        if (span == null) {
            return 0;
        }
        int idx = java.util.Arrays.binarySearch(sourceLineStarts, span.start());
        return (idx >= 0 ? idx : -idx - 2) + 1;
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

    @Override
    public List<FlatRow> flatten(java.util.function.Predicate<String> groupAsTable) {
        return flatten(context.getDocument(), groupAsTable);
    }

    /** Flattens a whole document: the root value is the expanded {@code $} row. */
    public static List<FlatRow> flatten(JsonDocument document) {
        return flatten(document, FlatRow.ALWAYS_TABLE);
    }

    /**
     * Flattens a whole document; {@code groupAsTable} decides per array path whether an
     * array of objects becomes an embedded table or individual {@code [i]} rows.
     */
    public static List<FlatRow> flatten(JsonDocument document, java.util.function.Predicate<String> groupAsTable) {
        List<FlatRow> rows = new ArrayList<>();
        JsonNode root = document != null ? document.getRootValue() : null;
        if (root != null) {
            flattenNode(root, ROOT_LABEL, 0, null, rows, true, groupAsTable);
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
        return flattenValue(container, FlatRow.CELL_GROUPS);
    }

    /**
     * The rows of an opened cell holding {@code container}, grouped like the root grid's rows: an
     * array of objects becomes one table row when {@code groupAsTable} accepts its path.
     */
    public static List<FlatRow> flattenValue(JsonNode container, java.util.function.Predicate<String> groupAsTable) {
        List<FlatRow> rows = new ArrayList<>();
        if (container == null) {
            return rows;
        }
        FlatRow anchor = new FlatRow(rowTypeOf(container), -1, container, null,
                labelOf(container), null, container.getChildCount());
        if (container instanceof JsonArray array && isObjectTable(array) && groupAsTable.test(array.getPath())) {
            // The cell holds an array of objects: one row with its embedded table, as in the root grid
            flattenNode(container, labelOf(container), 0, anchor, rows, true, groupAsTable);
            return rows;
        }
        flattenChildren(container, 0, anchor, rows, groupAsTable);
        FlatRow.openSoleContainer(rows, anchor);
        return rows;
    }

    private static void flattenNode(JsonNode node, String label, int depth, FlatRow parent,
                                    List<FlatRow> rows, boolean expand,
                                    java.util.function.Predicate<String> groupAsTable) {
        FlatRow.RowType type = rowTypeOf(node);
        if (node instanceof JsonPrimitive primitive) {
            rows.add(new FlatRow(type, depth, node, parent, label, JsonGridRecord.cellText(primitive), 0));
            return;
        }
        FlatRow row = new FlatRow(type, depth, node, parent, label, null, node.getChildCount());
        row.setExpanded(expand);
        rows.add(row);
        if (node instanceof JsonArray array && isObjectTable(array)) {
            if (groupAsTable.test(array.getPath())) {
                // Items are shown as an embedded table, not as rows; attachTables only reads this marker
                row.setTableGroup(array.getPath(), array.getChildren());
                return;
            }
            // Standard display of an array of objects: the array row carries the toggle glyph
            row.setStandardGroupKey(array.getPath());
        }
        flattenChildren(node, depth + 1, row, rows, groupAsTable);
    }

    /**
     * One-line preview of a collapsed object/array: compact JSON cut off at the limit.
     */
    @Override
    public String collapsedPreview(FlatRow row, int maxChars) {
        if (row.isVirtual() || !(row.getModelNode() instanceof JsonNode node)) {
            return null;
        }
        StringBuilder sb = new StringBuilder(Math.min(maxChars + 16, 4096));
        if (!appendPreview(node, sb, maxChars)) {
            sb.append('…');
        }
        return sb.toString();
    }

    /** @return {@code false} once the limit was hit and the output is incomplete */
    private static boolean appendPreview(JsonNode node, StringBuilder sb, int max) {
        if (sb.length() >= max) {
            return false;
        }
        if (node instanceof JsonPrimitive p) {
            sb.append(p.isString() ? "\"" + p.getAsString() + "\"" : JsonGridRecord.cellText(p));
            return sb.length() < max;
        }
        boolean array = node instanceof JsonArray;
        sb.append(array ? '[' : '{');
        boolean first = true;
        for (JsonNode child : node.getChildren()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            if (!array) {
                sb.append('"').append(child.getKey()).append("\": ");
            }
            if (!appendPreview(child, sb, max)) {
                return false;
            }
        }
        sb.append(array ? ']' : '}');
        return sb.length() < max;
    }

    private static void flattenChildren(JsonNode container, int depth, FlatRow parent, List<FlatRow> rows,
                                        java.util.function.Predicate<String> groupAsTable) {
        boolean array = container instanceof JsonArray;
        int index = 0;
        for (JsonNode child : container.getChildren()) {
            String label = array ? "[" + index + "]" : child.getKey();
            flattenNode(child, label, depth, parent, rows, false, groupAsTable);
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
            List<?> members = row.getTableMembers();
            if (members != null) {
                String label = row.getLabel();
                int depth = row.getDepth();
                // Built when the array is first expanded or drawn (FlatRow#setRepeatingTableFactory)
                row.setRepeatingTableFactory(() -> {
                    List<JsonGridRecord> records = new ArrayList<>();
                    for (Object item : members) {
                        if (item instanceof JsonObject object) {
                            records.add(new JsonGridRecord(object));
                        }
                    }
                    return RepeatingElementsTable.ofRecords(label, records, depth, onLayoutChanged);
                });
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
        if (row == null || row.isVirtual()) {
            return null; // a sibling group is no node
        }
        return row.getModelNode() instanceof JsonNode node ? node : null;
    }

    @Override
    public JsonNode nodeOf(RepeatingElementsTable.TableRow row) {
        return row != null && row.getNode() instanceof JsonNode node ? node : null;
    }

    /** A cell holding an object or array stands for that value. */
    @Override
    public JsonNode cellNode(RepeatingElementsTable.TableRow row, String column) {
        if (!(nodeOf(row) instanceof JsonObject object) || column == null) {
            return null;
        }
        JsonNode value = object.getProperty(column);
        return value instanceof JsonObject || value instanceof JsonArray ? value : null;
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
        return row.getDepth() >= 0 && !row.isVirtual() && hasEditableKey(nodeOf(row));
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
        return !(node instanceof JsonPrimitive p) || applyPrimitive(p, newValue);
    }

    @Override
    public boolean pasteCells(RepeatingElementsTable table, int startRow, int startCol,
                              List<List<String>> values) {
        CompositeJsonCommand cmd = JsonTableEditOps.pasteCells(table, startRow, startCol, values);
        return !cmd.isEmpty() && context.executeCommand(cmd);
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
        return !(property instanceof JsonPrimitive p) || applyPrimitive(p, newValue);
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
            toast(I18n.t("JsonGridAdapter.key-already-exists", key));
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

    // ==================== Type change (click on the type icon) ====================

    @Override
    public List<String> typeTargets(FlatRow row) {
        JsonNode node = nodeOf(row);
        if (node == null || row.isTableGroupOwner() || isRootNode(node)) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (JsonNodeType type : JsonGridContextMenu.VALUE_TYPES) {
            if (type != node.getNodeType()) {
                labels.add(JsonGridContextMenu.typeLabel(type));
            }
        }
        return labels;
    }

    @Override
    public boolean changeType(FlatRow row, String targetLabel) {
        if (!typeTargets(row).contains(targetLabel)) {
            return false;
        }
        for (JsonNodeType type : JsonGridContextMenu.VALUE_TYPES) {
            if (JsonGridContextMenu.typeLabel(type).equals(targetLabel)) {
                JsonCommand command = JsonGridContextMenu.typeChangeCommand(nodeOf(row), type);
                return command != null && context.executeCommand(command);
            }
        }
        return false;
    }

    private static boolean isRootNode(JsonNode node) {
        return node.getParent() == null || node.getParent() instanceof JsonDocument;
    }

    // ==================== Drag & drop ====================

    /** @return the node a row drags (never the root or a sibling group), or {@code null} */
    private JsonNode draggableNode(FlatRow row) {
        JsonNode node = nodeOf(row);
        return node != null && !isRootNode(node) ? node : null;
    }

    @Override
    public String dragText(FlatRow row) {
        JsonNode node = draggableNode(row);
        return node != null ? node.serialize(2, 0) : null;
    }

    @Override
    public boolean canDrag(FlatRow row) {
        return draggableNode(row) != null;
    }

    @Override
    public boolean canDrop(FlatRow dragged, FlatRow target, DropPosition position) {
        JsonNode targetNode = nodeOf(target);
        if (targetNode == null) {
            return false;
        }
        if (position == DropPosition.VALUE) {
            return dragged == null && canEditValue(target);
        }
        if (position == DropPosition.INTO) {
            if (!(targetNode instanceof JsonObject) && !(targetNode instanceof JsonArray)) {
                return false;
            }
        } else if (isRootNode(targetNode)) {
            return false; // the root has no siblings
        }
        if (dragged == null) {
            return true;
        }
        JsonNode node = draggableNode(dragged);
        // Never into itself or one of its descendants
        return node != null && !ReparentNodeCommand.isSelfOrAncestor(node, targetNode);
    }

    @Override
    public boolean dropRow(FlatRow dragged, FlatRow target, DropPosition position, boolean copy) {
        if (!canDrop(dragged, target, position)) {
            return false;
        }
        JsonNode node = draggableNode(dragged);
        JsonNode targetNode = nodeOf(target);
        JsonNode parent = position == DropPosition.INTO ? targetNode : targetNode.getParent();
        int index = position == DropPosition.INTO ? -1
                : parent.indexOf(targetNode) + (position == DropPosition.AFTER ? 1 : 0);
        if (copy) {
            return context.executeCommand(insertCommand(parent, index, node.deepCopy(), node.getKey()));
        }
        // ReparentNodeCommand takes the index after removing the node from its old place
        if (node.getParent() == parent) {
            int current = parent.indexOf(node);
            if (index >= 0 && current < index) {
                index--;
            }
            if (index == current || (index < 0 && current == parent.getChildCount() - 1)) {
                return false; // dropped onto its own place
            }
        }
        String key = parent instanceof JsonObject object
                ? (node.getParent() == parent ? node.getKey() : JsonGridContextMenu.uniqueKey(object, keyOrItem(node)))
                : null;
        return context.executeCommand(new ReparentNodeCommand(node, parent, index, key));
    }

    /**
     * Drops external text: on a value cell it replaces the value (keeping the type), elsewhere
     * JSON text becomes a new value (an object dropped into an object adds its properties),
     * any other text a string.
     */
    @Override
    public boolean dropText(FlatRow target, DropPosition position, String text) {
        if (text == null || !canDrop(null, target, position)) {
            return false;
        }
        if (position == DropPosition.VALUE) {
            return commitRowEdit(target, false, text.strip());
        }
        JsonNode targetNode = nodeOf(target);
        JsonNode parent = position == DropPosition.INTO ? targetNode : targetNode.getParent();
        int index = position == DropPosition.INTO ? -1
                : parent.indexOf(targetNode) + (position == DropPosition.AFTER ? 1 : 0);
        JsonNode value = parseDropped(text);
        if (value == null) {
            return false;
        }
        if (parent instanceof JsonObject object && value instanceof JsonObject properties) {
            List<JsonCommand> parts = new ArrayList<>();
            java.util.Set<String> used = new java.util.HashSet<>();
            int at = index;
            for (JsonNode property : List.copyOf(properties.getChildren())) {
                String key = uniqueKey(object, used, keyOrItem(property));
                used.add(key);
                parts.add(new AddPropertyCommand(object, key, property.deepCopy(), at));
                if (at >= 0) {
                    at++;
                }
            }
            return !parts.isEmpty()
                    && context.executeCommand(new CompositeJsonCommand("Drop " + parts.size() + " property(s)", parts));
        }
        return context.executeCommand(insertCommand(parent, index, value, "value"));
    }

    /** @return the dropped text as a JSON value; text that is no JSON becomes a string */
    static JsonNode parseDropped(String text) {
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = JsonNodeFactory.parse(trimmed).getRootValue();
            if (root != null) {
                root.setKey(null);
                if (root.getParent() != null) {
                    root.getParent().removeChild(root);
                }
                return root;
            }
        } catch (Exception e) {
            // not JSON: falls through to a string
        }
        return new JsonPrimitive(trimmed);
    }

    /** The command inserting a value into an object (unique key) or an array. */
    private static JsonCommand insertCommand(JsonNode parent, int index, JsonNode value, String preferredKey) {
        if (parent instanceof JsonObject object) {
            String key = JsonGridContextMenu.uniqueKey(object, preferredKey != null ? preferredKey : "item");
            return new AddPropertyCommand(object, key, value, index);
        }
        value.setKey(null);
        return new AddArrayItemCommand((JsonArray) parent, index, value);
    }

    private static String keyOrItem(JsonNode node) {
        return node.getKey() != null ? node.getKey() : "item";
    }

    /** {@link JsonGridContextMenu#uniqueKey} that also avoids the keys of the same drop. */
    private static String uniqueKey(JsonObject object, java.util.Set<String> used, String base) {
        String candidate = JsonGridContextMenu.uniqueKey(object, base);
        int n = 2;
        while (used.contains(candidate)) {
            candidate = JsonGridContextMenu.uniqueKey(object, base + "_" + n++);
        }
        return candidate;
    }

    // ==================== Serialization & menu ====================

    @Override
    public String serialize() {
        return context.serializeToString();
    }

    @Override
    public GridContextMenu<JsonNode> createContextMenu(Runnable refresh, GridContextMenu.ViewActions actions) {
        return new JsonGridContextMenu(context, refresh, actions);
    }

    /** The node's JSONPath-like path (unique per document). */
    @Override
    public String stableKey(FlatRow row) {
        return row.getModelNode() instanceof JsonNode node ? node.getPath() : GridModelAdapter.super.stableKey(row);
    }
}
