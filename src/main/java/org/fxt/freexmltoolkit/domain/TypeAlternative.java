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
 * Represents an XSD 1.1 type alternative (xs:alternative).
 * Type alternatives provide conditional type assignment based on XPath 2.0 expressions.
 */
public class TypeAlternative implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String test;              // XPath 2.0 boolean expression (null for default alternative)
    private String type;              // Type name to use when test is true
    private String documentation;     // Optional documentation from xs:annotation
    private String xpathDefaultNamespace; // Optional xpath-default-namespace attribute

    public TypeAlternative() {
    }

    public TypeAlternative(String test, String type) {
        this.test = test;
        this.type = type;
    }

    // Getters and setters

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    public String getXpathDefaultNamespace() {
        return xpathDefaultNamespace;
    }

    public void setXpathDefaultNamespace(String xpathDefaultNamespace) {
        this.xpathDefaultNamespace = xpathDefaultNamespace;
    }

    /**
     * Checks if this is the default alternative (no test condition).
     */
    public boolean isDefault() {
        return test == null || test.isEmpty();
    }

    /**
     * Gets a display string for the alternative.
     */
    public String getDisplayCondition() {
        if (isDefault()) {
            return "Default (otherwise)";
        }
        return "When: " + test;
    }

    @Override
    public String toString() {
        return "TypeAlternative{" +
                (test != null ? "test='" + test + '\'' : "default") +
                ", type='" + type + '\'' +
                '}';
    }
}
