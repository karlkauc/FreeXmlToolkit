package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageFinder;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Collects comprehensive statistics from an XSD schema.
 * Traverses the XsdNode tree and gathers information about all nodes,
 * documentation, type usage, and cardinality.
 *
 * @since 2.0
 */
public class XsdStatisticsCollector {

    private static final Logger logger = LogManager.getLogger(XsdStatisticsCollector.class);

    private static final Set<String> KNOWN_APPINFO_TAGS = Set.of(
            "@since", "@version", "@author", "@deprecated", "@see", "@param", "@return"
    );

    private final XsdSchema schema;

    /**
     * Creates a new statistics collector for the given schema.
     *
     * @param schema the XSD schema to analyze (must not be null)
     * @throws NullPointerException if schema is null
     */
    public XsdStatisticsCollector(XsdSchema schema) {
        Objects.requireNonNull(schema, "Schema cannot be null");
        this.schema = schema;
    }

    /**
     * Collects all statistics from the schema.
     *
     * @return the collected statistics
     */
    public XsdStatistics collect() {
        logger.info("Collecting statistics for schema: {}", schema.getMainSchemaPath());
        long startTime = System.currentTimeMillis();

        XsdStatistics.Builder builder = XsdStatistics.builder();

        // Collect schema information
        collectSchemaInfo(builder);

        // Collect schema references (includes/imports) information
        collectSchemaReferences(builder);

        // Traverse tree and collect node statistics (main schema and includes)
        Set<String> visitedIds = new HashSet<>();
        traverseAndCollect(schema, builder, visitedIds);

        // Also traverse imported schemas (they are NOT children of main schema)
        for (Map.Entry<String, XsdSchema> entry : schema.getImportedSchemas().entrySet()) {
            logger.debug("Collecting statistics from imported schema: {}", entry.getKey());
            traverseAndCollect(entry.getValue(), builder, visitedIds);
        }

        // Calculate documentation coverage
        builder.calculateDocumentationCoverage();

        // Collect type usage statistics
        collectTypeUsageStatistics(builder);

        XsdStatistics stats = builder.build();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Statistics collected in {}ms: {} total nodes", duration, stats.totalNodeCount());

        return stats;
    }

    /**
     * Collects schema-level information.
     */
    private void collectSchemaInfo(XsdStatistics.Builder builder) {
        // XSD Version
        String version = schema.detectXsdVersion();
        builder.xsdVersion(version != null ? version : "1.0");

        // Target Namespace
        builder.targetNamespace(schema.getTargetNamespace());

        // Form defaults
        builder.elementFormDefault(schema.getElementFormDefault());
        builder.attributeFormDefault(schema.getAttributeFormDefault());

        // Namespace count
        Map<String, String> namespaces = schema.getNamespaces();
        builder.namespaceCount(namespaces != null ? namespaces.size() : 0);

        // File count
        builder.mainSchemaPath(schema.getMainSchemaPath());
        Set<java.nio.file.Path> includedFiles = schema.getAllInvolvedFiles();
        if (includedFiles != null && !includedFiles.isEmpty()) {
            builder.includedFiles(includedFiles);
            builder.fileCount(includedFiles.size());
        } else {
            builder.fileCount(1);
        }
    }

