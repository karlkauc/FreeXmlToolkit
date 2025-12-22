package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XsdSampleDataGenerator - generates sample data based on XSD types and restrictions.
 */
class XsdSampleDataGeneratorTest {

    private XsdSampleDataGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new XsdSampleDataGenerator();
    }

    @Test
    @DisplayName("Should generate string sample data")
    void testGenerateStringData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("Should generate integer sample data")
    void testGenerateIntegerData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:integer");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertDoesNotThrow(() -> Integer.parseInt(result));
    }

    @Test
    @DisplayName("Should generate decimal sample data")
    void testGenerateDecimalData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:decimal");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertDoesNotThrow(() -> Double.parseDouble(result));
    }

    @Test
    @DisplayName("Should generate boolean sample data")
    void testGenerateBooleanData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:boolean");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.equals("true") || result.equals("false"));
    }

    @Test
    @DisplayName("Should generate date sample data")
    void testGenerateDateData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:date");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"), "Should match ISO date format");
    }

    @Test
    @DisplayName("Should generate datetime sample data")
    void testGenerateDateTimeData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:datetime");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("T"), "DateTime should contain T separator");
    }

    @Test
    @DisplayName("Should generate time sample data")
    void testGenerateTimeData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:time");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.matches("\\d{2}:\\d{2}:\\d{2}"), "Should match time format HH:MM:SS");
    }

    @Test
    @DisplayName("Should generate gYear sample data")
    void testGenerateGYearData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:gyear");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        int year = Integer.parseInt(result);
        assertTrue(year >= 1990 && year <= 2030, "Year should be in reasonable range");
    }

    @Test
    @DisplayName("Should select from enumeration values")
    void testGenerateFromEnumeration() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        List<String> enumValues = Arrays.asList("Red", "Green", "Blue");
        facets.put("enumeration", enumValues);

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(enumValues.contains(result), "Should select one of the enumeration values");
    }

    @Test
    @DisplayName("Should generate from pattern restriction")
    void testGenerateFromPattern() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[A-Z]{3}[0-9]{4}"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.matches("[A-Z]{3}[0-9]{4}"),
            "Generated value should match pattern: " + result);
    }

    @Test
    @DisplayName("Should respect string length restriction")
    void testGenerateWithLengthRestriction() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("length", Collections.singletonList("10"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertEquals(10, result.length(), "Generated string should have exact length");
    }

    @Test
    @DisplayName("Should respect minLength restriction")
    void testGenerateWithMinLength() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("minLength", Collections.singletonList("5"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.length() >= 5, "Generated string should meet minimum length");
    }

    @Test
    @DisplayName("Should respect maxLength restriction")
    void testGenerateWithMaxLength() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("maxLength", Collections.singletonList("8"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.length() <= 8, "Generated string should respect maximum length");
    }

    @Test
    @DisplayName("Should respect minInclusive and maxInclusive for numbers")
    void testGenerateWithMinMaxInclusive() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:integer");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("minInclusive", Collections.singletonList("10"));
        facets.put("maxInclusive", Collections.singletonList("20"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:integer", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        int value = Integer.parseInt(result);
        assertTrue(value >= 10 && value <= 20,
            "Generated value should be between 10 and 20 inclusive");
    }

    @Test
    @DisplayName("Should respect minExclusive and maxExclusive for numbers")
    void testGenerateWithMinMaxExclusive() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:integer");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("minExclusive", Collections.singletonList("5"));
        facets.put("maxExclusive", Collections.singletonList("15"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:integer", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        int value = Integer.parseInt(result);
        assertTrue(value >= 6 && value <= 14,
            "Generated value should be > 5 and < 15");
    }

    @Test
    @DisplayName("Should generate positive integer")
    void testGeneratePositiveInteger() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:positiveinteger");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        int value = Integer.parseInt(result);
        assertTrue(value > 0, "Positive integer should be > 0");
    }

    @Test
    @DisplayName("Should generate negative integer")
    void testGenerateNegativeInteger() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:negativeinteger");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        int value = Integer.parseInt(result);
        assertTrue(value < 0, "Negative integer should be < 0");
    }

    @Test
    @DisplayName("Should generate long type data")
    void testGenerateLongData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:long");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertDoesNotThrow(() -> Long.parseLong(result));
    }

    @Test
    @DisplayName("Should generate short type data")
    void testGenerateShortData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:short");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        short value = Short.parseShort(result);
        assertTrue(value >= 1 && value <= 100);
    }

    @Test
    @DisplayName("Should generate byte type data")
    void testGenerateByteData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:byte");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        byte value = Byte.parseByte(result);
        assertTrue(value >= 0 && value <= 127);
    }

    @Test
    @DisplayName("Should generate language type data")
    void testGenerateLanguageData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:language");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertEquals("en-US", result);
    }

    @Test
    @DisplayName("Should generate NCName type data")
    void testGenerateNCNameData() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:ncname");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertEquals("exampleName1", result);
    }

    @Test
    @DisplayName("Should return empty string for null element")
    void testGenerateNullElement() {
        // Act
        String result = generator.generate(null);

        // Assert
        assertEquals("", result);
    }

    @Test
    @DisplayName("Should return empty string for complex type with children")
    void testGenerateComplexTypeWithChildren() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType(null);
        element.setChildren(Arrays.asList("child1", "child2"));

        // Act
        String result = generator.generate(element);

        // Assert
        assertEquals("", result, "Complex types with children should not have text content");
    }

    @Test
    @DisplayName("Should handle unknown type gracefully")
    void testGenerateUnknownType() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:unknownType");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        // Should return empty or default value
    }

    @Test
    @DisplayName("Should prioritize enumeration over other restrictions")
    void testEnumerationPriority() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("enumeration", Arrays.asList("Option1", "Option2", "Option3"));
        facets.put("pattern", Collections.singletonList("[A-Z]+"));  // Should be ignored
        facets.put("length", Collections.singletonList("5"));        // Should be ignored

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        List<String> enums = Arrays.asList("Option1", "Option2", "Option3");
        assertTrue(enums.contains(result),
            "Should select enumeration value even with other restrictions present");
    }

    @Test
    @DisplayName("Should handle invalid pattern gracefully")
    void testInvalidPatternFallback() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        // Invalid regex that cannot be generated
        facets.put("pattern", Collections.singletonList("[[[invalid regex"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        // Should fall back to type-based generation
    }

    @Test
    @DisplayName("Should handle token type")
    void testGenerateTokenType() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:token");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("Should handle normalizedString type")
    void testGenerateNormalizedStringType() {
        // Arrange
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:normalizedstring");

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // =====================================================
    // Tests for Pattern-Structure-Aware Fallback Generation
    // =====================================================

    @Test
    @DisplayName("Should generate email-like pattern with @ literal")
    void testGenerateEmailPatternWithAtLiteral() {
        // Arrange - Email pattern from FundsXML4.xsd
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[_\\-a-zA-Z0-9\\.\\+]+@[a-zA-Z0-9]([\\.\\-a-zA-Z0-9]*[a-zA-Z0-9])*"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("@"), "Generated email pattern should contain @ symbol: " + result);
    }

    @Test
    @DisplayName("Should generate pattern with literal slash character")
    void testGeneratePatternWithSlashLiteral() {
        // Arrange - URL-like pattern
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[a-z]+/[a-z]+/[0-9]+"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("/"), "Generated pattern should contain / character: " + result);
        // Should have format like abc/def/123
        String[] parts = result.split("/");
        assertTrue(parts.length >= 2, "Should have multiple parts separated by /: " + result);
    }

    @Test
    @DisplayName("Should generate pattern with multiple literal characters")
    void testGeneratePatternWithMultipleLiterals() {
        // Arrange - Pattern like phone number: +XX-XXXXXXX
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[0-9]{2}-[0-9]{7}"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("-"), "Generated pattern should contain - character: " + result);
    }

    @Test
    @DisplayName("Should preserve literal underscore in pattern")
    void testGeneratePatternWithUnderscoreLiteral() {
        // Arrange - Pattern with underscore separator
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[A-Z]+_[0-9]+_[a-z]+"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("_"), "Generated pattern should contain _ character: " + result);
        // Should have at least 2 underscores
        long underscoreCount = result.chars().filter(ch -> ch == '_').count();
        assertTrue(underscoreCount >= 2, "Should have at least 2 underscores: " + result);
    }

    @Test
    @DisplayName("Should handle LEI-like pattern with exact quantifiers")
    void testGenerateLEILikePattern() {
        // Arrange - LEI pattern: 18 alphanumeric + 2 digits
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[0-9a-zA-Z]{18}[0-9]{2}"));
        facets.put("length", Collections.singletonList("20"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertEquals(20, result.length(), "LEI should be exactly 20 characters: " + result);
    }

    @Test
    @DisplayName("Should handle pattern with dot as separator (escaped)")
    void testGeneratePatternWithEscapedDot() {
        // Arrange - Pattern like version number: X.Y.Z
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[0-9]+\\.[0-9]+\\.[0-9]+"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("."), "Generated pattern should contain . character: " + result);
        long dotCount = result.chars().filter(ch -> ch == '.').count();
        assertTrue(dotCount >= 2, "Should have at least 2 dots for version pattern: " + result);
    }

    @Test
    @DisplayName("Should handle simple alphanumeric pattern without literals")
    void testGenerateSimplePatternNoLiterals() {
        // Arrange - Simple pattern without any literals
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[A-Z0-9]+"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Should only contain uppercase letters and digits
        assertTrue(result.matches("[A-Z0-9]+"), "Should match alphanumeric pattern: " + result);
    }

    @Test
    @DisplayName("Should handle pattern with colon literal")
    void testGeneratePatternWithColonLiteral() {
        // Arrange - Pattern like time: HH:MM
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[0-9]{2}:[0-9]{2}"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains(":"), "Generated pattern should contain : character: " + result);
    }

    @Test
    @DisplayName("Should handle currency code pattern [A-Z]{3}")
    void testGenerateCurrencyCodePattern() {
        // Arrange - Currency code pattern
        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementType("xs:string");

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("pattern", Collections.singletonList("[A-Z]{3}"));

        XsdExtendedElement.RestrictionInfo restriction =
            new XsdExtendedElement.RestrictionInfo("xs:string", facets);
        element.setRestrictionInfo(restriction);

        // Act
        String result = generator.generate(element);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.length(), "Currency code should be 3 characters");
        assertTrue(result.matches("[A-Z]{3}"), "Should match currency code pattern: " + result);
    }
}
