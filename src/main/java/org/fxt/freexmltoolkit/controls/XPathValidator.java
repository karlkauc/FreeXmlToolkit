package org.fxt.freexmltoolkit.controls;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XPath expression validator and enhanced highlighter for Schematron documents.
 * Provides validation and detailed highlighting of XPath expressions used in Schematron rules.
 */
public class XPathValidator {

    private static final Logger logger = LogManager.getLogger(XPathValidator.class);

    // XPath syntax patterns for detailed highlighting
    private static final Pattern XPATH_AXIS_PATTERN = Pattern.compile(
            "\\b(ancestor|ancestor-or-self|attribute|child|descendant|descendant-or-self|" +
                    "following|following-sibling|namespace|parent|preceding|preceding-sibling|self)::"
    );

    private static final Pattern XPATH_OPERATOR_PATTERN = Pattern.compile(
            "\\b(and|or|not|mod|div)\\b|[+\\-*=<>!]=?|\\||//"
    );

    private static final Pattern XPATH_NODE_TEST_PATTERN = Pattern.compile(
            "\\b(node|text|comment|processing-instruction)\\s*\\(\\s*\\)"
    );

    private static final Pattern XPATH_NUMERIC_PATTERN = Pattern.compile(
            "\\b\\d+(\\.\\d+)?\\b"
    );

    private static final Pattern XPATH_STRING_PATTERN = Pattern.compile(
            "'([^']*)'|\"([^\"]*)\""
    );

    private static final Pattern XPATH_VARIABLE_PATTERN = Pattern.compile(
            "\\$[a-zA-Z_][a-zA-Z0-9_\\-]*"
    );

