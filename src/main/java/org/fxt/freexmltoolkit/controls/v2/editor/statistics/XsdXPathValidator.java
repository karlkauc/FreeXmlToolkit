package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.w3c.dom.Document;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.*;

/**
 * Validates XPath expressions in XSD schemas.
 * Provides three levels of validation:
 * 1. Syntax Check - validates XPath syntax using Saxon
 * 2. Context Check - validates that referenced elements exist in schema
 * 3. Sample XML Test - tests XPath against sample XML if available
 *
 * @since 2.0
 */
public class XsdXPathValidator {

    private static final Logger logger = LogManager.getLogger(XsdXPathValidator.class);

    private final XsdSchema schema;
    private final Processor processor;
    private Document sampleXml;

    /**
     * XPath source type.
     */
    public enum XPathSource {
        KEY_SELECTOR,
        KEY_FIELD,
        KEYREF_SELECTOR,
        KEYREF_FIELD,
        UNIQUE_SELECTOR,
        UNIQUE_FIELD,
        ASSERT_TEST
    }

    /**
     * Validation severity.
     */
    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    /**
     * Result of XPath validation.
     */
    public record XPathValidationIssue(
            Severity severity,
            String xpath,
            String constraintName,
            XPathSource source,
            String message,
            int matchCount,  // Number of matches in sample XML (-1 if not tested)
            XsdNode sourceNode
    ) {
        /**
         * Creates an error issue.
         */
        public static XPathValidationIssue error(String xpath, String constraintName,
                                                 XPathSource source, String message, XsdNode sourceNode) {
            return new XPathValidationIssue(Severity.ERROR, xpath, constraintName, source, message, -1, sourceNode);
        }

        /**
         * Creates a warning issue.
         */
        public static XPathValidationIssue warning(String xpath, String constraintName,
                                                   XPathSource source, String message, XsdNode sourceNode) {
            return new XPathValidationIssue(Severity.WARNING, xpath, constraintName, source, message, -1, sourceNode);
        }

        /**
         * Creates an info issue with match count.
         */
        public static XPathValidationIssue info(String xpath, String constraintName,
                                                XPathSource source, String message, int matchCount, XsdNode sourceNode) {
            return new XPathValidationIssue(Severity.INFO, xpath, constraintName, source, message, matchCount, sourceNode);
        }

        /**
         * Gets a display-friendly source description.
         */
        public String getSourceDescription() {
            return switch (source) {
                case KEY_SELECTOR -> "Key Selector";
                case KEY_FIELD -> "Key Field";
                case KEYREF_SELECTOR -> "KeyRef Selector";
                case KEYREF_FIELD -> "KeyRef Field";
                case UNIQUE_SELECTOR -> "Unique Selector";
                case UNIQUE_FIELD -> "Unique Field";
                case ASSERT_TEST -> "Assert Test";
            };
        }
    }

    /**
     * Overall validation result.
     */
    public record ValidationResult(
            List<XPathValidationIssue> issues,
            int totalXPaths,
            int validCount,
            int errorCount,
            int warningCount,
            int infoCount
    ) {
        /**
         * Returns only errors.
         */
        public List<XPathValidationIssue> getErrors() {
            return issues.stream()
                    .filter(i -> i.severity() == Severity.ERROR)
                    .toList();
        }

        /**
         * Returns only warnings.
         */
        public List<XPathValidationIssue> getWarnings() {
            return issues.stream()
                    .filter(i -> i.severity() == Severity.WARNING)
                    .toList();
        }

        /**
         * Returns true if all XPaths are valid (no errors).
         */
        public boolean isAllValid() {
            return errorCount == 0;
        }
    }

    /**
     * Creates a new XPath validator for the given schema.
     *
     * @param schema the XSD schema to validate
     */
    public XsdXPathValidator(XsdSchema schema) {
        Objects.requireNonNull(schema, "Schema cannot be null");
        this.schema = schema;
        this.processor = new Processor(false);
    }

    /**
     * Sets sample XML for testing XPath expressions.
     *
     * @param sampleXml the sample XML document
     */
    public void setSampleXml(Document sampleXml) {
        this.sampleXml = sampleXml;
    }

