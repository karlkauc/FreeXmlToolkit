package org.fxt.freexmltoolkit.controls;

import java.util.Map;

/**
 * Result record for namespace management operations
 *
 * @param targetNamespace      The target namespace for the XSD schema
 * @param defaultNamespace     The default namespace (xmlns attribute)
 * @param elementFormDefault   Whether local elements should be qualified
 * @param attributeFormDefault Whether local attributes should be qualified
 * @param namespaceMappings    Map of prefix to namespace URI mappings
 */
public record NamespaceResult(
        String targetNamespace,
        String defaultNamespace,
        boolean elementFormDefault,
        boolean attributeFormDefault,
        Map<String, String> namespaceMappings
) {

    /**
     * Creates a default NamespaceResult with common XSD namespaces.
     *
     * @return a new NamespaceResult with standard XSD and XSI namespace mappings
     */
    public static NamespaceResult createDefault() {
        return new NamespaceResult(
                "",  // No target namespace
                "",  // No default namespace
                false,  // elementFormDefault = unqualified
                false,  // attributeFormDefault = unqualified
                Map.of(
                        "xs", "http://www.w3.org/2001/XMLSchema",
                        "xsi", "http://www.w3.org/2001/XMLSchema-instance"
                )
        );
    }

    /**
     * Validates the namespace configuration
     *
     * @return true if the configuration is valid
     */
    public boolean isValid() {
        // Check for duplicate namespace URIs with different prefixes
        var uriToPrefix = new java.util.HashMap<String, String>();
        for (var entry : namespaceMappings.entrySet()) {
            String existingPrefix = uriToPrefix.put(entry.getValue(), entry.getKey());
            if (existingPrefix != null && !existingPrefix.equals(entry.getKey())) {
                // Same URI mapped to different prefixes - potential issue
                return false;
            }
        }

        // Check for empty prefixes or URIs
        return namespaceMappings.entrySet().stream()
                .noneMatch(entry -> entry.getKey().trim().isEmpty() || entry.getValue().trim().isEmpty());
    }

    /**
     * Gets the prefix for a given namespace URI
     *
     * @param namespaceUri The namespace URI to look up
     * @return The prefix, or null if not found
     */
    public String getPrefixForNamespace(String namespaceUri) {
        return namespaceMappings.entrySet().stream()
                .filter(entry -> entry.getValue().equals(namespaceUri))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the namespace URI for a given prefix
     *
     * @param prefix The prefix to look up
     * @return The namespace URI, or null if not found
     */
    public String getNamespaceForPrefix(String prefix) {
        return namespaceMappings.get(prefix);
    }

    /**
     * Checks if a prefix is already in use
     *
     * @param prefix The prefix to check
     * @return true if the prefix exists in the mappings
     */
    public boolean hasPrefixMapping(String prefix) {
        return namespaceMappings.containsKey(prefix);
    }

    /**
     * Checks if a namespace URI is already mapped
     *
     * @param namespaceUri The namespace URI to check
     * @return true if the URI is mapped to any prefix
     */
    public boolean hasNamespaceMapping(String namespaceUri) {
        return namespaceMappings.containsValue(namespaceUri);
    }
}