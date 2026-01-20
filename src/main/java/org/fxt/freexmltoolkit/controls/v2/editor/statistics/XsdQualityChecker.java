package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Checks XSD schema quality including naming conventions, best practices,
 * and deprecated element detection.
 *
 * @since 2.0
 */
public class XsdQualityChecker {

    private static final Logger logger = LogManager.getLogger(XsdQualityChecker.class);

    private final XsdSchema schema;

    // Naming patterns
    private static final Pattern CAMEL_CASE = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");
    private static final Pattern LOWER_CAMEL_CASE = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern KEBAB_CASE = Pattern.compile("^[a-z][a-z0-9-]*$");

    /**
     * Issue category.
     */
    public enum IssueCategory {
        NAMING_CONVENTION,
        BEST_PRACTICE,
        DEPRECATED,
        CONSTRAINT_CONFLICT,
        INCONSISTENT_DEFINITION,
        DUPLICATE_DEFINITION,
        DUPLICATE_ELEMENT_IN_CONTAINER
    }

    /**
     * Issue severity.
     */
    public enum IssueSeverity {
        ERROR,
        WARNING,
        INFO,
        SUGGESTION
    }

    /**
     * Naming convention type.
     */
    public enum NamingConvention {
        UPPER_CAMEL_CASE("UpperCamelCase"),  // PascalCase
        LOWER_CAMEL_CASE("lowerCamelCase"),
        SNAKE_CASE("snake_case"),
        KEBAB_CASE("kebab-case"),
        UNKNOWN("Unknown");

        private final String displayName;