    /**
     * Collects information about schema references (xs:include and xs:import statements).
     * Deduplicates references by resolved path AND filename to avoid showing the same file multiple times.
     * Priority: Successfully resolved files take precedence over failed ones.
     */
    private void collectSchemaReferences(XsdStatistics.Builder builder) {
        logger.debug("Collecting schema reference information");

        // Track seen paths to deduplicate (same file might be included from multiple places)
        Set<Path> seenResolvedPaths = new HashSet<>();
        Set<String> seenFileNames = new HashSet<>(); // Track by filename for cross-reference dedup
        List<XsdSchemaReferenceInfo> resolvedRefs = new ArrayList<>();
        List<XsdSchemaReferenceInfo> unresolvedRefs = new ArrayList<>();

        // First pass: collect all references, separating resolved from unresolved
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdInclude include) {
                Path resolvedPath = include.getResolvedPath();
                String schemaLocation = include.getSchemaLocation();
                String fileName = extractFileName(schemaLocation);

                if (resolvedPath != null) {
                    // Already seen this exact path?
                    if (seenResolvedPaths.contains(resolvedPath)) {
                        continue;
                    }
                    seenResolvedPaths.add(resolvedPath);
                    seenFileNames.add(fileName); // Mark filename as resolved
                    resolvedRefs.add(collectIncludeInfo(include));
                } else {
                    // Collect but will filter later
                    unresolvedRefs.add(collectIncludeInfo(include));
                }
            } else if (child instanceof XsdImport xsdImport) {
                Path resolvedPath = xsdImport.getResolvedPath();
                String schemaLocation = xsdImport.getSchemaLocation();
                String fileName = extractFileName(schemaLocation);

                if (resolvedPath != null) {
                    if (seenResolvedPaths.contains(resolvedPath)) {
                        continue;
                    }
                    seenResolvedPaths.add(resolvedPath);
                    seenFileNames.add(fileName);
                    resolvedRefs.add(collectImportInfo(xsdImport));
                } else {
                    unresolvedRefs.add(collectImportInfo(xsdImport));
                }
            }
        }

        // Add all resolved references
        for (XsdSchemaReferenceInfo ref : resolvedRefs) {
            builder.addSchemaReference(ref);
        }

        // Add unresolved references only if filename wasn't already resolved
        Set<String> seenUnresolvedFileNames = new HashSet<>();
        for (XsdSchemaReferenceInfo ref : unresolvedRefs) {
            String fileName = extractFileName(ref.schemaLocation());
            // Skip if this file was successfully resolved elsewhere
            if (seenFileNames.contains(fileName)) {
                continue;
            }
            // Skip duplicate unresolved references
            if (seenUnresolvedFileNames.contains(fileName)) {
                continue;
            }
            seenUnresolvedFileNames.add(fileName);
            builder.addSchemaReference(ref);
        }
    }

    /**
     * Extracts the filename from a schema location (removes path components).
     */
    private String extractFileName(String schemaLocation) {
        if (schemaLocation == null) {
            return "";
        }
        // Handle both forward and backward slashes
        int lastSlash = Math.max(schemaLocation.lastIndexOf('/'), schemaLocation.lastIndexOf('\\'));
        return lastSlash >= 0 ? schemaLocation.substring(lastSlash + 1) : schemaLocation;
    }

    /**
     * Collects information about an xs:include statement.
     */
    private XsdSchemaReferenceInfo collectIncludeInfo(XsdInclude include) {
        String schemaLocation = include.getSchemaLocation();
        boolean resolved = include.isResolved();
        Path resolvedPath = include.getResolvedPath();
        String errorMessage = include.getResolutionError();

        if (resolved) {
            // Count components from this include
            int[] counts = countComponentsFromSource(include);
            return XsdSchemaReferenceInfo.forResolvedInclude(
                    schemaLocation, resolvedPath,
                    counts[0], counts[1], counts[2]); // elements, types, groups
        } else {
            return XsdSchemaReferenceInfo.forFailedInclude(schemaLocation, errorMessage);
        }
    }

    /**
     * Collects information about an xs:import statement.
     */
    private XsdSchemaReferenceInfo collectImportInfo(XsdImport xsdImport) {
        String schemaLocation = xsdImport.getSchemaLocation();
        String namespace = xsdImport.getNamespace();
        boolean resolved = xsdImport.isResolved();
        Path resolvedPath = xsdImport.getResolvedPath();
        String errorMessage = xsdImport.getResolutionError();

        if (resolved) {
            // Count components from this import
            int[] counts = countComponentsFromSource(xsdImport);
            return XsdSchemaReferenceInfo.forResolvedImport(
                    schemaLocation, namespace, resolvedPath,
                    counts[0], counts[1], counts[2]); // elements, types, groups
        } else {
            return XsdSchemaReferenceInfo.forFailedImport(schemaLocation, namespace, errorMessage);
        }
    }

    /**
     * Counts elements, types, and groups that came from a specific include/import source.
     * Returns int array: [elementCount, typeCount, groupCount]
     * <p>
     * Note: This method matches by resolved file path, not by include node ID.
     * This ensures that duplicate includes (same file included from multiple places)
     * show the correct statistics.
     */
    private int[] countComponentsFromSource(XsdNode sourceNode) {
        int elements = 0;
        int types = 0;
        int groups = 0;

        // Get the resolved path from the include/import node
        Path resolvedPath = null;
        if (sourceNode instanceof XsdInclude include) {
            resolvedPath = include.getResolvedPath();
        } else if (sourceNode instanceof XsdImport xsdImport) {
            resolvedPath = xsdImport.getResolvedPath();
        }

        if (resolvedPath == null) {
            // Fall back to ID-based matching if no resolved path
            return countComponentsFromSourceById(sourceNode.getId());
        }

        // Traverse schema children and count nodes from this source file
        for (XsdNode child : schema.getChildren()) {
            IncludeSourceInfo sourceInfo = child.getSourceInfo();
            if (sourceInfo != null && !sourceInfo.isMainSchema()) {
                Path childSourceFile = sourceInfo.getSourceFile();
                if (childSourceFile != null && childSourceFile.equals(resolvedPath)) {
                    if (child instanceof XsdElement) {
                        elements++;
                    } else if (child instanceof XsdComplexType || child instanceof XsdSimpleType) {
                        types++;
                    } else if (child instanceof XsdGroup || child instanceof XsdAttributeGroup) {
                        groups++;
                    }
                }
            }
        }

        return new int[]{elements, types, groups};
    }

    /**
     * Fallback method that counts components by include node ID.
     * Used when resolved path is not available.
     */
    private int[] countComponentsFromSourceById(String sourceNodeId) {
        int elements = 0;
        int types = 0;
        int groups = 0;

        for (XsdNode child : schema.getChildren()) {
            IncludeSourceInfo sourceInfo = child.getSourceInfo();
            if (sourceInfo != null && !sourceInfo.isMainSchema()) {
                String includeId = sourceInfo.getIncludeNodeId();
                if (sourceNodeId != null && sourceNodeId.equals(includeId)) {
                    if (child instanceof XsdElement) {
                        elements++;
                    } else if (child instanceof XsdComplexType || child instanceof XsdSimpleType) {
                        types++;
                    } else if (child instanceof XsdGroup || child instanceof XsdAttributeGroup) {
                        groups++;
                    }
                }
            }
        }

        return new int[]{elements, types, groups};
    }

    /**
     * Recursively traverses the node tree and collects statistics.
     *
     * @param node       the current node
     * @param builder    the statistics builder
     * @param visitedIds set of visited node IDs to prevent infinite loops
     */
    private void traverseAndCollect(XsdNode node, XsdStatistics.Builder builder, Set<String> visitedIds) {
        if (node == null) {
            return;
        }

        // Cycle detection
        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) {
            logger.trace("Skipping already visited node: {}", nodeId);
            return;
        }
        if (nodeId != null) {
            visitedIds.add(nodeId);
        }

        // Count this node by type
        XsdNodeType nodeType = node.getNodeType();
        if (nodeType != null) {
            builder.incrementNodeCount(nodeType);

            // Also count per source file
            Path sourceFile = getSourceFile(node);
            if (sourceFile != null) {
                builder.incrementNodeCountForFile(sourceFile, nodeType);
            }
        }

        // Collect documentation statistics
        collectDocumentationStats(node, builder);

        // Collect cardinality statistics for elements
        if (node instanceof XsdElement element) {
            collectCardinalityStats(element, builder);
        }

        // Recurse to children
        List<XsdNode> children = node.getChildren();
        if (children != null) {
            for (XsdNode child : children) {
                traverseAndCollect(child, builder, visitedIds);
            }
        }
    }

    /**
     * Gets the source file for a node, using source info or falling back to main schema path.
     */
    private Path getSourceFile(XsdNode node) {
        IncludeSourceInfo sourceInfo = node.getSourceInfo();
        if (sourceInfo != null) {
            return sourceInfo.getSourceFile();
        }
        return schema.getMainSchemaPath();
    }

    /**
     * Collects documentation-related statistics for a node.
     */
    private void collectDocumentationStats(XsdNode node, XsdStatistics.Builder builder) {
        // Skip annotation/documentation/appinfo nodes themselves
        XsdNodeType nodeType = node.getNodeType();
        if (nodeType == XsdNodeType.ANNOTATION ||
                nodeType == XsdNodeType.DOCUMENTATION ||
                nodeType == XsdNodeType.APPINFO) {
            return;
        }

        // Check for documentation
        boolean hasDoc = false;

        // Check single documentation
        String doc = node.getDocumentation();
        if (doc != null && !doc.isBlank()) {
            hasDoc = true;
        }

        // Check multi-language documentations
        List<XsdDocumentation> docs = node.getDocumentations();
        if (docs != null && !docs.isEmpty()) {
            hasDoc = true;
            for (XsdDocumentation xsdDoc : docs) {
                String lang = xsdDoc.getLang();
                if (lang != null && !lang.isBlank()) {
                    builder.addDocumentationLanguage(lang);
                }
            }
        }

        if (hasDoc) {
            builder.incrementNodesWithDocumentation();
        }

        // Check for appinfo
        XsdAppInfo appInfo = node.getAppinfo();
        if (appInfo != null && appInfo.hasEntries()) {
            builder.incrementNodesWithAppInfo();

            // Count appinfo tags
            for (XsdAppInfo.AppInfoEntry entry : appInfo.getEntries()) {
                String tag = entry.getTag();
                if (tag != null && !tag.isBlank()) {
                    builder.incrementAppInfoTag(tag);
                }
            }
        }
    }

    /**
     * Collects cardinality statistics for an element.
     */
    private void collectCardinalityStats(XsdElement element, XsdStatistics.Builder builder) {
        int minOccurs = element.getMinOccurs();
        int maxOccurs = element.getMaxOccurs();

        // Optional: minOccurs = 0
        if (minOccurs == 0) {
            builder.incrementOptionalElements();
        } else {
            builder.incrementRequiredElements();
        }

        // Unbounded: maxOccurs = -1 (UNBOUNDED)
        if (maxOccurs == -1) {
            builder.incrementUnboundedElements();
        }
    }

    /**
     * Collects type usage statistics using TypeUsageFinder.
     */
    private void collectTypeUsageStatistics(XsdStatistics.Builder builder) {
        // Collect all defined type names
        Set<String> definedTypes = collectDefinedTypeNames();

        if (definedTypes.isEmpty()) {
            logger.debug("No user-defined types found in schema");
            return;
        }

        logger.debug("Found {} defined types, analyzing usage...", definedTypes.size());

        TypeUsageFinder usageFinder = new TypeUsageFinder(schema);
        Map<String, Integer> usageCounts = new HashMap<>();
        Set<String> unusedTypes = new HashSet<>();
        List<XsdStatistics.TypeUsageEntry> topUsedTypes = new ArrayList<>();

        for (String typeName : definedTypes) {
            int count = usageFinder.countUsages(typeName);
            usageCounts.put(typeName, count);

            if (count == 0) {
                unusedTypes.add(typeName);
            } else {
                topUsedTypes.add(new XsdStatistics.TypeUsageEntry(typeName, count));
            }
        }

        // Sort by usage count (descending) and take top 10
        Collections.sort(topUsedTypes);
        if (topUsedTypes.size() > 10) {
            topUsedTypes = topUsedTypes.subList(0, 10);
        }

        builder.typeUsageCounts(usageCounts);
        builder.topUsedTypes(topUsedTypes);
        builder.unusedTypes(unusedTypes);

        logger.debug("Type usage analysis complete: {} unused types", unusedTypes.size());
    }

    /**
     * Collects all defined type names (ComplexTypes and SimpleTypes).
     */
    private Set<String> collectDefinedTypeNames() {
        Set<String> typeNames = new HashSet<>();
        collectTypeNamesRecursive(schema, typeNames, new HashSet<>());
        return typeNames;
    }

    /**
     * Recursively collects type names from the node tree.
     */
    private void collectTypeNamesRecursive(XsdNode node, Set<String> typeNames, Set<String> visitedIds) {
        if (node == null) {
            return;
        }

        // Cycle detection
        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) {
            return;
        }
        if (nodeId != null) {
            visitedIds.add(nodeId);
        }

        // Collect named types
        if (node instanceof XsdComplexType complexType) {
            String name = complexType.getName();
            if (name != null && !name.isBlank()) {
                typeNames.add(name);
            }
        } else if (node instanceof XsdSimpleType simpleType) {
            String name = simpleType.getName();
            if (name != null && !name.isBlank()) {
                typeNames.add(name);
            }
        }

        // Recurse to children
        List<XsdNode> children = node.getChildren();
        if (children != null) {
            for (XsdNode child : children) {
                collectTypeNamesRecursive(child, typeNames, visitedIds);
            }
        }
    }
}
