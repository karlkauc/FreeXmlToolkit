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

    /**
     * Creates a new empty TypeAlternative.
     */
    public TypeAlternative() {
    }

    /**
     * Creates a new TypeAlternative with the specified test and type.
     *
     * @param test The XPath 2.0 test expression (or null for default)
     * @param type The type to assign if the test passes
     */
    public TypeAlternative(String test, String type) {
        this.test = test;
        this.type = type;
    }

    // Getters and setters

    /**
     * Gets the test expression.
     *
     * @return The XPath 2.0 boolean test expression
     */
    public String getTest() {
        return test;
    }

    /**
     * Sets the test expression.
     *
     * @param test The XPath 2.0 boolean test expression
     */
    public void setTest(String test) {
        this.test = test;
    }

    /**
     * Gets the type name.
     *
     * @return The name of the type to use
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type name.
     *
     * @param type The name of the type to use
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the documentation.
     *
     * @return Documentation associated with this alternative
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation.
     *
     * @param documentation Documentation associated with this alternative
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Gets the default namespace.
     *
     * @return The default namespace for XPath expressions
     */
    public String getXpathDefaultNamespace() {
        return xpathDefaultNamespace;
    }

    /**
     * Sets the default namespace.
     *
     * @param xpathDefaultNamespace The default namespace for XPath expressions
     */
    public void setXpathDefaultNamespace(String xpathDefaultNamespace) {
        this.xpathDefaultNamespace = xpathDefaultNamespace;
    }

    /**
     * Checks if this is the default alternative (no test condition).
     *
     * @return true if this is the default alternative, false otherwise
     */
    public boolean isDefault() {
        return test == null || test.isEmpty();
    }

    /**
     * Gets a display string for the alternative.
     *
     * @return A human-readable description of the condition
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
