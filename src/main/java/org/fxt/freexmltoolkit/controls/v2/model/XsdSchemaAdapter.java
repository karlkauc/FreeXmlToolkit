package org.fxt.freexmltoolkit.controls.v2.model;

import org.fxt.freexmltoolkit.controls.v2.model.XsdCompositorModel.CompositorType;

import java.util.UUID;

/**
 * Adapter to convert between XsdSchema (XsdNode hierarchy) and XsdSchemaModel (flat structure).
 * This enables the new XsdNodeFactory to work with the existing XsdGraphView.
 *
 * @since 2.0
 */
public class XsdSchemaAdapter {

    /**
     * Converts an XsdSchema (tree structure) to XsdSchemaModel (flat structure).
     *
     * @param schema the XsdSchema to convert
     * @return the converted XsdSchemaModel
     */
    public static XsdSchemaModel toSchemaModel(XsdSchema schema) {
        if (schema == null) {
            return null;
        }

        XsdSchemaModel model = new XsdSchemaModel();

        // Copy schema-level properties
        if (schema.getTargetNamespace() != null) {
            model.setTargetNamespace(schema.getTargetNamespace());
        }
        if (schema.getElementFormDefault() != null) {
            model.setElementFormDefault(schema.getElementFormDefault());
        }
        if (schema.getAttributeFormDefault() != null) {
            model.setAttributeFormDefault(schema.getAttributeFormDefault());
        }

        // Copy namespaces
        if (schema.getNamespaces() != null) {
            schema.getNamespaces().forEach(model::addNamespace);
        }

        // Process children to extract global components
        if (schema.getChildren() != null) {
            for (XsdNode child : schema.getChildren()) {
                processChild(child, model);
            }
        }

        return model;
    }

    /**
     * Processes a child node and adds it to the appropriate collection in the schema model.
     *
     * @param child the child node to process
     * @param model the schema model to populate
     */
    private static void processChild(XsdNode child, XsdSchemaModel model) {
        switch (child.getNodeType()) {
            case ELEMENT -> {
                XsdElement element = (XsdElement) child;
                model.addGlobalElement(convertElement(element));
            }
            case COMPLEX_TYPE -> {
                XsdComplexType complexType = (XsdComplexType) child;
                if (complexType.getName() != null && !complexType.getName().isEmpty()) {
                    model.addGlobalComplexType(complexType.getName(), convertComplexType(complexType));
                }
            }
            case SIMPLE_TYPE -> {
                XsdSimpleType simpleType = (XsdSimpleType) child;
                if (simpleType.getName() != null && !simpleType.getName().isEmpty()) {
                    model.addGlobalSimpleType(simpleType.getName(), convertSimpleType(simpleType));
                }
            }
            case GROUP -> {
                XsdGroup group = (XsdGroup) child;
                if (group.getName() != null && !group.getName().isEmpty()) {
                    model.addGlobalGroup(group.getName(), convertGroup(group));
                }
            }
            case ATTRIBUTE_GROUP -> {
                XsdAttributeGroup attrGroup = (XsdAttributeGroup) child;
                if (attrGroup.getName() != null && !attrGroup.getName().isEmpty()) {
                    model.addGlobalAttributeGroup(attrGroup.getName(), convertAttributeGroup(attrGroup));
                }
            }
            default -> {
                // Ignore other node types at schema level
            }
        }
    }

