package org.fxt.freexmltoolkit.controls.v2.view;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAll;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAny;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAnyAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttributeGroup;
import org.fxt.freexmltoolkit.controls.v2.model.XsdChoice;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComment;
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
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.fxt.freexmltoolkit.domain.XsdBuiltinTypeCatalog;

/**
 * Builds a visual tree from the XsdNode-based model (XsdSchema).
 * This replaces the old approach of converting XsdSchema → XsdSchemaModel → VisualNode
 * with a direct XsdSchema → VisualNode mapping.
 * <p>
 * Performance optimizations:
 * - Static type resolution cache (shared across all builders)
 * - Global element index cache
 * - Incremental updates where possible
 * - Lazy loading for deep tree branches (Phase 2)
 *
 * @since 2.0
 */
public class XsdVisualTreeBuilder {

    /**
     * Build mode for controlling tree construction depth.
     * Used for lazy loading optimization.
     */
    public enum BuildMode {
        /**
         * Build the complete tree (current behavior for small schemas)
         */
        FULL,

        /**
         * Build only root + first level, load deeper levels on demand.
         * Suitable for large schemas to reduce initial load time.
         */
        LAZY_FIRST_LEVEL,

        /**
         * Build root + two levels, load deeper levels on demand.
         * Good balance between initial visibility and performance.
         */
        LAZY_TWO_LEVELS
    }

    private static final Logger logger = LogManager.getLogger(XsdVisualTreeBuilder.class);

    /** Maximum depth for visual tree to prevent extremely deep trees */
    private static final int MAX_DEPTH = 30;

    /**
     * Element cards one build creates eagerly. A type used in several places is unfolded at every use,
     * so a small schema with nested reuse can expand into millions of cards (seconds to minutes on the
     * FX thread); past this budget, elements with content get a lazy loader and unfold when expanded.
     */
    static final int DEFAULT_NODE_BUDGET = 2000;

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    /** What a type reference points at, which decides what its card can show. */
    private enum TypeKind {
        /** A type of the XSD namespace: no content of its own. */
        BUILTIN,
        /** A global complexType: its content unfolds under the card. */
        COMPLEX,
        /** A global simpleType: text only, nothing to unfold. */
        SIMPLE,
        /** Found nowhere, e.g. it lives in an include or import that could not be loaded. */
        UNRESOLVED
    }

    /**
     * The global components of a schema, by local name. Includes are part of the schema they are
     * flattened into; imported schemas are merged in with {@link #merge}.
     */
    private record SchemaIndex(Map<String, XsdComplexType> types, Set<String> simpleTypes,
                               Map<String, XsdElement> elements, Map<String, XsdGroup> groups,
                               Map<String, XsdAttributeGroup> attributeGroups) {

        SchemaIndex() {
            this(new HashMap<>(), new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        }

        static SchemaIndex of(XsdSchema schema) {
            SchemaIndex index = new SchemaIndex();
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType type && hasName(type)) {
                    index.types.put(type.getName(), type);
                } else if (child instanceof XsdSimpleType type && hasName(type)) {
                    index.simpleTypes.add(type.getName());
                } else if (child instanceof XsdElement element && hasName(element) && element.getRef() == null) {
                    index.elements.put(element.getName(), element);
                } else if (child instanceof XsdGroup group && hasName(group) && !group.isReference()) {
                    index.groups.put(group.getName(), group);
                } else if (child instanceof XsdAttributeGroup group && hasName(group) && !group.isReference()) {
                    index.attributeGroups.put(group.getName(), group);
                }
            }
            return index;
        }

        private static boolean hasName(XsdNode node) {
            return node.getName() != null && !node.getName().isEmpty();
        }

