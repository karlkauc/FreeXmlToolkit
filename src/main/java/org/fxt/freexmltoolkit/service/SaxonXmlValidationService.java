/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;

/**
 * Saxon-HE based implementation of XML validation service.
 *
 * <p>This implementation uses Saxon's SchemaFactory for XSD 1.0 validation.
 * XSD 1.1 features are not fully supported in Saxon-HE and will fall back to
 * well-formedness checking only.</p>
 */
public class SaxonXmlValidationService implements XmlValidationService {

    private static final Logger logger = LogManager.getLogger(SaxonXmlValidationService.class);
    private final SchemaFactory factory;
    private final SchemaResourceResolver resourceResolver;

    /**
     * Creates a new Saxon validation service instance.
     */
    public SaxonXmlValidationService() {
        this.resourceResolver = new SchemaResourceResolver();
        this.factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        // Configure resource resolver to handle schema references (xs:import, xs:include)
        // Supports local files, remote URLs (HTTP/HTTPS with caching), and circular import detection
        this.factory.setResourceResolver(resourceResolver);
    }

    /**
     * Gets the schema resource resolver used by this service.
     *
     * @return the schema resource resolver
     */
    public SchemaResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    @Override
    public List<SAXParseException> validateText(String xmlString, File schemaFile) {
        final List<SAXParseException> exceptions = new LinkedList<>();

        // Reset circular detection for new validation
        resourceResolver.resetCircularDetection();

        // If no schema is provided, only check for well-formedness.
        if (schemaFile == null) {
            return checkWellFormednessOnly(xmlString);
        }

        try {
            // Check if this is an XSD 1.1 schema
            String schemaContent = Files.readString(schemaFile.toPath());
            boolean isXsd11 = isXsd11Schema(schemaContent);

            if (isXsd11) {
                // XSD 1.1 validation is not fully supported with Saxon-HE
                // We can only check well-formedness of the XML
                logger.info("XSD 1.1 schema detected. Full schema validation is not available with Saxon-HE. Checking XML well-formedness only.");
                exceptions.add(new SAXParseException(
                    "Note: XSD 1.1 features detected in schema (e.g., assertions, type alternatives). " +
                    "Full schema validation requires Saxon-EE or Saxon-PE. Only checking XML well-formedness.",
                    null, null, -1, -1));
                exceptions.addAll(checkWellFormednessOnly(xmlString));
                return exceptions;
            }

            // Check if the schema itself is valid (for XSD 1.0)
            if (!isSchemaValid(schemaFile)) {
                logger.warn("Schema validation skipped because the schema file is invalid: {}", schemaFile.getAbsolutePath());
                // Add a custom error to inform the user about the invalid schema.
                exceptions.add(new SAXParseException("Schema is invalid or unreadable. XML validation was not performed.", null));
                // As a fallback, at least check if the XML itself is well-formed.
                exceptions.addAll(checkWellFormednessOnly(xmlString));
                return exceptions;
            }

            // If the schema is valid XSD 1.0, proceed with full validation
            logger.debug("Validating against XSD 1.0 schema: {}", schemaFile.getAbsolutePath());
            // Create StreamSource with systemId set to enable relative import resolution
            StreamSource schemaSource = new StreamSource(schemaFile);
            schemaSource.setSystemId(schemaFile.toURI().toString());
            Schema schemaToUse = factory.newSchema(schemaSource);
            Validator localValidator = schemaToUse.newValidator();

            localValidator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    exceptions.add(exception);
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    exceptions.add(exception);
                }

                @Override
                public void error(SAXParseException exception) {
                    exceptions.add(exception);
                }
            });

            // The validate method checks for well-formedness and, if a schema is loaded, for schema validity.
            StreamSource xmlStreamSource = new StreamSource(new StringReader(xmlString));
            localValidator.validate(xmlStreamSource);

            return exceptions;

        } catch (IOException e) {
            logger.error("Could not read schema file", e);
            exceptions.add(new SAXParseException("Could not read schema file: " + e.getMessage(), null));
            return exceptions;
        } catch (SAXException e) {
            // A SAXException here is often a fatal parsing error (e.g., not well-formed).
            // We add it to the error list to report it to the user.
            if (e instanceof SAXParseException) {
                exceptions.add((SAXParseException) e);
            } else {
                // For other system errors, create a synthetic exception.
                logger.error("Unexpected system error during validation", e);
                exceptions.add(new SAXParseException("System error during validation: " + e.getMessage(), null));
            }
            return exceptions;
        }
    }

    @Override
    public String getValidatorName() {
        return "Saxon-HE";
    }

    @Override
    public boolean supportsXsd11() {
        return false; // Saxon-HE does not fully support XSD 1.1
    }

    /**
     * Checks if the given XML string is well-formed without schema validation.
     *
     * @param xmlString the XML content to check
     * @return list of well-formedness errors (empty if well-formed)
     */
    private List<SAXParseException> checkWellFormednessOnly(String xmlString) {
        final List<SAXParseException> exceptions = new LinkedList<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setValidating(false); // Disable DTD validation
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();

            db.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) { /* Ignore warnings for well-formedness check */ }

                @Override
                public void error(SAXParseException e) {
                    exceptions.add(e);
                }

                @Override
                public void fatalError(SAXParseException e) {
                    exceptions.add(e);
                }
            });

            db.parse(new org.xml.sax.InputSource(new StringReader(xmlString)));

        } catch (SAXException | IOException | ParserConfigurationException e) {
            if (e instanceof SAXParseException) {
                exceptions.add((SAXParseException) e);
            } else {
                exceptions.add(new SAXParseException("Error checking well-formedness: " + e.getMessage(), null));
            }
        }
        return exceptions;
    }

    /**
     * Checks if the schema content contains XSD 1.1 features.
     *
     * @param schemaContent the schema content as string
     * @return true if XSD 1.1 features are detected
     */
    private boolean isXsd11Schema(String schemaContent) {
        if (schemaContent == null || schemaContent.isBlank()) {
            return false;
        }

        // XSD 1.1 specific elements and attributes
        String[] xsd11Features = {
            "<xs:assert", "<xsd:assert", // assertions
            "<xs:alternative", "<xsd:alternative", // type alternatives
            "<xs:openContent", "<xsd:openContent", // open content
            "vc:minVersion=\"1.1\"", // version declaration
            "explicitTimezone=" // XSD 1.1 facet
        };

        for (String feature : xsd11Features) {
            if (schemaContent.contains(feature)) {
                logger.debug("Detected XSD 1.1 feature: {}", feature);
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if the given schema file is valid.
     *
     * @param schemaFile the schema file to check
     * @return true if valid, false otherwise
     */
    private boolean isSchemaValid(File schemaFile) {
        if (schemaFile == null || !schemaFile.exists()) {
            return false;
        }

        try {
            String schemaContent = Files.readString(schemaFile.toPath());

            if (isXsd11Schema(schemaContent)) {
                // For XSD 1.1, just verify it's well-formed XML
                logger.debug("Detected XSD 1.1 schema: {}", schemaFile.getAbsolutePath());
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(true);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    db.parse(schemaFile);
                    logger.debug("XSD 1.1 schema is well-formed XML");
                    return true;
                } catch (Exception e) {
                    logger.warn("XSD 1.1 schema is not well-formed XML: {}", e.getMessage());
                    return false;
                }
            }

            // For XSD 1.0, use the standard validation
            // Set systemId to enable relative import resolution during schema validation
            StreamSource schemaSource = new StreamSource(schemaFile);
            schemaSource.setSystemId(schemaFile.toURI().toString());
            factory.newSchema(schemaSource);
            return true;
        } catch (IOException e) {
            logger.error("Could not read schema file: {}", schemaFile.getAbsolutePath(), e);
            return false;
        } catch (SAXException e) {
            logger.warn("The provided schema file '{}' is not a valid W3C XML Schema. Reason: {}",
                       schemaFile.getAbsolutePath(), e.getMessage());
            return false;
        }
    }
}
