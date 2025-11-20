package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;

import java.util.ArrayList;
import java.util.List;

/**
 * Completion provider for XSLT documents.
 * Provides IntelliSense for XSLT 1.0/2.0/3.0 elements and attributes.
 *
 * <p>Supports XSLT elements like:</p>
 * <ul>
 *   <li>stylesheet, template, apply-templates, call-template</li>
 *   <li>for-each, if, choose, when, otherwise</li>
 *   <li>value-of, copy-of, variable, param</li>
 *   <li>element, attribute, text, comment, processing-instruction</li>
 * </ul>
 */
public class XsltCompletionProvider implements CompletionProvider {

    private static final Logger logger = LogManager.getLogger(XsltCompletionProvider.class);

    private static final String XSLT_NS = "http://www.w3.org/1999/XSL/Transform";

    // XSLT elements
    private static final String[] XSLT_ELEMENTS = {
        // Structure
        "stylesheet", "transform", "template", "include", "import",
        // Flow control
        "apply-templates", "call-template", "for-each", "if", "choose", "when", "otherwise",
        // Variables and parameters
        "variable", "param", "with-param",
        // Output
        "value-of", "copy", "copy-of", "text", "element", "attribute", "comment", "processing-instruction",
        // Sorting and numbering
        "sort", "number", "key", "decimal-format",
        // Functions
        "function", "result-document", "analyze-string", "matching-substring", "non-matching-substring",
        // Misc
        "output", "preserve-space", "strip-space", "namespace-alias",
        "message", "fallback", "document"
    };

    // Common XSLT attributes
    private static final String[] STYLESHEET_ATTRIBUTES = {"version", "xmlns:xsl", "exclude-result-prefixes"};
    private static final String[] TEMPLATE_ATTRIBUTES = {"match", "name", "mode", "priority", "as"};
    private static final String[] APPLY_TEMPLATES_ATTRIBUTES = {"select", "mode"};
    private static final String[] CALL_TEMPLATE_ATTRIBUTES = {"name"};
    private static final String[] FOR_EACH_ATTRIBUTES = {"select"};
    private static final String[] IF_ATTRIBUTES = {"test"};
    private static final String[] WHEN_ATTRIBUTES = {"test"};
    private static final String[] VARIABLE_PARAM_ATTRIBUTES = {"name", "select", "as"};
    private static final String[] VALUE_OF_ATTRIBUTES = {"select", "separator", "disable-output-escaping"};
    private static final String[] SORT_ATTRIBUTES = {"select", "order", "data-type", "case-order", "lang"};
    private static final String[] ELEMENT_ATTRIBUTES = {"name", "namespace", "use-attribute-sets"};
    private static final String[] ATTRIBUTE_ATTRIBUTES = {"name", "namespace", "select", "separator"};

    @Override
    public boolean canProvideCompletions(XmlContext context, EditorMode mode) {
        // Only provide for XSLT mode
        return mode == EditorMode.XSLT;
    }

    @Override
    public List<CompletionItem> getCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();

        switch (context.getType()) {
            case ELEMENT:
                items.addAll(getElementCompletions(context));
                break;
            case ATTRIBUTE:
                items.addAll(getAttributeCompletions(context));
                break;
            case ATTRIBUTE_VALUE:
                items.addAll(getAttributeValueCompletions(context));
                break;
            default:
                break;
        }

