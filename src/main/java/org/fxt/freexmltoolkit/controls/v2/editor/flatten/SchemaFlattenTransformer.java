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
import org.fxt.freexmltoolkit.controls.v2.editor.usage.SchemaReferenceGraph;
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

    /** JavaDoc-style tag the serializer turns back into a clean {@code fxt:sourceFile} element. */
    private static final String SOURCE_FILE_TAG = "@sourceFile";

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
        // Last: the markers must survive stripAnnotations(), and there is no point marking
        // components that the reductions above just removed.
        if (options.trackSourceFiles()) {
            addSourceFileMarkers(schema);
        }
    }

    /**
     * Marks every global component that was inlined from an included file with an
     * {@code @sourceFile <filename>} appinfo entry — the persisted counterpart of the in-memory
     * {@link org.fxt.freexmltoolkit.controls.v2.model.IncludeSourceInfo}, which does not survive
     * writing the flattened schema to disk.
     */
    private void addSourceFileMarkers(XsdSchema schema) {
        for (XsdNode child : schema.getChildren()) {
            if (!isTrackableGlobal(child) || !child.isFromInclude()) {
                continue;
            }
            java.nio.file.Path sourceFile = child.getSourceFile();
            if (sourceFile == null || sourceFile.getFileName() == null) {
                continue;
            }
            setSourceFileMarker(child, sourceFile.getFileName().toString());
        }
    }

    /** The top-level constructs a source-file marker is attached to. */
    private boolean isTrackableGlobal(XsdNode node) {
        return node instanceof XsdElement
                || node instanceof XsdComplexType
                || node instanceof XsdSimpleType
                || node instanceof XsdGroup
                || node instanceof XsdAttributeGroup
                || node instanceof XsdAttribute;
    }

    /**
     * Replaces any marker the node already carries (a schema can be flattened twice, and an
     * included file may itself be a flatten result) with one naming {@code fileName}.
     */
    private void setSourceFileMarker(XsdNode node, String fileName) {
        XsdAppInfo appinfo = new XsdAppInfo();
        XsdAppInfo existing = node.getAppinfo();
        if (existing != null) {
            for (XsdAppInfo.AppInfoEntry entry : existing.getEntries()) {
                if (!isSourceFileMarker(entry)) {
                    appinfo.addEntry(entry);
                }
            }
        }
        appinfo.addEntry(null, SOURCE_FILE_TAG + " " + fileName);
        node.setAppinfo(appinfo);
        logger.trace("Marked global component '{}' as originating from '{}'", node.getName(), fileName);
    }

    /**
     * A marker is either the tag form produced here or the raw {@code fxt:sourceFile} element a
     * previously flattened file was parsed from.
     */
    private boolean isSourceFileMarker(XsdAppInfo.AppInfoEntry entry) {
        if (SOURCE_FILE_TAG.equals(entry.getTag())) {
            return true;
        }
        String rawXml = entry.getRawXml();
        return rawXml != null && (rawXml.contains(":sourceFile") || rawXml.contains("<sourceFile"));
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
        SchemaReferenceGraph graph = SchemaReferenceGraph.build(schema);
        if (graph.hasRedefineOrOverride()) {
            logger.warn("Schema contains xs:redefine/xs:override — skipping unused-type removal.");
            return;
        }
        for (SchemaReferenceGraph.ComponentKey key : graph.unreachable()) {
            SchemaReferenceGraph.Component component = graph.component(key);
            if (component != null) {
                logger.debug("Removing unused global component '{}'", key);
                schema.removeChild(component.node());
            }
        }
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
