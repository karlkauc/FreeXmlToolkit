package org.fxt.freexmltoolkit.controls.v2.view;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds a visual tree from the XsdNode-based model (XsdSchema).
 * This replaces the old approach of converting XsdSchema → XsdSchemaModel → VisualNode
 * with a direct XsdSchema → VisualNode mapping.
 * <p>
 * Performance optimizations:
 * - Static type resolution cache (shared across all builders)
 * - Global element index cache
 * - Incremental updates where possible
 *
 * @since 2.0
 */
public class XsdVisualTreeBuilder {

    private static final Logger logger = LogManager.getLogger(XsdVisualTreeBuilder.class);

    /** Maximum depth for visual tree to prevent extremely deep trees */
    private static final int MAX_DEPTH = 30;

    /** Maximum number of children per node before truncation */
    private static final int MAX_CHILDREN_PER_NODE = 200;

    /**
     * Static cache for type indexes per schema.
     * Key: Schema identity hash code (System.identityHashCode)
     * Value: Map of type name → XsdComplexType
     *
     * This cache persists across tree rebuilds for the same schema,
     * significantly improving performance for large schemas where
     * type resolution is called frequently.
     */
    private static final Map<Integer, Map<String, XsdComplexType>> TYPE_INDEX_CACHE = new ConcurrentHashMap<>();

    /**
     * Static cache for global element indexes per schema.
     * Key: Schema identity hash code
     * Value: Map of element name → XsdElement
     */
    private static final Map<Integer, Map<String, XsdElement>> ELEMENT_INDEX_CACHE = new ConcurrentHashMap<>();

    /**
     * Tracks which schemas have been indexed (by identity hash code).
     * This allows us to skip re-indexing if the schema hasn't changed.
     */
    private static final Set<Integer> INDEXED_SCHEMAS = ConcurrentHashMap.newKeySet();

    private final Map<String, VisualNode> nodeMap = new HashMap<>();
    private final Map<String, XsdComplexType> typeIndex = new HashMap<>();
    private final Map<String, XsdElement> globalElementIndex = new HashMap<>();
    private Map<String, XsdSchema> importedSchemas = new HashMap<>(); // Imported schemas from XsdNodeFactory
    private Runnable onModelChangeCallback;

    /** Current depth during tree building */
    private int currentDepth = 0;

    /**
     * Clears the static type and element caches.
     * Call this when a schema is modified structurally to ensure
     * fresh index data is used on next build.
     */
    public static void invalidateCache() {
        TYPE_INDEX_CACHE.clear();
        ELEMENT_INDEX_CACHE.clear();
        INDEXED_SCHEMAS.clear();
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
            TYPE_INDEX_CACHE.remove(schemaId);
            ELEMENT_INDEX_CACHE.remove(schemaId);
            INDEXED_SCHEMAS.remove(schemaId);
            logger.debug("Type resolution cache invalidated for schema: {}", schemaId);
        }
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
        logger.info("========== buildFromSchema CALLED ==========");
        this.onModelChangeCallback = onModelChangeCallback;
        this.importedSchemas = importedSchemas != null ? importedSchemas : new HashMap<>();
        nodeMap.clear();
        typeIndex.clear();
        globalElementIndex.clear();
        currentDepth = 0; // Reset depth counter for fresh build

        if (schema == null) {
            logger.warn("Cannot build visual tree from null schema");
            return null;
        }

        logger.info("Schema has {} children", schema.getChildren().size());
        logger.info("Imported schemas: {}", this.importedSchemas.size());

        // Use static cache for type and element indexes
        int schemaId = System.identityHashCode(schema);
        boolean usedCache = false;

        if (INDEXED_SCHEMAS.contains(schemaId)) {
            // Use cached indexes
            Map<String, XsdComplexType> cachedTypes = TYPE_INDEX_CACHE.get(schemaId);
            Map<String, XsdElement> cachedElements = ELEMENT_INDEX_CACHE.get(schemaId);
            if (cachedTypes != null && cachedElements != null) {
                typeIndex.putAll(cachedTypes);
                globalElementIndex.putAll(cachedElements);
                usedCache = true;
                logger.info("Used cached indexes for schema {} ({} types, {} elements)",
                        schemaId, typeIndex.size(), globalElementIndex.size());
            }
        }

