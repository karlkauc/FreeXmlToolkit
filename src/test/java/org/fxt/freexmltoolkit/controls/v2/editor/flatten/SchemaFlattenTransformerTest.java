package org.fxt.freexmltoolkit.controls.v2.editor.flatten;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdInclude;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link SchemaFlattenTransformer}: annotation/comment stripping, resolved
 * include removal and unused-global-component removal (mark-and-sweep), all on
 * schemas parsed with {@link XsdNodeFactory} and verified via {@link XsdSerializer}
 * output.
 */
class SchemaFlattenTransformerTest {

    private static final FlattenOptions ANNOTATIONS_ONLY = new FlattenOptions(true, false, false, false, false);
    private static final FlattenOptions COMMENTS_ONLY = new FlattenOptions(false, true, false, false, false);
    private static final FlattenOptions SHAKE_ONLY = new FlattenOptions(false, false, false, true, false);
    private static final FlattenOptions INCLUDES_ONLY = new FlattenOptions(false, false, false, false, true);

    private XsdSchema parse(String xsd) throws Exception {
        return new XsdNodeFactory().fromString(xsd);
    }

    private String transformAndSerialize(XsdSchema schema, FlattenOptions options) {
        new SchemaFlattenTransformer().apply(schema, options);
        return new XsdSerializer().serialize(schema);
    }

    // --- annotations ------------------------------------------------------

