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

/**
 * Strategy for generating values in profiled XML generation.
 * Each strategy determines how a specific XPath element or attribute gets its value.
 */
public enum GenerationStrategy {

    AUTO("Auto", "Type-based automatic generation (default behavior)"),
    FIXED("Fixed Value", "Use a fixed literal value"),
    OMIT("Omit", "Skip this element/attribute entirely"),
    EMPTY("Empty", "Create element but leave value empty"),
    XSD_EXAMPLE("XSD Example", "Use example values from XSD annotations"),
    ENUM_CYCLE("Enum Cycle", "Cycle through enumeration values sequentially"),
    SEQUENCE("Sequence", "Auto-increment with configurable pattern"),
    XPATH_REF("XPath Reference", "Copy value from another XPath"),
    RANDOM_FROM_LIST("Random from List", "Pick randomly from a user-defined list"),
    TEMPLATE("Template", "String template with placeholders"),
    NULL("Null (xsi:nil)", "Set xsi:nil=\"true\" for nillable elements");

    private final String displayName;
    private final String description;

    GenerationStrategy(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
