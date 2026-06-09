package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link SchemaActionRunner#generateXsdFromMultiple} (no UI): infers one
 * XSD from several XML samples (reuses SchemaGenerationEngine multi-document).
 */
class SchemaBatchGenTest {

    @Test
    void generatesOneXsdFromMultipleSamples(@TempDir Path tmp) throws Exception {
        File a = tmp.resolve("a.xml").toFile();
        File b = tmp.resolve("b.xml").toFile();
        Files.writeString(a.toPath(), "<order><id>1</id><customer>A</customer></order>");
        Files.writeString(b.toPath(), "<order><id>2</id><customer>B</customer></order>");

        String xsd = SchemaActionRunner.generateXsdFromMultiple(List.of(a, b));

        assertFalse(xsd.startsWith("ERROR:"), xsd);
        assertTrue(xsd.contains("schema"), "must be an XSD: " + xsd);
        assertTrue(xsd.contains("Source documents: 2"), "must analyse both samples: " + xsd);
        assertTrue(xsd.contains("order") || xsd.contains("customer") || xsd.contains("id"),
                "schema must describe the sampled elements: " + xsd);
    }

    @Test
    void missingFileReportsError() {
        assertTrue(SchemaActionRunner.generateXsdFromMultiple(List.of(new File("/no/such.xml")))
                .startsWith("ERROR:"));
    }
}
