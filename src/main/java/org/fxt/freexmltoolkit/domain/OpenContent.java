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
 * Represents XSD 1.1 open content (xs:openContent or xs:defaultOpenContent).
 * Open content allows a type to accept additional elements beyond those explicitly defined.
 */
public class OpenContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Mode determines where the wildcard elements can appear.
     */
    public enum Mode {
        INTERLEAVE,  // Elements can appear anywhere among the explicitly defined elements (default)
        SUFFIX       // Elements can only appear after the explicitly defined elements
    }

    /**
     * Indicates if this is default open content (applies to all types in schema).
     */
    private boolean isDefault;

    private Mode mode;
    private String namespace;           // From nested xs:any - namespace constraint
    private String processContents;     // From nested xs:any - strict/lax/skip
    private String documentation;       // Optional documentation

    public OpenContent() {
        this.mode = Mode.INTERLEAVE; // Default mode
    }

    public OpenContent(Mode mode) {
        this.mode = mode;
    }

    // Getters and setters

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getProcessContents() {
        return processContents;
    }

    public void setProcessContents(String processContents) {
        this.processContents = processContents;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Gets a display string for the mode.
     */
    public String getModeDisplayName() {
        return switch (mode) {
            case INTERLEAVE -> "Interleave (elements can appear anywhere)";
            case SUFFIX -> "Suffix (elements appear after defined content)";
        };
    }

    /**
     * Gets a display string for the namespace.
     */
    public String getNamespaceDisplayName() {
        if (namespace == null || namespace.isEmpty()) {
            return "##any (any namespace)";
        }
        return switch (namespace) {
            case "##any" -> "Any namespace";
            case "##other" -> "Any namespace except target namespace";
            case "##local" -> "No namespace";
            case "##targetNamespace" -> "Target namespace only";
            default -> namespace; // Specific URI(s)
        };
    }

    @Override
    public String toString() {
        return "OpenContent{" +
                "isDefault=" + isDefault +
                ", mode=" + mode +
                ", namespace='" + namespace + '\'' +
                ", processContents='" + processContents + '\'' +
                '}';
    }
}
