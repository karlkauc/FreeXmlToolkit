package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of SchematronService using Saxon for XSLT-based Schematron validation.
 * Provides Schematron validation capabilities for XML documents.
 */
public class SchematronServiceImpl implements SchematronService {

    private static final Logger logger = LogManager.getLogger(SchematronServiceImpl.class);

    // Standard Schematron XSLT files for validation
    private static final String SCHEMATRON_ISO_DSDL_XSLT =
            "https://raw.githubusercontent.com/Schematron/schematron/master/trunk/schematron/code/iso_dsdl_include.xsl";
    private static final String SCHEMATRON_ISO_ABSTRACT_EXPAND_XSLT =
            "https://raw.githubusercontent.com/Schematron/schematron/master/trunk/schematron/code/iso_abstract_expand.xsl";
    private static final String SCHEMATRON_ISO_SVRL_XSLT =
            "https://raw.githubusercontent.com/Schematron/schematron/master/trunk/schematron/code/iso_svrl_for_xslt2.xsl";

    private final Processor processor;
    private final XsltCompiler compiler;

    public SchematronServiceImpl() {
        this.processor = new Processor(false);
        this.compiler = processor.newXsltCompiler();
    }

    @Override
    public List<SchematronValidationError> validateXml(String xmlContent, File schematronFile) {
        List<SchematronValidationError> errors = new ArrayList<>();

        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            errors.add(new SchematronValidationError(
                    "XML content is null or empty",
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
            return errors;
        }

        if (schematronFile == null || !schematronFile.exists()) {
            errors.add(new SchematronValidationError(
                    "Schematron file is null or does not exist",
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
            return errors;
        }

        try {
            // Create a temporary XML file for validation
            File tempXmlFile = createTempXmlFile(xmlContent);

            // Perform validation
            List<SchematronValidationError> validationErrors = validateXmlFile(tempXmlFile, schematronFile);
            errors.addAll(validationErrors);

            // Clean up temporary file
            tempXmlFile.delete();

        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
            errors.add(new SchematronValidationError(
                    "Validation error: " + e.getMessage(),
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
        }

        return errors;
    }

    @Override
    public List<SchematronValidationError> validateXmlFile(File xmlFile, File schematronFile) {
        List<SchematronValidationError> errors = new ArrayList<>();

        if (xmlFile == null || !xmlFile.exists()) {
            errors.add(new SchematronValidationError(
                    "XML file is null or does not exist",
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
            return errors;
        }

        if (schematronFile == null || !schematronFile.exists()) {
            errors.add(new SchematronValidationError(
                    "Schematron file is null or does not exist",
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
            return errors;
        }

        try {
            // Try XSLT-based validation first
            XsltExecutable schematronXslt = compileSchematronToXslt(schematronFile);
            if (schematronXslt != null) {
                // Transform XML using the compiled Schematron XSLT
                XsltTransformer transformer = schematronXslt.load();
                transformer.setSource(new StreamSource(xmlFile));

                StringWriter resultWriter = new StringWriter();
                Serializer serializer = processor.newSerializer(resultWriter);
                transformer.setDestination(serializer);

                transformer.transform();

                // Parse the SVRL result and extract validation errors
                String svrlResult = resultWriter.toString();
                processSvrlResult(svrlResult, errors);
            } else {
                // Add error indicating XSLT compilation failed
                errors.add(new SchematronValidationError(
                        "Schematron XSLT compilation failed due to namespace or syntax issues. The schema may contain advanced features not supported by the fallback validator.",
                        "compilation-error",
                        null,
                        0,
                        0,
                        "error"
                ));
                
                // Fallback to direct validation if XSLT compilation fails
                logger.info("Using fallback direct validation for Schematron file: {}", schematronFile.getAbsolutePath());
                validateDirectly(xmlFile, schematronFile, errors);
            }

        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
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

    @Override
    public SchematronValidationResult validateXmlWithSchematron(File xmlFile, File schematronFile) {
        SchematronValidationResult result = new SchematronValidationResult();

        if (xmlFile == null || !xmlFile.exists()) {
            result.addError("XML file is null or does not exist");
            return result;
        }

        if (schematronFile == null || !schematronFile.exists()) {
            result.addError("Schematron file is null or does not exist");
            return result;
        }

        try {
            List<SchematronValidationError> errors = validateXmlFile(xmlFile, schematronFile);
            for (SchematronValidationError error : errors) {
                if ("error".equals(error.severity())) {
                    result.addError(error.message());
                } else if ("warning".equals(error.severity())) {
                    result.addWarning(error.message());
                }
            }
        } catch (Exception e) {
            result.addError("Validation failed: " + e.getMessage());
        }

        return result;
    }

    @Override
    public boolean isValidSchematronFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            // Try to parse the file as XML and check for Schematron elements
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);

            // Check if it contains Schematron elements
            NodeList schemaElements = document.getElementsByTagName("schema");
            NodeList patternElements = document.getElementsByTagName("pattern");
            NodeList ruleElements = document.getElementsByTagName("rule");

            return schemaElements.getLength() > 0 || patternElements.getLength() > 0 || ruleElements.getLength() > 0;

        } catch (Exception e) {
            logger.debug("File is not a valid Schematron file: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Compiles a Schematron file to XSLT for validation.
     * This is a simplified implementation that parses basic Schematron rules directly.
     *
     * @param schematronFile The Schematron file to compile
     * @return The compiled XSLT executable or null if compilation fails
     */
    private XsltExecutable compileSchematronToXslt(File schematronFile) {
        try {
            // For a simplified implementation, we'll parse the Schematron file directly
            // and create a simple XSLT that validates the rules
            String schematronXslt = generateValidationXsltFromSchematron(schematronFile);

            if (schematronXslt != null) {
                StreamSource source = new StreamSource(new ByteArrayInputStream(schematronXslt.getBytes(StandardCharsets.UTF_8)));
                return compiler.compile(source);
            }

            logger.warn("Could not generate XSLT from Schematron file: {}", schematronFile.getAbsolutePath());
            return null;

        } catch (SaxonApiException e) {
            logger.error("Saxon XSLT compilation error for Schematron file: {}", schematronFile.getAbsolutePath(), e);
            // Log the specific compilation errors
            if (e.getCause() != null) {
                logger.error("Compilation cause: {}", e.getCause().getMessage());
            }
            return null;
        } catch (Exception e) {
            logger.error("Error compiling Schematron to XSLT", e);
            return null;
        }
    }

    /**
     * Direct validation fallback method that validates XML against Schematron rules
     * without using XSLT transformation.
     *
     * @param xmlFile        The XML file to validate
     * @param schematronFile The Schematron file containing rules
     * @param errors         List to collect validation errors
     */
    private void validateDirectly(File xmlFile, File schematronFile, List<SchematronValidationError> errors) {
        try {
            // Parse the XML document
            DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
            xmlFactory.setNamespaceAware(true);
            DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
            Document xmlDoc = xmlBuilder.parse(xmlFile);

            // Parse the Schematron document
            DocumentBuilderFactory schFactory = DocumentBuilderFactory.newInstance();
            schFactory.setNamespaceAware(true);
            DocumentBuilder schBuilder = schFactory.newDocumentBuilder();
            Document schDoc = schBuilder.parse(schematronFile);

            // Create XPath evaluator with namespace context
            XPath xpath = XPathFactory.newInstance().newXPath();

            // Set up namespace context from Schematron document
            xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext() {
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
                                            // Check for XPath 2.0 features that aren't supported
                                            if (test.contains("xs:") && !test.contains("xs:string")) {
                                                // XPath 2.0 type functions not supported in fallback validation
                                                errors.add(new SchematronValidationError(
                                                        "XPath 2.0 type function '" + test + "' not supported in fallback validation. Original XSLT compilation failed.",
                                                        assertId != null && !assertId.isEmpty() ? assertId : test,
                                                        context,
                                                        0,
                                                        0,
                                                        "error"
                                                ));
                                                continue;
                                            }
                                            
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

    /**
     * Generates a simple XSLT from a Schematron file for basic validation.
     * This simplified implementation parses basic Schematron patterns, rules, and assertions.
     *
     * @param schematronFile The Schematron file to parse
     * @return XSLT string for validation or null if parsing fails
     */
    private String generateValidationXsltFromSchematron(File schematronFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document schematronDoc = builder.parse(schematronFile);

            StringBuilder xslt = new StringBuilder();
            xslt.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xslt.append("<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" ");
            xslt.append("xmlns:svrl=\"http://purl.oclc.org/dsdl/svrl\" ");
            xslt.append("xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" ");
            xslt.append("xmlns:fn=\"http://www.w3.org/2005/xpath-functions\"");

            // Extract namespace declarations from the Schematron schema
            NodeList nsElements = schematronDoc.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "ns");
            if (nsElements.getLength() == 0) {
                nsElements = schematronDoc.getElementsByTagName("ns");
            }

            for (int i = 0; i < nsElements.getLength(); i++) {
                Element nsElement = (Element) nsElements.item(i);
                String prefix = nsElement.getAttribute("prefix");
                String uri = nsElement.getAttribute("uri");
                if (prefix != null && !prefix.isEmpty() && uri != null && !uri.isEmpty()) {
                    xslt.append(" xmlns:").append(prefix).append("=\"").append(escapeXml(uri)).append("\"");
                }
            }

            xslt.append(">\n");
            xslt.append("<xsl:output method=\"xml\" indent=\"yes\"/>\n");
            xslt.append("<xsl:template match=\"/\">\n");
            xslt.append("<svrl:schematron-output>\n");

            // First, extract any variable declarations from the schema
            NodeList letElements = schematronDoc.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "let");
            if (letElements.getLength() == 0) {
                letElements = schematronDoc.getElementsByTagName("let");
            }

            // Add variable declarations to XSLT
            for (int i = 0; i < letElements.getLength(); i++) {
                Element letElement = (Element) letElements.item(i);
                String name = letElement.getAttribute("name");
                String value = letElement.getAttribute("value");
                if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
                    xslt.append("<xsl:variable name=\"").append(name).append("\" select=\"").append(escapeXml(value)).append("\"/>\\n");
                }
            }

            // Parse Schematron patterns and rules
            NodeList patterns = schematronDoc.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "pattern");
            if (patterns.getLength() == 0) {
                // Try without namespace for compatibility
                patterns = schematronDoc.getElementsByTagName("pattern");
            }

            logger.debug("Found {} patterns in Schematron file", patterns.getLength());
            for (int i = 0; i < patterns.getLength(); i++) {
                Element pattern = (Element) patterns.item(i);
                String patternId = pattern.getAttribute("id");

                NodeList rules = pattern.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "rule");
                if (rules.getLength() == 0) {
                    rules = pattern.getElementsByTagName("rule");
                }
                for (int j = 0; j < rules.getLength(); j++) {
                    Element rule = (Element) rules.item(j);
                    String context = rule.getAttribute("context");

                    if (context != null && !context.isEmpty()) {
                        xslt.append("<xsl:for-each select=\"").append(escapeXml(context)).append("\">\n");

                        // Add any let elements (variables) from within this rule
                        NodeList ruleLetElements = rule.getElementsByTagNameNS("http://purl.oclc.org/dsdl/schematron", "let");
                        if (ruleLetElements.getLength() == 0) {
                            ruleLetElements = rule.getElementsByTagName("let");
                        }

                        for (int l = 0; l < ruleLetElements.getLength(); l++) {
                            Element letElement = (Element) ruleLetElements.item(l);
                            String name = letElement.getAttribute("name");
                            String value = letElement.getAttribute("value");
                            if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
                                xslt.append("<xsl:variable name=\"").append(name).append("\" select=\"").append(escapeXml(value)).append("\"/>\n");
                            }
                        }

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
                                xslt.append("<xsl:if test=\"not(").append(escapeXml(test)).append(")\">\n");
                                xslt.append("<svrl:failed-assert test=\"").append(escapeXml(test)).append("\" ");
                                xslt.append("location=\"{generate-id(.)}\"");
                                if (assertId != null && !assertId.isEmpty()) {
                                    xslt.append(" id=\"").append(assertId).append("\"");
                                }
                                xslt.append(">\n");
                                xslt.append("<svrl:text>").append(escapeXml(message != null ? message : "Assertion failed")).append("</svrl:text>\n");
                                xslt.append("</svrl:failed-assert>\n");
                                xslt.append("</xsl:if>\n");
                            }
                        }

                        xslt.append("</xsl:for-each>\n");
                    }
                }
            }

            xslt.append("</svrl:schematron-output>\n");
            xslt.append("</xsl:template>\n");
            xslt.append("</xsl:stylesheet>");

            return xslt.toString();

        } catch (Exception e) {
            logger.error("Error parsing Schematron file: {}", schematronFile.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Processes the SVRL (Schematron Validation Report Language) result and extracts validation errors.
     *
     * @param svrlResult The SVRL XML result
     * @param errors     List to collect validation errors
     */
    private void processSvrlResult(String svrlResult, List<SchematronValidationError> errors) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document svrlDocument = builder.parse(new ByteArrayInputStream(svrlResult.getBytes(StandardCharsets.UTF_8)));

            // Extract failed assertions
            NodeList failedAsserts = svrlDocument.getElementsByTagName("svrl:failed-assert");
            for (int i = 0; i < failedAsserts.getLength(); i++) {
                Element failedAssert = (Element) failedAsserts.item(i);

                String message = getElementText(failedAssert, "svrl:text");
                String test = failedAssert.getAttribute("test");
                String location = failedAssert.getAttribute("location");

                // Parse location for line/column information
                int lineNumber = 0;
                int columnNumber = 0;
                if (location != null && !location.isEmpty()) {
                    String[] parts = location.split(":");
                    if (parts.length >= 2) {
                        try {
                            lineNumber = Integer.parseInt(parts[0]);
                            columnNumber = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            logger.debug("Could not parse location: {}", location);
                        }
                    }
                }

                errors.add(new SchematronValidationError(
                        message != null ? message : "Validation failed",
                        test,
                        location,
                        lineNumber,
                        columnNumber,
                        "error"
                ));
            }

        } catch (Exception e) {
            logger.error("Error processing SVRL result", e);
            errors.add(new SchematronValidationError(
                    "Error processing validation results: " + e.getMessage(),
                    null,
                    null,
                    0,
                    0,
                    "error"
            ));
        }
    }

    /**
     * Gets the text content of a child element.
     *
     * @param parent  The parent element
     * @param tagName The tag name of the child element
     * @return The text content or null if not found
     */
    private String getElementText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            Element child = (Element) children.item(0);
            return child.getTextContent();
        }
        return null;
    }

    /**
     * Escapes XML special characters for use in XML attributes and content.
     *
     * @param text The text to escape
     * @return The escaped text
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Creates a temporary XML file from content.
     *
     * @param xmlContent The XML content
     * @return The temporary file
     * @throws IOException If file creation fails
     */
    private File createTempXmlFile(String xmlContent) throws IOException {
        File tempFile = File.createTempFile("schematron_validation_", ".xml");
        tempFile.deleteOnExit();
        java.nio.file.Files.write(tempFile.toPath(), xmlContent.getBytes(StandardCharsets.UTF_8));
        return tempFile;
    }
}
