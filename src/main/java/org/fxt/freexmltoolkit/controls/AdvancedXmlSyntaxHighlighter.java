package org.fxt.freexmltoolkit.controls;

import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced XML Syntax Highlighter with semantic awareness and theme integration.
 * Provides intelligent highlighting with error detection, validation feedback, and customizable themes.
 *
 * @deprecated Use {@link org.fxt.freexmltoolkit.controls.v2.editor.managers.SyntaxHighlightManagerV2} instead.
 *             This V1 class will be removed in a future version.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class AdvancedXmlSyntaxHighlighter {

    private static final Logger logger = LogManager.getLogger(AdvancedXmlSyntaxHighlighter.class);

    // XML Patterns for advanced parsing
    private static final Pattern XML_DECLARATION_PATTERN = Pattern.compile(
            "<\\?xml\\s+version\\s*=\\s*[\"'][^\"']*[\"'](?:\\s+encoding\\s*=\\s*[\"'][^\"']*[\"'])?(?:\\s+standalone\\s*=\\s*[\"'][^\"']*[\"'])?\\s*\\?>"
    );

    private static final Pattern XML_PROCESSING_INSTRUCTION_PATTERN = Pattern.compile(
            "<\\?[a-zA-Z][^>]*\\?>"
    );

    private static final Pattern XML_COMMENT_PATTERN = Pattern.compile(
            "<!--[\\s\\S]*?-->"
    );

    private static final Pattern XML_CDATA_PATTERN = Pattern.compile(
            "<!\\[CDATA\\[[\\s\\S]*?\\]\\]>"
    );

    private static final Pattern XML_DOCTYPE_PATTERN = Pattern.compile(
            "<!DOCTYPE[\\s\\S]*?>"
    );

    private static final Pattern XML_ELEMENT_PATTERN = Pattern.compile(
            "<(/?)([a-zA-Z_:][a-zA-Z0-9._:-]*)((?:\\s+[a-zA-Z_:][a-zA-Z0-9._:-]*(?:\\s*=\\s*(?:[\"'][^\"']*[\"']|[^\\s>]+))?)*)(\\s*/?)>"
    );

    private static final Pattern XML_ATTRIBUTE_PATTERN = Pattern.compile(
            "([a-zA-Z_:][a-zA-Z0-9._:-]*)\\s*(=)\\s*([\"'])([^\"']*)\\3"
    );

    private static final Pattern XML_NAMESPACE_PATTERN = Pattern.compile(
            "xmlns(?::([a-zA-Z_:][a-zA-Z0-9._:-]*))?\\s*=\\s*[\"'][^\"']*[\"']"
    );

    private static final Pattern XML_ENTITY_PATTERN = Pattern.compile(
            "&(?:[a-zA-Z][a-zA-Z0-9]*|#(?:[0-9]+|x[0-9a-fA-F]+));"
    );

    // Error patterns
    private static final Pattern UNCLOSED_TAG_PATTERN = Pattern.compile(
            "<[a-zA-Z_:][a-zA-Z0-9._:-]*[^>]*(?![/>]$)"
    );

    private static final Pattern MALFORMED_ATTRIBUTE_PATTERN = Pattern.compile(
            "[a-zA-Z_:][a-zA-Z0-9._:-]*\\s*=\\s*[^\"'\\s>][^\\s>]*"
    );

    // Semantic highlighting configuration
    private boolean semanticHighlightingEnabled = true;
    private boolean errorHighlightingEnabled = true;
    private boolean warningHighlightingEnabled = true;
    private final Set<String> knownNamespaces = new HashSet<>();
    private final Set<String> deprecatedElements = new HashSet<>();
    private final Set<String> deprecatedAttributes = new HashSet<>();

    // Theme integration
    private final ModernXmlThemeManager themeManager;
    private ModernXmlThemeManager.XmlHighlightTheme currentTheme;

    // Performance settings
    private static final int CHUNK_SIZE = 10000; // Process in 10KB chunks for large files
    private static final int LARGE_FILE_THRESHOLD = 100000; // 100KB

    public AdvancedXmlSyntaxHighlighter() {
        themeManager = ModernXmlThemeManager.getInstance();
        currentTheme = themeManager.getCurrentTheme();

        // Listen for theme changes
        themeManager.addThemeChangeListener((oldTheme, newTheme) -> {
            currentTheme = newTheme;
            logger.debug("XML highlighter updated theme to: {}", newTheme.getDisplayName());
        });

        initializeKnownNamespaces();
        initializeDeprecatedElements();

        logger.info("Advanced XML Syntax Highlighter initialized with semantic awareness");
    }

    /**
     * Initialize known XML namespaces for better semantic highlighting
     */
    private void initializeKnownNamespaces() {
        knownNamespaces.addAll(Arrays.asList(
                "http://www.w3.org/XML/1998/namespace",
                "http://www.w3.org/2001/XMLSchema-instance",
                "http://www.w3.org/2001/XMLSchema",
                "http://www.w3.org/1999/XSL/Transform",
                "http://www.w3.org/1999/xhtml",
                "http://www.w3.org/2000/svg",
                "http://www.w3.org/1999/XSL/Format",
                "http://www.springframework.org/schema/beans",
                "http://maven.apache.org/POM/4.0.0"
        ));
    }

    /**
     * Initialize deprecated elements and attributes
     */
    private void initializeDeprecatedElements() {
        deprecatedElements.addAll(Arrays.asList(
                "font", "center", "u", "strike", "big", "small"
        ));

        deprecatedAttributes.addAll(Arrays.asList(
                "bgcolor", "color", "face", "size", "align", "valign"
        ));
    }

    // ========== Main Highlighting Methods ==========

    /**
     * Compute syntax highlighting with theme integration
     */
    public StyleSpans<Collection<String>> computeHighlighting(String text) {
        if (text == null || text.isEmpty()) {
            return StyleSpans.singleton(Collections.emptyList(), 0);
        }

        try {
            if (text.length() > LARGE_FILE_THRESHOLD) {
                return computeChunkedHighlighting(text);
            } else {
                return computeAdvancedHighlighting(text);
            }
        } catch (Exception e) {
            logger.warn("Error during XML syntax highlighting", e);
            return StyleSpans.singleton(Collections.emptyList(), text.length());
        }
    }

    /**
     * Compute highlighting asynchronously for large files
     */
    public Task<StyleSpans<Collection<String>>> computeHighlightingAsync(String text) {
        return new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                updateMessage("Computing XML syntax highlighting...");
                updateProgress(0, 1);

                StyleSpans<Collection<String>> result = computeHighlighting(text);

                updateProgress(1, 1);
                updateMessage("XML highlighting completed");

                return result;
            }
        };
    }

    /**
     * Compute advanced syntax highlighting with semantic awareness
     */
    private StyleSpans<Collection<String>> computeAdvancedHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastKwEnd = 0;

        // Create a list of all matches with their positions
        List<HighlightMatch> matches = new ArrayList<>();

        // Find all XML constructs
        findXmlDeclarations(text, matches);
        findProcessingInstructions(text, matches);
        findComments(text, matches);
        findCDataSections(text, matches);
        findDoctypeDeclarations(text, matches);
        findElements(text, matches);
        findEntities(text, matches);

        // Add error highlighting if enabled
        if (errorHighlightingEnabled) {
            findErrors(text, matches);
        }

        // Sort matches by start position
        matches.sort(Comparator.comparingInt(m -> m.start));

        // Build style spans
        for (HighlightMatch match : matches) {
            if (match.start > lastKwEnd) {
                // Add unstyled text before this match
                spansBuilder.add(Collections.emptyList(), match.start - lastKwEnd);
            }

            if (match.start >= lastKwEnd) {
                // Add styled text for this match
                spansBuilder.add(getStyleClasses(match.type, match.context), match.end - match.start);
                lastKwEnd = match.end;
            }
        }

        // Add remaining unstyled text
        if (lastKwEnd < text.length()) {
            spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        }

        return spansBuilder.create();
    }

    /**
     * Compute highlighting in chunks for very large files
     */
    private StyleSpans<Collection<String>> computeChunkedHighlighting(String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        int textLength = text.length();
        int processed = 0;

        while (processed < textLength) {
            int chunkEnd = Math.min(processed + CHUNK_SIZE, textLength);
            String chunk = text.substring(processed, chunkEnd);

            // Adjust chunk boundaries to avoid breaking XML constructs
            chunkEnd = adjustChunkBoundary(text, processed, chunkEnd);
            if (chunkEnd > processed) {
                chunk = text.substring(processed, chunkEnd);
            }

            StyleSpans<Collection<String>> chunkHighlighting = computeAdvancedHighlighting(chunk);

            // Merge chunk highlighting into main builder
            // Use a simpler approach to append the chunk highlighting
            spansBuilder.addAll(chunkHighlighting);

            processed = chunkEnd;
        }

        return spansBuilder.create();
    }

    /**
     * Adjust chunk boundary to avoid breaking XML constructs
     */
    private int adjustChunkBoundary(String text, int start, int end) {
        if (end >= text.length()) {
            return end;
        }

        // Look back for a safe breaking point (whitespace or >)
        for (int i = end; i > start + CHUNK_SIZE / 2; i--) {
            char c = text.charAt(i);
            if (c == '>' || Character.isWhitespace(c)) {
                return i + 1;
            }
        }

        return end; // Fallback to original end
    }

    // ========== Pattern Matching Methods ==========

    /**
     * Find XML declarations
     */
    private void findXmlDeclarations(String text, List<HighlightMatch> matches) {
        Matcher matcher = XML_DECLARATION_PATTERN.matcher(text);
        while (matcher.find()) {
            matches.add(new HighlightMatch(
                    matcher.start(), matcher.end(),
                    HighlightType.XML_DECLARATION,
                    matcher.group()
            ));
        }
    }

    /**
     * Find processing instructions
     */
    private void findProcessingInstructions(String text, List<HighlightMatch> matches) {
        Matcher matcher = XML_PROCESSING_INSTRUCTION_PATTERN.matcher(text);
        while (matcher.find()) {
            matches.add(new HighlightMatch(
                    matcher.start(), matcher.end(),
                    HighlightType.XML_PROCESSING_INSTRUCTION,
                    matcher.group()
            ));
        }
    }

    /**
     * Find XML comments
     */
    private void findComments(String text, List<HighlightMatch> matches) {
        Matcher matcher = XML_COMMENT_PATTERN.matcher(text);
        while (matcher.find()) {
            matches.add(new HighlightMatch(
                    matcher.start(), matcher.end(),
                    HighlightType.XML_COMMENT,
                    matcher.group()
            ));
        }
    }

    /**
     * Find CDATA sections
     */
    private void findCDataSections(String text, List<HighlightMatch> matches) {
        Matcher matcher = XML_CDATA_PATTERN.matcher(text);
        while (matcher.find()) {
            matches.add(new HighlightMatch(
                    matcher.start(), matcher.end(),
                    HighlightType.XML_CDATA,
                    matcher.group()
            ));
        }
    }

    /**
     * Find DOCTYPE declarations
     */
    private void findDoctypeDeclarations(String text, List<HighlightMatch> matches) {
        Matcher matcher = XML_DOCTYPE_PATTERN.matcher(text);
        while (matcher.find()) {
            matches.add(new HighlightMatch(
                    matcher.start(), matcher.end(),
                    HighlightType.XML_DECLARATION,
                    matcher.group()
            ));
        }
    }

    /**
     * Find XML elements with detailed attribute parsing
     */
    private void findElements(String text, List<HighlightMatch> matches) {
        Matcher elementMatcher = XML_ELEMENT_PATTERN.matcher(text);

        while (elementMatcher.find()) {
            String fullMatch = elementMatcher.group();
            String closingSlash = elementMatcher.group(1);
            String elementName = elementMatcher.group(2);
            String attributes = elementMatcher.group(3);
            String selfClosing = elementMatcher.group(4);

            int elementStart = elementMatcher.start();

            // Highlight opening/closing brackets
            matches.add(new HighlightMatch(
                    elementStart, elementStart + 1 + closingSlash.length(),
                    HighlightType.XML_ELEMENT_TAG,
                    "<" + closingSlash
            ));

            // Highlight element name with semantic awareness
            int nameStart = elementStart + 1 + closingSlash.length();
            int nameEnd = nameStart + elementName.length();

            HighlightType elementType = determineElementType(elementName, closingSlash, selfClosing);
            matches.add(new HighlightMatch(nameStart, nameEnd, elementType, elementName));

            // Parse and highlight attributes
            if (attributes != null && !attributes.trim().isEmpty()) {
                parseAttributes(attributes, nameEnd, matches);
            }

            // Highlight self-closing or closing bracket
            String endPart = selfClosing + ">";
            int endStart = elementMatcher.end() - endPart.length();
            matches.add(new HighlightMatch(
                    endStart, elementMatcher.end(),
                    HighlightType.XML_ELEMENT_TAG,
                    endPart
            ));
        }
    }

    /**
     * Parse and highlight XML attributes
     */
    private void parseAttributes(String attributesText, int baseOffset, List<HighlightMatch> matches) {
        Matcher attrMatcher = XML_ATTRIBUTE_PATTERN.matcher(attributesText);

        while (attrMatcher.find()) {
            String attrName = attrMatcher.group(1);
            String equals = attrMatcher.group(2);
            String quote = attrMatcher.group(3);
            String attrValue = attrMatcher.group(4);

            int attrStart = baseOffset + attrMatcher.start();

            // Highlight attribute name
            HighlightType attrType = determineAttributeType(attrName);
            matches.add(new HighlightMatch(
                    attrStart + attrMatcher.start(1),
                    attrStart + attrMatcher.end(1),
                    attrType,
                    attrName
            ));

            // Highlight equals sign
            matches.add(new HighlightMatch(
                    attrStart + attrMatcher.start(2),
                    attrStart + attrMatcher.end(2),
                    HighlightType.XML_ATTRIBUTE_EQUALS,
                    equals
            ));

            // Highlight quotes
            matches.add(new HighlightMatch(
                    attrStart + attrMatcher.start(3),
                    attrStart + attrMatcher.end(3),
                    HighlightType.XML_ATTRIBUTE_QUOTES,
                    quote
            ));

            // Highlight attribute value
            matches.add(new HighlightMatch(
                    attrStart + attrMatcher.start(4),
                    attrStart + attrMatcher.end(4),
                    HighlightType.XML_ATTRIBUTE_VALUE,
                    attrValue
            ));

            // Highlight closing quote
            int closingQuotePos = attrStart + attrMatcher.end(4);
            matches.add(new HighlightMatch(
                    closingQuotePos,
                    closingQuotePos + 1,
                    HighlightType.XML_ATTRIBUTE_QUOTES,
                    quote
            ));
        }

        // Check for namespace declarations separately
        Matcher nsMatcher = XML_NAMESPACE_PATTERN.matcher(attributesText);
        while (nsMatcher.find()) {
            matches.add(new HighlightMatch(
                    baseOffset + nsMatcher.start(),
                    baseOffset + nsMatcher.end(),
                    HighlightType.XML_NAMESPACE,
                    nsMatcher.group()
            ));
        }
    }

    /**
     * Find XML entities
     */
    private void findEntities(String text, List<HighlightMatch> matches) {
        Matcher matcher = XML_ENTITY_PATTERN.matcher(text);
        while (matcher.find()) {
            matches.add(new HighlightMatch(
                    matcher.start(), matcher.end(),
                    HighlightType.XML_ENTITY,
                    matcher.group()
            ));
        }
    }

    /**
     * Find XML errors and warnings
     */
    private void findErrors(String text, List<HighlightMatch> matches) {
        // Find malformed attributes
        Matcher malformedAttr = MALFORMED_ATTRIBUTE_PATTERN.matcher(text);
        while (malformedAttr.find()) {
            matches.add(new HighlightMatch(
                    malformedAttr.start(), malformedAttr.end(),
                    HighlightType.XML_ERROR,
                    malformedAttr.group()
            ));
        }

        // Additional error patterns can be added here
    }

    // ========== Semantic Analysis Methods ==========

    /**
     * Determine element highlight type based on semantic analysis
     */
    private HighlightType determineElementType(String elementName, String closingSlash, String selfClosing) {
        // Check if deprecated
        if (deprecatedElements.contains(elementName.toLowerCase())) {
            return HighlightType.XML_DEPRECATED;
        }

        // Determine type based on element structure
        if (!closingSlash.isEmpty()) {
            return HighlightType.XML_CLOSING_TAG;
        } else if (!selfClosing.isEmpty()) {
            return HighlightType.XML_SELF_CLOSING;
        } else {
            return HighlightType.XML_ELEMENT_NAME;
        }
    }

    /**
     * Determine attribute highlight type
     */
    private HighlightType determineAttributeType(String attrName) {
        // Check for namespace declarations
        if (attrName.startsWith("xmlns")) {
            return HighlightType.XML_NAMESPACE;
        }

        // Check if deprecated
        if (deprecatedAttributes.contains(attrName.toLowerCase())) {
            return HighlightType.XML_DEPRECATED;
        }

        return HighlightType.XML_ATTRIBUTE_NAME;
    }

    // ========== Style Management ==========

    /**
     * Get style classes for a highlight type
     */
    private Collection<String> getStyleClasses(HighlightType type, String context) {
        List<String> classes = new ArrayList<>();

        // Add base type class
        String baseClass = "xml-" + type.name().toLowerCase().replace("_", "-");
        classes.add(baseClass);

        // Add semantic classes if enabled
        if (semanticHighlightingEnabled) {
            classes.addAll(getSemanticClasses(type, context));
        }

        return classes;
    }

    /**
     * Get semantic CSS classes based on content analysis
     */
    private Collection<String> getSemanticClasses(HighlightType type, String context) {
        List<String> classes = new ArrayList<>();

        switch (type) {
            case XML_ELEMENT_NAME:
                // Add classes based on element name
                if (context.contains(":")) {
                    classes.add("xml-namespaced-element");
                }
                if (context.matches("^[A-Z].*")) {
                    classes.add("xml-capitalized-element");
                }
                break;

            case XML_ATTRIBUTE_VALUE:
                // Add classes based on value content
                if (context.startsWith("http://") || context.startsWith("https://")) {
                    classes.add("xml-url-value");
                } else if (context.matches("\\d+")) {
                    classes.add("xml-numeric-value");
                } else if (context.matches("true|false")) {
                    classes.add("xml-boolean-value");
                }
                break;

            case XML_NAMESPACE:
                // Check if it's a known namespace
                if (knownNamespaces.contains(context)) {
                    classes.add("xml-known-namespace");
                }
                break;
        }

        return classes;
    }

    // ========== Configuration Methods ==========

    /**
     * Enable or disable semantic highlighting
     */
    public void setSemanticHighlightingEnabled(boolean enabled) {
        this.semanticHighlightingEnabled = enabled;
        logger.debug("Semantic XML highlighting {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Enable or disable error highlighting
     */
    public void setErrorHighlightingEnabled(boolean enabled) {
        this.errorHighlightingEnabled = enabled;
        logger.debug("XML error highlighting {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Enable or disable warning highlighting
     */
    public void setWarningHighlightingEnabled(boolean enabled) {
        this.warningHighlightingEnabled = enabled;
        logger.debug("XML warning highlighting {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Add known namespace for better semantic highlighting
     */
    public void addKnownNamespace(String namespace) {
        if (namespace != null && !namespace.isEmpty()) {
            knownNamespaces.add(namespace);
        }
    }

    /**
     * Add deprecated element
     */
    public void addDeprecatedElement(String element) {
        if (element != null && !element.isEmpty()) {
            deprecatedElements.add(element.toLowerCase());
        }
    }

    /**
     * Add deprecated attribute
     */
    public void addDeprecatedAttribute(String attribute) {
        if (attribute != null && !attribute.isEmpty()) {
            deprecatedAttributes.add(attribute.toLowerCase());
        }
    }

    // ========== Helper Classes ==========

    /**
         * Highlight match representation
         */
        private record HighlightMatch(int start, int end, HighlightType type, String context) {
    }

    /**
     * Highlight type enumeration
     */
    private enum HighlightType {
        XML_DECLARATION,
        XML_PROCESSING_INSTRUCTION,
        XML_COMMENT,
        XML_CDATA,
        XML_ELEMENT_TAG,
        XML_ELEMENT_NAME,
        XML_CLOSING_TAG,
        XML_SELF_CLOSING,
        XML_ATTRIBUTE_NAME,
        XML_ATTRIBUTE_VALUE,
        XML_ATTRIBUTE_EQUALS,
        XML_ATTRIBUTE_QUOTES,
        XML_NAMESPACE,
        XML_ENTITY,
        XML_ERROR,
        XML_WARNING,
        XML_DEPRECATED
    }

    // ========== Public API ==========

    /**
     * Get current theme
     */
    public ModernXmlThemeManager.XmlHighlightTheme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Generate theme-specific CSS
     */
    public String generateThemeCss() {
        return currentTheme != null ? currentTheme.generateThemeCss() : "";
    }

    /**
     * Check if semantic highlighting is enabled
     */
    public boolean isSemanticHighlightingEnabled() {
        return semanticHighlightingEnabled;
    }

    /**
     * Check if error highlighting is enabled
     */
    public boolean isErrorHighlightingEnabled() {
        return errorHighlightingEnabled;
    }

    /**
     * Get known namespaces
     */
    public Set<String> getKnownNamespaces() {
        return new HashSet<>(knownNamespaces);
    }

    /**
     * Get deprecated elements
     */
    public Set<String> getDeprecatedElements() {
        return new HashSet<>(deprecatedElements);
    }
}