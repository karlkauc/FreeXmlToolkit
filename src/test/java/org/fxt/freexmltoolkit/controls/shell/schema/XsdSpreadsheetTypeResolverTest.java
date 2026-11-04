package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.service.SpreadsheetCellKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class XsdSpreadsheetTypeResolverTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Money">
                <xs:restriction base="xs:decimal"><xs:fractionDigits value="2"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Amount">
                <xs:restriction base="Money"><xs:minInclusive value="0"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Codes"><xs:list itemType="xs:int"/></xs:simpleType>
              <xs:simpleType name="Mixed"><xs:union memberTypes="xs:int xs:date"/></xs:simpleType>
              <xs:simpleType name="Loop"><xs:restriction base="Loop2"/></xs:simpleType>
              <xs:simpleType name="Loop2"><xs:restriction base="Loop"/></xs:simpleType>
              <xs:complexType name="PriceType">
                <xs:simpleContent>
                  <xs:extension base="Amount"><xs:attribute name="ccy" type="xs:string"/></xs:extension>
                </xs:simpleContent>
              </xs:complexType>
              <xs:complexType name="FolderType">
                <xs:sequence>
                  <xs:element name="size" type="xs:long"/>
                  <xs:element ref="folder" minOccurs="0" maxOccurs="unbounded"/>
                </xs:sequence>
              </xs:complexType>
              <xs:element name="folder" type="FolderType"/>
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="amount" type="Amount"/>
                    <xs:element name="price" type="PriceType"/>
                    <xs:element name="level">
                      <xs:simpleType>
                        <xs:restriction base="xs:int">
                          <xs:enumeration value="1"/><xs:enumeration value="2"/>
                        </xs:restriction>
                      </xs:simpleType>
                    </xs:element>
                    <xs:element name="day" type="xs:date"/>
                    <xs:element name="codes" type="Codes"/>
                    <xs:element name="mixed" type="Mixed"/>
                    <xs:element name="loop" type="Loop"/>
                    <xs:element name="name" type="xs:string"/>
                    <xs:element ref="folder"/>
                  </xs:sequence>
                  <xs:attribute name="paid" type="xs:boolean"/>
                  <xs:attribute name="at" type="xs:dateTime"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>""";

    private static XsdSpreadsheetTypeResolver resolver;

    @BeforeAll
    static void parse() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        resolver = new XsdSpreadsheetTypeResolver(schema);
    }

    @Test
    void resolvesBuiltinBasesThroughTypeChains() {
        assertEquals(SpreadsheetCellKind.DECIMAL, resolver.kindOf("/order/amount", null));
        assertEquals(SpreadsheetCellKind.DECIMAL, resolver.kindOf("/order/price", null), "simpleContent extension");
        assertEquals(SpreadsheetCellKind.INTEGER, resolver.kindOf("/order/level", null), "enumeration keeps the base");
        assertEquals(SpreadsheetCellKind.DATE, resolver.kindOf("/order/day", null));
        assertEquals(SpreadsheetCellKind.TEXT, resolver.kindOf("/order/name", null));
    }

    @Test
    void attributesAndRecursiveRefs() {
        assertEquals(SpreadsheetCellKind.BOOLEAN, resolver.kindOf("/order", "paid"));
        assertEquals(SpreadsheetCellKind.DATE_TIME, resolver.kindOf("/order", "at"));
        assertEquals(SpreadsheetCellKind.TEXT, resolver.kindOf("/order/price", "ccy"));
        assertEquals(SpreadsheetCellKind.TEXT, resolver.kindOf("/order", "xsi:type"));
        assertEquals(SpreadsheetCellKind.INTEGER, resolver.kindOf("/order/folder/folder[2]/folder/size", null));
    }

    @Test
    void listsUnionsCyclesAndUnknownPathsAreText() {
        assertEquals(SpreadsheetCellKind.TEXT, resolver.kindOf("/order/codes", null));
        assertEquals(SpreadsheetCellKind.TEXT, resolver.kindOf("/order/mixed", null));
        assertEquals(SpreadsheetCellKind.TEXT, resolver.kindOf("/order/loop", null));
        assertEquals(SpreadsheetCellKind.TEXT, resolver.kindOf("/order/unknown", null));
        assertEquals(SpreadsheetCellKind.TEXT, resolver.kindOf("/other", null));
    }
}