        logger.debug("XsltProvider returned {} items for context {}", items.size(), context.getType());
        return items;
    }

    /**
     * Gets element completions.
     */
    private List<CompletionItem> getElementCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();

        String currentElement = context.getCurrentElement();

        for (String element : XSLT_ELEMENTS) {
            // Filter based on parent context
            if (isElementValidInContext(element, currentElement)) {
                CompletionItem item = new CompletionItem.Builder(
                    "xsl:" + element,
                    "<xsl:" + element + "></xsl:" + element + ">",
                    CompletionItemType.ELEMENT
                )
                .description(getElementDescription(element))
                .build();

                items.add(item);
            }
        }

        return items;
    }

    /**
     * Gets attribute completions.
     */
    private List<CompletionItem> getAttributeCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();

        String currentElement = context.getCurrentElement();
        // Remove xsl: prefix if present
        if (currentElement != null && currentElement.startsWith("xsl:")) {
            currentElement = currentElement.substring(4);
        }

        String[] attributes = getAttributesForElement(currentElement);

        for (String attr : attributes) {
            CompletionItem item = new CompletionItem.Builder(
                attr,
                attr + "=\"\"",
                CompletionItemType.ATTRIBUTE
            )
            .description(getAttributeDescription(currentElement, attr))
            .build();

            items.add(item);
        }

        return items;
    }

    /**
     * Gets attribute value completions.
     */
    private List<CompletionItem> getAttributeValueCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();

        String currentAttribute = context.getCurrentAttribute();

        // Provide some common values
        if ("version".equals(currentAttribute)) {
            items.add(createValueItem("1.0", "XSLT 1.0"));
            items.add(createValueItem("2.0", "XSLT 2.0"));
            items.add(createValueItem("3.0", "XSLT 3.0"));
        } else if ("order".equals(currentAttribute)) {
            items.add(createValueItem("ascending", "Ascending order"));
            items.add(createValueItem("descending", "Descending order"));
        } else if ("data-type".equals(currentAttribute)) {
            items.add(createValueItem("text", "Sort as text"));
            items.add(createValueItem("number", "Sort as number"));
        } else if ("disable-output-escaping".equals(currentAttribute)) {
            items.add(createValueItem("yes", "Disable escaping"));
            items.add(createValueItem("no", "Enable escaping"));
        } else if ("method".equals(currentAttribute)) {
            items.add(createValueItem("xml", "XML output method"));
            items.add(createValueItem("html", "HTML output method"));
            items.add(createValueItem("xhtml", "XHTML output method"));
            items.add(createValueItem("text", "Text output method"));
        }

        return items;
    }

    /**
     * Checks if an element is valid in the current context.
     */
    private boolean isElementValidInContext(String element, String parent) {
        // Root elements
        if (parent == null || parent.isEmpty()) {
            return "stylesheet".equals(element) || "transform".equals(element);
        }

        // Stylesheet/transform children
        if ("stylesheet".equals(parent) || "transform".equals(parent)) {
            return List.of("template", "include", "import", "output", "key",
                          "decimal-format", "variable", "param", "function",
                          "namespace-alias", "preserve-space", "strip-space").contains(element);
        }

        // Template children (most elements are valid)
        if ("template".equals(parent)) {
            return !List.of("stylesheet", "transform", "template", "include", "import").contains(element);
        }

        return true; // Default: allow all
    }

    /**
     * Gets attributes for a specific element.
     */
    private String[] getAttributesForElement(String element) {
        return switch (element) {
            case "stylesheet", "transform" -> STYLESHEET_ATTRIBUTES;
            case "template" -> TEMPLATE_ATTRIBUTES;
            case "apply-templates" -> APPLY_TEMPLATES_ATTRIBUTES;
            case "call-template" -> CALL_TEMPLATE_ATTRIBUTES;
            case "for-each" -> FOR_EACH_ATTRIBUTES;
            case "if" -> IF_ATTRIBUTES;
            case "when" -> WHEN_ATTRIBUTES;
            case "variable", "param" -> VARIABLE_PARAM_ATTRIBUTES;
            case "value-of" -> VALUE_OF_ATTRIBUTES;
            case "sort" -> SORT_ATTRIBUTES;
            case "element" -> ELEMENT_ATTRIBUTES;
            case "attribute" -> ATTRIBUTE_ATTRIBUTES;
            default -> new String[0];
        };
    }

    /**
     * Gets description for an element.
     */
    private String getElementDescription(String element) {
        return switch (element) {
            case "stylesheet", "transform" -> "Root element of XSLT stylesheet";
            case "template" -> "Template rule for transformation";
            case "apply-templates" -> "Apply templates to nodes";
            case "call-template" -> "Call named template";
            case "for-each" -> "Iterate over node set";
            case "if" -> "Conditional processing";
            case "choose" -> "Multiple conditional processing";
            case "when" -> "Conditional branch in choose";
            case "otherwise" -> "Default branch in choose";
            case "variable" -> "Declare variable";
            case "param" -> "Declare parameter";
            case "value-of" -> "Output XPath expression value";
            case "copy" -> "Shallow copy current node";
            case "copy-of" -> "Deep copy nodes";
            case "element" -> "Create element";
            case "attribute" -> "Create attribute";
            case "text" -> "Create text node";
            case "comment" -> "Create comment";
            default -> "XSLT element: " + element;
        };
    }

    /**
     * Gets description for an attribute.
     */
    private String getAttributeDescription(String element, String attribute) {
        if ("match".equals(attribute)) {
            return "XPath pattern to match nodes";
        } else if ("select".equals(attribute)) {
            return "XPath expression to select nodes";
        } else if ("test".equals(attribute)) {
            return "XPath expression to test condition";
        } else if ("name".equals(attribute)) {
            return "Name identifier";
        } else if ("mode".equals(attribute)) {
            return "Processing mode";
        }
        return attribute + " attribute";
    }

    /**
     * Creates a value completion item.
     */
    private CompletionItem createValueItem(String value, String description) {
        return new CompletionItem.Builder(value, value, CompletionItemType.VALUE)
            .description(description)
            .build();
    }

    @Override
    public int getPriority() {
        return 90; // High priority for XSLT mode
    }

    @Override
    public String getName() {
        return "XsltCompletionProvider";
    }
}
