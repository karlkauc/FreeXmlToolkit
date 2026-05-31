package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Collectors;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TypeLibrary}: collecting the top-level named simple/complex
 * types of a schema for the Type Library panel.
 */
class TypeLibraryTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="AgeType">
                <xs:restriction base="xs:integer"/>
              </xs:simpleType>
              <xs:complexType name="PersonType">
                <xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence>
              </xs:complexType>
              <xs:element name="person" type="PersonType"/>
            </xs:schema>
            """;

    @Test
    void collectsTopLevelNamedTypesOnly() throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        List<String> names = TypeLibrary.collectNamedTypes(schema).stream()
                .map(XsdNode::getName).collect(Collectors.toList());

        assertTrue(names.contains("AgeType"));
        assertTrue(names.contains("PersonType"));
        assertFalse(names.contains("person"), "top-level element is not a type");
        assertEquals(2, names.size());
    }

    @Test
    void nullSchemaYieldsEmptyList() {
        assertTrue(TypeLibrary.collectNamedTypes(null).isEmpty());
    }
}
