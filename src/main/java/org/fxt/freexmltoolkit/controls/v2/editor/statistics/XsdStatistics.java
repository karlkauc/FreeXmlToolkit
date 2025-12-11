package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Immutable data model containing comprehensive statistics about an XSD schema.
 * Uses the Builder pattern for construction.
 *
 * @since 2.0
 */
public record XsdStatistics(
        // Schema Information
        String xsdVersion,
        String targetNamespace,
        String elementFormDefault,
        String attributeFormDefault,
        int namespaceCount,
        int fileCount,
        Path mainSchemaPath,
        Set<Path> includedFiles,

        // Node Counts (by type)
        Map<XsdNodeType, Integer> nodeCountsByType,
        int totalNodeCount,

        // Documentation Statistics
        int nodesWithDocumentation,
        int nodesWithAppInfo,
        double documentationCoveragePercent,
        Map<String, Integer> appInfoTagCounts,
        Set<String> documentationLanguages,

        // Type Usage Statistics
        Map<String, Integer> typeUsageCounts,
        List<TypeUsageEntry> topUsedTypes,
        Set<String> unusedTypes,

        // Cardinality Statistics
        int optionalElements,
        int requiredElements,
        int unboundedElements,

        // Schema References (includes/imports)
        List<XsdSchemaReferenceInfo> schemaReferences,
        Map<Path, Map<XsdNodeType, Integer>> nodeCountsByFile,
        int unresolvedReferencesCount,

        // Metadata
        LocalDateTime collectedAt
) {
    /**
     * Entry for type usage statistics.
     */
    public record TypeUsageEntry(String typeName, int usageCount) implements Comparable<TypeUsageEntry> {
        @Override
        public int compareTo(TypeUsageEntry other) {
            return Integer.compare(other.usageCount, this.usageCount); // Descending order
        }
    }

    /**
     * Builder for constructing XsdStatistics instances.
     */
    public static class Builder {
        // Schema Information
        private String xsdVersion = "1.0";
        private String targetNamespace = "";
        private String elementFormDefault = "unqualified";
        private String attributeFormDefault = "unqualified";
        private int namespaceCount = 0;
        private int fileCount = 1;
        private Path mainSchemaPath;
        private Set<Path> includedFiles = new HashSet<>();

        // Node Counts
        private Map<XsdNodeType, Integer> nodeCountsByType = new EnumMap<>(XsdNodeType.class);
        private int totalNodeCount = 0;

        // Documentation Statistics
        private int nodesWithDocumentation = 0;
        private int nodesWithAppInfo = 0;
        private double documentationCoveragePercent = 0.0;
        private Map<String, Integer> appInfoTagCounts = new HashMap<>();
        private Set<String> documentationLanguages = new HashSet<>();

        // Type Usage Statistics
        private Map<String, Integer> typeUsageCounts = new HashMap<>();
        private List<TypeUsageEntry> topUsedTypes = new ArrayList<>();
        private Set<String> unusedTypes = new HashSet<>();

        // Cardinality Statistics
        private int optionalElements = 0;
        private int requiredElements = 0;
        private int unboundedElements = 0;

        // Schema References
        private List<XsdSchemaReferenceInfo> schemaReferences = new ArrayList<>();
        private Map<Path, Map<XsdNodeType, Integer>> nodeCountsByFile = new HashMap<>();
        private int unresolvedReferencesCount = 0;

        public Builder() {
            // Initialize all node types with 0 count
            for (XsdNodeType type : XsdNodeType.values()) {
                nodeCountsByType.put(type, 0);
            }
        }

        // Schema Information setters
        public Builder xsdVersion(String xsdVersion) {
            this.xsdVersion = xsdVersion;
            return this;
        }

        public Builder targetNamespace(String targetNamespace) {
            this.targetNamespace = targetNamespace != null ? targetNamespace : "";
            return this;
        }

        public Builder elementFormDefault(String elementFormDefault) {
            this.elementFormDefault = elementFormDefault != null ? elementFormDefault : "unqualified";
            return this;
        }

        public Builder attributeFormDefault(String attributeFormDefault) {
            this.attributeFormDefault = attributeFormDefault != null ? attributeFormDefault : "unqualified";
            return this;
        }

        public Builder namespaceCount(int namespaceCount) {
            this.namespaceCount = namespaceCount;
            return this;
        }

        public Builder fileCount(int fileCount) {
            this.fileCount = fileCount;
            return this;
        }

        public Builder mainSchemaPath(Path mainSchemaPath) {
            this.mainSchemaPath = mainSchemaPath;
            return this;
        }

        public Builder includedFiles(Set<Path> includedFiles) {
            this.includedFiles = includedFiles != null ? new HashSet<>(includedFiles) : new HashSet<>();
            return this;
        }

        // Node Count setters
        public Builder incrementNodeCount(XsdNodeType type) {
            nodeCountsByType.merge(type, 1, Integer::sum);
            totalNodeCount++;
            return this;
        }

        public Builder nodeCountsByType(Map<XsdNodeType, Integer> counts) {
            this.nodeCountsByType = new EnumMap<>(XsdNodeType.class);
            this.nodeCountsByType.putAll(counts);
            this.totalNodeCount = counts.values().stream().mapToInt(Integer::intValue).sum();
            return this;
        }

        // Documentation Statistics setters
        public Builder nodesWithDocumentation(int count) {
            this.nodesWithDocumentation = count;
            return this;
        }

        public Builder nodesWithAppInfo(int count) {
            this.nodesWithAppInfo = count;
            return this;
        }

        public Builder documentationCoveragePercent(double percent) {
            this.documentationCoveragePercent = percent;
            return this;
        }

        public Builder appInfoTagCounts(Map<String, Integer> counts) {
            this.appInfoTagCounts = counts != null ? new HashMap<>(counts) : new HashMap<>();
            return this;
        }

        public Builder incrementAppInfoTag(String tag) {
            appInfoTagCounts.merge(tag, 1, Integer::sum);
            return this;
        }

        public Builder documentationLanguages(Set<String> languages) {
            this.documentationLanguages = languages != null ? new HashSet<>(languages) : new HashSet<>();
            return this;
        }

        public Builder addDocumentationLanguage(String lang) {
            if (lang != null && !lang.isBlank()) {
                documentationLanguages.add(lang);
            }
            return this;
        }

        // Type Usage setters
        public Builder typeUsageCounts(Map<String, Integer> counts) {
            this.typeUsageCounts = counts != null ? new HashMap<>(counts) : new HashMap<>();
            return this;
        }

        public Builder topUsedTypes(List<TypeUsageEntry> topTypes) {
            this.topUsedTypes = topTypes != null ? new ArrayList<>(topTypes) : new ArrayList<>();
            return this;
        }

        public Builder unusedTypes(Set<String> unused) {
            this.unusedTypes = unused != null ? new HashSet<>(unused) : new HashSet<>();
            return this;
        }

        // Cardinality setters
        public Builder optionalElements(int count) {
            this.optionalElements = count;
            return this;
        }

        public Builder requiredElements(int count) {
            this.requiredElements = count;
            return this;
        }

        public Builder unboundedElements(int count) {
            this.unboundedElements = count;
            return this;
        }

        public Builder incrementOptionalElements() {
            this.optionalElements++;
            return this;
        }

        public Builder incrementRequiredElements() {
            this.requiredElements++;
            return this;
        }

        public Builder incrementUnboundedElements() {
            this.unboundedElements++;
            return this;
        }

        public Builder incrementNodesWithDocumentation() {
            this.nodesWithDocumentation++;
            return this;
        }

        public Builder incrementNodesWithAppInfo() {
            this.nodesWithAppInfo++;
            return this;
        }

        // Schema References setters
        public Builder schemaReferences(List<XsdSchemaReferenceInfo> references) {
            this.schemaReferences = references != null ? new ArrayList<>(references) : new ArrayList<>();
            return this;
        }

        public Builder addSchemaReference(XsdSchemaReferenceInfo reference) {
            if (reference != null) {
                schemaReferences.add(reference);
                if (!reference.resolved()) {
                    unresolvedReferencesCount++;
                }
            }
            return this;
        }

        public Builder nodeCountsByFile(Map<Path, Map<XsdNodeType, Integer>> counts) {
            this.nodeCountsByFile = counts != null ? new HashMap<>(counts) : new HashMap<>();
            return this;
        }

        public Builder incrementNodeCountForFile(Path file, XsdNodeType type) {
            if (file != null && type != null) {
                nodeCountsByFile
                        .computeIfAbsent(file, k -> new EnumMap<>(XsdNodeType.class))
                        .merge(type, 1, Integer::sum);
            }
            return this;
        }

        public Builder unresolvedReferencesCount(int count) {
            this.unresolvedReferencesCount = count;
            return this;
        }

        /**
         * Calculates the documentation coverage percentage based on current counts.
         */
        public Builder calculateDocumentationCoverage() {
            if (totalNodeCount > 0) {
                // Exclude SCHEMA, ANNOTATION, DOCUMENTATION, APPINFO from coverage calculation
                int documentableNodes = totalNodeCount
                        - nodeCountsByType.getOrDefault(XsdNodeType.SCHEMA, 0)
                        - nodeCountsByType.getOrDefault(XsdNodeType.ANNOTATION, 0)
                        - nodeCountsByType.getOrDefault(XsdNodeType.DOCUMENTATION, 0)
                        - nodeCountsByType.getOrDefault(XsdNodeType.APPINFO, 0);
                if (documentableNodes > 0) {
                    this.documentationCoveragePercent = (nodesWithDocumentation * 100.0) / documentableNodes;
                }
            }
            return this;
        }

        public XsdStatistics build() {
            // Create immutable copy of nodeCountsByFile
            Map<Path, Map<XsdNodeType, Integer>> immutableNodeCountsByFile = new HashMap<>();
            for (Map.Entry<Path, Map<XsdNodeType, Integer>> entry : nodeCountsByFile.entrySet()) {
                immutableNodeCountsByFile.put(entry.getKey(),
                        Collections.unmodifiableMap(new EnumMap<>(entry.getValue())));
            }

            return new XsdStatistics(
                    xsdVersion,
                    targetNamespace,
                    elementFormDefault,
                    attributeFormDefault,
                    namespaceCount,
                    fileCount,
                    mainSchemaPath,
                    Collections.unmodifiableSet(new HashSet<>(includedFiles)),
                    Collections.unmodifiableMap(new EnumMap<>(nodeCountsByType)),
                    totalNodeCount,
                    nodesWithDocumentation,
                    nodesWithAppInfo,
                    documentationCoveragePercent,
                    Collections.unmodifiableMap(new HashMap<>(appInfoTagCounts)),
                    Collections.unmodifiableSet(new HashSet<>(documentationLanguages)),
                    Collections.unmodifiableMap(new HashMap<>(typeUsageCounts)),
                    Collections.unmodifiableList(new ArrayList<>(topUsedTypes)),
                    Collections.unmodifiableSet(new HashSet<>(unusedTypes)),
                    optionalElements,
                    requiredElements,
                    unboundedElements,
                    Collections.unmodifiableList(new ArrayList<>(schemaReferences)),
                    Collections.unmodifiableMap(immutableNodeCountsByFile),
                    unresolvedReferencesCount,
                    LocalDateTime.now()
            );
        }
    }

    /**
     * Creates a new Builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the count for a specific node type.
     */
    public int getNodeCount(XsdNodeType type) {
        return nodeCountsByType.getOrDefault(type, 0);
    }

    /**
     * Gets commonly accessed node counts.
     */
    public int getElementCount() {
        return getNodeCount(XsdNodeType.ELEMENT);
    }

    public int getAttributeCount() {
        return getNodeCount(XsdNodeType.ATTRIBUTE);
    }

    public int getComplexTypeCount() {
        return getNodeCount(XsdNodeType.COMPLEX_TYPE);
    }

    public int getSimpleTypeCount() {
        return getNodeCount(XsdNodeType.SIMPLE_TYPE);
    }

    public int getGroupCount() {
        return getNodeCount(XsdNodeType.GROUP);
    }

    public int getAttributeGroupCount() {
        return getNodeCount(XsdNodeType.ATTRIBUTE_GROUP);
    }

    public int getImportCount() {
        return getNodeCount(XsdNodeType.IMPORT);
    }

    public int getIncludeCount() {
        return getNodeCount(XsdNodeType.INCLUDE);
    }

    /**
     * Returns whether this is an XSD 1.1 schema.
     */
    public boolean isXsd11() {
        return "1.1".equals(xsdVersion);
    }

    /**
     * Returns the number of included/imported schemas.
     */
    public int getExternalSchemaCount() {
        return getImportCount() + getIncludeCount();
    }

    /**
     * Returns the number of resolved schema references.
     */
    public int getResolvedReferencesCount() {
        return (int) schemaReferences.stream().filter(XsdSchemaReferenceInfo::resolved).count();
    }

    /**
     * Returns whether there are any unresolved schema references.
     */
    public boolean hasUnresolvedReferences() {
        return unresolvedReferencesCount > 0;
    }

    /**
     * Gets the total number of schema references (includes + imports).
     */
    public int getTotalReferencesCount() {
        return schemaReferences.size();
    }

    /**
     * Gets the node counts for a specific file.
     */
    public Map<XsdNodeType, Integer> getNodeCountsForFile(Path file) {
        return nodeCountsByFile.getOrDefault(file, Collections.emptyMap());
    }

    /**
     * Gets the total node count for a specific file.
     */
    public int getTotalNodeCountForFile(Path file) {
        return getNodeCountsForFile(file).values().stream().mapToInt(Integer::intValue).sum();
    }
}
