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
        this.onModelChangeCallback = onModelChangeCallback;
        nodeMap.clear();

        if (schema == null) {
            logger.warn("Cannot build visual tree from null schema");
            return null;
        }

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
            VisualNode rootNode = createElementNode(rootElement, null, visitedTypes);
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
            for (XsdElement element : globalElements) {
                VisualNode elementNode = createElementNode(element, rootNode, visitedTypes);
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
     * @return the created visual node
     */
    private VisualNode createElementNode(XsdElement element, VisualNode parent, Set<String> visitedTypes) {
        String label = element.getName() != null ? element.getName() : "(unnamed)";
        String detail = "";

        if (element.getType() != null) {
            detail = element.getType();
        }

        int minOccurs = element.getMinOccurs();
        int maxOccurs = element.getMaxOccurs();

        if (minOccurs != 1 || maxOccurs != 1) {
            detail += " [" + minOccurs + ".." +
                    (maxOccurs == XsdNode.UNBOUNDED ? "*" : maxOccurs) + "]";
        }

        VisualNode node = new VisualNode(label, detail, NodeWrapperType.ELEMENT, element, parent,
                minOccurs, maxOccurs, onModelChangeCallback);

        // Add to nodeMap for later lookup
        nodeMap.put(element.getId(), node);

        // Process inline children (complexType, simpleType defined within this element)
        boolean hasInlineComplexType = false;
        for (XsdNode child : element.getChildren()) {
            if (child instanceof XsdComplexType) {
                processComplexType((XsdComplexType) child, node, visitedTypes);
                hasInlineComplexType = true;
            } else if (child instanceof XsdSimpleType) {
                // Simple types typically don't have visual children
                logger.trace("Element {} has inline simple type", element.getName());
            }
            // Note: XsdAnnotation handling would go here when that class is implemented
        }

        // If no inline type definition and element has a type reference, try to resolve it
        // This handles cases like <xs:element name="ControlData" type="ControlDataType"/>
        if (!hasInlineComplexType && element.getType() != null && !element.getType().startsWith("xs:")) {
            resolveTypeReference(element.getType(), node, element, visitedTypes);
        }

        return node;
    }

    /**
     * Processes a complex type and adds its content to the parent node.
     *
     * @param complexType the complex type to process
     * @param parentNode the parent visual node
     * @param visitedTypes set of type names currently being processed (to prevent circular references)
     */
    private void processComplexType(XsdComplexType complexType, VisualNode parentNode, Set<String> visitedTypes) {
        logger.debug("Processing complexType with {} children", complexType.getChildren().size());

        // Process children to find compositors and attributes
        for (XsdNode child : complexType.getChildren()) {
            logger.debug("ComplexType child: {} (type: {})", child.getClass().getSimpleName(), child.getClass().getName());

            if (child instanceof XsdSequence) {
                VisualNode compositor = createCompositorNode(child, parentNode, "sequence", visitedTypes);
                logger.debug("Created compositor node '{}' with {} visual children", compositor.getLabel(), compositor.getChildren().size());
                parentNode.addChild(compositor);
                logger.debug("Added compositor to parent '{}' which now has {} children", parentNode.getLabel(), parentNode.getChildren().size());
            } else if (child instanceof XsdChoice) {
                VisualNode compositor = createCompositorNode(child, parentNode, "choice", visitedTypes);
                parentNode.addChild(compositor);
            } else if (child instanceof XsdAll) {
                VisualNode compositor = createCompositorNode(child, parentNode, "all", visitedTypes);
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
     * @return the created visual node
     */
    private VisualNode createCompositorNode(XsdNode compositorNode, VisualNode parent, String compositorType, Set<String> visitedTypes) {
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
                VisualNode elementNode = createElementNode(element, node, visitedTypes);
                logger.debug("Adding element {} to compositor {}", elementNode.getLabel(), node.getLabel());
                node.addChild(elementNode);
                logger.debug("Compositor {} now has {} children", node.getLabel(), node.getChildren().size());
            } else if (child instanceof XsdSequence || child instanceof XsdChoice || child instanceof XsdAll) {
                // Nested compositor
                String nestedType = child instanceof XsdSequence ? "sequence" :
                        child instanceof XsdChoice ? "choice" : "all";
                VisualNode nestedNode = createCompositorNode(child, node, nestedType, visitedTypes);
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
        String detail = attribute.getType() != null ? attribute.getType() : "";

        if ("required".equals(attribute.getUse())) {
            detail += " (required)";
        }

        VisualNode node = new VisualNode(label, detail, NodeWrapperType.ATTRIBUTE, attribute, parent, 1, 1, onModelChangeCallback);

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
     */
    private void resolveTypeReference(String typeRef, VisualNode parentNode, XsdElement element, Set<String> visitedTypes) {
        if (typeRef == null || typeRef.isEmpty()) {
            return;
        }

        // Skip built-in XML Schema types (they don't have children)
        if (typeRef.startsWith("xs:") || typeRef.startsWith("xsd:")) {
            return;
        }

        // Remove namespace prefix if present
        String typeName = typeRef;
        if (typeName.contains(":")) {
            typeName = typeName.substring(typeName.indexOf(":") + 1);
        }

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

        // Add this type to the visited set before processing
        visitedTypes.add(typeName);
        try {
            // Search for complexType with matching name in schema's children
            for (XsdNode child : schema.getChildren()) {
                if (child instanceof XsdComplexType complexType) {
                    if (typeName.equals(complexType.getName())) {
                        logger.debug("Found complexType '{}' for element '{}'", typeName, element.getName());
                        processComplexType(complexType, parentNode, visitedTypes);
                        return;
                    }
                }
                // SimpleTypes don't have visual children, so we don't need to handle them
            }

            logger.debug("Type '{}' not found or is a simpleType (no children)", typeName);
        } finally {
            // Remove from visited set when done (backtracking) to allow this type to be used in other branches
            visitedTypes.remove(typeName);
        }
    }

    /**
     * Gets the node map for looking up VisualNodes by model ID.
     */
    public Map<String, VisualNode> getNodeMap() {
        return nodeMap;
    }
}
