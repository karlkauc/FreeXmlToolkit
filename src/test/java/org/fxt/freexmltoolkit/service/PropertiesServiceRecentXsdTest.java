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
 * Verifies the shared recent-XSD store (the Explorer's XSD picker
 * feeds from it): ordering, de-duplication, the 10-entry cap,
 * pruning of deleted files, and clearing.
 */
class PropertiesServiceRecentXsdTest {

    private final PropertiesService service = PropertiesServiceImpl.getInstance();

    @BeforeEach
    void clearStore() {
        service.clearRecentXsdFiles();
    }

    @Test
    void addedFilesComeBackMostRecentFirstWithoutDuplicates(@TempDir Path tmp) throws Exception {
        File a = Files.writeString(tmp.resolve("a.xsd"), "<x/>").toFile();
        File b = Files.writeString(tmp.resolve("b.xsd"), "<x/>").toFile();

        service.addRecentXsdFile(a);
        service.addRecentXsdFile(b);
        service.addRecentXsdFile(a); // re-adding moves to front, no duplicate

        List<String> recent = service.getRecentXsdFiles().stream()
                .map(File::getAbsolutePath).toList();
        assertEquals(List.of(a.getAbsolutePath(), b.getAbsolutePath()), recent);
    }

    @Test
    void storeIsCappedAtTenEntries(@TempDir Path tmp) throws Exception {
        for (int i = 0; i < 12; i++) {
            service.addRecentXsdFile(
                    Files.writeString(tmp.resolve("s" + i + ".xsd"), "<x/>").toFile());
        }
        List<File> recent = service.getRecentXsdFiles();
        assertEquals(10, recent.size());
        assertEquals("s11.xsd", recent.get(0).getName());
        assertEquals("s2.xsd", recent.get(9).getName());
    }

    @Test
    void deletedFilesArePrunedAndMissingAddsIgnored(@TempDir Path tmp) throws Exception {
        File gone = Files.writeString(tmp.resolve("gone.xsd"), "<x/>").toFile();
        service.addRecentXsdFile(gone);
        assertTrue(gone.delete());
        assertTrue(service.getRecentXsdFiles().isEmpty(),
                "deleted files must be pruned on read");

        service.addRecentXsdFile(new File(tmp.toFile(), "never-existed.xsd"));
        service.addRecentXsdFile(null);
        assertTrue(service.getRecentXsdFiles().isEmpty(),
                "null / non-existing files must not be recorded");
    }

    @Test
    void clearEmptiesTheStore(@TempDir Path tmp) throws Exception {
        service.addRecentXsdFile(
                Files.writeString(tmp.resolve("c.xsd"), "<x/>").toFile());
        assertFalse(service.getRecentXsdFiles().isEmpty());
        service.clearRecentXsdFiles();
        assertTrue(service.getRecentXsdFiles().isEmpty());
    }
}