        NamingConvention(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Quality issue record.
     */
    /**
     * Represents a quality issue found in the schema.
     * @param category The issue category
     * @param severity The severity level
     * @param message The issue message
     * @param suggestion Suggestion for fixing
     * @param affectedElements List of affected element names
     * @param sourceNode The source node in the DOM
     * @param xpath The XPath to the source node
     * @param sourceFile The source file path
     */
    public record QualityIssue(
            IssueCategory category,
            IssueSeverity severity,
            String message,
            String suggestion,
            List<String> affectedElements,
            XsdNode sourceNode,
            String xpath,
            Path sourceFile
    ) {
        /**
         * Creates a naming convention issue.
         */
        public static QualityIssue namingIssue(IssueSeverity severity, String message,
                                               String suggestion, List<String> affected) {
            return new QualityIssue(IssueCategory.NAMING_CONVENTION, severity, message, suggestion, affected, null, null, null);
        }

        /**
         * Creates a best practice issue.
         */
        public static QualityIssue bestPracticeIssue(IssueSeverity severity, String message,
                                                     String suggestion, List<String> affected, XsdNode node) {
            String xpath = node != null ? node.getXPath() : null;
            Path sourceFile = getSourceFileFromNode(node);
            return new QualityIssue(IssueCategory.BEST_PRACTICE, severity, message, suggestion, affected, node, xpath, sourceFile);
        }

        /**
         * Creates a deprecated element issue.
         */
        public static QualityIssue deprecatedIssue(String elementName, String deprecationMessage,
                                                   String alternative, XsdNode node) {
            String xpath = node != null ? node.getXPath() : null;
            Path sourceFile = getSourceFileFromNode(node);
            return new QualityIssue(
                    IssueCategory.DEPRECATED,
                    IssueSeverity.WARNING,
                    elementName + " is deprecated" + (deprecationMessage != null ? ": " + deprecationMessage : ""),
                    alternative != null ? "Use " + alternative + " instead" : null,
                    List.of(elementName),
                    node,
                    xpath,
                    sourceFile
            );
        }

        /**
         * Creates a constraint conflict issue.
         */
        public static QualityIssue constraintConflictIssue(IssueSeverity severity, String message,
                                                           String suggestion, List<String> affected, XsdNode node) {
            String xpath = node != null ? node.getXPath() : null;
            Path sourceFile = getSourceFileFromNode(node);
            return new QualityIssue(IssueCategory.CONSTRAINT_CONFLICT, severity, message, suggestion, affected, node, xpath, sourceFile);
        }

        /**
         * Creates an inconsistent definition issue (same name, different content).
         */
        public static QualityIssue inconsistentDefinitionIssue(String message, String suggestion,
                                                                List<String> affected, XsdNode node) {
            String xpath = node != null ? node.getXPath() : null;
            Path sourceFile = getSourceFileFromNode(node);
            return new QualityIssue(IssueCategory.INCONSISTENT_DEFINITION, IssueSeverity.WARNING, message, suggestion, affected, node, xpath, sourceFile);
        }

        /**
         * Creates a duplicate definition issue (different name, same content).
         */
        public static QualityIssue duplicateDefinitionIssue(String message, String suggestion,
                                                             List<String> affected, XsdNode node) {
            String xpath = node != null ? node.getXPath() : null;
            Path sourceFile = getSourceFileFromNode(node);
            return new QualityIssue(IssueCategory.DUPLICATE_DEFINITION, IssueSeverity.INFO, message, suggestion, affected, node, xpath, sourceFile);
        }

        /**
         * Creates a duplicate element in container issue (same element name appears multiple times in sequence/choice/all).
         * This is a schema error as it creates ambiguous element ordering.
         */
        public static QualityIssue duplicateElementInContainerIssue(String message, String suggestion,
                                                                     List<String> affected, XsdNode containerNode) {
            String xpath = containerNode != null ? containerNode.getXPath() : null;
            Path sourceFile = getSourceFileFromNode(containerNode);
            return new QualityIssue(IssueCategory.DUPLICATE_ELEMENT_IN_CONTAINER, IssueSeverity.ERROR, message, suggestion, affected, containerNode, xpath, sourceFile);
        }

        /**
         * Gets the source file name for display (without full path).
         */
        public String getSourceFileName() {
            if (sourceFile == null) {
                return "main";
            }
            java.nio.file.Path fileName = sourceFile.getFileName();
            return fileName != null ? fileName.toString() : "main";
        }

        /**
         * Helper to extract source file from node's source info.
         */
        private static Path getSourceFileFromNode(XsdNode node) {
            if (node == null) {
                return null;
            }
            IncludeSourceInfo sourceInfo = node.getSourceInfo();
            if (sourceInfo != null) {
                return sourceInfo.getSourceFile();
            }
            return null;
        }
    }

    /**
     * Quality check result.
     */
    /**
     * Result of quality analysis.
     * @param score The quality score
     * @param dominantNamingConvention The dominant naming convention
     * @param namingDistribution Distribution of naming conventions
     * @param issues List of quality issues
     * @param totalChecks Total number of checks performed
     * @param passedChecks Number of passed checks
     */
    public record QualityResult(
            int score,
            NamingConvention dominantNamingConvention,
            Map<NamingConvention, Integer> namingDistribution,
            List<QualityIssue> issues,
            int totalChecks,
            int passedChecks
    ) {
        /**
         * Gets issues by category.
         */
        public List<QualityIssue> getIssuesByCategory(IssueCategory category) {
            return issues.stream()
                    .filter(i -> i.category() == category)
                    .toList();
        }

        /**
         * Gets the score description.
         */
        public String getScoreDescription() {
            if (score >= 90) return "Excellent";
            if (score >= 75) return "Good";
            if (score >= 60) return "Fair";
            if (score >= 40) return "Needs Improvement";
            return "Poor";
        }
    }

    /**
     * Creates a new quality checker for the given schema.
     *
     * @param schema the XSD schema to check
     */
    public XsdQualityChecker(XsdSchema schema) {
        Objects.requireNonNull(schema, "Schema cannot be null");
        this.schema = schema;
    }

    /**
     * Runs all quality checks.
     *
     * @return the quality check result
     */
    public QualityResult check() {
        logger.info("Starting quality checks for schema");
        long startTime = System.currentTimeMillis();

        List<QualityIssue> issues = new ArrayList<>();

        // Collect naming statistics
        Map<NamingConvention, List<String>> namingByConvention = new EnumMap<>(NamingConvention.class);
        for (NamingConvention conv : NamingConvention.values()) {
            namingByConvention.put(conv, new ArrayList<>());
        }

        // Track best practice metrics
        int localTypesCount = 0;
        int globalTypesCount = 0;
        List<String> unboundedElements = new ArrayList<>();
        List<String> anyElements = new ArrayList<>();
        int maxNestingDepth = 0;

        // Track deprecated elements
        List<QualityIssue> deprecatedIssues = new ArrayList<>();

        // Traverse main schema (includes are already inlined as children)
        Set<String> visitedIds = new HashSet<>();
        traverseAndCheck(schema, namingByConvention, issues, deprecatedIssues, visitedIds, 0);

        // Also traverse imported schemas (they are NOT children of main schema)
        for (Map.Entry<String, XsdSchema> entry : schema.getImportedSchemas().entrySet()) {
            logger.debug("Checking quality for imported schema: {}", entry.getKey());
            traverseAndCheck(entry.getValue(), namingByConvention, issues, deprecatedIssues, visitedIds, 0);
        }

        // Check for inconsistent definitions (same name, different content)
        checkInconsistentDefinitions(issues);

        // Check for duplicate definitions (different name, same content)
        checkDuplicateDefinitions(issues);

        // Check for duplicate elements within containers (sequence/choice/all) - schema error
        checkDuplicateElementsInContainers(issues);

        // Calculate naming distribution
        Map<NamingConvention, Integer> namingDistribution = new EnumMap<>(NamingConvention.class);
        for (Map.Entry<NamingConvention, List<String>> entry : namingByConvention.entrySet()) {
            namingDistribution.put(entry.getKey(), entry.getValue().size());
        }

        // Find dominant naming convention
        NamingConvention dominant = findDominantConvention(namingDistribution);

        // Check naming consistency
        checkNamingConsistency(namingByConvention, dominant, issues);

        // Add deprecated issues
        issues.addAll(deprecatedIssues);

        // Calculate score
        int totalChecks = calculateTotalChecks(namingByConvention);
        int passedChecks = totalChecks - issues.stream()
                .filter(i -> i.severity() == IssueSeverity.ERROR || i.severity() == IssueSeverity.WARNING)
                .mapToInt(i -> i.affectedElements().size())
                .sum();
        int score = totalChecks > 0 ? Math.max(0, Math.min(100, (passedChecks * 100) / totalChecks)) : 100;

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Quality check completed in {}ms: score={}, {} issues", duration, score, issues.size());

        return new QualityResult(
                score,
                dominant,
                Collections.unmodifiableMap(namingDistribution),
                Collections.unmodifiableList(issues),
                totalChecks,
                passedChecks
        );
    }

    /**
     * Traverses the schema and collects quality information.
     */
    private void traverseAndCheck(XsdNode node, Map<NamingConvention, List<String>> namingByConvention,
                                  List<QualityIssue> issues, List<QualityIssue> deprecatedIssues,
                                  Set<String> visitedIds, int depth) {
        if (node == null) return;

        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) return;
        if (nodeId != null) visitedIds.add(nodeId);

        String name = node.getName();

        // Check naming convention for named elements
        if (name != null && !name.isBlank() && shouldCheckNaming(node)) {
            NamingConvention convention = detectNamingConvention(name);
            namingByConvention.get(convention).add(name);
        }

        // Check for deprecated elements
        checkDeprecated(node, deprecatedIssues);

        // Best practice checks
        checkBestPractices(node, issues, depth);

        // Check for length/enumeration conflicts
        checkLengthEnumerationConflict(node, issues);

        // Recurse to children
        for (XsdNode child : node.getChildren()) {
            traverseAndCheck(child, namingByConvention, issues, deprecatedIssues, visitedIds, depth + 1);
        }
    }

    /**
     * Determines if naming should be checked for this node.
     */
    private boolean shouldCheckNaming(XsdNode node) {
        return node instanceof XsdElement ||
                node instanceof XsdAttribute ||
                node instanceof XsdComplexType ||
                node instanceof XsdSimpleType ||
                node instanceof XsdGroup ||
                node instanceof XsdAttributeGroup;
    }

