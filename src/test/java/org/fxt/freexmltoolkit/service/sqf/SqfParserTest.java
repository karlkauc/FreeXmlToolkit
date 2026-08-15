package org.fxt.freexmltoolkit.service.sqf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.service.sqf.SqfModel.AssertKey;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfAdd;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfDelete;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfFix;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfReplace;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfStringReplace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqfParserTest {

    private static File fixture(String name) {
        return new File("src/test/resources/sqf/" + name);
    }

    private static SqfFix fixById(SqfCatalog catalog, String id) {
        return catalog.fixesByKey().values().stream()
                .filter(f -> id.equals(f.id()))
                .findFirst().orElseThrow(() -> new AssertionError("fix not found: " + id));
    }

    @Test
    void parsesLocalAndGlobalFixes() throws Exception {
        SqfCatalog catalog = SqfParser.parse(fixture("person-fixes.sch"));

        assertEquals(7, catalog.fixesByKey().size(), "5 rule-local + 2 global fixes");

        SqfFix addId = fixById(catalog, "addId");
        assertEquals("Add generated id attribute", addId.title());
        assertEquals(List.of("Adds an id derived from the element position."), addId.paragraphs());
        assertEquals(1, addId.activities().size());
        SqfAdd add = assertInstanceOf(SqfAdd.class, addId.activities().get(0));
        assertEquals("attribute", add.nodeType());
        assertEquals("id", add.target());
        assertNotNull(add.content(), "activity content must be captured");
        assertTrue(addId.letsInScope().stream().anyMatch(l -> l.name().equals("generated-id")),
                "rule-level let must be in scope");

        assertInstanceOf(SqfDelete.class, fixById(catalog, "removeDeprecated").activities().get(0));
        assertInstanceOf(SqfReplace.class, fixById(catalog, "renameNameToFullName").activities().get(0));
        SqfStringReplace sr = assertInstanceOf(SqfStringReplace.class,
                fixById(catalog, "normalizePhone").activities().get(0));
        assertEquals("^00", sr.regex());
        assertEquals("phone/text()", sr.match());
    }

    @Test
    void resolvesAssertReferences() throws Exception {
        SqfCatalog catalog = SqfParser.parse(fixture("person-fixes.sch"));

        List<String> byId = catalog.fixKeysByAssertId().get("person-needs-id");
        assertNotNull(byId, "assert with @id must be indexed by id");
        assertEquals(1, byId.size());
        assertEquals("addId", catalog.fixesByKey().get(byId.get(0)).id());

        // identical @test in two rules is disambiguated by the rule context
        List<String> person = catalog.fixKeysByAssert().get(new AssertKey("person", "@id"));
        assertNotNull(person);
        assertEquals("addId", catalog.fixesByKey().get(person.get(0)).id());
        List<String> company = catalog.fixKeysByAssert().get(new AssertKey("company", "@id"));
        assertNotNull(company);
        assertEquals("addCompanyMarker", catalog.fixesByKey().get(company.get(0)).id());
    }

    @Test
    void defaultFixIsOrderedFirst() throws Exception {
        SqfCatalog catalog = SqfParser.parse(fixture("person-fixes.sch"));

        List<String> keys = catalog.fixKeysByAssert().get(new AssertKey("person", "note or fullName"));
        assertNotNull(keys);
        assertEquals(2, keys.size());
        assertEquals("addEmptyNote", catalog.fixesByKey().get(keys.get(0)).id(),
                "sqf:default-fix must come first");
        assertEquals("addNote", catalog.fixesByKey().get(keys.get(1)).id());
    }

    @Test
    void resolvesSchInclude() throws Exception {
        SqfCatalog catalog = SqfParser.parse(fixture("includes/main.sch"));

        assertEquals(2, catalog.fixesByKey().size());
        List<String> code = catalog.fixKeysByAssert().get(new AssertKey("item", "@code"));
        assertNotNull(code, "assert from the included pattern must be indexed");
        assertEquals("addCode", catalog.fixesByKey().get(code.get(0)).id());
        List<String> legacy = catalog.fixKeysByAssert().get(new AssertKey("item", "legacy"));
        assertNotNull(legacy, "report referencing a global fix must resolve");
        assertEquals("globalRemoveLegacy", catalog.fixesByKey().get(legacy.get(0)).id());
    }

    @Test
    void brokenSqfDegradesGracefully() throws Exception {
        SqfCatalog catalog = SqfParser.parse(fixture("broken-sqf.sch"));

        assertEquals(0, catalog.fixesByKey().size(), "fix without @id is skipped");
        List<String> keys = catalog.fixKeysByAssert().get(new AssertKey("person", "@id"));
        assertTrue(keys == null || keys.isEmpty(), "unknown fix references yield no fixes");
    }

    @Test
    void nonSqfSchematronYieldsEmptyCatalog(@TempDir Path tempDir) throws Exception {
        Path sch = tempDir.resolve("plain.sch");
        Files.writeString(sch, """
                <?xml version="1.0"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern><rule context="a"><assert test="@b">b!</assert></rule></pattern>
                </schema>
                """);
        SqfCatalog catalog = SqfParser.parse(sch.toFile());
        assertTrue(catalog.isEmpty());
    }

    @Test
    void nonXmlFileThrows(@TempDir Path tempDir) throws IOException {
        Path sch = tempDir.resolve("garbage.sch");
        Files.writeString(sch, "not xml at all");
        assertThrows(SqfParseException.class, () -> SqfParser.parse(sch.toFile()));
    }
}
