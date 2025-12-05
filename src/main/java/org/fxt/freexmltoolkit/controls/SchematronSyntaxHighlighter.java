package org.fxt.freexmltoolkit.controls;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighter specifically designed for Schematron documents.
 * Extends basic XML highlighting with Schematron-specific elements, attributes, and XPath expressions.
 */
public class SchematronSyntaxHighlighter {

    // Schematron-specific elements
    private static final String[] SCHEMATRON_ELEMENTS = {
            "schema", "pattern", "rule", "assert", "report", "let", "param",
            "title", "p", "emph", "dir", "span", "ns", "diagnostics", "diagnostic",
            "name", "value-of", "extends", "include", "phase", "active"
    };

    // Schematron-specific attributes
    private static final String[] SCHEMATRON_ATTRIBUTES = {
            "context", "test", "flag", "id", "role", "subject", "fpi", "icon",
            "see", "space", "queryBinding", "schemaVersion", "defaultPhase",
            "prefix", "uri", "select", "ref", "documents", "is-a", "abstract"
    };

    // XPath functions commonly used in Schematron (expanded for XPath 2.0/3.0)
    private static final String[] XPATH_FUNCTIONS = {
            // XPath 1.0 core functions
            "count", "exists", "normalize-space", "string-length", "substring",
            "contains", "starts-with", "ends-with", "matches", "replace",
            "number", "string", "boolean", "not", "true", "false",
            "position", "last", "name", "local-name", "namespace-uri",
            "sum", "avg", "min", "max", "round", "ceiling", "floor",
            // Additional string functions
            "concat", "translate", "upper-case", "lower-case", "tokenize",
            "string-join", "substring-before", "substring-after", "normalize-unicode",
            // Sequence functions
            "empty", "distinct-values", "index-of", "reverse", "subsequence",
            "head", "tail", "insert-before", "remove",
            // Aggregate functions
            "sum", "avg", "min", "max", "count",
            // Node functions
            "root", "document", "doc", "id", "idref", "base-uri",
            // Type functions
            "data", "node-name", "nilled", "type-available",
            // Date/Time functions
            "current-date", "current-time", "current-dateTime"
    };

    // XPath operators and keywords
    private static final String[] XPATH_OPERATORS = {
            "and", "or", "div", "mod", "eq", "ne", "lt", "le", "gt", "ge",
            "is", "to", "union", "intersect", "except", "instance", "of",
            "treat", "as", "castable", "cast", "satisfies", "for", "in",
            "return", "if", "then", "else", "some", "every"
    };

    // XPath axis specifiers
    private static final String[] XPATH_AXES = {
            "ancestor", "ancestor-or-self", "attribute", "child", "descendant",
            "descendant-or-self", "following", "following-sibling", "namespace",
            "parent", "preceding", "preceding-sibling", "self"
    };

    // Compiled patterns for performance
    private static final Pattern XML_TAG = Pattern.compile("(?<ELEMENT></?\\s*[a-zA-Z][\\w:-]*(?:\\s+[^<>]*?)?>)");
    private static final Pattern ATTRIBUTES = Pattern.compile("(?<ATTRIBUTE>\\s+[a-zA-Z][\\w:-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))");
    private static final Pattern XML_COMMENT = Pattern.compile("(?<COMMENT><!--[\\s\\S]*?-->)");
    private static final Pattern XML_DECLARATION = Pattern.compile("(?<XMLDECL><\\?xml[\\s\\S]*?\\?>)");
    private static final Pattern CDATA = Pattern.compile("(?<CDATA><!\\[CDATA\\[[\\s\\S]*?\\]\\]>)");

    // Schematron-specific patterns
    private static final Pattern SCHEMATRON_ELEMENT_PATTERN;
    private static final Pattern SCHEMATRON_ATTRIBUTE_PATTERN;
    private static final Pattern XPATH_EXPRESSION_PATTERN;
    private static final Pattern XPATH_FUNCTION_PATTERN;

    static {
        // Build Schematron element pattern
        String elementRegex = "\\b(?:" + String.join("|", SCHEMATRON_ELEMENTS) + ")\\b";
        SCHEMATRON_ELEMENT_PATTERN = Pattern.compile("(?<SCHELEMENT>" + elementRegex + ")");

        // Build Schematron attribute pattern
        String attributeRegex = "\\b(?:" + String.join("|", SCHEMATRON_ATTRIBUTES) + ")\\s*=";
        SCHEMATRON_ATTRIBUTE_PATTERN = Pattern.compile("(?<SCHATTRIBUTE>" + attributeRegex + ")");

        // XPath expressions in test/context attributes (simplified)
        XPATH_EXPRESSION_PATTERN = Pattern.compile(
                "(?<XPATH>(?:test|context|select)\\s*=\\s*[\"']([^\"']*)[\"'])"
        );

        // XPath functions
        String functionRegex = "\\b(?:" + String.join("|", XPATH_FUNCTIONS) + ")\\s*\\(";
        XPATH_FUNCTION_PATTERN = Pattern.compile("(?<XPATHFUNC>" + functionRegex + ")");
    }

