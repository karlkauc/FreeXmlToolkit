package org.fxt.freexmltoolkit.service.xsd;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.service.xsd.adapters.XsdModelAdapter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that flattening XSD schemas does not create duplicate type definitions.
 *
 * <p>This test addresses the bug where flattening an XSD file with includes would create
 * duplicate complexType and simpleType definitions because:
 * <ol>
 *   <li>SchemaResolver.flattenIncludes() already inlines content from includes into the DOM</li>
 *   <li>XsdModelAdapter.handleIncludes() was then processing includes AGAIN, causing duplicates</li>
 * </ol>
 *
 * <p>The fix is to skip include processing in XsdModelAdapter when in FLATTEN mode,
 * since the DOM has already been flattened.
 */
class XsdFlatteningDuplicateTypesTest {

    @Test
    void testNoDuplicateTypesInFlattenedSchema() throws Exception {
        // Create a main XSD that includes another XSD
        String includedXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="AccountType">
                <xs:annotation>
                  <xs:documentation xml:lang="en">Account data type</xs:documentation>
                </xs:annotation>
                <xs:sequence>
                  <xs:element name="AccountNumber" type="xs:string"/>
                  <xs:element name="Balance" type="xs:decimal"/>
                </xs:sequence>
              </xs:complexType>
              <xs:simpleType name="CurrencyType">
                <xs:restriction base="xs:string">
                  <xs:pattern value="[A-Z]{3}"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

        String mainXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="included.xsd"/>
              <xs:element name="Root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="Account" type="AccountType"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        // Create temp files
        Path tempDir = java.nio.file.Files.createTempDirectory("xsd-flatten-test");
        Path includedFile = tempDir.resolve("included.xsd");
        Path mainFile = tempDir.resolve("main.xsd");

        try {
            java.nio.file.Files.writeString(includedFile, includedXsd);
            java.nio.file.Files.writeString(mainFile, mainXsd);

            // Parse with FLATTEN mode
            XsdParseOptions options = XsdParseOptions.builder()
                    .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                    .build();

            XsdParsingService parsingService = new XsdParsingServiceImpl();
            ParsedSchema parsed = parsingService.parse(mainFile, options);

            // Convert to XsdSchema model
            XsdModelAdapter adapter = new XsdModelAdapter(options);
            XsdSchema schema = adapter.toXsdModel(parsed);

            // Count types - should have exactly 1 AccountType and 1 CurrencyType
            int accountTypeCount = 0;
            int currencyTypeCount = 0;

            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType ct && "AccountType".equals(ct.getName())) {
                    accountTypeCount++;
                }
                if (child instanceof XsdSimpleType st && "CurrencyType".equals(st.getName())) {
                    currencyTypeCount++;
                }
            }

            assertEquals(1, accountTypeCount, "Should have exactly 1 AccountType, but found " + accountTypeCount);
            assertEquals(1, currencyTypeCount, "Should have exactly 1 CurrencyType, but found " + currencyTypeCount);

            // Serialize and verify no duplicates in output
            XsdSerializer serializer = new XsdSerializer();
            String serialized = serializer.serialize(schema);

            // Count occurrences using regex
            int accountTypeSerializedCount = countOccurrences(serialized, "complexType name=\"AccountType\"");
            int currencyTypeSerializedCount = countOccurrences(serialized, "simpleType name=\"CurrencyType\"");

