package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.junit.jupiter.api.Test;

/**
 * Facet resolution through a chain of named {@code xs:restriction base="..."} simple types
 * (previously only the first level resolved).
 */
class SchemaFacetsBaseChainTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="RootCode">
                <xs:restriction base="xs:string"><xs:pattern value="[A-Z]+"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="BaseCode">
                <xs:restriction base="RootCode"><xs:minLength value="1"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Code">
                <xs:restriction base="BaseCode"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
              <xs:element name="ref" type="Code"/>
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
    void baseChainResolvesAllAncestorFacets() throws Exception {
        XsdNode schema = parse();
        // Code's own facet is inline (maxLength); the base chain adds minLength + pattern.
        List<XsdFacet> base = SchemaFacets.resolveBaseChainFacets(find(schema, "Code"), schema);
        assertEquals(2, base.size(), "Code inherits minLength (BaseCode) + pattern (RootCode)");
    }

    @Test
    void referencedTypeIncludesItsWholeBaseChain() throws Exception {
        XsdNode schema = parse();
        // ref -> Code(maxLength) -> BaseCode(minLength) -> RootCode(pattern) = 3 effective facets.
        List<XsdFacet> all = SchemaFacets.resolveReferencedTypeFacets(find(schema, "ref"), schema);
        assertEquals(3, all.size(), "the referenced type's full base chain resolves");
    }

    @Test
    void circularBaseChainTerminates() throws Exception {
        String cyclic = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:simpleType name="A">
                    <xs:restriction base="B"><xs:minLength value="1"/></xs:restriction>
                  </xs:simpleType>
                  <xs:simpleType name="B">
                    <xs:restriction base="A"><xs:maxLength value="5"/></xs:restriction>
                  </xs:simpleType>
                </xs:schema>
                """;
        XsdNode schema = new XsdNodeFactory().fromString(cyclic);
        // Must not loop forever; A's base chain reaches B's facet then stops (A already visited).
        List<XsdFacet> base = SchemaFacets.resolveBaseChainFacets(find(schema, "A"), schema);
        assertEquals(1, base.size(), "cycle guard stops after B's facet");
    }
}
