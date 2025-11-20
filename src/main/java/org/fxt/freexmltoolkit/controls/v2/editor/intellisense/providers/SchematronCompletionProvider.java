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
 * Completion provider for Schematron documents.
 * Provides IntelliSense for Schematron elements and attributes.
 *
 * <p>Supports ISO Schematron elements like:</p>
 * <ul>
 *   <li>schema, pattern, rule, assert, report</li>
 *   <li>title, ns, let, value-of, name</li>
 *   <li>Attributes: context, test, id, flag, etc.</li>
 * </ul>
 */
public class SchematronCompletionProvider implements CompletionProvider {

    private static final Logger logger = LogManager.getLogger(SchematronCompletionProvider.class);

    private static final String SCHEMATRON_NS = "http://purl.oclc.org/dsdl/schematron";

    // Schematron elements
    private static final String[] SCHEMATRON_ELEMENTS = {
        "schema", "pattern", "rule", "assert", "report",
        "title", "ns", "let", "value-of", "name",
        "extends", "param", "phase", "active",
        "diagnostics", "diagnostic", "properties", "property",
        "p", "dir", "emph", "span"
    };

    // Common Schematron attributes
    private static final String[] SCHEMA_ATTRIBUTES = {"id", "schemaVersion", "defaultPhase", "queryBinding", "icon", "see", "fpi"};
    private static final String[] PATTERN_ATTRIBUTES = {"id", "abstract", "is-a"};
    private static final String[] RULE_ATTRIBUTES = {"context", "id", "abstract", "role", "flag"};
    private static final String[] ASSERT_REPORT_ATTRIBUTES = {"test", "id", "role", "flag", "diagnostics", "properties", "subject"};
    private static final String[] NS_ATTRIBUTES = {"prefix", "uri"};
    private static final String[] LET_ATTRIBUTES = {"name", "value"};
    private static final String[] VALUE_OF_ATTRIBUTES = {"select"};

    @Override
    public boolean canProvideCompletions(XmlContext context, EditorMode mode) {
        // Only provide for SCHEMATRON mode
        return mode == EditorMode.SCHEMATRON;
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

        logger.debug("SchematronProvider returned {} items for context {}", items.size(), context.getType());
        return items;
    }

    /**
     * Gets element completions.
     */
    private List<CompletionItem> getElementCompletions(XmlContext context) {
        List<CompletionItem> items = new ArrayList<>();

        String currentElement = context.getCurrentElement();

        for (String element : SCHEMATRON_ELEMENTS) {
            // Filter based on parent context
            if (isElementValidInContext(element, currentElement)) {
                CompletionItem item = new CompletionItem.Builder(
                    element,
                    "<" + element + "></" + element + ">",
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
        if ("queryBinding".equals(currentAttribute)) {
            items.add(createValueItem("xslt", "XSLT 1.0 query binding"));
            items.add(createValueItem("xslt2", "XSLT 2.0 query binding"));
            items.add(createValueItem("xpath", "XPath 1.0 query binding"));
            items.add(createValueItem("xpath2", "XPath 2.0 query binding"));
        } else if ("role".equals(currentAttribute)) {
            items.add(createValueItem("error", "Error severity"));
            items.add(createValueItem("warning", "Warning severity"));
            items.add(createValueItem("info", "Information severity"));
        } else if ("flag".equals(currentAttribute)) {
            items.add(createValueItem("fatal", "Fatal error"));
            items.add(createValueItem("error", "Error"));
            items.add(createValueItem("warning", "Warning"));
        }

        return items;
    }

    /**
     * Checks if an element is valid in the current context.
     */
    private boolean isElementValidInContext(String element, String parent) {
        // Root element
        if (parent == null || parent.isEmpty()) {
            return "schema".equals(element);
        }

        // Schema children
        if ("schema".equals(parent)) {
            return "pattern".equals(element) || "title".equals(element) ||
                   "ns".equals(element) || "phase".equals(element) ||
                   "diagnostics".equals(element) || "let".equals(element);
        }

        // Pattern children
        if ("pattern".equals(parent)) {
            return "rule".equals(element) || "title".equals(element) ||
                   "let".equals(element) || "param".equals(element);
        }

        // Rule children
        if ("rule".equals(parent)) {
            return "assert".equals(element) || "report".equals(element) ||
                   "let".equals(element) || "extends".equals(element);
        }

        // Assert/Report children
        if ("assert".equals(parent) || "report".equals(parent)) {
            return "value-of".equals(element) || "name".equals(element) ||
                   "diagnostics".equals(element) || "emph".equals(element) ||
                   "span".equals(element) || "dir".equals(element);
        }

        return true; // Default: allow all
    }

    /**
     * Gets attributes for a specific element.
     */
    private String[] getAttributesForElement(String element) {
        return switch (element) {
            case "schema" -> SCHEMA_ATTRIBUTES;
            case "pattern" -> PATTERN_ATTRIBUTES;
            case "rule" -> RULE_ATTRIBUTES;
            case "assert", "report" -> ASSERT_REPORT_ATTRIBUTES;
            case "ns" -> NS_ATTRIBUTES;
            case "let" -> LET_ATTRIBUTES;
            case "value-of" -> VALUE_OF_ATTRIBUTES;
            default -> new String[0];
        };
    }

    /**
     * Gets description for an element.
     */
    private String getElementDescription(String element) {
        return switch (element) {
            case "schema" -> "Root element of a Schematron schema";
            case "pattern" -> "Group of rules for validation";
            case "rule" -> "Validation rule with context";
            case "assert" -> "Assertion that must be true";
            case "report" -> "Condition that triggers a message";
            case "title" -> "Human-readable title";
            case "ns" -> "Namespace declaration";
            case "let" -> "Variable declaration";
            case "value-of" -> "Insert XPath expression value";
            case "name" -> "Insert element/attribute name";
            default -> "Schematron element: " + element;
        };
    }

    /**
     * Gets description for an attribute.
     */
    private String getAttributeDescription(String element, String attribute) {
        if ("context".equals(attribute)) {
            return "XPath expression for rule context";
        } else if ("test".equals(attribute)) {
            return "XPath expression to test";
        } else if ("id".equals(attribute)) {
            return "Unique identifier";
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
        return 90; // High priority for Schematron mode
    }

    @Override
    public String getName() {
        return "SchematronCompletionProvider";
    }
}
