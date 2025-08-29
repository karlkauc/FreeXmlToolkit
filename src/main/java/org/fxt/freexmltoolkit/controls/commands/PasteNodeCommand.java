package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdClipboardService;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Command for pasting XSD nodes with smart conflict resolution
 */
public class PasteNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(PasteNodeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo targetParent;
    private final XsdClipboardService.XsdClipboardData clipboardData;
    private final boolean resolveConflicts;

    // For undo operations
    private final List<Node> addedNodes = new ArrayList<>();
    private Node targetParentNode;

    public PasteNodeCommand(XsdDomManipulator domManipulator, XsdNodeInfo targetParent,
                            XsdClipboardService.XsdClipboardData clipboardData, boolean resolveConflicts) {
        this.domManipulator = domManipulator;
        this.targetParent = targetParent;
        this.clipboardData = clipboardData;
        this.resolveConflicts = resolveConflicts;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Pasting {} '{}' into {}",
                    clipboardData.getNodeInfo().nodeType(),
                    clipboardData.getNodeInfo().name(),
                    targetParent.name());

            // Find target parent in DOM
            targetParentNode = domManipulator.findNodeByPath(targetParent.xpath());
            if (targetParentNode == null || targetParentNode.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Target parent not found: {}", targetParent.xpath());
                return false;
            }

            // Parse clipboard XML fragment
            Element fragmentRoot = parseXmlFragment(clipboardData.getXmlFragment());
            if (fragmentRoot == null) {
                return false;
            }

            // Resolve name conflicts if needed
            if (resolveConflicts) {
                resolveNameConflicts(fragmentRoot, (Element) targetParentNode);
            }

            // Import and add the node(s)
            Document targetDoc = domManipulator.getDocument();
            Node importedNode = targetDoc.importNode(fragmentRoot, true);

            // Insert at appropriate position
            Node insertionPoint = findInsertionPoint((Element) targetParentNode, fragmentRoot);
            if (insertionPoint != null) {
                targetParentNode.insertBefore(importedNode, insertionPoint);
            } else {
                targetParentNode.appendChild(importedNode);
            }

            addedNodes.add(importedNode);

            logger.info("Successfully pasted node: {}", fragmentRoot.getAttribute("name"));
            return true;

        } catch (Exception e) {
            logger.error("Error pasting node", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            // Remove all added nodes
            for (Node node : addedNodes) {
                if (node.getParentNode() != null) {
                    node.getParentNode().removeChild(node);
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Error undoing paste operation", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String sourceTypeName = getNodeTypeDisplayName(clipboardData.getNodeInfo().nodeType());
        return String.format("Paste %s '%s'", sourceTypeName, clipboardData.getNodeInfo().name());
    }

    /**
     * Parse XML fragment from clipboard
     */
    private Element parseXmlFragment(String xmlFragment) {
        try {
            // Wrap fragment in a temporary document with proper namespace declarations
            StringBuilder wrappedXml = new StringBuilder();
            wrappedXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            wrappedXml.append("<temp xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"");

            // Add namespace declarations from clipboard
            for (var entry : clipboardData.getNamespaceDeclarations().entrySet()) {
                wrappedXml.append(" ").append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"");
            }

            wrappedXml.append(">");
            wrappedXml.append(xmlFragment);
            wrappedXml.append("</temp>");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(wrappedXml.toString())));

            // Get the first child of the temp element (our actual fragment)
            Element temp = doc.getDocumentElement();
            NodeList children = temp.getChildNodes();

            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    return (Element) child;
                }
            }

            return null;

        } catch (Exception e) {
            logger.error("Error parsing XML fragment", e);
            return null;
        }
    }

    /**
     * Resolve name conflicts by renaming elements/attributes
     */
    private void resolveNameConflicts(Element fragment, Element targetParent) {
        String originalName = fragment.getAttribute("name");
        if (originalName == null || originalName.isEmpty()) {
            return;
        }

        // Check if name conflicts with existing children
        Set<String> existingNames = collectExistingNames(targetParent);

        if (existingNames.contains(originalName)) {
            String newName = generateUniqueName(originalName, existingNames);
            fragment.setAttribute("name", newName);

            logger.info("Resolved name conflict: '{}' -> '{}'", originalName, newName);
        }

        // Recursively resolve conflicts in children
        NodeList children = fragment.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                resolveNameConflicts((Element) child, fragment);
            }
        }
    }

    /**
     * Collect existing names in parent element
     */
    private Set<String> collectExistingNames(Element parent) {
        Set<String> names = new HashSet<>();
        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String name = elem.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }

        return names;
    }

    /**
     * Generate unique name by adding suffix
     */
    private String generateUniqueName(String baseName, Set<String> existingNames) {
        String candidateName = baseName;
        int suffix = 1;

        while (existingNames.contains(candidateName)) {
            candidateName = baseName + "_copy" + (suffix > 1 ? suffix : "");
            suffix++;
        }

        return candidateName;
    }

    /**
     * Find appropriate insertion point for the pasted element
     */
    private Node findInsertionPoint(Element parent, Element elementToPaste) {
        String tagName = elementToPaste.getTagName();

        // Insert after similar elements but before different types
        NodeList children = parent.getChildNodes();
        Node lastSimilar = null;

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;

                if (tagName.equals(elem.getTagName())) {
                    lastSimilar = child;
                } else if (lastSimilar != null) {
                    // Found different element type after similar ones
                    return child;
                }
            }
        }

        // Insert at end if no specific position found
        return null;
    }

    /**
     * Get display name for node type
     */
    private String getNodeTypeDisplayName(XsdNodeInfo.NodeType nodeType) {
        return switch (nodeType) {
            case ELEMENT -> "element";
            case ATTRIBUTE -> "attribute";
            case SEQUENCE -> "sequence";
            case CHOICE -> "choice";
            case ANY -> "any";
            case SIMPLE_TYPE -> "simpleType";
            case COMPLEX_TYPE -> "complexType";
            case SCHEMA -> "schema";
        };
    }
}