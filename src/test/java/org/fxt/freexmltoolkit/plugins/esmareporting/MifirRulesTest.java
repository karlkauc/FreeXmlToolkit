package org.fxt.freexmltoolkit.plugins.esmareporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.junit.jupiter.api.Test;

/** ESMA's MiFIR content rules on the bundled (fictitious) transaction, with planted violations. */
class MifirRulesTest {

    private static String example() throws Exception {
        try (InputStream in = MifirRulesTest.class.getResourceAsStream("/plugins/esmareporting/examples/mifir-newt-example.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<RegRuleSet.Finding> run(String xml) throws Exception {
        RegRuleSet rules = EsmaReportingPlugin.rulesFor(RegValidator.rootOf(xml), xml);
        assertNotNull(rules, "recognised as MiFIR report");
        return rules.run(xml, "mifir.xml");
    }

    private static RegRuleSet.Finding find(List<RegRuleSet.Finding> findings, String id) {
        return findings.stream().filter(f -> f.ruleId().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError(id + " not reported: " + findings));
    }

    @Test
    void theExamplePassesAndIsRecognised() throws Exception {
        String xml = example();
        assertEquals(List.of(), run(xml));
        assertEquals("MiFIR · 1 transaction", EsmaReportingPlugin.label(xml));
        assertTrue(EsmaReportingPlugin.describe(xml).endsWith("1 × NEWT"), EsmaReportingPlugin.describe(xml));
    }

    @Test
    void venueCapacityAndCurrency() throws Exception {
        String xml = example()
                .replace("<TradVn>XWBO</TradVn>", "<TradVn>XOFF</TradVn>")
                .replace("<TradgCpcty>AOTC</TradgCpcty>", "<TradgCpcty>DEAL</TradgCpcty>")
                .replace("Ccy=\"EUR\"", "Ccy=\"XAU\"");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "MIFIR-CON-372").message().contains("XOFF"));
        assertTrue(find(findings, "MIFIR-CON-290").message().contains("executing entity"));
        assertTrue(find(findings, "MIFIR-CON-570").message().contains("Investment decision"));
        assertTrue(find(findings, "MIFIR-CON-310").message().contains("XAU"));
    }

    @Test
    void cfiDependentFieldsAndDates() throws Exception {
        String xml = example().replaceFirst("<FinInstrm>\\s*<Id>[A-Z0-9]{12}</Id>\\s*</FinInstrm>", """
                <FinInstrm><Othr><FinInstrmGnlAttrbts><FullNm>Call option</FullNm><ClssfctnTp>OCASPS</ClssfctnTp>\
                <NtnlCcy>EUR</NtnlCcy></FinInstrmGnlAttrbts><DebtInstrmAttrbts><MtrtyDt>2025-01-01</MtrtyDt></DebtInstrmAttrbts>\
                <DerivInstrmAttrbts><OptnTp>PUTO</OptnTp></DerivInstrmAttrbts></Othr></FinInstrm>""");
        List<RegRuleSet.Finding> findings = run(xml);
        assertTrue(find(findings, "MIFIR-CON-510").message().contains("Strike price is mandatory for CFI OCASPS"));
        assertTrue(find(findings, "MIFIR-CON-542").message().contains("Maturity date must not be reported"));
        assertTrue(find(findings, "MIFIR-CON-541").message().contains("before the trading date"));
        assertTrue(find(findings, "MIFIR-CON-502").message().contains("must be CALL"));
        assertFalse(findings.stream().anyMatch(f -> f.ruleId().equals("MIFIR-CON-441")), "notional currency reported");
    }

    @Test
    void personsAndCodes() throws Exception {
        String xml = example().replaceFirst("<Buyr>\\s*<AcctOwnr>\\s*<Id>\\s*<LEI>[A-Z0-9]{20}</LEI>", """
                <Buyr><AcctOwnr><Id><Prsn><FrstNm>Anna</FrstNm><Nm>Muster</Nm><BirthDt>1980-02-03</BirthDt><Othr>\
                <Id>AT19800203ANNA#MUST</Id><SchmeNm><Prtry>CONCAT</Prtry></SchmeNm></Othr></Prsn>""").replace(
                "</Id>\n\t\t\t\t\t</AcctOwnr>\n\t\t\t\t</Buyr>", "</Id></AcctOwnr></Buyr>");
        RegRuleSet.Finding concat = find(run(xml), "MIFIR-CON-074");
        assertTrue(concat.message().contains("20 characters"), concat.message());
        assertTrue(MifirRules.CONCAT.matcher("AT19800203ANNA#MUSTE").matches());
        assertEquals("CALL", MifirRules.optionTypeOf("OCASPS"));
        assertTrue(MifirRules.cfiMatches("DBFTFB", "D*****"));
        assertFalse(MifirRules.cfiMatches("DBFTFB", "O*****"));
    }
}
