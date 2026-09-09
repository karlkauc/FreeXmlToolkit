package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.FlatRow;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridRecord;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;

/**
 * {@link GridRecord} over one {@link JsonObject} of an array of objects: every property
 * becomes a column (document order), scalar properties supply the cell text, nested
 * objects/arrays are complex cells that can be expanded inline.
 */
public final class JsonGridRecord implements GridRecord {

    private final JsonObject object;
    private final List<String> columnKeys = new ArrayList<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, Object> complexChildren = new LinkedHashMap<>();

    public JsonGridRecord(JsonObject object) {
        this.object = object;
        for (JsonNode child : object.getChildren()) {
            String key = child.getKey();
            if (key == null || columnKeys.contains(key)) {
                continue;
            }
            columnKeys.add(key);
            values.put(key, cellText(child));
            if (child instanceof JsonObject || child instanceof JsonArray) {
                complexChildren.put(key, child);
            }
        }
    }

    /** @return the wrapped object */
    public JsonObject getObject() {
        return object;
    }

    /**
     * The text shown for a value in a table cell or a grid row: the raw string (unquoted),
     * the number/boolean literal, {@code null}, or a compact {@code {n}} / {@code [n]}
     * summary for containers.
     */
    public static String cellText(JsonNode node) {
        if (node instanceof JsonPrimitive p) {
            return p.isNull() ? "null" : p.getAsString();
        }
        if (node instanceof JsonObject) {
            return "{" + node.getChildCount() + "}";
        }
        if (node instanceof JsonArray) {
            return "[" + node.getChildCount() + "]";
        }
        return "";
    }

    @Override
    public Object node() {
        return object;
    }

    @Override
    public List<String> columnKeys() {
        return Collections.unmodifiableList(columnKeys);
    }

    @Override
    public String columnName(String key) {
        return key;
    }

    @Override
    public RepeatingElementsTable.ColumnType columnType(String key) {
        return RepeatingElementsTable.ColumnType.CHILD_ELEMENT;
    }

    @Override
    public Map<String, String> values() {
        return values;
    }

    @Override
    public Map<String, Object> complexChildren() {
        return complexChildren;
    }

    @Override
    public Map<String, String> attributeSuffixes() {
        return Map.of();
    }

    @Override
    public List<FlatRow> flattenComplexChild(String columnName) {
        Object child = complexChildren.get(columnName);
        return child instanceof JsonNode node ? JsonGridAdapter.flattenValue(node) : List.of();
    }
}
