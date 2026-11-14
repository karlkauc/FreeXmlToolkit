package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.ElementDescriptions;
import org.fxt.freexmltoolkit.plugin.ElementDescriber.ElementInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The FMA labels from the shipped workbooks (Positionsbeschreibung, Dictionary): the dictionary,
 * the element describer, the code hover and the grid formulas of the quarterly report.
 */
class PkLabelsTest {

    @TempDir
    static Path store;
    private static PkTaxonomy taxonomy;
    private static PkDictionary dict;

    @BeforeAll
    static void load() {
        taxonomy = new PkTaxonomy(store);
        taxonomy.ensureBundled();
        dict = PkDictionary.of(taxonomy.dir(PkTaxonomy.BUNDLED_VERSION));
    }

    @Test
    void dictionaryReadsTheWorkbooksAndCachesThem() {
        assertFalse(dict.isEmpty());
        assertEquals("Vermögensausweis", dict.template("PK.800.01"));
        assertEquals("Informationen zu den gehaltenen Positionen", dict.template("PK.006.02.01.01"));
        assertEquals("Laufende Guthaben", dict.row("R800-110").name());
        assertEquals("PK_VA_1", dict.row("R000-120").domain());
        assertEquals("Asset ID Code", dict.column("PK.006.02.01.01", "C0010PK").name());
        assertEquals("Asset ID Code SubBestand", dict.column("PK.006.03", "C1010PK").name());
        assertEquals("Bilanzwert", dict.column("PK.800.01", "C800-100").name());
        assertEquals("Ja", dict.valueLabel("PK_JN_1", "J"));
        assertEquals("Summen-VRG", dict.values("PK_VA").get("0"));
        assertNull(dict.valueLabel("PK_JN_1", "X"));
        assertTrue(Files.isRegularFile(taxonomy.dir(PkTaxonomy.BUNDLED_VERSION).resolve(PkDictionary.CACHE_FILE)));
        assertNotNull(taxonomy.workbook(PkTaxonomy.BUNDLED_VERSION, "Positionsbeschreibung"));
    }

    private static ElementInfo info(List<String> path, Map<String, String> children, Map<String, String> siblings, String text) {
        return new ElementInfo(path, Map.of(), children, siblings, text, List.of());
    }

    @Test
    void describerNamesTemplatesFactsAndColumns() {
        PkDescriber describer = new PkDescriber(() -> dict);
        String root = "Nationale_Meldung_pfq";
        String tpl = "PK.800.01_Vermögensausweis";
        assertEquals("PK.800.01 · Vermögensausweis", describer.describe(info(List.of(root, tpl), Map.of(), Map.of(), "")));

        Map<String, String> fact = Map.of("TemplateCode", "PK.800.01", "RowCode", "R800-110", "ColumnCode", "C800-100", "Wert", "5");
        String factText = describer.describe(info(List.of(root, tpl, "Datenpunkt"), fact, Map.of(), ""));
        assertTrue(factText.startsWith("R800-110 · Laufende Guthaben\nC800-100 · Bilanzwert"), factText);
        assertFalse(factText.contains("xs:decimal"), factText);
        // a part of the fact describes the fact
        assertEquals(factText, describer.describe(info(List.of(root, tpl, "Datenpunkt", "RowCode"), Map.of(), fact, "R800-110")));

        Map<String, String> stamm = Map.of("RowCode", "R000-120", "ColumnCode", "C000-100");
        String art = describer.describe(info(List.of(root, "PK.000.01_Stammdaten_zur_VRG", "Datenpunkt_string", "Wert"),
                Map.of(), stamm, "1"));
        assertTrue(art.contains("R000-120 · VRG Art") && art.contains("1 = VRG ohne Sub-VG") && art.contains("Domain: PK_VA_1"), art);

        String column = describer.describe(info(List.of(root, "PK.006.02.01.01_List_of_Assets", "Datenpunkt", "Wert_C1130PK"),
                Map.of(), Map.of("Wert_C0010PK", "AT0000000002"), "N"));
        assertTrue(column.startsWith("C1130PK · HTM\nPK.006.02.01.01 · Informationen zu den gehaltenen Positionen\nN = Nein"), column);

        assertNull(describer.describe(info(List.of("FundsXML4", "ControlData"), Map.of(), Map.of(), "")));
        assertNull(new PkDescriber(() -> null).describe(info(List.of(root, tpl), Map.of(), Map.of(), "")));
    }

