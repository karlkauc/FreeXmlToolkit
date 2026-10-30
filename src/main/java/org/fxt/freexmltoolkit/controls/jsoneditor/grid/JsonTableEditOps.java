package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddArrayItemCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddPropertyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.CompositeJsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.RenameKeyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonArray;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonObject;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonPrimitive;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TableEditOps.Placement;

/**
 * The whole-row / whole-column edits of a JSON table (an array of objects): insert or append a
 * row (a new object with every column), insert or rename a column (the property in
 * every object). The JSON counterpart of
 * {@link org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TableEditOps}; pure model code, the
 * context menu asks the user and executes the result through the editor context.
 */
public final class JsonTableEditOps {

    private JsonTableEditOps() {
    }

    /** @return the objects shown as the table's rows, in row order */
    static List<JsonObject> members(RepeatingElementsTable table) {
        List<JsonObject> members = new ArrayList<>();
        for (Object node : table.getNodes()) {
            if (node instanceof JsonObject object) {
                members.add(object);
            }
        }
        return members;
    }

    /**
     * A new object with every column of the table as an empty string, inserted next to the
     * reference row or appended after the last row.
     *
     * @param table     the table
     * @param refRow    the reference row index (ignored for {@link Placement#APPEND})
     * @param placement where the new row goes
     * @return the command, or {@code null} when the rows have no array parent
     */
    public static JsonCommand insertRow(RepeatingElementsTable table, int refRow, Placement placement) {
        List<JsonObject> members = members(table);
        if (members.isEmpty()) {
            return null;
        }
        JsonObject reference = placement == Placement.APPEND ? members.get(members.size() - 1)
                : members.get(Math.max(0, Math.min(refRow, members.size() - 1)));
        if (!(reference.getParent() instanceof JsonArray array)) {
            return null;
        }
        JsonObject fresh = new JsonObject();
        for (RepeatingElementsTable.TableColumn col : table.getColumns()) {
            JsonPrimitive empty = new JsonPrimitive("");
            empty.setKey(col.getName());
            fresh.addChild(empty);
        }
        int index = array.indexOf(reference) + (placement == Placement.BEFORE ? 0 : 1);
        return new AddArrayItemCommand(array, index, fresh);
    }

    /**
     * A new column: the property (an empty string) in every object that lacks it, placed next to
     * the reference column where the object has it, else at the end.
     *
     * @param table     the table
     * @param key       the property key
     * @param refColumn the reference column (may be {@code null} = append)
     * @param placement before or after the reference column
     * @return one undoable step covering every row (empty when nothing is to do)
     */
    public static CompositeJsonCommand insertColumn(RepeatingElementsTable table, String key,
                                                    String refColumn, Placement placement) {
        List<JsonCommand> parts = new ArrayList<>();
        for (JsonObject member : members(table)) {
            if (member.hasProperty(key)) {
                continue;
            }
            int index = -1;
            JsonNode ref = refColumn != null ? member.getProperty(refColumn) : null;
            if (ref != null) {
                index = member.indexOf(ref) + (placement == Placement.BEFORE ? 0 : 1);
            }
            parts.add(new AddPropertyCommand(member, key, new JsonPrimitive(""), index));
        }
        return new CompositeJsonCommand("Insert column " + key, parts);
    }

    /**
     * Renames a column in every object that has it. Fails as a whole (nothing changes) when an
     * object already uses the new key.
     *
     * @return one undoable step covering every row (empty when nothing is to do)
     */
    public static CompositeJsonCommand renameColumn(RepeatingElementsTable table, String oldKey, String newKey) {
        List<JsonCommand> parts = new ArrayList<>();
        if (oldKey == null || newKey == null || newKey.isBlank() || newKey.equals(oldKey)) {
            return new CompositeJsonCommand("Rename column", parts);
        }
        for (JsonObject member : members(table)) {
            JsonNode property = member.getProperty(oldKey);
            if (property != null) {
                parts.add(new RenameKeyCommand(property, newKey));
            }
        }
        return new CompositeJsonCommand("Rename column " + oldKey + " to " + newKey, parts);
    }
}
