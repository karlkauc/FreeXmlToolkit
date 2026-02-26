package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of SchematronService using Saxon XSLT transformation.
 */
public class SchematronServiceImpl implements SchematronService {

    private static final Logger logger = LogManager.getLogger(SchematronServiceImpl.class);
    private static final Processor SAXON_PROCESSOR = new Processor(false);
    private static final XsltCompiler XSLT_COMPILER;

    // Cache for compiled Schematron stylesheets
    private final Map<File, XsltExecutable> compiledSchematronCache = new ConcurrentHashMap<>();

    // Paths to the ISO Schematron skeleton stylesheets on the classpath
    private static final String[] SCHEMATRON_SKELETONS = {
            "/schematron/iso_dsdl_include.xsl",
            "/schematron/iso_abstract_expand.xsl",
            "/schematron/iso_svrl_for_xslt2.xsl"
    };

    static {
        XSLT_COMPILER = SAXON_PROCESSOR.newXsltCompiler();
        // Set a URIResolver that can resolve classpath resources
        XSLT_COMPILER.setURIResolver(new ClasspathURIResolver());
    }

    /**
     * Constructs a new SchematronServiceImpl with default settings.
     * Initializes the internal compiled Schematron cache.
     */
    public SchematronServiceImpl() {
        // Constructor
    }

    /**
     * URIResolver that resolves resources from the classpath.
     * This is needed because the Schematron skeleton XSL files use relative imports.
     */
    private static class ClasspathURIResolver implements URIResolver {
        @Override
        public Source resolve(String href, String base) {
            try {
                // Try to resolve relative to the schematron folder on classpath
                String resourcePath = "/schematron/" + href;
                URL resourceUrl = SchematronServiceImpl.class.getResource(resourcePath);
                if (resourceUrl != null) {
                    StreamSource source = new StreamSource(resourceUrl.openStream());
                    source.setSystemId(resourceUrl.toExternalForm());
                    return source;
                }

                // Fall back to default resolution
                return null;
            } catch (Exception e) {
                logger.debug("Could not resolve URI {} from classpath: {}", href, e.getMessage());
                return null;
            }
        }
    }