    private static final Pattern XPATH_FUNCTION_CALL_PATTERN = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_\\-]*):?([a-zA-Z_][a-zA-Z0-9_\\-]*)\\s*\\("
    );

    // Saxon processor for XPath validation
    private final Processor processor;
    private final XPathCompiler xpathCompiler;

    /**
     * Constructor - initializes Saxon processor for XPath validation
     */
    public XPathValidator() {
        this.processor = new Processor(false);
        this.xpathCompiler = processor.newXPathCompiler();

        // Set up common namespace prefixes for Schematron
        try {
            xpathCompiler.declareNamespace("xsl", "http://www.w3.org/1999/XSL/Transform");
            xpathCompiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");
        } catch (Exception e) {
            logger.warn("Failed to declare default namespaces", e);
        }

        logger.debug("XPathValidator initialized with Saxon processor");
    }

    /**
     * Validate an XPath expression
     *
     * @param xpathExpression The XPath expression to validate
     * @param namespaces      Additional namespace prefixes to declare
     * @return Validation result with any issues found
     */
    public XPathValidationResult validateXPath(String xpathExpression, List<NamespaceDeclaration> namespaces) {
        XPathValidationResult result = new XPathValidationResult(xpathExpression);

        if (xpathExpression == null || xpathExpression.trim().isEmpty()) {
            result.addError("XPath expression is empty");
            return result;
        }

        try {
            // Create a new compiler instance to avoid thread issues
            XPathCompiler compiler = processor.newXPathCompiler();

            // Declare standard namespaces
            compiler.declareNamespace("xsl", "http://www.w3.org/1999/XSL/Transform");
            compiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");

            // Declare custom namespaces
            if (namespaces != null) {
                for (NamespaceDeclaration ns : namespaces) {
                    compiler.declareNamespace(ns.prefix(), ns.uri());
                }
            }

            // Compile the XPath expression
            XPathExecutable executable = compiler.compile(xpathExpression);

            result.setValid(true);
            logger.debug("XPath expression validated successfully: {}", xpathExpression);

        } catch (SaxonApiException e) {
            result.setValid(false);
            result.addError("XPath compilation error: " + e.getMessage());
            logger.debug("XPath validation failed: {}", e.getMessage());
        } catch (Exception e) {
            result.setValid(false);
            result.addError("Unexpected error during XPath validation: " + e.getMessage());
            logger.error("Unexpected XPath validation error", e);
        }

        return result;
    }

    /**
     * Validate XPath expression with simple namespace context
     *
     * @param xpathExpression The XPath expression to validate
     * @return Validation result
     */
    public XPathValidationResult validateXPath(String xpathExpression) {
        return validateXPath(xpathExpression, null);
    }

    /**
     * Highlight XPath syntax within an expression
     *
     * @param xpathExpression The XPath expression to highlight
     * @return List of highlighting spans
     */
    public List<XPathHighlightSpan> highlightXPath(String xpathExpression) {
        List<XPathHighlightSpan> spans = new ArrayList<>();

        if (xpathExpression == null || xpathExpression.trim().isEmpty()) {
            return spans;
        }

        // Track processed characters to avoid overlapping spans
        boolean[] processed = new boolean[xpathExpression.length()];

        // Highlight string literals first (highest priority)
        highlightPattern(xpathExpression, XPATH_STRING_PATTERN, "xpath-string", spans, processed);

        // Highlight numeric literals
        highlightPattern(xpathExpression, XPATH_NUMERIC_PATTERN, "xpath-number", spans, processed);

        // Highlight variables
        highlightPattern(xpathExpression, XPATH_VARIABLE_PATTERN, "xpath-variable", spans, processed);

        // Highlight function calls
        highlightFunctionCalls(xpathExpression, spans, processed);

        // Highlight axes
        highlightPattern(xpathExpression, XPATH_AXIS_PATTERN, "xpath-axis", spans, processed);

        // Highlight node tests
        highlightPattern(xpathExpression, XPATH_NODE_TEST_PATTERN, "xpath-node-test", spans, processed);

        // Highlight operators
        highlightPattern(xpathExpression, XPATH_OPERATOR_PATTERN, "xpath-operator", spans, processed);

        return spans;
    }

    /**
     * Highlight a pattern within the XPath expression
     */
    private void highlightPattern(String text, Pattern pattern, String styleClass,
                                  List<XPathHighlightSpan> spans, boolean[] processed) {
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // Check if this range overlaps with already processed text
            boolean hasOverlap = false;
            for (int i = start; i < end && i < processed.length; i++) {
                if (processed[i]) {
                    hasOverlap = true;
                    break;
                }
            }

            if (!hasOverlap) {
                spans.add(new XPathHighlightSpan(start, end, styleClass, matcher.group()));

                // Mark as processed
                for (int i = start; i < end && i < processed.length; i++) {
                    processed[i] = true;
                }
            }
        }
    }

    /**
     * Highlight function calls with special handling for namespaced functions
     */
    private void highlightFunctionCalls(String text, List<XPathHighlightSpan> spans, boolean[] processed) {
        Matcher matcher = XPATH_FUNCTION_CALL_PATTERN.matcher(text);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end() - 1; // Exclude the opening parenthesis

            // Check for overlaps
            boolean hasOverlap = false;
            for (int i = start; i < end && i < processed.length; i++) {
                if (processed[i]) {
                    hasOverlap = true;
                    break;
                }
            }

            if (!hasOverlap) {
                String functionName = matcher.group(1);
                if (matcher.group(2) != null) {
                    functionName += ":" + matcher.group(2);
                }

                spans.add(new XPathHighlightSpan(start, end, "xpath-function", functionName));

                // Mark as processed
                for (int i = start; i < end && i < processed.length; i++) {
                    processed[i] = true;
                }
            }
        }
    }

    /**
     * Extract XPath expressions from Schematron attributes
     *
     * @param schematronText The Schematron document text
     * @return List of XPath expressions with their locations
     */
    public List<XPathLocation> extractXPathExpressions(String schematronText) {
        List<XPathLocation> xpathExpressions = new ArrayList<>();

        // Pattern to match test, context, and select attributes
        Pattern xpathAttributePattern = Pattern.compile(
                "(test|context|select)\\s*=\\s*([\"'])([^\"']*?)\\2"
        );

        Matcher matcher = xpathAttributePattern.matcher(schematronText);

        while (matcher.find()) {
            String attributeName = matcher.group(1);
            String xpathExpression = matcher.group(3);
            int start = matcher.start(3);
            int end = matcher.end(3);

            xpathExpressions.add(new XPathLocation(xpathExpression, attributeName, start, end));
        }

        return xpathExpressions;
    }

    /**
     * Parse namespace declarations from Schematron text
     *
     * @param schematronText The Schematron document text
     * @return List of namespace declarations
     */
    public List<NamespaceDeclaration> parseNamespaceDeclarations(String schematronText) {
        List<NamespaceDeclaration> namespaces = new ArrayList<>();

        // Pattern to match <ns> elements
        Pattern nsPattern = Pattern.compile(
                "<ns\\s+prefix\\s*=\\s*([\"'])([^\"']*?)\\1\\s+uri\\s*=\\s*([\"'])([^\"']*?)\\3[^>]*/?>"
        );

        Matcher matcher = nsPattern.matcher(schematronText);

        while (matcher.find()) {
            String prefix = matcher.group(2);
            String uri = matcher.group(4);
            namespaces.add(new NamespaceDeclaration(prefix, uri));
        }

        // Also check for xmlns declarations in schema element
        Pattern xmlnsPattern = Pattern.compile(
                "xmlns:([^=\\s]+)\\s*=\\s*([\"'])([^\"']*?)\\2"
        );

        matcher = xmlnsPattern.matcher(schematronText);

        while (matcher.find()) {
            String prefix = matcher.group(1);
            String uri = matcher.group(3);
            namespaces.add(new NamespaceDeclaration(prefix, uri));
        }

        return namespaces;
    }

    // ========== Inner Classes ==========

    /**
     * Represents an XPath validation result
     */
    public static class XPathValidationResult {
        private final String expression;
        private boolean valid = false;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public XPathValidationResult(String expression) {
            this.expression = expression;
        }

        public String getExpression() {
            return expression;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Represents a highlighted span within an XPath expression
     * @param start The start index
     * @param end The end index
     * @param styleClass The style class
     * @param text The text content
     */
    public record XPathHighlightSpan(int start, int end, String styleClass, String text) {
    }

    /**
     * Represents the location of an XPath expression in Schematron text
     * @param expression The XPath expression
     * @param attributeName The attribute name containing the expression
     * @param start The start index
     * @param end The end index
     */
    public record XPathLocation(String expression, String attributeName, int start, int end) {
    }

    /**
     * Represents a namespace declaration
     * @param prefix The namespace prefix
     * @param uri The namespace URI
     */
    public record NamespaceDeclaration(String prefix, String uri) {

        @Override
            public String toString() {
                return prefix + " -> " + uri;
            }
        }

    /**
     * Clean up resources
     */
    public void dispose() {
        // Saxon processor doesn't require explicit cleanup
        // but we could clear any cached data here if needed
        logger.debug("XPathValidator disposed");
    }
}