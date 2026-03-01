package org.fxt.freexmltoolkit.service;

import javafx.concurrent.Task;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.xsd.XsdParseOptions;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingService;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingServiceImpl;
import org.fxt.freexmltoolkit.service.xsd.SchemaResolver;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced XSD Validation Service with real-time validation, schema discovery,
 * and performance optimization for large XML files.
 */
public class XsdValidationService {

    private static final Logger logger = LogManager.getLogger(XsdValidationService.class);

    // Singleton instance
    private static XsdValidationService instance;

    // Schema cache for performance optimization
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();
    private final Map<String, Long> schemaCacheTimestamps = new ConcurrentHashMap<>();

    // Validation settings
    private boolean realTimeValidationEnabled = true;
    private boolean enableSchemaCache = true;
    private long schemaCacheTimeout = 300000; // 5 minutes in milliseconds
    private int maxErrorCount = 100; // Limit errors for performance
    private boolean validationOnSaveEnabled = true;

    // Performance settings
    private static final int LARGE_FILE_THRESHOLD = 5242880; // 5MB in bytes

    // Schema discovery patterns
    private static final Pattern XSD_SCHEMA_LOCATION_PATTERN = Pattern.compile(
            "xsi:schemaLocation\\s*=\\s*[\"']([^\"']*)[\"']"
    );
    private static final Pattern XSD_NO_NAMESPACE_SCHEMA_LOCATION_PATTERN = Pattern.compile(
            "xsi:noNamespaceSchemaLocation\\s*=\\s*[\"']([^\"']*)[\"']"
    );
    private static final Pattern TARGET_NAMESPACE_PATTERN = Pattern.compile(
            "targetNamespace\\s*=\\s*[\"']([^\"']*)[\"']"
    );

    // Built-in schema paths for common XML types
    private final Map<String, String> commonSchemas = new HashMap<>();

    // Unified XSD parsing service
    private final XsdParsingService xsdParsingService;

    /**
     * Returns the singleton instance of XsdValidationService.
     *
     * @return The singleton instance
     */
    public static synchronized XsdValidationService getInstance() {
        if (instance == null) {
            instance = new XsdValidationService();
        }
        return instance;
    }

    private XsdValidationService() {
        initializeCommonSchemas();
        this.xsdParsingService = new XsdParsingServiceImpl();
        logger.info("XSD Validation Service initialized with caching enabled");
    }


    /**
     * Initialize common schema mappings
     */
    private void initializeCommonSchemas() {
        // Add common XML schema mappings
        commonSchemas.put("http://www.w3.org/2001/XMLSchema", "XMLSchema.xsd");
        commonSchemas.put("http://www.w3.org/XML/1998/namespace", "xml.xsd");
        commonSchemas.put("http://www.w3.org/1999/xhtml", "xhtml1-transitional.xsd");
        commonSchemas.put("http://www.springframework.org/schema/beans", "spring-beans.xsd");
        commonSchemas.put("http://maven.apache.org/POM/4.0.0", "maven-4.0.0.xsd");
        commonSchemas.put("http://www.w3.org/2000/svg", "SVG.xsd");

        logger.debug("Initialized {} common schema mappings", commonSchemas.size());
    }

    // ========== Main Validation Methods ==========

    /**
     * Validate XML document with comprehensive error reporting
     */
    public XmlValidationResult validateXml(String xmlContent) {
        return validateXml(xmlContent, null);
    }

