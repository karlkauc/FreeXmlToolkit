package org.fxt.freexmltoolkit.controls.v2.editor.statistics;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Analyzes identity constraints (Key, KeyRef, Unique) and assertions in an XSD schema.
 * Collects constraint information and validates referential integrity.
 *
 * @since 2.0
 */
public class XsdIdentityConstraintAnalyzer {

    private static final Logger logger = LogManager.getLogger(XsdIdentityConstraintAnalyzer.class);

    private final XsdSchema schema;

    /**
     * Constraint type enumeration.
     */
    public enum ConstraintType {
        KEY, KEYREF, UNIQUE, ASSERT
    }

    /**
     * Validation status for constraints.
     */
    public enum ValidationStatus {
        VALID,      // Constraint is valid
        WARNING,    // Constraint has warnings (e.g., XPath might not match)
        ERROR       // Constraint has errors (e.g., KeyRef refers to non-existent key)
    }

    /**
     * Information about an identity constraint.
     * @param type The type of constraint
     * @param name The constraint name
     * @param parentElementName The parent element name
     * @param selectorXPath The selector XPath
     * @param fieldXPaths List of field XPaths
     * @param referTo The referred key name (for KeyRef)
     * @param testExpression The test expression (for Assert)
     * @param status The validation status
     * @param statusMessage The validation message
     * @param sourceNode The source node in the schema
     * @param sourceFile The source file path
     */
    public record IdentityConstraintInfo(
            ConstraintType type,
            String name,
            String parentElementName,
            String selectorXPath,
            List<String> fieldXPaths,
            String referTo,           // Only for KeyRef
            String testExpression,    // Only for Assert
            ValidationStatus status,
            String statusMessage,
            XsdNode sourceNode,
            Path sourceFile
    ) {
        /**
         * Creates info for a Key, KeyRef, or Unique constraint.
         */
        public static IdentityConstraintInfo fromIdentityConstraint(
                XsdIdentityConstraint constraint,
                String parentName,
                ValidationStatus status,
                String statusMessage) {

            ConstraintType type;
            String referTo = null;

            if (constraint instanceof XsdKey) {
                type = ConstraintType.KEY;
            } else if (constraint instanceof XsdKeyRef keyRef) {
                type = ConstraintType.KEYREF;
                referTo = keyRef.getRefer();
            } else if (constraint instanceof XsdUnique) {
                type = ConstraintType.UNIQUE;
            } else {
                throw new IllegalArgumentException("Unknown constraint type: " + constraint.getClass());
            }

            String selectorXPath = null;
            XsdSelector selector = constraint.getSelector();
            if (selector != null) {
                selectorXPath = selector.getXpath();
            }

            List<String> fieldXPaths = constraint.getFields().stream()
                    .map(XsdField::getXpath)
                    .filter(Objects::nonNull)
                    .toList();

            Path sourceFile = getSourceFileFromNode(constraint);

            return new IdentityConstraintInfo(
                    type,
                    constraint.getName(),
                    parentName,
                    selectorXPath,
                    fieldXPaths,
                    referTo,
                    null, // testExpression is only for asserts
                    status,
                    statusMessage,
                    constraint,
                    sourceFile
            );
        }

        /**
         * Creates info for an Assert constraint.
         */
        public static IdentityConstraintInfo fromAssert(
                XsdAssert assertion,
                String parentName,
                ValidationStatus status,
                String statusMessage) {

            Path sourceFile = getSourceFileFromNode(assertion);

            return new IdentityConstraintInfo(
                    ConstraintType.ASSERT,
                    assertion.getName(),
                    parentName,
                    null, // No selector for asserts
                    List.of(), // No fields for asserts
                    null, // No refer for asserts
                    assertion.getTest(),
                    status,
                    statusMessage,
                    assertion,
                    sourceFile
            );
        }

        /**
         * Returns true if this is a KeyRef constraint.
         */
        public boolean isKeyRef() {
            return type == ConstraintType.KEYREF;
        }

        /**
         * Returns true if this is an Assert constraint.
         */
        public boolean isAssert() {
            return type == ConstraintType.ASSERT;
        }

        /**
         * Returns a display-friendly type name.
         */
        public String getTypeDisplayName() {
            return switch (type) {
                case KEY -> "Key";
                case KEYREF -> "KeyRef";
                case UNIQUE -> "Unique";
                case ASSERT -> "Assert";
            };
        }

        /**
         * Gets the source file name for display (without full path).
         */
        public String getSourceFileName() {
            if (sourceFile == null) {
                return "main";
            }
            Path fileName = sourceFile.getFileName();
            return fileName != null ? fileName.toString() : "main";
        }

        /**
         * Helper to extract source file from node's source info.
         */
        private static Path getSourceFileFromNode(XsdNode node) {
            if (node == null) {
                return null;
            }
            IncludeSourceInfo sourceInfo = node.getSourceInfo();
            if (sourceInfo != null) {
                return sourceInfo.getSourceFile();
            }
            return null;
        }
    }

