package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link DiskStamp} and the off-thread comparison in {@link ExternalChangeMonitor}. */
class DiskStampTest {

    @Test
    void stampOfExistingFileCarriesTimeAndSize(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("a.xml");
        Files.writeString(file, "<a/>");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_700_000_000_000L));

        DiskStamp stamp = DiskStamp.of(file);

        assertTrue(stamp.exists());
        assertEquals(1_700_000_000_000L, stamp.lastModifiedMillis());
        assertEquals(4, stamp.size());
        assertEquals(stamp, DiskStamp.of(file), "an untouched file keeps its stamp");
    }

    @Test
    void missingFileAndMissingParentAreReportedAsMissing(@TempDir Path tmp) throws Exception {
        assertEquals(DiskStamp.MISSING, DiskStamp.of(tmp.resolve("nope.xml")));
        assertEquals(DiskStamp.MISSING, DiskStamp.of(tmp.resolve("gone").resolve("nope.xml")));

        Path file = tmp.resolve("plain.txt");
        Files.writeString(file, "x");
        assertEquals(DiskStamp.MISSING, DiskStamp.of(file.resolve("child.xml")),
                "a path below a regular file cannot exist");
    }

    @Test
    void probeReportsOnlyFilesThatDifferFromTheirStamp(@TempDir Path tmp) throws Exception {
        Path same = tmp.resolve("same.xml");
        Path changed = tmp.resolve("changed.xml");
        Path deleted = tmp.resolve("deleted.xml");
        Path fresh = tmp.resolve("fresh.xml");
        for (Path p : List.of(same, changed, deleted, fresh)) {
            Files.writeString(p, "<old/>");
        }
        OpenDocument sameDoc = OpenDocument.forPath(same);
        OpenDocument changedDoc = OpenDocument.forPath(changed);
        OpenDocument deletedDoc = OpenDocument.forPath(deleted);
        OpenDocument freshDoc = OpenDocument.forPath(fresh);
        List<ExternalChangeMonitor.Watched> snapshot = List.of(
                new ExternalChangeMonitor.Watched(sameDoc, same, DiskStamp.of(same)),
                new ExternalChangeMonitor.Watched(changedDoc, changed, DiskStamp.of(changed)),
                new ExternalChangeMonitor.Watched(deletedDoc, deleted, DiskStamp.of(deleted)),
                new ExternalChangeMonitor.Watched(freshDoc, fresh, null));

        Files.writeString(changed, "<new-content/>");
        Files.delete(deleted);

        List<ExternalChangeMonitor.Change> changes = ExternalChangeMonitor.probe(snapshot);

        assertEquals(List.of(changedDoc, deletedDoc, freshDoc),
                changes.stream().map(ExternalChangeMonitor.Change::document).toList());
        assertFalse(changes.get(0).deleted());
        assertEquals("<new-content/>", changes.get(0).diskText());
        assertTrue(changes.get(1).deleted());
        assertNull(changes.get(1).diskText());
        assertNull(changes.get(2).known(), "a document without a stamp reports its first known state");
        assertEquals(DiskStamp.of(fresh), changes.get(2).current());
    }
}
