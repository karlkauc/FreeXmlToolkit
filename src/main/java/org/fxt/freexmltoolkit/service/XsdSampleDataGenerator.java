package org.fxt.freexmltoolkit.service;

import com.mifmif.common.regex.Generex;
import jakarta.xml.bind.DatatypeConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.RestrictionInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates sample data for XSD elements based on their type,
 * restrictions and annotations. This version is independent of xmlet.xsdparser.
 */
public class XsdSampleDataGenerator {

    private static final Logger logger = LogManager.getLogger(XsdSampleDataGenerator.class);
    private static final int MAX_RECURSION_DEPTH = 10;
    private static final int MAX_PATTERN_GENERATION_ATTEMPTS = 10;

    /**
     * Represents a segment of a regex pattern for structure-aware generation.
     */
    private record PatternSegment(
            String characterClass,  // e.g., "a-zA-Z0-9"
            boolean isLiteral,      // true if this is a literal character
            String literalValue,    // the literal character (if isLiteral=true)
            int minRepeat,          // minimum repetitions (1 for +, 0 for *)
            int maxRepeat           // maximum repetitions (-1 for unlimited)
    ) {
    }

    /**
     * Holds the resolved type information: base XML type and merged restrictions.
     */
    public record ResolvedType(String baseType, RestrictionInfo mergedRestriction) {}

    /**
     * Functional interface for type resolution.
     * Implementations should resolve a named type to its base XML type,
     * collecting all restrictions along the type hierarchy.
     */
    @FunctionalInterface
    public interface TypeResolver {
        /**
         * Resolves a type name to its base XML type with merged restrictions.
         * @param typeName The type name to resolve (e.g., "FundAmountType")
         * @return ResolvedType with base type (e.g., "xs:decimal") and merged facets, or null if not found
         */
        ResolvedType resolve(String typeName);
    }

    private TypeResolver typeResolver;

    /**
     * Sets the type resolver for resolving named types to base XML types.
     * @param resolver The type resolver implementation
     */
    public void setTypeResolver(TypeResolver resolver) {
        this.typeResolver = resolver;
    }

    /**
     * Public method to start data generation for an element.
     *
     * @param element The element for which data should be generated.
     * @return A string with the appropriate sample data.
     */
    public String generate(XsdExtendedElement element) {
        return generateRecursive(element, 0);
    }

