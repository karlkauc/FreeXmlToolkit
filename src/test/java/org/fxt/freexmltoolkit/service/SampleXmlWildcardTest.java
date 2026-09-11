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
 * A required element wildcard gets an element the wildcard allows (work package G3). Modelled on xmldsig
 * {@code SignatureProperty} (a choice of {@code ##other}, lax), XBRL {@code segment} ({@code ##other}, lax, at least
 * one) and UBL {@code ExtensionContent} ({@code ##other}, skip); the generators recorded wildcards for the
 * documentation only, so these elements came out empty.
 */
class SampleXmlWildcardTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns="urn:w" targetNamespace="urn:w"
                       elementFormDefault="qualified">
              <xs:element name="document">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="SignatureProperty">
                      <xs:complexType>
                        <xs:choice maxOccurs="unbounded">
                          <xs:any namespace="##other" processContents="lax"/>
                        </xs:choice>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="segment">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:any namespace="##other" processContents="lax" minOccurs="1" maxOccurs="unbounded"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="ExtensionContent">
                      <xs:complexType>
                        <xs:sequence><xs:any namespace="##other" processContents="skip"/></xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="between">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="before" type="xs:string"/>
                          <xs:any namespace="##local" processContents="lax"/>
                          <xs:element name="after" type="xs:string"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="listed">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:any namespace="urn:a urn:b" processContents="skip"/>
                          <xs:any namespace="##any" processContents="lax" minOccurs="0"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void requiredWildcardsGetAnAllowedElement(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("wildcards.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic);

        assertTrue(!xml.contains("<SignatureProperty/>") && !xml.contains("<segment/>")
                && !xml.contains("<ExtensionContent/>"), "empty wildcard content in:\n" + xml);
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (org.xml.sax.SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
    }
}
