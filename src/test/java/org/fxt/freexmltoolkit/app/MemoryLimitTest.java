package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.app.MemoryLimit.Action;
import org.junit.jupiter.api.Test;

class MemoryLimitTest {

    private static final long MB = 1024L * 1024;

    @Test
    void storedValueParsesToMegabytes() {
        assertEquals(0, MemoryLimit.configuredMb(null));
        assertEquals(0, MemoryLimit.configuredMb(" "));
        assertEquals(0, MemoryLimit.configuredMb("0"));
        assertEquals(0, MemoryLimit.configuredMb("abc"));
        assertEquals(0, MemoryLimit.configuredMb("512"), "below the minimum counts as automatic");
        assertEquals(8192, MemoryLimit.configuredMb(" 8192 "));
    }

    @Test
    void choicesNeverExceedThePhysicalMemory() {
        assertEquals(List.of(2048L, 4096L, 6144L, 8192L), MemoryLimit.choicesMb(8192));
        assertEquals(10, MemoryLimit.choicesMb(-1).size());
        assertEquals(65536L, MemoryLimit.choicesMb(128 * 1024).getLast());
    }

    @Test
    void automaticNeedsNoRelaunch() {
        assertEquals(Action.NONE, MemoryLimit.decide(0, 4096 * MB, null));
        assertEquals(Action.NONE, MemoryLimit.decide(0, 4096 * MB, "0"));
    }

    @Test
    void automaticUndoesAnInheritedOverride() {
        // e.g. the updater restarted a process that had been relaunched with 16 GB
        assertEquals(Action.RESET, MemoryLimit.decide(0, 16384 * MB, "16384"));
    }

    @Test
    void differentLimitIsApplied() {
        assertEquals(Action.APPLY, MemoryLimit.decide(16384, 4096 * MB, null));
        assertEquals(Action.APPLY, MemoryLimit.decide(2048, 8192 * MB, null), "a lower limit is applied too");
    }

    @Test
    void limitWithinToleranceCountsAsApplied() {
        // Runtime.maxMemory() reports a little less than -Xmx
        assertEquals(Action.NONE, MemoryLimit.decide(8192, 7900 * MB, null));
    }

    @Test
    void relaunchedProcessNeverRelaunchesForTheSameValue() {
        assertEquals(Action.NONE, MemoryLimit.decide(16384, 4096 * MB, "16384"));
        assertEquals(Action.APPLY, MemoryLimit.decide(16384, 8192 * MB, "8192"), "the setting changed since");
    }

    @Test
    void javaOptionsReplaceAnyEarlierHeapLimit() {
        assertEquals("-Xmx8192m", MemoryLimit.javaOptions(null, 8192));
        assertEquals("-Dfoo=1 -Xmx8192m", MemoryLimit.javaOptions("-Dfoo=1 -Xmx2g -XX:MaxRAMPercentage=25", 8192));
        assertEquals("-Dfoo=1", MemoryLimit.javaOptions("-Dfoo=1 -Xmx2g", 0));
        assertNull(MemoryLimit.javaOptions("-Xmx2g", 0));
    }

    @Test
    void relaunchEnvironmentCarriesTheLimitAndWaitsForTheLock() {
        Map<String, String> env = new HashMap<>();
        MemoryLimit.prepareEnvironment(env, 16384);
        assertEquals("-Xmx16384m", env.get(MemoryLimit.JAVA_OPTIONS_ENV));
        assertEquals("16384", env.get(MemoryLimit.ENV_MARKER));
        assertEquals("1", env.get(SingleInstance.RELAUNCH_ENV));

        MemoryLimit.prepareEnvironment(env, 0);
        assertFalse(env.containsKey(MemoryLimit.JAVA_OPTIONS_ENV));
        assertEquals("0", env.get(MemoryLimit.ENV_MARKER));
    }

    @Test
    void developmentRunsNeverRelaunch() {
        // no jpackage.app-path in tests: the setting is read but nothing is started
        assertFalse(MemoryLimit.relaunchIfNeeded(new String[0], null, List::of, () -> "65536", v -> { }));
    }
}
