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

/**
 * Command for adding new SimpleType definitions to XSD
 */
public class AddSimpleTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddSimpleTypeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo parentNode;
    private final SimpleTypeResult simpleTypeResult;
    private Element addedElement;

    public AddSimpleTypeCommand(XsdDomManipulator domManipulator, XsdNodeInfo parentNode,
                                SimpleTypeResult simpleTypeResult) {
        this.domManipulator = domManipulator;
        this.parentNode = parentNode;
        this.simpleTypeResult = simpleTypeResult;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Adding simpleType: {}", simpleTypeResult.name());

            Document doc = domManipulator.getDocument();
            Element parent = (Element) domManipulator.findNodeByPath(parentNode.xpath());

            if (parent == null) {
                logger.error("Parent node not found: {}", parentNode.xpath());
                return false;
            }

            // Create simpleType element
            addedElement = doc.createElement("xs:simpleType");
            addedElement.setAttribute("name", simpleTypeResult.name());

            // Create restriction element
            Element restriction = doc.createElement("xs:restriction");
            restriction.setAttribute("base", simpleTypeResult.baseType());
            addedElement.appendChild(restriction);

            // Add facets based on the result
            addFacetsToRestriction(restriction, simpleTypeResult, doc);

            // Find appropriate insertion point (after imports, before elements)
            Node insertionPoint = findSimpleTypeInsertionPoint(parent);
            if (insertionPoint != null) {
                parent.insertBefore(addedElement, insertionPoint);
            } else {
                parent.appendChild(addedElement);
            }

            logger.info("Successfully added simpleType: {}", simpleTypeResult.name());
            return true;

        } catch (Exception e) {
            logger.error("Error adding simpleType", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (addedElement != null && addedElement.getParentNode() != null) {
                addedElement.getParentNode().removeChild(addedElement);
                logger.info("Removed simpleType: {}", simpleTypeResult.name());
            }
            return true;
        } catch (Exception e) {
            logger.error("Error undoing simpleType addition", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Add simpleType '%s'", simpleTypeResult.name());
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
     * Add facets to restriction element based on the SimpleType result
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

    /**
     * Find appropriate insertion point for simpleType (after imports, before elements/complexTypes)
     */
    private Node findSimpleTypeInsertionPoint(Element parent) {
        var children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String localName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

                // Insert before elements, complexTypes, groups, attributeGroups
                if (localName.equals("element") || localName.equals("complexType") ||
                        localName.equals("group") || localName.equals("attributeGroup")) {
                    return child;
                }
            }
        }

        return null; // Insert at end
    }
}