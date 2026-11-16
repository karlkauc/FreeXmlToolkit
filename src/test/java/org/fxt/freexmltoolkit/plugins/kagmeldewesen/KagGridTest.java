package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridFormulas;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlGridQuery;
import org.fxt.freexmltoolkit.plugins.regreport.GridAnnotations;
import org.junit.jupiter.api.Test;

/** The KAG grid formulas run in the grid's own engine without errors and give the expected values. */
class KagGridTest {

    private static String resource(String name) throws Exception {
        try (InputStream in = KagGridTest.class.getResourceAsStream("/plugins/kagmeldewesen/examples/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** @return element/name → result of every formula; a filter is checked to select rows */
    private static Map<String, String> evaluate(String xml) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml);
        Map<String, String> results = new LinkedHashMap<>();
        walk(context.getDocument().getRootElement(), results);
        return results;
    }

    private static void walk(XmlElement element, Map<String, String> results) {
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlProcessingInstruction pi && GridFormulas.isFormula(pi)) {
                GridFormulas.Evaluated e = GridFormulas.evaluate(pi);
                assertNull(e.error(), element.getName() + "/" + e.formula().name() + ": " + e.error());
                results.put(element.getName() + "/" + e.formula().name(), e.result());
            } else if (child instanceof XmlProcessingInstruction pi && "fxt-filter".equals(pi.getTarget())) {
                String expression = pi.getData().replaceFirst("^expression=\"", "").replaceFirst("\"$", "")
                        .replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
                results.put(element.getName() + "/filter", XmlGridQuery.evaluate(element, "count(" + expression + ")").text());
            } else if (child instanceof XmlElement e) {
                walk(e, results);
            }
        }
    }

    @Test
    void derimvFormulasAndFilter() throws Exception {
        KagSettings.Options options = new KagSettings.Options(new BigDecimal("0.5"), new BigDecimal(200), true, new BigDecimal(60));
        String xml = GridAnnotations.add(resource("derimv-example.xml"), KagGrid.annotations("DeRiMV", options));
        Map<String, String> r = evaluate(xml);
        assertEquals("4", r.get("Derivatemeldung/Fonds"));
        assertEquals("Comm: 2 · aVaR: 1 · rVaR: 1", r.get("Derivatemeldung/Je_Methode"));
        // rVaR 131.4 of 200 = 65.7 %; aVaR 6.85 of 20 = 34.25 %; Comm 18.75 %
        assertEquals("65,7 %", r.get("Derivatemeldung/Hoechste_Auslastung"));
        assertEquals("1", r.get("Derivatemeldung/Ab_60_Prozent"));
        assertEquals("0", r.get("Derivatemeldung/Ueber_Grenze"));
        assertEquals("6,85 %", r.get("Derivatemeldung/Max_aVaR"));
        assertEquals("1", r.get("Derivatemeldung/filter"));
        assertEquals(resource("derimv-example.xml"), GridAnnotations.remove(xml));
    }

    @Test
    void aifSumsOfPercentages() throws Exception {
        String xml = GridAnnotations.add(resource("aif-example.xml"), KagGrid.annotations("AIF", KagSettings.Options.defaults()));
        Map<String, String> r = evaluate(xml);
        assertTrue(r.size() >= 4, r.toString());
        assertEquals("100,00", r.get("NAVGeographicalFocus/Summe"), r.toString());
        assertEquals("100,00", r.get("AUMGeographicalFocus/Summe"), r.toString());
        assertEquals("100,00", r.get("InvestorConcentration/Summe"), r.toString());
        assertTrue(KagGrid.annotations("MMF", KagSettings.Options.defaults()).isEmpty());
    }
}
