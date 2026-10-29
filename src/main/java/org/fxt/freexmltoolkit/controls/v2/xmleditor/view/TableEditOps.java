package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.AddElementCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.CompositeXmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.RenameNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetAttributeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

/**
 * Builds the commands behind XMLSpy's whole-row and whole-column table operations on a
 * repeating group of XML elements: insert / append a row (a new member with every column
 * present but empty), insert a column (a new attribute or child element in every member) and
 * rename a column (in every member). Pure model code, no UI: the context menu asks the user
 * and hands the answers here; the canvas commits the result through the editor context.
 */
public final class TableEditOps {

    /** Where a new row or column goes relative to the clicked one. */
    public enum Placement {
        BEFORE,
        AFTER,
        APPEND
    }

    private TableEditOps() {
    }

    /**
     * A new member of the repeating group, with every column of the table present but empty
     * (attributes as {@code ""}, child elements without content), inserted next to the
     * reference row or appended after the last member.
     *
     * @param table     the table
     * @param refRow    the reference row index (ignored for {@link Placement#APPEND})
     * @param placement where the new row goes
     * @return the command, or {@code null} when the group has no XML parent
     */
    public static XmlCommand insertRow(RepeatingElementsTable table, int refRow, Placement placement) {
        List<XmlElement> members = table.getElements();
        if (members.isEmpty()) {
            return null;
        }
        XmlElement reference = placement == Placement.APPEND ? members.get(members.size() - 1)
                : members.get(Math.max(0, Math.min(refRow, members.size() - 1)));
        XmlNode parent = reference.getParent();
        if (!(parent instanceof XmlElement parentElement)) {
            return null;
        }
        XmlElement fresh = new XmlElement(table.getElementName());
        for (RepeatingElementsTable.TableColumn col : table.getColumns()) {
            switch (col.getType()) {
                case ATTRIBUTE -> fresh.setAttribute(col.getName(), "");
                case CHILD_ELEMENT -> fresh.addChild(new XmlElement(col.getName()));
                case TEXT_CONTENT -> { /* text is typed into the cell later */ }
            }
        }
        int index = parentElement.indexOf(reference) + (placement == Placement.BEFORE ? 0 : 1);
        return new AddElementCommand(parentElement, fresh, index);
    }

    /**
     * A new column in every member: an attribute (empty) or a child element (empty), placed
     * next to the reference column where the member has it, else at the end.
     *
     * @param table      the table
     * @param name       the attribute / element name
     * @param type       {@link RepeatingElementsTable.ColumnType#ATTRIBUTE} or {@code CHILD_ELEMENT}
     * @param refColumn  the reference column name (may be {@code null} = append)
     * @param placement  before or after the reference column
     * @return one undoable step covering every member (empty when nothing is to do)
     */
    public static CompositeXmlCommand insertColumn(RepeatingElementsTable table, String name,
                                                   RepeatingElementsTable.ColumnType type,
                                                   String refColumn, Placement placement) {
        List<XmlCommand> parts = new ArrayList<>();
        for (XmlElement member : table.getElements()) {
            if (type == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
                if (!member.hasAttribute(name)) {
                    parts.add(new SetAttributeCommand(member, name, ""));
                }
            } else {
                if (member.getChildElement(name) == null) {
                    int index = -1;
                    XmlElement ref = refColumn != null ? member.getChildElement(refColumn) : null;
                    if (ref != null) {
                        index = member.indexOf(ref) + (placement == Placement.BEFORE ? 0 : 1);
                    }
                    parts.add(new AddElementCommand(member, new XmlElement(name), index));
                }
            }
        }
        return new CompositeXmlCommand("Insert column " + name, parts);
    }

    /**
     * Renames a column in every member: the attribute or the (first) child element of the old
     * name.
     *
     * @param table   the table
     * @param oldName the column to rename
     * @param newName the new name
     * @return one undoable step covering every member (empty when nothing is to do)
     */
    public static CompositeXmlCommand renameColumn(RepeatingElementsTable table, String oldName, String newName) {
        List<XmlCommand> parts = new ArrayList<>();
        RepeatingElementsTable.TableColumn col = table.getColumn(oldName);
        if (col == null || newName == null || newName.isBlank() || newName.equals(oldName)) {
            return new CompositeXmlCommand("Rename column", parts);
        }
        for (XmlElement member : table.getElements()) {
            if (col.getType() == RepeatingElementsTable.ColumnType.ATTRIBUTE) {
                if (member.hasAttribute(oldName)) {
                    parts.add(new RenameAttributeCommand(member, oldName, newName));
                }
            } else if (col.getType() == RepeatingElementsTable.ColumnType.CHILD_ELEMENT) {
                XmlElement child = member.getChildElement(oldName);
                if (child != null) {
                    parts.add(new RenameNodeCommand(child, newName));
                }
            }
        }
        return new CompositeXmlCommand("Rename column " + oldName + " to " + newName, parts);
    }

    /**
     * A child element with the given text, for a cell whose member lacks the column's element.
     *
     * @param member the row's element
     * @param name   the column (child element) name
     * @param text   the cell text
     * @return the command appending the new child
     */
    public static XmlCommand createCellChild(XmlElement member, String name, String text) {
        XmlElement child = new XmlElement(name);
        if (text != null && !text.isEmpty()) {
            child.addChild(new XmlText(text));
        }
        return new AddElementCommand(member, child);
    }
}
