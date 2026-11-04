package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Rules for the top level of {@code xs:schema}: the order XSD demands and the names of
 * global components.
 * <p>
 * XSD requires {@code (include | import | redefine | override | annotation)*}, then an
 * optional {@code defaultOpenContent}, then the components
 * {@code (simpleType | complexType | group | attributeGroup | element | attribute | notation)},
 * with annotations (and comments) allowed anywhere in between.
 *
 * @since 2.6
 */
public final class SchemaLayoutRules {

    private SchemaLayoutRules() {
    }

    /** @return true for xs:import / xs:include / xs:redefine / xs:override */
    public static boolean isDirective(XsdNode node) {
        return node instanceof XsdImport || node instanceof XsdInclude
                || node instanceof XsdRedefine || node instanceof XsdOverride;
    }

    /** @return true for nodes that may stand anywhere (annotations and comments) */
    public static boolean isFloating(XsdNode node) {
        return node instanceof XsdAnnotation || node instanceof XsdComment;
    }

    /** @return true for the components that must follow all directives */
    public static boolean isComponent(XsdNode node) {
        return !isDirective(node) && !isFloating(node) && !(node instanceof XsdDefaultOpenContent);
    }

    /**
     * Checks a top-level order: directives first, then defaultOpenContent, then components.
     *
     * @param order the proposed children of the schema
     * @return allowed, or blocked with the reason
     */
    public static EditCheck checkOrder(List<XsdNode> order) {
        boolean openContentSeen = false;
        boolean componentSeen = false;
        for (XsdNode node : order) {
            if (node.isFromInclude()) {
                continue; // content of an included file: listed at its include, never saved here
            }
            if (isDirective(node)) {
                if (componentSeen || openContentSeen) {
                    return EditCheck.blocked("xs:" + directiveName(node)
                            + " must come before all components of the schema.");
                }
            } else if (node instanceof XsdDefaultOpenContent) {
                if (componentSeen) {
                    return EditCheck.blocked("xs:defaultOpenContent must come before all components of the schema.");
                }
                openContentSeen = true;
            } else if (isComponent(node)) {
                componentSeen = true;
            }
        }
        return EditCheck.allowed();
    }

    /** @return the first index at which a component may be inserted */
    public static int firstComponentIndex(XsdSchema schema) {
        List<XsdNode> children = schema.getChildren();
        int index = 0;
        for (int i = 0; i < children.size(); i++) {
            XsdNode child = children.get(i);
            if (isDirective(child) || child instanceof XsdDefaultOpenContent) {
                index = i + 1;
            }
        }
        return index;
    }

    /** @return the index after the last directive, where a new import/include goes */
    public static int directiveInsertIndex(XsdSchema schema) {
        List<XsdNode> children = schema.getChildren();
        int index = 0;
        for (int i = 0; i < children.size(); i++) {
            XsdNode child = children.get(i);
            if (isDirective(child)) {
                index = i + 1;
            } else if (isComponent(child) || child instanceof XsdDefaultOpenContent) {
                break;
            }
        }
        return index;
    }

    /**
     * Checks a name for a new global component.
     *
     * @return allowed, or blocked with the reason
     */
    public static EditCheck addComponent(XsdSchema schema, GlobalComponentKind kind, String name) {
        if (schema == null) {
            return EditCheck.blocked("No schema is open.");
        }
        if (kind == null || !kind.isNamed()) {
            return EditCheck.allowed();
        }
        return checkName(schema, kind, name, null);
    }

    /**
     * Checks a new name for a global component.
     *
     * @return allowed, or blocked with the reason
     */
    public static EditCheck rename(XsdNode node, String newName) {
        GlobalComponentKind kind = GlobalComponentKind.of(node);
        if (kind == null || !kind.isNamed() || !(node.getParent() instanceof XsdSchema schema)) {
            return EditCheck.blocked("Only global components can be renamed here.");
        }
        return checkName(schema, kind, newName, node);
    }

    private static EditCheck checkName(XsdSchema schema, GlobalComponentKind kind, String name, XsdNode self) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return EditCheck.blocked("Enter a name for the " + kind.label().toLowerCase() + ".");
        }
        if (!XsdEditRules.isNcName(n)) {
            return EditCheck.blocked("'" + n + "' is not a valid XML name: start with a letter or '_', "
                    + "then use letters, digits, '.', '-' or '_' (no spaces or ':').");
        }
        XsdNode existing = find(schema, kind, n);
        if (existing != null && existing != self) {
            GlobalComponentKind other = GlobalComponentKind.of(existing);
            return EditCheck.blocked("The schema already has " + article(other.label()) + " "
                    + other.label().toLowerCase() + " named '" + n + "'.");
        }
        return EditCheck.allowed();
    }

    /** @return the global component with that name in the kind's symbol space, or null */
    public static XsdNode find(XsdSchema schema, GlobalComponentKind kind, String name) {
        for (XsdNode child : schema.getChildren()) {
            GlobalComponentKind childKind = GlobalComponentKind.of(child);
            if (childKind != null && childKind.symbolSpace() == kind.symbolSpace() && name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    /** @return {@code base}, or {@code base} with the first free number appended */
    public static String uniqueName(XsdSchema schema, GlobalComponentKind kind, String base) {
        if (find(schema, kind, base) == null) {
            return base;
        }
        for (int i = 2; ; i++) {
            if (find(schema, kind, base + i) == null) {
                return base + i;
            }
        }
    }

    private static String article(String word) {
        return "AEIOU".indexOf(Character.toUpperCase(word.charAt(0))) >= 0 ? "an" : "a";
    }

    /** @return the XSD name of a directive ({@code import}, …) */
    public static String directiveName(XsdNode node) {
        return switch (node) {
            case XsdImport ignored -> "import";
            case XsdInclude ignored -> "include";
            case XsdRedefine ignored -> "redefine";
            case XsdOverride ignored -> "override";
            default -> node.getNodeType().name().toLowerCase();
        };
    }
}
