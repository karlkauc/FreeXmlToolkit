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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for sorting XSD schema children according to configurable order.
 * <p>
 * This sorter supports two modes:
 * <ul>
 *   <li>{@link XsdSortOrder#TYPE_BEFORE_NAME} - Sort by XSD construct type (alphabetically), then by name</li>
 *   <li>{@link XsdSortOrder#NAME_BEFORE_TYPE} - Sort by element name (alphabetically)</li>
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
public final class XsdNodeSorter {

    private static final Logger logger = LogManager.getLogger(XsdNodeSorter.class);

    private XsdNodeSorter() {
        // Utility class - no instantiation
    }

    /**
     * Sorts schema children according to the specified order.
     * <p>
     * Fixed order (always applied):
     * <ol>
     *   <li>xs:import (alphabetically by namespace + schemaLocation)</li>
     *   <li>xs:include (alphabetically by schemaLocation)</li>
     *   <li>Global xs:element (alphabetically by name)</li>
     *   <li>Remaining nodes per sortOrder setting</li>
     * </ol>
     *
     * @param children  the list of schema children to sort
     * @param sortOrder the sorting order to apply
     * @return a new sorted list (original list is not modified)
     */
    public static List<XsdNode> sortSchemaChildren(List<XsdNode> children, XsdSortOrder sortOrder) {
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }

        logger.debug("Sorting {} schema children with order: {}", children.size(), sortOrder);

        // Partition nodes into categories
        List<XsdNode> imports = new ArrayList<>();
        List<XsdNode> includes = new ArrayList<>();
        List<XsdNode> globalElements = new ArrayList<>();
        List<XsdNode> otherNodes = new ArrayList<>();

        for (XsdNode child : children) {
            if (child instanceof XsdImport) {
                imports.add(child);
            } else if (child instanceof XsdInclude) {
                includes.add(child);
            } else if (child instanceof XsdElement) {
                globalElements.add(child);
            } else {
                otherNodes.add(child);
            }
        }

        // Sort imports alphabetically by namespace + schemaLocation
        imports.sort(Comparator.comparing(
                n -> getImportSortKey((XsdImport) n),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        // Sort includes alphabetically by schemaLocation
        includes.sort(Comparator.comparing(
                n -> getIncludeSortKey((XsdInclude) n),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        // Sort global elements alphabetically by name
        globalElements.sort(Comparator.comparing(
                XsdNode::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        // Sort other nodes per setting
        if (sortOrder == XsdSortOrder.TYPE_BEFORE_NAME) {
            otherNodes.sort(Comparator
                    .comparing(XsdNodeSorter::getTypeSortKey, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(XsdNode::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        } else {
            // NAME_BEFORE_TYPE
            otherNodes.sort(Comparator.comparing(
                    XsdNode::getName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        }

        // Combine in fixed order
        List<XsdNode> result = new ArrayList<>(children.size());
        result.addAll(imports);
        result.addAll(includes);
        result.addAll(globalElements);
        result.addAll(otherNodes);

        logger.debug("Sorted result: {} imports, {} includes, {} elements, {} other",
                imports.size(), includes.size(), globalElements.size(), otherNodes.size());

        return result;
    }

    /**
     * Gets the sort key for xs:import elements.
     * Combines namespace and schemaLocation for sorting.
     *
     * @param xsdImport the import element
     * @return the sort key
     */
    private static String getImportSortKey(XsdImport xsdImport) {
        String namespace = xsdImport.getNamespace();
        String schemaLocation = xsdImport.getSchemaLocation();
        StringBuilder key = new StringBuilder();
        if (namespace != null) {
            key.append(namespace);
        }
        if (schemaLocation != null) {
            key.append(schemaLocation);
        }
        return key.toString();
    }

    /**
     * Gets the sort key for xs:include elements.
     *
     * @param include the include element
     * @return the sort key (schemaLocation)
     */
    private static String getIncludeSortKey(XsdInclude include) {
        return include.getSchemaLocation() != null ? include.getSchemaLocation() : "";
    }

    /**
     * Gets sort key for type-based sorting.
     * Returns lowercase type name for alphabetical ordering.
     * <p>
     * This results in alphabetical ordering of types:
     * attribute, attributegroup, complextype, group, simpletype, etc.
     *
     * @param node the XSD node
     * @return the type-based sort key
     */
    private static String getTypeSortKey(XsdNode node) {
        if (node == null || node.getNodeType() == null) {
            return "zzz"; // Unknown types at the end
        }
        return node.getNodeType().name().toLowerCase();
    }

    /**
     * Checks if two node lists have the same content (regardless of order).
     * Useful for detecting if sorting actually changed anything.
     *
     * @param list1 first list
     * @param list2 second list
     * @return true if lists have same content in same order
     */
    public static boolean hasSameOrder(List<XsdNode> list1, List<XsdNode> list2) {
        if (list1 == null || list2 == null) {
            return list1 == list2;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        for (int i = 0; i < list1.size(); i++) {
            if (list1.get(i) != list2.get(i)) {
                return false;
            }
        }
        return true;
    }
}
