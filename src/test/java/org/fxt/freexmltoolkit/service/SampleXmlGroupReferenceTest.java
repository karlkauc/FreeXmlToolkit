package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A model group reference contributes its particles wherever it appears. JATS declares {@code article} as
 * {@code <complexType><group ref="article-full-model"/>…}: the group reference was the content model itself, was never
 * resolved, and the sample came out as an empty {@code <article/>}.
 */
class SampleXmlGroupReferenceTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="library">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="article">
                      <xs:complexType>
                        <xs:group ref="article-model"/>
                        <xs:attribute name="article-type" type="xs:string"/>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="book">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="isbn" type="xs:string"/>
                          <xs:group ref="title-model"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="report" type="ReportType"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>

              <xs:group name="article-model">
                <xs:sequence>
                  <xs:element name="front" type="xs:string"/>
                  <xs:element name="body" type="xs:string"/>
                </xs:sequence>
              </xs:group>
              <xs:group name="title-model">
                <xs:sequence><xs:element name="title" type="xs:string"/></xs:sequence>
              </xs:group>

              <xs:complexType name="BaseReportType">
                <xs:group ref="title-model"/>
              </xs:complexType>
              <xs:complexType name="ReportType">
                <xs:complexContent>
                  <xs:extension base="BaseReportType">
                    <xs:group ref="article-model"/>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void groupReferencesContributeTheirParticles(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("groups.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic).replaceAll("\\s+", "");

        assertTrue(xml.matches(".*<article[^>]*><front>[^<]*</front><body>[^<]*</body></article>.*"),
                "group reference as the content model of article:\n" + xml);
        assertTrue(xml.matches(".*<book><isbn>[^<]*</isbn><title>[^<]*</title></book>.*"),
                "group reference inside a sequence:\n" + xml);
        assertTrue(xml.matches(".*<report><title>[^<]*</title><front>[^<]*</front><body>[^<]*</body></report>.*"),
                "group references in a base type and its extension:\n" + xml);
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                .newValidator().validate(new StreamSource(new StringReader(
                        SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic))));
    }
}
