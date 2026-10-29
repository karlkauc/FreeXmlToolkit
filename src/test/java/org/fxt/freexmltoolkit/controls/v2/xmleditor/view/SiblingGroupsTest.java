package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.junit.jupiter.api.Test;

/**
 * XMLSpy-style sibling groups: a container with more child rows than the group size gets them
 * split into collapsed group rows; attributes stay with their element; groups open exclusively;
 * a group row is no node for the adapter.
 */
class SiblingGroupsTest {

    private static List<FlatRow> rows(int children, int groupSize) {
        StringBuilder xml = new StringBuilder("<root id=\"r\">");
        for (int i = 0; i < children; i++) {
            xml.append("<n").append(i % 2).append(">").append(i).append("</n").append(i % 2).append('>');
        }
        xml.append("</root>");
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml.toString());
        // Every child has a distinct-enough name pair; disable tables so all children are rows
        List<FlatRow> flat = FlatRow.flatten(context.getDocument(), key -> false);
        return FlatRow.groupSiblings(flat, groupSize);
    }

    private static List<FlatRow> groups(List<FlatRow> rows) {
        return rows.stream().filter(r -> r.getType() == FlatRow.RowType.GROUP).toList();
    }

    @Test
    void largeContainersAreSplitIntoGroupsOfTheGivenSize() {
        List<FlatRow> rows = rows(25, 10);
        List<FlatRow> groups = groups(rows);
        assertEquals(3, groups.size());
        assertEquals("<1..10>", groups.get(0).getLabel(), "mixed names: range only");
        assertEquals("<21..25>", groups.get(2).getLabel());
        assertEquals(10, groups.get(0).getChildCount());
        assertEquals(5, groups.get(2).getChildCount());
        FlatRow root = rows.get(0);
        assertSame(root, groups.get(0).getParentRow());
        FlatRow firstChild = rows.get(rows.indexOf(groups.get(0)) + 1);
        assertSame(groups.get(0), firstChild.getParentRow(), "children are reparented to their group");
        FlatRow attribute = rows.get(1);
        assertEquals(FlatRow.RowType.ATTRIBUTE, attribute.getType());
        assertSame(root, attribute.getParentRow(), "attributes stay with their element");
        assertFalse(groups.get(0).isExpanded(), "groups start collapsed");
    }

    @Test
    void smallContainersAndGroupSizeZeroAreUnchanged() {
        assertTrue(groups(rows(10, 10)).isEmpty(), "exactly the size: no groups");
        assertTrue(groups(rows(25, 0)).isEmpty(), "0 = unlimited");
    }

    @Test
    void sameNamedRangesAreLabelledWithTheName() {
        StringBuilder xml = new StringBuilder("<root>");
        for (int i = 0; i < 6; i++) {
            xml.append("<item>").append(i).append("</item>");
        }
        xml.append("</root>");
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml.toString());
        List<FlatRow> rows = FlatRow.groupSiblings(FlatRow.flatten(context.getDocument(), key -> false), 4);
        assertEquals(List.of("item <1..4>", "item <5..6>"), groups(rows).stream().map(FlatRow::getLabel).toList());
    }

    @Test
    void groupsOpenExclusivelyAndHideTheirChildren() {
        List<FlatRow> rows = rows(25, 10);
        FlatRow.applyVisibility(rows);
        List<FlatRow> groups = groups(rows);
        FlatRow memberOfFirst = rows.get(rows.indexOf(groups.get(0)) + 1);
        FlatRow memberOfSecond = rows.get(rows.indexOf(groups.get(1)) + 1);
        assertFalse(memberOfFirst.isVisible(), "collapsed group hides its children");

        FlatRow.toggleExpand(groups.get(0), rows);
        assertTrue(memberOfFirst.isVisible());
        FlatRow.toggleExpand(groups.get(1), rows);
        assertTrue(memberOfSecond.isVisible());
        assertFalse(groups.get(0).isExpanded(), "opening the second group closed the first");
        assertFalse(memberOfFirst.isVisible());
    }

    @Test
    void aGroupRowIsNoNodeForTheAdapter() {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString("<root><a/><b/><c/></root>");
        XmlGridAdapter adapter = new XmlGridAdapter(context);
        FlatRow group = groups(FlatRow.groupSiblings(adapter.flatten(), 2)).get(0);
        assertNull(adapter.nodeOf(group), "menu actions must not hit the parent element");
        assertFalse(adapter.canEditValue(group));
        assertFalse(adapter.canEditName(group));
        assertFalse(adapter.canDrag(group));
        assertTrue(adapter.typeTargets(group).isEmpty());
    }
}
