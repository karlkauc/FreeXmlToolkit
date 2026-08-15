package org.fxt.freexmltoolkit.service.sqf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.SchematronServiceImpl;
import org.fxt.freexmltoolkit.service.sqf.SqfCorrelator.SqfFinding;
import org.fxt.freexmltoolkit.service.sqf.SqfExecutionEngine.SqfEditPlan;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfUserEntry;
import org.junit.jupiter.api.Test;

/**
 * Phase-5 semantics: {@code sqf:user-entry} values flow into fix content,
 * {@code sqf:call-fix} inlines generic fixes with bound {@code sqf:with-param}s,
 * call cycles are rejected, and param-declaring fixes are not offered directly.
 */
class SqfCallFixAndUserEntryTest {

    private static SqfFixSuggestion suggest(File sch, String xml, String fixId) throws Exception {
        String svrl = new SchematronServiceImpl().validateXmlWithSvrl(xml, sch).svrl();
        SqfCatalog catalog = SqfParser.parse(sch);
        return SqfCorrelator.correlate(svrl, catalog).stream()
                .flatMap(f -> f.fixes().stream())
                .filter(f -> fixId.equals(f.fixId()))
                .findFirst().orElseThrow(() -> new AssertionError("no suggestion " + fixId));
    }

    @Test
    void userEntryValueFlowsIntoContent() throws Exception {
        File sch = new File("src/test/resources/sqf/user-entry.sch");
        String xml = "<people><person><name>John</name></person></people>";
        SqfFixSuggestion fix = suggest(sch, xml, "addNickname");
        assertTrue(fix.needsUserInput());

        SqfCatalog catalog = SqfParser.parse(sch);
        List<SqfUserEntry> entries = SqfExecutionEngine.requiredUserEntries(catalog, fix);
        assertEquals(1, entries.size());
        assertEquals("nick", entries.get(0).name());
        assertEquals("Nickname", entries.get(0).title());

        SqfEditPlan plan = SqfExecutionEngine.computeEdit(xml, catalog, fix, Map.of("nick", "JD"));
        assertEquals("<people><person nickname=\"JD\"><name>John</name></person></people>",
                SqfExecutionEngineTest.apply(xml, plan));
    }

    @Test
    void userEntryDefaultXPathIsUsedWhenNoValueGiven() throws Exception {
        File sch = new File("src/test/resources/sqf/user-entry.sch");
        String xml = "<people><person><name>John</name></person></people>";
        SqfFixSuggestion fix = suggest(sch, xml, "addNickname");
        SqfCatalog catalog = SqfParser.parse(sch);

        SqfEditPlan plan = SqfExecutionEngine.computeEdit(xml, catalog, fix, Map.of());
        assertEquals("<people><person nickname=\"Johny\"><name>John</name></person></people>",
                SqfExecutionEngineTest.apply(xml, plan));
    }

    @Test
    void callFixInlinesGenericFixWithParams() throws Exception {
        File sch = new File("src/test/resources/sqf/generic-fixes.sch");
        String xml = "<doc><marker/><keep/><marker/></doc>";
        SqfFixSuggestion fix = suggest(sch, xml, "removeAllMarkers");
        SqfCatalog catalog = SqfParser.parse(sch);

        SqfEditPlan plan = SqfExecutionEngine.computeEdit(xml, catalog, fix, Map.of());
        assertEquals("<doc><keep/></doc>", SqfExecutionEngineTest.apply(xml, plan));
    }

    @Test
    void callFixCyclesAreRejected() throws Exception {
        File sch = new File("src/test/resources/sqf/generic-fixes.sch");
        String xml = "<doc><loop/></doc>";
        String svrl = new SchematronServiceImpl().validateXmlWithSvrl(xml, sch).svrl();
        SqfCatalog catalog = SqfParser.parse(sch);
        SqfFixSuggestion cycle = SqfCorrelator.correlate(svrl, catalog).stream()
                .flatMap(f -> f.fixes().stream())
                .filter(f -> "cycleA".equals(f.fixId()))
                .findFirst().orElseThrow();
        SqfExecutionException ex = assertThrows(SqfExecutionException.class,
                () -> SqfExecutionEngine.computeEdit(xml, catalog, cycle, Map.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("cycl")
                        || ex.getMessage().toLowerCase().contains("recursion"),
                "cycle must be reported: " + ex.getMessage());
    }

    @Test
    void paramDeclaringFixesAreNotOfferedDirectly() throws Exception {
        File sch = new File("src/test/resources/sqf/generic-fixes.sch");
        String xml = "<direct/>";
        String svrl = new SchematronServiceImpl().validateXmlWithSvrl(xml, sch).svrl();
        SqfCatalog catalog = SqfParser.parse(sch);
        List<SqfFinding> findings = SqfCorrelator.correlate(svrl, catalog);
        assertTrue(findings.stream().flatMap(f -> f.fixes().stream())
                        .noneMatch(f -> "removeMatching".equals(f.fixId())),
                "a fix with a default-less sqf:param must only be reachable via call-fix");
    }
}
