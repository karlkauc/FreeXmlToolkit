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

/**
 * Builds a visual tree from the XsdNode-based model (XsdSchema).
 * This replaces the old approach of converting XsdSchema → XsdSchemaModel → VisualNode
 * with a direct XsdSchema → VisualNode mapping.
 *
 * @since 2.0
 */
public class XsdVisualTreeBuilder {

    private static final Logger logger = LogManager.getLogger(XsdVisualTreeBuilder.class);

    private final Map<String, VisualNode> nodeMap = new HashMap<>();
    private final Map<String, XsdComplexType> typeIndex = new HashMap<>();
    private Runnable onModelChangeCallback;

    /**
     * Builds a visual tree directly from an XsdSchema.
     *
     * @param schema the XSD schema to visualize
     * @return the root VisualNode
     */
    public VisualNode buildFromSchema(XsdSchema schema) {
        return buildFromSchema(schema, null);
    }

    /**
     * Builds a visual tree directly from an XsdSchema with a callback for model changes.
     *
     * @param schema                the XSD schema to visualize
     * @param onModelChangeCallback callback to invoke when model changes (for triggering redraw)
     * @return the root VisualNode
     */
    public VisualNode buildFromSchema(XsdSchema schema, Runnable onModelChangeCallback) {
        logger.info("========== buildFromSchema CALLED ==========");
        this.onModelChangeCallback = onModelChangeCallback;
        nodeMap.clear();
        typeIndex.clear();

        if (schema == null) {
            logger.warn("Cannot build visual tree from null schema");
            return null;
        }

        logger.info("Schema has {} children", schema.getChildren().size());

        // Build type index for fast lookups
        buildTypeIndex(schema);
        logger.info("Built type index with {} types", typeIndex.size());

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
        logger.info("createElementNode: element='{}', type='{}', hasInlineChildren={}",
                element.getName(), element.getType(), element.getChildren().size());

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

        // Process children to find compositors and attributes
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
     * Gets the node map for looking up VisualNodes by model ID.
     */
    public Map<String, VisualNode> getNodeMap() {
        return nodeMap;
    }
}