        void merge(SchemaIndex other) {
            types.putAll(other.types);
            simpleTypes.addAll(other.simpleTypes);
            elements.putAll(other.elements);
            groups.putAll(other.groups);
            attributeGroups.putAll(other.attributeGroups);
        }
    }

    /**
     * Indexes per schema, by identity hash code. They persist across tree rebuilds for the same
     * schema, which saves re-scanning large schemas; structural edits invalidate them.
     */
    private static final Map<Integer, SchemaIndex> INDEX_CACHE = new ConcurrentHashMap<>();

    private final Map<String, VisualNode> nodeMap = new HashMap<>();
    /** Main schema merged with its imports; read-only once built, shared with lazy loaders. */
    private SchemaIndex index = new SchemaIndex();
    private Map<String, XsdSchema> importedSchemas = new HashMap<>(); // Imported schemas from XsdNodeFactory
    private Runnable onModelChangeCallback;

    /** Current depth during tree building */
    private int currentDepth = 0;

    /** Build mode for lazy loading (Phase 2 optimization) */
    private BuildMode buildMode = BuildMode.FULL;

    /** Depth threshold for lazy loading (applies when buildMode != FULL) */
    private int lazyLoadDepthThreshold = 1;

    /** Reference to the schema for lazy loading context */
    private XsdSchema currentSchema;

    /** Eager element cards allowed in one build (a lazy expansion starts a fresh budget). */
    private int nodeBudget = DEFAULT_NODE_BUDGET;

    /** Element cards created so far in this build. */
    private int elementNodeCount = 0;

    /**
     * Clears the static index cache.
     * Call this when a schema is modified structurally to ensure
     * fresh index data is used on next build.
     */
    public static void invalidateCache() {
        INDEX_CACHE.clear();
        logger.debug("Type resolution cache invalidated");
    }

    /**
     * Clears the cache for a specific schema.
     *
     * @param schema the schema whose cache should be cleared
     */
    public static void invalidateCacheFor(XsdSchema schema) {
        if (schema != null) {
            int schemaId = System.identityHashCode(schema);
            INDEX_CACHE.remove(schemaId);
            logger.debug("Type resolution cache invalidated for schema: {}", schemaId);
        }
    }

    /**
     * Sets the build mode for controlling tree construction depth.
     * Use LAZY_FIRST_LEVEL or LAZY_TWO_LEVELS for large schemas to improve performance.
     *
     * @param mode the build mode
     * @return this builder for method chaining
     */
    public XsdVisualTreeBuilder withBuildMode(BuildMode mode) {
        this.buildMode = mode;
        switch (mode) {
            case LAZY_FIRST_LEVEL -> this.lazyLoadDepthThreshold = 1;
            case LAZY_TWO_LEVELS -> this.lazyLoadDepthThreshold = 2;
            case FULL -> this.lazyLoadDepthThreshold = MAX_DEPTH;  // Effectively no limit
            default -> throw new IllegalStateException("Unexpected value: " + mode);
        }
        logger.debug("Build mode set to {} (lazy depth threshold: {})", mode, lazyLoadDepthThreshold);
        return this;
    }

    /**
     * Sets how many element cards a build creates before it defers the rest to lazy loaders.
     *
     * @param budget eager element cards per build (at least 1)
     * @return this builder for method chaining
     */
    public XsdVisualTreeBuilder withNodeBudget(int budget) {
        this.nodeBudget = Math.max(1, budget);
        return this;
    }

    /**
     * Gets the current build mode.
     *
     * @return the build mode
     */
    public BuildMode getBuildMode() {
        return buildMode;
    }

    /**
     * Builds a visual tree directly from an XsdSchema.
     *
     * @param schema the XSD schema to visualize
     * @return the root VisualNode
     */
    public VisualNode buildFromSchema(XsdSchema schema) {
        return buildFromSchema(schema, null, null);
    }

    /**
     * Builds a visual tree directly from an XsdSchema with a callback for model changes.
     *
     * @param schema                the XSD schema to visualize
     * @param onModelChangeCallback callback to invoke when model changes (for triggering redraw)
     * @return the root VisualNode
     */
    public VisualNode buildFromSchema(XsdSchema schema, Runnable onModelChangeCallback) {
        return buildFromSchema(schema, onModelChangeCallback, null);
    }

    /**
     * Builds a visual tree directly from an XsdSchema with imported schemas.
     *
     * @param schema                the XSD schema to visualize
     * @param onModelChangeCallback callback to invoke when model changes (for triggering redraw)
     * @param importedSchemas       map of imported schemas (from XsdNodeFactory)
     * @return the root VisualNode
     */
    public VisualNode buildFromSchema(XsdSchema schema, Runnable onModelChangeCallback, Map<String, XsdSchema> importedSchemas) {
        logger.info("========== buildFromSchema CALLED (BuildMode: {}) ==========", buildMode);
        this.onModelChangeCallback = onModelChangeCallback;
        this.importedSchemas = importedSchemas != null ? importedSchemas : new HashMap<>();
        this.currentSchema = schema;  // Store for lazy loading context
        nodeMap.clear();
        index = new SchemaIndex();
        currentDepth = 0; // Reset depth counter for fresh build

        if (schema == null) {
            logger.warn("Cannot build visual tree from null schema");
            return null;
        }

        logger.info("Schema has {} children", schema.getChildren().size());
        logger.info("Imported schemas: {}", this.importedSchemas.size());

        index.merge(cachedIndex(schema));
        for (XsdSchema importedSchema : this.importedSchemas.values()) {
            index.merge(cachedIndex(importedSchema));
        }
        logger.info("Total indexed types: {}, Total indexed elements: {}", index.types().size(), index.elements().size());

        // Find global elements (direct children of schema that are elements)
        java.util.List<XsdElement> globalElements = schema.getChildren().stream()
                .filter(node -> node instanceof XsdElement)
                .map(node -> (XsdElement) node)
                .toList();

        if (globalElements.isEmpty()) {
            // No global elements - create empty schema node
            VisualNode rootNode = new VisualNode(
                    "Schema: " + (schema.getTargetNamespace() != null ? schema.getTargetNamespace() : "empty"),
                    "No elements",
                    NodeWrapperType.SCHEMA,
                    schema,
                    null,
                    1,
                    1,
                    onModelChangeCallback
            );
            logger.debug("Visual tree built with no elements");
            return rootNode;
        }

        if (globalElements.size() == 1) {
            // Single root element - use it directly as root node
            XsdElement rootElement = globalElements.get(0);
            Set<String> visitedTypes = new HashSet<>();
            Set<String> visitedElements = new HashSet<>();
            VisualNode rootNode = createElementNode(rootElement, null, visitedTypes, visitedElements);
            rootNode.setExpanded(true);
            logger.debug("Visual tree built with single root element: {}", rootElement.getName());
            return rootNode;
        } else {
            // Multiple global elements - create schema node as root
            VisualNode rootNode = new VisualNode(
                    "Schema: " + (schema.getTargetNamespace() != null ? schema.getTargetNamespace() : "default"),
                    globalElements.size() + " elements",
                    NodeWrapperType.SCHEMA,
                    schema,
                    null,
                    1,
                    1,
                    onModelChangeCallback
            );
            rootNode.setExpanded(true);

            // Add global elements
            Set<String> visitedTypes = new HashSet<>();
            Set<String> visitedElements = new HashSet<>();
            for (XsdElement element : globalElements) {
                VisualNode elementNode = createElementNode(element, rootNode, visitedTypes, visitedElements);
                rootNode.addChild(elementNode);
            }

            // Add schema-level comments
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComment comment) {
                    VisualNode commentNode = createCommentNode(comment, rootNode);
                    rootNode.addChild(commentNode);
                }
            }

            logger.debug("Visual tree built with {} global elements", rootNode.getChildren().size());
            return rootNode;
        }
    }

    /** A builder for one lazy load: separate state and a fresh budget, sharing this build's index. */
    private XsdVisualTreeBuilder lazyBuilder(int depth) {
        XsdVisualTreeBuilder lazyBuilder = new XsdVisualTreeBuilder();
        lazyBuilder.buildMode = BuildMode.FULL;
        lazyBuilder.nodeBudget = this.nodeBudget;
        lazyBuilder.currentSchema = this.currentSchema;
        lazyBuilder.onModelChangeCallback = this.onModelChangeCallback;
        lazyBuilder.importedSchemas = this.importedSchemas;
        lazyBuilder.index = this.index;
        lazyBuilder.currentDepth = depth;
        return lazyBuilder;
    }

    private static SchemaIndex cachedIndex(XsdSchema schema) {
        return INDEX_CACHE.computeIfAbsent(System.identityHashCode(schema), id -> SchemaIndex.of(schema));
    }

    /**
     * Creates a visual node for an XsdElement.
     *
     * @param element the XSD element
     * @param parent the parent visual node
     * @param visitedTypes set of type names currently being processed (to prevent circular references)
     * @param visitedElements set of element IDs currently being processed (to prevent circular references)
     * @return the created visual node
     */
    private VisualNode createElementNode(XsdElement element, VisualNode parent, Set<String> visitedTypes, Set<String> visitedElements) {
        logger.info("createElementNode: element='{}', type='{}', hasInlineChildren={}, depth={}",
                element.getName(), element.getType(), element.getChildren().size(), currentDepth);

        // Check depth limit to prevent extremely deep trees
        if (currentDepth > MAX_DEPTH) {
            logger.debug("Maximum depth ({}) exceeded for element '{}'. Truncating tree.", MAX_DEPTH, element.getName());
            String label = element.getName() != null ? element.getName() : "(unnamed)";
            return new VisualNode(label + " (max depth)", "", NodeWrapperType.ELEMENT, element, parent,
                    element.getMinOccurs(), element.getMaxOccurs(), onModelChangeCallback);
        }

        // Check for circular reference at element level
        if (visitedElements.contains(element.getId())) {
            logger.debug("Circular reference detected: element '{}' (ID: {}) is already being processed. Skipping to prevent infinite recursion.",
                    element.getName(), element.getId());
            // Return a placeholder node without children
            String label = element.getName() != null ? element.getName() : "(unnamed)";
            return new VisualNode(label + " (circular ref)", "", NodeWrapperType.ELEMENT, element, parent,
                    element.getMinOccurs(), element.getMaxOccurs(), onModelChangeCallback);
        }

        String label = element.getName() != null ? element.getName() : "(unnamed)";
        int minOccurs = element.getMinOccurs();
        int maxOccurs = element.getMaxOccurs();

        // Extract element's type for detail string (used for icon selection)
        String detail = "";
        if (element.getType() != null && !element.getType().isEmpty()) {
            detail = element.getType();
        } else {
            // Check for inline complex/simple type
            for (XsdNode child : element.getChildren()) {
                if (child instanceof XsdComplexType complexType) {
                    // Try to extract the base type from inline complex type
                    for (XsdNode complexChild : complexType.getChildren()) {
                        if (complexChild instanceof XsdSimpleContent simpleContent) {
                            // Extract base type from simpleContent
                            for (XsdNode scChild : simpleContent.getChildren()) {
                                if (scChild instanceof XsdExtension ext && ext.getBase() != null) {
                                    detail = ext.getBase();
                                    break;
                                } else if (scChild instanceof XsdRestriction res && res.getBase() != null) {
                                    detail = res.getBase();
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    break;
                } else if (child instanceof XsdSimpleType simpleType) {
                    // Try to extract base from inline simple type
                    for (XsdNode stChild : simpleType.getChildren()) {
                        if (stChild instanceof XsdRestriction res && res.getBase() != null) {
                            detail = res.getBase();
                            break;
                        }
                    }
                    break;
                }
            }
        }

        // Create VisualNode with extracted type detail
        VisualNode node = new VisualNode(label, detail, NodeWrapperType.ELEMENT, element, parent,
                minOccurs, maxOccurs, onModelChangeCallback);

        // Add to nodeMap for later lookup
        nodeMap.put(element.getId(), node);

        // Add to visited elements before processing to prevent circular references
        visitedElements.add(element.getId());

        // Check if we should defer child loading (lazy loading optimization)
        // Elements with potential children beyond the lazy threshold get lazy loaders
        elementNodeCount++;
        boolean shouldLazyLoad = (buildMode != BuildMode.FULL && currentDepth >= lazyLoadDepthThreshold)
                || elementNodeCount > nodeBudget;
        boolean hasComplexContent = hasComplexContent(element);

        if (shouldLazyLoad && hasComplexContent) {
            // Set up lazy loading for children
            logger.debug("Setting up lazy loading for element '{}' at depth {}", element.getName(), currentDepth);

            // Capture the context needed for lazy loading
            final XsdElement capturedElement = element;
            final Set<String> capturedVisitedTypes = new HashSet<>(visitedTypes);
            final Set<String> capturedVisitedElements = new HashSet<>(visitedElements);
            final int capturedDepth = currentDepth;

            node.setLazyChildrenLoader(() -> {
                XsdVisualTreeBuilder lazyBuilder = lazyBuilder(capturedDepth);

                // The eager path fills the card itself, so a deferred card matches an eager one.
                lazyBuilder.buildChildrenInto(capturedElement, node, capturedVisitedTypes, capturedVisitedElements);
                return java.util.List.of();
            });

            return node;
        }

        buildChildrenInto(element, node, visitedTypes, visitedElements);
        return node;
    }

    /**
     * Adds an element's content to its card: the single path for eager cards and lazy loaders.
     *
     * @param element         the element to build children for
     * @param node            the element's card
     * @param visitedTypes    set of type names currently being processed
     * @param visitedElements set of element IDs currently being processed
     */
    private void buildChildrenInto(XsdElement element, VisualNode node,
                                   Set<String> visitedTypes, Set<String> visitedElements) {
        // Increment depth for child processing
        currentDepth++;
        try {
            // Check if this is an element reference (ref attribute)
            // If so, resolve it to the global element and use its structure
            if (element.getRef() != null && !element.getRef().isEmpty()) {
                logger.info("Element '{}' has ref='{}', resolving reference", element.getName(), element.getRef());
                resolveElementReference(element.getRef(), node, element, visitedTypes, visitedElements);
                logger.info("After resolving ref, element '{}' node has {} children", element.getName(), node.getChildren().size());
                return;
            }

            // Process inline children (complexType, simpleType defined within this element)
            boolean hasInlineComplexType = false;
            for (XsdNode child : element.getChildren()) {
                if (child instanceof XsdComplexType) {
                    processComplexType((XsdComplexType) child, node, visitedTypes, visitedElements);
                    hasInlineComplexType = true;
                } else if (child instanceof XsdSimpleType) {
                    // Simple types typically don't have visual children
                    logger.trace("Element {} has inline simple type", element.getName());
                }
                // Note: XsdAnnotation handling would go here when that class is implemented
            }

            // If no inline type definition and element has a type reference, try to resolve it
            // This handles cases like <xs:element name="ControlData" type="ControlDataType"/>
            String elementType = element.getType();
            if (!hasInlineComplexType && elementType != null && !elementType.isEmpty()) {
                resolveTypeReference(elementType, node, visitedTypes, visitedElements);
            } else if (!hasInlineComplexType) {
                // No type attribute and no inline type definition: according to the XSD spec this
                // means xs:anyType (allows any content), so there is no structure to show.
                logger.debug("Element '{}' is treated as xs:anyType", element.getName());
            }

            logger.info("After processing, element '{}' node has {} children", element.getName(), node.getChildren().size());
        } finally {
            currentDepth--;
        }
    }

    /**
     * Checks if an element will have something to unfold, which is what earns a card its expand
     * button before its children exist. An unresolved type counts: its placeholder says so.
     *
     * @param element the element to check
     * @return true if the element has complex content
     */
    private boolean hasComplexContent(XsdElement element) {
        // Check for inline complex type
        for (XsdNode child : element.getChildren()) {
            if (child instanceof XsdComplexType) {
                return true;
            }
        }

        if (element.getRef() != null && !element.getRef().isEmpty()) {
            return true;
        }
        String elementType = element.getType();
        if (elementType == null || elementType.isEmpty()) {
            return false;
        }
        TypeKind kind = classify(elementType);
        return kind == TypeKind.COMPLEX || kind == TypeKind.UNRESOLVED;
    }

    /**
     * Classifies a type reference through the schema's prefix declarations, so any prefix bound to
     * the XSD namespace works and a user type named like a built-in one is not mistaken for it.
     * A prefixless reference prefers an indexed type, since schemas with XSD as their default
     * namespace and no target namespace reference their own types that way.
     *
     * @param typeRef the QName as written in the schema
     * @return what the reference points at
     */
    private TypeKind classify(String typeRef) {
        int colon = typeRef.indexOf(':');
        String prefix = colon < 0 ? "" : typeRef.substring(0, colon);
        String localName = localName(typeRef);
        String namespace = currentSchema != null ? currentSchema.getNamespaces().get(prefix) : null;
        boolean xsdNamespace = namespace != null
                ? XSD_NAMESPACE.equals(namespace)
                : "xs".equals(prefix) || "xsd".equals(prefix);
        boolean indexed = index.types().containsKey(localName) || index.simpleTypes().contains(localName);

        if (xsdNamespace && (colon >= 0 || !indexed)) {
            return TypeKind.BUILTIN;
        }
        if (index.types().containsKey(localName)) {
            return TypeKind.COMPLEX;
        }
        if (indexed) {
            return TypeKind.SIMPLE;
        }
        // An included file may declare a prefix the main file does not know
        return namespace == null && colon >= 0 && XsdBuiltinTypeCatalog.isBuiltin(localName)
                ? TypeKind.BUILTIN : TypeKind.UNRESOLVED;
    }

    private static String localName(String qName) {
        return qName.substring(qName.indexOf(':') + 1);
    }

    /**
     * Adds a card that names something the schema refers to but the diagram cannot show. It has no
     * model node, so selection, delete and drag cannot touch the reference it stands for.
     *
     * @param parentNode the card it belongs to
     * @param label      the card's title
     * @param detail     the card's second line
     */
    private void addPlaceholder(VisualNode parentNode, String label, String detail) {
        parentNode.addChild(new VisualNode(label, detail, NodeWrapperType.ELEMENT, null, parentNode,
                1, 1, onModelChangeCallback));
    }

    /**
     * Processes a complex type and adds its content to the parent node.
     * Creates a fresh visitedElements set to allow elements within this type to be processed,
     * even if they were already processed in other type instances.
     *
     * @param complexType the complex type to process
     * @param parentNode the parent visual node
     * @param visitedTypes set of type names currently being processed (to prevent circular type references)
     * @param visitedElements set of element IDs from parent context (not used internally, kept for signature compatibility)
     */
    private void processComplexType(XsdComplexType complexType, VisualNode parentNode, Set<String> visitedTypes, Set<String> _visitedElements) {
        logger.debug("Processing complexType with {} children", complexType.getChildren().size());

        // Create a fresh visitedElements set for this type instance
        // This allows the same element definitions to be reused in multiple type instances
        Set<String> localVisitedElements = new HashSet<>();

        // Process children to find compositors, attributes, and content models
        for (XsdNode child : complexType.getChildren()) {
            logger.debug("ComplexType child: {} (type: {})", child.getClass().getSimpleName(), child.getClass().getName());

            if (child instanceof XsdSequence) {
                VisualNode compositor = createCompositorNode(child, parentNode, "sequence", visitedTypes, localVisitedElements);
                logger.debug("Created compositor node '{}' with {} visual children", compositor.getLabel(), compositor.getChildren().size());
                parentNode.addChild(compositor);
                logger.debug("Added compositor to parent '{}' which now has {} children", parentNode.getLabel(), parentNode.getChildren().size());
            } else if (child instanceof XsdChoice) {
                VisualNode compositor = createCompositorNode(child, parentNode, "choice", visitedTypes, localVisitedElements);
                parentNode.addChild(compositor);
            } else if (child instanceof XsdAll) {
                VisualNode compositor = createCompositorNode(child, parentNode, "all", visitedTypes, localVisitedElements);
                parentNode.addChild(compositor);
            } else if (child instanceof XsdAttribute) {
                VisualNode attributeNode = createAttributeNode((XsdAttribute) child, parentNode);
                parentNode.addChild(attributeNode);
            } else if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleContent simpleContent) {
                // Process simpleContent (base type + attributes from extension/restriction)
                logger.debug("Processing simpleContent with {} children", simpleContent.getChildren().size());
                processSimpleContent(simpleContent, parentNode, visitedTypes, localVisitedElements);
            } else if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdComplexContent complexContent) {
                // Process complexContent (base type + content model from extension/restriction)
                logger.debug("Processing complexContent with {} children", complexContent.getChildren().size());
                processComplexContent(complexContent, parentNode, visitedTypes, localVisitedElements);
            } else if (child instanceof XsdGroup group) {
                parentNode.addChild(createGroupRefNode(group, parentNode, visitedTypes, localVisitedElements));
            } else if (child instanceof XsdAttributeGroup group) {
                parentNode.addChild(createAttributeGroupRefNode(group, parentNode, visitedTypes));
            } else if (child instanceof XsdAnyAttribute any) {
                parentNode.addChild(createWildcardNode(any, NodeWrapperType.ATTRIBUTE, parentNode));
            } else if (child instanceof XsdComment comment) {
                VisualNode commentNode = createCommentNode(comment, parentNode);
                parentNode.addChild(commentNode);
            }
        }
    }

    /**
     * Creates a visual node for a compositor (sequence, choice, all).
     *
     * @param compositorNode the compositor node
     * @param parent the parent visual node
     * @param compositorType the type of compositor ("sequence", "choice", "all")
     * @param visitedTypes set of type names currently being processed (to prevent circular references)
     * @param visitedElements set of element IDs currently being processed (to prevent circular references)
     * @return the created visual node
     */
    private VisualNode createCompositorNode(XsdNode compositorNode, VisualNode parent, String compositorType, Set<String> visitedTypes, Set<String> visitedElements) {
        String label = compositorType;
        String detail = compositorNode.getChildren().size() + " items";

        NodeWrapperType nodeType = switch (compositorType) {
            case "sequence" -> NodeWrapperType.SEQUENCE;
            case "choice" -> NodeWrapperType.CHOICE;
            case "all" -> NodeWrapperType.ALL;
            default -> NodeWrapperType.GROUP;
        };

        VisualNode node = new VisualNode(label, detail, nodeType, compositorNode, parent, 1, 1, onModelChangeCallback);

        // Expand compositors by default so their children are visible
        node.setExpanded(true);

        // Add to nodeMap for later lookup
        if (compositorNode instanceof XsdNode) {
            nodeMap.put(compositorNode.getId(), node);
        }

        // Add child elements
        logger.debug("Compositor {} has {} children", compositorType, compositorNode.getChildren().size());

        for (XsdNode child : compositorNode.getChildren()) {
            logger.debug("Compositor child: {} (type: {})", child.getClass().getSimpleName(), child.getClass().getName());

            if (child instanceof XsdElement element) {
                logger.debug("Creating element node for: {}", element.getName());
                VisualNode elementNode = createElementNode(element, node, visitedTypes, visitedElements);
                logger.debug("Adding element {} to compositor {}", elementNode.getLabel(), node.getLabel());
                node.addChild(elementNode);
                logger.debug("Compositor {} now has {} children", node.getLabel(), node.getChildren().size());
            } else if (child instanceof XsdSequence || child instanceof XsdChoice || child instanceof XsdAll) {
                // Nested compositor
                String nestedType = child instanceof XsdSequence ? "sequence" :
                        child instanceof XsdChoice ? "choice" : "all";
                VisualNode nestedNode = createCompositorNode(child, node, nestedType, visitedTypes, visitedElements);
                node.addChild(nestedNode);
            } else if (child instanceof XsdGroup group) {
                node.addChild(createGroupRefNode(group, node, visitedTypes, visitedElements));
            } else if (child instanceof XsdAny any) {
                node.addChild(createWildcardNode(any, NodeWrapperType.ELEMENT, node));
            } else if (child instanceof XsdComment comment) {
                VisualNode commentNode = createCommentNode(comment, node);
                node.addChild(commentNode);
            }
        }

        return node;
    }

    /**
     * Creates the card of an {@code xs:group} reference, holding the referenced group's content.
     * The card's model is the reference, so deleting it never touches the shared definition, while
     * the cards below edit the definition as cards of a referenced type do.
     *
     * @param group           the reference
     * @param parent          the parent visual node
     * @param visitedTypes    set of names currently being processed (to prevent circular references)
     * @param visitedElements set of element IDs currently being processed
     * @return the created visual node
     */
    private VisualNode createGroupRefNode(XsdGroup group, VisualNode parent,
                                          Set<String> visitedTypes, Set<String> visitedElements) {
        VisualNode node = createGroupCard(group, parent);
        if (elementNodeCount > nodeBudget) {
            Set<String> capturedVisitedTypes = new HashSet<>(visitedTypes);
            Set<String> capturedVisitedElements = new HashSet<>(visitedElements);
            int capturedDepth = currentDepth;
            node.setExpanded(false);
            node.setLazyChildrenLoader(() -> {
                lazyBuilder(capturedDepth).fillGroupCard(group, node, capturedVisitedTypes, capturedVisitedElements);
                return java.util.List.of();
            });
            return node;
        }
        fillGroupCard(group, node, visitedTypes, visitedElements);
        return node;
    }

    private void fillGroupCard(XsdGroup group, VisualNode node, Set<String> visitedTypes, Set<String> visitedElements) {
        String name = localName(group.getRef());
        XsdGroup definition = index.groups().get(name);
        String visitKey = "group:" + name;

        if (definition == null) {
            addPlaceholder(node, "Unresolved group: " + group.getRef(), "Group definition not loaded");
        } else if (!visitedTypes.contains(visitKey)) {
            Set<String> localVisitedTypes = new HashSet<>(visitedTypes);
            localVisitedTypes.add(visitKey);
            for (XsdNode child : definition.getChildren()) {
                if (child instanceof XsdSequence) {
                    node.addChild(createCompositorNode(child, node, "sequence", localVisitedTypes, visitedElements));
                } else if (child instanceof XsdChoice) {
                    node.addChild(createCompositorNode(child, node, "choice", localVisitedTypes, visitedElements));
                } else if (child instanceof XsdAll) {
                    node.addChild(createCompositorNode(child, node, "all", localVisitedTypes, visitedElements));
                }
            }
        }
    }

    /**
     * Creates the card of an {@code xs:attributeGroup} reference. Its attributes load when it is
     * opened: schemas such as JATS repeat attribute groups on every element, and building them
     * eagerly costs six times the cards without adding to what a reader sees first.
     *
     * @param group        the reference
     * @param parent       the parent visual node
     * @param visitedTypes set of names currently being processed (to prevent circular references)
     * @return the created visual node
     */
    private VisualNode createAttributeGroupRefNode(XsdAttributeGroup group, VisualNode parent,
                                                   Set<String> visitedTypes) {
        VisualNode node = createGroupCard(group, parent);
        node.setExpanded(false);
        Set<String> capturedVisitedTypes = new HashSet<>(visitedTypes);
        node.setLazyChildrenLoader(() -> {
            fillAttributeGroupCard(group, node, capturedVisitedTypes);
            return java.util.List.of();
        });
        return node;
    }

    private void fillAttributeGroupCard(XsdAttributeGroup group, VisualNode node, Set<String> visitedTypes) {
        String name = localName(group.getRef());
        XsdAttributeGroup definition = index.attributeGroups().get(name);
        String visitKey = "attributeGroup:" + name;

        if (definition == null) {
            addPlaceholder(node, "Unresolved attribute group: " + group.getRef(),
                    "Attribute group definition not loaded");
        } else if (!visitedTypes.contains(visitKey)) {
            Set<String> localVisitedTypes = new HashSet<>(visitedTypes);
            localVisitedTypes.add(visitKey);
            for (XsdNode child : definition.getChildren()) {
                if (child instanceof XsdAttribute attribute) {
                    node.addChild(createAttributeNode(attribute, node));
                } else if (child instanceof XsdAttributeGroup nested) {
                    node.addChild(createAttributeGroupRefNode(nested, node, localVisitedTypes));
                } else if (child instanceof XsdAnyAttribute any) {
                    node.addChild(createWildcardNode(any, NodeWrapperType.ATTRIBUTE, node));
                }
            }
        }
    }

    /**
     * Creates the card of an {@code xs:any} or {@code xs:anyAttribute} wildcard.
     *
     * @param wildcard the wildcard
     * @param type     element or attribute card
     * @param parent   the parent visual node
     * @return the created visual node
     */
    private VisualNode createWildcardNode(XsdNode wildcard, NodeWrapperType type, VisualNode parent) {
        VisualNode node = new VisualNode(wildcard.getName(), "", type, wildcard, parent,
                wildcard.getMinOccurs(), wildcard.getMaxOccurs(), onModelChangeCallback);
        nodeMap.put(wildcard.getId(), node);
        return node;
    }

    private VisualNode createGroupCard(XsdNode reference, VisualNode parent) {
        VisualNode node = new VisualNode(reference.getName(), "", NodeWrapperType.GROUP, reference, parent,
                reference.getMinOccurs(), reference.getMaxOccurs(), onModelChangeCallback);
        node.setExpanded(true);
        nodeMap.put(reference.getId(), node);
        return node;
    }

    /**
     * Creates a visual node for an attribute.
     */
    private VisualNode createAttributeNode(XsdAttribute attribute, VisualNode parent) {
        String label = "@" + (attribute.getName() != null ? attribute.getName() : "(unnamed)");

        // Detail string will be built by VisualNode.buildDetailString() from model properties
        VisualNode node = new VisualNode(label, "", NodeWrapperType.ATTRIBUTE, attribute, parent, 1, 1, onModelChangeCallback);

        // Add to nodeMap for later lookup
        nodeMap.put(attribute.getId(), node);

        return node;
    }

    /**
     * Creates a visual node for an XML comment.
     */
    private VisualNode createCommentNode(XsdComment comment, VisualNode parent) {
        String content = comment.getContent();
        String label = "// " + (content != null
                ? content.trim().substring(0, Math.min(40, content.trim().length()))
                : "");
        VisualNode node = new VisualNode(label, "", NodeWrapperType.COMMENT,
                comment, parent, 1, 1, onModelChangeCallback);
        nodeMap.put(comment.getId(), node);
        return node;
    }

    /**
     * Resolves a type reference and adds its structure to the parent node.
     * This method finds a global complexType definition and adds its content to the element.
     * Prevents circular references by tracking visited types.
     *
     * @param typeRef      the type reference (e.g., "ControlDataType")
     * @param parentNode   the visual node to add children to
     * @param visitedTypes set of type names currently being processed (to prevent circular references)
     * @param visitedElements set of element IDs currently being processed (to prevent circular references)
     */
    private void resolveTypeReference(String typeRef, VisualNode parentNode,
                                      Set<String> visitedTypes, Set<String> visitedElements) {
        if (typeRef == null || typeRef.isEmpty()) {
            return;
        }

        switch (classify(typeRef)) {
            case BUILTIN, SIMPLE -> logger.trace("Type '{}' has no structure to show", typeRef);
            case UNRESOLVED -> addPlaceholder(parentNode, "Unresolved type: " + typeRef,
                    "Type definition not loaded");
            case COMPLEX -> {
                String typeName = localName(typeRef);
                // The same type may repeat in parallel branches, just not within its own chain
                if (visitedTypes.contains(typeName)) {
                    logger.debug("Circular reference detected: type '{}' is already being processed", typeName);
                    return;
                }
                Set<String> localVisitedTypes = new HashSet<>(visitedTypes);
                localVisitedTypes.add(typeName);
                processComplexType(index.types().get(typeName), parentNode, localVisitedTypes, visitedElements);
            }
        }
    }

    /**
     * Resolves an element reference and adds its structure to the parent node.
     * This method finds a global element definition and adds its content to the referring element.
     *
     * @param ref              the element reference (e.g., "ds:Signature")
     * @param parentNode       the visual node to add children to
     * @param referencingElement the element that contains the ref attribute
     * @param visitedTypes     set of type names currently being processed (to prevent circular references)
     * @param visitedElements  set of element IDs currently being processed (to prevent circular references)
     */
    private void resolveElementReference(String ref, VisualNode parentNode, XsdElement referencingElement,
                                        Set<String> visitedTypes, Set<String> visitedElements) {
        logger.debug("resolveElementReference called: ref='{}', element='{}'", ref, referencingElement.getName());

        if (ref == null || ref.isEmpty()) {
            logger.debug("Element reference is null or empty, skipping");
            return;
        }

        // Remove namespace prefix if present (e.g., "ds:Signature" -> "Signature")
        String elementName = localName(ref);
        logger.debug("Element name after prefix removal: '{}'", elementName);

        // Look up the global element in the index
        XsdElement referencedElement = index.elements().get(elementName);
        if (referencedElement != null) {
            logger.info("Found global element '{}' for ref='{}', processing structure", elementName, ref);

            // Check for circular reference
            if (visitedElements.contains(referencedElement.getId())) {
                logger.debug("Circular element reference detected: element '{}' is already being processed. Skipping.",
                        elementName);
                return;
            }

            // Create a new visitedElements set for this branch
            Set<String> localVisitedElements = new HashSet<>(visitedElements);
            localVisitedElements.add(referencedElement.getId());

            // Process the referenced element's type if it has one
            String referencedType = referencedElement.getType();
            if (referencedType != null && !referencedType.isEmpty()) {
                logger.info("Referenced element '{}' has type='{}', resolving", elementName, referencedType);
                resolveTypeReference(referencedType, parentNode, visitedTypes, localVisitedElements);
            }

            // Process the referenced element's inline children (complexType, etc.)
            for (XsdNode child : referencedElement.getChildren()) {
                if (child instanceof XsdComplexType) {
                    processComplexType((XsdComplexType) child, parentNode, visitedTypes, localVisitedElements);
                }
            }

            logger.info("After resolving ref, parentNode '{}' has {} children",
                    parentNode.getLabel(), parentNode.getChildren().size());
        } else {
            logger.warn("Referenced element '{}' not found in global element index (has {} elements). " +
                    "This may be because the element is defined in an imported schema that hasn't been loaded.",
                    elementName, index.elements().size());

            // Create a placeholder child node for unresolved external references
            // This allows the user to see that there is a reference and expand the node
            String namespacePrefix = ref.contains(":") ? ref.substring(0, ref.indexOf(":")) : null;
            String placeholderLabel = namespacePrefix != null
                    ? "External: " + ref + " (from " + namespacePrefix + " namespace)"
                    : "External: " + ref + " (unresolved)";

            addPlaceholder(parentNode, placeholderLabel, "Referenced element not loaded");
            logger.info("Created placeholder for unresolved reference '{}', parentNode '{}' now has {} children",
                    ref, parentNode.getLabel(), parentNode.getChildren().size());
        }
    }

    /**
     * Processes simpleContent (used in complexType with text content and attributes).
     * Example: <xs:simpleContent><xs:extension base="xs:decimal"><xs:attribute.../></xs:extension></xs:simpleContent>
     *
     * @param simpleContent   the simpleContent node
     * @param parentNode      the parent visual node
     * @param visitedTypes    set of visited type names
     * @param visitedElements set of visited element IDs
     */
    private void processSimpleContent(XsdSimpleContent simpleContent, VisualNode parentNode,
                                      Set<String> visitedTypes, Set<String> visitedElements) {
        // SimpleContent contains either extension or restriction; both only add attributes
        for (XsdNode child : simpleContent.getChildren()) {
            if (child instanceof XsdExtension extension) {
                addInheritedAttributes(extension.getBase(), extension, parentNode, visitedTypes);
                addDerivationContent(extension, parentNode, visitedTypes, visitedElements);
            } else if (child instanceof XsdRestriction restriction) {
                addInheritedAttributes(restriction.getBase(), restriction, parentNode, visitedTypes);
                addDerivationContent(restriction, parentNode, visitedTypes, visitedElements);
            }
        }
    }

    /**
     * Processes complexContent (used in complexType for type derivation).
     * Example: <xs:complexContent><xs:extension base="BaseType"><xs:sequence>...</xs:sequence></xs:extension></xs:complexContent>
     *
     * @param complexContent  the complexContent node
     * @param parentNode      the parent visual node
     * @param visitedTypes    set of visited type names
     * @param visitedElements set of visited element IDs
     */
    private void processComplexContent(org.fxt.freexmltoolkit.controls.v2.model.XsdComplexContent complexContent,
                                      VisualNode parentNode, Set<String> visitedTypes, Set<String> visitedElements) {
        // ComplexContent contains either extension or restriction
        for (XsdNode child : complexContent.getChildren()) {
            if (child instanceof XsdExtension extension) {
                logger.debug("Processing extension in complexContent, base='{}'", extension.getBase());

                // An extension appends to the base type's content, so that comes first
                String baseType = extension.getBase();
                resolveTypeReference(baseType, parentNode, visitedTypes, visitedElements);
                addDerivationContent(extension, parentNode, visitedTypes, visitedElements);
            } else if (child instanceof XsdRestriction restriction) {
                // A restriction restates the whole content model: the base's particles do not add to it
                logger.debug("Processing restriction in complexContent, base='{}'", restriction.getBase());
                addDerivationContent(restriction, parentNode, visitedTypes, visitedElements);
                addInheritedAttributes(restriction.getBase(), restriction, parentNode, visitedTypes);
            }
        }
    }

    /**
     * Adds the attributes a derivation takes from its base type. Only {@code xs:extension} under
     * complexContent gets them by unfolding the base; a restriction restates just the particles
     * and simpleContent has no particles to unfold.
     *
     * @param baseRef      the base type reference
     * @param derivation   the extension or restriction
     * @param parentNode   the parent visual node
     * @param visitedTypes set of visited type names
     */
    private void addInheritedAttributes(String baseRef, XsdNode derivation, VisualNode parentNode,
                                        Set<String> visitedTypes) {
        addBaseAttributes(baseRef, declaredAttributes(derivation), parentNode, visitedTypes);
    }

    /** Names a derivation declares itself: redeclared or prohibited, so the base's version is gone. */
    private static Set<String> declaredAttributes(XsdNode derivation) {
        Set<String> names = new HashSet<>();
        for (XsdNode child : derivation.getChildren()) {
            if (child instanceof XsdAttribute attribute && attribute.getName() != null) {
                names.add(attribute.getName());
            }
        }
        return names;
    }

    private void addBaseAttributes(String baseRef, Set<String> hidden, VisualNode parentNode, Set<String> visitedTypes) {
        if (baseRef == null || baseRef.isEmpty()) {
            return;
        }
        TypeKind kind = classify(baseRef);
        if (kind == TypeKind.UNRESOLVED) {
            addPlaceholder(parentNode, "Unresolved type: " + baseRef, "Type definition not loaded");
            return;
        }
        String typeName = localName(baseRef);
        if (kind != TypeKind.COMPLEX || visitedTypes.contains("attributes:" + typeName)) {
            return;
        }
        Set<String> localVisitedTypes = new HashSet<>(visitedTypes);
        localVisitedTypes.add("attributes:" + typeName);

        XsdComplexType type = index.types().get(typeName);
        for (XsdNode child : type.getChildren()) {
            if (child instanceof XsdSimpleContent || child instanceof XsdComplexContent) {
                for (XsdNode derivation : child.getChildren()) {
                    String derivationBase = derivation instanceof XsdExtension extension ? extension.getBase()
                            : derivation instanceof XsdRestriction restriction ? restriction.getBase() : null;
                    Set<String> derivationHidden = new HashSet<>(hidden);
                    derivationHidden.addAll(declaredAttributes(derivation));
                    addBaseAttributes(derivationBase, derivationHidden, parentNode, localVisitedTypes);
                    addAttributeUses(derivation, hidden, parentNode, localVisitedTypes);
                }
            }
        }
        addAttributeUses(type, hidden, parentNode, localVisitedTypes);
    }

    private void addAttributeUses(XsdNode owner, Set<String> hidden, VisualNode parentNode, Set<String> visitedTypes) {
        for (XsdNode child : owner.getChildren()) {
            if (child instanceof XsdAttribute attribute) {
                if (!hidden.contains(attribute.getName()) && !"prohibited".equals(attribute.getUse())) {
                    parentNode.addChild(createAttributeNode(attribute, parentNode));
                }
            } else if (child instanceof XsdAttributeGroup group) {
                parentNode.addChild(createAttributeGroupRefNode(group, parentNode, visitedTypes));
            } else if (child instanceof XsdAnyAttribute any) {
                parentNode.addChild(createWildcardNode(any, NodeWrapperType.ATTRIBUTE, parentNode));
            }
        }
    }

    /**
     * Adds what an extension or restriction declares itself.
     *
     * @param derivation      the extension or restriction
     * @param parentNode      the parent visual node
     * @param visitedTypes    set of visited type names
     * @param visitedElements set of visited element IDs
     */
    private void addDerivationContent(XsdNode derivation, VisualNode parentNode,
                                      Set<String> visitedTypes, Set<String> visitedElements) {
        for (XsdNode child : derivation.getChildren()) {
            if (child instanceof XsdSequence) {
                parentNode.addChild(createCompositorNode(child, parentNode, "sequence", visitedTypes, visitedElements));
            } else if (child instanceof XsdChoice) {
                parentNode.addChild(createCompositorNode(child, parentNode, "choice", visitedTypes, visitedElements));
            } else if (child instanceof XsdAll) {
                parentNode.addChild(createCompositorNode(child, parentNode, "all", visitedTypes, visitedElements));
            } else if (child instanceof XsdAttribute attribute) {
                parentNode.addChild(createAttributeNode(attribute, parentNode));
            } else if (child instanceof XsdGroup group) {
                parentNode.addChild(createGroupRefNode(group, parentNode, visitedTypes, visitedElements));
            } else if (child instanceof XsdAttributeGroup group) {
                parentNode.addChild(createAttributeGroupRefNode(group, parentNode, visitedTypes));
            } else if (child instanceof XsdAnyAttribute any) {
                parentNode.addChild(createWildcardNode(any, NodeWrapperType.ATTRIBUTE, parentNode));
            }
        }
    }

    /**
     * Gets the node map for looking up VisualNodes by model ID.
     */
    public Map<String, VisualNode> getNodeMap() {
        return nodeMap;
        }

}
