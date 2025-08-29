package org.fxt.freexmltoolkit.controls;

import java.util.List;
import java.util.Map;

/**
 * Result record for SimpleType creation/editing
 */
public record SimpleTypeResult(
        String name,
        String baseType,
        List<String> patterns,
        Map<String, String> enumerations,
        int exactLength,
        int minLength,
        int maxLength,
        String minInclusive,
        String maxInclusive,
        String minExclusive,
        String maxExclusive,
        int totalDigits,
        int fractionDigits,
        String whiteSpace,
        String documentation
) {
}