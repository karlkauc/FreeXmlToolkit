package org.fxt.freexmltoolkit.controls.shell.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Maps a path in an XML instance ({@code /FundsXML/DataSupplier[1]/Name}) to the declaration in
 * the V2 schema model that governs it, so the inspector can show and edit that declaration while
 * the user works in the XML document.
 * <p>
 * The walk starts at the global element named by the first step and descends through each
 * declaration's content model: an inline or named complex type, its compositors
 * (sequence/choice/all), {@code xs:group} references, {@code complexContent} extension base chains
 * and {@code ref=} particles, in the schema and its imported schemas. Position predicates are
 * ignored. Every type/group is entered at most once per step, so recursive schemas terminate.
 * UI-free.
 */
public final class XsdInstanceLocator {

    private XsdInstanceLocator() {
    }

    /**
     * Where an instance node is declared.
     *
     * @param particle    the node that sits in the content model: carries minOccurs/maxOccurs
     *                    (a {@code ref=} particle, or the declaration itself)
     * @param declaration the element/attribute declaration that carries type, facets, nillable,
     *                    default/fixed and documentation (the referenced global one for a ref)
     * @param namedType   the non-builtin named type the declaration uses, or {@code null} for an
     *                    inline type / builtin type
     * @param global      whether the declaration is a top-level schema component
     * @param foreign     whether the declaration comes from an included or imported schema file
     *                    (edits to it would not reach the root document)
     * @param particlePath the particle of every instance step from the global root element down
     *                    to {@code particle} (a {@code ref=} particle, not the referenced global),
     *                    ending with the attribute particle for an attribute lookup — the cards
     *                    the Graphic view shows along that instance path
     */
    public record Located(XsdNode particle, XsdNode declaration, XsdNode namedType, boolean global,
                          boolean foreign, List<XsdNode> particlePath) {
        public Located {
            particlePath = particlePath == null ? List.of() : List.copyOf(particlePath);
        }
    }

    /** Locates the element at {@code instancePath}. */
    public static Optional<Located> locateElement(XsdSchema schema, List<String> instancePath) {
        return locate(schema, instancePath, null);
    }

    /**
     * Locates the element at {@code instancePath}, or — with {@code attributeName} — that
     * attribute of it.
     *
     * @param instancePath element local names from the document root, e.g. {@code [FundsXML, DataSupplier]}
     */
    public static Optional<Located> locate(XsdSchema schema, List<String> instancePath, String attributeName) {
        if (schema == null || instancePath == null || instancePath.isEmpty()) {
            return Optional.empty();
        }
        List<XsdSchema> schemas = allSchemas(schema);
        XsdNode particle = findGlobal(schemas, XsdNodeType.ELEMENT, instancePath.get(0));
        XsdNode declaration = particle;
        List<XsdNode> particlePath = new ArrayList<>();
        if (particle != null) {
            particlePath.add(particle);
        }
        for (int i = 1; i < instancePath.size() && declaration != null; i++) {
            String step = instancePath.get(i);
            particle = null;
            for (XsdNode candidate : childElementParticles(declaration, schemas)) {
                if (step.equals(effectiveName(candidate))) {
                    particle = candidate;
                    break;
                }
            }
            if (particle != null) {
                particlePath.add(particle);
            }
            declaration = particle == null ? null : resolveRef(particle, schemas, XsdNodeType.ELEMENT);
        }
        if (declaration == null) {
            return Optional.empty();
        }
        if (attributeName != null) {
            XsdNode attrParticle = null;
            for (XsdNode candidate : attributes(declaration, schemas)) {
                if (attributeName.equals(effectiveName(candidate))) {
                    attrParticle = candidate;
                    break;
                }
            }
            if (attrParticle == null) {
                return Optional.empty();
            }
            particle = attrParticle;
            particlePath.add(attrParticle);
            declaration = resolveRef(attrParticle, schemas, XsdNodeType.ATTRIBUTE);
            if (declaration == null) {
                return Optional.empty();
            }
        }
        XsdNode namedType = namedTypeOf(declaration, schemas);
        boolean global = declaration.getParent() instanceof XsdSchema;
        boolean foreign = isForeign(declaration, schema) || isForeign(particle, schema);
        return Optional.of(new Located(particle, declaration, namedType, global, foreign, particlePath));
    }

