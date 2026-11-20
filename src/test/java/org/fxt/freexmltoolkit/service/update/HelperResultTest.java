package org.fxt.freexmltoolkit.service.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HelperResultTest {

    private static final Instant LAUNCHED = Instant.parse("2026-11-20T05:57:57Z");

    @TempDir
    Path dir;

    private Path log(String... lines) throws IOException {
        return Files.writeString(dir.resolve("fxt-update-helper.log"), String.join("\n", lines) + "\n");
    }

    @Test
    void readsTheLastResultOfThisUpdate() throws IOException {
        Path log = log(
                "2026-11-18T10:00:00.000001 [INFO] RESULT ok",
                "2026-11-20T05:58:03.000000 [INFO] UpdateHelper started (Rust v1.2.0)",
                "2026-11-20T05:59:10.120000 [WARN] Copy locked (attempt 6): C:\\Users\\x\\app\\a.jar (os error 32)",
                "2026-11-20T06:00:54.512345 [INFO] RESULT copy_exhausted folder=app",
                "2026-11-20T06:00:54.600000 [INFO] Restarting: C:\\Users\\x\\FreeXmlToolkit.exe");

        HelperResult result = HelperResult.lastSince(log, LAUNCHED).orElseThrow();

        assertEquals("copy_exhausted", result.code());
        assertEquals("app", result.folder());
    }

    @Test
    void aResultOfAnEarlierUpdateDoesNotCount() throws IOException {
        Path log = log("2026-11-18T10:00:00.000001 [INFO] RESULT ok");
        assertEquals(Optional.empty(), HelperResult.lastSince(log, LAUNCHED));
    }

    @Test
    void anOkWithoutFolderAndAMissingLog() throws IOException {
        Path log = log("2026-11-20T05:58:40.000000 [INFO] RESULT ok");
        HelperResult result = HelperResult.lastSince(log, LAUNCHED).orElseThrow();
        assertEquals("ok", result.code());
        assertNull(result.folder());
        assertTrue(HelperResult.lastSince(dir.resolve("missing.log"), LAUNCHED).isEmpty());
    }

    @Test
    void anythingButFixedCodesAndShortFolderNamesIsIgnored() throws IOException {
        Path log = log(
                "2026-11-20T05:58:40.000000 [INFO] RESULT copy_exhausted folder=C:\\Users\\karl",
                "2026-11-20T05:58:41.000000 [INFO] RESULT Copy failed for C:\\x");
        assertTrue(HelperResult.lastSince(log, LAUNCHED).isEmpty());
    }
}
