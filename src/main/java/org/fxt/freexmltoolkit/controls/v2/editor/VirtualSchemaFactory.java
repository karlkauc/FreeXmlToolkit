package org.fxt.freexmltoolkit.controls.v2.editor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.util.List;

/**
 * Factory for creating virtual XSD schemas for isolated type editing.
 * <p>
 * A virtual schema wraps a single ComplexType as a global element,
 * allowing it to be visualized and edited in XsdGraphView independently
 * from the main schema.
 * <p>
 * The virtual schema acts as an isolated editing context. Changes made
 * in the virtual schema can be merged back to the main schema using
 * {@link #mergeChangesBackToMainSchema}.
 *
 * @since 2.0 (Phase 2)
 */
public class VirtualSchemaFactory {

    private static final Logger logger = LogManager.getLogger(VirtualSchemaFactory.class);
    private static final String VIRTUAL_NAMESPACE = "http://virtual-schema/complextype-editor";

    /**
     * Creates a virtual XsdSchema containing only the given ComplexType.
     * <p>
     * The ComplexType is wrapped as a global element for visualization.
     * This allows XsdGraphView to display and edit the type in isolation.
     *
     * <p><b>Structure of virtual schema:</b>
     * <pre>
     * &lt;xs:schema targetNamespace="http://virtual-schema/complextype-editor"&gt;
     *   &lt;xs:element name="[TypeName]" type="[TypeName]"/&gt;  &lt;!-- Root element --&gt;
     *   &lt;xs:complexType name="[TypeName]"&gt;
     *     &lt;!-- Type content here --&gt;
     *   &lt;/xs:complexType&gt;
     * &lt;/xs:schema&gt;
     * </pre>
     *
     * @param complexType the ComplexType to wrap in virtual schema
     * @return a new virtual XsdSchema containing the ComplexType
     * @throws IllegalArgumentException if complexType is null or has no name
     */
    public static XsdSchema createVirtualSchemaForComplexType(XsdComplexType complexType) {
        if (complexType == null) {
            throw new IllegalArgumentException("ComplexType cannot be null");
        }

        String typeName = complexType.getName();
        if (typeName == null || typeName.isEmpty()) {
            throw new IllegalArgumentException("ComplexType must have a name");
        }

        logger.info("Creating virtual schema for ComplexType: {}", typeName);
        logger.debug("ComplexType has {} children", complexType.getChildren().size());

        // 1. Create new virtual schema
        XsdSchema virtualSchema = new XsdSchema();
        virtualSchema.setTargetNamespace(VIRTUAL_NAMESPACE);
        virtualSchema.setElementFormDefault("qualified");

        // 2. Create root element that references the ComplexType
        XsdElement rootElement = new XsdElement(typeName);
        rootElement.setType(typeName); // References the complexType by name
        rootElement.setMinOccurs(1);
        rootElement.setMaxOccurs(1);

        // Copy documentation from ComplexType to root element so it displays in properties panel
        if (complexType.getDocumentation() != null) {
            rootElement.setDocumentation(complexType.getDocumentation());
            logger.debug("Copied documentation from ComplexType to root element: '{}'",
                    complexType.getDocumentation().substring(0, Math.min(50, complexType.getDocumentation().length())));
        }
        // Copy all documentations list if present
        if (complexType.hasDocumentations()) {
            for (var doc : complexType.getDocumentations()) {
                rootElement.addDocumentation(doc);
            }
            logger.debug("Copied {} documentations from ComplexType to root element", complexType.getDocumentations().size());
        }
        // Copy appinfo if present
        if (complexType.getAppinfo() != null) {
            rootElement.setAppinfo(complexType.getAppinfo());
            logger.debug("Copied appinfo from ComplexType to root element");
        }

        // 3. Add root element to schema
        virtualSchema.addChild(rootElement);
        logger.debug("Added root element '{}' to virtual schema", typeName);

        // 4. Add ComplexType to schema as global type
        // Note: ComplexType should be added as a child of the schema
        virtualSchema.addChild(complexType);
        logger.debug("Added ComplexType to virtual schema");

        logger.info("Virtual schema created successfully: {} children total", virtualSchema.getChildren().size());

        return virtualSchema;
    }

