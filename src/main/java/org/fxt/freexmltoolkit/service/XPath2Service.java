package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.transform.dom.DOMSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for evaluating XPath 2.0 expressions using Saxon HE.
 * This service is used for XSD 1.1 assertions and type alternatives.
 * <p>
 * Saxon HE provides full XPath 2.0 support with proper function library and type system.
 */
public class XPath2Service {

    private static final Logger logger = LogManager.getLogger(XPath2Service.class);

    private final Processor processor;
    private final DocumentBuilder documentBuilder;

    /**
     * Result of an XPath 2.0 evaluation
     * @param success Whether the evaluation was successful
     * @param values List of result values as strings
     * @param errorMessage The error message (if any)
     * @param xdmValue The raw XdmValue result
     */
    public record XPath2Result(
            boolean success,
            List<String> values,
            String errorMessage,
            XdmValue xdmValue
    ) {
        public static XPath2Result success(List<String> values, XdmValue xdmValue) {
            return new XPath2Result(true, values, null, xdmValue);
        }

        public static XPath2Result error(String errorMessage) {
            return new XPath2Result(false, List.of(), errorMessage, null);
        }

        public boolean isBoolean() {
            return xdmValue != null && xdmValue instanceof XdmAtomicValue atomicValue
                    && atomicValue.getUnderlyingValue() instanceof net.sf.saxon.value.BooleanValue;
        }

        public boolean getBooleanValue() {
            if (xdmValue instanceof XdmAtomicValue atomicValue) {
                try {
                    return atomicValue.getBooleanValue();
                } catch (SaxonApiException e) {
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * Validation result for an XPath 2.0 expression
     * @param valid Whether the expression is valid
     * @param errorMessage The error message (if any)
     * @param errorLine The line number of the error
     * @param errorColumn The column number of the error
     */
    public record ValidationResult(
            boolean valid,
            String errorMessage,
            int errorLine,
            int errorColumn
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, null, -1, -1);
        }

        public static ValidationResult failure(String errorMessage, int line, int column) {
            return new ValidationResult(false, errorMessage, line, column);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage, -1, -1);
        }
    }

    /**
     * Creates a new XPath2Service with Saxon HE processor
     */
    public XPath2Service() {
        this.processor = new Processor(false); // false = use Saxon-HE (Home Edition)
        this.documentBuilder = processor.newDocumentBuilder();
        logger.info("XPath2Service initialized with Saxon HE {}", processor.getSaxonProductVersion());
    }

    /**
     * Validates an XPath 2.0 expression without evaluating it
     *
     * @param expression The XPath 2.0 expression to validate
     * @return ValidationResult indicating whether the expression is valid
     */
    public ValidationResult validateExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return ValidationResult.failure("Expression cannot be empty");
        }

