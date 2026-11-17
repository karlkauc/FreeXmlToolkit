package org.fxt.freexmltoolkit.service.fundsxml;

import static org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQualityTest.findings;
import static org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQualityTest.fixture;
import static org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQualityTest.shareClass;
import static org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQualityTest.withShareClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The structure, date, valuation and hint checks of the FundsXML data-quality catalog: each one is fed the
 * clean fixture with exactly the defect it looks for.
 */
@DisplayName("FundsXML data-quality checks: one defect per check")
class FundsXmlDataQualityChecksTest {

    /** A defect: the check that must report it, how to break the fixture, and the expected finding. */
    record Case(String key, UnaryOperator<String> breakIt, String expected) {
    }

    private static final String P1_EQUITY = "<Equity><Units>1000</Units></Equity>";
    private static final String P2_EQUITY = "<Equity><Units>2000</Units></Equity>";
    private static final String P1_VALUE = "<TotalValue><Amount ccy=\"EUR\">6000000.00</Amount></TotalValue>";
    private static final String TRANSACTION = """
            <Transaction>
                            <TransactionID>T1</TransactionID>""";

    /** The fixture with one more asset in AssetMasterData. */
    private static String withAsset(String xml, String asset) {
        return xml.replace("</AssetMasterData>", asset + "\n  </AssetMasterData>");
    }

    /** The fixture with share classes A (7 M) and B (3 M); {@code a} changes share class A. */
    private static String withClasses(UnaryOperator<String> a) throws Exception {
        return withShareClasses(
                a.apply(shareClass("AT0000A0K2G5", "7000000.00", "4200000.00", "60.00", "2800000.00", "40.00")),
                shareClass("AT0000A0K2H3", "3000000.00", "1800000.00", "60.00", "1200000.00", "40.00"));
    }