    /**
     * Generates sample data for a given XSD element considering
     * data types and restrictions.
     */
    private String generateRecursive(XsdExtendedElement element, int recursionDepth) {
        if (element == null || recursionDepth > MAX_RECURSION_DEPTH) {
            return "";
        }

        RestrictionInfo restriction = element.getRestrictionInfo();

        // Priority 2: Enumerations (selection lists)
        // Filter by minInclusive/maxInclusive if present (some XSDs have conflicting constraints)
        if (restriction != null && restriction.facets().containsKey("enumeration")) {
            List<String> enumerations = restriction.facets().get("enumeration");
            if (enumerations != null && !enumerations.isEmpty()) {
                List<String> validEnums = filterEnumerationsByConstraints(enumerations, restriction);
                if (!validEnums.isEmpty()) {
                    int randomIndex = ThreadLocalRandom.current().nextInt(validEnums.size());
                    return validEnums.get(randomIndex);
                }
                // If no valid values after filtering, fall through to type-based generation
            }
        }

        // Priority 3: Regex-Pattern with length restrictions
        if (restriction != null && restriction.facets().containsKey("pattern")) {
            String result = generateFromPatternWithLengthRestrictions(restriction, element.getElementName());
            if (result != null) {
                return result;
            }
        }

        // Priority 4: Data type-based generation
        String elementType = element.getElementType();
        if (elementType == null && restriction != null) {
            elementType = restriction.base();
        }
        // Check for simpleContent extension base type (e.g., FXRate with xs:decimal)
        if (elementType == null) {
            elementType = element.getSimpleContentExtensionBase();
            if (elementType != null) {
                logger.debug("Found simpleContent extension base type '{}' for element '{}'",
                        elementType, element.getElementName());
            }
        }

        // For complex types with child elements, don't generate text content
        // UNLESS all children are attributes (start with @) - that indicates simpleContent with extension
        if (elementType == null) {
            return "";
        }

        if (element.hasChildren()) {
            // Check if all children are attributes (start with @)
            // If so, this is likely simpleContent with extension - we should generate text content
            boolean hasNonAttributeChildren = element.getChildren().stream()
                    .anyMatch(child -> !child.contains("/@"));
            if (hasNonAttributeChildren) {
                return "";
            }
        }

        // Try to resolve named types to base XML types using the type resolver
        String resolvedType = elementType;
        RestrictionInfo effectiveRestriction = restriction;

        if (typeResolver != null && !isBaseXmlType(elementType)) {
            ResolvedType resolved = typeResolver.resolve(elementType);
            if (resolved != null && resolved.baseType() != null) {
                resolvedType = resolved.baseType();
                // Merge restrictions: type hierarchy restrictions take precedence, element restrictions override
                effectiveRestriction = mergeRestrictions(resolved.mergedRestriction(), restriction);
                logger.debug("Resolved type '{}' to base type '{}' for element '{}'",
                        elementType, resolvedType, element.getElementName());
            }
        }

        // Re-check for enumerations after type resolution (type may define them)
        if (effectiveRestriction != null && effectiveRestriction.facets().containsKey("enumeration")) {
            List<String> enumerations = effectiveRestriction.facets().get("enumeration");
            if (enumerations != null && !enumerations.isEmpty()) {
                int randomIndex = ThreadLocalRandom.current().nextInt(enumerations.size());
                return enumerations.get(randomIndex);
            }
        }

        // Re-check for patterns after type resolution (type may define them)
        if (effectiveRestriction != null && effectiveRestriction.facets().containsKey("pattern")) {
            String result = generateFromPatternWithLengthRestrictions(effectiveRestriction, element.getElementName());
            if (result != null) {
                return result;
            }
        }

        String finalType = resolvedType.substring(resolvedType.lastIndexOf(":") + 1);

        return switch (finalType.toLowerCase()) {
            // String types
            case "string", "token", "normalizedstring", "name" -> generateStringSample(effectiveRestriction);
            case "language" -> "en-US";
            case "ncname" -> "exampleName1";

            // NMTOKEN and ID types - these need valid values, not empty strings
            case "nmtoken", "nmtokens" -> generateNmToken(effectiveRestriction);
            // Use a fixed ID that can be referenced by IDREFs
            case "id" -> "id_generated";
            case "idref", "idrefs" -> "id_generated";
            case "anyuri" -> "http://example.com/sample";

            // Float and Double types - use Locale.US to ensure dot decimal separator
            case "float" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, new BigDecimal("0.01"), new BigDecimal("999.99"));
                yield String.format(Locale.US, "%.2f", randomDecimal.floatValue());
            }
            case "double" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, new BigDecimal("0.01"), new BigDecimal("9999.99"));
                yield String.format(Locale.US, "%.4f", randomDecimal.doubleValue());
            }

            // Numeric types using the helper method
            case "decimal" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, new BigDecimal("1.00"), new BigDecimal("1000.00"));
                yield DatatypeConverter.printDecimal(randomDecimal.setScale(2, RoundingMode.HALF_UP));
            }
            case "integer", "positiveinteger", "nonnegativeinteger" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, BigDecimal.ONE, new BigDecimal("10000"));
                yield DatatypeConverter.printInteger(randomDecimal.toBigInteger());
            }
            case "negativeinteger", "nonpositiveinteger" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, new BigDecimal("-10000"), BigDecimal.valueOf(-1));
                yield DatatypeConverter.printInteger(randomDecimal.toBigInteger());
            }
            case "long", "unsignedlong" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, new BigDecimal("10000"), new BigDecimal("50000"));
                yield DatatypeConverter.printLong(randomDecimal.longValue());
            }
            case "int", "unsignedint" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, new BigDecimal("100"), new BigDecimal("5000"));
                yield DatatypeConverter.printInt(randomDecimal.intValue());
            }
            case "short", "unsignedshort" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, BigDecimal.ONE, new BigDecimal("100"));
                yield DatatypeConverter.printShort(randomDecimal.shortValue());
            }
            case "byte", "unsignedbyte" -> {
                BigDecimal randomDecimal = generateNumberInRange(effectiveRestriction, BigDecimal.ZERO, new BigDecimal("127"));
                yield DatatypeConverter.printByte(randomDecimal.byteValue());
            }

            // Date, Time and Boolean
            case "date" -> {
                long minDay = LocalDate.of(2020, 1, 1).toEpochDay();
                long maxDay = LocalDate.now().toEpochDay();
                long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
                yield LocalDate.ofEpochDay(randomDay).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            case "datetime" -> {
                LocalDateTime start = LocalDateTime.now().minusYears(1);
                long startSeconds = start.toEpochSecond(java.time.ZoneOffset.UTC);
                long endSeconds = LocalDateTime.now().toEpochSecond(java.time.ZoneOffset.UTC);
                long randomSeconds = ThreadLocalRandom.current().nextLong(startSeconds, endSeconds);
                yield LocalDateTime.ofEpochSecond(randomSeconds, 0, java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME);
            }
            case "time" -> LocalTime.of(
                    ThreadLocalRandom.current().nextInt(0, 24),
                    ThreadLocalRandom.current().nextInt(0, 60),
                    ThreadLocalRandom.current().nextInt(0, 60)
            ).format(DateTimeFormatter.ISO_LOCAL_TIME);
            case "gyear" ->
                    String.valueOf(ThreadLocalRandom.current().nextInt(1990, LocalDate.now().getYear() + 1));
            case "gmonth" -> String.format("--%02d", ThreadLocalRandom.current().nextInt(1, 13));
            case "gday" -> String.format("---%02d", ThreadLocalRandom.current().nextInt(1, 29));
            case "gyearmonth" -> String.format("%d-%02d",
                    ThreadLocalRandom.current().nextInt(2020, LocalDate.now().getYear() + 1),
                    ThreadLocalRandom.current().nextInt(1, 13));
            case "gmonthday" -> String.format("--%02d-%02d",
                    ThreadLocalRandom.current().nextInt(1, 13),
                    ThreadLocalRandom.current().nextInt(1, 29));
            case "duration" -> "P1Y2M3DT4H5M6S";
            case "boolean" -> String.valueOf(ThreadLocalRandom.current().nextBoolean());

            // Binary types
            case "base64binary" -> "SGVsbG8gV29ybGQ="; // "Hello World" in Base64
            case "hexbinary" -> "48656C6C6F"; // "Hello" in hex

            default -> {
                // Recursive attempt for derived types
                if (restriction != null && restriction.base() != null) {
                    XsdExtendedElement tempElement = new XsdExtendedElement();
                    tempElement.setElementType(restriction.base());
                    tempElement.setRestrictionInfo(restriction);
                    yield generateRecursive(tempElement, recursionDepth + 1);
                }
                yield ""; // Fallback for unknown simple types
            }
        };
    }

    /**
     * Generates a string from a regex pattern while respecting length restrictions.
     * Uses Generex library with length parameters when available.
     *
     * @param restriction The restriction info containing pattern and length facets
     * @param elementName The element name for logging purposes
     * @return Generated string or null if generation fails
     */
    private String generateFromPatternWithLengthRestrictions(RestrictionInfo restriction, String elementName) {
        String patternValue = restriction.facets().get("pattern").stream().findFirst().orElse(null);
        if (patternValue == null) {
            return null;
        }

        Map<String, List<String>> facets = restriction.facets();
        Integer exactLength = getIntFacet(facets, "length");
        Integer minLength = getIntFacet(facets, "minLength");
        Integer maxLength = getIntFacet(facets, "maxLength");

        // Determine target length for fallback
        int targetMinLen = exactLength != null ? exactLength : (minLength != null ? minLength : 1);
        int targetMaxLen = exactLength != null ? exactLength : (maxLength != null ? maxLength : 50);

        // Check if pattern is too complex for Generex (may cause StackOverflowError)
        // Patterns with nested groups, lookahead/lookbehind, or recursive structures are problematic
        boolean patternTooComplex = isPatternTooComplex(patternValue);

        if (patternTooComplex) {
            logger.debug("Pattern '{}' is too complex for Generex, using fallback generator for element '{}'",
                    patternValue, elementName);
            return generateFallbackForPattern(patternValue, targetMinLen);
        }

        try {
            Generex generex = new Generex(patternValue);

            // If exact length is specified, use it
            if (exactLength != null) {
                return generateWithLengthHint(generex, exactLength, exactLength, patternValue);
            }

            // If min and max length are specified, use them
            if (minLength != null && maxLength != null) {
                return generateWithLengthHint(generex, minLength, maxLength, patternValue);
            }

            // If only minLength is specified
            if (minLength != null) {
                return generateWithLengthHint(generex, minLength, minLength + 20, patternValue);
            }

            // If only maxLength is specified
            if (maxLength != null) {
                return generateWithLengthHint(generex, 1, maxLength, patternValue);
            }

            // No length restrictions - still use validation and fallback logic
            // Use 1-50 as reasonable default range
            return generateWithLengthHint(generex, 1, 50, patternValue);

        } catch (StackOverflowError e) {
            // Generex can cause StackOverflowError on complex patterns with recursive structures
            logger.warn("StackOverflowError generating sample from pattern '{}' for element '{}'. Using fallback.",
                    patternValue, elementName);
            return generateFallbackForPattern(patternValue, targetMinLen);
        } catch (Exception e) {
            logger.warn("Could not generate sample from pattern '{}' for element '{}'. Falling back. Error: {}",
                    patternValue, elementName, e.getMessage());
            return generateFallbackForPattern(patternValue, targetMinLen);
        }
    }

    /**
     * Checks if a regex pattern is too complex for Generex (likely to cause StackOverflowError).
     * Complex patterns include: deeply nested groups, lookahead/lookbehind, certain email patterns, etc.
     */
    private boolean isPatternTooComplex(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // Count nesting depth of parentheses
        int maxDepth = 0;
        int currentDepth = 0;
        for (char c : pattern.toCharArray()) {
            if (c == '(') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == ')') {
                currentDepth--;
            }
        }

        // Patterns with deep nesting (>5 levels) are problematic
        if (maxDepth > 5) {
            return true;
        }

        // Patterns with lookahead/lookbehind assertions
        if (pattern.contains("(?=") || pattern.contains("(?!") ||
            pattern.contains("(?<=") || pattern.contains("(?<!")) {
            return true;
        }

        // Patterns with many alternations combined with repetition operators
        // This is a common cause of exponential backtracking
        long alternationCount = pattern.chars().filter(c -> c == '|').count();
        boolean hasUnboundedRepetition = pattern.contains("+") || pattern.contains("*");
        if (alternationCount > 10 && hasUnboundedRepetition) {
            return true;
        }

        // Common email pattern indicators that cause issues
        if (pattern.contains("@") && (pattern.contains("+") || pattern.contains("*"))) {
            // Email-like patterns often cause recursion issues in Generex
            return true;
        }

        // Very long patterns are often problematic
        return pattern.length() > 500;
    }

    /**
     * Generates a string using Generex with length hints, with retry logic.
     * Validates that the generated string actually matches the pattern.
     */
    private String generateWithLengthHint(Generex generex, int minLen, int maxLen, String pattern) {
        // Compile pattern for validation (XML Schema patterns are implicitly anchored)
        java.util.regex.Pattern regexPattern;
        try {
            regexPattern = java.util.regex.Pattern.compile("^" + pattern + "$");
        } catch (Exception e) {
            logger.debug("Could not compile pattern '{}' for validation: {}", pattern, e.getMessage());
            regexPattern = null;
        }

        // Try using Generex's random(min, max) if the pattern supports it
        try {
            String result = generex.random(minLen, maxLen);
            if (isValidResult(result, minLen, maxLen, regexPattern)) {
                return result;
            }
        } catch (StackOverflowError e) {
            // Generex can cause StackOverflowError on recursive patterns
            logger.debug("Generex random(min, max) caused StackOverflowError for pattern '{}', using fallback", pattern);
            return generateFallbackForPattern(pattern, minLen);
        } catch (Exception e) {
            // Generex random(min, max) may throw exceptions for some patterns
            logger.debug("Generex random(min, max) failed for pattern '{}': {}", pattern, e.getMessage());
        }

        // Fallback: try generating multiple times and pick one with valid length AND pattern match
        try {
            for (int attempt = 0; attempt < MAX_PATTERN_GENERATION_ATTEMPTS; attempt++) {
                String result = generex.random();
                if (isValidResult(result, minLen, maxLen, regexPattern)) {
                    return result;
                }
            }
        } catch (StackOverflowError e) {
            // Generex can cause StackOverflowError on recursive patterns
            logger.debug("Generex random() caused StackOverflowError for pattern '{}', using fallback", pattern);
            return generateFallbackForPattern(pattern, minLen);
        }

        // Last resort: use fallback generator which is pattern-aware
        logger.debug("Generex failed to produce valid output for pattern '{}', using fallback generator", pattern);
        return generateFallbackForPattern(pattern, minLen);
    }

    /**
     * Validates if a generated result meets length and pattern requirements.
     */
    private boolean isValidResult(String result, int minLen, int maxLen, java.util.regex.Pattern regexPattern) {
        if (result == null) return false;
        if (result.length() < minLen || result.length() > maxLen) return false;
        return regexPattern == null || regexPattern.matcher(result).matches();
    }

    /**
     * Generates a fallback string for patterns that Generex cannot handle.
     * Now parses pattern structure to detect and preserve literal characters.
     */
    private String generateFallbackForPattern(String pattern, int targetLength) {
        // Try to parse the pattern structure
        List<PatternSegment> segments = parsePatternStructure(pattern);

        // If parsing found literal characters, use structure-aware generation
        boolean hasLiterals = segments.stream().anyMatch(PatternSegment::isLiteral);
        if (hasLiterals) {
            return generateFromSegments(segments, targetLength);
        }

        // Fallback to simple character class extraction (original behavior)
        return generateSimpleFallback(pattern, targetLength);
    }

    /**
     * Parses a regex pattern into segments, identifying literal characters
     * and character class segments.
     *
     * @param pattern The regex pattern to parse
     * @return List of pattern segments in order
     */
    private List<PatternSegment> parsePatternStructure(String pattern) {
        List<PatternSegment> segments = new ArrayList<>();
        int i = 0;

        while (i < pattern.length()) {
            char c = pattern.charAt(i);

            // Character class: [...]
            if (c == '[') {
                int end = findClosingBracket(pattern, i);
                String charClass = pattern.substring(i + 1, end);

                // Check for quantifier after ]
                int[] repeatInfo = parseQuantifier(pattern, end + 1);
                segments.add(new PatternSegment(charClass, false, null, repeatInfo[0], repeatInfo[1]));
                i = repeatInfo[2]; // Move past quantifier
            }
            // Escaped character: \x
            else if (c == '\\' && i + 1 < pattern.length()) {
                char escaped = pattern.charAt(i + 1);
                // \s, \d, \w are character class shorthands
                if (escaped == 's' || escaped == 'd' || escaped == 'w' ||
                        escaped == 'S' || escaped == 'D' || escaped == 'W') {
                    int[] repeatInfo = parseQuantifier(pattern, i + 2);
                    segments.add(new PatternSegment(String.valueOf(escaped), false, null, repeatInfo[0], repeatInfo[1]));
                    i = repeatInfo[2];
                } else {
                    // Escaped literal (like \@, \., \-)
                    segments.add(new PatternSegment(null, true, String.valueOf(escaped), 1, 1));
                    i += 2;
                }
            }
            // Grouping: (...) - skip but process contents
            else if (c == '(' || c == ')') {
                i++;
            }
            // Quantifiers without preceding element (skip)
            else if (c == '*' || c == '+' || c == '?' || c == '{') {
                i++;
                // Skip digits and closing brace for {n,m}
                while (i < pattern.length() && (Character.isDigit(pattern.charAt(i)) || pattern.charAt(i) == ',' || pattern.charAt(i) == '}')) {
                    i++;
                }
            }
            // Literal character (like @, /, -)
            else if (!isRegexMetachar(c)) {
                segments.add(new PatternSegment(null, true, String.valueOf(c), 1, 1));
                i++;
            }
            // Other metacharacters (skip)
            else {
                i++;
            }
        }

        return segments;
    }

    /**
     * Checks if the character is a regex metacharacter.
     */
    private boolean isRegexMetachar(char c) {
        return "^$.*+?{}[]()|\\\n".indexOf(c) >= 0;
    }

    /**
     * Finds the closing bracket for a character class.
     */
    private int findClosingBracket(String pattern, int start) {
        for (int i = start + 1; i < pattern.length(); i++) {
            if (pattern.charAt(i) == '\\' && i + 1 < pattern.length()) {
                i++; // Skip escaped character
            } else if (pattern.charAt(i) == ']') {
                return i;
            }
        }
        return pattern.length() - 1;
    }

    /**
     * Parses a quantifier after a pattern element.
     *
     * @return int[3] = {minRepeat, maxRepeat, newIndex}
     */
    private int[] parseQuantifier(String pattern, int index) {
        if (index >= pattern.length()) {
            return new int[]{1, 1, index}; // Default: exactly 1
        }

        char c = pattern.charAt(index);
        return switch (c) {
            case '*' -> new int[]{0, 10, index + 1};  // 0 to many (cap at 10)
            case '+' -> new int[]{1, 10, index + 1};  // 1 to many (cap at 10)
            case '?' -> new int[]{0, 1, index + 1};   // 0 or 1
            case '{' -> parseExplicitQuantifier(pattern, index);
            default -> new int[]{1, 1, index};        // No quantifier = exactly 1
        };
    }

    /**
     * Parses an explicit quantifier like {3} or {2,5}.
     */
    private int[] parseExplicitQuantifier(String pattern, int start) {
        int end = pattern.indexOf('}', start);
        if (end < 0) return new int[]{1, 1, start};

        String content = pattern.substring(start + 1, end);
        String[] parts = content.split(",");

        try {
            int min = Integer.parseInt(parts[0].trim());
            int max = parts.length > 1 && !parts[1].trim().isEmpty()
                    ? Integer.parseInt(parts[1].trim())
                    : min;
            return new int[]{min, Math.min(max, 50), end + 1}; // Cap at 50
        } catch (NumberFormatException e) {
            return new int[]{1, 1, end + 1};
        }
    }

    /**
     * Generates a string from parsed pattern segments.
     */
    private String generateFromSegments(List<PatternSegment> segments, int targetLength) {
        StringBuilder result = new StringBuilder();

        for (PatternSegment segment : segments) {
            if (segment.isLiteral()) {
                result.append(segment.literalValue());
            } else {
                // Generate content for character class
                String chars = expandCharacterClass(segment.characterClass());
                int count = segment.minRepeat();
                if (segment.maxRepeat() > segment.minRepeat()) {
                    count = ThreadLocalRandom.current().nextInt(
                            segment.minRepeat(),
                            Math.min(segment.maxRepeat() + 1, segment.minRepeat() + 10)
                    );
                }

                for (int i = 0; i < count && !chars.isEmpty(); i++) {
                    result.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
                }
            }
        }

        // Adjust length if needed
        String output = result.toString();
        if (output.length() < targetLength) {
            // Pad with characters from first non-literal segment
            String padChars = segments.stream()
                    .filter(s -> !s.isLiteral())
                    .map(s -> expandCharacterClass(s.characterClass()))
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse("ABCDEFGHIJKLMNOPQRSTUVWXYZ");

            StringBuilder padded = new StringBuilder(output);
            while (padded.length() < targetLength && !padChars.isEmpty()) {
                padded.append(padChars.charAt(ThreadLocalRandom.current().nextInt(padChars.length())));
            }
            output = padded.toString();
        }

        return output;
    }

    /**
     * Expands a character class string to all allowed characters.
     */
    private String expandCharacterClass(String charClass) {
        if (charClass == null) return "";

        StringBuilder chars = new StringBuilder();

        // Handle shorthand classes
        if (charClass.equals("d") || charClass.equals("\\d")) {
            return "0123456789";
        }
        if (charClass.equals("w") || charClass.equals("\\w")) {
            return "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_";
        }
        if (charClass.equals("s") || charClass.equals("\\s")) {
            return " ";
        }

        // Parse ranges like A-Z, 0-9
        int i = 0;
        while (i < charClass.length()) {
            char c = charClass.charAt(i);

            // Escaped character
            if (c == '\\' && i + 1 < charClass.length()) {
                char next = charClass.charAt(i + 1);
                if (next == '-' || next == '.' || next == '+' || next == '_') {
                    chars.append(next);
                }
                i += 2;
                continue;
            }

            // Range like A-Z (but not at start or end where - is literal)
            if (i + 2 < charClass.length() && charClass.charAt(i + 1) == '-' && i > 0) {
                char start = c;
                char end = charClass.charAt(i + 2);
                for (char ch = start; ch <= end; ch++) {
                    chars.append(ch);
                }
                i += 3;
                continue;
            }

            // Single character (hyphen at start/end is literal)
            if (c != '-' || i == 0 || i == charClass.length() - 1) {
                chars.append(c);
            }
            i++;
        }

        return chars.toString();
    }

    /**
     * Original simple fallback for patterns without detected structure.
     */
    private String generateSimpleFallback(String pattern, int targetLength) {
        // Extract character class from pattern if possible
        // Common patterns: [A-Z]*, [A-Z0-9]*, [a-zA-Z]+, [A-Z\-\s]*, etc.

        // Check what characters are allowed in the pattern
        boolean hasUppercase = pattern.contains("[A-Z]") || pattern.contains("A-Z");
        boolean hasLowercase = pattern.contains("[a-z]") || pattern.contains("a-z");
        boolean hasDigits = pattern.contains("[0-9]") || pattern.contains("0-9");
        // Hyphen in character class is escaped as \- or placed at start/end like [-A-Z] or [A-Z-]
        boolean hasHyphen = pattern.contains("\\-") || pattern.matches(".*\\[[-].*") || pattern.matches(".*[-]\\].*");
        // Dot as literal is escaped as \. (unescaped . means any character)
        boolean hasDot = pattern.contains("\\.");
        boolean hasComma = pattern.contains(",");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < targetLength; i++) {
            // Build a list of possible characters based on the pattern
            StringBuilder charOptions = new StringBuilder();
            if (hasUppercase) charOptions.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            if (hasLowercase) charOptions.append("abcdefghijklmnopqrstuvwxyz");
            if (hasDigits) charOptions.append("0123456789");
            if (hasHyphen) charOptions.append("-");
            if (hasDot) charOptions.append(".");
            if (hasComma) charOptions.append(",");
            // Note: we avoid whitespace in generated content as it can cause issues

            if (charOptions.isEmpty()) {
                // Default to uppercase if pattern not recognized
                charOptions.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            }

            int randomIndex = ThreadLocalRandom.current().nextInt(charOptions.length());
            sb.append(charOptions.charAt(randomIndex));
        }
        return sb.toString();
    }

    /**
     * Extracts an integer facet value from the facets map.
     */
    private Integer getIntFacet(Map<String, List<String>> facets, String facetName) {
        if (facets.containsKey(facetName)) {
            try {
                return Integer.parseInt(facets.get(facetName).getFirst());
            } catch (NumberFormatException e) {
                logger.warn("Could not parse {} value: {}", facetName, facets.get(facetName).getFirst());
            }
        }
        return null;
    }

    /**
     * Generates a valid NMTOKEN value.
     * NMTOKEN can contain letters, digits, hyphens, underscores, colons, and periods.
     */
    private String generateNmToken(RestrictionInfo restriction) {
        // Generate a timestamp-like NMTOKEN for values like export.time
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String base = "T" + timestamp;

        if (restriction != null) {
            Integer maxLength = getIntFacet(restriction.facets(), "maxLength");
            if (maxLength != null && base.length() > maxLength) {
                return base.substring(0, maxLength);
            }
        }

        return base;
    }

    private String generateStringSample(RestrictionInfo restriction) {
        String sample = "ExampleText";

        if (restriction != null) {
            Map<String, List<String>> facets = restriction.facets();
            // Exact length has highest priority
            if (facets.containsKey("length")) {
                try {
                    int length = Integer.parseInt(facets.get("length").getFirst());
                    return "a".repeat(Math.max(0, length));
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse length value: {}", facets.get("length").getFirst());
                }
            }

            Integer min = facets.containsKey("minLength") ? Integer.parseInt(facets.get("minLength").getFirst()) : null;
            Integer max = facets.containsKey("maxLength") ? Integer.parseInt(facets.get("maxLength").getFirst()) : null;

            if (min != null && max == null) {
                return "a".repeat(min);
            }
            if (min == null && max != null) {
                return sample.length() > max ? sample.substring(0, max) : sample;
            }
            if (min != null) {
                if (min > max) {
                    logger.warn("minLength ({}) is greater than maxLength ({}) for a string restriction. Using minLength.", min, max);
                    return "a".repeat(min);
                }
                int targetLength = (min.equals(max)) ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
                return "a".repeat(targetLength);
            }
        }
        return sample;
    }

    /**
     * Generates a random number (as BigDecimal) considering
     * min/max restrictions from the XSD.
     */
    private BigDecimal generateNumberInRange(RestrictionInfo restriction, BigDecimal defaultMin, BigDecimal defaultMax) {
        BigDecimal min = defaultMin;
        BigDecimal max = defaultMax;

        if (restriction != null) {
            Map<String, List<String>> facets = restriction.facets();
            if (facets.containsKey("minInclusive")) {
                try {
                    min = new BigDecimal(facets.get("minInclusive").getFirst());
                } catch (NumberFormatException e) { /* ignore */ }
            } else if (facets.containsKey("minExclusive")) {
                try {
                    min = new BigDecimal(facets.get("minExclusive").getFirst()).add(BigDecimal.ONE);
                } catch (NumberFormatException e) { /* ignore */ }
            }

            if (facets.containsKey("maxInclusive")) {
                try {
                    max = new BigDecimal(facets.get("maxInclusive").getFirst());
                } catch (NumberFormatException e) { /* ignore */ }
            } else if (facets.containsKey("maxExclusive")) {
                try {
                    max = new BigDecimal(facets.get("maxExclusive").getFirst()).subtract(BigDecimal.ONE);
                } catch (NumberFormatException e) { /* ignore */ }
            }
        }

        if (min.compareTo(max) > 0) {
            logger.warn("Min value {} is greater than max value {}. Using max value for generation.", min, max);
            return max;
        }
        if (min.compareTo(max) == 0) {
            return min;
        }

        // Apply totalDigits constraint if present
        int totalDigits = -1;
        int fractionDigits = -1;
        if (restriction != null) {
            Map<String, List<String>> facets = restriction.facets();
            if (facets.containsKey("totalDigits")) {
                try {
                    totalDigits = Integer.parseInt(facets.get("totalDigits").getFirst());
                } catch (NumberFormatException e) { /* ignore */ }
            }
            if (facets.containsKey("fractionDigits")) {
                try {
                    fractionDigits = Integer.parseInt(facets.get("fractionDigits").getFirst());
                } catch (NumberFormatException e) { /* ignore */ }
            }
        }

        // Constrain max based on totalDigits
        if (totalDigits > 0) {
            // Maximum value with totalDigits (e.g., 3 digits -> max 999)
            BigDecimal maxByDigits = BigDecimal.TEN.pow(totalDigits).subtract(BigDecimal.ONE);
            if (max.compareTo(maxByDigits) > 0) {
                max = maxByDigits;
            }
            // Ensure min doesn't exceed the max
            if (min.compareTo(max) > 0) {
                min = max;
            }
        }

        BigDecimal range = max.subtract(min);
        BigDecimal randomValue = min.add(range.multiply(BigDecimal.valueOf(Math.random())));

        // Apply fractionDigits constraint
        if (fractionDigits >= 0) {
            randomValue = randomValue.setScale(fractionDigits, RoundingMode.HALF_UP);
        }

        // Ensure totalDigits is respected (including decimal places)
        if (totalDigits > 0) {
            randomValue = constrainToTotalDigits(randomValue, totalDigits, Math.max(fractionDigits, 0));
        }

        return randomValue;
    }

    /**
     * Constrains a BigDecimal to have at most the specified total number of digits.
     */
    private BigDecimal constrainToTotalDigits(BigDecimal value, int totalDigits, int fractionDigits) {
        String strValue = value.abs().toPlainString();
        String[] parts = strValue.split("\\.");
        int intDigits = parts[0].length();
        int fracDigits = parts.length > 1 ? parts[1].length() : 0;

        if (intDigits + fracDigits > totalDigits) {
            // Need to reduce - prioritize keeping integer part
            int allowedFrac = Math.max(0, totalDigits - intDigits);
            if (allowedFrac < fracDigits) {
                value = value.setScale(allowedFrac, RoundingMode.DOWN);
            }
            // If still too many digits, reduce integer part
            strValue = value.abs().toPlainString();
            parts = strValue.split("\\.");
            intDigits = parts[0].length();
            fracDigits = parts.length > 1 ? parts[1].length() : 0;
            if (intDigits + fracDigits > totalDigits) {
                // Truncate to max allowed value
                BigDecimal maxAllowed = BigDecimal.TEN.pow(totalDigits - fracDigits).subtract(BigDecimal.ONE);
                if (fracDigits > 0) {
                    maxAllowed = maxAllowed.add(BigDecimal.ONE.subtract(BigDecimal.ONE.scaleByPowerOfTen(-fracDigits)));
                }
                value = value.signum() < 0 ? maxAllowed.negate() : maxAllowed;
            }
        }
        return value;
    }

    /**
     * Filters enumeration values by minInclusive/maxInclusive constraints.
     * Some XSD schemas define enumerations that conflict with min/max constraints.
     */
    private List<String> filterEnumerationsByConstraints(List<String> enumerations, RestrictionInfo restriction) {
        if (restriction == null) {
            return enumerations;
        }

        Map<String, List<String>> facets = restriction.facets();
        BigDecimal minInclusive = null;
        BigDecimal maxInclusive = null;

        if (facets.containsKey("minInclusive")) {
            try {
                minInclusive = new BigDecimal(facets.get("minInclusive").getFirst());
            } catch (NumberFormatException e) { /* not numeric */ }
        }
        if (facets.containsKey("maxInclusive")) {
            try {
                maxInclusive = new BigDecimal(facets.get("maxInclusive").getFirst());
            } catch (NumberFormatException e) { /* not numeric */ }
        }

        // If no numeric constraints, return all enumerations
        if (minInclusive == null && maxInclusive == null) {
            return enumerations;
        }

        // Filter numeric enumerations
        List<String> filtered = new ArrayList<>();
        final BigDecimal finalMin = minInclusive;
        final BigDecimal finalMax = maxInclusive;

        for (String enumValue : enumerations) {
            try {
                BigDecimal numValue = new BigDecimal(enumValue);
                boolean valid = finalMin == null || numValue.compareTo(finalMin) >= 0;
                if (finalMax != null && numValue.compareTo(finalMax) > 0) {
                    valid = false;
                }
                if (valid) {
                    filtered.add(enumValue);
                }
            } catch (NumberFormatException e) {
                // Non-numeric enumeration - include it (constraint doesn't apply)
                filtered.add(enumValue);
            }
        }

        return filtered;
    }

    /**
     * Checks if the given type is a base XML Schema type (xs:* or xsd:*).
     * @param typeName The type name to check
     * @return true if it's a base XML type, false otherwise
     */
    private boolean isBaseXmlType(String typeName) {
        if (typeName == null) return false;

        // Remove namespace prefix for comparison
        String localName = typeName.contains(":")
            ? typeName.substring(typeName.lastIndexOf(":") + 1)
            : typeName;

        // List of all XML Schema built-in types
        return switch (localName.toLowerCase()) {
            case "string", "normalizedstring", "token", "language", "nmtoken", "nmtokens",
                 "name", "ncname", "id", "idref", "idrefs", "entity", "entities",
                 "integer", "nonnegativeinteger", "positiveinteger", "nonpositiveinteger", "negativeinteger",
                 "long", "int", "short", "byte",
                 "unsignedlong", "unsignedint", "unsignedshort", "unsignedbyte",
                 "decimal", "float", "double",
                 "boolean",
                 "date", "time", "datetime", "duration", "gyear", "gmonth", "gday", "gyearmonth", "gmonthday",
                 "base64binary", "hexbinary",
                 "anyuri", "qname", "notation",
                 "anysimpletype", "anytype" -> true;
            default -> false;
        };
    }

    /**
     * Merges two RestrictionInfo objects. The primary restriction takes precedence,
     * but facets from secondary are included if not present in primary.
     * @param primary The primary restriction (from type hierarchy)
     * @param secondary The secondary restriction (from element)
     * @return Merged RestrictionInfo
     */
    private RestrictionInfo mergeRestrictions(RestrictionInfo primary, RestrictionInfo secondary) {
        if (primary == null) return secondary;
        if (secondary == null) return primary;

        // Start with primary base type (from resolved type hierarchy)
        String mergedBase = primary.base() != null ? primary.base() : secondary.base();

        // Merge facets: secondary (element) overrides primary (type hierarchy)
        Map<String, List<String>> mergedFacets = new java.util.HashMap<>();

        // Add all facets from primary (type hierarchy)
        if (primary.facets() != null) {
            mergedFacets.putAll(primary.facets());
        }

        // Override/add facets from secondary (element-level restrictions)
        if (secondary.facets() != null) {
            // For enumerations and patterns, we might want to intersect rather than replace
            // For now, element-level restrictions override type-level
            mergedFacets.putAll(secondary.facets());
        }

        return new RestrictionInfo(mergedBase, mergedFacets);
    }
}
