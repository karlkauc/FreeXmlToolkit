package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link XsdInstanceLocator}: instance paths through local declarations, refs, named
 * types, extension chains, choices, groups, attributes (incl. attribute groups), includes and
 * recursive types.
 */
class XsdInstanceLocatorTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="note" type="xs:string"/>
              <xs:simpleType name="CodeType">
                <xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction>
              </xs:simpleType>
              <xs:complexType name="BaseType">
                <xs:sequence><xs:element name="id" type="xs:int"/></xs:sequence>
                <xs:attribute name="version" type="xs:string"/>
              </xs:complexType>
              <xs:complexType name="ItemType">
                <xs:complexContent>
                  <xs:extension base="BaseType">
                    <xs:sequence>
                      <xs:element name="code" type="CodeType" maxOccurs="3"/>
                      <xs:group ref="Extras"/>
                    </xs:sequence>
                    <xs:attributeGroup ref="Common"/>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
              <xs:group name="Extras">
                <xs:choice>
                  <xs:element name="a" type="xs:string"/>
                  <xs:element ref="note" minOccurs="0"/>
                </xs:choice>
              </xs:group>
              <xs:attributeGroup name="Common">
                <xs:attribute name="lang" type="xs:language"/>
              </xs:attributeGroup>
              <xs:complexType name="FolderType">
                <xs:sequence>
                  <xs:element name="folder" type="FolderType" minOccurs="0" maxOccurs="unbounded"/>
                </xs:sequence>
              </xs:complexType>
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="item" type="ItemType" maxOccurs="unbounded"/>
                    <xs:element name="local">
                      <xs:simpleType><xs:restriction base="xs:int"/></xs:simpleType>
                    </xs:element>
                    <xs:element name="folder" type="FolderType"/>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:ID" use="required"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static XsdSchema parse(String xsd) throws Exception {
        return new XsdNodeFactory().fromString(xsd);
    }

    @Test
    void instancePathDropsPrefixesPredicatesAndAttributes() {
        assertEquals(List.of("Root", "b", "c"), XsdInstanceLocator.instancePath("/a:Root/b[2]/c"));
        assertEquals(List.of("Root"), XsdInstanceLocator.instancePath("/Root/@id"));
    }

    @Test
    void locatesGlobalAndLocalElements() throws Exception {
        XsdSchema schema = parse(XSD);
        var root = XsdInstanceLocator.locateElement(schema, List.of("root")).orElseThrow();
        assertTrue(root.global());
        assertEquals("root", root.declaration().getName());

        var local = XsdInstanceLocator.locateElement(schema, List.of("root", "local")).orElseThrow();
        assertFalse(local.global());
        assertNull(local.namedType(), "an inline simple type is no named type");
        assertFalse(local.foreign());
    }

    @Test
    void followsNamedTypeAndExtensionBaseChain() throws Exception {
        XsdSchema schema = parse(XSD);
        var code = XsdInstanceLocator.locateElement(schema, List.of("root", "item", "code")).orElseThrow();
        assertEquals("code", code.declaration().getName());
        assertEquals(3, code.declaration().getMaxOccurs());
        assertEquals("CodeType", code.namedType().getName());

        var baseId = XsdInstanceLocator.locateElement(schema, List.of("root", "item", "id")).orElseThrow();
        assertEquals("xs:int", ((XsdElement) baseId.declaration()).getType(), "particle inherited from the extension base");
    }

    @Test
    void descendsIntoGroupChoiceAndResolvesRefs() throws Exception {
        XsdSchema schema = parse(XSD);
        var a = XsdInstanceLocator.locateElement(schema, List.of("root", "item", "a"));
        assertTrue(a.isPresent(), "element of a referenced group's choice");

        var note = XsdInstanceLocator.locateElement(schema, List.of("root", "item", "note")).orElseThrow();
        assertNotSame(note.particle(), note.declaration(), "a ref particle resolves to the global declaration");
        assertEquals(0, note.particle().getMinOccurs(), "cardinality stays on the particle");
        assertTrue(note.global());
        assertEquals("xs:string", ((XsdElement) note.declaration()).getType());
    }

    @Test
    void locatesAttributesIncludingAttributeGroupsAndBaseTypes() throws Exception {
        XsdSchema schema = parse(XSD);
        var id = XsdInstanceLocator.locate(schema, List.of("root"), "id").orElseThrow();
        assertEquals("required", ((XsdAttribute) id.declaration()).getUse());

        assertTrue(XsdInstanceLocator.locate(schema, List.of("root", "item"), "lang").isPresent(),
                "attribute of a referenced attribute group");
        assertTrue(XsdInstanceLocator.locate(schema, List.of("root", "item"), "version").isPresent(),
                "attribute of the extension base type");
        assertTrue(XsdInstanceLocator.locate(schema, List.of("root", "item"), "nope").isEmpty());
    }

    @Test
    void recursiveTypesTerminate() throws Exception {
        XsdSchema schema = parse(XSD);
        var deep = XsdInstanceLocator.locateElement(schema,
                List.of("root", "folder", "folder", "folder", "folder"));
        assertTrue(deep.isPresent());
        assertEquals("FolderType", deep.get().namedType().getName());
    }

    @Test
    void unknownStepsYieldEmpty() throws Exception {
        XsdSchema schema = parse(XSD);
        assertTrue(XsdInstanceLocator.locateElement(schema, List.of("nope")).isEmpty());
        assertTrue(XsdInstanceLocator.locateElement(schema, List.of("root", "item", "zzz")).isEmpty());
    }

    @Test
    void includedDeclarationsAreForeign(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("part.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="part" type="xs:string"/>
                </xs:schema>
                """);
        Path main = dir.resolve("main.xsd");
        String mainXsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="part.xsd"/>
                  <xs:element name="own" type="xs:string"/>
                </xs:schema>
                """;
        Files.writeString(main, mainXsd);
        XsdSchema schema = new XsdNodeFactory().fromFile(main);
        assertTrue(XsdInstanceLocator.locateElement(schema, List.of("part")).orElseThrow().foreign());
        assertFalse(XsdInstanceLocator.locateElement(schema, List.of("own")).orElseThrow().foreign());
    }
}