    /**
     * Converts an XsdElement to XsdElementModel.
     */
    private static XsdElementModel convertElement(XsdElement element) {
        String id = UUID.randomUUID().toString();
        XsdElementModel elementModel = new XsdElementModel(id, element.getName());

        // Basic properties
        if (element.getType() != null) {
            elementModel.setType(element.getType());
        }
        elementModel.setMinOccurs(element.getMinOccurs());
        elementModel.setMaxOccurs(element.getMaxOccurs());
        elementModel.setNillable(element.isNillable());

        if (element.getDefaultValue() != null) {
            elementModel.setDefaultValue(element.getDefaultValue());
        }
        if (element.getFixed() != null) {
            elementModel.setFixedValue(element.getFixed());
        }

        // Documentation
        if (element.getDocumentation() != null) {
            elementModel.setDocumentation(element.getDocumentation());
        }

        // Process children for inline types and compositors
        for (XsdNode child : element.getChildren()) {
            if (child instanceof XsdComplexType inlineType) {
                // Element has inline complex type - need to copy its content
                processInlineComplexType(elementModel, inlineType);
            } else if (child instanceof XsdSimpleType) {
                elementModel.setInlineSimpleType(convertSimpleType((XsdSimpleType) child));
            }
        }

        return elementModel;
    }

    /**
     * Processes an inline complex type by copying its compositors and attributes to the element.
     */
    private static void processInlineComplexType(XsdElementModel elementModel, XsdComplexType complexType) {
        // Process children to find compositor and attributes
        for (XsdNode child : complexType.getChildren()) {
            if (child instanceof XsdSequence) {
                XsdCompositorModel compositor = createCompositor(CompositorType.SEQUENCE, child);
                elementModel.addCompositor(compositor);
            } else if (child instanceof XsdChoice) {
                XsdCompositorModel compositor = createCompositor(CompositorType.CHOICE, child);
                elementModel.addCompositor(compositor);
            } else if (child instanceof XsdAll) {
                XsdCompositorModel compositor = createCompositor(CompositorType.ALL, child);
                elementModel.addCompositor(compositor);
            } else if (child instanceof XsdAttribute) {
                elementModel.addAttribute(convertAttribute((XsdAttribute) child));
            }
        }
    }

    /**
     * Creates a compositor model from an XsdNode compositor.
     */
    private static XsdCompositorModel createCompositor(CompositorType type, XsdNode compositorNode) {
        String id = UUID.randomUUID().toString();
        XsdCompositorModel compositor = XsdCompositorModel.create(id, type);

        // Add elements from compositor
        for (XsdNode child : compositorNode.getChildren()) {
            if (child instanceof XsdElement) {
                compositor.addElement(convertElement((XsdElement) child));
            } else if (child instanceof XsdSequence || child instanceof XsdChoice || child instanceof XsdAll) {
                // Nested compositor
                CompositorType nestedType = child instanceof XsdSequence ? CompositorType.SEQUENCE :
                        child instanceof XsdChoice ? CompositorType.CHOICE :
                                CompositorType.ALL;
                XsdCompositorModel nestedCompositor = createCompositor(nestedType, child);
                compositor.addCompositor(nestedCompositor);
            }
        }

        return compositor;
    }

    /**
     * Converts an XsdComplexType to XsdComplexTypeModel.
     */
    private static XsdComplexTypeModel convertComplexType(XsdComplexType complexType) {
        String id = UUID.randomUUID().toString();
        String name = complexType.getName() != null ? complexType.getName() : "";
        XsdComplexTypeModel typeModel = new XsdComplexTypeModel(id, name);

        typeModel.setMixedContent(complexType.isMixed());
        typeModel.setAbstractType(complexType.isAbstract());

        // Documentation
        if (complexType.getDocumentation() != null) {
            typeModel.setDocumentation(complexType.getDocumentation());
        }

        // Process children to find compositor and attributes
        for (XsdNode child : complexType.getChildren()) {
            if (child instanceof XsdSequence) {
                XsdCompositorModel compositor = createCompositor(CompositorType.SEQUENCE, child);
                typeModel.addCompositor(compositor);
            } else if (child instanceof XsdChoice) {
                XsdCompositorModel compositor = createCompositor(CompositorType.CHOICE, child);
                typeModel.addCompositor(compositor);
            } else if (child instanceof XsdAll) {
                XsdCompositorModel compositor = createCompositor(CompositorType.ALL, child);
                typeModel.addCompositor(compositor);
            } else if (child instanceof XsdAttribute) {
                typeModel.addAttribute(convertAttribute((XsdAttribute) child));
            }
        }

        return typeModel;
    }

