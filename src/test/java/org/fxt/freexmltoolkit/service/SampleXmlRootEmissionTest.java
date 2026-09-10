package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The root element of a generated sample must carry its own simple value and must not contain whitespace when its
 * content is empty or simple (XBRL {@code measure}, rim {@code Action}).
 */
class SampleXmlRootEmissionTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="amount" type="xs:decimal"/>
              <xs:element name="flag">
                <xs:complexType/>
              </xs:element>
              <xs:element name="price">
                <xs:complexType>
                  <xs:simpleContent>
                    <xs:extension base="xs:decimal">
                      <xs:attribute name="currency" type="xs:string" use="required"/>
                    </xs:extension>
                  </xs:simpleContent>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    static Path dir;

    static Stream<Arguments> roots() {
        return Stream.of("amount", "flag", "price")
                .flatMap(root -> Stream.of("plain", "realistic")
                        .flatMap(generator -> Stream.of(true, false)
                                .map(mandatoryOnly -> Arguments.of(root, generator, mandatoryOnly))));
    }

    @ParameterizedTest(name = "{0} via {1}, mandatoryOnly={2}")
    @MethodSource("roots")
    void rootWithSimpleOrEmptyContentIsValid(String root, String generator, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("roots.xsd");
        if (!Files.exists(xsd)) {
            Files.writeString(xsd, XSD);
        }
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());

        String xml;
        if ("plain".equals(generator)) {
            xml = service.generateSampleXml(root, mandatoryOnly, 2);
        } else {
            service.processXsd(Boolean.TRUE);
            GenerationProfile profile = new GenerationProfile("Realistic");
            profile.setMandatoryOnly(mandatoryOnly);
            profile.setMaxOccurrences(2);
            xml = new ProfiledXmlGeneratorService().generateRealistic(profile, service.xsdDocumentationData, xsd.toString(), root);
        }

        assertFalse(xml.startsWith("<!--"), xml);
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