    @Override
    public List<SchematronValidationError> validateXml(String xmlContent, File schematronFile) throws SchematronLoadException {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return List.of(new SchematronValidationError(
                    "XML content is null or empty", null, null, 0, 0, "error"));
        }
        if (schematronFile == null || !schematronFile.exists()) {
            throw new SchematronLoadException("Schematron file is null or does not exist: " + 
                    (schematronFile != null ? schematronFile.getAbsolutePath() : "null"));
        }
        try {
            return performValidation(new StreamSource(new StringReader(xmlContent)), schematronFile);
        } catch (SchematronLoadException e) {
            throw e; // Re-throw SchematronLoadException
        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
            return List.of(new SchematronValidationError(
                    "Validation error: " + e.getMessage(), null, null, 0, 0, "error"));
        }
    }

    @Override
    public List<SchematronValidationError> validateXmlFile(File xmlFile, File schematronFile) throws SchematronLoadException {
        if (xmlFile == null || !xmlFile.exists()) {
            return List.of(new SchematronValidationError(
                    "XML file is null or does not exist", null, null, 0, 0, "error"));
        }
        if (schematronFile == null || !schematronFile.exists()) {
            throw new SchematronLoadException("Schematron file is null or does not exist: " + 
                    (schematronFile != null ? schematronFile.getAbsolutePath() : "null"));
        }
        try {
            return performValidation(new StreamSource(xmlFile), schematronFile);
        } catch (SchematronLoadException e) {
            throw e; // Re-throw SchematronLoadException
        } catch (Exception e) {
            logger.error("Error during Schematron validation", e);
            return List.of(new SchematronValidationError(
                    "Validation error: " + e.getMessage(), "validation-error", null, 0, 0, "error"));
        }
    }

    /**
     * Compiles a Schematron file into a validation stylesheet (XSLT).
     * This is a multi-step process using the ISO Schematron skeletons.
     */
    private XsltExecutable compileSchematron(File schematronFile) throws SchematronLoadException {
        if (compiledSchematronCache.containsKey(schematronFile)) {
            return compiledSchematronCache.get(schematronFile);
        }

        XdmNode schematronNode;
        try {
            schematronNode = SAXON_PROCESSOR.newDocumentBuilder().build(new StreamSource(schematronFile));
        } catch (SaxonApiException e) {
            throw new SchematronLoadException("Failed to parse Schematron file: " + schematronFile.getAbsolutePath() + 
                    ". Reason: " + e.getMessage(), e);
        }

        XdmNode currentResult = schematronNode;

        for (String skeletonPath : SCHEMATRON_SKELETONS) {
            try (InputStream skeletonStream = SchematronServiceImpl.class.getResourceAsStream(skeletonPath)) {
                if (skeletonStream == null) {
                    throw new SchematronLoadException("Cannot find Schematron skeleton on classpath: " + skeletonPath);
                }
                // Create StreamSource with system ID for proper relative URI resolution
                URL skeletonUrl = SchematronServiceImpl.class.getResource(skeletonPath);
                StreamSource skeletonSource = new StreamSource(skeletonStream);
                if (skeletonUrl != null) {
                    skeletonSource.setSystemId(skeletonUrl.toExternalForm());
                }
                XsltExecutable skeleton = XSLT_COMPILER.compile(skeletonSource);
                XsltTransformer transformer = skeleton.load();
                transformer.setInitialContextNode(currentResult);
                XdmDestination resultDestination = new XdmDestination();
                transformer.setDestination(resultDestination);
                transformer.transform();
                currentResult = resultDestination.getXdmNode();
            } catch (SchematronLoadException e) {
                throw e;
            } catch (Exception e) {
                throw new SchematronLoadException("Failed to apply Schematron skeleton " + skeletonPath +
                        ". Reason: " + e.getMessage(), e);
            }
        }

        XsltExecutable validationStylesheet;
        try {
            validationStylesheet = XSLT_COMPILER.compile(currentResult.asSource());
        } catch (SaxonApiException e) {
            throw new SchematronLoadException("Failed to compile Schematron to XSLT. The Schematron file may contain invalid rules or syntax. " +
                    "File: " + schematronFile.getAbsolutePath() + ". Reason: " + e.getMessage(), e);
        }
        compiledSchematronCache.put(schematronFile, validationStylesheet);
        return validationStylesheet;
    }

    private List<SchematronValidationError> performValidation(Source xmlSource, File schematronFile) throws SchematronLoadException {
        List<SchematronValidationError> validationEvents = new ArrayList<>();
        try {
            XsltExecutable validationXslt = compileSchematron(schematronFile);

            XsltTransformer validator = validationXslt.load();
            validator.setSource(xmlSource);

            StringWriter sw = new StringWriter();
            validator.setDestination(SAXON_PROCESSOR.newSerializer(sw));
            validator.transform();

            String svrlResult = sw.toString();
            if (!svrlResult.isEmpty()) {
                DocumentBuilderFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
                factory.setNamespaceAware(true);
                Document svrlDocument = factory.newDocumentBuilder().parse(new InputSource(new StringReader(svrlResult)));

                parseSvrlReport(svrlDocument, validationEvents);
            }

        } catch (SchematronLoadException e) {
            throw e; // Re-throw SchematronLoadException
        } catch (Exception e) {
            logger.error("Error in Saxon-based Schematron validation", e);
            validationEvents.add(new SchematronValidationError(
                    "Validation failed: " + e.getMessage(), "validation-error", null, 0, 0, "fatal"));
        }
        return validationEvents;
    }

    private void parseSvrlReport(Document svrlDocument, List<SchematronValidationError> validationEvents) {
        // Use Saxon XPath 3.1 via SaxonXPathHelper
        List<XdmNode> failedAsserts = SaxonXPathHelper.evaluateNodes(
                svrlDocument, "//svrl:failed-assert", SaxonXPathHelper.SVRL_NAMESPACES);

        for (XdmNode assertNode : failedAsserts) {
            String text = getTextNodeContent(assertNode);
            String role = SaxonXPathHelper.getAttributeValue(assertNode, "role");
            String test = SaxonXPathHelper.getAttributeValue(assertNode, "test");
            String location = SaxonXPathHelper.getAttributeValue(assertNode, "location");

            validationEvents.add(new SchematronValidationError(
                    text, test, location, 0, 0,
                    role != null && !role.isEmpty() ? role : "error"));
        }

        List<XdmNode> successfulReports = SaxonXPathHelper.evaluateNodes(
                svrlDocument, "//svrl:successful-report", SaxonXPathHelper.SVRL_NAMESPACES);

        for (XdmNode reportNode : successfulReports) {
            String text = getTextNodeContent(reportNode);
            String role = SaxonXPathHelper.getAttributeValue(reportNode, "role");
            String test = SaxonXPathHelper.getAttributeValue(reportNode, "test");
            String location = SaxonXPathHelper.getAttributeValue(reportNode, "location");

            validationEvents.add(new SchematronValidationError(
                    text, test, location, 0, 0,
                    role != null && !role.isEmpty() ? role : "warning"));
        }
    }

    /**
     * Extracts the text content from a svrl:text child node.
     */
    private String getTextNodeContent(XdmNode parentNode) {
        try {
            // Navigate to svrl:text child
            for (XdmSequenceIterator<XdmNode> it = parentNode.axisIterator(Axis.CHILD); it.hasNext(); ) {
                XdmNode child = it.next();
                if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                    String localName = child.getNodeName().getLocalName();
                    if ("text".equals(localName)) {
                        String content = child.getStringValue();
                        return content != null ? content.trim() : "";
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting text node content: {}", e.getMessage());
        }
        return "";
    }

    @Override
    public SchematronValidationResult validateXmlWithSchematron(File xmlFile, File schematronFile) throws SchematronLoadException {
        SchematronValidationResult result = new SchematronValidationResult();
        List<SchematronValidationError> errors = validateXmlFile(xmlFile, schematronFile);
        for (SchematronValidationError error : errors) {
            if ("error".equalsIgnoreCase(error.severity()) || "fatal".equalsIgnoreCase(error.severity())) {
                result.addError(error.message());
            } else if ("warning".equalsIgnoreCase(error.severity())) {
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
            compileSchematron(file);
            return true;
        } catch (Exception e) {
            logger.warn("isValidSchematronFile check failed for {}", file.getAbsolutePath(), e);
            return false;
        }
    }
}
