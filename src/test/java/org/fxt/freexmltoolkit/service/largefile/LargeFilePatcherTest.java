package org.fxt.freexmltoolkit.service.largefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.fxt.freexmltoolkit.service.largefile.LargeFilePatcher.Patch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link LargeFilePatcher}: splices, refusals, cancellation and backups. */
@DisplayName("LargeFilePatcher")
class LargeFilePatcherTest {

    @TempDir
    Path dir;

    private Path file(String text) throws IOException {
        Path file = dir.resolve("p.txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static long modified(Path file) throws IOException {
        return Files.getLastModifiedTime(file).toMillis();
    }

    private static Patch patch(long start, long end, String text) {
        return new Patch(start, end, text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("several patches in any order: longer, shorter, insert, delete")
    void patches() throws Exception {
        Path file = file("0123456789");
        var result = LargeFilePatcher.apply(file, 10, modified(file),
                List.of(patch(8, 10, ""), patch(0, 1, "AAA"), patch(4, 4, "+"), patch(5, 7, "x")), false, null, null);
        assertEquals("AAA123+4x7", Files.readString(file));
        assertEquals(10, result.size());
        try (var listing = Files.list(dir)) {
            assertEquals(1, listing.count(), "no temporary file is left");
        }
    }

    @Test
    @DisplayName("a changed file, overlapping or outside patches are refused; the file stays")
    void refusals() throws Exception {
        Path file = file("abcdef");
        long modified = modified(file);
        assertThrows(IOException.class, () -> LargeFilePatcher.apply(file, 7, modified, List.of(patch(0, 1, "x")), false, null, null));
        assertThrows(IOException.class, () -> LargeFilePatcher.apply(file, 6, modified - 1000, List.of(patch(0, 1, "x")), false, null, null));
        assertThrows(IOException.class, () -> LargeFilePatcher.apply(file, 6, modified,
                List.of(patch(0, 3, "x"), patch(2, 4, "y")), false, null, null));
        assertThrows(IOException.class, () -> LargeFilePatcher.apply(file, 6, modified, List.of(patch(5, 9, "x")), false, null, null));
        assertThrows(IllegalArgumentException.class, () -> patch(3, 2, ""));
        assertEquals("abcdef", Files.readString(file));
    }

    @Test
    @DisplayName("cancelling leaves the original and no temporary file")
    void cancel() throws Exception {
        Path file = file("x".repeat(1000));
        assertThrows(CancellationException.class, () -> LargeFilePatcher.apply(file, 1000, modified(file),
                List.of(patch(500, 501, "y")), false, null, () -> true));
        assertEquals("x".repeat(1000), Files.readString(file));
        try (var listing = Files.list(dir)) {
            assertEquals(1, listing.count());
        }
    }

    @Test
    @DisplayName("a backup keeps the old file next to the new one")
    void backup() throws Exception {
        Path file = file("old");
        var result = LargeFilePatcher.apply(file, 3, modified(file), List.of(patch(0, 3, "new")), true, null, null);
        assertNotNull(result.backup());
        assertEquals("old", Files.readString(result.backup()));
        assertEquals("new", Files.readString(file));
        assertFalse(result.backup().equals(file));
    }
}