        try {
            XPathCompiler compiler = processor.newXPathCompiler();
            compiler.compile(expression);
            return ValidationResult.success();

        } catch (SaxonApiException e) {
            logger.debug("XPath validation error: {}", e.getMessage());
            return ValidationResult.failure(e.getMessage());
        }
    }

    /**
     * Validates an XPath 2.0 expression with namespace context
     *
     * @param expression The XPath 2.0 expression to validate
     * @param namespaces Map of namespace prefixes to URIs
     * @return ValidationResult indicating whether the expression is valid
     */
    public ValidationResult validateExpression(String expression, Map<String, String> namespaces) {
        if (expression == null || expression.isEmpty()) {
            return ValidationResult.failure("Expression cannot be empty");
        }

        try {
            XPathCompiler compiler = processor.newXPathCompiler();

            // Declare namespaces
            if (namespaces != null) {
                for (Map.Entry<String, String> ns : namespaces.entrySet()) {
                    compiler.declareNamespace(ns.getKey(), ns.getValue());
                }
            }

            compiler.compile(expression);
            return ValidationResult.success();

        } catch (SaxonApiException e) {
            logger.debug("XPath validation error: {}", e.getMessage());
            return ValidationResult.failure(e.getMessage());
        }
    }

    /**
     * Evaluates an XPath 2.0 expression against a DOM node
     *
     * @param expression  The XPath 2.0 expression
     * @param contextNode The context node for evaluation
     * @return XPath2Result containing the evaluation result
     */
    public XPath2Result evaluate(String expression, Node contextNode) {
        return evaluate(expression, contextNode, null);
    }

    /**
     * Evaluates an XPath 2.0 expression against a DOM node with namespace context
     *
     * @param expression  The XPath 2.0 expression
     * @param contextNode The context node for evaluation
     * @param namespaces  Map of namespace prefixes to URIs
     * @return XPath2Result containing the evaluation result
     */
    public XPath2Result evaluate(String expression, Node contextNode, Map<String, String> namespaces) {
        try {
            XPathCompiler compiler = processor.newXPathCompiler();

            // Declare namespaces
            if (namespaces != null) {
                for (Map.Entry<String, String> ns : namespaces.entrySet()) {
                    compiler.declareNamespace(ns.getKey(), ns.getValue());
                }
            }

            XPathExecutable executable = compiler.compile(expression);
            XPathSelector selector = executable.load();

            // Set context item from DOM node
            XdmNode xdmNode = documentBuilder.wrap(contextNode);
            selector.setContextItem(xdmNode);

            // Evaluate
            XdmValue result = selector.evaluate();

            // Convert result to strings
            List<String> values = new ArrayList<>();
            for (XdmItem item : result) {
                values.add(item.getStringValue());
            }

            return XPath2Result.success(values, result);

        } catch (SaxonApiException e) {
            logger.error("XPath evaluation error: {}", e.getMessage());
            return XPath2Result.error(e.getMessage());
        }
    }

    /**
     * Evaluates an XPath 2.0 expression against a DOM document
     *
     * @param expression The XPath 2.0 expression
     * @param document   The document for evaluation
     * @param namespaces Map of namespace prefixes to URIs
     * @return XPath2Result containing the evaluation result
     */
    public XPath2Result evaluateOnDocument(String expression, Document document, Map<String, String> namespaces) {
        try {
            XPathCompiler compiler = processor.newXPathCompiler();

            // Declare namespaces
            if (namespaces != null) {
                for (Map.Entry<String, String> ns : namespaces.entrySet()) {
                    compiler.declareNamespace(ns.getKey(), ns.getValue());
                }
            }

            XPathExecutable executable = compiler.compile(expression);
            XPathSelector selector = executable.load();

            // Set context item from document
            XdmNode xdmNode = documentBuilder.build(new DOMSource(document));
            selector.setContextItem(xdmNode);

            // Evaluate
            XdmValue result = selector.evaluate();

            // Convert result to strings
            List<String> values = new ArrayList<>();
            for (XdmItem item : result) {
                values.add(item.getStringValue());
            }

            return XPath2Result.success(values, result);

        } catch (SaxonApiException e) {
            logger.error("XPath evaluation error: {}", e.getMessage());
            return XPath2Result.error(e.getMessage());
        }
    }

    /**
     * Evaluates an XPath 2.0 assertion (must return boolean)
     *
     * @param assertion   The assertion expression
     * @param contextNode The context node for evaluation
     * @param namespaces  Map of namespace prefixes to URIs
     * @return XPath2Result with boolean result
     */
    public XPath2Result evaluateAssertion(String assertion, Node contextNode, Map<String, String> namespaces) {
        XPath2Result result = evaluate(assertion, contextNode, namespaces);

        if (!result.success()) {
            return result;
        }

        // Check if result is boolean
        if (!result.isBoolean() && !result.values().isEmpty()) {
            // Try to convert to boolean using XPath 2.0 effective boolean value rules
            String value = result.values().get(0);
            boolean boolValue = Boolean.parseBoolean(value);
            logger.debug("Assertion result converted to boolean: {}", boolValue);
        }

        return result;
    }

    /**
     * Extracts namespace declarations from a map of attributes
     * Useful for extracting namespaces from xs:assert/@xpath-default-namespace
     *
     * @param attributes Map of attribute name to value
     * @return Map of namespace prefixes to URIs
     */
    public Map<String, String> extractNamespaces(Map<String, String> attributes) {
        Map<String, String> namespaces = new HashMap<>();

        if (attributes == null) {
            return namespaces;
        }

        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            String name = attr.getKey();
            String value = attr.getValue();

            if (name.equals("xmlns")) {
                namespaces.put("", value); // Default namespace
            } else if (name.startsWith("xmlns:")) {
                String prefix = name.substring(6);
                namespaces.put(prefix, value);
            } else if (name.equals("xpath-default-namespace")) {
                namespaces.put("", value); // XSD 1.1 default namespace
            }
        }

        return namespaces;
    }

    /**
     * Returns the Saxon processor instance for advanced usage
     */
    public Processor getProcessor() {
        return processor;
    }
}
