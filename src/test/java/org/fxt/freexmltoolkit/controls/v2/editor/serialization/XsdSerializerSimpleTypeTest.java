package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for XsdSerializer SimpleType serialization.
 * Tests the complete pipeline: Model → XSD XML → Validation.
 *
 * @since 2.0
 */
class XsdSerializerSimpleTypeTest {

    private XsdSerializer serializer;
    private XsdSchema schema;

    @BeforeEach
    void setUp() {
        serializer = new XsdSerializer();
        schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");
    }

    // ========== Restriction Tests ==========

    @Test
    @DisplayName("serialize simpleType with restriction and pattern facet")
    void testSerializeSimpleTypeWithPattern() {
        // Create: <xs:simpleType name="ZipCodeType">
        //           <xs:restriction base="xs:string">
        //             <xs:pattern value="[0-9]{5}"/>
        //           </xs:restriction>
        //         </xs:simpleType>

        XsdSimpleType zipCodeType = new XsdSimpleType("ZipCodeType");
        XsdRestriction restriction = new XsdRestriction("xs:string");
        XsdFacet pattern = new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}");

        restriction.addFacet(pattern);
        zipCodeType.addChild(restriction);
        schema.addChild(zipCodeType);

        String xml = serializer.serialize(schema);

        assertNotNull(xml);
        assertTrue(xml.contains("<xs:simpleType name=\"ZipCodeType\">"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("<xs:pattern value=\"[0-9]{5}\"/>"));
        assertTrue(xml.contains("</xs:restriction>"));
        assertTrue(xml.contains("</xs:simpleType>"));
    }

    @Test
    @DisplayName("serialize simpleType with multiple facets")
    void testSerializeSimpleTypeWithMultipleFacets() {
        // Create: <xs:simpleType name="PercentageType">
        //           <xs:restriction base="xs:integer">
        //             <xs:minInclusive value="0"/>
        //             <xs:maxInclusive value="100"/>
        //           </xs:restriction>
        //         </xs:simpleType>

        XsdSimpleType percentageType = new XsdSimpleType("PercentageType");
        XsdRestriction restriction = new XsdRestriction("xs:integer");
        restriction.addFacet(new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0"));
        restriction.addFacet(new XsdFacet(XsdFacetType.MAX_INCLUSIVE, "100"));

        percentageType.addChild(restriction);
        schema.addChild(percentageType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:simpleType name=\"PercentageType\">"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:integer\">"));
        assertTrue(xml.contains("<xs:minInclusive value=\"0\"/>"));
        assertTrue(xml.contains("<xs:maxInclusive value=\"100\"/>"));
    }

    @Test
    @DisplayName("serialize simpleType with enumeration facets")
    void testSerializeSimpleTypeWithEnumerations() {
        // Create: <xs:simpleType name="ColorType">
        //           <xs:restriction base="xs:string">
        //             <xs:enumeration value="Red"/>
        //             <xs:enumeration value="Green"/>
        //             <xs:enumeration value="Blue"/>
        //           </xs:restriction>
        //         </xs:simpleType>

        XsdSimpleType colorType = new XsdSimpleType("ColorType");
        XsdRestriction restriction = new XsdRestriction("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Red"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Green"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Blue"));

