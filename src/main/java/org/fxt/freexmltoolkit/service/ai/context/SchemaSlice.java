package org.fxt.freexmltoolkit.service.ai.context;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.shell.schema.SchemaFacets;
import org.fxt.freexmltoolkit.controls.shell.schema.XsdInstanceLocator;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdImport;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * What a schema says about one place in an instance document — declaration, type, cardinality, facets,
 * enumeration values, documentation, children and attributes — as JSON (the {@code schema_describe} tool) and as
 * compact text (a model's context in Phase 2). Also the outline of a schema and of one global element's content.
 *
 * <p>Built on {@link XsdInstanceLocator} and {@link SchemaFacets}, the same rules the Properties Inspector uses.
 * Every walk is bounded: recursive types are cut ({@code recursive: true}), outlines by depth. UI-free.
 */
public final class SchemaSlice {

    private final XsdSchema schema;
    private final XsdInstanceLocator.Located located;
    private final List<String> path;
    private final String attribute;

    private SchemaSlice(XsdSchema schema, XsdInstanceLocator.Located located, List<String> path, String attribute) {
        this.schema = schema;
        this.located = located;
        this.path = List.copyOf(path);
        this.attribute = attribute;
    }

    /**
     * The slice for an instance path ({@code [Order, Status]}), or for an attribute of that element.
     *
     * @return empty when the schema declares nothing at that path
     */
    public static Optional<SchemaSlice> describe(XsdSchema schema, List<String> instancePath, String attribute) {
        return XsdInstanceLocator.locate(schema, instancePath, attribute)
                .map(located -> new SchemaSlice(schema, located, instancePath, attribute));
    }

    /** The longest prefix of {@code instancePath} that the schema declares; empty when not even the root. */
    public static List<String> nearestKnownPath(XsdSchema schema, List<String> instancePath) {
        for (int length = instancePath.size(); length > 0; length--) {
            List<String> prefix = instancePath.subList(0, length);
            if (XsdInstanceLocator.locateElement(schema, prefix).isPresent()) {
                return List.copyOf(prefix);
            }
        }
        return List.of();
    }

    /** The names of the elements allowed below {@code instancePath}, or an empty list. */
    public static List<String> childNames(XsdSchema schema, List<String> instancePath) {
        return XsdInstanceLocator.locateElement(schema, instancePath)
                .map(located -> XsdInstanceLocator.childElements(located.declaration(), schema).stream()
                        .map(XsdInstanceLocator::instanceName).toList())
                .orElse(List.of());
    }

    // ---------------------------------------------------------------- describe

    public JsonObject toJson() {
        XsdNode declaration = located.declaration();
        boolean isAttribute = attribute != null;
        JsonObject json = new JsonObject();
        json.addProperty("found", true);
        json.addProperty("kind", isAttribute ? "attribute" : "element");
        json.addProperty("name", XsdInstanceLocator.instanceName(located.particle()));
        json.addProperty("type", typeName(declaration));
        json.addProperty("builtin_base", SchemaFacets.builtinBase(declaration, schema).orElse(null));
        int[] occurs = occurrence(located.particle());
        json.addProperty("min", occurs[0]);
        putMax(json, occurs[1]);
        if (declaration instanceof XsdElement element) {
            json.addProperty("nillable", element.isNillable());
            json.addProperty("default", element.getDefaultValue());
            json.addProperty("fixed", element.getFixed());
        } else if (declaration instanceof XsdAttribute at) {
            json.addProperty("use", use(located.particle()));
            json.addProperty("default", at.getDefaultValue());
            json.addProperty("fixed", at.getFixed());
        }
        Map<String, List<String>> facets = facets(declaration);
        JsonArray enumeration = new JsonArray();
        facets.getOrDefault(XsdFacetType.ENUMERATION.getXmlName(), List.of()).forEach(enumeration::add);
        JsonObject facetJson = new JsonObject();
        facets.forEach((name, values) -> {
            if (name.equals(XsdFacetType.ENUMERATION.getXmlName())) {
                return;
            }
            if (values.size() == 1) {
                facetJson.addProperty(name, values.getFirst());
            } else {
                JsonArray array = new JsonArray();
                values.forEach(array::add);
                facetJson.add(name, array);
            }
        });
        json.add("facets", facetJson);
        json.add("enumeration", enumeration);
        JsonArray documentation = new JsonArray();
        for (String[] doc : documentation()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("lang", doc[0]);
            entry.addProperty("text", doc[1]);
            documentation.add(entry);
        }
        json.add("documentation", documentation);
        JsonArray children = new JsonArray();
        JsonArray attributes = new JsonArray();
        if (!isAttribute) {
            for (XsdNode particle : XsdInstanceLocator.childElements(declaration, schema)) {
                children.add(particleJson(particle));
            }
            for (XsdNode particle : XsdInstanceLocator.attributesOf(declaration, schema)) {
                JsonObject a = new JsonObject();
                a.addProperty("name", XsdInstanceLocator.instanceName(particle));
                a.addProperty("type", typeName(XsdInstanceLocator.declarationOf(particle, schema)));
                a.addProperty("use", use(particle));
                attributes.add(a);
            }
        }
        json.add("children", children);
        json.add("attributes", attributes);
        json.addProperty("declared_in", declaredIn(declaration));
        return json;
    }

    /**
     * Compact text, one fact per line: {@code Status: StatusType (token) [0..1]}, then enumeration, facets,
     * children (alternatives of a choice in parentheses), attributes and documentation.
     */
    public String toText() {
        XsdNode declaration = located.declaration();
        StringBuilder text = new StringBuilder();
        String name = XsdInstanceLocator.instanceName(located.particle());
        String type = typeName(declaration);
        String base = SchemaFacets.builtinBase(declaration, schema).orElse(null);
        int[] occurs = occurrence(located.particle());
        text.append(attribute != null ? "@" : "").append(name).append(": ").append(type == null ? "anonymous type" : type);
        if (base != null && (type == null || !isBuiltin(type))) {
            text.append(" (").append(base).append(')');
        }
        text.append(' ').append(range(occurs));
        Map<String, List<String>> facets = facets(declaration);
        List<String> enumeration = facets.remove(XsdFacetType.ENUMERATION.getXmlName());
        if (enumeration != null && !enumeration.isEmpty()) {
            text.append("\nenumeration: ").append(String.join(" | ", enumeration));
        }
        if (!facets.isEmpty()) {
            List<String> parts = new ArrayList<>();
            facets.forEach((facet, values) -> values.forEach(v -> parts.add(facet + "=" + v)));
            text.append("\nfacets: ").append(String.join(", ", parts));
        }
        if (attribute == null) {
            List<XsdNode> children = XsdInstanceLocator.childElements(declaration, schema);
            if (!children.isEmpty()) {
                text.append("\nchildren: ").append(childrenText(children));
            }
            for (XsdNode particle : XsdInstanceLocator.attributesOf(declaration, schema)) {
                String attrType = typeName(XsdInstanceLocator.declarationOf(particle, schema));
                text.append("\n@").append(XsdInstanceLocator.instanceName(particle)).append(": ")
                        .append(attrType == null ? "anonymous type" : attrType).append(" (").append(use(particle)).append(')');
            }
        }
        for (String[] doc : documentation()) {
            text.append('\n').append(doc[0] != null ? "[" + doc[0] + "] " : "").append(doc[1]);
        }
        String declaredIn = declaredIn(declaration);
        if (declaredIn != null) {
            text.append("\ndeclared in ").append(declaredIn);
        }
        return text.toString();
    }

    /** The instance path this slice describes, as {@code /a/b}. */
    public String path() {
        return "/" + String.join("/", path) + (attribute != null ? "/@" + attribute : "");
    }

    // ---------------------------------------------------------------- outline

    /** Target namespace, global elements, named types and imports of a schema. */
    public static JsonObject outline(XsdSchema schema) {
        JsonObject json = new JsonObject();
        json.addProperty("target_namespace", schema.getTargetNamespace());
        JsonArray elements = new JsonArray();
        JsonArray types = new JsonArray();
        JsonArray imports = new JsonArray();
        for (XsdNode child : schema.getChildren()) {
            switch (child.getNodeType()) {
                case ELEMENT -> elements.add(child.getName());
                case COMPLEX_TYPE, SIMPLE_TYPE -> {
                    JsonObject type = new JsonObject();
                    type.addProperty("name", child.getName());
                    type.addProperty("kind", child.getNodeType() == XsdNodeType.COMPLEX_TYPE ? "complex" : "simple");
                    types.add(type);
                }
                case IMPORT -> {
                    XsdImport anImport = (XsdImport) child;
                    JsonObject entry = new JsonObject();
                    entry.addProperty("namespace", anImport.getNamespace());
                    entry.addProperty("location", anImport.getSchemaLocation());
                    imports.add(entry);
                }
                default -> {
                    // annotations, groups, attribute groups, notations …
                }
            }
        }
        json.add("global_elements", elements);
        json.add("named_types", types);
        json.add("imports", imports);
        return json;
    }

    /**
     * The content tree of a global element: {@code name, type, min, max, children[]}, cut at {@code depth}
     * levels below it ({@code more: true} where cut). A type that already occurs on the way down is not
     * entered again ({@code recursive: true}); alternatives of a choice carry {@code choice: true}.
     *
     * @return empty when the schema has no global element of that name
     */
    public static Optional<JsonObject> elementTree(XsdSchema schema, String globalElement, int depth) {
        String local = globalElement.substring(globalElement.indexOf(':') + 1);
        for (XsdNode child : schema.getChildren()) {
            if (child.getNodeType() == XsdNodeType.ELEMENT && local.equals(child.getName())) {
                return Optional.of(treeNode(schema, child, depth,
                        Collections.newSetFromMap(new IdentityHashMap<>())));
            }
        }
        return Optional.empty();
    }

    /** Names of the global elements, for messages. */
    public static List<String> globalElementNames(XsdSchema schema) {
        return schema.getChildren().stream().filter(c -> c.getNodeType() == XsdNodeType.ELEMENT)
                .map(XsdNode::getName).toList();
    }

    private static JsonObject treeNode(XsdSchema schema, XsdNode particle, int depthLeft, Set<XsdNode> onPath) {
        XsdNode declaration = XsdInstanceLocator.declarationOf(particle, schema);
        JsonObject node = new JsonObject();
        node.addProperty("name", XsdInstanceLocator.instanceName(particle));
        node.addProperty("type", declaration == null ? null : typeName(declaration));
        int[] occurs = occurrence(particle);
        node.addProperty("min", occurs[0]);
        putMax(node, occurs[1]);
        if (choiceOf(particle) != null) {
            node.addProperty("choice", true);
        }
        if (declaration == null) {
            return node;
        }
        List<XsdNode> children = XsdInstanceLocator.childElements(declaration, schema);
        if (children.isEmpty()) {
            return node;
        }
        XsdNode key = Optional.ofNullable(XsdInstanceLocator.namedTypeOf(declaration, schema)).orElse(declaration);
        if (onPath.contains(key)) {
            node.addProperty("recursive", true);
            return node;
        }
        if (depthLeft <= 0) {
            node.addProperty("more", true);
            return node;
        }
        onPath.add(key);
        JsonArray array = new JsonArray();
        for (XsdNode child : children) {
            array.add(treeNode(schema, child, depthLeft - 1, onPath));
        }
        onPath.remove(key);
        node.add("children", array);
        return node;
    }

    // ---------------------------------------------------------------- helpers

    private JsonObject particleJson(XsdNode particle) {
        XsdNode declaration = XsdInstanceLocator.declarationOf(particle, schema);
        JsonObject json = new JsonObject();
        json.addProperty("name", XsdInstanceLocator.instanceName(particle));
        json.addProperty("type", declaration == null ? null : typeName(declaration));
        int[] occurs = occurrence(particle);
        json.addProperty("min", occurs[0]);
        putMax(json, occurs[1]);
        if (choiceOf(particle) != null) {
            json.addProperty("choice", true);
        }
        return json;
    }

    private static String childrenText(List<XsdNode> children) {
        List<String> parts = new ArrayList<>();
        XsdNode currentChoice = null;
        List<String> alternatives = new ArrayList<>();
        for (XsdNode particle : children) {
            XsdNode choice = choiceOf(particle);
            if (choice != currentChoice && !alternatives.isEmpty()) {
                parts.add("(" + String.join(" | ", alternatives) + ")");
                alternatives.clear();
            }
            currentChoice = choice;
            String name = XsdInstanceLocator.instanceName(particle);
            if (choice != null) {
                int[] occurs = occurrence(particle);
                alternatives.add(occurs[0] == 1 && occurs[1] == 1 ? name : name + " " + range(occurs));
            } else {
                parts.add(name + " " + range(occurrence(particle)));
            }
        }
        if (!alternatives.isEmpty()) {
            parts.add("(" + String.join(" | ", alternatives) + ")");
        }
        return String.join(", ", parts);
    }

    /** The nearest xs:choice between a particle and its type, or {@code null}. */
    private static XsdNode choiceOf(XsdNode particle) {
        for (XsdNode p = particle.getParent(); p != null; p = p.getParent()) {
            switch (p.getNodeType()) {
                case CHOICE -> {
                    return p;
                }
                case COMPLEX_TYPE, GROUP, ELEMENT, SCHEMA -> {
                    return null;
                }
                default -> {
                    // sequence, all, extension, complexContent: keep looking
                }
            }
        }
        return null;
    }

    /** Own facets, else those of the referenced named type, plus the base chain and list/union facets. */
    private Map<String, List<String>> facets(XsdNode declaration) {
        List<XsdFacet> facets = new ArrayList<>(SchemaFacets.collect(declaration));
        if (facets.isEmpty() && (declaration instanceof XsdElement || declaration instanceof XsdAttribute)) {
            facets.addAll(SchemaFacets.resolveReferencedTypeFacets(declaration, schema));
        }
        facets.addAll(SchemaFacets.resolveBaseChainFacets(declaration, schema));
        facets.addAll(SchemaFacets.resolveListUnionFacets(declaration, schema));
        Map<String, List<String>> byName = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (XsdFacet facet : facets) {
            String name = facet.getFacetType().getXmlName();
            if (seen.add(name + "\u0000" + facet.getValue())) {
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(facet.getValue());
            }
        }
        return byName;
    }

    /** {lang, text} pairs: the declaration's documentation, else its named type's. */
    private List<String[]> documentation() {
        List<String[]> docs = documentationOf(located.declaration());
        if (docs.isEmpty() && located.namedType() != null) {
            docs = documentationOf(located.namedType());
        }
        return docs;
    }

    private static List<String[]> documentationOf(XsdNode node) {
        List<String[]> out = new ArrayList<>();
        List<XsdDocumentation> docs = node.getDocumentations();
        if (docs != null) {
            for (XsdDocumentation doc : docs) {
                if (doc.getText() != null && !doc.getText().isBlank()) {
                    out.add(new String[] {doc.getLang(), doc.getText().strip()});
                }
            }
        }
        if (out.isEmpty() && node.getDocumentation() != null && !node.getDocumentation().isBlank()) {
            out.add(new String[] {null, node.getDocumentation().strip()});
        }
        return out;
    }

    private String declaredIn(XsdNode declaration) {
        if (!XsdInstanceLocator.isForeignTo(declaration, schema)) {
            return null;
        }
        if (declaration.getSourceFile() != null) {
            return declaration.getSourceFile().toString();
        }
        XsdNode top = declaration;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        Path main = top instanceof XsdSchema s ? s.getMainSchemaPath() : null;
        return main == null ? null : main.toString();
    }

    private static String typeName(XsdNode declaration) {
        String type = declaration instanceof XsdElement e ? e.getType()
                : declaration instanceof XsdAttribute a ? a.getType() : null;
        return type == null || type.isBlank() ? null : type;
    }

    private static boolean isBuiltin(String type) {
        return type.startsWith("xs:") || type.startsWith("xsd:");
    }

    /** {min, max} of a particle; max -1 = unbounded; an attribute: 1..1 when required, else 0..1. */
    private static int[] occurrence(XsdNode particle) {
        if (particle instanceof XsdAttribute) {
            return new int[] {"required".equals(use(particle)) ? 1 : 0, 1};
        }
        return new int[] {particle.getMinOccurs(), particle.getMaxOccurs()};
    }

    private static String use(XsdNode particle) {
        String use = particle instanceof XsdAttribute a ? a.getUse() : null;
        return use == null || use.isBlank() ? "optional" : use;
    }

    private static void putMax(JsonObject json, int max) {
        if (max == XsdNode.UNBOUNDED) {
            json.addProperty("max", "unbounded");
        } else {
            json.addProperty("max", max);
        }
    }

    private static String range(int[] occurs) {
        return "[" + occurs[0] + ".." + (occurs[1] == XsdNode.UNBOUNDED ? "*" : String.valueOf(occurs[1])) + "]";
    }
}
