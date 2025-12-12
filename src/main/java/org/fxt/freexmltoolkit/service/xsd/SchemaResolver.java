package org.fxt.freexmltoolkit.service.xsd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ConnectionService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified resolver for xs:include and xs:import schema references.
 *
 * <p>This class consolidates schema resolution logic that was previously
 * scattered across XsdNodeFactory, XsdFlattenerService, and SchemaResourceResolver.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Circular reference detection for both includes and imports</li>
 *   <li>Support for local files (relative and absolute paths)</li>
 *   <li>Support for remote schemas (HTTP/HTTPS)</li>
 *   <li>In-memory caching of resolved schemas</li>
 *   <li>Configurable maximum include depth</li>
 *   <li>Progress reporting during resolution</li>
 * </ul>
 */
public class SchemaResolver {

    private static final Logger logger = LogManager.getLogger(SchemaResolver.class);

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final DocumentBuilderFactory documentBuilderFactory;
    private final XsdParseOptions options;

    // Circular reference detection
    private final Set<Path> includeStack = new LinkedHashSet<>();
    private final Set<Path> processedIncludes = new HashSet<>();
    private final Set<String> processedImports = new HashSet<>();

    // Caching
    private final Map<Path, ParsedSchema> includeCache = new ConcurrentHashMap<>();
    private final Map<String, ParsedSchema> importCache = new ConcurrentHashMap<>();

    // Statistics
    private int resolvedIncludeCount = 0;
    private int resolvedImportCount = 0;
    private int failedIncludeCount = 0;
    private int failedImportCount = 0;

    /**
     * Creates a new SchemaResolver with the given options.
     *
     * @param options the parsing options
     */
    public SchemaResolver(XsdParseOptions options) {
        this.options = options != null ? options : XsdParseOptions.defaults();

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

    /**
     * Resolves all xs:include and xs:import references in a schema.
     *
     * @param parsedSchema the schema to resolve references in
     * @return the schema with all references resolved
     * @throws XsdParseException if resolution fails
     */
    public ParsedSchema resolveReferences(ParsedSchema parsedSchema) throws XsdParseException {
        Path baseDir = parsedSchema.getBaseDirectory();
        Element schemaElement = parsedSchema.getSchemaElement();
        Document document = parsedSchema.getDocument();

        // Process includes
        List<ParsedSchema.ResolvedInclude> resolvedIncludes = resolveIncludes(schemaElement, baseDir, 0);

        // In FLATTEN mode, inline the content of included schemas into the main document
        if (options.getIncludeMode() == XsdParseOptions.IncludeMode.FLATTEN) {
            flattenIncludes(document, schemaElement, resolvedIncludes);
        }

        ParsedSchema.Builder builder = ParsedSchema.builder()
                .document(document)
                .schemaElement(schemaElement)
                .sourceFile(parsedSchema.getSourceFile().orElse(null))
                .targetNamespace(parsedSchema.getTargetNamespace())
                .namespaceDeclarations(parsedSchema.getNamespaceDeclarations())
                .parseTime(parsedSchema.getParseTime())
                .options(parsedSchema.getOptions())
                .leadingComments(parsedSchema.getLeadingComments())
                .resolvedIncludes(resolvedIncludes);

        // Process imports
        if (options.isResolveImports()) {
            List<ParsedSchema.ResolvedImport> resolvedImports = resolveImports(schemaElement, baseDir);
            builder.resolvedImports(resolvedImports);
        }

        return builder.build();
    }

    /**
     * Flattens includes by inlining their content into the main schema.
     * Removes xs:include elements and inserts the children of the included schema.
     *
     * @param document      the main document
     * @param schemaElement the main schema element
     * @param includes      the resolved includes
     */
    private void flattenIncludes(Document document, Element schemaElement, List<ParsedSchema.ResolvedInclude> includes) {
        // Find and process all xs:include elements
        NodeList children = schemaElement.getChildNodes();
        List<Element> includeElements = new ArrayList<>();

        // Collect all include elements first (avoid modification during iteration)
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && isXsdElement(element, "include")) {
                includeElements.add(element);
            }
        }

