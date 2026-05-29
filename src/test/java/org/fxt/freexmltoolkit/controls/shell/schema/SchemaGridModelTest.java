package org.fxt.freexmltoolkit.controls.shell.schema;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchemaGridModel}: detecting repeating elements (rendered as an
 * embedded grid in the Graphic view) and collecting their field columns.
 */
class SchemaGridModelTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Funds">
                <xs:complexType><xs:sequence>
                  <xs:element name="Fund" maxOccurs="unbounded">
                    <xs:complexType><xs:sequence>
                      <xs:element name="Name" type="xs:string"/>
                      <xs:element name="Currency" type="xs:string"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:element name="Single" type="xs:string"/>
            </xs:schema>
            """;

    @Test
    void detectsRepeatingElements() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        assertTrue(SchemaGridModel.isRepeating(find(schema, "Fund")), "unbounded element is repeating");
        assertFalse(SchemaGridModel.isRepeating(find(schema, "Single")), "1..1 element is not repeating");
    }

    @Test
    void collectsFieldColumnsOfARepeatingElement() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        List<XsdNode> columns = SchemaGridModel.gridColumns(find(schema, "Fund"));
        List<String> names = columns.stream().map(XsdNode::getName).collect(Collectors.toList());
        assertEquals(List.of("Name", "Currency"), names, "grid columns are the element's direct fields");
    }

    @Test
    void simpleElementHasNoColumns() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        assertTrue(SchemaGridModel.gridColumns(find(schema, "Single")).isEmpty());
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