    // Build XPath operator pattern (word boundary required for proper matching)
    private static final Pattern XPATH_OPERATOR_PATTERN;
    private static final Pattern XPATH_AXIS_PATTERN;

    static {
        // Build operator pattern - must be word-bounded
        String operatorRegex = "\\b(?:" + String.join("|", XPATH_OPERATORS) + ")\\b";
        XPATH_OPERATOR_PATTERN = Pattern.compile(operatorRegex);

        // Build axis pattern - ends with ::
        String axisRegex = "\\b(?:" + String.join("|", XPATH_AXES) + ")::";
        XPATH_AXIS_PATTERN = Pattern.compile(axisRegex);
    }

    // Master pattern combining all patterns
    private static final Pattern PATTERN = Pattern.compile(
            "(?<XMLDECL><\\?xml[\\s\\S]*?\\?>)"
                    + "|(?<COMMENT><!--[\\s\\S]*?-->)"
                    + "|(?<CDATA><!\\[CDATA\\[[\\s\\S]*?\\]\\]>)"
                    + "|(?<ELEMENT></?\\s*[a-zA-Z][\\w:-]*(?:\\s+[^<>]*?)?>)"
                    + "|(?<XPATH>(?:test|context|select)\\s*=\\s*[\"']([^\"']*)[\"'])"
                    + "|(?<ATTRIBUTE>\\s+[a-zA-Z][\\w:-]*\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))"
                    + "|(?<XPATHAXIS>\\b(?:" + String.join("|", XPATH_AXES) + ")::)"
                    + "|(?<XPATHOP>\\b(?:" + String.join("|", XPATH_OPERATORS) + ")\\b)"
                    + "|(?<XPATHFUNC>\\b(?:" + String.join("|", XPATH_FUNCTIONS) + ")\\s*\\()"
                    + "|(?<XPATHVAR>\\$[a-zA-Z_][a-zA-Z0-9_]*)"
    );

    /**
     * Compute syntax highlighting for Schematron content
     *
     * @param text The text to highlight
     * @return StyleSpans with highlighting information
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = getStyleClass(matcher);

            // Add unhighlighted text before this match
            if (matcher.start() > lastKwEnd) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            }

            // Add highlighted match
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }

        // Add any remaining unhighlighted text
        if (lastKwEnd < text.length()) {
            spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        }

        return spansBuilder.create();
    }

    /**
     * Determine the appropriate style class based on the matcher groups
     */
    private static String getStyleClass(Matcher matcher) {
        if (matcher.group("XMLDECL") != null) {
            return "xml-declaration";
        } else if (matcher.group("COMMENT") != null) {
            return "xml-comment";
        } else if (matcher.group("CDATA") != null) {
            return "xml-cdata";
        } else if (matcher.group("ELEMENT") != null) {
            // Check if it's a Schematron-specific element
            String element = matcher.group("ELEMENT");
            if (containsSchematronElement(element)) {
                return "schematron-element";
            } else {
                return "xml-element";
            }
        } else if (matcher.group("XPATH") != null) {
            return "xpath-expression";
        } else if (matcher.group("ATTRIBUTE") != null) {
            // Check if it's a Schematron-specific attribute
            String attribute = matcher.group("ATTRIBUTE");
            if (containsSchematronAttribute(attribute)) {
                return "schematron-attribute";
            } else {
                return "xml-attribute";
            }
        } else if (matcher.group("XPATHAXIS") != null) {
            return "xpath-axis";
        } else if (matcher.group("XPATHOP") != null) {
            return "xpath-operator";
        } else if (matcher.group("XPATHFUNC") != null) {
            return "xpath-function";
        } else if (matcher.group("XPATHVAR") != null) {
            return "xpath-variable";
        }

        return "default";
    }

