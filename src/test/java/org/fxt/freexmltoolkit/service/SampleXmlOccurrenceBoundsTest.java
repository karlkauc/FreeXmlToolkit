package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * An element repeats at least {@code minOccurs} times even when that exceeds the configured maximum number of
 * occurrences (work package G4). Modelled on UCI: {@code Vertex} (at least 3 per polygon), {@code KnotVector} (at
 * least 4) and {@code Covariance} (6 to 120 values), with the audit's maximum of 2; a repeated choice likewise.
 */
class SampleXmlOccurrenceBoundsTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:occ" targetNamespace="urn:occ"
                       elementFormDefault="qualified">
              <xs:element name="shapes">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="polygon">
                      <xs:complexType>
                        <xs:sequence><xs:element name="vertex" type="xs:int" minOccurs="3" maxOccurs="unbounded"/></xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="covariance" type="xs:double" minOccurs="6" maxOccurs="120"/>
                    <xs:element ref="knot" minOccurs="4" maxOccurs="unbounded"/>
                    <xs:choice minOccurs="3" maxOccurs="unbounded">
                      <xs:element name="point" type="xs:int"/>
                      <xs:element name="line" type="xs:int"/>
                    </xs:choice>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="knot">
                <xs:complexType>
                  <xs:sequence><xs:element name="weight" type="xs:double"/></xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void elementsRepeatAtLeastMinOccursTimes(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("occ.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);

        assertTrue(count(xml, "<vertex>") >= 3, xml);
        assertTrue(count(xml, "<covariance>") >= 6, xml);
        assertTrue(count(xml, "<knot>") >= 4, xml);
        assertEquals(count(xml, "<knot>"), count(xml, "<weight>"), xml);
        assertTrue(count(xml, "<point>") + count(xml, "<line>") >= 3, xml);
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }

    @Test
    void theBoundsOfAReferenceApplyToTheReferencedElementOnly() throws Exception {
        Path xsd = dir.resolve("occ.xsd");
        Files.writeString(xsd, SCHEMA);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.FALSE);

        XsdExtendedElement knot = element(service, "knot", "/shapes/");
        XsdExtendedElement weight = element(service, "weight", "/shapes/");
        assertEquals("4", ((org.w3c.dom.Element) knot.getCardinalityNode()).getAttribute("minOccurs"));
        assertNull(weight.getCardinalityNode(), "content of a referenced element inherited the reference's bounds");
        assertEquals(1, XsdDocumentationService.elementRepeatCount(weight, 2));
    }

    private static XsdExtendedElement element(XsdDocumentationService service, String name, String xpathPrefix) {
        return service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> name.equals(e.getElementName()) && e.getCurrentXpath().startsWith(xpathPrefix))
                .findFirst().orElseThrow();
    }

    private static int count(String xml, String token) {
        Matcher matcher = Pattern.compile(Pattern.quote(token)).matcher(xml);
        int n = 0;
        while (matcher.find()) {
            n++;
        }
        return n;
    }
}
