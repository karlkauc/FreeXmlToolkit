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
     * Type of identity constraint.
     */
    public enum Type {
        KEY,      // xs:key - unique and not nullable (like primary key)
        KEYREF,   // xs:keyref - references a key or unique (like foreign key)
        UNIQUE    // xs:unique - unique but can be null
    }

    private Type type;
    private String name;
    private String selector;           // XPath expression to select elements
    private List<String> fields;       // XPath expressions for fields within selected elements
    private String refer;              // For keyref: name of the referenced key/unique
    private String documentation;      // Optional documentation from xs:annotation

    public IdentityConstraint() {
        this.fields = new ArrayList<>();
    }

    public IdentityConstraint(Type type, String name) {
        this.type = type;
        this.name = name;
        this.fields = new ArrayList<>();
    }

    // Getters and setters

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public void addField(String field) {
        this.fields.add(field);
    }

    public String getRefer() {
        return refer;
    }

    public void setRefer(String refer) {
        this.refer = refer;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Gets a display name for the constraint type.
     */
    public String getTypeDisplayName() {
        return switch (type) {
            case KEY -> "Key";
            case KEYREF -> "Key Reference";
            case UNIQUE -> "Unique";
        };
    }

    /**
     * Checks if this is a key reference that refers to another constraint.
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
