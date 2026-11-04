package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import java.util.List;
import java.util.Locale;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.GlobalComponentKind;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.SchemaLayoutRules;
import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * The texts and icons of the Schema Overview's component rows (UI-free).
 *
 * @since 2.6
 */
public final class OverviewRows {

    private OverviewRows() {
    }

    /** @return the kind column text: the XSD name of the node ({@code complexType}, {@code import}, …) */
    public static String kind(XsdNode node) {
        if (node instanceof XsdComment) {
            return "comment";
        }
        if (node instanceof XsdDefaultOpenContent) {
            return "defaultOpenContent";
        }
        if (SchemaLayoutRules.isDirective(node)) {
            return SchemaLayoutRules.directiveName(node);
        }
        GlobalComponentKind kind = GlobalComponentKind.of(node);
        return kind != null ? kind.xsdName() : node.getNodeType().name().toLowerCase(Locale.ROOT);
    }

    /** @return the icon literal of a row */
    public static String icon(XsdNode node) {
        return switch (node) {
            case XsdImport ignored -> "bi-box-arrow-in-down-right";
            case XsdInclude ignored -> "bi-file-earmark-plus";
            case XsdRedefine ignored -> "bi-pencil-square";
            case XsdOverride ignored -> "bi-arrow-repeat";
            case XsdComment ignored -> "bi-chat-left-text";
            case XsdAnnotation ignored -> "bi-card-text";
            case XsdNotation ignored -> "bi-tag";
            case XsdDefaultOpenContent ignored -> "bi-asterisk";
            case XsdElement ignored -> "bi-box";
            case XsdComplexType ignored -> "bi-diagram-3";
            case XsdSimpleType ignored -> "bi-type";
            case XsdGroup ignored -> "bi-collection";
            case XsdAttributeGroup ignored -> "bi-collection";
            case XsdAttribute ignored -> "bi-at";
            default -> "bi-circle";
        };
    }

    /** @return the name column text: the component name, the location of a directive, the comment text */
    public static String name(XsdNode node) {
        return switch (node) {
            case XsdSchemaReference reference -> nullToEmpty(reference.getSchemaLocation());
            case XsdComment comment -> oneLine(comment.getContent());
            case XsdAnnotation ignored -> "";
            case XsdDefaultOpenContent ignored -> "";
            default -> nullToEmpty(node.getName());
        };
    }

    /** @return true when the name column edits the component name */
    public static boolean hasEditableName(XsdNode node) {
        GlobalComponentKind kind = GlobalComponentKind.of(node);
        return kind != null && kind.isNamed() && !node.isFromInclude();
    }

    /** @return the details column text */
    public static String details(XsdNode node) {
        return switch (node) {
            case XsdImport imp -> "namespace: " + (imp.getNamespace() == null ? "(none)" : imp.getNamespace());
            case XsdSchemaReference reference -> reference.getResolutionError() != null
                    ? "not resolved: " + reference.getResolutionError() : "";
            case XsdElement element -> typeText(element.getType(), element);
            case XsdAttribute attribute -> typeText(attribute.getType(), attribute);
            case XsdComplexType type -> complexDetails(type);
            case XsdSimpleType type -> simpleDetails(type);
            case XsdNotation notation -> join(
                    notation.getPublicId() == null ? null : "public: " + notation.getPublicId(),
                    notation.getSystemId() == null ? null : "system: " + notation.getSystemId());
            case XsdDefaultOpenContent openContent -> "mode: " + (openContent.getMode() == null
                    ? "interleave" : openContent.getMode().getValue());
            default -> "";
        };
    }

    /** @return the first documentation line of the node, or the text of an annotation row */
    public static String annotation(XsdNode node) {
        if (node instanceof XsdComment) {
            return "";
        }
        List<XsdDocumentation> docs = node.getDocumentations();
        if (!docs.isEmpty()) {
            return oneLine(docs.getFirst().getText());
        }
        return oneLine(node.getDocumentation());
    }

    /** @return true when the annotation column can be edited in place (at most one documentation) */
    public static boolean hasEditableAnnotation(XsdNode node) {
        return !(node instanceof XsdComment) && !node.isFromInclude() && node.getDocumentations().size() <= 1;
    }

    /** @return the include file a row comes from, or null for rows of this file */
    public static String origin(XsdNode node) {
        return node.isFromInclude() && node.getSourceFile() != null
                ? String.valueOf(node.getSourceFile().getFileName()) : null;
    }

    private static String typeText(String type, XsdNode node) {
        if (type != null && !type.isBlank()) {
            return "type: " + type;
        }
        for (XsdNode child : node.getChildren()) {
            if (child instanceof XsdComplexType) {
                return "anonymous complex type";
            }
            if (child instanceof XsdSimpleType) {
                return "anonymous simple type";
            }
        }
        return "";
    }

    private static String complexDetails(XsdComplexType type) {
        for (XsdNode child : type.getChildren()) {
            if (child instanceof XsdComplexContent || child instanceof XsdSimpleContent) {
                for (XsdNode derivation : child.getChildren()) {
                    if (derivation instanceof XsdExtension extension) {
                        return "extends " + nullToEmpty(extension.getBase());
                    }
                    if (derivation instanceof XsdRestriction restriction) {
                        return "restricts " + nullToEmpty(restriction.getBase());
                    }
                }
            }
        }
        return type.isAbstract() ? "abstract" : "";
    }

    private static String simpleDetails(XsdSimpleType type) {
        for (XsdNode child : type.getChildren()) {
            if (child instanceof XsdRestriction restriction) {
                return "restricts " + nullToEmpty(restriction.getBase());
            }
            if (child instanceof XsdList list) {
                return "list of " + nullToEmpty(list.getItemType());
            }
            if (child instanceof XsdUnion) {
                return "union";
            }
        }
        return "";
    }

    private static String join(String a, String b) {
        if (a == null) {
            return b == null ? "" : b;
        }
        return b == null ? a : a + " · " + b;
    }

    static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        String line = text.strip().replaceAll("\\s+", " ");
        return line.length() > 160 ? line.substring(0, 157) + "…" : line;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
