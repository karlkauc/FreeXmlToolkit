package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.junit.jupiter.api.Test;

/**
 * Facet resolution through {@code xs:list} item types and {@code xs:union} member types that
 * reference named simple types (the V2 "no Union/List facet support" gap).
 */
class SchemaFacetsListUnionTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Amount">
                <xs:restriction base="xs:decimal"><xs:totalDigits value="9"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="CodeList">
                <xs:list itemType="Code"/>
              </xs:simpleType>
              <xs:simpleType name="CodeOrAmount">
                <xs:union memberTypes="Code Amount"/>
              </xs:simpleType>
              <xs:element name="codes" type="CodeList"/>
            </xs:schema>
            """;

    private XsdNode parse() throws Exception {
        return new XsdNodeFactory().fromString(XSD);
    }

    private XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode c : node.getChildren()) {
            XsdNode f = find(c, name);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    @Test
    void listItemTypeFacetsResolve() throws Exception {
        XsdNode schema = parse();
        List<XsdFacet> facets = SchemaFacets.resolveListUnionFacets(find(schema, "CodeList"), schema);
        assertEquals(1, facets.size(), "CodeList's item type Code contributes maxLength");
    }

    @Test
    void unionMemberTypeFacetsResolve() throws Exception {
        XsdNode schema = parse();
        List<XsdFacet> facets = SchemaFacets.resolveListUnionFacets(find(schema, "CodeOrAmount"), schema);
        assertEquals(2, facets.size(), "union members Code + Amount contribute one facet each");
    }

    @Test
    void elementReferencingAListTypeChainsToTheItemFacets() throws Exception {
        XsdNode schema = parse();
        List<XsdFacet> facets = SchemaFacets.resolveReferencedTypeFacets(find(schema, "codes"), schema);
        assertEquals(1, facets.size(), "codes -> CodeList -> Code maxLength");
    }
}
