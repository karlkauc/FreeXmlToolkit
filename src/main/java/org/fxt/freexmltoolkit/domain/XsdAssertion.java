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
 * Represents an XSD 1.1 assertion (xs:assert or xs:assertion).
 * Assertions are XPath 2.0 boolean expressions that must evaluate to true for the instance to be valid.
 */
public class XsdAssertion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Type of assertion.
     */
    public enum Type {
        ASSERT,      // xs:assert on complexType
        ASSERTION    // xs:assertion on simpleType
    }

    private Type type;
    private String test;              // XPath 2.0 boolean expression
    private String documentation;     // Optional documentation from xs:annotation
    private String xpathDefaultNamespace; // Optional xpath-default-namespace attribute

    public XsdAssertion() {
    }

    public XsdAssertion(Type type, String test) {
        this.type = type;
        this.test = test;
    }

    // Getters and setters

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
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
     * Gets a display name for the assertion type.
     */
    public String getTypeDisplayName() {
        return switch (type) {
            case ASSERT -> "Assert";
            case ASSERTION -> "Assertion";
        };
    }

    @Override
    public String toString() {
        return "XsdAssertion{" +
                "type=" + type +
                ", test='" + test + '\'' +
                (xpathDefaultNamespace != null ? ", xpathDefaultNamespace='" + xpathDefaultNamespace + '\'' : "") +
                '}';
    }
}
