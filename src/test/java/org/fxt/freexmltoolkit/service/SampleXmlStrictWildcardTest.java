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
 * A wildcard with {@code processContents="strict"} needs an element the validator can look up. The corpus holds 8
 * required ones (INSPIRE 3, SIRI 2, datajud 2, UBL 1) and 27 optional ones; a sample has to pick a global element of
 * an allowed namespace, or leave the content out where the model permits it.
 */
class SampleXmlStrictWildcardTest {

    /** A required strict wildcard for any namespace: a global element of the schema itself satisfies it. */
    private static final String STRICT_ANY = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:fxt:test" targetNamespace="urn:fxt:test"
                       elementFormDefault="qualified">
              <xs:element name="report">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="title" type="xs:string"/>
                    <xs:any namespace="##any"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="note" type="xs:string"/>
            </xs:schema>
            """;

    /** A required strict wildcard for the target namespace. */
    private static final String STRICT_TARGET = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:fxt:test" targetNamespace="urn:fxt:test"
                       elementFormDefault="qualified">
              <xs:element name="envelope">
                <xs:complexType>
                  <xs:sequence>
                    <xs:any namespace="##targetNamespace" maxOccurs="2"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="payload" type="xs:string"/>
            </xs:schema>
            """;

    /** An optional strict wildcard whose namespace no declaration serves: the content may stay out. */
    private static final String STRICT_OTHER_OPTIONAL = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:fxt:test" targetNamespace="urn:fxt:test"
                       elementFormDefault="qualified">
              <xs:element name="extension">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="id" type="xs:string"/>
                    <xs:any namespace="##other" minOccurs="0"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    static Stream<Arguments> cases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String schema : new String[]{"strict any", "strict target namespace", "strict other optional"}) {
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
    void strictWildcardsGetAnElementTheValidatorKnows(String schemaName, boolean realistic, boolean mandatoryOnly)
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
            case "strict any" -> STRICT_ANY;
            case "strict target namespace" -> STRICT_TARGET;
            case "strict other optional" -> STRICT_OTHER_OPTIONAL;
            default -> throw new IllegalArgumentException(name);
        };
    }
}
