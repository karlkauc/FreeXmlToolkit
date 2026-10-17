package org.fxt.freexmltoolkit.controls.v2.xmleditor.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link XsdSchemaAdapter}'s attribute lookups: declared attributes of an element inside a
 * sequence (whose map key carries a synthetic {@code SEQUENCE_n} segment the instance path has
 * not), their type, use, enumeration and default, and the attribute value check.
 */
class XsdSchemaAdapterAttributesTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="item" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:attribute name="id" type="xs:ID" use="required"/>
                        <xs:attribute name="lang" default="en">
                          <xs:simpleType>
                            <xs:restriction base="xs:string">
                              <xs:enumeration value="en"/>
                              <xs:enumeration value="de"/>
                            </xs:restriction>
                          </xs:simpleType>
                        </xs:attribute>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static XsdSchemaAdapter adapter(Path dir) throws Exception {
        Path xsd = dir.resolve("a.xsd");
        Files.writeString(xsd, XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.TRUE);
        XsdSchemaAdapter adapter = new XsdSchemaAdapter();
        adapter.setXsdDocumentationData(service.xsdDocumentationData);
        return adapter;
    }

    @Test
    void listsAndDescribesDeclaredAttributes(@TempDir Path dir) throws Exception {
        XsdSchemaAdapter adapter = adapter(dir);
        assertEquals(List.of("id", "lang"), adapter.getValidAttributes("/root/item[2]"));

        var id = adapter.getAttributeTypeInfo("/root/item[1]", "id").orElseThrow();
        assertEquals("xs:ID", id.typeName());
        assertTrue(id.isRequired());

        var lang = adapter.getAttributeTypeInfo("/root/item", "@lang").orElseThrow();
        assertFalse(lang.isRequired());
        assertEquals(List.of("en", "de"), lang.enumerationValues());
        assertEquals("en", lang.defaultValue());

        assertTrue(adapter.getAttributeTypeInfo("/root/item", "nope").isEmpty());
        assertTrue(adapter.getValidAttributes("/root/missing").isEmpty());
    }

    @Test
    void checksAttributeValuesAgainstTheEnumeration(@TempDir Path dir) throws Exception {
        XsdSchemaAdapter adapter = adapter(dir);
        assertNull(adapter.validateAttributeValue("/root/item", "lang", "de").errorMessage());
        String message = adapter.validateAttributeValue("/root/item", "lang", "fr").errorMessage();
        assertNotNull(message, "a value outside the enumeration is reported");
        assertTrue(message.contains("en, de"), message);
    }
}
