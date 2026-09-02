package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The documentation-coverage band drives the bar colour on the Statistics sub-tab. */
class StatisticsSectionTest {

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
