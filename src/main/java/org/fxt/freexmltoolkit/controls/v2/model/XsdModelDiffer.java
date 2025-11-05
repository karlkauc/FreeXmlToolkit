package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.*;

/**
 * Diff algorithm for detecting changes between two XSD model states.
 * This enables incremental updates to the view without complete rebuilds.
 *
 * @since 2.0
 */
public class XsdModelDiffer {

    /**
     * Compares two schema models and returns a list of changes.
     *
     * @param oldModel the previous model state (can be null for initial load)
     * @param newModel the new model state
     * @return list of changes, empty if no changes detected
     */
    public List<XsdModelChange> diff(XsdSchemaModel oldModel, XsdSchemaModel newModel) {
        Objects.requireNonNull(newModel, "New model cannot be null");

        List<XsdModelChange> changes = new ArrayList<>();

        // If old model is null, all nodes in new model are additions
        if (oldModel == null) {
            collectAllNodesAsAdded(newModel, changes);
            return changes;
        }

        // Compare schema properties
        compareSchemaProperties(oldModel, newModel, changes);

        // Compare global elements
        compareGlobalElements(oldModel, newModel, changes);

        // Compare global complex types
        compareComplexTypes(oldModel, newModel, changes);

        // Compare global simple types
        compareSimpleTypes(oldModel, newModel, changes);

        return changes;
    }

    /**
     * Collects all nodes in a new model as NODE_ADDED changes.
     * Used when there is no previous model state.
     */
    private void collectAllNodesAsAdded(XsdSchemaModel model, List<XsdModelChange> changes) {
        // Add schema itself
        changes.add(XsdModelChange.nodeAdded(
                model.getId(),
                null,
                XsdModelChange.NodeType.SCHEMA
        ).build());

        // Add all global elements
        for (XsdElementModel element : model.getGlobalElements()) {
            collectElementAsAdded(element, model.getId(), changes);
        }

        // Add all global complex types
        for (XsdComplexTypeModel complexType : model.getGlobalComplexTypes().values()) {
            collectComplexTypeAsAdded(complexType, model.getId(), changes);
        }

        // Add all global simple types
        for (XsdSimpleTypeModel simpleType : model.getGlobalSimpleTypes().values()) {
            collectSimpleTypeAsAdded(simpleType, model.getId(), changes);
        }
    }

    private void collectElementAsAdded(XsdElementModel element, String parentId, List<XsdModelChange> changes) {
        changes.add(XsdModelChange.nodeAdded(
                element.getId(),
                parentId,
                XsdModelChange.NodeType.ELEMENT
        ).build());

        // Add child elements
        for (XsdElementModel child : element.getChildren()) {
            collectElementAsAdded(child, element.getId(), changes);
        }

        // Add attributes
        for (XsdAttributeModel attribute : element.getAttributes()) {
            changes.add(XsdModelChange.nodeAdded(
                    attribute.getId(),
                    element.getId(),
                    XsdModelChange.NodeType.ATTRIBUTE
            ).build());
        }
    }

    private void collectComplexTypeAsAdded(XsdComplexTypeModel complexType, String parentId, List<XsdModelChange> changes) {
        changes.add(XsdModelChange.nodeAdded(
                complexType.getId(),
                parentId,
                XsdModelChange.NodeType.COMPLEX_TYPE
        ).build());

        // Add elements
        for (XsdElementModel element : complexType.getElements()) {
            collectElementAsAdded(element, complexType.getId(), changes);
        }

        // Add attributes
        for (XsdAttributeModel attribute : complexType.getAttributes()) {
            changes.add(XsdModelChange.nodeAdded(
                    attribute.getId(),
                    complexType.getId(),
                    XsdModelChange.NodeType.ATTRIBUTE
            ).build());
        }
    }

    private void collectSimpleTypeAsAdded(XsdSimpleTypeModel simpleType, String parentId, List<XsdModelChange> changes) {
        changes.add(XsdModelChange.nodeAdded(
                simpleType.getId(),
                parentId,
                XsdModelChange.NodeType.SIMPLE_TYPE
        ).build());
    }

    /**
     * Compares schema-level properties.
     */
    private void compareSchemaProperties(XsdSchemaModel oldModel, XsdSchemaModel newModel, List<XsdModelChange> changes) {
        Set<String> changedProperties = new HashSet<>();

        if (!Objects.equals(oldModel.getVersion(), newModel.getVersion())) {
            changedProperties.add("version");
        }

        if (!Objects.equals(oldModel.getTargetNamespace(), newModel.getTargetNamespace())) {
            changedProperties.add("targetNamespace");
        }

        if (!Objects.equals(oldModel.getElementFormDefault(), newModel.getElementFormDefault())) {
            changedProperties.add("elementFormDefault");
        }

        if (!Objects.equals(oldModel.getAttributeFormDefault(), newModel.getAttributeFormDefault())) {
            changedProperties.add("attributeFormDefault");
        }

        if (!oldModel.getNamespaces().equals(newModel.getNamespaces())) {
            changedProperties.add("namespaces");
        }

        if (!changedProperties.isEmpty()) {
            changes.add(XsdModelChange.nodeModified(newModel.getId(), XsdModelChange.NodeType.SCHEMA)
                    .changedProperties(changedProperties)
                    .build());
        }
    }