    /**
     * Validates all XPath expressions in the schema.
     *
     * @return the validation result
     */
    public ValidationResult validateAll() {
        logger.info("Starting XPath validation for schema");
        long startTime = System.currentTimeMillis();

        List<XPathValidationIssue> issues = new ArrayList<>();
        Set<String> visitedIds = new HashSet<>();

        // Collect all element names for context validation
        Set<String> elementNames = collectElementNames();

        // Traverse and validate
        traverseAndValidate(schema, issues, elementNames, visitedIds);

        // Count by severity
        int errorCount = 0;
        int warningCount = 0;
        int infoCount = 0;

        for (XPathValidationIssue issue : issues) {
            switch (issue.severity()) {
                case ERROR -> errorCount++;
                case WARNING -> warningCount++;
                case INFO -> infoCount++;
            }
        }

        // Count total XPaths (unique)
        Set<String> uniqueXPaths = new HashSet<>();
        for (XPathValidationIssue issue : issues) {
            uniqueXPaths.add(issue.xpath());
        }
        int totalXPaths = uniqueXPaths.size();
        int validCount = totalXPaths - (int) issues.stream()
                .filter(i -> i.severity() == Severity.ERROR)
                .map(XPathValidationIssue::xpath)
                .distinct()
                .count();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("XPath validation completed in {}ms: {} total, {} valid, {} errors, {} warnings",
                duration, totalXPaths, validCount, errorCount, warningCount);

        return new ValidationResult(
                Collections.unmodifiableList(issues),
                totalXPaths,
                validCount,
                errorCount,
                warningCount,
                infoCount
        );
    }

    /**
     * Collects all element names from the schema for context validation.
     */
    private Set<String> collectElementNames() {
        Set<String> names = new HashSet<>();
        collectElementNamesRecursive(schema, names, new HashSet<>());
        return names;
    }

    private void collectElementNamesRecursive(XsdNode node, Set<String> names, Set<String> visitedIds) {
        if (node == null) return;

        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) return;
        if (nodeId != null) visitedIds.add(nodeId);

        if (node instanceof XsdElement element) {
            String name = element.getName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }

