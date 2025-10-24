package org.fxt.freexmltoolkit.domain;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Represents the XSD version (1.0 or 1.1) of a schema and provides
 * version-specific validation and feature support checking.
 */
public class XsdVersion {
    private static final Logger logger = LogManager.getLogger(XsdVersion.class);

    public static final XsdVersion VERSION_1_0 = new XsdVersion("1.0", false);
    public static final XsdVersion VERSION_1_1 = new XsdVersion("1.1", false);
    public static final XsdVersion VERSION_1_1_STRICT = new XsdVersion("1.1", true);

    private final String version;
    private final boolean strict;
    private final Set<Xsd11Feature> detectedFeatures;

    /**
     * Creates a new XSD version context
     *
     * @param version The version string ("1.0" or "1.1")
     * @param strict  If true, enforces strict validation rules
     */
    public XsdVersion(String version, boolean strict) {
        this.version = version;
        this.strict = strict;
        this.detectedFeatures = EnumSet.noneOf(Xsd11Feature.class);
    }

    /**
     * Returns the version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Returns true if this is XSD 1.1
     */
    public boolean isVersion11() {
        return "1.1".equals(version);
    }

    /**
     * Returns true if this is XSD 1.0
     */
    public boolean isVersion10() {
        return "1.0".equals(version);
    }

    /**
     * Returns true if strict mode is enabled
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Checks if a specific XSD 1.1 feature is supported in this version
     *
     * @param feature The feature to check
     * @return true if the feature is supported
     */
    public boolean supports(Xsd11Feature feature) {
        if (feature == null) {
            return false;
        }
        return isVersion11();
    }

    /**
     * Registers that a specific XSD 1.1 feature was detected in the schema
     *
     * @param feature The detected feature
     */
    public void registerFeature(Xsd11Feature feature) {
        if (feature != null) {
            detectedFeatures.add(feature);
            logger.debug("Detected XSD 1.1 feature: {}", feature.getDisplayName());
        }
    }

    /**
     * Returns all detected XSD 1.1 features in the schema
     */
    public Set<Xsd11Feature> getDetectedFeatures() {
        return Collections.unmodifiableSet(detectedFeatures);
    }

    /**
     * Returns true if any XSD 1.1 features were detected
     */
    public boolean hasXsd11Features() {
        return !detectedFeatures.isEmpty();
    }

    /**
     * Validates a node against the current XSD version
     *
     * @param node The node to validate
     * @return A list of validation errors (empty if valid)
     */
    public List<String> validate(XsdNodeInfo node) {
        List<String> errors = new ArrayList<>();

        if (node == null) {
            return errors;
        }

        // Check if node uses XSD 1.1 features
        if (node.isXsd11() && isVersion10()) {
            if (node.nodeType().isXsd11Feature()) {
                errors.add(String.format(
                        "Node '%s' uses XSD 1.1 feature '%s' which is not supported in XSD 1.0",
                        node.name(), node.nodeType()
                ));
            }

            if (node.xpathExpression() != null && !node.xpathExpression().isEmpty()) {
                errors.add(String.format(
                        "Node '%s' uses XPath expressions which require XSD 1.1",
                        node.name()
                ));
            }

            if (!node.xsd11Attributes().isEmpty()) {
                errors.add(String.format(
                        "Node '%s' uses XSD 1.1 attributes: %s",
                        node.name(), String.join(", ", node.xsd11Attributes().keySet())
                ));
            }
        }

        // Recursively validate children
        if (node.children() != null) {
            for (XsdNodeInfo child : node.children()) {
                errors.addAll(validate(child));
            }
        }

        return errors;
    }

    /**
     * Creates a version context from a schema root element attributes
     *
     * @param minVersion The vc:minVersion attribute value (can be null)
     * @param maxVersion The vc:maxVersion attribute value (can be null)
     * @return An XsdVersion instance
     */
    public static XsdVersion fromAttributes(String minVersion, String maxVersion) {
        // If vc:minVersion is specified, use it
        if (minVersion != null && !minVersion.isEmpty()) {
            if (minVersion.equals("1.1")) {
                logger.info("Detected XSD 1.1 schema from vc:minVersion attribute");
                return VERSION_1_1;
            } else if (minVersion.equals("1.0")) {
                return VERSION_1_0;
            }
        }

        // If maxVersion is 1.0 explicitly, it's a 1.0 schema
        if ("1.0".equals(maxVersion)) {
            return VERSION_1_0;
        }

        // Default to 1.0 if no version indicators present
        return VERSION_1_0;
    }

    /**
     * Returns a human-readable string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("XSD ").append(version);
        if (strict) {
            sb.append(" (strict)");
        }
        if (hasXsd11Features()) {
            sb.append(" - Features: ").append(detectedFeatures.size());
        }
        return sb.toString();
    }

    /**
     * Generates a summary report of detected XSD 1.1 features
     */
    public String getFeatureSummary() {
        if (detectedFeatures.isEmpty()) {
            return "No XSD 1.1 features detected";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Detected XSD 1.1 Features:\n");
        for (Xsd11Feature feature : detectedFeatures) {
            sb.append("  - ").append(feature.getDisplayName())
                    .append(": ").append(feature.getDescription());
            if (feature.isCritical()) {
                sb.append(" [CRITICAL]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns warnings if XSD 1.1 features are used but version is not declared
     */
    public List<String> getCompatibilityWarnings() {
        List<String> warnings = new ArrayList<>();

        if (hasXsd11Features() && isVersion10()) {
            warnings.add("Schema uses XSD 1.1 features but is declared as version 1.0");
            warnings.add("Consider adding: xmlns:vc=\"http://www.w3.org/2007/XMLSchema-versioning\" vc:minVersion=\"1.1\"");

            // List critical features
            long criticalCount = detectedFeatures.stream()
                    .filter(Xsd11Feature::isCritical)
                    .count();

            if (criticalCount > 0) {
                warnings.add(String.format("%d critical XSD 1.1 features require an XSD 1.1 processor", criticalCount));
            }
        }

        return warnings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XsdVersion that = (XsdVersion) o;
        return strict == that.strict && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, strict);
    }
}
