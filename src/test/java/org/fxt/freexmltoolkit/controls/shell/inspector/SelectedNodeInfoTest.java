package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SelectedNodeInfo}, the UI-free projection of a selected XsdNode
 * into the inspector's four sections (used by the Tree/Graphic views).
 */
class SelectedNodeInfoTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="FundsXML4">
                <xs:complexType><xs:sequence>
                  <xs:element name="ControlData">
                    <xs:complexType><xs:sequence>
                      <xs:element name="UniqueDocumentID" type="xs:string"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                  <xs:element name="Fund" type="xs:string" maxOccurs="unbounded"/>
                </xs:sequence></xs:complexType>
              </xs:element>
              <xs:complexType name="DataSupplierType"><xs:sequence>
                <xs:element name="Short" type="xs:string"/>
              </xs:sequence></xs:complexType>
            </xs:schema>
            """;

    @Test
    void projectsElementNameTypeAndCardinality() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        XsdNode node = find(schema, "UniqueDocumentID");
        SelectedNodeInfo info = SelectedNodeInfo.of(node);

        assertEquals("Element", info.kind());
        assertEquals("UniqueDocumentID", info.name());
        assertEquals("xs:string", info.type());
        assertEquals("1..1", info.cardinality());
        // The inspector shows the XSD schema XPath (mirrors XsdNode.getXPath()), not an instance path.
        assertEquals(node.getXPath(), info.xpath());
        assertTrue(info.xpath().startsWith("/xs:schema/"), info.xpath());
        assertTrue(info.xpath().endsWith("xs:element[@name='UniqueDocumentID']"), info.xpath());
    }

    @Test
    void namedComplexTypeShowsSchemaXPath() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        SelectedNodeInfo info = SelectedNodeInfo.of(find(schema, "DataSupplierType"));
        assertEquals("/xs:schema/xs:complexType[@name='DataSupplierType']", info.xpath());
    }

    @Test
    void unboundedMaxOccursRendersAsStar() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        SelectedNodeInfo info = SelectedNodeInfo.of(find(schema, "Fund"));
        assertEquals("1..*", info.cardinality());
        assertTrue(info.xpath().endsWith("xs:element[@name='Fund']"), info.xpath());
    }

    @Test
    void nullNodeYieldsEmptyInfo() {
        SelectedNodeInfo info = SelectedNodeInfo.of(null);
        assertNotNull(info);
        assertEquals("", info.name());
        assertEquals("/", info.xpath());
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