    @Test
    void longExplanationsAreCut() {
        String text = PkDescriber.factText(dict, "PK.800.01", "R800-110", null, null);
        assertTrue(text.length() < PkDescriber.MAX_EXPLANATION + 200, String.valueOf(text.length()));
    }

    @Test
    void elementDescriptionsReachTheDescriberThroughThePluginManager() {
        XmlElement root = new XmlElement("Nationale_Meldung_pfq");
        XmlElement template = new XmlElement("PK.800.01_Vermögensausweis");
        template.setAttribute("VRG", "1");
        root.addChild(template);
        XmlElement point = new XmlElement("Datenpunkt");
        template.addChild(point);
        for (String[] f : new String[][] {{"RowCode", "R800-110"}, {"ColumnCode", "C800-100"}, {"Wert", "5"}}) {
            XmlElement child = new XmlElement(f[0]);
            child.setTextContent(f[1]);
            point.addChild(child);
        }
        ElementInfo info = ElementDescriptions.info((XmlElement) point.getChildElement("Wert"));
        assertEquals(List.of("Nationale_Meldung_pfq", "PK.800.01_Vermögensausweis", "Datenpunkt", "Wert"), info.path());
        assertEquals("R800-110", info.parentChildTexts().get("RowCode"));
        assertEquals("5", info.text());
        assertEquals("1", info.ancestorAttributes().get(1).get("VRG"));
        assertTrue(new PkDescriber(() -> dict).describe(info).startsWith("R800-110 · Laufende Guthaben"));
    }

    @Test
    void hoverFindsCodesAndTheirTemplate() {
        String xml = "<Nationale_Meldung_pfq>\n  <PK.006.03_Durchrechnung VRG=\"1\">\n    <Datenpunkt>\n"
                + "      <Wert_C1010PK>X</Wert_C1010PK>\n      <Wert_C1120PK>R800-110</Wert_C1120PK>\n";
        PkCodeHover.TextSource source = PkCodeHover.TextSource.of(xml);
        String[] column = PkCodeHover.describeAt(source, xml.indexOf("C1010PK") + 2, dict);
        assertEquals("C1010PK · Asset ID Code SubBestand", column[0]);
        String[] row = PkCodeHover.describeAt(source, xml.indexOf("R800-110<") + 3, dict);
        assertEquals("R800-110 · Laufende Guthaben", row[0]);
        String[] template = PkCodeHover.describeAt(source, xml.indexOf("PK.006.03") + 1, dict);
        assertTrue(template[0].startsWith("PK.006.03 · Durchrechnung"), template[0]);
        assertNull(PkCodeHover.describeAt(source, xml.indexOf("Datenpunkt") + 2, dict));
        assertEquals("PK.006.03", PkCodeHover.enclosingTemplate(source, xml.length() - 1));
        assertNull(PkCodeHover.enclosingTemplate(PkCodeHover.TextSource.of("<a><b/>"), 5));
    }

    @Test
    void gridFormulasAreAddedOnceAndRemovedAgain() throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/pkmeldewesen/rules-pfq.xml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertFalse(PkGridSetup.hasAnnotations(xml));
        String grid = PkGridSetup.add(xml, true, true);
        assertTrue(PkGridSetup.hasAnnotations(grid));
        assertTrue(grid.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"), grid.substring(0, 60));
        assertTrue(grid.contains("<?fxt-formula") && grid.contains("<?fxt-filter"));
        assertEquals(grid, PkGridSetup.add(grid, true, true), "running it twice gives the same file");
        String formulasOnly = PkGridSetup.add(xml, true, false);
        assertFalse(formulasOnly.contains("<?fxt-filter"));
        String clean = PkGridSetup.remove(grid);
        assertFalse(PkGridSetup.hasAnnotations(clean));
        assertEquals(PkGridSetup.remove(xml), clean, "removing restores the report");
        assertTrue(clean.contains("<RowCode>R800-110</RowCode>"));
    }
}
