package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.model.XsdAlternative;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdList;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdOverride;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRedefine;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdUnion;

/**
 * The reference graph of an XSD schema: every global component (named type, group, attribute
 * group, element, attribute declared directly under {@code xs:schema}, including nodes inlined
 * from {@code xs:include}) and every reference between components — {@code type}, {@code base},
 * {@code itemType}, {@code memberTypes}, {@code ref} (element, attribute, group, attribute
 * group) and {@code substitutionGroup}. Imported schemas are walked as referrers so a type used
 * only by an imported schema still counts as used; their own globals are not part of
 * {@link #components()}.
 * <p>
 * Built once per schema and shared by the statistics collector, the quality checker, the type
 * usage finder and the flatten transformer. Names are matched after stripping any namespace
 * prefix (the model stores references as written), so two components with the same local name
 * in different namespaces are not told apart — the established convention of this code base.
 * <p>
 * Instances are immutable snapshots: rebuild after the model changed.
 */
public final class SchemaReferenceGraph {

    /** The kinds of global components the graph tracks. */
    public enum ComponentKind {
        COMPLEX_TYPE("Complex type"),
        SIMPLE_TYPE("Simple type"),
        GROUP("Group"),
        ATTRIBUTE_GROUP("Attribute group"),
        ELEMENT("Element"),
        ATTRIBUTE("Attribute");

        private final String displayName;

        ComponentKind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /** The kinds that can be removed when unused (never global elements/attributes). */
        public static final Set<ComponentKind> REMOVABLE =
                Collections.unmodifiableSet(EnumSet.of(COMPLEX_TYPE, SIMPLE_TYPE, GROUP, ATTRIBUTE_GROUP));

        /** The two type kinds (they share one symbol space in XSD). */
        public static final Set<ComponentKind> TYPES = Collections.unmodifiableSet(EnumSet.of(COMPLEX_TYPE, SIMPLE_TYPE));
    }

    /** Identifies a global component by kind and prefix-free local name. */
    public record ComponentKey(ComponentKind kind, String localName) {
        public ComponentKey {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(localName, "localName");
        }

        @Override
        public String toString() {
            return kind.getDisplayName() + " '" + localName + "'";
        }
    }

    /** A global component of the main schema tree. */
    public record Component(ComponentKey key, XsdNode node, boolean fromInclude, Path sourceFile) {
    }

    /**
     * One reference from a node to a (named) component.
     *
     * @param targetName     the referenced local name (prefix stripped)
     * @param candidateKinds the kinds the reference may resolve to (types share a symbol space)
     * @param target         the resolved main-schema component, or {@code null} when the name is
     *                       not declared in the main tree (built-in, imported, or missing)
     * @param referrer       the node carrying the reference
     * @param type           the attribute the reference sits in
     * @param owner          the global component whose subtree contains {@code referrer}, or
     *                       {@code null} for imported schemas
     * @param fromImport     {@code true} when the referrer belongs to an imported schema
     * @param sourceFile     the referrer's source file (multi-file schemas), or {@code null}
     */
    public record Reference(String targetName, Set<ComponentKind> candidateKinds, ComponentKey target,
                            XsdNode referrer, UsageReferenceType type, ComponentKey owner,
                            boolean fromImport, Path sourceFile) {

        /** @return {@code true} when this reference points at the component that contains it. */
        public boolean isSelfReference() {
            return target != null && target.equals(owner);
        }

        /** @return {@code true} for base/itemType/memberTypes references (type derivation). */
        public boolean isDerivation() {
            return type == UsageReferenceType.RESTRICTION_BASE
                    || type == UsageReferenceType.EXTENSION_BASE
                    || type == UsageReferenceType.LIST_ITEM_TYPE
                    || type == UsageReferenceType.UNION_MEMBER_TYPE;
        }

        /** @return this reference as a {@link TypeUsageLocation} (the finder's result type). */
        public TypeUsageLocation toUsageLocation() {
            return new TypeUsageLocation(referrer, type, sourceFile);
        }
    }

    private static final Set<String> XSD_PREFIXES = Set.of("xs", "xsd");

