package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.StringReader;
import java.util.*;

/**
 * Extracts element and attribute names from XML documents.
 * Used to provide context-aware completions based on the actual XML content.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Efficient SAX-based parsing for large documents</li>
 *   <li>Caches extracted names until invalidated</li>
 *   <li>Tracks namespace prefixes</li>
 *   <li>Maps which attributes belong to which elements</li>
 * </ul>
 */
public class XmlDocumentElementExtractor {

    private static final Logger logger = LogManager.getLogger(XmlDocumentElementExtractor.class);

    private final Set<String> elementNames = new LinkedHashSet<>();
    private final Set<String> attributeNames = new LinkedHashSet<>();
    private final Map<String, Set<String>> elementAttributes = new HashMap<>();
    private final Map<String, String> namespaces = new HashMap<>();

    // Tree structure for path-aware completions
    private String rootElement = null;
    private final Map<String, Set<String>> elementChildren = new HashMap<>();

    private String lastXmlHash = null;
    private boolean cacheValid = false;

    /**
     * Extracts element and attribute names from XML content.
     * Results are cached; subsequent calls with the same content return immediately.
     *
     * @param xmlContent the XML content to parse
     */
    public void extractFromXml(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            clear();
            return;
        }

        // Check if cache is still valid
        String currentHash = String.valueOf(xmlContent.hashCode());
        if (cacheValid && Objects.equals(currentHash, lastXmlHash)) {
            logger.debug("Using cached element/attribute names");
            return;
        }

        // Clear and re-extract
        clear();

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            SAXParser parser = factory.newSAXParser();

            parser.parse(new InputSource(new StringReader(xmlContent)), new ElementAttributeHandler());

            lastXmlHash = currentHash;
            cacheValid = true;

