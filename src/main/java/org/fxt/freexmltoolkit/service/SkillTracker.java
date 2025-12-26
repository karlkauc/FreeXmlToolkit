package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.statistics.FeatureUsage;
import org.fxt.freexmltoolkit.domain.statistics.UsageStatistics;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class for skill tracking and feature discovery.
 * Provides feature definitions, category grouping, and skill progress calculations.
 */
public class SkillTracker {

    /**
     * Feature definition with metadata
     */
    public record FeatureDefinition(
        String id,
        String name,
        String category,
        String description,
        String iconLiteral,
        String pageLink,
        int skillPoints
    ) {}

    /**
     * Category with grouped features
     */
    public record FeatureCategory(
        String name,
        String iconLiteral,
        List<FeatureDefinition> features
    ) {}

    // All feature definitions
    private static final Map<String, FeatureDefinition> FEATURES = new LinkedHashMap<>();

    static {
        // Validation Features
        FEATURES.put("xml_validation", new FeatureDefinition(
            "xml_validation", "XML Validation", "Validation",
            "Validate XML documents against XSD schemas",
            "bi-check-circle", "xmlUltimate", 5
        ));
        FEATURES.put("xsd_validation", new FeatureDefinition(
            "xsd_validation", "XSD Schema Validation", "Validation",
            "Validate XSD schema files for correctness",
            "bi-file-earmark-check", "xsdValidation", 5
        ));
        FEATURES.put("schematron_validation", new FeatureDefinition(
            "schematron_validation", "Schematron Rules", "Validation",
            "Apply business rules validation with Schematron",
            "bi-shield-check", "schematron", 10
        ));
        FEATURES.put("batch_validation", new FeatureDefinition(
            "batch_validation", "Batch Validation", "Validation",
            "Validate multiple XML files at once",
            "bi-list-check", "xsdValidation", 8
        ));

        // Editing Features
        FEATURES.put("xml_formatting", new FeatureDefinition(
            "xml_formatting", "Format/Pretty Print", "Editing",
            "Format and beautify XML documents",
            "bi-text-indent-left", "xmlUltimate", 3
        ));
        FEATURES.put("xsd_visualization", new FeatureDefinition(
            "xsd_visualization", "XSD Visual Editor", "Editing",
            "Graphically view and edit XSD schemas",
            "bi-diagram-3", "xsd", 8
        ));
        FEATURES.put("intellisense", new FeatureDefinition(
            "intellisense", "IntelliSense Auto-complete", "Editing",
            "Context-aware code completion",
            "bi-lightning", "xmlUltimate", 7
        ));

        // Query Features
        FEATURES.put("xpath_queries", new FeatureDefinition(
            "xpath_queries", "XPath Queries", "Query",
            "Query XML data using XPath expressions",
            "bi-search", "xmlUltimate", 7
        ));
        FEATURES.put("xquery_execution", new FeatureDefinition(
            "xquery_execution", "XQuery Execution", "Query",
            "Execute XQuery for advanced data manipulation",
            "bi-code-slash", "xmlUltimate", 10
        ));

        // Transformation Features
        FEATURES.put("xslt_transformation", new FeatureDefinition(
            "xslt_transformation", "XSLT Transformation", "Transformation",
            "Transform XML using XSLT stylesheets",
            "bi-arrow-repeat", "xsltDeveloper", 8
        ));

        // Tools Features
        FEATURES.put("schema_generation", new FeatureDefinition(
            "schema_generation", "Schema Generation", "Tools",
            "Generate XSD schemas from XML samples",
            "bi-gear", "schemaGenerator", 8
        ));

        // Security Features
        FEATURES.put("digital_signature", new FeatureDefinition(
            "digital_signature", "XML Digital Signatures", "Security",
            "Sign and verify XML documents",
            "bi-key", "signature", 10
        ));

        // Export Features
        FEATURES.put("pdf_generation", new FeatureDefinition(
            "pdf_generation", "PDF Generation (FOP)", "Export",
            "Create PDFs from XML using XSL-FO",
            "bi-file-pdf", "fop", 8
        ));
        FEATURES.put("xsd_documentation", new FeatureDefinition(
            "xsd_documentation", "XSD Documentation Export", "Export",
            "Generate documentation from XSD schemas",
            "bi-book", "xsd", 7
        ));

        // Organization Features
        FEATURES.put("favorites_system", new FeatureDefinition(
            "favorites_system", "Favorites Management", "Organization",
            "Save and organize frequently used files",
            "bi-star", "xmlUltimate", 3
        ));
    }

    /**
     * Get all feature definitions
     */
    public static Collection<FeatureDefinition> getAllFeatureDefinitions() {
        return FEATURES.values();
    }

