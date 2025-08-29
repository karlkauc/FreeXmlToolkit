package org.fxt.freexmltoolkit.domain;

import java.util.List;
import java.util.Map;

/**
 * Represents a validation constraint in XSD schema
 * Used for transferring validation information between UI and domain layer
 */
public record ValidationConstraint(
        String constraintType,  // "pattern", "enumeration", "minInclusive", etc.
        String value,          // The constraint value
        String description,    // Human-readable description
        Map<String, String> attributes  // Additional attributes
) {

    /**
     * Factory methods for creating common constraint types
     */

    public static ValidationConstraint pattern(String pattern) {
        return new ValidationConstraint("pattern", pattern, "Regular expression pattern", null);
    }

    public static ValidationConstraint enumeration(List<String> values) {
        return new ValidationConstraint("enumeration", String.join(",", values),
                "Enumeration values: " + values.size() + " items", null);
    }

    public static ValidationConstraint minInclusive(String value) {
        return new ValidationConstraint("minInclusive", value,
                "Minimum inclusive value: " + value, null);
    }

    public static ValidationConstraint maxInclusive(String value) {
        return new ValidationConstraint("maxInclusive", value,
                "Maximum inclusive value: " + value, null);
    }

    public static ValidationConstraint minExclusive(String value) {
        return new ValidationConstraint("minExclusive", value,
                "Minimum exclusive value: " + value, null);
    }

    public static ValidationConstraint maxExclusive(String value) {
        return new ValidationConstraint("maxExclusive", value,
                "Maximum exclusive value: " + value, null);
    }

    public static ValidationConstraint length(String value) {
        return new ValidationConstraint("length", value,
                "Exact length: " + value + " characters", null);
    }

    public static ValidationConstraint minLength(String value) {
        return new ValidationConstraint("minLength", value,
                "Minimum length: " + value + " characters", null);
    }

    public static ValidationConstraint maxLength(String value) {
        return new ValidationConstraint("maxLength", value,
                "Maximum length: " + value + " characters", null);
    }

    public static ValidationConstraint totalDigits(String value) {
        return new ValidationConstraint("totalDigits", value,
                "Total digits: " + value, null);
    }

    public static ValidationConstraint fractionDigits(String value) {
        return new ValidationConstraint("fractionDigits", value,
                "Fraction digits: " + value, null);
    }

    public static ValidationConstraint whitespace(String action) {
        return new ValidationConstraint("whiteSpace", action,
                "Whitespace action: " + action, null);
    }

    public static ValidationConstraint custom(String name, String value, String description) {
        return new ValidationConstraint(name, value, description, null);
    }

    /**
     * Checks if this constraint is a range constraint
     */
    public boolean isRangeConstraint() {
        return "minInclusive".equals(constraintType) ||
                "maxInclusive".equals(constraintType) ||
                "minExclusive".equals(constraintType) ||
                "maxExclusive".equals(constraintType);
    }

    /**
     * Checks if this constraint is a length constraint
     */
    public boolean isLengthConstraint() {
        return "length".equals(constraintType) ||
                "minLength".equals(constraintType) ||
                "maxLength".equals(constraintType);
    }

    /**
     * Checks if this constraint is a decimal constraint
     */
    public boolean isDecimalConstraint() {
        return "totalDigits".equals(constraintType) ||
                "fractionDigits".equals(constraintType);
    }

    /**
     * Returns the XSD facet name for this constraint
     */
    public String getXsdFacetName() {
        return constraintType;
    }

    /**
     * Returns the XSD representation of this constraint
     */
    public String toXsdFragment() {
        return String.format("<xs:restriction><xs:%s value=\"%s\"/></xs:restriction>",
                constraintType, value);
    }
}