        // Process each include element
        for (Element includeElement : includeElements) {
            String schemaLocation = includeElement.getAttribute("schemaLocation");

            // Find the matching resolved include
            ParsedSchema.ResolvedInclude resolvedInclude = findMatchingInclude(includes, schemaLocation);

            if (resolvedInclude != null && resolvedInclude.isResolved() && resolvedInclude.parsedSchema() != null) {
                // Get the content from the included schema
                Element includedSchemaElement = resolvedInclude.parsedSchema().getSchemaElement();

                // First, recursively flatten any nested includes in the included schema
                List<ParsedSchema.ResolvedInclude> nestedIncludes = resolvedInclude.parsedSchema().getResolvedIncludes();
                if (nestedIncludes != null && !nestedIncludes.isEmpty()) {
                    flattenIncludes(resolvedInclude.parsedSchema().getDocument(), includedSchemaElement, nestedIncludes);
                }

                // Insert the children of the included schema before the include element
                NodeList includedChildren = includedSchemaElement.getChildNodes();
                List<Node> nodesToImport = new ArrayList<>();

                // Collect nodes to import (skip xs:include and xs:import in included files)
                for (int i = 0; i < includedChildren.getLength(); i++) {
                    Node child = includedChildren.item(i);
                    if (child instanceof Element childElement) {
                        // Skip include and import elements from included files
                        if (isXsdElement(childElement, "include") || isXsdElement(childElement, "import")) {
                            continue;
                        }
                        nodesToImport.add(child);
                    } else if (child instanceof org.w3c.dom.Comment) {
                        // Preserve comments
                        nodesToImport.add(child);
                    }
                }

                // Import and insert the nodes
                for (Node nodeToImport : nodesToImport) {
                    Node importedNode = document.importNode(nodeToImport, true);
                    schemaElement.insertBefore(importedNode, includeElement);
                }

                // Add a comment indicating where the content came from
                String sourceComment = " Included from: " + schemaLocation + " ";
                if (resolvedInclude.resolvedPath() != null) {
                    sourceComment = " Included from: " + resolvedInclude.resolvedPath().getFileName() + " ";
                }
                org.w3c.dom.Comment comment = document.createComment(sourceComment);
                schemaElement.insertBefore(comment, includeElement);

                logger.debug("Flattened include: {} ({} elements)", schemaLocation, nodesToImport.size());
            }

            // Remove the xs:include element
            schemaElement.removeChild(includeElement);
        }
    }

    /**
     * Finds a resolved include matching the given schema location.
     */
    private ParsedSchema.ResolvedInclude findMatchingInclude(List<ParsedSchema.ResolvedInclude> includes, String schemaLocation) {
        if (schemaLocation == null || includes == null) {
            return null;
        }

        for (ParsedSchema.ResolvedInclude include : includes) {
            if (schemaLocation.equals(include.schemaLocation())) {
                return include;
            }
        }

        return null;
    }

    /**
     * Resolves all xs:include directives in a schema element.
     *
     * @param schemaElement the schema element
     * @param baseDir       the base directory for relative paths
     * @param depth         current include depth
     * @return list of resolved includes
     * @throws XsdParseException if resolution fails
     */
    private List<ParsedSchema.ResolvedInclude> resolveIncludes(Element schemaElement, Path baseDir, int depth)
            throws XsdParseException {
        List<ParsedSchema.ResolvedInclude> resolved = new ArrayList<>();

        if (depth >= options.getMaxIncludeDepth()) {
            throw XsdParseException.maxDepthExceeded(depth, options.getMaxIncludeDepth());
        }

        NodeList children = schemaElement.getChildNodes();
        int totalIncludes = countElements(children, "include");
        int currentInclude = 0;

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && isXsdElement(element, "include")) {
                currentInclude++;
                options.reportProgress("Resolving include " + currentInclude + "/" + totalIncludes,
                        currentInclude - 1, totalIncludes);

                ParsedSchema.ResolvedInclude include = resolveInclude(element, baseDir, depth);
                resolved.add(include);
            }
        }

        return resolved;
    }

    /**
     * Resolves a single xs:include directive.
     */
    private ParsedSchema.ResolvedInclude resolveInclude(Element includeElement, Path baseDir, int depth)
            throws XsdParseException {
        String schemaLocation = includeElement.getAttribute("schemaLocation");

        if (schemaLocation == null || schemaLocation.isBlank()) {
            failedIncludeCount++;
            options.reportWarning("xs:include without schemaLocation attribute");
            return new ParsedSchema.ResolvedInclude(
                    schemaLocation, null, null, "No schemaLocation attribute");
        }

        // Skip remote schemas in this resolver (they're handled separately)
        if (schemaLocation.contains("://")) {
            try {
                ParsedSchema remoteParsed = resolveRemoteSchema(schemaLocation);
                resolvedIncludeCount++;
                return new ParsedSchema.ResolvedInclude(
                        schemaLocation, null, remoteParsed, null);
            } catch (XsdParseException e) {
                failedIncludeCount++;
                return new ParsedSchema.ResolvedInclude(
                        schemaLocation, null, null, e.getMessage());
            }
        }

        // Resolve path
        Path resolvedPath;
        try {
            resolvedPath = resolvePath(schemaLocation, baseDir);
        } catch (Exception e) {
            failedIncludeCount++;
            return new ParsedSchema.ResolvedInclude(
                    schemaLocation, null, null, "Path resolution failed: " + e.getMessage());
        }

        // Check if file exists
        if (!Files.exists(resolvedPath)) {
            failedIncludeCount++;
            return new ParsedSchema.ResolvedInclude(
                    schemaLocation, resolvedPath, null, "File not found");
        }

        // Get real path (resolves symlinks)
        Path realPath;
        try {
            realPath = resolvedPath.toRealPath();
        } catch (Exception e) {
            failedIncludeCount++;
            return new ParsedSchema.ResolvedInclude(
                    schemaLocation, resolvedPath, null, "Cannot resolve real path: " + e.getMessage());
        }

        // Check for duplicates
        if (processedIncludes.contains(realPath)) {
            logger.debug("Include already processed: {}", realPath);
            // Return cached result if available
            ParsedSchema cached = includeCache.get(realPath);
            return new ParsedSchema.ResolvedInclude(schemaLocation, realPath, cached, null);
        }

        // Check for circular includes
        if (includeStack.contains(realPath)) {
            failedIncludeCount++;
            options.reportWarning("Circular include detected: " + realPath);
            return new ParsedSchema.ResolvedInclude(
                    schemaLocation, realPath, null, "Circular include detected");
        }

        // Push to stack for circular detection
        includeStack.add(realPath);

        try {
            // Parse the included schema
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document includedDoc = builder.parse(realPath.toFile());
            Element includedRoot = includedDoc.getDocumentElement();

            if (!isXsdElement(includedRoot, "schema")) {
                failedIncludeCount++;
                return new ParsedSchema.ResolvedInclude(
                        schemaLocation, realPath, null, "Root element is not xs:schema");
            }

            // Build ParsedSchema for the include
            ParsedSchema includedParsed = ParsedSchema.builder()
                    .document(includedDoc)
                    .schemaElement(includedRoot)
                    .sourceFile(realPath)
                    .targetNamespace(includedRoot.getAttribute("targetNamespace"))
                    .namespaceDeclarations(extractNamespaces(includedRoot))
                    .options(options)
                    .build();

            // Recursively resolve includes in the included schema
            Path nextBaseDir = realPath.getParent();
            List<ParsedSchema.ResolvedInclude> nestedIncludes = resolveIncludes(includedRoot, nextBaseDir, depth + 1);

            // Update the parsed schema with nested includes
            includedParsed = ParsedSchema.builder()
                    .document(includedDoc)
                    .schemaElement(includedRoot)
                    .sourceFile(realPath)
                    .targetNamespace(includedRoot.getAttribute("targetNamespace"))
                    .namespaceDeclarations(extractNamespaces(includedRoot))
                    .resolvedIncludes(nestedIncludes)
                    .options(options)
                    .build();

            // Cache and mark as processed
            processedIncludes.add(realPath);
            if (options.isCacheEnabled()) {
                includeCache.put(realPath, includedParsed);
            }

            resolvedIncludeCount++;
            logger.debug("Successfully resolved include: {}", realPath);

            return new ParsedSchema.ResolvedInclude(schemaLocation, realPath, includedParsed, null);

        } catch (SAXParseException e) {
            failedIncludeCount++;
            return new ParsedSchema.ResolvedInclude(
                    schemaLocation, realPath, null,
                    String.format("Parse error at line %d, column %d: %s",
                            e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
        } catch (Exception e) {
            failedIncludeCount++;
            return new ParsedSchema.ResolvedInclude(
                    schemaLocation, realPath, null, "Parse error: " + e.getMessage());
        } finally {
            includeStack.remove(realPath);
        }
    }

    /**
     * Resolves all xs:import directives in a schema element.
     */
    private List<ParsedSchema.ResolvedImport> resolveImports(Element schemaElement, Path baseDir) {
        List<ParsedSchema.ResolvedImport> resolved = new ArrayList<>();

        NodeList children = schemaElement.getChildNodes();
        int totalImports = countElements(children, "import");
        int currentImport = 0;

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && isXsdElement(element, "import")) {
                currentImport++;
                options.reportProgress("Resolving import " + currentImport + "/" + totalImports,
                        currentImport - 1, totalImports);

                ParsedSchema.ResolvedImport imp = resolveImport(element, baseDir);
                resolved.add(imp);
            }
        }

        return resolved;
    }

    /**
     * Resolves a single xs:import directive.
     */
    private ParsedSchema.ResolvedImport resolveImport(Element importElement, Path baseDir) {
        String namespace = importElement.getAttribute("namespace");
        String schemaLocation = importElement.getAttribute("schemaLocation");

        // Imports can have namespace only (schema location is optional)
        if ((namespace == null || namespace.isBlank()) && (schemaLocation == null || schemaLocation.isBlank())) {
            failedImportCount++;
            return new ParsedSchema.ResolvedImport(
                    namespace, schemaLocation, null, null, "Both namespace and schemaLocation are empty");
        }

        // If no schemaLocation, we can't load the schema but it's not an error
        if (schemaLocation == null || schemaLocation.isBlank()) {
            return new ParsedSchema.ResolvedImport(namespace, null, null, null, null);
        }

        // Check for duplicates
        String importKey = namespace != null ? namespace : schemaLocation;
        if (processedImports.contains(importKey)) {
            logger.debug("Import already processed: {}", importKey);
            ParsedSchema cached = importCache.get(importKey);
            return new ParsedSchema.ResolvedImport(namespace, schemaLocation, null, cached, null);
        }

        processedImports.add(importKey);

        try {
            // Resolve schema location
            ParsedSchema importedSchema;

            if (schemaLocation.startsWith("http://") || schemaLocation.startsWith("https://")) {
                // Remote import
                importedSchema = resolveRemoteSchema(schemaLocation);
            } else {
                // Local import
                Path resolvedPath = resolvePath(schemaLocation, baseDir);

                if (!Files.exists(resolvedPath)) {
                    failedImportCount++;
                    return new ParsedSchema.ResolvedImport(
                            namespace, schemaLocation, resolvedPath, null, "File not found");
                }

                DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
                Document importedDoc = builder.parse(resolvedPath.toFile());
                Element importedRoot = importedDoc.getDocumentElement();

                if (!isXsdElement(importedRoot, "schema")) {
                    failedImportCount++;
                    return new ParsedSchema.ResolvedImport(
                            namespace, schemaLocation, resolvedPath, null, "Root element is not xs:schema");
                }

                importedSchema = ParsedSchema.builder()
                        .document(importedDoc)
                        .schemaElement(importedRoot)
                        .sourceFile(resolvedPath)
                        .targetNamespace(importedRoot.getAttribute("targetNamespace"))
                        .namespaceDeclarations(extractNamespaces(importedRoot))
                        .options(options)
                        .build();

                // Cache
                if (options.isCacheEnabled()) {
                    importCache.put(importKey, importedSchema);
                }

                resolvedImportCount++;
                return new ParsedSchema.ResolvedImport(namespace, schemaLocation, resolvedPath, importedSchema, null);
            }

            // For remote schemas
            if (options.isCacheEnabled()) {
                importCache.put(importKey, importedSchema);
            }

            resolvedImportCount++;
            return new ParsedSchema.ResolvedImport(namespace, schemaLocation, null, importedSchema, null);

        } catch (Exception e) {
            failedImportCount++;
            return new ParsedSchema.ResolvedImport(
                    namespace, schemaLocation, null, null, "Failed to load: " + e.getMessage());
        }
    }

    /**
     * Resolves a remote schema from HTTP/HTTPS.
     */
    private ParsedSchema resolveRemoteSchema(String url) throws XsdParseException {
        logger.info("Resolving remote schema: {}", url);

        try {
            ConnectionService connectionService = ServiceRegistry.get(ConnectionService.class);
            String content = connectionService.getTextContentFromURL(URI.create(url));

            if (content == null || content.isEmpty()) {
                throw XsdParseException.networkError(url, new Exception("Empty response"));
            }

            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(content)));
            Element root = doc.getDocumentElement();

            if (!isXsdElement(root, "schema")) {
                throw new XsdParseException("Remote resource is not a valid XSD schema: " + url);
            }

            return ParsedSchema.builder()
                    .document(doc)
                    .schemaElement(root)
                    .targetNamespace(root.getAttribute("targetNamespace"))
                    .namespaceDeclarations(extractNamespaces(root))
                    .options(options)
                    .build();

        } catch (XsdParseException e) {
            throw e;
        } catch (Exception e) {
            throw XsdParseException.networkError(url, e);
        }
    }

    /**
     * Resolves a schema location to an absolute path.
     */
    private Path resolvePath(String schemaLocation, Path baseDir) {
        // Handle file:// URIs
        if (schemaLocation.startsWith("file://")) {
            String path = schemaLocation.substring(7);
            // Handle Windows paths (file:///C:/...)
            if (path.length() > 2 && path.charAt(0) == '/' && path.charAt(2) == ':') {
                path = path.substring(1);
            }
            return Path.of(path).toAbsolutePath().normalize();
        }

        // Handle absolute paths
        Path schemaPath = Path.of(schemaLocation);
        if (schemaPath.isAbsolute()) {
            return schemaPath.normalize();
        }

        // Resolve relative to base directory
        if (baseDir != null) {
            return baseDir.resolve(schemaLocation).normalize();
        }

        return schemaPath.toAbsolutePath().normalize();
    }

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
     * Counts elements with a specific local name.
     */
    private int countElements(NodeList nodes, String localName) {
        int count = 0;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && isXsdElement(element, localName)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Clears the include stack (for reuse between schemas).
     */
    public void reset() {
        includeStack.clear();
        processedIncludes.clear();
        processedImports.clear();
    }

    /**
     * Clears all caches.
     */
    public void clearCache() {
        includeCache.clear();
        importCache.clear();
        reset();
    }

    /**
     * @return the number of successfully resolved includes
     */
    public int getResolvedIncludeCount() {
        return resolvedIncludeCount;
    }

    /**
     * @return the number of successfully resolved imports
     */
    public int getResolvedImportCount() {
        return resolvedImportCount;
    }

    /**
     * @return the number of failed include resolutions
     */
    public int getFailedIncludeCount() {
        return failedIncludeCount;
    }

    /**
     * @return the number of failed import resolutions
     */
    public int getFailedImportCount() {
        return failedImportCount;
    }

    /**
     * @return statistics about resolution results
     */
    public ResolutionStatistics getStatistics() {
        return new ResolutionStatistics(
                resolvedIncludeCount, failedIncludeCount,
                resolvedImportCount, failedImportCount,
                includeCache.size(), importCache.size()
        );
    }

    /**
     * Statistics about schema resolution.
     */
    public record ResolutionStatistics(
            int resolvedIncludes,
            int failedIncludes,
            int resolvedImports,
            int failedImports,
            int cachedIncludes,
            int cachedImports
    ) {
        public int totalResolved() {
            return resolvedIncludes + resolvedImports;
        }

        public int totalFailed() {
            return failedIncludes + failedImports;
        }
    }

    // =========================================================================
    // LSResourceResolver Implementation for SchemaFactory compatibility
    // =========================================================================

    /**
     * Creates an LSResourceResolver that can be used with SchemaFactory for validation.
     * This provides the same resolution logic as the SchemaResolver but in the
     * LSResourceResolver interface required by javax.xml.validation.SchemaFactory.
     *
     * @param baseDir the base directory for resolving relative paths
     * @return an LSResourceResolver implementation
     */
    public org.w3c.dom.ls.LSResourceResolver createLSResourceResolver(Path baseDir) {
        return new ValidationResourceResolver(baseDir);
    }

    /**
     * LSResourceResolver implementation for use with SchemaFactory.
     * Provides the same resolution logic as SchemaResolver but in the interface
     * required by javax.xml.validation.
     */
    public class ValidationResourceResolver implements org.w3c.dom.ls.LSResourceResolver {

        private final Path baseDir;
        private final ThreadLocal<Set<String>> visitedUris = ThreadLocal.withInitial(java.util.HashSet::new);
        private final org.fxt.freexmltoolkit.service.SchemaResourceCache cache;

        public ValidationResourceResolver(Path baseDir) {
            this.baseDir = baseDir;
            this.cache = new org.fxt.freexmltoolkit.service.SchemaResourceCache();
        }

        @Override
        public org.w3c.dom.ls.LSInput resolveResource(String type, String namespaceURI, String publicId,
                                                       String systemId, String baseURI) {
            logger.debug("LSResourceResolver - type: {}, namespace: {}, systemId: {}, baseURI: {}",
                    type, namespaceURI, systemId, baseURI);

            if (systemId == null || systemId.isBlank()) {
                logger.debug("SystemId is null or blank, returning null");
                return null;
            }

            // Check for circular imports
            Set<String> visited = visitedUris.get();
            String normalizedSystemId = normalizeSystemId(systemId, baseURI);
            if (visited.contains(normalizedSystemId)) {
                logger.warn("Circular import detected for: {}", normalizedSystemId);
                return null;
            }
            visited.add(normalizedSystemId);

            try {
                // Handle remote URLs (HTTP/HTTPS)
                if (systemId.startsWith("http://") || systemId.startsWith("https://")) {
                    return resolveRemoteForValidation(systemId, publicId);
                }

                // Handle local file paths
                return resolveLocalForValidation(systemId, publicId, baseURI);

            } catch (Exception e) {
                logger.warn("Error resolving resource '{}': {}", systemId, e.getMessage());
                return null;
            }
        }

        /**
         * Clears the visited URIs tracking for circular import detection.
         * Should be called before starting a new validation.
         */
        public void resetCircularDetection() {
            visitedUris.get().clear();
        }

        /**
         * Gets the underlying schema resource cache.
         *
         * @return the schema resource cache
         */
        public org.fxt.freexmltoolkit.service.SchemaResourceCache getCache() {
            return cache;
        }

        private org.w3c.dom.ls.LSInput resolveRemoteForValidation(String systemId, String publicId) {
            try {
                logger.debug("Resolving remote schema: {}", systemId);
                Path cachedPath = cache.getOrDownload(systemId);
                return new LSInputImpl(
                        publicId,
                        systemId,
                        cachedPath.toUri().toString(),
                        java.nio.file.Files.newInputStream(cachedPath)
                );
            } catch (java.io.IOException e) {
                logger.error("Failed to download remote schema '{}': {}", systemId, e.getMessage());
                return null;
            }
        }

        private org.w3c.dom.ls.LSInput resolveLocalForValidation(String systemId, String publicId, String baseURI) {
            try {
                Path resolvedPath = resolveLocalPath(systemId, baseURI);

                if (resolvedPath != null && java.nio.file.Files.exists(resolvedPath)) {
                    logger.debug("Resolved local schema: {} -> {}", systemId, resolvedPath);
                    return new LSInputImpl(
                            publicId,
                            systemId,
                            resolvedPath.toUri().toString(),
                            java.nio.file.Files.newInputStream(resolvedPath)
                    );
                } else {
                    logger.warn("Could not resolve local schema '{}' (tried: {})", systemId, resolvedPath);
                    return null;
                }
            } catch (java.nio.file.AccessDeniedException e) {
                logger.error("Access denied to schema file '{}'. On macOS, files in Downloads may be quarantined. " +
                            "Try: xattr -rd com.apple.quarantine <folder> or move files to another location.",
                            systemId);
                return null;
            } catch (Exception e) {
                String message = e.getMessage();
                if (message != null && message.contains("Operation not permitted")) {
                    logger.error("Permission denied for schema '{}'. On macOS, this may be due to quarantine attributes. " +
                                "Try: xattr -rd com.apple.quarantine <folder>", systemId);
                } else {
                    logger.warn("Error resolving local schema '{}': {}", systemId, message);
                }
                return null;
            }
        }

        private Path resolveLocalPath(String systemId, String baseURI) {
            try {
                // If systemId is already an absolute path
                if (systemId.startsWith("/") || (systemId.length() > 1 && systemId.charAt(1) == ':')) {
                    Path absolutePath = Path.of(systemId);
                    if (java.nio.file.Files.exists(absolutePath)) {
                        return absolutePath;
                    }
                }

                // If we have a baseURI, resolve relative to it
                if (baseURI != null && !baseURI.isBlank()) {
                    Path basePath = uriToPath(baseURI);
                    if (basePath != null) {
                        Path baseDirectory = basePath.getParent();
                        if (baseDirectory != null) {
                            Path resolvedPath = baseDirectory.resolve(systemId).normalize();
                            if (java.nio.file.Files.exists(resolvedPath)) {
                                return resolvedPath;
                            }
                        }
                    }
                }

                // Try with the constructor baseDir
                if (baseDir != null) {
                    Path resolvedPath = baseDir.resolve(systemId).normalize();
                    if (java.nio.file.Files.exists(resolvedPath)) {
                        return resolvedPath;
                    }
                }

                // Try as a plain path
                Path plainPath = Path.of(systemId);
                if (java.nio.file.Files.exists(plainPath)) {
                    return plainPath;
                }

                return null;
            } catch (Exception e) {
                logger.debug("Error resolving path '{}' with base '{}': {}",
                        systemId, baseURI, e.getMessage());
                return null;
            }
        }

        private Path uriToPath(String uriString) {
            if (uriString == null || uriString.isBlank()) {
                return null;
            }

            try {
                // Handle file:// URIs properly using Java's URI class
                if (uriString.startsWith("file:")) {
                    java.net.URI uri = new java.net.URI(uriString);
                    return Path.of(uri);
                }

                // Handle plain paths
                return Path.of(uriString);
            } catch (java.net.URISyntaxException | IllegalArgumentException e) {
                logger.debug("Could not convert URI to path: {}", uriString);

                // Fallback: try manual conversion for edge cases
                try {
                    String path = uriString;
                    if (path.startsWith("file:///")) {
                        path = path.substring(8);
                    } else if (path.startsWith("file://")) {
                        path = path.substring(7);
                    } else if (path.startsWith("file:/")) {
                        path = path.substring(6);
                    }
                    return Path.of(path);
                } catch (Exception fallbackException) {
                    return null;
                }
            }
        }

        private String normalizeSystemId(String systemId, String baseURI) {
            if (systemId.startsWith("http://") || systemId.startsWith("https://")) {
                return systemId;
            }

            if (baseURI != null && !baseURI.isBlank()) {
                try {
                    Path basePath = uriToPath(baseURI);
                    if (basePath != null) {
                        Path baseDirectory = basePath.getParent();
                        if (baseDirectory != null) {
                            return baseDirectory.resolve(systemId).normalize().toString();
                        }
                    }
                } catch (Exception e) {
                    // Fall through to return systemId as-is
                }
            }

            return systemId;
        }
    }

    /**
     * Simple LSInput implementation for SchemaFactory resource resolution.
     */
    private static class LSInputImpl implements org.w3c.dom.ls.LSInput {
        private final String publicId;
        private final String systemId;
        private final String baseURI;
        private final java.io.InputStream byteStream;
        private java.io.Reader characterStream;
        private String stringData;
        private String encoding;
        private boolean certifiedText;

        public LSInputImpl(String publicId, String systemId, String baseURI, java.io.InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseURI = baseURI;
            this.byteStream = byteStream;
        }

        @Override public java.io.Reader getCharacterStream() { return characterStream; }
        @Override public void setCharacterStream(java.io.Reader characterStream) { this.characterStream = characterStream; }
        @Override public java.io.InputStream getByteStream() { return byteStream; }
        @Override public void setByteStream(java.io.InputStream byteStream) { /* immutable */ }
        @Override public String getStringData() { return stringData; }
        @Override public void setStringData(String stringData) { this.stringData = stringData; }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String systemId) { /* immutable */ }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String publicId) { /* immutable */ }
        @Override public String getBaseURI() { return baseURI; }
        @Override public void setBaseURI(String baseURI) { /* immutable */ }
        @Override public String getEncoding() { return encoding; }
        @Override public void setEncoding(String encoding) { this.encoding = encoding; }
        @Override public boolean getCertifiedText() { return certifiedText; }
        @Override public void setCertifiedText(boolean certifiedText) { this.certifiedText = certifiedText; }
    }
}
