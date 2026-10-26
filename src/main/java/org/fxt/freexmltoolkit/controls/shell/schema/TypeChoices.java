package org.fxt.freexmltoolkit.controls.shell.schema;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.fxt.freexmltoolkit.controls.shell.schema.TypeChoice.Kind;
import org.fxt.freexmltoolkit.controls.shell.schema.TypeChoice.TypeDetails;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacet;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdList;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog.BuiltinType;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog.Category;

/**
 * Builds the {@link TypeChoice} list a type picker offers for a schema: the schema's named simple
 * and complex types (own, included, imported with the prefix the schema binds to their namespace)
 * followed by every built-in datatype, qualified with the schema's own XSD prefix.
 *
 * <p>UI-free; details are computed lazily when a choice is highlighted.
 */
public final class TypeChoices {

    static final String SIMPLE_GROUP = "Simple types";
    static final String COMPLEX_GROUP = "Complex types";
    static final String UNDECLARED_NAMESPACE_NOTE =
            "Namespace not declared in this schema – add an xmlns prefix for it before referencing the type.";

    private static final int MAX_DOC_LENGTH = 400;
    private static final int MAX_ENUMS = 6;

    private TypeChoices() {
    }

    /**
     * @param schema the edited schema; {@code null} yields the built-in types only
     * @return schema types first (simple, then complex, each A–Z), then the built-ins by category
     */
    public static List<TypeChoice> of(XsdSchema schema) {
        return build(schema, schema == null ? List.of() : TypeLibrary.collectNamedTypes(schema));
    }

    /**
     * Variant for callers that only hold the schema root node and a precollected type list (the
     * Inspector in Text mode).
     *
     * @param schemaRoot the schema root (an {@link XsdSchema} when available), may be {@code null}
     * @param namedTypes the named simple/complex types to offer
     */
    public static List<TypeChoice> of(XsdNode schemaRoot, List<XsdNode> namedTypes) {
        return build(schemaRoot, namedTypes == null ? List.of() : namedTypes);
    }

