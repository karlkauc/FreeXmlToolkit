package org.fxt.freexmltoolkit.service.xsd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link XsdParsingServiceImpl}.
 */
class XsdParsingServiceTest {

    @TempDir
    Path tempDir;

    private XsdParsingService service;

    @BeforeEach
    void setUp() {
        service = new XsdParsingServiceImpl();
    }

    @Test
    void parse_validSimpleSchema_shouldSucceed() throws Exception {
        // Create a simple XSD file
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/test"
                           elementFormDefault="qualified">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        Path xsdFile = tempDir.resolve("simple.xsd");
        Files.writeString(xsdFile, xsdContent);

        ParsedSchema result = service.parse(xsdFile);

        assertNotNull(result);
        assertNotNull(result.getDocument());
        assertNotNull(result.getSchemaElement());
        assertTrue(result.getSourceFile().isPresent());
        assertEquals(xsdFile.toAbsolutePath().normalize(), result.getSourceFile().get());
        assertEquals("http://example.com/test", result.getTargetNamespace());
        assertFalse(result.hasIncludes());
        assertFalse(result.hasImports());
    }

    @Test
    void parse_schemaWithNamespaceDeclarations_shouldExtractThem() throws Exception {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           xmlns:tns="http://example.com/test"
                           xmlns:other="http://example.com/other"
                           targetNamespace="http://example.com/test">
                    <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """;

        Path xsdFile = tempDir.resolve("namespaces.xsd");
        Files.writeString(xsdFile, xsdContent);

        ParsedSchema result = service.parse(xsdFile);

        assertNotNull(result.getNamespaceDeclarations());
        assertEquals("http://www.w3.org/2001/XMLSchema", result.getNamespaceDeclarations().get("xs"));
        assertEquals("http://example.com/test", result.getNamespaceDeclarations().get("tns"));
        assertEquals("http://example.com/other", result.getNamespaceDeclarations().get("other"));
    }

    @Test
    void parse_fileNotFound_shouldThrowException() {
        Path nonExistent = tempDir.resolve("nonexistent.xsd");

        XsdParseException ex = assertThrows(XsdParseException.class,
                () -> service.parse(nonExistent));

        assertEquals(XsdParseException.ErrorType.FILE_NOT_FOUND, ex.getErrorType());
    }

    @Test
    void parse_malformedXml_shouldThrowException() throws Exception {
        String badXml = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">" +
                "<xs:element name=\"unclosed\">";

        Path xsdFile = tempDir.resolve("malformed.xsd");
        Files.writeString(xsdFile, badXml);

        XsdParseException ex = assertThrows(XsdParseException.class,
                () -> service.parse(xsdFile));

        assertEquals(XsdParseException.ErrorType.MALFORMED_XML, ex.getErrorType());
    }

    @Test
    void parse_notXsdSchema_shouldThrowException() throws Exception {
        String notXsd = "<?xml version=\"1.0\"?><root>Not a schema</root>";

        Path xsdFile = tempDir.resolve("notxsd.xml");
        Files.writeString(xsdFile, notXsd);

        XsdParseException ex = assertThrows(XsdParseException.class,
                () -> service.parse(xsdFile));

        assertEquals(XsdParseException.ErrorType.INVALID_SCHEMA, ex.getErrorType());
    }

    @Test
    void parse_nullPath_shouldThrowException() {
        assertThrows(XsdParseException.class, () -> service.parse((Path) null));
    }

    @Test
    void parseString_validContent_shouldSucceed() throws Exception {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="test" type="xs:int"/>
                </xs:schema>
                """;

        ParsedSchema result = service.parse(xsdContent, tempDir);

        assertNotNull(result);
        assertTrue(result.getSourceFile().isEmpty());
    }

    @Test
    void parseString_nullContent_shouldThrowException() {
        assertThrows(XsdParseException.class, () -> service.parse((String) null, tempDir));
    }

    @Test
    void parseString_emptyContent_shouldThrowException() {
        assertThrows(XsdParseException.class, () -> service.parse("", tempDir));
    }

