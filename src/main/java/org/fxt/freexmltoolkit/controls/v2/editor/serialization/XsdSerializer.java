package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.ExportMetadataService;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Serializer for converting XSD model to XML representation.
 * <p>
 * Serializes from XsdNode-based model (not VisualNode view layer).
 * Supports:
 * - Full model-to-XSD XML serialization
 * - File backup functionality
 * - Pretty-print with configurable indentation
 * - Namespace management
 * - XSD 1.0/1.1 version support
 *
 * @since 2.0
 */
public class XsdSerializer {

    private static final Logger logger = LogManager.getLogger(XsdSerializer.class);
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String DEFAULT_INDENT = "    "; // 4 spaces
    private static final int MAX_SERIALIZATION_DEPTH = 100; // Prevent infinite recursion

    private String indentString = DEFAULT_INDENT;
    private XsdSortOrder sortOrder = null; // null means use default from settings

    /**
     * Creates a backup of the specified file.
     *
     * @param filePath the file to backup
     * @return the path to the backup file
     * @throws IOException if backup fails
     */
    public Path createBackup(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            logger.warn("Cannot create backup: file does not exist: {}", filePath);
            return null;
        }

        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
        String fileName = filePath.getFileName().toString();
        String backupFileName = fileName.replaceFirst("(\\.[^.]+)$", "_backup_" + timestamp + "$1");

