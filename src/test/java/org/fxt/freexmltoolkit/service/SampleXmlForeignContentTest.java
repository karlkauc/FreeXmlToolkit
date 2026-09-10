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
 * Content from an imported namespace, modelled on INSPIRE Addresses and GML 3.2.1. Once includes and imports resolved
 * relative to their own documents, GML types reached the samples and exposed three emission defects:
 * <ul>
 *   <li>a prefixed {@code attributeGroup ref} ({@code gml:SRSReferenceGroup}) was emitted as an element,</li>
 *   <li>an optional sequence holding a foreign element was emitted in mandatory-only mode while its namespace was not
 *   declared, so the sample was not well-formed,</li>
 *   <li>an element whose type has an empty sequence ({@code gml:ReferenceType}) was emitted with indentation
 *   whitespace, which empty content does not allow.</li>
 * </ul>
 */
class SampleXmlForeignContentTest {

    private static final String MAIN = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:ad="urn:ad" xmlns:gml="urn:gml"
                    targetNamespace="urn:ad" elementFormDefault="qualified">
              <import namespace="urn:gml" schemaLocation="gml/gml.xsd"/>
              <element name="GeographicPosition" type="ad:GeographicPositionType"/>
              <complexType name="GeographicPositionType">
                <sequence>
                  <element name="geometry" type="gml:PointPropertyType"/>
                  <element name="specification" type="gml:ReferenceType"/>
                  <element name="default" type="boolean"/>
                </sequence>
              </complexType>
            </schema>
            """;

    private static final String GML = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:gml="urn:gml" targetNamespace="urn:gml"
                    elementFormDefault="qualified">
              <attributeGroup name="SRSReferenceGroup">
                <attribute name="srsName" type="anyURI"/>
              </attributeGroup>
              <attributeGroup name="AssociationAttributeGroup">
                <attribute name="href" type="anyURI"/>
              </attributeGroup>
              <element name="Point" type="gml:PointType"/>
              <complexType name="PointType">
                <sequence><element name="pos" type="gml:DirectPositionType"/></sequence>
                <attributeGroup ref="gml:SRSReferenceGroup"/>
              </complexType>
              <complexType name="DirectPositionType">
                <simpleContent>
                  <extension base="string"><attributeGroup ref="gml:SRSReferenceGroup"/></extension>
                </simpleContent>
              </complexType>
              <complexType name="PointPropertyType">
                <sequence minOccurs="0"><element ref="gml:Point"/></sequence>
                <attributeGroup ref="gml:AssociationAttributeGroup"/>
              </complexType>
              <complexType name="ReferenceType">
                <sequence/>
                <attributeGroup ref="gml:AssociationAttributeGroup"/>
              </complexType>
            </schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void foreignContentIsWellFormedAndValid(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path main = dir.resolve("ad.xsd");
        Files.writeString(main, MAIN);
        Files.createDirectories(dir.resolve("gml"));
        Files.writeString(dir.resolve("gml/gml.xsd"), GML);

        String xml = SampleXmlRunner.generate(main.toFile(), mandatoryOnly, 1, realistic);

        assertFalse(xml.contains("ReferenceGroup") || xml.contains("AttributeGroup"),
                "attribute group emitted as an element:\n" + xml);
        assertTrue(xml.contains("<specification/>") || xml.matches("(?s).*<specification [^>]*/>.*"),
                "element with empty content must not contain whitespace:\n" + xml);
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(main.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
