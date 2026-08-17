package org.fxt.freexmltoolkit.controls.shell.editor.search;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.controls.shell.editor.search.XPathBatchRunner.FileXPathResult;
import org.fxt.freexmltoolkit.service.xmledit.XPathMatchLocator.XPathQuery;
import org.fxt.freexmltoolkit.service.xmledit.XPathReplacePlanner;
import org.fxt.freexmltoolkit.service.xmledit.XPathReplacePlanner.ReplaceMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XPathBatchRunnerTest {

    private static final XPathQuery QUERY = new XPathQuery("//price", Map.of());

    @Test
    void batchContinuesPastMalformedFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.xml"), "<r><price>1</price></r>");
        Files.writeString(dir.resolve("b.xml"), "<r><price>2</price><price>3</price></r>");
        Files.writeString(dir.resolve("broken.xml"), "<r><oops></r>");
        Files.writeString(dir.resolve("nomatch.xml"), "<r/>");

        List<FileXPathResult> results = XPathBatchRunner.search(
                dir, List.of("*.xml"), QUERY, null, null, () -> false);

        assertEquals(3, results.size(), "nomatch.xml omitted; broken.xml kept as error");
        FileXPathResult broken = results.stream()
                .filter(r -> r.error() != null).findFirst().orElseThrow();
        assertEquals("broken.xml", broken.file().getFileName().toString());
        assertTrue(broken.matches().isEmpty());
        long matchCount = results.stream()
                .flatMap(r -> r.matches().stream()).count();
        assertEquals(3, matchCount);
    }

    @Test
    void applyToDiskWritesPlannedEdits(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "<r><price>1</price></r>");
        FileXPathResult result = XPathBatchRunner.search(
                dir, List.of("*.xml"), QUERY, null, null, () -> false).get(0);

        var planned = XPathReplacePlanner.plan(result.baseText(), QUERY,
                ReplaceMode.SET_VALUE, "99", result.matches());
        var outcome = XPathBatchRunner.applyToDisk(result, planned.plan());
        assertNull(outcome.error());
        assertEquals("<r><price>99</price></r>", Files.readString(file));
    }

    @Test
    void applyToDiskRefusesChangedFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "<r><price>1</price></r>");
        FileXPathResult result = XPathBatchRunner.search(
                dir, List.of("*.xml"), QUERY, null, null, () -> false).get(0);
        var planned = XPathReplacePlanner.plan(result.baseText(), QUERY,
                ReplaceMode.SET_VALUE, "99", result.matches());

        Files.writeString(file, "<r><price>1</price><!-- changed --></r>");
        var outcome = XPathBatchRunner.applyToDisk(result, planned.plan());
        assertNotNull(outcome.error());
        assertTrue(Files.readString(file).contains("changed"), "file must stay untouched");
    }

    @Test
    void bufferOverrideWins(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.xml");
        Files.writeString(file, "<r/>");
        List<FileXPathResult> results = XPathBatchRunner.search(dir, List.of("*.xml"), QUERY,
                p -> "<r><price>7</price></r>", null, () -> false);
        assertEquals(1, results.size());
        assertTrue(results.get(0).fromEditorBuffer());
        assertEquals(1, results.get(0).matches().size());
    }
}
