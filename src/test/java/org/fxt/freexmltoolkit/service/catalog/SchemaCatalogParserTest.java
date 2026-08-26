package org.fxt.freexmltoolkit.service.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaCatalogParserTest {

    private static final String NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";

    private Path write(Path dir, String name, String body) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, body);
        return f;
    }

    @Test
    void parsesSystemUriPublicAndRewriteEntries(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("x3d.xsd"), "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
        Path cat = write(dir, "catalog.xml", """
                <catalog xmlns="%s">
                  <system systemId="https://www.web3d.org/specifications/x3d-4.0.xsd" uri="x3d.xsd"/>
                  <uri name="http://www.web3d.org/specifications/x3d-namespace" uri="x3d.xsd"/>
                  <public publicId="-//Web3D//DTD X3D 4.0//EN" uri="x3d.xsd"/>
                  <rewriteSystem systemIdStartString="http://example.org/schemas/" rewritePrefix="schemas/"/>
                  <rewriteURI uriStartString="http://example.org/uris/" rewritePrefix="file:///opt/uris/"/>
                </catalog>
                """.formatted(NS));
        ParsedCatalog c = SchemaCatalogParser.parse(cat);

        String expected = dir.resolve("x3d.xsd").toUri().toString();
        assertEquals(expected, c.matchSystem("https://www.web3d.org/specifications/x3d-4.0.xsd").orElseThrow());
        assertEquals(expected, c.matchUri("http://www.web3d.org/specifications/x3d-namespace").orElseThrow());
        assertEquals(expected, c.matchPublic("-//Web3D//DTD X3D 4.0//EN").orElseThrow());
        assertEquals(dir.resolve("schemas/a/b.xsd").toUri().toString(),
                c.matchSystem("http://example.org/schemas/a/b.xsd").orElseThrow());
        assertEquals("file:///opt/uris/q.xsd", c.matchUri("http://example.org/uris/q.xsd").orElseThrow());
        assertTrue(c.matchSystem("http://nowhere/").isEmpty());
        assertEquals(5, c.entryCount());
    }

    @Test
    void longestRewritePrefixWins(@TempDir Path dir) throws Exception {
        Path cat = write(dir, "catalog.xml", """
                <catalog xmlns="%s">
                  <rewriteSystem systemIdStartString="http://e.org/" rewritePrefix="file:///short/"/>
                  <rewriteSystem systemIdStartString="http://e.org/deep/" rewritePrefix="file:///long/"/>
                </catalog>
                """.formatted(NS));
        assertEquals("file:///long/x.xsd",
                SchemaCatalogParser.parse(cat).matchSystem("http://e.org/deep/x.xsd").orElseThrow());
    }

    @Test
    void followsNextCatalogAndSurvivesCycles(@TempDir Path dir) throws Exception {
        Path a = write(dir, "a.xml", """
                <catalog xmlns="%s">
                  <nextCatalog catalog="b.xml"/>
                </catalog>
                """.formatted(NS));
        write(dir, "b.xml", """
                <catalog xmlns="%s">
                  <system systemId="urn:b" uri="b.xsd"/>
                  <nextCatalog catalog="a.xml"/>
                </catalog>
                """.formatted(NS));
        ParsedCatalog c = SchemaCatalogParser.parse(a);
        assertEquals(dir.resolve("b.xsd").toUri().toString(), c.matchSystem("urn:b").orElseThrow());
        assertEquals(0, c.entryCount());
        assertEquals(1, c.allEntries().size());
    }

    @Test
    void honoursXmlBase(@TempDir Path dir) throws Exception {
        Path cat = write(dir, "catalog.xml", """
                <catalog xmlns="%s" xml:base="sub/">
                  <system systemId="urn:s" uri="s.xsd"/>
                </catalog>
                """.formatted(NS));
        assertEquals(dir.resolve("sub/s.xsd").toUri().toString(),
                SchemaCatalogParser.parse(cat).matchSystem("urn:s").orElseThrow());
    }

    @Test
    void unparsableCatalogThrows(@TempDir Path dir) throws Exception {
        Path cat = write(dir, "catalog.xml", "<catalog><system");
        assertThrows(SchemaCatalogParser.CatalogParseException.class, () -> SchemaCatalogParser.parse(cat));
    }

    @Test
    void missingNextCatalogIsIgnored(@TempDir Path dir) throws Exception {
        Path cat = write(dir, "catalog.xml", """
                <catalog xmlns="%s"><nextCatalog catalog="missing.xml"/><uri name="u" uri="u.xsd"/></catalog>
                """.formatted(NS));
        assertEquals(1, SchemaCatalogParser.parse(cat).entryCount());
    }
}
