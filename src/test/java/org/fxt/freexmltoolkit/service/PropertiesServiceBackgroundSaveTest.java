package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Settings are written in the background, coalesced ({@link PropertiesServiceImpl#saveProperties}):
 * a setter returns at once, the new value is visible immediately, and {@link PropertiesService#flush()}
 * (called on exit) puts it on disk. The test run's settings file lives under build/ (fxt.properties.file).
 */
class PropertiesServiceBackgroundSaveTest {

    private static Properties readFile() throws Exception {
        Properties raw = new Properties();
        Path file = Path.of(System.getProperty("fxt.properties.file", ""));
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            raw.load(in);
        }
        return raw;
    }

    @Test
    void aSetterIsVisibleAtOnceAndOnDiskAfterFlush() throws Exception {
        PropertiesService service = PropertiesServiceImpl.getInstance();
        String value = "v" + System.nanoTime();

        long start = System.nanoTime();
        for (int i = 0; i < 30; i++) {
            service.set("backgroundSaveTest.key" + i, value); // a burst, like the Settings page saving
        }
        long burstMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(value, service.get("backgroundSaveTest.key29"), "the new value is visible immediately");
        assertEquals(value, service.loadProperties().getProperty("backgroundSaveTest.key0"),
                "a load while the save is queued returns the newer in-memory settings");
        assertTrue(burstMs < 500, "30 setters took " + burstMs + " ms: they must not each write the file");

        service.flush();
        Properties onDisk = readFile();
        assertEquals(value, onDisk.getProperty("backgroundSaveTest.key0"));
        assertEquals(value, onDisk.getProperty("backgroundSaveTest.key29"));
        assertFalse(Files.exists(Path.of(System.getProperty("fxt.properties.file") + ".tmp")),
                "the file is replaced atomically, no temporary file is left behind");
    }
}
