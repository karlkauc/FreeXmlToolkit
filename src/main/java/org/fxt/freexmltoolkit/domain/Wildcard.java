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
        ANY,           // xs:any - allows any element
        ANY_ATTRIBUTE  // xs:anyAttribute - allows any attribute
    }

    /**
     * Process contents mode - how the processor should validate wildcard content.
     */
    public enum ProcessContents {
        STRICT,  // Must be valid according to schema (default)
        LAX,     // Validate if schema is available, otherwise skip
        SKIP     // No validation
    }

    private Type type;
    private String namespace;           // Namespace constraint: ##any, ##other, ##local, ##targetNamespace, or specific URIs
    private ProcessContents processContents;
    private String minOccurs;           // For xs:any only
    private String maxOccurs;           // For xs:any only
    private String documentation;       // Optional documentation from xs:annotation

    public Wildcard() {
    }

    public Wildcard(Type type, String namespace) {
        this.type = type;
        this.namespace = namespace;
    }

    // Getters and setters

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public ProcessContents getProcessContents() {
        return processContents;
    }

    public void setProcessContents(ProcessContents processContents) {
        this.processContents = processContents;
    }

    public String getMinOccurs() {
        return minOccurs;
    }

    public void setMinOccurs(String minOccurs) {
        this.minOccurs = minOccurs;
    }

    public String getMaxOccurs() {
        return maxOccurs;
    }

    public void setMaxOccurs(String maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Gets a display name for the wildcard type.
     */
    public String getTypeDisplayName() {
        return switch (type) {
            case ANY -> "Any Element";
            case ANY_ATTRIBUTE -> "Any Attribute";
        };
    }

    /**
     * Gets a display string for the namespace constraint.
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
