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
import org.w3c.dom.ls.LSInput;
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
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final String XSD_11_CONSTANTS = "http://www.w3.org/XML/XMLSchema/v1.1";
    private static final String XSD_10_CONSTANTS = "http://www.w3.org/TR/XMLSchema/v1.0";

    private final SchemaFactory schemaFactory10;
    private final SchemaFactory schemaFactory11;

    /**
     * Creates a new Xerces validation service instance.
     */
    public XercesXmlValidationService() {
        // Create schema factory for XSD 1.0
        this.schemaFactory10 = new org.apache.xerces.jaxp.validation.XMLSchemaFactory();

        // Create schema factory for XSD 1.1
        // Xerces uses the same XMLSchemaFactory for both XSD 1.0 and 1.1
        // The difference is in the properties/features set on the factory
        SchemaFactory tempFactory11;
        try {
            // Try creating with XSD 1.1 namespace first
            try {
                tempFactory11 = SchemaFactory.newInstance(XSD_11_CONSTANTS);
                logger.debug("Created SchemaFactory using XSD 1.1 namespace URI");
            } catch (IllegalArgumentException e) {
                // If XSD 1.1 URI is not recognized, create using Xerces directly
                logger.debug("XSD 1.1 URI not recognized, creating Xerces factory directly");
                tempFactory11 = new org.apache.xerces.jaxp.validation.XMLSchemaFactory();
            }

            // Set XSD 1.1 version property
            try {
                tempFactory11.setProperty(XERCES_XSD11_VERSION_PROPERTY, XSD_11_CONSTANTS);
                logger.info("Xerces validator initialized with full XSD 1.1 support (including assertions)");
            } catch (SAXException propException) {
                // Try alternative property value
                try {
                    tempFactory11.setProperty(XERCES_XSD11_VERSION_PROPERTY, "http://www.w3.org/2009/XMLSchema/XMLSchema.xsd");
                    logger.info("Xerces validator initialized with XSD 1.1 using alternative property");
                } catch (Exception altException) {
                    logger.warn("Could not set XSD 1.1 version property: {}. Validation may use XSD 1.0 only.",
                               altException.getMessage());
                }
            }

            // Try to enable additional XSD 1.1 features
            try {
                tempFactory11.setFeature(XERCES_CTA_FULL_XPATH_CHECKING, true);
                logger.debug("Enabled Conditional Type Assignment (CTA) - XSD 1.1 feature");
            } catch (Exception featureException) {
                logger.trace("CTA feature not available: {}", featureException.getMessage());
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

        // Configure resource resolver to handle relative schema references
        LSResourceResolver resourceResolver = createResourceResolver();
        schemaFactory10.setResourceResolver(resourceResolver);
        schemaFactory11.setResourceResolver(resourceResolver);
    }

    /**
     * Creates a resource resolver that can resolve relative schema references.
     * This resolver looks for schema files in the same directory as the main schema file.
     *
     * @return LSResourceResolver instance
     */
    private LSResourceResolver createResourceResolver() {
        return new LSResourceResolver() {
            @Override
            public LSInput resolveResource(String type, String namespaceURI, String publicId,
                                          String systemId, String baseURI) {
                logger.debug("Resolving resource - type: {}, namespace: {}, systemId: {}, baseURI: {}",
                           type, namespaceURI, systemId, baseURI);

                if (systemId == null) {
                    return null;
                }

                try {
                    // Try to resolve relative path based on baseURI
                    Path resolvedPath;
                    if (baseURI != null && !baseURI.isEmpty()) {
                        // Convert file:// URI to path
                        String basePath = baseURI.startsWith("file://")
                            ? baseURI.substring(7)
                            : baseURI.startsWith("file:")
                                ? baseURI.substring(5)
                                : baseURI;

                        Path baseDir = Paths.get(basePath).getParent();
                        if (baseDir != null) {
                            resolvedPath = baseDir.resolve(systemId).normalize();
                        } else {
                            resolvedPath = Paths.get(systemId);
                        }
                    } else {
                        resolvedPath = Paths.get(systemId);
                    }

                    if (Files.exists(resolvedPath)) {
                        logger.debug("Resolved schema reference to: {}", resolvedPath);
                        return new LSInputImpl(publicId, systemId, Files.newInputStream(resolvedPath));
                    } else {
                        logger.warn("Could not resolve schema reference: {}", resolvedPath);
                    }
                } catch (Exception e) {
                    logger.warn("Error resolving resource {}: {}", systemId, e.getMessage());
                }

                return null;
            }
        };
    }

    /**
         * Simple implementation of LSInput for schema resolution.
         */
        private record LSInputImpl(String publicId, String systemId, InputStream byteStream) implements LSInput {

        @Override
            public Reader getCharacterStream() {
                return null;
            }

            @Override
            public void setCharacterStream(Reader characterStream) {
            }

            @Override
            public InputStream getByteStream() {
                return byteStream;
            }

            @Override
            public void setByteStream(InputStream byteStream) {
            }

            @Override
            public String getStringData() {
                return null;
            }

            @Override
            public void setStringData(String stringData) {
            }

            @Override
            public String getSystemId() {
                return systemId;
            }

            @Override
            public void setSystemId(String systemId) {
            }

            @Override
            public String getPublicId() {
                return publicId;
            }

            @Override
            public void setPublicId(String publicId) {
            }

            @Override
            public String getBaseURI() {
                return null;
            }

            @Override
            public void setBaseURI(String baseURI) {
            }

            @Override
            public String getEncoding() {
                return null;
            }

            @Override
            public void setEncoding(String encoding) {
            }

            @Override
            public boolean getCertifiedText() {
                return false;
            }

            @Override
            public void setCertifiedText(boolean certifiedText) {
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
            String schemaError = getSchemaValidationError(schemaFile, isXsd11);
            if (schemaError != null) {
                logger.warn("Schema validation skipped because the schema file is invalid: {}",
                           schemaFile.getAbsolutePath());
                exceptions.add(new SAXParseException(schemaError, null));
                exceptions.addAll(checkWellFormednessOnly(xmlString));
                return exceptions;
            }

            // Perform validation with the appropriate schema version
            // The factory already supports the correct XSD version
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
            factory.newSchema(new StreamSource(schemaFile));
            return null; // Schema is valid
        } catch (SAXException e) {
            String errorMsg = "Schema validation error: " + e.getMessage();
            logger.warn("The provided schema file '{}' is not a valid W3C XML Schema. Reason: {}",
                       schemaFile.getAbsolutePath(), e.getMessage());
            return errorMsg;
        }
    }
}
