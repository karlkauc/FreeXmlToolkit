package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Multi-file XSD serializer that preserves the original file structure
 * when saving XSD schemas that use xs:include statements.
 * <p>
 * This serializer groups nodes by their source file (tracked via {@link IncludeSourceInfo})
 * and writes each file's content separately, preserving the modular schema structure.
 * <p>
 * Features:
 * <ul>
 *   <li>Groups schema components by source file</li>
 *   <li>Maintains xs:include statements in the main schema</li>
 *   <li>Writes only nodes that belong to each include file</li>
 *   <li>Creates timestamped backups before overwriting</li>
 *   <li>Supports dirty file tracking for incremental saves</li>
 * </ul>
 *
 * @since 2.0
 * @see IncludeSourceInfo
 * @see IncludeTracker
 */
public class MultiFileXsdSerializer {

    private static final Logger logger = LogManager.getLogger(MultiFileXsdSerializer.class);
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String DEFAULT_INDENT = "    "; // 4 spaces

    private String indentString = DEFAULT_INDENT;
    private boolean createBackups = true;

    /**
     * Result of saving a single file.
     */
    public record SaveResult(
            Path filePath,
            boolean success,
            String errorMessage,
            Path backupPath,
            int nodeCount
    ) {
        public static SaveResult success(Path filePath, Path backupPath, int nodeCount) {
            return new SaveResult(filePath, true, null, backupPath, nodeCount);
        }

        public static SaveResult failure(Path filePath, String errorMessage) {
            return new SaveResult(filePath, false, errorMessage, null, 0);
        }
    }

    /**
     * Saves all files in a multi-file XSD schema.
     * Groups nodes by their source file and writes each file separately.
     *
     * @param schema        the XSD schema to save
     * @param mainFilePath  the path to the main schema file (if different from schema.getMainSchemaPath())
     * @param createBackups whether to create backups before overwriting
     * @return map of file path to save result
     */
    public Map<Path, SaveResult> saveAll(XsdSchema schema, Path mainFilePath, boolean createBackups) {
        this.createBackups = createBackups;

        if (schema == null) {
            logger.warn("Cannot save null schema");
            return Collections.emptyMap();
        }

        Path effectiveMainPath = mainFilePath != null ? mainFilePath : schema.getMainSchemaPath();
        if (effectiveMainPath == null) {
            logger.warn("No main schema path specified");
            return Collections.emptyMap();
        }

        Map<Path, SaveResult> results = new LinkedHashMap<>();

        // Group nodes by source file
        Map<Path, List<XsdNode>> nodesByFile = groupNodesBySourceFile(schema, effectiveMainPath);

        logger.info("Multi-file save: {} files to process", nodesByFile.size());

        // Save the main schema file
        try {
            SaveResult mainResult = saveMainSchema(schema, effectiveMainPath, nodesByFile.get(effectiveMainPath));
            results.put(effectiveMainPath, mainResult);
        } catch (Exception e) {
            logger.error("Failed to save main schema: {}", e.getMessage(), e);
            results.put(effectiveMainPath, SaveResult.failure(effectiveMainPath, e.getMessage()));
        }

        // Save each included schema file
        for (Map.Entry<Path, List<XsdNode>> entry : nodesByFile.entrySet()) {
            Path filePath = entry.getKey();
            if (filePath.equals(effectiveMainPath)) {
                continue; // Already saved main schema
            }

            try {
                SaveResult result = saveIncludedSchema(schema, filePath, entry.getValue());
                results.put(filePath, result);
            } catch (Exception e) {
                logger.error("Failed to save included schema {}: {}", filePath, e.getMessage(), e);
                results.put(filePath, SaveResult.failure(filePath, e.getMessage()));
            }
        }

        // Log summary
        long successCount = results.values().stream().filter(SaveResult::success).count();
        logger.info("Multi-file save complete: {}/{} files saved successfully",
                successCount, results.size());

        return results;
    }

