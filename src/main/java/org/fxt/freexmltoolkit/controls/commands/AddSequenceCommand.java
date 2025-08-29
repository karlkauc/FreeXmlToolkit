package org.fxt.freexmltoolkit.controls.commands;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for adding a sequence to an XSD schema
 */
public class AddSequenceCommand implements XsdCommand {

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo parentNode;
    private final String minOccurs;
    private final String maxOccurs;
    private Element addedSequence;
    private Node parentDomNode;

    public AddSequenceCommand(XsdDomManipulator domManipulator, XsdNodeInfo parentNode,
                              String minOccurs, String maxOccurs) {
        this.domManipulator = domManipulator;
        this.parentNode = parentNode;
        this.minOccurs = minOccurs;
        this.maxOccurs = maxOccurs;
    }

    @Override
    public boolean execute() {
        try {
            // Store parent node for undo
            parentDomNode = domManipulator.findNodeByPath(parentNode.xpath());
            if (parentDomNode == null) {
                return false;
            }

            // Create new sequence element
            addedSequence = domManipulator.getDocument().createElement("xs:sequence");

            if (minOccurs != null && !minOccurs.trim().isEmpty() && !"1".equals(minOccurs)) {
                addedSequence.setAttribute("minOccurs", minOccurs);
            }

            if (maxOccurs != null && !maxOccurs.trim().isEmpty() && !"1".equals(maxOccurs)) {
                addedSequence.setAttribute("maxOccurs", maxOccurs);
            }

            // Find appropriate insertion point
            Node insertionPoint = findStructuralInsertionPoint(parentDomNode);

            // Insert sequence
            if (insertionPoint != null) {
                parentDomNode.insertBefore(addedSequence, insertionPoint);
            } else {
                parentDomNode.appendChild(addedSequence);
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (addedSequence != null && parentDomNode != null) {
                parentDomNode.removeChild(addedSequence);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Add sequence";
    }

    /**
     * Find appropriate insertion point for structural elements like sequence/choice
     */
    private Node findStructuralInsertionPoint(Node parent) {
        // Insert before attributes and other non-structural content
        Node child = parent.getFirstChild();

        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String tagName = elem.getTagName();

                // Insert before attributes
                if ("xs:attribute".equals(tagName)) {
                    return child;
                }
            }
            child = child.getNextSibling();
        }

        return null; // Insert at end
    }
}