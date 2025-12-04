package org.fxt.freexmltoolkit.controls.v2.editor.usage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a location where a type is used within an XSD schema.
 * Immutable record containing all information needed to display
 * and navigate to a usage location.
 *
 * @param node          the XSD node that references the type
 * @param referenceType the type of reference (e.g., "element type", "restriction base")
 * @param sourceFile    the source file containing the reference (for multi-file schemas)
 * @since 2.0
 */
public record TypeUsageLocation(
        XsdNode node,
        UsageReferenceType referenceType,
        Path sourceFile
) {

    /**
     * Creates a new TypeUsageLocation.
     *
     * @param node          the XSD node (must not be null)
     * @param referenceType the reference type (must not be null)
     * @param sourceFile    the source file (can be null for main schema)
     * @throws NullPointerException if node or referenceType is null
     */
    public TypeUsageLocation {
        Objects.requireNonNull(node, "Node cannot be null");
        Objects.requireNonNull(referenceType, "Reference type cannot be null");
    }

    /**
     * Gets a human-readable description of this usage location.
     *
     * @return description string
     */
    public String getDescription() {
        String nodeType = node.getClass().getSimpleName().replace("Xsd", "");
        String nodeName = node.getName() != null ? node.getName() : "(anonymous)";
        return String.format("%s '%s' (%s)", nodeType, nodeName, referenceType.getDisplayName());
    }

    /**
     * Gets the XPath-like path to this node from the schema root.
     *
     * @return path string showing the hierarchy (e.g., "Schema > ComplexType > Element")
     */
    public String getPath() {
        StringBuilder path = new StringBuilder();
        XsdNode current = node;

        while (current != null && current.getParent() != null) {
            if (!path.isEmpty()) {
                path.insert(0, " > ");
            }
            String name = current.getName() != null
                    ? current.getName()
                    : current.getNodeType().toString();
            path.insert(0, name);
            current = current.getParent();
        }

        return path.isEmpty() ? "(root)" : path.toString();
    }

    /**
     * Gets the name of the source file, or "(main)" if no source file is set.
     *
     * @return the file name or "(main)"
     */
    public String getSourceFileName() {
        if (sourceFile == null) {
            return "(main)";
        }
        return sourceFile.getFileName().toString();
    }

    /**
     * Gets the node name for display purposes.
     *
     * @return the node name or "(anonymous)"
     */
    public String getNodeName() {
        return node.getName() != null ? node.getName() : "(anonymous)";
    }

    /**
     * Gets the node type as a display string (e.g., "Element", "Restriction").
     *
     * @return the node type name without "Xsd" prefix
     */
    public String getNodeTypeName() {
        return node.getClass().getSimpleName().replace("Xsd", "");
    }
}
