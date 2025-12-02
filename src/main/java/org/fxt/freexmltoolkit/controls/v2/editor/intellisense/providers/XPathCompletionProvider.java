package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XmlDocumentElementExtractor;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.xpath.XPathFunctionLibrary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Provides XPath/XQuery completions based on the current context.
 * Combines suggestions from:
 * <ul>
 *   <li>XML document elements and attributes</li>
 *   <li>XPath functions, axes, and operators</li>
 *   <li>XQuery keywords (when in XQuery mode)</li>
 * </ul>
 */
public class XPathCompletionProvider {

    private static final Logger logger = LogManager.getLogger(XPathCompletionProvider.class);

    // Priority scores for different completion types
    private static final int SCORE_CONTEXT_SPECIFIC = 150;
    private static final int SCORE_XML_ELEMENTS = 100;
    private static final int SCORE_FUNCTIONS = 80;
    private static final int SCORE_AXES = 70;
    private static final int SCORE_NODE_TESTS = 65;
    private static final int SCORE_OPERATORS = 50;
    private static final int SCORE_XQUERY_KEYWORDS = 90;

    private final XmlDocumentElementExtractor elementExtractor;
    private final boolean isXQueryMode;

    /**
     * Creates a new XPath completion provider.
     *
     * @param elementExtractor the extractor for XML document elements
     * @param isXQueryMode     true if XQuery mode (includes FLWOR keywords)
     */
    public XPathCompletionProvider(XmlDocumentElementExtractor elementExtractor, boolean isXQueryMode) {
        this.elementExtractor = elementExtractor;
        this.isXQueryMode = isXQueryMode;
    }

    /**
     * Gets completions for the given XPath context.
     *
     * @param context the analyzed XPath context
     * @return list of completion items, sorted by relevance
     */
    public List<CompletionItem> getCompletions(XPathEditorContext context) {
        if (context == null) {
            return List.of();
        }

        List<CompletionItem> items = new ArrayList<>();
        String prefix = context.getCurrentToken();
        XPathContextType contextType = context.getContextType();

        logger.debug("Getting completions for context: {}, prefix: '{}'", contextType, prefix);

        switch (contextType) {
            case PATH_START -> {
                // At the start: show elements, axes, functions
                items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS));
                items.addAll(XPathFunctionLibrary.getAxisCompletions(prefix, SCORE_AXES));
                items.addAll(XPathFunctionLibrary.getFunctionCompletions(prefix, SCORE_FUNCTIONS));
                if (isXQueryMode) {
                    items.addAll(XPathFunctionLibrary.getXQueryKeywordCompletions(prefix, SCORE_XQUERY_KEYWORDS));
                }
            }

            case AFTER_SLASH -> {
                // After /: show only valid children based on the XPath path
                items.addAll(getPathAwareElementCompletions(context, prefix, SCORE_CONTEXT_SPECIFIC));
                items.addAll(XPathFunctionLibrary.getAxisCompletions(prefix, SCORE_AXES));
                items.addAll(XPathFunctionLibrary.getNodeTestCompletions(prefix, SCORE_NODE_TESTS));
            }

            case AFTER_DOUBLE_SLASH -> {
                // After //: show all elements (descendant-or-self can match any element)
                items.addAll(getElementCompletions(prefix, SCORE_CONTEXT_SPECIFIC));
                items.addAll(XPathFunctionLibrary.getAxisCompletions(prefix, SCORE_AXES));
                items.addAll(XPathFunctionLibrary.getNodeTestCompletions(prefix, SCORE_NODE_TESTS));
            }

            case AFTER_AT -> {
                // After @: show attributes only
                items.addAll(getAttributeCompletions(prefix, SCORE_CONTEXT_SPECIFIC));
            }

            case AFTER_AXIS -> {
                // After axis::: show elements, node tests, *
                items.addAll(getElementCompletions(prefix, SCORE_CONTEXT_SPECIFIC));
                items.addAll(XPathFunctionLibrary.getNodeTestCompletions(prefix, SCORE_NODE_TESTS));
            }

