package org.fxt.freexmltoolkit.service.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.service.finance.isin.IsinRecord;
import org.fxt.freexmltoolkit.service.xmledit.EditPlan;
import org.junit.jupiter.api.Test;

/** Collective fixes: one ISIN's CFI code and issuer LEI corrected in every place of the document. */
class IdentifierFixesTest {

    static final String ISIN = IdentifierProgressTest.isins(1).getFirst();
    static final String OTHER = IdentifierProgressTest.isins(2).getLast();
    static final String FIRDS_LEI = IdentifierProgressTest.leis(2).getFirst();
    static final String REPORTED_LEI = IdentifierProgressTest.leis(2).getLast();

    static IsinRecord firds(String isin) {
        return new IsinRecord(isin, IsinRecord.Source.FIRDS, "Example Bond", "DBFTFB", FIRDS_LEI, "EUR", 1, 1, null,
                null, null, null, false, Instant.now(), "1");
    }

    /** A report with {@code n} positions of {@link #ISIN} (wrong CFI, wrong issuer) and one of another ISIN. */
    static String report(int n) {
        StringBuilder xml = new StringBuilder("<Report>\n");
        for (int i = 0; i < n; i++) {
            xml.append("  <Position>\n    <ISIN>").append(ISIN).append("</ISIN>\n    <CFI>ESVUFR</CFI>\n")
                    .append("    <Issuer><LEI>").append(REPORTED_LEI).append("</LEI></Issuer>\n  </Position>\n");
        }
        xml.append("  <Position>\n    <ISIN>").append(OTHER).append("</ISIN>\n    <CFI>ESVUFR</CFI>\n  </Position>\n");
        return xml.append("</Report>\n").toString();
    }

    @Test
    void everyPlaceOfTheIsinIsFixedAndNothingElse() {
        String xml = report(100);
        IdentifierFixes.Plan plan = IdentifierFixes.plan(xml, List.of(ISIN), IdentifierFixesTest::firds);

        assertEquals(200, plan.edits().size(), "100 CFI codes and 100 issuer LEIs");
        assertEquals(1, plan.isins());
        assertEquals(2, plan.fixes().size());
        IdentifierFixes.Fix cfi = plan.fixes().stream().filter(f -> f.field() == IdentifierFixes.Field.CFI).findFirst()
                .orElseThrow();
        assertEquals("ESVUFR", cfi.from());
        assertEquals("DBFTFB", cfi.to());
        assertEquals(100, cfi.occurrences());

        String fixed = new EditPlan(plan.edits()).applyTo(xml);
        assertEquals(100, count(fixed, "<CFI>DBFTFB</CFI>"));
        assertEquals(100, count(fixed, "<LEI>" + FIRDS_LEI + "</LEI>"));
        assertEquals(1, count(fixed, "<CFI>ESVUFR</CFI>"), "the other ISIN keeps its value");
        assertEquals(xml.length(), fixed.length(), "only the values changed");

        // fixed: nothing left to do
        assertTrue(IdentifierFixes.plan(fixed, List.of(ISIN), IdentifierFixesTest::firds).isEmpty());
    }

    @Test
    void onlyTheChosenFieldAndOnlyWhatFirdsKnows() {
        String xml = report(3);
        IdentifierFixes.Plan cfiOnly = IdentifierFixes.plan(xml, ISIN::equals, Set.of(IdentifierFixes.Field.CFI),
                IdentifierFixesTest::firds);
        assertEquals(3, cfiOnly.edits().size());
        assertTrue(cfiOnly.edits().stream().allMatch(e -> e.replacement().equals("DBFTFB")));

        IdentifierFixes.Plan unknown = IdentifierFixes.plan(xml, List.of(ISIN), isin -> null);
        assertTrue(unknown.isEmpty(), "no FIRDS answer: no fix");

        IdentifierFixes.Plan wildcard = IdentifierFixes.plan(xml.replace("ESVUFR", "DBXXXX"), ISIN::equals,
                Set.of(IdentifierFixes.Field.CFI), IdentifierFixesTest::firds);
        assertTrue(wildcard.isEmpty(), "an X matches anything: not a difference");
        assertFalse(IdentifierFixes.plan(xml, null, Set.of(IdentifierFixes.Field.CFI), IdentifierFixesTest::firds)
                .isEmpty(), "null = every ISIN");
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            n++;
        }
        return n;
    }
}