    private final Map<ComponentKey, Component> components;
    private final Map<ComponentKey, XsdNode> importedComponents;
    private final List<Reference> references;
    private final Map<ComponentKey, List<Reference>> incoming;
    private final Map<String, List<Reference>> byTargetName;
    private final boolean redefineOrOverride;
    /** Prefix → namespace URI as declared on {@code xs:schema}. */
    private final Map<String, String> prefixMap;
    private final String targetNamespace;

    private SchemaReferenceGraph(Map<ComponentKey, Component> components,
                                 Map<ComponentKey, XsdNode> importedComponents,
                                 List<Reference> references,
                                 boolean redefineOrOverride,
                                 Map<String, String> prefixMap,
                                 String targetNamespace) {
        this.components = Collections.unmodifiableMap(components);
        this.importedComponents = Collections.unmodifiableMap(importedComponents);
        this.references = Collections.unmodifiableList(references);
        this.redefineOrOverride = redefineOrOverride;
        this.prefixMap = prefixMap;
        this.targetNamespace = targetNamespace;
        Map<ComponentKey, List<Reference>> in = new HashMap<>();
        Map<String, List<Reference>> byName = new HashMap<>();
        for (Reference reference : references) {
            if (reference.target() != null) {
                in.computeIfAbsent(reference.target(), k -> new ArrayList<>()).add(reference);
            }
            byName.computeIfAbsent(reference.targetName(), k -> new ArrayList<>()).add(reference);
        }
        this.incoming = in;
        this.byTargetName = byName;
    }

