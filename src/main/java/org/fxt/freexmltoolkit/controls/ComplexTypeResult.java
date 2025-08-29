package org.fxt.freexmltoolkit.controls;

import java.util.List;

/**
 * Result record for ComplexType creation/editing
 */
public record ComplexTypeResult(
        String name,
        String contentModel,           // sequence, choice, all, empty, simple
        String derivationType,         // none, extension, restriction
        String baseType,               // base type for extension/restriction
        boolean mixedContent,          // mixed content flag
        boolean abstractType,          // abstract type flag
        List<XsdComplexTypeEditor.ElementItem> elements,
        List<XsdComplexTypeEditor.AttributeItem> attributes,
        String documentation
) {
}