        if (!usedCache) {
            // Build type index for fast lookups (main schema)
            buildTypeIndex(schema);
            logger.info("Built type index with {} types from main schema", typeIndex.size());

            // Build global element index for fast ref lookups (main schema)
            buildGlobalElementIndex(schema);
            logger.info("Built global element index with {} elements from main schema", globalElementIndex.size());

            // Store in static cache for future rebuilds
            TYPE_INDEX_CACHE.put(schemaId, new HashMap<>(typeIndex));
            ELEMENT_INDEX_CACHE.put(schemaId, new HashMap<>(globalElementIndex));
            INDEXED_SCHEMAS.add(schemaId);
            logger.info("Cached indexes for schema {}", schemaId);
        }

        // Index types and elements from imported schemas (always check cache first)
        for (Map.Entry<String, XsdSchema> entry : this.importedSchemas.entrySet()) {
            String namespace = entry.getKey();
            XsdSchema importedSchema = entry.getValue();
            int importedSchemaId = System.identityHashCode(importedSchema);

            if (INDEXED_SCHEMAS.contains(importedSchemaId)) {
                // Use cached indexes for imported schema
                Map<String, XsdComplexType> cachedTypes = TYPE_INDEX_CACHE.get(importedSchemaId);
                Map<String, XsdElement> cachedElements = ELEMENT_INDEX_CACHE.get(importedSchemaId);
                if (cachedTypes != null) {
                    typeIndex.putAll(cachedTypes);
                }
                if (cachedElements != null) {
                    globalElementIndex.putAll(cachedElements);
                }
                logger.info("Used cached indexes for imported schema: namespace='{}'", namespace);
            } else {
                // Build and cache indexes for imported schema
                logger.info("Indexing imported schema: namespace='{}'", namespace);
                int typesBefore = typeIndex.size();
                int elementsBefore = globalElementIndex.size();
                buildTypeIndex(importedSchema);
                buildGlobalElementIndex(importedSchema);

                // Extract and cache only the new entries from this imported schema
                Map<String, XsdComplexType> importedTypes = new HashMap<>();
                Map<String, XsdElement> importedElements = new HashMap<>();
                for (XsdNode child : importedSchema.getChildren()) {
                    if (child instanceof XsdComplexType ct && ct.getName() != null) {
                        importedTypes.put(ct.getName(), ct);
                    } else if (child instanceof XsdElement el && el.getName() != null && el.getRef() == null) {
                        importedElements.put(el.getName(), el);
                    }
                }
                TYPE_INDEX_CACHE.put(importedSchemaId, importedTypes);
                ELEMENT_INDEX_CACHE.put(importedSchemaId, importedElements);
                INDEXED_SCHEMAS.add(importedSchemaId);
            }
        }
        logger.info("Total indexed types: {}, Total indexed elements: {}", typeIndex.size(), globalElementIndex.size());

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