    /**
     * Detects the naming convention of a name.
     */
    private NamingConvention detectNamingConvention(String name) {
        if (name == null || name.isEmpty()) {
            return NamingConvention.UNKNOWN;
        }

        if (CAMEL_CASE.matcher(name).matches()) {
            return NamingConvention.UPPER_CAMEL_CASE;
        }
        if (LOWER_CAMEL_CASE.matcher(name).matches()) {
            return NamingConvention.LOWER_CAMEL_CASE;
        }
        if (SNAKE_CASE.matcher(name).matches()) {
            return NamingConvention.SNAKE_CASE;
        }
        if (KEBAB_CASE.matcher(name).matches()) {
            return NamingConvention.KEBAB_CASE;
        }

        return NamingConvention.UNKNOWN;
    }

    /**
     * Finds the dominant naming convention.
     */
    private NamingConvention findDominantConvention(Map<NamingConvention, Integer> distribution) {
        NamingConvention dominant = NamingConvention.UNKNOWN;
        int maxCount = 0;

        for (Map.Entry<NamingConvention, Integer> entry : distribution.entrySet()) {
            if (entry.getKey() != NamingConvention.UNKNOWN && entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }

        return dominant;
    }

    /**
     * Checks naming consistency and creates issues for inconsistencies.
     */
    private void checkNamingConsistency(Map<NamingConvention, List<String>> namingByConvention,
                                        NamingConvention dominant, List<QualityIssue> issues) {
        if (dominant == NamingConvention.UNKNOWN) {
            return;
        }

        // Check for elements using different conventions
        for (Map.Entry<NamingConvention, List<String>> entry : namingByConvention.entrySet()) {
            NamingConvention convention = entry.getKey();
            List<String> elements = entry.getValue();

            if (convention != dominant && convention != NamingConvention.UNKNOWN && !elements.isEmpty()) {
                issues.add(QualityIssue.namingIssue(
                        IssueSeverity.WARNING,
                        elements.size() + " element(s) use " + convention.getDisplayName() +
                                " while dominant convention is " + dominant.getDisplayName(),
                        "Consider renaming to " + dominant.getDisplayName() + " for consistency",
                        new ArrayList<>(elements)
                ));
            }
        }
    }

    /**
     * Checks for deprecated elements.
     */
    private void checkDeprecated(XsdNode node, List<QualityIssue> deprecatedIssues) {
        XsdAppInfo appInfo = node.getAppinfo();
        if (appInfo != null && appInfo.isDeprecated()) {
            String deprecationMessage = appInfo.getDeprecated();
            String alternative = extractAlternative(deprecationMessage);

            deprecatedIssues.add(QualityIssue.deprecatedIssue(
                    node.getName() != null ? node.getName() : node.getNodeType().name(),
                    deprecationMessage,
                    alternative,
                    node
            ));
        }
    }

    /**
     * Extracts alternative suggestion from deprecation message.
     */
    private String extractAlternative(String message) {
        if (message == null) return null;

        // Look for patterns like "use X instead" or "replaced by X"
        String lower = message.toLowerCase();
        int useIndex = lower.indexOf("use ");
        if (useIndex >= 0) {
            String remaining = message.substring(useIndex + 4).trim();
            int endIndex = remaining.indexOf(' ');
            if (endIndex > 0) {
                return remaining.substring(0, endIndex);
            }
            return remaining;
        }

        return null;
    }

    /**
     * Checks best practices.
     */
    private void checkBestPractices(XsdNode node, List<QualityIssue> issues, int depth) {
        // Check for xs:any or xs:anyAttribute
        if (node instanceof XsdAny) {
            issues.add(QualityIssue.bestPracticeIssue(
                    IssueSeverity.INFO,
                    "xs:any allows arbitrary content",
                    "Consider defining explicit elements for better validation",
                    List.of(node.getName() != null ? node.getName() : "any"),
                    node
            ));
        }

        if (node instanceof XsdAnyAttribute) {
            issues.add(QualityIssue.bestPracticeIssue(
                    IssueSeverity.INFO,
                    "xs:anyAttribute allows arbitrary attributes",
                    "Consider defining explicit attributes for better validation",
                    List.of(node.getName() != null ? node.getName() : "anyAttribute"),
                    node
            ));
        }

        // Check for unbounded maxOccurs
        if (node instanceof XsdElement element) {
            if (element.getMaxOccurs() == -1) { // UNBOUNDED
                issues.add(QualityIssue.bestPracticeIssue(
                        IssueSeverity.INFO,
                        "Element '" + element.getName() + "' has unbounded maxOccurs",
                        "Consider setting a reasonable upper limit",
                        List.of(element.getName()),
                        element
                ));
            }
        }

        // Check for deep nesting (depth > 10)
        if (depth > 10 && (node instanceof XsdElement || node instanceof XsdComplexType)) {
            issues.add(QualityIssue.bestPracticeIssue(
                    IssueSeverity.WARNING,
                    "Deep nesting detected (depth=" + depth + ")",
                    "Consider flattening the schema structure",
                    List.of(node.getName() != null ? node.getName() : node.getNodeType().name()),
                    node
            ));
        }

        // Check for anonymous complex types (could be global)
        if (node instanceof XsdComplexType complexType) {
            if (complexType.getName() == null || complexType.getName().isBlank()) {
                XsdNode parent = complexType.getParent();
                if (parent instanceof XsdElement element) {
                    issues.add(QualityIssue.bestPracticeIssue(
                            IssueSeverity.SUGGESTION,
                            "Anonymous complex type in element '" + element.getName() + "'",
                            "Consider defining as a named global type for reusability",
                            List.of(element.getName()),
                            complexType
                    ));
                }
            }
        }
    }

    /**
     * Checks for conflicts between length constraints and enumeration values.
     * When a restriction has both length facets and enumerations, the length
     * constraints become ineffective since enumerations take precedence in XSD validation.
     */
    private void checkLengthEnumerationConflict(XsdNode node, List<QualityIssue> issues) {
        if (!(node instanceof XsdRestriction restriction)) {
            return;
        }

        List<XsdFacet> facets = restriction.getFacets();

        // Find length constraints
        Integer minLength = null;
        Integer maxLength = null;
        Integer exactLength = null;
        List<String> enumerations = new ArrayList<>();

        for (XsdFacet facet : facets) {
            if (facet.getFacetType() == null) continue;
            switch (facet.getFacetType()) {
                case MIN_LENGTH -> minLength = parseIntSafe(facet.getValue());
                case MAX_LENGTH -> maxLength = parseIntSafe(facet.getValue());
                case LENGTH -> exactLength = parseIntSafe(facet.getValue());
                case ENUMERATION -> {
                    if (facet.getValue() != null) {
                        enumerations.add(facet.getValue());
                    }
                }
                default -> { /* ignore other facet types */ }
            }
        }

        // Only check if both length constraints and enumerations exist
        if (enumerations.isEmpty()) return;
        if (minLength == null && maxLength == null && exactLength == null) return;

        // Find violations
        List<String> tooShort = new ArrayList<>();
        List<String> tooLong = new ArrayList<>();
        List<String> wrongLength = new ArrayList<>();

        for (String enumValue : enumerations) {
            int len = enumValue.length();
            if (exactLength != null && len != exactLength) {
                wrongLength.add(enumValue + " (" + len + " chars, expected " + exactLength + ")");
            }
            if (minLength != null && len < minLength) {
                tooShort.add(enumValue + " (" + len + " chars < minLength " + minLength + ")");
            }
            if (maxLength != null && len > maxLength) {
                tooLong.add(enumValue + " (" + len + " chars > maxLength " + maxLength + ")");
            }
        }

        // Report violations
        String parentName = getParentTypeName(restriction);

        if (!tooLong.isEmpty()) {
            issues.add(QualityIssue.constraintConflictIssue(
                    IssueSeverity.ERROR,
                    "Enumeration values exceed maxLength=" + maxLength + " in " + parentName,
                    "Either increase maxLength to " + findMaxEnumLength(enumerations) +
                            " or remove the ineffective maxLength constraint",
                    tooLong,
                    restriction
            ));
        }

        if (!tooShort.isEmpty()) {
            issues.add(QualityIssue.constraintConflictIssue(
                    IssueSeverity.ERROR,
                    "Enumeration values shorter than minLength=" + minLength + " in " + parentName,
                    "Either decrease minLength to " + findMinEnumLength(enumerations) +
                            " or remove the ineffective minLength constraint",
                    tooShort,
                    restriction
            ));
        }

        if (!wrongLength.isEmpty()) {
            issues.add(QualityIssue.constraintConflictIssue(
                    IssueSeverity.ERROR,
                    "Enumeration values don't match length=" + exactLength + " in " + parentName,
                    "Remove the ineffective length constraint",
                    wrongLength,
                    restriction
            ));
        }
    }

    /**
     * Safely parses an integer value, returning null on failure.
     */
    private Integer parseIntSafe(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Finds the maximum length among enumeration values.
     */
    private int findMaxEnumLength(List<String> enums) {
        return enums.stream().mapToInt(String::length).max().orElse(0);
    }

    /**
     * Finds the minimum length among enumeration values.
     */
    private int findMinEnumLength(List<String> enums) {
        return enums.stream().mapToInt(String::length).min().orElse(0);
    }

    /**
     * Gets the parent type name for error messages.
     */
    private String getParentTypeName(XsdRestriction restriction) {
        XsdNode parent = restriction.getParent();
        if (parent != null && parent.getName() != null && !parent.getName().isBlank()) {
            return parent.getName();
        }
        return "unnamed type";
    }

    /**
     * Calculates total checks performed.
     */
    private int calculateTotalChecks(Map<NamingConvention, List<String>> namingByConvention) {
        int total = 0;
        for (List<String> elements : namingByConvention.values()) {
            total += elements.size();
        }
        return total;
    }

    // ========== Inconsistent Definition Check ==========

    /**
     * Checks for nodes with the same name but different content/structure.
     * This indicates potential inconsistencies in the schema design.
     */
    private void checkInconsistentDefinitions(List<QualityIssue> issues) {
        // Group nodes by name and type
        Map<String, List<XsdNode>> nodesByNameAndType = new HashMap<>();

        collectNamedNodes(schema, nodesByNameAndType, new HashSet<>());

        // Check for same name but different content
        for (Map.Entry<String, List<XsdNode>> entry : nodesByNameAndType.entrySet()) {
            List<XsdNode> nodes = entry.getValue();
            if (nodes.size() < 2) continue;

            // Compare content signatures of nodes with same name
            Map<String, List<XsdNode>> bySignature = new HashMap<>();
            for (XsdNode node : nodes) {
                String signature = computeContentSignature(node);
                bySignature.computeIfAbsent(signature, k -> new ArrayList<>()).add(node);
            }

            // If there are multiple different signatures, we have inconsistent definitions
            if (bySignature.size() > 1) {
                String name = entry.getKey();
                List<String> affected = new ArrayList<>();
                XsdNode firstNode = null;
                int variantNum = 1;

                // Build detailed variant descriptions
                for (Map.Entry<String, List<XsdNode>> variantEntry : bySignature.entrySet()) {
                    List<XsdNode> variantNodes = variantEntry.getValue();
                    if (!variantNodes.isEmpty()) {
                        XsdNode representativeNode = variantNodes.get(0);
                        if (firstNode == null) firstNode = representativeNode;

                        // Add variant header with structure description
                        affected.add("=== Variant " + variantNum + " (" + variantNodes.size() + " occurrence" + (variantNodes.size() > 1 ? "s" : "") + ") ===");

                        // Generate and add readable structure for this variant
                        String structure = generateReadableStructure(representativeNode);
                        affected.add(structure);

                        // Add locations for this variant
                        affected.add("--- Locations ---");
                        for (XsdNode node : variantNodes) {
                            String xpath = node.getXPath();
                            affected.add("  " + (xpath != null ? xpath : node.getName()));
                        }
                        affected.add(""); // Empty line between variants
                        variantNum++;
                    }
                }

                // Generate difference summary
                String diffSummary = generateVariantDifferenceSummary(bySignature);
                if (!diffSummary.isEmpty()) {
                    affected.add("=== Key Differences ===");
                    affected.add(diffSummary);
                }

                issues.add(QualityIssue.inconsistentDefinitionIssue(
                        "Multiple definitions of '" + name + "' with different content (" + bySignature.size() + " variants)",
                        "Consider unifying the definitions or using different names to clarify intent",
                        affected,
                        firstNode
                ));
            }
        }
    }

    /**
     * Collects all named nodes grouped by their name and node type.
     * Key format: "nodeType:name" (e.g., "ELEMENT:PersonName" or "COMPLEX_TYPE:AddressType")
     */
    private void collectNamedNodes(XsdNode node, Map<String, List<XsdNode>> nodesByNameAndType, Set<String> visitedIds) {
        if (node == null) return;

        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) return;
        if (nodeId != null) visitedIds.add(nodeId);

        // Only collect named nodes that are relevant for comparison
        if (isComparableNamedNode(node)) {
            String name = node.getName();
            if (name != null && !name.isBlank()) {
                String key = node.getNodeType() + ":" + name;
                nodesByNameAndType.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
            }
        }

        // Recurse to children
        for (XsdNode child : node.getChildren()) {
            collectNamedNodes(child, nodesByNameAndType, visitedIds);
        }
    }

    /**
     * Determines if a node should be compared for inconsistent/duplicate definitions.
     */
    private boolean isComparableNamedNode(XsdNode node) {
        return switch (node.getNodeType()) {
            case ELEMENT, COMPLEX_TYPE, SIMPLE_TYPE, ATTRIBUTE, GROUP, ATTRIBUTE_GROUP -> true;
            default -> false;
        };
    }

    /**
     * Computes a content signature for a node to compare structural similarity.
     * Two nodes with the same signature have equivalent content.
     */
    private String computeContentSignature(XsdNode node) {
        StringBuilder sig = new StringBuilder();
        computeSignatureRecursive(node, sig, new HashSet<>(), 0);
        return sig.toString();
    }

    /**
     * Recursively builds a content signature for structural comparison.
     */
    private void computeSignatureRecursive(XsdNode node, StringBuilder sig, Set<String> visited, int depth) {
        if (node == null || depth > 20) return; // Prevent infinite recursion

        String nodeId = node.getId();
        if (nodeId != null && visited.contains(nodeId)) {
            sig.append("[REF]");
            return;
        }
        if (nodeId != null) visited.add(nodeId);

        // Build signature from node type and essential properties
        sig.append(node.getNodeType().name());

        // Add type reference for elements
        if (node instanceof XsdElement element) {
            String type = element.getType();
            if (type != null && !type.isBlank()) {
                sig.append(":type=").append(type);
            }
        }

        // Add type and use for attributes - IMPORTANT: This distinguishes attributes with different types
        if (node instanceof XsdAttribute attribute) {
            String type = attribute.getType();
            if (type != null && !type.isBlank()) {
                sig.append(":type=").append(type);
            }
            String use = attribute.getUse();
            if (use != null && !use.isBlank()) {
                sig.append(":use=").append(use);
            }
            String ref = attribute.getRef();
            if (ref != null && !ref.isBlank()) {
                sig.append(":ref=").append(ref);
            }
        }

        // Add mixed and abstract for complex types
        if (node instanceof XsdComplexType complexType) {
            if (complexType.isMixed()) {
                sig.append(":mixed=true");
            }
            if (complexType.isAbstract()) {
                sig.append(":abstract=true");
            }
        }

        // Add base type for restrictions
        if (node instanceof XsdRestriction restriction) {
            String base = restriction.getBase();
            if (base != null) {
                sig.append(":base=").append(base);
            }
        }

        // Add facets for restrictions
        if (node instanceof XsdRestriction restriction) {
            List<XsdFacet> facets = restriction.getFacets();
            if (!facets.isEmpty()) {
                sig.append(":facets=[");
                facets.stream()
                        .sorted((a, b) -> {
                            int cmp = a.getFacetType().compareTo(b.getFacetType());
                            return cmp != 0 ? cmp : String.valueOf(a.getValue()).compareTo(String.valueOf(b.getValue()));
                        })
                        .forEach(f -> sig.append(f.getFacetType()).append("=").append(f.getValue()).append(","));
                sig.append("]");
            }
        }

        // Add cardinality
        sig.append(":").append(node.getMinOccurs()).append("-").append(node.getMaxOccurs());

        // Recurse to children
        sig.append("{");
        List<XsdNode> children = node.getChildren();
        for (XsdNode child : children) {
            computeSignatureRecursive(child, sig, visited, depth + 1);
            sig.append(";");
        }
        sig.append("}");
    }

    // ========== Duplicate Definition Check ==========

    /**
     * Checks for nodes with different names but identical content/structure.
     * This indicates potential code duplication that could be refactored.
     */
    private void checkDuplicateDefinitions(List<QualityIssue> issues) {
        // Collect all comparable nodes
        List<XsdNode> comparableNodes = new ArrayList<>();
        collectAllComparableNodes(schema, comparableNodes, new HashSet<>());

        // Group by content signature
        Map<String, List<XsdNode>> bySignature = new HashMap<>();
        for (XsdNode node : comparableNodes) {
            String name = node.getName();
            if (name == null || name.isBlank()) continue;

            String signature = computeContentSignature(node);
            // Only consider non-trivial signatures (exclude simple/empty definitions)
            if (signature.length() > 30) { // Arbitrary threshold to filter out trivial matches
                bySignature.computeIfAbsent(signature, k -> new ArrayList<>()).add(node);
            }
        }

        // Find groups with same signature but different names
        Set<String> reportedGroups = new HashSet<>();
        for (Map.Entry<String, List<XsdNode>> entry : bySignature.entrySet()) {
            List<XsdNode> nodes = entry.getValue();
            if (nodes.size() < 2) continue;

            // Check if they have different names
            Set<String> names = new HashSet<>();
            for (XsdNode node : nodes) {
                names.add(node.getNodeType() + ":" + node.getName());
            }

            if (names.size() > 1) {
                // Multiple different names with same content - potential duplicates
                String groupKey = String.join(",", names.stream().sorted().toList());
                if (reportedGroups.contains(groupKey)) continue;
                reportedGroups.add(groupKey);

                List<String> affected = new ArrayList<>();
                XsdNode firstNode = null;
                Set<String> uniqueNames = new LinkedHashSet<>();

                for (XsdNode node : nodes) {
                    uniqueNames.add(node.getName());
                    String xpath = node.getXPath();
                    affected.add(xpath != null ? xpath : node.getName());
                    if (firstNode == null) firstNode = node;
                }

                // Generate human-readable structure description
                String structureDescription = generateReadableStructure(firstNode);

                // Add structure description as first item in affected list
                List<String> affectedWithStructure = new ArrayList<>();
                affectedWithStructure.add("=== Identical Structure ===");
                affectedWithStructure.add(structureDescription);
                affectedWithStructure.add("=== Found in Definitions ===");
                affectedWithStructure.addAll(affected);

                issues.add(QualityIssue.duplicateDefinitionIssue(
                        "Identical structure found in " + uniqueNames.size() + " different definitions: " + String.join(", ", uniqueNames),
                        "Consider consolidating into a single reusable type to reduce redundancy",
                        affectedWithStructure,
                        firstNode
                ));
            }
        }
    }

    /**
     * Collects all nodes that should be compared for duplication.
     */
    private void collectAllComparableNodes(XsdNode node, List<XsdNode> result, Set<String> visitedIds) {
        if (node == null) return;

        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) return;
        if (nodeId != null) visitedIds.add(nodeId);

        if (isComparableNamedNode(node) && node.getName() != null && !node.getName().isBlank()) {
            result.add(node);
        }

        for (XsdNode child : node.getChildren()) {
            collectAllComparableNodes(child, result, visitedIds);
        }
    }

