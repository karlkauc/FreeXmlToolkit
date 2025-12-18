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
package org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.AttributeTypeInfo;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XmlSchemaProvider.ElementTypeInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides rich documentation tooltips for XML elements and attributes.
 *
 * <p>This class creates informative tooltips that display:</p>
 * <ul>
 *   <li>Element/attribute name and type</li>
 *   <li>Documentation from XSD annotations</li>
 *   <li>Type constraints (enumerations, patterns, ranges)</li>
 *   <li>Cardinality information</li>
 *   <li>Default/fixed values</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class DocumentationTooltipProvider {

    private final XmlSchemaProvider schemaProvider;

    /**
     * Creates a new DocumentationTooltipProvider.
     *
     * @param schemaProvider the schema provider to use
     */
    public DocumentationTooltipProvider(XmlSchemaProvider schemaProvider) {
        this.schemaProvider = schemaProvider;
    }

    /**
     * Creates a rich tooltip for an element.
     *
     * @param elementXPath the XPath of the element
     * @return a configured Tooltip, or null if no info available
     */
    public Tooltip createElementTooltip(String elementXPath) {
        if (schemaProvider == null || !schemaProvider.hasSchema()) {
            return null;
        }

        Optional<ElementTypeInfo> typeInfoOpt = schemaProvider.getElementTypeInfo(elementXPath);
        if (typeInfoOpt.isEmpty()) {
            return null;
        }

        ElementTypeInfo info = typeInfoOpt.get();
        VBox content = createElementTooltipContent(info);

        return createRichTooltip(content);
    }

    /**
     * Creates a rich tooltip for an attribute.
     *
     * @param elementXPath  the XPath of the parent element
     * @param attributeName the attribute name
     * @return a configured Tooltip, or null if no info available
     */
    public Tooltip createAttributeTooltip(String elementXPath, String attributeName) {
        if (schemaProvider == null || !schemaProvider.hasSchema()) {
            return null;
        }

        Optional<AttributeTypeInfo> typeInfoOpt = schemaProvider.getAttributeTypeInfo(elementXPath, attributeName);
        if (typeInfoOpt.isEmpty()) {
            return null;
        }

        AttributeTypeInfo info = typeInfoOpt.get();
        VBox content = createAttributeTooltipContent(info);

        return createRichTooltip(content);
    }

    /**
     * Installs a documentation tooltip on a node for an element.
     *
     * @param node         the node to install the tooltip on
     * @param elementXPath the XPath of the element
     */
    public void installElementTooltip(Node node, String elementXPath) {
        Tooltip tooltip = createElementTooltip(elementXPath);
        if (tooltip != null) {
            Tooltip.install(node, tooltip);
        }
    }

    /**
     * Installs a documentation tooltip on a node for an attribute.
     *
     * @param node          the node to install the tooltip on
     * @param elementXPath  the XPath of the parent element
     * @param attributeName the attribute name
     */
    public void installAttributeTooltip(Node node, String elementXPath, String attributeName) {
        Tooltip tooltip = createAttributeTooltip(elementXPath, attributeName);
        if (tooltip != null) {
            Tooltip.install(node, tooltip);
        }
    }

    /**
     * Creates the content for an element tooltip.
     */
    private VBox createElementTooltipContent(ElementTypeInfo info) {
        VBox content = new VBox(4);
        content.setPadding(new Insets(5));

        // Element name as header
        Label nameLabel = new Label(info.name());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        nameLabel.setStyle("-fx-text-fill: #212529;");
        content.getChildren().add(nameLabel);

        // Type name
        if (info.typeName() != null && !info.typeName().isEmpty()) {
            Label typeLabel = createInfoLabel("Type: " + info.typeName(), "#6c757d");
            content.getChildren().add(typeLabel);
        }

        // Cardinality
        String cardinality = formatCardinality(info.minOccurs(), info.maxOccurs());
        if (!cardinality.isEmpty()) {
            Label cardLabel = createInfoLabel("Cardinality: " + cardinality, "#6c757d");
            content.getChildren().add(cardLabel);
        }

        // Required/Optional
        if (info.isMandatory()) {
            Label reqLabel = createInfoLabel("Required", "#dc3545");
            reqLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
            content.getChildren().add(reqLabel);
        }

        // Default/Fixed value
        if (info.defaultValue() != null && !info.defaultValue().isEmpty()) {
            Label defLabel = createInfoLabel("Default: \"" + info.defaultValue() + "\"", "#17a2b8");
            content.getChildren().add(defLabel);
        }
        if (info.fixedValue() != null && !info.fixedValue().isEmpty()) {
            Label fixLabel = createInfoLabel("Fixed: \"" + info.fixedValue() + "\"", "#ffc107");
            content.getChildren().add(fixLabel);
        }

        // Enumeration values
        if (info.hasEnumeration()) {
            String enumStr = formatEnumerationValues(info.enumerationValues());
            Label enumLabel = createInfoLabel("Values: " + enumStr, "#6f42c1");
            enumLabel.setWrapText(true);
            enumLabel.setMaxWidth(300);
            content.getChildren().add(enumLabel);
        }

        // Facets
        if (info.facets() != null && !info.facets().isEmpty()) {
            String facetsStr = formatFacets(info.facets());
            if (!facetsStr.isEmpty()) {
                Label facetsLabel = createInfoLabel("Constraints: " + facetsStr, "#17a2b8");
                facetsLabel.setWrapText(true);
                facetsLabel.setMaxWidth(300);
                content.getChildren().add(facetsLabel);
            }
        }

        // Documentation
        if (info.documentation() != null && !info.documentation().isEmpty()) {
            Label docLabel = createInfoLabel(info.documentation(), "#212529");
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(350);
            docLabel.setStyle("-fx-text-fill: #212529; -fx-padding: 5 0 0 0;");
            content.getChildren().add(docLabel);
        }

        return content;
    }

    /**
     * Creates the content for an attribute tooltip.
     */
    private VBox createAttributeTooltipContent(AttributeTypeInfo info) {
        VBox content = new VBox(4);
        content.setPadding(new Insets(5));

        // Attribute name as header
        Label nameLabel = new Label("@" + info.name());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        nameLabel.setStyle("-fx-text-fill: #212529;");
        content.getChildren().add(nameLabel);

        // Type name
        if (info.typeName() != null && !info.typeName().isEmpty()) {
            Label typeLabel = createInfoLabel("Type: " + info.typeName(), "#6c757d");
            content.getChildren().add(typeLabel);
        }

        // Required/Optional
        Label reqLabel = createInfoLabel(info.isRequired() ? "Required" : "Optional",
                info.isRequired() ? "#dc3545" : "#28a745");
        reqLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        content.getChildren().add(reqLabel);

        // Default/Fixed value
        if (info.defaultValue() != null && !info.defaultValue().isEmpty()) {
            Label defLabel = createInfoLabel("Default: \"" + info.defaultValue() + "\"", "#17a2b8");
            content.getChildren().add(defLabel);
        }
        if (info.fixedValue() != null && !info.fixedValue().isEmpty()) {
            Label fixLabel = createInfoLabel("Fixed: \"" + info.fixedValue() + "\"", "#ffc107");
            content.getChildren().add(fixLabel);
        }

        // Enumeration values
        if (info.hasEnumeration()) {
            String enumStr = formatEnumerationValues(info.enumerationValues());
            Label enumLabel = createInfoLabel("Values: " + enumStr, "#6f42c1");
            enumLabel.setWrapText(true);
            enumLabel.setMaxWidth(300);
            content.getChildren().add(enumLabel);
        }

        // Facets
        if (info.facets() != null && !info.facets().isEmpty()) {
            String facetsStr = formatFacets(info.facets());
            if (!facetsStr.isEmpty()) {
                Label facetsLabel = createInfoLabel("Constraints: " + facetsStr, "#17a2b8");
                facetsLabel.setWrapText(true);
                facetsLabel.setMaxWidth(300);
                content.getChildren().add(facetsLabel);
            }
        }

        // Documentation
        if (info.documentation() != null && !info.documentation().isEmpty()) {
            Label docLabel = createInfoLabel(info.documentation(), "#212529");
            docLabel.setWrapText(true);
            docLabel.setMaxWidth(350);
            docLabel.setStyle("-fx-text-fill: #212529; -fx-padding: 5 0 0 0;");
            content.getChildren().add(docLabel);
        }

        return content;
    }

    /**
     * Creates a rich tooltip with custom content.
     */
    private Tooltip createRichTooltip(VBox content) {
        Tooltip tooltip = new Tooltip();
        tooltip.setGraphic(content);
        tooltip.setShowDelay(Duration.millis(300));
        tooltip.setHideDelay(Duration.millis(200));
        tooltip.setShowDuration(Duration.seconds(30));
        tooltip.setStyle("-fx-background-color: white; -fx-border-color: #ced4da; " +
                "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 5;");
        return tooltip;
    }

    /**
     * Creates a styled info label.
     */
    private Label createInfoLabel(String text, String color) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px;");
        return label;
    }

    /**
     * Formats cardinality information.
     */
    private String formatCardinality(String minOccurs, String maxOccurs) {
        if (minOccurs == null && maxOccurs == null) {
            return "";
        }

        String min = minOccurs != null ? minOccurs : "1";
        String max = maxOccurs != null ? maxOccurs : "1";

        if ("unbounded".equalsIgnoreCase(max)) {
            max = "*";
        }

        if (min.equals(max)) {
            if ("1".equals(min)) {
                return "[1] (exactly one)";
            }
            return "[" + min + "]";
        }

        if ("0".equals(min) && "1".equals(max)) {
            return "[0..1] (optional)";
        }
        if ("0".equals(min) && "*".equals(max)) {
            return "[0..*] (any number)";
        }
        if ("1".equals(min) && "*".equals(max)) {
            return "[1..*] (one or more)";
        }

        return "[" + min + ".." + max + "]";
    }

    /**
     * Formats enumeration values for display.
     */
    private String formatEnumerationValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        if (values.size() <= 5) {
            return String.join(", ", values);
        }

        // Show first 4 values + "... and X more"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values.get(i));
        }
        sb.append(" ... and ").append(values.size() - 4).append(" more");
        return sb.toString();
    }

    /**
     * Formats facets for display.
     */
    private String formatFacets(Map<String, String> facets) {
        if (facets == null || facets.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // Pattern
        if (facets.containsKey("pattern")) {
            appendFacet(sb, "Pattern", facets.get("pattern"));
        }

        // Length constraints
        if (facets.containsKey("minLength")) {
            appendFacet(sb, "Min length", facets.get("minLength"));
        }
        if (facets.containsKey("maxLength")) {
            appendFacet(sb, "Max length", facets.get("maxLength"));
        }
        if (facets.containsKey("length")) {
            appendFacet(sb, "Length", facets.get("length"));
        }

        // Numeric constraints
        if (facets.containsKey("minInclusive")) {
            appendFacet(sb, "Min", facets.get("minInclusive"));
        }
        if (facets.containsKey("maxInclusive")) {
            appendFacet(sb, "Max", facets.get("maxInclusive"));
        }
        if (facets.containsKey("minExclusive")) {
            appendFacet(sb, "Min (exclusive)", facets.get("minExclusive"));
        }
        if (facets.containsKey("maxExclusive")) {
            appendFacet(sb, "Max (exclusive)", facets.get("maxExclusive"));
        }

        // Digit constraints
        if (facets.containsKey("totalDigits")) {
            appendFacet(sb, "Total digits", facets.get("totalDigits"));
        }
        if (facets.containsKey("fractionDigits")) {
            appendFacet(sb, "Fraction digits", facets.get("fractionDigits"));
        }

        return sb.toString();
    }

    /**
     * Appends a facet to the string builder.
     */
    private void appendFacet(StringBuilder sb, String name, String value) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(name).append(": ").append(value);
    }

    // ==================== Static helper methods ====================

    /**
     * Creates a simple text tooltip.
     *
     * @param text the tooltip text
     * @return a configured Tooltip
     */
    public static Tooltip createSimpleTooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(300));
        tooltip.setStyle("-fx-font-size: 12px;");
        return tooltip;
    }

    /**
     * Creates a compact type info string.
     *
     * @param info the element type info
     * @return a compact string description
     */
    public static String createCompactTypeInfo(ElementTypeInfo info) {
        if (info == null) return "";

        StringBuilder sb = new StringBuilder();

        if (info.typeName() != null && !info.typeName().isEmpty()) {
            sb.append(info.typeName());
        }

        if (info.isMandatory()) {
            sb.append(" (required)");
        }

        return sb.toString();
    }

    /**
     * Creates a compact attribute info string.
     *
     * @param info the attribute type info
     * @return a compact string description
     */
    public static String createCompactAttributeInfo(AttributeTypeInfo info) {
        if (info == null) return "";

        StringBuilder sb = new StringBuilder();

        if (info.typeName() != null && !info.typeName().isEmpty()) {
            sb.append(info.typeName());
        }

        sb.append(info.isRequired() ? " (required)" : " (optional)");

        return sb.toString();
    }
}
