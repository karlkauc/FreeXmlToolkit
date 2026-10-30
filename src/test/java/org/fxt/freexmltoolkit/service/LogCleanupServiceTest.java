package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private static Path file(Path dir, String name, int sizeKb, int ageDays) throws Exception {
        Path file = dir.resolve(name);
        Files.write(file, new byte[sizeKb * 1024]);
        Files.setLastModifiedTime(file, FileTime.from(NOW.minus(Duration.ofDays(ageDays))));
        return file;
    }

    @Test
    void recognisesTheApplicationsLogNames() {
        for (String name : new String[] {"fxt.log", "fxt-2026-10-30-1.log.gz", "fxt-test.log",
                "fxt-test-2026-10-27-118.log.gz", "fxt-test.log-20261015.gz", "fxt-test.log-20261030",
                "1730000000-fxt.log"}) {
            assertTrue(LogCleanupService.isLogFile(name), name);
        }
        for (String name : new String[] {"notes.txt", "app.log", "fxt.properties", "fxt.log.bak", "schema.xsd"}) {
            assertFalse(LogCleanupService.isLogFile(name), name);
        }
    }

    @Test
    void deletesFilesOlderThanTheAgeLimit(@TempDir Path dir) throws Exception {
        Path old = file(dir, "fxt-2026-10-01-1.log.gz", 1, 29);
        Path legacy = file(dir, "fxt-test.log-20261015.gz", 1, 15);
        Path recent = file(dir, "fxt-2026-10-29-1.log.gz", 1, 1);

        var result = LogCleanupService.clean(dir, 14, 0, NOW, Set.of());

        assertEquals(2, result.files());
        assertEquals(2 * 1024, result.bytes());
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(recent));
    }

    @Test
    void sizeLimitKeepsTheNewestFiles(@TempDir Path dir) throws Exception {
        Path newest = file(dir, "fxt-a.log.gz", 600, 1);
        Path middle = file(dir, "fxt-b.log.gz", 300, 2);
        Path oldest = file(dir, "fxt-c.log.gz", 300, 3);

        var result = LogCleanupService.clean(dir, 0, 1, NOW, Set.of()); // 1 MB = 1024 KB

        assertEquals(1, result.files());
        assertTrue(Files.exists(newest));
        assertTrue(Files.exists(middle));
        assertFalse(Files.exists(oldest));
    }

    @Test
    void neverDeletesTheActiveFileOrForeignFiles(@TempDir Path dir) throws Exception {
        Path active = file(dir, "fxt.log", 2048, 60);
        Path foreign = file(dir, "notes.txt", 1, 60);
        Path sub = Files.createDirectory(dir.resolve("fxt-archive.log"));

        var result = LogCleanupService.clean(dir, 14, 1, NOW, Set.of(active.toAbsolutePath().normalize()));

        assertEquals(0, result.files());
        assertTrue(Files.exists(active));
        assertTrue(Files.exists(foreign));
        assertTrue(Files.isDirectory(sub));
    }

    @Test
    void noLimitsMeansNothingIsDeleted(@TempDir Path dir) throws Exception {
        Path old = file(dir, "fxt-2025-01-01-1.log.gz", 1, 400);

        assertEquals(LogCleanupService.Result.NONE, LogCleanupService.clean(dir, 0, 0, NOW, Set.of()));
        assertTrue(Files.exists(old));
    }

    @Test
    void usageCountsOnlyLogFiles(@TempDir Path dir) throws Exception {
        file(dir, "fxt.log", 2, 0);
        file(dir, "fxt-x.log.gz", 3, 0);
        file(dir, "notes.txt", 5, 0);

        var usage = LogCleanupService.usage(dir);

        assertEquals(2, usage.files());
        assertEquals(5 * 1024, usage.bytes());
        assertEquals(0, LogCleanupService.usage(dir.resolve("missing")).files());
    }
}
