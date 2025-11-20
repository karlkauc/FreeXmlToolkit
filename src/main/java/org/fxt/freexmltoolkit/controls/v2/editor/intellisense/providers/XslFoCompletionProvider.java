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
 * Completion provider for XSL-FO (Formatting Objects) documents.
 * Provides IntelliSense for XSL-FO elements and attributes used with Apache FOP.
 *
 * <p>Supports XSL-FO elements like:</p>
 * <ul>
 *   <li>root, layout-master-set, page-sequence</li>
 *   <li>simple-page-master, region-body, region-before, region-after</li>
 *   <li>block, inline, table, list-block</li>
 *   <li>external-graphic, page-number, leader</li>
 * </ul>
 */
public class XslFoCompletionProvider implements CompletionProvider {

    private static final Logger logger = LogManager.getLogger(XslFoCompletionProvider.class);

    private static final String FO_NS = "http://www.w3.org/1999/XSL/Format";

    // XSL-FO elements
    private static final String[] FO_ELEMENTS = {
        // Root and structure
        "root", "layout-master-set", "page-sequence", "page-sequence-master",
        // Page layouts
        "simple-page-master", "region-body", "region-before", "region-after",
        "region-start", "region-end",
        // Flows
        "flow", "static-content",
        // Block-level
        "block", "block-container",
        // Inline
        "inline", "inline-container",
        // Tables
        "table", "table-column", "table-header", "table-footer", "table-body",
        "table-row", "table-cell",
        // Lists
        "list-block", "list-item", "list-item-label", "list-item-body",
        // Special
        "external-graphic", "instream-foreign-object",
        "page-number", "page-number-citation", "page-number-citation-last",
        "leader",
        // Wrapper
        "wrapper", "retrieve-marker", "marker",
        // Misc
        "title", "footnote", "footnote-body",
        "basic-link", "multi-toggle", "float"
    };

    // Common attributes by element
    private static final String[] ROOT_ATTRIBUTES = {"media-usage", "xmlns:fo"};
    private static final String[] PAGE_MASTER_ATTRIBUTES = {
        "master-name", "page-height", "page-width",
        "margin-top", "margin-bottom", "margin-left", "margin-right"
    };
    private static final String[] REGION_ATTRIBUTES = {
        "region-name", "extent", "precedence",
        "background-color", "border", "padding"
    };
    private static final String[] PAGE_SEQUENCE_ATTRIBUTES = {
        "master-reference", "initial-page-number", "force-page-count",
        "format", "country", "language"
    };
    private static final String[] FLOW_ATTRIBUTES = {"flow-name"};
    private static final String[] BLOCK_ATTRIBUTES = {
        "font-family", "font-size", "font-weight", "font-style",
        "color", "background-color",
        "text-align", "text-indent", "line-height",
        "space-before", "space-after",
        "margin-top", "margin-bottom", "margin-left", "margin-right",
        "padding-top", "padding-bottom", "padding-left", "padding-right",
        "border", "border-width", "border-style", "border-color",
        "keep-together", "keep-with-next", "keep-with-previous",
        "break-before", "break-after", "id"
    };
    private static final String[] INLINE_ATTRIBUTES = {
        "font-family", "font-size", "font-weight", "font-style",
        "color", "background-color", "text-decoration",
        "baseline-shift", "id"
    };
    private static final String[] TABLE_ATTRIBUTES = {
        "table-layout", "width", "border-collapse", "border-spacing",
        "space-before", "space-after"
    };
    private static final String[] TABLE_CELL_ATTRIBUTES = {
        "number-columns-spanned", "number-rows-spanned",
        "border", "padding", "background-color", "display-align"
    };
    private static final String[] EXTERNAL_GRAPHIC_ATTRIBUTES = {
        "src", "content-width", "content-height", "scaling", "content-type"
    };
    private static final String[] LEADER_ATTRIBUTES = {
        "leader-pattern", "leader-length", "leader-pattern-width"
    };
    private static final String[] BASIC_LINK_ATTRIBUTES = {
        "external-destination", "internal-destination", "show-destination"
    };

    @Override
    public boolean canProvideCompletions(XmlContext context, EditorMode mode) {
        // Provide for XSL-FO mode
        return mode == EditorMode.XSL_FO;
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

        logger.debug("XslFoProvider returned {} items for context {}", items.size(), context.getType());
        return items;
    }

