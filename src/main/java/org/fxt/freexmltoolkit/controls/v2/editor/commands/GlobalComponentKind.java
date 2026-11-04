package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * The kinds of top-level schema components the Schema Overview can create, with their
 * XSD names and symbol spaces (complex and simple types share one).
 *
 * @since 2.6
 */
public enum GlobalComponentKind {
    ELEMENT("element", "Element"),
    COMPLEX_TYPE("complexType", "Complex Type"),
    SIMPLE_TYPE("simpleType", "Simple Type"),
    GROUP("group", "Group"),
    ATTRIBUTE_GROUP("attributeGroup", "Attribute Group"),
    ATTRIBUTE("attribute", "Attribute"),
    NOTATION("notation", "Notation"),
    ANNOTATION("annotation", "Annotation");

    private final String xsdName;
    private final String label;

    GlobalComponentKind(String xsdName, String label) {
        this.xsdName = xsdName;
        this.label = label;
    }

    /** @return the XSD element name ({@code complexType}, …) */
    public String xsdName() {
        return xsdName;
    }

    /** @return the user-facing label ("Complex Type", …) */
    public String label() {
        return label;
    }

    /** @return true when components of this kind carry a name */
    public boolean isNamed() {
        return this != ANNOTATION;
    }

    /** @return the symbol space: kinds with the same value share their names (types) */
    public GlobalComponentKind symbolSpace() {
        return this == SIMPLE_TYPE ? COMPLEX_TYPE : this;
    }

    /** @return the default name offered for a new component of this kind */
    public String defaultName() {
        return switch (this) {
            case ELEMENT -> "newElement";
            case COMPLEX_TYPE -> "NewComplexType";
            case SIMPLE_TYPE -> "NewSimpleType";
            case GROUP -> "NewGroup";
            case ATTRIBUTE_GROUP -> "NewAttributeGroup";
            case ATTRIBUTE -> "newAttribute";
            case NOTATION -> "newNotation";
            case ANNOTATION -> "";
        };
    }

    /**
     * @return the kind of a top-level schema child, or null for directives, comments and
     *         references
     */
    public static GlobalComponentKind of(XsdNode node) {
        return switch (node) {
            case XsdElement element when element.getRef() == null || element.getRef().isBlank() -> ELEMENT;
            case XsdComplexType ignored -> COMPLEX_TYPE;
            case XsdSimpleType ignored -> SIMPLE_TYPE;
            case XsdGroup group when !group.isReference() -> GROUP;
            case XsdAttributeGroup group when !group.isReference() -> ATTRIBUTE_GROUP;
            case XsdAttribute attribute when attribute.getRef() == null || attribute.getRef().isBlank() -> ATTRIBUTE;
            case XsdNotation ignored -> NOTATION;
            case XsdAnnotation ignored -> ANNOTATION;
            case null, default -> null;
        };
    }

    /**
     * Creates a new, minimal component of this kind.
     *
     * @param name      the name (ignored for annotations)
     * @param xsdPrefix the prefix bound to the XSD namespace ({@code xs})
     * @return the new node
     */
    public XsdNode create(String name, String xsdPrefix) {
        String string = xsdPrefix + ":string";
        return switch (this) {
            case ELEMENT -> {
                XsdElement element = new XsdElement(name);
                element.setType(string);
                yield element;
            }
            case COMPLEX_TYPE -> {
                XsdComplexType type = new XsdComplexType(name);
                type.addChild(new XsdSequence());
                yield type;
            }
            case SIMPLE_TYPE -> {
                XsdSimpleType type = new XsdSimpleType(name);
                type.addChild(new XsdRestriction(string));
                yield type;
            }
            case GROUP -> {
                XsdGroup group = new XsdGroup(name);
                group.addChild(new XsdSequence());
                yield group;
            }
            case ATTRIBUTE_GROUP -> new XsdAttributeGroup(name);
            case ATTRIBUTE -> {
                XsdAttribute attribute = new XsdAttribute(name);
                attribute.setType(string);
                yield attribute;
            }
            case NOTATION -> {
                XsdNotation notation = new XsdNotation(name);
                notation.setPublicId(name);
                yield notation;
            }
            case ANNOTATION -> {
                XsdAnnotation annotation = new XsdAnnotation();
                annotation.addDocumentation(new XsdDocumentation("Documentation", null, null));
                yield annotation;
            }
        };
    }
}
