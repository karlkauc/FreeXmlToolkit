package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Service for finding usages of a type within an XSD schema.
 * Searches through all nodes to find where a given type name is referenced.
 * <p>
 * Supports finding usages in:
 * <ul>
 *   <li>Element type attributes</li>
 *   <li>Attribute type attributes</li>
 *   <li>Restriction base types</li>
 *   <li>Extension base types</li>
 *   <li>List item types</li>
 *   <li>Union member types</li>
 *   <li>Alternative types (XSD 1.1)</li>
 * </ul>
 * <p>
 * Uses cycle detection to prevent infinite loops in recursive schemas.
 *
 * @since 2.0
 */
public class TypeUsageFinder {

    private static final Logger logger = LogManager.getLogger(TypeUsageFinder.class);

    private final XsdSchema schema;

    /**
     * Creates a new TypeUsageFinder for the given schema.
     *
     * @param schema the XSD schema to search (must not be null)
     * @throws NullPointerException if schema is null
     */
    public TypeUsageFinder(XsdSchema schema) {
        Objects.requireNonNull(schema, "Schema cannot be null");
        this.schema = schema;
    }

    /**
     * Finds all usages of a type by name.
     *
     * @param typeName the type name to search for (must not be null or empty)
     * @return list of usage locations (never null, may be empty)
     * @throws IllegalArgumentException if typeName is null or empty
     */
    public List<TypeUsageLocation> findUsages(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("Type name cannot be null or empty");
        }

        logger.debug("Finding usages of type: {}", typeName);
        List<TypeUsageLocation> usages = new ArrayList<>();
        Set<String> visitedIds = new HashSet<>();

        // Normalize the type name (remove namespace prefix if present)
        String normalizedTypeName = normalizeTypeName(typeName);

        // Search through all children of the schema
        searchNode(schema, normalizedTypeName, usages, visitedIds);