        Path backupPath = filePath.getParent().resolve(backupFileName);
        Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        logger.info("Created backup: {}", backupPath);
        return backupPath;
    }

    /**
     * Serializes an XsdSchema to XSD XML.
     * This is the main entry point for model-to-XML serialization.
     *
     * @param schema the XSD schema model
     * @return XSD XML string
     */
    public String serialize(XsdSchema schema) {
        if (schema == null) {
            logger.warn("Cannot serialize null schema");
            return "";
        }

        logger.info("Serializing XsdSchema with {} children", schema.getChildren().size());

        StringBuilder sb = new StringBuilder();

        // XML declaration
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Output leading comments (comments before the schema element)
        for (String comment : schema.getLeadingComments()) {
            sb.append("<!--").append(comment).append("-->\n");
        }

        // Schema element with all namespace declarations
        sb.append("<xs:schema");

        // Output all namespace declarations
        Map<String, String> namespaces = schema.getNamespaces();
        for (Map.Entry<String, String> ns : namespaces.entrySet()) {
            String prefix = ns.getKey();
            String uri = ns.getValue();
            if (prefix.isEmpty()) {
                sb.append(" xmlns=\"").append(escapeXml(uri)).append("\"");
            } else {
                sb.append(" xmlns:").append(prefix).append("=\"").append(escapeXml(uri)).append("\"");
            }
        }

        // Add attributeFormDefault if not default (unqualified)
        if (schema.getAttributeFormDefault() != null && !schema.getAttributeFormDefault().isEmpty()) {
            sb.append(" attributeFormDefault=\"").append(escapeXml(schema.getAttributeFormDefault())).append("\"");
        }

        // Add elementFormDefault
        if (schema.getElementFormDefault() != null && !schema.getElementFormDefault().isEmpty()) {
            sb.append(" elementFormDefault=\"").append(escapeXml(schema.getElementFormDefault())).append("\"");
        }

        // Add target namespace if present
        if (schema.getTargetNamespace() != null && !schema.getTargetNamespace().isEmpty()) {
            sb.append(" targetNamespace=\"").append(escapeXml(schema.getTargetNamespace())).append("\"");
        }

        // Add version if present
        if (schema.getVersion() != null && !schema.getVersion().isEmpty()) {
            sb.append(" version=\"").append(escapeXml(schema.getVersion())).append("\"");
        }

        // Add additional attributes (like vc:minVersion)
        Map<String, String> additionalAttributes = schema.getAdditionalAttributes();
        for (Map.Entry<String, String> attr : additionalAttributes.entrySet()) {
            sb.append(" ").append(attr.getKey()).append("=\"").append(escapeXml(attr.getValue())).append("\"");
        }

        sb.append(">\n");

        // Get the effective sort order (from instance or from settings)
        XsdSortOrder effectiveSortOrder = getEffectiveSortOrder();

        // Sort children before serializing
        List<XsdNode> sortedChildren = XsdNodeSorter.sortSchemaChildren(
                schema.getChildren(), effectiveSortOrder);

        logger.debug("Serializing {} children with sort order: {}", sortedChildren.size(), effectiveSortOrder);

        // Serialize children (global elements, types, etc.)
        for (XsdNode child : sortedChildren) {
            serializeXsdNode(child, sb, 1);
        }

        sb.append("</xs:schema>\n");

        logger.info("Serialization complete: {} characters", sb.length());
        return sb.toString();
    }

    /**
     * Serializes a single SimpleType to XSD XML.
     * Useful for previewing individual SimpleTypes without full schema context.
     *
     * @param simpleType the simple type to serialize
     * @return XSD XML string for the simple type
     * @since 2.0
     */
    public String serializeSimpleTypeOnly(XsdSimpleType simpleType) {
        if (simpleType == null) {
            logger.warn("Cannot serialize null SimpleType");
            return "";
        }

        StringBuilder sb = new StringBuilder();
        serializeSimpleType(simpleType, sb, "", 0);
        return sb.toString();
    }

    /**
     * Serializes a single XsdNode (recursive helper method).
     *
     * @param node   the node to serialize
     * @param sb     the string builder
     * @param indent the indentation level
     */
    private void serializeXsdNode(XsdNode node, StringBuilder sb, int indent) {
        // Prevent infinite recursion
        if (indent > MAX_SERIALIZATION_DEPTH) {
            logger.warn("Maximum serialization depth ({}) exceeded. Truncating at node: {}",
                    MAX_SERIALIZATION_DEPTH, node.getName());
            return;
        }

        String indentation = indentString.repeat(indent);

        if (node instanceof XsdElement element) {
            serializeElement(element, sb, indentation, indent);
        } else if (node instanceof XsdComplexType complexType) {
            serializeComplexType(complexType, sb, indentation, indent);
        } else if (node instanceof XsdSequence sequence) {
            serializeSequence(sequence, sb, indentation, indent);
        } else if (node instanceof XsdChoice choice) {
            serializeChoice(choice, sb, indentation, indent);
        } else if (node instanceof XsdAll all) {
            serializeAll(all, sb, indentation, indent);
        } else if (node instanceof XsdAttribute attribute) {
            serializeAttribute(attribute, sb, indentation);
        } else if (node instanceof XsdSimpleType simpleType) {
            serializeSimpleType(simpleType, sb, indentation, indent);
        } else if (node instanceof XsdRestriction restriction) {
            serializeRestriction(restriction, sb, indentation, indent);
        } else if (node instanceof XsdList list) {
            serializeList(list, sb, indentation, indent);
        } else if (node instanceof XsdUnion union) {
            serializeUnion(union, sb, indentation, indent);
        } else if (node instanceof XsdFacet facet) {
            serializeFacet(facet, sb, indentation);
        } else if (node instanceof XsdGroup group) {
            serializeGroup(group, sb, indentation, indent);
        } else if (node instanceof XsdAttributeGroup attributeGroup) {
            serializeAttributeGroup(attributeGroup, sb, indentation, indent);
        } else if (node instanceof XsdKey key) {
            serializeKey(key, sb, indentation, indent);
        } else if (node instanceof XsdKeyRef keyRef) {
            serializeKeyRef(keyRef, sb, indentation, indent);
        } else if (node instanceof XsdUnique unique) {
            serializeUnique(unique, sb, indentation, indent);
        } else if (node instanceof XsdSelector selector) {
            serializeSelector(selector, sb, indentation);
        } else if (node instanceof XsdField field) {
            serializeField(field, sb, indentation);
        } else if (node instanceof XsdImport xsdImport) {
            serializeImport(xsdImport, sb, indentation);
        } else if (node instanceof XsdInclude include) {
            serializeInclude(include, sb, indentation);
        } else if (node instanceof XsdRedefine redefine) {
            serializeRedefine(redefine, sb, indentation, indent);
        } else if (node instanceof XsdAssert xsdAssert) {
            serializeAssert(xsdAssert, sb, indentation);
        } else if (node instanceof XsdAlternative alternative) {
            serializeAlternative(alternative, sb, indentation, indent);
        } else if (node instanceof XsdOpenContent openContent) {
            serializeOpenContent(openContent, sb, indentation, indent);
        } else if (node instanceof XsdOverride override) {
            serializeOverride(override, sb, indentation, indent);
        } else if (node instanceof XsdSimpleContent simpleContent) {
            serializeSimpleContent(simpleContent, sb, indentation, indent);
        } else if (node instanceof XsdComplexContent complexContent) {
            serializeComplexContent(complexContent, sb, indentation, indent);
        } else if (node instanceof XsdExtension extension) {
            serializeExtension(extension, sb, indentation, indent);
        } else if (node instanceof XsdComment comment) {
            serializeComment(comment, sb, indentation);
        } else if (node instanceof XsdAny any) {
            serializeAny(any, sb, indentation);
        } else if (node instanceof XsdAnyAttribute anyAttr) {
            serializeAnyAttribute(anyAttr, sb, indentation);
        } else {
            logger.warn("Unknown node type: {}", node.getClass().getSimpleName());
        }
    }

    private void serializeElement(XsdElement element, StringBuilder sb, String indentation, int indent) {
        // NOTE: We do NOT modify the model during serialization to prevent PropertyChangeEvents
        // that could cause infinite loops. Instead, we serialize constraints directly if needed.

        sb.append(indentation).append("<xs:element");

        // Check if this is a reference element (ref attribute)
        if (element.getRef() != null && !element.getRef().isEmpty()) {
            // Reference element: only ref, minOccurs, maxOccurs allowed
            sb.append(" ref=\"").append(escapeXml(element.getRef())).append("\"");

            // Add cardinality
            if (element.getMinOccurs() != 1) {
                sb.append(" minOccurs=\"").append(element.getMinOccurs()).append("\"");
            }
            if (element.getMaxOccurs() == XsdNode.UNBOUNDED) {
                sb.append(" maxOccurs=\"unbounded\"");
            } else if (element.getMaxOccurs() != 1) {
                sb.append(" maxOccurs=\"").append(element.getMaxOccurs()).append("\"");
            }

            sb.append("/>\n");
            return;
        }

        // Regular element declaration: name is required
        sb.append(" name=\"").append(escapeXml(element.getName())).append("\"");

        // Check if element has an inline type definition (complexType or simpleType child)
        boolean hasSimpleTypeChild = element.getChildren().stream()
                .anyMatch(c -> c instanceof XsdSimpleType);
        boolean hasInlineTypeDefinition = element.getChildren().stream()
                .anyMatch(c -> c instanceof XsdComplexType || c instanceof XsdSimpleType);

        // Check if element has constraints (for inline constraint handling later)
        boolean hasConstraints = !element.getEnumerations().isEmpty() ||
                                 !element.getPatterns().isEmpty() ||
                                 !element.getAssertions().isEmpty();

        // Add type attribute if specified and no inline type definition exists
        // Note: Elements can have type attribute AND still have children like xs:key, xs:keyref, xs:annotation
        if (element.getType() != null && !element.getType().isEmpty() && !hasInlineTypeDefinition) {
            sb.append(" type=\"").append(escapeXml(element.getType())).append("\"");
        }

        // Add default value (mutually exclusive with fixed)
        if (element.getDefaultValue() != null && !element.getDefaultValue().isEmpty()) {
            sb.append(" default=\"").append(escapeXml(element.getDefaultValue())).append("\"");
        }

        // Add fixed value (mutually exclusive with default)
        if (element.getFixed() != null && !element.getFixed().isEmpty()) {
            sb.append(" fixed=\"").append(escapeXml(element.getFixed())).append("\"");
        }

        // Add form attribute (qualified/unqualified)
        if (element.getForm() != null && !element.getForm().isEmpty()) {
            sb.append(" form=\"").append(escapeXml(element.getForm())).append("\"");
        }

        // Add substitutionGroup
        if (element.getSubstitutionGroup() != null && !element.getSubstitutionGroup().isEmpty()) {
            sb.append(" substitutionGroup=\"").append(escapeXml(element.getSubstitutionGroup())).append("\"");
        }

        // Add abstract flag
        if (element.isAbstract()) {
            sb.append(" abstract=\"true\"");
        }

        // Add nillable flag
        if (element.isNillable()) {
            sb.append(" nillable=\"true\"");
        }

        // Add block attribute (if available in model)
        if (element.getBlock() != null && !element.getBlock().isEmpty()) {
            sb.append(" block=\"").append(escapeXml(element.getBlock())).append("\"");
        }

        // Add cardinality
        if (element.getMinOccurs() != 1) {
            sb.append(" minOccurs=\"").append(element.getMinOccurs()).append("\"");
        }
        if (element.getMaxOccurs() == XsdNode.UNBOUNDED) {
            sb.append(" maxOccurs=\"unbounded\"");
        } else if (element.getMaxOccurs() != 1) {
            sb.append(" maxOccurs=\"").append(element.getMaxOccurs()).append("\"");
        }

        // Check if element has annotation, children, or inline constraints to serialize
        boolean hasAnnotation = !element.getDocumentations().isEmpty() ||
                                element.getDocumentation() != null ||
                                element.getAppinfo() != null;
        boolean needsInlineConstraints = hasConstraints && !hasSimpleTypeChild;

        if (element.hasChildren() || hasAnnotation || needsInlineConstraints) {
            sb.append(">\n");

            // Serialize annotation first (documentation/appinfo)
            if (hasAnnotation) {
                serializeAnnotation(element, sb, indentation + indentString, indent + 1);
            }

            // Serialize children (inline complexType, etc.)
            for (XsdNode child : element.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            // Serialize inline constraints if element has constraints but no simpleType child
            if (needsInlineConstraints) {
                serializeInlineConstraints(element, sb, indentation + indentString, indent + 1);
            }

            sb.append(indentation).append("</xs:element>\n");
        } else {
            // Self-closing tag for simple elements without annotation
            sb.append("/>\n");
        }
    }

    /**
     * Serializes element constraints (enumerations, patterns, assertions) as inline simpleType/restriction
     * WITHOUT modifying the model. This is a read-only serialization.
     *
     * @param element     the element with constraints
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeInlineConstraints(XsdElement element, StringBuilder sb, String indentation, int indent) {
        String innerIndent = indentation + indentString;
        String facetIndent = innerIndent + indentString;

        // Determine base type
        String baseType = element.getType() != null && !element.getType().isEmpty()
                ? element.getType()
                : "xs:string";

        sb.append(indentation).append("<xs:simpleType>\n");
        sb.append(innerIndent).append("<xs:restriction base=\"").append(escapeXml(baseType)).append("\">\n");

        // Serialize enumerations
        for (String enumValue : element.getEnumerations()) {
            sb.append(facetIndent).append("<xs:enumeration value=\"").append(escapeXml(enumValue)).append("\"/>\n");
        }

        // Serialize patterns
        for (String pattern : element.getPatterns()) {
            sb.append(facetIndent).append("<xs:pattern value=\"").append(escapeXml(pattern)).append("\"/>\n");
        }

        // Serialize assertions (XSD 1.1)
        for (String assertion : element.getAssertions()) {
            sb.append(facetIndent).append("<xs:assertion test=\"").append(escapeXml(assertion)).append("\"/>\n");
        }

        sb.append(innerIndent).append("</xs:restriction>\n");
        sb.append(indentation).append("</xs:simpleType>\n");
    }

    /**
     * Synchronizes constraint lists (enumerations, patterns, assertions) from the element
     * back to the XsdRestriction facets in the tree structure before serialization.
     * This ensures that changes made via commands are reflected in the serialized XSD.
     *
     * @param element the element to synchronize
     * @deprecated This method modifies the model during serialization, which can cause
     *             infinite loops due to PropertyChangeEvents. Use serializeInlineConstraints() instead.
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void synchronizeConstraintsToFacets(XsdElement element) {
        // Check if element has any constraints to synchronize
        if (element.getEnumerations().isEmpty() &&
            element.getPatterns().isEmpty() &&
            element.getAssertions().isEmpty()) {
            return; // Nothing to synchronize
        }

        // Find or create simpleType child
        XsdSimpleType simpleType = null;
        for (XsdNode child : element.getChildren()) {
            if (child instanceof XsdSimpleType) {
                simpleType = (XsdSimpleType) child;
                break;
            }
        }

        // If no simpleType exists, create one (without name for inline type)
        if (simpleType == null) {
            simpleType = new XsdSimpleType("");
            element.addChild(simpleType);
        }

        // Find or create restriction child
        XsdRestriction restriction = null;
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction) {
                restriction = (XsdRestriction) child;
                break;
            }
        }

        // If no restriction exists, create one
        if (restriction == null) {
            restriction = new XsdRestriction("xs:string"); // Default base type
            simpleType.addChild(restriction);
        }

        // Clear existing constraint facets from restriction
        restriction.getFacets().removeIf(facet ->
            facet.getFacetType() == XsdFacetType.ENUMERATION ||
            facet.getFacetType() == XsdFacetType.PATTERN ||
            facet.getFacetType() == XsdFacetType.ASSERTION
        );

        // Add enumerations as facets
        for (String enumValue : element.getEnumerations()) {
            XsdFacet facet = new XsdFacet(XsdFacetType.ENUMERATION, enumValue);
            restriction.addFacet(facet);
        }

        // Add patterns as facets
        for (String pattern : element.getPatterns()) {
            XsdFacet facet = new XsdFacet(XsdFacetType.PATTERN, pattern);
            restriction.addFacet(facet);
        }

        // Add assertions as facets
        for (String assertion : element.getAssertions()) {
            XsdFacet facet = new XsdFacet(XsdFacetType.ASSERTION, assertion);
            restriction.addFacet(facet);
        }
    }

    private void serializeComplexType(XsdComplexType complexType, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:complexType");

        // Named complex types have a name attribute
        if (complexType.getName() != null && !complexType.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(complexType.getName())).append("\"");
        }

        // Add mixed attribute
        if (complexType.isMixed()) {
            sb.append(" mixed=\"true\"");
        }

        // Add abstract attribute
        if (complexType.isAbstract()) {
            sb.append(" abstract=\"true\"");
        }

        // Add block attribute (if available in model)
        if (complexType.getBlock() != null && !complexType.getBlock().isEmpty()) {
            sb.append(" block=\"").append(escapeXml(complexType.getBlock())).append("\"");
        }

        // Add final attribute (if available in model)
        if (complexType.getFinal() != null && !complexType.getFinal().isEmpty()) {
            sb.append(" final=\"").append(escapeXml(complexType.getFinal())).append("\"");
        }

        // Check if complexType has content (children or annotation)
        boolean hasAnnotation = complexType.getDocumentation() != null || complexType.getAppinfo() != null
                || !complexType.getDocumentations().isEmpty();

        if (complexType.hasChildren() || hasAnnotation) {
            sb.append(">\n");

            // Serialize annotation FIRST (before children) - this is XSD standard order
            if (hasAnnotation) {
                serializeAnnotation(complexType, sb, indentation + indentString, indent + 1);
            }

            // Serialize children (sequence, choice, all, attributes)
            for (XsdNode child : complexType.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:complexType>\n");
        } else {
            // Empty complexType (no content model)
            sb.append("/>\n");
        }
    }

    private void serializeSequence(XsdSequence sequence, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:sequence");

        // Add cardinality if not default
        if (sequence.getMinOccurs() != 1) {
            sb.append(" minOccurs=\"").append(sequence.getMinOccurs()).append("\"");
        }
        if (sequence.getMaxOccurs() == XsdNode.UNBOUNDED) {
            sb.append(" maxOccurs=\"unbounded\"");
        } else if (sequence.getMaxOccurs() != 1) {
            sb.append(" maxOccurs=\"").append(sequence.getMaxOccurs()).append("\"");
        }

        sb.append(">\n");

        // Serialize child elements
        for (XsdNode child : sequence.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:sequence>\n");
    }

    private void serializeChoice(XsdChoice choice, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:choice");

        // Add cardinality if not default
        if (choice.getMinOccurs() != 1) {
            sb.append(" minOccurs=\"").append(choice.getMinOccurs()).append("\"");
        }
        if (choice.getMaxOccurs() == XsdNode.UNBOUNDED) {
            sb.append(" maxOccurs=\"unbounded\"");
        } else if (choice.getMaxOccurs() != 1) {
            sb.append(" maxOccurs=\"").append(choice.getMaxOccurs()).append("\"");
        }

        sb.append(">\n");

        // Serialize child elements
        for (XsdNode child : choice.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:choice>\n");
    }

    private void serializeAll(XsdAll all, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:all");

        // Add cardinality if not default (xs:all has restrictions)
        if (all.getMinOccurs() != 1) {
            sb.append(" minOccurs=\"").append(all.getMinOccurs()).append("\"");
        }
        if (all.getMaxOccurs() != 1) {
            sb.append(" maxOccurs=\"").append(all.getMaxOccurs()).append("\"");
        }

        sb.append(">\n");

        // Serialize child elements
        for (XsdNode child : all.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:all>\n");
    }

    private void serializeAttribute(XsdAttribute attribute, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:attribute");

        // Check if this is a reference attribute (ref attribute)
        if (attribute.getRef() != null && !attribute.getRef().isEmpty()) {
            // Reference attribute: only ref and use allowed
            sb.append(" ref=\"").append(escapeXml(attribute.getRef())).append("\"");

            // Add use (required/optional/prohibited)
            if (attribute.getUse() != null && !attribute.getUse().isEmpty() && !"optional".equals(attribute.getUse())) {
                sb.append(" use=\"").append(escapeXml(attribute.getUse())).append("\"");
            }

            sb.append("/>\n");
            return;
        }

        // Regular attribute declaration: name is required
        sb.append(" name=\"").append(escapeXml(attribute.getName())).append("\"");

        // Add type
        if (attribute.getType() != null && !attribute.getType().isEmpty()) {
            sb.append(" type=\"").append(escapeXml(attribute.getType())).append("\"");
        }

        // Add use (required/optional/prohibited) - only if not default "optional"
        if (attribute.getUse() != null && !attribute.getUse().isEmpty() && !"optional".equals(attribute.getUse())) {
            sb.append(" use=\"").append(escapeXml(attribute.getUse())).append("\"");
        }

        // Add default value (mutually exclusive with fixed)
        if (attribute.getDefaultValue() != null && !attribute.getDefaultValue().isEmpty()) {
            sb.append(" default=\"").append(escapeXml(attribute.getDefaultValue())).append("\"");
        }

        // Add fixed value (mutually exclusive with default)
        if (attribute.getFixed() != null && !attribute.getFixed().isEmpty()) {
            sb.append(" fixed=\"").append(escapeXml(attribute.getFixed())).append("\"");
        }

        // Add form attribute (qualified/unqualified)
        if (attribute.getForm() != null && !attribute.getForm().isEmpty()) {
            sb.append(" form=\"").append(escapeXml(attribute.getForm())).append("\"");
        }

        sb.append("/>\n");
    }

    private void serializeSimpleType(XsdSimpleType simpleType, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:simpleType");

        // Named simple types have a name attribute
        if (simpleType.getName() != null && !simpleType.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(simpleType.getName())).append("\"");
        }

        // Add final attribute
        if (simpleType.isFinal()) {
            sb.append(" final=\"#all\"");
        }

        sb.append(">\n");

        // Check if simpleType has annotation
        boolean hasAnnotation = simpleType.getDocumentation() != null || simpleType.getAppinfo() != null
                || !simpleType.getDocumentations().isEmpty();

        // Serialize annotation FIRST (before children) - this is XSD standard order
        if (hasAnnotation) {
            serializeAnnotation(simpleType, sb, indentation + indentString, indent + 1);
        }

        // Serialize children (restrictions, list, union, etc.)
        for (XsdNode child : simpleType.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:simpleType>\n");
    }

    /**
     * Serializes xs:restriction element.
     *
     * @param restriction the XSD restriction
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeRestriction(XsdRestriction restriction, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:restriction");

        // Add base attribute
        if (restriction.getBase() != null && !restriction.getBase().isEmpty()) {
            sb.append(" base=\"").append(escapeXml(restriction.getBase())).append("\"");
        }

        sb.append(">\n");

        // Serialize facets (children)
        for (XsdNode child : restriction.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:restriction>\n");
    }

    /**
     * Serializes xs:list element.
     *
     * @param list        the XSD list
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeList(XsdList list, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:list");

        // Add itemType attribute if specified
        if (list.getItemType() != null && !list.getItemType().isEmpty()) {
            sb.append(" itemType=\"").append(escapeXml(list.getItemType())).append("\"");
        }

        // Check if list has inline simpleType children
        if (list.hasChildren()) {
            sb.append(">\n");

            // Serialize inline simpleType children
            for (XsdNode child : list.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:list>\n");
        } else {
            // Self-closing tag if no children
            sb.append("/>\n");
        }
    }

    /**
     * Serializes xs:union element.
     *
     * @param union       the XSD union
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeUnion(XsdUnion union, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:union");

        // Add memberTypes attribute if specified
        if (union.getMemberTypes() != null && !union.getMemberTypes().isEmpty()) {
            String memberTypesStr = String.join(" ", union.getMemberTypes());
            sb.append(" memberTypes=\"").append(escapeXml(memberTypesStr)).append("\"");
        }

        // Check if union has inline simpleType children
        if (union.hasChildren()) {
            sb.append(">\n");

            // Serialize inline simpleType children
            for (XsdNode child : union.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:union>\n");
        } else {
            // Self-closing tag if no children
            sb.append("/>\n");
        }
    }

    /**
     * Serializes xs:facet elements (pattern, minLength, maxLength, enumeration, etc.).
     *
     * @param facet       the XSD facet
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeFacet(XsdFacet facet, StringBuilder sb, String indentation) {
        if (facet.getFacetType() == null) {
            logger.warn("Cannot serialize facet without type");
            return;
        }

        String facetName = facet.getFacetType().getXmlName();
        sb.append(indentation).append("<xs:").append(facetName);

        // Add value attribute - always output, even for empty values (valid XSD)
        if (facet.getValue() != null) {
            sb.append(" value=\"").append(escapeXml(facet.getValue())).append("\"");
        }

        // Add fixed attribute if true
        if (facet.isFixed()) {
            sb.append(" fixed=\"true\"");
        }

        sb.append("/>\n");
    }

    /**
     * Serializes xs:group element (element group).
     * Groups can be either definitions (with name + compositor) or references (with ref).
     *
     * @param group       the XSD group
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeGroup(XsdGroup group, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:group");

        // Check if this is a group reference or definition
        if (group.isReference()) {
            // Group reference: has ref attribute, no children
            sb.append(" ref=\"").append(escapeXml(group.getRef())).append("\"");
            sb.append("/>\n");
        } else {
            // Group definition: has name + compositor (sequence/choice/all)
            if (group.getName() != null && !group.getName().isEmpty()) {
                sb.append(" name=\"").append(escapeXml(group.getName())).append("\"");
            }
            sb.append(">\n");

            // Serialize compositor children (sequence/choice/all)
            for (XsdNode child : group.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:group>\n");
        }
    }

    /**
     * Serializes xs:attributeGroup element.
     * Attribute groups can be either definitions (with name + attributes) or references (with ref).
     *
     * @param attributeGroup the XSD attribute group
     * @param sb             the string builder
     * @param indentation    the indentation string
     * @param indent         the indentation level
     */
    private void serializeAttributeGroup(XsdAttributeGroup attributeGroup, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:attributeGroup");

        // Check if this is an attribute group reference or definition
        if (attributeGroup.isReference()) {
            // AttributeGroup reference: has ref attribute, no children
            sb.append(" ref=\"").append(escapeXml(attributeGroup.getRef())).append("\"");
            sb.append("/>\n");
        } else {
            // AttributeGroup definition: has name + attributes
            if (attributeGroup.getName() != null && !attributeGroup.getName().isEmpty()) {
                sb.append(" name=\"").append(escapeXml(attributeGroup.getName())).append("\"");
            }
            sb.append(">\n");

            // Serialize attribute children
            for (XsdNode child : attributeGroup.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:attributeGroup>\n");
        }
    }

    /**
     * Serializes xs:annotation with xs:documentation and xs:appinfo.
     * Outputs multiple documentation elements with xml:lang if present.
     *
     * @param node        the node containing documentation/appinfo
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeAnnotation(XsdNode node, StringBuilder sb, String indentation, int indent) {
        List<XsdDocumentation> documentations = node.getDocumentations();
        XsdAppInfo appinfo = node.getAppinfo();

        // Check if there's anything to serialize
        boolean hasDocumentations = !documentations.isEmpty();
        boolean hasLegacyDoc = !hasDocumentations && node.getDocumentation() != null &&  !node.getDocumentation().isEmpty();
        boolean hasAppinfo = appinfo != null && appinfo.hasEntries();

        // DEBUG logging
        if (hasDocumentations || hasLegacyDoc || hasAppinfo) {
            logger.debug("serializeAnnotation for node '{}': docs.size={}, hasLegacyDoc={}, hasAppinfo={}",
                        node.getName(), documentations.size(), hasLegacyDoc, hasAppinfo);
        }

        if (!hasDocumentations && !hasLegacyDoc && !hasAppinfo) {
            return; // Nothing to serialize
        }

        sb.append(indentation).append("<xs:annotation>\n");

        // Serialize documentation entries (new multi-language approach)
        if (hasDocumentations) {
            for (XsdDocumentation doc : documentations) {
                sb.append(indentation).append(indentString).append("<xs:documentation");
                if (doc.hasLang()) {
                    sb.append(" xml:lang=\"").append(escapeXml(doc.getLang())).append("\"");
                }
                if (doc.getSource() != null && !doc.getSource().isEmpty()) {
                    sb.append(" source=\"").append(escapeXml(doc.getSource())).append("\"");
                }
                sb.append(">");
                sb.append(escapeXml(doc.getText()));
                sb.append("</xs:documentation>\n");
            }
        } else if (hasLegacyDoc) {
            // Fallback to legacy single documentation string
            sb.append(indentation).append(indentString).append("<xs:documentation>");
            sb.append(escapeXml(node.getDocumentation()));
            sb.append("</xs:documentation>\n");
        }

        // Serialize structured appinfo entries
        if (hasAppinfo) {
            for (String xmlString : appinfo.toXmlStrings()) {
                sb.append(indentation).append(indentString).append(xmlString).append("\n");
            }
        }

        sb.append(indentation).append("</xs:annotation>\n");
    }

    /**
     * Serializes xs:key element (identity constraint).
     *
     * @param key         the XSD key
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeKey(XsdKey key, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:key");

        // Add name attribute
        if (key.getName() != null && !key.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(key.getName())).append("\"");
        }

        sb.append(">\n");

        // Serialize selector and fields (children)
        for (XsdNode child : key.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:key>\n");
    }

    /**
     * Serializes xs:keyref element (referential constraint).
     *
     * @param keyRef      the XSD keyref
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeKeyRef(XsdKeyRef keyRef, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:keyref");

        // Add name attribute
        if (keyRef.getName() != null && !keyRef.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(keyRef.getName())).append("\"");
        }

        // Add refer attribute (required)
        if (keyRef.getRefer() != null && !keyRef.getRefer().isEmpty()) {
            sb.append(" refer=\"").append(escapeXml(keyRef.getRefer())).append("\"");
        }

        sb.append(">\n");

        // Serialize selector and fields (children)
        for (XsdNode child : keyRef.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:keyref>\n");
    }

    /**
     * Serializes xs:unique element (uniqueness constraint).
     *
     * @param unique      the XSD unique
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeUnique(XsdUnique unique, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:unique");

        // Add name attribute
        if (unique.getName() != null && !unique.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(unique.getName())).append("\"");
        }

        sb.append(">\n");

        // Serialize selector and fields (children)
        for (XsdNode child : unique.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:unique>\n");
    }

    /**
     * Serializes xs:selector element.
     *
     * @param selector    the XSD selector
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeSelector(XsdSelector selector, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:selector");

        // Add xpath attribute
        if (selector.getXpath() != null && !selector.getXpath().isEmpty()) {
            sb.append(" xpath=\"").append(escapeXml(selector.getXpath())).append("\"");
        }

        sb.append("/>\n");
    }

    /**
     * Serializes xs:field element.
     *
     * @param field       the XSD field
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeField(XsdField field, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:field");

        // Add xpath attribute
        if (field.getXpath() != null && !field.getXpath().isEmpty()) {
            sb.append(" xpath=\"").append(escapeXml(field.getXpath())).append("\"");
        }

        sb.append("/>\n");
    }

    /**
     * Serializes xs:import element (schema reference for different namespace).
     *
     * @param xsdImport   the XSD import
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeImport(XsdImport xsdImport, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:import");

        // Add namespace attribute (required for import)
        if (xsdImport.getNamespace() != null && !xsdImport.getNamespace().isEmpty()) {
            sb.append(" namespace=\"").append(escapeXml(xsdImport.getNamespace())).append("\"");
        }

        // Add schemaLocation attribute (optional)
        if (xsdImport.getSchemaLocation() != null && !xsdImport.getSchemaLocation().isEmpty()) {
            sb.append(" schemaLocation=\"").append(escapeXml(xsdImport.getSchemaLocation())).append("\"");
        }

        sb.append("/>\n");
    }

    /**
     * Serializes xs:include element (schema reference for same namespace).
     *
     * @param include     the XSD include
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeInclude(XsdInclude include, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:include");

        // Add schemaLocation attribute (required for include)
        if (include.getSchemaLocation() != null && !include.getSchemaLocation().isEmpty()) {
            sb.append(" schemaLocation=\"").append(escapeXml(include.getSchemaLocation())).append("\"");
        }

        sb.append("/>\n");
    }

    /**
     * Serializes xs:redefine element (schema refinement, deprecated in XSD 1.1).
     *
     * @param redefine    the XSD redefine
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeRedefine(XsdRedefine redefine, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:redefine");

        // Add schemaLocation attribute (required for redefine)
        if (redefine.getSchemaLocation() != null && !redefine.getSchemaLocation().isEmpty()) {
            sb.append(" schemaLocation=\"").append(escapeXml(redefine.getSchemaLocation())).append("\"");
        }

        // Check if redefine has redefinition children
        if (redefine.hasChildren()) {
            sb.append(">\n");

            // Serialize redefined components (simpleType, complexType, group, attributeGroup)
            for (XsdNode child : redefine.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:redefine>\n");
        } else {
            // Self-closing tag if no redefinitions (just includes)
            sb.append("/>\n");
        }
    }

    /**
     * Serializes xs:assert element (XSD 1.1 feature).
     *
     * @param xsdAssert   the XSD assert
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeAssert(XsdAssert xsdAssert, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:assert");

        // Add test attribute (XPath 2.0 expression)
        if (xsdAssert.getTest() != null && !xsdAssert.getTest().isEmpty()) {
            sb.append(" test=\"").append(escapeXml(xsdAssert.getTest())).append("\"");
        }

        // Add xpathDefaultNamespace attribute (optional)
        if (xsdAssert.getXpathDefaultNamespace() != null && !xsdAssert.getXpathDefaultNamespace().isEmpty()) {
            sb.append(" xpathDefaultNamespace=\"").append(escapeXml(xsdAssert.getXpathDefaultNamespace())).append("\"");
        }

        sb.append("/>\n");
    }

    /**
     * Serializes xs:alternative element (XSD 1.1 conditional type assignment).
     *
     * @param alternative the XSD alternative
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeAlternative(XsdAlternative alternative, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:alternative");

        // Add test attribute (XPath 2.0 expression, optional if no type)
        if (alternative.getTest() != null && !alternative.getTest().isEmpty()) {
            sb.append(" test=\"").append(escapeXml(alternative.getTest())).append("\"");
        }

        // Add type attribute (type reference, optional if inline type)
        if (alternative.getType() != null && !alternative.getType().isEmpty()) {
            sb.append(" type=\"").append(escapeXml(alternative.getType())).append("\"");
        }

        // Check if alternative has inline type children
        if (alternative.hasChildren()) {
            sb.append(">\n");

            // Serialize inline simpleType or complexType
            for (XsdNode child : alternative.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:alternative>\n");
        } else {
            // Self-closing tag if no inline types
            sb.append("/>\n");
        }
    }

    /**
     * Serializes xs:openContent element (XSD 1.1 feature for controlled wildcard integration).
     *
     * @param openContent the XSD openContent
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeOpenContent(XsdOpenContent openContent, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:openContent");

        // Add mode attribute ("interleave" or "suffix")
        if (openContent.getMode() != null) {
            sb.append(" mode=\"").append(openContent.getMode().getValue()).append("\"");
        }

        // Check if openContent has wildcard child
        if (openContent.hasChildren()) {
            sb.append(">\n");

            // Serialize wildcard (any element)
            for (XsdNode child : openContent.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:openContent>\n");
        } else {
            // Self-closing tag if no wildcard
            sb.append("/>\n");
        }
    }

    /**
     * Serializes xs:override element (XSD 1.1 replacement for redefine).
     *
     * @param override    the XSD override
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeOverride(XsdOverride override, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:override");

        // Add schemaLocation attribute (required for override)
        if (override.getSchemaLocation() != null && !override.getSchemaLocation().isEmpty()) {
            sb.append(" schemaLocation=\"").append(escapeXml(override.getSchemaLocation())).append("\"");
        }

        // Check if override has component children
        if (override.hasChildren()) {
            sb.append(">\n");

            // Serialize override components (simpleType, complexType, group, attributeGroup, etc.)
            for (XsdNode child : override.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:override>\n");
        } else {
            // Self-closing tag if no overrides
            sb.append("/>\n");
        }
    }

    /**
     * Serializes xs:simpleContent element.
     *
     * @param simpleContent the XSD simpleContent
     * @param sb            the string builder
     * @param indentation   the indentation string
     * @param indent        the indentation level
     */
    private void serializeSimpleContent(XsdSimpleContent simpleContent, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:simpleContent>\n");

        // Serialize children (extension or restriction)
        for (XsdNode child : simpleContent.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:simpleContent>\n");
    }

    /**
     * Serializes xs:complexContent element.
     *
     * @param complexContent the XSD complexContent
     * @param sb             the string builder
     * @param indentation    the indentation string
     * @param indent         the indentation level
     */
    private void serializeComplexContent(XsdComplexContent complexContent, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:complexContent");

        // Add mixed attribute if present
        if (complexContent.isMixed()) {
            sb.append(" mixed=\"true\"");
        }

        sb.append(">\n");

        // Serialize children (extension or restriction)
        for (XsdNode child : complexContent.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:complexContent>\n");
    }

    /**
     * Serializes xs:extension element.
     *
     * @param extension   the XSD extension
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeExtension(XsdExtension extension, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:extension");

        // Add base attribute (required)
        if (extension.getBase() != null && !extension.getBase().isEmpty()) {
            sb.append(" base=\"").append(escapeXml(extension.getBase())).append("\"");
        }

        // Check if extension has children
        if (extension.hasChildren()) {
            sb.append(">\n");

            // Serialize children (sequence, choice, all, attributes, etc.)
            for (XsdNode child : extension.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:extension>\n");
        } else {
            // Self-closing tag if no additional content
            sb.append("/>\n");
        }
    }

    /**
     * Serializes an XML comment.
     *
     * @param comment     the XSD comment
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeComment(XsdComment comment, StringBuilder sb, String indentation) {
        if (comment.getContent() != null) {
            sb.append(indentation).append("<!--").append(comment.getContent()).append("-->\n");
        }
    }

    /**
     * Serializes xs:any wildcard.
     *
     * @param any         the XSD any
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeAny(XsdAny any, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:any");

        // Add namespace attribute if not default (##any)
        if (any.getNamespace() != null && !"##any".equals(any.getNamespace())) {
            sb.append(" namespace=\"").append(escapeXml(any.getNamespace())).append("\"");
        }

        // Add processContents attribute if not default (strict)
        if (any.getProcessContents() != null && any.getProcessContents() != XsdAny.ProcessContents.STRICT) {
            sb.append(" processContents=\"").append(any.getProcessContents().getValue()).append("\"");
        }

        // Add cardinality
        if (any.getMinOccurs() != 1) {
            sb.append(" minOccurs=\"").append(any.getMinOccurs()).append("\"");
        }
        if (any.getMaxOccurs() == XsdNode.UNBOUNDED) {
            sb.append(" maxOccurs=\"unbounded\"");
        } else if (any.getMaxOccurs() != 1) {
            sb.append(" maxOccurs=\"").append(any.getMaxOccurs()).append("\"");
        }

        sb.append("/>\n");
    }

    /**
     * Serializes xs:anyAttribute wildcard.
     *
     * @param anyAttr     the XSD anyAttribute
     * @param sb          the string builder
     * @param indentation the indentation string
     */
    private void serializeAnyAttribute(XsdAnyAttribute anyAttr, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:anyAttribute");

        // Add namespace attribute if not default (##any)
        if (anyAttr.getNamespace() != null && !"##any".equals(anyAttr.getNamespace())) {
            sb.append(" namespace=\"").append(escapeXml(anyAttr.getNamespace())).append("\"");
        }

        // Add processContents attribute if not default (strict)
        if (anyAttr.getProcessContents() != null && anyAttr.getProcessContents() != XsdAny.ProcessContents.STRICT) {
            sb.append(" processContents=\"").append(anyAttr.getProcessContents().getValue()).append("\"");
        }

        sb.append("/>\n");
    }

    /**
     * Escapes XML special characters.
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Sets the indentation string (default is 4 spaces).
     */
    public void setIndentString(String indentString) {
        this.indentString = indentString != null ? indentString : DEFAULT_INDENT;
    }

    /**
     * Sets the sort order for serialization.
     * If set to null, the default from application settings will be used.
     *
     * @param sortOrder the sort order to use, or null for default
     */
    public void setSortOrder(XsdSortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * Gets the sort order used for serialization.
     *
     * @return the current sort order, or null if using default from settings
     */
    public XsdSortOrder getSortOrder() {
        return sortOrder;
    }

    /**
     * Gets the effective sort order for serialization.
     * If a sort order has been explicitly set on this instance, that is returned.
     * Otherwise, the default from application settings is used.
     *
     * @return the effective sort order
     */
    private XsdSortOrder getEffectiveSortOrder() {
        if (sortOrder != null) {
            return sortOrder;
        }

        // Get from application settings
        try {
            PropertiesService propertiesService = PropertiesServiceImpl.getInstance();
            String sortOrderStr = propertiesService.getXsdSortOrder();
            return XsdSortOrder.valueOf(sortOrderStr);
        } catch (Exception e) {
            logger.warn("Could not get sort order from settings, using default NAME_BEFORE_TYPE: {}", e.getMessage());
            return XsdSortOrder.NAME_BEFORE_TYPE;
        }
    }

    /**
     * Serializes an XsdSchema to XSD XML with the specified sort order.
     *
     * @param schema    the XSD schema model
     * @param sortOrder the sort order to use for this serialization
     * @return XSD XML string
     */
    public String serialize(XsdSchema schema, XsdSortOrder sortOrder) {
        XsdSortOrder previousSortOrder = this.sortOrder;
        try {
            this.sortOrder = sortOrder;
            return serialize(schema);
        } finally {
            this.sortOrder = previousSortOrder;
        }
    }

    /**
     * Serializes a visual node tree to XSD XML.
     *
     * @param rootNode the root visual node
     * @return XSD XML string
     * @deprecated Use serialize(XsdSchema) instead for model-based serialization.
     */
    @Deprecated
    public String serializeFromVisualNode(VisualNode rootNode) {
        logger.warn("serializeFromVisualNode() is deprecated. Use serialize(XsdSchema) for proper model-based serialization.");

        // This method is kept for backwards compatibility but should not be used
        String sb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"" +
                " elementFormDefault=\"qualified\">\n" +
                "\n" +
                "  <!-- Warning: Serialized from VisualNode (view layer) instead of XsdNode (model layer) -->\n" +
                "\n" +
                "</xs:schema>\n";

        return sb;
    }

    /**
     * Saves the serialized XSD to a file.
     *
     * @param filePath     the file path to save to
     * @param content      the XSD content
     * @param createBackup whether to create a backup first
     * @throws IOException if save fails
     */
    public void saveToFile(Path filePath, String content, boolean createBackup) throws IOException {
        // Create backup if requested and file exists
        if (createBackup && Files.exists(filePath)) {
            createBackup(filePath);
        }

        // Add or update metadata in the content
        ExportMetadataService metadataService = ServiceRegistry.get(ExportMetadataService.class);
        String contentWithMetadata = metadataService.addOrUpdateXmlMetadata(content);

        // Write content
        Files.writeString(filePath, contentWithMetadata);
        logger.info("Saved XSD to: {}", filePath);
    }
}
