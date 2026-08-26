package org.fxt.freexmltoolkit.service.xsd;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.ls.LSInput;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaResolverLibraryHookTest {

    @AfterEach void tearDown() { ServiceRegistry.reset(); }

    /** Registers an empty, @TempDir-backed library so tests never fall through to the real
     *  production singleton (~/.freeXmlToolkit/schema-library.json). */
    private static SchemaLibraryServiceImpl emptyLibrary(Path dir) {
        return new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
    }

    @Test
    void importWithUnresolvableLocationIsServedFromLibraryByNamespace(@TempDir Path dir) throws Exception {
        Path types = dir.resolve("lib").resolve("types.xsd");
        Files.createDirectories(types.getParent());
        Files.writeString(types, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:types" elementFormDefault="qualified">
                  <xs:simpleType name="Code"><xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction></xs:simpleType>
                </xs:schema>
                """);
        var svc = emptyLibrary(dir);
        svc.addEntry(SchemaLibraryEntry.user("urn:types", types.toString(), SchemaKind.XSD, "", null));
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);
        LSInput in = resolver.resolveResource("http://www.w3.org/2001/XMLSchema", "urn:types", null,
                "https://nowhere.invalid/types.xsd", dir.toUri().toString());
        assertNotNull(in, "library should serve the import");
        assertEquals(types.toUri().toString(), in.getSystemId());
        assertNotNull(in.getByteStream());
        in.getByteStream().close();
    }

    @Test
    void missWithoutLibraryEntryFallsThroughToExistingBehaviour(@TempDir Path dir) {
        // Isolate from the production singleton: an empty library must still be a miss.
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, emptyLibrary(dir));

        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);
        assertNull(resolver.resolveResource("http://www.w3.org/2001/XMLSchema", "urn:none", null, "missing.xsd", dir.toUri().toString()));
    }

    @Test
    void catalogSystemEntryIsHonoured(@TempDir Path dir) throws Exception {
        Path local = dir.resolve("local.xsd");
        Files.writeString(local, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<system systemId='https://example.org/remote.xsd' uri='local.xsd'/></catalog>");
        var svc = emptyLibrary(dir);
        svc.addCatalog(cat);
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);
        LSInput in = resolver.resolveResource("http://www.w3.org/2001/XMLSchema", null, null,
                "https://example.org/remote.xsd", null);
        assertNotNull(in);
        assertEquals(local.toUri().toString(), in.getSystemId());
        in.getByteStream().close();
    }

    @Test
    void cycleThroughALibraryServedSchemaIsDetected(@TempDir Path dir) throws Exception {
        // main.xsd imports urn:types via an unresolvable location, served by the library from
        // types.xsd (same directory); types.xsd in turn imports back to main.xsd by relative
        // location. The second hop must be rejected as a circular import instead of re-serving
        // main.xsd - this only works if the library alias hooks the served file's own URI into
        // the same parent-chain entry as the original (unresolvable) systemId.
        Path main = dir.resolve("main.xsd");
        Files.writeString(main, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
                """);
        Path types = dir.resolve("types.xsd");
        Files.writeString(types, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:types"/>
                """);

        var svc = emptyLibrary(dir);
        svc.addEntry(SchemaLibraryEntry.user("urn:types", types.toString(), SchemaKind.XSD, "", null));
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);

        // Hop 1: main.xsd imports urn:types - unresolvable systemId, served from the library.
        LSInput hop1 = resolver.resolveResource("http://www.w3.org/2001/XMLSchema", "urn:types", null,
                "https://nowhere.invalid/types.xsd", main.toUri().toString());
        assertNotNull(hop1, "hop 1 should be served from the library");
        String servedBaseUri = hop1.getSystemId();
        hop1.getByteStream().close();

        // Hop 2: types.xsd (the served file) imports main.xsd back by relative location.
        LSInput hop2 = resolver.resolveResource("http://www.w3.org/2001/XMLSchema", null, null,
                "main.xsd", servedBaseUri);
        assertNull(hop2, "types.xsd -> main.xsd closes the cycle and must be rejected");
    }
}
