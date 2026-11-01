package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.UnresolvedSchemaReferences.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnresolvedSchemaReferencesTest {

    private static final String XS = "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

    private static XsdSchema load(Path file) throws Exception {
        return new XsdNodeFactory().fromStringWithSchemaFile(Files.readString(file), file, file.getParent());
    }

    @Test
    void missingIncludeIsReported(@TempDir Path dir) throws Exception {
        Path main = dir.resolve("main.xsd");
        Files.writeString(main, "<xs:schema " + XS + "><xs:include schemaLocation=\"gone.xsd\"/></xs:schema>");

        List<Entry> entries = UnresolvedSchemaReferences.of(load(main));

        assertEquals(1, entries.size());
        assertEquals("gone.xsd", entries.getFirst().location());
        assertTrue(entries.getFirst().error().contains("File not found"), entries.getFirst().error());
    }

    @Test
    void loadedIncludeIsNotReported(@TempDir Path dir) throws Exception {
        Path main = dir.resolve("main.xsd");
        Files.writeString(dir.resolve("part.xsd"), "<xs:schema " + XS + "/>");
        Files.writeString(main, "<xs:schema " + XS + "><xs:include schemaLocation=\"part.xsd\"/></xs:schema>");

        assertEquals(List.of(), UnresolvedSchemaReferences.of(load(main)));
    }

    @Test
    void includeNestedInAnIncludeIsReported(@TempDir Path dir) throws Exception {
        Path main = dir.resolve("main.xsd");
        Files.writeString(dir.resolve("part.xsd"), "<xs:schema " + XS + "><xs:include schemaLocation=\"gone.xsd\"/></xs:schema>");
        Files.writeString(main, "<xs:schema " + XS + "><xs:include schemaLocation=\"part.xsd\"/></xs:schema>");

        assertEquals(List.of("gone.xsd"),
                UnresolvedSchemaReferences.of(load(main)).stream().map(Entry::location).toList());
    }

    @Test
    void circularIncludesAreNotAFailure(@TempDir Path dir) throws Exception {
        Path main = dir.resolve("main.xsd");
        Files.writeString(dir.resolve("a.xsd"), "<xs:schema " + XS + "><xs:include schemaLocation=\"b.xsd\"/></xs:schema>");
        Files.writeString(dir.resolve("b.xsd"), "<xs:schema " + XS + "><xs:include schemaLocation=\"a.xsd\"/></xs:schema>");
        Files.writeString(main, "<xs:schema " + XS + "><xs:include schemaLocation=\"a.xsd\"/></xs:schema>");

        assertEquals(List.of(), UnresolvedSchemaReferences.of(load(main)));
    }

    @Test
    void includeInsideAnImportedSchemaIsReported(@TempDir Path dir) throws Exception {
        Path main = dir.resolve("main.xsd");
        Files.writeString(dir.resolve("other.xsd"), "<xs:schema " + XS + " targetNamespace=\"urn:o\">"
                + "<xs:include schemaLocation=\"gone.xsd\"/></xs:schema>");
        Files.writeString(main, "<xs:schema " + XS + "><xs:import namespace=\"urn:o\" schemaLocation=\"other.xsd\"/></xs:schema>");

        assertEquals(List.of("gone.xsd"),
                UnresolvedSchemaReferences.of(load(main)).stream().map(Entry::location).toList());
    }

    @Test
    void schemaWithoutAFileCannotResolveItsIncludes() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(
                "<xs:schema " + XS + "><xs:include schemaLocation=\"part.xsd\"/></xs:schema>");

        assertEquals(List.of("part.xsd"),
                UnresolvedSchemaReferences.of(schema).stream().map(Entry::location).toList());
    }

    @Test
    void messageNamesTheLocations() {
        Entry first = new Entry("a.xsd", "File not found: /x/a.xsd");

        assertTrue(UnresolvedSchemaReferences.message(List.of(first)).contains("a.xsd: File not found"));
        assertTrue(UnresolvedSchemaReferences.message(List.of(first, new Entry("b.xsd", "e")))
                .contains("2 referenced schemas: a.xsd, b.xsd"));
    }
}
