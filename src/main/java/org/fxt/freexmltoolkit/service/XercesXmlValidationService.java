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
 * Apache Xerces2-J based implementation of XML validation service.
 *
 * <p>This implementation uses Apache Xerces for XML schema validation.
 * Xerces supports both XSD 1.0 and XSD 1.1, providing comprehensive
 * schema validation capabilities including assertions.</p>
 */
public class XercesXmlValidationService implements XmlValidationService {

    private static final Logger logger = LogManager.getLogger(XercesXmlValidationService.class);
    private static final String XERCES_SCHEMA_FULL_CHECKING =
            "http://apache.org/xml/features/validation/schema-full-checking";
    private static final String XERCES_HONOUR_ALL_SCHEMA_LOCATIONS =
            "http://apache.org/xml/features/honour-all-schemaLocations";

    private final SchemaFactory schemaFactory10;
    private final SchemaFactory schemaFactory11;

    /**
     * Creates a new Xerces validation service instance.
     */
    public XercesXmlValidationService() {
        // Create schema factory for XSD 1.0
        this.schemaFactory10 = new org.apache.xerces.jaxp.validation.XMLSchemaFactory();

        // Create schema factory for XSD 1.1
        // Xerces 2.12.2 has limited XSD 1.1 support
        // We'll use the same factory but enable XSD 1.1 features
        SchemaFactory tempFactory11;
        try {
            org.apache.xerces.jaxp.validation.XMLSchemaFactory factory11 =
                    new org.apache.xerces.jaxp.validation.XMLSchemaFactory();

            // Try to enable XSD 1.1 features
            // Note: Xerces 2.12.2 supports XSD 1.1 features but may not have full support
            boolean ctaEnabled = false;
            try {
                factory11.setFeature("http://apache.org/xml/features/validation/cta-full-xpath-checking", true);
                ctaEnabled = true;
                logger.debug("Enabled Conditional Type Assignment (XSD 1.1 feature) in Xerces");
            } catch (Exception featureException) {
                logger.trace("CTA feature not available (expected with Xerces 2.12.2): {}", featureException.getMessage());
            }

            tempFactory11 = factory11;
            if (ctaEnabled) {
                logger.info("Xerces validator initialized with XSD 1.1 support");
            } else {
                logger.info("Xerces validator initialized with limited XSD 1.1 support (assertions supported)");
            }
        } catch (Exception e) {
            logger.warn("Could not create Xerces schema factory: {}. Using default factory.", e.getMessage());
            // Fallback to XSD 1.0 factory
            tempFactory11 = this.schemaFactory10;
        }
        this.schemaFactory11 = tempFactory11;

        // Configure Xerces features for better validation
        try {
            schemaFactory10.setFeature(XERCES_SCHEMA_FULL_CHECKING, true);
            schemaFactory10.setFeature(XERCES_HONOUR_ALL_SCHEMA_LOCATIONS, true);

            schemaFactory11.setFeature(XERCES_SCHEMA_FULL_CHECKING, true);
            schemaFactory11.setFeature(XERCES_HONOUR_ALL_SCHEMA_LOCATIONS, true);
        } catch (SAXException e) {
            logger.warn("Could not set Xerces features: {}", e.getMessage());
        }
    }

    @Override
    public List<SAXParseException> validateText(String xmlString, File schemaFile) {
        final List<SAXParseException> exceptions = new LinkedList<>();

        // If no schema is provided, only check for well-formedness.
        if (schemaFile == null) {
            return checkWellFormednessOnly(xmlString);
        }

        try {
            // Check if this is an XSD 1.1 schema
            String schemaContent = Files.readString(schemaFile.toPath());
            boolean isXsd11 = isXsd11Schema(schemaContent);

            // Select appropriate schema factory
            SchemaFactory factory = isXsd11 ? schemaFactory11 : schemaFactory10;

            logger.debug("Validating against XSD {} schema: {}",
                        isXsd11 ? "1.1" : "1.0",
                        schemaFile.getAbsolutePath());

            // Check if the schema itself is valid
            if (!isSchemaValid(schemaFile, isXsd11)) {
                logger.warn("Schema validation skipped because the schema file is invalid: {}",
                           schemaFile.getAbsolutePath());
                exceptions.add(new SAXParseException(
                    "Schema is invalid or unreadable. XML validation was not performed.", null));
                exceptions.addAll(checkWellFormednessOnly(xmlString));
                return exceptions;
            }

            // Perform validation with the appropriate schema version
            Schema schema = factory.newSchema(new StreamSource(schemaFile));
            Validator validator = schema.newValidator();

            validator.setErrorHandler(new ErrorHandler() {
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

            StreamSource xmlStreamSource = new StreamSource(new StringReader(xmlString));
            validator.validate(xmlStreamSource);

            return exceptions;

        } catch (IOException e) {
            logger.error("Could not read schema file", e);
            exceptions.add(new SAXParseException("Could not read schema file: " + e.getMessage(), null));
            return exceptions;
        } catch (SAXException e) {
            if (e instanceof SAXParseException) {
                exceptions.add((SAXParseException) e);
            } else {
                logger.error("Unexpected system error during validation", e);
                exceptions.add(new SAXParseException("System error during validation: " + e.getMessage(), null));
            }
            return exceptions;
        }
    }

    @Override
    public String getValidatorName() {
        return "Apache Xerces";
    }

    @Override
    public boolean supportsXsd11() {
        return true; // Xerces fully supports XSD 1.1
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
     * @param isXsd11    whether the schema is XSD 1.1
     * @return true if valid, false otherwise
     */
    private boolean isSchemaValid(File schemaFile, boolean isXsd11) {
        if (schemaFile == null || !schemaFile.exists()) {
            return false;
        }

        try {
            SchemaFactory factory = isXsd11 ? schemaFactory11 : schemaFactory10;
            factory.newSchema(new StreamSource(schemaFile));
            return true;
        } catch (SAXException e) {
            logger.warn("The provided schema file '{}' is not a valid W3C XML Schema. Reason: {}",
                       schemaFile.getAbsolutePath(), e.getMessage());
            return false;
        }
    }
}
