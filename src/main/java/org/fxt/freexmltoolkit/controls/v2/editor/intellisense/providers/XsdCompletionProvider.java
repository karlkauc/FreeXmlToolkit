package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.StartTagAttributes;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlNamespaceDeclarations;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider;
import org.fxt.freexmltoolkit.domain.XsdAttributeLookup;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdElementDisplayUtils;
import org.fxt.freexmltoolkit.domain.XsdElementNamespaceResolver;
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
                                   context.getType() == ContextType.TEXT_CONTENT ||
                                   // Specialized modes (XSLT, Schematron, XSL-FO) suggest their own values
                                   (context.getType() == ContextType.ATTRIBUTE_VALUE
                                           && (mode == null || !mode.requiresSpecializedProviders()));
        // XSLT attributes come from the curated XSLT 3.0 catalog (XsltCompletionProvider): the
        // auto-bound W3C schema only describes XSLT 2.0.
        if (mode == EditorMode.XSLT && context.getType() == ContextType.ATTRIBUTE) {
            return false;
        }

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
            case ATTRIBUTE_VALUE -> items.addAll(getAttributeValueCompletions(context, xsdData));
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
            // The XPath like "/root/parent/child" means we're inside "child",
            // so "child" is the parent for our completions
            String actualParent = extractLastElementFromXPath(parentPath);

            // Direct children already present before/after the caret (local names, in order)
            DirectChildScanner.Siblings siblings = DirectChildScanner.scan(
                    context.getTextBeforeCaret(), context.getTextAfterCaret(), actualParent);
            logger.debug("Smart filtering: actualParent='{}', siblings={}", actualParent, siblings);

            // Only the children that may legally appear at this position (sequence order, maxOccurs)
            List<XsdExtendedElement> allowed = new AllowedChildrenCalculator(xsdData)
                    .compute(parentInfo, siblings.before(), siblings.after());

            // Prefix each name the way the instance document declares its namespaces
            XmlNamespaceDeclarations declarations = XmlNamespaceDeclarations.scan(context.getTextBeforeCaret());

            int index = 0;
            for (XsdExtendedElement childInfo : allowed) {
                items.add(createElementCompletionItem(childInfo, index++, xsdData, declarations));
            }
        }

        return items;
    }

    /**
     * Extracts the last element's local name from an XPath.
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
        String lastPart = lastSlash < 0 ? xpath : xpath.substring(lastSlash + 1);
        return lastPart.isEmpty() ? null : DirectChildScanner.localName(lastPart);
    }

    /**
     * Creates a completion item from XSD element info. The label/insert text is the name
     * qualified with the prefix the instance document binds to the element's namespace.
     * Delegates to {@link XsdElementDisplayUtils#buildCompletionItem} and adds IntelliSense-specific description.
     */
    private CompletionItem createElementCompletionItem(XsdExtendedElement elementInfo, int index,
                                                       XsdDocumentationData xsdData,
                                                       XmlNamespaceDeclarations declarations) {
        // Build the base item with all display fields via shared utility
        CompletionItem baseItem = XsdElementDisplayUtils.buildCompletionItem(elementInfo, index);

        // Re-build with IntelliSense-specific description (tooltip)
        String documentation = buildElementDescription(elementInfo);

        String namespaceUri = XsdElementNamespaceResolver.resolveNamespaceUri(elementInfo, xsdData);
        String schemaPrefix = baseItem.getPrefix() != null ? baseItem.getPrefix() : schemaPrefixFor(namespaceUri, xsdData);
        String qualifiedName = declarations.qualify(elementInfo.getElementName(), namespaceUri, schemaPrefix);
        int colon = qualifiedName.indexOf(':');
        String usedPrefix = colon > 0 ? qualifiedName.substring(0, colon) : null;

        return new CompletionItem.Builder(qualifiedName, qualifiedName, baseItem.getType())
                .description(documentation)
                .dataType(baseItem.getDataType())
                .required(baseItem.isRequired())
                .relevanceScore(baseItem.getRelevanceScore())
                .cardinality(baseItem.getCardinality())
                .defaultValue(baseItem.getDefaultValue())
                .facetHints(baseItem.getFacetHints())
                .examples(baseItem.getExamples())
                .namespace(namespaceUri)
                .prefix(usedPrefix)
                .build();
    }

    /** The prefix the schema files themselves bind to {@code namespaceUri}, or null. */
    private static String schemaPrefixFor(String namespaceUri, XsdDocumentationData xsdData) {
        if (namespaceUri == null || xsdData.getNamespaces() == null) {
            return null;
        }
        return xsdData.getNamespaces().entrySet().stream()
                .filter(e -> namespaceUri.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .filter(p -> p != null && !p.isBlank())
                .findFirst()
                .orElse(null);
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
     * Gets attribute completions: the attributes the schema declares for the element whose start
     * tag the caret is in, without those already written in the tag; required ones first.
     */
    private List<CompletionItem> getAttributeCompletions(XmlContext context, XsdDocumentationData xsdData) {
        List<CompletionItem> items = new ArrayList<>();

        String elementPath = attributeOwnerPath(context);
        if (elementPath == null) {
            return items;
        }
        XsdExtendedElement elementInfo = xsdData.getExtendedXsdElementMap().get(elementPath);
        if (elementInfo == null) {
            elementInfo = schemaProvider.findBestMatchingElement(elementPath);
        }
        if (elementInfo == null) {
            logger.debug("No XSD element info found for attribute owner: {}", elementPath);
            return items;
        }

        Set<String> present = new HashSet<>();
        for (String name : StartTagAttributes.present(context.getTextBeforeCaret(), context.getTextAfterCaret())) {
            present.add(name.substring(name.indexOf(':') + 1));
        }

        List<XsdExtendedElement> attributes = new ArrayList<>(XsdAttributeLookup.declaredAttributes(xsdData, elementInfo));
        attributes.removeIf(a -> present.contains(XsdAttributeLookup.attributeName(a)));
        attributes.sort(Comparator.comparing((XsdExtendedElement a) -> !a.isMandatory())
                .thenComparing(XsdAttributeLookup::attributeName));

        for (XsdExtendedElement attribute : attributes) {
            String name = XsdAttributeLookup.attributeName(attribute);
            StringBuilder description = new StringBuilder("Attribute: ").append(name);
            if (attribute.getElementType() != null && !attribute.getElementType().isEmpty()) {
                description.append(" (").append(attribute.getElementType()).append(")");
            }
            if (attribute.isMandatory()) {
                description.append(" [required]");
            }
            String doc = stripHtmlTags(attribute.getDocumentationAsHtml());
            if (!doc.isEmpty()) {
                description.append("\n").append(doc);
            }
            items.add(new CompletionItem.Builder(name, name, CompletionItemType.ATTRIBUTE)
                    .description(description.toString())
                    .dataType(attribute.getElementType())
                    .required(attribute.isMandatory())
                    .relevanceScore(attribute.isMandatory() ? 150 : 100)
                    .build());
        }
        return items;
    }

    /**
     * Gets attribute value completions: the enumeration values of the attribute's type, or
     * {@code true}/{@code false} for a boolean.
     */
    private List<CompletionItem> getAttributeValueCompletions(XmlContext context, XsdDocumentationData xsdData) {
        List<CompletionItem> items = new ArrayList<>();
        String elementPath = attributeOwnerPath(context);
        String attributeName = context.getCurrentAttribute();
        if (elementPath == null || attributeName == null) {
            return items;
        }
        XsdExtendedElement elementInfo = xsdData.getExtendedXsdElementMap().get(elementPath);
        if (elementInfo == null) {
            elementInfo = schemaProvider.findBestMatchingElement(elementPath);
        }
        String local = attributeName.substring(attributeName.indexOf(':') + 1);
        XsdExtendedElement attribute = XsdAttributeLookup.declaredAttributes(xsdData, elementInfo).stream()
                .filter(a -> local.equals(XsdAttributeLookup.attributeName(a)))
                .findFirst().orElse(null);
        if (attribute == null) {
            return items;
        }

        List<String> values = List.of();
        String dataType = attribute.getElementType();
        XsdExtendedElement.RestrictionInfo restriction = attribute.getRestrictionInfo();
        if (restriction != null && restriction.facets() != null && restriction.facets().get("enumeration") != null) {
            values = restriction.facets().get("enumeration");
            dataType = restriction.base();
        } else if (isBoolean(dataType) || (restriction != null && isBoolean(restriction.base()))) {
            values = List.of("true", "false");
        }
        for (String value : values) {
            items.add(new CompletionItem.Builder(value, value, CompletionItemType.VALUE)
                    .description("Value of " + local + " from XSD")
                    .dataType(dataType)
                    .build());
        }
        return items;
    }

    private static boolean isBoolean(String type) {
        return type != null && "boolean".equals(type.substring(type.indexOf(':') + 1));
    }

    /**
     * The XPath of the element whose start tag the caret is in. The context's XPath already ends
     * with it, except for a self-closing tag ({@code <a |/>}), which the analyzer does not push.
     */
    private static String attributeOwnerPath(XmlContext context) {
        String path = context.getXPath();
        String element = context.getCurrentElement();
        if (element == null || element.isEmpty()) {
            return path == null || path.isEmpty() ? null : path;
        }
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/" + DirectChildScanner.localName(element);
        }
        // The XPath holds local names
        String local = DirectChildScanner.localName(element);
        String last = path.substring(path.lastIndexOf('/') + 1);
        return last.equals(local) ? path : path + "/" + local;
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
