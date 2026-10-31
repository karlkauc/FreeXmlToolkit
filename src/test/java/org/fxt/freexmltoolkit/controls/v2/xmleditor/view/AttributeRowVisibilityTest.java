package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A leaf element with attributes ({@code <Amount ccy="EUR">12</Amount>}) shows its value and its
 * attributes: in the tree after its parent is opened, and in an opened cell that only wraps it.
 */
class AttributeRowVisibilityTest {

    private static final String XML = "<Fund><TotalNetAssetValue><Amount ccy=\"EUR\">255.05</Amount></TotalNetAssetValue>"
            + "<Position><ID>P1</ID><TotalValue><Amount ccy=\"USD\">792.39</Amount></TotalValue></Position></Fund>";

    private XmlDocument document;

    @BeforeEach
    void load() {
        RepeatingElementsTable.clearAllCaches();
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(XML);
        document = context.getDocument();
    }

    private static FlatRow row(List<FlatRow> rows, String label) {
        return rows.stream().filter(r -> label.equals(r.getLabel())).findFirst().orElseThrow();
    }

    @Test
    void openingTheParentShowsTheLeafsAttributes() {
        List<FlatRow> rows = FlatRow.flatten(document);
        rows.forEach(r -> r.setExpanded(false));
        FlatRow.applyVisibility(rows);

        FlatRow.toggleExpand(row(rows, "Fund"), rows);
        FlatRow.toggleExpand(row(rows, "TotalNetAssetValue"), rows);

        FlatRow amount = row(rows, "Amount");
        assertTrue(amount.isVisible());
        assertEquals("255.05", amount.getValue(), "the value beside the name");
        FlatRow ccy = row(rows, "ccy");
        assertSame(amount, ccy.getParentRow());
        assertTrue(ccy.isVisible(), "the attribute shows with its element");
    }

    @Test
    void aCellWrappingALeafShowsTheLeafWithValueAndAttributes() {
        List<FlatRow> rows = new XmlGridRecord(document.getRootElement().getChildElement("Position"))
                .flattenComplexChild("TotalValue");
        FlatRow.applyVisibility(rows);

        FlatRow amount = row(rows, "Amount");
        assertEquals("792.39", amount.getValue(), "the leaf stays a row of its own, with its value");
        assertTrue(amount.isVisible());
        FlatRow ccy = row(rows, "ccy");
        assertEquals("USD", ccy.getValue());
        assertTrue(ccy.isVisible());
    }
}
