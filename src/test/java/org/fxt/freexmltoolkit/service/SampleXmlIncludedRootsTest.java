package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sample XML roots come from every schema document of the target namespace, not only the main document: JATS keeps
 * all its global elements, including {@code article}, in included modules, so the app used to report "No root element
 * found".
 */
class SampleXmlIncludedRootsTest {

    @TempDir
    Path dir;

    @Test
    void rootsFromIncludedDocumentsAreOfferedAndAnUnreferencedOneIsTheDefault() throws Exception {
        Path main = write("main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:doc" xmlns="urn:doc"
                           elementFormDefault="qualified">
                  <xs:include schemaLocation="blocks.xsd"/>
                  <xs:include schemaLocation="article.xsd"/>
                  <xs:import namespace="urn:other" schemaLocation="other.xsd"/>
                </xs:schema>
                """);
        write("blocks.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:doc" xmlns="urn:doc"
                           elementFormDefault="qualified">
                  <xs:element name="title" type="xs:string"/>
                  <xs:element name="block" abstract="true" type="xs:string"/>
                </xs:schema>
                """);
        write("article.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:doc" xmlns="urn:doc"
                           elementFormDefault="qualified">
                  <xs:element name="article">
                    <xs:complexType><xs:sequence><xs:element ref="title"/></xs:sequence></xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        write("other.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:other">
                  <xs:element name="foreign" type="xs:string"/>
                </xs:schema>
                """);

        XsdDocumentationService service = service(main);
        assertEquals(List.of("title", "block", "article"), service.getRootElementNames());

        // title is referenced by article and block is abstract, so article is the natural document root
        String first = service.generateSampleXml(false, 1);
        assertTrue(first.contains("<article"), first);
        assertValid(main, first);

        String realistic = SampleXmlRunner.generate(main.toFile(), false, 1, true);
        assertTrue(realistic.contains("<article"), realistic);
        assertValid(main, realistic);

        String title = service.generateSampleXml("title", false, 1);
        assertTrue(title.contains("<title"), title);
        assertValid(main, title);
    }

    @Test
    void theFirstElementOfTheMainDocumentStaysTheDefault() throws Exception {
        Path main = write("main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="notes.xsd"/>
                  <xs:element name="report" type="xs:string"/>
                </xs:schema>
                """);
        write("notes.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="note" type="xs:string"/>
                </xs:schema>
                """);

        XsdDocumentationService service = service(main);
        assertEquals(List.of("report", "note"), service.getRootElementNames());
        String xml = service.generateSampleXml(false, 1);
        assertTrue(xml.contains("<report"), xml);
        assertValid(main, xml);
    }

    @Test
    void rootsOfChameleonIncludesAreOffered() throws Exception {
        Path main = write("main.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:memo" xmlns="urn:memo"
                           elementFormDefault="qualified">
                  <xs:include schemaLocation="chameleon.xsd"/>
                </xs:schema>
                """);
        write("chameleon.xsd", """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified">
                  <xs:element name="memo" type="xs:string"/>
                </xs:schema>
                """);

        XsdDocumentationService service = service(main);
        assertEquals(List.of("memo"), service.getRootElementNames());
        String xml = service.generateSampleXml(false, 1);
        assertTrue(xml.contains("<memo"), xml);
        assertValid(main, xml);
    }

    private Path write(String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static XsdDocumentationService service(Path xsd) {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        return service;
    }

    private static void assertValid(Path xsd, String xml) throws Exception {
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
