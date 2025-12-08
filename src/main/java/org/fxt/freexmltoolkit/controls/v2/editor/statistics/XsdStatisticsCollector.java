package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageFinder;
import org.fxt.freexmltoolkit.controls.v2.model.*;

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

        // Traverse tree and collect node statistics
        Set<String> visitedIds = new HashSet<>();
        traverseAndCollect(schema, builder, visitedIds);

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
