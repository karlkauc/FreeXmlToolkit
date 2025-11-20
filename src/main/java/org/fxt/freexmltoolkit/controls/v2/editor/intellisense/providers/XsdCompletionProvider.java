package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.core.EditorMode;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextType;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XmlContext;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        }

        logger.debug("XSD provider returned {} completions for context: {}", items.size(), context.getType());
        return items;
    }

    /**
     * Gets element completions based on XSD schema.
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

        // Fallback to best matching
        if (parentInfo == null) {
            parentInfo = schemaProvider.findBestMatchingElement(parentPath);
        }

        if (parentInfo != null && parentInfo.getChildren() != null) {
            // Add all child elements
            int index = 0;
            for (String childXpath : parentInfo.getChildren()) {
                XsdExtendedElement childInfo = xsdData.getExtendedXsdElementMap().get(childXpath);
                if (childInfo != null && childInfo.getElementName() != null) {
                    CompletionItem item = createElementCompletionItem(childInfo, index++);
                    items.add(item);
                }
            }
        }

        return items;
    }

    /**
     * Creates a completion item from XSD element info.
     */
    private CompletionItem createElementCompletionItem(XsdExtendedElement elementInfo, int index) {
        String elementName = elementInfo.getElementName();

        CompletionItem.Builder builder = new CompletionItem.Builder(
                elementName,
                elementName,
                CompletionItemType.ELEMENT
        );

        // Add documentation if available
        // TODO: XsdExtendedElement doesn't have getDocumentation() yet - implement later
        // For now, use element name as description
        builder.description("Element: " + elementName);

        // Add type information
        if (elementInfo.getElementType() != null) {
            builder.dataType(elementInfo.getElementType());
        }

        // Mark required elements
        boolean isRequired = elementInfo.isMandatory();
        builder.required(isRequired);

        // Set relevance score (required first, preserve XSD order)
        int baseScore = isRequired ? 150 : 100;
        builder.relevanceScore(baseScore + (1000 - index));

        return builder.build();
    }

    /**
     * Gets attribute completions.
     * TODO: Implement XSD attribute completion
     */
    private List<CompletionItem> getAttributeCompletions(XmlContext context, XsdDocumentationData xsdData) {
        // TODO: Parse XSD for attribute definitions
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
