package org.fxt.freexmltoolkit.controls.shell.editor.search;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.FileSearchResult;
import org.fxt.freexmltoolkit.controls.shell.editor.search.FileSearchRunner.TextSearchQuery;
import org.fxt.freexmltoolkit.controls.shell.editor.search.ReplaceApplier.FileReplacePlan;
import org.fxt.freexmltoolkit.controls.shell.editor.search.ReplaceApplier.ReplaceOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplaceApplierTest {

    private static TextSearchQuery literal(String pattern) {
        return new TextSearchQuery(pattern, true, false, false, List.of("*.xml"));
    }

    private static List<FileSearchResult> search(Path dir, TextSearchQuery q) {
        return FileSearchRunner.search(dir, q, null, null, () -> false);
    }

    private static Map<Path, Set<Integer>> allStarts(List<FileSearchResult> results) {
        return results.stream().collect(Collectors.toMap(FileSearchResult::file,
                r -> r.matches().stream().map(FileSearchRunner.TextMatch::start)
                        .collect(Collectors.toSet())));
    }

    @Test
    void replacesAllCheckedMatchesOnDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "<v>old</v><v>old</v>");
        var q = literal("old");
        var results = search(dir, q);

        List<FileReplacePlan> plans = ReplaceApplier.plan(results, allStarts(results), q, "new", null);
        assertEquals(1, plans.size());
        ReplaceOutcome outcome = ReplaceApplier.applyToDisk(plans.get(0));
        assertNull(outcome.error());
        assertEquals(2, outcome.applied());
        assertEquals("<v>new</v><v>new</v>", Files.readString(file));
    }

    @Test
    void checkedSubsetOnlyReplacesThoseMatches(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "x x x");
        var q = literal("x");
        var results = search(dir, q);
        // check only the middle match (offset 2)
        Map<Path, Set<Integer>> starts = Map.of(file, Set.of(2));

        List<FileReplacePlan> plans = ReplaceApplier.plan(results, starts, q, "Y", null);
        ReplaceApplier.applyToDisk(plans.get(0));
        assertEquals("x Y x", Files.readString(file));
    }

    @Test
    void regexGroupsExpandInTemplate(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "<v ccy=\"EUR\">1</v><v ccy=\"USD\">2</v>");
        var q = new TextSearchQuery("ccy=\"(\\w+)\"", true, false, true, List.of("*.xml"));
        var results = search(dir, q);

        List<FileReplacePlan> plans = ReplaceApplier.plan(
                results, allStarts(results), q, "currency=\"$1\"", null);
        ReplaceApplier.applyToDisk(plans.get(0));
        assertEquals("<v currency=\"EUR\">1</v><v currency=\"USD\">2</v>", Files.readString(file));
    }

    @Test
    void literalModeTreatsDollarLiterally(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "price");
        var q = literal("price");
        var results = search(dir, q);

        List<FileReplacePlan> plans = ReplaceApplier.plan(results, allStarts(results), q, "$1 cost", null);
        ReplaceApplier.applyToDisk(plans.get(0));
        assertEquals("$1 cost", Files.readString(file));
    }

    @Test
    void staleFileIsRefused(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "old");
        var q = literal("old");
        var results = search(dir, q);
        Files.writeString(file, "old but changed"); // file changes after the search

        List<FileReplacePlan> plans = ReplaceApplier.plan(results, allStarts(results), q, "new", null);
        assertNotNull(plans.get(0).error());
        assertTrue(plans.get(0).error().contains("changed"));
        assertEquals("old but changed", Files.readString(file), "file must stay untouched");
    }

    @Test
    void isoCharsetAndCrlfSurviveRoundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Charset iso = Charset.forName("ISO-8859-1");
        String content = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\r\n<a>alt äöü</a>\r\n";
        Files.write(file, content.getBytes(iso));
        var q = literal("alt");
        var results = search(dir, q);

        List<FileReplacePlan> plans = ReplaceApplier.plan(results, allStarts(results), q, "neu", null);
        ReplaceOutcome outcome = ReplaceApplier.applyToDisk(plans.get(0));
        assertNull(outcome.error());
        String written = new String(Files.readAllBytes(file), iso);
        assertEquals(content.replace("alt", "neu"), written, "CRLF and umlauts preserved in ISO-8859-1");
    }

    @Test
    void bomIsPreservedOnWrite(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "<a>old</a>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        Files.write(file, all);
        var q = literal("old");
        var results = search(dir, q);

        ReplaceApplier.applyToDisk(ReplaceApplier.plan(results, allStarts(results), q, "new", null).get(0));
        byte[] written = Files.readAllBytes(file);
        assertEquals((byte) 0xEF, written[0]);
        assertEquals((byte) 0xBB, written[1]);
        assertEquals((byte) 0xBF, written[2]);
        assertEquals("<a>new</a>", new String(written, 3, written.length - 3,
                java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void unencodableReplacementIsRefused(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.write(file, "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>old</a>"
                .getBytes(Charset.forName("ISO-8859-1")));
        var q = literal("old");
        var results = search(dir, q);

        List<FileReplacePlan> plans = ReplaceApplier.plan(results, allStarts(results), q, "新しい", null);
        ReplaceOutcome outcome = ReplaceApplier.applyToDisk(plans.get(0));
        assertNotNull(outcome.error());
        assertTrue(outcome.error().contains("not representable"));
    }

    @Test
    void bufferOverrideMarksPlanViaEditor(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "disk");
        var q = literal("buf");
        var results = FileSearchRunner.search(dir, q, p -> "buf content", null, () -> false);

        List<FileReplacePlan> plans = ReplaceApplier.plan(
                results, allStarts(results), q, "X", p -> "buf content");
        assertEquals(1, plans.size());
        assertTrue(plans.get(0).viaEditor());
        assertNull(plans.get(0).error());
        assertEquals("X content", plans.get(0).plan().applyTo(plans.get(0).baseText()));
    }
}
