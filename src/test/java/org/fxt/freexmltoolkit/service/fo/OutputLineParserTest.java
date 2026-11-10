package org.fxt.freexmltoolkit.service.fo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.fxt.freexmltoolkit.service.FopEvent;
import org.junit.jupiter.api.Test;

class OutputLineParserTest {

    @Test
    void severityFollowsTheWordsOfALine() {
        assertEquals("ERROR", OutputLineParser.severityOf("[error] Unknown property"));
        assertEquals("ERROR", OutputLineParser.severityOf("SEVERE: Exception while rendering"));
        assertEquals("ERROR", OutputLineParser.severityOf("Fatal: out of memory"));
        assertEquals("WARN", OutputLineParser.severityOf("WARNING: Font \"Arial\" not found"));
        assertEquals("WARN", OutputLineParser.severityOf("[warn] overflow"));
        assertEquals("INFO", OutputLineParser.severityOf("Rendered 3 pages"));
        assertEquals("INFO", OutputLineParser.severityOf("AHFCmd :ErrorLevel=0"));
        assertEquals("INFO", OutputLineParser.severityOf("Finished with 0 errors"));
        assertEquals("INFO", OutputLineParser.severityOf("terrorist.xml"), "only whole words count");
    }

    @Test
    void blankLinesAndLogHeadersAreLeftOut() {
        List<FopEvent> events = OutputLineParser.parse(List.of(
                "Nov 10, 2026 10:15:02 AM org.apache.fop.events.LoggingEventListener processEvent",
                "WARNING: Font \"Symbol,normal,700\" not found. Substituting with \"Symbol,normal,400\".",
                "   ",
                "2026-11-10 10:15:03 INFO done"), FoProcessorPreset.APACHE_FOP, "FOP 2.10");
        assertEquals(1, events.size());
        assertEquals("WARN", events.get(0).severity());
        assertEquals("FOP 2.10", events.get(0).id());
    }
}
