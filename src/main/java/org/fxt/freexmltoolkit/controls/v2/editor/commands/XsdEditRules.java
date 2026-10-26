package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.ArrayList;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAll;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdChoice;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexContent;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleContent;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog;

/**
 * Structural rules of XML Schema for the editor's edits: which change keeps the schema valid,
 * which one conflicts with content that would have to go first, and which one is never valid.
 *
 * <p>Every editing entry point (Inspector, Tree and diagram context menus, properties panel) asks
 * these checks before it runs a command, and the commands guard themselves with the hard
 * conflicts ({@link #contentConflict(XsdNode)}, {@link #changeType}) so no path can produce a
 * {@code type} next to an inline type or child content under a simple type.
 *
 * <p>Pure model code: no JavaFX. Type references are resolved against the schema's own,
 * included (inlined) and imported top-level types, one level deep (no recursion, so circular
 * type references are harmless).
 */
public final class XsdEditRules {

    private XsdEditRules() {
    }

    // ----- type and ref ----------------------------------------------------------------------

    /**
     * Setting {@code type} on an element or attribute. A blank type (removing it) is always allowed.
     */
    public static EditCheck changeType(XsdNode node, String newType) {
        if (!(node instanceof XsdElement) && !(node instanceof XsdAttribute)) {
            return EditCheck.blocked("Only elements and attributes have a type.");
        }
        if (newType == null || newType.isBlank()) {
            return EditCheck.allowed();
        }
        String type = newType.trim();
        if (hasText(refOf(node))) {
            return EditCheck.blocked("'" + refOf(node) + "' is a reference: it takes its type from the referenced "
                    + kind(node) + ". Change the referenced declaration instead.");
        }
        XsdNode inline = inlineType(node);
        if (inline != null) {
            return EditCheck.replaceable("'" + displayName(node) + "' defines its own " + describeInline(inline)
                            + ". An " + kind(node) + " cannot have both a type reference and an inline type, so setting "
                            + type + " removes the inline definition.",
                    "Replace with " + type);
        }
        return EditCheck.allowed();
    }

    /** Setting {@code ref} on an element or attribute. A blank ref (removing it) is always allowed. */
    public static EditCheck changeRef(XsdNode node, String newRef) {
        if (newRef == null || newRef.isBlank()) {
            return EditCheck.allowed();
        }
        if (hasText(typeOf(node))) {
            return EditCheck.blocked("'" + displayName(node) + "' has the type " + typeOf(node)
                    + ". A reference brings its own type: remove the type first.");
        }
        XsdNode inline = inlineType(node);
        if (inline != null) {
            return EditCheck.blocked("'" + displayName(node) + "' defines its own " + describeInline(inline)
                    + ". A reference cannot have inline content: remove it first.");
        }
        return EditCheck.allowed();
    }

    // ----- content -------------------------------------------------------------------------

    /**
     * Adding a sequence / choice / all to an element.
     */
    public static EditCheck addCompositor(XsdNode node, String compositorName) {
        String what = compositorName == null ? "compositor" : compositorName;
        if (node instanceof XsdComplexType complexType) {
            EditCheck conflict = complexContentModelConflict(complexType, what);
            return conflict.isAllowed() ? allContentConflict(compositorOf(complexType), what, complexType) : conflict;
        }
        if (node instanceof XsdGroup group) {
            return hasText(group.getRef())
                    ? EditCheck.blocked("A group reference cannot contain a " + what + ".")
                    : EditCheck.allowed();
        }
        if (!(node instanceof XsdElement element)) {
            return EditCheck.blocked("A " + what + " can only be added to an element, a complex type or a group.");
        }
        if (hasText(element.getRef())) {
            return EditCheck.blocked("'" + element.getRef() + "' is a reference: its content comes from the "
                    + "referenced element.");
        }
        if (hasText(element.getType())) {
            return EditCheck.replaceable("'" + displayName(element) + "' has the type " + element.getType()
                            + ". A " + what + " needs an inline complex type, so the type reference is removed.",
                    "Remove type and add " + what);
        }
        XsdNode inline = inlineType(element);
        if (inline instanceof XsdSimpleType) {
            return EditCheck.blocked("'" + displayName(element) + "' defines an inline simple type, which cannot "
                    + "contain child elements.");
        }
        if (inline instanceof XsdComplexType complexType) {
            EditCheck conflict = complexContentModelConflict(complexType, what);
            return conflict.isAllowed() ? allContentConflict(compositorOf(complexType), what, element) : conflict;
        }
        return EditCheck.allowed();
    }

