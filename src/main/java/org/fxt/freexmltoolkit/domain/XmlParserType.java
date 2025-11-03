/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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

/**
 * Enumeration of available XML parser implementations for XSD validation.
 *
 * <p>This enum defines the different parser engines that can be used for
 * XML schema validation in the application.</p>
 */
public enum XmlParserType {
    /**
     * Saxon-HE parser for XSD validation.
     * Default parser with good XSD 1.0 support.
     */
    SAXON("Saxon-HE", "Saxon-HE with XSD 1.0 support"),

    /**
     * Apache Xerces2-J parser for XSD validation.
     * Alternative parser with comprehensive XSD 1.0 and partial XSD 1.1 support.
     */
    XERCES("Apache Xerces", "Apache Xerces with XSD 1.0/1.1 support");

    private final String displayName;
    private final String description;

    XmlParserType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the user-friendly display name for this parser type.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets a description of the parser capabilities.
     *
     * @return the parser description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