            case IN_PREDICATE -> {
                // In predicate: show functions, attributes, operators, elements
                items.addAll(XPathFunctionLibrary.getFunctionCompletions(prefix, SCORE_CONTEXT_SPECIFIC));
                items.addAll(getAttributeCompletions(prefix, SCORE_XML_ELEMENTS));
                items.addAll(XPathFunctionLibrary.getOperatorCompletions(prefix, SCORE_OPERATORS));
                items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS - 10));
            }

            case IN_FUNCTION_ARGS -> {
                // In function arguments: show elements, attributes, functions
                items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS));
                items.addAll(getAttributeCompletions(prefix, SCORE_XML_ELEMENTS - 5));
                items.addAll(XPathFunctionLibrary.getFunctionCompletions(prefix, SCORE_FUNCTIONS));
            }

            case AFTER_OPERATOR -> {
                // After operator: show elements, functions
                items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS));
                items.addAll(XPathFunctionLibrary.getFunctionCompletions(prefix, SCORE_FUNCTIONS));
            }

            case AFTER_DOLLAR -> {
                // After $: show variable suggestions (in XQuery mode)
                if (isXQueryMode) {
                    items.addAll(getVariableSuggestions(prefix, SCORE_CONTEXT_SPECIFIC));
                }
            }

            case AFTER_FOR, AFTER_LET -> {
                // After for/let: suggest $variable pattern
                items.add(createVariableSnippet(prefix));
            }

            case AFTER_BINDING -> {
                // After in/: = : show elements, paths
                items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS));
                items.addAll(XPathFunctionLibrary.getFunctionCompletions(prefix, SCORE_FUNCTIONS));
            }

            case AFTER_WHERE -> {
                // After where: show comparison functions, attributes
                items.addAll(XPathFunctionLibrary.getFunctionCompletions(prefix, SCORE_CONTEXT_SPECIFIC));
                items.addAll(getAttributeCompletions(prefix, SCORE_XML_ELEMENTS));
                items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS - 10));
            }

            case AFTER_RETURN -> {
                // After return: show elements, functions
                items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS));
                items.addAll(XPathFunctionLibrary.getFunctionCompletions(prefix, SCORE_FUNCTIONS));
            }

            case XQUERY_BODY -> {
                // XQuery body: show FLWOR keywords, elements, functions
                items.addAll(XPathFunctionLibrary.getXQueryKeywordCompletions(prefix, SCORE_XQUERY_KEYWORDS));
                items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS));
                items.addAll(XPathFunctionLibrary.getFunctionCompletions(prefix, SCORE_FUNCTIONS));
            }

            case IN_STRING_LITERAL, IN_COMMENT -> {
                // No completions in strings or comments
                return List.of();
            }

            case UNKNOWN -> {
                // Fallback: show all common completions
                items.addAll(getAllCompletions(prefix));
            }
        }

        // Sort by relevance score (descending) then by label
        items.sort(Comparator
                .comparingInt(CompletionItem::getRelevanceScore).reversed()
                .thenComparing(CompletionItem::getLabel));

        // Limit results - increased to 200 to ensure diverse types are included
        // (elements, functions, axes, operators should all be represented)
        if (items.size() > 200) {
            items = new ArrayList<>(items.subList(0, 200));
        }

        logger.debug("Returning {} completions", items.size());
        return items;
    }

    /**
     * Gets element completions from the XML document.
     */
    private List<CompletionItem> getElementCompletions(String prefix, int baseScore) {
        if (elementExtractor == null) {
            return List.of();
        }
        return elementExtractor.getElementCompletions(prefix, baseScore);
    }

    /**
     * Gets path-aware element completions based on the current XPath position.
     * <ul>
     *   <li>At root (/): shows only the root element</li>
     *   <li>After element path (/root/child/): shows only valid children of the last element</li>
     *   <li>Falls back to all elements if tree structure not available</li>
     * </ul>
     *
     * @param context   the XPath editor context with path information
     * @param prefix    the current token prefix for filtering
     * @param baseScore the base relevance score
     * @return list of completion items for valid elements at this path position
     */
    private List<CompletionItem> getPathAwareElementCompletions(XPathEditorContext context, String prefix, int baseScore) {
        if (elementExtractor == null) {
            return List.of();
        }

        // If at root level (just "/"), show only the root element
        if (context.isAtRoot()) {
            logger.debug("At root level - showing only root element");
            return elementExtractor.getChildElementCompletions(null, prefix, baseScore);
        }

        // If we have a path, get the last element and show its children
        String lastElement = context.getLastElementInPath();
        if (lastElement != null && !lastElement.isEmpty()) {
            logger.debug("Path-aware completion for parent element: {}", lastElement);
            List<CompletionItem> children = elementExtractor.getChildElementCompletions(lastElement, prefix, baseScore);

            // If no children found, fall back to all elements (the tree may be incomplete)
            if (children.isEmpty()) {
                logger.debug("No children found for {}, falling back to all elements", lastElement);
                return elementExtractor.getElementCompletions(prefix, baseScore);
            }
            return children;
        }

        // Fallback to all elements
        return elementExtractor.getElementCompletions(prefix, baseScore);
    }

    /**
     * Gets attribute completions from the XML document.
     */
    private List<CompletionItem> getAttributeCompletions(String prefix, int baseScore) {
        if (elementExtractor == null) {
            return List.of();
        }
        return elementExtractor.getAttributeCompletions(prefix, baseScore);
    }

    /**
     * Gets variable suggestions for XQuery.
     */
    private List<CompletionItem> getVariableSuggestions(String prefix, int baseScore) {
        List<CompletionItem> items = new ArrayList<>();

        // Common variable names
        String[] commonVars = {"x", "i", "item", "node", "doc", "result", "value", "text"};
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";

        for (String var : commonVars) {
            if (lowerPrefix.isEmpty() || var.startsWith(lowerPrefix)) {
                items.add(new CompletionItem.Builder(
                        "$" + var,
                        "$" + var,
                        org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType.XPATH_VARIABLE
                )
                        .description("Variable")
                        .relevanceScore(baseScore)
                        .build());
            }
        }

        return items;
    }

    /**
     * Creates a variable snippet completion.
     */
    private CompletionItem createVariableSnippet(String prefix) {
        String varName = prefix != null && !prefix.isEmpty() ? prefix : "x";
        return new CompletionItem.Builder(
                "$" + varName,
                "$" + varName,
                org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType.XPATH_VARIABLE
        )
                .description("Variable declaration")
                .relevanceScore(SCORE_CONTEXT_SPECIFIC)
                .build();
    }

    /**
     * Gets all available completions (fallback).
     */
    private List<CompletionItem> getAllCompletions(String prefix) {
        List<CompletionItem> items = new ArrayList<>();

        items.addAll(getElementCompletions(prefix, SCORE_XML_ELEMENTS));
        items.addAll(getAttributeCompletions(prefix, SCORE_XML_ELEMENTS - 10));

        if (isXQueryMode) {
            items.addAll(XPathFunctionLibrary.getAllXQueryCompletions(prefix, SCORE_FUNCTIONS));
        } else {
            items.addAll(XPathFunctionLibrary.getAllXPathCompletions(prefix, SCORE_FUNCTIONS));
        }

        return items;
    }

    /**
     * Returns whether this provider is in XQuery mode.
     */
    public boolean isXQueryMode() {
        return isXQueryMode;
    }
}
