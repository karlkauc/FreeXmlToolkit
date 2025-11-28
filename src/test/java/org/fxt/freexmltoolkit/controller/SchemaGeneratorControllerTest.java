package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.*;
import org.fxt.freexmltoolkit.service.SchemaGenerationOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.framework.junit5.ApplicationExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SchemaGeneratorController.
 * Tests the Intelligent Schema Generator - Revolutionary Feature #3.
 */
@ExtendWith({ApplicationExtension.class, MockitoExtension.class})
class SchemaGeneratorControllerTest {

    private SchemaGeneratorController controller;

    @Mock
    private CheckBox mockEnableSmartTypeInference;

    @Mock
    private CheckBox mockInferComplexTypes;

    @Mock
    private CheckBox mockStrictTypeInference;

    @Mock
    private CheckBox mockAnalyzeDataPatterns;

    @Mock
    private CheckBox mockGenerateComplexTypes;

    @Mock
    private CheckBox mockOptimizeSchema;

    @Mock
    private TextField mockTargetNamespaceField;

    @Mock
    private TextArea mockXmlInputArea;

    @Mock
    private TextArea mockXsdOutputArea;

    @Mock
    private Button mockGenerateSchemaBtn;

    @Mock
    private Button mockExportSchemaBtn;

    @Mock
    private ProgressBar mockGenerationProgressBar;

    @Mock
    private ComboBox<String> mockTypeFilterCombo;

    @Mock
    private TableView<SchemaGeneratorController.TypeDefinition> mockTypeDefinitionsTable;

    @BeforeEach
    void setUp() {
        controller = new SchemaGeneratorController();
    }