    /**
     * Compares global elements between two models.
     */
    private void compareGlobalElements(XsdSchemaModel oldModel, XsdSchemaModel newModel, List<XsdModelChange> changes) {
        Map<String, XsdElementModel> oldElements = indexElements(oldModel.getGlobalElements());
        Map<String, XsdElementModel> newElements = indexElements(newModel.getGlobalElements());

        // Find removed elements
        for (String id : oldElements.keySet()) {
            if (!newElements.containsKey(id)) {
                changes.add(XsdModelChange.nodeRemoved(
                        id,
                        oldModel.getId(),
                        XsdModelChange.NodeType.ELEMENT
                ).build());
            }
        }

        // Find added or modified elements
        for (Map.Entry<String, XsdElementModel> entry : newElements.entrySet()) {
            String id = entry.getKey();
            XsdElementModel newElement = entry.getValue();
            XsdElementModel oldElement = oldElements.get(id);

            if (oldElement == null) {
                // New element added
                collectElementAsAdded(newElement, newModel.getId(), changes);
            } else {
                // Compare existing elements
                compareElements(oldElement, newElement, changes);
            }
        }
    }

    /**
     * Compares two elements and their children.
     */
    private void compareElements(XsdElementModel oldElement, XsdElementModel newElement, List<XsdModelChange> changes) {
        Set<String> changedProperties = new HashSet<>();

        if (!Objects.equals(oldElement.getName(), newElement.getName())) {
            changedProperties.add("name");
        }

        if (!Objects.equals(oldElement.getType(), newElement.getType())) {
            changedProperties.add("type");
        }

        if (oldElement.getMinOccurs() != newElement.getMinOccurs()) {
            changedProperties.add("minOccurs");
        }

        if (oldElement.getMaxOccurs() != newElement.getMaxOccurs()) {
            changedProperties.add("maxOccurs");
        }

        if (oldElement.isNillable() != newElement.isNillable()) {
            changedProperties.add("nillable");
        }

        if (!Objects.equals(oldElement.getDefaultValue(), newElement.getDefaultValue())) {
            changedProperties.add("defaultValue");
        }

        if (!Objects.equals(oldElement.getFixedValue(), newElement.getFixedValue())) {
            changedProperties.add("fixedValue");
        }

        if (!Objects.equals(oldElement.getDocumentation(), newElement.getDocumentation())) {
            changedProperties.add("documentation");
        }

        if (!compareDocInfo(oldElement.getDocInfo(), newElement.getDocInfo())) {
            changedProperties.add("docInfo");
        }

        if (!changedProperties.isEmpty()) {
            changes.add(XsdModelChange.nodeModified(newElement.getId(), XsdModelChange.NodeType.ELEMENT)
                    .changedProperties(changedProperties)
                    .build());
        }

        // Compare child elements
        compareChildElements(oldElement, newElement, changes);

        // Compare attributes
        compareAttributes(oldElement.getAttributes(), newElement.getAttributes(), newElement.getId(), changes);
    }

    /**
     * Compares child elements.
     */
    private void compareChildElements(XsdElementModel oldParent, XsdElementModel newParent, List<XsdModelChange> changes) {
        Map<String, XsdElementModel> oldChildren = indexElements(oldParent.getChildren());
        Map<String, XsdElementModel> newChildren = indexElements(newParent.getChildren());

        // Find removed children
        for (String id : oldChildren.keySet()) {
            if (!newChildren.containsKey(id)) {
                changes.add(XsdModelChange.nodeRemoved(
                        id,
                        oldParent.getId(),
                        XsdModelChange.NodeType.ELEMENT
                ).build());
            }
        }

        // Find added or modified children
        for (Map.Entry<String, XsdElementModel> entry : newChildren.entrySet()) {
            String id = entry.getKey();
            XsdElementModel newChild = entry.getValue();
            XsdElementModel oldChild = oldChildren.get(id);

            if (oldChild == null) {
                collectElementAsAdded(newChild, newParent.getId(), changes);
            } else {
                compareElements(oldChild, newChild, changes);
            }
        }
    }

