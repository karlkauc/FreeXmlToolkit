package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link XsdDocumentationService#setEagerSampleData(boolean) setEagerSampleData(false)} (editor schema
 * loads) still yields a sample for every element — generated when it is first read instead of during
 * the traversal. Fixed and default values are taken as they are.
 */
class XsdDocumentationServiceLazySampleTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="Order">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Code">
                                <xs:simpleType>
                                    <xs:restriction base="xs:string">
                                        <xs:pattern value="[A-Z]{2}[0-9]{4}"/>
                                    </xs:restriction>
                                </xs:simpleType>
                            </xs:element>
                            <xs:element name="Currency" type="xs:string" default="EUR"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    void lazySamplesAreGeneratedOnRead(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("order.xsd");
        Files.writeString(xsd, XSD);

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.setEagerSampleData(false);
        service.processXsd(Boolean.TRUE);

        XsdExtendedElement code = element(service, "Code");
        assertNotNull(code, "the traversal must still build the element");
        String sample = code.getSampleData();
        assertFalse(sample.isEmpty(), "a lazily loaded element still has a sample");
        assertTrue(sample.matches("[A-Z]{2}[0-9]{4}"), sample);

        XsdExtendedElement currency = element(service, "Currency");
        assertNotNull(currency);
        assertTrue("EUR".equals(currency.getSampleData()), "a default value is used as is");
    }

    /** The element by name: map keys also carry compositor segments ({@code /Order/SEQUENCE_…/Code}). */
    private static XsdExtendedElement element(XsdDocumentationService service, String name) {
        return service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> name.equals(e.getElementName()))
                .findFirst().orElse(null);
    }
}
