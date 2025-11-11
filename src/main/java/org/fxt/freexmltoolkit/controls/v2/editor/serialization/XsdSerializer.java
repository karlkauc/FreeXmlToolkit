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
        } else {
            logger.warn("Unknown node type: {}", node.getClass().getSimpleName());
        }
    }

    private void serializeElement(XsdElement element, StringBuilder sb, String indentation, int indent) {
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

        // Serialize children (restrictions, etc.)
        for (XsdNode child : simpleType.getChildren()) {
            serializeXsdNode(child, sb, indent + 1);
        }

        sb.append(indentation).append("</xs:simpleType>\n");
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