    // ========== Duplicate Elements in Container Check ==========

    /**
     * Checks for duplicate element definitions within the same container (sequence, choice, all).
     * Having the same element name appear multiple times within a single container is a schema error
     * that causes validation ambiguity (e.g., UCITSExistingPerformanceFees appearing twice in a sequence).
     */
    private void checkDuplicateElementsInContainers(List<QualityIssue> issues) {
        Set<String> visitedIds = new HashSet<>();
        checkDuplicateElementsInContainersRecursive(schema, issues, visitedIds);

        // Also check imported schemas
        for (Map.Entry<String, XsdSchema> entry : schema.getImportedSchemas().entrySet()) {
            checkDuplicateElementsInContainersRecursive(entry.getValue(), issues, visitedIds);
        }
    }

    /**
     * Recursively traverses the schema looking for containers with duplicate element names.
     */
    private void checkDuplicateElementsInContainersRecursive(XsdNode node, List<QualityIssue> issues,
                                                              Set<String> visitedIds) {
        if (node == null) return;

        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) return;
        if (nodeId != null) visitedIds.add(nodeId);

        // Check if this node is a container (sequence, choice, all)
        if (node instanceof XsdSequence || node instanceof XsdChoice || node instanceof XsdAll) {
            checkContainerForDuplicates(node, issues);
        }

