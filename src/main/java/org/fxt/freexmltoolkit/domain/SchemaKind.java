package org.fxt.freexmltoolkit.domain;

/** Kind of schema a {@link SchemaLibraryEntry} points to. */
public enum SchemaKind {
    XSD("XSD"), JSON_SCHEMA("JSON Schema"), DTD("DTD");

    private final String label;

    SchemaKind(String label) { this.label = label; }

    public String label() { return label; }
}
