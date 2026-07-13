package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the shared recent-Schematron store (the Explorer's one-click
 * validation feeds from it): ordering, de-duplication, the 10-entry cap,
 * pruning of deleted files, and clearing.
 */
class PropertiesServiceRecentSchematronTest {

    private final PropertiesService service = PropertiesServiceImpl.getInstance();

    @BeforeEach
    void clearStore() {
        service.clearRecentSchematronFiles();
    }

    @Test
    void addedFilesComeBackMostRecentFirstWithoutDuplicates(@TempDir Path tmp) throws Exception {
        File a = Files.writeString(tmp.resolve("a.sch"), "<x/>").toFile();
        File b = Files.writeString(tmp.resolve("b.sch"), "<x/>").toFile();

        service.addRecentSchematronFile(a);
        service.addRecentSchematronFile(b);
        service.addRecentSchematronFile(a); // re-adding moves to front, no duplicate

        List<String> recent = service.getRecentSchematronFiles().stream()
                .map(File::getAbsolutePath).toList();
        assertEquals(List.of(a.getAbsolutePath(), b.getAbsolutePath()), recent);
    }

    @Test
    void storeIsCappedAtTenEntries(@TempDir Path tmp) throws Exception {
        for (int i = 0; i < 12; i++) {
            service.addRecentSchematronFile(
                    Files.writeString(tmp.resolve("s" + i + ".sch"), "<x/>").toFile());
        }
        List<File> recent = service.getRecentSchematronFiles();
        assertEquals(10, recent.size());
        assertEquals("s11.sch", recent.get(0).getName());
        assertEquals("s2.sch", recent.get(9).getName());
    }

    @Test
    void deletedFilesArePrunedAndMissingAddsIgnored(@TempDir Path tmp) throws Exception {
        File gone = Files.writeString(tmp.resolve("gone.sch"), "<x/>").toFile();
        service.addRecentSchematronFile(gone);
        assertTrue(gone.delete());
        assertTrue(service.getRecentSchematronFiles().isEmpty(),
                "deleted files must be pruned on read");

        service.addRecentSchematronFile(new File(tmp.toFile(), "never-existed.sch"));
        service.addRecentSchematronFile(null);
        assertTrue(service.getRecentSchematronFiles().isEmpty(),
                "null / non-existing files must not be recorded");
    }

    @Test
    void clearEmptiesTheStore(@TempDir Path tmp) throws Exception {
        service.addRecentSchematronFile(
                Files.writeString(tmp.resolve("c.sch"), "<x/>").toFile());
        assertFalse(service.getRecentSchematronFiles().isEmpty());
        service.clearRecentSchematronFiles();
        assertTrue(service.getRecentSchematronFiles().isEmpty());
    }
}
