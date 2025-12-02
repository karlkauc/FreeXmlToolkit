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
        } else if (node instanceof XsdSequence sequence) {
            serializeSequence(sequence, sb, indentation, indent);
        } else if (node instanceof XsdChoice choice) {
            serializeChoice(choice, sb, indentation, indent);
        } else if (node instanceof XsdAll all) {
            serializeAll(all, sb, indentation, indent);
        } else if (node instanceof XsdAttribute attribute) {
            serializeAttribute(attribute, sb, indentation);
        } else if (node instanceof XsdRestriction restriction) {
            serializeRestriction(restriction, sb, indentation, indent);
        } else if (node instanceof XsdExtension extension) {
            serializeExtension(extension, sb, indentation, indent);
        } else if (node instanceof XsdSimpleContent simpleContent) {
            serializeSimpleContent(simpleContent, sb, indentation, indent);
        } else if (node instanceof XsdComplexContent complexContent) {
            serializeComplexContent(complexContent, sb, indentation, indent);
        } else if (node instanceof XsdInclude include) {
            serializeInclude(include, sb, indentation);
        } else if (node instanceof XsdImport xsdImport) {
            serializeImport(xsdImport, sb, indentation);
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
        } else if (node instanceof XsdAny any) {
            serializeAny(any, sb, indentation);
        } else if (node instanceof XsdAnyAttribute anyAttr) {
            serializeAnyAttribute(anyAttr, sb, indentation);
        } else if (node instanceof XsdList list) {
            serializeList(list, sb, indentation, indent);
        } else if (node instanceof XsdUnion union) {
            serializeUnion(union, sb, indentation, indent);
        } else if (node instanceof XsdFacet facet) {
            serializeFacet(facet, sb, indentation);
        } else if (node instanceof XsdAssert xsdAssert) {
            serializeAssert(xsdAssert, sb, indentation);
        } else if (node instanceof XsdAlternative alternative) {
            serializeAlternative(alternative, sb, indentation, indent);
        } else if (node instanceof XsdOpenContent openContent) {
            serializeOpenContent(openContent, sb, indentation, indent);
        } else if (node instanceof XsdRedefine redefine) {
            serializeRedefine(redefine, sb, indentation, indent);
        } else if (node instanceof XsdOverride override) {
            serializeOverride(override, sb, indentation, indent);
        } else if (node instanceof XsdComment comment) {
            serializeComment(comment, sb, indentation);
        } else {
            // For truly unknown node types, log warning and output as comment
            logger.warn("Unknown node type in multi-file serialization: {}", node.getClass().getSimpleName());
            sb.append(indentation).append("<!-- Unknown node type: ")
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

    // ========== Additional serialization methods for all XSD node types ==========

    private void serializeSequence(XsdSequence sequence, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:sequence");
        appendCardinality(sequence, sb);
        sb.append(">\n");

        for (XsdNode child : sequence.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:sequence>\n");
    }

    private void serializeChoice(XsdChoice choice, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:choice");
        appendCardinality(choice, sb);
        sb.append(">\n");

        for (XsdNode child : choice.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:choice>\n");
    }

    private void serializeAll(XsdAll all, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:all");
        appendCardinality(all, sb);
        sb.append(">\n");

        for (XsdNode child : all.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:all>\n");
    }

    private void serializeAttribute(XsdAttribute attribute, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:attribute");

        if (attribute.getRef() != null && !attribute.getRef().isEmpty()) {
            sb.append(" ref=\"").append(escapeXml(attribute.getRef())).append("\"");
            if (attribute.getUse() != null && !attribute.getUse().isEmpty() && !"optional".equals(attribute.getUse())) {
                sb.append(" use=\"").append(escapeXml(attribute.getUse())).append("\"");
            }
            sb.append("/>\n");
            return;
        }

        sb.append(" name=\"").append(escapeXml(attribute.getName())).append("\"");

        if (attribute.getType() != null && !attribute.getType().isEmpty()) {
            sb.append(" type=\"").append(escapeXml(attribute.getType())).append("\"");
        }

        if (attribute.getUse() != null && !attribute.getUse().isEmpty() && !"optional".equals(attribute.getUse())) {
            sb.append(" use=\"").append(escapeXml(attribute.getUse())).append("\"");
        }

        if (attribute.getDefaultValue() != null && !attribute.getDefaultValue().isEmpty()) {
            sb.append(" default=\"").append(escapeXml(attribute.getDefaultValue())).append("\"");
        }

        if (attribute.getFixed() != null && !attribute.getFixed().isEmpty()) {
            sb.append(" fixed=\"").append(escapeXml(attribute.getFixed())).append("\"");
        }

        sb.append("/>\n");
    }

    private void serializeRestriction(XsdRestriction restriction, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:restriction");

        if (restriction.getBase() != null && !restriction.getBase().isEmpty()) {
            sb.append(" base=\"").append(escapeXml(restriction.getBase())).append("\"");
        }

        sb.append(">\n");

        for (XsdNode child : restriction.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:restriction>\n");
    }

    private void serializeExtension(XsdExtension extension, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:extension");

        if (extension.getBase() != null && !extension.getBase().isEmpty()) {
            sb.append(" base=\"").append(escapeXml(extension.getBase())).append("\"");
        }

        if (extension.hasChildren()) {
            sb.append(">\n");
            for (XsdNode child : extension.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:extension>\n");
        } else {
            sb.append("/>\n");
        }
    }

    private void serializeSimpleContent(XsdSimpleContent simpleContent, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:simpleContent>\n");

        for (XsdNode child : simpleContent.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:simpleContent>\n");
    }

    private void serializeComplexContent(XsdComplexContent complexContent, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:complexContent");

        if (complexContent.isMixed()) {
            sb.append(" mixed=\"true\"");
        }

        sb.append(">\n");

        for (XsdNode child : complexContent.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:complexContent>\n");
    }

    private void serializeKey(XsdKey key, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:key");

        if (key.getName() != null && !key.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(key.getName())).append("\"");
        }

        sb.append(">\n");

        for (XsdNode child : key.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:key>\n");
    }

    private void serializeKeyRef(XsdKeyRef keyRef, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:keyref");

        if (keyRef.getName() != null && !keyRef.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(keyRef.getName())).append("\"");
        }

        if (keyRef.getRefer() != null && !keyRef.getRefer().isEmpty()) {
            sb.append(" refer=\"").append(escapeXml(keyRef.getRefer())).append("\"");
        }

        sb.append(">\n");

        for (XsdNode child : keyRef.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:keyref>\n");
    }

    private void serializeUnique(XsdUnique unique, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:unique");

        if (unique.getName() != null && !unique.getName().isEmpty()) {
            sb.append(" name=\"").append(escapeXml(unique.getName())).append("\"");
        }

        sb.append(">\n");

        for (XsdNode child : unique.getChildren()) {
            sb.append(serializeSingleNode(child, indent + 1));
        }

        sb.append(indentation).append("</xs:unique>\n");
    }

    private void serializeSelector(XsdSelector selector, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:selector");

        if (selector.getXpath() != null && !selector.getXpath().isEmpty()) {
            sb.append(" xpath=\"").append(escapeXml(selector.getXpath())).append("\"");
        }

        sb.append("/>\n");
    }

    private void serializeField(XsdField field, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:field");

        if (field.getXpath() != null && !field.getXpath().isEmpty()) {
            sb.append(" xpath=\"").append(escapeXml(field.getXpath())).append("\"");
        }

        sb.append("/>\n");
    }

    private void serializeAny(XsdAny any, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:any");

        if (any.getNamespace() != null && !"##any".equals(any.getNamespace())) {
            sb.append(" namespace=\"").append(escapeXml(any.getNamespace())).append("\"");
        }

        if (any.getProcessContents() != null && any.getProcessContents() != XsdAny.ProcessContents.STRICT) {
            sb.append(" processContents=\"").append(any.getProcessContents().getValue()).append("\"");
        }

        appendCardinality(any, sb);
        sb.append("/>\n");
    }

    private void serializeAnyAttribute(XsdAnyAttribute anyAttr, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:anyAttribute");

        if (anyAttr.getNamespace() != null && !"##any".equals(anyAttr.getNamespace())) {
            sb.append(" namespace=\"").append(escapeXml(anyAttr.getNamespace())).append("\"");
        }

        if (anyAttr.getProcessContents() != null && anyAttr.getProcessContents() != XsdAny.ProcessContents.STRICT) {
            sb.append(" processContents=\"").append(anyAttr.getProcessContents().getValue()).append("\"");
        }

        sb.append("/>\n");
    }

    private void serializeList(XsdList list, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:list");

        if (list.getItemType() != null && !list.getItemType().isEmpty()) {
            sb.append(" itemType=\"").append(escapeXml(list.getItemType())).append("\"");
        }

        if (list.hasChildren()) {
            sb.append(">\n");
            for (XsdNode child : list.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:list>\n");
        } else {
            sb.append("/>\n");
        }
    }

    private void serializeUnion(XsdUnion union, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:union");

        if (union.getMemberTypes() != null && !union.getMemberTypes().isEmpty()) {
            String memberTypesStr = String.join(" ", union.getMemberTypes());
            sb.append(" memberTypes=\"").append(escapeXml(memberTypesStr)).append("\"");
        }

        if (union.hasChildren()) {
            sb.append(">\n");
            for (XsdNode child : union.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:union>\n");
        } else {
            sb.append("/>\n");
        }
    }

    private void serializeFacet(XsdFacet facet, StringBuilder sb, String indentation) {
        if (facet.getFacetType() == null) {
            return;
        }

        String facetName = facet.getFacetType().getXmlName();
        sb.append(indentation).append("<xs:").append(facetName);

        if (facet.getValue() != null) {
            sb.append(" value=\"").append(escapeXml(facet.getValue())).append("\"");
        }

        if (facet.isFixed()) {
            sb.append(" fixed=\"true\"");
        }

        sb.append("/>\n");
    }

    private void serializeAssert(XsdAssert xsdAssert, StringBuilder sb, String indentation) {
        sb.append(indentation).append("<xs:assert");

        if (xsdAssert.getTest() != null && !xsdAssert.getTest().isEmpty()) {
            sb.append(" test=\"").append(escapeXml(xsdAssert.getTest())).append("\"");
        }

        if (xsdAssert.getXpathDefaultNamespace() != null && !xsdAssert.getXpathDefaultNamespace().isEmpty()) {
            sb.append(" xpathDefaultNamespace=\"").append(escapeXml(xsdAssert.getXpathDefaultNamespace())).append("\"");
        }

        sb.append("/>\n");
    }

    private void serializeAlternative(XsdAlternative alternative, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:alternative");

        if (alternative.getTest() != null && !alternative.getTest().isEmpty()) {
            sb.append(" test=\"").append(escapeXml(alternative.getTest())).append("\"");
        }

        if (alternative.getType() != null && !alternative.getType().isEmpty()) {
            sb.append(" type=\"").append(escapeXml(alternative.getType())).append("\"");
        }

        if (alternative.hasChildren()) {
            sb.append(">\n");
            for (XsdNode child : alternative.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:alternative>\n");
        } else {
            sb.append("/>\n");
        }
    }

    private void serializeOpenContent(XsdOpenContent openContent, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:openContent");

        if (openContent.getMode() != null) {
            sb.append(" mode=\"").append(openContent.getMode().getValue()).append("\"");
        }

        if (openContent.hasChildren()) {
            sb.append(">\n");
            for (XsdNode child : openContent.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:openContent>\n");
        } else {
            sb.append("/>\n");
        }
    }

    private void serializeRedefine(XsdRedefine redefine, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:redefine");

        if (redefine.getSchemaLocation() != null && !redefine.getSchemaLocation().isEmpty()) {
            sb.append(" schemaLocation=\"").append(escapeXml(redefine.getSchemaLocation())).append("\"");
        }

        if (redefine.hasChildren()) {
            sb.append(">\n");
            for (XsdNode child : redefine.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:redefine>\n");
        } else {
            sb.append("/>\n");
        }
    }

    private void serializeOverride(XsdOverride override, StringBuilder sb, String indentation, int indent) {
        sb.append(indentation).append("<xs:override");

        if (override.getSchemaLocation() != null && !override.getSchemaLocation().isEmpty()) {
            sb.append(" schemaLocation=\"").append(escapeXml(override.getSchemaLocation())).append("\"");
        }

        if (override.hasChildren()) {
            sb.append(">\n");
            for (XsdNode child : override.getChildren()) {
                sb.append(serializeSingleNode(child, indent + 1));
            }
            sb.append(indentation).append("</xs:override>\n");
        } else {
            sb.append("/>\n");
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
