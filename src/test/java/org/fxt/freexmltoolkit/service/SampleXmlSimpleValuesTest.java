package org.fxt.freexmltoolkit.service;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Simple values the generators left empty or got wrong (work package F). Modelled on XBRL ({@code measure} of type
 * {@code QName}, {@code denominator} of the union {@code nonZeroDecimal} whose members are inline types) and UCI
 * ({@code AA_Code}, {@code hexBinary} with {@code length="6"}; {@code SHA_2_Hash} with {@code length="32"}).
 */
class SampleXmlSimpleValuesTest {

    private static final String SCHEMA = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:v="urn:v" targetNamespace="urn:v"
                    elementFormDefault="qualified">
              <element name="values">
                <complexType>
                  <sequence>
                    <element name="measure" type="QName"/>
                    <element name="anything" type="anySimpleType"/>
                    <element name="denominator" type="v:nonZeroDecimal"/>
                    <element name="dated" type="v:dateUnion"/>
                    <element name="AA_Code" type="v:AA_CodeType"/>
                    <element name="SHA_2_Hash" type="v:SHA_2_256_HashType"/>
                    <element name="Digest" type="v:DigestType"/>
                    <element name="Token" type="v:TokenType"/>
                  </sequence>
                </complexType>
              </element>
              <simpleType name="nonZeroDecimal">
                <union>
                  <simpleType><restriction base="decimal"><minExclusive value="0"/></restriction></simpleType>
                  <simpleType><restriction base="decimal"><maxExclusive value="0"/></restriction></simpleType>
                </union>
              </simpleType>
              <simpleType name="dateUnion">
                <union memberTypes="date dateTime "/>
              </simpleType>
              <simpleType name="AA_CodeType">
                <restriction base="hexBinary"><length value="6"/></restriction>
              </simpleType>
              <simpleType name="SHA_2_256_HashType">
                <restriction base="hexBinary"><length value="32"/></restriction>
              </simpleType>
              <simpleType name="DigestType">
                <restriction base="base64Binary"><length value="20"/></restriction>
              </simpleType>
              <simpleType name="TokenType">
                <restriction base="hexBinary"><minLength value="8"/><maxLength value="16"/></restriction>
              </simpleType>
            </schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void simpleValuesMatchTheirTypes(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("values.xsd");
        Files.writeString(xsd, SCHEMA);

        for (int run = 0; run < 5; run++) {
            String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic);
            try {
                new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                        .newValidator().validate(new StreamSource(new StringReader(xml)));
            } catch (org.xml.sax.SAXException e) {
                throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
            }
        }
    }
}
