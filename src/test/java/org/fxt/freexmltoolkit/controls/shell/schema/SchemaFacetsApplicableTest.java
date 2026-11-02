package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.model.XsdDatatypeFacets;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRedefine;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which facets a restriction may use, and what it inherits, whatever its base is made of. */
class SchemaFacetsApplicableTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Text"><xs:restriction base="xs:string"><xs:maxLength value="9"/></xs:restriction></xs:simpleType>
              <xs:simpleType name="Short"><xs:restriction base="Text"/></xs:simpleType>
              <xs:simpleType name="Shorter"><xs:restriction base="Short"><xs:minLength value="2"/></xs:restriction></xs:simpleType>
              <xs:simpleType name="Amount"><xs:restriction base="xs:decimal"/></xs:simpleType>
              <xs:simpleType name="Codes"><xs:list itemType="xs:string"/></xs:simpleType>
              <xs:simpleType name="FewCodes"><xs:restriction base="Codes"/></xs:simpleType>
              <xs:simpleType name="AnyId"><xs:union memberTypes="xs:string xs:int"/></xs:simpleType>
              <xs:simpleType name="SomeId"><xs:restriction base="AnyId"/></xs:simpleType>
              <xs:simpleType name="Tokens"><xs:restriction base="xs:NMTOKENS"/></xs:simpleType>
              <xs:simpleType name="InlineList">
                <xs:restriction><xs:simpleType><xs:list itemType="xs:int"/></xs:simpleType></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Lost"><xs:restriction base="gone:Nowhere"/></xs:simpleType>
              <xs:simpleType name="LoopA"><xs:restriction base="LoopB"/></xs:simpleType>
              <xs:simpleType name="LoopB"><xs:restriction base="LoopA"/></xs:simpleType>
            </xs:schema>
            """;

    private static XsdNode find(XsdNode parent, String name) {
        return parent.getChildren().stream().filter(child -> name.equals(child.getName())).findFirst().orElseThrow();
    }

    private static Set<XsdFacetType> applicable(String type) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        return SchemaFacets.applicableFacets(find(schema, type), schema);
    }

    @Test
    void aBuiltInBaseDecides() throws Exception {
        assertEquals(XsdDatatypeFacets.getApplicableFacets("xs:decimal"), applicable("Amount"));
    }

    @Test
    void namedBasesAreFollowedToTheBuiltIn() throws Exception {
        assertEquals(XsdDatatypeFacets.getApplicableFacets("xs:string"), applicable("Shorter"));
    }

    @Test
    void aRestrictionOfAListAllowsTheListFacets() throws Exception {
        assertEquals(XsdDatatypeFacets.LIST_FACETS, applicable("FewCodes"));
        assertFalse(applicable("FewCodes").contains(XsdFacetType.MAX_INCLUSIVE));
    }

    @Test
    void aRestrictionOfAUnionAllowsOnlyPatternEnumerationAndAssertion() throws Exception {
        assertEquals(EnumSet.of(XsdFacetType.PATTERN, XsdFacetType.ENUMERATION, XsdFacetType.ASSERTION),
                applicable("SomeId"));
    }

    @Test
    void aBuiltInListTypeAllowsTheListFacets() throws Exception {
        assertEquals(XsdDatatypeFacets.LIST_FACETS, applicable("Tokens"));
    }

    @Test
    void anInlineBaseDecidesToo() throws Exception {
        assertEquals(XsdDatatypeFacets.LIST_FACETS, applicable("InlineList"));
    }

    @Test
    void anUnknownBaseLimitsNothing() throws Exception {
        assertEquals(EnumSet.allOf(XsdFacetType.class), applicable("Lost"));
    }

    @Test
    void aCircularBaseChainEnds() throws Exception {
        assertEquals(EnumSet.allOf(XsdFacetType.class), applicable("LoopA"));
    }

    @Test
    void aChainOfThreeStepsInheritsEveryFacet() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        List<XsdFacet> inherited = SchemaFacets.resolveBaseChainFacets(find(schema, "Shorter"), schema);
        assertEquals(List.of(XsdFacetType.MAX_LENGTH), inherited.stream().map(XsdFacet::getFacetType).toList());
    }

    @Test
    void aRedefinedTypeReplacesAndBuildsOnItsOriginal(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("base.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:simpleType name="Code"><xs:restriction base="xs:string"><xs:maxLength value="9"/></xs:restriction></xs:simpleType>
                </xs:schema>
                """);
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:redefine schemaLocation="base.xsd">
                    <xs:simpleType name="Code"><xs:restriction base="Code"><xs:minLength value="2"/></xs:restriction></xs:simpleType>
                  </xs:redefine>
                  <xs:element name="code" type="Code"/>
                </xs:schema>
                """;
        Path main = dir.resolve("main.xsd");
        Files.writeString(main, xsd);
        XsdSchema schema = new XsdNodeFactory().fromStringWithSchemaFile(xsd, main, dir);
        XsdNode element = find(schema, "code");
        XsdNode redefined = schema.getChildren().stream().filter(XsdRedefine.class::isInstance).findFirst()
                .orElseThrow().getChildren().getFirst();

        assertEquals(Set.of(XsdFacetType.MIN_LENGTH, XsdFacetType.MAX_LENGTH),
                Set.copyOf(SchemaFacets.resolveReferencedTypeFacets(element, schema).stream()
                        .map(XsdFacet::getFacetType).toList()));
        assertEquals(XsdDatatypeFacets.getApplicableFacets("xs:string"),
                SchemaFacets.applicableFacets(redefined, schema));
    }
}
