package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round trips of the schema's top-level content: document order, schema-level annotations,
 * notations and defaultOpenContent must survive a save.
 */
class XsdSchemaLevelRoundTripTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:zz="urn:zz" xmlns:aa="urn:aa"
                       targetNamespace="urn:t" xmlns="urn:t" elementFormDefault="qualified">
              <xs:annotation>
                <xs:documentation>Schema intro</xs:documentation>
              </xs:annotation>
              <xs:simpleType name="Zeta">
                <xs:restriction base="xs:string"/>
              </xs:simpleType>
              <!-- describes beta -->
              <xs:element name="beta" type="xs:string"/>
              <xs:annotation>
                <xs:documentation>Between components</xs:documentation>
              </xs:annotation>
              <xs:complexType name="Alpha">
                <xs:sequence/>
              </xs:complexType>
              <xs:notation name="png" public="image/png" system="viewer.exe"/>
              <xs:element name="alpha" type="Alpha"/>
            </xs:schema>
            """;

    private static String roundTrip(String xsd) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(xsd);
        return new XsdSerializer().serialize(schema, XsdSortOrder.PRESERVE);
    }

    @Test
    @DisplayName("PRESERVE keeps the document order of top-level components")
    void keepsDocumentOrder() throws Exception {
        String out = roundTrip(XSD);
        int zeta = out.indexOf("name=\"Zeta\"");
        int comment = out.indexOf("describes beta");
        int beta = out.indexOf("name=\"beta\"");
        int alphaType = out.indexOf("name=\"Alpha\"");
        int alpha = out.indexOf("name=\"alpha\"");
        assertTrue(zeta > 0 && zeta < comment && comment < beta && beta < alphaType && alphaType < alpha, out);
    }

    @Test
    @DisplayName("Schema-level annotations are written back at their positions")
    void keepsSchemaLevelAnnotations() throws Exception {
        String out = roundTrip(XSD);
        int intro = out.indexOf("Schema intro");
        int between = out.indexOf("Between components");
        assertTrue(intro > 0, out);
        assertTrue(between > 0, out);
        assertTrue(intro < out.indexOf("name=\"Zeta\""), out);
        assertTrue(between > out.indexOf("name=\"beta\"") && between < out.indexOf("name=\"Alpha\""), out);
        // the second round trip is stable
        assertEquals(out, roundTrip(out));
    }

    @Test
    @DisplayName("xs:notation survives a round trip")
    void keepsNotation() throws Exception {
        String out = roundTrip(XSD);
        assertTrue(out.contains("<xs:notation name=\"png\" public=\"image/png\" system=\"viewer.exe\"/>"), out);
        assertTrue(out.indexOf("name=\"png\"") < out.indexOf("name=\"alpha\""), out);
    }

    @Test
    @DisplayName("Schema-level xs:defaultOpenContent survives a round trip")
    void keepsDefaultOpenContent() throws Exception {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:defaultOpenContent mode="suffix">
                    <xs:any namespace="##other" processContents="lax"/>
                  </xs:defaultOpenContent>
                  <xs:element name="a" type="xs:string"/>
                </xs:schema>
                """;
        String out = roundTrip(xsd);
        assertTrue(out.contains("<xs:defaultOpenContent mode=\"suffix\">"), out);
        assertTrue(out.indexOf("defaultOpenContent") < out.indexOf("name=\"a\""), out);
        assertTrue(out.contains("##other"), out);
    }
}