    /**
     * @return the element local names of a positional XPath ({@code /a:Root/b[2]/c} → {@code [Root, b, c]});
     * an attribute step ({@code @x}) is dropped
     */
    public static List<String> instancePath(String xpath) {
        List<String> out = new ArrayList<>();
        if (xpath == null) {
            return out;
        }
        for (String step : xpath.split("/")) {
            if (step.isBlank() || step.startsWith("@")) {
                continue;
            }
            int bracket = step.indexOf('[');
            String name = bracket >= 0 ? step.substring(0, bracket) : step;
            out.add(name.substring(name.indexOf(':') + 1));
        }
        return out;
    }

    /** @return the named, non-builtin type the element/attribute uses, or {@code null} */
    public static XsdNode namedTypeOf(XsdNode declaration, XsdSchema schema) {
        return namedTypeOf(declaration, allSchemas(schema));
    }

    // ----- content model walk --------------------------------------------------

    /** Element particles directly allowed inside {@code element} (compositors/groups/extensions flattened). */
    private static List<XsdNode> childElementParticles(XsdNode element, List<XsdSchema> schemas) {
        List<XsdNode> out = new ArrayList<>();
        XsdNode type = contentType(element, schemas);
        if (type != null) {
            collectParticles(type, schemas, out, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        return out;
    }

    /** The complex type governing an element's content: inline child, else its named type. */
    private static XsdNode contentType(XsdNode element, List<XsdSchema> schemas) {
        for (XsdNode child : element.getChildren()) {
            if (child.getNodeType() == XsdNodeType.COMPLEX_TYPE) {
                return child;
            }
        }
        String type = element instanceof XsdElement el ? el.getType() : null;
        return findGlobal(schemas, XsdNodeType.COMPLEX_TYPE, local(type));
    }

    private static void collectParticles(XsdNode node, List<XsdSchema> schemas, List<XsdNode> out, Set<XsdNode> visited) {
        if (!visited.add(node)) {
            return;
        }
        for (XsdNode child : node.getChildren()) {
            switch (child.getNodeType()) {
                case ELEMENT -> out.add(child);
                case SEQUENCE, CHOICE, ALL, COMPLEX_CONTENT, RESTRICTION -> collectParticles(child, schemas, out, visited);
                case EXTENSION -> {
                    // Base content comes first, then the extension's own particles.
                    XsdNode base = findGlobal(schemas, XsdNodeType.COMPLEX_TYPE, local(((XsdExtension) child).getBase()));
                    if (base != null) {
                        collectParticles(base, schemas, out, visited);
                    }
                    collectParticles(child, schemas, out, visited);
                }
                case GROUP -> {
                    XsdNode group = child;
                    String ref = ((XsdGroup) child).getRef();
                    if (ref != null && !ref.isBlank()) {
                        group = findGlobal(schemas, XsdNodeType.GROUP, local(ref));
                    }
                    if (group != null) {
                        collectParticles(group, schemas, out, visited);
                    }
                }
                default -> {
                    // attributes, annotations, identity constraints … are no element particles
                }
            }
        }
    }

    /** Attribute declarations of an element, including attribute groups and base types. */
    private static List<XsdNode> attributes(XsdNode element, List<XsdSchema> schemas) {
        List<XsdNode> out = new ArrayList<>();
        XsdNode type = contentType(element, schemas);
        if (type != null) {
            collectAttributes(type, schemas, out, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
        return out;
    }

    private static void collectAttributes(XsdNode node, List<XsdSchema> schemas, List<XsdNode> out, Set<XsdNode> visited) {
        if (!visited.add(node)) {
            return;
        }
        for (XsdNode child : node.getChildren()) {
            switch (child.getNodeType()) {
                case ATTRIBUTE -> out.add(child);
                case SIMPLE_CONTENT, COMPLEX_CONTENT, RESTRICTION -> collectAttributes(child, schemas, out, visited);
                case EXTENSION -> {
                    XsdNode base = findGlobal(schemas, XsdNodeType.COMPLEX_TYPE, local(((XsdExtension) child).getBase()));
                    if (base != null) {
                        collectAttributes(base, schemas, out, visited);
                    }
                    collectAttributes(child, schemas, out, visited);
                }
                case ATTRIBUTE_GROUP -> {
                    XsdNode group = child;
                    String ref = ((XsdAttributeGroup) child).getRef();
                    if (ref != null && !ref.isBlank()) {
                        group = findGlobal(schemas, XsdNodeType.ATTRIBUTE_GROUP, local(ref));
                    }
                    if (group != null) {
                        collectAttributes(group, schemas, out, visited);
                    }
                }
                default -> {
                    // element particles etc.
                }
            }
        }
    }

    // ----- lookup helpers --------------------------------------------------------

    /** The name a particle matches in the instance: its own name, or the local part of its ref. */
    private static String effectiveName(XsdNode particle) {
        String ref = particle instanceof XsdElement el ? el.getRef()
                : particle instanceof XsdAttribute at ? at.getRef() : null;
        if (ref != null && !ref.isBlank()) {
            return local(ref);
        }
        return particle.getName();
    }

    /** A {@code ref=} particle resolved to its global declaration; other particles are returned as is. */
    private static XsdNode resolveRef(XsdNode particle, List<XsdSchema> schemas, XsdNodeType kind) {
        String ref = particle instanceof XsdElement el ? el.getRef()
                : particle instanceof XsdAttribute at ? at.getRef() : null;
        if (ref == null || ref.isBlank()) {
            return particle;
        }
        return findGlobal(schemas, kind, local(ref));
    }

    private static XsdNode namedTypeOf(XsdNode declaration, List<XsdSchema> schemas) {
        String type = declaration instanceof XsdElement el ? el.getType()
                : declaration instanceof XsdAttribute at ? at.getType() : null;
        if (type == null || type.isBlank() || isBuiltin(type)) {
            return null;
        }
        XsdNode named = findGlobal(schemas, XsdNodeType.COMPLEX_TYPE, local(type));
        return named != null ? named : findGlobal(schemas, XsdNodeType.SIMPLE_TYPE, local(type));
    }

    private static boolean isBuiltin(String type) {
        return type.startsWith("xs:") || type.startsWith("xsd:");
    }

    /** @return the top-level component of the given kind and local name in any of the schemas */
    private static XsdNode findGlobal(List<XsdSchema> schemas, XsdNodeType kind, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (XsdSchema schema : schemas) {
            for (XsdNode child : schema.getChildren()) {
                if (child.getNodeType() == kind && name.equals(child.getName())) {
                    return child;
                }
            }
        }
        return null;
    }

    /** The schema followed by its (already flattened, transitively loaded) imported schemas. */
    private static List<XsdSchema> allSchemas(XsdSchema schema) {
        List<XsdSchema> out = new ArrayList<>();
        out.add(schema);
        for (XsdSchema imported : schema.getImportedSchemas().values()) {
            if (imported != null && !out.contains(imported)) {
                out.add(imported);
            }
        }
        return out;
    }

    /** Included (inlined) nodes and nodes of imported schemas do not belong to the root document. */
    private static boolean isForeign(XsdNode node, XsdSchema root) {
        if (node == null) {
            return false;
        }
        if (node.isFromInclude()) {
            return true;
        }
        XsdNode top = node;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        return top != root;
    }

    private static String local(String qname) {
        if (qname == null || qname.isBlank()) {
            return null;
        }
        return qname.substring(qname.indexOf(':') + 1);
    }
}
