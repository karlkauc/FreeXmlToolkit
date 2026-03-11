package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for XsdSerializer with excludeIncludedNodes.
 * <p>
 * Verifies the round-trip: parse XSD with xs:include/xs:import via XsdNodeFactory
 * (which inlines content) → serialize with excludeIncludedNodes=true → verify that
 * the output preserves directives but does NOT contain flattened content.
 *
 * @since 2.0
 */
class XsdSerializerIncludeImportRoundTripTest {

    private XsdSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new XsdSerializer();
        serializer.setExcludeIncludedNodes(true);
    }

    // ========== Round-trip with TempDir include ==========

    @Test
    @DisplayName("Round-trip: parse XSD with xs:include, serialize without flattening")
    void testRoundTripIncludeNotFlattened(@TempDir Path tempDir) throws Exception {
        String typesXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="NameType">
                        <xs:restriction base="xs:string">
                            <xs:maxLength value="100"/>
                        </xs:restriction>
                    </xs:simpleType>
                    <xs:complexType name="AddressType">
                        <xs:sequence>
                            <xs:element name="street" type="xs:string"/>
                            <xs:element name="city" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           elementFormDefault="qualified">
                    <xs:include schemaLocation="types.xsd"/>
                    <xs:element name="person" type="PersonType"/>
                    <xs:complexType name="PersonType">
                        <xs:sequence>
                            <xs:element name="name" type="NameType"/>
                            <xs:element name="address" type="AddressType"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("types.xsd"), typesXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        // Parse (factory inlines included content)
        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        // Verify factory inlined the included types
        assertTrue(schema.getChildren().stream()
                        .anyMatch(n -> n instanceof XsdSimpleType && "NameType".equals(n.getName())),
                "Factory should inline NameType from include");
        assertTrue(schema.getChildren().stream()
                        .anyMatch(n -> n instanceof XsdComplexType && "AddressType".equals(n.getName())),
                "Factory should inline AddressType from include");

        // Serialize with excludeIncludedNodes=true
        String result = serializer.serialize(schema);

        // Verify output preserves include directive
        assertTrue(result.contains("<xs:include schemaLocation=\"types.xsd\"/>"),
                "xs:include directive should be preserved in output");

        // Verify output contains main schema content
        assertTrue(result.contains("PersonType"),
                "Main schema PersonType should be present");
        assertTrue(result.contains("<xs:element name=\"person\""),
                "Main schema element 'person' should be present");

        // Verify output does NOT contain inlined content from types.xsd
        assertFalse(result.contains("<xs:simpleType name=\"NameType\">"),
                "Inlined NameType from include should NOT appear in output");
        assertFalse(result.contains("<xs:complexType name=\"AddressType\">"),
                "Inlined AddressType from include should NOT appear in output");
    }

    @Test
    @DisplayName("Round-trip: parse XSD with xs:import, serialize without flattening")
    void testRoundTripImportNotFlattened(@TempDir Path tempDir) throws Exception {
        String importedXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/common"
                           xmlns:cmn="http://example.com/common"
                           elementFormDefault="qualified">
                    <xs:simpleType name="CodeType">
                        <xs:restriction base="xs:string">
                            <xs:maxLength value="10"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:cmn="http://example.com/common"
                           elementFormDefault="qualified">
                    <xs:import namespace="http://example.com/common"
                               schemaLocation="common.xsd"/>
                    <xs:element name="order" type="OrderType"/>
                    <xs:complexType name="OrderType">
                        <xs:sequence>
                            <xs:element name="id" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("common.xsd"), importedXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        String result = serializer.serialize(schema);

        // xs:import directive should be preserved
        assertTrue(result.contains("<xs:import"),
                "xs:import directive should be preserved");
        assertTrue(result.contains("http://example.com/common"),
                "Import namespace should be preserved");

        // Main schema content should be present
        assertTrue(result.contains("OrderType"),
                "Main schema OrderType should be present");
        assertTrue(result.contains("<xs:element name=\"order\""),
                "Main schema element 'order' should be present");
    }

    @Test
    @DisplayName("Round-trip: parse XSD with both include and import, serialize without flattening")
    void testRoundTripMixedIncludeAndImport(@TempDir Path tempDir) throws Exception {
        String includedXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="IncludedType">
                        <xs:restriction base="xs:string">
                            <xs:maxLength value="50"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        String importedXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/ext"
                           xmlns:ext="http://example.com/ext">
                    <xs:simpleType name="ExtType">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:ext="http://example.com/ext"
                           elementFormDefault="qualified">
                    <xs:include schemaLocation="included.xsd"/>
                    <xs:import namespace="http://example.com/ext"
                               schemaLocation="imported.xsd"/>
                    <xs:element name="root" type="MainType"/>
                    <xs:complexType name="MainType">
                        <xs:sequence>
                            <xs:element name="value" type="IncludedType"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("included.xsd"), includedXsd);
        Files.writeString(tempDir.resolve("imported.xsd"), importedXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        String result = serializer.serialize(schema);

        // Both directives preserved
        assertTrue(result.contains("<xs:include schemaLocation=\"included.xsd\"/>"),
                "xs:include directive should be preserved");
        assertTrue(result.contains("<xs:import"),
                "xs:import directive should be preserved");

        // Main schema content present
        assertTrue(result.contains("MainType"),
                "Main schema MainType should be present");

        // Inlined content from included file NOT present
        assertFalse(result.contains("<xs:simpleType name=\"IncludedType\">"),
                "IncludedType from included file should NOT appear in serialized output");
    }

    @Test
    @DisplayName("Round-trip: multiple includes, none should be flattened")
    void testRoundTripMultipleIncludes(@TempDir Path tempDir) throws Exception {
        String types1Xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="Type1">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String types2Xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="Type2">
                        <xs:restriction base="xs:integer"/>
                    </xs:simpleType>
                    <xs:complexType name="ComplexFromInclude">
                        <xs:sequence>
                            <xs:element name="field" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types1.xsd"/>
                    <xs:include schemaLocation="types2.xsd"/>
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("types1.xsd"), types1Xsd);
        Files.writeString(tempDir.resolve("types2.xsd"), types2Xsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        // Verify factory has inlined all 3 types
        long totalTypes = schema.getChildren().stream()
                .filter(n -> n instanceof XsdSimpleType || n instanceof XsdComplexType)
                .count();
        assertTrue(totalTypes >= 3, "Factory should have inlined types from both includes");

        String result = serializer.serialize(schema);

        // Both include directives preserved
        assertTrue(result.contains("schemaLocation=\"types1.xsd\""),
                "First xs:include should be preserved");
        assertTrue(result.contains("schemaLocation=\"types2.xsd\""),
                "Second xs:include should be preserved");

        // Main element preserved
        assertTrue(result.contains("<xs:element name=\"root\""),
                "Main schema element should be present");

        // Inlined types NOT present
        assertFalse(result.contains("<xs:simpleType name=\"Type1\">"),
                "Type1 from types1.xsd should NOT appear");
        assertFalse(result.contains("<xs:simpleType name=\"Type2\">"),
                "Type2 from types2.xsd should NOT appear");
        assertFalse(result.contains("ComplexFromInclude"),
                "ComplexFromInclude from types2.xsd should NOT appear");
    }

    @Test
    @DisplayName("Round-trip: excludeIncludedNodes=false should produce flattened output")
    void testRoundTripFlattenedWhenFlagDisabled(@TempDir Path tempDir) throws Exception {
        String typesXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="FlattenedType">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types.xsd"/>
                    <xs:element name="test" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("types.xsd"), typesXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        // Use default (excludeIncludedNodes=false) for flattened output
        XsdSerializer flatSerializer = new XsdSerializer();
        String flattened = flatSerializer.serialize(schema);

        // Flattened output should contain BOTH directive and inlined content
        assertTrue(flattened.contains("<xs:include schemaLocation=\"types.xsd\"/>"),
                "xs:include directive should be present in flattened output");
        assertTrue(flattened.contains("FlattenedType"),
                "Inlined FlattenedType should appear in flattened output");
        assertTrue(flattened.contains("<xs:element name=\"test\""),
                "Main element should be present in flattened output");
    }

    // ========== Round-trip with real FundsXML4 schema ==========

    @Test
    @DisplayName("Round-trip: FundsXML4 with 15 includes should not flatten when excludeIncludedNodes=true")
    void testRoundTripFundsXml4NotFlattened() throws Exception {
        Path schemaPath = Paths.get("src/test/resources/schema/include_files/FundsXML4.xsd")
                .toAbsolutePath();

        if (!Files.exists(schemaPath)) {
            return; // Skip if test resource not available
        }

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(schemaPath);

        // Count total children (includes many inlined types)
        int totalChildren = schema.getChildren().size();

        // Count nodes from includes
        long nodesFromIncludes = schema.getChildren().stream()
                .filter(XsdNode::isFromInclude)
                .count();

        // Count include/import directives
        long directiveCount = schema.getChildren().stream()
                .filter(n -> n instanceof XsdInclude || n instanceof XsdImport)
                .count();

        // Verify factory inlined content (there should be many more children than directives)
        assertTrue(totalChildren > directiveCount + 10,
                "Factory should have inlined many types from includes. Total: " + totalChildren +
                        ", Directives: " + directiveCount);
        assertTrue(nodesFromIncludes > 0,
                "Should have nodes marked as from includes");

        // Serialize with excludeIncludedNodes=true
        String result = serializer.serialize(schema);

        // All include directives should be preserved
        assertTrue(result.contains("schemaLocation=\"FundsXML4_Core.xsd\""),
                "Include for FundsXML4_Core.xsd should be preserved");
        assertTrue(result.contains("schemaLocation=\"FundsXML4_FundStaticData.xsd\""),
                "Include for FundsXML4_FundStaticData.xsd should be preserved");

        // Import directive should be preserved
        assertTrue(result.contains("<xs:import"),
                "xs:import directive should be preserved");
        assertTrue(result.contains("http://www.w3.org/2000/09/xmldsig#"),
                "Import namespace should be preserved");

        // Main schema element (FundsXML4) should be preserved
        assertTrue(result.contains("FundsXML4"),
                "Main schema root element FundsXML4 should be present");

        // Serialized output should be much shorter than flattened output
        XsdSerializer flatSerializer = new XsdSerializer();
        String flattenedResult = flatSerializer.serialize(schema);

        assertTrue(result.length() < flattenedResult.length(),
                "Non-flattened output (" + result.length() + " chars) should be shorter " +
                        "than flattened output (" + flattenedResult.length() + " chars)");
    }

    @Test
    @DisplayName("Round-trip: saved output should be valid XML")
    void testRoundTripOutputIsValidXml(@TempDir Path tempDir) throws Exception {
        String typesXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="PhoneType">
                        <xs:restriction base="xs:string">
                            <xs:pattern value="\\+?[0-9\\-\\s]+"/>
                        </xs:restriction>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           elementFormDefault="qualified">
                    <xs:include schemaLocation="types.xsd"/>
                    <xs:element name="contact" type="ContactType"/>
                    <xs:complexType name="ContactType">
                        <xs:sequence>
                            <xs:element name="phone" type="PhoneType"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("types.xsd"), typesXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        String result = serializer.serialize(schema);

        // Verify basic XML structure
        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                "Output should start with XML declaration");
        assertTrue(result.contains("<xs:schema"),
                "Output should contain schema element");
        assertTrue(result.trim().endsWith("</xs:schema>"),
                "Output should end with closing schema tag");

        // Verify it can be re-parsed without errors
        XsdNodeFactory reparseFactory = new XsdNodeFactory();
        XsdSchema reparsed = reparseFactory.fromString(result);
        assertNotNull(reparsed, "Serialized output should be re-parseable");

        // Re-parsed schema should have include directive but NOT the inlined type
        assertTrue(reparsed.getChildren().stream()
                        .anyMatch(n -> n instanceof XsdInclude),
                "Re-parsed schema should have xs:include directive");
        assertFalse(reparsed.getChildren().stream()
                        .anyMatch(n -> n instanceof XsdSimpleType && "PhoneType".equals(n.getName())),
                "Re-parsed schema should NOT have inlined PhoneType (include not resolved without base dir)");
    }

    @Test
    @DisplayName("Round-trip: transitive includes (A→B→C) should all be excluded")
    void testRoundTripTransitiveIncludes(@TempDir Path tempDir) throws Exception {
        String baseXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="BaseType">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String middleXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="base.xsd"/>
                    <xs:simpleType name="MiddleType">
                        <xs:restriction base="xs:integer"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        String mainXsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="middle.xsd"/>
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        Files.writeString(tempDir.resolve("base.xsd"), baseXsd);
        Files.writeString(tempDir.resolve("middle.xsd"), middleXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"), mainXsd);

        XsdNodeFactory factory = new XsdNodeFactory();
        XsdSchema schema = factory.fromFile(mainFile);

        String result = serializer.serialize(schema);

        // Only main schema's include directive should be present
        assertTrue(result.contains("schemaLocation=\"middle.xsd\""),
                "Main schema's xs:include for middle.xsd should be preserved");

        // Main element should be present
        assertTrue(result.contains("<xs:element name=\"root\""),
                "Main schema element should be present");

        // Types from middle.xsd and base.xsd should NOT be present
        assertFalse(result.contains("MiddleType"),
                "MiddleType from middle.xsd should NOT appear");
        assertFalse(result.contains("BaseType"),
                "BaseType from base.xsd (transitive include) should NOT appear");
    }
}
