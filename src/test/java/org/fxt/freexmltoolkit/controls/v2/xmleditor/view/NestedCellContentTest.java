package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A table cell holds every child of its column, and an opened cell shows them like the root grid
 * would: the four {@code Position}s of a {@code Waehrung} as numbered, expandable rows.
 */
class NestedCellContentTest {

    private static final String POSITION = "<Position Posnr=\"%s\"><Wert>%s</Wert></Position>";
    private static final String XML = "<Land Isocode=\"AT\">"
            + "<Waehrung Isocode=\"CZK\">" + POSITION.formatted("0200000", "1") + POSITION.formatted("0215000", "2")
            + POSITION.formatted("0900000", "3") + POSITION.formatted("0905000", "4") + "</Waehrung>"
            + "<Waehrung Isocode=\"HRK\">" + POSITION.formatted("0200000", "5") + "</Waehrung>"
            + "</Land>";

    private List<XmlElement> waehrungen;

    @BeforeEach
    void load() {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        waehrungen = context.getDocument().getRootElement().getChildElements("Waehrung");
    }

    @Test
    void aColumnWithSeveralChildrenHoldsThemAll() {
        XmlGridRecord czk = new XmlGridRecord(waehrungen.getFirst());

        assertEquals(List.of("@Isocode", "Position"), czk.columnKeys(), "one column per name");
        assertEquals(4, czk.cellMembers("Position").size());
        assertTrue(czk.complexChildren().containsKey("Position"), "a group of children is a complex cell");
        assertEquals("4 × <Position>", czk.values().get("Position"));
        assertNull(czk.attributeSuffixes().get("Position"), "no single element's attributes as suffix");
    }

    @Test
    void anOpenedGroupCellShowsEveryMemberAsAnExpandableNumberedRow() {
        List<FlatRow> rows = new XmlGridRecord(waehrungen.getFirst()).flattenComplexChild("Position");

        List<FlatRow> members = rows.stream().filter(r -> r.getDepth() == 0).toList();
        assertEquals(4, members.size());
        for (int i = 0; i < 4; i++) {
            assertEquals("Position", members.get(i).getLabel());
            assertEquals(i + 1, members.get(i).getInstanceIndex());
            assertTrue(members.get(i).isExpandable());
        }
        FlatRow second = members.get(1);
        List<FlatRow> children = rows.stream().filter(r -> r.getParentRow() == second).toList();
        assertEquals(List.of("Posnr", "Wert"), children.stream().map(FlatRow::getLabel).toList());
        assertEquals("2", children.get(1).getValue());
    }

    @Test
    void aSingleComplexChildStillOpensToItsOwnContent() {
        XmlGridRecord hrk = new XmlGridRecord(waehrungen.get(1));
        List<FlatRow> rows = hrk.flattenComplexChild("Position");

        assertEquals(List.of("Posnr", "Wert"), rows.stream().map(FlatRow::getLabel).toList());
        assertEquals("0200000", rows.getFirst().getValue());
    }

    @Test
    void openingACellShowsOnlyTheTopLevelUntilRowsAreExpanded() {
        RepeatingElementsTable table = new RepeatingElementsTable("Waehrung", waehrungen, 0, () -> { });
        RepeatingElementsTable.TableRow czk = table.getRows().getFirst();

        czk.toggleColumnExpanded("Position");

        List<FlatRow> rows = czk.getExpandedCellRows("Position");
        // The collapsed members with their attributes (attributes stay visible, as in the root grid)
        assertEquals(8, rows.stream().filter(FlatRow::isVisible).count(), "4 × Position + 4 × Posnr");
        assertTrue(rows.stream().filter(r -> "Wert".equals(r.getLabel())).noneMatch(FlatRow::isVisible));
        FlatRow first = rows.getFirst();
        FlatRow.toggleExpand(first, rows);
        assertEquals(9, rows.stream().filter(FlatRow::isVisible).count(), "+ Wert of the first");
    }

    @Test
    void repeatingGroupsInsideACellAreGroupedWithTheirStableKey() {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString("<r><Item><Box><Part>1</Part><Part>2</Part><Part>3</Part></Box></Item>"
                + "<Item><Box><Part>4</Part></Box></Item></r>");
        XmlElement item = context.getDocument().getRootElement().getChildElements("Item").getFirst();

        List<FlatRow> asRows = FlatRow.flattenCellContent(item, List.of(item.getChildElement("Box")), FlatRow.CELL_GROUPS);
        assertEquals(List.of(1, 2, 3), asRows.stream().map(FlatRow::getInstanceIndex).toList());
        assertEquals("/r/Item[1]/Box/Part", asRows.getFirst().getStandardGroupKey());

        List<FlatRow> asTable = FlatRow.flattenCellContent(item, List.of(item.getChildElement("Box")), FlatRow.ALWAYS_TABLE);
        assertEquals(1, asTable.size(), "one row for the whole group");
        assertEquals(3, asTable.getFirst().getTableMembers().size());
    }
}
