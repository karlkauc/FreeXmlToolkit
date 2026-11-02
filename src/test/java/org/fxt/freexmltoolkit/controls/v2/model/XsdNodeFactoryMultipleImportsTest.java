package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A namespace may be spread over several files, each named by its own {@code xs:import}. */
class XsdNodeFactoryMultipleImportsTest {

    private static final String NS = "urn:shared";

    @TempDir
    Path dir;

    private void part(String file, String typeName) throws Exception {
        Files.writeString(dir.resolve(file), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:shared">
                  <xs:complexType name="%s"/>
                </xs:schema>
                """.formatted(typeName));
    }

    private XsdSchema load(String file, String xsd) throws Exception {
        Path path = dir.resolve(file);
        Files.writeString(path, xsd);
        return new XsdNodeFactory().fromStringWithSchemaFile(xsd, path, dir);
    }

    private static String importing(String targetNamespace, String... files) {
        StringBuilder imports = new StringBuilder();
        for (String file : files) {
            imports.append("<xs:import namespace=\"").append(NS).append("\" schemaLocation=\"")
                    .append(file).append("\"/>\n");
        }
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="%s">
                %s
                </xs:schema>
                """.formatted(targetNamespace, imports);
    }

    private static List<String> typeNames(Collection<XsdSchema> schemas) {
        List<String> names = new ArrayList<>();
        for (XsdSchema schema : schemas) {
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType) {
                    names.add(child.getName());
                }
            }
        }
        return names;
    }

    @Test
    void theMainSchemaKeepsEveryFileOfANamespace() throws Exception {
        part("a.xsd", "A");
        part("b.xsd", "B");

        XsdSchema schema = load("main.xsd", importing("urn:main", "a.xsd", "b.xsd"));

        assertTrue(typeNames(schema.getImportedSchemas().values()).containsAll(List.of("A", "B")));
        assertTrue(schema.getImportedSchemas().containsKey(NS), "the namespace still finds the first file");
        assertEquals(List.of("A"), typeNames(List.of(schema.getImportedSchemas().get(NS))));
    }

    @Test
    void aNestedSchemaKeepsEveryFileToo() throws Exception {
        part("a.xsd", "A");
        part("b.xsd", "B");
        Files.writeString(dir.resolve("middle.xsd"), importing("urn:middle", "a.xsd", "b.xsd"));

        XsdSchema schema = load("main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:main">
                  <xs:import namespace="urn:middle" schemaLocation="middle.xsd"/>
                </xs:schema>
                """);

        assertTrue(typeNames(schema.getImportedSchemas().values()).containsAll(List.of("A", "B")));
    }

    @Test
    void theSameFileTwiceStaysOneEntry() throws Exception {
        part("a.xsd", "A");

        XsdSchema schema = load("main.xsd", importing("urn:main", "a.xsd", "./a.xsd"));

        assertEquals(1, schema.getImportedSchemas().size(), schema.getImportedSchemas().keySet().toString());
    }

    @Test
    void savingKeepsBothImports() throws Exception {
        part("a.xsd", "A");
        part("b.xsd", "B");

        String saved = new XsdSerializer().serialize(load("main.xsd", importing("urn:main", "a.xsd", "b.xsd")));

        assertTrue(saved.contains("a.xsd"), saved);
        assertTrue(saved.contains("b.xsd"), saved);
    }
}