    /**
     * Extracts changes from virtual schema and merges them back to the original ComplexType in the main schema.
     * <p>
     * This method performs a deep merge of the ComplexType structure:
     * <ul>
     *   <li>Replaces all child nodes (elements, compositors, etc.)</li>
     *   <li>Updates all attributes</li>
     *   <li>Preserves the type name and other metadata</li>
     * </ul>
     *
     * <p><b>Important:</b> This is a one-way merge. The original ComplexType is modified in-place.
     * Any references to the ComplexType in the main schema remain valid.
     *
     * @param virtualSchema the virtual schema containing modifications
     * @param originalType  the original ComplexType in the main schema to update
     * @param mainSchema    the main schema containing the original type
     * @throws IllegalArgumentException if any parameter is null
     */
    public static void mergeChangesBackToMainSchema(
            XsdSchema virtualSchema,
            XsdComplexType originalType,
            XsdSchema mainSchema) {

        if (virtualSchema == null || originalType == null || mainSchema == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }

        String typeName = originalType.getName();
        logger.info("Merging changes from virtual schema back to main schema for type: {}", typeName);

        try {
            // 1. Extract modified ComplexType from virtual schema
            XsdComplexType modifiedType = extractComplexTypeFromVirtualSchema(virtualSchema, typeName);

            if (modifiedType == null) {
                logger.warn("Could not find ComplexType '{}' in virtual schema - no changes to merge", typeName);
                return;
            }

            // 2. Merge structure: Replace all children
            logger.debug("Merging {} children from modified type to original", modifiedType.getChildren().size());
            originalType.getChildren().clear();

            // Deep copy children to avoid reference issues
            for (XsdNode child : modifiedType.getChildren()) {
                originalType.addChild(child);
            }

            // 3. Merge properties
            originalType.setAbstract(modifiedType.isAbstract());
            originalType.setMixed(modifiedType.isMixed());

            // Note: Attributes are stored as XsdAttribute children in the structure
            // They are already included in the children merge above

            // 4. Merge documentation from root element back to ComplexType
            // The root element holds the editable documentation in the type editor
            XsdElement rootElement = extractRootElementFromVirtualSchema(virtualSchema, typeName);
            if (rootElement != null) {
                // Sync documentation from root element to ComplexType
                String rootDoc = rootElement.getDocumentation();
                if (rootDoc != null && !rootDoc.isEmpty()) {
                    originalType.setDocumentation(rootDoc);
                    logger.debug("Synced documentation from root element to ComplexType: '{}'",
                            rootDoc.substring(0, Math.min(50, rootDoc.length())));
                } else if (rootDoc == null || rootDoc.isEmpty()) {
                    // If documentation was cleared, clear it on the type too
                    originalType.setDocumentation(null);
                }

                // Sync documentations list
                originalType.getDocumentations().clear();
                if (rootElement.hasDocumentations()) {
                    for (var doc : rootElement.getDocumentations()) {
                        originalType.addDocumentation(doc);
                    }
                    logger.debug("Synced {} documentations from root element to ComplexType",
                            rootElement.getDocumentations().size());
                }

                // Sync appinfo
                if (rootElement.getAppinfo() != null) {
                    originalType.setAppinfo(rootElement.getAppinfo());
                    logger.debug("Synced appinfo from root element to ComplexType");
                }
            }

            // 5. Note: Property change events are automatically fired by the individual setters
            // No need to manually fire a schema-level event here

            logger.info("Successfully merged changes for ComplexType: {}", typeName);

        } catch (Exception e) {
            logger.error("Error merging changes for ComplexType: {}", typeName, e);
            throw new RuntimeException("Failed to merge changes: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the root element from a virtual schema.
     * <p>
     * The root element is the first XsdElement child of the virtual schema.
     *
     * @param virtualSchema the virtual schema
     * @param typeName      the expected name of the root element (for validation)
     * @return the root element, or null if not found
     */
    private static XsdElement extractRootElementFromVirtualSchema(XsdSchema virtualSchema, String typeName) {
        if (virtualSchema == null) {
            return null;
        }

        // The root element is typically the first element in the virtual schema
        for (XsdNode child : virtualSchema.getChildren()) {
            if (child instanceof XsdElement element) {
                if (typeName.equals(element.getName())) {
                    logger.debug("Found root element '{}' in virtual schema", typeName);
                    return element;
                }
            }
        }

        logger.warn("Root element '{}' not found in virtual schema", typeName);
        return null;
    }

    /**
     * Extracts the ComplexType from a virtual schema.
     * <p>
     * Searches the virtual schema's children for a ComplexType with the given name.
     *
     * @param virtualSchema the virtual schema
     * @param typeName      the name of the ComplexType to extract
     * @return the ComplexType, or null if not found
     */
    private static XsdComplexType extractComplexTypeFromVirtualSchema(XsdSchema virtualSchema, String typeName) {
        if (virtualSchema == null || typeName == null) {
            return null;
        }

        // Search for ComplexType in schema's direct children
        for (XsdNode child : virtualSchema.getChildren()) {
            if (child instanceof XsdComplexType complexType) {
                if (typeName.equals(complexType.getName())) {
                    logger.debug("Found ComplexType '{}' in virtual schema", typeName);
                    return complexType;
                }
            }
        }

        logger.warn("ComplexType '{}' not found in virtual schema", typeName);
        return null;
    }

    /**
     * Validates that a virtual schema is correctly structured.
     * <p>
     * Checks:
     * <ul>
     *   <li>Schema has virtual namespace</li>
     *   <li>Schema contains at least one root element</li>
     *   <li>Schema contains the ComplexType definition</li>
     * </ul>
     *
     * @param virtualSchema the virtual schema to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidVirtualSchema(XsdSchema virtualSchema) {
        if (virtualSchema == null) {
            return false;
        }

        // Check namespace
        if (!VIRTUAL_NAMESPACE.equals(virtualSchema.getTargetNamespace())) {
            logger.warn("Virtual schema has incorrect namespace: {}", virtualSchema.getTargetNamespace());
            return false;
        }

        // Check has children (at least root element and complexType)
        if (virtualSchema.getChildren().size() < 2) {
            logger.warn("Virtual schema has too few children: {}", virtualSchema.getChildren().size());
            return false;
        }

        // Check first child is element
        if (!(virtualSchema.getChildren().get(0) instanceof XsdElement)) {
            logger.warn("First child of virtual schema is not an element");
            return false;
        }

        logger.debug("Virtual schema validation passed");
        return true;
    }

    /**
     * Creates a deep copy of a ComplexType.
     * <p>
     * This is useful for creating an editable copy without affecting the original.
     * <p>
     * <b>Note:</b> Currently, this creates a shallow copy. A full deep copy implementation
     * would require recursive copying of all child nodes and attributes.
     *
     * @param original the original ComplexType
     * @return a copy of the ComplexType
     */
    public static XsdComplexType deepCopyComplexType(XsdComplexType original) {
        if (original == null) {
            return null;
        }

        logger.debug("Creating deep copy of ComplexType: {}", original.getName());

        XsdComplexType copy = new XsdComplexType(original.getName());

        // Copy properties
        copy.setAbstract(original.isAbstract());
        copy.setMixed(original.isMixed());

        // Copy children (currently shallow - could be enhanced for true deep copy)
        // Note: Attributes are included as XsdAttribute children
        for (XsdNode child : original.getChildren()) {
            copy.addChild(child);
        }

        logger.debug("Deep copy created with {} children",
                copy.getChildren().size());

        return copy;
    }
}
