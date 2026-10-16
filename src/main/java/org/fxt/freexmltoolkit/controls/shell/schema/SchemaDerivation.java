package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.List;
import java.util.Optional;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdList;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdUnion;

/**
 * UI-free type facts for the inspector's Type &amp; Facets and Cardinality sections: the
 * <em>base type</em> and <em>derivation</em> ({@code restriction} / {@code extension} /
 * {@code list} / {@code union}) of a node's type, and a particle's position in its compositor
 * ("2 of 6 · sequence").
 * <p>
 * The derivation is looked up on the node's inline type first; an element/attribute that
 * references a named type defined in the schema (or one of its imported schemas) reports that
 * type's derivation. Built-in types ({@code xs:*}) have no derivation to show.
 */
public final class SchemaDerivation {

    /** The base type of a derivation and how it is derived. */
    public record Derivation(String base, String method) {
    }

    private SchemaDerivation() {
    }

    /**
     * @param node       the selected node (element, attribute, simple/complex type, restriction …)
     * @param schemaRoot the schema root holding the global named types; may be {@code null}
     * @return the derivation of the node's type, or empty when none applies
     */
    public static Optional<Derivation> of(XsdNode node, XsdNode schemaRoot) {
        if (node == null) {
            return Optional.empty();
        }
        Derivation own = direct(node);
        if (own == null) {
            own = inline(node);
        }
        if (own != null) {
            return Optional.of(own);
        }
        String typeName = typeReference(node);
        if (typeName == null || schemaRoot == null) {
            return Optional.empty();
        }
        XsdNode named = findNamedType(schemaRoot, typeName);
        return named == null ? Optional.empty() : Optional.ofNullable(inline(named));
    }

    /**
     * @return the 1-based position of a particle among the particles of its compositor
     * ({@code "2 of 6 · sequence"}), or empty when the node's parent is no sequence/choice/all
     */
    public static Optional<String> order(XsdNode node) {
        if (node == null || node.getParent() == null) {
            return Optional.empty();
        }
        XsdNode parent = node.getParent();
        String compositor = switch (parent.getNodeType()) {
            case SEQUENCE -> "sequence";
            case CHOICE -> "choice";
            case ALL -> "all";
            default -> null;
        };
        if (compositor == null || !isParticle(node)) {
            return Optional.empty();
        }
        List<XsdNode> particles = parent.getChildren().stream().filter(SchemaDerivation::isParticle).toList();
        int index = particles.indexOf(node);
        if (index < 0) {
            return Optional.empty();
        }
        return Optional.of(format(index + 1, particles.size(), compositor));
    }

    /** @return {@code "n of m · compositor"} — shared with the schema-bound XML instance path. */
    public static String format(int position, int count, String compositor) {
        return position + " of " + count + " · " + compositor;
    }

    private static boolean isParticle(XsdNode node) {
        return switch (node.getNodeType()) {
            case ELEMENT, GROUP, ANY, SEQUENCE, CHOICE, ALL -> true;
            default -> false;
        };
    }

    /** The node itself is a derivation construct. */
    private static Derivation direct(XsdNode node) {
        if (node instanceof XsdRestriction r) {
            return new Derivation(orAnonymous(r.getBase()), "restriction");
        }
        if (node instanceof XsdExtension e) {
            return new Derivation(orAnonymous(e.getBase()), "extension");
        }
        if (node instanceof XsdList l) {
            return new Derivation(orAnonymous(l.getItemType()), "list");
        }
        if (node instanceof XsdUnion u) {
            return new Derivation(u.getMemberTypes().isEmpty() ? "(anonymous)"
                    : String.join(" ", u.getMemberTypes()), "union");
        }
        return null;
    }

    /** The first derivation construct inside the node's inline type definition. */
    private static Derivation inline(XsdNode node) {
        for (XsdNode child : node.getChildren()) {
            Derivation d = direct(child);
            if (d != null) {
                return d;
            }
            switch (child.getNodeType()) {
                case SIMPLE_TYPE, COMPLEX_TYPE, SIMPLE_CONTENT, COMPLEX_CONTENT -> {
                    Derivation nested = inline(child);
                    if (nested != null) {
                        return nested;
                    }
                }
                default -> {
                    // do not descend into nested elements/attributes/compositors
                }
            }
        }
        return null;
    }

    /** @return the local name of a non-built-in {@code type} reference, or {@code null} */
    private static String typeReference(XsdNode node) {
        String type = node instanceof XsdElement el ? el.getType()
                : node instanceof XsdAttribute at ? at.getType() : null;
        if (type == null || type.isBlank()) {
            return null;
        }
        int colon = type.indexOf(':');
        String prefix = colon >= 0 ? type.substring(0, colon) : "";
        if (prefix.equals("xs") || prefix.equals("xsd")) {
            return null;
        }
        return colon >= 0 ? type.substring(colon + 1) : type;
    }

    /** A global simple or complex type of that name in the schema or one of its imports. */
    private static XsdNode findNamedType(XsdNode schemaRoot, String name) {
        XsdNode found = findIn(schemaRoot, name);
        if (found == null && schemaRoot instanceof XsdSchema schema) {
            for (XsdSchema imported : schema.getImportedSchemas().values()) {
                found = findIn(imported, name);
                if (found != null) {
                    break;
                }
            }
        }
        return found;
    }

    private static XsdNode findIn(XsdNode schema, String name) {
        if (schema == null) {
            return null;
        }
        for (XsdNode child : schema.getChildren()) {
            XsdNodeType t = child.getNodeType();
            if ((t == XsdNodeType.SIMPLE_TYPE || t == XsdNodeType.COMPLEX_TYPE) && name.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private static String orAnonymous(String s) {
        return s == null || s.isBlank() ? "(anonymous)" : s;
    }
}
