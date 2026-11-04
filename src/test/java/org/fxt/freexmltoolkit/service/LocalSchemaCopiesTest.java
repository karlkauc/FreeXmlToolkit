package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Local copies of downloaded schemas next to the referencing schema.
 */
class LocalSchemaCopiesTest {

    private String previous;

    @BeforeEach
    void enable() {
        previous = System.getProperty(LocalSchemaCopies.PROPERTY);
        System.setProperty(LocalSchemaCopies.PROPERTY, "true");
    }

    @AfterEach
    void restore() {
        if (previous == null) {
            System.clearProperty(LocalSchemaCopies.PROPERTY);
        } else {
            System.setProperty(LocalSchemaCopies.PROPERTY, previous);
        }
    }

    @Test
    void targetsKeepTheOriginalNameAndLayout(@TempDir Path dir) {
        assertEquals(Optional.of(dir.resolve("xmldsig-core-schema.xsd")),
                LocalSchemaCopies.target(dir, "xmldsig-core-schema.xsd"));
        assertEquals(Optional.of(dir.resolve("common/types.xsd")), LocalSchemaCopies.target(dir, "common/types.xsd"));
        assertEquals(Optional.of(dir.getParent().resolve("x.xsd")), LocalSchemaCopies.target(dir, "../x.xsd"));
        assertEquals(Optional.of(dir.resolve("xml.xsd")),
                LocalSchemaCopies.target(dir, "http://www.w3.org/2001/xml.xsd"));
        assertEquals(Optional.of(dir.resolve("schema.xsd")),
                LocalSchemaCopies.target(dir, "https://example.com/get/schema?version=2"));
        assertTrue(LocalSchemaCopies.target(dir, "file:///tmp/a.xsd").isEmpty());
        assertTrue(LocalSchemaCopies.target(null, "a.xsd").isEmpty());
        assertTrue(LocalSchemaCopies.target(dir, " ").isEmpty());
    }

    @Test
    void neverWritesIntoTheApplicationCache() {
        Path cache = Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "cache", "schemas");
        assertTrue(LocalSchemaCopies.target(cache, "nested.xsd").isEmpty());
    }

    @Test
    void storeCopiesOnceAndNeverOverwrites(@TempDir Path dir) throws Exception {
        Path downloaded = Files.writeString(dir.resolve("abc123.xsd"), "<xs:schema/>");
        Path target = dir.resolve("project/sub/types.xsd");
        assertEquals(Optional.of(target), LocalSchemaCopies.store(target, downloaded));
        assertEquals("<xs:schema/>", Files.readString(target));

        Files.writeString(target, "edited by the user");
        assertEquals(Optional.of(target), LocalSchemaCopies.store(target, downloaded));
        assertEquals("edited by the user", Files.readString(target));
    }

    @Test
    void readOnlyFolderKeepsTheCacheFile(@TempDir Path dir) throws Exception {
        Path downloaded = Files.writeString(dir.resolve("abc.xsd"), "<xs:schema/>");
        Path folder = Files.createDirectory(dir.resolve("ro"));
        assertTrue(folder.toFile().setWritable(false));
        try {
            assertEquals(downloaded, LocalSchemaCopies.localize(folder, "x.xsd", downloaded));
        } finally {
            folder.toFile().setWritable(true);
        }
    }

    @Test
    void anImportNeedsTheExpectedNamespace(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("xml.xsd"),
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:a'/>");
        assertTrue(LocalSchemaCopies.existing(dir, "http://x.org/xml.xsd", "urn:a").isPresent());
        assertTrue(LocalSchemaCopies.existing(dir, "http://x.org/xml.xsd", "urn:b").isEmpty());
        assertTrue(LocalSchemaCopies.existing(dir, "xml.xsd", null).isPresent());
    }

    @Test
    void switchedOff(@TempDir Path dir) throws Exception {
        System.setProperty(LocalSchemaCopies.PROPERTY, "false");
        Path downloaded = Files.writeString(dir.resolve("abc.xsd"), "<xs:schema/>");
        assertEquals(downloaded, LocalSchemaCopies.localize(dir, "x.xsd", downloaded));
        assertFalse(Files.exists(dir.resolve("x.xsd")));
    }
}
