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
 * A sample expansion cannot always expand the required content of a particle: recursion through the same declaration
 * or group is cut, a strict wildcard needs a declaration, an abstract element may have no member. A choice then picks
 * an option whose content is complete, and optional content that cannot be completed is left out (JATS
 * {@code statement}, {@code fn} and {@code question}, datajud {@code comunicacaoprocessual}, INSPIRE
 * {@code Building}).
 */
class SampleXmlIncompleteContentTest {

    /** Choices pick at random, so every combination is generated often enough to reach each option. */
    private static final int RUNS = 16;

    /**
     * JATS statement in p: a required choice of p or a nested statement, each wrapped in a class group, where both
     * options are already being expanded.
     */
    private static final String RECURSIVE_OPTION = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="p">
                <xs:complexType mixed="true">
                  <xs:choice minOccurs="0" maxOccurs="unbounded"><xs:element ref="statement"/></xs:choice>
                </xs:complexType>
              </xs:element>
              <xs:element name="statement">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="label" type="xs:string" minOccurs="0"/>
                    <xs:choice maxOccurs="unbounded">
                      <xs:group ref="statement.class"/>
                      <xs:group ref="just-para.class"/>
                    </xs:choice>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:group name="statement.class"><xs:choice><xs:element ref="statement"/></xs:choice></xs:group>
              <xs:group name="just-para.class"><xs:choice><xs:element ref="p"/></xs:choice></xs:group>
            </xs:schema>
            """;

    /** JATS fn in p: the only option of the footnote's required choice is p again. */
    private static final String RECURSIVE_ONLY_OPTION = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="note">
                <xs:complexType><xs:sequence><xs:element ref="p"/></xs:sequence></xs:complexType>
              </xs:element>
              <xs:element name="p">
                <xs:complexType mixed="true">
                  <xs:choice minOccurs="0" maxOccurs="unbounded"><xs:element ref="fn"/></xs:choice>
                </xs:complexType>
              </xs:element>
              <xs:element name="fn">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="label" type="xs:string" minOccurs="0"/>
                    <xs:choice maxOccurs="unbounded"><xs:element ref="p"/></xs:choice>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    /** JATS question-model: the options are compositors, one of them holds only a recursive reference. */
    private static final String NESTED_COMPOSITOR_OPTION = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="question">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="label" type="xs:string" minOccurs="0"/>
                    <xs:choice>
                      <xs:choice maxOccurs="unbounded"><xs:element ref="question"/></xs:choice>
                      <xs:sequence>
                        <xs:choice maxOccurs="unbounded"><xs:element name="p" type="xs:string"/></xs:choice>
                        <xs:choice minOccurs="0" maxOccurs="unbounded"><xs:element ref="question"/></xs:choice>
                      </xs:sequence>
                    </xs:choice>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    /** datajud tipoComunicacaoProcessual: one option requires a strict wildcard that no declaration satisfies. */
    private static final String STRICT_WILDCARD_OPTION = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:fxt:test" xmlns="urn:fxt:test"
                       elementFormDefault="qualified">
              <xs:element name="intercomunicacao">
                <xs:complexType>
                  <xs:choice>
                    <xs:element name="comunicacao">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="processo" type="xs:string"/>
                          <xs:any namespace="##other"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="documento" type="xs:string"/>
                  </xs:choice>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    /** INSPIRE Building: one option requires an abstract element that nothing substitutes. */
    private static final String ABSTRACT_OPTION = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="feature">
                <xs:complexType>
                  <xs:choice>
                    <xs:element name="building">
                      <xs:complexType><xs:sequence><xs:element ref="AbstractBuilding"/></xs:sequence></xs:complexType>
                    </xs:element>
                    <xs:element name="address" type="xs:string"/>
                  </xs:choice>
                </xs:complexType>
              </xs:element>
              <xs:element name="AbstractBuilding" abstract="true" type="xs:string"/>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    static Stream<Arguments> cases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String schema : new String[]{"recursive option", "recursive only option", "nested compositor option",
                "strict wildcard option", "abstract option"}) {
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
    void samplesPickContentTheyCanComplete(String schemaName, boolean realistic, boolean mandatoryOnly)
            throws Exception {
        Path xsd = dir.resolve(schemaName.replace(' ', '-') + ".xsd");
        Files.writeString(xsd, schema(schemaName));
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

    private static String schema(String name) {
        return switch (name) {
            case "recursive option" -> RECURSIVE_OPTION;
            case "recursive only option" -> RECURSIVE_ONLY_OPTION;
            case "nested compositor option" -> NESTED_COMPOSITOR_OPTION;
            case "strict wildcard option" -> STRICT_WILDCARD_OPTION;
            case "abstract option" -> ABSTRACT_OPTION;
            default -> throw new IllegalArgumentException(name);
        };
    }
}
