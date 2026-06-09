package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.junit.jupiter.api.Test;

/**
 * {@link SchemaFacets#resolveReferencedTypeFacets} resolves the facets a node inherits via a
 * {@code type="..."} reference to a named simple type (the documented "one level of SimpleType
 * resolution" the inline {@link SchemaFacets#collect} does not cover).
 */
class SchemaFacetsInheritedTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string">
                  <xs:minLength value="2"/>
                  <xs:maxLength value="3"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:element name="ref" type="Code"/>
              <xs:element name="plain" type="xs:string"/>
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
    void resolvesFacetsFromAReferencedNamedSimpleType() throws Exception {
        XsdNode schema = parse();
        XsdNode ref = find(schema, "ref");

        // The element itself has no inline facets...
        assertTrue(SchemaFacets.collect(ref).isEmpty(), "ref has no inline facets");
        // ...but inherits Code's two facets via type="Code".
        List<XsdFacet> inherited = SchemaFacets.resolveReferencedTypeFacets(ref, schema);
        assertEquals(2, inherited.size(), "ref inherits minLength + maxLength from Code");
    }

    @Test
    void builtinTypesAndNamedTypesThemselvesHaveNoInheritedFacets() throws Exception {
        XsdNode schema = parse();
        assertTrue(SchemaFacets.resolveReferencedTypeFacets(find(schema, "plain"), schema).isEmpty(),
                "an xs:string element inherits no schema facets");
        assertTrue(SchemaFacets.resolveReferencedTypeFacets(find(schema, "Code"), schema).isEmpty(),
                "the named type itself references no other type");
    }
}
