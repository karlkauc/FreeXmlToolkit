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
import org.fxt.freexmltoolkit.service.xsd.XsdParseOptions;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingService;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingServiceImpl;
import org.fxt.freexmltoolkit.service.xsd.SchemaResolver;
import org.w3c.dom.ls.LSResourceResolver;
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
    private static final String XERCES_XSD11_VERSION_PROPERTY =
            "http://apache.org/xml/properties/validation/schema/version";
    private static final String XERCES_CTA_FULL_XPATH_CHECKING =
            "http://apache.org/xml/features/validation/cta-full-xpath-checking";
    private static final String XSD_11_NAMESPACE = "http://www.w3.org/XML/XMLSchema/v1.1";
    private static final String XSD_11_VERSION = "http://www.w3.org/2009/XMLSchema/XMLSchema.xsd";
    private static final String XSD_10_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    private final SchemaFactory schemaFactory10;
    private final SchemaFactory schemaFactory11;
    private final SchemaResolver schemaResolver;
    private final SchemaResolver.ValidationResourceResolver resourceResolver;
    private final XsdParsingService xsdParsingService;

    /**
     * Creates a new Xerces validation service instance.
     */
    public XercesXmlValidationService() {
        // Debug: Check which Xerces version is loaded
        try {
            Package xercesPackage = org.apache.xerces.impl.Version.class.getPackage();
            if (xercesPackage != null) {
                logger.info("Xerces Implementation Version: {}", xercesPackage.getImplementationVersion());
                logger.info("Xerces Specification Version: {}", xercesPackage.getSpecificationVersion());
            }
            
            // Try to get actual Xerces version info
            String version = org.apache.xerces.impl.Version.getVersion();
            logger.info("Xerces Version Info: {}", version);
        } catch (Exception e) {
            logger.debug("Could not determine Xerces version: {}", e.getMessage());
        }
        
        // Create schema factory for XSD 1.0
        this.schemaFactory10 = new org.apache.xerces.jaxp.validation.XMLSchemaFactory();

        // Create schema factory for XSD 1.1
        // For XSD 1.1 support with assertions, we need to use the special Xerces XSD 1.1 implementation
        SchemaFactory tempFactory11;
        try {
            // First, try to create a schema factory with the XSD 1.1 namespace
            try {
                tempFactory11 = SchemaFactory.newInstance(XSD_11_NAMESPACE);
                logger.info("Created SchemaFactory using XSD 1.1 namespace: {}", XSD_11_NAMESPACE);
            } catch (IllegalArgumentException e) {
                // If XSD 1.1 namespace not recognized, use the standard Xerces factory
                // but set the schema version property to enable XSD 1.1 features
                logger.debug("XSD 1.1 namespace not recognized, using standard Xerces factory with version property");
                tempFactory11 = new org.apache.xerces.jaxp.validation.XMLSchemaFactory();
                
                // Try to set the XSD version property to enable XSD 1.1 features
                try {
                    tempFactory11.setProperty(XERCES_XSD11_VERSION_PROPERTY, "1.1");
                    logger.info("Set Xerces schema version to 1.1 - XSD 1.1 features should be enabled");
                } catch (Exception versionException) {
                    logger.warn("Could not set XSD version property: {}", versionException.getMessage());
                }
                
                logger.info("Created Xerces SchemaFactory with XSD 1.1 configuration attempt");
            }

            // Try to enable CTA (Conditional Type Assignment) full XPath checking for XSD 1.1
            try {
                tempFactory11.setFeature(XERCES_CTA_FULL_XPATH_CHECKING, true);
                logger.debug("Enabled Conditional Type Assignment (CTA) - XSD 1.1 feature");
            } catch (Exception featureException) {
                logger.debug("CTA feature not available: {}", featureException.getMessage());
            }

            // Try to set other XSD 1.1 related properties if supported
            try {
                // Alternative property name that might be supported
                tempFactory11.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation", "");
                logger.debug("Set schema location property");
            } catch (Exception propException) {
                logger.trace("Schema location property not available: {}", propException.getMessage());
            }

        } catch (Exception e) {
            logger.warn("Could not create Xerces XSD 1.1 schema factory: {}. Using XSD 1.0 factory.", e.getMessage());
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

        // Configure unified schema resolver to handle schema references (xs:import, xs:include)
        // Supports local files, remote URLs (HTTP/HTTPS with caching), and circular import detection
        this.schemaResolver = new SchemaResolver(XsdParseOptions.defaults());
        this.resourceResolver = (SchemaResolver.ValidationResourceResolver) schemaResolver.createLSResourceResolver(null);
        schemaFactory10.setResourceResolver(resourceResolver);
        schemaFactory11.setResourceResolver(resourceResolver);

        // Initialize the unified XsdParsingService for schema parsing
        this.xsdParsingService = new XsdParsingServiceImpl();
    }

    /**
     * Gets the XSD parsing service used by this validation service.
     *
     * @return the XSD parsing service
     */
    public XsdParsingService getXsdParsingService() {
        return xsdParsingService;
    }

    /**
     * Gets the schema resource resolver used by this service.
     *
     * @return the schema resource resolver as LSResourceResolver
     */
    public LSResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    /**
     * Gets the unified schema resolver used by this service.
     *
     * @return the schema resolver
     */
    public SchemaResolver getSchemaResolver() {
        return schemaResolver;
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

            // Check if XSD 1.1 is requested but not supported
            if (isXsd11 && !supportsXsd11()) {
                logger.warn("XSD 1.1 schema detected but not supported by current Xerces version. Attempting XSD 1.0 validation instead.");
                exceptions.add(new SAXParseException(
                    "XSD 1.1 features (like assertions) are not supported by the current Xerces version. " +
                    "Validation performed as XSD 1.0 - assertions and other XSD 1.1 features will be ignored.", null));
                // Use XSD 1.0 factory for graceful degradation
                SchemaFactory factory = schemaFactory10;
                
                logger.debug("Validating XSD 1.1 schema as XSD 1.0: {}", schemaFile.getAbsolutePath());
                
                // Don't pre-validate since XSD 1.0 validator might reject XSD 1.1 syntax
                // Set systemId to enable relative import resolution
                StreamSource schemaSource = new StreamSource(schemaFile);
                schemaSource.setSystemId(schemaFile.toURI().toString());
                Schema schema = factory.newSchema(schemaSource);
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
            }

            // Use appropriate factory based on XSD version
            SchemaFactory factory = isXsd11 ? schemaFactory11 : schemaFactory10;

            logger.debug("Validating against XSD {} schema: {}",
                        isXsd11 ? "1.1" : "1.0",
                        schemaFile.getAbsolutePath());

            // For XSD 1.0 schemas, pre-validate the schema
            if (!isXsd11) {
                String schemaError = getSchemaValidationError(schemaFile, false);
                if (schemaError != null) {
                    logger.warn("Schema validation skipped because the schema file is invalid: {}",
                               schemaFile.getAbsolutePath());
                    exceptions.add(new SAXParseException(schemaError, null));
                    exceptions.addAll(checkWellFormednessOnly(xmlString));
                    return exceptions;
                }
            }

            // Perform validation with the appropriate schema version
            // The factory already supports the correct XSD version
            // Set systemId to enable relative import resolution
            StreamSource schemaSource = new StreamSource(schemaFile);
            schemaSource.setSystemId(schemaFile.toURI().toString());
            Schema schema = factory.newSchema(schemaSource);
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
        // Test if the current Xerces version actually supports XSD 1.1 assertions
        return testXsd11AssertionSupport();
    }
    
    /**
     * Tests if the current Xerces implementation actually supports XSD 1.1 assertions.
     * This is done by trying to parse a simple XSD 1.1 schema with an assertion.
     */
    private boolean testXsd11AssertionSupport() {
        String testSchema = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning"
                       vc:minVersion="1.1">
                <xs:element name="test">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="value" type="xs:int"/>
                        </xs:sequence>
                        <xs:assert test="value > 0"/>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;
            
        try {
            // Try to create a schema with XSD 1.1 assertions using the XSD 1.1 factory
            schemaFactory11.newSchema(new StreamSource(new StringReader(testSchema)));
            logger.info("XSD 1.1 assertion support confirmed");
            return true;
        } catch (Exception e) {
            if (e.getMessage() != null && 
                (e.getMessage().contains("assert") || e.getMessage().contains("invalid") || 
                 e.getMessage().contains("misplaced"))) {
                logger.warn("XSD 1.1 assertions not supported by current Xerces version: {}", e.getMessage());
                return false;
            } else {
                // Other error, might still support XSD 1.1 but test schema had issues
                logger.debug("XSD 1.1 test failed with unexpected error: {}", e.getMessage());
                return false;
            }
        }
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
            DocumentBuilderFactory dbf = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
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
    /**
     * Checks if the schema file is valid and returns an error message if not.
     *
     * @param schemaFile the schema file to validate
     * @param isXsd11    whether this is an XSD 1.1 schema
     * @return null if valid, or detailed error message if invalid
     */
    private String getSchemaValidationError(File schemaFile, boolean isXsd11) {
        if (schemaFile == null || !schemaFile.exists()) {
            return "Schema file does not exist or is null";
        }

        try {
            SchemaFactory factory = isXsd11 ? schemaFactory11 : schemaFactory10;

            // The factory already supports the correct XSD version
            // Set systemId to enable relative import resolution
            StreamSource schemaSource = new StreamSource(schemaFile);
            schemaSource.setSystemId(schemaFile.toURI().toString());
            factory.newSchema(schemaSource);
            return null; // Schema is valid
        } catch (SAXException e) {
            String errorMsg = "Schema validation error: " + e.getMessage();
            logger.warn("The provided schema file '{}' is not a valid W3C XML Schema. Reason: {}",
                       schemaFile.getAbsolutePath(), e.getMessage());
            return errorMsg;
        }
    }
}
