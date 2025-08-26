package org.fxt.freexmltoolkit.controls.intellisense;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Namespace resolver for XML documents with IntelliSense support.
 * Handles namespace declarations, prefix mappings, and context-aware resolution.
 */
public class NamespaceResolver {

    // Namespace declaration patterns
    private static final Pattern NAMESPACE_DECL_PATTERN =
            Pattern.compile("xmlns(?::(\\w+))?\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final Pattern DEFAULT_NAMESPACE_PATTERN =
            Pattern.compile("xmlns\\s*=\\s*[\"']([^\"']+)[\"']");

    private static final Pattern ELEMENT_WITH_PREFIX_PATTERN =
            Pattern.compile("<(\\w+):(\\w+)");

    // Namespace mappings
    private final Map<String, String> prefixToNamespace = new ConcurrentHashMap<>();
    private final Map<String, String> namespaceToPrefix = new ConcurrentHashMap<>();

    // Default namespace
    private volatile String defaultNamespace = null;

    // Scope stack for nested elements
    private final Deque<NamespaceScope> scopeStack = new ArrayDeque<>();

    // Common namespace prefixes
    private static final Map<String, String> COMMON_NAMESPACES = Map.of(
            "xs", "http://www.w3.org/2001/XMLSchema",
            "xsi", "http://www.w3.org/2001/XMLSchema-instance",
            "xml", "http://www.w3.org/XML/1998/namespace",
            "xmlns", "http://www.w3.org/2000/xmlns/",
            "xsl", "http://www.w3.org/1999/XSL/Transform",
            "soap", "http://schemas.xmlsoap.org/soap/envelope/",
            "wsdl", "http://schemas.xmlsoap.org/wsdl/"
    );

    public NamespaceResolver() {
        // Initialize with common namespaces
        prefixToNamespace.putAll(COMMON_NAMESPACES);
        COMMON_NAMESPACES.forEach((prefix, namespace) ->
                namespaceToPrefix.put(namespace, prefix));
    }

    /**
     * Parse XML content and extract namespace declarations
     */
    public void parseNamespaceDeclarations(String xmlContent) {
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return;
        }

        // Clear existing mappings (except common ones)
        clearUserDefinedNamespaces();

        // Find namespace declarations
        Matcher matcher = NAMESPACE_DECL_PATTERN.matcher(xmlContent);
        while (matcher.find()) {
            String prefix = matcher.group(1); // Can be null for default namespace
            String namespace = matcher.group(2);

            if (prefix != null) {
                // Prefixed namespace declaration: xmlns:prefix="namespace"
                addNamespaceMapping(prefix, namespace);
            } else {
                // Default namespace declaration: xmlns="namespace"
                setDefaultNamespace(namespace);
            }
        }
    }

    /**
     * Add namespace mapping
     */
    public void addNamespaceMapping(String prefix, String namespace) {
        if (prefix != null && namespace != null) {
            prefixToNamespace.put(prefix, namespace);
            namespaceToPrefix.put(namespace, prefix);
        }
    }

    /**
     * Set default namespace
     */
    public void setDefaultNamespace(String namespace) {
        this.defaultNamespace = namespace;
    }

    /**
     * Get namespace for prefix
     */
    public String getNamespaceForPrefix(String prefix) {
        if (prefix == null) {
            return defaultNamespace;
        }
        return prefixToNamespace.get(prefix);
    }

    /**
     * Get prefix for namespace
     */
    public String getPrefixForNamespace(String namespace) {
        if (namespace == null) {
            return null;
        }

        // Check if it's the default namespace
        if (namespace.equals(defaultNamespace)) {
            return null; // Default namespace has no prefix
        }

        return namespaceToPrefix.get(namespace);
    }

