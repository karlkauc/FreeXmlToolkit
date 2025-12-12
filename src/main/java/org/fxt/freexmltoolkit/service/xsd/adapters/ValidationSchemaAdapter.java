package org.fxt.freexmltoolkit.service.xsd.adapters;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.xsd.ParsedSchema;
import org.fxt.freexmltoolkit.service.xsd.XsdParseException;
import org.fxt.freexmltoolkit.service.xsd.XsdParsingService;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for converting ParsedSchema to javax.xml.validation.Schema.
 *
 * <p>This adapter creates validation schemas for use with XML validators,
 * supporting both XSD 1.0 and XSD 1.1 standards.</p>
 *
 * <h2>XSD Version Support</h2>
 * <ul>
 *   <li><b>XSD 1.0</b>: Full support via standard javax.xml.validation</li>
 *   <li><b>XSD 1.1</b>: Support via Apache Xerces (if available)</li>
 * </ul>
 *
 * <h2>Resource Resolution</h2>
 * <p>The adapter handles xs:include and xs:import by using the resolved
 * references from ParsedSchema when creating the validation schema.</p>
 */
public class ValidationSchemaAdapter {

    private static final Logger logger = LogManager.getLogger(ValidationSchemaAdapter.class);

    // XSD namespace URIs
    private static final String XSD_1_0_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String XSD_1_1_NS = "http://www.w3.org/XML/XMLSchema/v1.1";

    // Xerces schema factory for XSD 1.1
    private static final String XERCES_XSD_1_1_FACTORY =
            "org.apache.xerces.jaxp.validation.XMLSchema11Factory";

    /**
     * Creates a new ValidationSchemaAdapter.
     */
    public ValidationSchemaAdapter() {
    }

