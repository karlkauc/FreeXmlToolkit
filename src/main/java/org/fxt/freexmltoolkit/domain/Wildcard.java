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

/**
 * Represents an XSD wildcard (xs:any or xs:anyAttribute).
 * Wildcards allow elements or attributes from specified namespaces to appear in the content model.
 */
public class Wildcard implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Type of wildcard.
     */
    public enum Type {
        /** xs:any - allows any element from specified namespaces. */
        ANY,
        /** xs:anyAttribute - allows any attribute from specified namespaces. */
        ANY_ATTRIBUTE
    }

    /**
     * Process contents mode - how the processor should validate wildcard content.
     */
    public enum ProcessContents {
        /** Content must be valid according to schema (default behavior). */
        STRICT,
        /** Validate if schema is available, otherwise skip validation. */
        LAX,
        /** No validation is performed on the wildcard content. */
        SKIP
    }

    private Type type;
    private String namespace;           // Namespace constraint: ##any, ##other, ##local, ##targetNamespace, or specific URIs
    private ProcessContents processContents;
    private String minOccurs;           // For xs:any only
    private String maxOccurs;           // For xs:any only
    private String documentation;       // Optional documentation from xs:annotation

    /**
     * Default constructor.
     */
    public Wildcard() {
    }

    /**
     * Creates a wildcard with the specified type and namespace constraint.
     *
     * @param type      the type of wildcard (ANY or ANY_ATTRIBUTE)
     * @param namespace the namespace constraint
     */
    public Wildcard(Type type, String namespace) {
        this.type = type;
        this.namespace = namespace;
    }

    // Getters and setters

    /**
     * Gets the type of this wildcard.
     *
     * @return the wildcard type (ANY or ANY_ATTRIBUTE)
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the type of this wildcard.
     *
     * @param type the wildcard type to set
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Gets the namespace constraint.
     *
     * @return the namespace constraint (e.g., ##any, ##other, ##local, ##targetNamespace, or specific URIs)
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace constraint.
     *
     * @param namespace the namespace constraint to set
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the process contents mode.
     *
     * @return the process contents mode (STRICT, LAX, or SKIP)
     */
    public ProcessContents getProcessContents() {
        return processContents;
    }

    /**
     * Sets the process contents mode.
     *
     * @param processContents the process contents mode to set
     */
    public void setProcessContents(ProcessContents processContents) {
        this.processContents = processContents;
    }

    /**
     * Gets the minimum occurrences for xs:any wildcards.
     *
     * @return the minOccurs value, or null if not set
     */
    public String getMinOccurs() {
        return minOccurs;
    }

    /**
     * Sets the minimum occurrences for xs:any wildcards.
     *
     * @param minOccurs the minOccurs value to set
     */
    public void setMinOccurs(String minOccurs) {
        this.minOccurs = minOccurs;
    }

    /**
     * Gets the maximum occurrences for xs:any wildcards.
     *
     * @return the maxOccurs value, or null if not set
     */
    public String getMaxOccurs() {
        return maxOccurs;
    }

    /**
     * Sets the maximum occurrences for xs:any wildcards.
     *
     * @param maxOccurs the maxOccurs value to set
     */
    public void setMaxOccurs(String maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    /**
     * Gets the optional documentation from xs:annotation.
     *
     * @return the documentation text, or null if not present
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation text.
     *
     * @param documentation the documentation to set
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Gets a display name for the wildcard type.
     *
     * @return a human-readable display name ("Any Element" or "Any Attribute")
     */
    public String getTypeDisplayName() {
        return switch (type) {
            case ANY -> "Any Element";
            case ANY_ATTRIBUTE -> "Any Attribute";
        };
    }

    /**
     * Gets a display string for the namespace constraint.
     *
     * @return a human-readable description of the namespace constraint
     */
    public String getNamespaceDisplayName() {
        if (namespace == null || namespace.isEmpty()) {
            return "##any";
        }
        return switch (namespace) {
            case "##any" -> "Any namespace";
            case "##other" -> "Any namespace except target namespace";
            case "##local" -> "No namespace";
            case "##targetNamespace" -> "Target namespace only";
            default -> namespace; // Specific URI(s)
        };
    }

    /**
     * Gets cardinality string for xs:any wildcards.
     *
     * @return the cardinality notation (e.g., "[1]", "[0..*]"), or null for ANY_ATTRIBUTE wildcards
     */
    public String getCardinality() {
        if (type != Type.ANY) {
            return null;
        }
        String min = (minOccurs == null || minOccurs.isEmpty()) ? "1" : minOccurs;
        String max = (maxOccurs == null || maxOccurs.isEmpty()) ? "1" : maxOccurs;

        if ("unbounded".equals(max)) {
            max = "*";
        }

        if (min.equals(max)) {
            return "[" + min + "]";
        } else {
            return "[" + min + ".." + max + "]";
        }
    }

    @Override
    public String toString() {
        return "Wildcard{" +
                "type=" + type +
                ", namespace='" + namespace + '\'' +
                ", processContents=" + processContents +
                (minOccurs != null ? ", minOccurs='" + minOccurs + '\'' : "") +
                (maxOccurs != null ? ", maxOccurs='" + maxOccurs + '\'' : "") +
                '}';
    }
}
