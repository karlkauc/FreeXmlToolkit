package org.fxt.freexmltoolkit.plugins.esmareporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.fxt.freexmltoolkit.plugins.regreport.RecordNode;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.junit.jupiter.api.Test;

/** ESMA's SFTR validation rules on the bundled (fictitious) error report, with planted violations. */
class SftrRulesTest {

    private static String example() throws Exception {
        try (InputStream in = SftrRulesTest.class.getResourceAsStream("/plugins/esmareporting/examples/sftr-eror-example.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<RegRuleSet.Finding> run(String xml) throws Exception {
        RegRuleSet rules = EsmaReportingPlugin.rulesFor(RegValidator.rootOf(xml), xml);
        assertNotNull(rules, "recognised as SFTR report");
        return rules.run(xml, "sftr.xml");
    }

    private static RegRuleSet.Finding find(List<RegRuleSet.Finding> findings, String id) {
        return findings.stream().filter(f -> f.ruleId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError(id + " not reported: " + findings));
    }

    @Test
    void theExamplePassesAndIsRecognised() throws Exception {
        String xml = example();
        assertEquals(List.of(), run(xml));
        assertEquals("SFTR · 1 report", EsmaReportingPlugin.label(xml));
        assertTrue(EsmaReportingPlugin.describe(xml).startsWith("SFTR securities financing transaction report"));
    }

    @Test
    void matrixUtiAndIdentifiers() throws Exception {
        String xml = example()
                .replaceFirst("\\s*<RptgDtTm>[^<]*</RptgDtTm>", "")
                .replaceFirst("<UnqTradIdr>[^<]*</UnqTradIdr>", "<UnqTradIdr>not a uti</UnqTradIdr>")
                .replaceFirst("<LEI>(\\w{18})\\d\\d</LEI>", "<LEI>$100</LEI>");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "SFTR-1.1").message().contains("mandatory for action type EROR"));
        assertTrue(find(findings, "SFTR-2.1").message().contains("is no UTI"));
        assertTrue(find(findings, "SFTR-LEI").message().contains("check digits"));
    }

    @Test
    void sftTypeChoosesTheTableColumn() {
        RecordNode action = RecordNode.of("New", 1);
        RecordNode loan = action.add("LnData", 2, "");
        loan.add("SctiesLndg", 3, "");
        assertEquals("SL", SftrRules.sftType(action));
        assertEquals(null, SftrRules.sftType(RecordNode.of("Err", 1)));
    }
}
