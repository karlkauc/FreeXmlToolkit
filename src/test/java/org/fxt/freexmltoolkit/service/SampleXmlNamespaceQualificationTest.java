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
 * Every element and attribute is emitted in the namespace its declaration gives it (work package C). Modelled on AEAT
 * Modelo 170 ({@code comun:Modelo}: local elements of an imported, qualified type), INSPIRE ({@code gn:spelling}
 * after a nested reference), SIRI FR-IDF ({@code MessageText}: unqualified local elements under a qualified parent),
 * JATS and XBRL ({@code xlink:href}, {@code xlink:type} attribute references) and {@code xml:lang}.
 */
class SampleXmlNamespaceQualificationTest {

    private static final String MAIN = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:m="urn:m" xmlns:c="urn:c" xmlns:u="urn:u"
                    xmlns:xlink="http://www.w3.org/1999/xlink" targetNamespace="urn:m" elementFormDefault="qualified">
              <import namespace="urn:c" schemaLocation="c.xsd"/>
              <import namespace="urn:u" schemaLocation="u.xsd"/>
              <import namespace="http://www.w3.org/1999/xlink" schemaLocation="xlink.xsd"/>
              <import namespace="http://www.w3.org/XML/1998/namespace" schemaLocation="xml.xsd"/>
              <element name="declaration">
                <complexType>
                  <sequence>
                    <element name="header" type="c:HeaderType"/>
                    <element name="message" type="u:MessageType"/>
                    <element name="graphic">
                      <complexType>
                        <attribute ref="xlink:href" use="required"/>
                        <attribute ref="xml:lang" use="required"/>
                      </complexType>
                    </element>
                    <element ref="c:Party"/>
                    <element name="after" type="string"/>
                  </sequence>
                </complexType>
              </element>
            </schema>
            """;

    private static final String COMMON = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" xmlns:c="urn:c" targetNamespace="urn:c"
                    elementFormDefault="qualified" attributeFormDefault="qualified">
              <complexType name="HeaderType">
                <sequence>
                  <element name="Modelo" type="string"/>
                  <element name="Ref" type="c:RefType"/>
                </sequence>
              </complexType>
              <complexType name="RefType">
                <sequence>
                  <element ref="c:Code"/>
                  <element name="Label" type="string"/>
                </sequence>
                <attribute name="scheme" type="string" use="required"/>
              </complexType>
              <element name="Code" type="string"/>
              <element name="Party">
                <complexType><sequence><element name="Name" type="string"/></sequence></complexType>
              </element>
            </schema>
            """;

    private static final String UNQUALIFIED = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:u">
              <complexType name="MessageType">
                <sequence>
                  <element name="MessageType" type="string"/>
                  <element name="MessageText" type="string"/>
                </sequence>
              </complexType>
            </schema>
            """;

    private static final String XLINK = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" targetNamespace="http://www.w3.org/1999/xlink">
              <attribute name="href" type="anyURI"/>
            </schema>
            """;

    private static final String XML = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" targetNamespace="http://www.w3.org/XML/1998/namespace">
              <attribute name="lang" type="language"/>
            </schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void elementsAndAttributesTakeTheNamespaceOfTheirDeclaration(boolean realistic, boolean mandatoryOnly)
            throws Exception {
        Path main = write("main.xsd", MAIN);
        write("c.xsd", COMMON);
        write("u.xsd", UNQUALIFIED);
        write("xlink.xsd", XLINK);
        write("xml.xsd", XML);

        String xml = SampleXmlRunner.generate(main.toFile(), mandatoryOnly, 1, realistic);

        for (String expected : new String[]{":Modelo>", ":Label>", ":scheme=\"", "xlink:href=\"", "xml:lang=\"",
                "xmlns=\"\"", ":Name>", "<after>"}) {
            assertTrue(xml.contains(expected), "missing " + expected + " in:\n" + xml);
        }
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(main.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (org.xml.sax.SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
    }

    private Path write(String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
