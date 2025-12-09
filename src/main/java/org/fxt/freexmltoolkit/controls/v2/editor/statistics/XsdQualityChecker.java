package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

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
        DEPRECATED
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
    public record QualityIssue(
            IssueCategory category,
            IssueSeverity severity,
            String message,
            String suggestion,
            List<String> affectedElements,
            XsdNode sourceNode
    ) {
        /**
         * Creates a naming convention issue.
         */
        public static QualityIssue namingIssue(IssueSeverity severity, String message,
                                               String suggestion, List<String> affected) {
            return new QualityIssue(IssueCategory.NAMING_CONVENTION, severity, message, suggestion, affected, null);
        }

        /**
         * Creates a best practice issue.
         */
        public static QualityIssue bestPracticeIssue(IssueSeverity severity, String message,
                                                     String suggestion, List<String> affected, XsdNode node) {
            return new QualityIssue(IssueCategory.BEST_PRACTICE, severity, message, suggestion, affected, node);
        }

        /**
         * Creates a deprecated element issue.
         */
        public static QualityIssue deprecatedIssue(String elementName, String deprecationMessage,
                                                   String alternative, XsdNode node) {
            return new QualityIssue(
                    IssueCategory.DEPRECATED,
                    IssueSeverity.WARNING,
                    elementName + " is deprecated" + (deprecationMessage != null ? ": " + deprecationMessage : ""),
                    alternative != null ? "Use " + alternative + " instead" : null,
                    List.of(elementName),
                    node
            );
        }
    }

    /**
     * Quality check result.
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

        // Traverse schema
        Set<String> visitedIds = new HashSet<>();
        traverseAndCheck(schema, namingByConvention, issues, deprecatedIssues, visitedIds, 0);

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
     * Calculates total checks performed.
     */
    private int calculateTotalChecks(Map<NamingConvention, List<String>> namingByConvention) {
        int total = 0;
        for (List<String> elements : namingByConvention.values()) {
            total += elements.size();
        }
        return total;
    }
}
