package org.fxt.freexmltoolkit.service.fundsxml;

import static org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQualityTest.findings;
import static org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQualityTest.fixture;
import static org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQualityTest.shareClass;
import static org.fxt.freexmltoolkit.service.fundsxml.FundsXmlDataQualityTest.withShareClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Setting the TotalPercentage of the portfolio positions from their value and the net asset value. */
@DisplayName("FundsXML position weights (TotalPercentage)")
class FundsXmlPercentagesTest {

    private static final String P1_PERCENT = "\n                <TotalPercentage>60.00</TotalPercentage>";
    private static final String P2_PERCENT = "\n                <TotalPercentage>40.00</TotalPercentage>";

    private static String apply(String xml) throws IOException {
        return new EditPlan(FundsXmlPercentages.plan(xml).edits()).applyTo(xml);
    }

    @Test
    @DisplayName("right values are left alone")
    void rightValuesStay() throws Exception {
        FundsXmlPercentages.Plan plan = FundsXmlPercentages.plan(fixture());
        assertEquals(List.of(), plan.edits());
        assertEquals(2, plan.unchanged());
        assertEquals(List.of(), plan.skipped());
    }

    @Test
    @DisplayName("missing values are inserted after TotalValue, indented like it; running again changes nothing")
    void missingValuesAreInserted() throws Exception {
        String without = fixture().replace(P1_PERCENT, "").replace(P2_PERCENT, "");
        FundsXmlPercentages.Plan plan = FundsXmlPercentages.plan(without);
        assertEquals(2, plan.inserted());
        assertEquals(0, plan.corrected());
        assertEquals(List.of("P1 + 60.00", "P2 + 40.00"),
                plan.changes().stream().map(c -> c.position() + (c.inserted() ? " + " : " ") + c.to()).toList());
        assertEquals("Fund Test Fund", plan.changes().getFirst().owner());
        assertEquals("2025-10-01", plan.changes().getFirst().navDate());
        assertTrue(plan.changes().getFirst().line() > 0);

        String result = apply(without);
        assertEquals(fixture(), result, "the inserted elements sit exactly where the fixture has them");
        assertEquals(List.of(), FundsXmlPercentages.plan(result).edits());
        assertEquals(Map.of(), findings(result));
    }

    @Test
    @DisplayName("Windows line breaks are kept")
    void windowsLineBreaks() throws Exception {
        String without = fixture().replace(P1_PERCENT, "").replace(P2_PERCENT, "").replace("\n", "\r\n");
        assertEquals(fixture().replace("\n", "\r\n"), apply(without));
    }

    @Test
    @DisplayName("a wrong, an empty or a non-numeric value is replaced; one that rounds to the same value stays")
    void wrongValuesAreCorrected() throws Exception {
        String xml = fixture().replace("<TotalPercentage>40.00</TotalPercentage>", "<TotalPercentage>39.00</TotalPercentage>")
                .replace("<TotalPercentage>60.00</TotalPercentage>", "<TotalPercentage>60.004</TotalPercentage>");
        FundsXmlPercentages.Plan plan = FundsXmlPercentages.plan(xml);
        assertEquals(1, plan.unchanged());
        assertEquals(1, plan.corrected());
        assertEquals("39.00", plan.changes().getFirst().from());
        assertEquals("40.00", plan.changes().getFirst().to());
        assertTrue(apply(xml).contains("<TotalPercentage>60.004</TotalPercentage>"));
        assertTrue(apply(xml).contains("<TotalPercentage>40.00</TotalPercentage>"));

        String odd = fixture().replace("<TotalPercentage>40.00</TotalPercentage>", "<TotalPercentage/>")
                .replace("<TotalPercentage>60.00</TotalPercentage>", "<TotalPercentage>n/a</TotalPercentage>");
        assertEquals(fixture(), apply(odd));
        assertEquals(2, FundsXmlPercentages.plan(odd).corrected());
    }

