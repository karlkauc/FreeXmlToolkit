package org.fxt.freexmltoolkit.service.sqf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqfCatalogCacheTest {

    private static final String SQF_SCHEMA = """
            <?xml version="1.0"?>
            <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2"
                        xmlns:sqf="http://www.schematron-quickfix.com/validator/process">
                <sch:pattern>
                    <sch:rule context="a">
                        <sch:assert test="@b" sqf:fix="fixB">b missing</sch:assert>
                        <sqf:fix id="fixB">
                            <sqf:description><sqf:title>Add b</sqf:title></sqf:description>
                            <sqf:add node-type="attribute" target="b">x</sqf:add>
                        </sqf:fix>
                    </sch:rule>
                </sch:pattern>
            </sch:schema>
            """;

    @Test
    void cachesUntilFileChanges(@TempDir Path tempDir) throws Exception {
        Path sch = tempDir.resolve("cached.sch");
        Files.writeString(sch, SQF_SCHEMA);

        SqfCatalog first = SqfCatalogCache.forFile(sch.toFile());
        assertEquals(1, first.fixesByKey().size());
        assertSame(first, SqfCatalogCache.forFile(sch.toFile()), "unchanged file must hit the cache");

        Files.writeString(sch, SQF_SCHEMA.replace("fixB", "fixC"));
        Files.setLastModifiedTime(sch, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        SqfCatalog second = SqfCatalogCache.forFile(sch.toFile());
        assertEquals("fixC", second.fixesByKey().values().iterator().next().id(),
                "changed file must be re-parsed");
    }

    @Test
    void unparseableFileYieldsEmptyCatalog(@TempDir Path tempDir) throws Exception {
        Path sch = tempDir.resolve("garbage.sch");
        Files.writeString(sch, "no xml");
        SqfCatalog catalog = SqfCatalogCache.forFile(sch.toFile());
        assertTrue(catalog.isEmpty(), "parse errors must degrade to an empty catalog");
    }
}
