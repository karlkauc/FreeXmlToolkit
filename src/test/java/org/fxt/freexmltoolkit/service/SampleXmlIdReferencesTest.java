package org.fxt.freexmltoolkit.service;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A document that references an ID contains one (work package H). Modelled on JATS: {@code answer} requires
 * {@code pointer-to-question} (IDREFS) and has an optional {@code id}; {@code index-term-range-end} requires
 * {@code rid} (IDREF), declared before its optional {@code id}. With mandatory elements only, the optional IDs were
 * left out and the references pointed at nothing (11 audit samples).
 */
class SampleXmlIdReferencesTest {

    private static final String SCHEMA = """
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema">
              <xsd:element name="question-wrap">
                <xsd:complexType>
                  <xsd:sequence>
                    <xsd:element ref="answer"/>
                    <xsd:element ref="index-term-range-end"/>
                  </xsd:sequence>
                </xsd:complexType>
              </xsd:element>
              <xsd:element name="answer">
                <xsd:complexType>
                  <xsd:sequence><xsd:element name="p" type="xsd:string"/></xsd:sequence>
                  <xsd:attribute name="content-type" use="optional" type="xsd:string"/>
                  <xsd:attribute name="id" use="optional" type="xsd:ID"/>
                  <xsd:attribute name="pointer-to-question" use="required" type="xsd:IDREFS"/>
                </xsd:complexType>
              </xsd:element>
              <xsd:element name="index-term-range-end">
                <xsd:complexType>
                  <xsd:attribute name="rid" use="required" type="xsd:IDREF"/>
                  <xsd:attribute name="id" use="optional" type="xsd:ID"/>
                </xsd:complexType>
              </xsd:element>
            </xsd:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void everyReferencedIdIsEmitted(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("jats.xsd");
        Files.writeString(xsd, SCHEMA);

        for (String root : List.of("question-wrap", "answer", "index-term-range-end")) {
            String xml = generate(xsd, root, realistic, mandatoryOnly);
            try {
                new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                        .newValidator().validate(new StreamSource(new StringReader(xml)));
            } catch (org.xml.sax.SAXException e) {
                throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
            }
        }
    }

    /** A sample for the named root, as the audit harness generates it for each generator. */
    private static String generate(Path xsd, String root, boolean realistic, boolean mandatoryOnly) throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        if (!realistic) {
            return service.generateSampleXml(root, mandatoryOnly, 2);
        }
        service.loadSchema(XsdDocumentationService.MarkdownMode.ALL);
        service.expandForSample(root, mandatoryOnly, XsdDocumentationService.MarkdownMode.ALL);
        GenerationProfile profile = new GenerationProfile("Realistic");
        profile.setMandatoryOnly(mandatoryOnly);
        profile.setMaxOccurrences(2);
        return new ProfiledXmlGeneratorService().generateRealistic(profile, service.xsdDocumentationData,
                xsd.toString(), root);
    }
}
