package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SchemaDerivation}: base type + derivation of inline and named types, and a
 * particle's position in its compositor.
 */
class SchemaDerivationTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="CodeType">
                <xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="CodeList"><xs:list itemType="CodeType"/></xs:simpleType>
              <xs:complexType name="AmountType">
                <xs:simpleContent><xs:extension base="xs:decimal"/></xs:simpleContent>
              </xs:complexType>
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="code" type="CodeType"/>
                    <xs:element name="amount" type="AmountType"/>
                    <xs:element name="inline">
                      <xs:simpleType><xs:restriction base="xs:int"/></xs:simpleType>
                    </xs:element>
                    <xs:choice>
                      <xs:element name="a" type="xs:string"/>
                      <xs:element name="b" type="xs:string"/>
                    </xs:choice>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:string"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static XsdNode find(XsdNode node, String name) {
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

    @Test
    void elementReferencingANamedSimpleTypeReportsItsRestriction() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        var d = SchemaDerivation.of(find(schema, "code"), schema).orElseThrow();
        assertEquals("xs:string", d.base());
        assertEquals("restriction", d.method());
    }

    @Test
    void namedComplexTypeWithSimpleContentReportsItsExtension() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        var d = SchemaDerivation.of(find(schema, "amount"), schema).orElseThrow();
        assertEquals("xs:decimal", d.base());
        assertEquals("extension", d.method());
        assertEquals("extension", SchemaDerivation.of(find(schema, "AmountType"), schema).orElseThrow().method());
    }

    @Test
    void inlineTypeAndListAreFound() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        assertEquals("xs:int", SchemaDerivation.of(find(schema, "inline"), schema).orElseThrow().base());
        var list = SchemaDerivation.of(find(schema, "CodeList"), schema).orElseThrow();
        assertEquals("CodeType", list.base());
        assertEquals("list", list.method());
    }

    @Test
    void builtInTypesHaveNoDerivation() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        assertTrue(SchemaDerivation.of(find(schema, "a"), schema).isEmpty());
        assertTrue(SchemaDerivation.of(find(schema, "id"), schema).isEmpty());
        assertTrue(SchemaDerivation.of(null, schema).isEmpty());
    }

    @Test
    void orderCountsTheParticlesOfTheCompositor() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        // code, amount, inline and the nested choice are the four particles of the sequence.
        assertEquals("1 of 4 · sequence", SchemaDerivation.order(find(schema, "code")).orElseThrow());
        assertEquals("3 of 4 · sequence", SchemaDerivation.order(find(schema, "inline")).orElseThrow());
        assertEquals("2 of 2 · choice", SchemaDerivation.order(find(schema, "b")).orElseThrow());
        // Attributes and global components sit in no compositor.
        assertTrue(SchemaDerivation.order(find(schema, "id")).isEmpty());
        assertTrue(SchemaDerivation.order(find(schema, "root")).isEmpty());
    }
}
