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
 * A derived complex type carries the attributes of every type in its derivation chain. The generators used to take
 * only the base type's content model, so a required attribute declared next to the base's sequence (rim
 * {@code IdentifiableType@id}), on an attribute-only base (Subsonic {@code JukeboxStatus}) or in an attribute group was
 * missing from the sample.
 */
class SampleXmlInheritedAttributesTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:attrs" targetNamespace="urn:attrs"
                       elementFormDefault="qualified">
              <xs:element name="registry">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="extrinsic" type="ExtrinsicObjectType"/>
                    <xs:element name="playlist" type="JukeboxPlaylist"/>
                    <xs:element name="length" type="LengthType"/>
                    <xs:element name="grouped" type="GroupedChildType"/>
                    <xs:element name="restricted" type="RestrictedType"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>

              <!-- three-level extension chain, required attribute next to the top base's sequence -->
              <xs:complexType name="IdentifiableType">
                <xs:sequence><xs:element name="slot" type="xs:string" minOccurs="0"/></xs:sequence>
                <xs:attribute name="id" type="xs:anyURI" use="required"/>
              </xs:complexType>
              <xs:complexType name="RegistryObjectType">
                <xs:complexContent>
                  <xs:extension base="IdentifiableType">
                    <xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence>
                    <xs:attribute name="lid" type="xs:anyURI" use="required"/>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
              <xs:complexType name="ExtrinsicObjectType">
                <xs:complexContent>
                  <xs:extension base="RegistryObjectType">
                    <xs:attribute name="mimeType" type="xs:string" use="required"/>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>

              <!-- attribute-only base -->
              <xs:complexType name="JukeboxStatus">
                <xs:attribute name="currentIndex" type="xs:int" use="required"/>
                <xs:attribute name="playing" type="xs:boolean" use="required"/>
              </xs:complexType>
              <xs:complexType name="JukeboxPlaylist">
                <xs:complexContent>
                  <xs:extension base="JukeboxStatus">
                    <xs:sequence><xs:element name="entry" type="xs:string" minOccurs="0"/></xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>

              <!-- simpleContent extension of a complex type with a required attribute -->
              <xs:complexType name="MeasureType">
                <xs:simpleContent>
                  <xs:extension base="xs:double">
                    <xs:attribute name="uom" type="xs:string" use="required"/>
                  </xs:extension>
                </xs:simpleContent>
              </xs:complexType>
              <xs:complexType name="LengthType">
                <xs:simpleContent>
                  <xs:extension base="MeasureType"/>
                </xs:simpleContent>
              </xs:complexType>

              <!-- attribute group on the base -->
              <xs:attributeGroup name="Audit">
                <xs:attribute name="createdBy" type="xs:string" use="required"/>
              </xs:attributeGroup>
              <xs:complexType name="GroupedBaseType">
                <xs:sequence><xs:element name="value" type="xs:string"/></xs:sequence>
                <xs:attributeGroup ref="Audit"/>
              </xs:complexType>
              <xs:complexType name="GroupedChildType">
                <xs:complexContent>
                  <xs:extension base="GroupedBaseType"/>
                </xs:complexContent>
              </xs:complexType>

              <!-- a complexContent restriction inherits attributes it does not restate -->
              <xs:complexType name="RestrictedType">
                <xs:complexContent>
                  <xs:restriction base="IdentifiableType">
                    <xs:sequence><xs:element name="slot" type="xs:string"/></xs:sequence>
                  </xs:restriction>
                </xs:complexContent>
              </xs:complexType>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void inheritedAttributesAreGenerated(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("attrs.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic);

        for (String expected : new String[]{"<extrinsic id=", " lid=", " mimeType=", "<playlist currentIndex=",
                " playing=", "<length uom=", "<grouped createdBy=", "<restricted id="}) {
            assertTrue(xml.contains(expected), "missing " + expected + " in:\n" + xml);
        }
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