            logger.debug("Visual tree built with {} global elements", rootNode.getChildren().size());
            return rootNode;
        }
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
            logger.warn("Maximum depth ({}) exceeded for element '{}'. Truncating tree.", MAX_DEPTH, element.getName());
            String label = element.getName() != null ? element.getName() : "(unnamed)";
            return new VisualNode(label + " (max depth)", "", NodeWrapperType.ELEMENT, element, parent,
                    element.getMinOccurs(), element.getMaxOccurs(), onModelChangeCallback);
        }

        // Check for circular reference at element level
        if (visitedElements.contains(element.getId())) {
            logger.warn("Circular reference detected: element '{}' (ID: {}) is already being processed. Skipping to prevent infinite recursion.",
                    element.getName(), element.getId());
            // Return a placeholder node without children
            String label = element.getName() != null ? element.getName() : "(unnamed)";
            return new VisualNode(label + " (circular ref)", "", NodeWrapperType.ELEMENT, element, parent,
                    element.getMinOccurs(), element.getMaxOccurs(), onModelChangeCallback);
        }

        String label = element.getName() != null ? element.getName() : "(unnamed)";
        int minOccurs = element.getMinOccurs();
        int maxOccurs = element.getMaxOccurs();

        // Detail string will be built by VisualNode.buildDetailString() from model properties
        VisualNode node = new VisualNode(label, "", NodeWrapperType.ELEMENT, element, parent,
                minOccurs, maxOccurs, onModelChangeCallback);

        // Add to nodeMap for later lookup
        nodeMap.put(element.getId(), node);

        // Add to visited elements before processing to prevent circular references
        visitedElements.add(element.getId());

        // Increment depth for child processing
        currentDepth++;
        try {
            // Check if this is an element reference (ref attribute)
            // If so, resolve it to the global element and use its structure
            if (element.getRef() != null && !element.getRef().isEmpty()) {
                logger.info("Element '{}' has ref='{}', resolving reference", element.getName(), element.getRef());
                resolveElementReference(element.getRef(), node, element, visitedTypes, visitedElements);
                logger.info("After resolving ref, element '{}' node has {} children", element.getName(), node.getChildren().size());
                return node;
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
            logger.info("Element '{}': hasInlineComplexType={}, type='{}', startsWithXs={}",
                    element.getName(), hasInlineComplexType, elementType,
                    (elementType != null && elementType.startsWith("xs:")));

            if (!hasInlineComplexType && elementType != null && !elementType.isEmpty() && !elementType.startsWith("xs:")) {
                logger.info("Calling resolveTypeReference for element '{}' with type '{}'", element.getName(), elementType);
                resolveTypeReference(elementType, node, element, visitedTypes, visitedElements);
            } else if (!hasInlineComplexType && (elementType == null || elementType.isEmpty())) {
                // Element has no type attribute and no inline type definition
                // According to XSD spec, this means type="xs:anyType" (allows any content)
                logger.warn("Element '{}' has no type attribute and no inline type definition. " +
                        "This is treated as xs:anyType (allows any content). " +
                        "The element will have no structured visual children.", element.getName());
            } else {
                logger.info("NOT resolving type reference for element '{}' (type='{}')", element.getName(), elementType);
            }

            logger.info("After processing, element '{}' node has {} children", element.getName(), node.getChildren().size());
        } finally {
            currentDepth--;
        }
        return node;
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
    private void processComplexType(XsdComplexType complexType, VisualNode parentNode, Set<String> visitedTypes, Set<String> visitedElements) {
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
            }
        }

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
     * Resolves a type reference and adds its structure to the parent node.
     * This method finds a global complexType definition and adds its content to the element.
     * Prevents circular references by tracking visited types.
     *
     * @param typeRef      the type reference (e.g., "ControlDataType")
     * @param parentNode   the visual node to add children to
     * @param element      the element that references this type
     * @param visitedTypes set of type names currently being processed (to prevent circular references)
     * @param visitedElements set of element IDs currently being processed (to prevent circular references)
     */
    private void resolveTypeReference(String typeRef, VisualNode parentNode, XsdElement element, Set<String> visitedTypes, Set<String> visitedElements) {
        logger.debug("resolveTypeReference called: typeRef='{}', element='{}', parentNode.children={}",
                typeRef, element.getName(), parentNode.getChildren().size());

        if (typeRef == null || typeRef.isEmpty()) {
            logger.debug("Type reference is null or empty, skipping");
            return;
        }

        // Skip built-in XML Schema types (they don't have children)
        if (typeRef.startsWith("xs:") || typeRef.startsWith("xsd:")) {
            logger.debug("Skipping built-in XSD type: {}", typeRef);
            return;
        }

        // Remove namespace prefix if present
        String typeName = typeRef;
        if (typeName.contains(":")) {
            typeName = typeName.substring(typeName.indexOf(":") + 1);
        }
        logger.debug("Type name after prefix removal: '{}'", typeName);

        // Check for circular reference - if this type is already being processed, skip it
        if (visitedTypes.contains(typeName)) {
            logger.warn("Circular reference detected: type '{}' is already being processed for element '{}'. Skipping to prevent infinite recursion.",
                    typeName, element.getName());
            return;
        }

        logger.debug("Resolving type reference '{}' for element '{}'", typeName, element.getName());

        // Find the schema root
        XsdNode current = element;
        while (current != null && !(current instanceof XsdSchema)) {
            current = current.getParent();
        }

        if (!(current instanceof XsdSchema schema)) {
            logger.warn("Cannot resolve type '{}': schema root not found", typeName);
            return;
        }

        logger.debug("Found schema root, searching for type '{}'", typeName);

        // Check for circular reference - if this type is already in the chain, skip it
        if (visitedTypes.contains(typeName)) {
            logger.warn("Type '{}' already in processing chain, skipping to prevent circular reference", typeName);
            return;
        }

        // Use type index for fast lookup
        XsdComplexType complexType = typeIndex.get(typeName);
        if (complexType != null) {
            logger.info("Found complexType '{}' for element '{}' (from index), processing with {} children",
                    typeName, element.getName(), complexType.getChildren().size());

            // Create a new visitedTypes set that includes the current type
            // This prevents circular references within this type resolution chain
            // but allows the same type to be used in parallel branches
            Set<String> localVisitedTypes = new HashSet<>(visitedTypes);
            localVisitedTypes.add(typeName);

            processComplexType(complexType, parentNode, localVisitedTypes, visitedElements);
            logger.info("After processing, parentNode '{}' has {} children",
                    parentNode.getLabel(), parentNode.getChildren().size());
        } else {
            logger.warn("Type '{}' not found in type index (has {} types)", typeName, typeIndex.size());
        }
    }

    /**
     * Builds an index of complex types for fast lookup.
     *
     * @param schema the schema to index
     */
    private void buildTypeIndex(XsdSchema schema) {
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdComplexType complexType) {
                if (complexType.getName() != null && !complexType.getName().isEmpty()) {
                    typeIndex.put(complexType.getName(), complexType);
                }
            }
        }
    }

    /**
     * Builds an index of global elements for fast ref lookup.
     *
     * @param schema the schema to index
     */
    private void buildGlobalElementIndex(XsdSchema schema) {
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdElement element) {
                if (element.getName() != null && !element.getName().isEmpty() && element.getRef() == null) {
                    // Only index elements with name (not refs)
                    globalElementIndex.put(element.getName(), element);
                    logger.debug("Indexed global element: {}", element.getName());
                }
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
        String elementName = ref;
        if (elementName.contains(":")) {
            elementName = elementName.substring(elementName.indexOf(":") + 1);
        }
        logger.debug("Element name after prefix removal: '{}'", elementName);

        // Look up the global element in the index
        XsdElement referencedElement = globalElementIndex.get(elementName);
        if (referencedElement != null) {
            logger.info("Found global element '{}' for ref='{}', processing structure", elementName, ref);

            // Check for circular reference
            if (visitedElements.contains(referencedElement.getId())) {
                logger.warn("Circular element reference detected: element '{}' is already being processed. Skipping.",
                        elementName);
                return;
            }

            // Create a new visitedElements set for this branch
            Set<String> localVisitedElements = new HashSet<>(visitedElements);
            localVisitedElements.add(referencedElement.getId());

            // Process the referenced element's type if it has one
            String referencedType = referencedElement.getType();
            if (referencedType != null && !referencedType.isEmpty() && !referencedType.startsWith("xs:")) {
                logger.info("Referenced element '{}' has type='{}', resolving", elementName, referencedType);
                resolveTypeReference(referencedType, parentNode, referencedElement, visitedTypes, localVisitedElements);
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
                    elementName, globalElementIndex.size());
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
    private void processSimpleContent(org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleContent simpleContent,
                                     VisualNode parentNode, Set<String> visitedTypes, Set<String> visitedElements) {
        // SimpleContent contains either extension or restriction
        for (XsdNode child : simpleContent.getChildren()) {
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdExtension extension) {
                logger.debug("Processing extension in simpleContent, base='{}'", extension.getBase());
                // Process attributes from the extension
                for (XsdNode extChild : extension.getChildren()) {
                    if (extChild instanceof XsdAttribute attribute) {
                        VisualNode attributeNode = createAttributeNode(attribute, parentNode);
                        parentNode.addChild(attributeNode);
                        logger.debug("Added attribute '{}' from simpleContent extension", attribute.getName());
                    }
                }
            } else if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction) {
                logger.debug("Processing restriction in simpleContent, base='{}'", restriction.getBase());
                // Process attributes from the restriction (if any)
                for (XsdNode restChild : restriction.getChildren()) {
                    if (restChild instanceof XsdAttribute attribute) {
                        VisualNode attributeNode = createAttributeNode(attribute, parentNode);
                        parentNode.addChild(attributeNode);
                        logger.debug("Added attribute '{}' from simpleContent restriction", attribute.getName());
                    }
                }
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
            if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdExtension extension) {
                logger.debug("Processing extension in complexContent, base='{}'", extension.getBase());

                // First, resolve the base type to get inherited content
                String baseType = extension.getBase();
                if (baseType != null && !baseType.isEmpty() && !baseType.startsWith("xs:")) {
                    logger.debug("Resolving base type '{}' for complexContent extension", baseType);
                    // Create a dummy element to pass to resolveTypeReference
                    org.fxt.freexmltoolkit.controls.v2.model.XsdElement dummyElement =
                        new org.fxt.freexmltoolkit.controls.v2.model.XsdElement("extension");
                    dummyElement.setType(baseType);
                    resolveTypeReference(baseType, parentNode, dummyElement, visitedTypes, visitedElements);
                }

                // Then process additional content from the extension
                for (XsdNode extChild : extension.getChildren()) {
                    if (extChild instanceof XsdSequence) {
                        VisualNode compositor = createCompositorNode(extChild, parentNode, "sequence", visitedTypes, visitedElements);
                        parentNode.addChild(compositor);
                    } else if (extChild instanceof XsdChoice) {
                        VisualNode compositor = createCompositorNode(extChild, parentNode, "choice", visitedTypes, visitedElements);
                        parentNode.addChild(compositor);
                    } else if (extChild instanceof XsdAll) {
                        VisualNode compositor = createCompositorNode(extChild, parentNode, "all", visitedTypes, visitedElements);
                        parentNode.addChild(compositor);
                    } else if (extChild instanceof XsdAttribute attribute) {
                        VisualNode attributeNode = createAttributeNode(attribute, parentNode);
                        parentNode.addChild(attributeNode);
                    }
                }
            } else if (child instanceof org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction restriction) {
                logger.debug("Processing restriction in complexContent, base='{}'", restriction.getBase());

                // Resolve the base type to get inherited content
                String baseType = restriction.getBase();
                if (baseType != null && !baseType.isEmpty() && !baseType.startsWith("xs:")) {
                    logger.debug("Resolving base type '{}' for complexContent restriction", baseType);
                    org.fxt.freexmltoolkit.controls.v2.model.XsdElement dummyElement =
                        new org.fxt.freexmltoolkit.controls.v2.model.XsdElement("restriction");
                    dummyElement.setType(baseType);
                    resolveTypeReference(baseType, parentNode, dummyElement, visitedTypes, visitedElements);
                }

                // Process additional content from the restriction
                for (XsdNode restChild : restriction.getChildren()) {
                    if (restChild instanceof XsdSequence) {
                        VisualNode compositor = createCompositorNode(restChild, parentNode, "sequence", visitedTypes, visitedElements);
                        parentNode.addChild(compositor);
                    } else if (restChild instanceof XsdChoice) {
                        VisualNode compositor = createCompositorNode(restChild, parentNode, "choice", visitedTypes, visitedElements);
                        parentNode.addChild(compositor);
                    } else if (restChild instanceof XsdAll) {
                        VisualNode compositor = createCompositorNode(restChild, parentNode, "all", visitedTypes, visitedElements);
                        parentNode.addChild(compositor);
                    } else if (restChild instanceof XsdAttribute attribute) {
                        VisualNode attributeNode = createAttributeNode(attribute, parentNode);
                        parentNode.addChild(attributeNode);
                    }
                }
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
