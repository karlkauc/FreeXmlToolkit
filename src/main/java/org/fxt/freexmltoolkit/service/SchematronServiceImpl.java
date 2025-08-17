package org.fxt.freexmltoolkit.service;

import net.sf.saxon.s9api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
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
            // Compile the Schematron to XSLT
            XsltExecutable schematronXslt = compileSchematronToXslt(schematronFile);
            if (schematronXslt == null) {
                errors.add(new SchematronValidationError(
                        "Failed to compile Schematron file: " + schematronFile.getAbsolutePath(),
                        null,
                        null,
                        0,
                        0,
                        "error"
                ));
                return errors;
            }

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
     *
     * @param schematronFile The Schematron file to compile
     * @return The compiled XSLT executable or null if compilation fails
     */
    private XsltExecutable compileSchematronToXslt(File schematronFile) {
        try {
            // For now, we'll use a simplified approach
            // In a full implementation, you would need to download and use the standard Schematron XSLT files
            // to compile the Schematron to XSLT

            // This is a placeholder - in a real implementation, you would:
            // 1. Download the standard Schematron XSLT files
            // 2. Apply the DSDL include transformation
            // 3. Apply the abstract expand transformation  
            // 4. Apply the SVRL transformation

            logger.warn("Schematron compilation not fully implemented. Using placeholder.");
            return null;

        } catch (Exception e) {
            logger.error("Error compiling Schematron to XSLT", e);
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
