package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonGridRecord;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridColumnSort;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable.ColumnDataType;

/**
 * Sorts the items of an array of objects by the value of one property (a table column in
 * the grid), using the shared {@link GridColumnSort} rules (numeric / date / string, empties
 * last). The sort is stable; undo restores the original order.
 */
public class SortArrayCommand implements JsonCommand {

    private final JsonArray array;
    private final String columnName;
    private final boolean ascending;
    private final ColumnDataType dataType;
    private final List<JsonNode> originalOrder;

    public SortArrayCommand(JsonArray array, String columnName, boolean ascending, ColumnDataType dataType) {
        this.array = array;
        this.columnName = columnName;
        this.ascending = ascending;
        this.dataType = dataType != null ? dataType : ColumnDataType.STRING;
        this.originalOrder = new ArrayList<>(array.getChildren());
    }

    @Override
    public boolean execute() {
        if (originalOrder.size() < 2) {
            return false;
        }
        List<JsonNode> sorted = new ArrayList<>(originalOrder);
        sorted.sort(Comparator.comparing(this::columnValue, GridColumnSort.comparator(dataType, ascending)));
        if (sorted.equals(originalOrder)) {
            return false;
        }
        reorder(sorted);
        return true;
    }

    @Override
    public boolean undo() {
        reorder(originalOrder);
        return true;
    }

    private void reorder(List<JsonNode> order) {
        for (JsonNode node : new ArrayList<>(array.getChildren())) {
            array.removeChild(node);
        }
        for (JsonNode node : order) {
            array.addChild(node);
        }
    }

    private String columnValue(JsonNode item) {
        if (item instanceof JsonObject object) {
            JsonNode property = object.getProperty(columnName);
            return property != null ? JsonGridRecord.cellText(property) : "";
        }
        return "";
    }

    @Override
    public String getDescription() {
        return "Sort by '" + columnName + "' (" + (ascending ? "ascending" : "descending") + ")";
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
