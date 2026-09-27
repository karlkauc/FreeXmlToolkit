package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKey;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKind;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.Reference;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

class SchemaReferenceGraphTest {

    private static final String HEAD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:tns="urn:test" targetNamespace="urn:test"
                       xmlns:ext="urn:ext" elementFormDefault="qualified">
            """;

    private static XsdSchema parse(String body) throws Exception {
        return new XsdNodeFactory().fromString(HEAD + body + "</xs:schema>");
    }

    private static ComponentKey key(ComponentKind kind, String name) {
        return new ComponentKey(kind, name);
    }

    @Test
    void collectsGlobalComponentsOfEveryKind() throws Exception {
        XsdSchema schema = parse("""
                <xs:element name="Root" type="tns:RootType"/>
                <xs:attribute name="lang" type="xs:string"/>
                <xs:complexType name="RootType"><xs:sequence><xs:element ref="tns:Child"/></xs:sequence></xs:complexType>
                <xs:element name="Child" type="xs:string"/>
                <xs:simpleType name="Code"><xs:restriction base="xs:string"/></xs:simpleType>
                <xs:group name="G"><xs:sequence><xs:element name="x" type="xs:int"/></xs:sequence></xs:group>
                <xs:attributeGroup name="AG"><xs:attribute name="a" type="xs:int"/></xs:attributeGroup>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);

        assertEquals(Set.of(
                key(ComponentKind.ELEMENT, "Root"), key(ComponentKind.ATTRIBUTE, "lang"),
                key(ComponentKind.COMPLEX_TYPE, "RootType"), key(ComponentKind.ELEMENT, "Child"),
                key(ComponentKind.SIMPLE_TYPE, "Code"), key(ComponentKind.GROUP, "G"),
                key(ComponentKind.ATTRIBUTE_GROUP, "AG")), graph.components().keySet());
    }

    @Test
    void followsEveryReferenceKind() throws Exception {
        XsdSchema schema = parse("""
                <xs:element name="Root" type="tns:RootType"/>
                <xs:element name="Head" type="xs:string"/>
                <xs:element name="Member" type="xs:string" substitutionGroup="tns:Head"/>
                <xs:attribute name="ga" type="tns:Code"/>
                <xs:complexType name="RootType">
                  <xs:sequence>
                    <xs:element ref="tns:Head"/>
                    <xs:group ref="tns:G"/>
                  </xs:sequence>
                  <xs:attribute ref="tns:ga"/>
                  <xs:attributeGroup ref="tns:AG"/>
                </xs:complexType>
                <xs:complexType name="Derived"><xs:complexContent><xs:extension base="tns:RootType"/></xs:complexContent></xs:complexType>
                <xs:simpleType name="Code"><xs:restriction base="tns:Base"/></xs:simpleType>
                <xs:simpleType name="Base"><xs:restriction base="xs:string"/></xs:simpleType>
                <xs:simpleType name="Codes"><xs:list itemType="tns:Code"/></xs:simpleType>
                <xs:simpleType name="Any"><xs:union memberTypes="tns:Code tns:Base tns:Code"/></xs:simpleType>
                <xs:group name="G"><xs:sequence><xs:element name="x" type="xs:int"/></xs:sequence></xs:group>
                <xs:attributeGroup name="AG"><xs:attribute name="a" type="xs:int"/></xs:attributeGroup>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);

        assertEquals(List.of(UsageReferenceType.ELEMENT_REF),
                types(graph.referencesTo(key(ComponentKind.ELEMENT, "Head"))).stream()
                        .filter(t -> t == UsageReferenceType.ELEMENT_REF).toList());
        assertTrue(types(graph.referencesTo(key(ComponentKind.ELEMENT, "Head"))).contains(UsageReferenceType.SUBSTITUTION_GROUP));
        assertEquals(List.of(UsageReferenceType.GROUP_REF), types(graph.referencesTo(key(ComponentKind.GROUP, "G"))));
        assertEquals(List.of(UsageReferenceType.ATTRIBUTE_GROUP_REF), types(graph.referencesTo(key(ComponentKind.ATTRIBUTE_GROUP, "AG"))));
        assertEquals(List.of(UsageReferenceType.ATTRIBUTE_REF), types(graph.referencesTo(key(ComponentKind.ATTRIBUTE, "ga"))));
        assertEquals(List.of(UsageReferenceType.ELEMENT_TYPE, UsageReferenceType.EXTENSION_BASE),
                types(graph.referencesTo(key(ComponentKind.COMPLEX_TYPE, "RootType"))));
        // Code: attribute type, list itemType, one union member (deduplicated)
        assertEquals(3, graph.usageCount(key(ComponentKind.SIMPLE_TYPE, "Code")));
        assertEquals(List.of(UsageReferenceType.RESTRICTION_BASE, UsageReferenceType.UNION_MEMBER_TYPE),
                types(graph.referencesTo(key(ComponentKind.SIMPLE_TYPE, "Base"))));
        // xs:string is not a component but still a type reference by name
        assertFalse(graph.typeReferencesTo("string").isEmpty());
        assertTrue(graph.typeReferencesTo("Head").isEmpty(), "element refs are not type references");
    }

    @Test
    void unreferencedVersusUnreachable() throws Exception {
        XsdSchema schema = parse("""
                <xs:element name="Root" type="tns:RootType"/>
                <xs:complexType name="RootType"><xs:sequence><xs:element name="a" type="xs:string"/></xs:sequence></xs:complexType>
                <xs:complexType name="Orphan"><xs:sequence><xs:element name="b" type="tns:OrphanChild"/></xs:sequence></xs:complexType>
                <xs:simpleType name="OrphanChild"><xs:restriction base="xs:string"/></xs:simpleType>
                <xs:group name="UnusedGroup"><xs:sequence><xs:element name="x" type="xs:int"/></xs:sequence></xs:group>
                <xs:attributeGroup name="UnusedAG"><xs:attribute name="a" type="xs:int"/></xs:attributeGroup>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);

        assertEquals(Set.of(key(ComponentKind.COMPLEX_TYPE, "Orphan")),
                graph.unreferenced(ComponentKind.COMPLEX_TYPE, ComponentKind.SIMPLE_TYPE),
                "OrphanChild is referenced (by Orphan) and therefore not unreferenced");
        assertEquals(Set.of(key(ComponentKind.GROUP, "UnusedGroup")), graph.unreferenced(ComponentKind.GROUP));
        assertEquals(Set.of(key(ComponentKind.ATTRIBUTE_GROUP, "UnusedAG")), graph.unreferenced(ComponentKind.ATTRIBUTE_GROUP));
        assertEquals(Set.of(key(ComponentKind.COMPLEX_TYPE, "Orphan"), key(ComponentKind.SIMPLE_TYPE, "OrphanChild"),
                        key(ComponentKind.GROUP, "UnusedGroup"), key(ComponentKind.ATTRIBUTE_GROUP, "UnusedAG")),
                graph.unreachable(), "unreachable cascades through Orphan → OrphanChild");
    }

    @Test
    void typeLibraryWithoutGlobalElementsFallsBackToUnreferenced() throws Exception {
        XsdSchema schema = parse("""
                <xs:complexType name="A"><xs:sequence><xs:element name="b" type="tns:B"/></xs:sequence></xs:complexType>
                <xs:simpleType name="B"><xs:restriction base="xs:string"/></xs:simpleType>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        assertEquals(Set.of(key(ComponentKind.COMPLEX_TYPE, "A")), graph.unreachable());
    }

    @Test
    void singleUseExcludesSelfReferences() throws Exception {
        XsdSchema schema = parse("""
                <xs:element name="Root" type="tns:RootType"/>
                <xs:complexType name="RootType"><xs:sequence>
                  <xs:element name="once" type="tns:Once"/>
                  <xs:element name="twice1" type="tns:Twice"/>
                  <xs:element name="twice2" type="tns:Twice"/>
                </xs:sequence></xs:complexType>
                <xs:simpleType name="Once"><xs:restriction base="xs:string"/></xs:simpleType>
                <xs:simpleType name="Twice"><xs:restriction base="xs:string"/></xs:simpleType>
                <xs:complexType name="Self"><xs:sequence><xs:element name="me" type="tns:Self"/></xs:sequence></xs:complexType>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        assertEquals(Set.of(key(ComponentKind.COMPLEX_TYPE, "RootType"), key(ComponentKind.SIMPLE_TYPE, "Once")),
                graph.singleUse());
    }

    @Test
    void distinguishesContainmentFromDerivationCycles() throws Exception {
        XsdSchema schema = parse("""
                <xs:element name="Folder" type="tns:FolderType"/>
                <xs:complexType name="FolderType"><xs:sequence><xs:element ref="tns:Folder" minOccurs="0"/></xs:sequence></xs:complexType>
                <xs:complexType name="Node"><xs:sequence><xs:element name="child" type="tns:Node" minOccurs="0"/></xs:sequence></xs:complexType>
                <xs:simpleType name="A"><xs:restriction base="tns:B"/></xs:simpleType>
                <xs:simpleType name="B"><xs:restriction base="tns:A"/></xs:simpleType>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);

        List<List<ComponentKey>> cycles = graph.cycles();
        assertEquals(3, cycles.size(), cycles.toString());
        assertTrue(cycles.contains(List.of(key(ComponentKind.ELEMENT, "Folder"), key(ComponentKind.COMPLEX_TYPE, "FolderType"))));
        assertTrue(cycles.contains(List.of(key(ComponentKind.COMPLEX_TYPE, "Node"))), "self-loop is a cycle");
        assertEquals(List.of(List.of(key(ComponentKind.SIMPLE_TYPE, "A"), key(ComponentKind.SIMPLE_TYPE, "B"))),
                graph.derivationCycles());
    }

    @Test
    void reportsUnresolvedLocalReferencesOnly() throws Exception {
        XsdSchema schema = parse("""
                <xs:element name="Root" type="tns:Missing"/>
                <xs:element name="Other" type="ext:Foreign"/>
                <xs:element name="Plain" type="Missing2"/>
                <xs:element name="Builtin" type="xs:string"/>
                <xs:element name="Ok" type="tns:Known"/>
                <xs:simpleType name="Known"><xs:restriction base="xs:string"/></xs:simpleType>
                <xs:complexType name="C"><xs:sequence><xs:group ref="tns:NoGroup"/></xs:sequence></xs:complexType>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        List<String> unresolved = graph.unresolvedReferences().stream().map(Reference::targetName).sorted().toList();
        assertEquals(List.of("Missing", "Missing2", "NoGroup"), unresolved);
    }

    @Test
    void includedNodesAreComponentsAndImportsCountAsReferrers() throws Exception {
        XsdSchema schema = parse("""
                <xs:element name="Root" type="tns:RootType"/>
                <xs:complexType name="RootType"><xs:sequence><xs:element name="a" type="xs:string"/></xs:sequence></xs:complexType>
                <xs:simpleType name="UsedByImport"><xs:restriction base="xs:string"/></xs:simpleType>
                """);
        XsdSchema imported = new XsdNodeFactory().fromString("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:test" targetNamespace="urn:ext">
                  <xs:element name="Ext" type="t:UsedByImport"/>
                </xs:schema>
                """);
        schema.addImportedSchema("urn:ext", imported);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);

        ComponentKey used = key(ComponentKind.SIMPLE_TYPE, "UsedByImport");
        assertEquals(1, graph.usageCount(used));
        assertTrue(graph.referencesTo(used).get(0).fromImport());
        assertFalse(graph.unreachable().contains(used), "a reference from an import keeps the type alive");
        assertFalse(graph.components().containsKey(key(ComponentKind.ELEMENT, "Ext")), "imported globals are not ours");
    }

    @Test
    void redefineDisablesUnresolvedReporting() throws Exception {
        XsdSchema schema = parse("""
                <xs:redefine schemaLocation="base.xsd"><xs:simpleType name="X"><xs:restriction base="tns:X"/></xs:simpleType></xs:redefine>
                <xs:element name="Root" type="tns:Missing"/>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        assertTrue(graph.hasRedefineOrOverride());
        assertTrue(graph.unresolvedReferences().isEmpty());
    }

    @Test
    void referencesCarryOwnerAndSourceFile() throws Exception {
        XsdSchema schema = parse("""
                <xs:element name="Root" type="tns:RootType"/>
                <xs:complexType name="RootType"><xs:sequence><xs:element name="a" type="tns:Code"/></xs:sequence></xs:complexType>
                <xs:simpleType name="Code"><xs:restriction base="xs:string"/></xs:simpleType>
                """);
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        Reference ref = graph.referencesTo(key(ComponentKind.SIMPLE_TYPE, "Code")).get(0);
        assertEquals(key(ComponentKind.COMPLEX_TYPE, "RootType"), ref.owner());
        assertEquals("a", ref.referrer().getName());
        assertNotNull(ref.toUsageLocation());
        assertEquals(UsageReferenceType.ELEMENT_TYPE, ref.toUsageLocation().referenceType());
    }

    private static List<UsageReferenceType> types(List<Reference> references) {
        return references.stream().map(Reference::type).toList();
    }
}
