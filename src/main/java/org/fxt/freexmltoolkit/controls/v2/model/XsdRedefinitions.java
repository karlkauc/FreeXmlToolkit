package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * What {@code xs:redefine} and {@code xs:override} mean for a schema: a definition inside one
 * replaces the definition of the same kind and name in the target file. Inside
 * {@code xs:redefine} a definition may build on the one it replaces by referring to its own name.
 * The target stays a separate schema; this class only tells which of its definitions no longer
 * apply.
 */
public final class XsdRedefinitions {

    private XsdRedefinitions() {
        // utility
    }

    /** @return whether {@code node} is an {@code xs:redefine} or {@code xs:override} */
    public static boolean isRedefinition(XsdNode node) {
        return node instanceof XsdRedefine || node instanceof XsdOverride;
    }

    /** @return the redefinitions of {@code schema} and of the schemas it imports */
    public static List<XsdSchemaReference> of(XsdSchema schema) {
        Set<XsdSchema> schemas = Collections.newSetFromMap(new IdentityHashMap<>());
        schemas.add(schema);
        schemas.addAll(schema.getImportedSchemas().values());
        schemas.remove(null);
        List<XsdSchemaReference> found = new ArrayList<>();
        for (XsdSchema candidate : schemas) {
            for (XsdNode child : candidate.getChildren()) {
                if (isRedefinition(child)) {
                    found.add((XsdSchemaReference) child);
                }
            }
        }
        return found;
    }

    /**
     * @return the definitions in target files that a redefinition replaces, compared by identity;
     *         walking the schema without them counts every definition once
     */
    public static Set<XsdNode> replacedNodes(XsdSchema schema) {
        Set<XsdNode> replaced = Collections.newSetFromMap(new IdentityHashMap<>());
        for (XsdSchemaReference redefinition : of(schema)) {
            XsdSchema target = redefinition.getReferencedSchema();
            if (target == null) {
                continue;
            }
            for (XsdNode definition : redefinition.getChildren()) {
                XsdNode original = definitionIn(target, definition);
                if (original != null) {
                    replaced.add(original);
                }
            }
        }
        return replaced;
    }

    /**
     * Whether a reference means the definition its redefinition replaces: inside
     * {@code xs:redefine}, a type's base or a group's reference with the component's own name.
     *
     * @param reference the node that holds the reference
     * @param localName the local name it refers to
     */
    public static boolean refersToOriginal(XsdNode reference, String localName) {
        XsdNode component = enclosingDefinition(reference);
        if (component == null || !(component.getParent() instanceof XsdRedefine)
                || localName == null || !localName.equals(component.getName())) {
            return false;
        }
        boolean isType = component instanceof XsdComplexType || component instanceof XsdSimpleType;
        return isType
                ? reference instanceof XsdExtension || reference instanceof XsdRestriction
                : component.getClass() == reference.getClass();
    }

    /**
     * @param reference a node inside a definition of an {@code xs:redefine}
     * @return the definition in the target file that this definition replaces, or {@code null}
     */
    public static XsdNode originalOf(XsdNode reference) {
        XsdNode component = enclosingDefinition(reference);
        if (component != null && component.getParent() instanceof XsdRedefine redefine
                && redefine.getReferencedSchema() != null) {
            return definitionIn(redefine.getReferencedSchema(), component);
        }
        return null;
    }

    /** The node's ancestor directly below a redefinition, or {@code null} outside of one. */
    private static XsdNode enclosingDefinition(XsdNode node) {
        XsdNode component = node;
        while (component != null && component.getParent() != null && !isRedefinition(component.getParent())) {
            component = component.getParent();
        }
        return component == null || component.getParent() == null ? null : component;
    }

    private static XsdNode definitionIn(XsdSchema target, XsdNode definition) {
        for (XsdNode child : target.getChildren()) {
            if (child.getClass() == definition.getClass() && child.getName() != null
                    && child.getName().equals(definition.getName())) {
                return child;
            }
        }
        return null;
    }
}
