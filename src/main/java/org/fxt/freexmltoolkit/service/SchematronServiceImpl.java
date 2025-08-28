package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of SchematronService using com.helger.schematron library.
 * Provides multiple Schematron validation implementations with automatic fallback.
 */
public class SchematronServiceImpl implements SchematronService {

    private static final Logger logger = LogManager.getLogger(SchematronServiceImpl.class);

    public SchematronServiceImpl() {
        // No initialization needed for Helger Schematron
    }

    @Override
    public List<SchematronValidationError> validateXml(String xmlContent, File schematronFile) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return List.of(new SchematronValidationError(
                    "XML content is null or empty",
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
        }

        if (schematronFile == null || !schematronFile.exists()) {
            return List.of(new SchematronValidationError(
                    "Schematron file is null or does not exist",
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
        }

        try {
            // Create XML source from string
            Source xmlSource = new StreamSource(new StringReader(xmlContent));

            // Validate using Helger Schematron
            return performValidation(xmlSource, schematronFile);

        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
            return List.of(new SchematronValidationError(
                    "Validation error: " + e.getMessage(),
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
        }
    }

    @Override
    public List<SchematronValidationError> validateXmlFile(File xmlFile, File schematronFile) {
        if (xmlFile == null || !xmlFile.exists()) {
            return List.of(new SchematronValidationError(
                    "XML file is null or does not exist",
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
        }

        if (schematronFile == null || !schematronFile.exists()) {
            return List.of(new SchematronValidationError(
                    "Schematron file is null or does not exist",
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
        }

        try {
            // Create XML source from file
            Source xmlSource = new StreamSource(xmlFile);

            // Validate using Helger Schematron
            return performValidation(xmlSource, schematronFile);

        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
            return List.of(new SchematronValidationError(
                    "Validation error: " + e.getMessage(),
                    "validation-error",
                    null,
                    0,
                    0,
                    "error"
            ));
        }
    }

    /**
     * Core validation method using direct Schematron validation.
     * This implementation manually parses Schematron rules and validates XML against them.
     */
    private List<SchematronValidationError> performValidation(Source xmlSource, File schematronFile) {
        List<SchematronValidationError> errors = new ArrayList<>();
        
        try {
            // Parse the XML document from source
            Document xmlDoc = parseSourceToDocument(xmlSource);
            if (xmlDoc == null) {
                errors.add(new SchematronValidationError(
                        "Could not parse XML document",
                        "xml-parse-error",
                        null,
                        0,
                        0,
                        "error"
                ));
                return errors;
            }

            // Use direct validation approach
            validateDirectly(xmlDoc, schematronFile, errors);
            
        } catch (Exception e) {
            logger.error("Error in Schematron validation", e);
            errors.add(new SchematronValidationError(
                    "Validation error: " + e.getMessage(),
                    "validation-error",
                    null,
                    0,
                    0,
                    "error"
            ));
        }

        return errors;
    }

    /**
     * Parse XML source into DOM document.
     */
    private Document parseSourceToDocument(Source xmlSource) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            if (xmlSource instanceof StreamSource streamSource) {
                if (streamSource.getInputStream() != null) {
                    return builder.parse(streamSource.getInputStream());
                } else if (streamSource.getReader() != null) {
                    // Convert reader to input stream
                    String xmlContent = streamSource.getReader().toString();
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
                    return builder.parse(inputStream);
                } else if (streamSource.getSystemId() != null) {
                    return builder.parse(streamSource.getSystemId());
                }
            }

            return null;
        } catch (Exception e) {
            logger.error("Error parsing XML source", e);
            return null;
        }
    }

    /**
     * Direct validation method that validates XML against Schematron rules
     * without using external Schematron libraries.
     */
    private void validateDirectly(Document xmlDoc, File schematronFile, List<SchematronValidationError> errors) {
        try {
            // Parse the Schematron document
            DocumentBuilderFactory schFactory = DocumentBuilderFactory.newInstance();
            schFactory.setNamespaceAware(true);
            DocumentBuilder schBuilder = schFactory.newDocumentBuilder();
            Document schDoc = schBuilder.parse(schematronFile);

            // Create XPath evaluator with namespace context
            XPath xpath = XPathFactory.newInstance().newXPath();

            // Set up namespace context from Schematron document
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    if (prefix == null) {
                        throw new IllegalArgumentException("Prefix cannot be null");
                    }

                    // Default XML Schema namespace
                    if ("xs".equals(prefix)) {
                        return "http://www.w3.org/2001/XMLSchema";
                    }

                    // Extract from Schematron ns elements
                    try {
                        NodeList nsElements = schDoc.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "ns");
                        if (nsElements.getLength() == 0) {
                            nsElements = schDoc.getElementsByTagName("ns");
                        }

                        for (int i = 0; i < nsElements.getLength(); i++) {
                            Element nsElement = (Element) nsElements.item(i);
                            String nsPrefix = nsElement.getAttribute("prefix");
                            String nsUri = nsElement.getAttribute("uri");
                            if (prefix.equals(nsPrefix)) {
                                return nsUri;
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Error getting namespace for prefix {}: {}", prefix, e.getMessage());
                    }

                    return javax.xml.XMLConstants.NULL_NS_URI;
                }

                @Override
                public String getPrefix(String namespaceURI) {
                    return null; // Not needed for our use case
                }

                @Override
                public java.util.Iterator<String> getPrefixes(String namespaceURI) {
                    return null; // Not needed for our use case
                }
            });

            // Find all patterns
            NodeList patterns = schDoc.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "pattern");
            if (patterns.getLength() == 0) {
                patterns = schDoc.getElementsByTagName("pattern");
            }

            for (int i = 0; i < patterns.getLength(); i++) {
                Element pattern = (Element) patterns.item(i);
                String patternId = pattern.getAttribute("id");

                // Find all rules in this pattern
                NodeList rules = pattern.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "rule");
                if (rules.getLength() == 0) {
                    rules = pattern.getElementsByTagName("rule");
                }

                for (int j = 0; j < rules.getLength(); j++) {
                    Element rule = (Element) rules.item(j);
                    String context = rule.getAttribute("context");

                    if (context != null && !context.isEmpty()) {
                        try {
                            // Find all nodes that match the context
                            NodeList contextNodes = (NodeList) xpath.evaluate(context, xmlDoc, XPathConstants.NODESET);

                            // Check assertions for each context node
                            NodeList assertions = rule.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "assert");
                            if (assertions.getLength() == 0) {
                                assertions = rule.getElementsByTagName("assert");
                            }

                            for (int k = 0; k < assertions.getLength(); k++) {
                                Element assertion = (Element) assertions.item(k);
                                String test = assertion.getAttribute("test");
                                String message = assertion.getTextContent();
                                String assertId = assertion.getAttribute("id");

                                if (test != null && !test.isEmpty()) {
                                    // Test the assertion against each context node
                                    for (int l = 0; l < contextNodes.getLength(); l++) {
                                        Node contextNode = contextNodes.item(l);
                                        try {
                                            Boolean result = (Boolean) xpath.evaluate(test, contextNode, XPathConstants.BOOLEAN);
                                            if (result == null || !result) {
                                                // Assertion failed
                                                errors.add(new SchematronValidationError(
                                                        message != null && !message.trim().isEmpty() ? message : "Assertion failed: " + test,
                                                        assertId != null && !assertId.isEmpty() ? assertId : test,
                                                        context,
                                                        0, // Line number not available in direct validation
                                                        0, // Column number not available in direct validation
                                                        "error"
                                                ));
                                            }
                                        } catch (Exception e) {
                                            logger.debug("Error evaluating assertion '{}' on context '{}': {}", test, context, e.getMessage());
                                            // Add error for invalid assertion test
                                            errors.add(new SchematronValidationError(
                                                    "Invalid assertion test '" + test + "': " + e.getMessage(),
                                                    assertId != null && !assertId.isEmpty() ? assertId : test,
                                                    context,
                                                    0,
                                                    0,
                                                    "error"
                                            ));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Error evaluating context '{}': {}", context, e.getMessage());
                            // Add error for invalid XPath expressions
                            errors.add(new SchematronValidationError(
                                    "Invalid XPath expression in context '" + context + "': " + e.getMessage(),
                                    "xpath-error",
                                    context,
                                    0,
                                    0,
                                    "error"
                            ));
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error in direct Schematron validation", e);
            errors.add(new SchematronValidationError(
                    "Direct validation error: " + e.getMessage(),
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
        }
    }

    @Override
    public SchematronValidationResult validateXmlWithSchematron(File xmlFile, File schematronFile) {
        SchematronValidationResult result = new SchematronValidationResult();

        List<SchematronValidationError> errors = validateXmlFile(xmlFile, schematronFile);
        for (SchematronValidationError error : errors) {
            if ("error".equals(error.severity())) {
                result.addError(error.message());
            } else if ("warning".equals(error.severity())) {
                result.addWarning(error.message());
            }
        }

        return result;
    }

    @Override
    public boolean isValidSchematronFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document schematronDoc = builder.parse(file);

            // Check if this is a Schematron document by looking for key elements
            Element root = schematronDoc.getDocumentElement();
            if (root == null) {
                return false;
            }

            String rootName = root.getLocalName();
            String rootNamespace = root.getNamespaceURI();

            // Check for Schematron root element
            return ("schema".equals(rootName) && "http://purl.oclc.org/dsdl/schematron".equals(rootNamespace)) ||
                    ("schema".equals(rootName) && rootNamespace == null); // For non-namespaced Schematron
            
        } catch (Exception e) {
            logger.debug("File is not a valid Schematron file: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

}
