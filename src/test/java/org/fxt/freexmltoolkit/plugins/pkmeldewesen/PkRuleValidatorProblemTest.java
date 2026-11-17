package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** An FMA rule finding keeps its code, the category of its rule group and the position it is about. */
class PkRuleValidatorProblemTest {

    private static final PkRuleCatalog.Rule SUM = new PkRuleCatalog.Rule("SUM_1", "PK-4711", "abgl", "E", "Total", "");

    @Test
    void aPositionFindingIsAboutItsAsset() {
        var finding = new PkRuleCheck.Finding(SUM, "E", "2", "US0378331005", "1 ≠ 2", 40, "US0378331005", "Apple Inc.");
        var problem = PkRuleValidator.problem(finding);
        assertEquals("PK-4711", problem.rule());
        assertEquals("FMA_RECONCILIATION", problem.category());
        assertEquals("ASSET", problem.subjectKind());
        assertEquals("US0378331005", problem.subjectKey());
        assertEquals("Apple Inc.", problem.subjectLabel());
        assertEquals(40, problem.line());
    }

    @Test
    void aCommunityFindingIsAboutItsVrgAndAnInternalCheckIsAdditional() {
        var rule = new PkRuleCatalog.Rule("INT_9", "", "assets", "W", "Check", "");
        var problem = PkRuleValidator.problem(new PkRuleCheck.Finding(rule, "W", "3", "", "", 5));
        assertEquals("FMA_ADDITIONAL", problem.category());
        assertEquals("RECORD", problem.subjectKind());
        assertEquals("VRG 3", problem.subjectKey());
        assertNull(problem.subjectLabel());
    }
}
