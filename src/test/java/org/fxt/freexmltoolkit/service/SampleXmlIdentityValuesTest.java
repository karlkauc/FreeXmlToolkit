package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Values that must be unique per occurrence (work package H). Modelled on xmldsig {@code Reference/@Id} and KML
 * {@code Data/@id} (IDs of repeated element references), JATS {@code @rid} (IDREFs), SIRI {@code KeyValuePair_unique}
 * ({@code .//siri:KeyValue}) and {@code TypeOfValue_unique} ({@code siri:*}), and Garmin
 * {@code RunningSubFolderNamesMustBeUnique} ({@code tc2:Folder}, {@code @Name}).
 */
class SampleXmlIdentityValuesTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:ids="urn:ids" xmlns="urn:ids"
                       targetNamespace="urn:ids" elementFormDefault="qualified">
              <xs:element name="catalog">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element ref="ids:link" maxOccurs="unbounded"/>
                    <xs:element ref="ids:item" maxOccurs="unbounded"/>
                    <xs:element name="keyList">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="group">
                            <xs:complexType>
                              <xs:sequence><xs:element ref="ids:entry" maxOccurs="unbounded"/></xs:sequence>
                            </xs:complexType>
                          </xs:element>
                        </xs:sequence>
                      </xs:complexType>
                      <xs:unique name="entryKeys">
                        <xs:selector xpath=".//ids:entry"/>
                        <xs:field xpath="ids:key"/>
                      </xs:unique>
                    </xs:element>
                    <xs:element name="folders">
                      <xs:complexType>
                        <xs:sequence><xs:element ref="ids:folder" maxOccurs="unbounded"/></xs:sequence>
                      </xs:complexType>
                      <xs:unique name="folderNames">
                        <xs:selector xpath="ids:folder"/>
                        <xs:field xpath="@name"/>
                      </xs:unique>
                    </xs:element>
                    <xs:element name="values">
                      <xs:complexType>
                        <xs:sequence><xs:element ref="ids:colour" maxOccurs="unbounded"/></xs:sequence>
                      </xs:complexType>
                      <xs:unique name="valueCodes">
                        <xs:selector xpath="ids:*|ids:size"/>
                        <xs:field xpath="ids:code"/>
                      </xs:unique>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:element name="link">
                <xs:complexType><xs:attribute name="target" type="xs:IDREF" use="required"/></xs:complexType>
              </xs:element>
              <xs:element name="item">
                <xs:complexType>
                  <xs:sequence><xs:element name="alias" type="xs:ID"/></xs:sequence>
                  <xs:attribute name="id" type="xs:ID" use="required"/>
                </xs:complexType>
              </xs:element>
              <xs:element name="entry">
                <xs:complexType><xs:sequence><xs:element name="key" type="xs:string"/></xs:sequence></xs:complexType>
              </xs:element>
              <xs:element name="folder">
                <xs:complexType><xs:attribute name="name" type="xs:string" use="required"/></xs:complexType>
              </xs:element>
              <xs:element name="colour">
                <xs:complexType><xs:sequence><xs:element name="code" type="xs:string"/></xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void repeatedIdentityValuesStayUniqueAndReferencesResolve(boolean realistic, boolean mandatoryOnly)
            throws Exception {
        Path xsd = dir.resolve("ids.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 2, realistic);

        for (String repeated : new String[]{"<item ", "<entry>", "<folder ", "<colour>"}) {
            assertTrue(count(xml, repeated) >= 2, "expected repetitions of " + repeated + " in:\n" + xml);
        }
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (org.xml.sax.SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
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
