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

    @Test
    void importWithUnresolvableLocationIsServedFromLibraryByNamespace(@TempDir Path dir) throws Exception {
        Path types = dir.resolve("lib").resolve("types.xsd");
        Files.createDirectories(types.getParent());
        Files.writeString(types, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:types" elementFormDefault="qualified">
                  <xs:simpleType name="Code"><xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction></xs:simpleType>
                </xs:schema>
                """);
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
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
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
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
}
