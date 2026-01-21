package org.fxt.freexmltoolkit.domain;

import java.util.List;
import java.util.Map;

/**
 * Represents a validation constraint in XSD schema
 * Used for transferring validation information between UI and domain layer
 *
 * @param constraintType The constraint type (e.g., "pattern", "enumeration")
 * @param value The constraint value
 * @param description Human-readable description
 * @param attributes Additional attributes
 */
public record ValidationConstraint(
        String constraintType,  // "pattern", "enumeration", "minInclusive", etc.
        String value,          // The constraint value
        String description,    // Human-readable description
        Map<String, String> attributes  // Additional attributes
) {

    /**
     * Creates a pattern constraint.
     *
     * @param pattern the regular expression pattern
     * @return a new pattern constraint
     */
    public static ValidationConstraint pattern(String pattern) {
        return new ValidationConstraint("pattern", pattern, "Regular expression pattern", null);
    }

    /**
     * Creates an enumeration constraint.
     *
     * @param values the list of allowed values
     * @return a new enumeration constraint
     */
    public static ValidationConstraint enumeration(List<String> values) {
        return new ValidationConstraint("enumeration", String.join(",", values),
                "Enumeration values: " + values.size() + " items", null);
    }

    /**
     * Creates a minimum inclusive constraint.
     *
     * @param value the minimum inclusive value
     * @return a new minInclusive constraint
     */
    public static ValidationConstraint minInclusive(String value) {
        return new ValidationConstraint("minInclusive", value,
                "Minimum inclusive value: " + value, null);
    }

    /**
     * Creates a maximum inclusive constraint.
     *
     * @param value the maximum inclusive value
     * @return a new maxInclusive constraint
     */
    public static ValidationConstraint maxInclusive(String value) {
        return new ValidationConstraint("maxInclusive", value,
                "Maximum inclusive value: " + value, null);
    }

    /**
     * Creates a minimum exclusive constraint.
     *
     * @param value the minimum exclusive value
     * @return a new minExclusive constraint
     */
    public static ValidationConstraint minExclusive(String value) {
        return new ValidationConstraint("minExclusive", value,
                "Minimum exclusive value: " + value, null);
    }

    /**
     * Creates a maximum exclusive constraint.
     *
     * @param value the maximum exclusive value
     * @return a new maxExclusive constraint
     */
    public static ValidationConstraint maxExclusive(String value) {
        return new ValidationConstraint("maxExclusive", value,
                "Maximum exclusive value: " + value, null);
    }

    /**
     * Creates an exact length constraint.
     *
     * @param value the exact length value
     * @return a new length constraint
     */
    public static ValidationConstraint length(String value) {
        return new ValidationConstraint("length", value,
                "Exact length: " + value + " characters", null);
    }

    /**
     * Creates a minimum length constraint.
     *
     * @param value the minimum length value
     * @return a new minLength constraint
     */
    public static ValidationConstraint minLength(String value) {
        return new ValidationConstraint("minLength", value,
                "Minimum length: " + value + " characters", null);
    }

    /**
     * Creates a maximum length constraint.
     *
     * @param value the maximum length value
     * @return a new maxLength constraint
     */
    public static ValidationConstraint maxLength(String value) {
        return new ValidationConstraint("maxLength", value,
                "Maximum length: " + value + " characters", null);
    }

    /**
     * Creates a total digits constraint.
     *
     * @param value the total digits value
     * @return a new totalDigits constraint
     */
    public static ValidationConstraint totalDigits(String value) {
        return new ValidationConstraint("totalDigits", value,
                "Total digits: " + value, null);
    }

    /**
     * Creates a fraction digits constraint.
     *
     * @param value the fraction digits value
     * @return a new fractionDigits constraint
     */
    public static ValidationConstraint fractionDigits(String value) {
        return new ValidationConstraint("fractionDigits", value,
                "Fraction digits: " + value, null);
    }

    /**
     * Creates a whitespace handling constraint.
     *
     * @param action the whitespace action (preserve, replace, collapse)
     * @return a new whiteSpace constraint
     */
    public static ValidationConstraint whitespace(String action) {
        return new ValidationConstraint("whiteSpace", action,
                "Whitespace action: " + action, null);
    }

    /**
     * Creates a custom constraint.
     *
     * @param name        the constraint name
     * @param value       the constraint value
     * @param description the human-readable description
     * @return a new custom constraint
     */
    public static ValidationConstraint custom(String name, String value, String description) {
        return new ValidationConstraint(name, value, description, null);
    }

    /**
     * Checks if this constraint is a range constraint.
     *
     * @return true if this is a min/max inclusive/exclusive constraint
     */
    public boolean isRangeConstraint() {
        return "minInclusive".equals(constraintType) ||
                "maxInclusive".equals(constraintType) ||
                "minExclusive".equals(constraintType) ||
                "maxExclusive".equals(constraintType);
    }

    /**
     * Checks if this constraint is a length constraint.
     *
     * @return true if this is a length, minLength, or maxLength constraint
     */
    public boolean isLengthConstraint() {
        return "length".equals(constraintType) ||
                "minLength".equals(constraintType) ||
                "maxLength".equals(constraintType);
    }

    /**
     * Checks if this constraint is a decimal constraint.
     *
     * @return true if this is a totalDigits or fractionDigits constraint
     */
    public boolean isDecimalConstraint() {
        return "totalDigits".equals(constraintType) ||
                "fractionDigits".equals(constraintType);
    }

    /**
     * Returns the XSD facet name for this constraint.
     *
     * @return the XSD facet name
     */
    public String getXsdFacetName() {
        return constraintType;
    }

    /**
     * Returns the XSD representation of this constraint.
     *
     * @return the XSD fragment as a string
     */
    public String toXsdFragment() {
        return String.format("<xs:restriction><xs:%s value=\"%s\"/></xs:restriction>",
                constraintType, value);
    }
}