    /**
     * Turning an existing compositor into {@code xs:all} (its particles move over): {@code xs:all}
     * holds no nested sequence/choice/all, and in XSD 1.0 only elements that occur at most once.
     */
    private static EditCheck allContentConflict(XsdNode existing, String what, XsdNode context) {
        if (existing == null || !"all".equals(what)) {
            return EditCheck.allowed();
        }
        XsdSchema schema = schemaOf(context);
        boolean xsd11 = schema != null && "1.1".equals(schema.detectXsdVersion());
        for (XsdNode particle : existing.getChildren()) {
            if (isCompositor(particle)) {
                return EditCheck.blocked("An all cannot contain a nested sequence, choice or all.");
            }
            if (!xsd11 && (particle instanceof XsdGroup
                    || particle instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdAny)) {
                return EditCheck.blocked("In XSD 1.0 an all may only contain elements.");
            }
            if (!xsd11 && particle instanceof XsdElement e && (e.getMaxOccurs() < 0 || e.getMaxOccurs() > 1)) {
                return EditCheck.blocked("In XSD 1.0 every element in an all occurs at most once; '"
                        + displayName(e) + "' has maxOccurs " + (e.getMaxOccurs() < 0 ? "unbounded" : e.getMaxOccurs())
                        + ".");
            }
        }
        return EditCheck.allowed();
    }

    /** Adding a child element (or pasting/moving one) under {@code parent}. */
    public static EditCheck addElement(XsdNode parent) {
        if (parent instanceof XsdSchema || isCompositor(parent)) {
            return EditCheck.allowed();
        }
        EditCheck conflict = contentConflict(parent);
        if (!conflict.isAllowed()) {
            return conflict;
        }
        if (parent instanceof XsdGroup || parent instanceof XsdComplexType || parent instanceof XsdElement) {
            return elementContainer(parent) != null
                    ? EditCheck.allowed()
                    : EditCheck.blocked("'" + displayName(parent) + "' has no Sequence, Choice or All for child "
                    + "elements: add one first.");
        }
        return EditCheck.blocked("Elements cannot be added to " + article(parent) + ".");
    }

    /** Adding an attribute (or pasting/moving one) under {@code parent}. */
    public static EditCheck addAttribute(XsdNode parent) {
        if (parent instanceof XsdAttributeGroup group) {
            return hasText(group.getRef())
                    ? EditCheck.blocked("An attribute group reference cannot contain attributes.")
                    : EditCheck.allowed();
        }
        if (parent instanceof XsdComplexType || parent instanceof XsdExtension || parent instanceof XsdRestriction) {
            return parent instanceof XsdRestriction r && r.getParent() instanceof XsdSimpleType
                    ? EditCheck.blocked("A simple type restriction cannot have attributes.")
                    : EditCheck.allowed();
        }
        if (parent instanceof XsdElement) {
            EditCheck conflict = contentConflict(parent);
            if (!conflict.isAllowed()) {
                return conflict;
            }
            return inlineType(parent) instanceof XsdSimpleType
                    ? EditCheck.blocked("'" + displayName(parent) + "' defines an inline simple type, which cannot "
                    + "have attributes.")
                    : EditCheck.allowed();
        }
        return EditCheck.blocked("Attributes cannot be added to " + article(parent) + ".");
    }

    /**
     * Adding an element with all properties of {@code spec} under {@code parent}: the parent
     * rule ({@link #addElement}) plus {@link #newElementProperties}.
     */
    public static EditCheck newElement(XsdNode parent, NewElementSpec spec) {
        EditCheck properties = newElementProperties(parent, spec);
        return properties.isAllowed() ? addElement(parent) : properties;
    }

