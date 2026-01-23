package org.fxt.freexmltoolkit.controls.v2.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents an XSD schema (xs:schema) - the root element.
 *
 * @since 2.0
 */
public class XsdSchema extends XsdNode {

    private String targetNamespace;
    private String elementFormDefault = "qualified";
    private String attributeFormDefault = "unqualified";
    private String version;
    private final Map<String, String> namespaces = new HashMap<>();
    private final Map<String, String> additionalAttributes = new HashMap<>();
    private final List<String> leadingComments = new ArrayList<>();

    // Multi-file support properties
    private Path mainSchemaPath;
    private final Map<String, List<XsdNode>> nodesByInclude = new LinkedHashMap<>();
    private final Map<String, XsdInclude> includeMap = new HashMap<>();
    private final Set<Path> allIncludedFiles = new HashSet<>();
    private final Map<String, XsdSchema> importedSchemas = new HashMap<>();

    /**
     * Creates a new XSD schema.
     */
    public XsdSchema() {
        super("schema");
        // Default namespace for XSD
        namespaces.put("xs", "http://www.w3.org/2001/XMLSchema");
    }

    /**
     * Gets the target namespace.
     *
     * @return the target namespace, or null
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * Sets the target namespace.
     *
     * @param targetNamespace the target namespace
     */
    public void setTargetNamespace(String targetNamespace) {
        String oldValue = this.targetNamespace;
        this.targetNamespace = targetNamespace;
        pcs.firePropertyChange("targetNamespace", oldValue, targetNamespace);
    }

    /**
     * Gets the elementFormDefault attribute.
     *
     * @return the elementFormDefault value
     */
    public String getElementFormDefault() {
        return elementFormDefault;
    }

    /**
     * Sets the elementFormDefault attribute.
     *
     * @param elementFormDefault the elementFormDefault value (qualified/unqualified)
     */
    public void setElementFormDefault(String elementFormDefault) {
        String oldValue = this.elementFormDefault;
        this.elementFormDefault = elementFormDefault;
        pcs.firePropertyChange("elementFormDefault", oldValue, elementFormDefault);
    }

    /**
     * Gets the attributeFormDefault attribute.
     *
     * @return the attributeFormDefault value
     */
    public String getAttributeFormDefault() {
        return attributeFormDefault;
    }

    /**
     * Sets the attributeFormDefault attribute.
     *
     * @param attributeFormDefault the attributeFormDefault value (qualified/unqualified)
     */
    public void setAttributeFormDefault(String attributeFormDefault) {
        String oldValue = this.attributeFormDefault;
        this.attributeFormDefault = attributeFormDefault;
        pcs.firePropertyChange("attributeFormDefault", oldValue, attributeFormDefault);
    }

    /**
     * Gets the schema version.
     *
     * @return the version, or null
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the schema version.
     *
     * @param version the version (e.g., "4.2.10")
     */
    public void setVersion(String version) {
        String oldValue = this.version;
        this.version = version;
        pcs.firePropertyChange("version", oldValue, version);
    }

    /**
     * Gets all additional attributes (e.g., vc:minVersion).
     *
     * @return the additional attributes map (name -> value)
     */
    public Map<String, String> getAdditionalAttributes() {
        return new HashMap<>(additionalAttributes);
    }

    /**
     * Sets an additional attribute.
     *
     * @param name  the attribute name (may include prefix like "vc:minVersion")
     * @param value the attribute value
     */
    public void setAdditionalAttribute(String name, String value) {
        additionalAttributes.put(name, value);
        pcs.firePropertyChange("additionalAttributes", null, new HashMap<>(additionalAttributes));
    }

    /**
     * Removes an additional attribute.
     *
     * @param name the attribute name to remove
     */
    public void removeAdditionalAttribute(String name) {
        additionalAttributes.remove(name);
        pcs.firePropertyChange("additionalAttributes", null, new HashMap<>(additionalAttributes));
    }

    /**
     * Gets all leading comments (comments before the schema element).
     *
     * @return list of comment texts
     */
    public List<String> getLeadingComments() {
        return new ArrayList<>(leadingComments);
    }

