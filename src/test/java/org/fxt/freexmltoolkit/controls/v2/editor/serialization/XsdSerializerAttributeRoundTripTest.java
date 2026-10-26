package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

/**
 * An attribute's documentation and inline simple type survive parse + serialize; before, every
 * model edit in the Tree/Graphic view wrote {@code <xs:attribute name="s"/>} and lost both.
 */
class XsdSerializerAttributeRoundTripTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType>
                  <xs:attribute name="status" use="required">
                    <xs:annotation><xs:documentation>Order status</xs:documentation></xs:annotation>
                    <xs:simpleType>
                      <xs:restriction base="xs:string">
                        <xs:enumeration value="open"/>
                        <xs:enumeration value="closed"/>
                      </xs:restriction>
                    </xs:simpleType>
                  </xs:attribute>
                  <xs:attribute name="plain" type="xs:int"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @Test
    void attributeAnnotationAndInlineSimpleTypeRoundTrip() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        String out = new XsdSerializer().serialize(schema);
        assertTrue(out.contains("<xs:documentation>Order status</xs:documentation>"), out);
        assertTrue(out.contains("<xs:enumeration value=\"closed\"/>"), out);
        assertTrue(out.contains("<xs:attribute name=\"plain\" type=\"xs:int\"/>"), "simple attributes stay self-closing");
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new StreamSource(new StringReader(out)));

        String again = new XsdSerializer().serialize(new XsdNodeFactory().fromString(out));
        assertEquals(out, again, "stable across a second round trip");
    }
}
