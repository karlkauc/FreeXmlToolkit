package org.fxt.freexmltoolkit.controls.shell.schema;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchemaFacets}: collecting the inline facets of a node
 * (element with an inline restriction) for the inspector's facet table.
 */
class SchemaFacetsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Age">
                <xs:simpleType><xs:restriction base="xs:integer">
                  <xs:minInclusive value="0"/>
                  <xs:maxInclusive value="150"/>
                </xs:restriction></xs:simpleType>
              </xs:element>
              <xs:element name="Plain" type="xs:string"/>
            </xs:schema>
            """;

    @Test
    void collectsInlineFacetsOfAnElement() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        List<XsdFacet> facets = SchemaFacets.collect(find(schema, "Age"));

        List<String> names = facets.stream().map(f -> f.getFacetType().getXmlName()).collect(Collectors.toList());
        assertTrue(names.contains("minInclusive"), names.toString());
        assertTrue(names.contains("maxInclusive"), names.toString());
        assertEquals(2, facets.size());
    }

    @Test
    void elementWithoutFacetsYieldsEmptyList() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        assertTrue(SchemaFacets.collect(find(schema, "Plain")).isEmpty());
    }

    @Test
    void nullNodeYieldsEmptyList() {
        assertTrue(SchemaFacets.collect(null).isEmpty());
    }

    private XsdNode find(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = find(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
