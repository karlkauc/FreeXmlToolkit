package org.fxt.freexmltoolkit.service.ai.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaSliceTest {

    @TempDir
    static Path temp;

    private static XsdSchema schema;

    @BeforeAll
    static void load() throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        Files.writeString(temp.resolve("main.xsd"), org.fxt.freexmltoolkit.service.ai.tool.SchemaToolsTestSchemas.MAIN);
        Files.writeString(temp.resolve("other.xsd"), org.fxt.freexmltoolkit.service.ai.tool.SchemaToolsTestSchemas.OTHER);
        schema = new XsdNodeFactory().fromFile(temp.resolve("main.xsd"));
    }

    @Test
    void compactTextOfAnEnumeratedElement() {
        String text = SchemaSlice.describe(schema, List.of("Order", "Status"), null).orElseThrow().toText();
        assertTrue(text.startsWith("Status: StatusType (token) [0..1]"), text);
        assertTrue(text.contains("enumeration: OPEN | CLOSED"), text);
    }

    @Test
    void compactTextOfAComplexElement() {
        String text = SchemaSlice.describe(schema, List.of("Order"), null).orElseThrow().toText();
        assertTrue(text.contains("children: Id [1..1], Status [0..1], Note [0..*], Folder [0..1], (Cash | Card)"), text);
        assertTrue(text.contains("@version: xs:string (required)"), text);
        assertTrue(text.contains("[en] An order"), text);
    }

    @Test
    void nearestKnownPath() {
        assertEquals(List.of("Order", "Folder"),
                SchemaSlice.nearestKnownPath(schema, List.of("Order", "Folder", "Nope", "X")));
        assertEquals(List.of(), SchemaSlice.nearestKnownPath(schema, List.of("Nope")));
    }
}
