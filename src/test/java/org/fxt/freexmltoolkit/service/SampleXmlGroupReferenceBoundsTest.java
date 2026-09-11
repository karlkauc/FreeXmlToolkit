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
 * The occurrence bounds of a group reference apply to the referenced group's content model. MathML {@code mfrac}
 * (JATS) holds {@code <xs:group ref="Presentation-expr.class" minOccurs="2" maxOccurs="2"/>}, a choice that has to
 * occur twice; the generators read the bounds from the group's compositor, which has none.
 */
class SampleXmlGroupReferenceBoundsTest {

    private static final int RUNS = 6;

    /** MathML mfrac: numerator and denominator, each one presentation expression. */
    private static final String CHOICE_GROUP_TWICE = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="mfrac">
                <xs:complexType>
                  <xs:group ref="Presentation-expr.class" minOccurs="2" maxOccurs="2"/>
                </xs:complexType>
              </xs:element>
              <xs:group name="Presentation-expr.class">
                <xs:choice>
                  <xs:element name="mi" type="xs:string"/>
                  <xs:element name="mn" type="xs:decimal"/>
                </xs:choice>
              </xs:group>
            </xs:schema>
            """;

    /** A sequence group referenced inside a sequence with minOccurs="2". */
    private static final String SEQUENCE_GROUP_TWICE = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="pairs">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="title" type="xs:string"/>
                    <xs:group ref="pair" minOccurs="2" maxOccurs="3"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:group name="pair">
                <xs:sequence>
                  <xs:element name="key" type="xs:string"/>
                  <xs:element name="value" type="xs:int"/>
                </xs:sequence>
              </xs:group>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    static Stream<Arguments> cases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String schema : new String[]{"choice group twice", "sequence group twice"}) {
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
    void groupReferenceBoundsRepeatTheGroup(String schemaName, boolean realistic, boolean mandatoryOnly)
            throws Exception {
        Path xsd = dir.resolve(schemaName.replace(' ', '-') + ".xsd");
        Files.writeString(xsd, "choice group twice".equals(schemaName) ? CHOICE_GROUP_TWICE : SEQUENCE_GROUP_TWICE);
        Schema schema = new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile());

        for (int run = 1; run <= RUNS; run++) {
            String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic);
            try {
                schema.newValidator().validate(new StreamSource(new StringReader(xml)));
            } catch (SAXException e) {
                throw new AssertionError("run " + run + ": " + e.getMessage() + " in:\n" + xml, e);
            }
        }
    }
}