    @Test
    @DisplayName("percentages are rounded half up to two decimals")
    void rounding() throws Exception {
        String thirds = fixture().replace("<Amount ccy=\"EUR\">10000000.00</Amount>", "<Amount ccy=\"EUR\">9000000.00</Amount>")
                .replace("<Amount ccy=\"EUR\">6000000.00</Amount>", "<Amount ccy=\"EUR\">3000000.00</Amount>")
                .replace("<Amount ccy=\"EUR\">4000000.00</Amount>", "<Amount ccy=\"EUR\">6000000.00</Amount>");
        assertEquals(List.of("33.33", "66.67"), FundsXmlPercentages.plan(thirds).changes().stream()
                .map(FundsXmlPercentages.Change::to).toList());

        String small = fixture().replace("<Amount ccy=\"EUR\">10000000.00</Amount>", "<Amount ccy=\"EUR\">8000000.00</Amount>")
                .replace("<Amount ccy=\"EUR\">6000000.00</Amount>", "<Amount ccy=\"EUR\">10000.00</Amount>");
        assertEquals("0.13", FundsXmlPercentages.plan(small).changes().getFirst().to());
    }

    @Test
    @DisplayName("a share-class portfolio is weighted against the share class's net asset value")
    void shareClassPortfolio() throws Exception {
        String xml = withShareClasses(
                shareClass("AT0000A0K2G5", "7000000.00", "4200000.00", "0", "2800000.00", "40.00"),
                shareClass("AT0000A0K2H3", "3000000.00", "1800000.00", "60.00", "1200000.00", "40.00"));
        FundsXmlPercentages.Plan plan = FundsXmlPercentages.plan(xml);
        assertEquals(1, plan.changes().size(), plan.toString());
        FundsXmlPercentages.Change change = plan.changes().getFirst();
        assertEquals("Share class AT0000A0K2G5", change.owner());
        assertEquals("60.00", change.to());
        // the share-class positions sit on one line: the value is inserted without a line break
        String oneLine = xml.replace("<TotalPercentage>0</TotalPercentage>", "");
        assertTrue(apply(oneLine).contains("<Amount ccy=\"EUR\">4200000.00</Amount></TotalValue><TotalPercentage>60.00</TotalPercentage><Equity>"),
                apply(oneLine));
    }

    @Test
    @DisplayName("TotalPercentage follows OtherTotalValues, keeping the schema's order")
    void afterOtherTotalValues() throws Exception {
        String xml = fixture().replace(P1_PERCENT, "").replace("<TotalValue><Amount ccy=\"EUR\">6000000.00</Amount></TotalValue>",
                "<TotalValue><Amount ccy=\"EUR\">6000000.00</Amount></TotalValue>\n"
                        + "                <OtherTotalValues><OtherTotalValue><Amount ccy=\"EUR\">1</Amount></OtherTotalValue></OtherTotalValues>");
        String result = apply(xml);
        assertTrue(result.contains("</OtherTotalValues>\n                <TotalPercentage>60.00</TotalPercentage>"), result);
    }

    @Test
    @DisplayName("positions without a value in the owner's currency or without a net asset value are skipped")
    void skipped() throws Exception {
        String noNav = fixture().replace(P1_PERCENT, "").replace("<Portfolio>\n            <NavDate>2025-10-01</NavDate>",
                "<Portfolio>\n            <NavDate>2025-09-30</NavDate>");
        FundsXmlPercentages.Plan plan = FundsXmlPercentages.plan(noNav);
        assertEquals(List.of(), plan.edits());
        assertEquals(List.of("no net asset value in EUR for 2025-09-30", "no net asset value in EUR for 2025-09-30"),
                plan.skipped().stream().map(FundsXmlPercentages.Skip::reason).toList());

        String noValue = fixture().replace("<TotalValue><Amount ccy=\"EUR\">6000000.00</Amount></TotalValue>",
                "<TotalValue><Amount ccy=\"USD\">6600000.00</Amount></TotalValue>");
        FundsXmlPercentages.Plan valuePlan = FundsXmlPercentages.plan(noValue);
        assertEquals(1, valuePlan.skipped().size());
        assertEquals("P1", valuePlan.skipped().getFirst().position());
        assertEquals("no TotalValue in EUR", valuePlan.skipped().getFirst().reason());
        assertEquals(1, valuePlan.unchanged());
    }

    @Test
    @DisplayName("a document that is not well-formed is refused")
    void notWellFormed() {
        assertThrows(IOException.class, () -> FundsXmlPercentages.plan("<FundsXML4><Funds>"));
    }
}