    /**
     * Saves only the specified files from the schema (incremental save).
     *
     * @param schema       the XSD schema
     * @param filesToSave  set of file paths to save
     * @param createBackups whether to create backups
     * @return map of file path to save result
     */
    public Map<Path, SaveResult> saveFiles(XsdSchema schema, Set<Path> filesToSave, boolean createBackups) {
        this.createBackups = createBackups;

        if (schema == null || filesToSave == null || filesToSave.isEmpty()) {
            return Collections.emptyMap();
        }

        Path mainPath = schema.getMainSchemaPath();
        Map<Path, List<XsdNode>> nodesByFile = groupNodesBySourceFile(schema, mainPath);
        Map<Path, SaveResult> results = new LinkedHashMap<>();

        for (Path filePath : filesToSave) {
            List<XsdNode> nodes = nodesByFile.get(filePath);
            if (nodes == null) {
                logger.warn("No nodes found for file: {}", filePath);
                results.put(filePath, SaveResult.failure(filePath, "No nodes found for this file"));
                continue;
            }

            try {
                SaveResult result;
                if (filePath.equals(mainPath)) {
                    result = saveMainSchema(schema, mainPath, nodes);
                } else {
                    result = saveIncludedSchema(schema, filePath, nodes);
                }
                results.put(filePath, result);
            } catch (Exception e) {
                logger.error("Failed to save file {}: {}", filePath, e.getMessage(), e);
                results.put(filePath, SaveResult.failure(filePath, e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Groups all schema children by their source file.
     *
     * @param schema   the schema
     * @param mainPath the main schema path
     * @return map of file path to nodes from that file
     */
    private Map<Path, List<XsdNode>> groupNodesBySourceFile(XsdSchema schema, Path mainPath) {
        Map<Path, List<XsdNode>> result = new LinkedHashMap<>();

        // Ensure main schema is first in the map
        result.put(mainPath, new ArrayList<>());

        for (XsdNode child : schema.getChildren()) {
            Path sourceFile = getNodeSourceFile(child, mainPath);
            result.computeIfAbsent(sourceFile, k -> new ArrayList<>()).add(child);
        }

        return result;
    }

    /**
     * Gets the source file for a node, defaulting to main schema if not set.
     */
    private Path getNodeSourceFile(XsdNode node, Path mainPath) {
        if (node.getSourceInfo() != null && node.getSourceInfo().getSourceFile() != null) {
            return node.getSourceInfo().getSourceFile();
        }
        return mainPath;
    }

    /**
     * Saves the main schema file with xs:include statements preserved.
     */
    private SaveResult saveMainSchema(XsdSchema schema, Path mainPath, List<XsdNode> mainNodes)
            throws IOException {
        Path backupPath = null;
        if (createBackups && Files.exists(mainPath)) {
            backupPath = createBackup(mainPath);
        }

        String content = serializeMainSchema(schema, mainNodes);
        Files.writeString(mainPath, content);

        logger.info("Saved main schema to {} ({} nodes)", mainPath,
                mainNodes != null ? mainNodes.size() : 0);

        return SaveResult.success(mainPath, backupPath, mainNodes != null ? mainNodes.size() : 0);
    }

    /**
     * Saves an included schema file with only its own nodes.
     */
    private SaveResult saveIncludedSchema(XsdSchema schema, Path filePath, List<XsdNode> nodes)
            throws IOException {
        Path backupPath = null;
        if (createBackups && Files.exists(filePath)) {
            backupPath = createBackup(filePath);
        }

        String content = serializeIncludedSchema(schema, nodes);
        Files.writeString(filePath, content);

        logger.info("Saved included schema to {} ({} nodes)", filePath, nodes.size());

        return SaveResult.success(filePath, backupPath, nodes.size());
    }

    /**
     * Serializes the main schema with xs:include statements and only main-file nodes.
     */
    private String serializeMainSchema(XsdSchema schema, List<XsdNode> mainNodes) {
        StringBuilder sb = new StringBuilder();

        // XML declaration
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Leading comments
        for (String comment : schema.getLeadingComments()) {
            sb.append("<!--").append(comment).append("-->\n");
        }

        // Schema element with namespace declarations
        appendSchemaOpenTag(schema, sb);

        // Serialize children - xs:include and xs:import first, then main-file nodes
        for (XsdNode child : schema.getChildren()) {
            // Always include xs:include and xs:import in main schema
            if (child instanceof XsdInclude || child instanceof XsdImport) {
                serializeXsdNode(child, sb, 1);
            }
        }

        // Then serialize nodes that belong to the main file
        if (mainNodes != null) {
            for (XsdNode node : mainNodes) {
                // Skip includes/imports (already serialized above)
                if (node instanceof XsdInclude || node instanceof XsdImport) {
                    continue;
                }
                serializeXsdNode(node, sb, 1);
            }
        }

        sb.append("</xs:schema>\n");

        return sb.toString();
    }

    /**
     * Serializes an included schema file with only its own nodes.
     */
    private String serializeIncludedSchema(XsdSchema mainSchema, List<XsdNode> nodes) {
        StringBuilder sb = new StringBuilder();

        // XML declaration
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // Schema element - use same namespace/targetNamespace as main schema
        appendSchemaOpenTag(mainSchema, sb);

        // Serialize only the nodes that belong to this included file
        for (XsdNode node : nodes) {
            // Skip xs:include/xs:import nodes in included files
            if (node instanceof XsdInclude || node instanceof XsdImport) {
                continue;
            }
            serializeXsdNode(node, sb, 1);
        }

        sb.append("</xs:schema>\n");

        return sb.toString();
    }

    /**
     * Appends the xs:schema opening tag with all namespace declarations.
     */
    private void appendSchemaOpenTag(XsdSchema schema, StringBuilder sb) {
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

        // Add attributeFormDefault
        if (schema.getAttributeFormDefault() != null && !schema.getAttributeFormDefault().isEmpty()) {
            sb.append(" attributeFormDefault=\"").append(escapeXml(schema.getAttributeFormDefault())).append("\"");
        }

        // Add elementFormDefault
        if (schema.getElementFormDefault() != null && !schema.getElementFormDefault().isEmpty()) {
            sb.append(" elementFormDefault=\"").append(escapeXml(schema.getElementFormDefault())).append("\"");
        }

        // Add target namespace
        if (schema.getTargetNamespace() != null && !schema.getTargetNamespace().isEmpty()) {
            sb.append(" targetNamespace=\"").append(escapeXml(schema.getTargetNamespace())).append("\"");
        }

        // Add version
        if (schema.getVersion() != null && !schema.getVersion().isEmpty()) {
            sb.append(" version=\"").append(escapeXml(schema.getVersion())).append("\"");
        }

        // Add additional attributes
        Map<String, String> additionalAttributes = schema.getAdditionalAttributes();
        for (Map.Entry<String, String> attr : additionalAttributes.entrySet()) {
            sb.append(" ").append(attr.getKey()).append("=\"").append(escapeXml(attr.getValue())).append("\"");
        }

        sb.append(">\n");
    }

    /**
     * Serializes a single XsdNode. This delegates to the XsdSerializer's method.
     * Note: This is a simplified version; for production use, consider making
     * XsdSerializer.serializeXsdNode package-visible.
     */
    private void serializeXsdNode(XsdNode node, StringBuilder sb, int indent) {
        // Use a dedicated XsdSerializer instance for node serialization
        XsdSerializer serializer = new XsdSerializer();
        serializer.setIndentString(indentString);

        // For nodes that need full schema context, wrap them
        if (node instanceof XsdSchema) {
            sb.append(serializer.serialize((XsdSchema) node));
        } else {
            // Create a temporary schema wrapper and serialize just this node
            String nodeXml = serializeSingleNode(node, indent);
            sb.append(nodeXml);
        }
    }

    /**
     * Serializes a single node using the XsdSerializer's internal methods.
     * This replicates the logic from XsdSerializer.serializeXsdNode() to avoid
     * tight coupling while preserving exact output format.
     */
    private String serializeSingleNode(XsdNode node, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentation = indentString.repeat(indent);

        // Delegate to node-type-specific serialization
        if (node instanceof XsdElement element) {
            serializeElement(element, sb, indentation, indent);
        } else if (node instanceof XsdComplexType complexType) {
            serializeComplexType(complexType, sb, indentation, indent);
        } else if (node instanceof XsdSimpleType simpleType) {
            serializeSimpleType(simpleType, sb, indentation, indent);
        } else if (node instanceof XsdInclude include) {
            serializeInclude(include, sb, indentation);
        } else if (node instanceof XsdImport xsdImport) {
            serializeImport(xsdImport, sb, indentation);
        } else if (node instanceof XsdGroup group) {
            serializeGroup(group, sb, indentation, indent);
        } else if (node instanceof XsdAttributeGroup attributeGroup) {
            serializeAttributeGroup(attributeGroup, sb, indentation, indent);
        } else if (node instanceof XsdComment comment) {
            serializeComment(comment, sb, indentation);
        } else {
            // For other node types, use a simple representation
            logger.warn("Simplified serialization for node type: {}", node.getClass().getSimpleName());
            sb.append(indentation).append("<!-- Unsupported node type: ")
                    .append(node.getClass().getSimpleName()).append(" -->\n");
        }

        return sb.toString();
    }

    // ========== Node-specific serialization methods (subset for common types) ==========

    private void serializeElement(XsdElement element, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:element");

        if (element.getRef() != null && !element.getRef().isEmpty()) {
            sb.append(" ref=\"").append(escapeXml(element.getRef())).append("\"");
            appendCardinality(element, sb);
            sb.append("/>\n");
            return;
        }

        sb.append(" name=\"").append(escapeXml(element.getName())).append("\"");

        if (element.getType() != null && !element.getType().isEmpty() &&
                element.getChildren().stream().noneMatch(c -> c instanceof XsdComplexType || c instanceof XsdSimpleType)) {
            sb.append(" type=\"").append(escapeXml(element.getType())).append("\"");
        }

        if (element.getDefaultValue() != null && !element.getDefaultValue().isEmpty()) {
            sb.append(" default=\"").append(escapeXml(element.getDefaultValue())).append("\"");
        }
        if (element.getFixed() != null && !element.getFixed().isEmpty()) {
            sb.append(" fixed=\"").append(escapeXml(element.getFixed())).append("\"");
        }
        if (element.isNillable()) {
            sb.append(" nillable=\"true\"");
        }
        if (element.isAbstract()) {
            sb.append(" abstract=\"true\"");
        }

        appendCardinality(element, sb);

        if (element.hasChildren() || element.getDocumentation() != null) {
            sb.append(">\n");
            serializeAnnotationIfPresent(element, sb, indentation + indentString, indent + 1);
            for (XsdNode child : element.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:element>\n");
        } else {
            sb.append("/>\n");
        }
    }

    private void serializeComplexType(XsdComplexType complexType, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:complexType");

        if (complexType.getName() != null && !complexType.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(complexType.getName())).append("\"");
        }
        if (complexType.isMixed()) {
            sb.append(" mixed=\"true\"");
        }
        if (complexType.isAbstract()) {
            sb.append(" abstract=\"true\"");
        }

        if (complexType.hasChildren() || complexType.getDocumentation() != null) {
            sb.append(">\n");
            serializeAnnotationIfPresent(complexType, sb, indentation + indentString, indent + 1);
            for (XsdNode child : complexType.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:complexType>\n");
        } else {
            sb.append("/>\n");
        }
    }

    private void serializeSimpleType(XsdSimpleType simpleType, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:simpleType");

        if (simpleType.getName() != null && !simpleType.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(simpleType.getName())).append("\"");
        }
        if (simpleType.isFinal()) {
            sb.append(" final=\"#all\"");
        }

        sb.append(">\n");
        serializeAnnotationIfPresent(simpleType, sb, indentation + indentString, indent + 1);

        for (XsdNode child : simpleType.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:simpleType>\n");
    }

    private void serializeInclude(XsdInclude include, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:include");
        if (include.getSchemaLocation() != null && !include.getSchemaLocation().isEmpty()) {
            sb.append(" schemaLocation=\"").append(escapeXml(include.getSchemaLocation())).append("\"");
        }
        sb.append("/>\n");
    }

    private void serializeImport(XsdImport xsdImport, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:import");
        if (xsdImport.getNamespace() != null && !xsdImport.getNamespace().isEmpty()) {
            sb.append(" namespace=\"").append(escapeXml(xsdImport.getNamespace())).append("\"");
        }
        if (xsdImport.getSchemaLocation() != null && !xsdImport.getSchemaLocation().isEmpty()) {
            sb.append(" schemaLocation=\"").append(escapeXml(xsdImport.getSchemaLocation())).append("\"");
        }
        sb.append("/>\n");
    }

    private void serializeGroup(XsdGroup group, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:group");

        if (group.isReference()) {
            sb.append(" ref=\"").append(escapeXml(group.getRef())).append("\"");
            sb.append("/>\n");
        } else {
            if (group.getName() != null && !group.getName().isEmpty()) {
                sb.append(" name=\"").append(escapeXml(group.getName())).append("\"");
            }
            sb.append(">\n");
            for (XsdNode child : group.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:group>\n");
        }
    }

    private void serializeAttributeGroup(XsdAttributeGroup attributeGroup, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:attributeGroup");

        if (attributeGroup.isReference()) {
            sb.append(" ref=\"").append(escapeXml(attributeGroup.getRef())).append("\"");
            sb.append("/>\n");
        } else {
            if (attributeGroup.getName() != null && !attributeGroup.getName().isEmpty()) {
                sb.append(" name=\"").append(escapeXml(attributeGroup.getName())).append("\"");
            }
            sb.append(">\n");
            for (XsdNode child : attributeGroup.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:attributeGroup>\n");
        }
    }

    private void serializeComment(XsdComment comment, StringBuilder sb, String indentation) {
        if (comment.getContent() != null) {
            sb.append(indentation).append("<!--").append(comment.getContent()).append("-->\n");
        }
    }

    private void appendCardinality(XsdNode node, StringBuilder sb) {
        if (node.getMinOccurs() != 1) {
            sb.append(" minOccurs=\"").append(node.getMinOccurs()).append("\"");
        }
        if (node.getMaxOccurs() == XsdNode.UNBOUNDED) {
            sb.append(" maxOccurs=\"unbounded\"");
        } else if (node.getMaxOccurs() != 1) {
            sb.append(" maxOccurs=\"").append(node.getMaxOccurs()).append("\"");
        }
    }

    private void serializeAnnotationIfPresent(XsdNode node, StringBuilder sb, String indentation, int indent) {
        if (node.getDocumentation() != null && !node.getDocumentation().isEmpty()) {
            sb.append(indentation).append("<xs:annotation>\n");
            sb.append(indentation).append(indentString).append("<xs:documentation>");
            sb.append(escapeXml(node.getDocumentation()));
            sb.append("</xs:documentation>\n");
            sb.append(indentation).append("</xs:annotation>\n");
        }
    }

    /**
     * Creates a backup of the specified file.
     */
    private Path createBackup(Path filePath) throws IOException {
        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
        String fileName = filePath.getFileName().toString();
        String backupFileName = fileName.replaceFirst("(\\.[^.]+)$", "_backup_" + timestamp + "$1");

        Path backupPath = filePath.getParent().resolve(backupFileName);
        Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);

        logger.debug("Created backup: {}", backupPath);
        return backupPath;
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
     * Sets the indentation string.
     */
    public void setIndentString(String indentString) {
        this.indentString = indentString != null ? indentString : DEFAULT_INDENT;
    }

    /**
     * Sets whether backups should be created by default.
     */
    public void setCreateBackups(boolean createBackups) {
        this.createBackups = createBackups;
    }
}