        // Recurse to children
        for (XsdNode child : node.getChildren()) {
            checkDuplicateElementsInContainersRecursive(child, issues, visitedIds);
        }
    }

    /**
     * Checks a single container node for duplicate element names among its direct children.
     */
    private void checkContainerForDuplicates(XsdNode container, List<QualityIssue> issues) {
        Map<String, List<XsdNode>> elementsByName = new HashMap<>();

        // Collect direct child elements by name
        for (XsdNode child : container.getChildren()) {
            if (child instanceof XsdElement element) {
                String elementName = element.getName();
                if (elementName != null && !elementName.isBlank()) {
                    elementsByName.computeIfAbsent(elementName, k -> new ArrayList<>()).add(element);
                }
            }
        }

        // Report duplicates
        for (Map.Entry<String, List<XsdNode>> entry : elementsByName.entrySet()) {
            List<XsdNode> elements = entry.getValue();
            if (elements.size() > 1) {
                String elementName = entry.getKey();
                String containerType = container.getNodeType().name().toLowerCase();

                List<String> affected = new ArrayList<>();
                affected.add("Element '" + elementName + "' appears " + elements.size() + " times");

                // Add XPaths of duplicate occurrences
                for (XsdNode elem : elements) {
                    String xpath = elem.getXPath();
                    if (xpath != null) {
                        affected.add("  - " + xpath);
                    }
                }

                // Get parent context for better error message
                String parentContext = getContainerParentContext(container);

                issues.add(QualityIssue.duplicateElementInContainerIssue(
                        "Duplicate element '" + elementName + "' in " + containerType + parentContext,
                        "Remove one of the duplicate element definitions or rename one to create distinct elements. " +
                                "Duplicate elements in a " + containerType + " cause validation ambiguity.",
                        affected,
                        container
                ));
            }
        }
    }