            logger.debug("Extracted {} elements and {} attributes from XML",
                    elementNames.size(), attributeNames.size());

        } catch (Exception e) {
            logger.warn("Failed to parse XML for element extraction: {}", e.getMessage());
            // Keep any partial results
        }
    }

    /**
     * Gets element names matching the given prefix.
     */
    public List<String> searchElements(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(elementNames);
        }

        String lowerPrefix = prefix.toLowerCase();
        return elementNames.stream()
                .filter(name -> name.toLowerCase().startsWith(lowerPrefix))
                .toList();
    }

    /**
     * Gets attribute names matching the given prefix.
     */
    public List<String> searchAttributes(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(attributeNames);
        }

        String lowerPrefix = prefix.toLowerCase();
        return attributeNames.stream()
                .filter(name -> name.toLowerCase().startsWith(lowerPrefix))
                .toList();
    }

    /**
     * Gets attributes that belong to a specific element.
     */
    public Set<String> getAttributesForElement(String elementName) {
        if (elementName == null) {
            return Collections.emptySet();
        }
        return elementAttributes.getOrDefault(elementName, Collections.emptySet());
    }

    /**
     * Gets all extracted element names.
     */
    public Set<String> getAllElements() {
        return Collections.unmodifiableSet(elementNames);
    }

    /**
     * Gets all extracted attribute names.
     */
    public Set<String> getAllAttributes() {
        return Collections.unmodifiableSet(attributeNames);
    }

    /**
     * Gets detected namespace prefixes and URIs.
     */
    public Map<String, String> getNamespaces() {
        return Collections.unmodifiableMap(namespaces);
    }

    /**
     * Returns element completions for the given prefix.
     */
    public List<CompletionItem> getElementCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        List<String> matching = searchElements(prefix);

        for (String element : matching) {
            int score = baseScore;
            // Boost exact prefix matches
            if (prefix != null && element.toLowerCase().startsWith(prefix.toLowerCase())) {
                score += 10;
            }

            items.add(new CompletionItem.Builder(
                    element,
                    element,
                    CompletionItemType.ELEMENT
            )
                    .description("XML Element")
                    .relevanceScore(score)
                    .build());
        }

        return items;
    }

    /**
     * Returns attribute completions for the given prefix.
     */
    public List<CompletionItem> getAttributeCompletions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();
        List<String> matching = searchAttributes(prefix);

        for (String attr : matching) {
            int score = baseScore;
            // Boost exact prefix matches
            if (prefix != null && attr.toLowerCase().startsWith(prefix.toLowerCase())) {
                score += 10;
            }

            items.add(new CompletionItem.Builder(
                    attr,
                    attr,
                    CompletionItemType.ATTRIBUTE
            )
                    .description("XML Attribute")
                    .relevanceScore(score)
                    .build());
        }

        return items;
    }

    /**
     * Returns attribute completions for a specific element.
     */
    public List<CompletionItem> getAttributeCompletionsForElement(String elementName, String prefix, int baseScore) {
        Set<String> attrs = getAttributesForElement(elementName);
        if (attrs.isEmpty()) {
            return getAttributeCompletions(prefix, baseScore);
        }

        List<CompletionItem> items = new ArrayList<>();
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (String attr : attrs) {
            if (lowerPrefix.isEmpty() || attr.toLowerCase().startsWith(lowerPrefix)) {
                int score = baseScore + 5; // Boost element-specific attributes

                items.add(new CompletionItem.Builder(
                        attr,
                        attr,
                        CompletionItemType.ATTRIBUTE
                )
                        .description("Attribute of " + elementName)
                        .relevanceScore(score)
                        .build());
            }
        }

        // Also include general attributes that might not be on this element yet
        for (String attr : attributeNames) {
            if (!attrs.contains(attr)) {
                if (lowerPrefix.isEmpty() || attr.toLowerCase().startsWith(lowerPrefix)) {
                    items.add(new CompletionItem.Builder(
                            attr,
                            attr,
                            CompletionItemType.ATTRIBUTE
                    )
                            .description("XML Attribute")
                            .relevanceScore(baseScore)
                            .build());
                }
            }
        }

        return items;
    }

    /**
     * Gets the root element name.
     *
     * @return the root element name, or null if not parsed
     */
    public String getRootElement() {
        return rootElement;
    }

    /**
     * Gets child element names for a given parent element.
     * This is used for path-aware XPath completions.
     *
     * @param parentElement the parent element name
     * @return set of child element names, or empty set if no children
     */
    public Set<String> getChildElements(String parentElement) {
        if (parentElement == null) {
            return Collections.emptySet();
        }
        return elementChildren.getOrDefault(parentElement, Collections.emptySet());
    }

    /**
     * Gets child elements matching a prefix for a given parent.
     *
     * @param parentElement the parent element name
     * @param prefix        the prefix to filter by
     * @return list of matching child element names
     */
    public List<String> searchChildElements(String parentElement, String prefix) {
        Set<String> children = getChildElements(parentElement);
        if (children.isEmpty()) {
            return List.of();
        }

        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(children);
        }

        String lowerPrefix = prefix.toLowerCase();
        return children.stream()
                .filter(name -> name.toLowerCase().startsWith(lowerPrefix))
                .toList();
    }

    /**
     * Gets element completions for a specific parent element (path-aware).
     *
     * @param parentElement the parent element name (null for root)
     * @param prefix        the prefix to filter by
     * @param baseScore     the base relevance score
     * @return list of completion items for valid child elements
     */
    public List<CompletionItem> getChildElementCompletions(String parentElement, String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();

        // If no parent specified, return root element only
        if (parentElement == null) {
            if (rootElement != null) {
                if (prefix == null || prefix.isEmpty() ||
                        rootElement.toLowerCase().startsWith(prefix.toLowerCase())) {
                    items.add(new CompletionItem.Builder(
                            rootElement,
                            rootElement,
                            CompletionItemType.ELEMENT
                    )
                            .description("Root Element")
                            .relevanceScore(baseScore + 20)
                            .build());
                }
            }
            return items;
        }

        // Get children for the specified parent
        List<String> children = searchChildElements(parentElement, prefix);
        for (String child : children) {
            int score = baseScore;
            if (prefix != null && child.toLowerCase().startsWith(prefix.toLowerCase())) {
                score += 10;
            }

            items.add(new CompletionItem.Builder(
                    child,
                    child,
                    CompletionItemType.ELEMENT
            )
                    .description("Child of " + parentElement)
                    .relevanceScore(score)
                    .build());
        }

        return items;
    }

    /**
     * Clears all cached data.
     */
    public void clear() {
        elementNames.clear();
        attributeNames.clear();
        elementAttributes.clear();
        elementChildren.clear();
        namespaces.clear();
        rootElement = null;
        lastXmlHash = null;
        cacheValid = false;
    }

    /**
     * Invalidates the cache, forcing re-extraction on next call.
     */
    public void invalidateCache() {
        cacheValid = false;
    }

    /**
     * Returns true if the cache is valid.
     */
    public boolean isCacheValid() {
        return cacheValid;
    }

    /**
     * Returns the number of extracted elements.
     */
    public int getElementCount() {
        return elementNames.size();
    }

    /**
     * Returns the number of extracted attributes.
     */
    public int getAttributeCount() {
        return attributeNames.size();
    }

    /**
     * SAX handler that extracts element and attribute names and builds parent-child relationships.
     */
    private class ElementAttributeHandler extends DefaultHandler {

        private final Deque<String> elementStack = new ArrayDeque<>();
        private boolean isFirstElement = true;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {

            // Use qualified name if available, otherwise local name
            String elementName = qName != null && !qName.isEmpty() ? qName : localName;

            if (elementName != null && !elementName.isEmpty()) {
                elementNames.add(elementName);

                // Track root element
                if (isFirstElement) {
                    rootElement = elementName;
                    isFirstElement = false;
                }

                // Track parent-child relationship
                if (!elementStack.isEmpty()) {
                    String parentElement = elementStack.peek();
                    Set<String> children = elementChildren.computeIfAbsent(parentElement, k -> new LinkedHashSet<>());
                    children.add(elementName);
                }

                elementStack.push(elementName);

                // Also add local name if different
                if (localName != null && !localName.isEmpty() && !localName.equals(elementName)) {
                    elementNames.add(localName);
                }

                // Extract namespace prefix
                if (qName != null && qName.contains(":")) {
                    String prefix = qName.substring(0, qName.indexOf(':'));
                    if (uri != null && !uri.isEmpty()) {
                        namespaces.put(prefix, uri);
                    }
                }
            }

            // Extract attributes
            Set<String> attrsForElement = elementAttributes.computeIfAbsent(elementName, k -> new HashSet<>());

            for (int i = 0; i < attributes.getLength(); i++) {
                String attrQName = attributes.getQName(i);
                String attrLocalName = attributes.getLocalName(i);

                String attrName = attrQName != null && !attrQName.isEmpty() ? attrQName : attrLocalName;

                if (attrName != null && !attrName.isEmpty()) {
                    // Skip xmlns declarations for attribute completions
                    if (!attrName.startsWith("xmlns")) {
                        attributeNames.add(attrName);
                        attrsForElement.add(attrName);

                        // Also add local name if different
                        if (attrLocalName != null && !attrLocalName.isEmpty() && !attrLocalName.equals(attrName)) {
                            attributeNames.add(attrLocalName);
                            attrsForElement.add(attrLocalName);
                        }
                    }
                }

                // Handle namespace declarations
                if (attrName != null && attrName.startsWith("xmlns")) {
                    String prefix = attrName.contains(":") ? attrName.substring(attrName.indexOf(':') + 1) : "";
                    String nsUri = attributes.getValue(i);
                    if (!prefix.isEmpty() && nsUri != null) {
                        namespaces.put(prefix, nsUri);
                    }
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (!elementStack.isEmpty()) {
                elementStack.pop();
            }
        }
    }
}
