package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.Test;

/** A table record has no row of its own: its formulas are listed in the opened cell they summarize. */
class XmlGridRecordFormulaTest {

    private static final String XML = """
            <List VRG="2">
              <Datenpunkt><v>1</v></Datenpunkt>
              <Datenpunkt><v>2</v></Datenpunkt>
              <other><x>a</x></other>
              <other><x>b</x></other>
              <?fxt-formula name="Summe_v" store="false" expression="sum(Datenpunkt/v)"?>
              <?fxt-formula name="Anzahl" store="false" expression="count(other)"?>
              <?fxt-formula name="Konstante" store="false" expression="1 + 1"?>
            </List>
            """;

    private static XmlElement record(String xml) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml);
        return context.getDocument().getRootElement();
    }

    private static List<String> formulaNames(List<XmlProcessingInstruction> formulas) {
        return formulas.stream().map(pi -> GridFormulas.parse(pi.getData()).name()).toList();
    }

    @Test
    void eachFormulaGoesToTheCellItsExpressionNames() {
        XmlGridRecord record = new XmlGridRecord(record(XML));
        assertEquals(List.of("Summe_v", "Konstante"), formulaNames(record.cellFormulas("Datenpunkt")),
                "a formula naming no column goes to the first complex cell");
        assertEquals(List.of("Anzahl"), formulaNames(record.cellFormulas("other")));
        assertTrue(record.cellFormulas("VRG").isEmpty(), "an attribute column has no cell");
    }

    @Test
    void theOpenedCellListsItsFormulasFirst() {
        XmlGridRecord record = new XmlGridRecord(record(XML));
        List<FlatRow> rows = record.flattenCell("Datenpunkt", FlatRow.ALWAYS_TABLE);
        assertEquals(FlatRow.RowType.PROCESSING_INSTRUCTION, rows.get(0).getType());
        assertEquals(FlatRow.RowType.PROCESSING_INSTRUCTION, rows.get(1).getType());
        assertEquals("Datenpunkt", rows.get(2).getLabel(), "then the group");
        assertTrue(rows.get(2).isExpanded(), "the cell IS the group: its table shows at once, after the formulas");
        assertEquals(2, rows.stream().filter(r -> r.getType() == FlatRow.RowType.PROCESSING_INSTRUCTION).count());
        assertEquals("3", GridFormulas.evaluate((XmlProcessingInstruction) rows.get(0).getModelNode()).result(),
                "evaluated against the record");
    }

    @Test
    void aFormulaOfTheShownElementIsNotListedTwice() {
        XmlElement root = record("""
                <List>
                  <Datenpunkt><v>1</v><w>2</w><?fxt-formula name="own" store="false" expression="v + w"?></Datenpunkt>
                  <?fxt-formula name="outer" store="false" expression="sum(Datenpunkt/v)"?>
                </List>
                """);
        XmlGridRecord record = new XmlGridRecord(root);
        List<FlatRow> rows = record.flattenCell("Datenpunkt", FlatRow.ALWAYS_TABLE);
        List<String> formulas = rows.stream().filter(r -> r.getType() == FlatRow.RowType.PROCESSING_INSTRUCTION)
                .map(r -> GridFormulas.parse(r.getValue()).name()).toList();
        assertEquals(List.of("outer", "own"), formulas);
    }
}
