package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.SimpleTypeResult;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Command for editing existing SimpleType definitions in XSD
 */
public class EditSimpleTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(EditSimpleTypeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo simpleTypeNode;
    private final SimpleTypeResult newResult;
    private Element originalElement;
    private Element backupElement;

    public EditSimpleTypeCommand(XsdDomManipulator domManipulator, XsdNodeInfo simpleTypeNode,
                                 SimpleTypeResult newResult) {
        this.domManipulator = domManipulator;
        this.simpleTypeNode = simpleTypeNode;
        this.newResult = newResult;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Editing simpleType: {}", simpleTypeNode.name());

            originalElement = (Element) domManipulator.findNodeByPath(simpleTypeNode.xpath());
            if (originalElement == null) {
                logger.error("SimpleType element not found: {}", simpleTypeNode.xpath());
                return false;
            }

            // Create backup for undo
            backupElement = (Element) originalElement.cloneNode(true);

            // Update the simpleType
            updateSimpleTypeElement(originalElement, newResult);

            logger.info("Successfully edited simpleType: {}", newResult.name());
            return true;

        } catch (Exception e) {
            logger.error("Error editing simpleType", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (originalElement != null && backupElement != null) {
                // Replace current element with backup
                Node parent = originalElement.getParentNode();
                if (parent != null) {
                    // Import backup to current document
                    Document doc = domManipulator.getDocument();
                    Element restoredElement = (Element) doc.importNode(backupElement, true);
                    parent.replaceChild(restoredElement, originalElement);

                    logger.info("Restored simpleType: {}", simpleTypeNode.name());
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Error undoing simpleType edit", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Edit simpleType '%s'", simpleTypeNode.name());
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
     * Update the simpleType element with new values
     */
    private void updateSimpleTypeElement(Element simpleTypeEl, SimpleTypeResult result) {
        Document doc = simpleTypeEl.getOwnerDocument();

        // Update name attribute
        simpleTypeEl.setAttribute("name", result.name());

        // Find or create restriction element
        Element restriction = findOrCreateRestriction(simpleTypeEl, result.baseType(), doc);

        // Clear existing facets
        clearFacets(restriction);

        // Add new facets
        addFacetsToRestriction(restriction, result, doc);
    }

    /**
     * Find existing restriction or create new one
     */
    private Element findOrCreateRestriction(Element simpleType, String baseType, Document doc) {
        NodeList children = simpleType.getChildNodes();

        // Look for existing restriction
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String localName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
                if (localName.equals("restriction")) {
                    elem.setAttribute("base", baseType);
                    return elem;
                }
            }
        }

        // Create new restriction
        Element restriction = doc.createElement("xs:restriction");
        restriction.setAttribute("base", baseType);
        simpleType.appendChild(restriction);
        return restriction;
    }

    /**
     * Clear existing facets from restriction
     */
    private void clearFacets(Element restriction) {
        NodeList children = restriction.getChildNodes();

        // Collect facet elements to remove
        var facetsToRemove = new java.util.ArrayList<Node>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String localName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();
                if (isFacetElement(localName)) {
                    facetsToRemove.add(child);
                }
            }
        }

        // Remove facet elements
        for (Node facet : facetsToRemove) {
            restriction.removeChild(facet);
        }
    }

    /**
     * Check if element name is a facet
     */
    private boolean isFacetElement(String localName) {
        return localName.equals("pattern") || localName.equals("enumeration") ||
                localName.equals("length") || localName.equals("minLength") ||
                localName.equals("maxLength") || localName.equals("minInclusive") ||
                localName.equals("maxInclusive") || localName.equals("minExclusive") ||
                localName.equals("maxExclusive") || localName.equals("totalDigits") ||
                localName.equals("fractionDigits") || localName.equals("whiteSpace");
    }

    /**
     * Add facets to restriction element
     */
    private void addFacetsToRestriction(Element restriction, SimpleTypeResult result, Document doc) {
        // Pattern facets
        for (String pattern : result.patterns()) {
            if (!pattern.trim().isEmpty()) {
                Element patternEl = doc.createElement("xs:pattern");
                patternEl.setAttribute("value", pattern);
                restriction.appendChild(patternEl);
            }
        }

        // Enumeration facets
        for (var enumValue : result.enumerations().entrySet()) {
            Element enumEl = doc.createElement("xs:enumeration");
            enumEl.setAttribute("value", enumValue.getKey());
            if (!enumValue.getValue().trim().isEmpty()) {
                Element annotation = doc.createElement("xs:annotation");
                Element documentation = doc.createElement("xs:documentation");
                documentation.setTextContent(enumValue.getValue());
                annotation.appendChild(documentation);
                enumEl.appendChild(annotation);
            }
            restriction.appendChild(enumEl);
        }

        // Length facets
        if (result.exactLength() > 0) {
            Element lengthEl = doc.createElement("xs:length");
            lengthEl.setAttribute("value", String.valueOf(result.exactLength()));
            restriction.appendChild(lengthEl);
        } else {
            if (result.minLength() > 0) {
                Element minLengthEl = doc.createElement("xs:minLength");
                minLengthEl.setAttribute("value", String.valueOf(result.minLength()));
                restriction.appendChild(minLengthEl);
            }
            if (result.maxLength() > 0) {
                Element maxLengthEl = doc.createElement("xs:maxLength");
                maxLengthEl.setAttribute("value", String.valueOf(result.maxLength()));
                restriction.appendChild(maxLengthEl);
            }
        }

        // Numeric facets (for numeric base types)
        if (isNumericBaseType(result.baseType())) {
            if (result.minInclusive() != null && !result.minInclusive().trim().isEmpty()) {
                Element minIncEl = doc.createElement("xs:minInclusive");
                minIncEl.setAttribute("value", result.minInclusive());
                restriction.appendChild(minIncEl);
            }
            if (result.maxInclusive() != null && !result.maxInclusive().trim().isEmpty()) {
                Element maxIncEl = doc.createElement("xs:maxInclusive");
                maxIncEl.setAttribute("value", result.maxInclusive());
                restriction.appendChild(maxIncEl);
            }
            if (result.minExclusive() != null && !result.minExclusive().trim().isEmpty()) {
                Element minExcEl = doc.createElement("xs:minExclusive");
                minExcEl.setAttribute("value", result.minExclusive());
                restriction.appendChild(minExcEl);
            }
            if (result.maxExclusive() != null && !result.maxExclusive().trim().isEmpty()) {
                Element maxExcEl = doc.createElement("xs:maxExclusive");
                maxExcEl.setAttribute("value", result.maxExclusive());
                restriction.appendChild(maxExcEl);
            }
            if (result.totalDigits() > 0) {
                Element totalDigitsEl = doc.createElement("xs:totalDigits");
                totalDigitsEl.setAttribute("value", String.valueOf(result.totalDigits()));
                restriction.appendChild(totalDigitsEl);
            }
            if (result.fractionDigits() > 0) {
                Element fractionDigitsEl = doc.createElement("xs:fractionDigits");
                fractionDigitsEl.setAttribute("value", String.valueOf(result.fractionDigits()));
                restriction.appendChild(fractionDigitsEl);
            }
        }

        // String processing facets
        if (result.whiteSpace() != null && !result.whiteSpace().equals("preserve")) {
            Element whiteSpaceEl = doc.createElement("xs:whiteSpace");
            whiteSpaceEl.setAttribute("value", result.whiteSpace());
            restriction.appendChild(whiteSpaceEl);
        }
    }

    /**
     * Check if base type is numeric
     */
    private boolean isNumericBaseType(String baseType) {
        return baseType != null && (
                baseType.contains("int") ||
                        baseType.contains("decimal") ||
                        baseType.contains("double") ||
                        baseType.contains("float") ||
                        baseType.contains("long") ||
                        baseType.contains("short") ||
                        baseType.contains("byte")
        );
    }
}