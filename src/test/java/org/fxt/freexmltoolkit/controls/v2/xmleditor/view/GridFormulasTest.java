package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.InsertNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Grid formulas: persisted as fxt-formula instructions, evaluated live, results optionally stored. */
class GridFormulasTest {

    private XmlEditorContext context;
    private XmlElement temps;

    @BeforeEach
    void setUp() {
        context = new XmlEditorContext();
        context.loadDocumentFromString("<Temperatures><Month><Min>-5</Min></Month><Month><Min>12</Min></Month></Temperatures>");
        temps = context.getDocument().getRootElement();
    }

    private XmlProcessingInstruction add(GridFormulas.Formula formula) {
        XmlProcessingInstruction pi = GridFormulas.create(formula);
        assertTrue(context.executeCommand(new InsertNodeCommand(temps, pi, -1)));
        return pi;
    }

    @Test
    void definitionRoundTripsThroughTheInstructionDataAndTheFile() {
        GridFormulas.Formula f = new GridFormulas.Formula("Tricky", "concat(\"a\", '&', 1 > 0, \"?>\")", true);
        assertEquals(f, GridFormulas.parse(f.toData()));
        assertFalse(f.toData().contains("?>"), "the data never ends the instruction early");

        add(f);
        XmlEditorContext reloaded = new XmlEditorContext();
        reloaded.loadDocumentFromString(context.serializeToString());
        XmlProcessingInstruction pi = (XmlProcessingInstruction) reloaded.getDocument().getRootElement()
                .getChild(reloaded.getDocument().getRootElement().getChildCount() - 1);
        assertTrue(GridFormulas.isFormula(pi));
        assertEquals(f, GridFormulas.parse(pi.getData()), "saved and loaded with the file");
    }

    @Test
    void evaluatesWithTheParentAsContextAndReportsErrors() {
        XmlProcessingInstruction ok = add(new GridFormulas.Formula("MinMin", "min(Month/Min)", false));
        GridFormulas.Evaluated e = GridFormulas.evaluate(ok);
        assertEquals("-5", e.result());
        assertNull(e.error());

        XmlProcessingInstruction bad = add(new GridFormulas.Formula("Bad", "min(((", false));
        assertNull(GridFormulas.evaluate(bad).result());
        assertNotNull(GridFormulas.evaluate(bad).error());
        assertNull(GridFormulas.storeResult(bad), "no result to store");
    }

    @Test
    void storedResultsAreCreatedUpdatedAndUndoneAsOneStep() {
        XmlProcessingInstruction pi = add(new GridFormulas.Formula("Count", "count(Month)", true));
        assertTrue(context.executeCommand(GridFormulas.storeResult(pi)));
        XmlElement stored = temps.getChildElement("Count");
        assertNotNull(stored, "the result element follows the instruction");
        assertEquals(temps.indexOf(pi) + 1, temps.indexOf(stored));
        assertEquals("2", stored.getTextContent());

        // A new month, then recalculation updates the existing element
        XmlElement march = new XmlElement("Month");
        context.executeCommand(new InsertNodeCommand(temps, march, 0));
        assertTrue(context.executeCommand(GridFormulas.recalculateAll(context.getDocument())));
        assertEquals("3", temps.getChildElement("Count").getTextContent());
        context.undo();
        assertEquals("2", temps.getChildElement("Count").getTextContent(), "one undo step");
    }

    @Test
    void theAdapterExposesFormulaRowsAndEditsThem() {
        XmlProcessingInstruction pi = add(new GridFormulas.Formula("Avg", "avg(Month/Min)", false));
        XmlGridAdapter adapter = new XmlGridAdapter(context);
        FlatRow row = adapter.flatten().stream().filter(r -> r.getModelNode() == pi).findFirst().orElseThrow();
        GridModelAdapter.FormulaView view = adapter.formula(row);
        assertEquals("Avg", view.name());
        assertEquals("3.5", view.result());
        assertEquals("avg(Month/Min)", adapter.editSpec(row, false).currentValue());
        assertTrue(adapter.typeTargets(row).isEmpty(), "a formula keeps its type");

        adapter.commitRowEdit(row, false, "max(Month/Min)");
        adapter.commitRowEdit(row, true, "Max");
        assertEquals(new GridFormulas.Formula("Max", "max(Month/Min)", false), GridFormulas.parse(pi.getData()));
    }
}
