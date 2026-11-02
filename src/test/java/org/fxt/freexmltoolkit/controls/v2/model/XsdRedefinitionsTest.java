package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.shell.schema.TypeLibrary;
import org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatistics;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdStatisticsCollector;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKey;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Outside the diagram, a redefinition replaces its original as well. */
class XsdRedefinitionsTest {

    private static final String BASE = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="Address">
                <xs:sequence><xs:element name="street" type="xs:string"/></xs:sequence>
                <xs:attribute name="id" type="xs:string"/>
              </xs:complexType>
              <xs:complexType name="Untouched"/>
              <xs:simpleType name="Code"><xs:restriction base="xs:string"/></xs:simpleType>
              <xs:group name="Parts"><xs:sequence><xs:element name="part" type="xs:string"/></xs:sequence></xs:group>
              <xs:attributeGroup name="Audit"><xs:attribute name="created" type="xs:string"/></xs:attributeGroup>
            </xs:schema>
            """;

    private static final String REDEFINE_ALL_KINDS = """
            <xs:redefine schemaLocation="base.xsd">
              <xs:complexType name="Address"><xs:complexContent><xs:extension base="Address">
                <xs:sequence><xs:element name="country" type="xs:string"/></xs:sequence>
                <xs:attribute name="zone" type="xs:string"/>
              </xs:extension></xs:complexContent></xs:complexType>
              <xs:simpleType name="Code"><xs:restriction base="Code"><xs:maxLength value="3"/></xs:restriction></xs:simpleType>
              <xs:group name="Parts"><xs:sequence>
                <xs:group ref="Parts"/><xs:element name="extra" type="xs:string"/>
              </xs:sequence></xs:group>
              <xs:attributeGroup name="Audit">
                <xs:attributeGroup ref="Audit"/><xs:attribute name="changed" type="xs:string"/>
              </xs:attributeGroup>
            </xs:redefine>
            <xs:element name="home" type="Address"/>
            <xs:element name="box"><xs:complexType>
              <xs:group ref="Parts"/><xs:attributeGroup ref="Audit"/>
            </xs:complexType></xs:element>
            """;

    private static final String OVERRIDE = """
            <xs:override schemaLocation="base.xsd">
              <xs:complexType name="Address">
                <xs:sequence><xs:element name="replaced" type="xs:string"/></xs:sequence>
              </xs:complexType>
            </xs:override>
            <xs:element name="home" type="Address"/>
            """;

    @TempDir
    Path dir;

    private XsdSchema load(String body) throws Exception {
        Files.writeString(dir.resolve("base.xsd"), BASE);
        Path main = dir.resolve("main.xsd");
        String xsd = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" + body + "</xs:schema>";
        Files.writeString(main, xsd);
        return new XsdNodeFactory().fromStringWithSchemaFile(xsd, main, dir);
    }

    private static XsdNode located(XsdSchema schema, String... path) {
        return XsdInstanceLocator.locateElement(schema, List.of(path)).orElseThrow().declaration();
    }

    @Test
    void everyKindReplacesItsOriginal() throws Exception {
        XsdSchema schema = load(REDEFINE_ALL_KINDS);

        assertEquals(List.of("Address", "Audit", "Code", "Parts"),
                XsdRedefinitions.replacedNodes(schema).stream().map(XsdNode::getName).sorted().toList());
        assertTrue(XsdRedefinitions.replacedNodes(schema).stream()
                .allMatch(node -> node.getParent() != schema), "the originals stay in the target");
    }

    @Test
    void aMissingTargetReplacesNothing() throws Exception {
        XsdSchema schema = load("<xs:redefine schemaLocation=\"gone.xsd\"><xs:complexType name=\"X\"/></xs:redefine>");
        assertTrue(XsdRedefinitions.replacedNodes(schema).isEmpty());
    }

    @Test
    void theLocatorFindsTheRedefinitionAndItsOriginal() throws Exception {
        XsdSchema schema = load(REDEFINE_ALL_KINDS);

        assertEquals("country", located(schema, "home", "country").getName());
        assertEquals("street", located(schema, "home", "street").getName(), "from the original");
        assertEquals("extra", located(schema, "box", "extra").getName());
        assertEquals("part", located(schema, "box", "part").getName(), "group ref to itself: the original");
    }

    @Test
    void theLocatorFindsAttributesThroughTheRedefinition() throws Exception {
        XsdSchema schema = load(REDEFINE_ALL_KINDS);

        assertTrue(XsdInstanceLocator.locate(schema, List.of("home"), "zone").isPresent());
        assertTrue(XsdInstanceLocator.locate(schema, List.of("home"), "id").isPresent(), "from the original");
        assertTrue(XsdInstanceLocator.locate(schema, List.of("box"), "changed").isPresent());
        assertTrue(XsdInstanceLocator.locate(schema, List.of("box"), "created").isPresent());
    }

    @Test
    void aRedefinedDeclarationInTheMainFileCanBeEdited() throws Exception {
        XsdSchema schema = load(REDEFINE_ALL_KINDS);

        assertFalse(XsdInstanceLocator.locateElement(schema, List.of("home", "country")).orElseThrow().foreign());
        assertTrue(XsdInstanceLocator.locateElement(schema, List.of("home", "street")).orElseThrow().foreign());
    }

    @Test
    void overrideHasNoOriginalToBuildOn() throws Exception {
        XsdSchema schema = load(OVERRIDE);

        assertEquals("replaced", located(schema, "home", "replaced").getName());
        assertTrue(XsdInstanceLocator.locateElement(schema, List.of("home", "street")).isEmpty());
    }

    @Test
    void theGraphTakesTheRedefinitionAsTheComponent() throws Exception {
        XsdSchema schema = load(REDEFINE_ALL_KINDS);

        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);

        var address = new ComponentKey(ComponentKind.COMPLEX_TYPE, "Address");
        assertTrue(graph.component(address).node().getParent() instanceof XsdRedefine);
        assertTrue(graph.referencesTo(address).stream().anyMatch(ref -> "home".equals(ref.referrer().getName())));
        assertEquals(List.of(), graph.unresolvedReferences());
    }

    @Test
    void buildingOnTheOriginalIsNoCycle() throws Exception {
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(load(REDEFINE_ALL_KINDS));

        assertTrue(graph.derivationCycles().isEmpty(), graph.derivationCycles().toString());
    }

    @Test
    void aNameDefinedNowhereIsReportedDespiteALoadedRedefine() throws Exception {
        XsdSchema schema = load(REDEFINE_ALL_KINDS + "<xs:element name=\"broken\" type=\"Missing\"/>");

        assertEquals(List.of("Missing"), SchemaReferenceGraph.build(schema).unresolvedReferences().stream()
                .map(SchemaReferenceGraph.Reference::targetName).toList());
    }

    @Test
    void aTypeFromTheTargetIsNoUnresolvedReference() throws Exception {
        XsdSchema schema = load(REDEFINE_ALL_KINDS + "<xs:element name=\"other\" type=\"Untouched\"/>");

        assertEquals(List.of(), SchemaReferenceGraph.build(schema).unresolvedReferences());
    }

    @Test
    void theTypeLibraryListsEachTypeOnce() throws Exception {
        XsdSchema schema = load(REDEFINE_ALL_KINDS);

        List<XsdNode> types = TypeLibrary.collectNamedTypes(schema);

        assertEquals(List.of("Address", "Code", "Untouched"), types.stream().map(XsdNode::getName).sorted().toList());
        XsdNode address = types.stream().filter(type -> "Address".equals(type.getName())).findFirst().orElseThrow();
        assertTrue(address.getParent() instanceof XsdRedefine);
    }

    @Test
    void statisticsCountAReplacedDefinitionOnce() throws Exception {
        XsdStatistics statistics = new XsdStatisticsCollector(load(REDEFINE_ALL_KINDS)).collect();

        assertEquals(1, statistics.nodeCountsByType().get(XsdNodeType.SIMPLE_TYPE),
                "Code: the redefinition instead of the original");
    }
}
