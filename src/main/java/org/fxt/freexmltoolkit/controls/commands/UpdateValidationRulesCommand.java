package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.ValidationRulesResult;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.controls.XsdValidationRulesEditor.CustomFacet;
import org.fxt.freexmltoolkit.controls.XsdValidationRulesEditor.WhitespaceAction;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.List;

/**
 * Command for updating validation rules (facets) in XSD schema elements
 */
public class UpdateValidationRulesCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(UpdateValidationRulesCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo targetNode;
    private final ValidationRulesResult newRules;

    // Backup for undo
    private ValidationRulesResult originalRules;
    private Element targetElement;

    public UpdateValidationRulesCommand(XsdDomManipulator domManipulator,
                                        XsdNodeInfo targetNode,
                                        ValidationRulesResult validationRules) {
        this.domManipulator = domManipulator;
        this.targetNode = targetNode;
        this.newRules = validationRules;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Updating validation rules for node: {}", targetNode.name());

            Document doc = domManipulator.getDocument();
            targetElement = domManipulator.findElementByXPath(targetNode.xpath());

            if (targetElement == null) {
                logger.error("Target element not found: {}", targetNode.xpath());
                return false;
            }

            // Backup current rules
            originalRules = extractCurrentValidationRules(targetElement);

            // Apply new validation rules
            applyValidationRules(targetElement, newRules);

            logger.info("Successfully updated validation rules for node: {}", targetNode.name());
            return true;

        } catch (Exception e) {
            logger.error("Error updating validation rules", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (originalRules == null || targetElement == null) {
                logger.warn("No original validation rules to restore");
                return false;
            }

            // Restore original validation rules
            applyValidationRules(targetElement, originalRules);

            logger.info("Restored original validation rules for node: {}", targetNode.name());
            return true;

        } catch (Exception e) {
            logger.error("Error undoing validation rules changes", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Update validation rules for '" + targetNode.name() + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    /**
     * Extracts current validation rules from an element
     */
    private ValidationRulesResult extractCurrentValidationRules(Element element) {
        ValidationRulesResult result = new ValidationRulesResult();

        // Find simpleType restriction element
        Element restrictionElement = findRestrictionElement(element);
        if (restrictionElement == null) {
            return result; // No restrictions found
        }

        NodeList facets = restrictionElement.getChildNodes();
        for (int i = 0; i < facets.getLength(); i++) {
            Node facet = facets.item(i);
            if (facet.getNodeType() == Node.ELEMENT_NODE) {
                Element facetElement = (Element) facet;
                String facetName = facetElement.getLocalName();
                String facetValue = facetElement.getAttribute("value");

                // Map facets back to ValidationRulesResult
                switch (facetName) {
                    case "pattern" -> result.setPattern(facetValue);
                    case "enumeration" -> {
                        // Handle enumeration values (collect all)
                        List<String> enumValues = result.getEnumerationValues();
                        if (enumValues == null) {
                            enumValues = new java.util.ArrayList<>();
                            result.setEnumerationValues(enumValues);
                        }
                        enumValues.add(facetValue);
                    }
                    case "minInclusive" -> result.setMinInclusive(facetValue);
                    case "maxInclusive" -> result.setMaxInclusive(facetValue);
                    case "minExclusive" -> result.setMinExclusive(facetValue);
                    case "maxExclusive" -> result.setMaxExclusive(facetValue);
                    case "length" -> result.setLength(facetValue);
                    case "minLength" -> result.setMinLength(facetValue);
                    case "maxLength" -> result.setMaxLength(facetValue);
                    case "totalDigits" -> result.setTotalDigits(facetValue);
                    case "fractionDigits" -> result.setFractionDigits(facetValue);
                    case "whiteSpace" -> {
                        try {
                            result.setWhitespaceAction(WhitespaceAction.valueOf(facetValue.toUpperCase()));
                        } catch (IllegalArgumentException e) {
                            logger.warn("Unknown whitespace action: {}", facetValue);
                        }
                    }
                    default -> {
                        // Custom facet
                        List<CustomFacet> customFacets = result.getCustomFacets();
                        if (customFacets == null) {
                            customFacets = new java.util.ArrayList<>();
                            result.setCustomFacets(customFacets);
                        }
                        customFacets.add(new CustomFacet(facetName, facetValue, ""));
                    }
                }
            }
        }

        return result;
    }

    /**
     * Applies validation rules to an element
     */
    private void applyValidationRules(Element element, ValidationRulesResult rules) {
        if (!rules.hasAnyRules()) {
            // Remove all validation rules
            removeAllValidationRules(element);
            return;
        }

        // Ensure element has a simpleType with restriction
        Element restrictionElement = ensureRestrictionElement(element);

        // Clear existing facets
        clearExistingFacets(restrictionElement);

        // Add new facets
        Document doc = element.getOwnerDocument();

        // Pattern
        if (rules.getPattern() != null && !rules.getPattern().trim().isEmpty()) {
            Element patternFacet = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:pattern");
            patternFacet.setAttribute("value", rules.getPattern().trim());
            restrictionElement.appendChild(patternFacet);
        }

        // Enumeration values
        if (rules.getEnumerationValues() != null && !rules.getEnumerationValues().isEmpty()) {
            for (String enumValue : rules.getEnumerationValues()) {
                if (!enumValue.trim().isEmpty()) {
                    Element enumFacet = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:enumeration");
                    enumFacet.setAttribute("value", enumValue.trim());
                    restrictionElement.appendChild(enumFacet);
                }
            }
        }

        // Range constraints
        addFacetIfNotEmpty(doc, restrictionElement, "minInclusive", rules.getMinInclusive());
        addFacetIfNotEmpty(doc, restrictionElement, "maxInclusive", rules.getMaxInclusive());
        addFacetIfNotEmpty(doc, restrictionElement, "minExclusive", rules.getMinExclusive());
        addFacetIfNotEmpty(doc, restrictionElement, "maxExclusive", rules.getMaxExclusive());

        // Length constraints
        addFacetIfNotEmpty(doc, restrictionElement, "length", rules.getLength());
        addFacetIfNotEmpty(doc, restrictionElement, "minLength", rules.getMinLength());
        addFacetIfNotEmpty(doc, restrictionElement, "maxLength", rules.getMaxLength());

        // Decimal constraints
        addFacetIfNotEmpty(doc, restrictionElement, "totalDigits", rules.getTotalDigits());
        addFacetIfNotEmpty(doc, restrictionElement, "fractionDigits", rules.getFractionDigits());

        // Whitespace action
        if (rules.getWhitespaceAction() != null) {
            Element whitespaceFacet = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:whiteSpace");
            whitespaceFacet.setAttribute("value", rules.getWhitespaceAction().getValue());
            restrictionElement.appendChild(whitespaceFacet);
        }

        // Custom facets
        if (rules.getCustomFacets() != null && !rules.getCustomFacets().isEmpty()) {
            for (CustomFacet customFacet : rules.getCustomFacets()) {
                if (!customFacet.getName().trim().isEmpty() && !customFacet.getValue().trim().isEmpty()) {
                    Element customElement = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:" + customFacet.getName());
                    customElement.setAttribute("value", customFacet.getValue().trim());
                    restrictionElement.appendChild(customElement);
                }
            }
        }

        logger.debug("Applied {} validation rules to element: {}",
                countFacets(restrictionElement), element.getAttribute("name"));
    }

    private void addFacetIfNotEmpty(Document doc, Element parent, String facetName, String value) {
        if (value != null && !value.trim().isEmpty()) {
            Element facet = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:" + facetName);
            facet.setAttribute("value", value.trim());
            parent.appendChild(facet);
        }
    }

    /**
     * Finds the restriction element within an element's simpleType
     */
    private Element findRestrictionElement(Element element) {
        NodeList simpleTypes = element.getElementsByTagName("xs:simpleType");
        if (simpleTypes.getLength() == 0) {
            return null;
        }

        Element simpleType = (Element) simpleTypes.item(0);
        NodeList restrictions = simpleType.getElementsByTagName("xs:restriction");
        if (restrictions.getLength() == 0) {
            return null;
        }

        return (Element) restrictions.item(0);
    }

    /**
     * Ensures a restriction element exists, creating the necessary structure if needed
     */
    private Element ensureRestrictionElement(Element element) {
        Element restrictionElement = findRestrictionElement(element);
        if (restrictionElement != null) {
            return restrictionElement;
        }

        // Create the necessary structure
        Document doc = element.getOwnerDocument();

        Element simpleType = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:simpleType");
        Element restriction = doc.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:restriction");
        restriction.setAttribute("base", "xs:string"); // Default to string type

        simpleType.appendChild(restriction);
        element.appendChild(simpleType);

        return restriction;
    }

    /**
     * Removes all validation rules from an element
     */
    private void removeAllValidationRules(Element element) {
        NodeList simpleTypes = element.getElementsByTagName("xs:simpleType");
        for (int i = simpleTypes.getLength() - 1; i >= 0; i--) {
            Node simpleType = simpleTypes.item(i);
            if (simpleType.getParentNode().equals(element)) {
                element.removeChild(simpleType);
            }
        }
    }

    /**
     * Clears existing facets from a restriction element
     */
    private void clearExistingFacets(Element restrictionElement) {
        NodeList children = restrictionElement.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                restrictionElement.removeChild(child);
            }
        }
    }

    /**
     * Counts the number of facets in a restriction element
     */
    private int countFacets(Element restrictionElement) {
        int count = 0;
        NodeList children = restrictionElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }
        return count;
    }
}