    /**
     * Resolve element name with namespace context
     */
    public ResolvedName resolveElementName(String elementName) {
        if (elementName == null || elementName.trim().isEmpty()) {
            return new ResolvedName(elementName, null, defaultNamespace);
        }

        String[] parts = elementName.split(":", 2);
        if (parts.length == 2) {
            // Prefixed element: prefix:localName
            String prefix = parts[0];
            String localName = parts[1];
            String namespace = getNamespaceForPrefix(prefix);
            return new ResolvedName(localName, prefix, namespace);
        } else {
            // Unprefixed element: localName
            return new ResolvedName(elementName, null, defaultNamespace);
        }
    }

    /**
     * Get current context namespace based on cursor position
     */
    public String getContextNamespace(String xmlContent, int cursorPosition) {
        if (xmlContent == null || cursorPosition < 0 || cursorPosition > xmlContent.length()) {
            return defaultNamespace;
        }

        // Find the current element context
        String contextElement = findCurrentElement(xmlContent, cursorPosition);
        if (contextElement != null) {
            ResolvedName resolved = resolveElementName(contextElement);
            return resolved.namespace;
        }

        return defaultNamespace;
    }

    /**
     * Find current element at cursor position
     */
    private String findCurrentElement(String xmlContent, int cursorPosition) {
        // Look backwards from cursor position to find opening tag
        int searchStart = Math.max(0, cursorPosition - 1000); // Limit search scope
        String searchArea = xmlContent.substring(searchStart, cursorPosition);

        // Find last opening tag before cursor
        Pattern elementPattern = Pattern.compile("<(/?)(\\w+(?::\\w+)?)(?:\\s|>|/>)");
        Matcher matcher = elementPattern.matcher(searchArea);

        String lastElement = null;
        while (matcher.find()) {
            String isClosing = matcher.group(1);
            String elementName = matcher.group(2);

            if (isClosing == null || isClosing.isEmpty()) {
                // Opening tag
                lastElement = elementName;
            }
        }

        return lastElement;
    }

    /**
     * Get namespace suggestions for completion
     */
    public List<CompletionItem> getNamespaceSuggestions() {
        List<CompletionItem> suggestions = new ArrayList<>();

        // Add all registered namespaces
        for (Map.Entry<String, String> entry : prefixToNamespace.entrySet()) {
            String prefix = entry.getKey();
            String namespace = entry.getValue();

            CompletionItem item = new CompletionItem.Builder(
                    prefix + ":",
                    prefix + ":",
                    CompletionItemType.NAMESPACE
            )
                    .description("Namespace: " + namespace)
                    .dataType("namespace")
                    .relevanceScore(isCommonNamespace(namespace) ? 200 : 100)
                    .build();

            suggestions.add(item);
        }

        // Add default namespace suggestion if exists
        if (defaultNamespace != null) {
            CompletionItem item = new CompletionItem.Builder(
                    "(default)",
                    "",
                    CompletionItemType.NAMESPACE
            )
                    .description("Default namespace: " + defaultNamespace)
                    .dataType("namespace")
                    .relevanceScore(150)
                    .build();

            suggestions.add(item);
        }

        return suggestions;
    }

    /**
     * Get namespace declaration suggestions
     */
    public List<CompletionItem> getNamespaceDeclarationSuggestions() {
        List<CompletionItem> suggestions = new ArrayList<>();

        // Suggest common namespace declarations
        for (Map.Entry<String, String> entry : COMMON_NAMESPACES.entrySet()) {
            String prefix = entry.getKey();
            String namespace = entry.getValue();

            // Skip if already declared
            if (prefixToNamespace.containsKey(prefix) &&
                    namespace.equals(prefixToNamespace.get(prefix))) {
                continue;
            }

            CompletionItem item = new CompletionItem.Builder(
                    "xmlns:" + prefix,
                    "xmlns:" + prefix + "=\"" + namespace + "\"",
                    CompletionItemType.ATTRIBUTE
            )
                    .description("Namespace declaration for " + prefix)
                    .dataType("namespace-declaration")
                    .relevanceScore(180)
                    .build();

            suggestions.add(item);
        }

        return suggestions;
    }

    /**
     * Push new namespace scope
     */
    public void pushScope() {
        scopeStack.push(new NamespaceScope(
                new HashMap<>(prefixToNamespace),
                defaultNamespace
        ));
    }