    /**
     * Get feature definition by ID
     */
    public static FeatureDefinition getFeatureDefinition(String featureId) {
        return FEATURES.get(featureId);
    }

    /**
     * Get features grouped by category
     */
    public static List<FeatureCategory> getFeaturesByCategory() {
        Map<String, List<FeatureDefinition>> grouped = FEATURES.values().stream()
            .collect(Collectors.groupingBy(FeatureDefinition::category));

        List<FeatureCategory> categories = new ArrayList<>();

        // Define category order and icons
        Map<String, String> categoryIcons = Map.of(
            "Validation", "bi-check-circle",
            "Editing", "bi-pencil",
            "Query", "bi-search",
            "Transformation", "bi-arrow-repeat",
            "Tools", "bi-tools",
            "Security", "bi-shield-lock",
            "Export", "bi-box-arrow-up",
            "Organization", "bi-folder"
        );

        String[] categoryOrder = {"Validation", "Editing", "Query", "Transformation",
            "Tools", "Security", "Export", "Organization"};

        for (String category : categoryOrder) {
            List<FeatureDefinition> features = grouped.get(category);
            if (features != null && !features.isEmpty()) {
                categories.add(new FeatureCategory(
                    category,
                    categoryIcons.getOrDefault(category, "bi-circle"),
                    features
                ));
            }
        }

        return categories;
    }

    /**
     * Calculate skill progress for a category (0-100%)
     */
    public static double getCategoryProgress(String category, UsageStatistics stats) {
        List<FeatureDefinition> categoryFeatures = FEATURES.values().stream()
            .filter(f -> f.category().equals(category))
            .toList();

        if (categoryFeatures.isEmpty()) return 0;

        long discovered = categoryFeatures.stream()
            .filter(f -> {
                FeatureUsage usage = stats.getFeatureUsage().get(f.id());
                return usage != null && usage.isDiscovered();
            })
            .count();

        return (double) discovered / categoryFeatures.size() * 100;
    }

    /**
     * Calculate overall skill progress (0-100%)
     */
    public static double getOverallProgress(UsageStatistics stats) {
        if (FEATURES.isEmpty()) return 0;

        long discovered = stats.getFeatureUsage().values().stream()
            .filter(FeatureUsage::isDiscovered)
            .count();

        return (double) discovered / FEATURES.size() * 100;
    }

    /**
     * Get total skill points earned
     */
    public static int getTotalSkillPoints(UsageStatistics stats) {
        return stats.getFeatureUsage().values().stream()
            .filter(FeatureUsage::isDiscovered)
            .mapToInt(usage -> {
                FeatureDefinition def = FEATURES.get(usage.getFeatureId());
                return def != null ? def.skillPoints() : 0;
            })
            .sum();
    }

    /**
     * Get maximum possible skill points
     */
    public static int getMaxSkillPoints() {
        return FEATURES.values().stream()
            .mapToInt(FeatureDefinition::skillPoints)
            .sum();
    }

    /**
     * Get recommended next feature to try based on current usage
     */
    public static Optional<FeatureDefinition> getRecommendedFeature(UsageStatistics stats) {
        // Priority order: features in categories user already uses, but hasn't discovered yet
        Set<String> discoveredCategories = stats.getFeatureUsage().values().stream()
            .filter(FeatureUsage::isDiscovered)
            .map(FeatureUsage::getCategory)
            .collect(Collectors.toSet());

        // First, try to find undiscovered features in known categories
        Optional<FeatureDefinition> sameCategory = FEATURES.values().stream()
            .filter(f -> discoveredCategories.contains(f.category()))
            .filter(f -> {
                FeatureUsage usage = stats.getFeatureUsage().get(f.id());
                return usage == null || !usage.isDiscovered();
            })
            .findFirst();

        if (sameCategory.isPresent()) {
            return sameCategory;
        }

        // Otherwise, suggest a high-value feature from a new category
        return FEATURES.values().stream()
            .filter(f -> !discoveredCategories.contains(f.category()))
            .filter(f -> {
                FeatureUsage usage = stats.getFeatureUsage().get(f.id());
                return usage == null || !usage.isDiscovered();
            })
            .max(Comparator.comparingInt(FeatureDefinition::skillPoints));
    }

    /**
     * Get skill level title based on progress
     */
    public static String getSkillLevelTitle(double progress) {
        if (progress >= 90) return "XML Master";
        if (progress >= 70) return "Expert User";
        if (progress >= 50) return "Proficient";
        if (progress >= 30) return "Intermediate";
        if (progress >= 10) return "Getting Started";
        return "Newcomer";
    }
}
