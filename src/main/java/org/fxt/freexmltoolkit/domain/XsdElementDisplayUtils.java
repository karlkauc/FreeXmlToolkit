package org.fxt.freexmltoolkit.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItem;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model.CompletionItemType;

/**
 * Shared utility methods for extracting display information from {@link XsdExtendedElement}.
 * Used by both IntelliSense autocomplete and the properties sidebar "Possible Child Elements" section.
 * <p>
 * All methods are static and have no JavaFX dependencies.
 */
public final class XsdElementDisplayUtils {

    private XsdElementDisplayUtils() {
    }

    /**
     * Builds a cardinality string from minOccurs/maxOccurs.
     * Examples: "1", "0..1", "1..*", "0..*", "2..5"
     */
    public static String buildCardinalityString(XsdExtendedElement elementInfo) {
        org.w3c.dom.Node cardNode = elementInfo.getCardinalityNode();
        org.w3c.dom.Node currentNode = elementInfo.getCurrentNode();

        org.w3c.dom.Node sourceNode = cardNode != null ? cardNode : currentNode;
        if (sourceNode == null) {
            return "";
        }

        String minOccurs = getNodeAttribute(sourceNode, "minOccurs");
        String maxOccurs = getNodeAttribute(sourceNode, "maxOccurs");

        int min = 1;
        int max = 1;
        boolean unbounded = false;

        if (minOccurs != null) {
            try {
                min = Integer.parseInt(minOccurs);
            } catch (NumberFormatException ignored) {
            }
        }

        if (maxOccurs != null) {
            if ("unbounded".equals(maxOccurs)) {
                unbounded = true;
            } else {
                try {
                    max = Integer.parseInt(maxOccurs);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        if (min == 1 && max == 1 && !unbounded) {
            return "1";
        } else if (min == 0 && max == 1) {
            return "0..1";
        } else if (min == 0 && unbounded) {
            return "0..*";
        } else if (min == 1 && unbounded) {
            return "1..*";
        } else if (unbounded) {
            return min + "..*";
        } else if (min == max) {
            return String.valueOf(min);
        } else {
            return min + ".." + max;
        }
    }

    /**
     * Extracts the default value from the element definition.
     * Returns fixed values suffixed with " (fixed)".
     */
    public static String extractDefaultValue(XsdExtendedElement elementInfo) {
        org.w3c.dom.Node currentNode = elementInfo.getCurrentNode();
        if (currentNode == null) {
            return null;
        }

        String defaultVal = getNodeAttribute(currentNode, "default");
        if (defaultVal != null) {
            return defaultVal;
        }

        String fixedVal = getNodeAttribute(currentNode, "fixed");
        if (fixedVal != null) {
            return fixedVal + " (fixed)";
        }

        return null;
    }

    /**
     * Builds facet hints from restriction info.
     * Returns a list like ["pattern", "maxLength:100", "minInclusive:0"].
     * Enumeration facets are skipped (handled separately in examples).
     */
    public static List<String> buildFacetHints(XsdExtendedElement elementInfo) {
        List<String> hints = new ArrayList<>();

        XsdExtendedElement.RestrictionInfo restrictionInfo = elementInfo.getRestrictionInfo();
        if (restrictionInfo == null || restrictionInfo.facets() == null) {
            return hints;
        }

        Map<String, List<String>> facets = restrictionInfo.facets();

        for (Map.Entry<String, List<String>> entry : facets.entrySet()) {
            String facetName = entry.getKey();
            List<String> values = entry.getValue();

            if (values == null || values.isEmpty()) {
                continue;
            }

            switch (facetName) {
                case "pattern":
                    hints.add("pattern");
                    break;
                case "enumeration":
                    break;
                case "minLength":
                case "maxLength":
                case "length":
                case "minInclusive":
                case "maxInclusive":
                case "minExclusive":
                case "maxExclusive":
                case "totalDigits":
                case "fractionDigits":
                    hints.add(facetName + ":" + values.get(0));
                    break;
                case "whiteSpace":
                    hints.add("whiteSpace:" + values.get(0));
                    break;
                default:
                    hints.add(facetName);
                    break;
            }
        }

        return hints;
    }

    /**
     * Extracts example values from enumeration facets, XSD example values, or sample data.
     */
    public static List<String> extractExamples(XsdExtendedElement elementInfo) {
        List<String> examples = new ArrayList<>();

        XsdExtendedElement.RestrictionInfo restrictionInfo = elementInfo.getRestrictionInfo();
        if (restrictionInfo != null && restrictionInfo.facets() != null) {
            List<String> enumerations = restrictionInfo.facets().get("enumeration");
            if (enumerations != null && !enumerations.isEmpty()) {
                int limit = Math.min(5, enumerations.size());
                for (int i = 0; i < limit; i++) {
                    examples.add(enumerations.get(i));
                }
                if (enumerations.size() > 5) {
                    examples.add("... (" + (enumerations.size() - 5) + " more)");
                }
                return examples;
            }
        }

        List<String> exampleValues = elementInfo.getExampleValues();
        if (exampleValues != null && !exampleValues.isEmpty()) {
            int limit = Math.min(3, exampleValues.size());
            for (int i = 0; i < limit; i++) {
                examples.add(exampleValues.get(i));
            }
            return examples;
        }

        String sampleData = elementInfo.getDisplaySampleData();
        if (sampleData != null && !sampleData.isEmpty() && sampleData.length() < 50) {
            examples.add(sampleData);
        }

        return examples;
    }

    /**
     * Helper to get attribute value from a DOM Node.
     */
    public static String getNodeAttribute(org.w3c.dom.Node node, String attrName) {
        if (node == null || node.getAttributes() == null) {
            return null;
        }
        org.w3c.dom.Node attrNode = node.getAttributes().getNamedItem(attrName);
        return attrNode != null ? attrNode.getNodeValue() : null;
    }

    /**
     * Checks if an element name is an XSD compositor (SEQUENCE, CHOICE, ALL, GROUP).
     */
    public static boolean isCompositorElement(String name) {
        if (name == null) {
            return false;
        }
        return name.equals("SEQUENCE") || name.startsWith("SEQUENCE_") ||
               name.equals("CHOICE") || name.startsWith("CHOICE_") ||
               name.equals("ALL") || name.startsWith("ALL_") ||
               name.equals("GROUP") || name.startsWith("GROUP_");
    }

    /**
     * Recursively collects real child elements, skipping compositor elements.
     */
    public static void collectRealChildElements(XsdExtendedElement parent, XsdDocumentationData xsdData,
                                                List<XsdExtendedElement> result, Set<String> visited) {
        if (parent == null || parent.getChildren() == null) {
            return;
        }

        for (String childXpath : parent.getChildren()) {
            if (visited.contains(childXpath)) {
                continue;
            }
            visited.add(childXpath);

            XsdExtendedElement childInfo = xsdData.getExtendedXsdElementMap().get(childXpath);
            if (childInfo == null || childInfo.getElementName() == null) {
                continue;
            }

            String elementName = childInfo.getElementName();

            if (isCompositorElement(elementName)) {
                collectRealChildElements(childInfo, xsdData, result, visited);
            } else {
                boolean alreadyAdded = result.stream()
                        .anyMatch(e -> e.getElementName().equals(elementName));
                if (!alreadyAdded) {
                    result.add(childInfo);
                }
            }
        }
    }

    /**
     * Builds a {@link CompletionItem} from an XsdExtendedElement for display.
     *
     * @param elementInfo the XSD element info
     * @param index       ordering index (used for relevance scoring)
     * @return a CompletionItem with all display fields populated
     */
    public static CompletionItem buildCompletionItem(XsdExtendedElement elementInfo, int index) {
        String elementName = elementInfo.getElementName();

        CompletionItem.Builder builder = new CompletionItem.Builder(
                elementName,
                elementName,
                CompletionItemType.ELEMENT
        );

        if (elementInfo.getElementType() != null) {
            builder.dataType(elementInfo.getElementType());
        }

        boolean isRequired = elementInfo.isMandatory();
        builder.required(isRequired);

        int baseScore = isRequired ? 150 : 100;
        builder.relevanceScore(baseScore + (1000 - index));

        String cardinality = buildCardinalityString(elementInfo);
        if (!cardinality.isEmpty()) {
            builder.cardinality(cardinality);
        }

        String defaultValue = extractDefaultValue(elementInfo);
        if (defaultValue != null && !defaultValue.isEmpty()) {
            builder.defaultValue(defaultValue);
        }

        List<String> facetHints = buildFacetHints(elementInfo);
        if (!facetHints.isEmpty()) {
            builder.facetHints(facetHints);
        }

        List<String> examples = extractExamples(elementInfo);
        if (!examples.isEmpty()) {
            builder.examples(examples);
        }

        if (elementInfo.getSourceNamespace() != null && !elementInfo.getSourceNamespace().isEmpty()) {
            builder.namespace(elementInfo.getSourceNamespace());
        }
        if (elementInfo.getSourceNamespacePrefix() != null && !elementInfo.getSourceNamespacePrefix().isEmpty()) {
            builder.prefix(elementInfo.getSourceNamespacePrefix());
        }

        return builder.build();
    }

    /**
     * Resolves a parent element's children into a list of {@link CompletionItem}s,
     * recursively resolving SEQUENCE/CHOICE/ALL compositors.
     *
     * @param parent  the parent XSD element
     * @param xsdData the XSD documentation data
     * @return list of CompletionItems for all real child elements
     */
    public static List<CompletionItem> resolveChildElements(XsdExtendedElement parent, XsdDocumentationData xsdData) {
        if (parent == null || parent.getChildren() == null || parent.getChildren().isEmpty()) {
            return List.of();
        }
        if (xsdData == null || xsdData.getExtendedXsdElementMap() == null) {
            return List.of();
        }

        List<XsdExtendedElement> realChildren = new ArrayList<>();
        collectRealChildElements(parent, xsdData, realChildren, new HashSet<>());

        List<CompletionItem> items = new ArrayList<>();
        for (int i = 0; i < realChildren.size(); i++) {
            items.add(buildCompletionItem(realChildren.get(i), i));
        }
        return items;
    }
}
