package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EditStatsTest {

    private RecordingTelemetryService telemetry;

    private static final class AddElementCommand {
    }

    @BeforeEach
    void setUp() {
        telemetry = new RecordingTelemetryService();
        Telemetry.install(telemetry);
        EditStats.reset();
    }

    @AfterEach
    void tearDown() {
        Telemetry.reset();
        EditStats.reset();
    }

    @Test
    void countsAreFlushedAsOneEventPerDomain() {
        EditStats.executed(EditStats.DOMAIN_XSD, AddElementCommand.class);
        EditStats.executed(EditStats.DOMAIN_XSD, AddElementCommand.class);
        EditStats.undone(EditStats.DOMAIN_XSD);
        EditStats.feature("completion_accepted");
        EditStats.executed(null, AddElementCommand.class); // uncounted domain

        EditStats.flush();
        List<TelemetryEvent> events = telemetry.events("edit_summary");
        assertEquals(2, events.size());
        TelemetryEvent xsd = events.stream().filter(e -> "xsd".equals(e.meta().get("domain"))).findFirst().orElseThrow();
        assertEquals(2, ((Number) xsd.meta().get("add_element")).intValue());
        assertEquals(1, ((Number) xsd.meta().get("undo")).intValue());
        assertEquals(3, ((Number) xsd.meta().get("total")).intValue());

        EditStats.flush();
        assertEquals(2, telemetry.events("edit_summary").size(), "counters are reset after a flush");
    }

    @Test
    void largeDomainsFlushEarly() {
        for (int i = 0; i < EditStats.EARLY_FLUSH_AT; i++) {
            EditStats.feature("completion_shown");
        }
        assertEquals(1, telemetry.events("edit_summary").size());
    }

    @Test
    void topCountsKeepsTheMostFrequentAndSumsTheRest() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < 25; i++) {
            counts.put("k" + i, i + 1);
        }
        Map<String, Integer> top = EditStats.topCounts(counts);
        assertEquals(EditStats.MAX_KEYS + 1, top.size());
        assertEquals(25, top.get("k24"));
        assertEquals(1 + 2 + 3 + 4 + 5, top.get("other"));
    }

    @Test
    void commandNamesAreSnakeCaseWithoutSuffix() {
        assertEquals("add_element", EditStats.commandName(AddElementCommand.class));
        assertEquals("string", EditStats.commandName(String.class));
        assertTrue(EditStats.commandName(new Object() { }.getClass()).equals("anonymous"));
    }
}