    /**
     * Validate XML document against specific XSD schema
     */
    public XmlValidationResult validateXml(String xmlContent, String xsdPath) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return XmlValidationResult.createEmpty("No XML content to validate");
        }

        long startTime = System.currentTimeMillis();
        XmlValidationResult result = new XmlValidationResult();
        result.setValidationStartTime(LocalDateTime.now());

        try {
            // Discover schema if not provided
            if (xsdPath == null || xsdPath.trim().isEmpty()) {
                xsdPath = discoverSchema(xmlContent);
                result.setAutoDiscoveredSchema(xsdPath);
            }

            if (xsdPath != null) {
                result = performXsdValidation(xmlContent, xsdPath);
            } else {
                result = performWellFormednessValidation(xmlContent);
                result.addWarning(new XmlValidationError(
                        XmlValidationError.ErrorType.WARNING,
                        0, 0,
                        "No XSD schema found - only well-formedness validation performed",
                        "Consider adding xsi:schemaLocation or xsi:noNamespaceSchemaLocation"
                ));
            }

        } catch (Exception e) {
            logger.error("Validation failed with exception", e);
            result.addError(new XmlValidationError(
                    XmlValidationError.ErrorType.FATAL,
                    0, 0,
                    "Validation engine error: " + e.getMessage(),
                    "Check XML syntax and schema references"
            ));
        }

        long duration = System.currentTimeMillis() - startTime;
        result.setValidationDuration(duration);

        logger.debug("XML validation completed in {}ms - Valid: {}, Errors: {}, Warnings: {}",
                duration, result.isValid(), result.getErrors().size(), result.getWarnings().size());

        return result;
    }

    /**
     * Validate XML asynchronously for large files
     */
    public Task<XmlValidationResult> validateXmlAsync(String xmlContent, String xsdPath) {
        return new Task<XmlValidationResult>() {
            @Override
            protected XmlValidationResult call() throws Exception {
                updateMessage("Starting XML validation...");
                updateProgress(0, 1);

                XmlValidationResult result;

                if (xmlContent.length() > LARGE_FILE_THRESHOLD) {
                    updateMessage("Processing large XML file...");
                    result = validateLargeXml(xmlContent, xsdPath);
                } else {
                    updateMessage("Validating XML content...");
                    result = validateXml(xmlContent, xsdPath);
                }

                updateProgress(1, 1);
                updateMessage("XML validation completed");

                return result;
            }
        };
    }

    // ========== Schema Discovery ==========

    /**
     * Discover XSD schema from XML content
     */
    public String discoverSchema(String xmlContent) {
        try {
            // Look for schemaLocation attribute
            Matcher schemaLocationMatcher = XSD_SCHEMA_LOCATION_PATTERN.matcher(xmlContent);
            if (schemaLocationMatcher.find()) {
                String schemaLocation = schemaLocationMatcher.group(1);
                String[] parts = schemaLocation.trim().split("\\s+");
                if (parts.length >= 2) {
                    String schemaUrl = parts[parts.length - 1]; // Last part is usually the schema URL
                    logger.debug("Discovered schema from schemaLocation: {}", schemaUrl);
                    return resolveSchemaPath(schemaUrl);
                }
            }

            // Look for noNamespaceSchemaLocation
            Matcher noNamespaceSchemaLocationMatcher = XSD_NO_NAMESPACE_SCHEMA_LOCATION_PATTERN.matcher(xmlContent);
            if (noNamespaceSchemaLocationMatcher.find()) {
                String schemaUrl = noNamespaceSchemaLocationMatcher.group(1);
                logger.debug("Discovered schema from noNamespaceSchemaLocation: {}", schemaUrl);
                return resolveSchemaPath(schemaUrl);
            }

            // Look for target namespace and try to match with common schemas
            Matcher namespaceMatcher = TARGET_NAMESPACE_PATTERN.matcher(xmlContent);
            if (namespaceMatcher.find()) {
                String namespace = namespaceMatcher.group(1);
                String commonSchema = commonSchemas.get(namespace);
                if (commonSchema != null) {
                    logger.debug("Found common schema for namespace {}: {}", namespace, commonSchema);
                    return commonSchema;
                }
            }

        } catch (Exception e) {
            logger.warn("Error during schema discovery", e);
        }

        return null;
    }

    /**
     * Resolve schema path from URL or relative path
     */
    private String resolveSchemaPath(String schemaRef) {
        if (schemaRef == null || schemaRef.trim().isEmpty()) {
            return null;
        }

        try {
            // Try as absolute URL
            if (schemaRef.startsWith("http://") || schemaRef.startsWith("https://")) {
                return schemaRef;
            }

            // Try as file path
            Path schemaPath = Paths.get(schemaRef);
            if (Files.exists(schemaPath)) {
                return schemaPath.toAbsolutePath().toString();
            }

            // Try relative to common schema locations
            String[] commonPaths = {
                    "src/main/resources/schemas/",
                    "schemas/",
                    "xsd/",
                    "./",
                    "../schemas/"
            };

            for (String commonPath : commonPaths) {
                Path candidatePath = Paths.get(commonPath, schemaRef);
                if (Files.exists(candidatePath)) {
                    return candidatePath.toAbsolutePath().toString();
                }
            }

        } catch (Exception e) {
            logger.debug("Could not resolve schema path: {}", schemaRef, e);
        }

        return schemaRef; // Return as-is if resolution failed
    }

    // ========== Validation Implementation ==========

        /**
         * Perform XSD validation with detailed error reporting.
         * Uses StreamSource for efficient validation without building a DOM tree.
         */
        private XmlValidationResult performXsdValidation(String xmlContent, String xsdPath) {
            XmlValidationResult result = new XmlValidationResult();
            result.setSchemaPath(xsdPath);
    
            try {
                // Load or get cached schema
                Schema schema = getOrLoadSchema(xsdPath);
                if (schema == null) {
                    result.addError(new XmlValidationError(
                            XmlValidationError.ErrorType.FATAL,
                            0, 0,
                            "Failed to load XSD schema: " + xsdPath,
                            "Verify schema file exists and is valid"
                    ));
                    return result;
                }
    
                // Custom error handler to collect validation errors
                ValidationErrorHandler errorHandler = new ValidationErrorHandler();
    
                // Validate against schema using streaming
                Validator validator = schema.newValidator();
                validator.setErrorHandler(errorHandler);
    
                try (StringReader reader = new StringReader(xmlContent)) {
                    validator.validate(new StreamSource(reader));
                }
    
                // Add validation errors
                result.addErrors(errorHandler.getErrors());
    
            } catch (Exception e) {
                logger.error("XSD validation failed", e);
                result.addError(new XmlValidationError(
                        XmlValidationError.ErrorType.FATAL,
                        0, 0,
                        "XSD validation error: " + e.getMessage(),
                        "Check XML and XSD syntax"
                ));
            }
    
            return result;
        }
    
        /**
         * Perform well-formedness validation only
         */
        private XmlValidationResult performWellFormednessValidation(String xmlContent) {
            XmlValidationResult result = new XmlValidationResult();
            result.setSchemaPath(null);
    
                    try {
                        // Use SAX for well-formedness check (faster than DOM)
                        javax.xml.parsers.SAXParserFactory factory = org.fxt.freexmltoolkit.util.SecureXmlFactory.createSecureSAXParserFactory();
                        factory.setNamespaceAware(true);
                        javax.xml.parsers.SAXParser parser = factory.newSAXParser();    
                ValidationErrorHandler errorHandler = new ValidationErrorHandler();
                try (StringReader reader = new StringReader(xmlContent)) {
                    parser.parse(new org.xml.sax.InputSource(reader), new org.xml.sax.helpers.DefaultHandler() {
                        @Override
                        public void warning(SAXParseException e) throws SAXException { errorHandler.warning(e); }
                        @Override
                        public void error(SAXParseException e) throws SAXException { errorHandler.error(e); }
                        @Override
                        public void fatalError(SAXParseException e) throws SAXException { errorHandler.fatalError(e); }
                    });
                }
    
                result.addErrors(errorHandler.getErrors());
    
            } catch (Exception e) {
                logger.error("Well-formedness validation failed", e);
                result.addError(new XmlValidationError(
                        XmlValidationError.ErrorType.FATAL,
                        0, 0,
                        "XML parsing error: " + e.getMessage(),
                        "Check XML syntax and structure"
                ));
            }
    
            return result;
        }
    
        /**
         * Validate large XML files with streaming optimization.
         */
        private XmlValidationResult validateLargeXml(String xmlContent, String xsdPath) {
            logger.debug("Validating large XML file ({} characters) with streaming optimization", xmlContent.length());
    
            // Both methods now use streaming/SAX, so we just call the appropriate one
            XmlValidationResult result = (xsdPath != null) ? performXsdValidation(xmlContent, xsdPath) : performWellFormednessValidation(xmlContent);
    
                    // Add performance note
                    result.addInfo(new XmlValidationError(
                            XmlValidationError.ErrorType.INFO,
                            0, 0,
                            "Large file validation completed using streaming optimization (" + org.apache.commons.io.FileUtils.byteCountToDisplaySize(xmlContent.length()) + ")",
                            "Streaming validation reduces memory footprint for large documents."
                    ));    
            return result;
        }
    // ========== Schema Management ==========

    /**
     * Load or retrieve cached schema
     */
    private Schema getOrLoadSchema(String xsdPath) {
        if (!enableSchemaCache) {
            return loadSchema(xsdPath);
        }

        // Check cache
        Schema cachedSchema = schemaCache.get(xsdPath);
        Long cacheTime = schemaCacheTimestamps.get(xsdPath);

        if (cachedSchema != null && cacheTime != null) {
            if (System.currentTimeMillis() - cacheTime < schemaCacheTimeout) {
                logger.debug("Using cached schema: {}", xsdPath);
                return cachedSchema;
            } else {
                // Cache expired
                schemaCache.remove(xsdPath);
                schemaCacheTimestamps.remove(xsdPath);
            }
        }

        // Load and cache schema
        Schema schema = loadSchema(xsdPath);
        if (schema != null) {
            schemaCache.put(xsdPath, schema);
            schemaCacheTimestamps.put(xsdPath, System.currentTimeMillis());
            logger.debug("Cached schema: {}", xsdPath);
        }

        return schema;
    }

    /**
     * Load XSD schema from file or URL.
     * <p>
     * Uses unified SchemaResolver to support relative xs:import and xs:include references.
     * The systemId is set on the StreamSource to enable proper relative path resolution.
     * </p>
     */
    private Schema loadSchema(String xsdPath) {
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

            // Configure unified schema resolver to handle relative schema references (xs:import, xs:include)
            SchemaResolver schemaResolver = new SchemaResolver(XsdParseOptions.defaults());
            Path baseDir = xsdPath.startsWith("http") ? null : Paths.get(xsdPath).getParent();
            schemaFactory.setResourceResolver(schemaResolver.createLSResourceResolver(baseDir));

            StreamSource schemaSource;

            if (xsdPath.startsWith("http://") || xsdPath.startsWith("https://")) {
                // Load from URL
                URI schemaUri = URI.create(xsdPath);
                schemaSource = new StreamSource(schemaUri.toURL().openStream());
                schemaSource.setSystemId(xsdPath); // Enable relative import resolution from URL
            } else {
                // Load from file
                Path schemaFilePath = Paths.get(xsdPath);
                if (!Files.exists(schemaFilePath)) {
                    logger.warn("Schema file does not exist: {}", xsdPath);
                    return null;
                }
                schemaSource = new StreamSource(Files.newInputStream(schemaFilePath));
                schemaSource.setSystemId(schemaFilePath.toUri().toString()); // Enable relative import resolution
            }

            Schema schema = schemaFactory.newSchema(schemaSource);
            logger.debug("Successfully loaded schema: {}", xsdPath);
            return schema;

        } catch (Exception e) {
            logger.error("Failed to load XSD schema: {}", xsdPath, e);
            return null;
        }
    }

    // ========== Configuration Methods ==========

    public void setRealTimeValidationEnabled(boolean enabled) {
        this.realTimeValidationEnabled = enabled;
        logger.debug("Real-time validation {}", enabled ? "enabled" : "disabled");
    }

    public void setValidationOnSaveEnabled(boolean enabled) {
        this.validationOnSaveEnabled = enabled;
        logger.debug("Validation on save {}", enabled ? "enabled" : "disabled");
    }

    public void setEnableSchemaCache(boolean enabled) {
        this.enableSchemaCache = enabled;
        if (!enabled) {
            clearSchemaCache();
        }
        logger.debug("Schema caching {}", enabled ? "enabled" : "disabled");
    }

    public void setSchemaCacheTimeout(long timeoutMs) {
        this.schemaCacheTimeout = timeoutMs;
        logger.debug("Schema cache timeout set to {}ms", timeoutMs);
    }

    public void setMaxErrorCount(int maxErrors) {
        this.maxErrorCount = maxErrors;
        logger.debug("Max error count set to {}", maxErrors);
    }

    public void clearSchemaCache() {
        schemaCache.clear();
        schemaCacheTimestamps.clear();
        logger.debug("Schema cache cleared");
    }

    // ========== Getters ==========

    public boolean isRealTimeValidationEnabled() {
        return realTimeValidationEnabled;
    }

    public boolean isValidationOnSaveEnabled() {
        return validationOnSaveEnabled;
    }

    public boolean isEnableSchemaCaching() {
        return enableSchemaCache;
    }

    public int getCachedSchemaCount() {
        return schemaCache.size();
    }

    public Set<String> getCachedSchemas() {
        return new HashSet<>(schemaCache.keySet());
    }

    // ========== Inner Classes ==========

    /**
     * Custom error handler for collecting validation errors
     */
    private class ValidationErrorHandler implements ErrorHandler {
        private final List<XmlValidationError> errors = new ArrayList<>();
        private boolean hasFatalErrors = false;

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            if (errors.size() < maxErrorCount) {
                errors.add(new XmlValidationError(
                        XmlValidationError.ErrorType.WARNING,
                        exception.getLineNumber(),
                        exception.getColumnNumber(),
                        exception.getMessage(),
                        "Review XML content for potential issues"
                ));
            }
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            if (errors.size() < maxErrorCount) {
                errors.add(new XmlValidationError(
                        XmlValidationError.ErrorType.ERROR,
                        exception.getLineNumber(),
                        exception.getColumnNumber(),
                        exception.getMessage(),
                        "Fix XML validation error"
                ));
            }
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            hasFatalErrors = true;
            if (errors.size() < maxErrorCount) {
                errors.add(new XmlValidationError(
                        XmlValidationError.ErrorType.FATAL,
                        exception.getLineNumber(),
                        exception.getColumnNumber(),
                        exception.getMessage(),
                        "Fix critical XML error"
                ));
            }
        }

        public List<XmlValidationError> getErrors() {
            return errors;
        }

        public boolean hasFatalErrors() {
            return hasFatalErrors;
        }
    }
}