    /**
     * @return the prefix the schema binds to the XML Schema namespace ({@code xs}, {@code xsd}, …);
     * {@code xs} when the schema is {@code null} or declares none. {@link XsdSchema} always carries a
     * default {@code xs} binding, so when several prefixes map to the namespace the one the schema's
     * own type references use wins, then an explicitly declared one.
     */
    public static String schemaPrefix(XsdSchema schema) {
        if (schema == null) {
            return "xs";
        }
        Set<String> candidates = new HashSet<>();
        boolean blank = false;
        for (Map.Entry<String, String> e : schema.getNamespaces().entrySet()) {
            if (XsdBuiltinTypeCatalog.XSD_NAMESPACE.equals(e.getValue())) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    blank = true;
                } else {
                    candidates.add(e.getKey());
                }
            }
        }
        if (candidates.isEmpty()) {
            return blank ? "" : "xs";
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        String used = usedPrefix(schema, candidates, 0);
        if (used != null) {
            return used;
        }
        return candidates.stream().filter(c -> !c.equals("xs")).sorted().findFirst().orElse("xs");
    }

    /** The first XSD-namespace prefix a type/base/itemType reference in the tree actually uses. */
    private static String usedPrefix(XsdNode node, Set<String> candidates, int depth) {
        if (node == null || depth > 64) {
            return null;
        }
        String ref = switch (node) {
            case XsdElement el -> el.getType();
            case XsdAttribute at -> at.getType();
            case XsdRestriction r -> r.getBase();
            case XsdExtension e -> e.getBase();
            case XsdList l -> l.getItemType();
            default -> null;
        };
        if (ref != null) {
            int colon = ref.indexOf(':');
            if (colon > 0 && candidates.contains(ref.substring(0, colon))) {
                return ref.substring(0, colon);
            }
        }
        for (XsdNode child : node.getChildren()) {
            String found = usedPrefix(child, candidates, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * @return the prefix {@code root} declares for the target namespace of {@code owner}; {@code ""}
     * when both share a namespace (or {@code owner} has none); {@code null} when the namespace is not
     * declared in {@code root}
     */
    static String prefixFor(XsdSchema root, XsdSchema owner) {
        if (root == null || owner == null || owner == root) {
            return "";
        }
        String ns = owner.getTargetNamespace();
        if (ns == null || ns.isBlank() || Objects.equals(ns, root.getTargetNamespace())) {
            return "";
        }
        String best = null;
        for (Map.Entry<String, String> e : root.getNamespaces().entrySet()) {
            if (ns.equals(e.getValue()) && e.getKey() != null && !e.getKey().isBlank()) {
                if (best == null || e.getKey().compareTo(best) < 0) {
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    private static List<TypeChoice> build(XsdNode schemaRoot, List<XsdNode> namedTypes) {
        XsdSchema root = schemaRoot instanceof XsdSchema s ? s : null;
        String xsPrefix = schemaPrefix(root);

        List<TypeChoice> simple = new ArrayList<>();
        List<TypeChoice> complex = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (XsdNode node : namedTypes) {
            if (node == null || node.getName() == null || node.getName().isBlank()) {
                continue;
            }
            Kind kind = switch (node.getNodeType()) {
                case SIMPLE_TYPE -> Kind.SIMPLE;
                case COMPLEX_TYPE -> Kind.COMPLEX;
                default -> null;
            };
            if (kind == null) {
                continue;
            }
            XsdSchema owner = owningSchema(node);
            String prefix = owner == null ? "" : prefixFor(root, owner);
            boolean undeclared = prefix == null;
            String qualified = undeclared || prefix.isEmpty() ? node.getName() : prefix + ":" + node.getName();
            if (!seen.add(kind + "|" + qualified)) {
                continue;
            }
            TypeChoice choice = schemaChoice(node, kind, qualified, root, xsPrefix, undeclared);
            (kind == Kind.SIMPLE ? simple : complex).add(choice);
        }
        Comparator<TypeChoice> byName = Comparator.comparing(TypeChoice::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TypeChoice::qualifiedName);
        simple.sort(byName);
        complex.sort(byName);

        List<TypeChoice> result = new ArrayList<>(simple);
        result.addAll(complex);
        for (Map.Entry<Category, List<BuiltinType>> group : XsdBuiltinTypeCatalog.byCategory().entrySet()) {
            for (BuiltinType type : group.getValue()) {
                result.add(builtinChoice(type, group.getKey(), xsPrefix));
            }
        }
        return result;
    }

    // ----- built-in types ------------------------------------------------------------------

    private static TypeChoice builtinChoice(BuiltinType type, Category category, String xsPrefix) {
        String qualified = type.qualified(xsPrefix);
        Supplier<TypeDetails> details = () -> new TypeDetails(
                qualified,
                "Built-in type · " + category.displayName() + (type.listType() ? " · list" : ""),
                type.baseType() == null ? null : XsdBuiltinTypeCatalog.find(type.baseType())
                        .map(b -> b.qualified(xsPrefix)).orElse(type.baseType()),
                type.baseType() == null ? null : type.listType() ? "list" : "restriction",
                type.description(),
                List.of(),
                null,
                type.example(),
                builtinNote(type));
        return new TypeChoice(qualified, type.localName(), Kind.BUILTIN, category.displayName(),
                type.description(), details, null);
    }

    private static String builtinNote(BuiltinType type) {
        if (type.limitedSupport()) {
            return "XSD 1.1 only · not supported by Xerces (the validator used by this application)";
        }
        if (type.xsd11Only()) {
            return "XSD 1.1 only";
        }
        return null;
    }

    // ----- schema types --------------------------------------------------------------------

    private static TypeChoice schemaChoice(XsdNode node, Kind kind, String qualified, XsdSchema root,
                                           String xsPrefix, boolean undeclaredNamespace) {
        String summary = summaryOf(node, kind, root);
        Supplier<TypeDetails> details = () -> detailsOf(node, kind, qualified, root, undeclaredNamespace);
        return new TypeChoice(qualified, node.getName(), kind,
                kind == Kind.SIMPLE ? SIMPLE_GROUP : COMPLEX_GROUP, summary, details, node);
    }

    private static String summaryOf(XsdNode node, Kind kind, XsdSchema root) {
        var derivation = SchemaDerivation.of(node, root).orElse(null);
        if (derivation != null) {
            return derivation.method() + " of " + derivation.base();
        }
        if (kind == Kind.COMPLEX && node instanceof XsdComplexType ct) {
            String content = contentModel(ct);
            return content == null ? "" : content;
        }
        String doc = documentationOf(node);
        return doc == null ? "" : firstSentence(doc);
    }

    private static TypeDetails detailsOf(XsdNode node, Kind kind, String qualified, XsdSchema root,
                                         boolean undeclaredNamespace) {
        var derivation = SchemaDerivation.of(node, root).orElse(null);
        List<String> facts = new ArrayList<>();
        if (node instanceof XsdComplexType ct) {
            if (ct.isAbstract()) {
                facts.add("abstract – only derived types can be used in instances");
            }
            if (ct.isMixed()) {
                facts.add("mixed content (text and child elements)");
            }
            String content = contentModel(ct);
            if (content != null) {
                facts.add("Content: " + content);
            }
            int attributes = countAttributes(ct);
            if (attributes > 0) {
                facts.add(attributes == 1 ? "1 attribute" : attributes + " attributes");
            }
        }
        facts.addAll(facetFacts(SchemaFacets.collect(node)));
        String note = undeclaredNamespace ? UNDECLARED_NAMESPACE_NOTE : null;
        return new TypeDetails(
                qualified,
                kind == Kind.SIMPLE ? "Simple type" : "Complex type",
                derivation == null ? null : derivation.base(),
                derivation == null ? null : derivation.method(),
                documentationOf(node),
                List.copyOf(facts),
                sourceOf(node, root),
                null,
                note);
    }

    private static String contentModel(XsdComplexType ct) {
        if (ct.getSimpleContent() != null) {
            return "simple content";
        }
        if (ct.getSequence() != null) {
            return "sequence";
        }
        if (ct.getChoice() != null) {
            return "choice";
        }
        if (ct.getAll() != null) {
            return "all";
        }
        if (ct.getComplexContent() != null) {
            return "complex content";
        }
        return countAttributes(ct) > 0 ? "attributes only" : "empty";
    }

    private static int countAttributes(XsdNode node) {
        int count = 0;
        for (XsdNode child : node.getChildren()) {
            XsdNodeType t = child.getNodeType();
            if (t == XsdNodeType.ATTRIBUTE || t == XsdNodeType.ATTRIBUTE_GROUP) {
                count++;
            } else if (t == XsdNodeType.SIMPLE_CONTENT || t == XsdNodeType.COMPLEX_CONTENT
                    || t == XsdNodeType.EXTENSION || t == XsdNodeType.RESTRICTION) {
                count += countAttributes(child);
            }
        }
        return count;
    }

    private static List<String> facetFacts(List<XsdFacet> facets) {
        List<String> facts = new ArrayList<>();
        List<String> enums = new ArrayList<>();
        for (XsdFacet facet : facets) {
            if (facet.getFacetType() == XsdFacetType.ENUMERATION) {
                enums.add(facet.getValue());
            }
        }
        if (!enums.isEmpty()) {
            StringBuilder sb = new StringBuilder("Enumeration: ");
            sb.append(String.join(", ", enums.subList(0, Math.min(MAX_ENUMS, enums.size()))));
            if (enums.size() > MAX_ENUMS) {
                sb.append(", +").append(enums.size() - MAX_ENUMS).append(" more");
            }
            facts.add(sb.toString());
        }
        for (XsdFacet facet : facets) {
            if (facet.getFacetType() != XsdFacetType.ENUMERATION && facet.getFacetType() != null) {
                facts.add(facet.getFacetType().getXmlName() + ": " + facet.getValue());
            }
        }
        return facts;
    }

    /** The English (or language-less) {@code xs:documentation}, whitespace-normalised and capped. */
    static String documentationOf(XsdNode node) {
        String text = null;
        List<XsdDocumentation> docs = node.getDocumentations();
        if (docs != null && !docs.isEmpty()) {
            XsdDocumentation best = null;
            for (XsdDocumentation doc : docs) {
                if (doc == null || doc.getText() == null || doc.getText().isBlank()) {
                    continue;
                }
                String lang = doc.getLang();
                boolean preferred = lang == null || lang.isBlank() || lang.toLowerCase(java.util.Locale.ROOT).startsWith("en");
                if (best == null || (preferred && !isPreferred(best))) {
                    best = doc;
                }
            }
            text = best == null ? null : best.getText();
        }
        if (text == null || text.isBlank()) {
            text = node.getDocumentation();
        }
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalised = text.trim().replaceAll("\\s+", " ");
        return normalised.length() > MAX_DOC_LENGTH ? normalised.substring(0, MAX_DOC_LENGTH - 1) + "…" : normalised;
    }

    private static boolean isPreferred(XsdDocumentation doc) {
        String lang = doc.getLang();
        return lang == null || lang.isBlank() || lang.toLowerCase(java.util.Locale.ROOT).startsWith("en");
    }

    private static String firstSentence(String text) {
        int end = text.indexOf(". ");
        String sentence = end > 0 && end < 120 ? text.substring(0, end + 1) : text;
        return sentence.length() > 120 ? sentence.substring(0, 119) + "…" : sentence;
    }

    private static String sourceOf(XsdNode node, XsdSchema root) {
        Path file = node.getSourceFile();
        XsdSchema owner = owningSchema(node);
        boolean imported = owner != null && root != null && owner != root;
        if (file == null) {
            return imported ? "Imported schema" : node.isFromInclude() ? "Included schema" : null;
        }
        if (root != null && !imported && !node.isFromInclude()
                && (root.getMainSchemaPath() == null || file.equals(root.getMainSchemaPath()))) {
            return null;
        }
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        return (imported ? "Imported from " : "Included from ") + name;
    }

    /** @return the {@link XsdSchema} the node hangs under, or {@code null} for a detached node. */
    static XsdSchema owningSchema(XsdNode node) {
        XsdNode current = node;
        while (current != null) {
            if (current instanceof XsdSchema schema) {
                return schema;
            }
            current = current.getParent();
        }
        return null;
    }
}
