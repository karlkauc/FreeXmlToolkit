package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every element and attribute is emitted in the namespace its declaration gives it (work package C). Modelled on AEAT
 * Modelo 170 ({@code comun:Modelo}: local elements of an imported, qualified type), INSPIRE ({@code gn:spelling}
 * after a nested reference), SIRI FR-IDF ({@code MessageText}: unqualified local elements under a qualified parent),
 * JATS and XBRL ({@code xlink:href}, {@code xlink:type} attribute references, INSPIRE with {@code fixed} on the
 * reference) and {@code xml:lang}.
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
                        <attribute ref="xlink:type" fixed="simple"/>
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
              <attribute name="type">
                <simpleType>
                  <restriction base="token">
                    <enumeration value="simple"/>
                    <enumeration value="extended"/>
                    <enumeration value="locator"/>
                  </restriction>
                </simpleType>
              </attribute>
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
                "xlink:type=\"simple\"",
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

    @AfterEach
    void resetServices() {
        ServiceRegistry.reset();
    }

    @ParameterizedTest(name = "realistic={0}, mandatoryOnly={1}")
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void anImportWhoseFileIsMissingResolvesThroughTheSchemaLibrary(boolean realistic, boolean mandatoryOnly)
            throws Exception {
        // JATS imports standard-modules/xlink.xsd; the copy at hand lives elsewhere and is mapped by its namespace
        Path main = write("main.xsd", MAIN.replace("schemaLocation=\"xlink.xsd\"",
                "schemaLocation=\"standard-modules/xlink.xsd\""));
        write("c.xsd", COMMON);
        write("u.xsd", UNQUALIFIED);
        write("xml.xsd", XML);
        Files.createDirectories(dir.resolve("local"));
        Path xlink = write("local/xlink.xsd", XLINK);
        var library = new SchemaLibraryServiceImpl(dir.resolve("lib.json"),
                new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        library.addEntry(SchemaLibraryEntry.user("http://www.w3.org/1999/xlink", xlink.toString(), SchemaKind.XSD, "",
                null));
        ServiceRegistry.register(SchemaLibraryService.class, library);

        String xml = SampleXmlRunner.generate(main.toFile(), mandatoryOnly, 1, realistic);

        assertTrue(xml.contains("xlink:href=\"") && xml.contains("xmlns:xlink=\"http://www.w3.org/1999/xlink\""), xml);
        Path reference = write("reference.xsd", MAIN.replace("schemaLocation=\"xlink.xsd\"",
                "schemaLocation=\"local/xlink.xsd\""));
        try {
            new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(reference.toFile())
                    .newValidator().validate(new StreamSource(new StringReader(xml)));
        } catch (org.xml.sax.SAXException e) {
            throw new AssertionError(e.getMessage() + " in:\n" + xml, e);
        }
        library.awaitSave();
    }

    private Path write(String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