    /**
     * The properties of a new element under {@code parent}: a valid name, cardinality and value
     * constraint, and top-level-only properties only on a top-level element.
     */
    public static EditCheck newElementProperties(XsdNode parent, NewElementSpec spec) {
        if (spec == null) {
            return EditCheck.blocked("Nothing to add.");
        }
        EditCheck name = newName(spec.name(), "element");
        if (!name.isAllowed()) {
            return name;
        }
        boolean global = parent instanceof XsdSchema;
        if (global) {
            if (spec.minOccurs() != 1 || spec.maxOccurs() != 1) {
                return EditCheck.blocked("A top-level element has no minOccurs or maxOccurs: set the "
                        + "cardinality where the element is referenced.");
            }
            for (XsdNode child : ((XsdSchema) parent).getChildren()) {
                if (child instanceof XsdElement && spec.name().trim().equals(child.getName())) {
                    return EditCheck.blocked("The schema already has a top-level element named '"
                            + spec.name().trim() + "'.");
                }
            }
        } else {
            if (spec.minOccurs() < 0) {
                return EditCheck.blocked("minOccurs cannot be negative.");
            }
            if (spec.maxOccurs() != ChangeCardinalityCommand.UNBOUNDED && spec.minOccurs() > spec.maxOccurs()) {
                return EditCheck.blocked("minOccurs (" + spec.minOccurs() + ") is greater than maxOccurs ("
                        + spec.maxOccurs() + ").");
            }
            if (spec.abstractElement()) {
                return EditCheck.blocked("Only a top-level element can be abstract.");
            }
            if (hasText(spec.substitutionGroup())) {
                return EditCheck.blocked("Only a top-level element can join a substitution group.");
            }
        }
        if (hasValue(spec.defaultValue()) && hasValue(spec.fixed())) {
            return EditCheck.blocked("An element can have a default or a fixed value, not both.");
        }
        return EditCheck.allowed();
    }

    /**
     * Adding an attribute with all properties of {@code spec} under {@code parent}: the parent
     * rule ({@link #addAttribute}) plus {@link #newAttributeProperties}.
     */
    public static EditCheck newAttribute(XsdNode parent, NewAttributeSpec spec) {
        EditCheck properties = newAttributeProperties(spec);
        return properties.isAllowed() ? addAttribute(parent) : properties;
    }

    /** The properties of a new attribute: a valid name and a use that fits the value constraint. */
    public static EditCheck newAttributeProperties(NewAttributeSpec spec) {
        if (spec == null) {
            return EditCheck.blocked("Nothing to add.");
        }
        EditCheck name = newName(spec.name(), "attribute");
        if (!name.isAllowed()) {
            return name;
        }
        boolean hasDefault = hasValue(spec.defaultValue());
        boolean hasFixed = hasValue(spec.fixed());
        if (hasDefault && hasFixed) {
            return EditCheck.blocked("An attribute can have a default or a fixed value, not both.");
        }
        String use = spec.use() == null ? "" : spec.use().trim();
        if (hasDefault && !use.isEmpty() && !"optional".equals(use)) {
            return EditCheck.blocked("An attribute with a default value must be optional.");
        }
        if (hasFixed && "prohibited".equals(use)) {
            return EditCheck.blocked("A prohibited attribute cannot have a fixed value.");
        }
        return EditCheck.allowed();
    }

