package org.fxt.freexmltoolkit.controls;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced error detection for Schematron documents.
 * Provides comprehensive validation including XML syntax, Schematron structure,
 * XPath expressions, and common semantic errors.
 */
public class SchematronErrorDetector {

    private static final Logger logger = LogManager.getLogger(SchematronErrorDetector.class);

    // Required Schematron elements and their relationships
    private static final String[] REQUIRED_ELEMENTS = {"schema"};
    private static final String[] PATTERN_CHILDREN = {"title", "rule", "let", "param", "p"};
    private static final String[] RULE_CHILDREN = {"assert", "report", "extends", "let"};

    // XPath validator for expression validation
    private final XPathValidator xpathValidator;

    /**
     * Constructs a new SchematronErrorDetector with default settings.
     * Initializes the internal XPath validator for expression validation.
     */
    public SchematronErrorDetector() {
        this.xpathValidator = new XPathValidator();
        logger.debug("SchematronErrorDetector initialized");
    }

    /**
     * Perform comprehensive error detection on Schematron text
     *
     * @param schematronText The Schematron document text
     * @return Detailed error detection result
     */
    public SchematronErrorResult detectErrors(String schematronText) {
        SchematronErrorResult result = new SchematronErrorResult();

        if (schematronText == null || schematronText.trim().isEmpty()) {
            result.addError(new SchematronError(ErrorType.STRUCTURAL, 0, 0,
                    "Document is empty", ErrorSeverity.ERROR));
            return result;
        }

        // 1. XML Syntax Validation
        detectXmlSyntaxErrors(schematronText, result);

        // 2. Schematron Structure Validation
        detectStructuralErrors(schematronText, result);

        // 3. XPath Expression Validation
        detectXPathErrors(schematronText, result);

        // 4. Semantic Validation
        detectSemanticErrors(schematronText, result);

        // 5. Best Practice Checks
        detectBestPracticeIssues(schematronText, result);

        logger.debug("Error detection completed: {} errors, {} warnings",
                result.getErrors().size(), result.getWarnings().size());

        return result;
    }

