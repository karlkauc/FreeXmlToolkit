package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
 * The content type of a complex type decides whether a sample writes text: a type with attributes only and a type
 * with an element-only content model allow none, {@code mixed="true"} does (rim {@code LocalizedString}, KML and JATS
 * hold many of each).
 */
class SampleXmlContentTypeTest {

    /** Attributes only, no content model: an instance may not contain text. */
    private static final String ATTRIBUTES_ONLY = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="entry">
                <xs:complexType>
                  <xs:attribute name="lang" type="xs:string" use="required"/>
                  <xs:attribute name="value" type="xs:string"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    /** Element-only content: no text between the children. */
    private static final String ELEMENT_ONLY = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="person">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="given" type="xs:string"/>
                    <xs:element name="family" type="xs:string"/>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:string"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    /** Mixed content: text is allowed between the children. */
    private static final String MIXED = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="para">
                <xs:complexType mixed="true">
                  <xs:sequence>
                    <xs:element name="emphasis" type="xs:string" minOccurs="0" maxOccurs="unbounded"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    static Stream<Arguments> cases() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String schema : new String[]{"attributes only", "element only", "mixed"}) {
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
    void samplesWriteTextOnlyWhereTheContentTypeAllowsIt(String schemaName, boolean realistic, boolean mandatoryOnly)
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
        if ("attributes only".equals(schemaName)) {
            assertFalse(xml.contains("</entry>"), "an element without content model is written empty:\n" + xml);
        }
    }

    private static String schema(String name) {
        return switch (name) {
            case "attributes only" -> ATTRIBUTES_ONLY;
            case "element only" -> ELEMENT_ONLY;
            case "mixed" -> MIXED;
            default -> throw new IllegalArgumentException(name);
        };
    }
}
