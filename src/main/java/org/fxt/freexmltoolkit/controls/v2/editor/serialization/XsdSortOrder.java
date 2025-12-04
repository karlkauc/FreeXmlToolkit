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

package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

/**
 * Defines the sorting order for XSD schema elements during serialization.
 * <p>
 * When saving XSD files, elements can be sorted in two ways:
 * <ul>
 *   <li>{@link #TYPE_BEFORE_NAME} - Sort by XSD construct type (alphabetically), then by name within each type</li>
 *   <li>{@link #NAME_BEFORE_TYPE} - Sort purely by element name (alphabetically)</li>
 * </ul>
 * <p>
 * In both modes, the following fixed order is maintained at the top:
 * <ol>
 *   <li>xs:import (sorted alphabetically by namespace/schemaLocation)</li>
 *   <li>xs:include (sorted alphabetically by schemaLocation)</li>
 *   <li>Global xs:element declarations (sorted alphabetically by name)</li>
 * </ol>
 *
 * @since 2.0
 */
public enum XsdSortOrder {

    /**
     * Sort by XSD construct type first (alphabetically), then by name within each type.
     * <p>
     * Example order: attributeGroup, complexType, group, simpleType (A-Z by type),
     * with each type's elements sorted alphabetically by name.
     */
    TYPE_BEFORE_NAME("Type before Name"),

    /**
     * Sort purely by element name (alphabetically), regardless of XSD construct type.
     */
    NAME_BEFORE_TYPE("Name before Type");

    private final String displayName;

    XsdSortOrder(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the display name for UI presentation.
     *
     * @return the human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the display name.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Finds a sort order by its display name.
     *
     * @param displayName the display name to search for
     * @return the matching XsdSortOrder, or null if not found
     */
    public static XsdSortOrder findByDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        for (XsdSortOrder order : values()) {
            if (order.displayName.equals(displayName)) {
                return order;
            }
        }
        return null;
    }
}
