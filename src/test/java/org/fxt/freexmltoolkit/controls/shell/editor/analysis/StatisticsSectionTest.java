package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure helpers of the Statistics / Quality sub-tabs. */
class StatisticsSectionTest {

    @Test
    void qualityCountTextDistinguishesFilteredFromUnfiltered() {
        assertEquals("1 issue", AnalysisSupport.countText(1, 1, "issue"));
        assertEquals("2251 issues", AnalysisSupport.countText(2251, 2251, "issue"));
        assertEquals("Showing 350 of 2251 issues", AnalysisSupport.countText(350, 2251, "issue"));
        assertEquals("Showing 0 of 1 issue", AnalysisSupport.countText(0, 1, "issue"));
    }

    @Test
    void coverageBandThresholds() {
        assertEquals("poor", StatisticsSection.coverageBand(0));
        assertEquals("poor", StatisticsSection.coverageBand(39.9));
        assertEquals("fair", StatisticsSection.coverageBand(40));
        assertEquals("fair", StatisticsSection.coverageBand(74.9));
        assertEquals("good", StatisticsSection.coverageBand(75));
        assertEquals("good", StatisticsSection.coverageBand(100));
    }
}