    /**
     * Compares attributes.
     */
    private void compareAttributes(List<XsdAttributeModel> oldAttributes, List<XsdAttributeModel> newAttributes,
                                   String parentId, List<XsdModelChange> changes) {
        Map<String, XsdAttributeModel> oldAttrs = indexAttributes(oldAttributes);
        Map<String, XsdAttributeModel> newAttrs = indexAttributes(newAttributes);

        // Find removed attributes
        for (String id : oldAttrs.keySet()) {
            if (!newAttrs.containsKey(id)) {
                changes.add(XsdModelChange.nodeRemoved(
                        id,
                        parentId,
                        XsdModelChange.NodeType.ATTRIBUTE
                ).build());
            }
        }

        // Find added or modified attributes
        for (Map.Entry<String, XsdAttributeModel> entry : newAttrs.entrySet()) {
            String id = entry.getKey();
            XsdAttributeModel newAttr = entry.getValue();
            XsdAttributeModel oldAttr = oldAttrs.get(id);

            if (oldAttr == null) {
                changes.add(XsdModelChange.nodeAdded(
                        id,
                        parentId,
                        XsdModelChange.NodeType.ATTRIBUTE
                ).build());
            } else {
                compareAttribute(oldAttr, newAttr, changes);
            }
        }
    }

    /**
     * Compares two attributes.
     */
    private void compareAttribute(XsdAttributeModel oldAttr, XsdAttributeModel newAttr, List<XsdModelChange> changes) {
        Set<String> changedProperties = new HashSet<>();

        if (!Objects.equals(oldAttr.getName(), newAttr.getName())) {
            changedProperties.add("name");
        }

        if (!Objects.equals(oldAttr.getType(), newAttr.getType())) {
            changedProperties.add("type");
        }

        if (oldAttr.isRequired() != newAttr.isRequired()) {
            changedProperties.add("required");
        }

        if (!Objects.equals(oldAttr.getDefaultValue(), newAttr.getDefaultValue())) {
            changedProperties.add("defaultValue");
        }

        if (!Objects.equals(oldAttr.getFixedValue(), newAttr.getFixedValue())) {
            changedProperties.add("fixedValue");
        }

        if (!Objects.equals(oldAttr.getDocumentation(), newAttr.getDocumentation())) {
            changedProperties.add("documentation");
        }

        if (!compareDocInfo(oldAttr.getDocInfo(), newAttr.getDocInfo())) {
            changedProperties.add("docInfo");
        }

        if (!changedProperties.isEmpty()) {
            changes.add(XsdModelChange.nodeModified(newAttr.getId(), XsdModelChange.NodeType.ATTRIBUTE)
                    .changedProperties(changedProperties)
                    .build());
        }
    }

    /**
     * Compares global complex types.
     */
    private void compareComplexTypes(XsdSchemaModel oldModel, XsdSchemaModel newModel, List<XsdModelChange> changes) {
        Map<String, XsdComplexTypeModel> oldTypes = oldModel.getGlobalComplexTypes();
        Map<String, XsdComplexTypeModel> newTypes = newModel.getGlobalComplexTypes();

        // Find removed types
        for (String id : oldTypes.keySet()) {
            if (!newTypes.containsKey(id)) {
                changes.add(XsdModelChange.nodeRemoved(
                        id,
                        oldModel.getId(),
                        XsdModelChange.NodeType.COMPLEX_TYPE
                ).build());
            }
        }

        // Find added or modified types
        for (Map.Entry<String, XsdComplexTypeModel> entry : newTypes.entrySet()) {
            String id = entry.getKey();
            XsdComplexTypeModel newType = entry.getValue();
            XsdComplexTypeModel oldType = oldTypes.get(id);

            if (oldType == null) {
                collectComplexTypeAsAdded(newType, newModel.getId(), changes);
            } else {
                compareComplexType(oldType, newType, changes);
            }
        }
    }

    /**
     * Compares two complex types.
     */
    private void compareComplexType(XsdComplexTypeModel oldType, XsdComplexTypeModel newType, List<XsdModelChange> changes) {
        Set<String> changedProperties = new HashSet<>();

        if (!Objects.equals(oldType.getName(), newType.getName())) {
            changedProperties.add("name");
        }

        if (oldType.isMixedContent() != newType.isMixedContent()) {
            changedProperties.add("mixedContent");
        }

        if (oldType.isAbstractType() != newType.isAbstractType()) {
            changedProperties.add("abstractType");
        }

        if (!Objects.equals(oldType.getBaseType(), newType.getBaseType())) {
            changedProperties.add("baseType");
        }

        if (!Objects.equals(oldType.getDocumentation(), newType.getDocumentation())) {
            changedProperties.add("documentation");
        }

        if (!compareDocInfo(oldType.getDocInfo(), newType.getDocInfo())) {
            changedProperties.add("docInfo");
        }

        if (!changedProperties.isEmpty()) {
            changes.add(XsdModelChange.nodeModified(newType.getId(), XsdModelChange.NodeType.COMPLEX_TYPE)
                    .changedProperties(changedProperties)
                    .build());
        }

        // Compare elements
        Map<String, XsdElementModel> oldElements = indexElements(oldType.getElements());
        Map<String, XsdElementModel> newElements = indexElements(newType.getElements());

        for (String id : oldElements.keySet()) {
            if (!newElements.containsKey(id)) {
                changes.add(XsdModelChange.nodeRemoved(
                        id,
                        oldType.getId(),
                        XsdModelChange.NodeType.ELEMENT
                ).build());
            }
        }

        for (Map.Entry<String, XsdElementModel> entry : newElements.entrySet()) {
            String id = entry.getKey();
            XsdElementModel newElement = entry.getValue();
            XsdElementModel oldElement = oldElements.get(id);

            if (oldElement == null) {
                collectElementAsAdded(newElement, newType.getId(), changes);
            } else {
                compareElements(oldElement, newElement, changes);
            }
        }

        // Compare attributes
        compareAttributes(oldType.getAttributes(), newType.getAttributes(), newType.getId(), changes);
    }