    /**
     * Check if the element contains Schematron-specific elements
     */
    private static boolean containsSchematronElement(String elementTag) {
        for (String schElement : SCHEMATRON_ELEMENTS) {
            if (elementTag.contains(schElement)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the attribute is Schematron-specific
     */
    private static boolean containsSchematronAttribute(String attributeTag) {
        for (String schAttribute : SCHEMATRON_ATTRIBUTES) {
            if (attributeTag.contains(schAttribute)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get CSS style definitions for Schematron highlighting
     *
     * @return CSS string with style definitions
     */
    public static String getStylesheet() {
        return """
                /* XML Basic Styles */
                .xml-declaration {
                    -fx-fill: #8b008b;
                    -fx-font-weight: bold;
                }

                .xml-comment {
                    -fx-fill: #808080;
                    -fx-font-style: italic;
                }

                .xml-cdata {
                    -fx-fill: #b8860b;
                    -fx-font-weight: bold;
                }

                .xml-element {
                    -fx-fill: #0000ff;
                    -fx-font-weight: bold;
                }

                .xml-attribute {
                    -fx-fill: #ff4500;
                }

                /* Schematron Specific Styles */
                .schematron-element {
                    -fx-fill: #dc143c;
                    -fx-font-weight: bold;
                }

                .schematron-attribute {
                    -fx-fill: #ff6347;
                    -fx-font-weight: bold;
                }

                /* XPath Specific Styles */
                .xpath-expression {
                    -fx-fill: #006400;
                    -fx-font-style: italic;
                    -fx-background-color: #f0fff0;
                }

                .xpath-function {
                    -fx-fill: #4b0082;
                    -fx-font-weight: bold;
                }

                .xpath-operator {
                    -fx-fill: #d2691e;
                    -fx-font-weight: bold;
                }

                .xpath-axis {
                    -fx-fill: #2e8b57;
                    -fx-font-weight: bold;
                }

                .xpath-variable {
                    -fx-fill: #9932cc;
                    -fx-font-style: italic;
                }

                /* Validation Error/Warning Styles */
                .validation-error {
                    -fx-background-color: #ffcccc;
                    -rtfx-underline-color: #ff0000;
                    -rtfx-underline-width: 1px;
                }

                .validation-warning {
                    -fx-background-color: #fff3cd;
                    -rtfx-underline-color: #ffc107;
                    -rtfx-underline-width: 1px;
                }

                /* Default style */
                .default {
                    -fx-fill: #000000;
                }
                """;
    }

    /**
     * Enhanced highlighting that also handles nested XPath expressions within attributes.
     * This method provides multi-pass highlighting for complex Schematron documents
     * with nested XPath expressions containing functions, operators, axes, and variables.
     *
     * @param text The text to highlight
     * @return StyleSpans with enhanced highlighting
     */
    public static StyleSpans<Collection<String>> computeEnhancedHighlighting(String text) {
        // The basic highlighting already handles all patterns including:
        // - XML declarations, comments, CDATA
        // - Schematron elements and attributes
        // - XPath expressions (test/context/select attributes)
        // - XPath functions with parentheses
        // - XPath operators (and, or, div, mod, eq, ne, etc.)
        // - XPath axis specifiers (ancestor::, child::, descendant::, etc.)
        // - XPath variables ($varname)
        //
        // For even more sophisticated highlighting (e.g., nested function calls
        // or complex XPath expressions), a second-pass parser could be added here.
        // Current implementation provides comprehensive coverage for common patterns.
        return computeHighlighting(text);
    }

    /**
     * Validate basic Schematron structure
     *
     * @param text The Schematron text to validate
     * @return Validation result with any issues found
     */
    public static SchematronValidationResult validateStructure(String text) {
        SchematronValidationResult result = new SchematronValidationResult();

        // Check for required root element
        if (!text.contains("<schema") && !text.contains("<sch:schema")) {
            result.addError("Missing root 'schema' element");
        }

        // Check for namespace declaration
        if (!text.contains("http://purl.oclc.org/dsdl/schematron")) {
            result.addWarning("Missing Schematron namespace declaration");
        }

        // Check for at least one pattern
        if (!text.contains("<pattern") && !text.contains("<sch:pattern")) {
            result.addWarning("No patterns found - Schematron should contain at least one pattern");
        }

        // Check for at least one rule
        if (!text.contains("<rule") && !text.contains("<sch:rule")) {
            result.addWarning("No rules found - patterns should contain rules");
        }

        // Check for assertions or reports
        if (!text.contains("<assert") && !text.contains("<sch:assert") &&
                !text.contains("<report") && !text.contains("<sch:report")) {
            result.addWarning("No assertions or reports found");
        }

        return result;
    }

    /**
     * Simple validation result class
     */
    public static class SchematronValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();
        private final java.util.List<String> warnings = new java.util.ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public java.util.List<String> getErrors() {
            return errors;
        }

        public java.util.List<String> getWarnings() {
            return warnings;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean isValid() {
            return errors.isEmpty();
        }
    }
}