    /**
     * Adds a leading comment.
     *
     * @param comment the comment text (without &lt;!-- and --&gt;)
     */
    public void addLeadingComment(String comment) {
        leadingComments.add(comment);
    }

    /**
     * Clears all leading comments.
     */
    public void clearLeadingComments() {
        leadingComments.clear();
    }

    /**
     * Gets all namespace mappings.
     *
     * @return the namespace map (prefix -> URI)
     */
    public Map<String, String> getNamespaces() {
        return new HashMap<>(namespaces);
    }

    /**
     * Adds a namespace mapping.
     *
     * @param prefix the namespace prefix
     * @param uri    the namespace URI
     */
    public void addNamespace(String prefix, String uri) {
        namespaces.put(prefix, uri);
        pcs.firePropertyChange("namespaces", null, new HashMap<>(namespaces));
    }

    /**
     * Removes a namespace mapping.
     *
     * @param prefix the namespace prefix to remove
     */
    public void removeNamespace(String prefix) {
        namespaces.remove(prefix);
        pcs.firePropertyChange("namespaces", null, new HashMap<>(namespaces));
    }

    // ========== Multi-File Support Methods ==========

    /**
     * Gets the main schema file path.
     *
     * @return the main schema file path, or null if not set
     */
    public Path getMainSchemaPath() {
        return mainSchemaPath;
    }

    /**
     * Sets the main schema file path.
     *
     * @param mainSchemaPath the absolute path to the main schema file
     */
    public void setMainSchemaPath(Path mainSchemaPath) {
        Path oldValue = this.mainSchemaPath;
        this.mainSchemaPath = mainSchemaPath;
        pcs.firePropertyChange("mainSchemaPath", oldValue, mainSchemaPath);
    }

    /**
     * Registers a node as belonging to a specific include file.
     *
     * @param node    the node to register
     * @param include the XsdInclude that brought this node in
     */
    public void registerNodeForInclude(XsdNode node, XsdInclude include) {
        if (include == null || node == null) return;

        String location = include.getSchemaLocation();
        if (location == null) return;

        nodesByInclude.computeIfAbsent(location, k -> new ArrayList<>()).add(node);
        includeMap.put(location, include);

        if (include.getResolvedPath() != null) {
            allIncludedFiles.add(include.getResolvedPath());
        }
    }

    /**
     * Gets all nodes that came from a specific include file.
     *
     * @param schemaLocation the schemaLocation attribute of the include
     * @return list of nodes from that include (never null)
     */
    public List<XsdNode> getNodesFromInclude(String schemaLocation) {
        return nodesByInclude.getOrDefault(schemaLocation, Collections.emptyList());
    }

    /**
     * Gets the XsdInclude node for a given schema location.
     *
     * @param schemaLocation the schemaLocation attribute
     * @return the XsdInclude node, or null if not found
     */
    public XsdInclude getIncludeByLocation(String schemaLocation) {
        return includeMap.get(schemaLocation);
    }

    /**
     * Gets all include schema locations registered in this schema.
     *
     * @return unmodifiable set of schema locations
     */
    public Set<String> getIncludeLocations() {
        return Collections.unmodifiableSet(nodesByInclude.keySet());
    }

    /**
     * Gets all XsdInclude nodes in this schema.
     *
     * @return unmodifiable collection of XsdInclude nodes
     */
    public java.util.Collection<XsdInclude> getAllIncludes() {
        return Collections.unmodifiableCollection(includeMap.values());
    }

    /**
     * Gets all files involved in this schema (main + all includes).
     *
     * @return set of all file paths
     */
    public Set<Path> getAllInvolvedFiles() {
        Set<Path> files = new HashSet<>(allIncludedFiles);
        if (mainSchemaPath != null) {
            files.add(mainSchemaPath);
        }
        return files;
    }

    /**
     * Checks if this schema has any includes.
     *
     * @return true if at least one xs:include is present
     */
    public boolean hasIncludes() {
        return !includeMap.isEmpty();
    }

