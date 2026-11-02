package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every local file a schema pulls in is known, however it got there. */
class XsdSchemaDependencyFilesTest {

    private static final String XS = "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

    private static Set<String> dependencies(Path dir, String main) throws Exception {
        Path file = dir.resolve("main.xsd");
        Files.writeString(file, main);
        XsdSchema schema = new XsdNodeFactory().fromStringWithSchemaFile(main, file, dir);
        return schema.getDependencyFiles().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void includesAtAnyDepth(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("middle.xsd"),
                "<xs:schema " + XS + "><xs:include schemaLocation='deep.xsd'/></xs:schema>");
        Files.writeString(dir.resolve("deep.xsd"), "<xs:schema " + XS + "/>");

        assertEquals(Set.of("middle.xsd", "deep.xsd"),
                dependencies(dir, "<xs:schema " + XS + "><xs:include schemaLocation='middle.xsd'/></xs:schema>"));
    }

    @Test
    void anIncludeWithoutComponentsCounts(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("empty.xsd"), "<xs:schema " + XS + "/>");

        assertEquals(Set.of("empty.xsd"),
                dependencies(dir, "<xs:schema " + XS + "><xs:include schemaLocation='empty.xsd'/></xs:schema>"));
    }

    @Test
    void importsTheirIncludesAndNestedImports(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.xsd"), "<xs:schema " + XS + " targetNamespace='urn:a'>"
                + "<xs:include schemaLocation='a-part.xsd'/>"
                + "<xs:import namespace='urn:b' schemaLocation='b.xsd'/></xs:schema>");
        Files.writeString(dir.resolve("a-part.xsd"), "<xs:schema " + XS + " targetNamespace='urn:a'/>");
        Files.writeString(dir.resolve("b.xsd"), "<xs:schema " + XS + " targetNamespace='urn:b'/>");

        assertEquals(Set.of("a.xsd", "a-part.xsd", "b.xsd"), dependencies(dir,
                "<xs:schema " + XS + "><xs:import namespace='urn:a' schemaLocation='a.xsd'/></xs:schema>"));
    }

    @Test
    void redefineAndOverrideTargets(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("r.xsd"), "<xs:schema " + XS + "/>");
        Files.writeString(dir.resolve("o.xsd"), "<xs:schema " + XS + "/>");

        assertEquals(Set.of("r.xsd", "o.xsd"), dependencies(dir, "<xs:schema " + XS + ">"
                + "<xs:redefine schemaLocation='r.xsd'/><xs:override schemaLocation='o.xsd'/></xs:schema>"));
    }

    @Test
    void importCycleEndsAndLeavesOutTheMainFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("other.xsd"), "<xs:schema " + XS + " targetNamespace='urn:o'>"
                + "<xs:import namespace='urn:m' schemaLocation='main.xsd'/></xs:schema>");

        assertEquals(Set.of("other.xsd"), dependencies(dir, "<xs:schema " + XS + " targetNamespace='urn:m'>"
                + "<xs:import namespace='urn:o' schemaLocation='other.xsd'/></xs:schema>"));
    }

    @Test
    void aMissingIncludeIsListedSoThatItsAppearingCanBeNoticed(@TempDir Path dir) throws Exception {
        assertEquals(Set.of("later.xsd"),
                dependencies(dir, "<xs:schema " + XS + "><xs:include schemaLocation='later.xsd'/></xs:schema>"));
    }

    @Test
    void aSchemaWithoutReferencesHasNone(@TempDir Path dir) throws Exception {
        assertTrue(dependencies(dir, "<xs:schema " + XS + "/>").isEmpty());
    }
}
