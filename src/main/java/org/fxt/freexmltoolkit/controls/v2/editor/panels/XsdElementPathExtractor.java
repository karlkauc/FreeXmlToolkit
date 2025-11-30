package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts XPath-like element paths from an XSD schema for autocomplete suggestions.
 * Used by the AppInfo editor for {@link} tag autocomplete.
 * <p>
 * Handles circular type references safely by tracking visited node IDs.
 *
 * @since 2.0
 */
public class XsdElementPathExtractor {

    private static final Logger logger = LogManager.getLogger(XsdElementPathExtractor.class);

    /** Maximum depth to traverse to avoid extremely deep schemas */
    private static final int MAX_DEPTH = 50;

    /** Maximum number of results to return */
    private static final int MAX_RESULTS = 100;

    private final XsdSchema schema;

    /** Cache of extracted paths */
    private List<String> cachedPaths;

    /** Track visited node IDs to prevent infinite loops */
    private final Set<String> visitedIds = new HashSet<>();

    /**
     * Creates a new path extractor for the given schema.
     *
     * @param schema the XSD schema
     */
    public XsdElementPathExtractor(XsdSchema schema) {
        this.schema = schema;
    }

    /**
     * Gets element paths matching the given prefix.
     *
     * @param prefix the prefix to filter by (case-insensitive), or empty for all
     * @return list of matching XPath-like paths (e.g., "/Root/Child/Element")
     */
    public List<String> getElementPaths(String prefix) {
        if (cachedPaths == null) {
            cachedPaths = extractAllPaths();
        }

        String lowerPrefix = prefix == null ? "" : prefix.toLowerCase();

        return cachedPaths.stream()
                .filter(path -> path.toLowerCase().contains(lowerPrefix))
                .sorted(Comparator.comparingInt(String::length).thenComparing(String::compareTo))
                .limit(MAX_RESULTS)
                .toList();
    }

    /**
     * Gets all extracted element paths.
     *
     * @return list of all XPath-like paths
     */
    public List<String> getAllPaths() {
        if (cachedPaths == null) {
            cachedPaths = extractAllPaths();
        }
        return new ArrayList<>(cachedPaths);
    }

    /**
     * Invalidates the path cache, forcing re-extraction on next query.
     */
    public void invalidateCache() {
        cachedPaths = null;
    }

    /**
     * Extracts all element paths from the schema.
     */
    private List<String> extractAllPaths() {
        List<String> paths = new ArrayList<>();
        visitedIds.clear();

        if (schema == null) {
            logger.warn("Schema is null, returning empty paths");
            return paths;
        }

        long startTime = System.currentTimeMillis();

        // Start traversal from root elements
        for (XsdNode child : schema.getChildren()) {
            collectPaths(child, "", paths, 0);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        logger.debug("Extracted {} element paths in {}ms", paths.size(), elapsed);

        return paths;
    }

    /**
     * Recursively collects element paths.
     *
     * @param node        the current node
     * @param currentPath the path so far
     * @param paths       the list to add paths to
     * @param depth       current traversal depth
     */
    private void collectPaths(XsdNode node, String currentPath, List<String> paths, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return;
        }

        // Check for circular references
        if (node.getId() != null && visitedIds.contains(node.getId())) {
            logger.trace("Skipping already visited node: {}", node.getId());
            return;
        }

        if (node.getId() != null) {
            visitedIds.add(node.getId());
        }

        try {
            // Handle elements - add to path
            if (node instanceof XsdElement element) {
                String elementName = element.getName();
                if (elementName != null && !elementName.isEmpty()) {
                    String newPath = currentPath + "/" + elementName;
                    paths.add(newPath);

                    // Traverse children
                    for (XsdNode child : element.getChildren()) {
                        collectPaths(child, newPath, paths, depth + 1);
                    }
                }
            }
            // Handle complex types - traverse but don't add to path
            else if (node instanceof XsdComplexType complexType) {
                for (XsdNode child : complexType.getChildren()) {
                    collectPaths(child, currentPath, paths, depth + 1);
                }
            }
            // Handle sequences, choices, all - traverse without adding to path
            else if (node instanceof XsdSequence || node instanceof XsdChoice || node instanceof XsdAll) {
                for (XsdNode child : node.getChildren()) {
                    collectPaths(child, currentPath, paths, depth + 1);
                }
            }
            // Handle groups - traverse
            else if (node instanceof XsdGroup group) {
                for (XsdNode child : group.getChildren()) {
                    collectPaths(child, currentPath, paths, depth + 1);
                }
            }
            // Handle other nodes that may contain elements
            else {
                for (XsdNode child : node.getChildren()) {
                    collectPaths(child, currentPath, paths, depth + 1);
                }
            }
        } finally {
            // Remove from visited set after processing to allow different paths to same element
            if (node.getId() != null) {
                visitedIds.remove(node.getId());
            }
        }
    }

    /**
     * Gets element names (without full path) matching the prefix.
     *
     * @param prefix the prefix to filter by (case-insensitive)
     * @return list of matching element names
     */
    public List<String> getElementNames(String prefix) {
        if (cachedPaths == null) {
            cachedPaths = extractAllPaths();
        }

        String lowerPrefix = prefix == null ? "" : prefix.toLowerCase();

        return cachedPaths.stream()
                .map(path -> path.substring(path.lastIndexOf('/') + 1))
                .distinct()
                .filter(name -> name.toLowerCase().contains(lowerPrefix))
                .sorted()
                .limit(MAX_RESULTS)
                .toList();
    }

    /**
     * Suggests completions for a partial {@link} target.
     * Handles both XPath format (/Root/Child) and element name format (ElementName).
     *
     * @param partial the partial link target text
     * @return list of suggestions
     */
    public List<LinkSuggestion> suggestLinks(String partial) {
        List<LinkSuggestion> suggestions = new ArrayList<>();

        if (partial == null || partial.isEmpty()) {
            // Return top-level elements
            getElementPaths("").stream()
                    .filter(p -> p.chars().filter(c -> c == '/').count() == 1)
                    .limit(20)
                    .forEach(p -> suggestions.add(new LinkSuggestion(p, p, "XPath")));
            return suggestions;
        }

        if (partial.startsWith("/")) {
            // XPath mode - suggest paths
            getElementPaths(partial).forEach(path ->
                    suggestions.add(new LinkSuggestion(path, path, "XPath")));
        } else {
            // Element name mode - suggest names
            getElementNames(partial).forEach(name ->
                    suggestions.add(new LinkSuggestion(name, name, "Element")));

            // Also add matching full paths
            getElementPaths(partial).stream()
                    .limit(MAX_RESULTS / 2)
                    .forEach(path ->
                            suggestions.add(new LinkSuggestion(path, path, "XPath")));
        }

        return suggestions.stream()
                .distinct()
                .limit(MAX_RESULTS)
                .toList();
    }

    /**
     * Represents a link suggestion for autocomplete.
     */
    public record LinkSuggestion(String displayText, String insertText, String type) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LinkSuggestion that = (LinkSuggestion) o;
            return Objects.equals(insertText, that.insertText);
        }

        @Override
        public int hashCode() {
            return Objects.hash(insertText);
        }
    }
}
