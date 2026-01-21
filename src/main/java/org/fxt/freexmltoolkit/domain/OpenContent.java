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
     * Mode determines where the wildcard elements can appear within the content model.
     * This is a key concept in XSD 1.1 open content that controls the flexibility
     * of element placement.
     */
    public enum Mode {
        /**
         * Elements can appear anywhere among the explicitly defined elements.
         * This is the default mode and provides maximum flexibility, allowing
         * additional elements to be interleaved with the declared content.
         */
        INTERLEAVE,

        /**
         * Elements can only appear after the explicitly defined elements.
         * This mode is more restrictive, requiring all declared content to
         * appear first before any additional elements.
         */
        SUFFIX
    }

    /**
     * Indicates if this is default open content that applies to all complex types in the schema.
     * When true, this represents xs:defaultOpenContent; when false, it represents xs:openContent.
     */
    private boolean isDefault;

    /**
     * The mode controlling where wildcard elements can appear in the content model.
     * Defaults to {@link Mode#INTERLEAVE} if not explicitly specified.
     */
    private Mode mode;

    /**
     * The namespace constraint from the nested xs:any element.
     * Common values include "##any", "##other", "##local", "##targetNamespace",
     * or specific namespace URIs.
     */
    private String namespace;

    /**
     * The processContents attribute from the nested xs:any element.
     * Valid values are "strict" (full validation required), "lax" (validate if schema available),
     * or "skip" (no validation performed).
     */
    private String processContents;

    /**
     * Optional documentation describing the purpose or usage of this open content definition.
     */
    private String documentation;

    /**
     * Creates a new OpenContent instance with the default INTERLEAVE mode.
     * This constructor initializes the open content with the most flexible
     * mode that allows additional elements to appear anywhere in the content.
     */
    public OpenContent() {
        this.mode = Mode.INTERLEAVE;
    }

    /**
     * Creates a new OpenContent instance with the specified mode.
     *
     * @param mode the mode controlling where wildcard elements can appear;
     *             must not be null
     */
    public OpenContent(Mode mode) {
        this.mode = mode;
    }

    // Getters and setters

    /**
     * Checks whether this open content is a default open content definition.
     * Default open content applies to all complex types in the schema that do not
     * have their own open content specification.
     *
     * @return {@code true} if this is default open content (xs:defaultOpenContent);
     *         {@code false} if this is local open content (xs:openContent)
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Sets whether this open content is a default open content definition.
     *
     * @param isDefault {@code true} to mark this as default open content that applies
     *                  to all complex types in the schema; {@code false} for local open content
     */
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * Gets the mode that controls where wildcard elements can appear.
     *
     * @return the current mode; never null after construction
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Sets the mode that controls where wildcard elements can appear.
     *
     * @param mode the mode to set; should not be null
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Gets the namespace constraint for the wildcard elements.
     * This value comes from the nested xs:any element's namespace attribute.
     *
     * @return the namespace constraint, or {@code null} if not specified
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace constraint for the wildcard elements.
     *
     * @param namespace the namespace constraint; common values include "##any",
     *                  "##other", "##local", "##targetNamespace", or specific URIs
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Gets the processContents attribute value that controls validation behavior.
     *
     * @return the processContents value ("strict", "lax", or "skip"),
     *         or {@code null} if not specified
     */
    public String getProcessContents() {
        return processContents;
    }

    /**
     * Sets the processContents attribute that controls validation behavior.
     *
     * @param processContents the validation mode; valid values are "strict"
     *                        (full validation), "lax" (validate if schema available),
     *                        or "skip" (no validation)
     */
    public void setProcessContents(String processContents) {
        this.processContents = processContents;
    }

    /**
     * Gets the optional documentation for this open content definition.
     *
     * @return the documentation text, or {@code null} if no documentation is provided
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the optional documentation for this open content definition.
     *
     * @param documentation the documentation text describing the purpose or usage
     *                      of this open content
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Gets a human-readable display string for the current mode.
     * This is useful for presenting the mode in user interfaces with
     * additional explanatory text about what each mode means.
     *
     * @return a display string describing the mode and its behavior
     */
    public String getModeDisplayName() {
        return switch (mode) {
            case INTERLEAVE -> "Interleave (elements can appear anywhere)";
            case SUFFIX -> "Suffix (elements appear after defined content)";
        };
    }

    /**
     * Gets a human-readable display string for the namespace constraint.
     * This method translates the XSD namespace constraint values into
     * more understandable descriptions for user interfaces.
     *
     * @return a display string describing the namespace constraint;
     *         returns "##any (any namespace)" if no namespace is specified
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

    /**
     * Returns a string representation of this OpenContent instance.
     * The string includes all relevant properties for debugging purposes.
     *
     * @return a string representation containing isDefault, mode, namespace,
     *         and processContents values
     */
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
