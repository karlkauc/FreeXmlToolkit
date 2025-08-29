package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.TypeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Command for finding all usages/references of a type in the XSD schema.
 * This is a non-modifying command that searches and reports type usage locations.
 */
public class FindTypeUsagesCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(FindTypeUsagesCommand.class);

    private final XsdDomManipulator domManipulator;
    private final TypeInfo typeInfo;
    private final List<TypeUsage> foundUsages;

    public FindTypeUsagesCommand(XsdDomManipulator domManipulator, TypeInfo typeInfo) {
        this.domManipulator = domManipulator;
        this.typeInfo = typeInfo;
        this.foundUsages = new ArrayList<>();
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Finding usages for type: {}", typeInfo.name());

            foundUsages.clear();
            List<Element> references = domManipulator.findTypeReferences(typeInfo.name());

            for (Element element : references) {
                TypeUsage usage = analyzeUsage(element);
                if (usage != null) {
                    foundUsages.add(usage);
                }
            }

            logger.info("Found {} usages for type '{}'", foundUsages.size(), typeInfo.name());
            return true;

        } catch (Exception e) {
            logger.error("Error finding usages for type: " + typeInfo.name(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        // This is a search operation, no undo needed
        return true;
    }

    @Override
    public String getDescription() {
        return String.format("Find usages of type '%s'", typeInfo.name());
    }

    @Override
    public boolean canUndo() {
        return false; // Search operations don't need undo
    }

    @Override
    public boolean isModifying() {
        return false; // This command doesn't modify the schema
    }

    /**
     * Analyze how a type is being used in a specific element
     */
    private TypeUsage analyzeUsage(Element element) {
        try {
            String elementName = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
            String usageContext = determineUsageContext(element);
            String xpath = generateSimpleXPath(element);
            String parentName = getParentElementName(element);

            // Determine the usage type
            UsageType usageType = determineUsageType(element);

            return new TypeUsage(
                    typeInfo.name(),
                    elementName,
                    usageContext,
                    usageType,
                    xpath,
                    parentName
            );

        } catch (Exception e) {
            logger.error("Error analyzing usage for element", e);
            return null;
        }
    }

    /**
     * Determine how the type is being used
     */
    private UsageType determineUsageType(Element element) {
        String localName = element.getLocalName() != null ? element.getLocalName() : element.getTagName();

        // Check which attribute contains our type reference
        if (typeInfo.name().equals(getLocalTypeName(element.getAttribute("type")))) {
            if ("element".equals(localName)) {
                return UsageType.ELEMENT_TYPE;
            } else if ("attribute".equals(localName)) {
                return UsageType.ATTRIBUTE_TYPE;
            }
            return UsageType.TYPE_REFERENCE;
        }

        if (typeInfo.name().equals(getLocalTypeName(element.getAttribute("base")))) {
            return UsageType.BASE_TYPE;
        }

        if (typeInfo.name().equals(getLocalTypeName(element.getAttribute("itemType")))) {
            return UsageType.LIST_ITEM_TYPE;
        }

        String memberTypes = element.getAttribute("memberTypes");
        if (memberTypes != null && memberTypes.contains(typeInfo.name())) {
            return UsageType.UNION_MEMBER;
        }

        return UsageType.OTHER;
    }

    /**
     * Get local name from potentially namespaced type
     */
    private String getLocalTypeName(String typeName) {
        if (typeName == null || typeName.isEmpty()) return "";
        return typeName.contains(":") ? typeName.substring(typeName.indexOf(":") + 1) : typeName;
    }

    /**
     * Determine the context where the type is used
     */
    private String determineUsageContext(Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent != null) {
            String parentName = parent.getLocalName() != null ? parent.getLocalName() : parent.getTagName();
            String elementName = parent.getAttribute("name");
            if (elementName != null && !elementName.isEmpty()) {
                return parentName + " '" + elementName + "'";
            }
            return parentName;
        }
        return "schema root";
    }

    /**
     * Get the name of the parent element
     */
    private String getParentElementName(Element element) {
        Element parent = (Element) element.getParentNode();
        while (parent != null) {
            String name = parent.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                return name;
            }
            parent = (Element) parent.getParentNode();
        }
        return "schema";
    }

    /**
     * Generate a simple XPath for the element
     */
    private String generateSimpleXPath(Element element) {
        StringBuilder xpath = new StringBuilder();
        Element current = element;

        while (current != null && current.getNodeType() == Element.ELEMENT_NODE) {
            String name = current.getLocalName() != null ? current.getLocalName() : current.getTagName();
            String nameAttr = current.getAttribute("name");

            if (nameAttr != null && !nameAttr.isEmpty()) {
                xpath.insert(0, "/" + name + "[@name='" + nameAttr + "']");
            } else {
                xpath.insert(0, "/" + name);
            }

            if (current.getParentNode() instanceof Element) {
                current = (Element) current.getParentNode();
            } else {
                break;
            }
        }

        return xpath.toString();
    }

    /**
     * Get the list of found usages
     */
    public List<TypeUsage> getFoundUsages() {
        return new ArrayList<>(foundUsages);
    }

    /**
     * Get the type that was searched
     */
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    /**
     * Represents a single usage/reference of a type
     */
    public static class TypeUsage {
        private final String typeName;
        private final String elementName;
        private final String usageContext;
        private final UsageType usageType;
        private final String xpath;
        private final String parentName;

        public TypeUsage(String typeName, String elementName, String usageContext,
                         UsageType usageType, String xpath, String parentName) {
            this.typeName = typeName;
            this.elementName = elementName;
            this.usageContext = usageContext;
            this.usageType = usageType;
            this.xpath = xpath;
            this.parentName = parentName;
        }

        // Getters
        public String getTypeName() {
            return typeName;
        }

        public String getElementName() {
            return elementName;
        }

        public String getUsageContext() {
            return usageContext;
        }

        public UsageType getUsageType() {
            return usageType;
        }

        public String getXPath() {
            return xpath;
        }

        public String getParentName() {
            return parentName;
        }

        @Override
        public String toString() {
            return String.format("%s used as %s in %s",
                    typeName, usageType.getDescription(), usageContext);
        }
    }

    /**
     * Types of usage for a type reference
     */
    public enum UsageType {
        ELEMENT_TYPE("element type"),
        ATTRIBUTE_TYPE("attribute type"),
        BASE_TYPE("base type"),
        LIST_ITEM_TYPE("list item type"),
        UNION_MEMBER("union member"),
        TYPE_REFERENCE("type reference"),
        OTHER("other");

        private final String description;

        UsageType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}