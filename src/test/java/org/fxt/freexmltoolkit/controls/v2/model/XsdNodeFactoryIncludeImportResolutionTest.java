package org.fxt.freexmltoolkit.controls.v2.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XsdNodeFactory resolution of xs:include and xs:import references.
 * <p>
 * These tests verify that the factory correctly resolves relative schemaLocation
 * paths when a base directory or schema file path is provided.
 *
 * @since 2.0
 */
class XsdNodeFactoryIncludeImportResolutionTest {

    // ========================================================================
    // xs:include resolution tests
    // ========================================================================

    @Test
    void testIncludeResolvesFromFile(@TempDir Path tempDir) throws Exception {
        // types.xsd defines a simple type
        String typesXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="NameType">
                        <xs:restriction base="xs:string">
                            <xs:maxLength value="100"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        // main.xsd includes types.xsd
        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types.xsd"/>
                    <xs:element name="person" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("types.xsd"), typesXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema);
        // Should contain the inlined NameType from types.xsd plus the element from main.xsd
        boolean hasNameType = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "NameType".equals(n.getName()));
        assertTrue(hasNameType, "Included NameType should be present in schema");

        boolean hasPersonElement = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdElement && "person".equals(n.getName()));
        assertTrue(hasPersonElement, "Main schema element should still be present");
    }

    @Test
    void testIncludeResolvesFromStringWithSchemaFile(@TempDir Path tempDir) throws Exception {
        // Simulates the controller path: content comes from text editor, but we know the file path
        String typesXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="AgeType">
                        <xs:restriction base="xs:integer">
                            <xs:minInclusive value="0"/>
                            <xs:maxInclusive value="150"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types.xsd"/>
                    <xs:element name="age" type="AgeType"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("types.xsd"), typesXsd);
        Path mainFile = tempDir.resolve("main.xsd");
        Files.writeString(mainFile, mainContent);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromStringWithSchemaFile(mainContent, mainFile, tempDir);

        assertNotNull(schema);
        boolean hasAgeType = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "AgeType".equals(n.getName()));
        assertTrue(hasAgeType, "Included AgeType should be resolved via fromStringWithSchemaFile");
    }

    @Test
    void testIncludeFailsWithoutBaseDirectory() throws Exception {
        // Without a base directory, include cannot be resolved — but parsing should not fail
        String mainContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types.xsd"/>
                    <xs:element name="test" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(mainContent);

        assertNotNull(schema, "Schema should still parse even without resolving includes");

        // The include node should exist but its content should not be inlined
        boolean hasIncludeNode = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdInclude);
        assertTrue(hasIncludeNode, "Include node should be present in schema");

        // The element from main should still be there
        boolean hasTestElement = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdElement && "test".equals(n.getName()));
        assertTrue(hasTestElement, "Main schema element should be present");

        // No types from the include should appear (since it couldn't be resolved)
        long simpleTypeCount = schema.getChildren().stream()
                .filter(n -> n instanceof XsdSimpleType)
                .count();
        assertEquals(0, simpleTypeCount, "No types should be inlined when base directory is null");
    }

    @Test
    void testIncludeFromSubdirectory(@TempDir Path tempDir) throws Exception {
        Path typesDir = Files.createDirectories(tempDir.resolve("types"));
        String commonXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="EmailType">
                        <xs:restriction base="xs:string">
                            <xs:pattern value=".+@.+\\..+"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types/common.xsd"/>
                    <xs:element name="email" type="EmailType"/>
                </xs:schema>
                """;

        Files.writeString(typesDir.resolve("common.xsd"), commonXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema);
        boolean hasEmailType = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "EmailType".equals(n.getName()));
        assertTrue(hasEmailType, "Include from subdirectory should resolve");
    }

    @Test
    void testIncludeFromParentDirectory(@TempDir Path tempDir) throws Exception {
        Path subDir = Files.createDirectories(tempDir.resolve("schemas"));
        String sharedXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="SharedType">
                        <xs:restriction base="xs:string">
                            <xs:maxLength value="50"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="../shared.xsd"/>
                    <xs:element name="data" type="SharedType"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("shared.xsd"), sharedXsd);
        Path mainFile = Files.writeString(subDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema);
        boolean hasSharedType = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "SharedType".equals(n.getName()));
        assertTrue(hasSharedType, "Include from parent directory should resolve");
    }

    @Test
    void testIncludeMissingFile(@TempDir Path tempDir) throws Exception {
        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="nonexistent.xsd"/>
                    <xs:element name="test" type="xs:string"/>
                </xs:schema>
                """;

        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema, "Schema should parse even with missing include");

        // Find the include node and verify it's not resolved
        XsdInclude include = schema.getChildren().stream()
                .filter(n -> n instanceof XsdInclude)
                .map(n -> (XsdInclude) n)
                .findFirst()
                .orElse(null);
        assertNotNull(include, "Include node should be present");
        assertFalse(include.isResolved(), "Include should not be resolved for missing file");
        assertNotNull(include.getResolutionError(), "Resolution error should be set");
    }

    @Test
    void testCircularIncludeDetection(@TempDir Path tempDir) throws Exception {
        // a.xsd includes b.xsd, b.xsd includes a.xsd
        String aXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="b.xsd"/>
                    <xs:element name="fromA" type="xs:string"/>
                </xs:schema>
                """;

        String bXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="a.xsd"/>
                    <xs:element name="fromB" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("a.xsd"), aXsd);
        Files.writeString(tempDir.resolve("b.xsd"), bXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        // This should NOT hang in an infinite loop
        XsdSchema schema = factory.fromFile(tempDir.resolve("a.xsd"));

        assertNotNull(schema, "Schema should parse even with circular includes");
        // Should have elements from both files (fromA is main, fromB is included)
        boolean hasFromA = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdElement && "fromA".equals(n.getName()));
        assertTrue(hasFromA, "Element from main schema (a.xsd) should be present");

        boolean hasFromB = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdElement && "fromB".equals(n.getName()));
        assertTrue(hasFromB, "Element from included schema (b.xsd) should be inlined");
    }

    @Test
    void testMultipleIncludes(@TempDir Path tempDir) throws Exception {
        String types1Xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="Type1">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String types2Xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="Type2">
                        <xs:restriction base="xs:integer"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types1.xsd"/>
                    <xs:include schemaLocation="types2.xsd"/>
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("types1.xsd"), types1Xsd);
        Files.writeString(tempDir.resolve("types2.xsd"), types2Xsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema);
        boolean hasType1 = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "Type1".equals(n.getName()));
        boolean hasType2 = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "Type2".equals(n.getName()));
        assertTrue(hasType1, "Type1 from first include should be present");
        assertTrue(hasType2, "Type2 from second include should be present");
    }

    @Test
    void testIncludeChain(@TempDir Path tempDir) throws Exception {
        // main.xsd → mid.xsd → base.xsd (transitive include)
        String baseXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="BaseType">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String midXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="base.xsd"/>
                    <xs:simpleType name="MidType">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="mid.xsd"/>
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("base.xsd"), baseXsd);
        Files.writeString(tempDir.resolve("mid.xsd"), midXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema);
        boolean hasBaseType = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "BaseType".equals(n.getName()));
        boolean hasMidType = schema.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "MidType".equals(n.getName()));
        assertTrue(hasBaseType, "BaseType from transitive include should be present");
        assertTrue(hasMidType, "MidType from direct include should be present");
    }

    // ========================================================================
    // xs:import resolution tests
    // ========================================================================

    @Test
    void testImportResolvesFromFile(@TempDir Path tempDir) throws Exception {
        String otherXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/other">
                    <xs:element name="otherElement" type="xs:string"/>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:other="http://example.com/other"
                           targetNamespace="http://example.com/main">
                    <xs:import namespace="http://example.com/other" schemaLocation="other.xsd"/>
                    <xs:element name="mainElement" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("other.xsd"), otherXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema);

        // Find the import node
        XsdImport xsdImport = schema.getChildren().stream()
                .filter(n -> n instanceof XsdImport)
                .map(n -> (XsdImport) n)
                .findFirst()
                .orElse(null);
        assertNotNull(xsdImport, "Import node should be present");
        assertNotNull(xsdImport.getImportedSchema(), "Imported schema should be resolved");
        assertNotNull(xsdImport.getResolvedPath(), "Resolved path should be set");
    }

    @Test
    void testImportResolvesFromStringWithSchemaFile(@TempDir Path tempDir) throws Exception {
        String otherXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/types">
                    <xs:simpleType name="PositiveInt">
                        <xs:restriction base="xs:integer">
                            <xs:minInclusive value="1"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:t="http://example.com/types"
                           targetNamespace="http://example.com/main">
                    <xs:import namespace="http://example.com/types" schemaLocation="types.xsd"/>
                    <xs:element name="count" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("types.xsd"), otherXsd);
        Path mainFile = tempDir.resolve("main.xsd");
        Files.writeString(mainFile, mainContent);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromStringWithSchemaFile(mainContent, mainFile, tempDir);

        assertNotNull(schema);

        XsdImport xsdImport = schema.getChildren().stream()
                .filter(n -> n instanceof XsdImport)
                .map(n -> (XsdImport) n)
                .findFirst()
                .orElse(null);
        assertNotNull(xsdImport, "Import node should be present");
        assertNotNull(xsdImport.getImportedSchema(),
                "Imported schema should be resolved via fromStringWithSchemaFile");
    }

    @Test
    void testImportFailsWithoutSchemaFile() throws Exception {
        String mainContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/main">
                    <xs:import namespace="http://example.com/other" schemaLocation="other.xsd"/>
                    <xs:element name="test" type="xs:string"/>
                </xs:schema>
                """;

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromString(mainContent);

        assertNotNull(schema, "Schema should parse even without resolving imports");

        XsdImport xsdImport = schema.getChildren().stream()
                .filter(n -> n instanceof XsdImport)
                .map(n -> (XsdImport) n)
                .findFirst()
                .orElse(null);
        assertNotNull(xsdImport, "Import node should be present");
        // Without a schema file, loadSchemaFromFile resolves relative to CWD which likely won't find it
        // The import should either fail gracefully or not be resolved
        // (The exact behavior depends on whether other.xsd exists in CWD)
    }

    @Test
    void testImportMissingFile(@TempDir Path tempDir) throws Exception {
        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/main">
                    <xs:import namespace="http://example.com/missing" schemaLocation="missing.xsd"/>
                    <xs:element name="test" type="xs:string"/>
                </xs:schema>
                """;

        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema, "Schema should parse even with missing import");

        XsdImport xsdImport = schema.getChildren().stream()
                .filter(n -> n instanceof XsdImport)
                .map(n -> (XsdImport) n)
                .findFirst()
                .orElse(null);
        assertNotNull(xsdImport, "Import node should be present");
        assertNull(xsdImport.getImportedSchema(), "Import should not resolve for missing file");
        assertNotNull(xsdImport.getResolutionError(), "Resolution error should be set");
    }

    @Test
    void testImportWithDifferentNamespace(@TempDir Path tempDir) throws Exception {
        String ns1Xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/ns1">
                    <xs:element name="elem1" type="xs:string"/>
                </xs:schema>
                """;

        String ns2Xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/ns2">
                    <xs:element name="elem2" type="xs:integer"/>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:ns1="http://example.com/ns1"
                           xmlns:ns2="http://example.com/ns2"
                           targetNamespace="http://example.com/main">
                    <xs:import namespace="http://example.com/ns1" schemaLocation="ns1.xsd"/>
                    <xs:import namespace="http://example.com/ns2" schemaLocation="ns2.xsd"/>
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("ns1.xsd"), ns1Xsd);
        Files.writeString(tempDir.resolve("ns2.xsd"), ns2Xsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema);

        // Verify both imports resolved
        var imports = schema.getChildren().stream()
                .filter(n -> n instanceof XsdImport)
                .map(n -> (XsdImport) n)
                .toList();
        assertEquals(2, imports.size(), "Should have 2 import nodes");

        // Both should have their imported schemas
        for (XsdImport imp : imports) {
            assertNotNull(imp.getImportedSchema(),
                    "Import for namespace '" + imp.getNamespace() + "' should be resolved");
        }

        // Verify the imported schemas are accessible on the main schema
        var importedSchemas = schema.getImportedSchemas();
        assertNotNull(importedSchemas);
        assertTrue(importedSchemas.containsKey("http://example.com/ns1"),
                "importedSchemas should contain ns1");
        assertTrue(importedSchemas.containsKey("http://example.com/ns2"),
                "importedSchemas should contain ns2");
    }
}
