package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

/**
 * The whole-schema overview SVG is also an image file of the PDF documentation, read by FOP's XML parser:
 * its zoom script must not break well-formedness ({@code &&} / {@code <} outside a CDATA section).
 */
class XsdDocumentationOverviewSvgTest {

    @Test
    void overviewSvgIsWellFormedXml(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("small.xsd");
        Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="order">
                    <xs:complexType><xs:sequence>
                      <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:schema>
                """, StandardCharsets.UTF_8);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(false);
        XsdDocumentationSvgService svg = new XsdDocumentationSvgService();
        svg.setDocumentationData(service.xsdDocumentationData);

        String overview = svg.printOverviewSvg();
        assertTrue(overview.contains("<script"), "the overview keeps its zoom script");
        assertDoesNotThrow(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(overview))));
    }
}
