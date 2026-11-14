package org.fxt.freexmltoolkit.plugins.esmareporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.plugins.regreport.ActionMatrix;
import org.fxt.freexmltoolkit.plugins.regreport.ConditionalRules;
import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.junit.jupiter.api.Test;

/**
 * ESMA's conditional (C) fields of EMIR and SFTR on small new-trade reports (NEWT) with planted
 * violations: the condition decides, choice elements are read as ESMA's codes, repeating blocks
 * (schedule periods, collateral components) are checked one by one.
 */
class ConditionalFieldsTest {

    private static List<RegRuleSet.Finding> run(String xml) throws Exception {
        return EsmaReportingPlugin.rulesFor(RegValidator.rootOf(xml), xml).run(xml, "report.xml");
    }

    private static List<RegRuleSet.Finding> only(List<RegRuleSet.Finding> findings, String id) {
        return findings.stream().filter(f -> f.ruleId().equals(id)).toList();
    }

    private static int lineOf(String xml, String marker) {
        return (int) xml.substring(0, xml.indexOf(marker)).chars().filter(c -> c == '\n').count() + 1;
    }

    /** A new EMIR trade: an option of a non-financial counterparty 1 with an LEI counterparty 2. */
    private static String emir(String reportingParty, String otherParty, String contract, String txData) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:auth.030.001.03">
                <DerivsTradRpt>
                <RptHdr><NbRcrds>1</NbRcrds></RptHdr>
                <TradData><Rpt><New>
                <CtrPtySpcfcData><CtrPty>
                <RptgCtrPty>%s</RptgCtrPty>
                <OthrCtrPty>%s</OthrCtrPty>
                </CtrPty></CtrPtySpcfcData>
                <CmonTradData>
                <CtrctData>%s</CtrctData>
                <TxData>%s</TxData>
                </CmonTradData>
                </New></Rpt></TradData>
                </DerivsTradRpt>
                </Document>
                """.formatted(reportingParty, otherParty, contract, txData);
    }

    @Test
    void emirConditionsFollowTheReportedValues() throws Exception {
        String xml = emir(
                "<Ntr><NFI><Sctr><Id>C</Id></Sctr></NFI></Ntr>",            // 1.5 = N, 1.7 missing
                "<IdTp><Lgl><Id><LEI>5299009FXTBANK000080</LEI></Id></Lgl></IdTp>", // 1.8 = TRUE, 1.11 missing
                "<CtrctTp>OPTN</CtrctTp><AsstClss>EQUI</AsstClss>",       // 2.13, 2.132 missing
                """
                <TradClr><ClrSts><NonClrd><Rsn>NORE</Rsn></NonClrd></ClrSts></TradClr>
                <TxPric>
                <SchdlPrd><UadjstdFctvDt>2026-01-01</UadjstdFctvDt><Pric><Pctg>1.5</Pctg></Pric></SchdlPrd>
                <SchdlPrd><Pric><Pctg>1.6</Pctg></Pric></SchdlPrd>
                </TxPric>
                """);
        List<RegRuleSet.Finding> findings = run(xml);

        RegRuleSet.Finding threshold = only(findings, "EMIR-1.7").getFirst();
        assertTrue(threshold.message().contains("must be reported when 1.5 Nature of the counterparty 1 is N"), threshold.message());
        assertEquals(1, only(findings, "EMIR-1.11").size(), findings.toString());
        assertEquals(1, only(findings, "EMIR-2.13").size(), findings.toString());
        assertEquals(1, only(findings, "EMIR-2.132").size(), findings.toString());
        // the second schedule period has a price but no effective date - only that one is reported
        List<RegRuleSet.Finding> period = only(findings, "EMIR-2.50");
        assertEquals(1, period.size(), findings.toString());
        assertEquals(lineOf(xml, "<SchdlPrd><Pric>"), period.getFirst().line());
        // not cleared: no CCP expected, and none reported
        assertEquals(List.of(), only(findings, "EMIR-2.33"));

        // the other way round: counterparty 1 financial (no clearing threshold expected), a cleared trade without CCP
        String fixed = emir("<Ntr><FI><Sctr><Sctr>INVF</Sctr></Sctr></FI></Ntr>",
                "<IdTp><Lgl><Id><LEI>5299009FXTBANK000080</LEI></Id></Lgl></IdTp><Ntr><Othr/></Ntr>",
                "<CtrctTp>SWAP</CtrctTp><AsstClss>EQUI</AsstClss><UndrlygInstrm><ISIN>AT0000A0E9W5</ISIN></UndrlygInstrm>",
                "<TradClr><ClrSts><Clrd><Dtls><ClrDtTm>2026-01-02T10:00:00Z</ClrDtTm></Dtls></Clrd></ClrSts></TradClr>");
        List<RegRuleSet.Finding> second = run(fixed);
        Set<String> ids = second.stream().map(RegRuleSet.Finding::ruleId).collect(Collectors.toSet());
        for (String id : List.of("EMIR-1.7", "EMIR-1.11", "EMIR-2.13", "EMIR-2.132", "EMIR-2.14")) {
            assertTrue(!ids.contains(id), id + " reported: " + second);
        }
        assertTrue(only(second, "EMIR-2.33").getFirst().message().contains("when 2.31 Cleared is Y"), second.toString());
    }

    @Test
    void sftrCollateralComponentsAreCheckedOneByOne() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:auth.052.001.02">
                <SctiesFincgRptgTxRpt><TradData><Rpt><New>
                <LnData><RpTrad><ClrSts><Clrd><ClrDtTm>2026-01-02T10:00:00Z</ClrDtTm></Clrd></ClrSts></RpTrad></LnData>
                <CollData><RpTrad>
                <AsstTp><Scty><Qlty>INVG</Qlty></Scty></AsstTp>
                <AsstTp><Csh><Amt Ccy="EUR">1000</Amt></Csh></AsstTp>
                <AsstTp><Csh><HrcutOrMrgn>2</HrcutOrMrgn></Csh></AsstTp>
                </RpTrad></CollData>
                </New></Rpt></TradData></SctiesFincgRptgTxRpt>
                </Document>
                """;
        List<RegRuleSet.Finding> findings = run(xml);
        // cleared (choice Clrd = true) without CCP
        assertEquals(1, only(findings, "SFTR-2.7").size(), findings.toString());
        // the security component lacks its identifier; the cash components are not asked for one
        List<RegRuleSet.Finding> security = only(findings, "SFTR-2.78");
        assertEquals(1, security.size(), findings.toString());
        assertEquals(lineOf(xml, "<AsstTp><Scty>"), security.getFirst().line());
        // the second cash component lacks its amount
        List<RegRuleSet.Finding> cash = only(findings, "SFTR-2.76");
        assertEquals(1, cash.size(), findings.toString());
        assertEquals(lineOf(xml, "<AsstTp><Csh><Hrcut"), cash.getFirst().line());
    }

    @Test
    void everyRuleNamesFieldsOfTheMatrix() {
        for (String kind : List.of("emir", "sftr")) {
            ActionMatrix matrix = ActionMatrix.load("/plugins/esmareporting/" + kind + "/" + kind + "-matrix.json");
            ConditionalRules rules = ConditionalRules.load("/plugins/esmareporting/" + kind + "/" + kind + "-conditions.json");
            for (String field : rules.fields()) {
                assertTrue(matrix.entries().containsKey(field), kind + ": unknown field " + field);
            }
        }
    }
}
