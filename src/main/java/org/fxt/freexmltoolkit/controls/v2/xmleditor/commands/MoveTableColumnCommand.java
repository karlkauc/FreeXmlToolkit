package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.RepeatingElementsTable;

/**
 * Moves a column of a repeating-group table next to another column (XMLSpy: drag a column
 * header onto another one): in every member the column's child element moves before/after the
 * target column's element, or the attribute moves within the attribute list. The table's
 * cached column order follows, so the rebuilt table shows the new order; undo restores both.
 * Only columns of the same kind (attribute/attribute, element/element) can be swapped, since
 * attributes are always listed before child elements.
 */
public final class MoveTableColumnCommand implements XmlCommand {

    private final List<XmlElement> members;
    private final String elementName;
    private final RepeatingElementsTable.TableColumn column;
    private final RepeatingElementsTable.TableColumn target;
    private final boolean before;
    private final List<String> newOrder;
    private List<String> oldOrder;
    private final List<XmlCommand> parts = new ArrayList<>();
    private final List<LinkedHashMap<String, String>> oldAttributes = new ArrayList<>();
    private boolean executed;

    /**
     * @param table  the table
     * @param from   the moved column's index
     * @param to     the target column's index
     * @param before whether the column goes before (true) or after (false) the target
     */
    public MoveTableColumnCommand(RepeatingElementsTable table, int from, int to, boolean before) {
        this.members = table.getElements();
        this.elementName = table.getElementName();
        this.column = table.getColumn(from);
        this.target = table.getColumn(to);
        this.before = before;
        List<String> keys = new ArrayList<>(table.columnKeys());
        String key = keys.remove(from);
        int targetIndex = keys.indexOf(RepeatingElementsTable.columnKey(target));
        keys.add(before ? targetIndex : targetIndex + 1, key);
        this.newOrder = keys;
    }

    /** @return whether the move is possible: two different columns of the same kind */
    public boolean isApplicable() {
        return column != null && target != null && column != target && column.getType() == target.getType()
                && column.getType() != RepeatingElementsTable.ColumnType.TEXT_CONTENT;
    }

    @Override
    public boolean execute() {
        if (!isApplicable()) {
            return false;
        }
        parts.clear();
        oldAttributes.clear();
        for (XmlElement member : members) {
            if (column.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
                oldAttributes.add(new LinkedHashMap<>(member.getAttributes()));
                reorderAttributes(member);
            } else {
                // Every child of the column moves, in document order, next to the target's children
                // (before the first one or after the last one).
                List<XmlElement> moving = member.getChildElements(column.getName());
                List<XmlElement> anchors = member.getChildElements(target.getName());
                if (moving.isEmpty() || anchors.isEmpty()) {
                    continue;
                }
                XmlElement previous = null;
                for (XmlElement moved : moving) {
                    int index = previous != null ? member.indexOf(previous) + 1
                            : before ? member.indexOf(anchors.getFirst())
                            : member.indexOf(anchors.getLast()) + 1;
                    if (member.indexOf(moved) < index) {
                        index--; // MoveNodeCommand inserts after removing the node
                    }
                    MoveNodeCommand move = new MoveNodeCommand(moved, member, index);
                    if (move.execute()) {
                        parts.add(move);
                    }
                    previous = moved;
                }
            }
        }
        oldOrder = RepeatingElementsTable.cachedColumnOrder(elementName);
        RepeatingElementsTable.setCachedColumnOrder(elementName, newOrder);
        executed = true;
        return true;
    }

    private void reorderAttributes(XmlElement member) {
        if (!member.hasAttribute(column.getName()) || !member.hasAttribute(target.getName())) {
            return;
        }
        String value = member.getAttribute(column.getName());
        LinkedHashMap<String, String> rebuilt = new LinkedHashMap<>();
        for (var entry : member.getAttributes().entrySet()) {
            String name = entry.getKey();
            if (name.equals(column.getName())) {
                continue;
            }
            if (name.equals(target.getName()) && before) {
                rebuilt.put(column.getName(), value);
            }
            rebuilt.put(name, entry.getValue());
            if (name.equals(target.getName()) && !before) {
                rebuilt.put(column.getName(), value);
            }
        }
        member.clearAttributes();
        rebuilt.forEach(member::setAttribute);
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        for (int i = parts.size() - 1; i >= 0; i--) {
            parts.get(i).undo();
        }
        if (column.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
            for (int i = 0; i < members.size() && i < oldAttributes.size(); i++) {
                XmlElement member = members.get(i);
                member.clearAttributes();
                oldAttributes.get(i).forEach(member::setAttribute);
            }
        }
        RepeatingElementsTable.setCachedColumnOrder(elementName, oldOrder);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Move column " + (column != null ? column.getName() : "");
    }

    /** @return the column keys after the move */
    public List<String> newOrder() {
        return List.copyOf(newOrder);
    }

}