    /**
     * Groups all direct child nodes by their source file.
     *
     * @return map of file path to list of nodes from that file
     */
    public Map<Path, List<XsdNode>> groupChildrenBySourceFile() {
        Map<Path, List<XsdNode>> result = new LinkedHashMap<>();

        // Ensure main schema is first
        if (mainSchemaPath != null) {
            result.put(mainSchemaPath, new ArrayList<>());
        }

        for (XsdNode child : getChildren()) {
            Path sourceFile = child.getSourceFile();
            if (sourceFile == null) {
                sourceFile = mainSchemaPath;
            }
            result.computeIfAbsent(sourceFile, k -> new ArrayList<>()).add(child);
        }

        return result;
    }

    /**
     * Clears all include tracking data.
     * Call this when reloading the schema.
     */
    public void clearIncludeTracking() {
        nodesByInclude.clear();
        includeMap.clear();
        allIncludedFiles.clear();
        importedSchemas.clear();
    }

    /**
     * Gets all imported schemas (from xs:import statements).
     *
     * @return map of namespace/location to imported schema
     */
    public Map<String, XsdSchema> getImportedSchemas() {
        return Collections.unmodifiableMap(importedSchemas);
    }

    /**
     * Adds an imported schema.
     *
     * @param key the namespace or schemaLocation
     * @param schema the imported schema
     */
    public void addImportedSchema(String key, XsdSchema schema) {
        importedSchemas.put(key, schema);
    }

    /**
     * Checks if this schema has any imports.
     *
     * @return true if at least one xs:import with a loaded schema is present
     */
    public boolean hasImports() {
        return !importedSchemas.isEmpty();
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.SCHEMA;
    }

    /**
     * Detects the XSD version used in this schema.
     * Returns "1.1" if any XSD 1.1 features are detected, otherwise "1.0".
     * <p>
     * XSD 1.1 detection methods:
     * - vc:minVersion attribute on the schema element (explicit declaration)
     * - xs:assert (assertions in complexType)
     * - xs:override
     * - xs:openContent / xs:defaultOpenContent
     * - xs:alternative (conditional type assignment)
     * - assertion facet in simpleType restrictions
     *
     * @return "1.1" if XSD 1.1 features are present, "1.0" otherwise
     */
    public String detectXsdVersion() {
        // First check for explicit vc:minVersion declaration
        String minVersion = additionalAttributes.get("vc:minVersion");
        if (minVersion == null) {
            // Also check without prefix (in case it was stored differently)
            minVersion = additionalAttributes.get("minVersion");
        }
        if (minVersion != null && minVersion.contains("1.1")) {
            return "1.1";
        }

        // Fall back to feature detection
        return hasXsd11Features(this) ? "1.1" : "1.0";
    }

    /**
     * Recursively checks if a node or its children contain XSD 1.1 features.
     *
     * @param node the node to check
     * @return true if XSD 1.1 features are found
     */
    private boolean hasXsd11Features(XsdNode node) {
        // Check if this node is an XSD 1.1 feature
        if (node instanceof XsdAssert ||
            node instanceof XsdOverride ||
            node instanceof XsdOpenContent ||
            node instanceof XsdAlternative) {
            return true;
        }

        // Check for assertion facet in restrictions
        if (node instanceof XsdFacet facet) {
            if (facet.getFacetType() == XsdFacetType.ASSERTION) {
                return true;
            }
        }

        // Recursively check children
        for (XsdNode child : node.getChildren()) {
            if (hasXsd11Features(child)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Schema name is always "schema", suffix is not applied
        XsdSchema copy = new XsdSchema();

        // Copy XsdSchema-specific properties
        copy.setTargetNamespace(this.targetNamespace);
        copy.setElementFormDefault(this.elementFormDefault);
        copy.setAttributeFormDefault(this.attributeFormDefault);
        copy.setVersion(this.version);

        // Copy namespaces
        for (var entry : this.namespaces.entrySet()) {
            copy.addNamespace(entry.getKey(), entry.getValue());
        }

        // Copy additional attributes
        for (var entry : this.additionalAttributes.entrySet()) {
            copy.setAdditionalAttribute(entry.getKey(), entry.getValue());
        }

        // Copy leading comments
        for (String comment : this.leadingComments) {
            copy.addLeadingComment(comment);
        }

        // Copy multi-file support properties
        // Note: nodesByInclude and includeMap are NOT copied as they are runtime data
        // that gets populated during parsing. mainSchemaPath is copied.
        copy.mainSchemaPath = this.mainSchemaPath;

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