    /**
     * Gets contextual information about the container's parent for better error messages.
     */
    private String getContainerParentContext(XsdNode container) {
        XsdNode parent = container.getParent();
        if (parent == null) return "";

        StringBuilder context = new StringBuilder();
        context.append(" within ");

        // Traverse up to find meaningful parent context
        while (parent != null) {
            if (parent instanceof XsdComplexType complexType) {
                String name = complexType.getName();
                if (name != null && !name.isBlank()) {
                    context.append("complexType '").append(name).append("'");
                    return context.toString();
                }
            } else if (parent instanceof XsdElement element) {
                String name = element.getName();
                if (name != null && !name.isBlank()) {
                    context.append("element '").append(name).append("'");
                    return context.toString();
                }
            } else if (parent instanceof XsdGroup group) {
                String name = group.getName();
                if (name != null && !name.isBlank()) {
                    context.append("group '").append(name).append("'");
                    return context.toString();
                }
            }
            parent = parent.getParent();
        }

        return "";
    }

    // ========== Readable Structure Generation ==========

    /**
     * Generates a human-readable description of a node's structure.
     * Used to show what the identical structure looks like in duplicate detection.
     */
    private String generateReadableStructure(XsdNode node) {
        StringBuilder sb = new StringBuilder();
        generateReadableStructureRecursive(node, sb, "", new HashSet<>(), 0);
        return sb.toString().trim();
    }