    @Test
    void parse_withInclude_shouldResolveInclude() throws Exception {
        // Create included schema
        String includedContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:complexType name="IncludedType">
                        <xs:sequence>
                            <xs:element name="field" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        Path includedFile = tempDir.resolve("included.xsd");
        Files.writeString(includedFile, includedContent);

        // Create main schema with include
        String mainContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="included.xsd"/>
                    <xs:element name="root" type="IncludedType"/>
                </xs:schema>
                """;

        Path mainFile = tempDir.resolve("main.xsd");
        Files.writeString(mainFile, mainContent);

        ParsedSchema result = service.parse(mainFile);

        assertNotNull(result);
        assertTrue(result.hasIncludes());
        assertEquals(1, result.getResolvedIncludes().size());

        ParsedSchema.ResolvedInclude include = result.getResolvedIncludes().get(0);
        assertEquals("included.xsd", include.schemaLocation());
        assertTrue(include.isResolved());
        assertNotNull(include.parsedSchema());
    }

    @Test
    void parse_withMissingInclude_shouldReportError() throws Exception {
        String mainContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="nonexistent.xsd"/>
                </xs:schema>
                """;

        Path mainFile = tempDir.resolve("main.xsd");
        Files.writeString(mainFile, mainContent);

        ParsedSchema result = service.parse(mainFile);

        assertTrue(result.hasIncludes());
        ParsedSchema.ResolvedInclude include = result.getResolvedIncludes().get(0);
        assertFalse(include.isResolved());
        assertNotNull(include.error());
    }

    @Test
    void parse_withFlattenMode_shouldFlattenIncludes() throws Exception {
        // Create included schema
        String includedContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="included" type="xs:string"/>
                    <xs:complexType name="IncludedType">
                        <xs:sequence>
                            <xs:element name="field" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        Path includedFile = tempDir.resolve("types.xsd");
        Files.writeString(includedFile, includedContent);

        // Create main schema
        String mainContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="types.xsd"/>
                    <xs:element name="main" type="xs:string"/>
                </xs:schema>
                """;

        Path mainFile = tempDir.resolve("main.xsd");
        Files.writeString(mainFile, mainContent);

        XsdParseOptions options = XsdParseOptions.forFlattening();
        ParsedSchema result = service.parse(mainFile, options);

        assertNotNull(result);
        assertEquals(XsdParseOptions.IncludeMode.FLATTEN, options.getIncludeMode());

        // Verify that the content was actually flattened into the DOM
        org.w3c.dom.Element schemaElement = result.getSchemaElement();
        org.w3c.dom.NodeList children = schemaElement.getChildNodes();

        // Count xs:element and xs:complexType children
        int elementCount = 0;
        int complexTypeCount = 0;
        int includeCount = 0;
        boolean foundIncludedElement = false;
        boolean foundIncludedType = false;
        boolean foundMainElement = false;

        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node instanceof org.w3c.dom.Element elem) {
                String localName = elem.getLocalName();
                if ("element".equals(localName)) {
                    elementCount++;
                    String name = elem.getAttribute("name");
                    if ("included".equals(name)) foundIncludedElement = true;
                    if ("main".equals(name)) foundMainElement = true;
                } else if ("complexType".equals(localName)) {
                    complexTypeCount++;
                    String name = elem.getAttribute("name");
                    if ("IncludedType".equals(name)) foundIncludedType = true;
                } else if ("include".equals(localName)) {
                    includeCount++;
                }
            }
        }

        // xs:include elements should be removed
        assertEquals(0, includeCount, "xs:include should be removed after flattening");

        // Both elements should be present (main + included)
        assertEquals(2, elementCount, "Should have 2 xs:element children after flattening");
        assertTrue(foundIncludedElement, "Included element 'included' should be in the flattened schema");
        assertTrue(foundMainElement, "Original element 'main' should still be present");

        // ComplexType from included file should be present
        assertEquals(1, complexTypeCount, "Should have 1 xs:complexType from included file");
        assertTrue(foundIncludedType, "Included complexType 'IncludedType' should be in the flattened schema");
    }

    @Test
    void parse_withFlattenMode_shouldHandleNestedIncludes() throws Exception {
        // Create base schema (level 2)
        String baseContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:simpleType name="BaseType">
                        <xs:restriction base="xs:string"/>
                    </xs:simpleType>
                </xs:schema>
                """;

        Path baseFile = tempDir.resolve("base.xsd");
        Files.writeString(baseFile, baseContent);

        // Create middle schema (level 1) - includes base
        String middleContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="base.xsd"/>
                    <xs:complexType name="MiddleType">
                        <xs:sequence>
                            <xs:element name="value" type="BaseType"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;

        Path middleFile = tempDir.resolve("middle.xsd");
        Files.writeString(middleFile, middleContent);

        // Create main schema (level 0) - includes middle
        String mainContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:include schemaLocation="middle.xsd"/>
                    <xs:element name="root" type="MiddleType"/>
                </xs:schema>
                """;

        Path mainFile = tempDir.resolve("main.xsd");
        Files.writeString(mainFile, mainContent);

        XsdParseOptions options = XsdParseOptions.forFlattening();
        ParsedSchema result = service.parse(mainFile, options);

        assertNotNull(result);

        // Verify that all content from all levels was flattened
        org.w3c.dom.Element schemaElement = result.getSchemaElement();
        org.w3c.dom.NodeList children = schemaElement.getChildNodes();

        int simpleTypeCount = 0;
        int complexTypeCount = 0;
        int elementCount = 0;
        int includeCount = 0;

        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node node = children.item(i);
            if (node instanceof org.w3c.dom.Element elem) {
                String localName = elem.getLocalName();
                if ("simpleType".equals(localName)) simpleTypeCount++;
                else if ("complexType".equals(localName)) complexTypeCount++;
                else if ("element".equals(localName)) elementCount++;
                else if ("include".equals(localName)) includeCount++;
            }
        }

        // No includes should remain
        assertEquals(0, includeCount, "All xs:include elements should be removed");

        // All content should be present
        assertEquals(1, simpleTypeCount, "BaseType from base.xsd should be present");
        assertEquals(1, complexTypeCount, "MiddleType from middle.xsd should be present");
        assertEquals(1, elementCount, "root element from main.xsd should be present");
    }

    @Test
    void isValidXsd_validSchema_shouldReturnTrue() throws Exception {
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="test" type="xs:string"/>
                </xs:schema>
                """;

        Path xsdFile = tempDir.resolve("valid.xsd");
        Files.writeString(xsdFile, xsdContent);

        assertTrue(service.isValidXsd(xsdFile));
    }

