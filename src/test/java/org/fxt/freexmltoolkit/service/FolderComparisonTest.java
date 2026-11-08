package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.service.FolderComparison.Entry;
import org.fxt.freexmltoolkit.service.FolderComparison.Options;
import org.fxt.freexmltoolkit.service.FolderComparison.Result;
import org.fxt.freexmltoolkit.service.FolderComparison.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@link FolderComparison}. */
@DisplayName("FolderComparison")
class FolderComparisonTest {

    @TempDir
    Path dir;

    private void write(String relative, String text) throws Exception {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    @DisplayName("identical, equivalent, different, only on one side; hidden folders skipped")
    void statuses() throws Exception {
        write("l/same.txt", "x");
        write("r/same.txt", "x");
        write("l/sub/order.xml", "<order id=\"1\" date=\"x\"><item>A</item></order>");
        write("r/sub/order.xml", "<order date=\"x\" id=\"1\">\n  <item>A</item>\n</order>\n");
        write("l/changed.xml", "<a>1</a>");
        write("r/changed.xml", "<a>2</a>");
        write("l/notes.txt", "one");
        write("r/notes.txt", "two");
        write("l/broken.xml", "<a>");
        write("r/broken.xml", "<a/>");
        write("l/left-only.xsd", "<x/>");
        write("r/right-only.json", "{}");
        write("l/.git/config", "a");
        write("r/.git/config", "b");

        Result result = FolderComparison.compare(dir.resolve("l"), dir.resolve("r"), Options.DEFAULTS, null, null);
        Map<String, Entry> byPath = result.entries().stream().collect(Collectors.toMap(Entry::relativePath, e -> e));
        assertEquals(Status.IDENTICAL, byPath.get("same.txt").status());
        assertEquals(Status.EQUIVALENT, byPath.get("sub/order.xml").status());
        assertEquals(Status.DIFFERENT, byPath.get("changed.xml").status());
        assertEquals(1, byPath.get("changed.xml").xmlDifferences());
        assertEquals(Status.DIFFERENT, byPath.get("notes.txt").status());
        assertEquals(-1, byPath.get("notes.txt").xmlDifferences());
        assertEquals(Status.DIFFERENT, byPath.get("broken.xml").status());
        assertTrue(byPath.get("broken.xml").note().startsWith("Left: Not well-formed"), byPath.get("broken.xml").note());
        assertEquals(Status.ONLY_LEFT, byPath.get("left-only.xsd").status());
        assertEquals(-1, byPath.get("left-only.xsd").rightSize());
        assertEquals(Status.ONLY_RIGHT, byPath.get("right-only.json").status());
        assertFalse(byPath.containsKey(".git/config"), "hidden folders are skipped");
        assertEquals(1, result.count(Status.EQUIVALENT));
        assertFalse(result.same());

        Result bytesOnly = FolderComparison.compare(dir.resolve("l"), dir.resolve("r"),
                new Options(false, null), null, null);
        assertEquals(0, bytesOnly.count(Status.EQUIVALENT), "without XML comparison formatting counts");
    }

    @Test
    @DisplayName("matching folders, progress and cancellation")
    void sameProgressCancel() throws Exception {
        write("l/a.xml", "<a/>");
        write("r/a.xml", "<a></a>");
        int[] seen = {0};
        Result result = FolderComparison.compare(dir.resolve("l"), dir.resolve("r"), Options.DEFAULTS,
                count -> seen[0] = count, () -> false);
        assertTrue(result.same());
        assertEquals(1, seen[0]);
        assertThrows(CancellationException.class,
                () -> FolderComparison.compare(dir.resolve("l"), dir.resolve("r"), Options.DEFAULTS, null, () -> true));
        assertTrue(FolderComparison.isXml("x/Y.XSD"));
        assertFalse(FolderComparison.isXml("readme.md"));
    }
}
