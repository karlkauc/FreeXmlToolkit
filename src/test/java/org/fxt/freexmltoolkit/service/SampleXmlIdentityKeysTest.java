package org.fxt.freexmltoolkit.service;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xml.sax.SAXException;

/**
 * Repeated key fields need distinct values of their own type: XTCE names its containers with a pattern type
 * ({@code containerNameKey}), Garmin identifies activities by an {@code xsd:dateTime} ({@code ActivityIdMustBeUnique}).
 * The same sampled name repeated, and a suffix {@code _1} made the date-time invalid. An element a key selects must
 * carry its field: XTCE {@code messageNameKey} selects {@code MessageSet/*}, including the optional
 * {@code LongDescription}, which has no {@code name}.
 */
class SampleXmlIdentityKeysTest {

    private static final String SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="system">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="container" minOccurs="3" maxOccurs="unbounded">
                      <xs:complexType><xs:attribute name="name" type="NameType" use="required"/></xs:complexType>
                    </xs:element>
                    <xs:element name="activity" minOccurs="3" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:sequence><xs:element name="Id" type="xs:dateTime"/></xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="messageSet">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="LongDescription" type="xs:string" minOccurs="0"/>
                          <xs:element name="Message" maxOccurs="unbounded">
                            <xs:complexType><xs:attribute name="name" type="xs:string" use="required"/></xs:complexType>
                          </xs:element>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
                <xs:key name="containerNameKey"><xs:selector xpath="container"/><xs:field xpath="@name"/></xs:key>
                <xs:key name="activityIdKey"><xs:selector xpath="activity"/><xs:field xpath="Id"/></xs:key>
                <xs:key name="messageNameKey"><xs:selector xpath="messageSet/*"/><xs:field xpath="@name"/></xs:key>
              </xs:element>
              <xs:simpleType name="NameType">
                <xs:restriction base="xs:normalizedString"><xs:pattern value="[^./:\\[\\] ]+"/></xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void repeatedKeyFieldsGetDistinctValidValues(boolean realistic, boolean mandatoryOnly) throws Exception {
        Path xsd = dir.resolve("keys.xsd");
        Files.writeString(xsd, SCHEMA);

        String xml = SampleXmlRunner.generate(xsd.toFile(), mandatoryOnly, 1, realistic);
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
    }
}
