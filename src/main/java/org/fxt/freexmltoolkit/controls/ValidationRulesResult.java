package org.fxt.freexmltoolkit.controls;

import org.fxt.freexmltoolkit.controls.XsdValidationRulesEditor.CustomFacet;
import org.fxt.freexmltoolkit.controls.XsdValidationRulesEditor.WhitespaceAction;

import java.util.List;

/**
 * Result record for XSD validation rules configuration
 * Contains all validation constraints that can be applied to XSD elements
 */
public class ValidationRulesResult {

    // Pattern constraint
    private String pattern;

    // Enumeration constraint
    private List<String> enumerationValues;

    // Range constraints
    private String minInclusive;
    private String maxInclusive;
    private String minExclusive;
    private String maxExclusive;

    // Length constraints
    private String length;
    private String minLength;
    private String maxLength;

    // Decimal constraints
    private String totalDigits;
    private String fractionDigits;

    // Whitespace handling
    private WhitespaceAction whitespaceAction;

    // Custom facets
    private List<CustomFacet> customFacets;

    public ValidationRulesResult() {
        // Default constructor
    }

    public ValidationRulesResult(String pattern, List<String> enumerationValues,
                                 String minInclusive, String maxInclusive, String minExclusive, String maxExclusive,
                                 String length, String minLength, String maxLength,
                                 String totalDigits, String fractionDigits,
                                 WhitespaceAction whitespaceAction, List<CustomFacet> customFacets) {
        this.pattern = pattern;
        this.enumerationValues = enumerationValues;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.minExclusive = minExclusive;
        this.maxExclusive = maxExclusive;
        this.length = length;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.totalDigits = totalDigits;
        this.fractionDigits = fractionDigits;
        this.whitespaceAction = whitespaceAction;
        this.customFacets = customFacets;
    }

    // Getters and setters
    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public List<String> getEnumerationValues() {
        return enumerationValues;
    }

    public void setEnumerationValues(List<String> enumerationValues) {
        this.enumerationValues = enumerationValues;
    }

    public String getMinInclusive() {
        return minInclusive;
    }

    public void setMinInclusive(String minInclusive) {
        this.minInclusive = minInclusive;
    }

    public String getMaxInclusive() {
        return maxInclusive;
    }

    public void setMaxInclusive(String maxInclusive) {
        this.maxInclusive = maxInclusive;
    }

    public String getMinExclusive() {
        return minExclusive;
    }

    public void setMinExclusive(String minExclusive) {
        this.minExclusive = minExclusive;
    }

    public String getMaxExclusive() {
        return maxExclusive;
    }

    public void setMaxExclusive(String maxExclusive) {
        this.maxExclusive = maxExclusive;
    }

    public String getLength() {
        return length;
    }

    public void setLength(String length) {
        this.length = length;
    }

    public String getMinLength() {
        return minLength;
    }

    public void setMinLength(String minLength) {
        this.minLength = minLength;
    }

    public String getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(String maxLength) {
        this.maxLength = maxLength;
    }

    public String getTotalDigits() {
        return totalDigits;
    }

    public void setTotalDigits(String totalDigits) {
        this.totalDigits = totalDigits;
    }

    public String getFractionDigits() {
        return fractionDigits;
    }

    public void setFractionDigits(String fractionDigits) {
        this.fractionDigits = fractionDigits;
    }

    public WhitespaceAction getWhitespaceAction() {
        return whitespaceAction;
    }

    public void setWhitespaceAction(WhitespaceAction whitespaceAction) {
        this.whitespaceAction = whitespaceAction;
    }

    public List<CustomFacet> getCustomFacets() {
        return customFacets;
    }

    public void setCustomFacets(List<CustomFacet> customFacets) {
        this.customFacets = customFacets;
    }

    /**
     * Checks if any validation rules are defined
     */
    public boolean hasAnyRules() {
        return pattern != null ||
                (enumerationValues != null && !enumerationValues.isEmpty()) ||
                minInclusive != null || maxInclusive != null ||
                minExclusive != null || maxExclusive != null ||
                length != null || minLength != null || maxLength != null ||
                totalDigits != null || fractionDigits != null ||
                whitespaceAction != null ||
                (customFacets != null && !customFacets.isEmpty());
    }

    /**
     * Returns a human-readable summary of the validation rules
     */
    public String getSummary() {
        StringBuilder summary = new StringBuilder();

        if (pattern != null) {
            summary.append("Pattern: ").append(pattern).append("; ");
        }
        if (enumerationValues != null && !enumerationValues.isEmpty()) {
            summary.append("Enum: ").append(enumerationValues.size()).append(" values; ");
        }
        if (minInclusive != null || maxInclusive != null) {
            summary.append("Range: [").append(minInclusive != null ? minInclusive : "")
                    .append(",").append(maxInclusive != null ? maxInclusive : "").append("]; ");
        }
        if (length != null) {
            summary.append("Length: ").append(length).append("; ");
        } else if (minLength != null || maxLength != null) {
            summary.append("Length: [").append(minLength != null ? minLength : "")
                    .append(",").append(maxLength != null ? maxLength : "").append("]; ");
        }
        if (totalDigits != null) {
            summary.append("Digits: ").append(totalDigits);
            if (fractionDigits != null) {
                summary.append(" (").append(fractionDigits).append(" fraction)");
            }
            summary.append("; ");
        }
        if (whitespaceAction != null && whitespaceAction != WhitespaceAction.PRESERVE) {
            summary.append("Whitespace: ").append(whitespaceAction.name().toLowerCase()).append("; ");
        }
        if (customFacets != null && !customFacets.isEmpty()) {
            summary.append("Custom: ").append(customFacets.size()).append(" facets; ");
        }

        if (summary.length() > 0) {
            // Remove trailing "; "
            summary.setLength(summary.length() - 2);
            return summary.toString();
        } else {
            return "No validation rules defined";
        }
    }

    @Override
    public String toString() {
        return "ValidationRulesResult{" + getSummary() + "}";
    }
}