package org.fxt.freexmltoolkit.service.xsd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of {@link XsdParsingService}.
 *
 * <p>This service provides unified XSD parsing with support for:
 * <ul>
 *   <li>Local file parsing</li>
 *   <li>String content parsing</li>
 *   <li>Remote URL parsing</li>
 *   <li>xs:include resolution (flatten or preserve structure)</li>
 *   <li>xs:import resolution (as references)</li>
 *   <li>Caching for improved performance</li>
 * </ul>
 */
public class XsdParsingServiceImpl implements XsdParsingService {

    private static final Logger logger = LogManager.getLogger(XsdParsingServiceImpl.class);

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String XSD_1_0_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String XSD_1_1_NS = "http://www.w3.org/XML/XMLSchema/v1.1";

    private final DocumentBuilderFactory documentBuilderFactory;

    // Cache for parsed schemas
    private final Map<Path, ParsedSchema> schemaCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    /**
     * Creates a new XsdParsingServiceImpl.
     */
    public XsdParsingServiceImpl() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        this.documentBuilderFactory.setIgnoringComments(false);
        this.documentBuilderFactory.setIgnoringElementContentWhitespace(false);

        // Allow DOCTYPE declarations (required for some W3C schemas like xmldsig-core-schema.xsd)
        // but disable external entity processing for security
        try {
            this.documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            this.documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            this.documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            this.documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            this.documentBuilderFactory.setExpandEntityReferences(false);
        } catch (Exception e) {
            logger.warn("Could not configure all XML security features: {}", e.getMessage());
        }
    }

    @Override
    public ParsedSchema parse(Path xsdFile) throws XsdParseException {
        return parse(xsdFile, XsdParseOptions.defaults());
    }

    @Override
    public ParsedSchema parse(Path xsdFile, XsdParseOptions options) throws XsdParseException {
        if (xsdFile == null) {
            throw new XsdParseException("XSD file path cannot be null");
        }

        // Normalize path
        Path normalizedPath;
        try {
            normalizedPath = xsdFile.toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new XsdParseException("Invalid file path: " + xsdFile, e);
        }

        // Check file exists
        if (!Files.exists(normalizedPath)) {
            throw XsdParseException.fileNotFound(normalizedPath);
        }

        // Check cache
        if (options.isCacheEnabled() && schemaCache.containsKey(normalizedPath)) {
            cacheHits.incrementAndGet();
            logger.debug("Cache hit for schema: {}", normalizedPath);
            return schemaCache.get(normalizedPath);
        }
        cacheMisses.incrementAndGet();

        options.reportProgress("Parsing " + normalizedPath.getFileName(), 0, -1);

        try {
            // Parse the document
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(normalizedPath.toFile());
            Element root = document.getDocumentElement();

            // Validate root element
            if (!isXsdElement(root, "schema")) {
                throw new XsdParseException("Root element is not xs:schema in file: " + normalizedPath,
                        null, normalizedPath, null, -1, -1, XsdParseException.ErrorType.INVALID_SCHEMA);
            }

            // Extract leading comments
            String leadingComments = extractLeadingComments(document);

            // Build initial ParsedSchema
            ParsedSchema parsedSchema = ParsedSchema.builder()
                    .document(document)
                    .schemaElement(root)
                    .sourceFile(normalizedPath)
                    .targetNamespace(root.getAttribute("targetNamespace"))
                    .namespaceDeclarations(extractNamespaces(root))
                    .options(options)
                    .leadingComments(leadingComments)
                    .build();

            // Resolve includes and imports
            SchemaResolver resolver = new SchemaResolver(options);
            parsedSchema = resolver.resolveReferences(parsedSchema);

            // Cache if enabled
            if (options.isCacheEnabled()) {
                schemaCache.put(normalizedPath, parsedSchema);
            }

            logger.info("Successfully parsed XSD: {} (includes: {}, imports: {})",
                    normalizedPath,
                    parsedSchema.getResolvedIncludes().size(),
                    parsedSchema.getResolvedImports().size());

            return parsedSchema;

        } catch (SAXParseException e) {
            throw XsdParseException.malformedXml(normalizedPath, e, e.getLineNumber(), e.getColumnNumber());
        } catch (XsdParseException e) {
            throw e;
        } catch (Exception e) {
            throw new XsdParseException("Failed to parse XSD file: " + normalizedPath, e,
                    normalizedPath, null, -1, -1, XsdParseException.ErrorType.PARSE_ERROR);
        }
    }

    @Override
    public ParsedSchema parse(String content, Path baseDirectory) throws XsdParseException {
        return parse(content, baseDirectory, XsdParseOptions.defaults());
    }

    @Override
    public ParsedSchema parse(String content, Path baseDirectory, XsdParseOptions options) throws XsdParseException {
        if (content == null || content.isBlank()) {
            throw new XsdParseException("XSD content cannot be null or empty");
        }

        options.reportProgress("Parsing XSD content", 0, -1);

        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(content)));
            Element root = document.getDocumentElement();

            if (!isXsdElement(root, "schema")) {
                throw new XsdParseException("Root element is not xs:schema");
            }

            String leadingComments = extractLeadingComments(document);

            ParsedSchema parsedSchema = ParsedSchema.builder()
                    .document(document)
                    .schemaElement(root)
                    .sourceFile(null) // No source file for string content
                    .targetNamespace(root.getAttribute("targetNamespace"))
                    .namespaceDeclarations(extractNamespaces(root))
                    .options(options)
                    .leadingComments(leadingComments)
                    .build();

            // Resolve includes and imports if base directory is provided
            if (baseDirectory != null) {
                SchemaResolver resolver = new SchemaResolver(options);
                parsedSchema = resolver.resolveReferences(parsedSchema);
            }

            return parsedSchema;

        } catch (SAXParseException e) {
            throw XsdParseException.malformedXml(null, e, e.getLineNumber(), e.getColumnNumber());
        } catch (XsdParseException e) {
            throw e;
        } catch (Exception e) {
            throw new XsdParseException("Failed to parse XSD content: " + e.getMessage(), e);
        }
    }

    @Override
    public ParsedSchema parseFromUrl(String url) throws XsdParseException {
        return parseFromUrl(url, XsdParseOptions.defaults());
    }

    @Override
    public ParsedSchema parseFromUrl(String url, XsdParseOptions options) throws XsdParseException {
        if (url == null || url.isBlank()) {
            throw new XsdParseException("URL cannot be null or empty");
        }

        options.reportProgress("Fetching schema from " + url, 0, -1);
        logger.info("Fetching XSD from URL: {}", url);

        try {
            ConnectionService connectionService = ServiceRegistry.get(ConnectionService.class);
            String content = connectionService.getTextContentFromURL(URI.create(url));

            if (content == null || content.isEmpty()) {
                throw XsdParseException.networkError(url, new Exception("Empty response from URL"));
            }

            // Parse without base directory - remote schemas can't have local includes
            return parse(content, null, options);

        } catch (XsdParseException e) {
            throw e;
        } catch (Exception e) {
            throw XsdParseException.networkError(url, e);
        }
    }

    @Override
    public XsdSchema toXsdModel(ParsedSchema parsedSchema) throws XsdParseException {
        if (parsedSchema == null) {
            throw new XsdParseException("ParsedSchema cannot be null");
        }

        try {
            // Use XsdNodeFactory for conversion to maintain compatibility
            XsdNodeFactory factory = new XsdNodeFactory();

            // Configure factory based on options
            XsdParseOptions options = parsedSchema.getOptions();
            if (options != null) {
                boolean preserveStructure = options.getIncludeMode() == XsdParseOptions.IncludeMode.PRESERVE_STRUCTURE;
                factory.setPreserveIncludeStructure(preserveStructure);
            }

            // Convert from ParsedSchema
            if (parsedSchema.getSourceFile().isPresent()) {
                return factory.fromFile(parsedSchema.getSourceFile().get());
            } else {
                // For string/URL content, we need to serialize and re-parse
                // This is a temporary solution until XsdNodeFactory supports Document input
                String content = serializeDocument(parsedSchema.getDocument());
                return factory.fromString(content);
            }

        } catch (Exception e) {
            throw new XsdParseException("Failed to convert to XsdSchema model: " + e.getMessage(), e);
        }
    }

    @Override
    public Schema toValidationSchema(ParsedSchema parsedSchema, XsdVersion version) throws XsdParseException {
        if (parsedSchema == null) {
            throw new XsdParseException("ParsedSchema cannot be null");
        }

        try {
            String schemaLanguage = switch (version) {
                case XSD_1_0 -> XSD_1_0_NS;
                case XSD_1_1 -> XSD_1_1_NS;
            };

            SchemaFactory schemaFactory = SchemaFactory.newInstance(schemaLanguage);

            // If we have a source file, use it directly
            if (parsedSchema.getSourceFile().isPresent()) {
                return schemaFactory.newSchema(parsedSchema.getSourceFile().get().toFile());
            }

            // Otherwise, create schema from DOM source
            javax.xml.transform.dom.DOMSource source =
                    new javax.xml.transform.dom.DOMSource(parsedSchema.getDocument());
            return schemaFactory.newSchema(source);

        } catch (Exception e) {
            throw new XsdParseException("Failed to create validation schema: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isValidXsd(Path xsdFile) {
        if (xsdFile == null || !Files.exists(xsdFile)) {
            return false;
        }

        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document document = builder.parse(xsdFile.toFile());
            Element root = document.getDocumentElement();
            return isXsdElement(root, "schema");
        } catch (Exception e) {
            logger.debug("File is not a valid XSD: {} - {}", xsdFile, e.getMessage());
            return false;
        }
    }

    @Override
    public void clearCache() {
        schemaCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        logger.info("Schema cache cleared");
    }

    @Override
    public CacheStatistics getCacheStatistics() {
        long estimatedMemory = schemaCache.size() * 100_000L; // Rough estimate: 100KB per schema
        return new CacheStatistics(
                cacheHits.get(),
                cacheMisses.get(),
                schemaCache.size(),
                estimatedMemory
        );
    }

    // ========== Helper Methods ==========

    /**
     * Checks if an element is an XSD element with the given local name.
     */
    private boolean isXsdElement(Element element, String localName) {
        return XSD_NS.equals(element.getNamespaceURI()) && localName.equals(element.getLocalName());
    }

    /**
     * Extracts namespace declarations from a schema element.
     */
    private Map<String, String> extractNamespaces(Element schemaElement) {
        Map<String, String> namespaces = new LinkedHashMap<>();

        for (int i = 0; i < schemaElement.getAttributes().getLength(); i++) {
            Node attr = schemaElement.getAttributes().item(i);
            String name = attr.getNodeName();

            if (name.startsWith("xmlns:")) {
                String prefix = name.substring(6);
                namespaces.put(prefix, attr.getNodeValue());
            } else if ("xmlns".equals(name)) {
                namespaces.put("", attr.getNodeValue());
            }
        }

        return namespaces;
    }

    /**
     * Extracts leading comments (before the root element).
     */
    private String extractLeadingComments(Document document) {
        StringBuilder comments = new StringBuilder();
        Node node = document.getFirstChild();

        while (node != null && !(node instanceof Element)) {
            if (node instanceof Comment comment) {
                if (!comments.isEmpty()) {
                    comments.append("\n");
                }
                comments.append("<!--").append(comment.getData()).append("-->");
            }
            node = node.getNextSibling();
        }

        return comments.isEmpty() ? null : comments.toString();
    }

    /**
     * Serializes a DOM Document to string.
     */
    private String serializeDocument(Document document) throws Exception {
        javax.xml.transform.TransformerFactory factory = javax.xml.transform.TransformerFactory.newInstance();
        javax.xml.transform.Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");

        java.io.StringWriter writer = new java.io.StringWriter();
        transformer.transform(
                new javax.xml.transform.dom.DOMSource(document),
                new javax.xml.transform.stream.StreamResult(writer)
        );

        return writer.toString();
    }
}
