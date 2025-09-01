package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of SchematronService using Saxon XSLT transformation.
 */
public class SchematronServiceImpl implements SchematronService {

    private static final Logger logger = LogManager.getLogger(SchematronServiceImpl.class);
    private static final Processor SAXON_PROCESSOR = new Processor(false);
    private static final XsltCompiler XSLT_COMPILER = SAXON_PROCESSOR.newXsltCompiler();

    // Cache for compiled Schematron stylesheets
    private final Map<File, XsltExecutable> compiledSchematronCache = new ConcurrentHashMap<>();

    // Paths to the ISO Schematron skeleton stylesheets on the classpath
    private static final String[] SCHEMATRON_SKELETONS = {
            "/schematron/iso_dsdl_include.xsl",
            "/schematron/iso_abstract_expand.xsl",
            "/schematron/iso_svrl_for_xslt2.xsl"
    };

    public SchematronServiceImpl() {
        // Constructor
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
                XsltExecutable skeleton = XSLT_COMPILER.compile(new StreamSource(skeletonStream));
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
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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

    private void parseSvrlReport(Document svrlDocument, List<SchematronValidationError> validationEvents) throws Exception {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xPath = xPathFactory.newXPath();
        xPath.setNamespaceContext(new SimpleNamespaceContext("svrl", "http://purl.oclc.org/dsdl/svrl"));

        NodeList failedAsserts = (NodeList) xPath.evaluate("//svrl:failed-assert", svrlDocument, XPathConstants.NODESET);
        for (int i = 0; i < failedAsserts.getLength(); i++) {
            Element elem = (Element) failedAsserts.item(i);
            NodeList textNodes = elem.getElementsByTagNameNS("http://purl.oclc.org/dsdl/svrl", "text");
            String text = (textNodes.getLength() > 0) ? textNodes.item(0).getTextContent().trim() : "";
            String role = elem.getAttribute("role");
            validationEvents.add(new SchematronValidationError(
                    text, elem.getAttribute("test"), elem.getAttribute("location"), 0, 0,
                    role != null && !role.isEmpty() ? role : "error"));
        }

        NodeList successfulReports = (NodeList) xPath.evaluate("//svrl:successful-report", svrlDocument, XPathConstants.NODESET);
        for (int i = 0; i < successfulReports.getLength(); i++) {
            Element elem = (Element) successfulReports.item(i);
            NodeList textNodes = elem.getElementsByTagNameNS("http://purl.oclc.org/dsdl/svrl", "text");
            String text = (textNodes.getLength() > 0) ? textNodes.item(0).getTextContent().trim() : "";
            String role = elem.getAttribute("role");
            validationEvents.add(new SchematronValidationError(
                    text, elem.getAttribute("test"), elem.getAttribute("location"), 0, 0,
                    role != null && !role.isEmpty() ? role : "warning"));
        }
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

    private static class SimpleNamespaceContext implements NamespaceContext {
        private final Map<String, String> prefixToUri = new HashMap<>();

        public SimpleNamespaceContext(String prefix, String uri) {
            prefixToUri.put(prefix, uri);
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return prefixToUri.getOrDefault(prefix, javax.xml.XMLConstants.NULL_NS_URI);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            for (Map.Entry<String, String> entry : prefixToUri.entrySet()) {
                if (entry.getValue().equals(namespaceURI)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            String prefix = getPrefix(namespaceURI);
            return prefix != null ? Collections.singleton(prefix).iterator() : Collections.emptyIterator();
        }
    }
}
