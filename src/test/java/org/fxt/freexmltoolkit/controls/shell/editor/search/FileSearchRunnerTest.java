package org.fxt.freexmltoolkit.controls.shell.editor.search;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.FileSearchResult;
import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.TextSearchQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSearchRunnerTest {

    private static TextSearchQuery query(String pattern) {
        return new TextSearchQuery(pattern, false, false, false, List.of("*.xml"));
    }

    @Test
    void findsMatchesWithLineNumbersAcrossFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.xml"), "<root>\n  <name>Foo</name>\n  <name>foo</name>\n</root>");
        Files.writeString(dir.resolve("b.xml"), "<root/>");
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub/c.xml"), "foo");

        List<FileSearchResult> results = FileSearchRunner.search(
                dir, query("foo"), null, null, () -> false);

        assertEquals(2, results.size(), "b.xml has no match and is omitted");
        FileSearchResult a = results.stream()
                .filter(r -> r.file().getFileName().toString().equals("a.xml")).findFirst().orElseThrow();
        assertEquals(2, a.matches().size(), "case-insensitive by default");
        assertEquals(2, a.matches().get(0).lineNumber());
        assertEquals(3, a.matches().get(1).lineNumber());
        assertEquals("  <name>Foo</name>", a.matches().get(0).lineText());
        // offsets point at the actual match
        String text = Files.readString(dir.resolve("a.xml"));
        var m = a.matches().get(0);
        assertEquals("Foo", text.substring(m.start(), m.end()));
    }

    @Test
    void caseSensitiveAndWholeWordOptions(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.xml"), "value valuex Value");

        var caseSensitive = new TextSearchQuery("value", true, false, false, List.of("*.xml"));
        assertEquals(2, FileSearchRunner.search(dir, caseSensitive, null, null, () -> false)
                .get(0).matches().size(), "matches 'value' and the prefix in 'valuex'");

        var wholeWord = new TextSearchQuery("value", true, true, false, List.of("*.xml"));
        assertEquals(1, FileSearchRunner.search(dir, wholeWord, null, null, () -> false)
                .get(0).matches().size());
    }

    @Test
    void regexModeSupportsGroupsAndQuoting(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.xml"), "<v>1</v><v>23</v>");

        var regex = new TextSearchQuery("<v>(\\d+)</v>", true, false, true, List.of("*.xml"));
        assertEquals(2, FileSearchRunner.search(dir, regex, null, null, () -> false)
                .get(0).matches().size());

        // non-regex mode treats metacharacters literally
        Files.writeString(dir.resolve("b.xml"), "a.c abc");
        var literal = new TextSearchQuery("a.c", true, false, false, List.of("*.xml"));
        FileSearchResult b = FileSearchRunner.search(dir, literal, null, null, () -> false).stream()
                .filter(r -> r.file().getFileName().toString().equals("b.xml")).findFirst().orElseThrow();
        assertEquals(1, b.matches().size());
    }

    @Test
    void globFilterAndRecursionAndSkips(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.xml"), "hit");
        Files.writeString(dir.resolve("a.txt"), "hit");
        Files.write(dir.resolve("bin.xml"), new byte[]{'h', 'i', 't', 0, 0, 0});

        List<FileSearchResult> results = FileSearchRunner.search(
                dir, query("hit"), null, null, () -> false);
        assertEquals(2, results.size(), "a.txt filtered by glob; bin.xml kept as error entry");
        FileSearchResult bin = results.stream()
                .filter(r -> r.error() != null).findFirst().orElseThrow();
        assertTrue(bin.error().contains("Binary"));
        assertTrue(bin.matches().isEmpty());
    }

    @Test
    void cancellationStopsEarly(@TempDir Path dir) throws Exception {
        for (int i = 0; i < 10; i++) {
            Files.writeString(dir.resolve("f" + i + ".xml"), "x");
        }
        AtomicInteger seen = new AtomicInteger();
        FileSearchRunner.search(dir, query("x"), null,
                done -> seen.incrementAndGet(),
                () -> seen.get() >= 3);
        assertTrue(seen.get() < 10, "cancelled run must not process all files");
    }

    @Test
    void bufferOverrideWinsOverDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "disk content");

        List<FileSearchResult> results = FileSearchRunner.search(
                dir, query("buffer"), p -> p.equals(file) ? "buffer content" : null,
                null, () -> false);
        assertEquals(1, results.size());
        assertTrue(results.get(0).fromEditorBuffer());
        assertEquals(1, results.get(0).matches().size());
    }

    @Test
    void matchCapTruncates(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.xml"), "y".repeat(FileSearchRunner.MAX_MATCHES_PER_FILE + 50));
        FileSearchResult r = FileSearchRunner.search(dir, query("y"), null, null, () -> false).get(0);
        assertEquals(FileSearchRunner.MAX_MATCHES_PER_FILE, r.matches().size());
        assertTrue(r.truncated());
    }

    @Test
    void fingerprintReflectsDiskState(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "abc");
        FileSearchResult r = FileSearchRunner.search(dir, query("abc"), null, null, () -> false).get(0);
        assertEquals(Files.size(file), r.fingerprint().size());
        assertEquals("abc".hashCode(), r.fingerprint().textHash());
        assertEquals(StandardCharsets.UTF_8, r.charset());
    }
}