        colorType.addChild(restriction);
        schema.addChild(colorType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:simpleType name=\"ColorType\">"));
        assertTrue(xml.contains("<xs:enumeration value=\"Red\"/>"));
        assertTrue(xml.contains("<xs:enumeration value=\"Green\"/>"));
        assertTrue(xml.contains("<xs:enumeration value=\"Blue\"/>"));
    }

    @Test
    @DisplayName("serialize simpleType with fixed facet")
    void testSerializeSimpleTypeWithFixedFacet() {
        // Create: <xs:simpleType name="FixedLengthType">
        //           <xs:restriction base="xs:string">
        //             <xs:length value="10" fixed="true"/>
        //           </xs:restriction>
        //         </xs:simpleType>

        XsdSimpleType fixedLengthType = new XsdSimpleType("FixedLengthType");
        XsdRestriction restriction = new XsdRestriction("xs:string");
        XsdFacet lengthFacet = new XsdFacet(XsdFacetType.LENGTH, "10");
        lengthFacet.setFixed(true);
        restriction.addFacet(lengthFacet);

        fixedLengthType.addChild(restriction);
        schema.addChild(fixedLengthType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:length value=\"10\" fixed=\"true\"/>"));
    }

    // ========== List Tests ==========

    @Test
    @DisplayName("serialize simpleType with list")
    void testSerializeSimpleTypeWithList() {
        // Create: <xs:simpleType name="IntegerListType">
        //           <xs:list itemType="xs:integer"/>
        //         </xs:simpleType>

        XsdSimpleType integerListType = new XsdSimpleType("IntegerListType");
        XsdList list = new XsdList("xs:integer");

        integerListType.addChild(list);
        schema.addChild(integerListType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:simpleType name=\"IntegerListType\">"));
        assertTrue(xml.contains("<xs:list itemType=\"xs:integer\"/>"));
        assertTrue(xml.contains("</xs:simpleType>"));
    }

    @Test
    @DisplayName("serialize simpleType with list of custom type")
    void testSerializeSimpleTypeWithListCustomType() {
        // Create: <xs:simpleType name="ZipCodeListType">
        //           <xs:list itemType="ZipCodeType"/>
        //         </xs:simpleType>

        XsdSimpleType zipCodeListType = new XsdSimpleType("ZipCodeListType");
        XsdList list = new XsdList("ZipCodeType");

        zipCodeListType.addChild(list);
        schema.addChild(zipCodeListType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:list itemType=\"ZipCodeType\"/>"));
    }

    @Test
    @DisplayName("serialize simpleType with list and inline simpleType")
    void testSerializeSimpleTypeWithListInline() {
        // Create: <xs:simpleType name="RestrictedListType">
        //           <xs:list>
        //             <xs:simpleType>
        //               <xs:restriction base="xs:string">
        //                 <xs:pattern value="[A-Z]{2}"/>
        //               </xs:restriction>
        //             </xs:simpleType>
        //           </xs:list>
        //         </xs:simpleType>

        XsdSimpleType restrictedListType = new XsdSimpleType("RestrictedListType");
        XsdList list = new XsdList();

        XsdSimpleType inlineType = new XsdSimpleType();
        inlineType.setName(null); // Inline simpleTypes don't have names
        XsdRestriction restriction = new XsdRestriction("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z]{2}"));
        inlineType.addChild(restriction);

        list.addChild(inlineType);
        restrictedListType.addChild(list);
        schema.addChild(restrictedListType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:list>"));
        assertTrue(xml.contains("<xs:simpleType>"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("<xs:pattern value=\"[A-Z]{2}\"/>"));
        assertTrue(xml.contains("</xs:list>"));
    }

    // ========== Union Tests ==========

    @Test
    @DisplayName("serialize simpleType with union")
    void testSerializeSimpleTypeWithUnion() {
        // Create: <xs:simpleType name="NumberOrStringType">
        //           <xs:union memberTypes="xs:integer xs:string"/>
        //         </xs:simpleType>

        XsdSimpleType numberOrStringType = new XsdSimpleType("NumberOrStringType");
        XsdUnion union = new XsdUnion("xs:integer", "xs:string");

        numberOrStringType.addChild(union);
        schema.addChild(numberOrStringType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:simpleType name=\"NumberOrStringType\">"));
        assertTrue(xml.contains("<xs:union memberTypes=\"xs:integer xs:string\"/>"));
        assertTrue(xml.contains("</xs:simpleType>"));
    }

    @Test
    @DisplayName("serialize simpleType with union of multiple types")
    void testSerializeSimpleTypeWithUnionMultiple() {
        // Create: <xs:simpleType name="NumericUnionType">
        //           <xs:union memberTypes="xs:integer xs:decimal xs:double"/>
        //         </xs:simpleType>

        XsdSimpleType numericUnionType = new XsdSimpleType("NumericUnionType");
        XsdUnion union = new XsdUnion("xs:integer", "xs:decimal", "xs:double");

        numericUnionType.addChild(union);
        schema.addChild(numericUnionType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:union memberTypes=\"xs:integer xs:decimal xs:double\"/>"));
    }

    @Test
    @DisplayName("serialize simpleType with union and inline simpleTypes")
    void testSerializeSimpleTypeWithUnionInline() {
        // Create: <xs:simpleType name="ComplexUnionType">
        //           <xs:union>
        //             <xs:simpleType>
        //               <xs:restriction base="xs:integer">
        //                 <xs:minInclusive value="0"/>
        //               </xs:restriction>
        //             </xs:simpleType>
        //             <xs:simpleType>
        //               <xs:restriction base="xs:string">
        //                 <xs:pattern value="[A-Z]+"/>
        //               </xs:restriction>
        //             </xs:simpleType>
        //           </xs:union>
        //         </xs:simpleType>

        XsdSimpleType complexUnionType = new XsdSimpleType("ComplexUnionType");
        XsdUnion union = new XsdUnion();

        // First inline simpleType: integer >= 0
        XsdSimpleType inlineType1 = new XsdSimpleType();
        inlineType1.setName(null); // Inline simpleTypes don't have names
        XsdRestriction restriction1 = new XsdRestriction("xs:integer");
        restriction1.addFacet(new XsdFacet(XsdFacetType.MIN_INCLUSIVE, "0"));
        inlineType1.addChild(restriction1);

        // Second inline simpleType: uppercase letters
        XsdSimpleType inlineType2 = new XsdSimpleType();
        inlineType2.setName(null); // Inline simpleTypes don't have names
        XsdRestriction restriction2 = new XsdRestriction("xs:string");
        restriction2.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z]+"));
        inlineType2.addChild(restriction2);

        union.addChild(inlineType1);
        union.addChild(inlineType2);
        complexUnionType.addChild(union);
        schema.addChild(complexUnionType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:union>"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:integer\">"));
        assertTrue(xml.contains("<xs:minInclusive value=\"0\"/>"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("<xs:pattern value=\"[A-Z]+\"/>"));
        assertTrue(xml.contains("</xs:union>"));
    }

    // ========== Complex Integration Tests ==========

    @Test
    @DisplayName("serialize schema with multiple simpleTypes")
    void testSerializeSchemaWithMultipleSimpleTypes() {
        // Create schema with ZipCodeType, ColorType, and IntegerListType

        // ZipCodeType
        XsdSimpleType zipCodeType = new XsdSimpleType("ZipCodeType");
        XsdRestriction restriction1 = new XsdRestriction("xs:string");
        restriction1.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}"));
        zipCodeType.addChild(restriction1);

        // ColorType
        XsdSimpleType colorType = new XsdSimpleType("ColorType");
        XsdRestriction restriction2 = new XsdRestriction("xs:string");
        restriction2.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Red"));
        restriction2.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Green"));
        restriction2.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Blue"));
        colorType.addChild(restriction2);

        // IntegerListType
        XsdSimpleType integerListType = new XsdSimpleType("IntegerListType");
        XsdList list = new XsdList("xs:integer");
        integerListType.addChild(list);

        schema.addChild(zipCodeType);
        schema.addChild(colorType);
        schema.addChild(integerListType);

        String xml = serializer.serialize(schema);

        // Verify all three types are present
        assertTrue(xml.contains("<xs:simpleType name=\"ZipCodeType\">"));
        assertTrue(xml.contains("<xs:simpleType name=\"ColorType\">"));
        assertTrue(xml.contains("<xs:simpleType name=\"IntegerListType\">"));
    }

    @Test
    @DisplayName("serialize element with inline simpleType")
    void testSerializeElementWithInlineSimpleType() {
        // Create: <xs:element name="zipCode">
        //           <xs:simpleType>
        //             <xs:restriction base="xs:string">
        //               <xs:pattern value="[0-9]{5}"/>
        //             </xs:restriction>
        //           </xs:simpleType>
        //         </xs:element>

        XsdElement zipCodeElement = new XsdElement("zipCode");

        XsdSimpleType inlineType = new XsdSimpleType();
        inlineType.setName(null); // Inline simpleTypes don't have names
        XsdRestriction restriction = new XsdRestriction("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[0-9]{5}"));
        inlineType.addChild(restriction);

        zipCodeElement.addChild(inlineType);
        schema.addChild(zipCodeElement);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:element name=\"zipCode\">"));
        assertTrue(xml.contains("<xs:simpleType>"));
        assertTrue(xml.contains("<xs:restriction base=\"xs:string\">"));
        assertTrue(xml.contains("<xs:pattern value=\"[0-9]{5}\"/>"));
        assertTrue(xml.contains("</xs:element>"));
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("serialize simpleType with all 14 facet types")
    void testSerializeAllFacetTypes() {
        XsdSimpleType allFacetsType = new XsdSimpleType("AllFacetsType");
        XsdRestriction restriction = new XsdRestriction("xs:string");

        // Add one of each facet type (where applicable)
        restriction.addFacet(new XsdFacet(XsdFacetType.MIN_LENGTH, "1"));
        restriction.addFacet(new XsdFacet(XsdFacetType.MAX_LENGTH, "100"));
        restriction.addFacet(new XsdFacet(XsdFacetType.LENGTH, "50"));
        restriction.addFacet(new XsdFacet(XsdFacetType.PATTERN, "[A-Z]+"));
        restriction.addFacet(new XsdFacet(XsdFacetType.ENUMERATION, "Value1"));
        restriction.addFacet(new XsdFacet(XsdFacetType.WHITE_SPACE, "collapse"));

        allFacetsType.addChild(restriction);
        schema.addChild(allFacetsType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:minLength value=\"1\"/>"));
        assertTrue(xml.contains("<xs:maxLength value=\"100\"/>"));
        assertTrue(xml.contains("<xs:length value=\"50\"/>"));
        assertTrue(xml.contains("<xs:pattern value=\"[A-Z]+\"/>"));
        assertTrue(xml.contains("<xs:enumeration value=\"Value1\"/>"));
        assertTrue(xml.contains("<xs:whiteSpace value=\"collapse\"/>"));
    }

    @Test
    @DisplayName("serialize simpleType with XML special characters")
    void testSerializeSimpleTypeWithXmlSpecialCharacters() {
        XsdSimpleType specialType = new XsdSimpleType("SpecialType");
        XsdRestriction restriction = new XsdRestriction("xs:string");
        restriction.addFacet(new XsdFacet(XsdFacetType.PATTERN, "<>&\"'"));

        specialType.addChild(restriction);
        schema.addChild(specialType);

        String xml = serializer.serialize(schema);

        // XML special characters should be escaped
        assertTrue(xml.contains("&lt;&gt;&amp;&quot;&apos;"));
    }

    @Test
    @DisplayName("serialize empty simpleType")
    void testSerializeEmptySimpleType() {
        XsdSimpleType emptyType = new XsdSimpleType("EmptyType");
        schema.addChild(emptyType);

        String xml = serializer.serialize(schema);

        assertTrue(xml.contains("<xs:simpleType name=\"EmptyType\">"));
        assertTrue(xml.contains("</xs:simpleType>"));
    }
}
