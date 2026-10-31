package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.serialization.StreamingXmlParser;
import org.junit.jupiter.api.Test;

/**
 * The grid's descendant ranges are computed once per layout ({@link GridCanvasView#lastDescendantIndices})
 * and expanding a row groups the children once ({@link FlatRow#toggleExpand}); both used to rescan the row
 * list per row. The former algorithms are the oracle on random documents and random expand states.
 */
class GridRowRangesTest {

    @Test
    void descendantRangesMatchTheForwardScan() throws Exception {
        Random random = new Random(11);
        for (int doc = 0; doc < 150; doc++) {
            List<FlatRow> rows = FlatRow.flatten(parse(randomXml(random)));
            randomExpandState(rows, random);
            List<FlatRow> visible = rows.stream().filter(FlatRow::isVisible).toList();

            int[] expected = new int[visible.size()];
            for (int i = 0; i < visible.size(); i++) {
                expected[i] = legacyLastVisibleDescendant(visible, i);
            }
            assertArrayEquals(expected, GridCanvasView.lastDescendantIndices(visible), "doc " + doc);
        }
    }

    @Test
    void expandingMatchesTheFormerRecursion() throws Exception {
        Random random = new Random(12);
        for (int doc = 0; doc < 150; doc++) {
            String xml = randomXml(random);
            long seed = random.nextLong();
            List<FlatRow> current = FlatRow.flatten(parse(xml));
            List<FlatRow> legacy = FlatRow.flatten(parse(xml));
            randomExpandState(current, new Random(seed));
            randomExpandState(legacy, new Random(seed));

            for (int toggle = 0; toggle < 6; toggle++) {
                int pick = random.nextInt(current.size());
                FlatRow.toggleExpand(current.get(pick), current);
                legacyToggleExpand(legacy.get(pick), legacy);
                assertEquals(visibility(legacy), visibility(current), "doc " + doc + " toggle " + toggle);
            }
        }
    }

    // ---- helpers and the former algorithms --------------------------------------------------------

    private static XmlDocument parse(String xml) throws Exception {
        return new StreamingXmlParser().parse(xml);
    }

    private static String randomXml(Random random) {
        StringBuilder xml = new StringBuilder();
        appendElement(xml, random, 0);
        return xml.toString();
    }

    private static void appendElement(StringBuilder xml, Random random, int depth) {
        String name = "e" + random.nextInt(5);
        xml.append('<').append(name);
        for (int a = random.nextInt(3); a > 0; a--) {
            xml.append(" a").append(a).append("=\"").append(random.nextInt(9)).append('"');
        }
        int children = depth > 4 ? 0 : random.nextInt(4);
        if (children == 0 && random.nextBoolean()) {
            xml.append("/>");
            return;
        }
        xml.append('>');
        for (int i = 0; i < children; i++) {
            if (random.nextInt(4) == 0) {
                xml.append("text").append(i);
            } else {
                appendElement(xml, random, depth + 1);
            }
        }
        xml.append("</").append(name).append('>');
    }

    private static void randomExpandState(List<FlatRow> rows, Random random) {
        for (FlatRow row : rows) {
            if (row.getType().isContainer()) {
                row.setExpanded(random.nextInt(3) != 0);
            }
        }
        FlatRow.applyVisibility(rows);
    }

    private static List<Boolean> visibility(List<FlatRow> rows) {
        List<Boolean> out = new ArrayList<>();
        for (FlatRow row : rows) {
            out.add(row.isVisible());
        }
        return out;
    }

    private static int legacyLastVisibleDescendant(List<FlatRow> visible, int parentIndex) {
        int lastIndex = parentIndex;
        for (int i = parentIndex + 1; i < visible.size(); i++) {
            if (FlatRow.isDescendantOf(visible.get(i), visible.get(parentIndex))) {
                lastIndex = i;
            } else {
                break;
            }
        }
        return lastIndex;
    }

    private static void legacyToggleExpand(FlatRow elementRow, List<FlatRow> allRows) {
        if (!elementRow.getType().isContainer()) {
            return;
        }
        if (elementRow.isExpanded()) {
            elementRow.setExpanded(false);
            for (FlatRow row : allRows) {
                if (FlatRow.isDescendantOf(row, elementRow)) {
                    if (row.getType().isAttribute() && row.getParentRow() == elementRow) {
                        continue;
                    }
                    row.setVisible(false);
                }
            }
        } else {
            elementRow.setExpanded(true);
            for (FlatRow row : allRows) {
                if (row.getParentRow() == elementRow) {
                    row.setVisible(true);
                    if (row.getType().isContainer() && row.isExpanded()) {
                        legacyShowExpandedSubtree(row, allRows);
                    } else {
                        legacyShowAttributes(row, allRows);
                    }
                }
            }
        }
    }

    /** Attributes always show with their element, even a collapsed one or a leaf. */
    private static void legacyShowAttributes(FlatRow elementRow, List<FlatRow> allRows) {
        for (FlatRow row : allRows) {
            if (row.getParentRow() == elementRow && row.getType().isAttribute()) {
                row.setVisible(true);
            }
        }
    }

    private static void legacyShowExpandedSubtree(FlatRow elementRow, List<FlatRow> allRows) {
        for (FlatRow row : allRows) {
            if (row.getParentRow() == elementRow) {
                row.setVisible(true);
                if (row.getType().isContainer() && row.isExpanded()) {
                    legacyShowExpandedSubtree(row, allRows);
                } else {
                    legacyShowAttributes(row, allRows);
                }
            }
        }
    }
}