    /**
     * Analysis result containing all constraint information.
     * @param keys List of Key constraints
     * @param keyRefs List of KeyRef constraints
     * @param uniques List of Unique constraints
     * @param asserts List of Assert constraints
     * @param totalCount Total number of constraints
     * @param errorCount Number of constraints with errors
     * @param warningCount Number of constraints with warnings
     */
    public record AnalysisResult(
            List<IdentityConstraintInfo> keys,
            List<IdentityConstraintInfo> keyRefs,
            List<IdentityConstraintInfo> uniques,
            List<IdentityConstraintInfo> asserts,
            int totalCount,
            int errorCount,
            int warningCount
    ) {
        /**
         * Returns all constraints as a combined list.
         */
        public List<IdentityConstraintInfo> getAllConstraints() {
            List<IdentityConstraintInfo> all = new ArrayList<>();
            all.addAll(keys);
            all.addAll(keyRefs);
            all.addAll(uniques);
            all.addAll(asserts);
            return all;
        }

        /**
         * Returns constraints grouped by type.
         */
        public Map<ConstraintType, List<IdentityConstraintInfo>> getByType() {
            Map<ConstraintType, List<IdentityConstraintInfo>> map = new EnumMap<>(ConstraintType.class);
            map.put(ConstraintType.KEY, keys);
            map.put(ConstraintType.KEYREF, keyRefs);
            map.put(ConstraintType.UNIQUE, uniques);
            map.put(ConstraintType.ASSERT, asserts);
            return map;
        }
    }

    /**
     * Creates a new analyzer for the given schema.
     *
     * @param schema the XSD schema to analyze (must not be null)
     * @throws NullPointerException if schema is null
     */
    public XsdIdentityConstraintAnalyzer(XsdSchema schema) {
        Objects.requireNonNull(schema, "Schema cannot be null");
        this.schema = schema;
    }

    /**
     * Analyzes the schema and returns all identity constraints.
     *
     * @return the analysis result
     */
    public AnalysisResult analyze() {
        logger.info("Analyzing identity constraints in schema");
        long startTime = System.currentTimeMillis();

        List<IdentityConstraintInfo> keys = new ArrayList<>();
        List<IdentityConstraintInfo> keyRefs = new ArrayList<>();
        List<IdentityConstraintInfo> uniques = new ArrayList<>();
        List<IdentityConstraintInfo> asserts = new ArrayList<>();

        Set<String> visitedIds = new HashSet<>();

        // Collect all key/unique names for KeyRef validation (main schema and imports)
        Set<String> keyAndUniqueNames = new HashSet<>();
        collectKeyAndUniqueNames(schema, keyAndUniqueNames, new HashSet<>());
        for (XsdSchema importedSchema : schema.getImportedSchemas().values()) {
            collectKeyAndUniqueNames(importedSchema, keyAndUniqueNames, new HashSet<>());
        }

        // Traverse and collect constraints from main schema (includes are already inlined)
        traverseAndCollect(schema, keys, keyRefs, uniques, asserts, keyAndUniqueNames, visitedIds);

        // Also traverse imported schemas (they are NOT children of main schema)
        for (Map.Entry<String, XsdSchema> entry : schema.getImportedSchemas().entrySet()) {
            logger.debug("Analyzing identity constraints in imported schema: {}", entry.getKey());
            traverseAndCollect(entry.getValue(), keys, keyRefs, uniques, asserts, keyAndUniqueNames, visitedIds);
        }

        // Calculate counts
        int errorCount = 0;
        int warningCount = 0;

        for (List<IdentityConstraintInfo> list : List.of(keys, keyRefs, uniques, asserts)) {
            for (IdentityConstraintInfo info : list) {
                if (info.status() == ValidationStatus.ERROR) {
                    errorCount++;
                } else if (info.status() == ValidationStatus.WARNING) {
                    warningCount++;
                }
            }
        }

        int totalCount = keys.size() + keyRefs.size() + uniques.size() + asserts.size();

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Identity constraint analysis completed in {}ms: {} total, {} errors, {} warnings",
                duration, totalCount, errorCount, warningCount);

