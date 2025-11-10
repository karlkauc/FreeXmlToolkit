package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Enumeration of XSD node types in the model.
 *
 * @since 2.0
 */
public enum XsdNodeType {
    SCHEMA,
    ELEMENT,
    ATTRIBUTE,
    COMPLEX_TYPE,
    SIMPLE_TYPE,
    SEQUENCE,
    CHOICE,
    ALL,
    GROUP,
    ATTRIBUTE_GROUP,
    ANNOTATION,
    DOCUMENTATION,
    APPINFO,

    // Simple type constructs
    RESTRICTION,
    FACET,
    LIST,
    UNION,

    // Complex content constructs
    SIMPLE_CONTENT,
    COMPLEX_CONTENT,
    EXTENSION,

    // Identity constraints
    KEY,
    KEYREF,
    UNIQUE,
    SELECTOR,
    FIELD,

    // XSD 1.1 features
    ASSERT,
    ALTERNATIVE,
    OPEN_CONTENT,
    OVERRIDE,

    // Import/Include
    IMPORT,
    INCLUDE,
    REDEFINE
}
