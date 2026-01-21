/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.fxt.freexmltoolkit.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an XSD identity constraint (key, keyref, or unique).
 * Identity constraints define uniqueness and referential integrity rules within XML documents.
 */
public class IdentityConstraint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Type of identity constraint as defined in XSD.
     * Each type represents a different kind of constraint that can be applied
     * to XML elements to enforce data integrity rules.
     */
    public enum Type {
        /**
         * Represents an xs:key constraint.
         * Keys must be unique and non-nullable, similar to a primary key in databases.
         * Every element matched by the selector must have a value for the key fields.
         */
        KEY,

        /**
         * Represents an xs:keyref constraint.
         * Key references point to an existing key or unique constraint,
         * similar to a foreign key in databases. The referenced values must exist.
         */
        KEYREF,

        /**
         * Represents an xs:unique constraint.
         * Unique constraints ensure values are unique when present,
         * but unlike keys, the values can be absent (null).
         */
        UNIQUE
    }

    private Type type;
    private String name;
    private String selector;           // XPath expression to select elements
    private List<String> fields;       // XPath expressions for fields within selected elements
    private String refer;              // For keyref: name of the referenced key/unique
    private String documentation;      // Optional documentation from xs:annotation

    /**
     * Creates a new empty identity constraint with an empty field list.
     * The type and name must be set separately before the constraint is used.
     */
    public IdentityConstraint() {
        this.fields = new ArrayList<>();
    }

    /**
     * Creates a new identity constraint with the specified type and name.
     *
     * @param type the type of identity constraint (KEY, KEYREF, or UNIQUE)
     * @param name the name of the constraint as defined in the XSD schema
     */
    public IdentityConstraint(Type type, String name) {
        this.type = type;
        this.name = name;
        this.fields = new ArrayList<>();
    }

    /**
     * Returns the type of this identity constraint.
     *
     * @return the constraint type (KEY, KEYREF, or UNIQUE)
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the type of this identity constraint.
     *
     * @param type the constraint type to set (KEY, KEYREF, or UNIQUE)
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Returns the name of this identity constraint as defined in the XSD schema.
     *
     * @return the constraint name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this identity constraint.
     *
     * @param name the constraint name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the XPath selector expression that identifies which elements are constrained.
     * The selector defines the scope within which the identity constraint applies.
     *
     * @return the XPath selector expression
     */
    public String getSelector() {
        return selector;
    }

    /**
     * Sets the XPath selector expression that identifies which elements are constrained.
     *
     * @param selector the XPath selector expression to set
     */
    public void setSelector(String selector) {
        this.selector = selector;
    }

    /**
     * Returns the list of XPath field expressions that define the values being constrained.
     * Each field expression identifies a specific value within the selected elements
     * that participates in the identity constraint.
     *
     * @return a list of XPath field expressions
     */
    public List<String> getFields() {
        return fields;
    }

    /**
     * Sets the list of XPath field expressions for this constraint.
     *
     * @param fields the list of XPath field expressions to set
     */
    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    /**
     * Adds a single XPath field expression to this constraint.
     *
     * @param field the XPath field expression to add
     */
    public void addField(String field) {
        this.fields.add(field);
    }

    /**
     * Returns the name of the referenced constraint for keyref constraints.
     * This value is only applicable when the type is KEYREF and identifies
     * which key or unique constraint this keyref references.
     *
     * @return the name of the referenced constraint, or null if not a keyref
     */
    public String getRefer() {
        return refer;
    }

    /**
     * Sets the name of the referenced constraint for keyref constraints.
     *
     * @param refer the name of the key or unique constraint to reference
     */
    public void setRefer(String refer) {
        this.refer = refer;
    }

    /**
     * Returns the optional documentation associated with this constraint.
     * Documentation is extracted from xs:annotation elements in the XSD schema.
     *
     * @return the documentation text, or null if no documentation is available
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation for this constraint.
     *
     * @param documentation the documentation text to set
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Returns a human-readable display name for the constraint type.
     * This is useful for presenting the constraint type in user interfaces.
     *
     * @return a display name such as "Key", "Key Reference", or "Unique"
     */
    public String getTypeDisplayName() {
        return switch (type) {
            case KEY -> "Key";
            case KEYREF -> "Key Reference";
            case UNIQUE -> "Unique";
        };
    }

    /**
     * Checks whether this constraint is a key reference (keyref).
     * Key references are used to establish referential integrity between elements,
     * similar to foreign keys in databases.
     *
     * @return true if this is a KEYREF constraint, false otherwise
     */
    public boolean isKeyRef() {
        return type == Type.KEYREF;
    }

    @Override
    public String toString() {
        return "IdentityConstraint{" +
                "type=" + type +
                ", name='" + name + '\'' +
                ", selector='" + selector + '\'' +
                ", fields=" + fields +
                (refer != null ? ", refer='" + refer + '\'' : "") +
                '}';
    }
}