    /**
     * Gets element completions.
     */
    private List<CompletionItem> getElementCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();

        String currentElement = context.getCurrentElement();

        for (String element : FO_ELEMENTS) {
            // Filter based on parent context
            if (isElementValidInContext(element, currentElement)) {
                CompletionItem item = new CompletionItem.Builder(
                    "fo:" + element,
                    "<fo:" + element + "></fo:" + element + ">",
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
        // Remove fo: prefix if present
        if (currentElement != null && currentElement.startsWith("fo:")) {
            currentElement = currentElement.substring(3);
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

        // Provide common values for specific attributes
        switch (currentAttribute) {
            case "font-weight":
                items.add(createValueItem("normal", "Normal weight"));
                items.add(createValueItem("bold", "Bold weight"));
                items.add(createValueItem("bolder", "Bolder weight"));
                items.add(createValueItem("lighter", "Lighter weight"));
                break;
            case "font-style":
                items.add(createValueItem("normal", "Normal style"));
                items.add(createValueItem("italic", "Italic style"));
                items.add(createValueItem("oblique", "Oblique style"));
                break;
            case "text-align":
                items.add(createValueItem("start", "Align to start"));
                items.add(createValueItem("center", "Center align"));
                items.add(createValueItem("end", "Align to end"));
                items.add(createValueItem("justify", "Justify"));
                break;
            case "text-decoration":
                items.add(createValueItem("none", "No decoration"));
                items.add(createValueItem("underline", "Underline"));
                items.add(createValueItem("overline", "Overline"));
                items.add(createValueItem("line-through", "Line through"));
                break;
            case "border-style":
                items.add(createValueItem("solid", "Solid border"));
                items.add(createValueItem("dashed", "Dashed border"));
                items.add(createValueItem("dotted", "Dotted border"));
                items.add(createValueItem("double", "Double border"));
                items.add(createValueItem("none", "No border"));
                break;
            case "break-before", "break-after":
                items.add(createValueItem("auto", "Automatic"));
                items.add(createValueItem("page", "Page break"));
                items.add(createValueItem("column", "Column break"));
                items.add(createValueItem("even-page", "Even page break"));
                items.add(createValueItem("odd-page", "Odd page break"));
                break;
            case "keep-together", "keep-with-next", "keep-with-previous":
                items.add(createValueItem("auto", "Automatic"));
                items.add(createValueItem("always", "Always keep"));
                break;
            case "table-layout":
                items.add(createValueItem("auto", "Auto layout"));
                items.add(createValueItem("fixed", "Fixed layout"));
                break;
            case "border-collapse":
                items.add(createValueItem("collapse", "Collapsed borders"));
                items.add(createValueItem("separate", "Separated borders"));
                break;
            case "leader-pattern":
                items.add(createValueItem("space", "Space pattern"));
                items.add(createValueItem("dots", "Dots pattern"));
                items.add(createValueItem("rule", "Rule pattern"));
                items.add(createValueItem("use-content", "Use content"));
                break;
            case "scaling":
                items.add(createValueItem("uniform", "Uniform scaling"));
                items.add(createValueItem("non-uniform", "Non-uniform scaling"));
                break;
            case "display-align":
                items.add(createValueItem("before", "Align to before"));
                items.add(createValueItem("center", "Center align"));
                items.add(createValueItem("after", "Align to after"));
                break;
        }

        return items;
    }

    /**
     * Checks if an element is valid in the current context.
     */
    private boolean isElementValidInContext(String element, String parent) {
        // Root element
        if (parent == null || parent.isEmpty()) {
            return "root".equals(element);
        }

        // Root children
        if ("root".equals(parent)) {
            return List.of("layout-master-set", "page-sequence").contains(element);
        }

        // Layout master set children
        if ("layout-master-set".equals(parent)) {
            return List.of("simple-page-master", "page-sequence-master").contains(element);
        }

        // Simple page master children
        if ("simple-page-master".equals(parent)) {
            return List.of("region-body", "region-before", "region-after",
                          "region-start", "region-end").contains(element);
        }

        // Page sequence children
        if ("page-sequence".equals(parent)) {
            return List.of("title", "flow", "static-content").contains(element);
        }

        // Table structure
        if ("table".equals(parent)) {
            return List.of("table-column", "table-header", "table-footer", "table-body").contains(element);
        }

        if (List.of("table-header", "table-footer", "table-body").contains(parent)) {
            return "table-row".equals(element);
        }

        if ("table-row".equals(parent)) {
            return "table-cell".equals(element);
        }

        // List structure
        if ("list-block".equals(parent)) {
            return "list-item".equals(element);
        }

        if ("list-item".equals(parent)) {
            return List.of("list-item-label", "list-item-body").contains(element);
        }

        return true; // Default: allow all
    }

    /**
     * Gets attributes for a specific element.
     */
    private String[] getAttributesForElement(String element) {
        return switch (element) {
            case "root" -> ROOT_ATTRIBUTES;
            case "simple-page-master" -> PAGE_MASTER_ATTRIBUTES;
            case "region-body", "region-before", "region-after", "region-start", "region-end" -> REGION_ATTRIBUTES;
            case "page-sequence" -> PAGE_SEQUENCE_ATTRIBUTES;
            case "flow", "static-content" -> FLOW_ATTRIBUTES;
            case "block", "block-container" -> BLOCK_ATTRIBUTES;
            case "inline", "inline-container" -> INLINE_ATTRIBUTES;
            case "table" -> TABLE_ATTRIBUTES;
            case "table-cell" -> TABLE_CELL_ATTRIBUTES;
            case "external-graphic" -> EXTERNAL_GRAPHIC_ATTRIBUTES;
            case "leader" -> LEADER_ATTRIBUTES;
            case "basic-link" -> BASIC_LINK_ATTRIBUTES;
            default -> new String[0];
        };
    }

    /**
     * Gets description for an element.
     */
    private String getElementDescription(String element) {
        return switch (element) {
            case "root" -> "Root element of XSL-FO document";
            case "layout-master-set" -> "Container for page layout definitions";
            case "page-sequence" -> "Sequence of pages with common properties";
            case "simple-page-master" -> "Page layout template";
            case "region-body" -> "Main body region of page";
            case "region-before" -> "Header region of page";
            case "region-after" -> "Footer region of page";
            case "region-start" -> "Left sidebar region (LTR) or right (RTL)";
            case "region-end" -> "Right sidebar region (LTR) or left (RTL)";
            case "flow" -> "Main content flow";
            case "static-content" -> "Static content for headers/footers";
            case "block" -> "Block-level content (paragraph)";
            case "block-container" -> "Container for absolutely positioned blocks";
            case "inline" -> "Inline formatting";
            case "table" -> "Table container";
            case "table-column" -> "Table column definition";
            case "table-header" -> "Table header rows";
            case "table-footer" -> "Table footer rows";
            case "table-body" -> "Table body rows";
            case "table-row" -> "Table row";
            case "table-cell" -> "Table cell";
            case "list-block" -> "List container";
            case "list-item" -> "List item";
            case "list-item-label" -> "List item label (bullet/number)";
            case "list-item-body" -> "List item content";
            case "external-graphic" -> "External image";
            case "page-number" -> "Current page number";
            case "page-number-citation" -> "Reference to page number of target";
            case "leader" -> "Leader dots or rules";
            case "basic-link" -> "Hyperlink";
            default -> "XSL-FO element: " + element;
        };
    }

    /**
     * Gets description for an attribute.
     */
    private String getAttributeDescription(String element, String attribute) {
        if ("master-reference".equals(attribute) || "master-name".equals(attribute)) {
            return "Reference to page master";
        } else if ("flow-name".equals(attribute) || "region-name".equals(attribute)) {
            return "Region name reference";
        } else if (attribute.startsWith("font-")) {
            return "Font property: " + attribute;
        } else if (attribute.startsWith("margin-") || attribute.startsWith("padding-")) {
            return "Spacing property: " + attribute;
        } else if (attribute.startsWith("border-")) {
            return "Border property: " + attribute;
        } else if ("src".equals(attribute)) {
            return "Source URI for external resource";
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
        return 85; // High priority for XSL-FO mode
    }

    @Override
    public String getName() {
        return "XslFoCompletionProvider";
    }
}