    private static UnaryOperator<String> classes(UnaryOperator<String> a) {
        return ignored -> {
            try {
                return withClasses(a);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private static String price(String currency) {
        return "<Price><NavDate>2025-10-01</NavDate><NavPrice>100</NavPrice><PriceCurrency>" + currency
                + "</PriceCurrency><PriceNature>OFFICIAL</PriceNature></Price>";
    }

    private static final String FX_FORWARD = """
            <Asset><UniqueID>FX1</UniqueID><Currency>EUR</Currency><Name>EUR/USD FORWARD</Name><AssetType>FX</AssetType>
              <AssetDetails><FXForward><CurrencyBuy>USD</CurrencyBuy><CurrencySell>EUR</CurrencySell>
                <AmountBuy>1100000</AmountBuy><AmountSell>1000000</AmountSell><AgreedFxRate>%s</AgreedFxRate>
                <MaturityDate>2026-01-15</MaturityDate></FXForward></AssetDetails></Asset>""";

    static List<Case> cases() {
        return List.of(
                // the first 17 checks
                new Case("control-data", x -> x.replace("<UniqueDocumentID>DQ-TEST-1</UniqueDocumentID>", ""),
                        "ControlData/UniqueDocumentID is missing"),
                new Case("content-date", x -> x.replace("<ContentDate>2025-10-01</ContentDate>", "<ContentDate>2999-01-01</ContentDate>"),
                        "ContentDate 2999-01-01 is in the future"),
                new Case("isin", x -> x.replace("<ISIN>DE0005140008</ISIN></Identifiers>\n                <Currency>",
                        "<ISIN>DE0005140009</ISIN></Identifiers>\n                <Currency>"),
                        "Position P2: ISIN DE0005140009 is invalid"),
                new Case("lei", x -> x.replace("529900T8BM49AURSDO55", "529900T8BM49AURSDO56"),
                        "FundsXML4/Funds/Fund/Identifiers/LEI: LEI 529900T8BM49AURSDO56 is invalid"),
                new Case("currency", x -> x.replace("<Currency>EUR</Currency>\n                " + P1_VALUE,
                        "<Currency>Eur</Currency>\n                " + P1_VALUE),
                        "Position P1: Currency Eur is no ISO 4217 code"),
                new Case("weights", x -> x.replace("<TotalPercentage>40.00</TotalPercentage>", "<TotalPercentage>30.00</TotalPercentage>"),
                        "Fund Test Fund, 2025-10-01: the position weights add up to 90 %"),
                new Case("value-vs-nav", x -> x.replace("<TotalValue><Amount ccy=\"EUR\">4000000.00</Amount></TotalValue>",
                        "<TotalValue><Amount ccy=\"EUR\">3000000.00</Amount></TotalValue>"),
                        "Fund Test Fund, 2025-10-01: positions add up to 9000000 EUR, the net asset value is 10000000 EUR (-10 %)"),
                new Case("duplicate-positions", x -> x.replace("<UniqueID>P2</UniqueID>\n                <Identifiers>",
                        "<UniqueID>P1</UniqueID>\n                <Identifiers>"),
                        "Fund Test Fund: position P1 appears 2 times"),
                new Case("asset-master", x -> x.replace("<UniqueID>P2</UniqueID>\n                <Identifiers>",
                        "<UniqueID>X8</UniqueID>\n                <Identifiers>"),
                        "Position X8 has no Asset in AssetMasterData"),
                new Case("unused-assets", x -> withAsset(x,
                        "<Asset><UniqueID>X9</UniqueID><Identifiers><ISIN>DE0007164600</ISIN></Identifiers><Currency>EUR</Currency><Name>SOLD BOND</Name><AssetType>BO</AssetType></Asset>"),
                        "Asset X9 (SOLD BOND) is not used by any position, transaction or earning"),
                new Case("negative-values", x -> x.replace("<Amount ccy=\"EUR\">4000000.00</Amount>", "<Amount ccy=\"EUR\">-4000000.00</Amount>"),
                        "Position P2 has a negative value: -4000000"),
                new Case("missing-identifier", x -> x.replace("<UniqueID>P1</UniqueID>\n                <Identifiers><ISIN>US0378331005</ISIN></Identifiers>",
                        "<UniqueID>P1</UniqueID>"),
                        "Position P1 has no identifier"),
                new Case("nav-dates", x -> x.replace("<Portfolio>\n            <NavDate>2025-10-01</NavDate>",
                        "<Portfolio>\n            <NavDate>2025-09-30</NavDate>"),
                        "Fund Test Fund: no net asset value for the portfolio date 2025-09-30"),
                new Case("share-classes-sum", ignored -> {
                    try {
                        return withShareClasses(
                                shareClass("AT0000A0K2G5", "7000000.00", "4200000.00", "60.00", "2800000.00", "40.00"),
                                shareClass("AT0000A0K2H3", "2000000.00", "1200000.00", "60.00", "800000.00", "40.00"));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }, "Fund Test Fund, 2025-10-01: share classes add up to 9000000 EUR, the fund has 10000000 EUR"),
                new Case("price-x-shares", classes(a -> a.replace("<TotalAssetValues>", "<Prices>" + price("EUR") + "</Prices><TotalAssetValues>")
                        .replace("<TotalNetAssetValue>", "<SharesOutstanding>60000</SharesOutstanding><TotalNetAssetValue>")),
                        "Share class AT0000A0K2G5, 2025-10-01: 100 × 60000 = 6000000, the net asset value is 7000000"),
                new Case("transaction-dates", x -> x.replace("<ValutaDate>2025-10-01</ValutaDate>", "<ValutaDate>2025-09-01</ValutaDate>"),
                        "Transaction T1: ValutaDate 2025-09-01 is before EntryDate 2025-09-29"),
                new Case("matured", x -> x.replace("<Name>DEUTSCHE BANK AG</Name><AssetType>EQ</AssetType></Asset>",
                        "<Name>DEUTSCHE BANK AG</Name><AssetType>EQ</AssetType><AssetDetails><Bond><MaturityDate>2025-01-01</MaturityDate></Bond></AssetDetails></Asset>"),
                        "Asset P2 (DEUTSCHE BANK AG) matured on 2025-01-01 but is still held"),
                // structure and references
                new Case("fund-ccy-value", x -> x.replace(P1_VALUE, P1_VALUE.replace("EUR", "USD")),
                        "Fund Test Fund: position P1 has no TotalValue in EUR"),
                new Case("fund-ccy-tna", x -> x.replace("<TotalNetAssetValue><Amount ccy=\"EUR\">",
                        "<TotalNetAssetValue><Amount ccy=\"USD\">"),
                        "Fund Test Fund, 2025-10-01: the net asset value is not given in EUR"),
                new Case("duplicate-assets", x -> withAsset(x,
                        "<Asset><UniqueID>P1</UniqueID><Currency>EUR</Currency><Name>APPLE INC.</Name><AssetType>EQ</AssetType></Asset>"),
                        "Asset P1 appears 2 times in AssetMasterData"),
                new Case("duplicate-isin-assets", x -> withAsset(x,
                        "<Asset><UniqueID>X9</UniqueID><Identifiers><ISIN>US0378331005</ISIN></Identifiers><Currency>EUR</Currency><Name>APPLE</Name><AssetType>EQ</AssetType></Asset>"),
                        "ISIN US0378331005 belongs to 2 assets: P1, X9"),
                new Case("position-asset-isin", x -> x.replace("<Asset><UniqueID>P2</UniqueID><Identifiers><ISIN>DE0005140008",
                        "<Asset><UniqueID>P2</UniqueID><Identifiers><ISIN>DE0007164600"),
                        "Position P2: ISIN DE0005140008, the asset has ISIN DE0007164600"),
                new Case("transaction-asset", x -> x.replace("<AssetUniqueID>P1</AssetUniqueID>", "<AssetUniqueID>X7</AssetUniqueID>"),
                        "Transaction T1: asset X7 is not in AssetMasterData"),
                new Case("duplicate-transactions", x -> x.replace("</Transactions>", TRANSACTION + "</Transaction>\n</Transactions>"),
                        "Fund Test Fund: transaction T1 appears 2 times"),
                new Case("security-isin", x -> x.replace("<Asset><UniqueID>P2</UniqueID><Identifiers><ISIN>DE0005140008</ISIN></Identifiers>",
                        "<Asset><UniqueID>P2</UniqueID>"),
                        "Asset P2 (DEUTSCHE BANK AG, EQ) has no ISIN"),
                new Case("derivative-underlying", x -> withAsset(x,
                        "<Asset><UniqueID>F1</UniqueID><Currency>EUR</Currency><Name>BUND FUTURE</Name><AssetType>FU</AssetType><AssetDetails><Future><MaturityDate>2026-03-08</MaturityDate></Future></AssetDetails></Asset>"),
                        "Asset F1 (BUND FUTURE, FU) has no underlying"),
                new Case("country-codes", x -> x.replace("<Country>DE</Country>", "<Country>de</Country>"),
                        "Asset P2: Country de is no ISO 3166 code"),
                new Case("bic", x -> x.replace("<TransactionKind>BUY</TransactionKind>",
                        "<TransactionKind>BUY</TransactionKind><Counterparty><Identifiers><SwiftBIC>DEUTDEF</SwiftBIC></Identifiers></Counterparty>"),
                        "Transaction T1: BIC DEUTDEF is invalid"),
                // dates
                new Case("generated-date", x -> x.replace("2025-10-02T08:00:00", "2025-09-30T08:00:00"),
                        "DocumentGenerated 2025-09-30T08:00:00 is before the ContentDate 2025-10-01"),
                new Case("navdate-after-content", x -> x.replace("<ContentDate>2025-10-01</ContentDate>", "<ContentDate>2025-09-30</ContentDate>"),
                        "Fund Test Fund: Portfolio of 2025-10-01 is after the ContentDate 2025-09-30"),
                new Case("navdate-before-inception", x -> x.replace("<FundDynamicData>",
                        "<FundStaticData><InceptionDate>2025-10-02</InceptionDate></FundStaticData><FundDynamicData>"),
                        "Fund Test Fund: TotalAssetValue of 2025-10-01 is before the InceptionDate 2025-10-02"),
                new Case("share-class-dates", classes(a -> a.replace("2025-10-01", "2025-09-30")),
                        "Share class AT0000A0K2G5: net asset value of 2025-09-30, the fund has none for that date"),
                new Case("bond-dates", x -> withAsset(x,
                        "<Asset><UniqueID>B1</UniqueID><Currency>EUR</Currency><Name>BAD BOND</Name><AssetType>BO</AssetType><AssetDetails><Bond><IssueDate>2030-01-01</IssueDate><MaturityDate>2025-01-01</MaturityDate></Bond></AssetDetails></Asset>"),
                        "Asset B1 (BAD BOND): MaturityDate 2025-01-01 is before IssueDate 2030-01-01"),
                new Case("earning-dates", x -> x.replace("</Transactions>", """
                        </Transactions>
                        <Earnings><Earning><EarningID>E1</EarningID><AssetUniqueID>P1</AssetUniqueID><EarningKind>DIVIDEND</EarningKind>
                          <EntryDate>2025-09-29</EntryDate><ValutaDate>2025-09-01</ValutaDate></Earning></Earnings>"""),
                        "Earning E1: ValutaDate 2025-09-01 is before EntryDate 2025-09-29"),
                new Case("transaction-after-content", x -> x.replace("<EntryDate>2025-09-29</EntryDate>", "<EntryDate>2025-10-05</EntryDate>"),
                        "Transaction T1: EntryDate 2025-10-05 is after the ContentDate 2025-10-01"),
                // valuation
                new Case("units-x-price", x -> x.replace(P1_EQUITY,
                        "<Equity><Units>1000</Units><Price><Amount ccy=\"EUR\">5000</Amount></Price><MarketValue><Amount ccy=\"EUR\">6000000.00</Amount></MarketValue></Equity>"),
                        "Position P1: 1000 × 5000 = 5000000 EUR, the market value is 6000000 EUR"),
                new Case("nominal-x-price", x -> x.replace(P2_EQUITY,
                        "<Bond><Nominal>4000000</Nominal><Price><Amount ccy=\"EUR\">90</Amount></Price><MarketValue><Amount ccy=\"EUR\">4000000.00</Amount></MarketValue></Bond>"),
                        "Position P2: 4000000 × 90 % = 3600000 EUR, the market value is 4000000 EUR"),
                new Case("weight-vs-value", x -> x.replace("<TotalPercentage>60.00</TotalPercentage>", "<TotalPercentage>60.50</TotalPercentage>")
                        .replace("<TotalPercentage>40.00</TotalPercentage>", "<TotalPercentage>39.50</TotalPercentage>"),
                        "Fund Test Fund, 2025-10-01: position P1 has 60.5 %, its value is 60 % of the net asset value"),
                new Case("fx-rates", x -> x.replace(P1_VALUE,
                        P1_VALUE + "<FXRates><FXRate fromCcy=\"EUR\" mulDiv=\"M\" toCcy=\"EUR\">1.1</FXRate></FXRates>"),
                        "Position P1: FX rate EUR→EUR is 1.1"),
                new Case("fx-consistency", x -> x.replace(P1_VALUE,
                        P1_VALUE + "<FXRates><FXRate fromCcy=\"USD\" mulDiv=\"M\" toCcy=\"EUR\">0.9</FXRate></FXRates>")
                        .replace("<TotalValue><Amount ccy=\"EUR\">4000000.00</Amount></TotalValue>",
                                "<TotalValue><Amount ccy=\"EUR\">4000000.00</Amount></TotalValue><FXRates><FXRate fromCcy=\"USD\" mulDiv=\"M\" toCcy=\"EUR\">0.91</FXRate></FXRates>"),
                        "Fund Test Fund, 2025-10-01: USD→EUR (M) is used with 2 rates: 0.9, 0.91"),
                new Case("value-direction", x -> x.replace(P1_VALUE,
                        "<TotalValue><Amount ccy=\"EUR\">6000000.00</Amount><Amount ccy=\"USD\">-7000000.00</Amount></TotalValue>"),
                        "Position P1 has positive and negative values: 6000000.00 EUR, -7000000.00 USD"),
                new Case("share-class-ratio", ignored -> {
                    try {
                        return withShareClasses(
                                shareClass("AT0000A0K2G5", "7000000.00", "4200000.00", "60.00", "2800000.00", "40.00")
                                        .replace("</TotalNetAssetValue>", "</TotalNetAssetValue><Ratio>70</Ratio>"),
                                shareClass("AT0000A0K2H3", "3000000.00", "1800000.00", "60.00", "1200000.00", "40.00")
                                        .replace("</TotalNetAssetValue>", "</TotalNetAssetValue><Ratio>20</Ratio>"));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }, "Fund Test Fund, 2025-10-01: the share class ratios add up to 90 %"),
                new Case("positive-nav", x -> x.replace("<Amount ccy=\"EUR\">10000000.00</Amount>", "<Amount ccy=\"EUR\">-10000000.00</Amount>"),
                        "Fund Test Fund, 2025-10-01: net asset value -10000000.00 EUR"),
                new Case("duplicate-prices", classes(a -> a.replace("<TotalAssetValues>",
                        "<Prices>" + price("EUR") + price("EUR") + "</Prices><TotalAssetValues>")),
                        "Share class AT0000A0K2G5: 2 prices for 2025-10-01 OFFICIAL EUR"),
                new Case("price-currency", classes(a -> a.replace("<TotalAssetValues>",
                        "<Prices>" + price("USD") + "</Prices><TotalAssetValues>")),
                        "Share class AT0000A0K2G5, 2025-10-01: no price in EUR (only USD)"),
                new Case("position-currency", x -> x.replace("<Currency>EUR</Currency>\n                " + P1_VALUE,
                        "<Currency>USD</Currency>\n                " + P1_VALUE),
                        "Position P1: currency USD, the asset has EUR"),
                new Case("fx-forward", x -> withAsset(x, FX_FORWARD.formatted("1.2")),
                        "Asset FX1 (EUR/USD FORWARD): agreed rate 1.2, the amounts give 1.1 (or 0.909091)"),
                new Case("bond-price-range", x -> x.replace(P2_EQUITY,
                        "<Bond><Nominal>1000000</Nominal><Price><Amount ccy=\"EUR\">300</Amount></Price><MarketValue><Amount ccy=\"EUR\">3000000</Amount></MarketValue></Bond>"),
                        "Position P2: bond price 300 EUR is not plausible"),
                // hints
                new Case("fund-identifiers", x -> x.replace("<Identifiers><LEI>529900T8BM49AURSDO55</LEI></Identifiers>", ""),
                        "Fund Test Fund has no LEI"),
                new Case("otc-counterparty-lei", x -> withAsset(x, FX_FORWARD.formatted("1.1")),
                        "Asset FX1 (EUR/USD FORWARD, FX) has no counterparty LEI"),
                new Case("account-counterparty", x -> withAsset(x,
                        "<Asset><UniqueID>AC1</UniqueID><Currency>EUR</Currency><Name>CURRENT ACCOUNT</Name><AssetType>AC</AssetType></Asset>"),
                        "Account AC1 (CURRENT ACCOUNT) has no counterparty LEI or BIC"),
                new Case("derivative-exposure", x -> x.replace(P2_EQUITY, "<Future><Contracts>10</Contracts></Future>"),
                        "Position P2 (Future) has no exposure"),
                new Case("zero-positions", x -> x.replace("</Positions>", """
                        <Position><UniqueID>P3</UniqueID><Currency>EUR</Currency>
                          <TotalValue><Amount ccy="EUR">0.00</Amount></TotalValue><TotalPercentage>0</TotalPercentage></Position>
                        </Positions>"""),
                        "Position P3 has a value of 0"),
                new Case("stale-navdate", x -> x.replace("<ContentDate>2025-10-01</ContentDate>", "<ContentDate>2025-12-01</ContentDate>"),
                        "Fund Test Fund: the latest portfolio is of 2025-10-01, 61 days before the ContentDate"));
    }

    @TestFactory
    @DisplayName("each check reports its defect, at a line of the document")
    Stream<DynamicTest> eachCheckFindsItsDefect() {
        return cases().stream().map(c -> DynamicTest.dynamicTest(c.key(), () -> {
            String xml = c.breakIt().apply(fixture());
            Map<String, FundsXmlDataQuality.Result> byKey = FundsXmlDataQuality.run(xml).stream()
                    .collect(Collectors.toMap(r -> r.check().key, r -> r));
            byKey.values().forEach(r -> assertEquals(null, r.error(), r.check().key));
            FundsXmlDataQuality.Result result = byKey.get(c.key());
            assertNotNull(result, "no check " + c.key());
            assertTrue(result.findings().contains(c.expected()), c.key() + ": " + result.findings());
            int line = result.lineOf(result.findings().indexOf(c.expected()));
            assertTrue(line > 0 && line <= xml.lines().count(), c.key() + " line " + line);
        }));
    }

    @Test
    @DisplayName("every check of the catalog has a defect case here")
    void everyCheckIsCovered() {
        Set<String> covered = cases().stream().map(Case::key).collect(Collectors.toSet());
        List<String> uncovered = FundsXmlDataQuality.checks().stream().map(c -> c.key)
                .filter(k -> !covered.contains(k)).toList();
        assertEquals(List.of(), uncovered);
    }

    @TestFactory
    @DisplayName("each check's XPath selects nothing in the clean fixture and something for its defect")
    Stream<DynamicTest> eachXPathSelectsTheDefect() throws Exception {
        var processor = new net.sf.saxon.s9api.Processor(false);
        var builder = processor.newDocumentBuilder();
        net.sf.saxon.s9api.XdmNode clean = builder.build(new javax.xml.transform.stream.StreamSource(
                new java.io.StringReader(fixture())));
        Map<String, String> xpaths = FundsXmlDataQuality.checks().stream().collect(Collectors.toMap(c -> c.key, c -> c.xpath));
        return cases().stream().map(c -> DynamicTest.dynamicTest(c.key(), () -> {
            String xpath = xpaths.get(c.key());
            assertNotNull(xpath, c.key() + " has no xpath");
            var selector = processor.newXPathCompiler().compile(xpath).load();
            selector.setContextItem(clean);
            assertEquals(0, selector.evaluate().size(), c.key() + " selects nodes in the clean fixture");
            selector.setContextItem(builder.build(new javax.xml.transform.stream.StreamSource(
                    new java.io.StringReader(c.breakIt().apply(fixture())))));
            assertTrue(selector.evaluate().size() > 0, c.key() + " selects nothing for its defect");
        }));
    }

    @Test
    @DisplayName("look-alikes of the defects are no findings")
    void consistentVariantsPass() throws Exception {
        String consistent = fixture()
                .replace(P1_EQUITY, "<Equity><Units>1000</Units><Price><Amount ccy=\"EUR\">6000</Amount></Price>"
                        + "<MarketValue><Amount ccy=\"EUR\">6000000.00</Amount></MarketValue></Equity>")
                .replace(P2_EQUITY, "<Bond><Nominal>4000000</Nominal><Price><Amount ccy=\"EUR\">100.2</Amount></Price>"
                        + "<MarketValue><Amount ccy=\"EUR\">4000000.00</Amount></MarketValue></Bond>")
                .replace(P1_VALUE, P1_VALUE + "<FXRates><FXRate fromCcy=\"EUR\" mulDiv=\"M\" toCcy=\"EUR\">1</FXRate></FXRates>")
                // a cancelled copy of a transaction is no duplicate
                .replace("</Transactions>", TRANSACTION + "<CancellationFlag>true</CancellationFlag></Transaction>\n</Transactions>");
        assertEquals(Map.of(), findings(consistent));

        String fxForward = withAsset(fixture(), FX_FORWARD.formatted("1.1"))
                .replace("</Transactions>", "<Transaction><TransactionID>T2</TransactionID><AssetUniqueID>FX1</AssetUniqueID>"
                        + "<TransactionKind>BUY</TransactionKind></Transaction>\n</Transactions>");
        assertEquals(Set.of("otc-counterparty-lei"), findings(fxForward).keySet(), findings(fxForward).toString());

        String ratios = withShareClasses(
                shareClass("AT0000A0K2G5", "7000000.00", "4200000.00", "60.00", "2800000.00", "40.00")
                        .replace("</TotalNetAssetValue>", "</TotalNetAssetValue><Ratio>70</Ratio>")
                        .replace("<TotalAssetValues>", "<Prices>" + price("EUR") + price("USD") + "</Prices><TotalAssetValues>"),
                shareClass("AT0000A0K2H3", "3000000.00", "1800000.00", "60.00", "1200000.00", "40.00")
                        .replace("</TotalNetAssetValue>", "</TotalNetAssetValue><Ratio>30</Ratio>"));
        assertEquals(Map.of(), findings(ratios));
    }

    @Test
    @DisplayName("severities: hints do not fail a document; the report shows the severity")
    void severities() throws Exception {
        Map<String, String> severity = FundsXmlDataQuality.checks().stream()
                .collect(Collectors.toMap(c -> c.key, FundsXmlDataQuality.Check::severity));
        assertEquals("warning", severity.get("isin"), "the first 17 checks stay warnings");
        assertEquals("error", severity.get("duplicate-assets"));
        assertEquals("info", severity.get("fund-identifiers"));
        assertTrue(severity.values().stream().allMatch(s -> Set.of("error", "warning", "info").contains(s)));

        FundsXmlDataQuality.Check unnamed = new FundsXmlDataQuality.Check();
        assertEquals("warning", unnamed.severity());
        unnamed.severity = "fatal";
        assertEquals("warning", unnamed.severity(), "an unknown severity falls back to warning");

        String hintOnly = fixture().replace("<Identifiers><LEI>529900T8BM49AURSDO55</LEI></Identifiers>", "");
        List<FundsXmlDataQuality.Result> results = FundsXmlDataQuality.run(hintOnly);
        assertTrue(results.stream().noneMatch(FundsXmlDataQuality.Result::failed), "a hint is no failure");
        var hint = results.stream().filter(r -> r.check().key.equals("fund-identifiers")).findFirst().orElseThrow();
        assertFalse(hint.passed());
        String html = FundsXmlDataQuality.htmlReport(results, "fund.xml");
        assertTrue(html.contains("54 checks · all passed · 1 with hints") && html.contains("1 hint<"), html);
        assertTrue(html.contains("<th>Severity</th>") && html.contains("<td class=\"sev\">info</td>"), html);
    }
}
