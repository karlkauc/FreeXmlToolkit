package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Element, group and attribute-group references resolve in the namespace of the referencing document. JATS imports
 * MathML, which declares its own global {@code sec}, {@code list}, {@code title}, {@code annotation} and
 * {@code product}; looked up by local name, JATS {@code sec} got the MathML content and attributes.
 */
class CrossNamespaceReferenceResolutionTest {

    private static final String JATS = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:jats" xmlns:mml="urn:mml"
                       targetNamespace="urn:jats" elementFormDefault="qualified">
              <xs:import namespace="urn:mml" schemaLocation="mathml/mml.xsd"/>
              <xs:element name="article">
                <xs:complexType><xs:sequence><xs:element ref="sec"/></xs:sequence></xs:complexType>
              </xs:element>
              <xs:element name="sec">
                <xs:complexType>
                  <xs:group ref="sec-model"/>
                  <xs:attributeGroup ref="sec-atts"/>
                </xs:complexType>
              </xs:element>
              <xs:group name="sec-model">
                <xs:sequence><xs:element name="heading" type="xs:string"/></xs:sequence>
              </xs:group>
              <xs:attributeGroup name="sec-atts">
                <xs:attribute name="sec-type" type="xs:string" use="required"/>
              </xs:attributeGroup>
            </xs:schema>
            """;

    private static final String MATHML = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:mml" targetNamespace="urn:mml"
                       elementFormDefault="qualified">
              <xs:element name="math">
                <xs:complexType><xs:sequence><xs:element ref="sec"/></xs:sequence></xs:complexType>
              </xs:element>
              <xs:element name="sec">
                <xs:complexType>
                  <xs:group ref="sec-model"/>
                  <xs:attributeGroup ref="sec-atts"/>
                </xs:complexType>
              </xs:element>
              <xs:group name="sec-model">
                <xs:sequence><xs:element name="apply" type="xs:string"/></xs:sequence>
              </xs:group>
              <xs:attributeGroup name="sec-atts">
                <xs:attribute name="definitionURL" type="xs:anyURI" use="required"/>
              </xs:attributeGroup>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void referencesResolveInTheReferencingDocumentsNamespace(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path main = dir.resolve("jats.xsd");
        Files.writeString(main, JATS);
        Files.createDirectories(dir.resolve("mathml"));
        Files.writeString(dir.resolve("mathml/mml.xsd"), MATHML);

        String xml = SampleXmlRunner.generate(main.toFile(), mandatoryOnly, 1, realistic);

        assertTrue(xml.contains("<heading>") && xml.contains("sec-type="), "JATS sec content missing:\n" + xml);
        assertFalse(xml.contains("apply") || xml.contains("definitionURL"), "MathML sec content used:\n" + xml);
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(main.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
