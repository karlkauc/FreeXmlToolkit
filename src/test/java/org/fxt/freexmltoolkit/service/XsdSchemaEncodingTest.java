package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Schemas must be read with the encoding they declare (or signal with a byte order mark), not as UTF-8 text.
 */
class XsdSchemaEncodingTest {

    @TempDir
    Path dir;

    @Test
    void windows1251SchemaGeneratesSampleXml() throws Exception {
        Path xsd = dir.resolve("cyrillic.xsd");
        String schema = """
                <?xml version="1.0" encoding="windows-1251"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="Документ">
                    <xs:annotation><xs:documentation>Файл обмена</xs:documentation></xs:annotation>
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="Имя" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """;
        Files.write(xsd, schema.getBytes(Charset.forName("windows-1251")));

        String xml = generate(xsd);

        assertTrue(xml.contains("<Документ"), xml);
        assertTrue(xml.contains("<Имя>"), xml);
        assertValid(xsd, xml);
    }

    @Test
    void utf8SchemaWithByteOrderMarkGeneratesSampleXml() throws Exception {
        Path xsd = dir.resolve("bom.xsd");
        String schema = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="report">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="title" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """;
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = schema.getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);
        Files.write(xsd, content);

        String xml = generate(xsd);

        assertTrue(xml.contains("<report"), xml);
        assertValid(xsd, xml);
    }

    private static String generate(Path xsd) {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        return service.generateSampleXml(false, 1);
    }

    private static void assertValid(Path xsd, String xml) throws Exception {
        new org.apache.xerces.jaxp.validation.XMLSchemaFactory().newSchema(xsd.toFile())
                .newValidator().validate(new StreamSource(new StringReader(xml)));
    }
}
