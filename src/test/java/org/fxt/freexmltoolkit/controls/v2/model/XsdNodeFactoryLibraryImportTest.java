package org.fxt.freexmltoolkit.controls.v2.model;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the V2 {@link XsdNodeFactory} consults the Schema Library
 * ({@link SchemaLibraryService}) when an {@code xs:import}'s {@code schemaLocation} cannot be
 * resolved locally (falling back to the import's namespace) and when an {@code xs:include}'s
 * remote {@code schemaLocation} matches a registered OASIS catalog system id.
 */
class XsdNodeFactoryLibraryImportTest {

    @AfterEach void tearDown() { ServiceRegistry.reset(); }

    private SchemaLibraryServiceImpl library(Path dir) {
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);
        return svc;
    }

    @Test
    void importResolvedViaLibraryNamespace(@TempDir Path dir) throws Exception {
        Path types = dir.resolve("elsewhere").resolve("types.xsd");
        Files.createDirectories(types.getParent());
        Files.writeString(types, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:types">
                  <xs:simpleType name="Code"><xs:restriction base="xs:string"/></xs:simpleType>
                </xs:schema>""");
        library(dir).addEntry(SchemaLibraryEntry.user("urn:types", types.toString(), SchemaKind.XSD, "", null));

        Path main = dir.resolve("main.xsd");
        Files.writeString(main, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:types">
                  <xs:import namespace="urn:types" schemaLocation="missing/types.xsd"/>
                  <xs:element name="root" type="t:Code"/>
                </xs:schema>""");
        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(false);
        XsdSchema schema = factory.fromFile(main);
        XsdImport imp = (XsdImport) schema.getChildren().stream().filter(n -> n instanceof XsdImport).findFirst().orElseThrow();
        assertTrue(imp.isResolved(), "import should be resolved through the library");
        assertNotNull(schema.getImportedSchemas().get("urn:types"), "imported schema should be registered under its namespace");
    }

    @Test
    void includeResolvedViaCatalogSystemId(@TempDir Path dir) throws Exception {
        Path part = dir.resolve("parts").resolve("part.xsd");
        Files.createDirectories(part.getParent());
        Files.writeString(part, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="fromInclude" type="xs:string"/>
                </xs:schema>""");
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<system systemId='https://example.org/part.xsd' uri='parts/part.xsd'/></catalog>");
        library(dir).addCatalog(cat);

        Path main = dir.resolve("main.xsd");
        Files.writeString(main, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="https://example.org/part.xsd"/>
                </xs:schema>""");
        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(false);
        XsdSchema schema = factory.fromFile(main);
        assertTrue(schema.getChildren().stream().anyMatch(n -> n instanceof XsdElement e && "fromInclude".equals(e.getName())));
    }
}