    /**
     * Converts an XsdAttribute to XsdAttributeModel.
     */
    private static XsdAttributeModel convertAttribute(XsdAttribute attribute) {
        String id = UUID.randomUUID().toString();
        XsdAttributeModel attrModel = new XsdAttributeModel(id, attribute.getName());

        if (attribute.getType() != null) {
            attrModel.setType(attribute.getType());
        }

        // Convert "use" attribute - map "required" to setRequired(true)
        if ("required".equals(attribute.getUse())) {
            attrModel.setRequired(true);
        }

        if (attribute.getDefaultValue() != null) {
            attrModel.setDefaultValue(attribute.getDefaultValue());
        }
        if (attribute.getFixed() != null) {
            attrModel.setFixedValue(attribute.getFixed());
        }

        // Documentation
        if (attribute.getDocumentation() != null) {
            attrModel.setDocumentation(attribute.getDocumentation());
        }

        return attrModel;
    }

    /**
     * Converts an XsdSimpleType to XsdSimpleTypeModel.
     */
    private static XsdSimpleTypeModel convertSimpleType(XsdSimpleType simpleType) {
        String id = UUID.randomUUID().toString();
        String name = simpleType.getName() != null ? simpleType.getName() : "";
        XsdSimpleTypeModel typeModel = new XsdSimpleTypeModel(id, name);

        // Documentation
        if (simpleType.getDocumentation() != null) {
            typeModel.setDocumentation(simpleType.getDocumentation());
        }

        // Find restriction
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction restriction) {
                if (restriction.getBase() != null) {
                    typeModel.setBaseType(restriction.getBase());
                }

                // Convert facets
                for (XsdFacet facet : restriction.getFacets()) {
                    String facetName = facet.getFacetType().toString().toLowerCase();
                    typeModel.addFacet(facetName, facet.getValue());
                }
            }
        }

        return typeModel;
    }

    /**
     * Converts an XsdGroup to XsdGroupModel.
     */
    private static XsdGroupModel convertGroup(XsdGroup group) {
        String id = UUID.randomUUID().toString();
        XsdGroupModel groupModel = new XsdGroupModel(id, group.getName());

        // Documentation
        if (group.getDocumentation() != null) {
            groupModel.setDocumentation(group.getDocumentation());
        }

        // Process children to find compositor
        for (XsdNode child : group.getChildren()) {
            if (child instanceof XsdSequence) {
                XsdCompositorModel compositor = createCompositor(CompositorType.SEQUENCE, child);
                groupModel.setCompositor(compositor);
            } else if (child instanceof XsdChoice) {
                XsdCompositorModel compositor = createCompositor(CompositorType.CHOICE, child);
                groupModel.setCompositor(compositor);
            } else if (child instanceof XsdAll) {
                XsdCompositorModel compositor = createCompositor(CompositorType.ALL, child);
                groupModel.setCompositor(compositor);
            }
        }

        return groupModel;
    }

    /**
     * Converts an XsdAttributeGroup to XsdAttributeGroupModel.
     */
    private static XsdAttributeGroupModel convertAttributeGroup(XsdAttributeGroup attrGroup) {
        String id = UUID.randomUUID().toString();
        XsdAttributeGroupModel groupModel = new XsdAttributeGroupModel(id, attrGroup.getName());

        // Documentation
        if (attrGroup.getDocumentation() != null) {
            groupModel.setDocumentation(attrGroup.getDocumentation());
        }

        // Process children (attributes)
        for (XsdNode child : attrGroup.getChildren()) {
            if (child instanceof XsdAttribute) {
                groupModel.addAttribute(convertAttribute((XsdAttribute) child));
            }
        }

        return groupModel;
    }
}
