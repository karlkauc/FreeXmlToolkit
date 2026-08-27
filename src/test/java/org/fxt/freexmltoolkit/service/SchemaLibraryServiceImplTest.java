package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLibraryServiceImplTest {

    static final String BUNDLED = """
            {"version":1,"entries":[
              {"namespace":"http://www.w3.org/XML/1998/namespace","location":"https://www.w3.org/2001/xml.xsd",
               "kind":"XSD","description":"XML namespace"},
              {"namespace":"urn:bundled:b","location":"https://example.org/b.xsd","kind":"XSD","description":"B"}
            ]}""";

    @TempDir Path dir;
    SchemaResourceCache cache;
    SchemaLibraryServiceImpl svc;

    @BeforeEach
    void setUp() {
        cache = new SchemaResourceCache(dir.resolve("cache"));
        svc = newService();
    }

    /** Reloads from disk; {@code awaitSave()} first because the writer runs off-thread (I6). */
    SchemaLibraryServiceImpl newService() {
        if (svc != null) svc.awaitSave();
        return new SchemaLibraryServiceImpl(dir.resolve("schema-library.json"), cache,
                () -> new ByteArrayInputStream(BUNDLED.getBytes(StandardCharsets.UTF_8)));
    }

    @org.junit.jupiter.api.AfterEach
    void flushWriter() {
        // The library persists on a background writer thread; wait for it so the @TempDir
        // cleanup does not race with a file still being written.
        if (svc != null) svc.awaitSave();
    }

    @Test
    void startsWithBundledEntriesOnly() {
        assertEquals(2, svc.getEntries().size());
        assertTrue(svc.getEntries().stream().allMatch(e -> e.source() == EntrySource.BUNDLED));
        assertFalse(Files.exists(svc.getStorageFile()));
    }

    @Test
    void addPersistsAndReloads() throws Exception {
        SchemaLibraryEntry added = svc.addEntry(SchemaLibraryEntry.user("urn:u", "/tmp/u.xsd", SchemaKind.XSD, "U", null));
        assertEquals(EntrySource.USER, added.source());
        svc.awaitSave();
        assertTrue(Files.exists(svc.getStorageFile()));

        SchemaLibraryServiceImpl reloaded = newService();
        assertEquals(3, reloaded.getEntries().size());
        assertTrue(reloaded.getEntries().stream().anyMatch(e -> e.id().equals(added.id())));
    }

    @Test
    void userEntryOverridesBundledWithSameKey() {
        svc.addEntry(SchemaLibraryEntry.user("urn:bundled:b", "/tmp/local-b.xsd", SchemaKind.XSD, "mine", null));
        var matches = svc.getEntries().stream().filter(e -> e.namespace().equals("urn:bundled:b")).toList();
        assertEquals(1, matches.size());
        assertEquals(EntrySource.USER, matches.getFirst().source());
        assertEquals(2, svc.getEntries().size());
    }

    @Test
    void bundledCanBeDisabledButNotRemoved() {
        SchemaLibraryEntry b = svc.getEntries().stream().filter(e -> e.namespace().equals("urn:bundled:b")).findFirst().orElseThrow();
        assertFalse(svc.removeEntry(b.id()));
        assertTrue(svc.setEnabled(b.id(), false));
        assertFalse(svc.getEntries().stream().filter(e -> e.id().equals(b.id())).findFirst().orElseThrow().enabled());
        assertFalse(newService().getEntries().stream().filter(e -> e.namespace().equals("urn:bundled:b")).findFirst().orElseThrow().enabled());
    }

    @Test
    void updateAndRemoveUserEntry() {
        SchemaLibraryEntry a = svc.addEntry(SchemaLibraryEntry.user("urn:a", "/tmp/a.xsd", SchemaKind.XSD, "", null));
        SchemaLibraryEntry changed = new SchemaLibraryEntry(a.id(), "urn:a2", a.location(), a.kind(), a.source(), true, "x", null);
        assertTrue(svc.updateEntry(changed));
        assertEquals("urn:a2", svc.getEntries().stream().filter(e -> e.id().equals(a.id())).findFirst().orElseThrow().namespace());
        assertTrue(svc.removeEntry(a.id()));
        assertFalse(svc.updateEntry(changed));
    }

    @Test
    void catalogsRoundTrip() throws Exception {
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'/>");
        SchemaCatalogRef ref = svc.addCatalog(cat);
        assertEquals(1, svc.getCatalogs().size());
        assertEquals(1, newService().getCatalogs().size());
        assertTrue(svc.setCatalogEnabled(ref.id(), false));
        assertFalse(svc.getCatalogs().getFirst().enabled());
        assertTrue(svc.removeCatalog(ref.id()));
        assertTrue(svc.getCatalogs().isEmpty());
    }

    @Test
    void corruptStorageIsBackedUpAndIgnored() throws Exception {
        Files.writeString(dir.resolve("schema-library.json"), "{not json");
        SchemaLibraryServiceImpl s = newService();
        assertEquals(2, s.getEntries().size());
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("schema-library.json.broken-")));
        }
    }

    @Test
    void rejectsUnsafeUrls() {
        assertThrows(IllegalArgumentException.class, () ->
                svc.addEntry(SchemaLibraryEntry.user("urn:x", "http://127.0.0.1/x.xsd", SchemaKind.XSD, "", null)));
    }
}