    private static EditCheck newName(String name, String what) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return EditCheck.blocked("Enter a name for the " + what + ".");
        }
        if (!isNcName(n)) {
            return EditCheck.blocked("'" + n + "' is not a valid XML name: start with a letter or '_', "
                    + "then use letters, digits, '.', '-' or '_' (no spaces or ':').");
        }
        return EditCheck.allowed();
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isEmpty();
    }

    /** Pasting {@code node} under {@code target}: the rule of the pasted kind. */
    public static EditCheck paste(XsdNode target, XsdNode node) {
        if (target == null || node == null) {
            return EditCheck.blocked("Nothing to paste.");
        }
        return place(target, node);
    }

    /** Moving {@code node} under another parent (reordering among siblings is always allowed). */
    public static EditCheck move(XsdNode node, XsdNode newParent) {
        if (node == null || newParent == null || node.getParent() == newParent) {
            return EditCheck.allowed();
        }
        for (XsdNode p = newParent; p != null; p = p.getParent()) {
            if (p == node) {
                return EditCheck.blocked("A node cannot be moved into itself.");
            }
        }
        return place(newParent, node);
    }

    /**
     * The hard conflicts of putting content (child elements or attributes) under an element:
     * a reference, a simple type reference, an unresolvable type or an inline simple type.
     * The commands guard themselves with this check.
     */
    public static EditCheck contentConflict(XsdNode parent) {
        if (!(parent instanceof XsdElement element)) {
            return EditCheck.allowed();
        }
        if (hasText(element.getRef())) {
            return EditCheck.blocked("'" + element.getRef() + "' is a reference: its content comes from the "
                    + "referenced element.");
        }
        String type = element.getType();
        if (hasText(type)) {
            XsdNode named = findNamedType(element, type);
            if (named instanceof XsdComplexType) {
                return EditCheck.allowed();
            }
            if (named instanceof XsdSimpleType || isBuiltinRef(element, type) && !isAnyType(type)) {
                return EditCheck.blocked("'" + displayName(element) + "' has the simple type " + type
                        + ", which cannot contain child elements or attributes.");
            }
            if (isBuiltinRef(element, type)) {
                return EditCheck.blocked("'" + displayName(element) + "' has the type " + type
                        + " (any content). To define its content, remove the type and add a Sequence, Choice or All.");
            }
            return EditCheck.blocked("The type " + type + " of '" + displayName(element)
                    + "' is not defined in this schema, so its content cannot be extended here.");
        }
        if (inlineType(element) instanceof XsdSimpleType) {
            return EditCheck.blocked("'" + displayName(element) + "' defines an inline simple type, which cannot "
                    + "contain child elements or attributes.");
        }
        return EditCheck.allowed();
    }

    /**
     * Where a child element added to {@code parent} goes: the compositor of the inline complex type,
     * of the referenced named complex type, or of the group; {@code null} when there is none.
     */
    public static XsdNode elementContainer(XsdNode parent) {
        if (parent instanceof XsdSchema || isCompositor(parent)) {
            return parent;
        }
        if (parent instanceof XsdElement element) {
            if (hasText(element.getType()) && findNamedType(element, element.getType())
                    instanceof XsdComplexType named) {
                XsdNode c = compositorOf(named);
                if (c != null) {
                    return c;
                }
            }
            for (XsdNode child : element.getChildren()) {
                if (child instanceof XsdComplexType ct) {
                    XsdNode c = compositorOf(ct);
                    if (c != null) {
                        return c;
                    }
                } else if (isCompositor(child)) {
                    return child;
                }
            }
            return null;
        }
        if (parent instanceof XsdComplexType || parent instanceof XsdGroup) {
            return compositorOf(parent);
        }
        return null;
    }

    // ----- global types --------------------------------------------------------------------

    /**
     * Declaring a new top-level simple or complex type named {@code name}. Simple and complex
     * types share one symbol space, so the name must be unused by both (own and included types).
     */
    public static EditCheck addGlobalType(XsdSchema schema, String name) {
        if (schema == null) {
            return EditCheck.blocked("No schema is open.");
        }
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return EditCheck.blocked("Enter a name for the type.");
        }
        if (!isNcName(n)) {
            return EditCheck.blocked("'" + n + "' is not a valid XML name: start with a letter or '_', "
                    + "then use letters, digits, '.', '-' or '_' (no spaces or ':').");
        }
        for (XsdNode child : schema.getChildren()) {
            if ((child instanceof XsdComplexType || child instanceof XsdSimpleType) && n.equals(child.getName())) {
                return EditCheck.blocked("The schema already has " + (child instanceof XsdComplexType
                        ? "a complex" : "a simple") + " type named '" + n + "'.");
            }
        }
        return EditCheck.allowed();
    }

    /** @return {@code base}, or {@code base} with the first free number appended ("NewType2", …) */
    public static String uniqueTypeName(XsdSchema schema, String base) {
        if (addGlobalType(schema, base).isAllowed()) {
            return base;
        }
        for (int i = 2; ; i++) {
            if (addGlobalType(schema, base + i).isAllowed()) {
                return base + i;
            }
        }
    }

    /** @return the prefix bound to the XSD namespace in {@code context}'s schema, {@code xs} if none */
    public static String xsdPrefix(XsdNode context) {
        XsdSchema schema = schemaOf(context);
        if (schema != null) {
            String fallback = null;
            for (var e : schema.getNamespaces().entrySet()) {
                if (XsdBuiltinTypeCatalog.XSD_NAMESPACE.equals(e.getValue()) && hasText(e.getKey())) {
                    if ("xs".equals(e.getKey())) {
                        return "xs";
                    }
                    fallback = fallback == null ? e.getKey() : fallback;
                }
            }
            if (fallback != null) {
                return fallback;
            }
        }
        return "xs";
    }

    /** @return whether {@code s} is an XML NCName (a name without a colon) */
    public static boolean isNcName(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        int first = s.codePointAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }
        for (int i = Character.charCount(first); i < s.length(); ) {
            int c = s.codePointAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_'
                    || Character.getType(c) == Character.NON_SPACING_MARK
                    || Character.getType(c) == Character.COMBINING_SPACING_MARK)) {
                return false;
            }
            i += Character.charCount(c);
        }
        return true;
    }

    // ----- helpers -------------------------------------------------------------------------

    /** @return the inline (anonymous) {@code xs:complexType} / {@code xs:simpleType} child, or null. */
    public static XsdNode inlineType(XsdNode node) {
        if (node == null) {
            return null;
        }
        for (XsdNode child : node.getChildren()) {
            if (child instanceof XsdComplexType || child instanceof XsdSimpleType) {
                return child;
            }
        }
        return null;
    }

    /**
     * Where an attribute added to {@code parent} goes: the extension/restriction of a
     * simpleContent/complexContent, else the complex type itself (inline or the referenced named
     * one). {@code null} for an element without any complex type yet (the command creates an
     * inline one) or for a parent that cannot hold attributes.
     */
    public static XsdNode attributeContainer(XsdNode parent) {
        if (parent instanceof XsdElement element) {
            if (hasText(element.getType()) && findNamedType(element, element.getType())
                    instanceof XsdComplexType named) {
                return attributeContainer(named);
            }
            XsdNode inline = inlineType(element);
            return inline instanceof XsdComplexType ct ? attributeContainer(ct) : null;
        }
        if (parent instanceof XsdComplexType complexType) {
            for (XsdNode child : complexType.getChildren()) {
                if (child instanceof XsdSimpleContent || child instanceof XsdComplexContent) {
                    for (XsdNode derivation : child.getChildren()) {
                        if (derivation instanceof XsdExtension || derivation instanceof XsdRestriction) {
                            return derivation;
                        }
                    }
                }
            }
            return complexType;
        }
        if (parent instanceof XsdExtension || parent instanceof XsdRestriction || parent instanceof XsdAttributeGroup) {
            return parent;
        }
        return null;
    }

    /**
     * @return whether {@code typeRef} names an XSD built-in datatype: its prefix is bound to the XSD
     * namespace (or is {@code xs}/{@code xsd} when undeclared), or it is unprefixed, built-in and not
     * shadowed by a type of the schema
     */
    public static boolean isBuiltinRef(XsdNode context, String typeRef) {
        if (!hasText(typeRef) || !XsdBuiltinTypeCatalog.isBuiltin(typeRef)) {
            return false;
        }
        String ref = typeRef.trim();
        int colon = ref.indexOf(':');
        if (colon < 0) {
            return findNamedType(context, ref) == null;
        }
        String prefix = ref.substring(0, colon);
        XsdSchema schema = schemaOf(context);
        String uri = schema == null ? null : schema.getNamespaces().get(prefix);
        return uri != null ? XsdBuiltinTypeCatalog.XSD_NAMESPACE.equals(uri)
                : "xs".equals(prefix) || "xsd".equals(prefix);
    }

    private static boolean isAnyType(String typeRef) {
        return "anyType".equals(typeRef.substring(typeRef.indexOf(':') + 1).trim());
    }

    /**
     * @return the top-level simple or complex type named {@code typeRef} in the schema of
     * {@code context} — own, included or imported — or {@code null}; a reference whose prefix is
     * bound to the XSD namespace never resolves to a schema type
     */
    public static XsdNode findNamedType(XsdNode context, String typeRef) {
        if (!hasText(typeRef)) {
            return null;
        }
        String ref = typeRef.trim();
        int colon = ref.indexOf(':');
        XsdSchema owner = schemaOf(context);
        if (colon > 0 && owner != null
                && XsdBuiltinTypeCatalog.XSD_NAMESPACE.equals(owner.getNamespaces().get(ref.substring(0, colon)))) {
            return null;
        }
        String local = ref.substring(colon + 1).trim();
        XsdSchema schema = schemaOf(context);
        if (schema == null) {
            return null;
        }
        List<XsdNode> roots = new ArrayList<>();
        roots.add(schema);
        roots.addAll(schema.getImportedSchemas().values());
        for (XsdNode root : roots) {
            for (XsdNode child : root.getChildren()) {
                if ((child instanceof XsdComplexType || child instanceof XsdSimpleType)
                        && local.equals(child.getName())) {
                    return child;
                }
            }
        }
        return null;
    }

    private static EditCheck place(XsdNode target, XsdNode node) {
        if (node instanceof XsdElement) {
            return addElement(target);
        }
        if (node instanceof XsdAttribute || node instanceof XsdAttributeGroup) {
            return addAttribute(target);
        }
        if (isCompositor(node)) {
            return addCompositor(target, node.getClass().getSimpleName().replace("Xsd", "").toLowerCase());
        }
        if ((node instanceof XsdComplexType || node instanceof XsdSimpleType) && hasText(node.getName())
                || node instanceof XsdGroup g && !hasText(g.getRef())) {
            return target instanceof XsdSchema
                    ? EditCheck.allowed()
                    : EditCheck.blocked("A named type or group can only be placed at the top level of the schema.");
        }
        return EditCheck.allowed();
    }

    private static EditCheck complexContentModelConflict(XsdComplexType complexType, String what) {
        for (XsdNode child : complexType.getChildren()) {
            if (child instanceof XsdSimpleContent) {
                return EditCheck.blocked("The complex type has simple content (text only), which cannot contain a "
                        + what + ".");
            }
            if (child instanceof XsdComplexContent) {
                return EditCheck.blocked("The complex type derives its content via complexContent: add the " + what
                        + " to its extension or restriction.");
            }
        }
        return EditCheck.allowed();
    }

    private static XsdNode compositorOf(XsdNode container) {
        for (XsdNode child : container.getChildren()) {
            if (isCompositor(child)) {
                return child;
            }
        }
        return null;
    }

    private static boolean isCompositor(XsdNode node) {
        return node instanceof XsdSequence || node instanceof XsdChoice || node instanceof XsdAll;
    }

    private static XsdSchema schemaOf(XsdNode node) {
        XsdNode current = node;
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }
        return (XsdSchema) current;
    }

    private static String describeInline(XsdNode inline) {
        if (inline instanceof XsdSimpleType) {
            return "inline simple type";
        }
        XsdNode compositor = compositorOf(inline);
        if (compositor == null) {
            return "inline complex type";
        }
        long elements = compositor.getChildren().stream().filter(c -> c instanceof XsdElement).count();
        String name = compositor.getClass().getSimpleName().replace("Xsd", "").toLowerCase();
        return "inline complex type with a " + name + " of " + elements + (elements == 1 ? " element" : " elements");
    }

    private static String typeOf(XsdNode node) {
        return node instanceof XsdElement e ? e.getType() : node instanceof XsdAttribute a ? a.getType() : null;
    }

    private static String refOf(XsdNode node) {
        return node instanceof XsdElement e ? e.getRef() : node instanceof XsdAttribute a ? a.getRef() : null;
    }

    private static String kind(XsdNode node) {
        return node instanceof XsdAttribute ? "attribute" : "element";
    }

    private static String displayName(XsdNode node) {
        String name = node.getName();
        return hasText(name) ? name : hasText(refOf(node)) ? refOf(node) : "(unnamed)";
    }

    private static String article(XsdNode node) {
        String simple = node.getClass().getSimpleName().replace("Xsd", "");
        return "a node of kind " + simple;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
