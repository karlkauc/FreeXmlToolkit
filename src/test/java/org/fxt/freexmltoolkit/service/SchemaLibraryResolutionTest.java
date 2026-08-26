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

class SchemaLibraryResolutionTest {

    static final String CATALOG_NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";
    static final String BUNDLED = """
            {"version":1,"entries":[
              {"namespace":"urn:shared","location":"https://example.org/bundled.xsd","kind":"XSD","description":"bundled"},
              {"namespace":"","location":"https://example.org/noNs.xsd","kind":"XSD","description":"no ns","rootElement":"invoice"}
            ]}""";

    @TempDir Path dir;
    SchemaLibraryServiceImpl svc;
    Path localXsd;

    @BeforeEach
    void setUp() throws Exception {
        localXsd = dir.resolve("local.xsd");
        Files.writeString(localXsd, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:local'/>");
        svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream(BUNDLED.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void userBeatsCatalogBeatsBundled() throws Exception {
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, ("<catalog xmlns='%s'><uri name='urn:shared' uri='local.xsd'/></catalog>").formatted(CATALOG_NS));
        svc.addCatalog(cat);
        // catalog wins over bundled
        assertEquals(localXsd.toUri(), svc.resolveSystemId("urn:shared", null).orElseThrow());
        SchemaLibraryEntry catalogHit = svc.resolveNamespace("urn:shared", SchemaKind.XSD).orElseThrow();
        assertEquals(EntrySource.CATALOG, catalogHit.source());
        assertEquals(localXsd.toString(), catalogHit.location());
        // user wins over both
        svc.addEntry(SchemaLibraryEntry.user("urn:shared", localXsd.toString(), SchemaKind.XSD, "", null));
        assertEquals(EntrySource.USER, svc.resolveNamespace("urn:shared", SchemaKind.XSD).orElseThrow().source());
    }

    @Test
    void disabledEntriesAreSkipped() {
        SchemaLibraryEntry e = svc.addEntry(SchemaLibraryEntry.user("urn:local", localXsd.toString(), SchemaKind.XSD, "", null));
        assertTrue(svc.resolveNamespace("urn:local", SchemaKind.XSD).isPresent());
        svc.setEnabled(e.id(), false);
        assertTrue(svc.resolveNamespace("urn:local", SchemaKind.XSD).isEmpty());
    }

    @Test
    void kindIsPartOfTheKey() {
        svc.addEntry(SchemaLibraryEntry.user("https://example.org/s.json", dir.resolve("s.json").toString(), SchemaKind.JSON_SCHEMA, "", null));
        assertTrue(svc.resolveJsonSchema("https://example.org/s.json").isPresent());
        assertTrue(svc.resolveNamespace("https://example.org/s.json", SchemaKind.XSD).isEmpty());
    }

    @Test
    void rootElementResolution() {
        assertEquals("invoice", svc.resolveByRootElement("invoice").orElseThrow().rootElement());
        assertTrue(svc.resolveByRootElement("order").isEmpty());
    }

    @Test
    void resolveSystemIdMatchesUserLocationAndRelativeBase() {
        svc.addEntry(SchemaLibraryEntry.user("urn:local", localXsd.toString(), SchemaKind.XSD, "", null));
        assertEquals(localXsd.toUri(), svc.resolveSystemId(localXsd.toUri().toString(), null).orElseThrow());
        assertEquals(localXsd.toUri(), svc.resolveSystemId("local.xsd", dir.toUri().toString()).orElseThrow());
        assertTrue(svc.resolveSystemId("other.xsd", dir.toUri().toString()).isEmpty());
    }

    @Test
    void materializeAndStatus() {
        SchemaLibraryEntry ok = svc.addEntry(SchemaLibraryEntry.user("urn:local", localXsd.toString(), SchemaKind.XSD, "", null));
        assertEquals(localXsd, svc.materialize(ok).orElseThrow());
        assertEquals(SchemaEntryStatus.LOCAL_OK, svc.statusOf(ok));

        SchemaLibraryEntry missing = svc.addEntry(SchemaLibraryEntry.user("urn:missing", dir.resolve("nope.xsd").toString(), SchemaKind.XSD, "", null));
        assertTrue(svc.materialize(missing).isEmpty());
        assertEquals(SchemaEntryStatus.LOCAL_MISSING, svc.statusOf(missing));

        SchemaLibraryEntry remote = svc.resolveNamespace("urn:shared", SchemaKind.XSD).orElseThrow();
        assertEquals(SchemaEntryStatus.NOT_DOWNLOADED, svc.statusOf(remote));
        assertTrue(svc.resolveNamespaceToFile("urn:missing", SchemaKind.XSD).isEmpty());
    }

    @Test
    void unparsableCatalogIsReportedNotFatal() throws Exception {
        Path bad = dir.resolve("bad.xml");
        Files.writeString(bad, "<catalog");
        SchemaCatalogRef ref = svc.addCatalog(bad);
        assertTrue(svc.resolveSystemId("urn:x", null).isEmpty());
        assertTrue(svc.catalogErrors().containsKey(ref.id()));
        assertEquals(-1, svc.catalogEntryCount(ref.id()));
    }

    @Test
    void importCatalogPreview() throws Exception {
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, ("<catalog xmlns='%s'><uri name='urn:a' uri='local.xsd'/>"
                + "<system systemId='https://x/y.xsd' uri='local.xsd'/>"
                + "<rewriteSystem systemIdStartString='http://p/' rewritePrefix='q/'/></catalog>").formatted(CATALOG_NS));
        var preview = svc.importCatalog(cat);
        assertEquals(2, preview.size()); // rewrite entries are not importable
        assertTrue(preview.stream().allMatch(e -> e.source() == EntrySource.CATALOG));
        assertTrue(preview.stream().anyMatch(e -> e.namespace().equals("urn:a") && e.location().equals(localXsd.toString())));
    }

    @Test
    void entryFromFilePrefillsXsdAndJson() throws Exception {
        SchemaLibraryEntry x = svc.entryFromFile(localXsd).orElseThrow();
        assertEquals("urn:local", x.namespace());
        assertEquals(SchemaKind.XSD, x.kind());
        Path json = dir.resolve("s.schema.json");
        Files.writeString(json, "{\"$id\":\"https://example.org/s.json\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        SchemaLibraryEntry j = svc.entryFromFile(json).orElseThrow();
        assertEquals("https://example.org/s.json", j.namespace());
        assertEquals(SchemaKind.JSON_SCHEMA, j.kind());
    }
}
