package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.Test;

/** Grid filters saved in the document as fxt-filter instructions; the grid shows no row for them. */
class GridFiltersTest {

    private static final String XML = """
            <Meldung>
              <List VRG="2">
                <?fxt-filter expression="Datenpunkt[starts-with(Code, 'B')]"?>
                <Datenpunkt><Code>A</Code></Datenpunkt>
                <Datenpunkt><Code>B</Code></Datenpunkt>
              </List>
              <List VRG="4"><Datenpunkt><Code>C</Code></Datenpunkt><Datenpunkt><Code>D</Code></Datenpunkt></List>
            </Meldung>
            """;

    private static XmlEditorContext load(String xml) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml);
        return context;
    }

    @Test
    void expressionRoundTripsThroughTheInstructionData() {
        String tricky = "Datenpunkt[Code = \"a&b\" and Wert > 1 and starts-with(X, '?>')]";
        XmlProcessingInstruction pi = GridFilters.create(tricky);
        assertEquals(GridFilters.TARGET, pi.getTarget());
        assertEquals(tricky, GridFilters.parse(pi.getData()));
        assertFalse(pi.getData().contains("?>"), "the data never ends the instruction early");
        assertEquals("", GridFilters.parse(null));
    }

    @Test
    void savedFiltersAreKeyedLikeTheGridRowOfTheirElement() {
        XmlEditorContext context = load(XML);
        Map<String, XmlProcessingInstruction> saved = GridFilters.savedFilters(context.getDocument());
        assertEquals(1, saved.size());
        XmlElement record = context.getDocument().getRootElement().getChildElements("List").getFirst();
        FlatRow anchor = new FlatRow(FlatRow.RowType.ELEMENT, -1, record, null, record.getName(), null, 0);
        assertEquals(new XmlGridAdapter(context).stableKey(anchor), saved.keySet().iterator().next());
        assertEquals("Datenpunkt[starts-with(Code, 'B')]", new XmlGridAdapter(context).savedFilters().values().iterator().next());
    }

    @Test
    void aSavedFilterHasNoRowAndDoesNotCountAsChild() {
        XmlEditorContext context = load("<r><a><?fxt-filter expression=\"x\"?><b>1</b></a></r>");
        List<FlatRow> rows = FlatRow.flatten(context.getDocument());
        assertTrue(rows.stream().noneMatch(r -> r.getType() == FlatRow.RowType.PROCESSING_INSTRUCTION));
        FlatRow a = rows.stream().filter(r -> "a".equals(r.getLabel())).findFirst().orElseThrow();
        assertEquals(1, a.getChildCount(), "only <b>");

        XmlElement record = context.getDocument().getRootElement();
        XmlGridRecord cell = new XmlGridRecord(record);
        assertTrue(cell.flattenCell("a", FlatRow.ALWAYS_TABLE).stream()
                .noneMatch(r -> r.getType() == FlatRow.RowType.PROCESSING_INSTRUCTION), "nor inside an opened cell");
    }

    @Test
    void theMemoSkipsUnchangedSubtreesButSeesNewAndRemovedFilters() {
        StringBuilder xml = new StringBuilder("<Orders>");
        for (int i = 0; i < GridFilters.MEMO_CHILDREN + 5; i++) {
            xml.append("<Order><Items><Item>").append(i).append("</Item><Item>x</Item></Items></Order>");
        }
        XmlEditorContext context = load(xml.append("</Orders>").toString());
        GridFilters.Memo memo = new GridFilters.Memo();
        assertTrue(GridFilters.savedFilters(context.getDocument(), memo).isEmpty());

        XmlElement items = context.getDocument().getRootElement().getChildElements("Order").get(7)
                .getChildElement("Items");
        XmlProcessingInstruction pi = GridFilters.create("Item[. = 'x']");
        items.addChild(0, pi);
        Map<String, XmlProcessingInstruction> saved = GridFilters.savedFilters(context.getDocument(), memo);
        assertEquals(Map.of(org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNodeXPath.positional(items), pi), saved,
                "a filter added below a remembered subtree is found");
        assertEquals(saved, GridFilters.savedFilters(context.getDocument()), "same result as a full scan");

        items.removeChild(pi);
        assertTrue(GridFilters.savedFilters(context.getDocument(), memo).isEmpty());
    }
}