        return new AnalysisResult(
                Collections.unmodifiableList(keys),
                Collections.unmodifiableList(keyRefs),
                Collections.unmodifiableList(uniques),
                Collections.unmodifiableList(asserts),
                totalCount,
                errorCount,
                warningCount
        );
    }

    /**
     * Collects all key and unique constraint names for reference validation.
     */
    private void collectKeyAndUniqueNames(XsdNode node, Set<String> names, Set<String> visitedIds) {
        if (node == null) return;

        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) return;
        if (nodeId != null) visitedIds.add(nodeId);

        if (node instanceof XsdKey key) {
            String name = key.getName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        } else if (node instanceof XsdUnique unique) {
            String name = unique.getName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }

        for (XsdNode child : node.getChildren()) {
            collectKeyAndUniqueNames(child, names, visitedIds);
        }
    }

    /**
     * Traverses the schema and collects constraints.
     */
    private void traverseAndCollect(
            XsdNode node,
            List<IdentityConstraintInfo> keys,
            List<IdentityConstraintInfo> keyRefs,
            List<IdentityConstraintInfo> uniques,
            List<IdentityConstraintInfo> asserts,
            Set<String> keyAndUniqueNames,
            Set<String> visitedIds) {

        if (node == null) return;

        String nodeId = node.getId();
        if (nodeId != null && visitedIds.contains(nodeId)) return;
        if (nodeId != null) visitedIds.add(nodeId);

        // Get parent element name for context
        String parentName = getParentElementName(node);

        // Process Keys
        if (node instanceof XsdKey key) {
            ValidationStatus status = validateIdentityConstraint(key);
            String message = status == ValidationStatus.VALID ? "Valid" : getValidationMessage(key);
            keys.add(IdentityConstraintInfo.fromIdentityConstraint(key, parentName, status, message));
        }
        // Process KeyRefs
        else if (node instanceof XsdKeyRef keyRef) {
            ValidationStatus status = validateKeyRef(keyRef, keyAndUniqueNames);
            String message = getKeyRefValidationMessage(keyRef, keyAndUniqueNames);
            keyRefs.add(IdentityConstraintInfo.fromIdentityConstraint(keyRef, parentName, status, message));
        }
        // Process Uniques
        else if (node instanceof XsdUnique unique) {
            ValidationStatus status = validateIdentityConstraint(unique);
            String message = status == ValidationStatus.VALID ? "Valid" : getValidationMessage(unique);
            uniques.add(IdentityConstraintInfo.fromIdentityConstraint(unique, parentName, status, message));
        }
        // Process Asserts
        else if (node instanceof XsdAssert assertion) {
            ValidationStatus status = validateAssert(assertion);
            String message = status == ValidationStatus.VALID ? "Valid XPath 2.0" : getAssertValidationMessage(assertion);
            asserts.add(IdentityConstraintInfo.fromAssert(assertion, parentName, status, message));
        }

        // Recurse to children
        for (XsdNode child : node.getChildren()) {
            traverseAndCollect(child, keys, keyRefs, uniques, asserts, keyAndUniqueNames, visitedIds);
        }
    }

    /**
     * Gets the parent element name for context.
     */
    private String getParentElementName(XsdNode node) {
        XsdNode parent = node.getParent();
        while (parent != null) {
            if (parent instanceof XsdElement element) {
                return element.getName();
            } else if (parent instanceof XsdComplexType complexType) {
                return complexType.getName();
            }
            parent = parent.getParent();
        }
        return "(root)";
    }

    /**
     * Validates a Key or Unique constraint.
     */
    private ValidationStatus validateIdentityConstraint(XsdIdentityConstraint constraint) {
        // Check if selector exists
        XsdSelector selector = constraint.getSelector();
        if (selector == null || selector.getXpath() == null || selector.getXpath().isBlank()) {
            return ValidationStatus.ERROR;
        }

        // Check if at least one field exists
        List<XsdField> fields = constraint.getFields();
        if (fields.isEmpty()) {
            return ValidationStatus.ERROR;
        }

        // Check if any field has empty XPath
        for (XsdField field : fields) {
            if (field.getXpath() == null || field.getXpath().isBlank()) {
                return ValidationStatus.WARNING;
            }
        }

        return ValidationStatus.VALID;
    }

    /**
     * Validates a KeyRef constraint.
     */
    private ValidationStatus validateKeyRef(XsdKeyRef keyRef, Set<String> keyAndUniqueNames) {
        // First check basic validity
        ValidationStatus basicStatus = validateIdentityConstraint(keyRef);
        if (basicStatus == ValidationStatus.ERROR) {
            return ValidationStatus.ERROR;
        }

        // Check if 'refer' attribute is set
        String refer = keyRef.getRefer();
        if (refer == null || refer.isBlank()) {
            return ValidationStatus.ERROR;
        }

        // Check if referenced key/unique exists
        // Remove namespace prefix if present
        String referName = refer.contains(":") ? refer.substring(refer.indexOf(':') + 1) : refer;
        if (!keyAndUniqueNames.contains(referName) && !keyAndUniqueNames.contains(refer)) {
            return ValidationStatus.ERROR;
        }

        return basicStatus;
    }

    /**
     * Validates an Assert constraint.
     */
    private ValidationStatus validateAssert(XsdAssert assertion) {
        String test = assertion.getTest();
        if (test == null || test.isBlank()) {
            return ValidationStatus.ERROR;
        }

        // Basic syntax check - more detailed validation is done by XsdXPathValidator
        // For now, just check if the expression is not empty
        return ValidationStatus.VALID;
    }

    /**
     * Gets validation message for identity constraint.
     */
    private String getValidationMessage(XsdIdentityConstraint constraint) {
        XsdSelector selector = constraint.getSelector();
        if (selector == null || selector.getXpath() == null || selector.getXpath().isBlank()) {
            return "Missing selector XPath";
        }

        List<XsdField> fields = constraint.getFields();
        if (fields.isEmpty()) {
            return "No field elements defined";
        }

        for (XsdField field : fields) {
            if (field.getXpath() == null || field.getXpath().isBlank()) {
                return "Empty field XPath";
            }
        }

        return "Valid";
    }

    /**
     * Gets validation message for KeyRef.
     */
    private String getKeyRefValidationMessage(XsdKeyRef keyRef, Set<String> keyAndUniqueNames) {
        String refer = keyRef.getRefer();
        if (refer == null || refer.isBlank()) {
            return "Missing 'refer' attribute";
        }

        String referName = refer.contains(":") ? refer.substring(refer.indexOf(':') + 1) : refer;
        if (!keyAndUniqueNames.contains(referName) && !keyAndUniqueNames.contains(refer)) {
            return "Referenced key/unique '" + refer + "' not found";
        }

        return getValidationMessage(keyRef);
    }

    /**
     * Gets validation message for Assert.
     */
    private String getAssertValidationMessage(XsdAssert assertion) {
        String test = assertion.getTest();
        if (test == null || test.isBlank()) {
            return "Missing 'test' expression";
        }
        return "Valid XPath 2.0";
    }
}
