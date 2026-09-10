package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Sample expansion stays small for grammars whose optional content recurses through many different elements. JATS
 * mixed content lets every inline element contain every other one; once group references resolved, the expansion of
 * {@code article} exceeded the node limit in both modes, and SIRI {@code Siri} did with optional elements.
 */
class SampleXmlBoundedExpansionTest {

    private static final String[] INLINE = {"bold", "italic", "sup", "sub", "sc", "monospace", "underline", "overline",
            "strike", "roman"};

    @TempDir
    Path dir;

    @AfterEach
    void restoreLimit() {
        System.clearProperty(SampleXmlLimits.MAX_EXPANDED_NODES_PROPERTY);
    }

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void recursiveInlineContentExpandsWithinTheNodeLimit(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("inline.xsd");
        Files.writeString(xsd, schema());
        // Without bounding, the ten mutually nested inline elements expand to millions of nodes
        System.setProperty(SampleXmlLimits.MAX_EXPANDED_NODES_PROPERTY, "20000");

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);

        assertFalse(xml.contains("Sample XML not generated"), xml);
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    private static String schema() {
        StringBuilder inline = new StringBuilder();
        StringBuilder refs = new StringBuilder();
        for (String name : INLINE) {
            refs.append("<xs:element ref=\"").append(name).append("\"/>");
        }
        for (String name : INLINE) {
            inline.append("<xs:element name=\"").append(name).append("\">")
                    .append("<xs:complexType mixed=\"true\">")
                    .append("<xs:choice minOccurs=\"0\" maxOccurs=\"unbounded\">").append(refs).append("</xs:choice>")
                    // not xs:ID: the plain generator repeats one value per schema node (plan H1)
                    .append("<xs:attribute name=\"id\" type=\"xs:string\"/>")
                    .append("<xs:attribute name=\"content-type\" type=\"xs:string\"/>")
                    .append("</xs:complexType></xs:element>\n");
        }
        return """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="article">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="title" type="p-type"/>
                        <xs:element name="p" type="p-type" maxOccurs="unbounded"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                  <xs:complexType name="p-type" mixed="true">
                    <xs:choice minOccurs="0" maxOccurs="unbounded">%s</xs:choice>
                  </xs:complexType>
                  %s
                </xs:schema>
                """.formatted(refs, inline);
    }
}
