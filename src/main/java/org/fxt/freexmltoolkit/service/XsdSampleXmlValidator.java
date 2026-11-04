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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.XsdDocumentationService.ValidationError;
import org.fxt.freexmltoolkit.service.XsdDocumentationService.ValidationResult;
import org.fxt.freexmltoolkit.util.PathValidator;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.LSInput;

/**
 * Validates generated XML against the schema of an {@link XsdDocumentationService}: XSD 1.0 or, for schemas declaring
 * {@code vc:minVersion="1.1"}, XSD 1.1, with local-only resolution of imports and includes.
 */
final class XsdSampleXmlValidator {

    private static final Logger logger = LogManager.getLogger(XsdSampleXmlValidator.class);

    private XsdSampleXmlValidator() {
    }

    /**
     * Validates the generated XML against the XSD schema.
     * Automatically detects XSD 1.1 schemas and uses appropriate validation.
     * @param xmlContent The XML content to validate
     * @param xsdFilePath the schema file
     * @param doc         the parsed main schema, or {@code null} when it is not loaded yet
     * @return Validation result with success status and any error messages
     */
    static ValidationResult validate(String xmlContent, String xsdFilePath, Document doc) {
        try {
            // Determine if schema requires XSD 1.1
            boolean requiresXsd11 = isXsd11Schema(doc, xsdFilePath);

            // Create appropriate schema factory
            SchemaFactory factory;
            if (requiresXsd11) {
                // Use Xerces XSD 1.1 factory
                factory = SecureXmlFactory.createSecureSchemaFactory(
                        new org.apache.xerces.jaxp.validation.XMLSchema11Factory());
                logger.debug("Using XSD 1.1 schema factory for validation");
            } else {
                factory = SecureXmlFactory.createSecureSchemaFactory(
                        javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
                logger.debug("Using XSD 1.0 schema factory for validation");
            }

            // Set up resource resolver for imports/includes
            File schemaFile = new File(xsdFilePath);
            File schemaDir = schemaFile.getParentFile();
            // SECURITY: block remote (http/https/ftp) schema references to prevent SSRF; this guard
            // throws on a remote reference, answers a DTD with an empty document and returns null
            // for other local references.
            final org.w3c.dom.ls.LSResourceResolver remoteGuard = SecureXmlFactory.createLocalOnlySchemaResolver();
            factory.setResourceResolver(new org.w3c.dom.ls.LSResourceResolver() {
                @Override
                public org.w3c.dom.ls.LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
                    org.w3c.dom.ls.LSInput guarded = remoteGuard.resolveResource(type, namespaceURI, publicId, systemId, baseURI);
                    if (guarded != null) {
                        return guarded;
                    }
                    if (systemId != null && schemaDir != null) {
                        // SECURITY: Validate systemId before resolving
                        int traversalCount = PathValidator.countParentTraversals(systemId);
                        if (traversalCount > 5) {
                            logger.warn("SECURITY: Rejected schema import with excessive traversal: {}", systemId);
                            return null;
                        }

                        File resolvedFile = new File(schemaDir, systemId);
                        try {
                            File canonicalFile = resolvedFile.getCanonicalFile();

                            // SECURITY: Check for system directory access
                            if (!PathValidator.isOutputPathSafe(canonicalFile.getAbsolutePath())) {
                                logger.warn("SECURITY: Rejected schema import targeting system directory: {}", systemId);
                                return null;
                            }

                            if (canonicalFile.exists()) {
                                return new LSInputImpl(publicId, systemId, canonicalFile);
                            }
                        } catch (Exception e) {
                            logger.debug("Could not resolve resource: {}", systemId);
                        }
                    }
                    return null;
                }
            });

            // Load the schema
            Source schemaSource = new StreamSource(schemaFile);
            Schema schema = factory.newSchema(schemaSource);

            // Create validator
            Validator validator = schema.newValidator();

            // Create error handler to collect validation errors
            List<ValidationError> errors = new ArrayList<>();
            final int[] errorCount = {0};
            final int[] warningCount = {0};

            validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException exception) {
                    warningCount[0]++;
                    errors.add(new ValidationError(
                            exception.getLineNumber(),
                            exception.getColumnNumber(),
                            "Warning",
                            exception.getMessage()
                    ));
                }

                @Override
                public void error(org.xml.sax.SAXParseException exception) {
                    errorCount[0]++;
                    errors.add(new ValidationError(
                            exception.getLineNumber(),
                            exception.getColumnNumber(),
                            "Error",
                            exception.getMessage()
                    ));
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException exception) {
                    errorCount[0]++;
                    errors.add(new ValidationError(
                            exception.getLineNumber(),
                            exception.getColumnNumber(),
                            "Fatal Error",
                            exception.getMessage()
                    ));
                }
            });

            // Validate the XML
            Source xmlSource = new StreamSource(new StringReader(xmlContent));
            validator.validate(xmlSource);

            // Determine if validation passed (no errors, warnings are OK)
            boolean isValid = errorCount[0] == 0;
            String message;

            if (isValid && warningCount[0] > 0) {
                message = warningCount[0] + " warning(s) found";
            } else if (!isValid) {
                message = errorCount[0] + " error(s) found";
            } else {
                message = "Validation successful";
            }

            return new ValidationResult(isValid, message, errors);

        } catch (Exception e) {
            return new ValidationResult(false, "Validation failed: " + e.getMessage(),
                    List.of(new ValidationError(0, 0, "Fatal Error", e.getMessage())));
        }
    }

    // --- Schema Version Detection ---

    /**
     * Checks if the loaded schema requires XSD 1.1.
     * Looks for vc:minVersion="1.1" attribute on the schema element.
     */
    private static boolean isXsd11Schema(Document doc, String xsdFilePath) {
        // If doc is not loaded yet, try to parse the schema file to check version
        Document schemaDoc = doc;
        if (schemaDoc == null && xsdFilePath != null) {
            try {
                DocumentBuilderFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureDocumentBuilderFactory();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                schemaDoc = builder.parse(new File(xsdFilePath));
            } catch (Exception e) {
                logger.debug("Could not parse schema file to check version: {}", e.getMessage());
                return false;
            }
        }

        if (schemaDoc == null) {
            return false;
        }
        Element root = schemaDoc.getDocumentElement();
        if (root == null) {
            return false;
        }

        // Check for vc:minVersion attribute
        String minVersion = root.getAttributeNS("http://www.w3.org/2007/XMLSchema-versioning", "minVersion");
        if ("1.1".equals(minVersion)) {
            return true;
        }

        // Also check without namespace (some schemas use it without proper namespace)
        minVersion = root.getAttribute("vc:minVersion");
        return "1.1".equals(minVersion);
    }

    /**
         * Simple LSInput implementation for resource resolution.
         */
        private record LSInputImpl(String publicId, String systemId, File file) implements LSInput {

        @Override
            public Reader getCharacterStream() {
                try {
                    return new FileReader(file, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            public void setCharacterStream(Reader characterStream) {
            }

            @Override
            public InputStream getByteStream() {
                try {
                    return new FileInputStream(file);
                } catch (Exception e) {
                    return null;
                }
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
                return file.toURI().toString();
            }

            @Override
            public void setBaseURI(String baseURI) {
            }

            @Override
            public String getEncoding() {
                return "UTF-8";
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
}