    /** Builds the graph of {@code schema} (main tree incl. inlined includes, imports as referrers). */
    public static SchemaReferenceGraph build(XsdSchema schema) {
        Objects.requireNonNull(schema, "Schema cannot be null");
        Map<ComponentKey, Component> components = new LinkedHashMap<>();
        boolean redefine = false;
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdRedefine || child instanceof XsdOverride) {
                redefine = true;
            }
            ComponentKey key = keyOf(child);
            if (key != null) {
                components.putIfAbsent(key, new Component(key, child, child.isFromInclude(), child.getSourceFile()));
            }
        }
        Map<ComponentKey, XsdNode> imported = new LinkedHashMap<>();
        for (XsdSchema importedSchema : schema.getImportedSchemas().values()) {
            for (XsdNode child : importedSchema.getChildren()) {
                ComponentKey key = keyOf(child);
                if (key != null) {
                    imported.putIfAbsent(key, child);
                }
            }
        }

        Map<String, String> prefixMap = schema.getNamespaces() != null
                ? new HashMap<>(schema.getNamespaces()) : new HashMap<>();

        List<Reference> references = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (XsdNode child : schema.getChildren()) {
            collectReferences(child, keyOf(child), false, components, references, visited);
        }
        for (XsdSchema importedSchema : schema.getImportedSchemas().values()) {
            if (importedSchema == schema) {
                continue;
            }
            collectReferences(importedSchema, null, true, components, references, visited);
        }
        return new SchemaReferenceGraph(components, imported, references, redefine, prefixMap,
                schema.getTargetNamespace());
    }

    /** The key of a global declaration, or {@code null} when {@code node} is not one. */
    public static ComponentKey keyOf(XsdNode node) {
        if (node == null || node.getName() == null || node.getName().isBlank()) {
            return null;
        }
        String name = stripPrefix(node.getName());
        return switch (node) {
            case XsdComplexType ignored -> new ComponentKey(ComponentKind.COMPLEX_TYPE, name);
            case XsdSimpleType ignored -> new ComponentKey(ComponentKind.SIMPLE_TYPE, name);
            case XsdGroup group -> group.isReference() ? null : new ComponentKey(ComponentKind.GROUP, name);
            case XsdAttributeGroup group -> group.isReference() ? null : new ComponentKey(ComponentKind.ATTRIBUTE_GROUP, name);
            case XsdElement element -> isBlank(element.getRef()) ? new ComponentKey(ComponentKind.ELEMENT, name) : null;
            case XsdAttribute attribute -> isBlank(attribute.getRef()) ? new ComponentKey(ComponentKind.ATTRIBUTE, name) : null;
            default -> null;
        };
    }

    private static void collectReferences(XsdNode root, ComponentKey owner, boolean fromImport,
                                          Map<ComponentKey, Component> components,
                                          List<Reference> out, Set<String> visited) {
        Deque<XsdNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            XsdNode node = stack.pop();
            String id = node.getId();
            if (id != null && !visited.add(id)) {
                continue;
            }
            addReferencesOf(node, owner, fromImport, components, out);
            List<XsdNode> children = node.getChildren();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
    }

    private static void addReferencesOf(XsdNode node, ComponentKey owner, boolean fromImport,
                                        Map<ComponentKey, Component> components, List<Reference> out) {
        Path sourceFile = node.getSourceFile();
        switch (node) {
            case XsdElement element -> {
                add(out, components, element.getType(), ComponentKind.TYPES, node,
                        UsageReferenceType.ELEMENT_TYPE, owner, fromImport, sourceFile);
                add(out, components, element.getRef(), EnumSet.of(ComponentKind.ELEMENT), node,
                        UsageReferenceType.ELEMENT_REF, owner, fromImport, sourceFile);
                add(out, components, element.getSubstitutionGroup(), EnumSet.of(ComponentKind.ELEMENT), node,
                        UsageReferenceType.SUBSTITUTION_GROUP, owner, fromImport, sourceFile);
            }
            case XsdAttribute attribute -> {
                add(out, components, attribute.getType(), ComponentKind.TYPES, node,
                        UsageReferenceType.ATTRIBUTE_TYPE, owner, fromImport, sourceFile);
                add(out, components, attribute.getRef(), EnumSet.of(ComponentKind.ATTRIBUTE), node,
                        UsageReferenceType.ATTRIBUTE_REF, owner, fromImport, sourceFile);
            }
            case XsdRestriction restriction -> add(out, components, restriction.getBase(), ComponentKind.TYPES, node,
                    UsageReferenceType.RESTRICTION_BASE, owner, fromImport, sourceFile);
            case XsdExtension extension -> add(out, components, extension.getBase(), ComponentKind.TYPES, node,
                    UsageReferenceType.EXTENSION_BASE, owner, fromImport, sourceFile);
            case XsdList list -> add(out, components, list.getItemType(), ComponentKind.TYPES, node,
                    UsageReferenceType.LIST_ITEM_TYPE, owner, fromImport, sourceFile);
            case XsdUnion union -> {
                if (union.getMemberTypes() != null) {
                    Set<String> seen = new HashSet<>();
                    for (String member : union.getMemberTypes()) {
                        if (member != null && seen.add(stripPrefix(member))) {
                            add(out, components, member, ComponentKind.TYPES, node,
                                    UsageReferenceType.UNION_MEMBER_TYPE, owner, fromImport, sourceFile);
                        }
                    }
                }
            }
            case XsdAlternative alternative -> add(out, components, alternative.getType(), ComponentKind.TYPES, node,
                    UsageReferenceType.ALTERNATIVE_TYPE, owner, fromImport, sourceFile);
            case XsdGroup group -> {
                if (group.isReference()) {
                    add(out, components, group.getRef(), EnumSet.of(ComponentKind.GROUP), node,
                            UsageReferenceType.GROUP_REF, owner, fromImport, sourceFile);
                }
            }
            case XsdAttributeGroup attributeGroup -> {
                if (attributeGroup.isReference()) {
                    add(out, components, attributeGroup.getRef(), EnumSet.of(ComponentKind.ATTRIBUTE_GROUP), node,
                            UsageReferenceType.ATTRIBUTE_GROUP_REF, owner, fromImport, sourceFile);
                }
            }
            default -> {
            }
        }
    }

    private static void add(List<Reference> out, Map<ComponentKey, Component> components, String rawName,
                            Set<ComponentKind> candidates, XsdNode referrer, UsageReferenceType type,
                            ComponentKey owner, boolean fromImport, Path sourceFile) {
        if (isBlank(rawName)) {
            return;
        }
        String local = stripPrefix(rawName.trim());
        ComponentKey target = null;
        for (ComponentKind kind : candidates) {
            ComponentKey candidate = new ComponentKey(kind, local);
            if (components.containsKey(candidate)) {
                target = candidate;
                break;
            }
        }
        out.add(new Reference(local, candidates, target, referrer, type, owner, fromImport, sourceFile));
    }

    // ---------------------------------------------------------------- queries

    /** @return the global components of the main schema tree, in declaration order. */
    public Map<ComponentKey, Component> components() {
        return components;
    }

    /** @return the global components of the given kinds, in declaration order. */
    public List<Component> components(ComponentKind... kinds) {
        Set<ComponentKind> wanted = kinds.length == 0 ? EnumSet.allOf(ComponentKind.class) : EnumSet.of(kinds[0], kinds);
        List<Component> result = new ArrayList<>();
        for (Component component : components.values()) {
            if (wanted.contains(component.key().kind())) {
                result.add(component);
            }
        }
        return result;
    }

    /** @return the component for {@code key}, or {@code null}. */
    public Component component(ComponentKey key) {
        return components.get(key);
    }

    /** @return every reference in the schema (main tree and imported schemas), in document order. */
    public List<Reference> references() {
        return references;
    }

    /** @return the references that resolve to {@code key} (imported referrers included). */
    public List<Reference> referencesTo(ComponentKey key) {
        return incoming.getOrDefault(key, List.of());
    }

    /**
     * @return the <em>type</em> references ({@code type}/{@code base}/{@code itemType}/
     * {@code memberTypes}/alternative) naming {@code localName}, whether or not it resolves —
     * the {@link TypeUsageFinder} contract, which also reports {@code xs:string} usages.
     */
    public List<Reference> typeReferencesTo(String localName) {
        List<Reference> result = new ArrayList<>();
        for (Reference reference : byTargetName.getOrDefault(stripPrefix(localName), List.of())) {
            if (reference.candidateKinds().contains(ComponentKind.COMPLEX_TYPE)) {
                result.add(reference);
            }
        }
        return result;
    }

    /** @return the number of references resolving to {@code key}. */
    public int usageCount(ComponentKey key) {
        return referencesTo(key).size();
    }

    /** @return the components of the given kinds with no incoming reference at all, in declaration order. */
    public Set<ComponentKey> unreferenced(ComponentKind... kinds) {
        Set<ComponentKey> result = new LinkedHashSet<>();
        for (Component component : components(kinds)) {
            if (!incoming.containsKey(component.key())) {
                result.add(component.key());
            }
        }
        return result;
    }

    /**
     * The components of the given kinds referenced exactly once, and not by themselves
     * (candidates for inlining). Defaults to both type kinds.
     */
    public Set<ComponentKey> singleUse(ComponentKind... kinds) {
        ComponentKind[] effective = kinds.length == 0
                ? new ComponentKind[]{ComponentKind.COMPLEX_TYPE, ComponentKind.SIMPLE_TYPE} : kinds;
        Set<ComponentKey> result = new LinkedHashSet<>();
        for (Component component : components(effective)) {
            List<Reference> in = referencesTo(component.key());
            if (in.size() == 1 && !in.get(0).isSelfReference()) {
                result.add(component.key());
            }
        }
        return result;
    }

    /**
     * Removable components (types, groups, attribute groups) that no global element or
     * attribute reaches, directly or through other components — the cascading "unused" set.
     * References from imported schemas keep a component alive. A schema without any global
     * element or attribute (a pure type library) falls back to {@link #unreferenced}.
     */
    public Set<ComponentKey> unreachable() {
        Deque<ComponentKey> queue = new ArrayDeque<>();
        Set<ComponentKey> reached = new HashSet<>();
        for (Component component : components.values()) {
            ComponentKind kind = component.key().kind();
            if (kind == ComponentKind.ELEMENT || kind == ComponentKind.ATTRIBUTE) {
                if (reached.add(component.key())) {
                    queue.add(component.key());
                }
            }
        }
        if (queue.isEmpty()) {
            return unreferenced(ComponentKind.REMOVABLE.toArray(ComponentKind[]::new));
        }
        for (Reference reference : references) {
            if (reference.fromImport() && reference.target() != null && reached.add(reference.target())) {
                queue.add(reference.target());
            }
        }
        Map<ComponentKey, List<ComponentKey>> outgoing = outgoingEdges(false);
        while (!queue.isEmpty()) {
            ComponentKey current = queue.poll();
            for (ComponentKey next : outgoing.getOrDefault(current, List.of())) {
                if (reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        Set<ComponentKey> result = new LinkedHashSet<>();
        for (Component component : components.values()) {
            if (ComponentKind.REMOVABLE.contains(component.key().kind()) && !reached.contains(component.key())) {
                result.add(component.key());
            }
        }
        return result;
    }

    /**
     * Strongly connected components of size &gt; 1, plus single components that reference
     * themselves, over all reference kinds — element/type containment recursion included.
     */
    public List<List<ComponentKey>> cycles() {
        return tarjan(outgoingEdges(false));
    }

    /**
     * Cycles built from derivation edges only ({@code base}, {@code itemType},
     * {@code memberTypes}): a type deriving from itself, directly or indirectly, which no
     * validator accepts.
     */
    public List<List<ComponentKey>> derivationCycles() {
        return tarjan(outgoingEdges(true));
    }

    /**
     * References naming a component that exists nowhere: not in the main tree, not in a loaded
     * imported schema, not an XML Schema built-in, and not in a foreign namespace prefix (those
     * belong to imports that may not be loaded). Empty when the schema uses
     * {@code xs:redefine}/{@code xs:override}, whose components are not modeled.
     */
    public List<Reference> unresolvedReferences() {
        if (redefineOrOverride) {
            return List.of();
        }
        List<Reference> result = new ArrayList<>();
        for (Reference reference : references) {
            if (reference.target() != null || reference.fromImport()) {
                continue;
            }
            String raw = rawName(reference);
            if (raw == null || isForeignPrefixed(raw)) {
                continue;
            }
            boolean inImport = false;
            for (ComponentKind kind : reference.candidateKinds()) {
                if (importedComponents.containsKey(new ComponentKey(kind, reference.targetName()))) {
                    inImport = true;
                    break;
                }
            }
            if (!inImport) {
                result.add(reference);
            }
        }
        return result;
    }

    /** @return {@code true} when the schema contains {@code xs:redefine} or {@code xs:override}. */
    public boolean hasRedefineOrOverride() {
        return redefineOrOverride;
    }

    // ---------------------------------------------------------------- internals

    /** Component → referenced components (resolved, main tree only), deduplicated. */
    private Map<ComponentKey, List<ComponentKey>> outgoingEdges(boolean derivationOnly) {
        Map<ComponentKey, LinkedHashSet<ComponentKey>> edges = new LinkedHashMap<>();
        for (Reference reference : references) {
            if (reference.owner() == null || reference.target() == null) {
                continue;
            }
            if (derivationOnly && !reference.isDerivation()) {
                continue;
            }
            edges.computeIfAbsent(reference.owner(), k -> new LinkedHashSet<>()).add(reference.target());
        }
        Map<ComponentKey, List<ComponentKey>> result = new LinkedHashMap<>();
        edges.forEach((k, v) -> result.put(k, new ArrayList<>(v)));
        return result;
    }

    /** Iterative Tarjan; returns SCCs with more than one member or a self-loop, members in declaration order. */
    private List<List<ComponentKey>> tarjan(Map<ComponentKey, List<ComponentKey>> edges) {
        List<ComponentKey> order = new ArrayList<>(components.keySet());
        Map<ComponentKey, Integer> index = new HashMap<>();
        Map<ComponentKey, Integer> low = new HashMap<>();
        Deque<ComponentKey> stack = new ArrayDeque<>();
        Set<ComponentKey> onStack = new HashSet<>();
        List<List<ComponentKey>> result = new ArrayList<>();
        int[] counter = {0};

        record Frame(ComponentKey node, List<ComponentKey> next, int[] pos) {
        }

        for (ComponentKey start : order) {
            if (index.containsKey(start)) {
                continue;
            }
            Deque<Frame> frames = new ArrayDeque<>();
            frames.push(new Frame(start, edges.getOrDefault(start, List.of()), new int[]{0}));
            index.put(start, counter[0]);
            low.put(start, counter[0]);
            counter[0]++;
            stack.push(start);
            onStack.add(start);
            while (!frames.isEmpty()) {
                Frame frame = frames.peek();
                if (frame.pos()[0] < frame.next().size()) {
                    ComponentKey next = frame.next().get(frame.pos()[0]++);
                    if (!index.containsKey(next)) {
                        index.put(next, counter[0]);
                        low.put(next, counter[0]);
                        counter[0]++;
                        stack.push(next);
                        onStack.add(next);
                        frames.push(new Frame(next, edges.getOrDefault(next, List.of()), new int[]{0}));
                    } else if (onStack.contains(next)) {
                        low.put(frame.node(), Math.min(low.get(frame.node()), index.get(next)));
                    }
                } else {
                    frames.pop();
                    if (!frames.isEmpty()) {
                        ComponentKey parent = frames.peek().node();
                        low.put(parent, Math.min(low.get(parent), low.get(frame.node())));
                    }
                    if (low.get(frame.node()).equals(index.get(frame.node()))) {
                        List<ComponentKey> scc = new ArrayList<>();
                        ComponentKey member;
                        do {
                            member = stack.pop();
                            onStack.remove(member);
                            scc.add(member);
                        } while (!member.equals(frame.node()));
                        boolean selfLoop = scc.size() == 1
                                && edges.getOrDefault(scc.get(0), List.of()).contains(scc.get(0));
                        if (scc.size() > 1 || selfLoop) {
                            scc.sort((a, b) -> Integer.compare(order.indexOf(a), order.indexOf(b)));
                            result.add(Collections.unmodifiableList(scc));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static String rawName(Reference reference) {
        return switch (reference.type()) {
            case ELEMENT_TYPE -> ((XsdElement) reference.referrer()).getType();
            case ELEMENT_REF -> ((XsdElement) reference.referrer()).getRef();
            case SUBSTITUTION_GROUP -> ((XsdElement) reference.referrer()).getSubstitutionGroup();
            case ATTRIBUTE_TYPE -> ((XsdAttribute) reference.referrer()).getType();
            case ATTRIBUTE_REF -> ((XsdAttribute) reference.referrer()).getRef();
            case RESTRICTION_BASE -> ((XsdRestriction) reference.referrer()).getBase();
            case EXTENSION_BASE -> ((XsdExtension) reference.referrer()).getBase();
            case LIST_ITEM_TYPE -> ((XsdList) reference.referrer()).getItemType();
            case ALTERNATIVE_TYPE -> ((XsdAlternative) reference.referrer()).getType();
            case GROUP_REF -> ((XsdGroup) reference.referrer()).getRef();
            case ATTRIBUTE_GROUP_REF -> ((XsdAttributeGroup) reference.referrer()).getRef();
            case UNION_MEMBER_TYPE -> {
                List<String> members = ((XsdUnion) reference.referrer()).getMemberTypes();
                String match = null;
                if (members != null) {
                    for (String member : members) {
                        if (member != null && stripPrefix(member.trim()).equals(reference.targetName())) {
                            match = member.trim();
                            break;
                        }
                    }
                }
                yield match;
            }
        };
    }

    /** {@code true} for xs:/xsd: names and for prefixes bound to a namespace other than the target namespace. */
    private boolean isForeignPrefixed(String raw) {
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String prefix = raw.substring(0, colon);
        if (XSD_PREFIXES.contains(prefix)) {
            return true;
        }
        String uri = prefixMap.get(prefix);
        if (uri == null) {
            // Unknown prefix: we cannot prove the name is local, so do not report it.
            return true;
        }
        if ("http://www.w3.org/2001/XMLSchema".equals(uri)) {
            return true;
        }
        return targetNamespace != null && !targetNamespace.equals(uri);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** "tns:MyType" → "MyType". */
    public static String stripPrefix(String name) {
        if (name == null) {
            return null;
        }
        int colon = name.lastIndexOf(':');
        return colon >= 0 && colon < name.length() - 1 ? name.substring(colon + 1) : name;
    }
}
