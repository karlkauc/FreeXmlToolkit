package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The realistic generator resolves named types as far as the plain one (work package R1). Modelled on SIRI 2.2:
 * {@code RequestorRef} is a {@code ParticipantRefStructure}, simple content extending {@code ParticipantCodeType},
 * which restricts {@code NMTOKEN}; {@code Name} is a {@code NaturalLanguageStringStructure} extending
 * {@code PopulatedStringType} ({@code minLength="1"}). The realistic generator wrote empty values for both, 100 invalid
 * SIRI samples with mandatory elements only and 208 with optional elements.
 */
class SampleXmlRealisticValuesTest {

    private static final String SCHEMA = """
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns="urn:siri" targetNamespace="urn:siri"
                        elementFormDefault="qualified">
              <xsd:element name="ServiceRequest">
                <xsd:complexType>
                  <xsd:sequence>
                    <xsd:element name="RequestorRef" type="ParticipantRefStructure"/>
                    <xsd:element name="Name" type="NaturalLanguageStringStructure"/>
                    <xsd:element name="PlaceName" type="PopulatedPlaceNameType"/>
                  </xsd:sequence>
                </xsd:complexType>
              </xsd:element>
              <xsd:complexType name="ParticipantRefStructure">
                <xsd:simpleContent><xsd:extension base="ParticipantCodeType"/></xsd:simpleContent>
              </xsd:complexType>
              <xsd:simpleType name="ParticipantCodeType">
                <xsd:restriction base="xsd:NMTOKEN"/>
              </xsd:simpleType>
              <xsd:complexType name="NaturalLanguageStringStructure">
                <xsd:simpleContent>
                  <xsd:extension base="PopulatedStringType">
                    <xsd:attribute name="lang" type="xsd:language"/>
                  </xsd:extension>
                </xsd:simpleContent>
              </xsd:complexType>
              <xsd:simpleType name="PopulatedStringType">
                <xsd:restriction base="xsd:string"><xsd:minLength value="1"/></xsd:restriction>
              </xsd:simpleType>
              <xsd:simpleType name="PopulatedPlaceNameType">
                <xsd:restriction base="PopulatedStringType">
                  <xsd:pattern value="[^,\\[\\]\\{\\}\\?$%\\^=@#;:]+"/>
                </xsd:restriction>
              </xsd:simpleType>
            </xsd:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void valuesOfSimpleContentChainsAreGenerated(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("siri.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic);

        assertFalse(xml.contains("<RequestorRef/>") || xml.contains("<Name/>") || xml.contains("\"/>\n\t<PlaceName"),
                "empty value in:\n" + xml);
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (org.xml.sax.SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
    }
}
