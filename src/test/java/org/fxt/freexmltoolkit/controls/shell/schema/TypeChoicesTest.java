package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.shell.schema.TypeChoice.Kind;
import org.fxt.freexmltoolkit.controls.shell.schema.TypeChoice.TypeDetails;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog;
import org.junit.jupiter.api.Test;

/**
 * {@link TypeChoices} offers the schema's named types (own, imported with prefix) ahead of every
 * built-in type, qualified with the schema's own XSD prefix, each with an explanation.
 */
class TypeChoicesTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:cmn="urn:common" targetNamespace="urn:main" xmlns="urn:main">
              <xs:import namespace="urn:common" schemaLocation="common.xsd"/>
              <xs:simpleType name="CurrencyCode">
                <xs:annotation>
                  <xs:documentation xml:lang="de">Währungscode</xs:documentation>
                  <xs:documentation xml:lang="en">ISO 4217   currency
                     code</xs:documentation>
                </xs:annotation>
                <xs:restriction base="xs:string">
                  <xs:enumeration value="EUR"/>
                  <xs:enumeration value="USD"/>
                  <xs:enumeration value="GBP"/>
                  <xs:enumeration value="CHF"/>
                  <xs:enumeration value="JPY"/>
                  <xs:enumeration value="CAD"/>
                  <xs:enumeration value="AUD"/>
                  <xs:pattern value="[A-Z]{3}"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:complexType name="BaseType" abstract="true" mixed="true">
                <xs:sequence><xs:element name="id" type="xs:string"/></xs:sequence>
                <xs:attribute name="version" type="xs:int"/>
              </xs:complexType>
              <xs:complexType name="AmountType">
                <xs:complexContent>
                  <xs:extension base="BaseType">
                    <xs:sequence><xs:element name="value" type="xs:decimal"/></xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
              <xs:element name="root" type="AmountType"/>
            </xs:schema>
            """;

    private static final String COMMON = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:common">
              <xs:simpleType name="Money"><xs:restriction base="xs:decimal"/></xs:simpleType>
            </xs:schema>
            """;

    private static final String OTHER = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:other">
              <xs:simpleType name="Zebra"><xs:restriction base="xs:string"/></xs:simpleType>
            </xs:schema>
            """;

    private static XsdSchema schema() throws Exception {
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema root = factory.fromString(XSD);
        root.addImportedSchema("urn:common", factory.fromString(COMMON));
        root.addImportedSchema("urn:other", factory.fromString(OTHER));
        return root;
    }

    private static TypeChoice find(List<TypeChoice> choices, String qualified) {
        return choices.stream().filter(c -> c.qualifiedName().equals(qualified)).findFirst()
                .orElseThrow(() -> new AssertionError("missing choice " + qualified + " in "
                        + choices.stream().map(TypeChoice::qualifiedName).toList()));
    }

    @Test
    void schemaTypesComeFirstThenBuiltinsByCategory() throws Exception {
        List<TypeChoice> choices = TypeChoices.of(schema());
        List<String> names = choices.stream().map(TypeChoice::qualifiedName).toList();

        // simple A–Z (own + imported), then complex A–Z, then the built-ins
        assertEquals(List.of("CurrencyCode", "cmn:Money", "Zebra", "AmountType", "BaseType"), names.subList(0, 5));
        assertEquals("xs:anyType", names.get(5));
        assertEquals(5 + XsdBuiltinTypeCatalog.all().size(), names.size());
        assertEquals(TypeChoices.SIMPLE_GROUP, find(choices, "cmn:Money").group());
        assertEquals(TypeChoices.COMPLEX_GROUP, find(choices, "BaseType").group());
        assertEquals("Numeric", find(choices, "xs:int").group());
        assertEquals(Kind.BUILTIN, find(choices, "xs:int").kind());
    }

    @Test
    void importedTypesCarryTheDeclaredPrefixOrANote() throws Exception {
        List<TypeChoice> choices = TypeChoices.of(schema());
        TypeChoice money = find(choices, "cmn:Money");
        assertEquals("Money", money.displayName());
        assertNull(money.details().get().note());
        assertEquals("Imported schema", money.details().get().source());

        TypeChoice zebra = find(choices, "Zebra");   // urn:other has no prefix in the root schema
        assertEquals(TypeChoices.UNDECLARED_NAMESPACE_NOTE, zebra.details().get().note());
    }

    @Test
    void builtinsUseTheSchemasOwnPrefix() throws Exception {
        XsdSchema xsd = new XsdNodeFactory().fromString("""
                <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                  <xsd:element name="a" type="xsd:string"/>
                </xsd:schema>
                """);
        List<TypeChoice> choices = TypeChoices.of(xsd);
        assertEquals("xsd:anyType", choices.getFirst().qualifiedName());
        TypeDetails d = find(choices, "xsd:int").details().get();
        assertEquals("xsd:long", d.baseType());
        assertEquals("restriction", d.derivation());
        assertEquals("2147483647", d.example());
        assertTrue(d.kindLabel().startsWith("Built-in type"));
        assertEquals("XSD 1.1 only", find(choices, "xsd:dateTimeStamp").details().get().note());
        assertEquals("xs", TypeChoices.schemaPrefix(null));
        XsdSchema both = new XsdNodeFactory().fromString("""
                <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xsd:simpleType name="T"><xsd:restriction base="xsd:token"/></xsd:simpleType>
                </xsd:schema>
                """);
        assertEquals("xsd", TypeChoices.schemaPrefix(both), "the prefix the schema's references use wins");
        assertEquals("xs:string", TypeChoices.of((XsdSchema) null).stream()
                .filter(c -> c.displayName().equals("string")).findFirst().orElseThrow().qualifiedName());
    }

    @Test
    void simpleTypeDetailsExplainDerivationFacetsAndDocumentation() throws Exception {
        TypeChoice currency = find(TypeChoices.of(schema()), "CurrencyCode");
        assertEquals("restriction of xs:string", currency.summary());
        TypeDetails d = currency.details().get();
        assertEquals("Simple type", d.kindLabel());
        assertEquals("xs:string", d.baseType());
        assertEquals("restriction", d.derivation());
        assertEquals("ISO 4217 currency code", d.documentation(), "English documentation wins, whitespace normalised");
        assertTrue(d.facts().contains("Enumeration: EUR, USD, GBP, CHF, JPY, CAD, +1 more"), d.facts().toString());
        assertTrue(d.facts().contains("pattern: [A-Z]{3}"), d.facts().toString());
        assertNull(d.source());
        assertNull(d.example());
    }

    @Test
    void complexTypeDetailsExplainContentAndFlags() throws Exception {
        List<TypeChoice> choices = TypeChoices.of(schema());
        TypeDetails base = find(choices, "BaseType").details().get();
        assertEquals("Complex type", base.kindLabel());
        assertTrue(base.facts().stream().anyMatch(f -> f.startsWith("abstract")), base.facts().toString());
        assertTrue(base.facts().stream().anyMatch(f -> f.startsWith("mixed")), base.facts().toString());
        assertTrue(base.facts().contains("Content: sequence"), base.facts().toString());
        assertTrue(base.facts().contains("1 attribute"), base.facts().toString());
        assertEquals("sequence", find(choices, "BaseType").summary());

        TypeChoice amount = find(choices, "AmountType");
        assertEquals("extension of BaseType", amount.summary());
        assertEquals("BaseType", amount.details().get().baseType());
        assertEquals("extension", amount.details().get().derivation());
    }

    @Test
    void choicesMatchByLocalOrQualifiedName() throws Exception {
        TypeChoice money = find(TypeChoices.of(schema()), "cmn:Money");
        assertTrue(money.matches("mon"));
        assertTrue(money.matches("cmn:"));
        assertTrue(money.matches(""));
        assertFalse(money.matches("euro"));
        assertTrue(money.startsWith("MO"));
        assertFalse(money.startsWith("oney"));
    }

    @Test
    void prefixForResolvesAgainstTheRootDeclarations() throws Exception {
        XsdSchema root = schema();
        XsdSchema common = root.getImportedSchemas().get("urn:common");
        XsdSchema other = root.getImportedSchemas().get("urn:other");
        assertEquals("cmn", TypeChoices.prefixFor(root, common));
        assertNull(TypeChoices.prefixFor(root, other));
        assertEquals("", TypeChoices.prefixFor(root, root));
        XsdSchema sameNs = new XsdSchema();
        sameNs.setTargetNamespace("urn:main");
        assertEquals("", TypeChoices.prefixFor(root, sameNs));
    }
}