    /**
     * Compares global simple types.
     */
    private void compareSimpleTypes(XsdSchemaModel oldModel, XsdSchemaModel newModel, List<XsdModelChange> changes) {
        Map<String, XsdSimpleTypeModel> oldTypes = oldModel.getGlobalSimpleTypes();
        Map<String, XsdSimpleTypeModel> newTypes = newModel.getGlobalSimpleTypes();

        // Find removed types
        for (String id : oldTypes.keySet()) {
            if (!newTypes.containsKey(id)) {
                changes.add(XsdModelChange.nodeRemoved(
                        id,
                        oldModel.getId(),
                        XsdModelChange.NodeType.SIMPLE_TYPE
                ).build());
            }
        }

        // Find added or modified types
        for (Map.Entry<String, XsdSimpleTypeModel> entry : newTypes.entrySet()) {
            String id = entry.getKey();
            XsdSimpleTypeModel newType = entry.getValue();
            XsdSimpleTypeModel oldType = oldTypes.get(id);

            if (oldType == null) {
                collectSimpleTypeAsAdded(newType, newModel.getId(), changes);
            } else {
                compareSimpleType(oldType, newType, changes);
            }
        }
    }

    /**
     * Compares two simple types.
     */
    private void compareSimpleType(XsdSimpleTypeModel oldType, XsdSimpleTypeModel newType, List<XsdModelChange> changes) {
        Set<String> changedProperties = new HashSet<>();

        if (!Objects.equals(oldType.getName(), newType.getName())) {
            changedProperties.add("name");
        }

        if (!Objects.equals(oldType.getBaseType(), newType.getBaseType())) {
            changedProperties.add("baseType");
        }

        if (!Objects.equals(oldType.getDocumentation(), newType.getDocumentation())) {
            changedProperties.add("documentation");
        }

        if (!compareDocInfo(oldType.getDocInfo(), newType.getDocInfo())) {
            changedProperties.add("docInfo");
        }

        if (!oldType.getFacets().equals(newType.getFacets())) {
            changedProperties.add("facets");
        }

        if (!oldType.getEnumerations().equals(newType.getEnumerations())) {
            changedProperties.add("enumerations");
        }

        if (!changedProperties.isEmpty()) {
            changes.add(XsdModelChange.nodeModified(newType.getId(), XsdModelChange.NodeType.SIMPLE_TYPE)
                    .changedProperties(changedProperties)
                    .build());
        }
    }

    /**
     * Compares two XsdDocInfo objects.
     */
    private boolean compareDocInfo(XsdDocInfo oldInfo, XsdDocInfo newInfo) {
        if (oldInfo == null && newInfo == null) {
            return true;
        }
        if (oldInfo == null || newInfo == null) {
            return false;
        }

        return Objects.equals(oldInfo.getSinceVersion(), newInfo.getSinceVersion())
                && oldInfo.getSeeReferences().equals(newInfo.getSeeReferences())
                && Objects.equals(oldInfo.getDeprecationInfo(), newInfo.getDeprecationInfo());
    }

    /**
     * Creates an index of elements by their ID.
     */
    private Map<String, XsdElementModel> indexElements(List<XsdElementModel> elements) {
        Map<String, XsdElementModel> index = new HashMap<>();
        for (XsdElementModel element : elements) {
            index.put(element.getId(), element);
        }
        return index;
    }

    /**
     * Creates an index of attributes by their ID.
     */
    private Map<String, XsdAttributeModel> indexAttributes(List<XsdAttributeModel> attributes) {
        Map<String, XsdAttributeModel> index = new HashMap<>();
        for (XsdAttributeModel attribute : attributes) {
            index.put(attribute.getId(), attribute);
        }
        return index;
    }
}