    /**
     * Detects XML syntax errors in the Schematron document.
     * Uses a DOM parser with a custom error handler to collect all XML parsing errors.
     *
     * @param schematronText the Schematron document text to validate
     * @param result the result object to collect detected errors
     */
    private void detectXmlSyntaxErrors(String schematronText, SchematronErrorResult result) {
        try {
            DocumentBuilderFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Custom error handler to collect errors
            List<SAXParseException> xmlErrors = new ArrayList<>();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    xmlErrors.add(exception);
                }

                @Override
                public void error(SAXParseException exception) {
                    xmlErrors.add(exception);
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    xmlErrors.add(exception);
                }
            });

            // Parse the document
            builder.parse(new ByteArrayInputStream(schematronText.getBytes()));

        } catch (Exception e) {
            // Parse failed - extract line/column information if possible
            int line = 1;
            int column = 1;

            if (e instanceof SAXParseException saxe) {
                line = saxe.getLineNumber();
                column = saxe.getColumnNumber();
            }

            result.addError(new SchematronError(ErrorType.XML_SYNTAX, line, column,
                    "XML syntax error: " + e.getMessage(), ErrorSeverity.ERROR));
        }
    }

    /**
     * Detects Schematron structural errors in the document.
     * Validates root element, namespace, patterns, and rules structure.
     *
     * @param schematronText the Schematron document text to validate
     * @param result the result object to collect detected errors
     */
    private void detectStructuralErrors(String schematronText, SchematronErrorResult result) {
        try {
            DocumentBuilderFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(schematronText.getBytes()));

            Element root = document.getDocumentElement();

            // Check root element
            if (!"schema".equals(root.getLocalName())) {
                result.addError(new SchematronError(ErrorType.STRUCTURAL, 1, 1,
                        "Root element must be 'schema', found: " + root.getLocalName(),
                        ErrorSeverity.ERROR));
            }

            // Check namespace
            String namespace = root.getNamespaceURI();
            if (!"http://purl.oclc.org/dsdl/schematron".equals(namespace)) {
                result.addWarning(new SchematronError(ErrorType.STRUCTURAL, 1, 1,
                        "Missing or incorrect Schematron namespace", ErrorSeverity.WARNING));
            }

            // Check for patterns
            NodeList patterns = root.getElementsByTagNameNS("*", "pattern");
            if (patterns.getLength() == 0) {
                result.addWarning(new SchematronError(ErrorType.STRUCTURAL, 1, 1,
                        "No patterns found - Schematron should contain at least one pattern",
                        ErrorSeverity.WARNING));
            }

            // Check pattern structure
            for (int i = 0; i < patterns.getLength(); i++) {
                Element pattern = (Element) patterns.item(i);
                validatePatternStructure(pattern, result);
            }

            // Check for rules
            NodeList rules = root.getElementsByTagNameNS("*", "rule");
            if (rules.getLength() == 0) {
                result.addWarning(new SchematronError(ErrorType.STRUCTURAL, 1, 1,
                        "No rules found - patterns should contain rules",
                        ErrorSeverity.WARNING));
            }

            // Check rule structure
            for (int i = 0; i < rules.getLength(); i++) {
                Element rule = (Element) rules.item(i);
                validateRuleStructure(rule, result);
            }

        } catch (Exception e) {
            logger.debug("Structural validation failed", e);
            // XML syntax errors would have been caught earlier
        }
    }

    /**
     * Validates the structure of a pattern element.
     * Checks for required attributes on abstract patterns and verifies rule presence.
     *
     * @param pattern the pattern element to validate
     * @param result the result object to collect detected errors
     */
    private void validatePatternStructure(Element pattern, SchematronErrorResult result) {
        // Check for required context attribute in abstract patterns
        if ("true".equals(pattern.getAttribute("abstract"))) {
            if (pattern.getAttribute("id").isEmpty()) {
                result.addError(new SchematronError(ErrorType.STRUCTURAL,
                        getLineNumber(pattern), 1,
                        "Abstract pattern must have an 'id' attribute",
                        ErrorSeverity.ERROR));
            }
        }

        // Check for rules within pattern
        NodeList rules = pattern.getElementsByTagNameNS("*", "rule");
        if (rules.getLength() == 0 && !"true".equals(pattern.getAttribute("abstract"))) {
            result.addWarning(new SchematronError(ErrorType.STRUCTURAL,
                    getLineNumber(pattern), 1,
                    "Pattern contains no rules",
                    ErrorSeverity.WARNING));
        }
    }

    /**
     * Validates the structure of a rule element.
     * Checks for required context attribute and presence of assertions or reports.
     *
     * @param rule the rule element to validate
     * @param result the result object to collect detected errors
     */
    private void validateRuleStructure(Element rule, SchematronErrorResult result) {
        // Check for required context attribute
        String context = rule.getAttribute("context");
        if (context.isEmpty()) {
            result.addError(new SchematronError(ErrorType.STRUCTURAL,
                    getLineNumber(rule), 1,
                    "Rule must have a 'context' attribute",
                    ErrorSeverity.ERROR));
        }

        // Check for assertions or reports
        NodeList assertions = rule.getElementsByTagNameNS("*", "assert");
        NodeList reports = rule.getElementsByTagNameNS("*", "report");

        if (assertions.getLength() == 0 && reports.getLength() == 0) {
            result.addWarning(new SchematronError(ErrorType.STRUCTURAL,
                    getLineNumber(rule), 1,
                    "Rule contains no assertions or reports",
                    ErrorSeverity.WARNING));
        }

        // Validate assert and report elements
        for (int i = 0; i < assertions.getLength(); i++) {
            validateAssertionElement((Element) assertions.item(i), result);
        }

        for (int i = 0; i < reports.getLength(); i++) {
            validateReportElement((Element) reports.item(i), result);
        }
    }

    /**
     * Validates an assert element.
     * Checks for required test attribute and presence of a meaningful message.
     *
     * @param assertElement the assert element to validate
     * @param result the result object to collect detected errors
     */
    private void validateAssertionElement(Element assertElement, SchematronErrorResult result) {
        String test = assertElement.getAttribute("test");
        if (test.isEmpty()) {
            result.addError(new SchematronError(ErrorType.STRUCTURAL,
                    getLineNumber(assertElement), 1,
                    "Assert element must have a 'test' attribute",
                    ErrorSeverity.ERROR));
        }

        // Check for meaningful message
        String message = assertElement.getTextContent().trim();
        if (message.isEmpty()) {
            result.addWarning(new SchematronError(ErrorType.STRUCTURAL,
                    getLineNumber(assertElement), 1,
                    "Assert element should contain a meaningful message",
                    ErrorSeverity.WARNING));
        }
    }

    /**
     * Validates a report element.
     * Checks for required test attribute and presence of a meaningful message.
     *
     * @param reportElement the report element to validate
     * @param result the result object to collect detected errors
     */
    private void validateReportElement(Element reportElement, SchematronErrorResult result) {
        String test = reportElement.getAttribute("test");
        if (test.isEmpty()) {
            result.addError(new SchematronError(ErrorType.STRUCTURAL,
                    getLineNumber(reportElement), 1,
                    "Report element must have a 'test' attribute",
                    ErrorSeverity.ERROR));
        }

        // Check for meaningful message
        String message = reportElement.getTextContent().trim();
        if (message.isEmpty()) {
            result.addWarning(new SchematronError(ErrorType.STRUCTURAL,
                    getLineNumber(reportElement), 1,
                    "Report element should contain a meaningful message",
                    ErrorSeverity.WARNING));
        }
    }

    /**
     * Detects XPath expression errors in the Schematron document.
     * Extracts and validates all XPath expressions found in test, context, and select attributes.
     *
     * @param schematronText the Schematron document text to validate
     * @param result the result object to collect detected errors
     */
    private void detectXPathErrors(String schematronText, SchematronErrorResult result) {
        // Extract namespace declarations
        List<XPathValidator.NamespaceDeclaration> namespaces =
                xpathValidator.parseNamespaceDeclarations(schematronText);

        // Extract XPath expressions
        List<XPathValidator.XPathLocation> xpathLocations =
                xpathValidator.extractXPathExpressions(schematronText);

        for (XPathValidator.XPathLocation location : xpathLocations) {
            XPathValidator.XPathValidationResult validation =
                    xpathValidator.validateXPath(location.expression(), namespaces);

            if (!validation.isValid()) {
                int line = calculateLineNumber(schematronText, location.start());
                int column = calculateColumnNumber(schematronText, location.start());

                for (String error : validation.getErrors()) {
                    result.addError(new SchematronError(ErrorType.XPATH, line, column,
                            "XPath error in " + location.attributeName() + ": " + error,
                            ErrorSeverity.ERROR));
                }
            }

            for (String warning : validation.getWarnings()) {
                int line = calculateLineNumber(schematronText, location.start());
                int column = calculateColumnNumber(schematronText, location.start());

                result.addWarning(new SchematronError(ErrorType.XPATH, line, column,
                        "XPath warning in " + location.attributeName() + ": " + warning,
                        ErrorSeverity.WARNING));
            }
        }
    }

    /**
     * Detects semantic errors in the Schematron document.
     * Checks for duplicate IDs, undefined references, and circular dependencies.
     *
     * @param schematronText the Schematron document text to validate
     * @param result the result object to collect detected errors
     */
    private void detectSemanticErrors(String schematronText, SchematronErrorResult result) {
        // Check for duplicate IDs
        detectDuplicateIds(schematronText, result);

        // Check for undefined references
        detectUndefinedReferences(schematronText, result);

        // Check for circular dependencies
        detectCircularDependencies(schematronText, result);
    }

    /**
     * Detects duplicate ID attributes in the Schematron document.
     * ID attributes must be unique within the document.
     *
     * @param schematronText the Schematron document text to validate
     * @param result the result object to collect detected errors
     */
    private void detectDuplicateIds(String schematronText, SchematronErrorResult result) {
        Pattern idPattern = Pattern.compile("\\bid\\s*=\\s*[\"']([^\"']+)[\"']");
        Matcher matcher = idPattern.matcher(schematronText);

        List<String> seenIds = new ArrayList<>();

        while (matcher.find()) {
            String id = matcher.group(1);
            if (seenIds.contains(id)) {
                int line = calculateLineNumber(schematronText, matcher.start());
                result.addError(new SchematronError(ErrorType.SEMANTIC, line, 1,
                        "Duplicate ID: " + id, ErrorSeverity.ERROR));
            } else {
                seenIds.add(id);
            }
        }
    }

    /**
     * Detects undefined references in the Schematron document.
     * Checks that all rule references in extends elements point to existing rules.
     *
     * @param schematronText the Schematron document text to validate
     * @param result the result object to collect detected errors
     */
    private void detectUndefinedReferences(String schematronText, SchematronErrorResult result) {
        // Extract all defined IDs
        List<String> definedIds = extractDefinedIds(schematronText);

        // Check extends references
        Pattern extendsPattern = Pattern.compile("<extends\\s+rule\\s*=\\s*[\"']([^\"']+)[\"']");
        Matcher matcher = extendsPattern.matcher(schematronText);

        while (matcher.find()) {
            String referencedId = matcher.group(1);
            if (!definedIds.contains(referencedId)) {
                int line = calculateLineNumber(schematronText, matcher.start());
                result.addError(new SchematronError(ErrorType.SEMANTIC, line, 1,
                        "Undefined rule reference: " + referencedId, ErrorSeverity.ERROR));
            }
        }
    }

    /**
     * Detects circular dependencies in rule extensions.
     * Checks for rules that directly or indirectly extend themselves.
     *
     * @param schematronText the Schematron document text to validate
     * @param result the result object to collect detected errors
     */
    private void detectCircularDependencies(String schematronText, SchematronErrorResult result) {
        // This is a simplified check - a full implementation would use graph algorithms
        Pattern extendsPattern = Pattern.compile(
                "<rule[^>]+id\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>.*?<extends\\s+rule\\s*=\\s*[\"']([^\"']+)[\"']"
        );

        Matcher matcher = extendsPattern.matcher(schematronText);

        while (matcher.find()) {
            String ruleId = matcher.group(1);
            String extendsId = matcher.group(2);

            if (ruleId.equals(extendsId)) {
                int line = calculateLineNumber(schematronText, matcher.start());
                result.addError(new SchematronError(ErrorType.SEMANTIC, line, 1,
                        "Rule cannot extend itself: " + ruleId, ErrorSeverity.ERROR));
            }
        }
    }

    /**
     * Detects best practice issues in the Schematron document.
     * Checks for missing documentation, titles, and suggests improvements.
     *
     * @param schematronText the Schematron document text to validate
     * @param result the result object to collect detected issues
     */
    private void detectBestPracticeIssues(String schematronText, SchematronErrorResult result) {
        // Check for missing titles
        if (!schematronText.contains("<title>")) {
            result.addWarning(new SchematronError(ErrorType.BEST_PRACTICE, 1, 1,
                    "Consider adding a title element to document the schema purpose",
                    ErrorSeverity.INFO));
        }

        // Check for missing documentation
        if (!schematronText.contains("<p>") && !schematronText.contains("<!--")) {
            result.addWarning(new SchematronError(ErrorType.BEST_PRACTICE, 1, 1,
                    "Consider adding documentation (p elements or comments)",
                    ErrorSeverity.INFO));
        }

        // Check for hardcoded values in XPath
        Pattern hardcodedPattern = Pattern.compile("test\\s*=\\s*[\"'][^\"']*[\"']\\s*[^\"']*\\b\\d+\\b");
        Matcher matcher = hardcodedPattern.matcher(schematronText);

        while (matcher.find()) {
            int line = calculateLineNumber(schematronText, matcher.start());
            result.addWarning(new SchematronError(ErrorType.BEST_PRACTICE, line, 1,
                    "Consider using variables instead of hardcoded values",
                    ErrorSeverity.INFO));
        }
    }

    // ========== Helper Methods ==========

    /**
     * Extracts all defined ID attributes from the Schematron text.
     * Searches for all id attributes and returns their values.
     *
     * @param schematronText the Schematron document text to search
     * @return a list of all ID values found in the document
     */
    private List<String> extractDefinedIds(String schematronText) {
        List<String> ids = new ArrayList<>();
        Pattern idPattern = Pattern.compile("\\bid\\s*=\\s*[\"']([^\"']+)[\"']");
        Matcher matcher = idPattern.matcher(schematronText);

        while (matcher.find()) {
            ids.add(matcher.group(1));
        }

        return ids;
    }

    /**
     * Calculates the line number for a character position in the text.
     * Counts newline characters up to the given position.
     *
     * @param text the text to search
     * @param position the character position (0-based)
     * @return the line number (1-based)
     */
    private int calculateLineNumber(String text, int position) {
        int line = 1;
        for (int i = 0; i < position && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Calculates the column number for a character position in the text.
     * Counts characters from the last newline to the given position.
     *
     * @param text the text to search
     * @param position the character position (0-based)
     * @return the column number (1-based)
     */
    private int calculateColumnNumber(String text, int position) {
        int column = 1;
        for (int i = position - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                break;
            }
            column++;
        }
        return column;
    }

    /**
     * Gets the line number for a DOM element.
     * This is a simplified implementation that returns a default value
     * since standard DOM does not preserve line number information.
     *
     * @param element the DOM element
     * @return the line number (approximation, defaults to 1)
     */
    private int getLineNumber(Element element) {
        // This is a simplified implementation
        // In a real implementation, you might use a different approach
        // to track line numbers during parsing
        return 1;
    }

    /**
     * Cleans up resources used by this detector.
     * Disposes the internal XPath validator.
     */
    public void dispose() {
        if (xpathValidator != null) {
            xpathValidator.dispose();
        }
    }

    // ========== Inner Classes ==========

    /**
     * Represents the result of error detection.
     * Contains categorized lists of errors, warnings, and informational issues.
     */
    public static class SchematronErrorResult {
        private final List<SchematronError> errors = new ArrayList<>();
        private final List<SchematronError> warnings = new ArrayList<>();
        private final List<SchematronError> infos = new ArrayList<>();

        /**
         * Constructs a new empty SchematronErrorResult.
         * Initializes empty lists for errors, warnings, and informational issues.
         */
        public SchematronErrorResult() {
            // Default constructor
        }

        /**
         * Adds an error to the appropriate category based on its severity.
         *
         * @param error the error to add
         */
        public void addError(SchematronError error) {
            switch (error.severity()) {
                case ERROR -> errors.add(error);
                case WARNING -> warnings.add(error);
                case INFO -> infos.add(error);
            }
        }

        /**
         * Adds a warning to the warnings list.
         *
         * @param error the warning to add
         */
        public void addWarning(SchematronError error) {
            warnings.add(error);
        }

        /**
         * Adds an informational issue to the infos list.
         *
         * @param error the info issue to add
         */
        public void addInfo(SchematronError error) {
            infos.add(error);
        }

        /**
         * Returns a copy of the errors list.
         *
         * @return a new list containing all errors
         */
        public List<SchematronError> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * Returns a copy of the warnings list.
         *
         * @return a new list containing all warnings
         */
        public List<SchematronError> getWarnings() {
            return new ArrayList<>(warnings);
        }

        /**
         * Returns a copy of the infos list.
         *
         * @return a new list containing all informational issues
         */
        public List<SchematronError> getInfos() {
            return new ArrayList<>(infos);
        }

        /**
         * Returns all issues combined (errors, warnings, and infos).
         *
         * @return a new list containing all issues
         */
        public List<SchematronError> getAllIssues() {
            List<SchematronError> all = new ArrayList<>();
            all.addAll(errors);
            all.addAll(warnings);
            all.addAll(infos);
            return all;
        }

        /**
         * Checks if there are any errors.
         *
         * @return true if there is at least one error
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * Checks if there are any warnings.
         *
         * @return true if there is at least one warning
         */
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        /**
         * Checks if there are any informational issues.
         *
         * @return true if there is at least one info issue
         */
        public boolean hasInfos() {
            return !infos.isEmpty();
        }

        /**
         * Checks if there are any issues of any severity.
         *
         * @return true if there are errors, warnings, or infos
         */
        public boolean hasAnyIssues() {
            return hasErrors() || hasWarnings() || hasInfos();
        }
    }

    /**
     * Represents a single error, warning, or informational issue found during validation.
     *
     * @param type the type of error (XML_SYNTAX, STRUCTURAL, XPATH, SEMANTIC, or BEST_PRACTICE)
     * @param line the line number where the error was detected (1-based)
     * @param column the column number where the error was detected (1-based)
     * @param message the human-readable error message
     * @param severity the severity level (ERROR, WARNING, or INFO)
     */
    public record SchematronError(ErrorType type, int line, int column, String message, ErrorSeverity severity) {

        /**
         * Returns a formatted string representation of this error.
         * Format: "SEVERITY [line:column] TYPE: message"
         *
         * @return the formatted error string
         */
        @Override
        public String toString() {
            return String.format("%s [%d:%d] %s: %s",
                    severity, line, column, type, message);
        }
    }

    /**
     * Types of errors that can be detected in a Schematron document.
     */
    public enum ErrorType {
        /** XML syntax errors such as malformed XML or unclosed tags. */
        XML_SYNTAX,
        /** Structural errors in Schematron elements such as missing required attributes. */
        STRUCTURAL,
        /** Errors in XPath expressions such as invalid syntax or undefined functions. */
        XPATH,
        /** Semantic errors such as duplicate IDs or undefined references. */
        SEMANTIC,
        /** Best practice issues such as missing documentation or hardcoded values. */
        BEST_PRACTICE
    }

    /**
     * Error severity levels indicating the importance of a detected issue.
     */
    public enum ErrorSeverity {
        /** Critical errors that prevent the Schematron from functioning correctly. */
        ERROR,
        /** Potential issues that may cause problems or indicate mistakes. */
        WARNING,
        /** Informational suggestions for improvement. */
        INFO
    }
}