    /**
     * Recursively builds a human-readable structure description.
     */
    private void generateReadableStructureRecursive(XsdNode node, StringBuilder sb, String indent,
                                                     Set<String> visited, int depth) {
        if (node == null || depth > 10) return;

        String nodeId = node.getId();
        if (nodeId != null && visited.contains(nodeId)) {
            sb.append(indent).append("(circular reference)\n");
            return;
        }
        if (nodeId != null) visited.add(nodeId);

        // Format based on node type
        switch (node.getNodeType()) {
            case ELEMENT -> {
                XsdElement elem = (XsdElement) node;
                sb.append(indent).append("element");
                if (elem.getType() != null && !elem.getType().isBlank()) {
                    sb.append(" type=\"").append(elem.getType()).append("\"");
                }
                appendCardinality(sb, node);
                sb.append("\n");
            }
            case COMPLEX_TYPE -> {
                sb.append(indent).append("complexType\n");
            }
            case SIMPLE_TYPE -> {
                sb.append(indent).append("simpleType\n");
            }
            case SEQUENCE -> {
                sb.append(indent).append("sequence");
                appendCardinality(sb, node);
                sb.append("\n");
            }
            case CHOICE -> {
                sb.append(indent).append("choice");
                appendCardinality(sb, node);
                sb.append("\n");
            }
            case ALL -> {
                sb.append(indent).append("all\n");
            }
            case RESTRICTION -> {
                XsdRestriction restriction = (XsdRestriction) node;
                sb.append(indent).append("restriction base=\"").append(restriction.getBase()).append("\"\n");

                // Add facets
                for (XsdFacet facet : restriction.getFacets()) {
                    sb.append(indent).append("  ").append(formatFacetType(facet.getFacetType()));
                    sb.append("=\"").append(facet.getValue()).append("\"\n");
                }
            }
            case EXTENSION -> {
                sb.append(indent).append("extension\n");
            }
            case ATTRIBUTE -> {
                XsdAttribute attr = (XsdAttribute) node;
                sb.append(indent).append("attribute");
                if (attr.getType() != null && !attr.getType().isBlank()) {
                    sb.append(" type=\"").append(attr.getType()).append("\"");
                }
                if (attr.getUse() != null) {
                    sb.append(" use=\"").append(attr.getUse()).append("\"");
                }
                sb.append("\n");
            }
            default -> {
                sb.append(indent).append(node.getNodeType().name().toLowerCase());
                appendCardinality(sb, node);
                sb.append("\n");
            }
        }

        // Recurse to children
        String childIndent = indent + "  ";
        for (XsdNode child : node.getChildren()) {
            generateReadableStructureRecursive(child, sb, childIndent, visited, depth + 1);
        }
    }

    /**
     * Appends cardinality information if not default (1..1).
     */
    private void appendCardinality(StringBuilder sb, XsdNode node) {
        int min = node.getMinOccurs();
        int max = node.getMaxOccurs();

        // Only show if not default (1..1)
        // Note: max = -1 typically means unbounded
        boolean isDefault = (min == 1 && max == 1);
        if (!isDefault) {
            sb.append(" [");
            sb.append(min);
            sb.append("..");
            sb.append(max == -1 || max == Integer.MAX_VALUE ? "*" : String.valueOf(max));
            sb.append("]");
        }
    }

    /**
     * Formats facet type for display.
     */
    private String formatFacetType(XsdFacetType type) {
        return switch (type) {
            case MIN_LENGTH -> "minLength";
            case MAX_LENGTH -> "maxLength";
            case LENGTH -> "length";
            case PATTERN -> "pattern";
            case ENUMERATION -> "enumeration";
            case WHITE_SPACE -> "whiteSpace";
            case MAX_INCLUSIVE -> "maxInclusive";
            case MAX_EXCLUSIVE -> "maxExclusive";
            case MIN_INCLUSIVE -> "minInclusive";
            case MIN_EXCLUSIVE -> "minExclusive";
            case TOTAL_DIGITS -> "totalDigits";
            case FRACTION_DIGITS -> "fractionDigits";
            case ASSERTION -> "assertion";
            case EXPLICIT_TIMEZONE -> "explicitTimezone";
        };
    }

    /**
     * Generates a summary of key differences between variants.
     * Compares structural characteristics to highlight what makes each variant unique.
     */
    private String generateVariantDifferenceSummary(Map<String, List<XsdNode>> bySignature) {
        if (bySignature.size() < 2) return "";

        StringBuilder sb = new StringBuilder();
        List<VariantInfo> variants = new ArrayList<>();
        int variantNum = 1;

        // Collect info about each variant
        for (List<XsdNode> nodes : bySignature.values()) {
            if (!nodes.isEmpty()) {
                XsdNode node = nodes.get(0);
                variants.add(new VariantInfo(variantNum++, node));
            }
        }

        // Compare variants pairwise
        for (int i = 0; i < variants.size(); i++) {
            for (int j = i + 1; j < variants.size(); j++) {
                VariantInfo v1 = variants.get(i);
                VariantInfo v2 = variants.get(j);
                List<String> differences = compareVariants(v1, v2);

                if (!differences.isEmpty()) {
                    sb.append("Variant ").append(v1.number).append(" vs Variant ").append(v2.number).append(":\n");
                    for (String diff : differences) {
                        sb.append("  - ").append(diff).append("\n");
                    }
                }
            }
        }

        return sb.toString().trim();
    }

