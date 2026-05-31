package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.Locale;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * UI-free presentation helpers for rendering {@link XsdNode}s in the Schema views:
 * a Bootstrap icon per node type and a one-line display text (name + cardinality
 * + element type). Kept separate from the cell so it is unit-testable.
 */
public final class XsdNodeLabels {

    private XsdNodeLabels() {
    }

    /** @return a resolvable Bootstrap icon literal for the node type (never null). */
    public static String icon(XsdNodeType type) {
        return switch (type) {
            case SCHEMA -> "bi-file-earmark-code";
            case ELEMENT -> "bi-box";
            case ATTRIBUTE -> "bi-at";
            case SEQUENCE -> "bi-list-ol";
            case CHOICE -> "bi-signpost-split";
            case ALL, GROUP, ATTRIBUTE_GROUP -> "bi-collection";
            case COMPLEX_TYPE, COMPLEX_CONTENT -> "bi-diagram-3";
            case SIMPLE_TYPE, SIMPLE_CONTENT -> "bi-type";
            case RESTRICTION, FACET -> "bi-funnel";
            case EXTENSION -> "bi-plus-square";
            case ANNOTATION, DOCUMENTATION, APPINFO -> "bi-card-text";
            case ANY, ANY_ATTRIBUTE -> "bi-asterisk";
            case KEY, KEYREF, UNIQUE, SELECTOR, FIELD -> "bi-key";
            default -> "bi-circle";
        };
    }

    /** @return a one-line label: name (or node-type) + cardinality + element type. */
    public static String displayText(XsdNode node) {
        String name = node.getName();
        String base = (name != null && !name.isBlank()) ? name : typeLabel(node.getNodeType());
        String type = "";
        if (node instanceof XsdElement element) {
            String elementType = element.getType();
            if (elementType != null && !elementType.isBlank()) {
                type = " : " + elementType;
            }
        }
        return base + cardinality(node) + type;
    }

    private static String cardinality(XsdNode node) {
        int min = node.getMinOccurs();
        int max = node.getMaxOccurs();
        if (min == 1 && max == 1) {
            return "";
        }
        String maxText = (max < 0 || max >= Integer.MAX_VALUE) ? "*" : Integer.toString(max);
        return " [" + min + ".." + maxText + "]";
    }

    private static String typeLabel(XsdNodeType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
