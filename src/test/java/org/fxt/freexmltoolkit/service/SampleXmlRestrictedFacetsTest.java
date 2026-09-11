package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A restriction's facets replace those of its base type (work package F4). Modelled on SIRI 2.2:
 * {@code DaysOfWeekEnumerationx} and {@code HolidayTypeEnumerationx} both restrict {@code DayTypeEnumeration}, which
 * lists all 22 values, to 12 and 10 of them. The element map appended each restriction's values to the base's, so
 * {@code DayType} came out as {@code schoolDays} and {@code HolidayType} as {@code weekdays}.
 */
class SampleXmlRestrictedFacetsTest {

    private static final String SCHEMA = """
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns="urn:time" targetNamespace="urn:time"
                        elementFormDefault="qualified">
              <xsd:element name="ValidityCondition">
                <xsd:complexType>
                  <xsd:sequence>
                    <xsd:element name="DayType" type="DaysOfWeekEnumerationx" maxOccurs="unbounded"/>
                    <xsd:element name="HolidayType" type="HolidayTypeEnumerationx" maxOccurs="unbounded"/>
                    <xsd:element name="Code" type="ShortCodeType"/>
                  </xsd:sequence>
                </xsd:complexType>
              </xsd:element>
              <xsd:simpleType name="DayTypeEnumeration">
                <xsd:restriction base="xsd:NMTOKEN">
                  <xsd:enumeration value="monday"/>
                  <xsd:enumeration value="tuesday"/>
                  <xsd:enumeration value="holiday"/>
                  <xsd:enumeration value="publicHoliday"/>
                  <xsd:enumeration value="schoolDays"/>
                  <xsd:enumeration value="everyDay"/>
                </xsd:restriction>
              </xsd:simpleType>
              <xsd:simpleType name="DaysOfWeekEnumerationx">
                <xsd:restriction base="DayTypeEnumeration">
                  <xsd:enumeration value="monday"/>
                  <xsd:enumeration value="tuesday"/>
                </xsd:restriction>
              </xsd:simpleType>
              <xsd:simpleType name="HolidayTypeEnumerationx">
                <xsd:restriction base="DayTypeEnumeration">
                  <xsd:enumeration value="holiday"/>
                  <xsd:enumeration value="publicHoliday"/>
                </xsd:restriction>
              </xsd:simpleType>
              <xsd:simpleType name="CodeType">
                <xsd:restriction base="xsd:string"><xsd:maxLength value="10"/></xsd:restriction>
              </xsd:simpleType>
              <xsd:simpleType name="ShortCodeType">
                <xsd:restriction base="CodeType"><xsd:maxLength value="3"/></xsd:restriction>
              </xsd:simpleType>
            </xsd:schema>
            """;

    @TempDir
    Path dir;

    @Test
    void theElementMapKeepsOnlyTheRestrictionsOwnFacets() throws Exception {
        Path xsd = dir.resolve("time.xsd");
        Files.writeString(xsd, SCHEMA);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.FALSE);

        assertEquals(List.of("monday", "tuesday"), facet(service, "DayType", "enumeration"));
        assertEquals(List.of("holiday", "publicHoliday"), facet(service, "HolidayType", "enumeration"));
        assertEquals(List.of("3"), facet(service, "Code", "maxLength"));
    }

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void samplesUseTheRestrictedValues(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("time.xsd");
        Files.writeString(xsd, SCHEMA);

        for (int run = 0; run < 10; run++) {
            String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);
            try {
                new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                        .newValidator().validate(new StreamSource(new StringReader(xml)));
            } catch (org.xml.sax.SAXException e) {
                throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
            }
        }
    }

    private static List<String> facet(XsdDocumentationService service, String element, String facet) {
        XsdExtendedElement declaration = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> element.equals(e.getElementName()))
                .findFirst().orElseThrow();
        return declaration.getRestrictionInfo().facets().get(facet);
    }
}