        for (XsdNode child : node.getChildren()) {
            collectElementNamesRecursive(child, names, visitedIds);
        }
    }

    /**
     * Traverses the schema and validates XPath expressions.
     */
    private void traverseAndValidate(XsdNode node, List<XPathValidationIssue> issues,
                                     Set<String> elementNames, Set<String> visitedIds) {
        if (node == null) return;

        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) return;
        if (nodeId != null) visitedIds.add(nodeId);

        // Validate Key
        if (node instanceof XsdKey key) {
            validateIdentityConstraint(key, XPathSource.KEY_SELECTOR, XPathSource.KEY_FIELD, issues, elementNames);
        }
        // Validate KeyRef
        else if (node instanceof XsdKeyRef keyRef) {
            validateIdentityConstraint(keyRef, XPathSource.KEYREF_SELECTOR, XPathSource.KEYREF_FIELD, issues, elementNames);
        }
        // Validate Unique
        else if (node instanceof XsdUnique unique) {
            validateIdentityConstraint(unique, XPathSource.UNIQUE_SELECTOR, XPathSource.UNIQUE_FIELD, issues, elementNames);
        }
        // Validate Assert
        else if (node instanceof XsdAssert assertion) {
            validateAssert(assertion, issues);
        }

        // Recurse to children
        for (XsdNode child : node.getChildren()) {
            traverseAndValidate(child, issues, elementNames, visitedIds);
        }
    }

    /**
     * Validates an identity constraint (Key, KeyRef, Unique).
     */
    private void validateIdentityConstraint(XsdIdentityConstraint constraint,
                                            XPathSource selectorSource,
                                            XPathSource fieldSource,
                                            List<XPathValidationIssue> issues,
                                            Set<String> elementNames) {
        String constraintName = constraint.getName();

        // Validate selector XPath
        XsdSelector selector = constraint.getSelector();
        if (selector != null) {
            String selectorXPath = selector.getXpath();
            if (selectorXPath != null && !selectorXPath.isBlank()) {
                validateXPath(selectorXPath, constraintName, selectorSource, issues, elementNames, constraint);
            }
        }

        // Validate field XPaths
        for (XsdField field : constraint.getFields()) {
            String fieldXPath = field.getXpath();
            if (fieldXPath != null && !fieldXPath.isBlank()) {
                validateXPath(fieldXPath, constraintName, fieldSource, issues, elementNames, constraint);
            }
        }
    }

    /**
     * Validates an Assert constraint.
     */
    private void validateAssert(XsdAssert assertion, List<XPathValidationIssue> issues) {
        String test = assertion.getTest();
        if (test == null || test.isBlank()) {
            issues.add(XPathValidationIssue.error(
                    "(empty)",
                    assertion.getName(),
                    XPathSource.ASSERT_TEST,
                    "Assert test expression is empty",
                    assertion
            ));
            return;
        }

        // Validate XPath 2.0 syntax using Saxon
        String syntaxError = validateXPathSyntax(test, true);
        if (syntaxError != null) {
            issues.add(XPathValidationIssue.error(
                    test,
                    assertion.getName(),
                    XPathSource.ASSERT_TEST,
                    syntaxError,
                    assertion
            ));
        }
    }

    /**
     * Validates a single XPath expression.
     */
    private void validateXPath(String xpath, String constraintName, XPathSource source,
                               List<XPathValidationIssue> issues, Set<String> elementNames,
                               XsdNode sourceNode) {
        // Level 1: Syntax validation
        String syntaxError = validateXPathSyntax(xpath, source == XPathSource.ASSERT_TEST);
        if (syntaxError != null) {
            issues.add(XPathValidationIssue.error(xpath, constraintName, source, syntaxError, sourceNode));
            return; // Don't continue if syntax is invalid
        }

        // Level 2: Context validation - check if referenced elements exist
        List<String> pathElements = extractElementNames(xpath);
        for (String elementName : pathElements) {
            if (!elementNames.contains(elementName) && !isBuiltInXPath(elementName)) {
                issues.add(XPathValidationIssue.warning(
                        xpath,
                        constraintName,
                        source,
                        "Element '" + elementName + "' not found in schema",
                        sourceNode
                ));
            }
        }

        // Level 3: Sample XML test (if available)
        if (sampleXml != null) {
            try {
                int matchCount = testXPathAgainstSample(xpath);
                if (matchCount == 0) {
                    issues.add(XPathValidationIssue.info(
                            xpath,
                            constraintName,
                            source,
                            "No matches in sample XML",
                            matchCount,
                            sourceNode
                    ));
                } else {
                    issues.add(XPathValidationIssue.info(
                            xpath,
                            constraintName,
                            source,
                            matchCount + " match(es) in sample XML",
                            matchCount,
                            sourceNode
                    ));
                }
            } catch (Exception e) {
                logger.debug("XPath test failed for '{}': {}", xpath, e.getMessage());
            }
        }
    }

    /**
     * Validates XPath syntax using Saxon.
     *
     * @param xpath    the XPath expression
     * @param isXPath2 whether to use XPath 2.0 validation
     * @return error message or null if valid
     */
    private String validateXPathSyntax(String xpath, boolean isXPath2) {
        try {
            XPathCompiler compiler = processor.newXPathCompiler();

            // Declare common namespaces
            compiler.declareNamespace("xs", "http://www.w3.org/2001/XMLSchema");
            compiler.declareNamespace("fn", "http://www.w3.org/2005/xpath-functions");

            // Try to compile the expression
            compiler.compile(xpath);
            return null; // Valid
        } catch (SaxonApiException e) {
            String message = e.getMessage();
            // Extract a cleaner error message
            if (message.contains(":")) {
                message = message.substring(message.lastIndexOf(':') + 1).trim();
            }
            return "Syntax error: " + message;
        }
    }

    /**
     * Extracts element names from an XPath expression.
     * This is a simplified extraction for context validation.
     */
    private List<String> extractElementNames(String xpath) {
        List<String> elements = new ArrayList<>();

        // Split by / and extract element names
        String[] parts = xpath.split("/");
        for (String part : parts) {
            // Remove predicates [...]
            int bracketIndex = part.indexOf('[');
            if (bracketIndex > 0) {
                part = part.substring(0, bracketIndex);
            }

            // Skip empty parts, axis specifiers, *, @, .
            part = part.trim();
            if (part.isEmpty() || part.equals(".") || part.equals("..") ||
                    part.startsWith("@") || part.equals("*")) {
                continue;
            }

            // Skip axis specifiers (e.g., child::, descendant::)
            if (part.contains("::")) {
                part = part.substring(part.indexOf("::") + 2);
            }

            // Skip namespace prefixes
            if (part.contains(":")) {
                part = part.substring(part.indexOf(":") + 1);
            }

            if (!part.isEmpty() && Character.isLetter(part.charAt(0))) {
                elements.add(part);
            }
        }

        return elements;
    }

    /**
     * Checks if the element name is a built-in XPath function or node test.
     */
    private boolean isBuiltInXPath(String name) {
        return Set.of(
                "node", "text", "comment", "processing-instruction",
                "document-node", "element", "attribute", "schema-element",
                "schema-attribute", "namespace-node"
        ).contains(name.toLowerCase());
    }

    /**
     * Tests an XPath expression against the sample XML.
     *
     * @param xpath the XPath expression
     * @return number of matches
     */
    private int testXPathAgainstSample(String xpath) throws Exception {
        if (sampleXml == null) {
            return -1;
        }

        XPath xpathEval = XPathFactory.newInstance().newXPath();

        // Try as node set first
        try {
            org.w3c.dom.NodeList result = (org.w3c.dom.NodeList) xpathEval.evaluate(
                    xpath, sampleXml, XPathConstants.NODESET);
            return result.getLength();
        } catch (Exception e) {
            // Try as string/number
            try {
                String result = xpathEval.evaluate(xpath, sampleXml);
                return result != null && !result.isEmpty() ? 1 : 0;
            } catch (Exception e2) {
                throw e; // Re-throw original exception
            }
        }
    }
}
