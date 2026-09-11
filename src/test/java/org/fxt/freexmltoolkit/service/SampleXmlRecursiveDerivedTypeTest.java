package org.fxt.freexmltoolkit.service;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xml.sax.SAXException;

/**
 * An element of an abstract type gets a concrete derived type whose own content does not require the abstract type
 * again. Garmin {@code AbstractStep_t} is derived first by {@code Repeat_t}, whose required {@code Child} is an
 * {@code AbstractStep_t}; the recursion was cut and the workout's step lost its required child.
 */
class SampleXmlRecursiveDerivedTypeTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="workouts">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="workout">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="Name" type="xs:token"/>
                          <xs:element name="Step" type="AbstractStep_t" maxOccurs="unbounded"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:complexType name="AbstractStep_t" abstract="true">
                <xs:sequence><xs:element name="StepId" type="xs:positiveInteger"/></xs:sequence>
              </xs:complexType>
              <xs:complexType name="Repeat_t">
                <xs:complexContent>
                  <xs:extension base="AbstractStep_t">
                    <xs:sequence>
                      <xs:element name="Repetitions" type="xs:positiveInteger"/>
                      <xs:element name="Child" type="AbstractStep_t" maxOccurs="unbounded"/>
                    </xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
              <xs:complexType name="Step_t">
                <xs:complexContent>
                  <xs:extension base="AbstractStep_t">
                    <xs:sequence><xs:element name="Intensity" type="xs:token"/></xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void abstractTypesGetADerivedTypeWithCompleteContent(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("workouts.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic);
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
    }
}
