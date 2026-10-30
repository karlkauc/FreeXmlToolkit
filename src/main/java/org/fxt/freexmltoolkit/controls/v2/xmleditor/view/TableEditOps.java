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
     * Renames a column in every member: the attribute or every child element of the old name.
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
                for (XmlElement child : member.getChildElements(oldName)) {
                    parts.add(new RenameNodeCommand(child, newName));
                }
            }
        }
        return new CompositeXmlCommand("Rename column " + oldName + " to " + newName, parts);
    }

    /**
     * The commands that write a pasted block of cell values into the table starting at the
     * given cell (XMLSpy: pasting a spreadsheet range): existing cells are overwritten, rows
     * beyond the last member are appended as new members (every column present), values
     * beyond the last column are ignored. Attribute cells become attribute values, element
     * cells the child's text (the child is created when the member lacks it), a text-content
     * column the member's own text.
     *
     * @param table    the table
     * @param startRow the row of the target cell
     * @param startCol the column of the target cell
     * @param values   the pasted values, row by row
     * @return one undoable step (empty when nothing is to do)
     */
    public static CompositeXmlCommand pasteCells(RepeatingElementsTable table, int startRow, int startCol,
                                                 List<List<String>> values) {
        List<XmlCommand> parts = new ArrayList<>();
        List<XmlElement> members = new ArrayList<>(table.getElements());
        List<RepeatingElementsTable.TableColumn> columns = table.getColumns();
        if (members.isEmpty() || startRow < 0 || startCol < 0) {
            return new CompositeXmlCommand("Paste cells", parts);
        }
        XmlNode parent = members.get(0).getParent();
        for (int i = 0; i < values.size(); i++) {
            int r = startRow + i;
            XmlElement member;
            if (r < members.size()) {
                member = members.get(r);
            } else {
                if (!(parent instanceof XmlElement parentElement)) {
                    break;
                }
                // Append a new member with every column present, right after the current last one
                member = new XmlElement(table.getElementName());
                for (RepeatingElementsTable.TableColumn col : columns) {
                    switch (col.getType()) {
                        case ATTRIBUTE -> member.setAttribute(col.getName(), "");
                        case CHILD_ELEMENT -> member.addChild(new XmlElement(col.getName()));
                        case TEXT_CONTENT -> { /* set below */ }
                    }
                }
                XmlElement last = members.get(members.size() - 1);
                parts.add(new AddElementCommand(parentElement, member, parentElement.indexOf(last) + 1));
                members.add(member);
            }
            List<String> rowValues = values.get(i);
            for (int j = 0; j < rowValues.size(); j++) {
                int c = startCol + j;
                if (c >= columns.size()) {
                    break;
                }
                RepeatingElementsTable.TableColumn col = columns.get(c);
                String value = rowValues.get(j);
                switch (col.getType()) {
                    case ATTRIBUTE -> parts.add(new SetAttributeCommand(member, col.getName(), value));
                    case CHILD_ELEMENT -> {
                        if (member.getChildElements(col.getName()).size() > 1) {
                            break; // a group of several children has no single value to overwrite
                        }
                        XmlElement child = member.getChildElement(col.getName());
                        if (child != null) {
                            parts.add(new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand(
                                    child, value));
                        } else {
                            parts.add(createCellChild(member, col.getName(), value));
                        }
                    }
                    case TEXT_CONTENT -> {
                        if (!member.hasElementChildren()) {
                            parts.add(new org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand(
                                    member, value));
                        }
                    }
                }
            }
        }
        return new CompositeXmlCommand("Paste " + values.size() + " row(s)", parts);
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