    /**
     * Converts a ParsedSchema to a validation Schema.
     *
     * @param parsedSchema the parsed schema
     * @param version      the XSD version to use
     * @return the validation schema
     * @throws XsdParseException if schema creation fails
     */
    public Schema toValidationSchema(ParsedSchema parsedSchema, XsdParsingService.XsdVersion version)
            throws XsdParseException {
        if (parsedSchema == null) {
            throw new XsdParseException("ParsedSchema cannot be null");
        }

        try {
            SchemaFactory schemaFactory = createSchemaFactory(version);

            // Set up resource resolver for includes/imports
            Path baseDir = parsedSchema.getBaseDirectory();
            schemaFactory.setResourceResolver(new ParsedSchemaResourceResolver(parsedSchema, baseDir));

            // Collect errors during schema creation
            List<SAXParseException> errors = new ArrayList<>();
            schemaFactory.setErrorHandler(new CollectingErrorHandler(errors));

            Schema schema;

            // Try to use source file if available (more reliable for includes)
            if (parsedSchema.getSourceFile().isPresent()) {
                Path sourceFile = parsedSchema.getSourceFile().get();
                logger.debug("Creating validation schema from file: {}", sourceFile);
                schema = schemaFactory.newSchema(sourceFile.toFile());
            } else {
                // Create from DOM
                logger.debug("Creating validation schema from DOM");
                DOMSource source = new DOMSource(parsedSchema.getDocument());
                schema = schemaFactory.newSchema(source);
            }

            // Log any warnings
            if (!errors.isEmpty()) {
                for (SAXParseException e : errors) {
                    logger.warn("Schema warning at line {}, column {}: {}",
                            e.getLineNumber(), e.getColumnNumber(), e.getMessage());
                }
            }

            logger.info("Successfully created {} validation schema", version);
            return schema;

        } catch (SAXException e) {
            throw new XsdParseException("Invalid XSD schema: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new XsdParseException("Failed to create validation schema: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a SchemaFactory for the specified XSD version.
     */
    private SchemaFactory createSchemaFactory(XsdParsingService.XsdVersion version) throws XsdParseException {
        try {
            return switch (version) {
                case XSD_1_0 -> {
                    logger.debug("Creating XSD 1.0 schema factory");
                    yield SchemaFactory.newInstance(XSD_1_0_NS);
                }
                case XSD_1_1 -> {
                    logger.debug("Creating XSD 1.1 schema factory (Xerces)");
                    try {
                        // Try to use Xerces XSD 1.1 factory
                        yield SchemaFactory.newInstance(XSD_1_1_NS);
                    } catch (IllegalArgumentException e) {
                        // Fallback: try loading Xerces factory directly
                        try {
                            @SuppressWarnings("unchecked")
                            Class<? extends SchemaFactory> factoryClass =
                                    (Class<? extends SchemaFactory>) Class.forName(XERCES_XSD_1_1_FACTORY);
                            yield factoryClass.getDeclaredConstructor().newInstance();
                        } catch (Exception ex) {
                            throw new XsdParseException(
                                    "XSD 1.1 support requires Apache Xerces. " +
                                            "Please ensure xerces is on the classpath.", ex);
                        }
                    }
                }
            };
        } catch (XsdParseException e) {
            throw e;
        } catch (Exception e) {
            throw new XsdParseException("Failed to create schema factory: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if XSD 1.1 support is available.
     *
     * @return true if XSD 1.1 is supported
     */
    public static boolean isXsd11Supported() {
        try {
            SchemaFactory.newInstance(XSD_1_1_NS);
            return true;
        } catch (Exception e1) {
            try {
                Class.forName(XERCES_XSD_1_1_FACTORY);
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    /**
     * Error handler that collects errors for later reporting.
     */
    private static class CollectingErrorHandler implements ErrorHandler {
        private final List<SAXParseException> errors;

        CollectingErrorHandler(List<SAXParseException> errors) {
            this.errors = errors;
        }

        @Override
        public void warning(SAXParseException exception) {
            errors.add(exception);
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }

    /**
     * Resource resolver that uses ParsedSchema's resolved includes/imports.
     */
    private static class ParsedSchemaResourceResolver implements LSResourceResolver {
        private final ParsedSchema parsedSchema;
        private final Path baseDir;

        ParsedSchemaResourceResolver(ParsedSchema parsedSchema, Path baseDir) {
            this.parsedSchema = parsedSchema;
            this.baseDir = baseDir;
        }

        @Override
        public org.w3c.dom.ls.LSInput resolveResource(String type, String namespaceURI,
                                                       String publicId, String systemId, String baseURI) {
            logger.debug("Resolving resource: type={}, namespace={}, systemId={}, baseURI={}",
                    type, namespaceURI, systemId, baseURI);

            // Check resolved includes
            for (ParsedSchema.ResolvedInclude include : parsedSchema.getResolvedIncludes()) {
                if (matchesLocation(include.schemaLocation(), systemId)) {
                    if (include.isResolved() && include.resolvedPath() != null) {
                        return createLSInput(include.resolvedPath(), systemId);
                    }
                }
            }

            // Check resolved imports
            for (ParsedSchema.ResolvedImport imp : parsedSchema.getResolvedImports()) {
                if (matchesNamespaceOrLocation(imp, namespaceURI, systemId)) {
                    if (imp.isLoaded() && imp.resolvedPath() != null) {
                        return createLSInput(imp.resolvedPath(), systemId);
                    }
                }
            }

            // Fallback: try to resolve relative to base directory
            if (systemId != null && baseDir != null) {
                Path resolved = baseDir.resolve(systemId);
                if (java.nio.file.Files.exists(resolved)) {
                    return createLSInput(resolved, systemId);
                }
            }

            // Let the default resolver handle it
            return null;
        }

        private boolean matchesLocation(String schemaLocation, String systemId) {
            if (schemaLocation == null || systemId == null) {
                return false;
            }
            return schemaLocation.equals(systemId) ||
                    schemaLocation.endsWith("/" + systemId) ||
                    systemId.endsWith("/" + schemaLocation);
        }

        private boolean matchesNamespaceOrLocation(ParsedSchema.ResolvedImport imp,
                                                    String namespaceURI, String systemId) {
            // Match by namespace
            if (namespaceURI != null && namespaceURI.equals(imp.namespace())) {
                return true;
            }
            // Match by location
            return matchesLocation(imp.schemaLocation(), systemId);
        }

        private org.w3c.dom.ls.LSInput createLSInput(Path path, String systemId) {
            return new SimpleLSInput(path, systemId);
        }
    }

    /**
     * Simple LSInput implementation for file-based resources.
     */
    private static class SimpleLSInput implements org.w3c.dom.ls.LSInput {
        private final Path path;
        private final String systemId;

        SimpleLSInput(Path path, String systemId) {
            this.path = path;
            this.systemId = systemId;
        }

        @Override
        public java.io.Reader getCharacterStream() {
            try {
                return java.nio.file.Files.newBufferedReader(path);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void setCharacterStream(java.io.Reader characterStream) {
        }

        @Override
        public java.io.InputStream getByteStream() {
            try {
                return java.nio.file.Files.newInputStream(path);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void setByteStream(java.io.InputStream byteStream) {
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
            return null;
        }

        @Override
        public void setPublicId(String publicId) {
        }

        @Override
        public String getBaseURI() {
            return path.getParent() != null ? path.getParent().toUri().toString() : null;
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
