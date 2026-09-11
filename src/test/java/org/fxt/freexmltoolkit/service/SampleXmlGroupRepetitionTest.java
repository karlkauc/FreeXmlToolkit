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
 * A model group repeats as a whole: a sequence with {@code minOccurs="2"} needs its particles twice, in order. A
 * required choice whose options are all optional may stay empty. Both are golden cases of the plan (G4, G5).
 */
class SampleXmlGroupRepetitionTest {

    private static final int RUNS = 8;

    /** A required sequence that repeats: (name, value){2,3}. */
    private static final String REPEATED_SEQUENCE = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="pairs">
                <xs:complexType>
                  <xs:sequence minOccurs="2" maxOccurs="3">
                    <xs:element name="name" type="xs:string"/>
                    <xs:element name="value" type="xs:int"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    /** A repeated sequence as an option of a choice, next to a plain element. */
    private static final String REPEATED_SEQUENCE_OPTION = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="position">
                <xs:complexType>
                  <xs:choice>
                    <xs:sequence minOccurs="2" maxOccurs="unbounded">
                      <xs:element name="x" type="xs:double"/>
                      <xs:element name="y" type="xs:double"/>
                    </xs:sequence>
                    <xs:element name="label" type="xs:string"/>
                  </xs:choice>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    /** A required choice whose options are all optional: an empty choice is valid. */
    private static final String CHOICE_OF_OPTIONAL_OPTIONS = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="note">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="title" type="xs:string"/>
                    <xs:choice>
                      <xs:element name="body" type="xs:string" minOccurs="0"/>
                      <xs:element name="link" type="xs:anyURI" minOccurs="0"/>
                    </xs:choice>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    static Stream<Arguments> cases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String schema : new String[]{"repeated sequence", "repeated sequence option",
                "choice of optional options"}) {
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
    void repeatedGroupsAreGeneratedAsAWhole(String schemaName, boolean realistic, boolean mandatoryOnly)
            throws Exception {
        Path xsd = dir.resolve(schemaName.replace(' ', '-') + ".xsd");
        Files.writeString(xsd, schema(schemaName));
        Schema schema = new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile());

        for (int run = 1; run <= RUNS; run++) {
            String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);
            try {
                schema.newValidator().validate(new StreamSource(new StringReader(xml)));
            } catch (SAXException e) {
                throw new AssertionError("run " + run + ": " + e.getMessage() + " in:\n" + xml, e);
            }
        }
    }

    private static String schema(String name) {
        return switch (name) {
            case "repeated sequence" -> REPEATED_SEQUENCE;
            case "repeated sequence option" -> REPEATED_SEQUENCE_OPTION;
            case "choice of optional options" -> CHOICE_OF_OPTIONAL_OPTIONS;
            default -> throw new IllegalArgumentException(name);
        };
    }
}