    /**
     * Compares two variants and returns a list of differences.
     */
    private List<String> compareVariants(VariantInfo v1, VariantInfo v2) {
        List<String> differences = new ArrayList<>();

        // Compare content model (simpleContent, complexContent, mixed)
        boolean v1HasSimpleContent = hasChildOfType(v1.node, XsdNodeType.SIMPLE_CONTENT);
        boolean v2HasSimpleContent = hasChildOfType(v2.node, XsdNodeType.SIMPLE_CONTENT);
        boolean v1HasComplexContent = hasChildOfType(v1.node, XsdNodeType.COMPLEX_CONTENT);
        boolean v2HasComplexContent = hasChildOfType(v2.node, XsdNodeType.COMPLEX_CONTENT);

        if (v1HasSimpleContent != v2HasSimpleContent) {
            differences.add("Variant " + v1.number + (v1HasSimpleContent ? " uses simpleContent" : " does not use simpleContent") +
                           ", Variant " + v2.number + (v2HasSimpleContent ? " uses simpleContent" : " does not use simpleContent"));
        }

        if (v1HasComplexContent != v2HasComplexContent) {
            differences.add("Variant " + v1.number + (v1HasComplexContent ? " uses complexContent" : " does not use complexContent") +
                           ", Variant " + v2.number + (v2HasComplexContent ? " uses complexContent" : " does not use complexContent"));
        }

        // Compare mixed attribute for complexType
        if (v1.node instanceof XsdComplexType ct1 && v2.node instanceof XsdComplexType ct2) {
            if (ct1.isMixed() != ct2.isMixed()) {
                differences.add("Variant " + v1.number + (ct1.isMixed() ? " has mixed=\"true\"" : " has mixed=\"false\" (or unset)") +
                               ", Variant " + v2.number + (ct2.isMixed() ? " has mixed=\"true\"" : " has mixed=\"false\" (or unset)"));
            }
        }

        // Check for complexType child with mixed attribute
        XsdComplexType ct1 = findComplexTypeChild(v1.node);
        XsdComplexType ct2 = findComplexTypeChild(v2.node);
        if (ct1 != null && ct2 != null) {
            if (ct1.isMixed() != ct2.isMixed()) {
                differences.add("Variant " + v1.number + " complexType " + (ct1.isMixed() ? "has mixed=\"true\"" : "has no mixed attribute") +
                               ", Variant " + v2.number + " complexType " + (ct2.isMixed() ? "has mixed=\"true\"" : "has no mixed attribute"));
            }
        }

        // Compare compositor types (sequence, choice, all)
        boolean v1HasSequence = hasChildOfType(v1.node, XsdNodeType.SEQUENCE);
        boolean v2HasSequence = hasChildOfType(v2.node, XsdNodeType.SEQUENCE);
        boolean v1HasChoice = hasChildOfType(v1.node, XsdNodeType.CHOICE);
        boolean v2HasChoice = hasChildOfType(v2.node, XsdNodeType.CHOICE);

        if (v1HasSequence != v2HasSequence || v1HasChoice != v2HasChoice) {
            String v1Compositor = v1HasSequence ? "sequence" : (v1HasChoice ? "choice" : "none/other");
            String v2Compositor = v2HasSequence ? "sequence" : (v2HasChoice ? "choice" : "none/other");
            if (!v1Compositor.equals(v2Compositor)) {
                differences.add("Different compositors: Variant " + v1.number + " uses " + v1Compositor +
                               ", Variant " + v2.number + " uses " + v2Compositor);
            }
        }

        // Compare child element count
        int v1ChildElements = countChildElements(v1.node);
        int v2ChildElements = countChildElements(v2.node);
        if (v1ChildElements != v2ChildElements) {
            differences.add("Different child element count: Variant " + v1.number + " has " + v1ChildElements +
                           " child element(s), Variant " + v2.number + " has " + v2ChildElements);
        }

        // Compare attribute count
        int v1Attributes = countAttributes(v1.node);
        int v2Attributes = countAttributes(v2.node);
        if (v1Attributes != v2Attributes) {
            differences.add("Different attribute count: Variant " + v1.number + " has " + v1Attributes +
                           " attribute(s), Variant " + v2.number + " has " + v2Attributes);
        }

        // Compare type reference vs inline definition
        if (v1.node instanceof XsdElement e1 && v2.node instanceof XsdElement e2) {
            String t1 = e1.getType();
            String t2 = e2.getType();
            boolean v1HasTypeRef = t1 != null && !t1.isBlank();
            boolean v2HasTypeRef = t2 != null && !t2.isBlank();

            if (v1HasTypeRef != v2HasTypeRef) {
                differences.add("Variant " + v1.number + (v1HasTypeRef ? " references type \"" + t1 + "\"" : " uses inline definition") +
                               ", Variant " + v2.number + (v2HasTypeRef ? " references type \"" + t2 + "\"" : " uses inline definition"));
            } else if (v1HasTypeRef && !t1.equals(t2)) {
                differences.add("Different type references: Variant " + v1.number + " uses \"" + t1 +
                               "\", Variant " + v2.number + " uses \"" + t2 + "\"");
            }
        }

        return differences;
    }

    /**
     * Checks if a node has a direct or nested child of the specified type.
     */
    private boolean hasChildOfType(XsdNode node, XsdNodeType type) {
        for (XsdNode child : node.getChildren()) {
            if (child.getNodeType() == type) return true;
            // Check nested children (e.g., complexType inside element)
            if (hasChildOfType(child, type)) return true;
        }
        return false;
    }

    /**
     * Finds a ComplexType child node.
     */
    private XsdComplexType findComplexTypeChild(XsdNode node) {
        for (XsdNode child : node.getChildren()) {
            if (child instanceof XsdComplexType ct) return ct;
            XsdComplexType nested = findComplexTypeChild(child);
            if (nested != null) return nested;
        }
        return null;
    }

    /**
     * Counts the number of child elements (including nested ones).
     */
    private int countChildElements(XsdNode node) {
        int count = 0;
        for (XsdNode child : node.getChildren()) {
            if (child.getNodeType() == XsdNodeType.ELEMENT) count++;
            count += countChildElements(child);
        }
        return count;
    }

    /**
     * Counts the number of attributes (including nested ones).
     */
    private int countAttributes(XsdNode node) {
        int count = 0;
        for (XsdNode child : node.getChildren()) {
            if (child.getNodeType() == XsdNodeType.ATTRIBUTE) count++;
            count += countAttributes(child);
        }
        return count;
    }

    /**
     * Helper class to store variant information.
     */
    private static class VariantInfo {
        final int number;
        final XsdNode node;

        VariantInfo(int number, XsdNode node) {
            this.number = number;
            this.node = node;
        }
    }
}
