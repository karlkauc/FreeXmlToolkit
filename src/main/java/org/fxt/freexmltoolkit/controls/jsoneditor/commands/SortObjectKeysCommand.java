package org.fxt.freexmltoolkit.controls.jsoneditor.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.service.json.JsonFormatter;

/**
 * Sorts the members of an object by key (case-insensitive, then exact - the order of the Text
 * view's Sort Keys), optionally of every object below it too (also inside arrays). One undo step
 * restores every object's previous order.
 */
public class SortObjectKeysCommand implements JsonCommand {

    private final JsonObject object;
    private final boolean recursive;
    /** Every affected object with its member order before the sort. */
    private final Map<JsonObject, List<JsonNode>> originalOrders = new LinkedHashMap<>();

    /**
     * @param object    the object to sort
     * @param recursive whether objects nested in it are sorted too
     */
    public SortObjectKeysCommand(JsonObject object, boolean recursive) {
        this.object = object;
        this.recursive = recursive;
    }

    @Override
    public boolean execute() {
        originalOrders.clear();
        collect(object);
        boolean changed = false;
        for (var entry : originalOrders.entrySet()) {
            List<JsonNode> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparing(n -> n.getKey() == null ? "" : n.getKey(), JsonFormatter.KEY_ORDER));
            if (!sorted.equals(entry.getValue())) {
                reorder(entry.getKey(), sorted);
                changed = true;
            }
        }
        return changed;
    }

    private void collect(JsonNode node) {
        if (node instanceof JsonObject obj) {
            originalOrders.put(obj, new ArrayList<>(obj.getChildren()));
        }
        if (recursive && (node instanceof JsonObject || node instanceof JsonArray)) {
            for (JsonNode child : node.getChildren()) {
                collect(child);
            }
        }
    }

    @Override
    public boolean undo() {
        originalOrders.forEach(SortObjectKeysCommand::reorder);
        return true;
    }

    private static void reorder(JsonObject obj, List<JsonNode> order) {
        for (JsonNode node : new ArrayList<>(obj.getChildren())) {
            obj.removeChild(node);
        }
        for (JsonNode node : order) {
            obj.addChild(node);
        }
    }

    @Override
    public String getDescription() {
        return recursive ? "Sort keys recursively" : "Sort keys";
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