        logger.debug("Found {} usages of type '{}'", usages.size(), typeName);
        return usages;
    }

    /**
     * Counts the number of usages of a type.
     *
     * @param typeName the type name to search for
     * @return the number of usages (0 if not used)
     */
    public int countUsages(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return 0;
        }
        return findUsages(typeName).size();
    }

    /**
     * Checks if a type is used anywhere in the schema.
     *
     * @param typeName the type name to check
     * @return true if the type is used, false otherwise
     */
    public boolean isTypeUsed(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return false;
        }
        // Optimization: stop searching after finding first usage
        return !findUsages(typeName).isEmpty();
    }

    /**
     * Recursively searches a node and its children for type usages.
     *
     * @param node           the current node to search
     * @param typeName       the normalized type name to find
     * @param usages         the list to add found usages to
     * @param visitedIds     set of visited node IDs to prevent cycles
     */
    private void searchNode(XsdNode node, String typeName, List<TypeUsageLocation> usages, Set<String> visitedIds) {
        if (node == null) {
            return;
        }

        // Cycle detection using node ID
        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) {
            logger.trace("Skipping already visited node: {}", nodeId);
            return;
        }
        if (nodeId != null) {
            visitedIds.add(nodeId);
        }

        // Get source file from node's IncludeSourceInfo
        Path sourceFile = getSourceFile(node);

        // Check this node for type references
        checkNodeForTypeUsage(node, typeName, sourceFile, usages);

        // Recursively search children
        List<XsdNode> children = node.getChildren();
        if (children != null) {
            for (XsdNode child : children) {
                searchNode(child, typeName, usages, visitedIds);
            }
        }
    }

    /**
     * Checks a single node for type references.
     *
     * @param node       the node to check
     * @param typeName   the normalized type name to find
     * @param sourceFile the source file path for this node
     * @param usages     the list to add found usages to
     */
    private void checkNodeForTypeUsage(XsdNode node, String typeName, Path sourceFile, List<TypeUsageLocation> usages) {
        // Check XsdElement type attribute
        if (node instanceof XsdElement element) {
            if (matchesTypeName(element.getType(), typeName)) {
                usages.add(new TypeUsageLocation(node, UsageReferenceType.ELEMENT_TYPE, sourceFile));
                logger.trace("Found element type usage: {}", element.getName());
            }
        }

        // Check XsdAttribute type attribute
        if (node instanceof XsdAttribute attribute) {
            if (matchesTypeName(attribute.getType(), typeName)) {
                usages.add(new TypeUsageLocation(node, UsageReferenceType.ATTRIBUTE_TYPE, sourceFile));
                logger.trace("Found attribute type usage: {}", attribute.getName());
            }
        }

        // Check XsdRestriction base attribute
        if (node instanceof XsdRestriction restriction) {
            if (matchesTypeName(restriction.getBase(), typeName)) {
                usages.add(new TypeUsageLocation(node, UsageReferenceType.RESTRICTION_BASE, sourceFile));
                logger.trace("Found restriction base usage in: {}", getParentName(node));
            }
        }

        // Check XsdExtension base attribute
        if (node instanceof XsdExtension extension) {
            if (matchesTypeName(extension.getBase(), typeName)) {
                usages.add(new TypeUsageLocation(node, UsageReferenceType.EXTENSION_BASE, sourceFile));
                logger.trace("Found extension base usage in: {}", getParentName(node));
            }
        }

        // Check XsdList itemType attribute
        if (node instanceof XsdList list) {
            if (matchesTypeName(list.getItemType(), typeName)) {
                usages.add(new TypeUsageLocation(node, UsageReferenceType.LIST_ITEM_TYPE, sourceFile));
                logger.trace("Found list itemType usage in: {}", getParentName(node));
            }
        }

        // Check XsdUnion memberTypes attribute
        if (node instanceof XsdUnion union) {
            List<String> memberTypes = union.getMemberTypes();
            if (memberTypes != null) {
                for (String memberType : memberTypes) {
                    if (matchesTypeName(memberType, typeName)) {
                        usages.add(new TypeUsageLocation(node, UsageReferenceType.UNION_MEMBER_TYPE, sourceFile));
                        logger.trace("Found union memberType usage in: {}", getParentName(node));
                        break; // Only add one usage per union, even if type appears multiple times
                    }
                }
            }
        }

        // Check XsdAlternative type attribute (XSD 1.1)
        if (node instanceof XsdAlternative alternative) {
            if (matchesTypeName(alternative.getType(), typeName)) {
                usages.add(new TypeUsageLocation(node, UsageReferenceType.ALTERNATIVE_TYPE, sourceFile));
                logger.trace("Found alternative type usage in: {}", getParentName(node));
            }
        }
    }

    /**
     * Checks if a type reference matches the target type name.
     * Handles namespace prefixes by normalizing both names.
     *
     * @param typeRef    the type reference to check (may be null or have namespace prefix)
     * @param targetName the target type name (already normalized)
     * @return true if they match, false otherwise
     */
    private boolean matchesTypeName(String typeRef, String targetName) {
        if (typeRef == null || typeRef.isBlank()) {
            return false;
        }
        String normalizedRef = normalizeTypeName(typeRef);
        return normalizedRef.equals(targetName);
    }

    /**
     * Normalizes a type name by removing namespace prefix.
     * Converts "tns:MyType" or "xs:string" to "MyType" or "string".
     *
     * @param typeName the type name to normalize
     * @return the normalized type name without prefix
     */
    private String normalizeTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        int colonIndex = typeName.lastIndexOf(':');
        if (colonIndex >= 0 && colonIndex < typeName.length() - 1) {
            return typeName.substring(colonIndex + 1);
        }
        return typeName;
    }

    /**
     * Gets the source file path for a node.
     *
     * @param node the node to get the source file for
     * @return the source file path, or null if not set
     */
    private Path getSourceFile(XsdNode node) {
        IncludeSourceInfo sourceInfo = node.getSourceInfo();
        if (sourceInfo != null) {
            return sourceInfo.getSourceFile();
        }
        return null;
    }

    /**
     * Gets the parent name for logging purposes.
     *
     * @param node the node to get the parent name from
     * @return the parent's name or "(no parent)"
     */
    private String getParentName(XsdNode node) {
        XsdNode parent = node.getParent();
        if (parent != null && parent.getName() != null) {
            return parent.getName();
        }
        return "(no parent)";
    }
}
