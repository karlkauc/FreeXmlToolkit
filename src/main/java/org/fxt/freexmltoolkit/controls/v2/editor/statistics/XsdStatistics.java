package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * Immutable data model containing comprehensive statistics about an XSD schema.
 * Uses the Builder pattern for construction.
 *
 * @param xsdVersion The XSD version
 * @param targetNamespace The target namespace
 * @param elementFormDefault The element form default
 * @param attributeFormDefault The attribute form default
 * @param namespaceCount The number of namespaces
 * @param fileCount The number of files
 * @param mainSchemaPath The main schema path
 * @param includedFiles The set of included files
 * @param nodeCountsByType Map of node counts by type
 * @param totalNodeCount The total number of nodes
 * @param nodesWithDocumentation Number of nodes with documentation
 * @param nodesWithAppInfo Number of nodes with app info
 * @param documentationCoveragePercent Documentation coverage percentage
 * @param appInfoTagCounts Map of app info tag counts
 * @param documentationLanguages Set of documentation languages
 * @param typeUsageCounts Map of type usage counts
 * @param topUsedTypes List of top used types
 * @param unusedTypes Set of unused types
 * @param optionalElements Number of optional elements
 * @param requiredElements Number of required elements
 * @param unboundedElements Number of unbounded elements
 * @param schemaReferences List of schema references
 * @param nodeCountsByFile Map of node counts by file
 * @param unresolvedReferencesCount Number of unresolved references
 * @param componentUsage Usage of groups/attribute groups, unreachable components, single-use types and cycles
 * @param complexity Structural complexity metrics
 * @param collectedAt Timestamp when statistics were collected
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

        // Component usage beyond named types (groups, reachability, cycles)
        ComponentUsage componentUsage,

        // Structural complexity metrics
        ComplexityMetrics complexity,

        // Metadata
        LocalDateTime collectedAt
) {
    /**
     * Entry for type usage statistics.
     * @param typeName The type name
     * @param usageCount The usage count
     */
    public record TypeUsageEntry(String typeName, int usageCount) implements Comparable<TypeUsageEntry> {
        @Override
        public int compareTo(TypeUsageEntry other) {
            return Integer.compare(other.usageCount, this.usageCount); // Descending order
        }
    }

    /**
     * Usage facts about global components that go beyond the named-type counters.
     *
     * @param unusedGroups           global {@code xs:group}s never referenced
     * @param unusedAttributeGroups  global {@code xs:attributeGroup}s never referenced
     * @param unreachableComponents  types/groups/attribute groups no global element or attribute
     *                               reaches, directly or transitively ("Kind 'name'" labels,
     *                               declaration order) — the cascading unused set
     * @param singleUseTypes         named types referenced exactly once (inline candidates)
     * @param containmentCycles      reference cycles over all edges (recursive content models),
     *                               each as its member labels
     * @param derivationCycles       cycles over base/itemType/memberTypes only (invalid schemas)
     */
    public record ComponentUsage(Set<String> unusedGroups,
                                 Set<String> unusedAttributeGroups,
                                 List<String> unreachableComponents,
                                 List<String> singleUseTypes,
                                 List<List<String>> containmentCycles,
                                 List<List<String>> derivationCycles) {

        public static final ComponentUsage EMPTY = new ComponentUsage(Set.of(), Set.of(), List.of(), List.of(), List.of(), List.of());

        public ComponentUsage {
            unusedGroups = unusedGroups == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(unusedGroups));
            unusedAttributeGroups = unusedAttributeGroups == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(unusedAttributeGroups));
            unreachableComponents = unreachableComponents == null ? List.of() : List.copyOf(unreachableComponents);
            singleUseTypes = singleUseTypes == null ? List.of() : List.copyOf(singleUseTypes);
            containmentCycles = containmentCycles == null ? List.of() : List.copyOf(containmentCycles);
            derivationCycles = derivationCycles == null ? List.of() : List.copyOf(derivationCycles);
        }

        /** @return unused groups + unused attribute groups. */
        public int unusedGroupCount() {
            return unusedGroups.size() + unusedAttributeGroups.size();
        }
    }

    /**
     * Structural complexity metrics of the schema.
     *
     * @param maxElementNestingDepth     deepest lexical element nesting (element inside element,
     *                                   anonymous types only — type references are not followed)
     * @param deepestElementXPath        the XPath of that deepest element, or {@code null}
     * @param avgChildrenPerComplexType  average number of element/attribute declarations directly
     *                                   in a complex type's content model
     * @param maxChildrenPerComplexType  the largest such content model
     * @param widestComplexType          name (or XPath when anonymous) of the widest complex type
     * @param maxTypeDerivationDepth     number of derivation steps in the longest base-type chain
     *                                   among named types (1 = derives from a built-in or a
     *                                   non-derived type, 0 = no derived types)
     * @param abstractTypes              {@code abstract="true"} complex types
     * @param abstractElements           {@code abstract="true"} elements
     * @param substitutionGroupHeads     distinct elements named as a substitution group head
     * @param substitutionGroupMembers   elements declaring {@code substitutionGroup}
     * @param anonymousComplexTypes      complex types without a name (inline)
     * @param anonymousSimpleTypes       simple types without a name (inline)
     * @param mixedContentTypes          complex types with {@code mixed="true"}
     * @param extensions                 {@code xs:extension} derivations
     * @param restrictions               {@code xs:restriction} derivations (simple and complex)
     * @param facetCounts                facets by kind
     * @param enumerationValues          total {@code xs:enumeration} values
     * @param xsd11Features              {@code xs:assert}, {@code xs:alternative},
     *                                   {@code xs:openContent} and XSD 1.1 facets
     */
    public record ComplexityMetrics(int maxElementNestingDepth,
                                    String deepestElementXPath,
                                    double avgChildrenPerComplexType,
                                    int maxChildrenPerComplexType,
                                    String widestComplexType,
                                    int maxTypeDerivationDepth,
                                    int abstractTypes,
                                    int abstractElements,
                                    int substitutionGroupHeads,
                                    int substitutionGroupMembers,
                                    int anonymousComplexTypes,
                                    int anonymousSimpleTypes,
                                    int mixedContentTypes,
                                    int extensions,
                                    int restrictions,
                                    Map<XsdFacetType, Integer> facetCounts,
                                    int enumerationValues,
                                    int xsd11Features) {

        public static final ComplexityMetrics EMPTY = new ComplexityMetrics(
                0, null, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), 0, 0);

        public ComplexityMetrics {
            Map<XsdFacetType, Integer> copy = new EnumMap<>(XsdFacetType.class);
            if (facetCounts != null) {
                copy.putAll(facetCounts);
            }
            facetCounts = Collections.unmodifiableMap(copy);
        }

        /** @return total number of facets of any kind. */
        public int totalFacets() {
            return facetCounts.values().stream().mapToInt(Integer::intValue).sum();
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

        // Component usage / complexity
        private ComponentUsage componentUsage = ComponentUsage.EMPTY;
        private ComplexityMetrics complexity = ComplexityMetrics.EMPTY;

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

        public Builder componentUsage(ComponentUsage usage) {
            this.componentUsage = usage != null ? usage : ComponentUsage.EMPTY;
            return this;
        }

        public Builder complexity(ComplexityMetrics metrics) {
            this.complexity = metrics != null ? metrics : ComplexityMetrics.EMPTY;
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
                    componentUsage,
                    complexity,
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
