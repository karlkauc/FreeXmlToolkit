package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionMarkerTest {

    @TempDir
    Path dir;

    private static long markerCount(Path dir) throws Exception {
        try (var files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    void cleanExitLeavesNothingToReport() throws Exception {
        SessionMarker first = new SessionMarker(dir, m -> false);
        assertTrue(first.begin("s1", "2.4.0").isEmpty());
        assertEquals(1, markerCount(dir));
        first.end();
        assertEquals(0, markerCount(dir));

        assertTrue(new SessionMarker(dir, m -> false).begin("s2", "2.4.0").isEmpty());
    }

    @Test
    void deadSessionIsReportedOnceOnTheNextStart() throws Exception {
        new SessionMarker(dir, m -> false).begin("crashed", "2.3.0"); // never ended

        List<SessionMarker.DeadSession> dead = new SessionMarker(dir, m -> false).begin("s2", "2.4.0");
        assertEquals(List.of(new SessionMarker.DeadSession("crashed", "2.3.0")), dead);
        assertTrue(new SessionMarker(dir, m -> false).begin("s3", "2.4.0").stream()
                .noneMatch(d -> d.sessionId().equals("crashed")), "reported only once");
    }

    @Test
    void runningInstancesAreNotReported() throws Exception {
        new SessionMarker(dir, m -> true).begin("other-instance", "2.4.0");
        Set<String> alive = Set.of("other-instance");
        List<SessionMarker.DeadSession> dead =
                new SessionMarker(dir, m -> alive.contains(m.sessionId())).begin("me", "2.4.0");
        assertTrue(dead.isEmpty());
        assertEquals(2, markerCount(dir));
    }

    @Test
    void endBeforeBeginLeavesNoMarker() throws Exception {
        SessionMarker marker = new SessionMarker(dir, m -> false);
        marker.end();
        marker.begin("late", "2.4.0");
        assertEquals(0, markerCount(dir));
    }

    @Test
    void reportsAreCapped() throws Exception {
        for (int i = 0; i < 6; i++) {
            new SessionMarker(dir, m -> true).begin("dead-" + i, "2.4.0"); // keeps the others
        }
        List<SessionMarker.DeadSession> dead = new SessionMarker(dir, m -> false).begin("new", "2.4.0");
        assertEquals(SessionMarker.MAX_REPORTED, dead.size());
        assertEquals(1, markerCount(dir), "all dead markers are cleaned up, only the new one stays");
    }

    @Test
    void ownProcessIsAliveAndGarbageIsNot() {
        long pid = ProcessHandle.current().pid();
        assertTrue(SessionMarker.processAlive(new SessionMarker.Marker("s", "v", pid, 0)));
        assertFalse(SessionMarker.processAlive(new SessionMarker.Marker("s", "v", Long.MAX_VALUE, 0)));
    }
}
