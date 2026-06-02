package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.shell.schema.SchemaConstraints.ConstraintInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.junit.jupiter.api.Test;

/** Collection of identity constraints (key/keyref/unique) and assertions for the inspector. */
class SchemaConstraintsTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="catalog">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="book" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
                <xs:key name="bookKey">
                  <xs:selector xpath="book"/>
                  <xs:field xpath="@id"/>
                </xs:key>
                <xs:keyref name="bookRef" refer="bookKey">
                  <xs:selector xpath="ref"/>
                  <xs:field xpath="@book"/>
                </xs:keyref>
              </xs:element>
            </xs:schema>
            """;

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
    void collectsKeyAndKeyrefWithSelectorFieldsAndRefer() throws Exception {
        XsdNode schema = new XsdNodeFactory().fromString(XSD);
        List<ConstraintInfo> constraints = SchemaConstraints.collect(find(schema, "catalog"));
        assertEquals(2, constraints.size(), "catalog has a key and a keyref");

        ConstraintInfo key = constraints.stream().filter(c -> c.kind().equals("key")).findFirst().orElseThrow();
        assertEquals("bookKey", key.name());
        assertTrue(key.detail().contains("selector: book"), key.detail());
        assertTrue(key.detail().contains("fields: @id"), key.detail());

        ConstraintInfo ref = constraints.stream().filter(c -> c.kind().equals("keyref")).findFirst().orElseThrow();
        assertTrue(ref.detail().contains("refer: bookKey"), ref.detail());
    }

    @Test
    void elementWithoutConstraintsYieldsEmpty() throws Exception {
        XsdNode schema = new XsdNodeFactory().fromString(XSD);
        assertTrue(SchemaConstraints.collect(find(schema, "book")).isEmpty());
        assertTrue(SchemaConstraints.collect(null).isEmpty());
    }
}
