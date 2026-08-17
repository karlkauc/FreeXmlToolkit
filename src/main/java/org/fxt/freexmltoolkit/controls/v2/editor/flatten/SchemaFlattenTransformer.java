package org.fxt.freexmltoolkit.controls.v2.editor.flatten;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAlternative;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAppInfo;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComment;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdInclude;
import org.fxt.freexmltoolkit.controls.v2.model.XsdList;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdOverride;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRedefine;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdUnion;

/**
 * One-shot reduction pass applied to a freshly parsed, throwaway {@link XsdSchema}
 * before serialization (see {@link FlattenOptions}). Mutates the model in place;
 * this is intentionally not command-based because the schema never enters an
 * interactive editing session.
 * <p>
 * All traversals are cycle-safe via visited node IDs (XSD trees can contain
 * circular type references).
 */
public final class SchemaFlattenTransformer {

    private static final Logger logger = LogManager.getLogger(SchemaFlattenTransformer.class);

    /** Applies all reductions selected in {@code options} to {@code schema}. */
    public void apply(XsdSchema schema, FlattenOptions options) {
        if (schema == null || options == null || !options.requiresTransform()) {
            return;
        }
        if (options.removeAnnotations()) {
            stripAnnotations(schema);
        }
        if (options.removeComments()) {
            removeComments(schema);
        }
        if (options.removeResolvedIncludes()) {
            removeResolvedIncludes(schema);
        }
        if (options.removeUnusedTypes()) {
            removeUnusedGlobalComponents(schema);
        }
    }

    private void stripAnnotations(XsdSchema schema) {
        forEachNode(schema, node -> {
            node.setDocumentation(null);
            node.clearDocumentations();
            node.setAppinfo((XsdAppInfo) null);
        });
    }

    private void removeComments(XsdSchema schema) {
        schema.clearLeadingComments();
        forEachNode(schema, node -> {
            List<XsdNode> comments = null;
            for (XsdNode child : node.getChildren()) {
                if (child instanceof XsdComment) {
                    if (comments == null) {
                        comments = new ArrayList<>();
                    }
                    comments.add(child);
                }
            }
            if (comments != null) {
                comments.forEach(node::removeChild);
            }
        });
    }

    private void removeResolvedIncludes(XsdSchema schema) {
        // Successful resolution is signaled by a resolved path (the factory never
        // flips the legacy "resolved" flag); failed directives stay as a signal.
        List<XsdNode> resolved = new ArrayList<>();
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdInclude include && include.getResolvedPath() != null) {
                resolved.add(include);
            }
        }
        resolved.forEach(schema::removeChild);
    }

    /**
     * Mark-and-sweep over the schema's own global components: everything reachable
     * from a global element or attribute declaration (by prefix-stripped local name,
     * matching the {@code TypeUsageFinder} convention) is kept; unreferenced global
     * types, groups and attribute groups are removed. Global elements/attributes are
     * never removed — any global element is a legal instance root, which also keeps
     * substitution-group members safe. Skipped entirely when xs:redefine/xs:override
     * are present (their reference semantics are not modeled here).
     */
    private void removeUnusedGlobalComponents(XsdSchema schema) {
        Map<String, XsdNode> globals = new HashMap<>();
        Deque<XsdNode> queue = new ArrayDeque<>();
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdRedefine || child instanceof XsdOverride) {
                logger.warn("Schema contains xs:redefine/xs:override — skipping unused-type removal.");
                return;
            }
            if (isShakableGlobal(child) && child.getName() != null && !child.getName().isBlank()) {
                globals.put(child.getName(), child);
            } else if (child instanceof XsdElement || child instanceof XsdAttribute) {
                queue.add(child);
            }
        }
        if (globals.isEmpty()) {
            return;
        }

        Set<String> retained = new HashSet<>();
        Set<String> visitedIds = new HashSet<>();
        while (!queue.isEmpty()) {
            XsdNode root = queue.poll();
            collectReachableRefs(root, globals, retained, queue, visitedIds);
        }

        for (Map.Entry<String, XsdNode> entry : globals.entrySet()) {
            if (!retained.contains(entry.getKey())) {
                logger.debug("Removing unused global component '{}'", entry.getKey());
                schema.removeChild(entry.getValue());
            }
        }
    }

    private boolean isShakableGlobal(XsdNode node) {
        return node instanceof XsdComplexType
                || node instanceof XsdSimpleType
                || (node instanceof XsdGroup group && !group.isReference())
                || (node instanceof XsdAttributeGroup attributeGroup && !attributeGroup.isReference());
    }

    /** Walks {@code root}'s subtree and enqueues newly retained global components. */
    private void collectReachableRefs(XsdNode root, Map<String, XsdNode> globals,
                                      Set<String> retained, Deque<XsdNode> queue,
                                      Set<String> visitedIds) {
        Deque<XsdNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            XsdNode node = stack.pop();
            String id = node.getId();
            if (id != null && !visitedIds.add(id)) {
                continue;
            }
            for (String ref : referencedNames(node)) {
                if (ref == null || ref.isBlank()) {
                    continue;
                }
                String localName = stripPrefix(ref);
                XsdNode target = globals.get(localName);
                if (target != null && retained.add(localName)) {
                    queue.add(target);
                }
            }
            for (XsdNode child : node.getChildren()) {
                stack.push(child);
            }
        }
    }

    /** All type/group names a single node references (same edges as TypeUsageFinder). */
    private List<String> referencedNames(XsdNode node) {
        List<String> refs = new ArrayList<>(2);
        switch (node) {
            case XsdElement element -> refs.add(element.getType());
            case XsdAttribute attribute -> refs.add(attribute.getType());
            case XsdRestriction restriction -> refs.add(restriction.getBase());
            case XsdExtension extension -> refs.add(extension.getBase());
            case XsdList list -> refs.add(list.getItemType());
            case XsdUnion union -> {
                if (union.getMemberTypes() != null) {
                    refs.addAll(union.getMemberTypes());
                }
            }
            case XsdAlternative alternative -> refs.add(alternative.getType());
            case XsdGroup group -> refs.add(group.getRef());
            case XsdAttributeGroup attributeGroup -> refs.add(attributeGroup.getRef());
            default -> {
            }
        }
        return refs;
    }

    private String stripPrefix(String name) {
        int colon = name.lastIndexOf(':');
        return colon >= 0 && colon < name.length() - 1 ? name.substring(colon + 1) : name;
    }

    /** Cycle-safe pre-order visit of every node in the schema tree. */
    private void forEachNode(XsdSchema schema, java.util.function.Consumer<XsdNode> visitor) {
        Deque<XsdNode> stack = new ArrayDeque<>();
        Set<String> visitedIds = new HashSet<>();
        stack.push(schema);
        while (!stack.isEmpty()) {
            XsdNode node = stack.pop();
            String id = node.getId();
            if (id != null && !visitedIds.add(id)) {
                continue;
            }
            visitor.accept(node);
            for (XsdNode child : node.getChildren()) {
                stack.push(child);
            }
        }
    }
}