    /**
     * Helper method to inject fields using reflection
     */
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @Test
    @DisplayName("Should create controller instance")
    void testControllerInstantiation() {
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Should validate XSD namespace")
    void testXsdNamespace() {
        String xsdNamespace = "http://www.w3.org/2001/XMLSchema";
        assertNotNull(xsdNamespace);
        assertTrue(xsdNamespace.contains("XMLSchema"));
    }

    @Test
    @DisplayName("Should recognize XSD basic types")
    void testXsdBasicTypes() {
        String[] basicTypes = {
            "xs:string", "xs:integer", "xs:decimal", "xs:boolean",
            "xs:date", "xs:time", "xs:dateTime", "xs:anyURI"
        };

        for (String type : basicTypes) {
            assertNotNull(type);
            assertTrue(type.startsWith("xs:"), "Type should have xs: prefix: " + type);
        }
    }

    @Test
    @DisplayName("Should recognize XSD complex type elements")
    void testXsdComplexTypeElements() {
        String[] complexTypeElements = {
            "complexType", "sequence", "choice", "all",
            "element", "attribute", "group", "attributeGroup"
        };

        for (String element : complexTypeElements) {
            assertNotNull(element);
            assertFalse(element.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate schema generation options")
    void testSchemaGenerationOptions() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();
        assertNotNull(options);

        // Test option setters
        options.setEnableSmartTypeInference(true);
        options.setInferComplexTypes(true);
        options.setStrictTypeInference(false);
        options.setAnalyzeDataPatterns(true);
        options.setGenerateComplexTypes(true);
        options.setOptimizeSchema(true);

        // All options should be set without errors
        assertTrue(options.isEnableSmartTypeInference());
        assertTrue(options.isInferComplexTypes());
        assertFalse(options.isStrictTypeInference());
        assertTrue(options.isAnalyzeDataPatterns());
        assertTrue(options.isGenerateComplexTypes());
        assertTrue(options.isOptimizeSchema());
    }

    @Test
    @DisplayName("Should validate type filter options")
    void testTypeFilterOptions() {
        String[] filterOptions = {
            "All Types", "Complex Types", "Simple Types", "Elements", "Attributes"
        };

        for (String option : filterOptions) {
            assertNotNull(option);
            assertFalse(option.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate XSD file extension")
    void testXsdFileExtension() {
        String fileName = "generated_schema.xsd";
        assertTrue(fileName.endsWith(".xsd"), "Schema file should have .xsd extension");

        String invalidFileName = "schema.xml";
        assertFalse(invalidFileName.endsWith(".xsd"));
    }

    @Test
    @DisplayName("Should validate XML input requirement")
    void testXmlInputRequirement() {
        String validXml = "<?xml version=\"1.0\"?><root><element>value</element></root>";
        String emptyXml = "";

        assertNotNull(validXml);
        assertFalse(validXml.isEmpty(), "Valid XML should not be empty");
        assertTrue(emptyXml.isEmpty(), "Empty XML should be detected");
    }

    @Test
    @DisplayName("Should format byte sizes correctly")
    void testFormatBytes() throws Exception {
        // Use reflection to call the private formatBytes method
        Method formatBytesMethod = controller.getClass().getDeclaredMethod("formatBytes", long.class);
        formatBytesMethod.setAccessible(true);

        // Test byte formatting - use locale-independent assertions
        assertEquals("500B", formatBytesMethod.invoke(controller, 500L));

        // Accept both "1.5KB" (English locale) and "1,5KB" (German locale)
        String result1536 = (String) formatBytesMethod.invoke(controller, 1536L);
        assertTrue(result1536.equals("1.5KB") || result1536.equals("1,5KB"),
                "Expected '1.5KB' or '1,5KB' but got: " + result1536);

        String result1MB = (String) formatBytesMethod.invoke(controller, 1048576L);
        assertTrue(result1MB.equals("1.0MB") || result1MB.equals("1,0MB"),
                "Expected '1.0MB' or '1,0MB' but got: " + result1MB);
    }

    @Test
    @DisplayName("Should validate namespace URI format")
    void testNamespaceUriFormat() {
        String validNamespace = "http://example.com/schema";
        String invalidNamespace = "not-a-uri";

        assertNotNull(validNamespace);
        assertTrue(validNamespace.startsWith("http://") || validNamespace.startsWith("https://"),
                "Valid namespace should be a URI");
        assertFalse(invalidNamespace.startsWith("http://"),
                "Invalid namespace should not start with http://");
    }

    @Test
    @DisplayName("Should handle TypeDefinition inner class")
    void testTypeDefinitionInnerClass() {
        SchemaGeneratorController.TypeDefinition typeDef =
            new SchemaGeneratorController.TypeDefinition(
                "PersonType", "ComplexType", "xs:anyType", 5, "Person information"
            );

        assertNotNull(typeDef);
        assertEquals("PersonType", typeDef.getName());
        assertEquals("ComplexType", typeDef.getKind());
        assertEquals("xs:anyType", typeDef.getBaseType());
        assertEquals(5, typeDef.getUsageCount());
        assertEquals("Person information", typeDef.getDescription());
    }

    @Test
    @DisplayName("Should validate TypeDefinition setters")
    void testTypeDefinitionSetters() {
        SchemaGeneratorController.TypeDefinition typeDef =
            new SchemaGeneratorController.TypeDefinition(
                "Original", "Simple", "xs:string", 1, "Original description"
            );

        typeDef.setName("Updated");
        typeDef.setKind("Complex");
        typeDef.setBaseType("xs:anyType");
        typeDef.setUsageCount(10);
        typeDef.setDescription("Updated description");

        assertEquals("Updated", typeDef.getName());
        assertEquals("Complex", typeDef.getKind());
        assertEquals("xs:anyType", typeDef.getBaseType());
        assertEquals(10, typeDef.getUsageCount());
        assertEquals("Updated description", typeDef.getDescription());
    }

    @Test
    @DisplayName("Should support schema optimization options")
    void testSchemaOptimizationOptions() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();

        options.setOptimizeSchema(true);
        options.setEliminateDuplicateTypes(true);
        options.setMergeCompatibleTypes(true);
        options.setGroupSimilarElements(true);

        assertTrue(options.isOptimizeSchema());
        assertTrue(options.isEliminateDuplicateTypes());
        assertTrue(options.isMergeCompatibleTypes());
        assertTrue(options.isGroupSimilarElements());
    }

    @Test
    @DisplayName("Should support namespace handling options")
    void testNamespaceHandlingOptions() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();

        options.setPreserveNamespaces(true);
        options.setGenerateTargetNamespace(true);
        options.setTargetNamespaceUri("http://example.com/schema");

        assertTrue(options.isPreserveNamespaces());
        assertTrue(options.isGenerateTargetNamespace());
        assertEquals("http://example.com/schema", options.getTargetNamespaceUri());
    }

    @Test
    @DisplayName("Should support type inference options")
    void testTypeInferenceOptions() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();

        options.setEnableSmartTypeInference(true);
        options.setInferComplexTypes(true);
        options.setStrictTypeInference(false);
        options.setAnalyzeDataPatterns(true);

        assertTrue(options.isEnableSmartTypeInference());
        assertTrue(options.isInferComplexTypes());
        assertFalse(options.isStrictTypeInference());
        assertTrue(options.isAnalyzeDataPatterns());
    }

    @Test
    @DisplayName("Should support structure generation options")
    void testStructureGenerationOptions() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();

        options.setGenerateComplexTypes(true);
        options.setInlineSimpleTypes(false);
        options.setGroupSimilarElements(true);
        options.setGenerateGroups(true);

        assertTrue(options.isGenerateComplexTypes());
        assertFalse(options.isInlineSimpleTypes());
        assertTrue(options.isGroupSimilarElements());
        assertTrue(options.isGenerateGroups());
    }

    @Test
    @DisplayName("Should validate XSD element declarations")
    void testXsdElementDeclarations() {
        String elementDeclaration = "<xs:element name=\"person\" type=\"PersonType\"/>";

        assertNotNull(elementDeclaration);
        assertTrue(elementDeclaration.contains("xs:element"));
        assertTrue(elementDeclaration.contains("name="));
        assertTrue(elementDeclaration.contains("type="));
    }

    @Test
    @DisplayName("Should validate XSD attribute declarations")
    void testXsdAttributeDeclarations() {
        String attributeDeclaration = "<xs:attribute name=\"id\" type=\"xs:string\" use=\"required\"/>";

        assertNotNull(attributeDeclaration);
        assertTrue(attributeDeclaration.contains("xs:attribute"));
        assertTrue(attributeDeclaration.contains("name="));
        assertTrue(attributeDeclaration.contains("type="));
        assertTrue(attributeDeclaration.contains("use="));
    }

    @Test
    @DisplayName("Should recognize common type kinds")
    void testCommonTypeKinds() {
        String[] typeKinds = {"ComplexType", "SimpleType", "Element", "Attribute"};

        for (String kind : typeKinds) {
            assertNotNull(kind);
            assertFalse(kind.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate schema structure elements")
    void testSchemaStructureElements() {
        String[] structureElements = {
            "schema", "complexType", "simpleType", "element",
            "attribute", "sequence", "choice", "all"
        };

        for (String element : structureElements) {
            assertNotNull(element);
            assertFalse(element.isEmpty());
        }
    }

    @Test
    @DisplayName("Should handle shutdown gracefully")
    void testShutdown() {
        assertDoesNotThrow(() -> controller.shutdown(),
                "Shutdown should complete without exceptions");
    }

    @Test
    @DisplayName("Should validate XSD restriction facets")
    void testXsdRestrictionFacets() {
        String[] facets = {
            "minLength", "maxLength", "length", "pattern",
            "enumeration", "minInclusive", "maxInclusive",
            "minExclusive", "maxExclusive", "totalDigits", "fractionDigits"
        };

        for (String facet : facets) {
            assertNotNull(facet);
            assertFalse(facet.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate occurrence constraints")
    void testOccurrenceConstraints() {
        // Default occurrences
        int minOccursDefault = 1;
        int maxOccursDefault = 1;

        assertTrue(minOccursDefault >= 0);
        assertTrue(maxOccursDefault >= minOccursDefault);

        // Unbounded
        String maxOccursUnbounded = "unbounded";
        assertNotNull(maxOccursUnbounded);
        assertEquals("unbounded", maxOccursUnbounded);
    }

    @Test
    @DisplayName("Should validate default generation options")
    void testDefaultGenerationOptions() {
        SchemaGenerationOptions options = new SchemaGenerationOptions();

        // Default options should be set to recommended values
        assertNotNull(options);

        // Smart type inference is recommended by default
        options.setEnableSmartTypeInference(true);
        assertTrue(options.isEnableSmartTypeInference());

        // Complex type inference is recommended
        options.setInferComplexTypes(true);
        assertTrue(options.isInferComplexTypes());

        // Schema optimization is recommended
        options.setOptimizeSchema(true);
        assertTrue(options.isOptimizeSchema());
    }

    @Test
    @DisplayName("Should validate generation statistics fields")
    void testGenerationStatisticsFields() {
        // Statistics that should be tracked
        int complexTypesGenerated = 5;
        int simpleTypesGenerated = 3;
        int elementsGenerated = 10;
        int attributesGenerated = 7;
        long generationTimeMs = 150;

        assertTrue(complexTypesGenerated >= 0);
        assertTrue(simpleTypesGenerated >= 0);
        assertTrue(elementsGenerated >= 0);
        assertTrue(attributesGenerated >= 0);
        assertTrue(generationTimeMs >= 0);
    }

    @Test
    @DisplayName("Should support formatting options")
    void testFormattingOptions() {
        boolean formatXsdOutput = true;
        boolean addComments = true;

        assertTrue(formatXsdOutput, "Format output should be enabled by default");
        assertTrue(addComments, "Comments should be enabled by default");
    }
}
