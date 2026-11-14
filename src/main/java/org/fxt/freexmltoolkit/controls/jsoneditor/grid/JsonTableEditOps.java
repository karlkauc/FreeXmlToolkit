package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddArrayItemCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.AddPropertyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.CompositeJsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.JsonCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.RenameKeyCommand;
import org.fxt.freexmltoolkit.controls.jsoneditor.commands.SetPrimitiveValueCommand;
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

    /**
     * Writes a pasted block of cells (e.g. TSV from a spreadsheet) into the table starting at a
     * cell: an existing scalar keeps its type (a value that does not parse as that type is
     * skipped), a missing property is added as a string, object/array cells are left alone, and
     * rows beyond the table become new objects appended to the array.
     *
     * @param table    the table
     * @param startRow the target cell's row
     * @param startCol the target cell's column
     * @param values   the pasted values, row by row
     * @return one undoable step for the whole paste (empty when nothing is to do)
     */
    public static CompositeJsonCommand pasteCells(RepeatingElementsTable table, int startRow, int startCol,
                                                  List<List<String>> values) {
        List<JsonCommand> parts = new ArrayList<>();
        List<JsonObject> members = members(table);
        List<RepeatingElementsTable.TableColumn> columns = table.getColumns();
        if (members.isEmpty() || startRow < 0 || startCol < 0 || values == null
                || !(members.get(members.size() - 1).getParent() instanceof JsonArray array)) {
            return new CompositeJsonCommand("Paste cells", parts);
        }
        int appendAt = array.indexOf(members.get(members.size() - 1)) + 1;
        for (int i = 0; i < values.size(); i++) {
            int r = startRow + i;
            List<String> rowValues = values.get(i);
            if (r >= members.size()) {
                // a new row: every column, the pasted ones filled in
                JsonObject fresh = new JsonObject();
                for (int c = 0; c < columns.size(); c++) {
                    int j = c - startCol;
                    JsonPrimitive value = new JsonPrimitive(j >= 0 && j < rowValues.size() ? rowValues.get(j) : "");
                    value.setKey(columns.get(c).getName());
                    fresh.addChild(value);
                }
                parts.add(new AddArrayItemCommand(array, appendAt++, fresh));
                continue;
            }
            JsonObject member = members.get(r);
            for (int j = 0; j < rowValues.size(); j++) {
                int c = startCol + j;
                if (c >= columns.size()) {
                    break;
                }
                String key = columns.get(c).getName();
                String text = rowValues.get(j);
                JsonNode property = member.getProperty(key);
                if (property == null) {
                    parts.add(new AddPropertyCommand(member, key, new JsonPrimitive(text), -1));
                } else if (property instanceof JsonPrimitive primitive) {
                    try {
                        Object value = JsonValueParser.parse(text, primitive.getNodeType());
                        if (!java.util.Objects.equals(value, primitive.getValue())) {
                            parts.add(new SetPrimitiveValueCommand(primitive, value));
                        }
                    } catch (IllegalArgumentException e) {
                        // not a number / boolean for a typed cell: left as it was
                    }
                }
            }
        }
        return new CompositeJsonCommand("Paste cells", parts);
    }
}