            assertEquals(1, accountTypeSerializedCount,
                    "Serialized XSD should have exactly 1 AccountType definition, but found " + accountTypeSerializedCount);
            assertEquals(1, currencyTypeSerializedCount,
                    "Serialized XSD should have exactly 1 CurrencyType definition, but found " + currencyTypeSerializedCount);

        } finally {
            // Cleanup
            java.nio.file.Files.deleteIfExists(includedFile);
            java.nio.file.Files.deleteIfExists(mainFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testNoDuplicatesWithNestedIncludes() throws Exception {
        // Test nested includes: main -> included1 -> included2
        String included2Xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:complexType name="BaseType">
                <xs:sequence>
                  <xs:element name="Id" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

        String included1Xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="included2.xsd"/>
              <xs:complexType name="ExtendedType">
                <xs:complexContent>
                  <xs:extension base="BaseType">
                    <xs:sequence>
                      <xs:element name="Name" type="xs:string"/>
                    </xs:sequence>
                  </xs:extension>
                </xs:complexContent>
              </xs:complexType>
            </xs:schema>
            """;

        String mainXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="included1.xsd"/>
              <xs:element name="Root" type="ExtendedType"/>
            </xs:schema>
            """;

        Path tempDir = java.nio.file.Files.createTempDirectory("xsd-nested-flatten-test");
        Path included2File = tempDir.resolve("included2.xsd");
        Path included1File = tempDir.resolve("included1.xsd");
        Path mainFile = tempDir.resolve("main.xsd");

        try {
            java.nio.file.Files.writeString(included2File, included2Xsd);
            java.nio.file.Files.writeString(included1File, included1Xsd);
            java.nio.file.Files.writeString(mainFile, mainXsd);

            XsdParseOptions options = XsdParseOptions.builder()
                    .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                    .build();

            XsdParsingService parsingService = new XsdParsingServiceImpl();
            ParsedSchema parsed = parsingService.parse(mainFile, options);

            XsdModelAdapter adapter = new XsdModelAdapter(options);
            XsdSchema schema = adapter.toXsdModel(parsed);

            // Count types
            Map<String, Integer> typeCounts = new HashMap<>();
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType ct && ct.getName() != null) {
                    typeCounts.merge(ct.getName(), 1, Integer::sum);
                }
            }

            assertEquals(1, typeCounts.getOrDefault("BaseType", 0),
                    "Should have exactly 1 BaseType, but found " + typeCounts.getOrDefault("BaseType", 0));
            assertEquals(1, typeCounts.getOrDefault("ExtendedType", 0),
                    "Should have exactly 1 ExtendedType, but found " + typeCounts.getOrDefault("ExtendedType", 0));

        } finally {
            java.nio.file.Files.deleteIfExists(included2File);
            java.nio.file.Files.deleteIfExists(included1File);
            java.nio.file.Files.deleteIfExists(mainFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testNoDuplicatesWithDiamondIncludes() throws Exception {
        // Diamond pattern: main includes A and B, both A and B include Common
        String commonXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="CommonType">
                <xs:restriction base="xs:string">
                  <xs:maxLength value="100"/>
                </xs:restriction>
              </xs:simpleType>
            </xs:schema>
            """;

        String aXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="common.xsd"/>
              <xs:complexType name="TypeA">
                <xs:sequence>
                  <xs:element name="ValueA" type="CommonType"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

        String bXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="common.xsd"/>
              <xs:complexType name="TypeB">
                <xs:sequence>
                  <xs:element name="ValueB" type="CommonType"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

        String mainXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:include schemaLocation="a.xsd"/>
              <xs:include schemaLocation="b.xsd"/>
              <xs:element name="Root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="PartA" type="TypeA"/>
                    <xs:element name="PartB" type="TypeB"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        Path tempDir = java.nio.file.Files.createTempDirectory("xsd-diamond-flatten-test");
        Path commonFile = tempDir.resolve("common.xsd");
        Path aFile = tempDir.resolve("a.xsd");
        Path bFile = tempDir.resolve("b.xsd");
        Path mainFile = tempDir.resolve("main.xsd");

        try {
            java.nio.file.Files.writeString(commonFile, commonXsd);
            java.nio.file.Files.writeString(aFile, aXsd);
            java.nio.file.Files.writeString(bFile, bXsd);
            java.nio.file.Files.writeString(mainFile, mainXsd);

            XsdParseOptions options = XsdParseOptions.builder()
                    .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                    .build();

            XsdParsingService parsingService = new XsdParsingServiceImpl();
            ParsedSchema parsed = parsingService.parse(mainFile, options);

            XsdModelAdapter adapter = new XsdModelAdapter(options);
            XsdSchema schema = adapter.toXsdModel(parsed);

            // Count all types
            Map<String, Integer> complexTypeCounts = new HashMap<>();
            Map<String, Integer> simpleTypeCounts = new HashMap<>();

            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType ct && ct.getName() != null) {
                    complexTypeCounts.merge(ct.getName(), 1, Integer::sum);
                }
                if (child instanceof XsdSimpleType st && st.getName() != null) {
                    simpleTypeCounts.merge(st.getName(), 1, Integer::sum);
                }
            }

            // CommonType should appear exactly once (diamond deduplication)
            assertEquals(1, simpleTypeCounts.getOrDefault("CommonType", 0),
                    "CommonType should appear exactly 1 time in diamond include pattern, but found " +
                    simpleTypeCounts.getOrDefault("CommonType", 0));

            assertEquals(1, complexTypeCounts.getOrDefault("TypeA", 0),
                    "TypeA should appear exactly 1 time");
            assertEquals(1, complexTypeCounts.getOrDefault("TypeB", 0),
                    "TypeB should appear exactly 1 time");

            // Serialize and verify
            XsdSerializer serializer = new XsdSerializer();
            String serialized = serializer.serialize(schema);

            int commonCount = countOccurrences(serialized, "simpleType name=\"CommonType\"");
            assertEquals(1, commonCount,
                    "Serialized XSD should have exactly 1 CommonType definition, but found " + commonCount);

        } finally {
            java.nio.file.Files.deleteIfExists(commonFile);
            java.nio.file.Files.deleteIfExists(aFile);
            java.nio.file.Files.deleteIfExists(bFile);
            java.nio.file.Files.deleteIfExists(mainFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testFundsXML4FlatteningNoDuplicates() throws Exception {
        // Test with the actual FundsXML4 schema if available
        Path fundsXmlPath = Path.of("src/test/resources/schema/include_files/FundsXML4.xsd");

        if (!java.nio.file.Files.exists(fundsXmlPath)) {
            // Skip test if file doesn't exist
            return;
        }

        XsdParseOptions options = XsdParseOptions.builder()
                .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                .build();

        XsdParsingService parsingService = new XsdParsingServiceImpl();
        ParsedSchema parsed = parsingService.parse(fundsXmlPath, options);

        XsdModelAdapter adapter = new XsdModelAdapter(options);
        XsdSchema schema = adapter.toXsdModel(parsed);

        // Collect all named types and check for duplicates
        Set<String> complexTypeNames = new HashSet<>();
        Set<String> duplicateComplexTypes = new HashSet<>();
        Set<String> simpleTypeNames = new HashSet<>();
        Set<String> duplicateSimpleTypes = new HashSet<>();

        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType ct && ct.getName() != null) {
                if (!complexTypeNames.add(ct.getName())) {
                    duplicateComplexTypes.add(ct.getName());
                }
            }
            if (child instanceof XsdSimpleType st && st.getName() != null) {
                if (!simpleTypeNames.add(st.getName())) {
                    duplicateSimpleTypes.add(st.getName());
                }
            }
        }

        assertTrue(duplicateComplexTypes.isEmpty(),
                "Found duplicate complexTypes in flattened FundsXML4: " + duplicateComplexTypes);
        assertTrue(duplicateSimpleTypes.isEmpty(),
                "Found duplicate simpleTypes in flattened FundsXML4: " + duplicateSimpleTypes);
    }

    @Test
    void testImportPreservedWhenNotFlattening() throws Exception {
        // Test that xs:import statements are preserved when resolveImports is false
        String importedXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:ds="http://www.w3.org/2000/09/xmldsig#"
                       targetNamespace="http://www.w3.org/2000/09/xmldsig#">
              <xs:element name="Signature" type="ds:SignatureType"/>
              <xs:complexType name="SignatureType">
                <xs:sequence>
                  <xs:element name="SignedInfo" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

        String mainXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
              <xs:import namespace="http://www.w3.org/2000/09/xmldsig#"
                         schemaLocation="xmldsig.xsd"/>
              <xs:element name="Root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="Data" type="xs:string"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

        Path tempDir = java.nio.file.Files.createTempDirectory("xsd-import-test");
        Path importedFile = tempDir.resolve("xmldsig.xsd");
        Path mainFile = tempDir.resolve("main.xsd");

        try {
            java.nio.file.Files.writeString(importedFile, importedXsd);
            java.nio.file.Files.writeString(mainFile, mainXsd);

            // Parse with FLATTEN mode but WITHOUT resolving imports
            XsdParseOptions options = XsdParseOptions.builder()
                    .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                    .resolveImports(false)  // Do NOT flatten imports
                    .build();

            XsdParsingService parsingService = new XsdParsingServiceImpl();
            ParsedSchema parsed = parsingService.parse(mainFile, options);

            XsdModelAdapter adapter = new XsdModelAdapter(options);
            XsdSchema schema = adapter.toXsdModel(parsed);

            // Serialize to XSD
            XsdSerializer serializer = new XsdSerializer();
            String serialized = serializer.serialize(schema);

            // The xs:import statement should be preserved in the output
            assertTrue(serialized.contains("xs:import"),
                    "xs:import statement should be preserved when resolveImports is false");
            assertTrue(serialized.contains("namespace=\"http://www.w3.org/2000/09/xmldsig#\""),
                    "Import namespace should be preserved");
            assertTrue(serialized.contains("schemaLocation=\"xmldsig.xsd\""),
                    "Import schemaLocation should be preserved");

            // The content of the imported schema should NOT be in the output
            assertFalse(serialized.contains("SignatureType"),
                    "Imported schema content should NOT be inlined when resolveImports is false");

        } finally {
            java.nio.file.Files.deleteIfExists(importedFile);
            java.nio.file.Files.deleteIfExists(mainFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testImportFlattenedWhenOptionEnabled() throws Exception {
        // Test that xs:import content is inlined when resolveImports is true
        String importedXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.com/imported">
              <xs:complexType name="ImportedType">
                <xs:sequence>
                  <xs:element name="Value" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

        String mainXsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:imp="http://example.com/imported">
              <xs:import namespace="http://example.com/imported"
                         schemaLocation="imported.xsd"/>
              <xs:element name="Root" type="xs:string"/>
            </xs:schema>
            """;

        Path tempDir = java.nio.file.Files.createTempDirectory("xsd-import-flatten-test");
        Path importedFile = tempDir.resolve("imported.xsd");
        Path mainFile = tempDir.resolve("main.xsd");

        try {
            java.nio.file.Files.writeString(importedFile, importedXsd);
            java.nio.file.Files.writeString(mainFile, mainXsd);

            // Parse with resolveImports enabled
            XsdParseOptions options = XsdParseOptions.builder()
                    .includeMode(XsdParseOptions.IncludeMode.FLATTEN)
                    .resolveImports(true)  // DO flatten imports
                    .build();

            XsdParsingService parsingService = new XsdParsingServiceImpl();
            ParsedSchema parsed = parsingService.parse(mainFile, options);

            XsdModelAdapter adapter = new XsdModelAdapter(options);
            XsdSchema schema = adapter.toXsdModel(parsed);

            // Check that import was resolved
            boolean hasImportNode = schema.getChildren().stream()
                    .anyMatch(n -> n instanceof XsdImport);
            assertTrue(hasImportNode, "Should have XsdImport node");

            // The XsdImport should have the imported schema content loaded
            XsdImport xsdImport = (XsdImport) schema.getChildren().stream()
                    .filter(n -> n instanceof XsdImport)
                    .findFirst()
                    .orElseThrow();

            assertNotNull(xsdImport.getImportedSchema(),
                    "Imported schema should be loaded when resolveImports is true");

        } finally {
            java.nio.file.Files.deleteIfExists(importedFile);
            java.nio.file.Files.deleteIfExists(mainFile);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    private int countOccurrences(String text, String pattern) {
        Pattern p = Pattern.compile(Pattern.quote(pattern));
        Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}
