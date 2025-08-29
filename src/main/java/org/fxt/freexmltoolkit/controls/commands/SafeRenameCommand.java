package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.controls.XsdSafeRenameDialog.ReferenceInfo;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command for safely renaming XSD elements and updating all references
 */
public class SafeRenameCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(SafeRenameCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo targetNode;
    private final String newName;
    private final List<ReferenceInfo> affectedReferences;
    private final boolean updateReferences;

    // Backup for undo
    private String originalName;
    private final Map<Element, String> originalAttributeValues;
    private Element targetElement;

    public SafeRenameCommand(XsdDomManipulator domManipulator,
                             XsdNodeInfo targetNode,
                             String newName,
                             List<ReferenceInfo> affectedReferences,
                             boolean updateReferences) {
        this.domManipulator = domManipulator;
        this.targetNode = targetNode;
        this.newName = newName;
        this.affectedReferences = affectedReferences;
        this.updateReferences = updateReferences;
        this.originalAttributeValues = new HashMap<>();
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Executing safe rename: '{}' -> '{}'", targetNode.name(), newName);

            // Find the target element
            targetElement = domManipulator.findElementByXPath(targetNode.xpath());
            if (targetElement == null) {
                logger.error("Target element not found: {}", targetNode.xpath());
                return false;
            }

            // Backup original values
            originalName = targetElement.getAttribute("name");

            // Rename the main element
            targetElement.setAttribute("name", newName);

            // Update references if requested
            if (updateReferences && !affectedReferences.isEmpty()) {
                updateAllReferences();
            }

            logger.info("Successfully renamed '{}' to '{}' with {} reference updates",
                    originalName, newName, originalAttributeValues.size());
            return true;

        } catch (Exception e) {
            logger.error("Error executing safe rename", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (targetElement == null || originalName == null) {
                logger.warn("No rename to undo");
                return false;
            }

            // Restore original element name
            targetElement.setAttribute("name", originalName);

            // Restore original reference values
            for (Map.Entry<Element, String> entry : originalAttributeValues.entrySet()) {
                Element element = entry.getKey();
                String originalValue = entry.getValue();

                // Determine which attribute to restore
                if (element.hasAttribute("type")) {
                    element.setAttribute("type", originalValue);
                } else if (element.hasAttribute("ref")) {
                    element.setAttribute("ref", originalValue);
                } else if (element.hasAttribute("base")) {
                    element.setAttribute("base", originalValue);
                }
            }

            logger.info("Successfully undone rename operation: '{}' -> '{}'", newName, originalName);
            return true;

        } catch (Exception e) {
            logger.error("Error undoing safe rename", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Safe rename '%s' to '%s'", originalName != null ? originalName : targetNode.name(), newName);
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    private void updateAllReferences() {
        for (ReferenceInfo ref : affectedReferences) {
            updateReference(ref);
        }
    }

    private void updateReference(ReferenceInfo ref) {
        try {
            // Find all elements that match this reference
            NodeList elements = domManipulator.getDocument().getElementsByTagName("*");

            for (int i = 0; i < elements.getLength(); i++) {
                Element element = (Element) elements.item(i);

                switch (ref.type()) {
                    case TYPE_REFERENCE:
                        updateTypeReference(element, ref);
                        break;
                    case REF_REFERENCE:
                        updateRefReference(element, ref);
                        break;
                    case BASE_REFERENCE:
                        updateBaseReference(element, ref);
                        break;
                }
            }

        } catch (Exception e) {
            logger.error("Error updating reference: {}", ref.description(), e);
        }
    }

    private void updateTypeReference(Element element, ReferenceInfo ref) {
        String typeAttr = element.getAttribute("type");
        if (typeAttr != null && !typeAttr.isEmpty()) {
            String cleanType = removeNamespacePrefix(typeAttr);
            String cleanOriginal = removeNamespacePrefix(originalName);

            if (cleanOriginal.equals(cleanType) || originalName.equals(typeAttr)) {
                // Backup original value
                originalAttributeValues.put(element, typeAttr);

                // Update the reference
                String updatedType = typeAttr.replace(cleanOriginal, newName);
                element.setAttribute("type", updatedType);

                logger.debug("Updated type reference: {} -> {}", typeAttr, updatedType);
            }
        }
    }

    private void updateRefReference(Element element, ReferenceInfo ref) {
        String refAttr = element.getAttribute("ref");
        if (refAttr != null && !refAttr.isEmpty()) {
            String cleanRef = removeNamespacePrefix(refAttr);
            String cleanOriginal = removeNamespacePrefix(originalName);

            if (cleanOriginal.equals(cleanRef) || originalName.equals(refAttr)) {
                // Backup original value
                originalAttributeValues.put(element, refAttr);

                // Update the reference
                String updatedRef = refAttr.replace(cleanOriginal, newName);
                element.setAttribute("ref", updatedRef);

                logger.debug("Updated ref reference: {} -> {}", refAttr, updatedRef);
            }
        }
    }

    private void updateBaseReference(Element element, ReferenceInfo ref) {
        String baseAttr = element.getAttribute("base");
        if (baseAttr != null && !baseAttr.isEmpty()) {
            String cleanBase = removeNamespacePrefix(baseAttr);
            String cleanOriginal = removeNamespacePrefix(originalName);

            if (cleanOriginal.equals(cleanBase) || originalName.equals(baseAttr)) {
                // Backup original value
                originalAttributeValues.put(element, baseAttr);

                // Update the reference
                String updatedBase = baseAttr.replace(cleanOriginal, newName);
                element.setAttribute("base", updatedBase);

                logger.debug("Updated base reference: {} -> {}", baseAttr, updatedBase);
            }
        }
    }

    private String removeNamespacePrefix(String name) {
        if (name != null && name.contains(":")) {
            return name.substring(name.lastIndexOf(':') + 1);
        }
        return name;
    }

    public int getUpdatedReferencesCount() {
        return originalAttributeValues.size();
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getNewName() {
        return newName;
    }
}