    @Test
    void removesDocumentationAndAppinfoEverywhere() throws Exception {
        XsdSchema schema = parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:annotation>
                    <xs:documentation>Schema doc</xs:documentation>
                    <xs:appinfo>meta</xs:appinfo>
                  </xs:annotation>
                  <xs:complexType name="PersonType">
                    <xs:annotation><xs:documentation xml:lang="en">Person doc</xs:documentation></xs:annotation>
                    <xs:sequence>
                      <xs:element name="name" type="xs:string">
                        <xs:annotation><xs:documentation>Name doc</xs:documentation></xs:annotation>
                      </xs:element>
                    </xs:sequence>
                  </xs:complexType>
                  <xs:element name="person" type="PersonType"/>
                </xs:schema>
                """);
        String result = transformAndSerialize(schema, ANNOTATIONS_ONLY);
        assertFalse(result.contains("annotation"), result);
        assertFalse(result.contains("documentation"), result);
        assertFalse(result.contains("appinfo"), result);
        assertTrue(result.contains("PersonType"), result);
    }

    @Test
    void removesLegacyDocumentationString() throws Exception {
        XsdSchema schema = parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="person" type="xs:string"/>
                </xs:schema>
                """);
        schema.getChildren().getFirst().setDocumentation("legacy doc set programmatically");
        String result = transformAndSerialize(schema, ANNOTATIONS_ONLY);
        assertFalse(result.contains("legacy doc"), result);
        assertFalse(result.contains("annotation"), result);
    }

    // --- comments ---------------------------------------------------------

    @Test
    void removesCommentsIncludingLeading() throws Exception {
        XsdSchema schema = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- leading file comment -->
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <!-- global comment -->
                  <xs:complexType name="PersonType">
                    <xs:sequence>
                      <!-- inner comment -->
                      <xs:element name="name" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                  <xs:element name="person" type="PersonType"/>
                </xs:schema>
                """);
        String result = transformAndSerialize(schema, COMMENTS_ONLY);
        assertFalse(result.contains("<!--"), result);
        assertFalse(result.contains("comment"), result);
        assertTrue(result.contains("PersonType"), result);
    }

    // --- resolved includes ------------------------------------------------

    @Test
    void removesResolvedIncludeDirectiveButKeepsUnresolved(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("types.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:simpleType name="IncludedType">
                    <xs:restriction base="xs:string"/>
                  </xs:simpleType>
                </xs:schema>
                """);
        String main = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="types.xsd"/>
                  <xs:include schemaLocation="missing.xsd"/>
                  <xs:element name="person" type="IncludedType"/>
                </xs:schema>
                """;
        XsdSchema schema = new XsdNodeFactory().fromString(main, dir);
        long resolvedBefore = schema.getChildren().stream()
                .filter(c -> c instanceof XsdInclude inc && inc.getResolvedPath() != null).count();
        assertEquals(1, resolvedBefore, "fixture should have one resolved include");

        String result = transformAndSerialize(schema, INCLUDES_ONLY);
        assertFalse(result.contains("types.xsd"), result);
        assertTrue(result.contains("missing.xsd"), "unresolved include must stay: " + result);
        assertTrue(result.contains("IncludedType"), "inlined content must stay: " + result);
    }

    // --- unused global components ----------------------------------------

    @Test
    void removesUnusedGlobalsKeepsTransitiveChain() throws Exception {
        XsdSchema schema = parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="person" type="PersonType"/>
                  <xs:complexType name="PersonType">
                    <xs:sequence><xs:element name="address" type="AddressType"/></xs:sequence>
                  </xs:complexType>
                  <xs:complexType name="AddressType">
                    <xs:sequence><xs:element name="zip" type="ZipType"/></xs:sequence>
                  </xs:complexType>
                  <xs:simpleType name="ZipType"><xs:restriction base="xs:string"/></xs:simpleType>
                  <xs:complexType name="UnusedComplex">
                    <xs:sequence><xs:element name="x" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                  <xs:simpleType name="UnusedSimple"><xs:restriction base="xs:string"/></xs:simpleType>
                </xs:schema>
                """);
        String result = transformAndSerialize(schema, SHAKE_ONLY);
        assertTrue(result.contains("PersonType"), result);
        assertTrue(result.contains("AddressType"), result);
        assertTrue(result.contains("ZipType"), result);
        assertFalse(result.contains("UnusedComplex"), result);
        assertFalse(result.contains("UnusedSimple"), result);
    }

    @Test
    void circularTypesKeptWhenReachableRemovedWhenNot() throws Exception {
        String fixture = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  %s
                  <xs:complexType name="FolderType">
                    <xs:sequence><xs:element name="file" type="FileType" minOccurs="0"/></xs:sequence>
                  </xs:complexType>
                  <xs:complexType name="FileType">
                    <xs:sequence><xs:element name="folder" type="FolderType" minOccurs="0"/></xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """;
        String reachable = transformAndSerialize(
                parse(fixture.formatted("<xs:element name=\"root\" type=\"FolderType\"/>")), SHAKE_ONLY);
        assertTrue(reachable.contains("FolderType"), reachable);
        assertTrue(reachable.contains("FileType"), reachable);

        String unreachable = transformAndSerialize(
                parse(fixture.formatted("<xs:element name=\"root\" type=\"xs:string\"/>")), SHAKE_ONLY);
        assertFalse(unreachable.contains("FolderType"), unreachable);
        assertFalse(unreachable.contains("FileType"), unreachable);
    }

    @Test
    void followsPrefixedRefsUnionListExtensionGroupEdges() throws Exception {
        XsdSchema schema = parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="urn:test" targetNamespace="urn:test">
                  <xs:element name="root" type="tns:RootType"/>
                  <xs:complexType name="RootType">
                    <xs:sequence>
                      <xs:element name="u" type="tns:UnionType"/>
                      <xs:element name="l" type="tns:ListType"/>
                      <xs:element name="e" type="tns:ExtendedType"/>
                      <xs:group ref="tns:UsedGroup"/>
                    </xs:sequence>
                    <xs:attributeGroup ref="tns:UsedAttrGroup"/>
                  </xs:complexType>
                  <xs:simpleType name="UnionType">
                    <xs:union memberTypes="tns:MemberA tns:MemberB"/>
                  </xs:simpleType>
                  <xs:simpleType name="MemberA"><xs:restriction base="xs:string"/></xs:simpleType>
                  <xs:simpleType name="MemberB"><xs:restriction base="xs:int"/></xs:simpleType>
                  <xs:simpleType name="ListType">
                    <xs:list itemType="tns:ItemType"/>
                  </xs:simpleType>
                  <xs:simpleType name="ItemType"><xs:restriction base="xs:string"/></xs:simpleType>
                  <xs:complexType name="ExtendedType">
                    <xs:complexContent>
                      <xs:extension base="tns:BaseType"/>
                    </xs:complexContent>
                  </xs:complexType>
                  <xs:complexType name="BaseType">
                    <xs:sequence><xs:element name="b" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                  <xs:group name="UsedGroup">
                    <xs:sequence><xs:element name="g" type="xs:string"/></xs:sequence>
                  </xs:group>
                  <xs:attributeGroup name="UsedAttrGroup">
                    <xs:attribute name="a" type="xs:string"/>
                  </xs:attributeGroup>
                  <xs:group name="UnusedGroup">
                    <xs:sequence><xs:element name="ug" type="xs:string"/></xs:sequence>
                  </xs:group>
                  <xs:attributeGroup name="UnusedAttrGroup">
                    <xs:attribute name="ua" type="xs:string"/>
                  </xs:attributeGroup>
                </xs:schema>
                """);
        String result = transformAndSerialize(schema, SHAKE_ONLY);
        for (String kept : new String[]{"UnionType", "MemberA", "MemberB", "ListType", "ItemType",
                "ExtendedType", "BaseType", "UsedGroup", "UsedAttrGroup"}) {
            assertTrue(result.contains(kept), "expected retained: " + kept + "\n" + result);
        }
        assertFalse(result.contains("UnusedGroup"), result);
        assertFalse(result.contains("UnusedAttrGroup"), result);
    }

    @Test
    void substitutionGroupMembersAndTheirTypesAreKept() throws Exception {
        XsdSchema schema = parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="head" type="HeadType"/>
                  <xs:element name="member" substitutionGroup="head" type="MemberType"/>
                  <xs:complexType name="HeadType">
                    <xs:sequence><xs:element name="h" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                  <xs:complexType name="MemberType">
                    <xs:sequence><xs:element name="m" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """);
        String result = transformAndSerialize(schema, SHAKE_ONLY);
        assertTrue(result.contains("member"), result);
        assertTrue(result.contains("HeadType"), result);
        assertTrue(result.contains("MemberType"), result);
    }

    @Test
    void shakingSkippedWhenRedefinePresent() throws Exception {
        XsdSchema schema = parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:redefine schemaLocation="other.xsd"/>
                  <xs:element name="root" type="xs:string"/>
                  <xs:complexType name="UnusedComplex">
                    <xs:sequence><xs:element name="x" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """);
        String result = transformAndSerialize(schema, SHAKE_ONLY);
        assertTrue(result.contains("UnusedComplex"), "shaking must be skipped with redefine: " + result);
    }

    @Test
    void noneOptionsLeaveSchemaUntouched() throws Exception {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="UnusedComplex">
                    <xs:annotation><xs:documentation>doc</xs:documentation></xs:annotation>
                    <xs:sequence><xs:element name="x" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """;
        XsdSchema untouched = parse(xsd);
        String before = new XsdSerializer().serialize(untouched);
        String after = transformAndSerialize(parse(xsd), FlattenOptions.NONE);
        assertEquals(before, after);
    }
}
