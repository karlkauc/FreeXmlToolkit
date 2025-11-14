package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
    private String indentString = DEFAULT_INDENT;

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

        // Schema element with namespace
        sb.append("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"");

        // Add target namespace if present
        if (schema.getTargetNamespace() != null && !schema.getTargetNamespace().isEmpty()) {
            sb.append(" targetNamespace=\"").append(escapeXml(schema.getTargetNamespace())).append("\"");
        }

        // Add elementFormDefault
        sb.append(" elementFormDefault=\"qualified\"");

        sb.append(">\n");

        // Serialize children (global elements, types, etc.)
        for (XsdNode child : schema.getChildren()) {
            serializeXsdNode(child, sb, 1);
        }

        sb.append("</xs:schema>\n");

        logger.info("Serialization complete: {} characters", sb.length());
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
        } else {
            logger.warn("Unknown node type: {}", node.getClass().getSimpleName());
        }
    }

    private void serializeElement(XsdElement element, StringBuilder sb, String indentation, int indent) {
        // Synchronize constraint lists to facets before serialization
        synchronizeConstraintsToFacets(element);

        sb.append(indentation).append("<xs:element name=\"").append(escapeXml(element.getName())).append("\"");

        // Add type if specified (for simple content elements)
        if (element.getType() != null && !element.getType().isEmpty() && !element.hasChildren()) {
            sb.append(" type=\"").append(escapeXml(element.getType())).append("\"");
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

        // Check if element has annotation or children
        boolean hasAnnotation = element.getDocumentation() != null || element.getAppinfo() != null;
        if (element.hasChildren() || hasAnnotation) {
            sb.append(">\n");

            // Serialize annotation first (documentation/appinfo)
            if (hasAnnotation) {
                serializeAnnotation(element, sb, indentation + indentString, indent + 1);
            }

            // Serialize children (inline complexType, etc.)
            for (XsdNode child : element.getChildren()) {
                serializeXsdNode(child, sb, indent + 1);
            }

            sb.append(indentation).append("</xs:element>\n");
        } else {
            // Self-closing tag for simple elements without annotation
            sb.append("/>\n");
        }
    }

    /**
     * Synchronizes constraint lists (enumerations, patterns, assertions) from the element
     * back to the XsdRestriction facets in the tree structure before serialization.
     * This ensures that changes made via commands are reflected in the serialized XSD.
     *
     * @param element the element to synchronize
     */
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

        sb.append(">\n");

        // Serialize children (sequence, choice, all, attributes)
        for (XsdNode child : complexType.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:complexType>\n");
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
        sb.append(indentation).append("<xs:attribute name=\"").append(escapeXml(attribute.getName())).append("\"");

        // Add type
        if (attribute.getType() != null && !attribute.getType().isEmpty()) {
            sb.append(" type=\"").append(escapeXml(attribute.getType())).append("\"");
        }

        // Add use (required/optional)
        if (attribute.getUse() != null && !attribute.getUse().isEmpty()) {
            sb.append(" use=\"").append(escapeXml(attribute.getUse())).append("\"");
        }

        sb.append("/>\n");
    }

    private void serializeSimpleType(XsdSimpleType simpleType, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:simpleType");

        // Named simple types have a name attribute
        if (simpleType.getName() != null && !simpleType.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(simpleType.getName())).append("\"");
        }

        sb.append(">\n");

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

        // Add value attribute
        if (facet.getValue() != null && !facet.getValue().isEmpty()) {
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
     *
     * @param node        the node containing documentation/appinfo
     * @param sb          the string builder
     * @param indentation the indentation string
     * @param indent      the indentation level
     */
    private void serializeAnnotation(XsdNode node, StringBuilder sb, String indentation, int indent) {
        String documentation = node.getDocumentation();
        XsdAppInfo appinfo = node.getAppinfo();

        if (documentation == null && (appinfo == null || !appinfo.hasEntries())) {
            return; // Nothing to serialize
        }

        sb.append(indentation).append("<xs:annotation>\n");

        // Serialize documentation
        if (documentation != null) {
            sb.append(indentation).append(indentString).append("<xs:documentation>");
            sb.append(escapeXml(documentation));
            sb.append("</xs:documentation>\n");
        }

        // Serialize structured appinfo entries
        if (appinfo != null && appinfo.hasEntries()) {
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

        // Write content
        Files.writeString(filePath, content);
        logger.info("Saved XSD to: {}", filePath);
    }
}
