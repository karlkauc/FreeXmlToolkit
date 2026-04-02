package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdElementDisplayUtils;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

/**
 * Provides completions based on XSD schema.
 * This is the highest priority provider when an XSD is loaded.
 */
public class XsdCompletionProvider implements CompletionProvider {

    private static final Logger logger = LogManager.getLogger(XsdCompletionProvider.class);

    private final XmlSchemaProvider schemaProvider;

    public XsdCompletionProvider(XmlSchemaProvider schemaProvider) {
        this.schemaProvider = Objects.requireNonNull(schemaProvider, "SchemaProvider cannot be null");
    }

    @Override
    public boolean canProvideCompletions(XmlContext context, EditorMode mode) {
        // Only provide if schema is loaded and we're in element or attribute context
        boolean hasSchema = schemaProvider.hasSchema();
        boolean supportedContext = context.getType() == ContextType.ELEMENT ||
                                   context.getType() == ContextType.ATTRIBUTE ||
                                   context.getType() == ContextType.TEXT_CONTENT;

        return hasSchema && supportedContext;
    }

    @Override
    public List<CompletionItem> getCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();

        XsdDocumentationData xsdData = schemaProvider.getXsdDocumentationData();
        if (xsdData == null) {
            return items;
        }

        switch (context.getType()) {
            case ELEMENT -> items.addAll(getElementCompletions(context, xsdData));
            case ATTRIBUTE -> items.addAll(getAttributeCompletions(context, xsdData));
            case TEXT_CONTENT -> items.addAll(getTextContentCompletions(context, xsdData));
            default -> { }
        }

