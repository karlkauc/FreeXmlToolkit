package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.NamespaceSchemaDownloader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for transitive (nested) xs:import resolution: imports declared inside imported
 * schemas, imports declared inside xs:include'd files, circular-import and diamond-import
 * handling, the import depth cap, and flattening of all transitively imported schemas
 * into the root schema's imported-schema map.
 *
 * @since 2.0
 */
class XsdNodeFactoryNestedImportResolutionTest {

    private static final String NS_A = "http://example.com/a";
    private static final String NS_B = "http://example.com/b";
    private static final String NS_C = "http://example.com/c";
    private static final String NS_D = "http://example.com/d";

    private static String schema(String targetNamespace, String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="%s">
                %s
                </xs:schema>
                """.formatted(targetNamespace, body);
    }

    private static String importOf(String namespace, String schemaLocation) {
        return "    <xs:import namespace=\"%s\" schemaLocation=\"%s\"/>\n".formatted(namespace, schemaLocation);
    }

    private static XsdImport findImport(XsdSchema schema, String namespace) {
        return schema.getChildren().stream()
                .filter(n -> n instanceof XsdImport)
                .map(n -> (XsdImport) n)
                .filter(i -> namespace.equals(i.getNamespace()))
                .findFirst()
                .orElse(null);
    }

    // ========================================================================
    // Transitive import chains
    // ========================================================================

    @Test
    void testNestedImportChain(@TempDir Path tempDir) throws Exception {
        // a.xsd imports sub/b.xsd, which imports deep/c.xsd — three namespaces,
        // spread over subdirectories to prove per-file base resolution.
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        Path deep = Files.createDirectories(sub.resolve("deep"));

        Files.writeString(deep.resolve("c.xsd"), schema(NS_C,
                "    <xs:simpleType name=\"CType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"));
        Files.writeString(sub.resolve("b.xsd"), schema(NS_B,
                importOf(NS_C, "deep/c.xsd")
                        + "    <xs:simpleType name=\"BType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"));
        Path mainFile = Files.writeString(tempDir.resolve("a.xsd"), schema(NS_A,
                importOf(NS_B, "sub/b.xsd")
                        + "    <xs:element name=\"root\" type=\"xs:string\"/>"));

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertNotNull(schema);
        var imported = schema.getImportedSchemas();
        assertTrue(imported.containsKey(NS_B), "Directly imported namespace B must be registered on the root");
        assertTrue(imported.containsKey(NS_C), "Transitively imported namespace C must be flattened onto the root");

        XsdImport importB = findImport(schema, NS_B);
        assertNotNull(importB, "Import node for B should be present");
        assertTrue(importB.isResolved(), "Import of B should be resolved");

        XsdSchema schemaB = imported.get(NS_B);
        XsdImport importC = findImport(schemaB, NS_C);
        assertNotNull(importC, "Import node for C should be present inside B's schema");
        assertTrue(importC.isResolved(), "Nested import of C should be resolved");

        boolean cTypeReachable = imported.get(NS_C).getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "CType".equals(n.getName()));
        assertTrue(cTypeReachable, "C's global type must be reachable through the root map");
    }

    @Test
    void testImportInsideImportedSchemaResolvesRelativeToThatFile(@TempDir Path tempDir) throws Exception {
        // b.xsd lives in sub/ and imports types/c.xsd — which only exists relative to sub/,
        // not relative to the root schema's directory.
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        Path types = Files.createDirectories(sub.resolve("types"));

        Files.writeString(types.resolve("c.xsd"), schema(NS_C,
                "    <xs:simpleType name=\"CType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"));
        Files.writeString(sub.resolve("b.xsd"), schema(NS_B,
                importOf(NS_C, "types/c.xsd")));
        Path mainFile = Files.writeString(tempDir.resolve("a.xsd"), schema(NS_A,
                importOf(NS_B, "sub/b.xsd")));

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertTrue(schema.getImportedSchemas().containsKey(NS_C),
                "Nested import must resolve relative to the declaring file (sub/), not the root schema");
        XsdImport importC = findImport(schema.getImportedSchemas().get(NS_B), NS_C);
        assertNotNull(importC);
        assertTrue(importC.isResolved(), "Nested import of C should be resolved");
    }

    @Test
    void testNestedIncludeInsideImportedSchema(@TempDir Path tempDir) throws Exception {
        // a.xsd imports b.xsd; b.xsd includes b2.xsd (same namespace). The included
        // components must be inlined into B's schema as seen from the root map.
        Files.writeString(tempDir.resolve("b2.xsd"), schema(NS_B,
                "    <xs:simpleType name=\"IncludedType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"));
        Files.writeString(tempDir.resolve("b.xsd"), schema(NS_B,
                "    <xs:include schemaLocation=\"b2.xsd\"/>\n"
                        + "    <xs:element name=\"bElement\" type=\"xs:string\"/>"));
        Path mainFile = Files.writeString(tempDir.resolve("a.xsd"), schema(NS_A,
                importOf(NS_B, "b.xsd")));

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        XsdSchema schemaB = schema.getImportedSchemas().get(NS_B);
        assertNotNull(schemaB, "Imported schema B must be registered");
        boolean hasIncludedType = schemaB.getChildren().stream()
                .anyMatch(n -> n instanceof XsdSimpleType && "IncludedType".equals(n.getName()));
        assertTrue(hasIncludedType, "Include inside the imported schema must be inlined into B");
    }

    @Test
    void testImportInsideIncludedFile(@TempDir Path tempDir) throws Exception {
        // a.xsd includes included.xsd (same namespace); included.xsd declares an import of C.
        // The import must be resolved and flattened onto the root, but not added as a child node.
        Files.writeString(tempDir.resolve("c.xsd"), schema(NS_C,
                "    <xs:simpleType name=\"CType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"));
        Files.writeString(tempDir.resolve("included.xsd"), schema(NS_A,
                importOf(NS_C, "c.xsd")
                        + "    <xs:element name=\"fromInclude\" type=\"xs:string\"/>"));
        Path mainFile = Files.writeString(tempDir.resolve("a.xsd"), schema(NS_A,
                "    <xs:include schemaLocation=\"included.xsd\"/>\n"
                        + "    <xs:element name=\"root\" type=\"xs:string\"/>"));

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        assertTrue(schema.getImportedSchemas().containsKey(NS_C),
                "Import declared inside the included file must be resolved and registered on the root");

        long importNodes = schema.getChildren().stream().filter(n -> n instanceof XsdImport).count();
        assertEquals(0, importNodes,
                "Imports from included files must not appear as child nodes of the main schema");
    }

    // ========================================================================
    // Cycle, diamond, and depth-cap handling
    // ========================================================================

    @Test
    void testCircularImportDetection(@TempDir Path tempDir) throws Exception {
        // a.xsd imports b.xsd, b.xsd imports a.xsd — must terminate; the back-edge fails.
        Files.writeString(tempDir.resolve("a.xsd"), schema(NS_A,
                importOf(NS_B, "b.xsd")
                        + "    <xs:element name=\"fromA\" type=\"xs:string\"/>"));
        Files.writeString(tempDir.resolve("b.xsd"), schema(NS_B,
                importOf(NS_A, "a.xsd")
                        + "    <xs:element name=\"fromB\" type=\"xs:string\"/>"));

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(tempDir.resolve("a.xsd"));

        assertNotNull(schema, "Parsing must terminate despite the circular import");

        XsdImport importB = findImport(schema, NS_B);
        assertNotNull(importB);
        assertTrue(importB.isResolved(), "The forward import A -> B should be resolved");

        XsdSchema schemaB = schema.getImportedSchemas().get(NS_B);
        assertNotNull(schemaB);
        XsdImport backEdge = findImport(schemaB, NS_A);
        assertNotNull(backEdge, "B's import of A should be present as a node");
        assertFalse(backEdge.isResolved(), "The back-edge B -> A must not resolve");
        assertNotNull(backEdge.getResolutionError());
        assertTrue(backEdge.getResolutionError().contains("Circular import"),
                "Back-edge error should mention the circular import, was: " + backEdge.getResolutionError());
    }

    @Test
    void testDiamondImport(@TempDir Path tempDir) throws Exception {
        // a.xsd imports b.xsd and c.xsd; both import d.xsd. D must be parsed once and
        // the same instance reused, with both imports marked as resolved.
        Files.writeString(tempDir.resolve("d.xsd"), schema(NS_D,
                "    <xs:simpleType name=\"DType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"));
        Files.writeString(tempDir.resolve("b.xsd"), schema(NS_B, importOf(NS_D, "d.xsd")));
        Files.writeString(tempDir.resolve("c.xsd"), schema(NS_C, importOf(NS_D, "d.xsd")));
        Path mainFile = Files.writeString(tempDir.resolve("a.xsd"), schema(NS_A,
                importOf(NS_B, "b.xsd") + importOf(NS_C, "c.xsd")));

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        var imported = schema.getImportedSchemas();
        assertTrue(imported.containsKey(NS_B));
        assertTrue(imported.containsKey(NS_C));
        assertTrue(imported.containsKey(NS_D), "The shared import D must be flattened onto the root");

        XsdImport dFromB = findImport(imported.get(NS_B), NS_D);
        XsdImport dFromC = findImport(imported.get(NS_C), NS_D);
        assertNotNull(dFromB);
        assertNotNull(dFromC);
        assertTrue(dFromB.isResolved(), "B's import of D should be resolved");
        assertTrue(dFromC.isResolved(), "C's import of D should be resolved (reused, not a duplicate error)");
        assertSame(dFromB.getImportedSchema(), dFromC.getImportedSchema(),
                "Diamond import must reuse the same parsed schema instance");
    }

    @Test
    void testImportDepthCapExceeded(@TempDir Path tempDir) throws Exception {
        // s0.xsd imports s1.xsd imports ... s12.xsd. Schemas at depth 1..10 resolve;
        // the import declared in the depth-10 schema must fail with the depth message.
        int chainLength = 12;
        for (int i = chainLength; i >= 0; i--) {
            String body = i < chainLength
                    ? importOf(ns(i + 1), "s" + (i + 1) + ".xsd")
                    : "    <xs:element name=\"leaf\" type=\"xs:string\"/>";
            Files.writeString(tempDir.resolve("s" + i + ".xsd"), schema(ns(i), body));
        }

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(tempDir.resolve("s0.xsd"));

        assertNotNull(schema, "Parsing must terminate without a stack overflow");

        var imported = schema.getImportedSchemas();
        for (int i = 1; i <= 10; i++) {
            assertTrue(imported.containsKey(ns(i)), "Schema s" + i + " (within the depth cap) should resolve");
        }
        assertFalse(imported.containsKey(ns(11)), "Schema s11 (beyond the depth cap) must not resolve");

        XsdImport failing = findImport(imported.get(ns(10)), ns(11));
        assertNotNull(failing, "The import declared at the depth cap should be present as a node");
        assertFalse(failing.isResolved());
        assertNotNull(failing.getResolutionError());
        assertTrue(failing.getResolutionError().contains("Maximum import depth"),
                "Error should mention the depth cap, was: " + failing.getResolutionError());
    }

    private static String ns(int i) {
        return "http://example.com/s" + i;
    }

    // ========================================================================
    // Namespace-URL fallback in nested imports
    // ========================================================================

    @Test
    void testNestedImportViaNamespaceFallback(@TempDir Path tempDir) throws Exception {
        // a.xsd imports b.xsd (local); b.xsd imports C with a schemaLocation that does not
        // exist locally. The downloader injected on the ROOT factory must be shared into
        // the nested level and resolve C via its namespace URL.
        String cXsd = schema(NS_C,
                "    <xs:simpleType name=\"CType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>");
        Path cachedFile = Files.writeString(tempDir.resolve("cached-c.xsd"), cXsd);

        Files.writeString(tempDir.resolve("b.xsd"), schema(NS_B,
                importOf(NS_C, "does-not-exist.xsd")));
        Path mainFile = Files.writeString(tempDir.resolve("a.xsd"), schema(NS_A,
                importOf(NS_B, "b.xsd")));

        NamespaceSchemaDownloader downloader = mock(NamespaceSchemaDownloader.class);
        when(downloader.resolve(NS_C, "does-not-exist.xsd"))
                .thenReturn(Optional.of(new NamespaceSchemaDownloader.ResolvedNamespaceSchema(
                        cXsd, cachedFile, "https://example.com/c.xsd")));

        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(true);
        factory.setNamespaceSchemaDownloader(downloader);
        XsdSchema schema = factory.fromFile(mainFile);

        assertTrue(schema.getImportedSchemas().containsKey(NS_C),
                "Nested import must resolve via the shared namespace-URL fallback downloader");
        XsdImport importC = findImport(schema.getImportedSchemas().get(NS_B), NS_C);
        assertNotNull(importC);
        assertTrue(importC.isResolved());
    }

    // ========================================================================
    // Factory reuse
    // ========================================================================

    @Test
    void testFactoryReuseStartsFreshResolutionRun(@TempDir Path tempDir) throws Exception {
        // Parsing two different root schemas with the same factory instance must not
        // leak resolution state (stack, registry, root schema) between runs.
        Files.writeString(tempDir.resolve("b.xsd"), schema(NS_B,
                "    <xs:simpleType name=\"BType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>"));
        Path first = Files.writeString(tempDir.resolve("first.xsd"), schema(NS_A,
                importOf(NS_B, "b.xsd")));
        Path second = Files.writeString(tempDir.resolve("second.xsd"), schema("http://example.com/second",
                importOf(NS_B, "b.xsd")));

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema1 = factory.fromFile(first);
        XsdSchema schema2 = factory.fromFile(second);

        assertTrue(schema1.getImportedSchemas().containsKey(NS_B));
        assertTrue(schema2.getImportedSchemas().containsKey(NS_B),
                "Second parse must register the import on ITS OWN root schema");
        XsdImport secondImport = findImport(schema2, NS_B);
        assertNotNull(secondImport);
        assertTrue(secondImport.isResolved(), "Import must resolve again on factory reuse");
    }

    // ========================================================================
    // Consumer-facing flattening (List used by TypeLibrary-style consumers)
    // ========================================================================

    @Test
    void testFlattenedSchemasCarryGlobalTypes(@TempDir Path tempDir) throws Exception {
        // One-level consumers iterate root.getImportedSchemas().values() and collect
        // named types — a transitively imported type must show up that way.
        Files.writeString(tempDir.resolve("c.xsd"), schema(NS_C,
                "    <xs:complexType name=\"DeepType\"><xs:sequence/></xs:complexType>"));
        Files.writeString(tempDir.resolve("b.xsd"), schema(NS_B, importOf(NS_C, "c.xsd")));
        Path mainFile = Files.writeString(tempDir.resolve("a.xsd"), schema(NS_A,
                importOf(NS_B, "b.xsd")));

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        List<String> namedTypes = schema.getImportedSchemas().values().stream()
                .flatMap(s -> s.getChildren().stream())
                .filter(n -> n instanceof XsdComplexType || n instanceof XsdSimpleType)
                .map(XsdNode::getName)
                .toList();
        assertTrue(namedTypes.contains("DeepType"),
                "A one-level walk over the root's imported schemas must surface the transitive type");
    }
}
