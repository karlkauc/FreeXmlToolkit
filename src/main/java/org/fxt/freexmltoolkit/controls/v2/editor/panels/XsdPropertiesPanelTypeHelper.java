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

package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for type-related functionality in XsdPropertiesPanel.
 *
 * <p>Manages type selection, icon updates, and type discovery.</p>
 *
 * @since 2.0
 */
public class XsdPropertiesPanelTypeHelper {
    private static final Logger logger = LogManager.getLogger(XsdPropertiesPanelTypeHelper.class);

    private static final List<String> BUILTIN_TYPES = new ArrayList<>();

    static {
        // XSD built-in types
        BUILTIN_TYPES.addAll(java.util.Arrays.asList(
                "xs:string", "xs:normalizedString", "xs:token",
                "xs:base64Binary", "xs:hexBinary",
                "xs:integer", "xs:long", "xs:int", "xs:short", "xs:byte",
                "xs:nonNegativeInteger", "xs:positiveInteger",
                "xs:unsignedLong", "xs:unsignedInt", "xs:unsignedShort", "xs:unsignedByte",
                "xs:decimal", "xs:float", "xs:double",
                "xs:boolean", "xs:date", "xs:time", "xs:dateTime",
                "xs:duration", "xs:gYear", "xs:gYearMonth", "xs:gMonth", "xs:gMonthDay", "xs:gDay",
                "xs:QName", "xs:anyURI", "xs:ENTITY", "xs:NCName", "xs:ID", "xs:IDREF", "xs:IDREFS",
                "xs:NOTATION", "xs:Name", "xs:NMTOKEN", "xs:NMTOKENS"
        ));
    }

    /**
     * Updates type icon based on the selected type.
     *
     * @param typeIcon the icon to update
     * @param datatype the datatype string
     */
    public void updateTypeIcon(FontIcon typeIcon, String datatype) {
        if (datatype == null || datatype.isEmpty()) {
            typeIcon.setIconLiteral("bi-diagram-3");
            typeIcon.setStyle("-fx-icon-color: #666666;");
            return;
        }

        if (isBuiltinType(datatype)) {
            typeIcon.setIconLiteral("bi-building");
            typeIcon.setStyle("-fx-icon-color: #0066cc;"); // Blue for built-in
        } else {
            typeIcon.setIconLiteral("bi-diagram-3");
            typeIcon.setStyle("-fx-icon-color: #228b22;"); // Green for custom
        }

        logger.debug("Updated type icon for type: {}", datatype);
    }

    /**
     * Checks if a type is a built-in XSD type.
     *
     * @param type the type to check
     * @return true if built-in type
     */
    public boolean isBuiltinType(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        return BUILTIN_TYPES.contains(type) || type.startsWith("xs:");
    }

    /**
     * Gets all available types (built-in + user-defined).
     *
     * @param rootSchema the root schema node
     * @return list of available types
     */
    public List<String> getAvailableTypes(XsdNode rootSchema) {
        List<String> types = new ArrayList<>(BUILTIN_TYPES);

        if (rootSchema != null) {
            Set<String> userDefinedTypes = new HashSet<>();
            collectUserDefinedTypes(rootSchema, userDefinedTypes);
            types.addAll(userDefinedTypes);
        }

        return types;
    }

    /**
     * Collects user-defined types from the schema tree.
     *
     * @param node the current node to process
     * @param types the set to collect types into
     */
    private void collectUserDefinedTypes(XsdNode node, Set<String> types) {
        if (node == null) {
            return;
        }

        // Add SimpleType and ComplexType names
        String nodeTypeName = node.getClass().getSimpleName();
        if (nodeTypeName.contains("SimpleType") || nodeTypeName.contains("ComplexType")) {
            if (node.getName() != null && !node.getName().isEmpty()) {
                types.add(node.getName());
            }
        }

        // Recursively process children
        if (node.getChildren() != null) {
            for (XsdNode child : node.getChildren()) {
                collectUserDefinedTypes(child, types);
            }
        }
    }

    /**
     * Formats type list for display in combo box.
     *
     * @param types raw list of types
     * @return formatted list
     */
    public List<String> formatTypeList(List<String> types) {
        List<String> formatted = new ArrayList<>(types);
        // Sort built-in types first, then user-defined
        formatted.sort((a, b) -> {
            boolean aBuiltin = isBuiltinType(a);
            boolean bBuiltin = isBuiltinType(b);

            if (aBuiltin && !bBuiltin) return -1;
            if (!aBuiltin && bBuiltin) return 1;
            return a.compareTo(b);
        });

        return formatted;
    }

    /**
     * Gets type description for tooltip.
     *
     * @param type the type string
     * @return description
     */
    public String getTypeDescription(String type) {
        if (type == null || type.isEmpty()) {
            return "Select a type";
        }

        if (isBuiltinType(type)) {
            return "Built-in XSD type";
        } else {
            return "User-defined type";
        }
    }
}