        logger.debug("XSD provider returned {} completions for context: {}", items.size(), context.getType());
        return items;
    }

    /**
     * Gets element completions based on XSD schema.
     * Filters out elements that have already reached their maxOccurs limit.
     */
    private List<CompletionItem> getElementCompletions(XmlContext context, XsdDocumentationData xsdData) {
        List<CompletionItem> items = new ArrayList<>();

        // Get parent XPath to find allowed children
        String parentPath = context.getXPath();
        if (parentPath.endsWith("/")) {
            parentPath = parentPath.substring(0, parentPath.length() - 1);
        }

        // Try exact match first
        XsdExtendedElement parentInfo = xsdData.getExtendedXsdElementMap().get(parentPath);

        // Fallback to best matching (handles XSD compositor elements like SEQUENCE_X, CHOICE_X)
        if (parentInfo == null) {
            parentInfo = schemaProvider.findBestMatchingElement(parentPath);
        }

        if (parentInfo != null && parentInfo.getChildren() != null) {
            // Extract the actual parent element name from the XPath
            // The XPath like "/root/parent/child" means we're inside "child",
            // so "child" is the parent for our completions
            String actualParent = extractLastElementFromXPath(parentPath);

            // Count existing sibling elements to filter by maxOccurs
            Map<String, Integer> siblingCounts = countExistingSiblings(
                context.getTextBeforeCaret(),
                actualParent
            );
            logger.debug("Smart filtering: actualParent='{}', siblingCounts={}", actualParent, siblingCounts);

            // Collect all real child elements, recursively digging into compositors
            List<XsdExtendedElement> realChildren = new ArrayList<>();
            collectRealChildElements(parentInfo, xsdData, realChildren, new java.util.HashSet<>());

            // Create completion items, filtering out elements that reached maxOccurs
            int index = 0;
            for (XsdExtendedElement childInfo : realChildren) {
                String elementName = childInfo.getElementName();
                int maxOccurs = getMaxOccurs(childInfo);
                int currentCount = siblingCounts.getOrDefault(elementName, 0);

                // Skip elements that have reached their maxOccurs limit
                if (maxOccurs > 0 && currentCount >= maxOccurs) {
                    logger.debug("Filtering out '{}': maxOccurs={}, count={}",
                        elementName, maxOccurs, currentCount);
                    continue;
                }

                CompletionItem item = createElementCompletionItem(childInfo, index++);
                items.add(item);
            }
        }

        return items;
    }

    /**
     * Counts how many times each child element appears in the current parent element.
     * Only counts direct children, not nested elements.
     *
     * @param textBeforeCaret the XML text from the beginning to the cursor position
     * @param parentElement   the name of the parent element
     * @return a map of element names to their occurrence counts
     */
    private Map<String, Integer> countExistingSiblings(String textBeforeCaret, String parentElement) {
        Map<String, Integer> counts = new HashMap<>();

        if (textBeforeCaret == null || textBeforeCaret.isEmpty() ||
            parentElement == null || parentElement.isEmpty()) {
            return counts;
        }

        // Find the last opening tag for the parent element
        String openTag = "<" + parentElement;
        int parentStart = textBeforeCaret.lastIndexOf(openTag);
        if (parentStart < 0) {
            return counts;
        }

        // Find end of opening tag (after '>')
        int tagEnd = textBeforeCaret.indexOf('>', parentStart);
        if (tagEnd < 0) {
            return counts;
        }

        // Extract content between parent start and cursor
        String content = textBeforeCaret.substring(tagEnd + 1);

        // Remove comments and CDATA to avoid counting elements inside them
        content = removeCommentsAndCData(content);

        // Use stack-based counting to only count direct children
        // Match all opening and closing tags
        Pattern tagPattern = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9_:-]*)(?:\\s[^>]*)?(/?)>");
        Matcher matcher = tagPattern.matcher(content);

        int depth = 0;
        while (matcher.find()) {
            boolean isClosing = !matcher.group(1).isEmpty();
            String tagName = matcher.group(2);
            boolean isSelfClosing = !matcher.group(3).isEmpty();

            if (isClosing) {
                // Closing tag - decrease depth
                depth--;
            } else if (isSelfClosing) {
                // Self-closing tag at depth 0 = direct child
                if (depth == 0) {
                    counts.merge(tagName, 1, Integer::sum);
                }
            } else {
                // Opening tag
                if (depth == 0) {
                    // Direct child of parent
                    counts.merge(tagName, 1, Integer::sum);
                }
                depth++;
            }
        }

        logger.debug("Counted siblings in '{}': {}", parentElement, counts);
        return counts;
    }

    /**
     * Removes XML comments and CDATA sections from content.
     */
    private String removeCommentsAndCData(String content) {
        if (content == null) {
            return "";
        }
        // Remove comments: <!-- ... -->
        content = content.replaceAll("<!--[\\s\\S]*?-->", "");
        // Remove CDATA: <![CDATA[ ... ]]>
        content = content.replaceAll("<!\\[CDATA\\[[\\s\\S]*?]]>", "");
        return content;
    }

    /**
     * Extracts the maxOccurs value from an XsdExtendedElement.
     *
     * @param element the XSD element info
     * @return maxOccurs value, -1 for "unbounded" (no limit)
     */
    private int getMaxOccurs(XsdExtendedElement element) {
        org.w3c.dom.Node cardNode = element.getCardinalityNode();
        org.w3c.dom.Node currentNode = element.getCurrentNode();

        // Try cardinalityNode first (for element references), then currentNode
        org.w3c.dom.Node sourceNode = cardNode != null ? cardNode : currentNode;
        if (sourceNode == null) {
            return -1; // No info available, assume unbounded
        }

        String maxOccurs = XsdElementDisplayUtils.getNodeAttribute(sourceNode, "maxOccurs");
        if (maxOccurs == null || maxOccurs.isEmpty()) {
            return 1; // Default maxOccurs is 1
        }
        if ("unbounded".equals(maxOccurs)) {
            return -1; // No limit
        }
        try {
            return Integer.parseInt(maxOccurs);
        } catch (NumberFormatException e) {
            return -1; // Parse error, assume unbounded
        }
    }

    /**
     * Extracts the last element name from an XPath.
     * For "/FundsXML4/AssetDetails/Future" returns "Future".
     *
     * @param xpath the XPath string
     * @return the last element name, or null if empty
     */
    private String extractLastElementFromXPath(String xpath) {
        if (xpath == null || xpath.isEmpty()) {
            return null;
        }
        int lastSlash = xpath.lastIndexOf('/');
        if (lastSlash < 0) {
            return xpath;
        }
        String lastPart = xpath.substring(lastSlash + 1);
        return lastPart.isEmpty() ? null : lastPart;
    }

    /**
     * Recursively collects real child elements, skipping compositor elements.
     * Delegates to {@link XsdElementDisplayUtils#collectRealChildElements}.
     */
    private void collectRealChildElements(XsdExtendedElement parent, XsdDocumentationData xsdData,
                                          List<XsdExtendedElement> result, java.util.Set<String> visited) {
        XsdElementDisplayUtils.collectRealChildElements(parent, xsdData, result, visited);
    }

    /**
     * Creates a completion item from XSD element info.
     * Delegates to {@link XsdElementDisplayUtils#buildCompletionItem} and adds IntelliSense-specific description.
     */
    private CompletionItem createElementCompletionItem(XsdExtendedElement elementInfo, int index) {
        // Build the base item with all display fields via shared utility
        CompletionItem baseItem = XsdElementDisplayUtils.buildCompletionItem(elementInfo, index);

        // Re-build with IntelliSense-specific description (tooltip)
        String documentation = buildElementDescription(elementInfo);

        return new CompletionItem.Builder(baseItem.getLabel(), baseItem.getInsertText(), baseItem.getType())
                .description(documentation)
                .dataType(baseItem.getDataType())
                .required(baseItem.isRequired())
                .relevanceScore(baseItem.getRelevanceScore())
                .cardinality(baseItem.getCardinality())
                .defaultValue(baseItem.getDefaultValue())
                .facetHints(baseItem.getFacetHints())
                .examples(baseItem.getExamples())
                .namespace(baseItem.getNamespace())
                .prefix(baseItem.getPrefix())
                .build();
    }

    /**
     * Builds a description for an element completion item.
     * Includes XSD documentation, type information, and cardinality.
     *
     * @param elementInfo the XSD element information
     * @return formatted description string
     */
    private String buildElementDescription(XsdExtendedElement elementInfo) {
        StringBuilder description = new StringBuilder();

        // Add element name and type
        description.append("Element: ").append(elementInfo.getElementName());
        if (elementInfo.getElementType() != null && !elementInfo.getElementType().isEmpty()) {
            description.append(" (").append(elementInfo.getElementType()).append(")");
        }

        // Add mandatory indicator
        if (elementInfo.isMandatory()) {
            description.append(" [required]");
        }

        // Add XSD documentation if available
        String xsdDoc = elementInfo.getDocumentationAsHtml();
        if (xsdDoc != null && !xsdDoc.trim().isEmpty()) {
            // Strip HTML tags for plain text display
            String plainDoc = stripHtmlTags(xsdDoc);
            if (!plainDoc.trim().isEmpty()) {
                description.append("\n").append(plainDoc.trim());
            }
        }

        return description.toString();
    }

    /**
     * Strips HTML tags from a string for plain text display.
     * Also handles common HTML entities.
     *
     * @param html the HTML string
     * @return plain text without HTML tags
     */
    private String stripHtmlTags(String html) {
        if (html == null) {
            return "";
        }

        // Remove HTML tags
        String text = html.replaceAll("<[^>]+>", "");

        // Replace common HTML entities
        text = text.replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&amp;", "&")
                   .replace("&quot;", "\"")
                   .replace("&apos;", "'")
                   .replace("&nbsp;", " ")
                   .replace("<br />", "\n")
                   .replace("<br/>", "\n")
                   .replace("<br>", "\n");

        // Normalize whitespace
        text = text.replaceAll("\\s+", " ");

        return text.trim();
    }

    /**
     * Gets attribute completions.
     *
     * FUTURE ENHANCEMENT: XSD attribute completion
     * Implementation would require:
     * 1. Parse XSD schema to extract attribute definitions for current element type
     * 2. Resolve element's type definition (complexType/simpleType)
     * 3. Collect attributes from type definition and its base types
     * 4. Filter out attributes already present in the element
     * 5. Include global attributes if applicable
     * 6. Provide completion items with attribute names, types, and documentation
     *
     * Complexity: High - requires full XSD type resolution and inheritance chain traversal
     *
     * @param context the XML context at cursor position
     * @param xsdData the XSD documentation data
     * @return list of attribute completion items (currently empty)
     */
    private List<CompletionItem> getAttributeCompletions(XmlContext _context, XsdDocumentationData _xsdData) {
        // Future enhancement: Parse XSD for attribute definitions
        // See JavaDoc above for implementation requirements
        logger.debug("Attribute completions from XSD not yet implemented");
        return new ArrayList<>();
    }

    /**
     * Gets text content completions (e.g., enumeration values).
     * Provides suggestions for element text content based on XSD constraints.
     */
    private List<CompletionItem> getTextContentCompletions(XmlContext context, XsdDocumentationData xsdData) {
        List<CompletionItem> items = new ArrayList<>();

        // Get current XPath
        String currentPath = context.getXPath();
        if (currentPath == null || currentPath.isEmpty()) {
            return items;
        }

        // Try exact match first
        XsdExtendedElement elementInfo = xsdData.getExtendedXsdElementMap().get(currentPath);

        // Fallback to best matching
        if (elementInfo == null) {
            elementInfo = schemaProvider.findBestMatchingElement(currentPath);
        }

        if (elementInfo == null) {
            logger.debug("No XSD element info found for path: {}", currentPath);
            return items;
        }

        // Check for restriction info with enumeration facets
        XsdExtendedElement.RestrictionInfo restrictionInfo = elementInfo.getRestrictionInfo();
        if (restrictionInfo != null && restrictionInfo.facets() != null) {
            List<String> enumerationValues = restrictionInfo.facets().get("enumeration");

            if (enumerationValues != null && !enumerationValues.isEmpty()) {
                logger.debug("Found {} enumeration values for {}", enumerationValues.size(), currentPath);

                // Create completion items for each enumeration value
                for (String enumValue : enumerationValues) {
                    CompletionItem item = new CompletionItem.Builder(
                        enumValue,
                        enumValue,
                        CompletionItemType.VALUE
                    )
                    .description("Enumeration value from XSD")
                    .dataType(restrictionInfo.base())
                    .build();

                    items.add(item);
                }
            } else {
                logger.debug("No enumeration facets found for element: {}", currentPath);
            }
        } else {
            logger.debug("No restriction info found for element: {}", currentPath);
        }

        return items;
    }

    @Override
    public int getPriority() {
        return 100; // Highest priority
    }

    @Override
    public String getName() {
        return "XSD Completion Provider";
    }
}
