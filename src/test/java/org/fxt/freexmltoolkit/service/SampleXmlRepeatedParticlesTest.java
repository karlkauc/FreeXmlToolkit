package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Two particles with the same name in one content model are two children (JATS {@code ruby-model}:
 * {@code rb, (rt | (rp, rt, rp))}). The element map keyed both {@code rp} under the same XPath, so the second replaced
 * the first and samples missed a required element.
 */
class SampleXmlRepeatedParticlesTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="ruby">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element ref="rb"/>
                    <xs:element ref="rp"/>
                    <xs:element ref="rt"/>
                    <xs:element ref="rp"/>
                    <xs:element name="derived" type="DerivedType"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="rb" type="xs:string"/>
              <xs:element name="rt" type="xs:string"/>
              <xs:element name="rp" type="xs:string"/>
              <xs:complexType name="BaseType">
                <xs:sequence><xs:element name="note" type="xs:string"/></xs:sequence>
              </xs:complexType>
              <xs:complexType name="DerivedType">
                <xs:complexContent>
                  <xs:extension base="BaseType">
                    <xs:sequence><xs:element name="note" type="xs:string"/></xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @Test
    void theElementMapKeepsEverySameNamedParticle() throws Exception {
        Path xsd = dir.resolve("ruby.xsd");
        Files.writeString(xsd, SCHEMA);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.FALSE);

        assertEquals(List.of("rb", "rp", "rt", "rp", "derived"), childNames(service, "ruby"));
        assertEquals(List.of("note", "note"), childNames(service, "derived"));
    }

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void samplesContainEverySameNamedParticle(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("ruby.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic);
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (org.xml.sax.SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
    }

    /** Names of the element children of the named element, containers flattened, in order. */
    private static List<String> childNames(XsdDocumentationService service, String name) {
        var map = service.xsdDocumentationData.getExtendedXsdElementMap();
        XsdExtendedElement element = map.values().stream()
                .filter(e -> name.equals(e.getElementName()))
                .findFirst().orElseThrow();
        List<String> names = new java.util.ArrayList<>();
        collect(map, element, names);
        return names;
    }

    private static void collect(java.util.Map<String, XsdExtendedElement> map, XsdExtendedElement element,
                                List<String> names) {
        for (String childXpath : element.getChildren()) {
            XsdExtendedElement child = map.get(childXpath);
            if (child == null || child.getElementName().startsWith("@")) {
                continue;
            }
            if (child.getElementName().matches("SEQUENCE|CHOICE|ALL")) {
                collect(map, child, names);
            } else {
                names.add(child.getElementName());
            }
        }
    }
}
