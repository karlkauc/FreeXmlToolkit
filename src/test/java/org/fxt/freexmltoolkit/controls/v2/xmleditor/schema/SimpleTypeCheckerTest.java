package org.fxt.freexmltoolkit.controls.v2.xmleditor.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.XsdType;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link SimpleTypeChecker} through {@link XsdSchemaAdapter}: the type behind an element or attribute is built with
 * XSD semantics — named restrictions of any depth (FundsXML {@code PercentageType}), simple content of complex types
 * (FundsXML {@code AmountType}), implicit bounds of derived built-ins, XSD regular expressions and patterns of
 * several derivation steps, whiteSpace, digits and exclusive bounds, lists, unions of enumerations and the
 * Gregorian/binary types the old checks did not know.
 */
class SimpleTypeCheckerTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:t" targetNamespace="urn:t"
                       elementFormDefault="qualified">
              <xs:element name="Root">
                <xs:complexType><xs:sequence>
                  <xs:element name="TotalPercentage" type="t:PercentageType"/>
                  <xs:element name="Amount" type="t:AmountType"/>
                  <xs:element name="Small" type="xs:unsignedByte"/>
                  <xs:element name="Price" type="t:PriceType"/>
                  <xs:element name="Code" type="t:NarrowCodeType"/>
                  <xs:element name="Name" type="t:NameType"/>
                  <xs:element name="Short" type="t:ShortTokenType"/>
                  <xs:element name="Day" type="xs:date"/>
                  <xs:element name="Stamp" type="xs:dateTimeStamp"/>
                  <xs:element name="Sizes" type="t:SizesType"/>
                  <xs:element name="Mode" type="t:ModeType"/>
                  <xs:element name="Year" type="xs:gYear"/>
                  <xs:element name="Blob" type="xs:hexBinary"/>
                  <xs:element name="Inline">
                    <xs:simpleType><xs:restriction base="xs:integer">
                      <xs:minInclusive value="1"/><xs:maxInclusive value="10"/>
                    </xs:restriction></xs:simpleType>
                  </xs:element>
                  <xs:element name="Folder"><xs:complexType><xs:sequence>
                    <xs:element name="Leaf" type="xs:string"/></xs:sequence></xs:complexType></xs:element>
                </xs:sequence>
                <xs:attribute name="rate" type="t:PercentageType"/>
                </xs:complexType>
              </xs:element>
              <xs:simpleType name="PercentageType"><xs:restriction base="xs:decimal"/></xs:simpleType>
              <xs:complexType name="AmountType">
                <xs:simpleContent><xs:extension base="t:AmountValueType">
                  <xs:attribute name="ccy" type="xs:string"/>
                </xs:extension></xs:simpleContent>
              </xs:complexType>
              <xs:simpleType name="AmountValueType">
                <xs:restriction base="xs:decimal"><xs:totalDigits value="18"/><xs:fractionDigits value="4"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="PriceType">
                <xs:restriction base="xs:decimal">
                  <xs:minExclusive value="0"/><xs:maxInclusive value="100"/><xs:fractionDigits value="2"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="CodeType">
                <xs:restriction base="xs:string"><xs:pattern value="[A-Z]{3}"/><xs:pattern value="\\d{3}"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="NarrowCodeType">
                <xs:restriction base="t:CodeType"><xs:pattern value="[A-M].*|\\d+"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="NameType">
                <xs:restriction base="xs:string"><xs:pattern value="\\i\\c*"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="ShortTokenType">
                <xs:restriction base="xs:token"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="SizesType"><xs:list itemType="xs:int"/></xs:simpleType>
              <xs:simpleType name="ModeType"><xs:union memberTypes="t:ModeA t:ModeB"/></xs:simpleType>
              <xs:simpleType name="ModeA">
                <xs:restriction base="xs:string"><xs:enumeration value="a"/><xs:enumeration value="b"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="ModeB">
                <xs:restriction base="xs:string"><xs:enumeration value="c"/></xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    private static XsdSchemaAdapter adapter;

    @BeforeAll
    static void loadSchema(@TempDir Path dir) throws Exception {
        Path xsd = dir.resolve("t.xsd");
        Files.writeString(xsd, XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.TRUE);
        adapter = new XsdSchemaAdapter();
        adapter.setXsdDocumentationData(service.xsdDocumentationData);
    }

    private static XmlSchemaProvider.ElementTypeInfo info(String leaf) {
        return adapter.getElementTypeInfo("/Root/" + leaf).orElseThrow();
    }

    private static SimpleTypeChecker checker(String leaf) {
        SimpleTypeChecker checker = info(leaf).checker();
        assertNotNull(checker, leaf + " has a checker");
        return checker;
    }

    private static void valid(String leaf, String... values) {
        for (String value : values) {
            assertTrue(checker(leaf).isValid(value), leaf + " accepts '" + value + "': " + checker(leaf).check(value));
        }
    }

    private static void invalid(String leaf, String... values) {
        for (String value : values) {
            var result = checker(leaf).check(value);
            assertFalse(result.isValid(), leaf + " rejects '" + value + "'");
            assertNotNull(result.errorMessage());
        }
    }

    @Test
    void percentageTypeIsADecimalWithFractions() {
        assertEquals(XsdType.DECIMAL, info("TotalPercentage").xsdType());
        valid("TotalPercentage", "3.5", "0.035", "-12.25", "100", "12345678901234567890.123456789");
        invalid("TotalPercentage", "3,5", "abc", "1E3");
        var rate = adapter.getAttributeTypeInfo("/Root", "rate").orElseThrow();
        assertEquals(XsdType.DECIMAL, rate.xsdType(), "attributes resolve the same way");
        assertFalse(adapter.validateAttributeValue("/Root", "rate", "x").isValid());
    }

    @Test
    void simpleContentFollowsTheExtensionBase() {
        assertEquals(XsdType.DECIMAL, info("Amount").xsdType(), "was STRING: simple content had no restriction");
        assertEquals("4", info("Amount").facets().get("fractionDigits"));
        valid("Amount", "1234.5678");
        invalid("Amount", "1.23456", "1234567890123456789");
    }

    @Test
    void derivedBuiltinsKeepTheirBounds() {
        assertEquals(XsdType.INTEGER, info("Small").xsdType());
        valid("Small", "0", "255");
        invalid("Small", "256", "-1", "1.5");
        valid("Inline", "1", "10");
        invalid("Inline", "0", "11");
    }

    @Test
    void exclusiveBoundsAndFractionDigits() {
        valid("Price", "0.01", "100", "99.99");
        invalid("Price", "0", "100.01", "1.234");
    }

    @Test
    void patternsOfEveryDerivationStepMustHold() {
        // Step 1: [A-Z]{3} or \d{3}; step 2: [A-M].* or \d+
        valid("Code", "ABC", "123");
        invalid("Code", "XYZ", "AB", "12");
        assertEquals(java.util.Set.of("[A-Z]{3}|\\d{3}", "[A-M].*|\\d+"),
                java.util.Set.of(info("Code").facets().get("pattern").split("\n")), "one line per derivation step");
    }

    @Test
    void xsdRegularExpressionsAreNotJavaRegex() {
        valid("Name", "abc", "_x-1", "a.b");
        invalid("Name", "1abc", "-x");
    }

    @Test
    void whiteSpaceIsCollapsedBeforeTheLengthIsChecked() {
        valid("Short", " ab ", "a  b");
        invalid("Short", "abcd");
    }

    @Test
    void datesTimezonesAndGregorianTypes() {
        assertEquals(XsdType.DATE, info("Day").xsdType());
        valid("Day", "2024-02-29", "2024-01-01Z", "2024-01-01-05:00");
        invalid("Day", "2023-02-29", "01.01.2024");
        assertEquals(XsdType.DATE_TIME, info("Stamp").xsdType());
        valid("Stamp", "2024-01-01T10:00:00.5+02:00");
        invalid("Stamp", "2024-01-01T10:00:00");
        assertEquals(XsdType.G_YEAR, info("Year").xsdType());
        valid("Year", "2024", "-0044");
        invalid("Year", "24", "2024-01");
        assertEquals(XsdType.HEX_BINARY, info("Blob").xsdType());
        valid("Blob", "0FB7", "");
        invalid("Blob", "0FB", "XY");
    }

    @Test
    void listsAndUnions() {
        assertEquals(XsdType.STRING, info("Sizes").xsdType());
        valid("Sizes", "1 2  3", "");
        invalid("Sizes", "1 x");
        assertEquals(XsdType.ENUMERATION, info("Mode").xsdType(), "a union of enumerations is a dropdown");
        assertEquals(List.of("a", "b", "c"), info("Mode").enumerationValues());
        valid("Mode", "a", "c");
        invalid("Mode", "d");
    }

    @Test
    void elementOnlyContentHasNoChecker() {
        assertNull(info("Folder").checker());
    }

    @Test
    void builtinCheckersAndUnknownNames() {
        assertTrue(SimpleTypeChecker.builtin("boolean").orElseThrow().isValid("1"));
        assertFalse(SimpleTypeChecker.builtin("boolean").orElseThrow().isValid("yes"));
        assertTrue(SimpleTypeChecker.builtin("noSuchType").isEmpty());
        assertTrue(SimpleTypeChecker.builtin("anySimpleType").isEmpty(), "accepts everything: nothing to check");
    }
}
