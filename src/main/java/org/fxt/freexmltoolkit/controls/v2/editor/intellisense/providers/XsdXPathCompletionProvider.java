package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XmlDocumentElementExtractor;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XsdSchemaElementExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * XPath/XQuery completion provider specialized for XSD documents.
 * <p>
 * Extends the base XPathCompletionProvider to include XSD-specific completions:
 * <ul>
 *   <li>XSD schema element names (xs:element/@name)</li>
 *   <li>XSD schema type names (xs:complexType/@name, xs:simpleType/@name)</li>
 *   <li>XSD common attribute names (name, type, ref, minOccurs, etc.)</li>
 *   <li>XSD built-in types (xs:string, xs:integer, etc.)</li>
 * </ul>
 */
public class XsdXPathCompletionProvider extends XPathCompletionProvider {

    private static final Logger logger = LogManager.getLogger(XsdXPathCompletionProvider.class);

    // Priority scores for XSD-specific completions
    private static final int SCORE_XSD_ELEMENTS = 85;
    private static final int SCORE_XSD_TYPES = 80;
    private static final int SCORE_XSD_ATTRIBUTES = 75;
    private static final int SCORE_XSD_BUILTIN_TYPES = 70;
    private static final int SCORE_XSD_GROUPS = 65;

    private final XsdSchemaElementExtractor xsdExtractor;

    /**
     * Creates a new XSD-aware XPath completion provider.
     *
     * @param elementExtractor the extractor for XML document elements (XSD as XML)
     * @param xsdExtractor     the extractor for XSD-specific schema constructs
     * @param isXQueryMode     true if XQuery mode (includes FLWOR keywords)
     */
    public XsdXPathCompletionProvider(XmlDocumentElementExtractor elementExtractor,
                                       XsdSchemaElementExtractor xsdExtractor,
                                       boolean isXQueryMode) {
        super(elementExtractor, isXQueryMode);
        this.xsdExtractor = xsdExtractor;
    }

    /**
     * Gets completions for the given XPath context, enhanced with XSD-specific suggestions.
     *
     * @param context the analyzed XPath context
     * @return list of completion items, sorted by relevance
     */
    @Override
    public List<CompletionItem> getCompletions(XPathEditorContext context) {
        // Get base completions from parent
        List<CompletionItem> items = new ArrayList<>(super.getCompletions(context));

        if (xsdExtractor == null) {
            return items;
        }

        String prefix = context != null ? context.getCurrentToken() : "";
        XPathContextType contextType = context != null ? context.getContextType() : XPathContextType.UNKNOWN;

        logger.debug("Adding XSD-specific completions for context: {}, prefix: '{}'", contextType, prefix);

        // Add XSD-specific completions based on context
        switch (contextType) {
            case PATH_START, AFTER_SLASH, AFTER_DOUBLE_SLASH, AFTER_AXIS -> {
                // Element contexts: add XSD element names and types
                items.addAll(xsdExtractor.getSchemaElementCompletions(prefix, SCORE_XSD_ELEMENTS));
                items.addAll(xsdExtractor.getSchemaTypeCompletions(prefix, SCORE_XSD_TYPES));
                items.addAll(xsdExtractor.getGroupCompletions(prefix, SCORE_XSD_GROUPS));
            }

            case AFTER_AT -> {
                // Attribute context: add XSD-specific attributes
                items.addAll(xsdExtractor.getXsdAttributeCompletions(prefix, SCORE_XSD_ATTRIBUTES));
            }

            case IN_PREDICATE, IN_FUNCTION_ARGS -> {
                // Predicate/function context: add attributes and element names
                items.addAll(xsdExtractor.getXsdAttributeCompletions(prefix, SCORE_XSD_ATTRIBUTES));
                items.addAll(xsdExtractor.getSchemaElementCompletions(prefix, SCORE_XSD_ELEMENTS - 10));
                // Also add built-in types for type comparisons
                items.addAll(xsdExtractor.getBuiltinTypeCompletions(prefix, SCORE_XSD_BUILTIN_TYPES));
            }

            case XQUERY_BODY, AFTER_OPERATOR, AFTER_RETURN, AFTER_BINDING -> {
                // General expression contexts: add schema elements
                items.addAll(xsdExtractor.getSchemaElementCompletions(prefix, SCORE_XSD_ELEMENTS));
                items.addAll(xsdExtractor.getSchemaTypeCompletions(prefix, SCORE_XSD_TYPES));
            }

            case UNKNOWN -> {
                // Fallback: add all XSD-specific completions
                items.addAll(xsdExtractor.getSchemaElementCompletions(prefix, SCORE_XSD_ELEMENTS));
                items.addAll(xsdExtractor.getSchemaTypeCompletions(prefix, SCORE_XSD_TYPES));
                items.addAll(xsdExtractor.getXsdAttributeCompletions(prefix, SCORE_XSD_ATTRIBUTES));
                items.addAll(xsdExtractor.getGroupCompletions(prefix, SCORE_XSD_GROUPS));
            }

            default -> {
                // Other contexts: no additional XSD completions
            }
        }

        // Re-sort by relevance score (descending) then by label
        items.sort(Comparator
                .comparingInt(CompletionItem::getRelevanceScore).reversed()
                .thenComparing(CompletionItem::getLabel));

        // Limit results
        if (items.size() > 250) {
            items = new ArrayList<>(items.subList(0, 250));
        }

        logger.debug("Returning {} completions (including XSD-specific)", items.size());
        return items;
    }

    /**
     * Returns the XSD schema element extractor.
     */
    public XsdSchemaElementExtractor getXsdExtractor() {
        return xsdExtractor;
    }
}
