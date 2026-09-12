package org.fxt.freexmltoolkit.service;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXException;

/**
 * A list type holds whitespace-separated items of its item type, and its length facets count items, not characters.
 * INSPIRE and SIRI hold 11 list types each, KML two.
 */
class SampleXmlListValuesTest {

    /** A list of an enumerated item type, once as an element and once as an attribute. */
    private static final String ENUM_LIST = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="route">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="days" type="DayList"/>
                  </xs:sequence>
                  <xs:attribute name="modes" type="DayList" use="required"/>
                </xs:complexType>
              </xs:element>
              <xs:simpleType name="DayList"><xs:list itemType="Day"/></xs:simpleType>
              <xs:simpleType name="Day">
                <xs:restriction base="xs:string">
                  <xs:enumeration value="mon"/><xs:enumeration value="tue"/><xs:enumeration value="wed"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    /** A list with length facets: they count items. */
    private static final String BOUNDED_LIST = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="position">
                <xs:complexType>
                  <xs:sequence><xs:element name="coordinates" type="Pair"/></xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:simpleType name="Pair">
                <xs:restriction>
                  <xs:simpleType><xs:list itemType="xs:double"/></xs:simpleType>
                  <xs:length value="2"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    /** A list of a pattern-restricted item type. */
    private static final String PATTERN_LIST = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="codes" type="CodeList"/>
              <xs:simpleType name="CodeList"><xs:list itemType="Code"/></xs:simpleType>
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string"><xs:pattern value="[A-Z]{2}[0-9]{2}"/></xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    static Stream<Arguments> cases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String schema : new String[]{"enum list", "bounded list", "pattern list"}) {
            for (boolean realistic : new boolean[]{false, true}) {
                for (boolean mandatoryOnly : new boolean[]{true, false}) {
                    cases.add(Arguments.of(schema, realistic, mandatoryOnly));
                }
            }
        }
        return cases.build();
    }

    @ParameterizedTest(name = "{0}, realistic={1}, mandatoryOnly={2}")
    @MethodSource("cases")
    void listValuesHoldItemsOfTheirItemType(String schemaName, boolean realistic, boolean mandatoryOnly)
            throws Exception {
        Path xsd = dir.resolve(schemaName.replace(' ', '-') + ".xsd");
        Files.writeString(xsd, schema(schemaName));
        Schema schema = new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile());

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);
        try {
            schema.newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
    }

    private static String schema(String name) {
        return switch (name) {
            case "enum list" -> ENUM_LIST;
            case "bounded list" -> BOUNDED_LIST;
            case "pattern list" -> PATTERN_LIST;
            default -> throw new IllegalArgumentException(name);
        };
    }
}
