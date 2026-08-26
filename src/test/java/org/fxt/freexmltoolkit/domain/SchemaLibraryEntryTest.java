package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLibraryEntryTest {

    @Test
    void userFactoryAssignsIdAndUserSource() {
        SchemaLibraryEntry e = SchemaLibraryEntry.user("urn:x", "/tmp/x.xsd", SchemaKind.XSD, "desc", null);
        assertNotNull(e.id());
        assertEquals(EntrySource.USER, e.source());
        assertTrue(e.enabled());
        assertEquals("XSD|urn:x", e.key());
        assertFalse(e.isRemote());
    }

    @Test
    void remoteDetectionAndWithers() {
        SchemaLibraryEntry e = SchemaLibraryEntry.user("urn:x", "https://example.org/x.xsd", SchemaKind.XSD, "", null);
        assertTrue(e.isRemote());
        SchemaLibraryEntry disabled = e.withEnabled(false);
        assertEquals(e.id(), disabled.id());
        assertFalse(disabled.enabled());
        assertEquals(EntrySource.BUNDLED, e.withSource(EntrySource.BUNDLED).source());
    }

    @Test
    void nullNamespaceBecomesEmptyString() {
        SchemaLibraryEntry e = SchemaLibraryEntry.user(null, "/tmp/x.xsd", SchemaKind.XSD, null, "root");
        assertEquals("", e.namespace());
        assertEquals("", e.description());
        assertEquals("root", e.rootElement());
    }

    @Test
    void catalogRefOfPath() {
        SchemaCatalogRef ref = SchemaCatalogRef.of(java.nio.file.Path.of("/tmp/catalog.xml"));
        assertNotNull(ref.id());
        assertTrue(ref.enabled());
        assertTrue(ref.path().endsWith("catalog.xml"));
        assertFalse(ref.withEnabled(false).enabled());
    }
}