    @Test
    void isValidXsd_notSchema_shouldReturnFalse() throws Exception {
        String content = "<root>Not a schema</root>";

        Path file = tempDir.resolve("notschema.xml");
        Files.writeString(file, content);

        assertFalse(service.isValidXsd(file));
    }

    @Test
    void isValidXsd_nonexistent_shouldReturnFalse() {
        assertFalse(service.isValidXsd(tempDir.resolve("nonexistent.xsd")));
    }

    @Test
    void isValidXsd_null_shouldReturnFalse() {
        assertFalse(service.isValidXsd(null));
    }

    @Test
    void clearCache_shouldResetStatistics() throws Exception {
        // Parse a file to populate cache
        String xsdContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="test" type="xs:string"/>
                </xs:schema>
                """;

        Path xsdFile = tempDir.resolve("cached.xsd");
        Files.writeString(xsdFile, xsdContent);

        service.parse(xsdFile);

        XsdParsingService.CacheStatistics statsBefore = service.getCacheStatistics();
        assertTrue(statsBefore.misses() > 0 || statsBefore.size() > 0);

        service.clearCache();

        XsdParsingService.CacheStatistics statsAfter = service.getCacheStatistics();
        assertEquals(0, statsAfter.hits());
        assertEquals(0, statsAfter.misses());
        assertEquals(0, statsAfter.size());
    }

    @Test
    void cacheStatistics_hitRatio_shouldCalculateCorrectly() {
        var stats = new XsdParsingService.CacheStatistics(80, 20, 10, 1000);

        assertEquals(0.8, stats.hitRatio(), 0.001);
    }

    @Test
    void cacheStatistics_hitRatioWithZeroTotal_shouldReturnZero() {
        var stats = new XsdParsingService.CacheStatistics(0, 0, 0, 0);

        assertEquals(0.0, stats.hitRatio(), 0.001);
    }
}
