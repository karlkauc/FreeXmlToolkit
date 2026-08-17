package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the execution-statistics collector: flag gating, probe measurements,
 * ring buffer, listeners, and CSV/JSON export.
 */
class ExecutionStatsServiceTest {

    private final ExecutionStatsService service = ExecutionStatsService.getInstance();
    private String previousFlag;

    @BeforeEach
    void setUp() {
        previousFlag = PropertiesServiceImpl.getInstance().get(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED);
        service.resetForTests();
    }

    @AfterEach
    void tearDown() {
        PropertiesServiceImpl.getInstance().set(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED,
                previousFlag == null ? "false" : previousFlag);
        service.resetForTests();
    }

    private void setEnabled(boolean enabled) {
        PropertiesServiceImpl.getInstance().set(DeveloperPropertyKeys.EXECUTION_STATS_ENABLED,
                Boolean.toString(enabled));
    }

    // ------------------------------------------------------------------
    // Flag gating
    // ------------------------------------------------------------------

    @Test
    void disabledFlagRecordsNothingButStillReturnsElapsedMillis() throws Exception {
        setEnabled(false);

        var probe = service.begin(ExecutionStats.OperationType.XSLT, "test.xslt");
        Thread.sleep(5);
        long elapsed = probe.finish(100, 200, true, "");

        assertTrue(elapsed >= 5, "probe must return wall time even when disabled, was: " + elapsed);
        assertTrue(service.snapshot().isEmpty(), "nothing must be recorded when the flag is off");
    }

    @Test
    void enabledFlagRecordsACompleteEntry() {
        setEnabled(true);

        var probe = service.begin(ExecutionStats.OperationType.VALIDATION, "demo.xml");
        probe.phase("XSD", 12);
        probe.phase("Schematron", 8);
        probe.finish(1000, -1, true, "");

        List<ExecutionStats> snapshot = service.snapshot();
        assertEquals(1, snapshot.size());
        ExecutionStats stats = snapshot.get(0);
        assertEquals(ExecutionStats.OperationType.VALIDATION, stats.type());
        assertEquals("demo.xml", stats.target());
        assertNotNull(stats.startedAt());
        assertTrue(stats.wallMillis() >= 0);
        assertEquals(1000, stats.inputChars());
        assertEquals(-1, stats.outputChars());
        assertEquals(List.of("XSD", "Schematron"), List.copyOf(stats.phaseMillis().keySet()),
                "phases must keep insertion order");
        assertTrue(stats.success());
        assertEquals("", stats.errorSummary());
    }

    @Test
    void errorRunCapturesTheErrorSummary() {
        setEnabled(true);

        service.begin(ExecutionStats.OperationType.XQUERY, "broken.xq")
                .finish(50, -1, false, "ERROR: XPST0003 unexpected token");

        ExecutionStats stats = service.snapshot().get(0);
        assertFalse(stats.success());
        assertEquals("ERROR: XPST0003 unexpected token", stats.errorSummary());
    }

    @Test
    void zeroPhasesAreNotRecorded() {
        setEnabled(true);

        var probe = service.begin(ExecutionStats.OperationType.VALIDATION, "x.xml");
        probe.phase("Schematron", 0); // stage did not run
        probe.finish(-1, -1, true, "");

        assertTrue(service.snapshot().get(0).phaseMillis().isEmpty());
    }

    // ------------------------------------------------------------------
    // Ring buffer, clear, listeners
    // ------------------------------------------------------------------

    @Test
    void ringBufferEvictsOldestBeyondCapacity() {
        setEnabled(true);

        for (int i = 0; i < ExecutionStatsService.MAX_ENTRIES + 5; i++) {
            service.begin(ExecutionStats.OperationType.XPATH, "q" + i).finish(-1, -1, true, "");
        }

        List<ExecutionStats> snapshot = service.snapshot();
        assertEquals(ExecutionStatsService.MAX_ENTRIES, snapshot.size());
        assertEquals("q" + (ExecutionStatsService.MAX_ENTRIES + 4), snapshot.get(0).target(),
                "snapshot must be newest first");
        assertEquals("q5", snapshot.get(snapshot.size() - 1).target(),
                "the oldest five entries must have been evicted");
    }

    @Test
    void clearEmptiesTheHistory() {
        setEnabled(true);
        service.begin(ExecutionStats.OperationType.XSLT, "a.xslt").finish(-1, -1, true, "");

        service.clear();

        assertTrue(service.snapshot().isEmpty());
    }

    @Test
    void listenerIsNotifiedOncePerFinish() {
        setEnabled(true);
        AtomicInteger notifications = new AtomicInteger();
        java.util.function.Consumer<ExecutionStats> listener = s -> notifications.incrementAndGet();
        service.addListener(listener);

        var probe = service.begin(ExecutionStats.OperationType.XPROC, "pipe.xpl");
        probe.finish(-1, -1, true, "");
        probe.finish(-1, -1, true, ""); // double finish must not record twice

        assertEquals(1, notifications.get());
        assertEquals(1, service.snapshot().size());

        service.removeListener(listener);
        service.begin(ExecutionStats.OperationType.XPROC, "pipe2.xpl").finish(-1, -1, true, "");
        assertEquals(1, notifications.get(), "removed listener must not fire");
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    @Test
    void csvExportQuotesCommasAndContainsHeader() {
        setEnabled(true);
        var probe = service.begin(ExecutionStats.OperationType.XPATH, "a, tricky \"target\"");
        probe.phase("Eval", 3);
        probe.finish(10, 20, true, "");

        String csv = ExecutionStatsService.toCsv(service.snapshot());

        assertTrue(csv.startsWith("id,startedAt,operation,target,wallMillis"),
                "CSV must start with the header row");
        assertTrue(csv.contains("\"a, tricky \"\"target\"\"\""),
                "target with comma/quotes must be RFC-4180 quoted, was: " + csv);
        assertTrue(csv.contains("Eval=3"));
    }

    @Test
    void jsonExportContainsAllFields() {
        setEnabled(true);
        var probe = service.begin(ExecutionStats.OperationType.XSLT, "report.xslt");
        probe.phase("Compile", 7);
        probe.finish(111, 222, true, "");

        String json = ExecutionStatsService.toJson(service.snapshot());

        assertTrue(json.contains("\"operation\": \"XSLT\""));
        assertTrue(json.contains("\"target\": \"report.xslt\""));
        assertTrue(json.contains("\"inputChars\": 111"));
        assertTrue(json.contains("\"Compile\": 7"));
        assertTrue(json.contains("\"success\": true"));
    }

    // ------------------------------------------------------------------
    // Display helpers
    // ------------------------------------------------------------------

    @Test
    void shortLabelAndDurationFormatting() {
        setEnabled(true);
        service.begin(ExecutionStats.OperationType.FOP_PDF, "out.pdf").finish(-1, 5000, true, "");
        ExecutionStats stats = service.snapshot().get(0);

        assertTrue(stats.shortLabel().startsWith("FOP_PDF · "));
        assertEquals("42 ms", ExecutionStats.formatMillis(42));
        assertEquals("1.2 s", ExecutionStats.formatMillis(1234));
        assertEquals("01:05", ExecutionStats.formatMillis(65_000));
    }
}