    /**
     * Pop namespace scope
     */
    public void popScope() {
        if (!scopeStack.isEmpty()) {
            NamespaceScope scope = scopeStack.pop();
            prefixToNamespace.clear();
            prefixToNamespace.putAll(scope.prefixToNamespace);
            defaultNamespace = scope.defaultNamespace;

            // Rebuild reverse mapping
            namespaceToPrefix.clear();
            prefixToNamespace.forEach((prefix, namespace) ->
                    namespaceToPrefix.put(namespace, prefix));
        }
    }

    /**
     * Clear user-defined namespaces (keep common ones)
     */
    private void clearUserDefinedNamespaces() {
        // Keep only common namespaces
        prefixToNamespace.entrySet().removeIf(entry ->
                !COMMON_NAMESPACES.containsKey(entry.getKey()));

        namespaceToPrefix.entrySet().removeIf(entry ->
                !COMMON_NAMESPACES.containsValue(entry.getKey()));

        // Clear default namespace if it's not a common one
        if (defaultNamespace != null && !COMMON_NAMESPACES.containsValue(defaultNamespace)) {
            defaultNamespace = null;
        }
    }

    /**
     * Check if namespace is a common/standard namespace
     */
    private boolean isCommonNamespace(String namespace) {
        return COMMON_NAMESPACES.containsValue(namespace);
    }

    /**
     * Get all registered namespaces
     */
    public Map<String, String> getAllNamespaces() {
        return new HashMap<>(prefixToNamespace);
    }

    /**
     * Get default namespace
     */
    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    /**
     * Clear all namespace mappings
     */
    public void clear() {
        prefixToNamespace.clear();
        namespaceToPrefix.clear();
        defaultNamespace = null;
        scopeStack.clear();

        // Re-add common namespaces
        prefixToNamespace.putAll(COMMON_NAMESPACES);
        COMMON_NAMESPACES.forEach((prefix, namespace) ->
                namespaceToPrefix.put(namespace, prefix));
    }

    /**
     * Validate namespace declaration
     */
    public ValidationResult validateNamespaceDeclaration(String declaration) {
        if (declaration == null || declaration.trim().isEmpty()) {
            return new ValidationResult(false, "Empty namespace declaration");
        }

        Matcher matcher = NAMESPACE_DECL_PATTERN.matcher(declaration);
        if (!matcher.matches()) {
            return new ValidationResult(false, "Invalid namespace declaration syntax");
        }

        String prefix = matcher.group(1);
        String namespace = matcher.group(2);

        // Check for reserved prefixes
        if ("xml".equals(prefix) || "xmlns".equals(prefix)) {
            return new ValidationResult(false, "Reserved prefix: " + prefix);
        }

        // Check for valid URI format (basic validation)
        if (!isValidNamespaceURI(namespace)) {
            return new ValidationResult(false, "Invalid namespace URI format");
        }

        return new ValidationResult(true, "Valid namespace declaration");
    }

    /**
     * Basic namespace URI validation
     */
    private boolean isValidNamespaceURI(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return false;
        }

        // Allow HTTP(S) URLs, URNs, and other common patterns
        return uri.startsWith("http://") ||
                uri.startsWith("https://") ||
                uri.startsWith("urn:") ||
                uri.startsWith("file:") ||
                uri.contains(":");
    }

    // Helper classes
        public record ResolvedName(String localName, String prefix, String namespace) {

        public String getQualifiedName() {
                return prefix != null ? prefix + ":" + localName : localName;
            }

            @Override
            public String toString() {
                return String.format("ResolvedName{localName='%s', prefix='%s', namespace='%s'}",
                        localName, prefix, namespace);
            }
        }

    private record NamespaceScope(Map<String, String> prefixToNamespace, String defaultNamespace) {
    }

    public record ValidationResult(boolean isValid, String message) {

        @Override
            public String toString() {
                return String.format("ValidationResult{valid=%s, message='%s'}", isValid, message);
            }
        }
}