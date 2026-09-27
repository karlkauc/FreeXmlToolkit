package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.Component;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKey;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.ComponentKind;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph.Reference;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdList;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdUnion;

/**
 * One row of the Types report: a global component with its base, documentation, usage count
 * and usage locations. Holds strings and XPaths only — never model nodes — because the
 * analysis works on a fresh parse and navigation goes through the editor's own model by XPath.
 *
 * @param kind           the component kind
 * @param name           the local name
 * @param baseType       "extends X" / "restricts X" / "list of X" / "union of A, B" / the
 *                       element or attribute type, or {@code ""}
 * @param documentation  the first documentation text, or {@code ""}
 * @param fromInclude    {@code true} when declared in an {@code xs:include}d file
 * @param sourceFileName the declaring file's name, or {@code ""} for the main schema
 * @param xpath          the component's XPath in the schema
 * @param usageCount     the number of references to it
 * @param unreachable    {@code true} when no global element/attribute reaches it (removable)
 * @param usages         every reference, in document order
 */
public record ComponentInfo(ComponentKind kind,
                            String name,
                            String baseType,
                            String documentation,
                            boolean fromInclude,
                            String sourceFileName,
                            String xpath,
                            int usageCount,
                            boolean unreachable,
                            List<UsageRef> usages) {

    /**
     * One reference to a component.
     *
     * @param referrerDisplay e.g. {@code Element 'person' (Element type)}
     * @param type            the reference kind
     * @param referrerXPath   the referring node's XPath (navigation target)
     * @param sourceFileName  the referring file's name, {@code "(main)"} for the main schema
     * @param fromImport      {@code true} when the referrer belongs to an imported schema
     */
    public record UsageRef(String referrerDisplay, UsageReferenceType type, String referrerXPath,
                           String sourceFileName, boolean fromImport) {
    }

    public ComponentInfo {
        usages = usages == null ? List.of() : List.copyOf(usages);
        baseType = baseType == null ? "" : baseType;
        documentation = documentation == null ? "" : documentation;
        sourceFileName = sourceFileName == null ? "" : sourceFileName;
    }

    /** @return {@code true} when nothing references this component. */
    public boolean isUnused() {
        return usageCount == 0;
    }

    /** @return {@code true} for a named type referenced exactly once (not by itself). */
    public boolean isSingleUse() {
        return ComponentKind.TYPES.contains(kind) && usageCount == 1;
    }

    /** @return the component's key in the reference graph. */
    public ComponentKey key() {
        return new ComponentKey(kind, name);
    }

    /** Builds the rows for every global type, group and attribute group of {@code graph}, in declaration order. */
    public static List<ComponentInfo> collect(SchemaReferenceGraph graph) {
        Set<ComponentKey> unreachable = graph.unreachable();
        List<ComponentInfo> result = new ArrayList<>();
        for (Component component : graph.components(ComponentKind.COMPLEX_TYPE, ComponentKind.SIMPLE_TYPE,
                ComponentKind.GROUP, ComponentKind.ATTRIBUTE_GROUP)) {
            XsdNode node = component.node();
            List<Reference> references = graph.referencesTo(component.key());
            List<UsageRef> usages = new ArrayList<>(references.size());
            for (Reference reference : references) {
                TypeUsageLocation location = reference.toUsageLocation();
                usages.add(new UsageRef(location.getDescription(), reference.type(),
                        reference.referrer().getXPath(), location.getSourceFileName(), reference.fromImport()));
            }
            result.add(new ComponentInfo(component.key().kind(), component.key().localName(),
                    describeBase(node), firstDocumentation(node), component.fromInclude(),
                    fileName(component.sourceFile()), node.getXPath(), references.size(),
                    unreachable.contains(component.key()), usages));
        }
        return result;
    }

    /** "extends X", "restricts X", "list of X", "union of A, B", an element/attribute type, or "". */
    public static String describeBase(XsdNode node) {
        return switch (node) {
            case XsdComplexType complexType -> {
                XsdNode content = complexType.getComplexContent() != null
                        ? complexType.getComplexContent() : complexType.getSimpleContent();
                if (content == null) {
                    yield "";
                }
                for (XsdNode child : content.getChildren()) {
                    if (child instanceof XsdExtension extension && extension.getBase() != null) {
                        yield "extends " + extension.getBase();
                    }
                    if (child instanceof XsdRestriction restriction && restriction.getBase() != null) {
                        yield "restricts " + restriction.getBase();
                    }
                }
                yield "";
            }
            case XsdSimpleType simpleType -> {
                for (XsdNode child : simpleType.getChildren()) {
                    if (child instanceof XsdRestriction restriction) {
                        yield restriction.getBase() != null ? "restricts " + restriction.getBase() : "restricts (inline)";
                    }
                    if (child instanceof XsdList list) {
                        yield list.getItemType() != null ? "list of " + list.getItemType() : "list of (inline)";
                    }
                    if (child instanceof XsdUnion union) {
                        List<String> members = union.getMemberTypes();
                        yield members == null || members.isEmpty() ? "union of (inline)" : "union of " + String.join(", ", members);
                    }
                }
                yield "";
            }
            case XsdElement element -> element.getType() != null ? element.getType()
                    : element.getRef() != null ? "ref " + element.getRef() : "";
            case XsdAttribute attribute -> attribute.getType() != null ? attribute.getType()
                    : attribute.getRef() != null ? "ref " + attribute.getRef() : "";
            default -> "";
        };
    }

    /** The node's documentation text (single-language first, else the first entry), or "". */
    public static String firstDocumentation(XsdNode node) {
        String doc = node.getDocumentation();
        if (doc != null && !doc.isBlank()) {
            return doc.trim();
        }
        List<XsdDocumentation> docs = node.getDocumentations();
        if (docs != null) {
            for (XsdDocumentation entry : docs) {
                if (entry.getText() != null && !entry.getText().isBlank()) {
                    return entry.getText().trim();
                }
            }
        }
        return "";
    }

    private static String fileName(Path path) {
        return path == null || path.getFileName() == null ? "" : path.getFileName().toString();
    }
}
